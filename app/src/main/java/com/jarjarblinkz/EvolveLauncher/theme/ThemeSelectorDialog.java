package com.jarjarblinkz.EvolveLauncher.theme;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.List;

/**
 * Theme selector with image preview cards
 */
public class ThemeSelectorDialog {

    private static final int REQUEST_PICK_IMAGE = 9999;
    private static AlertDialog currentDialog = null;
    private static OnThemeAppliedListener currentListener = null;
    private static Theme editingTheme = null;
    private static Context editingContext = null;
    private static boolean editingIsDuplicate = false;
    private static ImageView editingPreviewImageView = null;
    private static TextView editingImagePathView = null;

    public interface OnThemeAppliedListener {
        void onThemeApplied(Theme theme);
    }

    public static void show(Context context, OnThemeAppliedListener listener) {
        ThemeManager tm = ThemeManager.getInstance(context);

        ScrollView scrollView = new ScrollView(context);
        LinearLayout outerContainer = new LinearLayout(context);
        outerContainer.setOrientation(LinearLayout.VERTICAL);
        outerContainer.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 12));
        scrollView.addView(outerContainer);

        // Get all themes
        List<Theme> allThemes = tm.getAllThemes();

        // Add theme cards in rows of 4
        LinearLayout currentRow = null;
        for (int i = 0; i < allThemes.size(); i++) {
            if (i % 4 == 0) {
                currentRow = new LinearLayout(context);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowParams.bottomMargin = dp(context, 6);
                currentRow.setLayoutParams(rowParams);
                outerContainer.addView(currentRow);
            }
            View card = createThemeCard(context, allThemes.get(i), tm, listener);
            currentRow.addView(card);
        }

        // Fill remaining slots in last row with invisible spacers
        if (currentRow != null && allThemes.size() % 4 != 0) {
            int remaining = 4 - (allThemes.size() % 4);
            for (int i = 0; i < remaining; i++) {
                View spacer = new View(context);
                LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                spacerParams.setMargins(dp(context, 3), 0, dp(context, 3), 0);
                spacer.setLayoutParams(spacerParams);
                currentRow.addView(spacer);
            }
        }

        // Create custom theme button
        Button createBtn = new Button(context);
        createBtn.setText("+ Create Custom Theme");
        createBtn.setTextColor(Theme.getContrastColor(tm.getCurrentTheme().accentPrimary));
        createBtn.setBackgroundColor(tm.getCurrentTheme().accentPrimary);
        createBtn.setOnClickListener(v -> {
            showCustomThemeEditor(context, tm.getCurrentTheme().copyWithName("My Theme"), tm, listener, true);
        });
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 44));
        btnParams.topMargin = dp(context, 12);
        createBtn.setLayoutParams(btnParams);
        outerContainer.addView(createBtn);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("🎨 Choose Theme")
                .setView(scrollView)
                .setNegativeButton("Close", null)
                .create();

        dialog.show();
    }

    /**
     * Create a compact theme card with buttons BELOW the card
     */
    private static View createThemeCard(Context context, Theme theme, ThemeManager tm,
                                        OnThemeAppliedListener listener) {
        // Outer container holds card + buttons
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        boolean isActive = theme.id.equals(tm.getCurrentTheme().id);

        // Layout params for 3-column grid
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        containerParams.setMargins(dp(context, 3), 0, dp(context, 3), 0);
        container.setLayoutParams(containerParams);

        // === CARD (image + name only) ===
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(theme.bgSecondary);
        bg.setCornerRadius(dp(context, 6));
        bg.setStroke(dp(context, isActive ? 2 : 1),
                isActive ? theme.accentPrimary : theme.borderPrimary);
        card.setBackground(bg);
        card.setClipToOutline(true);

        // Image preview
        FrameLayout previewFrame = new FrameLayout(context);
        FrameLayout.LayoutParams previewParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(context, 70));
        previewFrame.setLayoutParams(previewParams);
        previewFrame.setBackgroundColor(theme.bgPrimary);

        ImageView previewImage = new ImageView(context);
        previewImage.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        previewImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        previewFrame.addView(previewImage);

        // Load image
        if (theme.backgroundType == Theme.BackgroundType.IMAGE &&
                theme.backgroundImagePath != null && !theme.backgroundImagePath.isEmpty()) {

            Object imagePath;
            if (theme.backgroundImagePath.startsWith("content://") ||
                    theme.backgroundImagePath.startsWith("file://")) {
                imagePath = Uri.parse(theme.backgroundImagePath);
            } else {
                imagePath = "file:///android_asset/theme_bgs/" + theme.backgroundImagePath;
            }

            try {
                Glide.with(context)
                        .load(imagePath)
                        .centerCrop()
                        .into(previewImage);
            } catch (Exception e) {
                previewImage.setBackgroundColor(theme.bgPrimary);
            }
        } else {
            previewImage.setBackgroundColor(theme.bgPrimary);
        }

        // Active checkmark
        if (isActive) {
            TextView check = new TextView(context);
            check.setText("✓");
            check.setTextColor(Color.WHITE);
            check.setTextSize(14);
            check.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            check.setShadowLayer(3f, 1f, 1f, Color.BLACK);
            FrameLayout.LayoutParams checkParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            checkParams.gravity = Gravity.TOP | Gravity.END;
            checkParams.setMargins(0, dp(context, 4), dp(context, 6), 0);
            check.setLayoutParams(checkParams);
            previewFrame.addView(check);
        }

        card.addView(previewFrame);

        // Theme name (inside card, below image)
        TextView nameView = new TextView(context);
        nameView.setText(theme.name);
        nameView.setTextColor(theme.textPrimary);
        nameView.setTextSize(11);
        nameView.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        nameView.setGravity(Gravity.CENTER);
        nameView.setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4));
        nameView.setMaxLines(1);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(nameView);

        container.addView(card);

        // === BUTTONS BELOW CARD (side by side) ===
        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams btnRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnRowParams.topMargin = dp(context, 3);
        btnRow.setLayoutParams(btnRowParams);

        // Apply button
        Button applyBtn = new Button(context);
        applyBtn.setText("Apply");
        applyBtn.setTextSize(11);
        applyBtn.setTextColor(Theme.getContrastColor(theme.accentPrimary));
        applyBtn.setBackgroundColor(theme.accentPrimary);
        applyBtn.setPadding(0, 0, 0, 0);
        applyBtn.setMinHeight(0);
        applyBtn.setMinimumHeight(0);
        applyBtn.setOnClickListener(v -> {
            tm.setActiveTheme(theme.id);
            Toast.makeText(context, "Applied: " + theme.name, Toast.LENGTH_SHORT).show();
            if (listener != null) listener.onThemeApplied(theme);
        });
        LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(
                0, dp(context, 28), 1f);
        applyParams.rightMargin = dp(context, 3);
        applyBtn.setLayoutParams(applyParams);
        btnRow.addView(applyBtn);

        // Edit/Copy button
        Button editBtn = new Button(context);
        editBtn.setText(theme.isBuiltIn ? "Copy" : "Edit");
        editBtn.setTextSize(11);
        editBtn.setTextColor(Theme.getContrastColor(theme.bgTertiary));
        editBtn.setBackgroundColor(theme.bgTertiary);
        editBtn.setPadding(0, 0, 0, 0);
        editBtn.setMinHeight(0);
        editBtn.setMinimumHeight(0);
        editBtn.setOnClickListener(v -> {
            Theme editTheme = theme.isBuiltIn ? theme.copyWithName(theme.name + " Copy") : theme;
            showCustomThemeEditor(context, editTheme, tm, listener, theme.isBuiltIn);
        });
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                0, dp(context, 28), 1f);
        if (!theme.isBuiltIn) editParams.rightMargin = dp(context, 3);
        editBtn.setLayoutParams(editParams);
        btnRow.addView(editBtn);

        // Delete button (only for custom themes)
        if (!theme.isBuiltIn) {
            Button deleteBtn = new Button(context);
            deleteBtn.setText("✕");
            deleteBtn.setTextSize(11);
            deleteBtn.setTextColor(Color.WHITE);
            deleteBtn.setBackgroundColor(Color.parseColor("#D32F2F"));
            deleteBtn.setPadding(0, 0, 0, 0);
            deleteBtn.setMinHeight(0);
            deleteBtn.setMinimumHeight(0);
            deleteBtn.setOnClickListener(v -> {
                new AlertDialog.Builder(context)
                        .setTitle("Delete Theme?")
                        .setMessage("Delete '" + theme.name + "'?")
                        .setPositiveButton("Delete", (d, w) -> {
                            tm.deleteCustomTheme(theme.id);
                            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show();
                            if (listener != null) listener.onThemeApplied(tm.getCurrentTheme());
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
            LinearLayout.LayoutParams delParams = new LinearLayout.LayoutParams(
                    dp(context, 32), dp(context, 28));
            deleteBtn.setLayoutParams(delParams);
            btnRow.addView(deleteBtn);
        }

        container.addView(btnRow);
        return container;
    }

    /**
     * Show options menu for a theme (long-press fallback)
     */
    private static void showThemeOptions(Context context, Theme theme, ThemeManager tm,
                                         OnThemeAppliedListener listener) {
        String[] options;
        if (theme.isBuiltIn) {
            options = new String[]{"Apply", "Copy & Customize"};
        } else {
            options = new String[]{"Apply", "Edit", "Delete"};
        }

        new AlertDialog.Builder(context)
                .setTitle(theme.name)
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        tm.setActiveTheme(theme.id);
                        Toast.makeText(context, "Applied: " + theme.name, Toast.LENGTH_SHORT).show();
                        if (listener != null) listener.onThemeApplied(theme);
                    } else if (which == 1) {
                        Theme editTheme = theme.isBuiltIn ? theme.copyWithName(theme.name + " Copy") : theme;
                        showCustomThemeEditor(context, editTheme, tm, listener, theme.isBuiltIn);
                    } else if (which == 2 && !theme.isBuiltIn) {
                        new AlertDialog.Builder(context)
                                .setTitle("Delete Theme?")
                                .setMessage("Delete '" + theme.name + "'?")
                                .setPositiveButton("Delete", (d2, w) -> {
                                    tm.deleteCustomTheme(theme.id);
                                    Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show();
                                    if (listener != null) listener.onThemeApplied(tm.getCurrentTheme());
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Custom theme editor with image background selection
     */
    private static void showCustomThemeEditor(Context context, Theme theme, ThemeManager tm,
                                              OnThemeAppliedListener listener, boolean isDuplicate) {
        // Store state for image picker callback
        editingTheme = theme;
        editingContext = context;
        editingIsDuplicate = isDuplicate;
        currentListener = listener;

        ScrollView scrollView = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 12));
        scrollView.addView(container);

        // Name input
        TextView nameLabel = new TextView(context);
        nameLabel.setText("Theme Name");
        nameLabel.setTextSize(11);
        nameLabel.setTextColor(Color.parseColor("#888888"));
        container.addView(nameLabel);

        EditText nameInput = new EditText(context);
        nameInput.setText(theme.name);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nameInput.setTextColor(Color.WHITE);
        nameInput.setTextSize(13);
        container.addView(nameInput);

        // === BACKGROUND IMAGE SECTION ===
        addCompactSection(context, container, "BACKGROUND IMAGE");

        // Image preview
        FrameLayout imagePreviewFrame = new FrameLayout(context);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 120));
        previewParams.bottomMargin = dp(context, 6);
        imagePreviewFrame.setLayoutParams(previewParams);
        imagePreviewFrame.setBackgroundColor(theme.bgTertiary);

        ImageView imagePreview = new ImageView(context);
        imagePreview.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        imagePreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imagePreviewFrame.addView(imagePreview);
        editingPreviewImageView = imagePreview;

        // Load current image if exists
        loadImageIntoPreview(context, theme, imagePreview);

        container.addView(imagePreviewFrame);

        // Image path label
        TextView imagePathView = new TextView(context);
        imagePathView.setText(getImageDescription(theme));
        imagePathView.setTextColor(Color.parseColor("#888888"));
        imagePathView.setTextSize(10);
        imagePathView.setPadding(0, 0, 0, dp(context, 6));
        container.addView(imagePathView);
        editingImagePathView = imagePathView;

        // Image action buttons
        LinearLayout imageBtnRow = new LinearLayout(context);
        imageBtnRow.setOrientation(LinearLayout.HORIZONTAL);

        Button pickImageBtn = new Button(context);
        pickImageBtn.setText("📁 Pick Image");
        pickImageBtn.setTextSize(11);
        pickImageBtn.setTextColor(Color.WHITE);
        pickImageBtn.setBackgroundColor(Color.parseColor("#2196F3"));
        pickImageBtn.setOnClickListener(v -> launchImagePicker(context));
        LinearLayout.LayoutParams pickParams = new LinearLayout.LayoutParams(
                0, dp(context, 36), 1f);
        pickParams.rightMargin = dp(context, 4);
        pickImageBtn.setLayoutParams(pickParams);
        imageBtnRow.addView(pickImageBtn);

        Button clearImageBtn = new Button(context);
        clearImageBtn.setText("🚫 No Image");
        clearImageBtn.setTextSize(11);
        clearImageBtn.setTextColor(Color.WHITE);
        clearImageBtn.setBackgroundColor(Color.parseColor("#757575"));
        clearImageBtn.setOnClickListener(v -> {
            editingTheme.backgroundType = Theme.BackgroundType.SOLID;
            editingTheme.backgroundImagePath = "";
            imagePreview.setImageDrawable(null);
            imagePreview.setBackgroundColor(editingTheme.bgPrimary);
            imagePathView.setText("Solid color background");
        });
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                0, dp(context, 36), 1f);
        clearImageBtn.setLayoutParams(clearParams);
        imageBtnRow.addView(clearImageBtn);

        container.addView(imageBtnRow);

        // === COLOR SECTION ===
        addCompactSection(context, container, "COLORS");
        ColorPickerHolder bgPrimary = addCompactColorPicker(context, container, "BG Primary", theme.bgPrimary);
        ColorPickerHolder bgSecondary = addCompactColorPicker(context, container, "BG Secondary", theme.bgSecondary);
        ColorPickerHolder bgTertiary = addCompactColorPicker(context, container, "BG Tertiary", theme.bgTertiary);
        ColorPickerHolder accent1 = addCompactColorPicker(context, container, "Accent 1", theme.accentPrimary);
        ColorPickerHolder accent2 = addCompactColorPicker(context, container, "Accent 2", theme.accentSecondary);
        ColorPickerHolder accent3 = addCompactColorPicker(context, container, "Accent 3", theme.accentTertiary);
        ColorPickerHolder textPrimary = addCompactColorPicker(context, container, "Text Primary", theme.textPrimary);
        ColorPickerHolder textSecondary = addCompactColorPicker(context, container, "Text Secondary", theme.textSecondary);
        ColorPickerHolder textMuted = addCompactColorPicker(context, container, "Text Muted", theme.textMuted);
        ColorPickerHolder borderPrimary = addCompactColorPicker(context, container, "Border", theme.borderPrimary);
        ColorPickerHolder borderAccent = addCompactColorPicker(context, container, "Border Accent", theme.borderAccent);

        addCompactSection(context, container, "SHAPES");
        SeekBarHolder cornerRadius = addCompactSeekBar(context, container, "Button Corner", theme.buttonCornerRadius, 0, 40);
        SeekBarHolder cardCorner = addCompactSeekBar(context, container, "Card Corner", theme.cardCornerRadius, 0, 40);
        SeekBarHolder dialogCorner = addCompactSeekBar(context, container, "Dialog Corner", theme.dialogCornerRadius, 0, 40);
        SeekBarHolder borderWidth = addCompactSeekBar(context, container, "Border Width", theme.borderWidth, 0, 5);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(isDuplicate ? "Create Theme" : "Edit Theme")
                .setView(scrollView)
                .setPositiveButton("Save", (d, w) -> {
                    theme.name = nameInput.getText().toString().trim();
                    if (theme.name.isEmpty()) theme.name = "Custom";
                    if (isDuplicate) {
                        theme.id = "custom_" + System.currentTimeMillis();
                        theme.isBuiltIn = false;
                    }
                    theme.bgPrimary = bgPrimary.getColor();
                    theme.bgSecondary = bgSecondary.getColor();
                    theme.bgTertiary = bgTertiary.getColor();
                    theme.accentPrimary = accent1.getColor();
                    theme.accentSecondary = accent2.getColor();
                    theme.accentTertiary = accent3.getColor();
                    theme.textPrimary = textPrimary.getColor();
                    theme.textSecondary = textSecondary.getColor();
                    theme.textMuted = textMuted.getColor();
                    theme.borderPrimary = borderPrimary.getColor();
                    theme.borderAccent = borderAccent.getColor();
                    theme.buttonCornerRadius = cornerRadius.getValue();
                    theme.cardCornerRadius = cardCorner.getValue();
                    theme.dialogCornerRadius = dialogCorner.getValue();
                    theme.borderWidth = borderWidth.getValue();

                    tm.saveCustomTheme(theme);
                    tm.setActiveTheme(theme.id);
                    Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show();
                    if (listener != null) listener.onThemeApplied(theme);
                })
                .setNegativeButton("Cancel", null)
                .create();

        currentDialog = dialog;
        dialog.show();
    }

    private static void loadImageIntoPreview(Context context, Theme theme, ImageView imageView) {
        if (theme.backgroundType == Theme.BackgroundType.IMAGE &&
                theme.backgroundImagePath != null && !theme.backgroundImagePath.isEmpty()) {

            String imagePath;
            if (theme.backgroundImagePath.startsWith("content://") ||
                    theme.backgroundImagePath.startsWith("file://")) {
                imagePath = theme.backgroundImagePath;
            } else {
                imagePath = "file:///android_asset/theme_bgs/" + theme.backgroundImagePath;
            }

            try {
                Glide.with(context)
                        .load(imagePath)
                        .centerCrop()
                        .into(imageView);
            } catch (Exception e) {
                imageView.setImageDrawable(null);
                imageView.setBackgroundColor(theme.bgPrimary);
            }
        } else {
            imageView.setImageDrawable(null);
            imageView.setBackgroundColor(theme.bgPrimary);
        }
    }

    private static String getImageDescription(Theme theme) {
        if (theme.backgroundType == Theme.BackgroundType.IMAGE &&
                theme.backgroundImagePath != null && !theme.backgroundImagePath.isEmpty()) {
            if (theme.backgroundImagePath.startsWith("content://") ||
                    theme.backgroundImagePath.startsWith("file://")) {
                return "Custom image selected";
            } else {
                return "Built-in: " + theme.backgroundImagePath;
            }
        }
        return "Solid color background";
    }

    private static void launchImagePicker(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

            if (context instanceof Activity) {
                ((Activity) context).startActivityForResult(
                        Intent.createChooser(intent, "Select Background Image"),
                        REQUEST_PICK_IMAGE
                );
            } else {
                Toast.makeText(context, "Cannot open image picker", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "Failed to open image picker", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Call this from SettingsActivity.onActivityResult to handle image selection
     */
    public static boolean handleImageResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_PICK_IMAGE || resultCode != Activity.RESULT_OK || data == null) {
            return false;
        }

        Uri imageUri = data.getData();
        if (imageUri == null) return false;

        try {
            // Persist permission
            if (editingContext != null) {
                try {
                    editingContext.getContentResolver().takePersistableUriPermission(
                            imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                } catch (Exception e) {
                    // Continue even if permission persist fails
                }
            }

            // Update theme
            if (editingTheme != null) {
                editingTheme.backgroundType = Theme.BackgroundType.IMAGE;
                editingTheme.backgroundImagePath = imageUri.toString();

                // Update preview
                if (editingPreviewImageView != null && editingContext != null) {
                    try {
                        Glide.with(editingContext)
                                .load(imageUri)
                                .centerCrop()
                                .into(editingPreviewImageView);
                    } catch (Exception e) {
                        // Ignore preview failure
                    }
                }

                if (editingImagePathView != null) {
                    editingImagePathView.setText("Custom image selected");
                }

                Toast.makeText(editingContext, "Image selected!", Toast.LENGTH_SHORT).show();
            }
            return true;
        } catch (Exception e) {
            Toast.makeText(editingContext, "Failed to load image", Toast.LENGTH_SHORT).show();
            return true;
        }
    }

    private static void addCompactSection(Context context, LinearLayout container, String text) {
        TextView header = new TextView(context);
        header.setText(text);
        header.setTextColor(Color.parseColor("#6B8EFF"));
        header.setTextSize(11);
        header.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        header.setLetterSpacing(0.15f);
        header.setPadding(0, dp(context, 12), 0, dp(context, 4));
        container.addView(header);
    }

    private static ColorPickerHolder addCompactColorPicker(Context context, LinearLayout container,
                                                           String label, int initialColor) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(context, 2), 0, dp(context, 2));

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextColor(Color.WHITE);
        labelView.setTextSize(11);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelView.setLayoutParams(labelParams);
        row.addView(labelView);

        View colorView = new View(context);
        ColorPickerHolder holder = new ColorPickerHolder(initialColor);
        updateColorView(context, colorView, holder.getColor());
        colorView.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 28), dp(context, 28)));
        colorView.setOnClickListener(v -> showColorPicker(context, holder.getColor(),
                newColor -> {
                    holder.setColor(newColor);
                    updateColorView(context, colorView, newColor);
                    if (holder.hexView != null) {
                        holder.hexView.setText(String.format("#%06X", newColor & 0xFFFFFF));
                    }
                }));
        row.addView(colorView);

        TextView hexView = new TextView(context);
        hexView.setText(String.format("#%06X", initialColor & 0xFFFFFF));
        hexView.setTextColor(Color.parseColor("#888888"));
        hexView.setTextSize(9);
        hexView.setPadding(dp(context, 6), 0, 0, 0);
        hexView.setTypeface(android.graphics.Typeface.MONOSPACE);
        row.addView(hexView);
        holder.hexView = hexView;

        container.addView(row);
        return holder;
    }

    private static void updateColorView(Context context, View view, int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(color);
        bg.setCornerRadius(dp(context, 4));
        bg.setStroke(dp(context, 1), Color.parseColor("#33FFFFFF"));
        view.setBackground(bg);
    }

    private static SeekBarHolder addCompactSeekBar(Context context, LinearLayout container,
                                                   String label, int initialValue, int min, int max) {
        TextView labelView = new TextView(context);
        labelView.setText(label + ": " + initialValue + "dp");
        labelView.setTextColor(Color.WHITE);
        labelView.setTextSize(11);
        labelView.setPadding(0, dp(context, 6), 0, 0);
        container.addView(labelView);

        SeekBar seekBar = new SeekBar(context);
        seekBar.setMax(max - min);
        seekBar.setProgress(initialValue - min);
        container.addView(seekBar);

        SeekBarHolder holder = new SeekBarHolder(initialValue, min);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress + min;
                holder.setValue(value);
                labelView.setText(label + ": " + value + "dp");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        return holder;
    }

    private static void showColorPicker(Context context, int currentColor, OnColorSelected listener) {
        ScrollView scroll = new ScrollView(context);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 12));
        scroll.addView(layout);

        int alpha = Color.alpha(currentColor);
        int red = Color.red(currentColor);
        int green = Color.green(currentColor);
        int blue = Color.blue(currentColor);
        int[] argb = {alpha, red, green, blue};

        View preview = new View(context);
        preview.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 50)));
        updateColorView(context, preview, currentColor);
        layout.addView(preview);

        TextView hexLabel = new TextView(context);
        hexLabel.setText(String.format("#%06X", currentColor & 0xFFFFFF));
        hexLabel.setTextColor(Color.WHITE);
        hexLabel.setTextSize(14);
        hexLabel.setGravity(Gravity.CENTER);
        hexLabel.setPadding(0, dp(context, 6), 0, dp(context, 8));
        hexLabel.setTypeface(android.graphics.Typeface.MONOSPACE);
        layout.addView(hexLabel);

        String[] labels = {"A", "R", "G", "B"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;

            TextView label = new TextView(context);
            label.setText(labels[i] + ": " + argb[i]);
            label.setTextColor(Color.WHITE);
            label.setTextSize(11);
            label.setPadding(0, dp(context, 2), 0, 0);
            layout.addView(label);

            SeekBar bar = new SeekBar(context);
            bar.setMax(255);
            bar.setProgress(argb[i]);
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    argb[idx] = progress;
                    label.setText(labels[idx] + ": " + progress);
                    int newColor = Color.argb(argb[0], argb[1], argb[2], argb[3]);
                    updateColorView(context, preview, newColor);
                    hexLabel.setText(String.format("#%06X", newColor & 0xFFFFFF));
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            layout.addView(bar);
        }

        new AlertDialog.Builder(context)
                .setTitle("Pick Color")
                .setView(scroll)
                .setPositiveButton("OK", (d, w) -> {
                    int newColor = Color.argb(argb[0], argb[1], argb[2], argb[3]);
                    listener.onColorSelected(newColor);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    interface OnColorSelected { void onColorSelected(int color); }

    static class ColorPickerHolder {
        private int color;
        TextView hexView;
        ColorPickerHolder(int color) { this.color = color; }
        int getColor() { return color; }
        void setColor(int color) { this.color = color; }
    }

    static class SeekBarHolder {
        private int value;
        SeekBarHolder(int value, int min) { this.value = value; }
        int getValue() { return value; }
        void setValue(int value) { this.value = value; }
    }

    private static int dp(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}