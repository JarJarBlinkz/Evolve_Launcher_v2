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

    // Shizuku manager for shell-level commands
    private ShizukuManager shizukuManager;

    private static final String PREFS_NAME = "VRLPrefs";
    private static final String CATEGORY_PREFS = "vr_categories";

    private static final String KEY_EDIT_MODE = "edit_mode";
    private static final String KEY_ICON_SIZE = "icon_size";
    private static final String KEY_SHOW_CATEGORIES = "show_categories";
    private static final String KEY_BG_OPACITY = "background_opacity";
    private static final String KEY_AUTO_START = "auto_start";

    private static final int REQUEST_CODE_CREATE_BACKUP = 200;
    private static final int REQUEST_CODE_OPEN_BACKUP = 201;
    private static final int REQUEST_CODE_USAGE_ACCESS = 202;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_settings);

        Window window = getWindow();
        android.view.WindowManager.LayoutParams lp = window.getAttributes();
        lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
        lp.height = (int) (getResources().getDisplayMetrics().heightPixels * 1.00);
        window.setAttributes(lp);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        categoryPrefs = getSharedPreferences(CATEGORY_PREFS, MODE_PRIVATE);

        // Initialize all views
        SwitchCompat switchEditMode = findViewById(R.id.switchEditMode);
        SwitchCompat switchCategories = findViewById(R.id.switchCategories);
        SeekBar seekIconSize = findViewById(R.id.seekIconSize);
        TextView txtIconSize = findViewById(R.id.txtIconSize);
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

        int iconSize = prefs.getInt(KEY_ICON_SIZE, 110);
        seekIconSize.setProgress(convertIconSizeToSeekPosition(iconSize));
        txtIconSize.setText(iconSize + "dp");

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

        seekIconSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean f) {
                txtIconSize.setText(convertSeekPositionToIconSize(p) + "dp");
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {
                int size = convertSeekPositionToIconSize(sb.getProgress());
                prefs.edit().putInt(KEY_ICON_SIZE, size).apply();
                if (MainActivity.instance != null) MainActivity.instance.updateIconSizes();
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
                Intent intent = new Intent(this, BundledAppsActivity.class);
                startActivity(intent);
            });
        }

        // THEMES BUTTON
        Button btnThemes = findViewById(R.id.btnThemes);
        if (btnThemes != null) {
            btnThemes.setOnClickListener(v -> {
                com.jarjarblinkz.EvolveLauncher.theme.ThemeSelectorDialog.show(this, theme -> {
                    Toast.makeText(this, "Applied: " + theme.name, Toast.LENGTH_SHORT).show();
                    recreate(); // Refresh activity to apply theme
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
     * Update Native Settings button - green "Open" if installed, red "Install" if not
     */
    private static final String NATIVE_SETTINGS_PACKAGE = "com.anagan.xrnativeandroidsettings";

    private void updateNativeSettingsButton(Button btn) {
        // Mark button to be ignored by theme system - we manage colors manually
        btn.setTag("theme_ignore");

        boolean isInstalled = isPackageInstalled(NATIVE_SETTINGS_PACKAGE);

        if (isInstalled) {
            // Green - Open button (launch the app)
            btn.setText("Open");
            btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#4CAF50")));
            btn.setTextColor(android.graphics.Color.WHITE);
            btn.setOnClickListener(v -> {
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(NATIVE_SETTINGS_PACKAGE);
                if (launchIntent != null) {
                    startActivity(launchIntent);
                } else {
                    Toast.makeText(this, "Cannot launch Native Settings", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            // Red - Install button (opens bundled apps)
            btn.setText("Install");
            btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#F44336")));
            btn.setTextColor(android.graphics.Color.WHITE);
            btn.setOnClickListener(v -> {
                Intent intent = new Intent(this, BundledAppsActivity.class);
                startActivity(intent);
            });
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

                // DEBUG: Log the actual type
                android.util.Log.d("SettingsActivity", "Key: " + entry.getKey() +
                        ", Value: " + value +
                        ", Type: " + (value != null ? value.getClass().getName() : "null"));

                if (value instanceof Boolean) {
                    android.util.Log.d("SettingsActivity", "  -> Saving as Boolean");
                    prefsData.put(entry.getKey(), (Boolean) value);
                } else if (value instanceof Integer) {
                    android.util.Log.d("SettingsActivity", "  -> Saving as Integer");
                    prefsData.put(entry.getKey(), (Integer) value);
                } else if (value instanceof Long) {
                    android.util.Log.d("SettingsActivity", "  -> Saving as Long");
                    prefsData.put(entry.getKey(), (Long) value);
                } else if (value instanceof Float) {
                    android.util.Log.d("SettingsActivity", "  -> Saving as Float");
                    prefsData.put(entry.getKey(), (Float) value);
                } else if (value instanceof Set) {
                    android.util.Log.d("SettingsActivity", "  -> Skipping Set");
                    // Skip sets here, they go in categories
                    continue;
                } else {
                    android.util.Log.d("SettingsActivity", "  -> Saving as String");
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

            // Quest VR doesn't support file picker reliably - save directly to /sdcard/
            String fileName = "vrlauncher_backup_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".json";

            saveBackupDirectly(backup, fileName);

        } catch (Exception e) {
            Toast.makeText(this, "Backup failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Fallback method: save to /sdcard/evolve_backups with permission check
    private void saveBackupDirectly(JSONObject backup, String fileName) {
        try {
            // Check if we have permission to write to external storage
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ requires MANAGE_EXTERNAL_STORAGE permission
                if (!Environment.isExternalStorageManager()) {
                    // Show dialog explaining why we need this permission
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

            // Create /sdcard/evolve_backups directory
            File externalStorage = Environment.getExternalStorageDirectory();
            File backupDir = new File(externalStorage, "evolve_backups");

            // Create directory if it doesn't exist
            if (!backupDir.exists()) {
                boolean created = backupDir.mkdirs();
                android.util.Log.i("SettingsActivity", "Creating backup directory: " + backupDir.getAbsolutePath() + ", created=" + created);

                if (!created && !backupDir.exists()) {
                    throw new Exception("Failed to create backup directory. Please grant storage permission in Settings.");
                }
            }

            // Verify directory exists and is writable
            if (!backupDir.exists()) {
                throw new Exception("Backup directory doesn't exist: " + backupDir.getAbsolutePath());
            }

            if (!backupDir.canWrite()) {
                throw new Exception("Backup directory not writable. Please grant storage permission in Settings.");
            }

            File backupFile = new File(backupDir, fileName);
            android.util.Log.i("SettingsActivity", "Writing backup to: " + backupFile.getAbsolutePath());

            FileOutputStream fos = new FileOutputStream(backupFile);
            fos.write(backup.toString(2).getBytes());
            fos.flush();
            fos.close();

            android.util.Log.i("SettingsActivity", "Backup saved successfully: " + backupFile.getAbsolutePath());

            // Show success dialog instead of toast (more reliable on Quest)
            android.util.Log.d("SettingsActivity", "Creating success dialog...");
            try {
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle("✅ Backup Successful")
                        .setMessage("Backup saved to:\n\n/sdcard/evolve_backups/" + fileName)
                        .setPositiveButton("OK", null)
                        .create();
                android.util.Log.d("SettingsActivity", "Showing dialog...");
                ThemedDialog.showThemed(dialog);
                android.util.Log.d("SettingsActivity", "Dialog shown successfully");
            } catch (Exception dialogEx) {
                android.util.Log.e("SettingsActivity", "Failed to show dialog: " + dialogEx.getMessage(), dialogEx);
                // Fallback to toast if dialog fails
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
        // Use Storage Access Framework to let user choose backup file
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");

        // Optional: specify initial directory
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri initialUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3A");
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
        }

        startActivityForResult(intent, REQUEST_CODE_OPEN_BACKUP);
    }

    // SINGLE onActivityResult method handling all cases
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Handle theme background image picker
        if (com.jarjarblinkz.EvolveLauncher.theme.ThemeSelectorDialog.handleImageResult(requestCode, resultCode, data)) {
            return; // Handled by theme dialog
        }

        // Handle usage access permission return
        if (requestCode == REQUEST_CODE_USAGE_ACCESS) {
            // onResume will re-check and update the button automatically
            if (hasUsageStatsPermission()) {
                Toast.makeText(this, "Usage Access granted! Playtime tracking is now active.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Usage Access not granted. Playtime stats won't be available.", Toast.LENGTH_LONG).show();
            }
        }

        // Handle backup creation
        else if (requestCode == REQUEST_CODE_CREATE_BACKUP) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                saveBackupToUri(data.getData());
            }
        }

        // Handle backup restore
        else if (requestCode == REQUEST_CODE_OPEN_BACKUP) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                restoreFromUri(data.getData());
            }
        }
    }

    private void saveBackupToUri(Uri uri) {
        try {
            JSONObject backup = new JSONObject();

            // Save VRLPrefs - preserve types
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
                    // Skip sets here, they go in categories
                    continue;
                } else {
                    prefsData.put(entry.getKey(), String.valueOf(value));
                }
            }
            backup.put("vrprefs", prefsData);

            // Save categories (these are StringSets)
            JSONObject categoriesData = new JSONObject();
            for (Map.Entry<String, ?> entry : categoryPrefs.getAll().entrySet()) {
                if (entry.getValue() instanceof Set) {
                    JSONArray setArray = new JSONArray((Set) entry.getValue());
                    categoriesData.put(entry.getKey(), setArray);
                }
            }
            backup.put("categories", categoriesData);

            // Write to the selected URI
            getContentResolver().openOutputStream(uri).write(backup.toString(2).getBytes());

            Toast.makeText(this, "Backup saved successfully!", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this, "Backup failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void restoreFromUri(Uri uri) {
        try {
            // Read from URI
            byte[] data = new byte[getContentResolver().openInputStream(uri).available()];
            getContentResolver().openInputStream(uri).read(data);

            String backupStr = new String(data);
            JSONObject backup = new JSONObject(backupStr);

            // VALIDATE the backup has required sections
            if (!backup.has("vrprefs") || !backup.has("categories")) {
                Toast.makeText(this, "Invalid backup file - missing required data", Toast.LENGTH_LONG).show();
                return;
            }

            // Clear and restore VRLPrefs with proper types
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
                    // Restore with correct type
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

            // Restore categories
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

            // Show success message with restart option
            ThemedDialog.showThemed(new AlertDialog.Builder(this)
                    .setTitle("Restore Complete")
                    .setMessage("Settings restored successfully!\n\nRestart the launcher now to apply changes?")
                    .setPositiveButton("Restart Now", (d, w) -> {
                        // Kill the app process completely
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

            // Option to clear corrupted preferences
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


    private int convertSeekPositionToIconSize(int pos) {
        return 80 + (pos * 60 / 100);
    }

    private int convertIconSizeToSeekPosition(int size) {
        return (size - 80) * 100 / 60;
    }


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
                            Toast.makeText(this, "Category '" + name + "' created", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .create());
    }

    private void showCategoryManager() {
        Map<String, ?> allEntries = categoryPrefs.getAll();

        Log.d("SettingsActivity", "All entries in categoryPrefs: " + allEntries.toString());

        List<String> categoryNames = new ArrayList<>();

        for (String key : allEntries.keySet()) {
            Log.d("SettingsActivity", "Checking key: " + key);
            if (key.startsWith("cat_")) {
                String categoryName = key.substring(4);
                Log.d("SettingsActivity", "Found category: " + categoryName);
                categoryNames.add(categoryName);
            }
        }

        Collections.sort(categoryNames);

        Log.d("SettingsActivity", "Total categories found: " + categoryNames.size());
        Log.d("SettingsActivity", "Category names: " + categoryNames.toString());

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
                    Log.d("SettingsActivity", "Selected category: " + selectedCategory);
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

        Log.d("SettingsActivity", "Dialog created with " + categoriesArray.length + " items");
    }

    private void showCategoryOptions(String categoryName) {
        Set<String> appsInCategory = categoryPrefs.getStringSet("cat_" + categoryName, new HashSet<>());
        int appCount = appsInCategory != null ? appsInCategory.size() : 0;

        Log.d("SettingsActivity", "Category: " + categoryName + " has " + appCount + " apps");

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
        categoryPrefs.edit()
                .remove("cat_" + categoryName)
                .apply();

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
                    categoryPrefs.edit().clear().apply();
                    Toast.makeText(this, "All categories deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .create());
    }

    // ===== AUTO-RESTART TOGGLE =====

    /**
     * Setup Auto-Restart toggle
     * Allows users to enable/disable the VRShellMonitor service
     */
    private void setupAutoRestartToggle() {
        // Find the toggle switch
        SwitchCompat autoRestartSwitch = findViewById(R.id.autoRestartSwitch);
        TextView autoRestartStatus = findViewById(R.id.autoRestartStatus);

        if (autoRestartSwitch == null || autoRestartStatus == null) {
            Log.w("SettingsActivity", "Auto-restart UI elements not found in layout");
            return;
        }

        // Load current setting
        boolean isEnabled = prefs.getBoolean("auto_restart_enabled", true);
        autoRestartSwitch.setChecked(isEnabled);
        updateAutoRestartStatus(autoRestartStatus, isEnabled);

        // Handle toggle changes
        autoRestartSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Save preference
            prefs.edit().putBoolean("auto_restart_enabled", isChecked).apply();

            // Update status text
            updateAutoRestartStatus(autoRestartStatus, isChecked);

            // Start or stop the service
            Intent serviceIntent = new Intent(this, VRShellMonitorService.class);

            if (isChecked) {
                // Enable: Start the service
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent);
                    } else {
                        startService(serviceIntent);
                    }
                    Toast.makeText(this, "Auto-restart enabled", Toast.LENGTH_SHORT).show();
                    Log.i("SettingsActivity", "Auto-restart enabled - service started");
                } catch (Exception e) {
                    Log.e("SettingsActivity", "Failed to start service", e);
                    Toast.makeText(this, "Failed to enable auto-restart", Toast.LENGTH_SHORT).show();
                }
            } else {
                // Disable: Stop the service
                try {
                    stopService(serviceIntent);
                    Toast.makeText(this, "Auto-restart disabled", Toast.LENGTH_SHORT).show();
                    Log.i("SettingsActivity", "Auto-restart disabled - service stopped");
                } catch (Exception e) {
                    Log.e("SettingsActivity", "Failed to stop service", e);
                }
            }
        });
    }

    /**
     * Update auto-restart status text
     */
    private void updateAutoRestartStatus(TextView statusText, boolean isEnabled) {
        if (statusText == null) return;

        if (isEnabled) {
            statusText.setText(R.string.auto_restart_enabled);
            statusText.setTextColor(0xFF4CAF50); // Green
        } else {
            statusText.setText(R.string.auto_restart_disabled);
            statusText.setTextColor(0xFF9E9E9E); // Gray
        }
    }

    // ===== VERSION & UPDATES =====

    /**
     * Setup version display and update checker
     */
    private void setupVersionAndUpdates() {
        // Find UI elements
        TextView txtCurrentVersion = findViewById(R.id.txtCurrentVersion);
        AppCompatButton btnCheckUpdates = findViewById(R.id.btnCheckUpdates);
        SwitchCompat switchAutoUpdate = findViewById(R.id.switchAutoUpdate);

        // Set current version from BuildConfig
        if (txtCurrentVersion != null) {
            try {
                String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                txtCurrentVersion.setText(versionName);
            } catch (Exception e) {
                Log.e("SettingsActivity", "Error getting version", e);
                txtCurrentVersion.setText("Unknown");
            }
        }

        // Setup auto-update toggle
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

        // Setup check for updates button
        if (btnCheckUpdates != null) {
            btnCheckUpdates.setOnClickListener(v -> {
                Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show();

                // Create UpdateManager and check for updates
                UpdateManager updateManager = new UpdateManager(this);

                updateManager.checkForUpdates(new UpdateManager.UpdateCallback() {
                    @Override
                    public void onUpdateAvailable(String version, String downloadUrl, String releaseNotes) {
                        // Show update dialog
                        ThemedDialog.showThemed(new AlertDialog.Builder(SettingsActivity.this)
                                .setTitle("Update Available! 🎉")
                                .setMessage("Version " + version + " is available!\n\n" +
                                        "Release Notes:\n" + releaseNotes)
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
                        Log.e("SettingsActivity", "Update check error: " + error);
                    }
                });
            });
        }
    }

    /**
     * Launch native Settings via helper app
     * Shows dialog over SettingsActivity if helper not installed
     */
    private void launchNativeSettings() {
        try {
            Log.d("SettingsActivity", "Checking for XRNativeAndroidSettings helper app...");

            // Try to launch XRNativeAndroidSettings app
            PackageManager pm = getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage("com.anagan.xrnativeandroidsettings");

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                Log.i("SettingsActivity", "✅ Launched XRNativeAndroidSettings helper app");
            } else {
                Log.w("SettingsActivity", "Helper app not installed - showing dialog");
                showInstallHelperDialog();
            }

        } catch (Exception e) {
            Log.e("SettingsActivity", "Failed to launch helper app", e);
            showInstallHelperDialog();
        }
    }

    /**
     * Show dialog with installation instructions
     * This dialog appears OVER SettingsActivity so user can see it
     */
    private void showInstallHelperDialog() {
        try {
            Log.d("SettingsActivity", "Creating install helper dialog...");

            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
            builder.setTitle("Install Settings Helper App");
            builder.setMessage("XRNativeAndroidSettings is required to access native Settings.\n\n" +
                    "This free app bypasses Meta's restrictions.\n\n" +
                    "Steps:\n" +
                    "1. Click 'Download' below\n" +
                    "2. Download the APK in browser\n" +
                    "3. Come back here\n" +
                    "4. Click 'Install from Downloads'");
            builder.setCancelable(true);

            // Add buttons - they auto-dismiss by default
            builder.setPositiveButton("Download from itch.io", null);
            builder.setNegativeButton("Install from Downloads", null);
            builder.setNeutralButton("Cancel", null);

            final androidx.appcompat.app.AlertDialog dialog = builder.create();

            // Override button behavior when dialog is shown
            dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
                @Override
                public void onShow(android.content.DialogInterface dialogInterface) {
                    Log.d("SettingsActivity", "Dialog shown, overriding button behavior");

                    android.widget.Button downloadButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
                    android.widget.Button installButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);
                    android.widget.Button cancelButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL);

                    Log.d("SettingsActivity", "Download button: " + (downloadButton != null));
                    Log.d("SettingsActivity", "Install button: " + (installButton != null));
                    Log.d("SettingsActivity", "Cancel button: " + (cancelButton != null));

                    if (downloadButton != null) {
                        downloadButton.setOnClickListener(new android.view.View.OnClickListener() {
                            @Override
                            public void onClick(android.view.View v) {
                                Log.d("SettingsActivity", "Download button clicked - NOT dismissing dialog");
                                try {
                                    Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://anagan79.itch.io/xr-native-android-settings"));
                                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    startActivity(browserIntent);
                                    Toast.makeText(SettingsActivity.this,
                                            "Dialog will stay open. After download, click 'Install from Downloads'",
                                            Toast.LENGTH_LONG).show();
                                    // DO NOT call dialog.dismiss() - dialog stays open!
                                } catch (Exception e) {
                                    Log.e("SettingsActivity", "Failed to open browser", e);
                                    Toast.makeText(SettingsActivity.this, "Unable to open browser", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }

                    if (installButton != null) {
                        installButton.setOnClickListener(new android.view.View.OnClickListener() {
                            @Override
                            public void onClick(android.view.View v) {
                                Log.d("SettingsActivity", "Install button clicked - searching for APK");
                                installHelperFromDownloads();
                                // DO NOT call dialog.dismiss() - dialog stays open so they can retry
                            }
                        });
                    }

                    if (cancelButton != null) {
                        cancelButton.setOnClickListener(new android.view.View.OnClickListener() {
                            @Override
                            public void onClick(android.view.View v) {
                                Log.d("SettingsActivity", "Cancel clicked - dismissing dialog");
                                dialog.dismiss();
                            }
                        });
                    }
                }
            });

            Log.d("SettingsActivity", "Showing dialog...");
            ThemedDialog.showThemed(dialog);
            Log.d("SettingsActivity", "Dialog.show() called");

        } catch (Exception e) {
            Log.e("SettingsActivity", "Failed to show dialog: " + e.getMessage(), e);
            Toast.makeText(this,
                    "Download from: https://anagan79.itch.io/xr-native-android-settings",
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Install helper app from Downloads folder
     * Uses Android's PackageInstaller API
     */
    private void installHelperFromDownloads() {
        try {
            Log.d("SettingsActivity", "========== SEARCHING FOR APK ==========");

            // First check if we have storage permissions
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {

                    Log.w("SettingsActivity", "No READ_EXTERNAL_STORAGE permission - opening Settings...");

                    ThemedDialog.showThemed(new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Storage Permission Needed")
                            .setMessage("Evolve needs storage permission to access downloaded files.\n\n" +
                                    "Steps:\n" +
                                    "1. Click 'Open Settings' below\n" +
                                    "2. Find 'Permissions' or 'App permissions'\n" +
                                    "3. Click 'Storage' or 'Files and media'\n" +
                                    "4. Toggle it ON\n" +
                                    "5. Come back to Evolve\n" +
                                    "6. Click 'Install from Downloads' again")
                            .setPositiveButton("Open Settings", (d, w) -> {
                                try {
                                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    android.net.Uri uri = android.net.Uri.fromParts("package", getPackageName(), null);
                                    intent.setData(uri);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    startActivity(intent);
                                    Toast.makeText(this, "Grant Storage permission, then return to Evolve", Toast.LENGTH_LONG).show();
                                } catch (Exception e) {
                                    Log.e("SettingsActivity", "Failed to open settings", e);
                                    Toast.makeText(this, "Unable to open Settings", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .create());
                    return;
                }
            }

            // Directories to search
            java.io.File[] searchDirs = {
                    android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS),
                    new java.io.File("/sdcard/Download"),
                    new java.io.File("/sdcard/Downloads"),
                    new java.io.File("/sdcard/Oculus/Downloads"),
                    new java.io.File("/storage/emulated/0/Download"),
                    new java.io.File("/storage/emulated/0/Downloads")
            };

            // List ALL files in each directory for debugging
            for (java.io.File dir : searchDirs) {
                if (dir.exists() && dir.isDirectory()) {
                    Log.d("SettingsActivity", "📁 Directory: " + dir.getAbsolutePath());
                    java.io.File[] files = dir.listFiles();
                    if (files != null && files.length > 0) {
                        for (java.io.File f : files) {
                            Log.d("SettingsActivity", "  - " + f.getName() + " (" + f.length() + " bytes)");
                        }
                    } else {
                        Log.d("SettingsActivity", "  (empty)");
                    }
                } else {
                    Log.d("SettingsActivity", "❌ Not found: " + dir.getAbsolutePath());
                }
            }

            // Search for the APK - version agnostic
            Log.d("SettingsActivity", "========== SEARCHING FOR ANY .APK FILES ==========");

            java.io.File foundApk = null;
            StringBuilder searchLog = new StringBuilder();

            // Search each directory for ANY XRNativeAndroidSettings APK (version agnostic)
            for (java.io.File dir : searchDirs) {
                if (dir.exists() && dir.isDirectory()) {
                    java.io.File[] apkFiles = dir.listFiles(new java.io.FilenameFilter() {
                        @Override
                        public boolean accept(java.io.File dir, String name) {
                            String lowerName = name.toLowerCase();
                            return (lowerName.startsWith("xrnativeandroidsettings") ||
                                    lowerName.startsWith("xr-native-android-settings")) &&
                                    lowerName.endsWith(".apk");
                        }
                    });

                    if (apkFiles != null && apkFiles.length > 0) {
                        foundApk = apkFiles[0];  // Take the first match
                        Log.i("SettingsActivity", "✅ FOUND APK: " + foundApk.getAbsolutePath());
                        break;
                    }
                }
            }

            // Also search for ANY .apk file in Downloads as fallback
            java.util.List<java.io.File> anyApks = new java.util.ArrayList<>();
            if (foundApk == null) {
                for (java.io.File dir : searchDirs) {
                    if (dir.exists() && dir.isDirectory()) {
                        java.io.File[] files = dir.listFiles(new java.io.FilenameFilter() {
                            @Override
                            public boolean accept(java.io.File dir, String name) {
                                return name.toLowerCase().endsWith(".apk");
                            }
                        });
                        if (files != null) {
                            for (java.io.File f : files) {
                                anyApks.add(f);
                                Log.d("SettingsActivity", "Found .apk: " + f.getAbsolutePath());
                            }
                        }
                    }
                }
            }

            if (foundApk != null && foundApk.exists()) {
                Log.i("SettingsActivity", "Installing APK from: " + foundApk.getAbsolutePath());
                installApk(foundApk);

            } else if (!anyApks.isEmpty()) {
                // Show user the APK files we found
                StringBuilder apkList = new StringBuilder("Found these APK files:\n\n");
                for (java.io.File apk : anyApks) {
                    apkList.append("• ").append(apk.getName()).append("\n");
                }
                apkList.append("\nNone match expected pattern.\n\n")
                        .append("Expected filename must start with:\n")
                        .append("• XRNativeAndroidSettings\n")
                        .append("OR\n")
                        .append("• xr-native-android-settings\n\n")
                        .append("Examples:\n")
                        .append("• XRNativeAndroidSettings_1.0.3.apk\n")
                        .append("• xr-native-android-settings.apk");

                Log.w("SettingsActivity", apkList.toString());

                ThemedDialog.showThemed(new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Wrong APK filename?")
                        .setMessage(apkList.toString())
                        .setPositiveButton("OK", null)
                        .create());

            } else {
                // No APK found - suggest alternatives
                Log.w("SettingsActivity", "No APK files found in Downloads");

                String errorMessage = "APK not found in Downloads!\n\n" +
                        "This might be due to:\n" +
                        "• File permissions (Android security)\n" +
                        "• APK not downloaded yet\n" +
                        "• Different download location\n\n" +
                        "Solutions:\n" +
                        "1. Install via ADB:\n" +
                        "   adb shell pm install /sdcard/Download/XRNativeAndroidSettings*.apk\n\n" +
                        "2. Use Quest's Files app to install\n\n" +
                        "3. Grant storage permission and try again";

                ThemedDialog.showThemed(new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("APK Not Found")
                        .setMessage(errorMessage)
                        .setPositiveButton("OK", null)
                        .create());
            }

        } catch (Exception e) {
            Log.e("SettingsActivity", "Failed to install from downloads", e);
            Toast.makeText(this, "Installation error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Install the APK file
     */
    private void installApk(java.io.File apkFile) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            android.net.Uri apkUri;

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                // Android 7.0+ requires FileProvider
                apkUri = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        getApplicationContext().getPackageName() + ".fileprovider",
                        apkFile);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                apkUri = android.net.Uri.fromFile(apkFile);
            }

            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);

            Toast.makeText(this, "Opening installer for: " + apkFile.getName(), Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Log.e("SettingsActivity", "Failed to install APK", e);
            Toast.makeText(this, "Install failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ===== SHIZUKU INTEGRATION =====

    /**
     * Initialize Shizuku manager for shell-level commands
     */
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

    /**
     * Show Shizuku setup dialog - checks status and offers installation
     */
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

    /**
     * Step 1: Offer to install Shizuku from bundled APK
     */
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
                        Toast.makeText(this, "Follow the installer prompts...", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "❌ Failed to start installer", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Not Now", null)
                .create());
    }

    /**
     * Step 2: Shizuku installed but server not running - show setup instructions
     */
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
                        Toast.makeText(this, "❌ Could not open Shizuku app", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Close", null)
                .create());
    }

    /**
     * Step 3: Shizuku running - grant permission
     */
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

    /**
     * Fallback: Shizuku APK not bundled - show manual instructions
     */
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

    /**
     * Clear VrShell data using Shizuku (resets to old UI)
     */
    private void resetQuestUI() {
        if (shizukuManager == null || !shizukuManager.isReady()) {
            showShizukuSetupDialog();
            if (shizukuManager != null) {
                shizukuManager.recheckStatus();
            }
            return;
        }

        // Confirm action
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

    /**
     * Force stop VrShell using Shizuku
     */
    private void restartQuestUI() {
        if (shizukuManager == null || !shizukuManager.isReady()) {
            showShizukuSetupDialog();
            if (shizukuManager != null) {
                shizukuManager.recheckStatus();
            }
            return;
        }

        // Confirm action
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