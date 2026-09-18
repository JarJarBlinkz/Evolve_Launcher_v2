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

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean autoStartEnabled = prefs.getBoolean(KEY_AUTO_START, true);

            if (autoStartEnabled) {
                Log.d(TAG, "Auto-starting VR Launcher");

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // Ignore
                }

                Intent launchIntent = new Intent(context, MainActivity.class);
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY);
                }

                try {
                    context.startActivity(launchIntent);
                    Log.d(TAG, "VR Launcher started successfully");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start VR Launcher: " + e.getMessage());
                }

                // Suppress Meta Store on boot if enabled.
                // Uses Runtime.exec with am force-stop.
                boolean suppressStore = prefs.getBoolean("suppress_store", false);
                if (suppressStore) {
                    new Thread(() -> {
                        try { Thread.sleep(10000); } catch (InterruptedException ignored) {}
                        try {
                            Process p = Runtime.getRuntime().exec(
                                    new String[]{"am", "force-stop", "com.oculus.store"});
                            int result = p.waitFor();
                            Log.d(TAG, "Store force-stop result: " + result);
                        } catch (Exception e) {
                            Log.e(TAG, "Store suppression failed: " + e.getMessage());
                        }
                    }).start();
                }

            } else {
                Log.d(TAG, "Auto-start is disabled in settings");
            }
        }
    }
}