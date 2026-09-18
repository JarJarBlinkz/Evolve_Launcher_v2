package com.jarjarblinkz.EvolveLauncher;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * Accessibility service that watches Quest's system UI for sustained hover
 * on a specific app tile and opens Evolve Launcher when the dwell threshold
 * is reached. Quick hovers (clicking through to open the app normally) do
 * not trigger - the user must hold their pointer on the target tile for
 * HOVER_DWELL_MS continuous milliseconds.
 *
 * The user must enable this service manually in:
 *   Settings -> Accessibility -> Evolve Launcher hover trigger
 */
public class EvolveAccessibilityService extends AccessibilityService {

    private static final String TAG = "EvolveA11y";

    // Set to true to log every hover event with full node-tree dump.
    // Useful for discovering what identifying info Quest exposes for
    // each hover target. Turn off once you've found a working matcher.
    private static final boolean DISCOVERY_MODE = false;

    // How many parent levels to walk up when looking for metadata.
    private static final int PARENT_WALK_DEPTH = 4;

    // The exact content description of the app tile that triggers the
    // launcher when hovered. This is the label shown under the icon in
    // Quest's library panel - "Camera", "Store", "Files", etc. Change
    // this to whatever app you want to hijack as a launcher shortcut.
    private static final String TRIGGER_APP_NAME = "Help & Tips";

    // How long the pointer must remain on the trigger tile before the
    // launcher opens. Lets you still open the app normally with a quick
    // tap-through - only a sustained dwell triggers the launcher.
    private static final long HOVER_DWELL_MS = 300;

    // Cooldown - don't open the launcher more than once every this many ms.
    private static final long LAUNCH_COOLDOWN_MS = 4000;
    private long lastLaunchMs = 0;

    // Dwell tracking - runs on the main thread.
    private final Handler dwellHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingTrigger = null;

    // Packages that indicate a VR game is now in the foreground.
    // UnityPlayerActivity and UE4 GameActivity cover most Quest games.
    private static final String[] VR_GAME_CLASSES = {
            "com.unity3d.player.UnityPlayerActivity",
            "com.epicgames.ue4.GameActivity",
            "com.epicgames.unreal.GameActivity"
    };

    // Cooldown for wake lock so we don't fire it repeatedly
    private long lastWakeLockMs = 0;
    private static final long WAKE_LOCK_COOLDOWN_MS = 3000;

    // Store suppression - push Evolve to front when store appears uninvited
    private static final String STORE_PKG = "com.oculus.store";
    private static final String KEY_SUPPRESS_STORE = "suppress_store";
    private boolean userOpenedStore = false;
    private long lastStoreSuppressMs = 0;
    private static final long STORE_SUPPRESS_COOLDOWN_MS = 5000;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        int type = event.getEventType();

        // Detect when a VR game window becomes active and fire a wake lock
        // to force the compositor to refresh and dismiss the loading overlay.
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkgName = event.getPackageName();
            CharSequence className = event.getClassName();

