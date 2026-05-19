package com.jarjarblinkz.EvolveLauncher;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateManager {

    private static final String TAG = "UpdateManager";
    private static final String PREFS_NAME = "VRLPrefs";
    private static final String KEY_AUTO_UPDATE_CHECK = "auto_update_check";
    private static final String KEY_LAST_UPDATE_CHECK = "last_update_check";
    private static final String KEY_UPDATE_FREQUENCY = "update_frequency"; // hours

    // IMPORTANT: Replace with your GitHub username and repository name
    private static final String GITHUB_API_URL = "https://api.github.com/repos/YOUR_USERNAME/YOUR_REPO/releases/latest";

    private final Context context;
    private final SharedPreferences prefs;
    private final ExecutorService executor;
    private long downloadId = -1;
    private BroadcastReceiver downloadReceiver;

    public UpdateManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Check if auto-update is enabled
     */
    public boolean isAutoUpdateEnabled() {
        return prefs.getBoolean(KEY_AUTO_UPDATE_CHECK, true);
    }

    /**
     * Enable or disable auto-update
     */
    public void setAutoUpdateEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_UPDATE_CHECK, enabled).apply();
    }

    /**
     * Get update frequency in hours
     */
    public int getUpdateFrequency() {
        return prefs.getInt(KEY_UPDATE_FREQUENCY, 24); // Default: check once per day
    }

    /**
     * Set update frequency in hours
     */
    public void setUpdateFrequency(int hours) {
        prefs.edit().putInt(KEY_UPDATE_FREQUENCY, hours).apply();
    }

    /**
     * Check if it's time to check for updates based on frequency setting
     */
    public boolean shouldCheckForUpdates() {
        if (!isAutoUpdateEnabled()) {
            return false;
        }

        long lastCheck = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0);
        long currentTime = System.currentTimeMillis();
        long frequencyMillis = getUpdateFrequency() * 60 * 60 * 1000L;

        return (currentTime - lastCheck) > frequencyMillis;
    }

    /**
     * Mark that we've checked for updates
     */
    private void markUpdateChecked() {
        prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, System.currentTimeMillis()).apply();
    }

    /**
     * Get current app version name
     */
    public String getCurrentVersion() {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Could not get version name", e);
            return "Unknown";
        }
    }

    /**
     * Get current app version code
     */
    public int getCurrentVersionCode() {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return (int) pInfo.getLongVersionCode();
            } else {
                return pInfo.versionCode;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Could not get version code", e);
            return 0;
        }
    }

    /**
     * Check for updates in the background
     * @param showToastIfNoUpdate Show a toast message even if there's no update
     */
    public void checkForUpdates(boolean showToastIfNoUpdate) {
        executor.execute(() -> {
            try {
                markUpdateChecked();

                URL url = new URL(GITHUB_API_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                // GitHub API requires User-Agent header
                connection.setRequestProperty("User-Agent", "EvolveLauncher-UpdateChecker");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject release = new JSONObject(response.toString());
                    String latestVersion = release.getString("tag_name").replaceAll("[^0-9.]", "");
                    String currentVersion = getCurrentVersion().replaceAll("[^0-9.]", "");

                    Log.d(TAG, "Current version: " + currentVersion + ", Latest version: " + latestVersion);

                    // Find the APK asset in the release
                    JSONArray assets = release.getJSONArray("assets");
                    String downloadUrl = null;
                    String fileName = null;

                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String name = asset.getString("name");
                        if (name.endsWith(".apk")) {
                            downloadUrl = asset.getString("browser_download_url");
                            fileName = name;
                            break;
                        }
                    }

                    if (downloadUrl == null) {
                        showErrorOnMainThread("No APK found in latest release");
                        return;
                    }

                    // Compare versions
                    if (compareVersions(latestVersion, currentVersion) > 0) {
                        // New version available
                        String releaseNotes = release.optString("body", "No release notes available");
                        String finalDownloadUrl = downloadUrl;
                        String finalFileName = fileName;
                        String finalLatestVersion = latestVersion;

                        ((Activity) context).runOnUiThread(() -> {
                            showUpdateDialog(finalLatestVersion, currentVersion, releaseNotes, finalDownloadUrl, finalFileName);
                        });
                    } else {
                        // No update available
                        if (showToastIfNoUpdate) {
                            ((Activity) context).runOnUiThread(() -> {
                                Toast.makeText(context, "You're already on the latest version!", Toast.LENGTH_SHORT).show();
                            });
                        }
                        Log.d(TAG, "No update available");
                    }
                } else {
                    showErrorOnMainThread("Failed to check for updates: HTTP " + responseCode);
                }

                connection.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "Error checking for updates", e);
                showErrorOnMainThread("Error checking for updates: " + e.getMessage());
            }
        });
    }

    /**
     * Compare two version strings
     * @return positive if v1 > v2, negative if v1 < v2, 0 if equal
     */
    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");

        int maxLength = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < maxLength; i++) {
            int num1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int num2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;

            if (num1 != num2) {
                return num1 - num2;
            }
        }

        return 0;
    }

    /**
     * Parse a version part, extracting only the numeric portion
     */
    private int parseVersionPart(String part) {
        try {
            // Extract only digits from the start of the string
            StringBuilder numStr = new StringBuilder();
            for (char c : part.toCharArray()) {
                if (Character.isDigit(c)) {
                    numStr.append(c);
                } else {
                    break;
                }
            }
            return numStr.length() > 0 ? Integer.parseInt(numStr.toString()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Show update dialog with release information
     */
    private void showUpdateDialog(String newVersion, String currentVersion, String releaseNotes, String downloadUrl, String fileName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Update Available!");

        String message = "A new version is available!\n\n" +
                "Current Version: " + currentVersion + "\n" +
                "New Version: " + newVersion + "\n\n" +
                "Release Notes:\n" + releaseNotes;

        builder.setMessage(message);
        builder.setCancelable(true);

        builder.setPositiveButton("Download & Install", (dialog, which) -> {
            // Check if we can install from unknown sources
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.getPackageManager().canRequestPackageInstalls()) {
                    // Need to request permission
                    requestInstallPermission();
                    return;
                }
            }
            downloadAndInstallUpdate(downloadUrl, fileName);
        });

        builder.setNegativeButton("Later", (dialog, which) -> {
            dialog.dismiss();
        });

        builder.setNeutralButton("Skip This Version", (dialog, which) -> {
            // You could save this version to skip it in the future
            dialog.dismiss();
        });

        builder.show();
    }

    /**
     * Request permission to install packages from unknown sources (Android 8+)
     */
    private void requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.getPackageManager().canRequestPackageInstalls()) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle("Permission Required");
                builder.setMessage("To install updates, you need to allow installation from unknown sources.");
                builder.setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    intent.setData(Uri.parse("package:" + context.getPackageName()));
                    context.startActivity(intent);
                });
                builder.setNegativeButton("Cancel", null);
                builder.show();
            }
        }
    }

    /**
     * Download and install the update
     */
    private void downloadAndInstallUpdate(String downloadUrl, String fileName) {
        try {
            // Create downloads directory if it doesn't exist
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File outputFile = new File(downloadsDir, fileName);

            // Delete old file if exists
            if (outputFile.exists()) {
                outputFile.delete();
            }

            // Start download using DownloadManager
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
            request.setTitle("Evolve Launcher Update");
            request.setDescription("Downloading " + fileName);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            request.setMimeType("application/vnd.android.package-archive");

            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            downloadId = downloadManager.enqueue(request);

            Toast.makeText(context, "Downloading update...", Toast.LENGTH_SHORT).show();

            // Register receiver to handle download completion
            registerDownloadReceiver(fileName);

        } catch (Exception e) {
            Log.e(TAG, "Error downloading update", e);
            Toast.makeText(context, "Error downloading update: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Register broadcast receiver for download completion
     */
    private void registerDownloadReceiver(String fileName) {
        if (downloadReceiver != null) {
            try {
                context.unregisterReceiver(downloadReceiver);
            } catch (Exception e) {
                // Ignore if not registered
            }
        }

        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);

                if (id == downloadId) {
                    // Download completed
                    DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

                    // Check download status
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(id);
                    Cursor cursor = downloadManager.query(query);

                    if (cursor.moveToFirst()) {
                        int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        int status = cursor.getInt(statusIndex);

                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            // Install the APK
                            installApk(fileName);
                        } else {
                            Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                    cursor.close();

                    // Unregister receiver
                    try {
                        context.unregisterReceiver(this);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        context.registerReceiver(downloadReceiver, filter);
    }

    /**
     * Install the downloaded APK
     */
    private void installApk(String fileName) {
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File apkFile = new File(downloadsDir, fileName);

            if (!apkFile.exists()) {
                Toast.makeText(context, "APK file not found", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Use FileProvider for Android 7+
                Uri apkUri = FileProvider.getUriForFile(context,
                        context.getPackageName() + ".provider", apkFile);
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            Toast.makeText(context, "Installing update...", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e(TAG, "Error installing APK", e);
            Toast.makeText(context, "Error installing update: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Show error message on main thread
     */
    private void showErrorOnMainThread(String message) {
        if (context instanceof Activity) {
            ((Activity) context).runOnUiThread(() -> {
                Log.e(TAG, message);
                // Optionally show toast for errors
                // Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            });
        }
    }

    /**
     * Clean up resources
     */
    public void cleanup() {
        if (downloadReceiver != null) {
            try {
                context.unregisterReceiver(downloadReceiver);
            } catch (Exception e) {
                // Ignore if not registered
            }
            downloadReceiver = null;
        }

        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}