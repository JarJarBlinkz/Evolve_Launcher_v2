package com.jarjarblinkz.EvolveLauncher.theme;

import android.graphics.Color;

/**
 * Theme configuration with colors, shapes, and backgrounds
 */
public class Theme {
    public String id;
    public String name;
    public boolean isBuiltIn;

    // === COLORS ===
    public int bgPrimary;
    public int bgSecondary;
    public int bgTertiary;
    public int accentPrimary;
    public int accentSecondary;
    public int accentTertiary;
    public int textPrimary;
    public int textSecondary;
    public int textMuted;
    public int borderPrimary;
    public int borderAccent;

    // === SHAPES ===
    public int buttonCornerRadius;
    public int cardCornerRadius;
    public int dialogCornerRadius;
    public int borderWidth;

    // === EFFECTS ===
    public boolean useGlassEffect;
    public int glassOpacity;

    // === BACKGROUND TYPE (NEW!) ===
    public BackgroundType backgroundType = BackgroundType.SOLID;
    public String backgroundImagePath = ""; // For IMAGE type - filename in assets/theme_bgs/
    public int gradientStart = 0;
    public int gradientEnd = 0;
    public int gradientAngle = 135;

    public enum BackgroundType {
        SOLID,      // Just bgPrimary color
        GRADIENT,   // gradientStart to gradientEnd
        IMAGE       // Image from assets/theme_bgs/
    }

    public Theme() {}

    /**
     * Calculate the best text color (black or white) for a given background color
     * Uses perceived luminance formula for accurate contrast
     */
    public static int getContrastColor(int backgroundColor) {
        double r = Color.red(backgroundColor) / 255.0;
        double g = Color.green(backgroundColor) / 255.0;
        double b = Color.blue(backgroundColor) / 255.0;

        // Relative luminance formula (perceived brightness)
        double luminance = 0.299 * r + 0.587 * g + 0.114 * b;

        // If background is light, use dark text; if dark, use light text
        return luminance > 0.55 ? Color.parseColor("#1A1A1A") : Color.parseColor("#FFFFFF");
    }

    public Theme copyWithName(String newName) {
        Theme copy = new Theme();
        copy.id = "custom_" + System.currentTimeMillis();
        copy.name = newName;
        copy.isBuiltIn = false;
        copy.bgPrimary = this.bgPrimary;
        copy.bgSecondary = this.bgSecondary;
        copy.bgTertiary = this.bgTertiary;
        copy.accentPrimary = this.accentPrimary;
        copy.accentSecondary = this.accentSecondary;
        copy.accentTertiary = this.accentTertiary;
        copy.textPrimary = this.textPrimary;
        copy.textSecondary = this.textSecondary;
        copy.textMuted = this.textMuted;
        copy.borderPrimary = this.borderPrimary;
        copy.borderAccent = this.borderAccent;
        copy.buttonCornerRadius = this.buttonCornerRadius;
        copy.cardCornerRadius = this.cardCornerRadius;
        copy.dialogCornerRadius = this.dialogCornerRadius;
        copy.borderWidth = this.borderWidth;
        copy.useGlassEffect = this.useGlassEffect;
        copy.glassOpacity = this.glassOpacity;
        copy.backgroundType = this.backgroundType;
        copy.backgroundImagePath = this.backgroundImagePath;
        copy.gradientStart = this.gradientStart;
        copy.gradientEnd = this.gradientEnd;
        copy.gradientAngle = this.gradientAngle;
        return copy;
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(id).append("|");
        sb.append(name).append("|");
        sb.append(isBuiltIn ? "1" : "0").append("|");
        sb.append(bgPrimary).append("|");
        sb.append(bgSecondary).append("|");
        sb.append(bgTertiary).append("|");
        sb.append(accentPrimary).append("|");
        sb.append(accentSecondary).append("|");
        sb.append(accentTertiary).append("|");
        sb.append(textPrimary).append("|");
        sb.append(textSecondary).append("|");
        sb.append(textMuted).append("|");
        sb.append(borderPrimary).append("|");
        sb.append(borderAccent).append("|");
        sb.append(buttonCornerRadius).append("|");
        sb.append(cardCornerRadius).append("|");
        sb.append(dialogCornerRadius).append("|");
        sb.append(borderWidth).append("|");
        sb.append(useGlassEffect ? "1" : "0").append("|");
        sb.append(glassOpacity).append("|");
        sb.append(backgroundType.name()).append("|");
        sb.append(backgroundImagePath).append("|");
        sb.append(gradientStart).append("|");
        sb.append(gradientEnd).append("|");
        sb.append(gradientAngle);
        return sb.toString();
    }

    public static Theme deserialize(String data) {
        try {
            String[] parts = data.split("\\|", -1);
            if (parts.length < 20) return null;

            Theme theme = new Theme();
            theme.id = parts[0];
            theme.name = parts[1];
            theme.isBuiltIn = parts[2].equals("1");
            theme.bgPrimary = Integer.parseInt(parts[3]);
            theme.bgSecondary = Integer.parseInt(parts[4]);
            theme.bgTertiary = Integer.parseInt(parts[5]);
            theme.accentPrimary = Integer.parseInt(parts[6]);
            theme.accentSecondary = Integer.parseInt(parts[7]);
            theme.accentTertiary = Integer.parseInt(parts[8]);
            theme.textPrimary = Integer.parseInt(parts[9]);
            theme.textSecondary = Integer.parseInt(parts[10]);
            theme.textMuted = Integer.parseInt(parts[11]);
            theme.borderPrimary = Integer.parseInt(parts[12]);
            theme.borderAccent = Integer.parseInt(parts[13]);
            theme.buttonCornerRadius = Integer.parseInt(parts[14]);
            theme.cardCornerRadius = Integer.parseInt(parts[15]);
            theme.dialogCornerRadius = Integer.parseInt(parts[16]);
            theme.borderWidth = Integer.parseInt(parts[17]);
            theme.useGlassEffect = parts[18].equals("1");
            theme.glassOpacity = Integer.parseInt(parts[19]);

            // New fields (might not exist in old saves)
            if (parts.length >= 25) {
                try {
                    theme.backgroundType = BackgroundType.valueOf(parts[20]);
                } catch (Exception e) {
                    theme.backgroundType = BackgroundType.SOLID;
                }
                theme.backgroundImagePath = parts[21];
                theme.gradientStart = Integer.parseInt(parts[22]);
                theme.gradientEnd = Integer.parseInt(parts[23]);
                theme.gradientAngle = Integer.parseInt(parts[24]);
            }
            return theme;
        } catch (Exception e) {
            return null;
        }
    }
}