package com.jarjarblinkz.EvolveLauncher.theme;

import android.content.DialogInterface;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

/**
 * Comprehensive theme application for AlertDialogs
 */
public class ThemedDialog {

    /**
     * Apply theme thoroughly to all parts of the dialog
     */
    public static void apply(AlertDialog dialog) {
        if (dialog == null || !dialog.isShowing()) return;

        Theme theme = ThemeManager.getInstance(dialog.getContext()).getCurrentTheme();
        if (theme == null) return;

        Window window = dialog.getWindow();
        if (window == null) return;

        // Create rounded background using theme colors
        GradientDrawable dialogBg = new GradientDrawable();
        dialogBg.setShape(GradientDrawable.RECTANGLE);
        dialogBg.setColor(theme.bgSecondary);
        dialogBg.setCornerRadius(dpToPx(dialog.getContext(), theme.dialogCornerRadius));
        if (theme.borderWidth > 0) {
            dialogBg.setStroke(dpToPx(dialog.getContext(), theme.borderWidth), theme.accentPrimary);
        }
        window.setBackgroundDrawable(dialogBg);

        // Walk entire dialog tree forcing all colors
        View decorView = window.getDecorView();
        if (decorView != null) {
            forceThemeAllViews(decorView, theme);
        }

        // Theme the alert buttons (Positive/Negative/Neutral)
        themeAlertButtons(dialog, theme);

        // Theme list items if it's an items-based dialog
        themeListItems(dialog, theme);
    }

    /**
     * Recursively force theme colors on every view
     */
    private static void forceThemeAllViews(View view, Theme theme) {
        if (view == null) return;

        // Recurse first
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;

            // Theme group background if it has a colored background
            if (view.getBackground() instanceof ColorDrawable) {
                int bgColor = ((ColorDrawable) view.getBackground()).getColor();
                if (bgColor != android.graphics.Color.TRANSPARENT) {
                    view.setBackgroundColor(theme.bgSecondary);
                }
            }

            for (int i = 0; i < group.getChildCount(); i++) {
                forceThemeAllViews(group.getChildAt(i), theme);
            }
        }

        // ALL TextViews (including those inside dialog) - auto-contrast
        if (view instanceof TextView && !(view instanceof Button)) {
            TextView tv = (TextView) view;
            // Use contrast color against dialog bg (bgSecondary) for guaranteed readability
            int contrastText = Theme.getContrastColor(theme.bgSecondary);
            tv.setTextColor(contrastText);
            // Theme link colors too
            tv.setLinkTextColor(theme.accentPrimary);
        }

        // Theme any plain View with a background
        if (view.getBackground() instanceof ColorDrawable && !(view instanceof ViewGroup)) {
            int bgColor = ((ColorDrawable) view.getBackground()).getColor();
            if (bgColor != android.graphics.Color.TRANSPARENT) {
                int red = (bgColor >> 16) & 0xFF;
                int green = (bgColor >> 8) & 0xFF;
                int blue = bgColor & 0xFF;
                int brightness = (red + green + blue) / 3;

                // If dark, theme as bg color; if very dark/light, leave alone
                if (brightness > 20 && brightness < 100) {
                    view.setBackgroundColor(theme.bgTertiary);
                }
            }
        }
    }

    /**
     * Theme the bottom alert dialog buttons
     */
    private static void themeAlertButtons(AlertDialog dialog, Theme theme) {
        Button positiveBtn = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        Button negativeBtn = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        Button neutralBtn = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);

        // Get contrast for the dialog bg
        int contrastText = Theme.getContrastColor(theme.bgSecondary);
        // Muted = 60% opacity of contrast
        int mutedText = (0x99 << 24) | (contrastText & 0x00FFFFFF);

        if (positiveBtn != null) {
            // Use accent only if it contrasts well, otherwise contrast text
            positiveBtn.setTextColor(theme.accentPrimary);
        }
        if (negativeBtn != null) {
            negativeBtn.setTextColor(mutedText);
        }
        if (neutralBtn != null) {
            neutralBtn.setTextColor(mutedText);
        }

        // Title - use accent color (usually has good contrast)
        int titleId = dialog.getContext().getResources()
                .getIdentifier("alertTitle", "id", "android");
        if (titleId > 0) {
            TextView title = dialog.findViewById(titleId);
            if (title != null) {
                title.setTextColor(theme.accentPrimary);
            }
        }

        // Message - use contrast color for guaranteed readability
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextColor(contrastText);
        }
    }

    /**
     * Theme list items in setItems()-based dialogs
     */
    private static void themeListItems(AlertDialog dialog, Theme theme) {
        ListView listView = dialog.getListView();
        if (listView != null) {
            listView.setBackgroundColor(theme.bgSecondary);
            // Remove dividers between items - cleaner look
            listView.setDivider(null);
            listView.setDividerHeight(0);

            // Re-theme list children after layout
            listView.post(() -> {
                for (int i = 0; i < listView.getChildCount(); i++) {
                    View child = listView.getChildAt(i);
                    if (child != null) {
                        forceThemeAllViews(child, theme);
                    }
                }
            });
        }
    }

    /**
     * Show a dialog with theme applied automatically
     */
    public static AlertDialog showThemed(AlertDialog dialog) {
        dialog.setOnShowListener(d -> apply((AlertDialog) d));
        dialog.show();
        apply(dialog);
        return dialog;
    }

    private static int dpToPx(android.content.Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}