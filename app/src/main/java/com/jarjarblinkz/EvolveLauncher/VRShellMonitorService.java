package com.jarjarblinkz.EvolveLauncher;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

/**
 * AGGRESSIVE Service that ALWAYS keeps launcher open in home environment
 * Monitors constantly and immediately restarts launcher if it's not visible
 */
public class VRShellMonitorService extends Service {

    private static final String TAG = "VRShellMonitor";
    private static final long CHECK_INTERVAL = 1000; // Check every 1 second (aggressive!)
    private static final long IMMEDIATE_RESTART_DELAY = 500; // 500ms delay before restart

    private Handler handler;
    private Runnable checkRunnable;
    private BroadcastReceiver homeReceiver;
    private BroadcastReceiver screenReceiver;
    private BroadcastReceiver packageReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "AGGRESSIVE VR Shell Monitor Service created - launcher will ALWAYS be open in home");

        handler = new Handler(Looper.getMainLooper());

        // Register all receivers for maximum coverage
        registerHomeReceiver();
        registerScreenReceiver();
        registerPackageReceiver();

        // Start aggressive periodic checking
        startAggressiveCheck();
    }

    /**
     * Listen for HOME button presses and home intent broadcasts
     * This is the PRIMARY trigger - user pressed HOME = launcher should open IMMEDIATELY
     */
    private void registerHomeReceiver() {
        homeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.i(TAG, "HOME event detected: " + action + " - IMMEDIATELY opening launcher");

                // User pressed HOME or returned to home environment
                // Restart launcher IMMEDIATELY with minimal delay
                handler.postDelayed(() -> forceRestartLauncher(), 200);
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS); // Home button pressed
        filter.addAction(Intent.ACTION_MAIN);
        registerReceiver(homeReceiver, filter);

        Log.i(TAG, "Home receiver registered - listening for HOME button");
    }

    /**
     * Listen for screen on/off events (VR headset put on/off)
     */
    private void registerScreenReceiver() {
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    Log.i(TAG, "Screen ON - User put on headset - opening launcher NOW");
                    handler.postDelayed(() -> forceRestartLauncher(), 1500);
                } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                    Log.i(TAG, "User PRESENT - opening launcher NOW");
                    handler.postDelayed(() -> forceRestartLauncher(), 500);
                } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    Log.i(TAG, "Screen OFF - User took off headset");
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenReceiver, filter);

        Log.i(TAG, "Screen receiver registered");
    }

    /**
     * Listen for package events (apps closing, system restarts)
     */
    private void registerPackageReceiver() {
        packageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                // When any app-related event happens, check if we should be visible
                if (Intent.ACTION_PACKAGE_RESTARTED.equals(action) ||
                        Intent.ACTION_PACKAGE_REPLACED.equals(action)) {

                    Log.i(TAG, "Package event: " + action + " - checking launcher state");
                    handler.postDelayed(() -> checkAndRestartLauncher(), 2000);
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_RESTARTED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);

        Log.i(TAG, "Package receiver registered");
    }

    /**
     * AGGRESSIVE periodic checking - every 1 second
     * Ensures launcher is ALWAYS visible in home environment
     */
    private void startAggressiveCheck() {
        checkRunnable = new Runnable() {
            @Override
            public void run() {
                checkAndRestartLauncher();
                handler.postDelayed(this, CHECK_INTERVAL);
            }
        };

        // Start checking immediately
        handler.post(checkRunnable);
        Log.i(TAG, "AGGRESSIVE periodic check started (every " + CHECK_INTERVAL + "ms)");
    }

    /**
     * Check if launcher should be visible and restart if needed
     * This is called every second to ensure launcher is ALWAYS open in home
     */
    private void checkAndRestartLauncher() {
        try {
            // Check if we're the default home launcher
            String packageName = getPackageName();

            // If we're the default home launcher, we should ALWAYS be visible
            // unless the user is actively inside another app
            if (isDefaultHomeLauncher()) {
                if (isUserInHomeEnvironment()) {
                    // User is in home environment but launcher might not be visible
                    if (!isLauncherVisible()) {
                        Log.i(TAG, "User in HOME environment but launcher NOT visible - RESTARTING NOW");
                        forceRestartLauncher();
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error checking launcher state", e);
        }
    }

    /**
     * Check if our app is set as the default home launcher
     */
    private boolean isDefaultHomeLauncher() {
        try {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.addCategory(Intent.CATEGORY_DEFAULT);

            String packageName = getPackageName();
            String currentHome = homeIntent.resolveActivity(getPackageManager()).getPackageName();

            return packageName.equals(currentHome);
        } catch (Exception e) {
            Log.e(TAG, "Error checking default home launcher", e);
            return false;
        }
    }

    /**
     * Check if user is in home environment (not inside an app)
     * Returns true if no app is running in foreground = user in home/passthrough
     */
    private boolean isUserInHomeEnvironment() {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Check running tasks
                java.util.List<android.app.ActivityManager.AppTask> tasks = am.getAppTasks();
                if (tasks == null || tasks.isEmpty()) {
                    // No tasks running = user in home
                    return true;
                }
            }

            // Check which app is in foreground
            String foregroundPackage = getForegroundPackage();

            if (foregroundPackage == null || foregroundPackage.isEmpty()) {
                // No app in foreground = user in home
                return true;
            }

            // If system UI or launcher or nothing = user in home
            if (foregroundPackage.contains("systemui") ||
                    foregroundPackage.contains("launcher") ||
                    foregroundPackage.equals(getPackageName())) {
                return true;
            }

            // Check if it's a system app/overlay - these don't count as "user in app"
            if (foregroundPackage.contains("android") ||
                    foregroundPackage.contains("com.android") ||
                    foregroundPackage.contains("com.oculus") ||
                    foregroundPackage.contains("com.meta")) {
                // System app in foreground = treat as home environment
                return true;
            }

            // A real user app is running in foreground
            return false;

        } catch (Exception e) {
            Log.e(TAG, "Error checking home environment", e);
            // Default to true - assume home environment if we can't tell
            return true;
        }
    }

    /**
     * Get the package name of the app currently in foreground
     */
    private String getForegroundPackage() {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                java.util.List<android.app.ActivityManager.AppTask> tasks = am.getAppTasks();
                if (tasks != null && !tasks.isEmpty()) {
                    android.app.ActivityManager.AppTask task = tasks.get(0);
                    if (task != null) {
                        android.app.ActivityManager.RecentTaskInfo taskInfo = task.getTaskInfo();
                        if (taskInfo != null && taskInfo.topActivity != null) {
                            return taskInfo.topActivity.getPackageName();
                        }
                    }
                }
            } else {
                java.util.List<android.app.ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
                if (tasks != null && !tasks.isEmpty()) {
                    android.content.ComponentName topActivity = tasks.get(0).topActivity;
                    if (topActivity != null) {
                        return topActivity.getPackageName();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting foreground package", e);
        }

        return null;
    }

    /**
     * Check if our launcher is currently visible
     */
    private boolean isLauncherVisible() {
        try {
            String foregroundPackage = getForegroundPackage();
            if (foregroundPackage != null && foregroundPackage.equals(getPackageName())) {
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking if launcher visible", e);
        }

        return false;
    }

    /**
     * FORCE restart the launcher - bring it to foreground NOW
     */
    private void forceRestartLauncher() {
        try {
            Log.i(TAG, "FORCE RESTARTING LAUNCHER NOW");

            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

            // Add HOME category to ensure it opens as home launcher
            intent.addCategory(Intent.CATEGORY_HOME);

            startActivity(intent);

            Log.i(TAG, "✅ Launcher FORCED to foreground");
        } catch (Exception e) {
            Log.e(TAG, "Error force restarting launcher", e);

            // Fallback: try simpler approach
            try {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                Log.i(TAG, "✅ Launcher restarted (fallback method)");
            } catch (Exception e2) {
                Log.e(TAG, "Error in fallback restart", e2);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "AGGRESSIVE VR Shell Monitor Service started - launcher will persist");
        return START_STICKY; // ALWAYS restart service if killed by system
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "VR Shell Monitor Service destroyed - attempting to restart...");

        // Clean up handlers
        if (handler != null && checkRunnable != null) {
            handler.removeCallbacks(checkRunnable);
        }

        // Unregister receivers
        try {
            if (homeReceiver != null) unregisterReceiver(homeReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering home receiver", e);
        }

        try {
            if (screenReceiver != null) unregisterReceiver(screenReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering screen receiver", e);
        }

        try {
            if (packageReceiver != null) unregisterReceiver(packageReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering package receiver", e);
        }

        // Try to restart ourselves
        try {
            Intent restartIntent = new Intent(getApplicationContext(), VRShellMonitorService.class);
            startService(restartIntent);
            Log.i(TAG, "Service restart initiated");
        } catch (Exception e) {
            Log.e(TAG, "Could not restart service", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.i(TAG, "Task removed - service persisting");

        // Restart launcher immediately when task is removed
        handler.postDelayed(() -> {
            if (isUserInHomeEnvironment()) {
                forceRestartLauncher();
            }
        }, 1000);
    }
}