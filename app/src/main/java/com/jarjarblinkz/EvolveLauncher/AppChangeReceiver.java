package com.jarjarblinkz.EvolveLauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class AppChangeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action == null) return;

        // Check for app install/uninstall/update events
        if (action.equals(Intent.ACTION_PACKAGE_ADDED) ||
                action.equals(Intent.ACTION_PACKAGE_REMOVED) ||
                action.equals(Intent.ACTION_PACKAGE_REPLACED)) {

            // Skip if it's a replacement (update) that doesn't add the app
            boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);

            // Get the package name that was changed
            String packageName = intent.getData() != null ? intent.getData().getSchemeSpecificPart() : null;

            if (packageName == null) return;

            // Skip our own package to avoid unnecessary refreshes
            if (packageName.equals(context.getPackageName())) {
                return;
            }

            // ALWAYS send broadcast - MainActivity will receive it even if in foreground
            Intent refreshIntent = new Intent("APP_CHANGE_DETECTED");
            refreshIntent.putExtra("package_name", packageName);
            refreshIntent.putExtra("action", action);
            refreshIntent.putExtra("replacing", replacing);
            context.sendBroadcast(refreshIntent);

            // Also try to refresh MainActivity directly if it's available
            if (MainActivity.instance != null) {
                MainActivity.instance.runOnUiThread(() -> {
                    // Small delay to ensure PackageManager has updated
                    new android.os.Handler().postDelayed(() -> {
                        MainActivity.instance.refreshAll();

                        if (action.equals(Intent.ACTION_PACKAGE_ADDED) && !replacing) {
                            Toast.makeText(context, "New app detected - Launcher updated", Toast.LENGTH_SHORT).show();
                        }
                    }, 1500);
                });
            }
        }
    }
}