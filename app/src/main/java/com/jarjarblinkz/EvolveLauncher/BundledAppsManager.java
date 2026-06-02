package com.jarjarblinkz.EvolveLauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages bundled APK files in app/src/main/assets/bundled_apps/
 * Scans assets folder and provides install functionality
 */
public class BundledAppsManager {
    private static final String TAG = "BundledAppsManager";
    private static final String BUNDLED_APPS_FOLDER = "bundled_apps";

    private final Context context;

    public BundledAppsManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Get list of all bundled APKs with metadata
     */
    public List<BundledApp> getBundledApps() {
        List<BundledApp> apps = new ArrayList<>();

        try {
            String[] files = context.getAssets().list(BUNDLED_APPS_FOLDER);
            if (files == null || files.length == 0) {
                Log.i(TAG, "No bundled apps found in assets/" + BUNDLED_APPS_FOLDER);
                return apps;
            }

            for (String filename : files) {
                if (filename.toLowerCase().endsWith(".apk")) {
                    BundledApp app = analyzeApk(filename);
                    if (app != null) {
                        apps.add(app);
                    }
                }
            }

            Log.i(TAG, "Found " + apps.size() + " bundled apps");

        } catch (Exception e) {
            Log.e(TAG, "Failed to list bundled apps", e);
        }

        return apps;
    }

    /**
     * Analyze an APK to get its metadata (name, package, icon, version)
     */
    private BundledApp analyzeApk(String filename) {
        File extractedFile = null;
        try {
            // Extract APK temporarily to read metadata
            extractedFile = extractApk(filename);
            if (extractedFile == null) return null;

            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(extractedFile.getAbsolutePath(), 0);

            if (info == null) {
                Log.w(TAG, "Could not parse APK: " + filename);
                return null;
            }

            // Set the source dir so we can load icon/label
            ApplicationInfo appInfo = info.applicationInfo;
            appInfo.sourceDir = extractedFile.getAbsolutePath();
            appInfo.publicSourceDir = extractedFile.getAbsolutePath();

            BundledApp app = new BundledApp();
            app.filename = filename;
            app.packageName = info.packageName;
            app.versionName = info.versionName != null ? info.versionName : "Unknown";

            try {
                app.label = pm.getApplicationLabel(appInfo).toString();
            } catch (Exception e) {
                app.label = filename.replace(".apk", "");
            }

            try {
                app.icon = pm.getApplicationIcon(appInfo);
            } catch (Exception e) {
                app.icon = null;
            }

            // Check if already installed
            app.isInstalled = isPackageInstalled(app.packageName);
            if (app.isInstalled) {
                try {
                    PackageInfo installed = pm.getPackageInfo(app.packageName, 0);
                    app.installedVersion = installed.versionName;
                } catch (Exception e) {
                    app.installedVersion = "Unknown";
                }
            }

            return app;

        } catch (Exception e) {
            Log.e(TAG, "Failed to analyze APK: " + filename, e);
            return null;
        }
    }

    /**
     * Extract APK from assets to cache directory
     */
    public File extractApk(String filename) {
        try {
            File outputFile = new File(context.getCacheDir(), "bundled_" + filename);

            // Delete old extraction if exists
            if (outputFile.exists()) {
                outputFile.delete();
            }

            // Extract from assets
            try (InputStream in = context.getAssets().open(BUNDLED_APPS_FOLDER + "/" + filename);
                 OutputStream out = new FileOutputStream(outputFile)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
            }

            return outputFile;

        } catch (Exception e) {
            Log.e(TAG, "Failed to extract: " + filename, e);
            return null;
        }
    }

    /**
     * Install a bundled APK
     */
    public boolean installApk(String filename) {
        try {
            File apkFile = extractApk(filename);
            if (apkFile == null || !apkFile.exists()) {
                Log.e(TAG, "Failed to extract APK for installation");
                return false;
            }

            // Build install intent
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            Uri apkUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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
            context.startActivity(intent);

            Log.i(TAG, "Launched installer for: " + filename);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to install: " + filename, e);
            return false;
        }
    }

    /**
     * Check if a package is installed
     */
    private boolean isPackageInstalled(String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Launch an installed app
     */
    public boolean launchApp(String packageName) {
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return true;
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch: " + packageName, e);
            return false;
        }
    }

    /**
     * Data class representing a bundled app
     */
    public static class BundledApp {
        public String filename;
        public String label;
        public String packageName;
        public String versionName;
        public Drawable icon;
        public boolean isInstalled;
        public String installedVersion;
    }
}