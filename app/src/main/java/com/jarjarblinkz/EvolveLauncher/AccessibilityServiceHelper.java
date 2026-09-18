package com.jarjarblinkz.EvolveLauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

/**
 * Helper for checking whether EvolveAccessibilityService is enabled and
 * for opening Quest's hidden Android accessibility settings page so the
 * user can toggle it on/off.
 *
 * Apps can't programmatically enable their own accessibility services
 * (Android security restriction), so this just gives users a one-tap
 * shortcut to the right settings page.
 *
 * On Quest, the standard Settings.ACTION_ACCESSIBILITY_SETTINGS intent
 * gets intercepted by Horizon OS and opens Meta's stripped-down settings
 * UI which doesn't expose installed accessibility services. To work
 * around that we launch the native Android accessibility activity by
 * its explicit component name - Meta only intercepts action-based intent
 * resolution, not direct component launches.
 */
public class AccessibilityServiceHelper {

    private static final String TAG = "AccessibilityHelper";
    private static final String SETTINGS_PACKAGE = "com.android.settings";

    /**
     * Returns true if EvolveAccessibilityService is currently enabled in
     * the system's accessibility settings.
     */
    public static boolean isEnabled(Context context) {
        ComponentName expected = new ComponentName(
                context.getPackageName(),
                EvolveAccessibilityService.class.getName());

        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

        if (TextUtils.isEmpty(enabledServices)) return false;

        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        while (splitter.hasNext()) {
            ComponentName enabled = ComponentName.unflattenFromString(splitter.next());
            if (enabled != null && enabled.equals(expected)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Open Android's accessibility settings using the same component-launch
     * approach as the launcher's other native-settings shortcuts. Tries
     * methods in order of preference:
     *
     *   1. Direct ComponentName to AccessibilitySettingsActivity - opens
     *      straight to the accessibility services list, bypassing Meta's
     *      Settings UI interception.
     *   2. com.android.settings package launch intent + TASK_ON_HOME flag -
     *      opens settings homepage, user navigates to Accessibility from
     *      there. Same approach as the launcher's existing native-settings
     *      button.
     *   3. Application details fallback - last resort if Android Settings
     *      doesn't expose a launch intent at all.
     */
    public static void openSettings(Context context) {
        // Method 1: Direct ComponentName launch. Meta intercepts action-based
        // intents like Settings.ACTION_ACCESSIBILITY_SETTINGS, but a direct
        // component launch tells Android exactly what activity to start - no
        // action resolution for Meta to hijack.
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(
                    SETTINGS_PACKAGE,
                    "com.android.settings.Settings$AccessibilitySettingsActivity"));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
            context.startActivity(intent);
            return;
        } catch (Exception e) {
            Log.w(TAG, "Direct accessibility activity launch failed, falling back", e);
        }

        // Method 2: Open settings homepage. User navigates to Accessibility
        // manually from there. Same approach the launcher's native-settings
        // button uses (gotosettings approach).
        try {
            PackageManager pm = context.getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(SETTINGS_PACKAGE);
            if (intent != null) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
                context.startActivity(intent);
                return;
            }
        } catch (Exception e) {
            Log.w(TAG, "Settings homepage launch failed", e);
        }

        // Method 3: Application details fallback - last resort
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + SETTINGS_PACKAGE));
            intent.setPackage(SETTINGS_PACKAGE);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "All settings launch methods failed", e);
        }
    }
}