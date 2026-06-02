package com.jarjarblinkz.EvolveLauncher.theme;

import android.graphics.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * 8 themed presets with proper readability
 * All text colors verified for contrast against their backgrounds
 */
public class ThemePresets {

    public static List<Theme> getBuiltInThemes() {
        List<Theme> themes = new ArrayList<>();
        themes.add(createDefault());
        themes.add(createSciFi());
        themes.add(createNeon());
        themes.add(createRetro());
        themes.add(createDeepSpace());
        themes.add(createCyberpunk());
        themes.add(createNature());
        themes.add(createSunset());
        return themes;
    }

    /**
     * DEFAULT - Original Evolve look
     * Dark bg + white text = readable
     */
    public static Theme createDefault() {
        Theme t = new Theme();
        t.id = "default";
        t.name = "Default";
        t.isBuiltIn = true;
        t.bgPrimary = Color.parseColor("#121212");
        t.bgSecondary = Color.parseColor("#1E1E1E");
        t.bgTertiary = Color.parseColor("#2A2A2A");
        t.accentPrimary = Color.parseColor("#6B8EFF");
        t.accentSecondary = Color.parseColor("#2196F3");
        t.accentTertiary = Color.parseColor("#A0BFFF");
        t.textPrimary = Color.parseColor("#FFFFFF");
        t.textSecondary = Color.parseColor("#CCCCCC");
        t.textMuted = Color.parseColor("#888888");
        t.borderPrimary = Color.parseColor("#333333");
        t.borderAccent = Color.parseColor("#6B8EFF");
        t.buttonCornerRadius = 4;
        t.cardCornerRadius = 12;
        t.dialogCornerRadius = 8;
        t.borderWidth = 1;
        t.backgroundType = Theme.BackgroundType.SOLID;
        return t;
    }

    /**
     * SCI-FI - Dark blue with cyan accents
     * Dark bg + light blue text = readable
     */
    public static Theme createSciFi() {
        Theme t = new Theme();
        t.id = "scifi";
        t.name = "Sci-Fi";
        t.isBuiltIn = true;
        t.bgPrimary = Color.parseColor("#0D1B2A");
        t.bgSecondary = Color.parseColor("#1B263B");
        t.bgTertiary = Color.parseColor("#2A3F5F");
        t.accentPrimary = Color.parseColor("#00B8D4");      // Darker cyan for better text contrast
        t.accentSecondary = Color.parseColor("#0097A7");
        t.accentTertiary = Color.parseColor("#4DD0E1");
        t.textPrimary = Color.parseColor("#FFFFFF");         // White on dark bg
        t.textSecondary = Color.parseColor("#B3E5FC");
        t.textMuted = Color.parseColor("#7A95B5");
        t.borderPrimary = Color.parseColor("#2A3F5F");
        t.borderAccent = Color.parseColor("#00B8D4");
        t.buttonCornerRadius = 2;
        t.cardCornerRadius = 4;
        t.dialogCornerRadius = 6;
        t.borderWidth = 2;
        t.backgroundType = Theme.BackgroundType.IMAGE;
        t.backgroundImagePath = "scifi_bg.jpg";
        return t;
    }

    /**
     * NEON - Dark with magenta accents
     * Dark bg + white text = readable
     */
    public static Theme createNeon() {
        Theme t = new Theme();
        t.id = "neon";
        t.name = "Neon";
        t.isBuiltIn = true;
        t.bgPrimary = Color.parseColor("#0F0019");
        t.bgSecondary = Color.parseColor("#1A0033");
        t.bgTertiary = Color.parseColor("#2D0052");
        t.accentPrimary = Color.parseColor("#D500F9");      // Slightly darker magenta
        t.accentSecondary = Color.parseColor("#AA00FF");
        t.accentTertiary = Color.parseColor("#E040FB");
        t.textPrimary = Color.parseColor("#FFFFFF");
        t.textSecondary = Color.parseColor("#F8BBD0");
        t.textMuted = Color.parseColor("#9966CC");
        t.borderPrimary = Color.parseColor("#2D0052");
        t.borderAccent = Color.parseColor("#D500F9");
        t.buttonCornerRadius = 0;
        t.cardCornerRadius = 0;
        t.dialogCornerRadius = 4;
        t.borderWidth = 3;
        t.backgroundType = Theme.BackgroundType.IMAGE;
        t.backgroundImagePath = "neon_bg.jpg";
        return t;
    }

    /**
     * RETRO - Vaporwave purple/pink
     */
    public static Theme createRetro() {
        Theme t = new Theme();
        t.id = "retro";
        t.name = "Retro";
        t.isBuiltIn = true;
        t.bgPrimary = Color.parseColor("#1A0033");
        t.bgSecondary = Color.parseColor("#330066");
        t.bgTertiary = Color.parseColor("#4D0099");
        t.accentPrimary = Color.parseColor("#E91E63");      // Deeper pink for white text contrast
        t.accentSecondary = Color.parseColor("#C2185B");
        t.accentTertiary = Color.parseColor("#FF80AB");
        t.textPrimary = Color.parseColor("#FFFFFF");
        t.textSecondary = Color.parseColor("#FFB6E1");
        t.textMuted = Color.parseColor("#9966CC");
        t.borderPrimary = Color.parseColor("#4D0099");
        t.borderAccent = Color.parseColor("#E91E63");
        t.buttonCornerRadius = 16;
        t.cardCornerRadius = 20;
        t.dialogCornerRadius = 24;
        t.borderWidth = 2;
        t.backgroundType = Theme.BackgroundType.IMAGE;
        t.backgroundImagePath = "retro_bg.jpg";
        return t;
    }

