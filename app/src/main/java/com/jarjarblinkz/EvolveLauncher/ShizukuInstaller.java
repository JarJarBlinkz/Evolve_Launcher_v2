package com.jarjarblinkz.EvolveLauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Manages Shizuku APK installation from bundled assets
 * Allows users to install Shizuku without external downloads
 */
public class ShizukuInstaller {
    private static final String TAG = "ShizukuInstaller";
    private static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";
    private static final String SHIZUKU_APK_ASSET = "shizuku.apk";
    private static final String SHIZUKU_APK_FILENAME = "shizuku-bundled.apk";

    private final Context context;

    public ShizukuInstaller(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Check if Shizuku is already installed
     */
    public boolean isShizukuInstalled() {
        try {
            context.getPackageManager().getPackageInfo(SHIZUKU_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Get installed Shizuku version, or null if not installed
     */
    public String getInstalledVersion() {
        try {
            return context.getPackageManager()
                    .getPackageInfo(SHIZUKU_PACKAGE, 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /**
     * Check if Shizuku APK is bundled in assets
     */
    public boolean isShizukuApkBundled() {
        try {
            String[] assets = context.getAssets().list("");
            if (assets != null) {
                for (String asset : assets) {
                    if (asset.equals(SHIZUKU_APK_ASSET)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to check bundled assets", e);
            return false;
        }
    }

    /**
     * Extract Shizuku APK from assets to internal storage
     * @return File pointer to extracted APK, or null on failure
     */
    public File extractShizukuApk() {
        if (!isShizukuApkBundled()) {
            Log.e(TAG, "Shizuku APK not bundled in assets!");
            return null;
        }

        try {
            File outputFile = new File(context.getCacheDir(), SHIZUKU_APK_FILENAME);

            // Delete old extraction if exists
            if (outputFile.exists()) {
                outputFile.delete();
            }

            // Extract from assets
            try (InputStream in = context.getAssets().open(SHIZUKU_APK_ASSET);
                 OutputStream out = new FileOutputStream(outputFile)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
            }

            Log.i(TAG, "Shizuku APK extracted: " + outputFile.getAbsolutePath() +
                    " (" + outputFile.length() + " bytes)");
            return outputFile;

        } catch (Exception e) {
            Log.e(TAG, "Failed to extract Shizuku APK", e);
            return null;
        }
    }

    /**
     * Install Shizuku APK from bundled assets
     * Shows system installer UI for user confirmation
     */
    public boolean installShizuku() {
        try {
            File apkFile = extractShizukuApk();
            if (apkFile == null || !apkFile.exists()) {
                Log.e(TAG, "Failed to extract APK for installation");
                return false;
            }

            // Build install intent
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            Uri apkUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Use FileProvider for Android 7.0+
                apkUri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".fileprovider",
                        apkFile
                );
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                apkUri = Uri.fromFile(apkFile);
            }

            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");

            // Launch installer
            context.startActivity(intent);

            Log.i(TAG, "Launched Shizuku APK installer");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to install Shizuku", e);
            return false;
        }
    }

    /**
     * Check if Shizuku is running (binder available)
     */
    public boolean isShizukuRunning() {
        try {
            return rikka.shizuku.Shizuku.pingBinder();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Launch Shizuku Manager app (if installed)
     */
    public boolean launchShizukuApp() {
        try {
            Intent launchIntent = context.getPackageManager()
                    .getLaunchIntentForPackage(SHIZUKU_PACKAGE);

            if (launchIntent != null) {
                launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launchIntent);
                return true;
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch Shizuku app", e);
            return false;
        }
    }

    /**
     * Get installation status as enum
     */
    public InstallStatus getStatus() {
        if (!isShizukuApkBundled()) {
            return InstallStatus.NOT_BUNDLED;
        }
        if (!isShizukuInstalled()) {
            return InstallStatus.NOT_INSTALLED;
        }
        if (!isShizukuRunning()) {
            return InstallStatus.INSTALLED_NOT_RUNNING;
        }
        return InstallStatus.RUNNING;
    }

    public enum InstallStatus {
        NOT_BUNDLED,           // APK not bundled in assets
        NOT_INSTALLED,         // Shizuku app not installed
        INSTALLED_NOT_RUNNING, // Installed but server not started
        RUNNING                // Fully ready to use
    }
}