package com.jarjarblinkz.EvolveLauncher.theme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

/**
 * Loads and prepares theme background images from assets
 * Images go in: app/src/main/assets/theme_bgs/
 */
public class ThemeBackgroundManager {
    private static final String TAG = "ThemeBgManager";
    private static final String BG_FOLDER = "theme_bgs";

    /**
     * Get the background drawable for a theme
     * Returns appropriate drawable based on theme.backgroundType
     */
    public static Drawable getBackgroundDrawable(Context context, Theme theme) {
        if (theme == null) {
            return new ColorDrawable(Color.parseColor("#121212"));
        }

        switch (theme.backgroundType) {
            case IMAGE:
                Drawable imageDrawable = loadImageDrawable(context, theme);
                if (imageDrawable != null) {
                    return imageDrawable;
                }
                // Fallback to solid if image fails
                return new ColorDrawable(theme.bgPrimary);

            case GRADIENT:
                return createGradient(theme);

            case SOLID:
            default:
                return new ColorDrawable(theme.bgPrimary);
        }
    }

    /**
     * Load image from assets with darkening overlay for readability
     */
    private static Drawable loadImageDrawable(Context context, Theme theme) {
        if (theme.backgroundImagePath == null || theme.backgroundImagePath.isEmpty()) {
            return null;
        }

        try {
            String fullPath = BG_FOLDER + "/" + theme.backgroundImagePath;
            InputStream is = context.getAssets().open(fullPath);

            // Load bitmap with sampling to save memory
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2; // Downsample by 2x for memory
            options.inPreferredConfig = Bitmap.Config.RGB_565; // Save memory

            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
            is.close();

            if (bitmap == null) {
                Log.w(TAG, "Failed to decode: " + fullPath);
                return null;
            }

            // Add a dark overlay so UI is readable
            Bitmap darkenedBitmap = darkenBitmap(bitmap, theme);

            BitmapDrawable drawable = new BitmapDrawable(context.getResources(), darkenedBitmap);
            drawable.setTileModeXY(android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);

            return drawable;

        } catch (IOException e) {
            Log.w(TAG, "Background image not found: " + theme.backgroundImagePath +
                    " - place in assets/theme_bgs/");
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load background", e);
            return null;
        }
    }

    /**
     * Apply a dark overlay to the bitmap for text readability
     */
    private static Bitmap darkenBitmap(Bitmap original, Theme theme) {
        Bitmap result = original.copy(Bitmap.Config.ARGB_8888, true);
        if (result == null) result = original;

        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        paint.setColor(Color.argb(140, 0, 0, 0)); // Dark overlay with ~55% opacity
        canvas.drawRect(0, 0, result.getWidth(), result.getHeight(), paint);

        return result;
    }

    /**
     * Create a gradient drawable
     */
    private static GradientDrawable createGradient(Theme theme) {
        int start = theme.gradientStart != 0 ? theme.gradientStart : theme.bgPrimary;
        int end = theme.gradientEnd != 0 ? theme.gradientEnd : theme.bgSecondary;

        GradientDrawable.Orientation orientation;
        int angle = theme.gradientAngle;
        if (angle >= 0 && angle < 45) {
            orientation = GradientDrawable.Orientation.LEFT_RIGHT;
        } else if (angle >= 45 && angle < 90) {
            orientation = GradientDrawable.Orientation.TL_BR;
        } else if (angle >= 90 && angle < 135) {
            orientation = GradientDrawable.Orientation.TOP_BOTTOM;
        } else if (angle >= 135 && angle < 180) {
            orientation = GradientDrawable.Orientation.TR_BL;
        } else {
            orientation = GradientDrawable.Orientation.TL_BR;
        }

        GradientDrawable gradient = new GradientDrawable(orientation, new int[]{start, end});
        return gradient;
    }

    /**
     * Check if a background image is available in assets
     */
    public static boolean isBackgroundImageAvailable(Context context, String filename) {
        if (filename == null || filename.isEmpty()) return false;

        try {
            String[] files = context.getAssets().list(BG_FOLDER);
            if (files != null) {
                for (String file : files) {
                    if (file.equals(filename)) return true;
                }
            }
        } catch (IOException e) {
            // Folder doesn't exist
        }
        return false;
    }
}