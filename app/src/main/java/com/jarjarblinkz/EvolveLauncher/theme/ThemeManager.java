package com.jarjarblinkz.EvolveLauncher.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.content.res.ColorStateList;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages the current theme and provides runtime drawable generation
 * Singleton - access via ThemeManager.getInstance(context)
 */
public class ThemeManager {
    private static final String TAG = "ThemeManager";
    private static final String PREFS_NAME = "ThemeManager";
    private static final String KEY_CURRENT_THEME_ID = "current_theme_id";
    private static final String KEY_CUSTOM_THEMES = "custom_themes";

    private static ThemeManager instance;

    private final Context context;
    private final SharedPreferences prefs;
    private Theme currentTheme;
    private final List<ThemeChangeListener> listeners = new ArrayList<>();

    public interface ThemeChangeListener {
        void onThemeChanged(Theme newTheme);
    }

    private ThemeManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadCurrentTheme();
    }

    public static ThemeManager getInstance(Context context) {
        if (instance == null) {
            instance = new ThemeManager(context);
        }
        return instance;
    }

    /**
     * Get currently active theme
     */
    public Theme getCurrentTheme() {
        if (currentTheme == null) {
            currentTheme = ThemePresets.createDefault(); // Default
        }
        return currentTheme;
    }

    /**
     * Set active theme by ID
     */
    public void setActiveTheme(String themeId) {
        Theme theme = findThemeById(themeId);
        if (theme != null) {
            currentTheme = theme;
            prefs.edit().putString(KEY_CURRENT_THEME_ID, themeId).apply();
            notifyThemeChanged();
            Log.i(TAG, "Theme changed to: " + theme.name);
        }
    }

    /**
     * Get all available themes (built-in + custom)
     */
    public List<Theme> getAllThemes() {
        List<Theme> all = new ArrayList<>();
        all.addAll(ThemePresets.getBuiltInThemes());
        all.addAll(getCustomThemes());
        return all;
    }

    /**
     * Get only custom themes
     */
    public List<Theme> getCustomThemes() {
        List<Theme> themes = new ArrayList<>();
        Set<String> serialized = prefs.getStringSet(KEY_CUSTOM_THEMES, new HashSet<>());
        for (String data : serialized) {
            Theme theme = Theme.deserialize(data);
            if (theme != null) themes.add(theme);
        }
        return themes;
    }

    /**
     * Save a custom theme
     */
    public void saveCustomTheme(Theme theme) {
        Set<String> serialized = new HashSet<>(
                prefs.getStringSet(KEY_CUSTOM_THEMES, new HashSet<>())
        );

        // Remove existing theme with same ID
        serialized.removeIf(s -> {
            Theme existing = Theme.deserialize(s);
            return existing != null && existing.id.equals(theme.id);
        });

        // Add new/updated theme
        serialized.add(theme.serialize());
        prefs.edit().putStringSet(KEY_CUSTOM_THEMES, serialized).apply();
        Log.i(TAG, "Custom theme saved: " + theme.name);
    }

    /**
     * Delete a custom theme (built-in themes cannot be deleted)
     */
    public boolean deleteCustomTheme(String themeId) {
        Set<String> serialized = new HashSet<>(
                prefs.getStringSet(KEY_CUSTOM_THEMES, new HashSet<>())
        );

        boolean removed = serialized.removeIf(s -> {
            Theme existing = Theme.deserialize(s);
            return existing != null && existing.id.equals(themeId) && !existing.isBuiltIn;
        });

        if (removed) {
            prefs.edit().putStringSet(KEY_CUSTOM_THEMES, serialized).apply();

            // If deleted theme was active, switch to default
            if (currentTheme != null && currentTheme.id.equals(themeId)) {
                setActiveTheme("default");
            }
        }
        return removed;
    }

    /**
     * Find theme by ID
     */
    public Theme findThemeById(String themeId) {
        for (Theme t : getAllThemes()) {
            if (t.id.equals(themeId)) return t;
        }
        return null;
    }

    private void loadCurrentTheme() {
        String themeId = prefs.getString(KEY_CURRENT_THEME_ID, "default");
        currentTheme = findThemeById(themeId);
        if (currentTheme == null) {
            currentTheme = ThemePresets.createDefault();
        }
    }

    // ============================================
    // LISTENERS
    // ============================================

    public void addThemeChangeListener(ThemeChangeListener listener) {
        listeners.add(listener);
    }

    public void removeThemeChangeListener(ThemeChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyThemeChanged() {
        for (ThemeChangeListener listener : listeners) {
            listener.onThemeChanged(currentTheme);
        }
    }

    // ============================================
    // DRAWABLE GENERATORS
    // Generate drawables at runtime from current theme
    // ============================================

    /**
     * Create button background drawable (normal/pressed states)
     */
    public StateListDrawable createButtonDrawable() {
        Theme t = getCurrentTheme();
        StateListDrawable states = new StateListDrawable();

        // Pressed state - brighter
        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.RECTANGLE);
        pressed.setCornerRadius(dpToPx(t.buttonCornerRadius));
        pressed.setColor(adjustAlpha(t.accentPrimary, 80));
        if (t.borderWidth > 0) {
            pressed.setStroke(dpToPx(t.borderWidth), t.accentPrimary);
        }

        // Normal state
        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.RECTANGLE);
        normal.setCornerRadius(dpToPx(t.buttonCornerRadius));
        normal.setColor(t.bgSecondary);
        if (t.borderWidth > 0) {
            normal.setStroke(dpToPx(t.borderWidth), t.borderAccent);
        }

        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{}, normal);
        return states;
    }

    /**
     * Create accent button drawable (more vibrant)
     */
    public StateListDrawable createAccentButtonDrawable() {
        Theme t = getCurrentTheme();
        StateListDrawable states = new StateListDrawable();

        // Pressed
        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.RECTANGLE);
        pressed.setCornerRadius(dpToPx(t.buttonCornerRadius));
        pressed.setColor(t.accentPrimary);
        if (t.borderWidth > 0) {
            pressed.setStroke(dpToPx(t.borderWidth), t.textPrimary);
        }

        // Normal
        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.RECTANGLE);
        normal.setCornerRadius(dpToPx(t.buttonCornerRadius));
        normal.setColor(adjustAlpha(t.accentPrimary, 200));
        if (t.borderWidth > 0) {
            normal.setStroke(dpToPx(t.borderWidth), t.accentPrimary);
        }

        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{}, normal);
        return states;
    }

    /**
     * Create card/section background
     */
    public GradientDrawable createCardDrawable() {
        Theme t = getCurrentTheme();
        GradientDrawable card = new GradientDrawable();
        card.setShape(GradientDrawable.RECTANGLE);
        card.setCornerRadius(dpToPx(t.cardCornerRadius));
        card.setColor(t.bgSecondary);
        if (t.borderWidth > 0) {
            card.setStroke(dpToPx(t.borderWidth), t.borderPrimary);
        }
        return card;
    }

    /**
     * Create dialog/window background
     */
    public GradientDrawable createDialogDrawable() {
        Theme t = getCurrentTheme();
        GradientDrawable dialog = new GradientDrawable();
        dialog.setShape(GradientDrawable.RECTANGLE);
        dialog.setCornerRadius(dpToPx(t.dialogCornerRadius));
        dialog.setColor(t.bgPrimary);
        if (t.borderWidth > 0) {
            dialog.setStroke(dpToPx(t.borderWidth), t.borderAccent);
        }
        return dialog;
    }

    /**
     * Create chip/pill drawable
     */
    public StateListDrawable createChipDrawable() {
        Theme t = getCurrentTheme();
        StateListDrawable states = new StateListDrawable();

        // Selected (active)
        GradientDrawable selected = new GradientDrawable();
        selected.setShape(GradientDrawable.RECTANGLE);
        selected.setCornerRadius(dpToPx(20));
        selected.setColor(t.accentPrimary);
        if (t.borderWidth > 0) {
            selected.setStroke(dpToPx(t.borderWidth), t.textPrimary);
        }

        // Normal
        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.RECTANGLE);
        normal.setCornerRadius(dpToPx(20));
        normal.setColor(t.bgSecondary);
        if (t.borderWidth > 0) {
            normal.setStroke(dpToPx(t.borderWidth), t.borderPrimary);
        }

        states.addState(new int[]{android.R.attr.state_selected}, selected);
        states.addState(new int[]{android.R.attr.state_activated}, selected);
        states.addState(new int[]{}, normal);
        return states;
    }

    /**
     * Create edit text background
     */
    public StateListDrawable createEditTextDrawable() {
        Theme t = getCurrentTheme();
        StateListDrawable states = new StateListDrawable();

        // Focused
        GradientDrawable focused = new GradientDrawable();
        focused.setShape(GradientDrawable.RECTANGLE);
        focused.setCornerRadius(dpToPx(10));
        focused.setColor(t.bgSecondary);
        focused.setStroke(dpToPx(Math.max(t.borderWidth, 1)), t.accentPrimary);

        // Normal
        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.RECTANGLE);
        normal.setCornerRadius(dpToPx(10));
        normal.setColor(t.bgTertiary);
        normal.setStroke(dpToPx(Math.max(t.borderWidth, 1)), t.borderPrimary);

        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        return states;
    }

    // ============================================
    // UTILITIES
    // ============================================

    private int dpToPx(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    /**
     * Adjust alpha of a color (0-255)
     */
    public static int adjustAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    /**
     * Get color state list for text
     */
    public ColorStateList getTextColorStateList() {
        Theme t = getCurrentTheme();
        return ColorStateList.valueOf(t.textPrimary);
    }

    /**
     * Get color state list for secondary text
     */
    public ColorStateList getSecondaryTextColorStateList() {
        Theme t = getCurrentTheme();
        return ColorStateList.valueOf(t.textSecondary);
    }
}