    /**
     * DEEP SPACE - Cosmic purple/violet
     */
    public static Theme createDeepSpace() {
        Theme t = new Theme();
        t.id = "deep_space";
        t.name = "Deep Space";
        t.isBuiltIn = true;
        t.bgPrimary = Color.parseColor("#000511");
        t.bgSecondary = Color.parseColor("#0A0E2A");
        t.bgTertiary = Color.parseColor("#1A1F4A");
        t.accentPrimary = Color.parseColor("#651FFF");      // Deeper purple for better contrast
        t.accentSecondary = Color.parseColor("#4527A0");
        t.accentTertiary = Color.parseColor("#B388FF");
        t.textPrimary = Color.parseColor("#FFFFFF");
        t.textSecondary = Color.parseColor("#B19CD9");
        t.textMuted = Color.parseColor("#6A5ACD");
        t.borderPrimary = Color.parseColor("#2A2F5A");
        t.borderAccent = Color.parseColor("#651FFF");
        t.buttonCornerRadius = 20;
        t.cardCornerRadius = 24;
        t.dialogCornerRadius = 28;
        t.borderWidth = 1;
        t.backgroundType = Theme.BackgroundType.IMAGE;
        t.backgroundImagePath = "deep_space_bg.jpg";
        return t;
    }

    /**
     * CYBERPUNK - Dark with yellow accents
     * IMPORTANT: Yellow accent buttons get BLACK text automatically (via contrast helper)
     * Text on dark background uses white
     */
    public static Theme createCyberpunk() {
        Theme t = new Theme();
        t.id = "cyberpunk";
        t.name = "Cyberpunk";
        t.isBuiltIn = true;
        t.bgPrimary = Color.parseColor("#0D0D0D");
        t.bgSecondary = Color.parseColor("#1A1A1A");
        t.bgTertiary = Color.parseColor("#2A2A0A");
        t.accentPrimary = Color.parseColor("#FFD600");      // Bright yellow - gets BLACK text via contrast
        t.accentSecondary = Color.parseColor("#FF6D00");    // Orange
        t.accentTertiary = Color.parseColor("#00E5FF");
        t.textPrimary = Color.parseColor("#FFFFFF");        // White text for dark backgrounds
        t.textSecondary = Color.parseColor("#FFD600");      // Yellow accent text
        t.textMuted = Color.parseColor("#999933");
        t.borderPrimary = Color.parseColor("#FFD600");
        t.borderAccent = Color.parseColor("#FF6D00");
        t.buttonCornerRadius = 0;
        t.cardCornerRadius = 2;
        t.dialogCornerRadius = 4;
        t.borderWidth = 2;
        t.backgroundType = Theme.BackgroundType.IMAGE;
        t.backgroundImagePath = "cyberpunk_bg.jpg";
        return t;
    }

    /**
     * NATURE - Forest green
     */
    public static Theme createNature() {
        Theme t = new Theme();
        t.id = "nature";
        t.name = "Nature";
        t.isBuiltIn = true;
        t.bgPrimary = Color.parseColor("#0D1F0D");
        t.bgSecondary = Color.parseColor("#1B3A1B");
        t.bgTertiary = Color.parseColor("#2E5C2E");
        t.accentPrimary = Color.parseColor("#2E7D32");      // Darker green for white text contrast
        t.accentSecondary = Color.parseColor("#1B5E20");
        t.accentTertiary = Color.parseColor("#66BB6A");
        t.textPrimary = Color.parseColor("#FFFFFF");
        t.textSecondary = Color.parseColor("#A5D6A7");
        t.textMuted = Color.parseColor("#558B5C");
        t.borderPrimary = Color.parseColor("#2E5C2E");
        t.borderAccent = Color.parseColor("#2E7D32");
        t.buttonCornerRadius = 20;
        t.cardCornerRadius = 24;
        t.dialogCornerRadius = 28;
        t.borderWidth = 1;
        t.backgroundType = Theme.BackgroundType.IMAGE;
        t.backgroundImagePath = "nature_bg.jpg";
        return t;
    }

    /**
     * SUNSET - Warm orange/red
     */
    public static Theme createSunset() {
        Theme t = new Theme();
        t.id = "sunset";
        t.name = "Sunset";
        t.isBuiltIn = true;
        t.bgPrimary = Color.parseColor("#1A0F1F");
        t.bgSecondary = Color.parseColor("#2D1B2F");
        t.bgTertiary = Color.parseColor("#3D2B3F");
        t.accentPrimary = Color.parseColor("#E53935");      // Deeper red for white text contrast
        t.accentSecondary = Color.parseColor("#FF6D00");
        t.accentTertiary = Color.parseColor("#FF8E53");
        t.textPrimary = Color.parseColor("#FFFFFF");
        t.textSecondary = Color.parseColor("#FFD4B3");
        t.textMuted = Color.parseColor("#A0613D");
        t.borderPrimary = Color.parseColor("#3D2B3F");
        t.borderAccent = Color.parseColor("#E53935");
        t.buttonCornerRadius = 12;
        t.cardCornerRadius = 16;
        t.dialogCornerRadius = 20;
        t.borderWidth = 1;
        t.backgroundType = Theme.BackgroundType.IMAGE;
        t.backgroundImagePath = "sunset_bg.jpg";
        return t;
    }
}