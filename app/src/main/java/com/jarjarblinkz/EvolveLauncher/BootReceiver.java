package com.jarjarblinkz.EvolveLauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";
    private static final String PREFS_NAME = "VRLPrefs";
    private static final String KEY_AUTO_START = "auto_start";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Boot receiver triggered: " + action);

        // Only handle BOOT_COMPLETED - remove QUICKBOOT_POWERON
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {

            // Check if auto-start is enabled in preferences
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean autoStartEnabled = prefs.getBoolean(KEY_AUTO_START, true);

            if (autoStartEnabled) {
                Log.d(TAG, "Auto-starting VR Launcher");

                // Small delay to ensure system is fully ready
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // Ignore
                }

                // Launch MainActivity
                Intent launchIntent = new Intent(context, MainActivity.class);
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                // Required for Android 10+ to show activity from background
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY);
                }

                try {
                    context.startActivity(launchIntent);
                    Log.d(TAG, "VR Launcher started successfully");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start VR Launcher: " + e.getMessage());
                }
            } else {
                Log.d(TAG, "Auto-start is disabled in settings");
            }
        }
    }
}