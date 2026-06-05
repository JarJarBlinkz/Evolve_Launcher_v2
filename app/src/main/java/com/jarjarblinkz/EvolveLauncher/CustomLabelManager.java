package com.jarjarblinkz.EvolveLauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Manages user-customized app labels.
 * Stores custom names per package name in SharedPreferences.
 * Original labels are preserved - this just provides display overrides.
 */
public class CustomLabelManager {
    private static final String TAG = "CustomLabelManager";
    private static final String PREFS_NAME = "EvolveCustomLabels";

    private static CustomLabelManager instance;
    private final SharedPreferences prefs;

    private CustomLabelManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized CustomLabelManager getInstance(Context context) {
        if (instance == null) {
            instance = new CustomLabelManager(context);
        }
        return instance;
    }

    /**
     * Get the custom label for a package, or null if none set.
     */
    public String getCustomLabel(String packageName) {
        if (packageName == null) return null;
        return prefs.getString(packageName, null);
    }

    /**
     * Get the effective display label - custom name if set, otherwise the original.
     */
    public String getDisplayLabel(String packageName, String originalLabel) {
        String custom = getCustomLabel(packageName);
        return (custom != null && !custom.isEmpty()) ? custom : originalLabel;
    }

    /**
     * Set a custom label for a package.
     * Pass null or empty string to remove the custom label (reverts to original).
     */
    public void setCustomLabel(String packageName, String label) {
        if (packageName == null) return;
        if (label == null || label.trim().isEmpty()) {
            prefs.edit().remove(packageName).apply();
            Log.i(TAG, "Removed custom label for: " + packageName);
        } else {
            prefs.edit().putString(packageName, label.trim()).apply();
            Log.i(TAG, "Set custom label for " + packageName + " = " + label.trim());
        }
    }

    /**
     * Check if a package has a custom label.
     */
    public boolean hasCustomLabel(String packageName) {
        return getCustomLabel(packageName) != null;
    }

    /**
     * Clear all custom labels (reset everything to original names).
     */
    public void clearAll() {
        prefs.edit().clear().apply();
    }
}