package com.jarjarblinkz.EvolveLauncher;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
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

    // Auto-update manager
    private UpdateManager updateManager;

    private static final String PREFS_NAME = "VRLPrefs";
    private static final String CATEGORY_PREFS = "vr_categories";

    private static final String KEY_EDIT_MODE = "edit_mode";
    private static final String KEY_ICON_SIZE = "icon_size";
    private static final String KEY_SHOW_CATEGORIES = "show_categories";
    private static final String KEY_BG_TYPE = "background_type";
    private static final String KEY_BG_OPACITY = "background_opacity";
    private static final String KEY_CUSTOM_BG_PATH = "custom_background_path";
    private static final String KEY_AUTO_START = "auto_start";

    private static final int REQUEST_IMAGE_PICK = 1001;
    private static final int REQUEST_CODE_CREATE_BACKUP = 200;
    private static final int REQUEST_CODE_OPEN_BACKUP = 201;
    private static final int REQUEST_CODE_USAGE_ACCESS = 202;

    private static final String[] BUILTIN_BGS =
            {"space", "nebula", "grid", "mountains", "abstract", "circuit"};
    private static final String[] BUILTIN_BG_NAMES =
            {"Deep Space", "Cosmic Nebula", "VR Grid", "Mountain Range", "Abstract Waves", "Circuit Board"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_settings);

        Window window = getWindow();
        android.view.WindowManager.LayoutParams lp = window.getAttributes();
        // Increased to 0.85 width for optimal 2-column display
        lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
        lp.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.95);
        window.setAttributes(lp);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        categoryPrefs = getSharedPreferences(CATEGORY_PREFS, MODE_PRIVATE);

        // Initialize all views
        SwitchCompat switchEditMode = findViewById(R.id.switchEditMode);
        SwitchCompat switchCategories = findViewById(R.id.switchCategories);
        SeekBar seekIconSize = findViewById(R.id.seekIconSize);
        TextView txtIconSize = findViewById(R.id.txtIconSize);
        AppCompatButton btnBgBuiltin = findViewById(R.id.btnBgBuiltin);
        AppCompatButton btnBgCustom = findViewById(R.id.btnBgCustom);
        AppCompatButton btnBgDefault = findViewById(R.id.btnBgDefault);
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

        // Initialize update manager
        updateManager = new UpdateManager(this);

        // Get update UI elements
        SwitchCompat switchAutoUpdate = findViewById(R.id.switchAutoUpdate);
        AppCompatButton btnCheckUpdates = findViewById(R.id.btnCheckUpdates);
        TextView txtCurrentVersion = findViewById(R.id.txtCurrentVersion);

        // Set current version
        txtCurrentVersion.setText("Version: " + updateManager.getCurrentVersion());

        // Set initial auto-update state
        switchAutoUpdate.setChecked(updateManager.isAutoUpdateEnabled());

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

        // Auto-update switch listener
        switchAutoUpdate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateManager.setAutoUpdateEnabled(isChecked);
            Toast.makeText(this,
                    isChecked ? "Auto-update enabled" : "Auto-update disabled",
                    Toast.LENGTH_SHORT).show();
        });

        // Check for updates button
        btnCheckUpdates.setOnClickListener(v -> {
            Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show();
            updateManager.checkForUpdates(true); // Show toast even if no update
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

        btnBgBuiltin.setOnClickListener(v -> showBuiltinBackgroundsDialog());
        btnBgCustom.setOnClickListener(v -> launchImagePicker());

        btnBgDefault.setOnClickListener(v -> {
            prefs.edit().putString(KEY_BG_TYPE, "default").putString(KEY_CUSTOM_BG_PATH, "").apply();
            if (MainActivity.instance != null) MainActivity.instance.updateBackground();
            Toast.makeText(this, "Default background set", Toast.LENGTH_SHORT).show();
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
        new androidx.appcompat.app.AlertDialog.Builder(this)
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
                .show();
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
                    // Skip sets here, they go in categories
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

            // Try Storage Access Framework first
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");

            String fileName = "vrlauncher_backup_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".json";
            intent.putExtra(Intent.EXTRA_TITLE, fileName);

            // Optional: specify initial directory
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Uri initialUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3A");
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
            }

            try {
                startActivityForResult(intent, REQUEST_CODE_CREATE_BACKUP);
            } catch (ActivityNotFoundException e) {
                android.util.Log.e("SettingsActivity", "File picker not available, using fallback", e);
                // Fallback: save directly to Downloads folder
                saveBackupDirectly(backup, fileName);
            }

        } catch (Exception e) {
            android.util.Log.e("SettingsActivity", "Backup failed", e);
            Toast.makeText(this, "Backup failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
                    new AlertDialog.Builder(this)
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
                            .show();
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

            Toast.makeText(this, "Backup saved to:\n/sdcard/evolve_backups/" + fileName, Toast.LENGTH_LONG).show();
            android.util.Log.i("SettingsActivity", "Backup saved successfully: " + backupFile.getAbsolutePath());
        } catch (Exception e) {
            android.util.Log.e("SettingsActivity", "Fallback backup failed: " + e.getMessage(), e);
            Toast.makeText(this, "Backup failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void restoreLayout() {
        // Use Storage Access Framework to let user choose backup file
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");

        // Set initial directory to /sdcard/evolve_backups
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            File externalStorage = Environment.getExternalStorageDirectory();
            File backupDir = new File(externalStorage, "evolve_backups");
            if (backupDir.exists()) {
                Uri backupUri = Uri.fromFile(backupDir);
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, backupUri);
            }
        }

        startActivityForResult(intent, REQUEST_CODE_OPEN_BACKUP);
    }

    // SINGLE onActivityResult method handling all cases
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Handle image picker
        if (requestCode == REQUEST_IMAGE_PICK) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                handleSelectedImage(data.getData());
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(this, "Image selection cancelled", Toast.LENGTH_SHORT).show();
            }
        }

        // Handle usage access permission return
        else if (requestCode == REQUEST_CODE_USAGE_ACCESS) {
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

            // Write to the selected URI with proper stream handling
            OutputStream outputStream = null;
            try {
                outputStream = getContentResolver().openOutputStream(uri);
                if (outputStream != null) {
                    outputStream.write(backup.toString(2).getBytes());
                    outputStream.flush();
                    Toast.makeText(this, "Backup saved successfully!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Failed to open file for writing", Toast.LENGTH_SHORT).show();
                }
            } finally {
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (Exception closeEx) {
                        // Ignore close errors
                    }
                }
            }

        } catch (Exception e) {
            Toast.makeText(this, "Backup failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void restoreFromUri(Uri uri) {
        InputStream inputStream = null;
        try {
            // Read from URI with proper stream handling
            inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Toast.makeText(this, "Failed to open backup file", Toast.LENGTH_SHORT).show();
                return;
            }

            byte[] data = new byte[inputStream.available()];
            inputStream.read(data);

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
            new AlertDialog.Builder(this)
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
                    .show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Restore failed: " + e.getMessage(), Toast.LENGTH_LONG).show();

            // Option to clear corrupted preferences
            new AlertDialog.Builder(this)
                    .setTitle("Restore Failed")
                    .setMessage("The backup file may be corrupted.\n\nWould you like to clear all settings and start fresh?")
                    .setPositiveButton("Clear Settings", (d, w) -> {
                        prefs.edit().clear().commit();
                        categoryPrefs.edit().clear().commit();
                        Toast.makeText(this, "Settings cleared. Restart app.", Toast.LENGTH_SHORT).show();
                        android.os.Process.killProcess(android.os.Process.myPid());
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception closeEx) {
                    // Ignore close errors
                }
            }
        }
    }

    // ===== END BACKUP & RESTORE METHODS =====


    private int convertSeekPositionToIconSize(int pos) {
        return 80 + (pos * 60 / 100);
    }

    private int convertIconSizeToSeekPosition(int size) {
        return (size - 80) * 100 / 60;
    }

    private void showBuiltinBackgroundsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Select Built-in Background")
                .setItems(BUILTIN_BG_NAMES, (d, i) -> {
                    prefs.edit().putString(KEY_BG_TYPE, "builtin_" + BUILTIN_BGS[i]).apply();
                    if (MainActivity.instance != null) MainActivity.instance.updateBackground();
                    Toast.makeText(this, "Background set: " + BUILTIN_BG_NAMES[i], Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void launchImagePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);

            Intent pick = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pick.setType("image/*");

            Intent openDoc = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            openDoc.setType("image/*");
            openDoc.addCategory(Intent.CATEGORY_OPENABLE);

            Intent chooser = Intent.createChooser(intent, "Select Background Image");
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{pick, openDoc});

            startActivityForResult(chooser, REQUEST_IMAGE_PICK);
        } catch (ActivityNotFoundException e) {
            showNoFileManagerDialog();
        }
    }

    private void showNoFileManagerDialog() {
        new AlertDialog.Builder(this)
                .setTitle("No File Manager")
                .setMessage("No app found to select images.\n\nYou can install a file manager or use built-in backgrounds.")
                .setPositiveButton("Use Built-in", (d, w) -> showBuiltinBackgroundsDialog())
                .setNeutralButton("Try Gallery", (d, w) -> tryGalleryPicker())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void tryGalleryPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");

            PackageManager pm = getPackageManager();
            List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);

            if (!apps.isEmpty()) {
                startActivityForResult(Intent.createChooser(intent, "Select from Gallery"), REQUEST_IMAGE_PICK);
            } else {
                Toast.makeText(this, "No gallery apps found.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open gallery", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSelectedImage(Uri uri) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                try {
                    getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) {}
            }

            prefs.edit()
                    .putString(KEY_BG_TYPE, "custom")
                    .putString(KEY_CUSTOM_BG_PATH, uri.toString())
                    .apply();

            if (MainActivity.instance != null) MainActivity.instance.updateBackground();

            String name = getImageDisplayName(uri);
            Toast.makeText(this,
                    name != null ? "Background set: " + name : "Custom background set",
                    Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "Error setting background", Toast.LENGTH_SHORT).show();
        }
    }

    private String getImageDisplayName(Uri uri) {
        try (Cursor c = getContentResolver().query(
                uri,
                new String[]{MediaStore.Images.Media.DISPLAY_NAME},
                null, null, null)) {
            if (c != null && c.moveToFirst()) {
                return c.getString(0);
            }
        } catch (Exception ignored) {}
        return null;
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

        new AlertDialog.Builder(this)
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
                .show();
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
            new AlertDialog.Builder(this)
                    .setTitle("Category Manager")
                    .setMessage("No categories created yet.")
                    .setPositiveButton("Create New", (d, w) -> showCreateCategoryDialog())
                    .setNegativeButton("Cancel", null)
                    .show();
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
        dialog.show();

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

        new AlertDialog.Builder(this)
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
                .show();
    }

    private void showAppsInCategory(String categoryName, Set<String> appPackages) {
        if (appPackages == null || appPackages.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Apps in " + categoryName)
                    .setMessage("No apps in this category.")
                    .setPositiveButton("OK", null)
                    .show();
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

        new AlertDialog.Builder(this)
                .setTitle("Apps in " + categoryName + " (" + appPackages.size() + " apps)")
                .setItems(appNamesArray, null)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showRenameCategoryDialog(String oldName) {
        final EditText input = new EditText(this);
        input.setText(oldName);
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
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
                .show();
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

        new AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage(message)
                .setPositiveButton("Delete", (d, w) -> {
                    deleteCategory(categoryName);
                })
                .setNegativeButton("Cancel", null)
                .show();
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

        new AlertDialog.Builder(this)
                .setTitle("Delete All Categories")
                .setMessage(message)
                .setPositiveButton("Delete All", (d, w) -> {
                    categoryPrefs.edit().clear().apply();
                    Toast.makeText(this, "All categories deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (updateManager != null) {
            updateManager.cleanup();
        }
    }
}