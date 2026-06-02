package com.jarjarblinkz.EvolveLauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Map;
import java.util.Set;

/**
 * Manages custom app names for the launcher
 * Stores user-defined names that override the default APK names
 */
public class AppNameManager {
    private static final String TAG = "AppNameManager";
    private static final String PREFS_NAME = "CustomAppNames";

    private final SharedPreferences prefs;

    public AppNameManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Get the custom name for a package, or null if none set
     * @param packageName The package name (e.g., "com.example.game")
     * @return Custom name or null
     */
    public String getCustomName(String packageName) {
        return prefs.getString(packageName, null);
    }

    /**
     * Get the display name - returns custom name if set, otherwise the default
     * @param packageName The package name
     * @param defaultName The default name from APK
     * @return The name to display
     */
    public String getDisplayName(String packageName, String defaultName) {
        String customName = getCustomName(packageName);
        return (customName != null && !customName.isEmpty()) ? customName : defaultName;
    }

    /**
     * Set a custom name for a package
     * @param packageName The package name
     * @param customName The new custom name (empty string or null clears it)
     */
    public void setCustomName(String packageName, String customName) {
        if (customName == null || customName.trim().isEmpty()) {
            // Clear custom name - revert to default
            prefs.edit().remove(packageName).apply();
            Log.i(TAG, "Cleared custom name for: " + packageName);
        } else {
            prefs.edit().putString(packageName, customName.trim()).apply();
            Log.i(TAG, "Set custom name for " + packageName + " = " + customName.trim());
        }
    }

    /**
     * Clear custom name for a package (revert to default)
     */
    public void clearCustomName(String packageName) {
        setCustomName(packageName, null);
    }

    /**
     * Check if a package has a custom name
     */
    public boolean hasCustomName(String packageName) {
        return prefs.contains(packageName);
    }

    /**
     * Get all custom names (for backup/export)
     */
    public Map<String, ?> getAllCustomNames() {
        return prefs.getAll();
    }

    /**
     * Get count of customized apps
     */
    public int getCustomNameCount() {
        return prefs.getAll().size();
    }

    /**
     * Clear ALL custom names
     */
    public void clearAll() {
        prefs.edit().clear().apply();
        Log.i(TAG, "Cleared all custom names");
    }

    /**
     * Export custom names as backup string
     */
    public String exportToString() {
        StringBuilder sb = new StringBuilder();
        Map<String, ?> all = prefs.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Import custom names from backup string
     */
    public void importFromString(String data) {
        if (data == null || data.isEmpty()) return;

        SharedPreferences.Editor editor = prefs.edit();
        String[] lines = data.split("\n");
        for (String line : lines) {
            int idx = line.indexOf('=');
            if (idx > 0) {
                String key = line.substring(0, idx);
                String value = line.substring(idx + 1);
                editor.putString(key, value);
            }
        }
        editor.apply();
        Log.i(TAG, "Imported " + lines.length + " custom names");
    }
}