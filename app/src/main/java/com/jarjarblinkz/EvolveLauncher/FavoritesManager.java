package com.jarjarblinkz.EvolveLauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Manages user's favorite apps - persistence, ordering, add/remove operations.
 * Singleton pattern: get instance via FavoritesManager.getInstance(context)
 */
public class FavoritesManager {
    private static final String TAG = "FavoritesManager";
    private static final String PREFS_NAME = "EvolveFavorites";
    private static final String KEY_FAVORITES = "favorites_list";
    private static final String SEPARATOR = "|||";

    private static FavoritesManager instance;
    private final SharedPreferences prefs;
    private final List<String> favorites;
    private final List<FavoritesChangeListener> listeners = new ArrayList<>();

    public interface FavoritesChangeListener {
        void onFavoritesChanged();
    }

    private FavoritesManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        favorites = loadFavorites();
    }

    public static synchronized FavoritesManager getInstance(Context context) {
        if (instance == null) {
            instance = new FavoritesManager(context);
        }
        return instance;
    }

    /**
     * Load favorites from SharedPreferences
     */
    private List<String> loadFavorites() {
        String stored = prefs.getString(KEY_FAVORITES, "");
        if (stored.isEmpty()) {
            return new ArrayList<>();
        }
        // Use LinkedHashSet to preserve order while removing duplicates
        LinkedHashSet<String> unique = new LinkedHashSet<>(
                Arrays.asList(stored.split(java.util.regex.Pattern.quote(SEPARATOR))));
        return new ArrayList<>(unique);
    }

    /**
     * Save favorites to SharedPreferences
     */
    private void saveFavorites() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < favorites.size(); i++) {
            if (i > 0) sb.append(SEPARATOR);
            sb.append(favorites.get(i));
        }
        prefs.edit().putString(KEY_FAVORITES, sb.toString()).apply();
        notifyListeners();
        Log.i(TAG, "Saved " + favorites.size() + " favorites");
    }

    /**
     * Add an app to favorites (appended to end)
     */
    public boolean addFavorite(String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        if (favorites.contains(packageName)) return false;
        favorites.add(packageName);
        saveFavorites();
        Log.i(TAG, "Added favorite: " + packageName);
        return true;
    }

    /**
     * Remove an app from favorites
     */
    public boolean removeFavorite(String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        boolean removed = favorites.remove(packageName);
        if (removed) {
            saveFavorites();
            Log.i(TAG, "Removed favorite: " + packageName);
        }
        return removed;
    }

    /**
     * Toggle favorite state - returns new state
     */
    public boolean toggleFavorite(String packageName) {
        if (isFavorite(packageName)) {
            removeFavorite(packageName);
            return false;
        } else {
            addFavorite(packageName);
            return true;
        }
    }

    /**
     * Check if a package is favorited
     */
    public boolean isFavorite(String packageName) {
        return packageName != null && favorites.contains(packageName);
    }

    /**
     * Get list of favorite package names (in user-defined order)
     */
    public List<String> getFavorites() {
        return new ArrayList<>(favorites);
    }

    /**
     * Get count of favorites
     */
    public int getFavoritesCount() {
        return favorites.size();
    }

    /**
     * Move a favorite from one position to another (for drag-to-reorder).
     * Returns true on success.
     */
    public boolean moveFavorite(int fromPosition, int toPosition) {
        if (fromPosition < 0 || fromPosition >= favorites.size()) return false;
        if (toPosition < 0 || toPosition >= favorites.size()) return false;
        if (fromPosition == toPosition) return false;

        Collections.swap(favorites, fromPosition, toPosition);
        saveFavorites();
        return true;
    }

    /**
     * Reorder favorites - more efficient than swap for drags across many positions.
     * Used by ItemTouchHelper for fluid drag-and-drop.
     */
    public boolean reorderFavorite(int fromPosition, int toPosition) {
        if (fromPosition < 0 || fromPosition >= favorites.size()) return false;
        if (toPosition < 0 || toPosition >= favorites.size()) return false;
        if (fromPosition == toPosition) return false;

        String item = favorites.remove(fromPosition);
        favorites.add(toPosition, item);
        // Don't save on each step during drag - caller should call commitReorder() at end
        return true;
    }

    /**
     * Commit reorder changes - call after drag finishes
     */
    public void commitReorder() {
        saveFavorites();
    }

    /**
     * Clear all favorites
     */
    public void clearAll() {
        favorites.clear();
        saveFavorites();
    }

    /**
     * Remove favorites that are no longer installed
     */
    public void cleanupUninstalled(List<String> installedPackages) {
        boolean changed = favorites.retainAll(installedPackages);
        if (changed) {
            saveFavorites();
            Log.i(TAG, "Cleaned up uninstalled favorites");
        }
    }

    public void addListener(FavoritesChangeListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(FavoritesChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (FavoritesChangeListener listener : listeners) {
            try {
                listener.onFavoritesChanged();
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener", e);
            }
        }
    }
}
