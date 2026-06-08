package com.jarjarblinkz.EvolveLauncher.theme;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;

import com.google.android.material.card.MaterialCardView;

/**
 * Thorough theme applier - themes EVERYTHING:
 * - All TextViews force themed colors
 * - All view backgrounds detected and themed
 * - Dialog content fully themed
 * - Cards keep their shapes
 * - Buttons preserve their tint logic
 */
public class ThemeApplier {

    public enum ButtonStyle { NORMAL, ACCENT, CHIP }
    public enum TextStyle { PRIMARY, SECONDARY, MUTED, ACCENT, HEADER }

    /**
     * Apply theme to entire activity WITH background image (MainActivity only)
     */
    public static void applyThemeToActivity(Activity activity) {
        if (activity == null) return;
        Theme theme = ThemeManager.getInstance(activity).getCurrentTheme();
        activity.getWindow().setBackgroundDrawable(
                ThemeBackgroundManager.getBackgroundDrawable(activity, theme)
        );
        View root = activity.findViewById(android.R.id.content);
        applyThemeToHierarchy(root);
    }

    /**
     * Apply theme to activity using only SOLID theme color (no background image)
     * Use this for Settings, Bundled Apps, Playtime Stats, etc.
     */
    public static void applyThemeNoBackground(Activity activity) {
        if (activity == null) return;
        Theme theme = ThemeManager.getInstance(activity).getCurrentTheme();
        // Use solid bgPrimary instead of image
        activity.getWindow().setBackgroundDrawable(
                new ColorDrawable(theme.bgPrimary)
        );
        View root = activity.findViewById(android.R.id.content);
        applyThemeToHierarchy(root);
    }

