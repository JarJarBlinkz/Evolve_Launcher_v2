package com.jarjarblinkz.EvolveLauncher;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.util.Log;
import android.view.Window;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.SwitchCompat;

import com.jarjarblinkz.EvolveLauncher.theme.ThemeApplier;
import com.jarjarblinkz.EvolveLauncher.theme.ThemeManager;
import com.jarjarblinkz.EvolveLauncher.theme.ThemeSelectorDialog;
import com.jarjarblinkz.EvolveLauncher.theme.ThemedDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private SharedPreferences categoryPrefs;

    // Icon scale values from old launcher (82, 99, 125, 165, 236 dp)
    // These map to seekbar positions 0-100
    private static final int[] ICON_SCALES_DP = {82, 99, 125, 165, 236};
    private static final int DEFAULT_SCALE_INDEX = 2;  // 125dp
    private static final int ICON_SIZE_MIN_DP = 82;
    private static final int ICON_SIZE_MAX_DP = 236;

    // Broadcast action for theme changes
    private static final String ACTION_THEME_CHANGED = "com.jarjarblinkz.EvolveLauncher.THEME_CHANGED";
    // Broadcast action for category changes (create/rename/delete/modify)
    private static final String ACTION_CATEGORIES_CHANGED = "com.jarjarblinkz.EvolveLauncher.CATEGORIES_CHANGED";

    // Meta standard window size (1024x640 dp)
    private static final int META_STANDARD_WIDTH_DP = 1024;
    private static final int META_STANDARD_HEIGHT_DP = 640;

    // Shizuku manager for shell-level commands
    private ShizukuManager shizukuManager;

    private static final String PREFS_NAME = "VRLPrefs";
    private static final String CATEGORY_PREFS = "vr_categories";

    private static final String KEY_EDIT_MODE = "edit_mode";
    private static final String KEY_ICON_SIZE = "icon_size";
    private static final String KEY_ICON_SIZE_SCALE = "icon_size_scale";  // Store scale index (0-4)
    private static final String KEY_SHOW_CATEGORIES = "show_categories";
    private static final String KEY_BG_OPACITY = "background_opacity";
    private static final String KEY_AUTO_START = "auto_start";

    private static final int REQUEST_CODE_CREATE_BACKUP = 200;
    private static final int REQUEST_CODE_OPEN_BACKUP = 201;
    private static final int REQUEST_CODE_USAGE_ACCESS = 202;

    /**
     * Tracks whether we're temporarily losing focus because we launched
     * a sub-activity (file picker, device info, etc.). When true, we
     * don't auto-close on focus loss because the user will return.
     */
    private boolean expectingFocusReturn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

        // Set to Meta's standard window size (same as MainActivity)
        setMetaStandardWindowSize();

        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        categoryPrefs = getSharedPreferences(CATEGORY_PREFS, MODE_PRIVATE);

        // Initialize all views
        SwitchCompat switchEditMode = findViewById(R.id.switchEditMode);
        SwitchCompat switchCategories = findViewById(R.id.switchCategories);
        SeekBar seekIconSize = findViewById(R.id.seekIconSize);
        TextView txtIconSize = findViewById(R.id.txtIconSize);
        TextView txtIconSizeRange = findViewById(R.id.txtIconSizeRange);
        SeekBar seekBgOpacity = findViewById(R.id.seekBgOpacity);
        TextView txtBgOpacity = findViewById(R.id.txtBgOpacity);
        AppCompatButton btnManageCategories = findViewById(R.id.btnManageCategories);
        AppCompatButton btnBack = findViewById(R.id.btnBack);
        AppCompatButton btnGameStats = findViewById(R.id.btnGameStats);
        AppCompatButton btnDeviceInfo = findViewById(R.id.btnDeviceInfo);
        AppCompatButton btnUsageAccess = findViewById(R.id.btnUsageAccess);
        SwitchCompat switchAutoStart = findViewById(R.id.switchAutoStart);

        // ADD BACKUP/RESTORE BUTTONS
        Button btnBackup = findViewById(R.id.btnBackup);
        Button btnRestore = findViewById(R.id.btnRestore);

        // ADD SYSTEM ACCESS BUTTON (Native Settings)
        Button btnNativeSettings = findViewById(R.id.btnNativeSettings);

        // Set initial values
        switchEditMode.setChecked(prefs.getBoolean(KEY_EDIT_MODE, false));
        switchCategories.setChecked(prefs.getBoolean(KEY_SHOW_CATEGORIES, true));
        switchAutoStart.setChecked(prefs.getBoolean(KEY_AUTO_START, true));

        // Setup Icon Size SeekBar - maps to old launcher scale values (82-236 dp)
        if (seekIconSize != null && txtIconSize != null) {
            // Get current scale index (0-4) and convert to seekbar progress (0-100)
            int currentScaleIndex = prefs.getInt(KEY_ICON_SIZE_SCALE, DEFAULT_SCALE_INDEX);
            int seekProgress = convertScaleIndexToSeekProgress(currentScaleIndex);

            seekIconSize.setProgress(seekProgress);
            int currentIconSizeDp = ICON_SCALES_DP[currentScaleIndex];
            txtIconSize.setText(currentIconSizeDp + "dp");

            // Show range text
            if (txtIconSizeRange != null) {
                txtIconSizeRange.setText(ICON_SIZE_MIN_DP + "dp - " + ICON_SIZE_MAX_DP + "dp");
            }

            seekIconSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    // Convert progress (0-100) to actual dp using the old launcher scale mapping
                    int iconSizeDp = convertSeekProgressToIconSizeDp(progress);
                    txtIconSize.setText(iconSizeDp + "dp");
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    int progress = seekBar.getProgress();
                    int iconSizeDp = convertSeekProgressToIconSizeDp(progress);
                    int scaleIndex = convertIconSizeDpToScaleIndex(iconSizeDp);

                    // Save both the scale index and the actual size
                    prefs.edit()
                            .putInt(KEY_ICON_SIZE_SCALE, scaleIndex)
                            .putInt(KEY_ICON_SIZE, iconSizeDp)
                            .apply();

                    // Apply to MainActivity and FavoritesActivity
                    if (MainActivity.instance != null) {
                        MainActivity.instance.updateIconSizes();
                    }
                    if (FavoritesActivity.instance != null) {
                        FavoritesActivity.instance.updateIconSizes();
                    }

                    Toast.makeText(SettingsActivity.this, "Icon size: " + iconSizeDp + "dp", Toast.LENGTH_SHORT).show();
                }
            });
        }

        int opacity = prefs.getInt(KEY_BG_OPACITY, 100);
        seekBgOpacity.setProgress(opacity);
        txtBgOpacity.setText(opacity + "%");

        // Set up listeners
        switchEditMode.setOnCheckedChangeListener((b, c) -> {
            prefs.edit().putBoolean(KEY_EDIT_MODE, c).apply();
            if (MainActivity.instance != null) MainActivity.instance.refreshEditMode();
        });

        switchAutoStart.setOnCheckedChangeListener((b, c) -> {
            prefs.edit().putBoolean(KEY_AUTO_START, c).apply();
            Toast.makeText(this, c ? "Auto-start enabled - App will launch on boot" : "Auto-start disabled", Toast.LENGTH_SHORT).show();
        });

        switchCategories.setOnCheckedChangeListener((b, c) -> {
            prefs.edit().putBoolean(KEY_SHOW_CATEGORIES, c).apply();
            if (MainActivity.instance != null) {
                new android.os.Handler().postDelayed(() -> {
                    MainActivity.instance.runOnUiThread(() -> {
                        MainActivity.instance.refreshDisplay();
                    });
                }, 200);
            }
        });

        seekBgOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean f) {
                txtBgOpacity.setText(p + "%");
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {
                prefs.edit().putInt(KEY_BG_OPACITY, sb.getProgress()).apply();
                if (MainActivity.instance != null) MainActivity.instance.updateBackground();
            }
        });

        btnDeviceInfo.setOnClickListener(v -> {
            startActivity(new Intent(this, DeviceInfoActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        btnManageCategories.setOnClickListener(v -> showCategoryManager());
        btnBack.setOnClickListener(v -> finish());

        btnGameStats.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, PlaytimeStatsActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });

        // Set initial label based on current permission state
        updateUsageAccessButton(btnUsageAccess);

        btnUsageAccess.setOnClickListener(v -> requestUsageAccessPermission(btnUsageAccess));

        // ADD BACKUP/RESTORE BUTTON LISTENERS
        btnBackup.setOnClickListener(v -> backupLayout());
        btnRestore.setOnClickListener(v -> restoreLayout());

        // ADD QUEST UTILITIES BUTTON LISTENERS
        Button btnResetUI = findViewById(R.id.btnResetUI);
        Button btnRestartUI = findViewById(R.id.btnRestartUI);

        if (btnResetUI != null) {
            btnResetUI.setOnClickListener(v -> resetQuestUI());
        }
        if (btnRestartUI != null) {
            btnRestartUI.setOnClickListener(v -> restartQuestUI());
        }

        // BUNDLED APPS BUTTON
        Button btnBundledApps = findViewById(R.id.btnBundledApps);
        if (btnBundledApps != null) {
            btnBundledApps.setOnClickListener(v -> {
                Intent bundledIntent = new Intent(this, BundledAppsActivity.class);
                startActivity(bundledIntent);
            });
        }

        // APP MANAGER BUTTON
        Button btnAppManager = findViewById(R.id.btnAppManager);
        if (btnAppManager != null) {
            btnAppManager.setOnClickListener(v -> {
                Intent appManagerIntent = new Intent(this, AppManagerActivity.class);
                startActivity(appManagerIntent);
            });
        }

        // THEMES BUTTON - with real-time sync to MainActivity
        Button btnThemes = findViewById(R.id.btnThemes);
        if (btnThemes != null) {
            btnThemes.setOnClickListener(v -> {
                ThemeSelectorDialog.show(this, theme -> {
                    // Apply to SettingsActivity immediately
                    View rootView = findViewById(android.R.id.content);
                    ThemeApplier.applyThemeToHierarchy(rootView);

                    // DIRECT CALL - forces MainActivity to refresh instantly
                    MainActivity.refreshTheme();

                    Toast.makeText(this, "Applied: " + theme.name, Toast.LENGTH_SHORT).show();
                });
            });
        }

        // Setup auto-restart toggle
        setupAutoRestartToggle();

        // Initialize Shizuku for shell commands
        initializeShizuku();

        // Setup version display and update checker
        setupVersionAndUpdates();

        // Apply theme FIRST to entire activity
        View rootView = findViewById(android.R.id.content);
        ThemeApplier.applyThemeToHierarchy(rootView);

        // THEN setup Native Settings button (red/green) - happens after theme so colors persist
        if (btnNativeSettings != null) {
            updateNativeSettingsButton(btnNativeSettings);
        }
    }

    /**
     * Set window size to match the launching activity (MainActivity).
     * MainActivity passes its current window dimensions via Intent extras
     * (window_width, window_height) so the Settings panel renders at the
     * same size as the main launcher, even when the launcher has been
     * resized in Horizon Home.
     *
     * Falls back to Meta's standard size (1024x640 dp) if extras aren't
     * provided (e.g., direct launch via adb).
     */
    private void setMetaStandardWindowSize() {
        Window window = getWindow();
        float density = getResources().getDisplayMetrics().density;

        // Try to read window dimensions passed from MainActivity
        int widthPx = getIntent().getIntExtra("window_width", 0);
        int heightPx = getIntent().getIntExtra("window_height", 0);

        Log.d("SettingsActivity", "Intent extras: " + widthPx + "x" + heightPx + "px");

        // Sanity check - if dimensions are too small (e.g., 0 or phone-size),
        // fall back to Meta standard. This catches cases where the launcher
        // hadn't fully laid out at click time.
        int minAcceptablePx = (int) (700 * density);  // ~700dp minimum
        if (widthPx < minAcceptablePx || heightPx < minAcceptablePx) {
            widthPx = (int) (META_STANDARD_WIDTH_DP * density);
            heightPx = (int) (META_STANDARD_HEIGHT_DP * density);
            Log.d("SettingsActivity", "Using fallback Meta standard: " + widthPx + "x" + heightPx + "px");
        } else {
            Log.d("SettingsActivity", "Using MainActivity dimensions: " + widthPx + "x" + heightPx + "px");
        }

        // Apply via both setAttributes AND setLayout for maximum effect.
        // setAttributes alone can be overridden by the dialog theme's window
        // constraints; setLayout forces the size unconditionally.
        android.view.WindowManager.LayoutParams params = window.getAttributes();
        params.width = widthPx;
        params.height = heightPx;
        params.gravity = android.view.Gravity.CENTER;
        window.setAttributes(params);
        window.setLayout(widthPx, heightPx);

        // Set minimum size so user can't shrink it below comfortable usability
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int minWidthPx = (int) (800 * density);
            int minHeightPx = (int) (500 * density);
            window.getDecorView().setMinimumWidth(minWidthPx);
            window.getDecorView().setMinimumHeight(minHeightPx);
        }

        Log.d("SettingsActivity", "Final window size: " + widthPx + "x" + heightPx + "px");
    }

    /**
     * Send broadcast to MainActivity to update theme in real-time
     */
    private void sendThemeChangedBroadcast() {
        Intent intent = new Intent(ACTION_THEME_CHANGED);
        sendBroadcast(intent);
        Log.d("SettingsActivity", "Theme change broadcast sent");
    }

    /**
     * Tell MainActivity that categories have changed (create/rename/delete/modify).
     * MainActivity will rebuild the category bar and refresh the app cards
     * so removed categories disappear immediately without needing a refresh.
     */
    private void sendCategoriesChangedBroadcast() {
        Intent intent = new Intent(ACTION_CATEGORIES_CHANGED);
        // Explicit package - required on Android 13+ for receivers in separate tasks
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
        Log.d("SettingsActivity", "Categories change broadcast sent to " + getPackageName());
    }

    /**
     * Convert seekbar progress (0-100) to icon size in dp using the old launcher scale mapping
     * This maps to the 5 discrete values: 82, 99, 125, 165, 236
     */
    private int convertSeekProgressToIconSizeDp(int progress) {
        // Map progress ranges to the 5 discrete values
        if (progress <= 20) {
            return ICON_SCALES_DP[0]; // 82dp
        } else if (progress <= 40) {
            return ICON_SCALES_DP[1]; // 99dp
        } else if (progress <= 60) {
            return ICON_SCALES_DP[2]; // 125dp
        } else if (progress <= 80) {
            return ICON_SCALES_DP[3]; // 165dp
        } else {
            return ICON_SCALES_DP[4]; // 236dp
        }
    }

    /**
     * Convert scale index (0-4) to seekbar progress (0-100)
     */
    private int convertScaleIndexToSeekProgress(int scaleIndex) {
        switch (scaleIndex) {
            case 0: return 10;  // 82dp
            case 1: return 30;  // 99dp
            case 2: return 50;  // 125dp (default)
            case 3: return 70;  // 165dp
            case 4: return 90;  // 236dp
            default: return 50;
        }
    }

    /**
     * Convert icon size dp to the closest scale index (0-4)
     */
    private int convertIconSizeDpToScaleIndex(int iconSizeDp) {
        int closestIndex = DEFAULT_SCALE_INDEX;
        int smallestDiff = Math.abs(iconSizeDp - ICON_SCALES_DP[DEFAULT_SCALE_INDEX]);

        for (int i = 0; i < ICON_SCALES_DP.length; i++) {
            int diff = Math.abs(iconSizeDp - ICON_SCALES_DP[i]);
            if (diff < smallestDiff) {
                smallestDiff = diff;
                closestIndex = i;
            }
        }
        return closestIndex;
    }

    /**
     * Native Settings button - launches Quest's Android Settings directly
     * Uses the gotosettings approach (https://github.com/arpruss/gotosettings)
     * No external APK needed - just uses standard Android intents
     */
    private static final String SETTINGS_PACKAGE = "com.android.settings";

    private void updateNativeSettingsButton(Button btn) {
        // Mark button to be ignored by theme system - we manage colors manually
        btn.setTag("theme_ignore");

        // Always show "Open" - Settings is built into Android, always available
        btn.setText("Open");
        btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#4CAF50")));
        btn.setTextColor(android.graphics.Color.WHITE);
        btn.setOnClickListener(v -> goToSettings());
    }

    /**
     * Launch Quest's native Android Settings.
     * Shows setup instructions dialog. Settings only opens from this dialog,
     * and the dialog STAYS OPEN so the user can refer to it while configuring.
     */
    private void goToSettings() {
        // Build the dialog
        final androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Quest Developer Setup")
                .setMessage(
                        "Tap 'Open Settings' below - this dialog will stay open so you can refer to these steps:\n\n" +
                                "1️⃣ ENABLE DEVELOPER OPTIONS:\n" +
                                "   • Go to: About Headset\n" +
                                "   • Tap 'Build Number' 7 times\n" +
                                "   • Developer mode enabled!\n\n" +
                                "2️⃣ ENABLE WIRELESS DEBUGGING:\n" +
                                "   • Back out, go to: System > Developer Options\n" +
                                "   • Turn ON 'Wireless Debugging'\n" +
                                "   • Tap LEFT side of the text\n" +
                                "   • Select 'Pair device with pairing code'\n\n" +
                                "3️⃣ CONNECT SHIZUKU:\n" +
                                "   • Note the IP, port, and pairing code\n" +
                                "   • Open the Shizuku app\n" +
                                "   • Enter the pairing info to connect\n\n" +
                                "4️⃣ AUTHORIZE APPS IN SHIZUKU:\n" +
                                "   • Tap 'Authorized Applications'\n" +
                                "   • Toggle ON: Evolve Launcher\n" +
                                "   • Toggle ON: ZArchiver (if using file manager)\n\n" +
                                "✅ Your launcher will then have full system access!")
                // Listeners null - we override AFTER show so dialog doesn't auto-dismiss
                .setPositiveButton("Open Settings", null)
                .setNegativeButton("Close", null)
                .setCancelable(true)
                .create();

        // Show with theme applied (this fires its own OnShowListener for theming)
        ThemedDialog.showThemed(dialog);

        // NOW set our button click overrides (after the dialog is shown and themed)
        // This must happen AFTER showThemed because that sets its own OnShowListener
        Button openBtn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
        if (openBtn != null) {
            openBtn.setOnClickListener(v -> {
                openSettingsDirectly();
                Toast.makeText(this, "Dialog stays open - tap Close when done", Toast.LENGTH_SHORT).show();
                // NOTE: NOT calling dialog.dismiss() - keeps dialog visible for reference
            });
        }

        Button closeBtn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> dialog.dismiss());
        }
    }

    /**
     * Actually open Settings - tries Shizuku first, falls back to intent.
     *
     * Tries 3 methods in order of preference:
     * 1. Shizuku 'am start' - DIRECT, no extra screens (best UX)
     * 2. Standard launch intent with TASK_ON_HOME flag (gotosettings approach)
     * 3. Application details fallback
     */
    private void openSettingsDirectly() {
        // Method 1: Try Shizuku for direct launch (best UX - no extra clicks)
        if (shizukuManager != null && shizukuManager.isReady()) {
            try {
                // DeepLinkHomepageActivity opens Settings directly without App Info screen
                shizukuManager.executeShellCommand(
                        "am start -n com.android.settings/.homepage.DeepLinkHomepageActivity"
                );
                return;
            } catch (Exception e) {
                android.util.Log.w("SettingsActivity", "Shizuku launch failed, trying intent", e);
            }
        }

        // Method 2: Standard launch intent (gotosettings approach - may show App Info on v81+)
        PackageManager pm = getPackageManager();
        try {
            Intent i = pm.getLaunchIntentForPackage(SETTINGS_PACKAGE);
            if (i != null) {
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
                startActivity(i);
                return;
            }
            throw new Exception("No launch intent");
        } catch (Exception e) {
            // Method 3: Application details fallback
            try {
                Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:" + SETTINGS_PACKAGE));
                i.setPackage(SETTINGS_PACKAGE);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
                startActivity(i);
            } catch (Exception e2) {
                Toast.makeText(this, "Cannot open Android Settings on this device", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    // ===== USAGE ACCESS PERMISSION =====

    @Override
    protected void onResume() {
        super.onResume();
        // Re-check permission state when user returns from the settings screen
        AppCompatButton btnUsageAccess = findViewById(R.id.btnUsageAccess);
        if (btnUsageAccess != null) {
            updateUsageAccessButton(btnUsageAccess);
        }

        // Re-apply theme FIRST in case it changed
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            ThemeApplier.applyThemeToHierarchy(rootView);
        }

        // THEN re-check Native Settings install state (overrides theme colors)
        Button btnNativeSettings = findViewById(R.id.btnNativeSettings);
        if (btnNativeSettings != null) {
            updateNativeSettingsButton(btnNativeSettings);
        }
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                getPackageName()
        );
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void updateUsageAccessButton(AppCompatButton btn) {
        if (hasUsageStatsPermission()) {
            btn.setText("✅ Usage Access: Granted");
            btn.setSupportBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50")));
        } else {
            btn.setText("🔓 Grant Usage Access Permission");
            btn.setSupportBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F44336")));
        }
    }

    private void requestUsageAccessPermission(AppCompatButton btn) {
        if (hasUsageStatsPermission()) {
            Toast.makeText(this, "Usage Access already granted!", Toast.LENGTH_SHORT).show();
            return;
        }
        ThemedDialog.showThemed(new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Usage Access Required")
                .setMessage("This allows the launcher to track game playtime and show stats.\n\nTap OK to open the permission screen, then find your launcher app and enable the toggle.")
                .setPositiveButton("Open Settings", (d, w) -> {
                    try {
                        // Deep-link directly to this app's usage access entry (avoids Quest 2 list crash)
                        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, REQUEST_CODE_USAGE_ACCESS);
                    } catch (ActivityNotFoundException e) {
                        // Fallback to the general usage access list
                        try {
                            startActivityForResult(
                                    new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                                    REQUEST_CODE_USAGE_ACCESS);
                        } catch (ActivityNotFoundException ex) {
                            Toast.makeText(this, "Settings screen not available on this device", Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .create());
    }

    // ===== BACKUP & RESTORE METHODS =====

    private void backupLayout() {
        try {
            JSONObject backup = new JSONObject();

            // Save VRLPrefs - preserve types properly
            JSONObject prefsData = new JSONObject();
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                Object value = entry.getValue();

                if (value instanceof Boolean) {
                    prefsData.put(entry.getKey(), (Boolean) value);
                } else if (value instanceof Integer) {
                    prefsData.put(entry.getKey(), (Integer) value);
                } else if (value instanceof Long) {
                    prefsData.put(entry.getKey(), (Long) value);
                } else if (value instanceof Float) {
                    prefsData.put(entry.getKey(), (Float) value);
                } else if (value instanceof Set) {
                    continue;
                } else {
                    prefsData.put(entry.getKey(), String.valueOf(value));
                }
            }
            backup.put("vrprefs", prefsData);

            // Save categories
            JSONObject categoriesData = new JSONObject();
            for (Map.Entry<String, ?> entry : categoryPrefs.getAll().entrySet()) {
                if (entry.getValue() instanceof Set) {
                    JSONArray setArray = new JSONArray((Set) entry.getValue());
                    categoriesData.put(entry.getKey(), setArray);
                }
            }
            backup.put("categories", categoriesData);

            String fileName = "vrlauncher_backup_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".json";

            saveBackupDirectly(backup, fileName);

        } catch (Exception e) {
            Toast.makeText(this, "Backup failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveBackupDirectly(JSONObject backup, String fileName) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    ThemedDialog.showThemed(new AlertDialog.Builder(this)
                            .setTitle("Storage Permission Needed")
                            .setMessage("To save backups to /sdcard/, we need storage access permission.\n\nWould you like to grant this permission now?")
                            .setPositiveButton("Grant Permission", (dialog, which) -> {
                                try {
                                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                                    intent.setData(uri);
                                    startActivity(intent);
                                    Toast.makeText(this, "Please enable 'Allow management of all files' and try backup again", Toast.LENGTH_LONG).show();
                                } catch (Exception e) {
                                    android.util.Log.e("SettingsActivity", "Failed to open settings", e);
                                    Toast.makeText(this, "Please manually enable storage permission in Settings", Toast.LENGTH_LONG).show();
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .create());
                    return;
                }
            }

            File externalStorage = Environment.getExternalStorageDirectory();
            File backupDir = new File(externalStorage, "evolve_backups");

            if (!backupDir.exists()) {
                boolean created = backupDir.mkdirs();
                if (!created && !backupDir.exists()) {
                    throw new Exception("Failed to create backup directory. Please grant storage permission in Settings.");
                }
            }

            if (!backupDir.exists() || !backupDir.canWrite()) {
                throw new Exception("Backup directory not writable. Please grant storage permission in Settings.");
            }

            File backupFile = new File(backupDir, fileName);
            FileOutputStream fos = new FileOutputStream(backupFile);
            fos.write(backup.toString(2).getBytes());
            fos.flush();
            fos.close();

            try {
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle("✅ Backup Successful")
                        .setMessage("Backup saved to:\n\n/sdcard/evolve_backups/" + fileName)
                        .setPositiveButton("OK", null)
                        .create();
                ThemedDialog.showThemed(dialog);
            } catch (Exception dialogEx) {
                Toast.makeText(this, "Backup saved to:\n/sdcard/evolve_backups/" + fileName, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            android.util.Log.e("SettingsActivity", "Fallback backup failed: " + e.getMessage(), e);
            ThemedDialog.showThemed(new AlertDialog.Builder(this)
                    .setTitle("❌ Backup Failed")
                    .setMessage(e.getMessage())
                    .setPositiveButton("OK", null)
                    .create());
        }
    }

    private void restoreLayout() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri initialUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3A");
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
        }

        startActivityForResult(intent, REQUEST_CODE_OPEN_BACKUP);
    }

    // ===== AUTO-CLOSE WHEN FOCUS RETURNS TO LAUNCHER =====

    @Override
    public void startActivity(Intent intent) {
        // Mark that we're launching a sub-activity so the auto-close logic
        // doesn't fire us while we're temporarily in the background.
        expectingFocusReturn = true;
        super.startActivity(intent);
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        expectingFocusReturn = true;
        super.startActivityForResult(intent, requestCode);
    }

    /**
     * Called when this activity gains or loses the "top resumed activity"
     * status. This is the right signal for auto-closing: it fires when
     * ANOTHER activity becomes top (user clicked launcher, or we launched
     * a sub-activity), but NOT when dialogs open on top of us (because
     * dialogs are part of our own window).
     *
     * Available since Android 10 (API 29). Quest 3 runs Android 14.
     */
    @Override
    public void onTopResumedActivityChanged(boolean isTopResumedActivity) {
        super.onTopResumedActivityChanged(isTopResumedActivity);

        if (isTopResumedActivity) {
            // We became the top activity (just opened, or a sub-activity returned).
            expectingFocusReturn = false;
            return;
        }

        if (isFinishing() || expectingFocusReturn) {
            return;
        }

        // We lost top-resumed status and weren't expecting it. The user
        // tapped on the main launcher panel - close ourselves.
        Log.d("SettingsActivity", "Auto-closing - launcher gained focus");
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Sub-activity returned - clear the flag so future focus loss closes us.
        expectingFocusReturn = false;

        if (com.jarjarblinkz.EvolveLauncher.theme.ThemeSelectorDialog.handleImageResult(requestCode, resultCode, data)) {
            return;
        }

        if (requestCode == REQUEST_CODE_USAGE_ACCESS) {
            if (hasUsageStatsPermission()) {
                Toast.makeText(this, "Usage Access granted! Playtime tracking is now active.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Usage Access not granted. Playtime stats won't be available.", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_CODE_CREATE_BACKUP) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                saveBackupToUri(data.getData());
            }
        } else if (requestCode == REQUEST_CODE_OPEN_BACKUP) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                restoreFromUri(data.getData());
            }
        }
    }

    private void saveBackupToUri(Uri uri) {
        try {
            JSONObject backup = new JSONObject();

            JSONObject prefsData = new JSONObject();
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Boolean) {
                    prefsData.put(entry.getKey(), (Boolean) value);
                } else if (value instanceof Integer) {
                    prefsData.put(entry.getKey(), (Integer) value);
                } else if (value instanceof Long) {
                    prefsData.put(entry.getKey(), (Long) value);
                } else if (value instanceof Float) {
                    prefsData.put(entry.getKey(), (Float) value);
                } else if (value instanceof Set) {
                    continue;
                } else {
                    prefsData.put(entry.getKey(), String.valueOf(value));
                }
            }
            backup.put("vrprefs", prefsData);

            JSONObject categoriesData = new JSONObject();
            for (Map.Entry<String, ?> entry : categoryPrefs.getAll().entrySet()) {
                if (entry.getValue() instanceof Set) {
                    JSONArray setArray = new JSONArray((Set) entry.getValue());
                    categoriesData.put(entry.getKey(), setArray);
                }
            }
            backup.put("categories", categoriesData);

            getContentResolver().openOutputStream(uri).write(backup.toString(2).getBytes());

            Toast.makeText(this, "Backup saved successfully!", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this, "Backup failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void restoreFromUri(Uri uri) {
        try {
            byte[] data = new byte[getContentResolver().openInputStream(uri).available()];
            getContentResolver().openInputStream(uri).read(data);

            String backupStr = new String(data);
            JSONObject backup = new JSONObject(backupStr);

            if (!backup.has("vrprefs") || !backup.has("categories")) {
                Toast.makeText(this, "Invalid backup file - missing required data", Toast.LENGTH_LONG).show();
                return;
            }

            SharedPreferences.Editor prefsEditor = prefs.edit();
            prefsEditor.clear();

            JSONObject prefsData = backup.getJSONObject("vrprefs");
            JSONArray keys = prefsData.names();

            if (keys == null || keys.length() == 0) {
                Toast.makeText(this, "Backup contains no preference data", Toast.LENGTH_LONG).show();
                return;
            }

            for (int i = 0; i < keys.length(); i++) {
                String key = keys.getString(i);
                Object value = prefsData.get(key);

                if (key != null && !key.isEmpty()) {
                    if (value instanceof Boolean) {
                        prefsEditor.putBoolean(key, (Boolean) value);
                    } else if (value instanceof Integer) {
                        prefsEditor.putInt(key, (Integer) value);
                    } else if (value instanceof Long) {
                        prefsEditor.putLong(key, (Long) value);
                    } else if (value instanceof Double) {
                        prefsEditor.putFloat(key, ((Double) value).floatValue());
                    } else {
                        prefsEditor.putString(key, String.valueOf(value));
                    }
                }
            }
            boolean prefsApplied = prefsEditor.commit();

            if (!prefsApplied) {
                Toast.makeText(this, "Failed to save preferences", Toast.LENGTH_LONG).show();
                return;
            }

            SharedPreferences.Editor catEditor = categoryPrefs.edit();
            catEditor.clear();

            JSONObject categoriesData = backup.getJSONObject("categories");
            JSONArray catKeys = categoriesData.names();
            if (catKeys != null) {
                for (int i = 0; i < catKeys.length(); i++) {
                    String key = catKeys.getString(i);
                    if (key == null || !key.startsWith("cat_")) {
                        continue;
                    }

                    JSONArray setArray = categoriesData.getJSONArray(key);
                    Set<String> set = new HashSet<>();
                    for (int j = 0; j < setArray.length(); j++) {
                        String packageName = setArray.getString(j);
                        if (packageName != null && !packageName.isEmpty()) {
                            set.add(packageName);
                        }
                    }
                    catEditor.putStringSet(key, set);
                }
            }
            boolean catApplied = catEditor.commit();

            if (!catApplied) {
                Toast.makeText(this, "Failed to save categories", Toast.LENGTH_LONG).show();
                return;
            }

            ThemedDialog.showThemed(new AlertDialog.Builder(this)
                    .setTitle("Restore Complete")
                    .setMessage("Settings restored successfully!\n\nRestart the launcher now to apply changes?")
                    .setPositiveButton("Restart Now", (d, w) -> {
                        android.os.Process.killProcess(android.os.Process.myPid());
                    })
                    .setNegativeButton("Later", (d, w) -> {
                        Toast.makeText(this, "Changes will apply after restart", Toast.LENGTH_LONG).show();
                    })
                    .setCancelable(false)
                    .create());

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Restore failed: " + e.getMessage(), Toast.LENGTH_LONG).show();

            ThemedDialog.showThemed(new AlertDialog.Builder(this)
                    .setTitle("Restore Failed")
                    .setMessage("The backup file may be corrupted.\n\nWould you like to clear all settings and start fresh?")
                    .setPositiveButton("Clear Settings", (d, w) -> {
                        prefs.edit().clear().commit();
                        categoryPrefs.edit().clear().commit();
                        Toast.makeText(this, "Settings cleared. Restart app.", Toast.LENGTH_SHORT).show();
                        android.os.Process.killProcess(android.os.Process.myPid());
                    })
                    .setNegativeButton("Cancel", null)
                    .create());
        }
    }

    // ===== END BACKUP & RESTORE METHODS =====

    private void showCreateCategoryDialog() {
        Set<String> existingCategories = new HashSet<>();
        Map<String, ?> all = categoryPrefs.getAll();
        for (String key : all.keySet()) {
            if (key.startsWith("cat_")) {
                existingCategories.add(key.substring(4));
            }
        }

        final EditText input = new EditText(this);

        ThemedDialog.showThemed(new AlertDialog.Builder(this)
                .setTitle("Create Category")
                .setMessage("Enter category name:")
                .setView(input)
                .setPositiveButton("Create", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        if (existingCategories.contains(name)) {
                            Toast.makeText(this, "Category '" + name + "' already exists!", Toast.LENGTH_SHORT).show();
                            showCreateCategoryDialog();
                        } else {
                            categoryPrefs.edit().putStringSet("cat_" + name, new HashSet<>()).apply();
                            sendCategoriesChangedBroadcast();
                            Toast.makeText(this, "Category '" + name + "' created", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .create());
    }

    private void showCategoryManager() {
        Map<String, ?> allEntries = categoryPrefs.getAll();

        List<String> categoryNames = new ArrayList<>();

        for (String key : allEntries.keySet()) {
            if (key.startsWith("cat_")) {
                String categoryName = key.substring(4);
                categoryNames.add(categoryName);
            }
        }

        Collections.sort(categoryNames);

        if (categoryNames.isEmpty()) {
            ThemedDialog.showThemed(new AlertDialog.Builder(this)
                    .setTitle("Category Manager")
                    .setMessage("No categories created yet.")
                    .setPositiveButton("Create New", (d, w) -> showCreateCategoryDialog())
                    .setNegativeButton("Cancel", null)
                    .create());
            return;
        }

        final String[] categoriesArray = new String[categoryNames.size()];
        for (int i = 0; i < categoryNames.size(); i++) {
            categoriesArray[i] = categoryNames.get(i);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Category Manager (" + categoryNames.size() + " categories)")
                .setItems(categoriesArray, (d, w) -> {
                    String selectedCategory = categoriesArray[w];
                    showCategoryOptions(selectedCategory);
                })
                .setPositiveButton("Create New", (d, w) -> showCreateCategoryDialog());

        if (!categoryNames.isEmpty()) {
            builder.setNeutralButton("Delete All", (d, w) -> {
                showDeleteAllConfirmation();
            });
        }

        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        ThemedDialog.showThemed(dialog);
    }

    private void showCategoryOptions(String categoryName) {
        Set<String> appsInCategory = categoryPrefs.getStringSet("cat_" + categoryName, new HashSet<>());
        int appCount = appsInCategory != null ? appsInCategory.size() : 0;

        String[] options = {
                "View Apps (" + appCount + " apps)",
                "Rename Category",
                "Delete Category"
        };

        ThemedDialog.showThemed(new AlertDialog.Builder(this)
                .setTitle("Category: " + categoryName)
                .setItems(options, (d, w) -> {
                    switch (w) {
                        case 0:
                            showAppsInCategory(categoryName, appsInCategory);
                            break;
                        case 1:
                            showRenameCategoryDialog(categoryName);
                            break;
                        case 2:
                            showDeleteCategoryConfirmation(categoryName);
                            break;
                    }
                })
                .setNegativeButton("Back", (d, w) -> showCategoryManager())
                .create());
    }

    private void showAppsInCategory(String categoryName, Set<String> appPackages) {
        if (appPackages == null || appPackages.isEmpty()) {
            ThemedDialog.showThemed(new AlertDialog.Builder(this)
                    .setTitle("Apps in " + categoryName)
                    .setMessage("No apps in this category.")
                    .setPositiveButton("OK", null)
                    .create());
            return;
        }

        List<String> appNames = new ArrayList<>();
        PackageManager pm = getPackageManager();

        for (String packageName : appPackages) {
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                String appName = pm.getApplicationLabel(appInfo).toString();
                appNames.add(appName + "\n  (" + packageName + ")");
            } catch (PackageManager.NameNotFoundException e) {
                appNames.add("[Unknown App]\n  (" + packageName + ")");
            }
        }

        Collections.sort(appNames);

        String[] appNamesArray = appNames.toArray(new String[0]);

        ThemedDialog.showThemed(new AlertDialog.Builder(this)
                .setTitle("Apps in " + categoryName + " (" + appPackages.size() + " apps)")
                .setItems(appNamesArray, null)
                .setPositiveButton("OK", null)
                .create());
    }

    private void showRenameCategoryDialog(String oldName) {
        final EditText input = new EditText(this);
        input.setText(oldName);
        input.setSelection(input.getText().length());

        ThemedDialog.showThemed(new AlertDialog.Builder(this)
                .setTitle("Rename Category")
                .setMessage("Enter new name for " + oldName)
                .setView(input)
                .setPositiveButton("Rename", (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty() && !newName.equals(oldName)) {
                        renameCategory(oldName, newName);
                    }
                })
                .setNegativeButton("Cancel", null)
                .create());
    }

    private void renameCategory(String oldName, String newName) {
        Set<String> existingCategories = new HashSet<>();
        Map<String, ?> all = categoryPrefs.getAll();
        for (String key : all.keySet()) {
            if (key.startsWith("cat_")) {
                existingCategories.add(key.substring(4));
            }
        }

        if (existingCategories.contains(newName)) {
            Toast.makeText(this, "Category '" + newName + "' already exists!", Toast.LENGTH_SHORT).show();
            return;
        }

        Set<String> apps = categoryPrefs.getStringSet("cat_" + oldName, new HashSet<>());

        categoryPrefs.edit()
                .putStringSet("cat_" + newName, apps != null ? new HashSet<>(apps) : new HashSet<>())
                .remove("cat_" + oldName)
                .apply();

        // Update per-app category references in main prefs
        if (apps != null && !apps.isEmpty()) {
            SharedPreferences.Editor editor = prefs.edit();
            for (String packageName : apps) {
                editor.putString("cat_" + packageName, newName);
            }
            editor.apply();
        }

        sendCategoriesChangedBroadcast();

        Toast.makeText(this, "Category renamed to " + newName, Toast.LENGTH_SHORT).show();
        showCategoryManager();
    }

    private void showDeleteCategoryConfirmation(String categoryName) {
        Set<String> appsInCategory = categoryPrefs.getStringSet("cat_" + categoryName, new HashSet<>());
        int appCount = appsInCategory != null ? appsInCategory.size() : 0;

        String message = "Delete category '" + categoryName + "'?";
        if (appCount > 0) {
            message += "\n\nThis will remove " + appCount + " app(s) from this category.\nThey will reappear in 'All Apps'.";
        }

        ThemedDialog.showThemed(new AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage(message)
                .setPositiveButton("Delete", (d, w) -> {
                    deleteCategory(categoryName);
                })
                .setNegativeButton("Cancel", null)
                .create());
    }

    private void deleteCategory(String categoryName) {
        // CRITICAL: Also clear per-app category references in main prefs.
        // Without this, restarting the launcher would re-apply the deleted
        // category to the same apps (badge reappears on cards).
        Set<String> appsInCategory = categoryPrefs.getStringSet("cat_" + categoryName, new HashSet<>());
        if (appsInCategory != null && !appsInCategory.isEmpty()) {
            SharedPreferences.Editor editor = prefs.edit();
            for (String packageName : appsInCategory) {
                editor.remove("cat_" + packageName);
            }
            editor.apply();
        }

        categoryPrefs.edit()
                .remove("cat_" + categoryName)
                .apply();

        sendCategoriesChangedBroadcast();

        Toast.makeText(this, "Category '" + categoryName + "' deleted", Toast.LENGTH_SHORT).show();
        showCategoryManager();
    }

    private void showDeleteAllConfirmation() {
        Map<String, ?> all = categoryPrefs.getAll();
        int categoryCount = 0;
        int totalApps = 0;

        for (String key : all.keySet()) {
            if (key.startsWith("cat_")) {
                categoryCount++;
                Set<String> apps = categoryPrefs.getStringSet(key, new HashSet<>());
                if (apps != null) {
                    totalApps += apps.size();
                }
            }
        }

        String message = "Delete ALL " + categoryCount + " categories?";
        if (totalApps > 0) {
            message += "\n\nThis will remove " + totalApps + " app(s) from all categories.\nThey will reappear in 'All Apps'.";
        }

        ThemedDialog.showThemed(new AlertDialog.Builder(this)
                .setTitle("Delete All Categories")
                .setMessage(message)
                .setPositiveButton("Delete All", (d, w) -> {
                    // Clear per-app category references from main prefs first
                    Set<String> allCategorizedPackages = new HashSet<>();
                    for (Map.Entry<String, ?> entry : categoryPrefs.getAll().entrySet()) {
                        if (entry.getKey().startsWith("cat_")) {
                            Set<String> apps = categoryPrefs.getStringSet(entry.getKey(), null);
                            if (apps != null) allCategorizedPackages.addAll(apps);
                        }
                    }
                    SharedPreferences.Editor editor = prefs.edit();
                    for (String packageName : allCategorizedPackages) {
                        editor.remove("cat_" + packageName);
                    }
                    editor.apply();

                    categoryPrefs.edit().clear().apply();
                    sendCategoriesChangedBroadcast();
                    Toast.makeText(this, "All categories deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .create());
    }

    // ===== AUTO-RESTART TOGGLE =====

    private void setupAutoRestartToggle() {
        SwitchCompat autoRestartSwitch = findViewById(R.id.autoRestartSwitch);
        TextView autoRestartStatus = findViewById(R.id.autoRestartStatus);

        if (autoRestartSwitch == null || autoRestartStatus == null) {
            Log.w("SettingsActivity", "Auto-restart UI elements not found in layout");
            return;
        }

        boolean isEnabled = prefs.getBoolean("auto_restart_enabled", true);
        autoRestartSwitch.setChecked(isEnabled);
        updateAutoRestartStatus(autoRestartStatus, isEnabled);

        autoRestartSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("auto_restart_enabled", isChecked).apply();
            updateAutoRestartStatus(autoRestartStatus, isChecked);

            Intent serviceIntent = new Intent(this, VRShellMonitorService.class);

            if (isChecked) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent);
                    } else {
                        startService(serviceIntent);
                    }
                    Toast.makeText(this, "Auto-restart enabled", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e("SettingsActivity", "Failed to start service", e);
                    Toast.makeText(this, "Failed to enable auto-restart", Toast.LENGTH_SHORT).show();
                }
            } else {
                try {
                    stopService(serviceIntent);
                    Toast.makeText(this, "Auto-restart disabled", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e("SettingsActivity", "Failed to stop service", e);
                }
            }
        });
    }

    private void updateAutoRestartStatus(TextView statusText, boolean isEnabled) {
        if (statusText == null) return;

        if (isEnabled) {
            statusText.setText(R.string.auto_restart_enabled);
            statusText.setTextColor(0xFF4CAF50);
        } else {
            statusText.setText(R.string.auto_restart_disabled);
            statusText.setTextColor(0xFF9E9E9E);
        }
    }

    // ===== VERSION & UPDATES =====

    private void setupVersionAndUpdates() {
        TextView txtCurrentVersion = findViewById(R.id.txtCurrentVersion);
        AppCompatButton btnCheckUpdates = findViewById(R.id.btnCheckUpdates);
        SwitchCompat switchAutoUpdate = findViewById(R.id.switchAutoUpdate);

        if (txtCurrentVersion != null) {
            try {
                String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                txtCurrentVersion.setText(versionName);
            } catch (Exception e) {
                txtCurrentVersion.setText("Unknown");
            }
        }

        if (switchAutoUpdate != null) {
            boolean autoUpdateEnabled = prefs.getBoolean("auto_update_enabled", true);
            switchAutoUpdate.setChecked(autoUpdateEnabled);

            switchAutoUpdate.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean("auto_update_enabled", isChecked).apply();
                Toast.makeText(this,
                        isChecked ? "Auto-update enabled" : "Auto-update disabled",
                        Toast.LENGTH_SHORT).show();
            });
        }

        if (btnCheckUpdates != null) {
            btnCheckUpdates.setOnClickListener(v -> {
                Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show();

                UpdateManager updateManager = new UpdateManager(this);

                updateManager.checkForUpdates(new UpdateManager.UpdateCallback() {
                    @Override
                    public void onUpdateAvailable(String version, String downloadUrl, String releaseNotes) {
                        android.widget.LinearLayout container = new android.widget.LinearLayout(SettingsActivity.this);
                        container.setOrientation(android.widget.LinearLayout.VERTICAL);
                        int padding = (int) (16 * getResources().getDisplayMetrics().density);
                        container.setPadding(padding, padding, padding, padding);

                        android.widget.TextView versionInfo = new android.widget.TextView(SettingsActivity.this);
                        versionInfo.setText("Version " + version + " is available!");
                        versionInfo.setTextSize(13);
                        versionInfo.setTypeface(null, android.graphics.Typeface.BOLD);
                        versionInfo.setPadding(0, 0, 0, padding);
                        container.addView(versionInfo);

                        android.widget.TextView notesView = new android.widget.TextView(SettingsActivity.this);
                        notesView.setTextSize(12);
                        notesView.setLineSpacing(0, 1.2f);
                        notesView.setTextIsSelectable(true);

                        String html = UpdateManager.markdownToHtml(releaseNotes);
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                            notesView.setText(android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_COMPACT));
                        } else {
                            notesView.setText(android.text.Html.fromHtml(html));
                        }
                        notesView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());

                        android.widget.ScrollView scroll = new android.widget.ScrollView(SettingsActivity.this);
                        scroll.addView(notesView);
                        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.6);
                        scroll.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, maxHeight));
                        container.addView(scroll);

                        ThemedDialog.showThemed(new AlertDialog.Builder(SettingsActivity.this)
                                .setTitle("Update Available! 🎉")
                                .setView(container)
                                .setPositiveButton("Download", (d, w) -> {
                                    updateManager.downloadAndInstall(downloadUrl);
                                })
                                .setNegativeButton("Later", null)
                                .create());
                    }

                    @Override
                    public void onNoUpdateAvailable(String currentVersion) {
                        ThemedDialog.showThemed(new AlertDialog.Builder(SettingsActivity.this)
                                .setTitle("Up to Date ✓")
                                .setMessage("You're already running the latest version!\n\nCurrent version: " + currentVersion)
                                .setPositiveButton("OK", null)
                                .create());
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(SettingsActivity.this, error, Toast.LENGTH_LONG).show();
                    }
                });
            });
        }
    }

    private void launchNativeSettings() {
        goToSettings();
    }

    // ===== SHIZUKU INTEGRATION =====

    private void initializeShizuku() {
        shizukuManager = new ShizukuManager(this);

        shizukuManager.initialize(new ShizukuManager.ShizukuStatusListener() {
            @Override
            public void onStatusChanged(boolean available, boolean hasPermission) {
                Log.i("SettingsActivity", String.format("Shizuku status - Available: %b, Permission: %b",
                        available, hasPermission));
            }

            @Override
            public void onCommandResult(boolean success, String output) {
                runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(SettingsActivity.this, "✅ " + output, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(SettingsActivity.this, "⚠️ " + output, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void showShizukuSetupDialog() {
        ShizukuInstaller installer = new ShizukuInstaller(this);
        ShizukuInstaller.InstallStatus status = installer.getStatus();

        switch (status) {
            case NOT_INSTALLED:
                showInstallShizukuDialog(installer);
                break;
            case INSTALLED_NOT_RUNNING:
                showStartShizukuDialog(installer);
                break;
            case RUNNING:
                showGrantPermissionDialog();
                break;
            case NOT_BUNDLED:
            default:
                showManualSetupDialog();
                break;
        }
    }

    private void showInstallShizukuDialog(ShizukuInstaller installer) {
        ThemedDialog.showThemed(new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("📦 Install Shizuku")
                .setMessage("Shizuku is required for advanced Quest features like resetting the UI.\n\n" +
                        "✅ Shizuku APK is bundled with Evolve\n" +
                        "✅ One-tap install - no downloads needed\n" +
                        "✅ Free and open source\n\n" +
                        "Would you like to install it now?")
                .setPositiveButton("Install", (dialog, which) -> {
                    boolean started = installer.installShizuku();
                    if (started) {
                        Toast.makeText(SettingsActivity.this, "Follow the installer prompts...", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(SettingsActivity.this, "❌ Failed to start installer", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Not Now", null)
                .create());
    }

    private void showStartShizukuDialog(ShizukuInstaller installer) {
        String instructions = shizukuManager != null ?
                shizukuManager.getSetupInstructions() : "Configure Shizuku to enable features";

        ThemedDialog.showThemed(new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🚀 Start Shizuku Server")
                .setMessage("✅ Shizuku is installed!\n\n" +
                        "Now you need to start the Shizuku server:\n\n" +
                        instructions)
                .setPositiveButton("Open Shizuku", (dialog, which) -> {
                    if (!installer.launchShizukuApp()) {
                        Toast.makeText(SettingsActivity.this, "❌ Could not open Shizuku app", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Close", null)
                .create());
    }

    private void showGrantPermissionDialog() {
        ThemedDialog.showThemed(new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🔓 Grant Permission")
                .setMessage("✅ Shizuku is running!\n\n" +
                        "Evolve needs permission from Shizuku to execute Quest commands.\n\n" +
                        "Tap 'Grant' and then 'Allow' in the Shizuku popup.")
                .setPositiveButton("Grant", (dialog, which) -> {
                    if (shizukuManager != null) {
                        shizukuManager.requestPermission();
                    }
                })
                .setNegativeButton("Cancel", null)
                .create());
    }

    private void showManualSetupDialog() {
        String status = shizukuManager != null ? shizukuManager.getStatusMessage() : "⚠️ Shizuku not initialized";
        String instructions = shizukuManager != null ? shizukuManager.getSetupInstructions() : "";

        ThemedDialog.showThemed(new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Shizuku Setup Required")
                .setMessage(status + "\n\n" + instructions)
                .setPositiveButton("Grant Permission", (dialog, which) -> {
                    if (shizukuManager != null) {
                        shizukuManager.requestPermission();
                    }
                })
                .setNegativeButton("Close", null)
                .create());
    }

    // ===== QUEST UTILITIES =====

    private void resetQuestUI() {
        if (shizukuManager == null || !shizukuManager.isReady()) {
            showShizukuSetupDialog();
            if (shizukuManager != null) {
                shizukuManager.recheckStatus();
            }
            return;
        }

        ThemedDialog.showThemed(new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Clear VrShell Data?")
                .setMessage("This will reset Quest UI to old style.\n\nAll VrShell settings will be reset.")
                .setPositiveButton("Clear Data", (dialog, which) -> {
                    Toast.makeText(this, "Clearing VrShell data...", Toast.LENGTH_SHORT).show();
                    shizukuManager.clearPackageData("com.oculus.vrshell");
                })
                .setNegativeButton("Cancel", null)
                .create());
    }

    private void restartQuestUI() {
        if (shizukuManager == null || !shizukuManager.isReady()) {
            showShizukuSetupDialog();
            if (shizukuManager != null) {
                shizukuManager.recheckStatus();
            }
            return;
        }

        ThemedDialog.showThemed(new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Force Stop VrShell?")
                .setMessage("This will stop VrShell.\n\nPress Quest button to restart it.")
                .setPositiveButton("Force Stop", (dialog, which) -> {
                    Toast.makeText(this, "Stopping VrShell...", Toast.LENGTH_SHORT).show();
                    shizukuManager.forceStopPackage("com.oculus.vrshell");
                })
                .setNegativeButton("Cancel", null)
                .create());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (shizukuManager != null) {
            shizukuManager.cleanup();
        }
    }
}