            // Store suppression - bring Evolve back if store appears uninvited
            if (pkgName != null && STORE_PKG.contentEquals(pkgName)) {
                android.content.SharedPreferences prefs = getSharedPreferences("VRLPrefs",
                        android.content.Context.MODE_PRIVATE);
                boolean suppressEnabled = prefs.getBoolean(KEY_SUPPRESS_STORE, false);
                long now = System.currentTimeMillis();
                if (suppressEnabled && !userOpenedStore &&
                        now - lastStoreSuppressMs > STORE_SUPPRESS_COOLDOWN_MS) {
                    lastStoreSuppressMs = now;
                    Log.i(TAG, "🚫 Store appeared uninvited - pushing Evolve to front");
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            Intent intent = new Intent(this, MainActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                            startActivity(intent);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to suppress store", e);
                        }
                    }, 500);
                }
                // Do NOT reset userOpenedStore here - keep it true while store is open
            } else if (pkgName != null && !STORE_PKG.contentEquals(pkgName)) {
                // User navigated away from store - safe to reset the flag now
                if (userOpenedStore) {
                    Log.i(TAG, "User left store - resetting suppress bypass");
                    userOpenedStore = false;
                }
            }

            if (className != null) {
                for (String gameClass : VR_GAME_CLASSES) {
                    if (gameClass.contentEquals(className)) {
                        fireCompositorRefresh();
                        break;
                    }
                }
            }
        }

        // We need both enter and exit to drive the dwell timer:
        //   HOVER_ENTER on target  -> start dwell
        //   HOVER_EXIT on target   -> cancel dwell
        //   HOVER_ENTER on non-target -> cancel dwell (pointer moved away)
        boolean isEnter = type == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER ||
                type == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                type == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
        boolean isExit = type == AccessibilityEvent.TYPE_VIEW_HOVER_EXIT;

        if (!isEnter && !isExit) {
            return;
        }

        String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "";
        AccessibilityNodeInfo source = event.getSource();

        if (DISCOVERY_MODE && isEnter) {
            dumpEventDetails(event, source, pkg);
        }

        try {
            boolean onTarget = (source != null && isHoveringTarget(source));

            if (isEnter) {
                if (onTarget) {
                    startDwellTimer();
                } else {
                    cancelDwellTimer("hover entered non-target");
                }
            } else if (isExit) {
                // Exit always cancels - if user re-enters, they'll restart
                cancelDwellTimer("hover exited");
            }
        } finally {
            if (source != null) source.recycle();
        }
    }

    /**
     * Check if the hover landed on (or inside) our trigger tile by walking
     * up from the source node looking for the matching content description.
     */
    private boolean isHoveringTarget(AccessibilityNodeInfo source) {
        if (nodeMatchesTarget(source)) return true;
        AccessibilityNodeInfo node = source.getParent();
        int depth = 0;
        while (node != null && depth < PARENT_WALK_DEPTH) {
            try {
                if (nodeMatchesTarget(node)) {
                    node.recycle();
                    return true;
                }
                AccessibilityNodeInfo next = node.getParent();
                node.recycle();
                node = next;
                depth++;
            } catch (Exception e) {
                break;
            }
        }
        if (node != null) node.recycle();
        return false;
    }

    private boolean nodeMatchesTarget(AccessibilityNodeInfo node) {
        if (node == null) return false;
        CharSequence desc = node.getContentDescription();
        return desc != null && TRIGGER_APP_NAME.contentEquals(desc);
    }

    /**
     * Start the dwell timer. If a timer is already pending (the user is
     * still hovering on the target), do nothing - don't restart and reset
     * progress. New events for the same target should let the existing
     * timer continue to completion.
     */
    private void startDwellTimer() {
        if (pendingTrigger != null) return; // already counting down

        Log.i(TAG, "👀 Hovering " + TRIGGER_APP_NAME + " - dwell timer started (" +
                HOVER_DWELL_MS + "ms)");

        pendingTrigger = () -> {
            pendingTrigger = null;
            triggerLauncherOpen("sustained hover on " + TRIGGER_APP_NAME);
        };
        dwellHandler.postDelayed(pendingTrigger, HOVER_DWELL_MS);
    }

    /**
     * Cancel any pending dwell trigger. Called when the pointer moves
     * away from the target tile (either explicit HOVER_EXIT or a new
     * HOVER_ENTER on a different element).
     */
    private void cancelDwellTimer(String reason) {
        if (pendingTrigger != null) {
            dwellHandler.removeCallbacks(pendingTrigger);
            pendingTrigger = null;
            if (DISCOVERY_MODE) {
                Log.i(TAG, "  dwell cancelled: " + reason);
            }
        }
    }

    private void fireCompositorRefresh() {
        long now = System.currentTimeMillis();
        if (now - lastWakeLockMs < WAKE_LOCK_COOLDOWN_MS) return;
        lastWakeLockMs = now;

        Log.i(TAG, "🔄 VR game detected - firing compositor refresh");

        // Fire wake lock to force the display compositor to refresh.
        // This simulates the screen off/on that clears the loading overlay.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                PowerManager.WakeLock wl = pm.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                                PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "Evolve:CompositorRefresh");
                wl.acquire(300);
                wl.release();
                Log.i(TAG, "🔄 Compositor refresh wake lock fired");
            } catch (Exception e) {
                Log.e(TAG, "Compositor refresh failed", e);
            }
        }, 800);
    }

    // Called from MainActivity when user explicitly opens the store
    // from the Meta Apps category - allows it through the suppression
    public static EvolveAccessibilityService instance;

    public void setUserOpenedStore() {
        userOpenedStore = true;
        Log.i(TAG, "Store opened by user - suppression bypassed");
    }

    private void triggerLauncherOpen(String reason) {
        long now = System.currentTimeMillis();
        if (now - lastLaunchMs < LAUNCH_COOLDOWN_MS) {
            return;
        }
        lastLaunchMs = now;

        Log.i(TAG, "🎯 Launcher trigger fired: " + reason);

        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open launcher", e);
        }
    }

    private void dumpEventDetails(AccessibilityEvent event, AccessibilityNodeInfo source, String pkg) {
        StringBuilder sb = new StringBuilder();
        sb.append("EVENT type=").append(AccessibilityEvent.eventTypeToString(event.getEventType()));
        sb.append(" pkg=").append(pkg);
        sb.append(" class=").append(event.getClassName());
        List<CharSequence> eventText = event.getText();
        if (eventText != null && !eventText.isEmpty()) {
            sb.append(" eventText=").append(eventText);
        }
        if (event.getContentDescription() != null) {
            sb.append(" eventDesc=").append(event.getContentDescription());
        }
        Log.i(TAG, sb.toString());

        if (source == null) {
            Log.i(TAG, "  source=null");
            return;
        }
        Log.i(TAG, "  source: " + describeNode(source));
        AccessibilityNodeInfo node = source.getParent();
        int depth = 1;
        while (node != null && depth <= PARENT_WALK_DEPTH) {
            Log.i(TAG, "  parent[" + depth + "]: " + describeNode(node));
            AccessibilityNodeInfo next = node.getParent();
            node.recycle();
            node = next;
            depth++;
        }
        if (node != null) node.recycle();
    }

    private String describeNode(AccessibilityNodeInfo node) {
        if (node == null) return "<null>";
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        return "class=" + node.getClassName() +
                " desc=" + node.getContentDescription() +
                " text=" + node.getText() +
                " id=" + node.getViewIdResourceName() +
                " bounds=" + bounds.toShortString() +
                " clickable=" + node.isClickable() +
                " childCount=" + node.getChildCount();
    }

    @Override
    public void onInterrupt() { }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "Accessibility service connected - hover on '" +
                TRIGGER_APP_NAME + "' for " + HOVER_DWELL_MS + "ms to open Evolve");

        // On boot the store may already be in the foreground by the time
        // the accessibility service connects. Check after a short delay
        // and push Evolve to front if store is showing uninvited.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                android.content.SharedPreferences prefs = getSharedPreferences(
                        "VRLPrefs", android.content.Context.MODE_PRIVATE);
                if (!prefs.getBoolean(KEY_SUPPRESS_STORE, false)) return;
                if (userOpenedStore) return;

                android.app.ActivityManager am = (android.app.ActivityManager)
                        getSystemService(Context.ACTIVITY_SERVICE);
                if (am == null) return;
                java.util.List<android.app.ActivityManager.RunningTaskInfo> tasks =
                        am.getRunningTasks(1);
                if (tasks != null && !tasks.isEmpty()) {
                    android.content.ComponentName top = tasks.get(0).topActivity;
                    if (top != null && STORE_PKG.equals(top.getPackageName())) {
                        Log.i(TAG, "🚫 Store in foreground on service connect - pushing Evolve");
                        lastStoreSuppressMs = System.currentTimeMillis();
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(intent);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Boot store check failed", e);
            }
        }, 3000);
    }

    @Override
    public void onDestroy() {
        instance = null;
        cancelDwellTimer("service destroyed");
        super.onDestroy();
    }
}