    /**
     * Walk view tree and apply theme to every view
     */
    public static void applyThemeToHierarchy(View view) {
        if (view == null) return;
        Theme theme = ThemeManager.getInstance(view.getContext()).getCurrentTheme();
        applyToView(view, theme);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyThemeToHierarchy(group.getChildAt(i));
            }
        }
    }

    private static void applyToView(View view, Theme theme) {
        // Skip if tagged to ignore
        if (view.getTag() != null && view.getTag().toString().equals("theme_ignore")) {
            return;
        }

        // ============================================
        // MATERIAL CARD VIEWS (game cards) - ColorStateList for all states
        // ============================================
        if (view instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) view;

            int[][] states = new int[][] {
                    new int[] { android.R.attr.state_pressed },
                    new int[] { android.R.attr.state_focused },
                    new int[] { android.R.attr.state_selected },
                    new int[] { android.R.attr.state_hovered },
                    new int[] {}
            };

            int[] colors = new int[] {
                    theme.bgTertiary, theme.bgTertiary, theme.bgTertiary,
                    theme.bgTertiary, theme.bgSecondary
            };

            card.setCardBackgroundColor(new ColorStateList(states, colors));

            int[] strokeColors = new int[] {
                    theme.accentPrimary, theme.accentPrimary, theme.accentPrimary,
                    theme.accentPrimary, theme.borderPrimary
            };
            card.setStrokeColor(new ColorStateList(states, strokeColors));
            card.setRippleColor(ColorStateList.valueOf(theme.accentPrimary));
            return;
        }

        // ============================================
        // REGULAR CARD VIEWS
        // ============================================
        if (view instanceof CardView) {
            ((CardView) view).setCardBackgroundColor(theme.bgSecondary);
            return;
        }

        // ============================================
        // BUTTONS - theme regular buttons, keep status colors (red/green)
        // ============================================
        if (view instanceof Button || view instanceof AppCompatButton) {
            Button btn = (Button) view;
            String tag = btn.getTag() != null ? btn.getTag().toString() : "";

            // Skip status color preservation for category buttons (they are inside HorizontalScrollView)
            if (btn.getParent() != null && btn.getParent().getParent() instanceof HorizontalScrollView) {
                // This is a category button - always theme it with accent color
                btn.setBackgroundTintList(ColorStateList.valueOf(theme.accentPrimary));
                btn.setTextColor(Theme.getContrastColor(theme.accentPrimary));
                return;
            }

            int buttonBgColor;

            if (tag.equals("muted") || tag.equals("secondary")) {
                buttonBgColor = theme.bgTertiary;
            } else {
                ColorStateList currentTint = btn.getBackgroundTintList();
                if (currentTint != null) {
                    int currentColor = currentTint.getDefaultColor();

                    // KEEP status colors - they communicate state (installed/not installed)
                    if (isStatusColor(currentColor)) {
                        // Don't theme red, green, orange status indicators
                        btn.setTextColor(Theme.getContrastColor(currentColor));
                        return;
                    }

                    if (isMutedColor(currentColor) || currentColor == Color.parseColor("#333333")) {
                        buttonBgColor = theme.bgTertiary;
                    } else {
                        // Regular colored buttons become primary accent
                        buttonBgColor = theme.accentPrimary;
                    }
                } else {
                    buttonBgColor = theme.accentPrimary;
                }
            }

            btn.setBackgroundTintList(ColorStateList.valueOf(buttonBgColor));
            btn.setTextColor(Theme.getContrastColor(buttonBgColor));
            return;
        }

        // ============================================
        // EDIT TEXT
        // ============================================
        if (view instanceof EditText) {
            EditText et = (EditText) view;
            et.setTextColor(theme.textPrimary);
            et.setHintTextColor(theme.textMuted);
            et.setBackgroundTintList(ColorStateList.valueOf(theme.accentPrimary));
            return;
        }

        // ============================================
        // SWITCHES
        // ============================================
        if (view instanceof SwitchCompat) {
            SwitchCompat sw = (SwitchCompat) view;
            sw.setThumbTintList(ColorStateList.valueOf(theme.accentPrimary));
            sw.setTrackTintList(ColorStateList.valueOf(theme.bgTertiary));
            return;
        }

        // ============================================
        // SEEKBARS
        // ============================================
        if (view instanceof SeekBar) {
            SeekBar sb = (SeekBar) view;
            if (sb.getProgressDrawable() != null) {
                sb.getProgressDrawable().setColorFilter(theme.accentPrimary, PorterDuff.Mode.SRC_IN);
            }
            if (sb.getThumb() != null) {
                sb.getThumb().setColorFilter(theme.accentPrimary, PorterDuff.Mode.SRC_IN);
            }
            return;
        }

        // ============================================
        // TEXTVIEWS - auto-contrast based on parent background
        // ============================================
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            String tag = tv.getTag() != null ? tv.getTag().toString() : "";

            // Find the effective background this text sits on
            int effectiveBg = findEffectiveBackground(tv, theme);
            int contrastText = Theme.getContrastColor(effectiveBg);

            if (tag.equals("header") || tag.equals("accent")) {
                // Accent text - check if accent has enough contrast with bg
                if (hasGoodContrast(theme.accentPrimary, effectiveBg)) {
                    tv.setTextColor(theme.accentPrimary);
                } else {
                    // Fall back to contrast color
                    tv.setTextColor(contrastText);
                }
            } else if (tag.equals("muted")) {
                // Muted = 60% opacity contrast color
                int alpha = 0x99; // ~60%
                tv.setTextColor((alpha << 24) | (contrastText & 0x00FFFFFF));
            } else if (tag.equals("secondary")) {
                // Secondary = 80% opacity contrast color
                int alpha = 0xCC; // ~80%
                tv.setTextColor((alpha << 24) | (contrastText & 0x00FFFFFF));
            } else {
                int currentColor = tv.getCurrentTextColor();

                // Preserve specific original tints if they're recognizable
                if (isAccentColor(currentColor) && hasGoodContrast(theme.accentPrimary, effectiveBg)) {
                    tv.setTextColor(theme.accentPrimary);
                } else if (isMutedColor(currentColor)) {
                    int alpha = 0x99;
                    tv.setTextColor((alpha << 24) | (contrastText & 0x00FFFFFF));
                } else {
                    // Default: use auto-contrast for readability
                    tv.setTextColor(contrastText);
                }
            }
            return;
        }

        // ============================================
        // ALL VIEWGROUPS - theme backgrounds
        // ============================================
        if (view instanceof LinearLayout || view instanceof RelativeLayout ||
                view instanceof FrameLayout || view instanceof ScrollView ||
                view instanceof ListView) {
            Drawable bg = view.getBackground();
            if (bg instanceof ColorDrawable) {
                int color = ((ColorDrawable) bg).getColor();
                if (isMainBgColor(color)) {
                    view.setBackgroundColor(theme.bgPrimary);
                } else if (isSecondaryBgColor(color)) {
                    view.setBackgroundColor(theme.bgSecondary);
                } else if (isTertiaryBgColor(color)) {
                    view.setBackgroundColor(theme.bgTertiary);
                } else if (isDividerColor(color)) {
                    view.setBackgroundColor(theme.borderPrimary);
                }
            }
            return;
        }

        // ============================================
        // DIVIDER VIEWS (plain View elements used as lines)
        // ============================================
        Drawable bg = view.getBackground();
        if (bg instanceof ColorDrawable) {
            int color = ((ColorDrawable) bg).getColor();
            if (isDividerColor(color)) {
                view.setBackgroundColor(theme.borderPrimary);
            } else if (isMainBgColor(color)) {
                view.setBackgroundColor(theme.bgPrimary);
            } else if (isSecondaryBgColor(color)) {
                view.setBackgroundColor(theme.bgSecondary);
            }
        }
    }

    // ========================================
    // COLOR DETECTION (expanded)
    // ========================================

    /**
     * Find the effective background color this view sits on by walking up parents
     */
    private static int findEffectiveBackground(View view, Theme theme) {
        View current = view;
        while (current != null) {
            // Check view's own background
            Drawable bg = current.getBackground();
            if (bg instanceof ColorDrawable) {
                int color = ((ColorDrawable) bg).getColor();
                // Ignore transparent
                if ((color >>> 24) > 0x40) {
                    return color;
                }
            }

            // Check for MaterialCardView/CardView background
            if (current instanceof MaterialCardView) {
                ColorStateList csl = ((MaterialCardView) current).getCardBackgroundColor();
                if (csl != null) return csl.getDefaultColor();
            } else if (current instanceof CardView) {
                ColorStateList csl = ((CardView) current).getCardBackgroundColor();
                if (csl != null) return csl.getDefaultColor();
            }

            // Move up
            android.view.ViewParent parent = current.getParent();
            if (parent instanceof View) {
                current = (View) parent;
            } else {
                break;
            }
        }
        // Fallback - assume bgPrimary
        return theme.bgPrimary;
    }

    /**
     * Check if two colors have enough contrast to be readable (WCAG-ish)
     */
    private static boolean hasGoodContrast(int color1, int color2) {
        double lum1 = relativeLuminance(color1);
        double lum2 = relativeLuminance(color2);
        double lighter = Math.max(lum1, lum2);
        double darker = Math.min(lum1, lum2);
        double ratio = (lighter + 0.05) / (darker + 0.05);
        return ratio >= 3.0; // 3:1 = readable for large text
    }

    /**
     * Calculate relative luminance per WCAG 2.0
     */
    private static double relativeLuminance(int color) {
        double r = ((color >> 16) & 0xFF) / 255.0;
        double g = ((color >> 8) & 0xFF) / 255.0;
        double b = (color & 0xFF) / 255.0;
        r = r <= 0.03928 ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = g <= 0.03928 ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = b <= 0.03928 ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static boolean isAccentColor(int color) {
        return color == Color.parseColor("#6B8EFF") ||
                color == Color.parseColor("#2196F3") ||
                color == Color.parseColor("#1976D2") ||
                color == Color.parseColor("#42A5F5") ||
                color == Color.parseColor("#A0BFFF");
    }

    /**
     * Status colors communicate state (installed=green, not installed=red, warning=orange)
     * These should NEVER be themed - they need to remain consistent
     */
    private static boolean isStatusColor(int color) {
        return color == Color.parseColor("#F44336") || // red - error/not installed
                color == Color.parseColor("#D32F2F");   // dark red
    }

    private static boolean isWarningColor(int color) {
        return color == Color.parseColor("#FF5722") ||
                color == Color.parseColor("#FF9800") ||
                color == Color.parseColor("#FF6B35") ||
                color == Color.parseColor("#F44336") ||
                color == Color.parseColor("#E53935");
    }

    private static boolean isMutedColor(int color) {
        return color == Color.parseColor("#AAAAAA") ||
                color == Color.parseColor("#888888") ||
                color == Color.parseColor("#999999") ||
                color == Color.parseColor("#9E9E9E") ||
                color == Color.parseColor("#666666") ||
                color == Color.parseColor("#555555") ||
                color == Color.parseColor("#777777") ||
                color == Color.parseColor("#757575");
    }

    private static boolean isSecondaryColor(int color) {
        return color == Color.parseColor("#CCCCCC") ||
                color == Color.parseColor("#BBBBBB") ||
                color == Color.parseColor("#DDDDDD") ||
                color == Color.parseColor("#E0E0E0");
    }

    private static boolean isMainBgColor(int color) {
        return color == Color.parseColor("#121212") ||
                color == Color.parseColor("#0D0D0D") ||
                color == Color.parseColor("#000000") ||
                color == Color.parseColor("#1A1A1A");
    }

    private static boolean isSecondaryBgColor(int color) {
        return color == Color.parseColor("#1E1E1E") ||
                color == Color.parseColor("#252525");
    }

    private static boolean isTertiaryBgColor(int color) {
        return color == Color.parseColor("#2A2A2A");
    }

    private static boolean isDividerColor(int color) {
        return color == Color.parseColor("#333333");
    }

    // ========================================
    // MANUAL STYLING METHODS
    // ========================================

    public static void applyButton(Button button, ButtonStyle style) {
        Theme theme = ThemeManager.getInstance(button.getContext()).getCurrentTheme();
        int bgColor;
        switch (style) {
            case NORMAL: bgColor = theme.bgTertiary; break;
            case ACCENT: bgColor = theme.accentPrimary; break;
            case CHIP: bgColor = theme.bgSecondary; break;
            default: bgColor = theme.bgTertiary;
        }
        button.setBackgroundTintList(ColorStateList.valueOf(bgColor));
        button.setTextColor(Theme.getContrastColor(bgColor));
    }

    public static void applyText(TextView textView, TextStyle style) {
        Theme theme = ThemeManager.getInstance(textView.getContext()).getCurrentTheme();
        switch (style) {
            case PRIMARY: textView.setTextColor(theme.textPrimary); break;
            case SECONDARY: textView.setTextColor(theme.textSecondary); break;
            case MUTED: textView.setTextColor(theme.textMuted); break;
            case ACCENT: textView.setTextColor(theme.accentPrimary); break;
            case HEADER:
                textView.setTextColor(theme.accentPrimary);
                textView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                break;
        }
    }

    public static void applyEditText(EditText editText) {
        Theme theme = ThemeManager.getInstance(editText.getContext()).getCurrentTheme();
        editText.setTextColor(theme.textPrimary);
        editText.setHintTextColor(theme.textMuted);
        editText.setBackgroundTintList(ColorStateList.valueOf(theme.accentPrimary));
    }

    public static void applyCard(View view) {
        Theme theme = ThemeManager.getInstance(view.getContext()).getCurrentTheme();
        if (view instanceof MaterialCardView) {
            ((MaterialCardView) view).setCardBackgroundColor(theme.bgSecondary);
        } else if (view instanceof CardView) {
            ((CardView) view).setCardBackgroundColor(theme.bgSecondary);
        } else {
            view.setBackgroundColor(theme.bgSecondary);
        }
    }

    public static void applyDialog(View view) {
        Theme theme = ThemeManager.getInstance(view.getContext()).getCurrentTheme();
        view.setBackgroundColor(theme.bgPrimary);
    }

    public static GradientDrawable createButtonDrawable(Context context, Theme theme, boolean accent) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dpToPx(context, theme.buttonCornerRadius));
        d.setColor(accent ? theme.accentPrimary : theme.bgTertiary);
        if (theme.borderWidth > 0) {
            d.setStroke(dpToPx(context, theme.borderWidth),
                    accent ? theme.accentSecondary : theme.borderAccent);
        }
        return d;
    }

    private static int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}