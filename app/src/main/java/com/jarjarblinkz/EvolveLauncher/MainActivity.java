package com.jarjarblinkz.EvolveLauncher;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.jarjarblinkz.EvolveLauncher.PlaytimeTracker;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.jarjarblinkz.EvolveLauncher.theme.ThemeApplier;
import com.jarjarblinkz.EvolveLauncher.theme.ThemeManager;
import com.jarjarblinkz.EvolveLauncher.theme.ThemedDialog;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private RecyclerView appsGrid;
    private AppAdapter appAdapter;
    private List<AppInfo> appList = new ArrayList<>();
    private List<AppInfo> filteredList = new ArrayList<>();
    private PackageManager packageManager;
    private EditText searchEditText;

    // Floating favorites button - stored as field so we can update its color on theme change
    private android.widget.ImageButton floatingFavoritesButton;

    private ExecutorService executorService = Executors.newFixedThreadPool(4);
    private Map<String, Drawable> iconCache = new HashMap<>();
    private static final String GITHUB_ICON_BASE_URL = "https://raw.githubusercontent.com/JarJarBlinkz/LauncherIcons/main/oculus_landscape/";

    // RESTORED: Icon scale values from old launcher (82, 99, 125, 165, 236 dp)
    private static final int[] ICON_SCALES_DP = {82, 99, 125, 165, 236};
    private static final int DEFAULT_SCALE_INDEX = 2;  // 125dp default

    // Card overhead: margin (12dp each side = 24dp) + padding (8dp each side = 16dp) = 40dp total
    private static final int CARD_HORIZONTAL_OVERHEAD_DP = 40;

    // Broadcast action for theme changes
    private static final String ACTION_THEME_CHANGED = "com.jarjarblinkz.EvolveLauncher.THEME_CHANGED";
    private static final String ACTION_CATEGORIES_CHANGED = "com.jarjarblinkz.EvolveLauncher.CATEGORIES_CHANGED";

    private static final String[] SYSTEM_PACKAGES = {
            "com.android.settings",
            "com.android.systemui",
            "com.google.android.gms",
            "com.google.android.googlequicksearchbox",
            "com.android.vending",
            "com.google.android.apps.maps",
            "com.google.android.apps.photos",
            "com.google.android.youtube",
            "com.google.android.calendar",
            "com.google.android.contacts",
            "com.google.android.dialer",
            "com.google.android.gm",
            "com.oculus",
            "com.facebook",
            "com.android.chrome",
            "com.android.email",
            "com.android.camera",
            "com.android.calculator",
            "com.android.deskclock",
            "com.android.mms",
            "com.android.phone",
            "com.android.providers",
            "com.android.server",
            "com.qualcomm",
            "android",
            "com.meta"
    };

    private boolean isEditMode = false;
    private SharedPreferences prefs;
    private Set<String> selectedApps = new HashSet<>();

    private static final boolean ENABLE_IMAGE_CACHING = true;
    public static MainActivity instance;

    /**
     * Force refresh theme on MainActivity from Settings
     * Called directly from SettingsActivity when theme changes
     */
    public static void refreshTheme() {
        if (instance != null) {
            instance.runOnUiThread(() -> {
                // Force complete refresh of all theme elements
                View rootView = instance.findViewById(android.R.id.content);
                ThemeApplier.applyThemeToHierarchy(rootView);
                instance.updateBackground();
                instance.refreshFloatingFavoritesColor();

                // Refresh the app grid
                if (instance.appAdapter != null) {
                    instance.appAdapter.notifyDataSetChanged();
                }

                // Force layout redraw
                if (instance.appsGrid != null) {
                    instance.appsGrid.invalidate();
                    instance.appsGrid.requestLayout();
                }

                Toast.makeText(instance, "Theme applied!", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private static final String PREFS_NAME = "VRLPrefs";
    private static final String KEY_PERMISSION_GRANTED = "usage_stats_permission_granted";
    private static final String KEY_PERMISSION_CHECKED = "usage_stats_permission_checked";
    private static final String KEY_LAST_CATEGORY = "last_category";

    private static final String CATEGORY_PREFS = "vr_categories";
    private SharedPreferences categoryPrefs;
    private Map<String, Set<String>> categories = new HashMap<>();
    private LinearLayout categoryBar;
    private String currentCategory = "All Apps";

    // Quick Settings Panel
    private View quickSettingsPanel;
    private ImageButton btnClosePanel;
    private boolean isQuickSettingsVisible = false;
    private QuickSettingsManager quickSettingsManager;
    private Handler quickSettingsHandler = new Handler();
    private Runnable quickSettingsUpdateRunnable;

    private BroadcastReceiver appChangeListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // Custom broadcast
            if ("APP_CHANGE_DETECTED".equals(action)) {
                refreshAll();
                String packageName = intent.getStringExtra("package_name");
                String appAction = intent.getStringExtra("action");
                boolean replacing = intent.getBooleanExtra("replacing", false);

                if (Intent.ACTION_PACKAGE_ADDED.equals(appAction) && !replacing) {
                    Toast.makeText(MainActivity.this, "New app installed - Refreshed", Toast.LENGTH_SHORT).show();
                }
            }
            // System package events
            else if (Intent.ACTION_PACKAGE_ADDED.equals(action) ||
                    Intent.ACTION_PACKAGE_REMOVED.equals(action) ||
                    Intent.ACTION_PACKAGE_CHANGED.equals(action)) {
                refreshAll();

                if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                    String packageName = intent.getData().getSchemeSpecificPart();
                    Toast.makeText(MainActivity.this, "New app installed: " + packageName, Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    // Theme change receiver for real-time updates from Settings
    private BroadcastReceiver themeChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_THEME_CHANGED.equals(intent.getAction())) {
                // Re-apply theme to MainActivity in real-time
                runOnUiThread(() -> {
                    View rootView = findViewById(android.R.id.content);
                    ThemeApplier.applyThemeToHierarchy(rootView);
                    updateBackground();
                    refreshFloatingFavoritesColor();
                    if (appAdapter != null) {
                        appAdapter.notifyDataSetChanged();
                    }
                    Toast.makeText(MainActivity.this, "Theme updated", Toast.LENGTH_SHORT).show();
                });
            }
        }
    };

    /**
     * Receives broadcasts when categories are modified in Settings
     * (create/rename/delete). Rebuilds the category bar and refreshes
     * the app grid so removed categories vanish immediately - no manual
     * refresh required.
     */
    private BroadcastReceiver categoryChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_CATEGORIES_CHANGED.equals(intent.getAction())) {
                runOnUiThread(() -> {
                    // 1. Reload the in-memory categories map from SharedPreferences
                    loadCategories();

                    // 2. CRITICAL: Recompute each app's .category field. The badge on
                    //    each card is rendered from app.category which is cached on
                    //    the AppInfo object - so we must update it manually here,
                    //    otherwise deleted categories' badges persist.
                    if (appList != null) {
                        for (AppInfo app : appList) {
                            String newCategory = "Uncategorized";
                            for (Map.Entry<String, Set<String>> entry : categories.entrySet()) {
                                if (entry.getValue().contains(app.packageName)) {
                                    newCategory = entry.getKey();
                                    break;
                                }
                            }
                            app.category = newCategory;
                        }
                    }

                    // 3. Rebuild the category bar at the bottom
                    buildCategoryBar();

                    // 4. If user was viewing a now-deleted category, switch to All Apps
                    if (currentCategory != null && !"All Apps".equals(currentCategory)
                            && !categories.containsKey(currentCategory)) {
                        currentCategory = "All Apps";
                        prefs.edit().putString("selected_category", "All Apps").apply();
                        String query = searchEditText != null ? searchEditText.getText().toString() : "";
                        filterApps(query);
                    }
                    updateCategoryButtonStates(currentCategory);

                    // 5. Refresh visible cards so badges update
                    if (appAdapter != null) {
                        appAdapter.notifyDataSetChanged();
                    }

                    Log.d("MainActivity", "Categories refreshed - " + categories.size() + " categories");
                });
            }
        }
    };

    private Handler statusHandler = new Handler();
    private TextView txtTime;
    private TextView txtIP;
    private ImageView batteryIcon;
    private TextView txtBattery;
    private ImageView wifiIcon;
    private TextView txtWifiSignal;
    private WifiManager wifiManager;
    private LinearLayout wifiContainer;
    private Runnable statusUpdateRunnable;

    private PlaytimeTracker playtimeTracker;
    private Handler playtimeHandler = new Handler();
    private Runnable playtimeUpdateRunnable;
    private PlaytimeTracker.TimeRange currentPlaytimeRange = PlaytimeTracker.TimeRange.TODAY;

    private BroadcastReceiver batteryReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            updateBatteryLevel(intent);
        }
    };

    // ===== SAFE BOOLEAN PREFERENCE HELPER =====
    private boolean getBooleanPreference(String key, boolean defaultValue) {
        try {
            // Try to get as boolean first
            return prefs.getBoolean(key, defaultValue);
        } catch (ClassCastException e) {
            // If it's stored as string, convert it
            try {
                String value = prefs.getString(key, String.valueOf(defaultValue));
                return Boolean.parseBoolean(value);
            } catch (Exception ex) {
                return defaultValue;
            }
        }
    }
    // ===== END HELPER =====

    /**
     * Set window size to Meta's standard size (1024x640 dp)
     */
    private void setMetaStandardWindowSize() {
        float density = getResources().getDisplayMetrics().density;
        int widthPx = (int) (1024 * density);
        int heightPx = (int) (640 * density);

        Window window = getWindow();
        android.view.WindowManager.LayoutParams params = window.getAttributes();
        params.width = widthPx;
        params.height = heightPx;
        window.setAttributes(params);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.getDecorView().setMinimumWidth(widthPx);
            window.getDecorView().setMinimumHeight(heightPx);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make window background transparent for true see-through effect
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        setContentView(R.layout.activity_main);

        instance = this;

        appsGrid = findViewById(R.id.appsGrid);
        // Hide grid until entry animation kicks off, so user doesn't see
        // a static frame of the final layout before cards fly in.
        appsGrid.setAlpha(0f);

        // Find the search container first
        LinearLayout searchContainer = findViewById(R.id.searchContainer);

        // Then find the search EditText within the container
        searchEditText = searchContainer.findViewById(R.id.searchEditText);

        categoryBar = findViewById(R.id.categoryBar);

        txtTime = findViewById(R.id.txtTime);
        txtIP = findViewById(R.id.txtIP);

        // Find battery and WiFi views
        batteryIcon = findViewById(R.id.batteryIcon);
        txtBattery = findViewById(R.id.txtBattery);
        wifiIcon = findViewById(R.id.wifiIcon);
        txtWifiSignal = findViewById(R.id.txtWifiSignal);
        wifiContainer = findViewById(R.id.wifiContainer);

        // Quick Settings Panel
        quickSettingsPanel = findViewById(R.id.quickSettingsPanel);
        btnClosePanel = findViewById(R.id.btnClosePanel);

        appsGrid.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        appsGrid.setOverScrollMode(View.OVER_SCROLL_NEVER);

        packageManager = getPackageManager();

        prefs = getSharedPreferences("VRLPrefs", MODE_PRIVATE);
        categoryPrefs = getSharedPreferences(CATEGORY_PREFS, MODE_PRIVATE);

        // FIXED: Use safe boolean helper
        isEditMode = getBooleanPreference("edit_mode", false);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        // Initialize playtime tracker
        playtimeTracker = new PlaytimeTracker(this);

        // Initialize Quick Settings Manager
        quickSettingsManager = new QuickSettingsManager(this);

        // Check for usage stats permission - but use cached result
        boolean permissionGranted = prefs.getBoolean(KEY_PERMISSION_GRANTED, false);
        boolean permissionChecked = prefs.getBoolean(KEY_PERMISSION_CHECKED, false);

        if (!permissionChecked) {
            // First time running, check actual permission
            if (playtimeTracker.hasPermission()) {
                prefs.edit().putBoolean(KEY_PERMISSION_GRANTED, true).apply();
            }
            prefs.edit().putBoolean(KEY_PERMISSION_CHECKED, true).apply();
            permissionGranted = prefs.getBoolean(KEY_PERMISSION_GRANTED, false);
        }

        if (!permissionGranted) {

        }

        if (permissionGranted) {
            startPlaytimeUpdates();
        }

        loadCategories();

        // Initialize default categories if none exist
        if (categories.isEmpty()) {
            SharedPreferences.Editor editor = categoryPrefs.edit();
            editor.putStringSet("cat_Games", new HashSet<>());
            editor.putStringSet("cat_Media", new HashSet<>());
            editor.putStringSet("cat_Tools", new HashSet<>());
            editor.putStringSet("cat_Social", new HashSet<>());
            editor.apply();
            loadCategories(); // Reload after adding defaults
        }

        // Load last selected category from preferences
        // Default to "All Apps" if no saved category exists
        currentCategory = prefs.getString("last_category", "All Apps");

        // Validate that the saved category still exists
        // If it was deleted, fall back to "All Apps"
        if (!currentCategory.equals("All Apps") && !categories.containsKey(currentCategory)) {
            currentCategory = "All Apps";
            // Save the corrected category
            prefs.edit().putString("last_category", currentCategory).apply();
        }

        loadUserApps();

        // Filter apps based on the restored category BEFORE creating adapter
        filteredList.clear();
        FavoritesManager favManagerInit = FavoritesManager.getInstance(this);
        if (currentCategory.equals("All Apps")) {
            // Show only uncategorized apps in All Apps view (and exclude favorites)
            for (AppInfo app : appList) {
                if (favManagerInit.isFavorite(app.packageName)) continue;  // Skip favorites
                boolean isInAnyCategory = false;
                for (Set<String> categoryApps : categories.values()) {
                    if (categoryApps.contains(app.packageName)) {
                        isInAnyCategory = true;
                        break;
                    }
                }
                if (!isInAnyCategory) {
                    filteredList.add(app);
                }
            }
        } else {
            // Show apps from the saved category (excluding favorites)
            Set<String> pkgs = categories.get(currentCategory);
            if (pkgs != null) {
                for (AppInfo app : appList) {
                    if (favManagerInit.isFavorite(app.packageName)) continue;  // Skip favorites
                    if (pkgs.contains(app.packageName)) {
                        filteredList.add(app);
                    }
                }
            }
        }

        // Create adapter with filtered list
        appAdapter = new AppAdapter(filteredList);
        appsGrid.setAdapter(appAdapter);

        // FIXED: Call setupRecyclerView() so the layout manager and
        // dynamic resize listener are properly initialized
        setupRecyclerView();

        // VR POLISH: schedule the directional entry animation to play once
        // the grid has finished laying out its visible cards.
        scheduleEntryAnimation();

        // Build category bar and highlight the saved category
        buildCategoryBar();
        updateCategoryButtonStates(currentCategory);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package"); // critical for package-related broadcasts

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(appChangeListener, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(appChangeListener, filter);
        }

        // Register theme change receiver for real-time updates from Settings
        IntentFilter themeFilter = new IntentFilter(ACTION_THEME_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(themeChangeReceiver, themeFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(themeChangeReceiver, themeFilter);
        }

        // Register category change receiver for real-time updates from Settings
        IntentFilter categoryFilter = new IntentFilter(ACTION_CATEGORIES_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(categoryChangeReceiver, categoryFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(categoryChangeReceiver, categoryFilter);
        }

        IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, batteryFilter);

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                filterApps(s.toString());
            }
        });

        ImageView clearSearch = findViewById(R.id.clearSearch);
        clearSearch.setOnClickListener(v -> {
            searchEditText.setText("");
            filterApps("");
        });

        ImageView btnSettings = findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

            // Get Main window dimensions
            android.graphics.Rect mainWindowRect = new android.graphics.Rect();
            getWindow().getDecorView().getWindowVisibleDisplayFrame(mainWindowRect);

            // Alternative: get actual window dimensions
            int mainWidth = getWindow().getDecorView().getWidth();
            int mainHeight = getWindow().getDecorView().getHeight();

            if (mainWidth > 0 && mainHeight > 0) {
                intent.putExtra("window_width", mainWidth);
                intent.putExtra("window_height", mainHeight);
            } else {
                // Fallback to screen dimensions
                android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(metrics);
                intent.putExtra("window_width", metrics.widthPixels);
                intent.putExtra("window_height", metrics.heightPixels);
            }

            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        // Long press settings button to open Quick Settings
        btnSettings.setOnLongClickListener(v -> {
            showQuickSettings();
            return true;
        });

        // FLOATING FAVORITES BUTTON - bottom-right corner
        addFloatingFavoritesButton();

        // Close Quick Settings button
        btnClosePanel.setOnClickListener(v -> hideQuickSettings());

        if (wifiContainer != null) {
            wifiContainer.setOnLongClickListener(v -> {
                showWifiDetails();
                return true;
            });
        }

        appsGrid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                if (newState == RecyclerView.SCROLL_STATE_DRAGGING ||
                        newState == RecyclerView.SCROLL_STATE_SETTLING) {
                    Glide.with(MainActivity.this).pauseRequests();
                } else {
                    Glide.with(MainActivity.this).resumeRequests();
                }
            }
        });

        startStatusUpdates();

        // Setup Quick Settings panel
        setupQuickSettings();

        // Start VR Shell Monitor service for auto-restart functionality
        startVRShellMonitor();

        // Apply current theme to entire view hierarchy
        View rootView = findViewById(android.R.id.content);
        ThemeApplier.applyThemeToHierarchy(rootView);
    }

    /**
     * Start VR Shell Monitor service to auto-restart launcher
     * Only starts if user has enabled the feature in settings
     */
    private void startVRShellMonitor() {
        try {
            // Check if auto-restart is enabled in settings
            SharedPreferences prefs = getSharedPreferences("VRLPrefs", MODE_PRIVATE);
            boolean autoRestartEnabled = prefs.getBoolean("auto_restart_enabled", true); // Default: enabled

            if (!autoRestartEnabled) {
                Log.i("MainActivity", "Auto-restart is disabled - not starting VR Shell Monitor");
                return;
            }

            Intent serviceIntent = new Intent(this, VRShellMonitorService.class);

            // Use startForegroundService for Android 8.0+
            // Service will call startForeground() with notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }

            Log.i("MainActivity", "VR Shell Monitor service started (auto-restart enabled)");
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to start VR Shell Monitor service", e);
        }
    }

    // ===== QUICK SETTINGS METHODS =====

    private void setupQuickSettings() {
        // Volume control only
        SeekBar volumeSeek = findViewById(R.id.seekVolume);
        TextView volumeValue = findViewById(R.id.txtVolumeValue);
        View btnVolumeDown = findViewById(R.id.btnVolumeDown);
        View btnVolumeUp = findViewById(R.id.btnVolumeUp);

        if (volumeSeek != null && volumeValue != null) {
            int currentVolume = quickSettingsManager.getCurrentVolume();
            volumeSeek.setProgress(currentVolume);
            volumeValue.setText(currentVolume + "%");

            volumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    quickSettingsManager.setVolume(progress, seekBar, volumeValue);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }

        if (btnVolumeDown != null) {
            btnVolumeDown.setOnClickListener(v -> {
                if (volumeSeek != null) {
                    int current = volumeSeek.getProgress();
                    volumeSeek.setProgress(Math.max(0, current - 10));
                }
            });
        }

        if (btnVolumeUp != null) {
            btnVolumeUp.setOnClickListener(v -> {
                if (volumeSeek != null) {
                    int current = volumeSeek.getProgress();
                    volumeSeek.setProgress(Math.min(100, current + 10));
                }
            });
        }
    }

    /**
     * Add a floating star button in the bottom-right corner that opens Favorites screen.
     * Created programmatically so we don't need to modify activity_main.xml.
     */
    private void addFloatingFavoritesButton() {
        try {
            // Create the floating button
            floatingFavoritesButton = new android.widget.ImageButton(this);
            floatingFavoritesButton.setImageResource(android.R.drawable.btn_star_big_on);
            floatingFavoritesButton.setColorFilter(0xFFFFFFFF);
            floatingFavoritesButton.setContentDescription("Open Favorites");
            floatingFavoritesButton.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);

            // Apply theme-based background
            refreshFloatingFavoritesColor();

            // Elevation for shadow effect
            floatingFavoritesButton.setElevation(8 * getResources().getDisplayMetrics().density);

            // Position: bottom-right corner with margin
            android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                    (int) (48 * getResources().getDisplayMetrics().density),
                    (int) (48 * getResources().getDisplayMetrics().density)
            );
            params.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
            int margin = (int) (16 * getResources().getDisplayMetrics().density);
            params.setMargins(margin, margin, margin, margin);

            // Click → open Favorites
            floatingFavoritesButton.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(this, FavoritesActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });

            // Long-press → show favorites count
            floatingFavoritesButton.setOnLongClickListener(v -> {
                int count = FavoritesManager.getInstance(this).getFavoritesCount();
                android.widget.Toast.makeText(this,
                        "⭐ " + count + " favorite" + (count == 1 ? "" : "s"),
                        android.widget.Toast.LENGTH_SHORT).show();
                return true;
            });

            // Add it to the activity's content area as an overlay
            addContentView(floatingFavoritesButton, params);

            android.util.Log.i("MainActivity", "Floating favorites button added");
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Failed to add floating favorites button", e);
        }
    }

    /**
     * Refresh the floating favorites button's background color to match the current theme.
     * Call this whenever the theme changes.
     */
    private void refreshFloatingFavoritesColor() {
        if (floatingFavoritesButton == null) return;
        try {
            com.jarjarblinkz.EvolveLauncher.theme.Theme theme =
                    com.jarjarblinkz.EvolveLauncher.theme.ThemeManager.getInstance(this).getCurrentTheme();

            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            bg.setColor(theme.accentPrimary);
            bg.setStroke(2, 0x80FFFFFF);
            floatingFavoritesButton.setBackground(bg);
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Failed to refresh favorites button color", e);
        }
    }

    private void showQuickSettings() {
        isQuickSettingsVisible = true;
        quickSettingsPanel.setVisibility(View.VISIBLE);

        // Slide in animation
        quickSettingsPanel.animate()
                .translationX(0)
                .alpha(1.0f)
                .setDuration(300)
                .start();
    }

    private void hideQuickSettings() {
        isQuickSettingsVisible = false;
        quickSettingsPanel.animate()
                .translationX(quickSettingsPanel.getWidth())
                .alpha(0.0f)
                .setDuration(300)
                .withEndAction(() -> quickSettingsPanel.setVisibility(View.GONE))
                .start();
    }

    // ===== END QUICK SETTINGS =====

    private void startStatusUpdates() {
        // Remove any existing callbacks first to avoid duplicates
        if (statusHandler != null && statusUpdateRunnable != null) {
            statusHandler.removeCallbacks(statusUpdateRunnable);
        }

        statusUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateTime();
                updateIPAddress();
                updateWifiSignal();
                statusHandler.postDelayed(this, 1000); // Update every 1 seconds
            }
        };
        statusHandler.post(statusUpdateRunnable);
    }

    private void updateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String currentTime = sdf.format(new Date());
        txtTime.setText(currentTime);
    }

    private void updateIPAddress() {
        String ipAddress = getDeviceIPAddress();
        if (ipAddress != null && !ipAddress.isEmpty()) {
            txtIP.setText("🌐 " + ipAddress);
            txtIP.setTextColor(Color.parseColor("#32CD32"));
        } else {
            txtIP.setText("No Internet");
            txtIP.setTextColor(Color.parseColor("#808080"));
        }
    }

    private void updateBatteryLevel(Intent batteryIntent) {
        if (batteryIntent == null) {
            batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        }

        if (batteryIntent != null) {
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;

            float batteryPercent = (level / (float) scale) * 100;
            int roundedPercent = Math.round(batteryPercent);

            runOnUiThread(() -> {
                txtBattery.setText(roundedPercent + "%");

                int batteryIconRes = R.drawable.ic_battery_full;
                if (isCharging) {
                    batteryIconRes = R.drawable.ic_battery_charging;
                } else if (roundedPercent <= 20) {
                    batteryIconRes = R.drawable.ic_battery_low;
                } else if (roundedPercent <= 50) {
                    batteryIconRes = R.drawable.ic_battery_half;
                } else if (roundedPercent <= 80) {
                    batteryIconRes = R.drawable.ic_battery_high;
                }
                // If >80%, use ic_battery_full (default)

                batteryIcon.setImageResource(batteryIconRes);

                if (roundedPercent <= 15) {
                    txtBattery.setTextColor(Color.parseColor("#FF6B6B"));
                    batteryIcon.setColorFilter(Color.parseColor("#FF6B6B"));
                } else if (isCharging) {
                    txtBattery.setTextColor(Color.parseColor("#4CAF50"));
                    batteryIcon.setColorFilter(Color.parseColor("#4CAF50"));
                } else {
                    txtBattery.setTextColor(Color.parseColor("#6B8EFF"));
                    batteryIcon.setColorFilter(Color.parseColor("#6B8EFF"));
                }
            });
        }
    }

    private void updateWifiSignal() {
        runOnUiThread(() -> {
            try {
                if (wifiManager != null && wifiManager.isWifiEnabled()) {
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    int rssi = wifiInfo.getRssi();
                    int level = WifiManager.calculateSignalLevel(rssi, 4);

                    int wifiIconRes;
                    String signalText;

                    // Only set text color, NOT icon color
                    int textColor;

                    switch (level) {
                        case 0:
                            wifiIconRes = R.drawable.ic_wifi_low;
                            signalText = "Weak";
                            textColor = Color.parseColor("#FF6B6B"); // Red text only
                            break;
                        case 1:
                            wifiIconRes = R.drawable.ic_wifi_low;
                            signalText = "Low";
                            textColor = Color.parseColor("#FF6B6B"); // Red text only
                            break;
                        case 2:
                            wifiIconRes = R.drawable.ic_wifi_medium;
                            signalText = "Good";
                            textColor = Color.parseColor("#FFA500"); // Orange text only
                            break;
                        case 3:
                            wifiIconRes = R.drawable.ic_wifi_full;
                            signalText = "Excellent";
                            textColor = Color.parseColor("#6B8EFF"); // Blue text only
                            break;
                        default:
                            wifiIconRes = R.drawable.ic_wifi_full;
                            signalText = "Full";
                            textColor = Color.parseColor("#6B8EFF"); // Blue text only
                            break;
                    }

                    wifiIcon.setImageResource(wifiIconRes);
                    txtWifiSignal.setText(signalText);
                    txtWifiSignal.setTextColor(textColor);

                    // IMPORTANT: Remove the color filter so the icon shows its true colors
                    wifiIcon.clearColorFilter();

                } else {
                    // WiFi is disabled
                    wifiIcon.setImageResource(R.drawable.ic_wifi_off);
                    txtWifiSignal.setText("Off");
                    txtWifiSignal.setTextColor(Color.parseColor("#808080"));
                    wifiIcon.clearColorFilter(); // No color filter - shows red slash
                }
            } catch (Exception e) {
                wifiIcon.setImageResource(R.drawable.ic_wifi_off);
                txtWifiSignal.setText("N/A");
                txtWifiSignal.setTextColor(Color.parseColor("#808080"));
                wifiIcon.clearColorFilter();
            }
        });
    }

    private void showWifiDetails() {
        try {
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int rssi = wifiInfo.getRssi();
                String ssid = wifiInfo.getSSID();
                if (ssid.equals("<unknown ssid>") || ssid.equals("0x")) {
                    ssid = "Hidden Network";
                }

                String details = "SSID: " + ssid.replace("\"", "") +
                        "\nSignal: " + rssi + " dBm" +
                        "\nSpeed: " + wifiInfo.getLinkSpeed() + " Mbps" +
                        "\nFrequency: " + wifiInfo.getFrequency() + " MHz";

                ThemedDialog.showThemed(new AlertDialog.Builder(this)
                        .setTitle("WiFi Details")
                        .setMessage(details)
                        .setPositiveButton("OK", null)
                        .create());
            } else {
                Toast.makeText(this, "WiFi is disabled", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Unable to get WiFi details", Toast.LENGTH_SHORT).show();
        }
    }

    private String getDeviceIPAddress() {
        try {
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int ip = wifiInfo.getIpAddress();
                return String.format(Locale.getDefault(), "%d.%d.%d.%d",
                        (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
            }

            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                if (activeNetwork != null && activeNetwork.isConnected()) {
                    if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                        try {
                            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                                NetworkInterface intf = en.nextElement();
                                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                                    InetAddress inetAddress = enumIpAddr.nextElement();
                                    if (!inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress() && inetAddress.isSiteLocalAddress()) {
                                        return inetAddress.getHostAddress();
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Continue to fallback
                        }
                    }
                }
            }

            try {
                for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                    NetworkInterface intf = en.nextElement();
                    for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                        InetAddress inetAddress = enumIpAddr.nextElement();
                        if (!inetAddress.isLoopbackAddress()) {
                            String ip = inetAddress.getHostAddress();
                            boolean isIPv4 = ip.indexOf(':') < 0;
                            if (isIPv4) {
                                return ip;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Return null if all methods fail
            }

        } catch (Exception e) {
            // Return null if all methods fail
        }

        return null;
    }

    public void refreshEditMode() {
        // FIXED: Use safe boolean helper
        isEditMode = getBooleanPreference("edit_mode", false);
        selectedApps.clear();
        appAdapter.notifyDataSetChanged();
    }

    private void saveCurrentCategory() {
        prefs.edit().putString(KEY_LAST_CATEGORY, currentCategory).apply();
    }

    /**
     * Start periodic playtime updates
     */
    private void startPlaytimeUpdates() {
        // Only start if permission is granted
        if (!prefs.getBoolean(KEY_PERMISSION_GRANTED, false)) {
            return;
        }

        playtimeUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updatePlaytimeData();
                playtimeHandler.postDelayed(this, 30000); // Update every 30 seconds
            }
        };
        playtimeHandler.post(playtimeUpdateRunnable);
    }

    /**
     * Update playtime data for all apps
     */
    private void updatePlaytimeData() {
        if (playtimeTracker == null) return;

        runOnUiThread(() -> {
            try {
                // Get playtime for different time ranges
                Map<String, Long> todayPlaytime = playtimeTracker.getTodayPlaytime();
                Map<String, Long> weekPlaytime = playtimeTracker.getLast7DaysPlaytime();
                Map<String, Long> monthPlaytime = playtimeTracker.getLast30DaysPlaytime();
                Map<String, Long> allTimePlaytime = playtimeTracker.getAllTimePlaytime();

                // Get currently running app
                String currentPackage = playtimeTracker.getCurrentRunningApp();

                // Update all apps in appList
                for (AppInfo app : appList) {
                    app.playtimeToday = todayPlaytime.getOrDefault(app.packageName, 0L);
                    app.playtimeWeek = weekPlaytime.getOrDefault(app.packageName, 0L);
                    app.playtimeMonth = monthPlaytime.getOrDefault(app.packageName, 0L);
                    app.playtimeAllTime = allTimePlaytime.getOrDefault(app.packageName, 0L);
                    app.isCurrentlyRunning = app.packageName.equals(currentPackage);
                }

                // Refresh adapter if we're showing playtime in UI
                if (appAdapter != null) {
                    appAdapter.notifyDataSetChanged();
                }

            } catch (Exception e) {
            }
        });
    }

    /**
     * Show playtime details for an app
     */
    private void showPlaytimeDetails(AppInfo app) {
        String playtimeInfo =
                "📊 Playtime for " + app.label + "\n\n" +
                        "Today: " + PlaytimeTracker.formatPlaytime(app.playtimeToday) + "\n" +
                        "This Week: " + PlaytimeTracker.formatPlaytime(app.playtimeWeek) + "\n" +
                        "This Month: " + PlaytimeTracker.formatPlaytime(app.playtimeMonth) + "\n" +
                        "All Time: " + PlaytimeTracker.formatPlaytime(app.playtimeAllTime) + "\n" +
                        (app.isCurrentlyRunning ? "\n▶️ Currently Running" : "");

        ThemedDialog.showThemed(new AlertDialog.Builder(this)
                .setTitle("Playtime Stats")
                .setMessage(playtimeInfo)
                .setPositiveButton("OK", null)
                .create());
    }

    /**
     * Show playtime leaderboard
     */
    public void showPlaytimeLeaderboard() {
        if (playtimeTracker == null) return;

        String[] timeRanges = {"Today", "This Week", "This Month", "All Time"};
        ThemedDialog.showThemed(new AlertDialog.Builder(this)
                .setTitle("Most Played Games")
                .setItems(timeRanges, (d, which) -> {
                    PlaytimeTracker.TimeRange range;
                    switch (which) {
                        case 0:
                            range = PlaytimeTracker.TimeRange.TODAY;
                            break;
                        case 1:
                            range = PlaytimeTracker.TimeRange.WEEK;
                            break;
                        case 2:
                            range = PlaytimeTracker.TimeRange.MONTH;
                            break;
                        default:
                            range = PlaytimeTracker.TimeRange.ALL_TIME;
                            break;
                    }
                    showLeaderboardForRange(range);
                })
                .create());
    }

    /**
     * Show leaderboard for specific time range
     */
    private void showLeaderboardForRange(PlaytimeTracker.TimeRange range) {
        if (playtimeTracker == null) return;

        List<PlaytimeTracker.PlaytimeEntry> leaderboard =
                playtimeTracker.getPlaytimeLeaderboard(20, range);

        if (leaderboard.isEmpty()) {
            Toast.makeText(this, "No playtime data yet", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("Top 20 Most Played\n\n");

        int rank = 1;
        for (PlaytimeTracker.PlaytimeEntry entry : leaderboard) {
            // Get app name from package using appInfo
            String packageName = entry.appInfo != null ? entry.appInfo.packageName : "";
            String appName = packageName;

            // Try to find a friendly name
            for (AppInfo app : appList) {
                if (app.packageName.equals(packageName)) {
                    appName = app.label;
                    break;
                }
            }

            // If still package name, clean it up
            if (appName.equals(packageName) && !appName.isEmpty()) {
                appName = cleanUpPackageName(packageName);
            }

            message.append(rank).append(". ")
                    .append(appName)
                    .append(" - ").append(entry.getFormattedPlaytime())
                    .append("\n");
            rank++;
        }

        String rangeText;
        switch (range) {
            case TODAY:
                rangeText = "Today";
                break;
            case WEEK:
                rangeText = "This Week";
                break;
            case MONTH:
                rangeText = "This Month";
                break;
            default:
                rangeText = "All Time";
                break;
        }

        ThemedDialog.showThemed(new AlertDialog.Builder(this)
                .setTitle("Leaderboard - " + rangeText)
                .setMessage(message.toString())
                .setPositiveButton("OK", null)
                .create());
    }

    /**
     * Toggle playtime display in grid
     */
    private void togglePlaytimeDisplay() {
        // You can cycle through time ranges
        PlaytimeTracker.TimeRange[] ranges = PlaytimeTracker.TimeRange.values();
        int nextIndex = (currentPlaytimeRange.ordinal() + 1) % ranges.length;
        currentPlaytimeRange = ranges[nextIndex];

        String rangeText;
        switch (currentPlaytimeRange) {
            case TODAY:
                rangeText = "Today";
                break;
            case WEEK:
                rangeText = "This Week";
                break;
            case MONTH:
                rangeText = "This Month";
                break;
            default:
                rangeText = "All Time";
                break;
        }

        Toast.makeText(this, "Showing: " + rangeText + " playtime", Toast.LENGTH_SHORT).show();
        appAdapter.notifyDataSetChanged();
    }

    public void refreshDisplay() {
        runOnUiThread(() -> {
            if (appAdapter != null) {
                // Force complete rebind of all items
                appAdapter.notifyItemRangeChanged(0, appAdapter.getItemCount());

                // Force grid to redraw completely
                if (appsGrid != null) {
                    appsGrid.invalidate();
                    appsGrid.requestLayout();
                    appsGrid.scheduleLayoutAnimation();
                }
            }
        });
    }

    /**
     * RESTORED: Update icon sizes using the old launcher logic
     * Sets a FIXED column width, letting the GridView calculate columns naturally
     * This is what made the old launcher work perfectly on Quest
     */
    public void updateIconSizes() {
        isEditMode = getBooleanPreference("edit_mode", false);

        // Get the user's selected icon scale index (0-4, default 2 = 125dp)
        int scaleIndex = prefs.getInt("icon_size_scale", DEFAULT_SCALE_INDEX);
        int iconSizeDp = ICON_SCALES_DP[scaleIndex];

        // Save the actual icon size in dp for the adapter to use
        prefs.edit().putInt("icon_size", iconSizeDp).apply();

        // Calculate columns based on fixed icon size
        int columns = calculateOptimalColumns();

        if (appsGrid != null) {
            GridLayoutManager glm = (GridLayoutManager) appsGrid.getLayoutManager();
            if (glm != null) {
                glm.setSpanCount(columns);
                if (gridSpacingDecoration != null) {
                    gridSpacingDecoration.setSpanCount(columns);
                }
            }
        }

        if (appAdapter != null) {
            appAdapter.notifyDataSetChanged();
        }

        Toast.makeText(this, "Icon size: " + iconSizeDp + "dp, " + columns + " columns", Toast.LENGTH_SHORT).show();
    }

    public void updateBackground() {
        runOnUiThread(() -> {
            try {
                View mainLayout = findViewById(R.id.mainLayout);
                if (mainLayout == null) return;

                int opacity = prefs.getInt("background_opacity", 100);

                // Convert opacity (0-100) to alpha (0-255)
                int alpha = (int) ((opacity / 100f) * 255);

                // Always keep the layout itself fully opaque
                mainLayout.setAlpha(1.0f);

                // Get current theme
                com.jarjarblinkz.EvolveLauncher.theme.Theme currentTheme =
                        com.jarjarblinkz.EvolveLauncher.theme.ThemeManager.getInstance(this).getCurrentTheme();

                // ALPHA = 0 means fully transparent - hide everything
                if (alpha == 0) {
                    mainLayout.setBackgroundColor(Color.TRANSPARENT);
                } else if (currentTheme != null && currentTheme.backgroundType ==
                        com.jarjarblinkz.EvolveLauncher.theme.Theme.BackgroundType.IMAGE) {
                    // Determine if it's a built-in asset or custom URI
                    Object themeAsset;
                    String imagePath = currentTheme.backgroundImagePath;
                    if (imagePath.startsWith("content://") || imagePath.startsWith("file://")) {
                        // Custom user-picked image
                        themeAsset = Uri.parse(imagePath);
                    } else {
                        // Built-in asset
                        themeAsset = "file:///android_asset/theme_bgs/" + imagePath;
                    }

                    try {
                        Glide.with(this)
                                .load(themeAsset)
                                .centerCrop()
                                .into(new CustomTarget<Drawable>(mainLayout.getWidth() > 0 ? mainLayout.getWidth() : 1920,
                                        mainLayout.getHeight() > 0 ? mainLayout.getHeight() : 1080) {
                                    @Override
                                    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                                        Drawable mutableDrawable = resource.mutate();
                                        mutableDrawable.setAlpha(alpha);
                                        mainLayout.setBackground(mutableDrawable);
                                    }
                                    @Override
                                    public void onLoadCleared(@Nullable Drawable placeholder) {}
                                });
                    } catch (Exception e) {
                        e.printStackTrace();
                        mainLayout.setBackgroundColor(currentTheme.bgPrimary);
                    }
                } else if (currentTheme != null) {
                    // Theme has solid background
                    int themeColor = currentTheme.bgPrimary;
                    int r = Color.red(themeColor);
                    int g = Color.green(themeColor);
                    int b = Color.blue(themeColor);
                    mainLayout.setBackgroundColor(Color.argb(alpha, r, g, b));
                } else {
                    // Fallback (shouldn't happen)
                    String hexColor = String.format("#%02X0A0A0A", alpha);
                    mainLayout.setBackgroundColor(Color.parseColor(hexColor));
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * FIXED: refreshAll() no longer creates a new GridLayoutManager.
     * It just updates the span count on the existing one, keeping the
     * OnLayoutChangeListener from setupRecyclerView() intact.
     */
    public void refreshAll() {
        runOnUiThread(() -> {
            if (searchEditText != null) {
                searchEditText.setText("");
            }

            // DON'T reset to "All Apps" - load saved category instead
            currentCategory = prefs.getString(KEY_LAST_CATEGORY, "All Apps");

            // Validate category still exists
            if (!currentCategory.equals("All Apps") && !categories.containsKey(currentCategory)) {
                currentCategory = "All Apps";
                saveCurrentCategory();
            }

            updateCategoryButtonStates(currentCategory);
            updateSearchStatus("", false);
            selectedApps.clear();

            loadCategories();
            loadUserApps();
            buildCategoryBar();
            filterApps("");

            int columns = calculateOptimalColumns();
            if (appsGrid != null) {
                GridLayoutManager glm = (GridLayoutManager) appsGrid.getLayoutManager();
                if (glm != null) {
                    glm.setSpanCount(columns);
                    if (gridSpacingDecoration != null) {
                        gridSpacingDecoration.setSpanCount(columns);
                    }
                }
            }

            if (appAdapter != null) {
                appAdapter.notifyDataSetChanged();
            }

            updateBackground();

            if (appsGrid != null) {
                appsGrid.invalidate();
                appsGrid.requestLayout();
            }

            updateWifiSignal();

            Toast.makeText(this, "Launcher refreshed", Toast.LENGTH_SHORT).show();
        });
    }

    private GridSpacingItemDecoration gridSpacingDecoration;

    /**
     * Tracks whether we've already played the entry animation this session.
     */
    private boolean entryAnimationPlayed = false;

    /**
     * VR POLISH: animates ALL currently visible cards flying in from 8 different
     * directions. Runs ONCE per session, triggered by a layout listener after
     * the grid has fully laid out its children.
     *
     * Using a batch approach (all-at-once after layout) instead of per-bind
     * animation avoids race conditions where some cards animate and others
     * don't, depending on when their onBindViewHolder fires relative to the
     * RecyclerView's layout pass.
     */
    private void playEntryAnimation() {
        if (entryAnimationPlayed || appsGrid == null) return;
        if (appsGrid.getChildCount() == 0) return;  // no children yet, wait
        entryAnimationPlayed = true;

        float density = getResources().getDisplayMetrics().density;
        float distance = 300f * density;

        // STEP 1: While the grid is still alpha=0 (invisible), set each child
        // to its off-screen starting state. This way when we reveal the grid,
        // the children are already invisible (alpha 0) and off-position - no
        // static-layout flash.
        for (int i = 0; i < appsGrid.getChildCount(); i++) {
            View card = appsGrid.getChildAt(i);
            if (card == null) continue;

            int direction = i % 8;
            float startX = 0f, startY = 0f;
            switch (direction) {
                case 0: startY = -distance; break;
                case 1: startX = distance;  startY = -distance; break;
                case 2: startX = distance;  break;
                case 3: startX = distance;  startY = distance;  break;
                case 4: startY = distance;  break;
                case 5: startX = -distance; startY = distance;  break;
                case 6: startX = -distance; break;
                case 7: startX = -distance; startY = -distance; break;
            }

            float startRotation = (direction % 2 == 0) ? -8f : 8f;

            card.setTranslationX(startX);
            card.setTranslationY(startY);
            card.setAlpha(0f);
            card.setScaleX(0.5f);
            card.setScaleY(0.5f);
            card.setRotation(startRotation);
        }

        // STEP 2: Reveal the grid container - children still invisible because
        // their individual alpha is 0
        appsGrid.setAlpha(1f);

        // STEP 3: Animate each child flying in
        for (int i = 0; i < appsGrid.getChildCount(); i++) {
            View card = appsGrid.getChildAt(i);
            if (card == null) continue;

            long delay = i * 45L;

            card.animate()
                    .translationX(0f)
                    .translationY(0f)
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .rotation(0f)
                    .setDuration(650)
                    .setStartDelay(delay)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                    .start();
        }
    }

    /**
     * Sets up a polling check that fires the entry animation once children
     * have been laid out. The initial delay (300ms) lets any other initial
     * setup logic (column recalculation, deferred notifyDataSetChanged, etc.)
     * complete and stabilize before we animate, so the animation isn't
     * destroyed by a subsequent rebinding.
     */
    private void scheduleEntryAnimation() {
        if (appsGrid == null) return;
        // Initial delay lets the layout settle (column calc, initial bindings)
        appsGrid.postDelayed(() -> tryPlayEntryAnimation(0), 300);
    }

    private void tryPlayEntryAnimation(int attempt) {
        if (entryAnimationPlayed || appsGrid == null) return;
        if (attempt > 20) {
            // Safety: never hide the grid forever - reveal it even if animation fails
            appsGrid.setAlpha(1f);
            entryAnimationPlayed = true;
            return;
        }

        if (appsGrid.getChildCount() > 0) {
            playEntryAnimation();
        } else {
            // No children yet - try again in 100ms
            appsGrid.postDelayed(() -> tryPlayEntryAnimation(attempt + 1), 100);
        }
    }

    /**
     * VR POLISH: Adds hover, focus, and press animations to a card.
     * On Quest, the controller pointer triggers hover events on Android views,
     * making this effectively a "looking-at-it / pointing-at-it" highlight.
     *
     *   - HOVER / FOCUS:  card pops forward dramatically (scale 1.15×, +40dp lift)
     *                     with an overshoot bounce when pointer enters
     *   - PRESS:          card briefly compresses, then POPS even further on release
     *                     (scale 1.22×, +56dp lift) before settling back to hover
     */
    /**
     * Smoothly transitions between categories: existing cards fly out to
     * 8 different directions, then the new category's cards fly in from
     * 8 different directions. Same drama as the initial entry animation.
     */
    private void switchToCategoryAnimated(String newCategory) {
        if (newCategory == null) return;
        if (newCategory.equals(currentCategory)) return;  // no change, skip animation

        Log.i("MainActivity", "switchToCategoryAnimated: " + currentCategory + " → " + newCategory);

        animateGridTransition(() -> {
            currentCategory = newCategory;
            saveCurrentCategory();
            updateCategoryButtonStates(newCategory);
            // Clear search text here (during the hidden phase) so the
            // TextWatcher doesn't fire filterApps while the exit animation
            // is running and kill it.
            if (searchEditText != null) {
                searchEditText.setText("");
            }
            filterApps("");
        });
    }

    /**
     * Animate current cards out (fly to 8 directions), then run the data swap,
     * then animate new cards in. Total transition ~700ms.
     */
    private void animateGridTransition(Runnable dataSwap) {
        if (appsGrid == null) {
            Log.i("MainActivity", "animateGridTransition: appsGrid is null");
            dataSwap.run();
            return;
        }
        int childCount = appsGrid.getChildCount();
        Log.i("MainActivity", "animateGridTransition: animating " + childCount + " cards out");

        if (childCount == 0) {
            dataSwap.run();
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        float distance = 200f * density;

        // STEP 1: animate current cards OUT
        for (int i = 0; i < appsGrid.getChildCount(); i++) {
            View card = appsGrid.getChildAt(i);
            if (card == null) continue;

            // Exit direction cycles through 8 angles, same pattern as entry
            int direction = i % 8;
            float endX = 0f, endY = 0f;
            switch (direction) {
                case 0: endY = -distance; break;
                case 1: endX = distance;  endY = -distance; break;
                case 2: endX = distance;  break;
                case 3: endX = distance;  endY = distance;  break;
                case 4: endY = distance;  break;
                case 5: endX = -distance; endY = distance;  break;
                case 6: endX = -distance; break;
                case 7: endX = -distance; endY = -distance; break;
            }

            card.animate().cancel();
            card.animate()
                    .translationX(endX)
                    .translationY(endY)
                    .alpha(0f)
                    .scaleX(0.5f)
                    .scaleY(0.5f)
                    .rotation((direction % 2 == 0) ? 8f : -8f)
                    .setDuration(280)
                    .setStartDelay(i * 12L)  // light stagger on exit
                    .setInterpolator(new android.view.animation.AccelerateInterpolator())
                    .start();
        }

        // STEP 2: after exit animation, swap data and animate new cards in
        appsGrid.postDelayed(() -> {
            // Reset the one-shot flag so entry animation can play again
            entryAnimationPlayed = false;
            // Hide grid briefly during data swap to prevent a flash of the
            // new items at their final position
            appsGrid.setAlpha(0f);
            // Perform the actual category/filter change
            dataSwap.run();
            // CRITICAL: notifyDataSetChanged inside dataSwap triggers a layout
            // pass that re-binds all children. We MUST wait for that layout
            // pass to complete before starting the entry animation, otherwise
            // the rebind will reset the translation/alpha/scale we just set
            // and the cards will appear at their final position instead of
            // flying in. This was specifically breaking on categories with
            // many items where layout takes longer.
            appsGrid.postDelayed(() -> tryPlayEntryAnimation(0), 80);
        }, 320);
    }

    private boolean categoryBarEntryAnimated = false;
    private final java.util.HashSet<String> animatedCategoryNames = new java.util.HashSet<>();

    /**
     * VR POLISH: hover/focus/press animations for category buttons.
     * Same drama as the app cards but slightly less dramatic since buttons
     * are smaller. Uses scale + translation only (no translationZ since
     * default Button widgets don't have card-like shadows).
     */
    private void applyButtonInteractionEffects(View btn) {
        if (btn == null) return;

        // Log button setup once so we can verify the latest build is installed
        String btnTag = (btn instanceof Button) ? ((Button) btn).getText().toString() : "button";
        Log.i("MainActivity", "Applying interaction effects to: " + btnTag);

        final float hoverScale = 1.10f;
        final float pressScale = 0.94f;
        final float popScale = 1.16f;

        btn.setFocusable(true);
        btn.setClickable(true);

        btn.setOnHoverListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_HOVER_ENTER:
                    v.animate().cancel();
                    v.animate().scaleX(hoverScale).scaleY(hoverScale)
                            .setDuration(180)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(1.8f))
                            .start();
                    break;
                case android.view.MotionEvent.ACTION_HOVER_EXIT:
                    v.animate().cancel();
                    v.animate().scaleX(1f).scaleY(1f)
                            .setDuration(180)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .start();
                    break;
            }
            return false;
        });

        btn.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().cancel();
            if (hasFocus) {
                v.animate().scaleX(hoverScale).scaleY(hoverScale)
                        .setDuration(180)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.8f))
                        .start();
            } else {
                v.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
            }
        });

        btn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().cancel();
                    v.animate().scaleX(pressScale).scaleY(pressScale)
                            .setDuration(70)
                            .setInterpolator(new android.view.animation.AccelerateInterpolator())
                            .start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                    Log.i("MainActivity", "Button ACTION_UP fired");
                    v.animate().cancel();
                    v.animate().scaleX(popScale).scaleY(popScale)
                            .setDuration(180)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(2.5f))
                            .withEndAction(() -> {
                                float endScale = v.isHovered() || v.isFocused() ? hoverScale : 1f;
                                v.animate().scaleX(endScale).scaleY(endScale)
                                        .setDuration(200)
                                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                                        .start();
                            })
                            .start();
                    break;
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().cancel();
                    float endScale = v.isHovered() || v.isFocused() ? hoverScale : 1f;
                    v.animate().scaleX(endScale).scaleY(endScale).setDuration(180).start();
                    break;
            }
            return false;
        });
    }

    /**
     * Animates a single category button popping in from below.
     * Used for both the initial entry animation and for buttons added later
     * (e.g., when a new category is created in Settings).
     */
    private void animateCategoryButtonEntry(View btn, int indexInBar) {
        if (btn == null) return;
        float density = getResources().getDisplayMetrics().density;

        btn.setTranslationY(80f * density);   // start below
        btn.setAlpha(0f);
        btn.setScaleX(0.6f);
        btn.setScaleY(0.6f);
        btn.setRotation((indexInBar % 2 == 0) ? -6f : 6f);

        btn.animate()
                .translationY(0f)
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .rotation(0f)
                .setStartDelay(indexInBar * 60L)
                .setDuration(500)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.3f))
                .start();
    }

    /**
     * Initial entry animation for the entire category bar.
     * Only fires once per session (subsequent rebuilds don't re-animate).
     */
    private void animateCategoryBarEntry() {
        if (categoryBarEntryAnimated || categoryBar == null) return;
        if (categoryBar.getChildCount() == 0) return;
        categoryBarEntryAnimated = true;

        for (int i = 0; i < categoryBar.getChildCount(); i++) {
            View btn = categoryBar.getChildAt(i);
            animateCategoryButtonEntry(btn, i);
            // Track this button's label so it doesn't re-animate on later rebuilds
            if (btn instanceof Button) {
                animatedCategoryNames.add(((Button) btn).getText().toString());
            }
        }
    }

    private void applyCardInteractionEffects(View card) {
        if (card == null) return;

        final float density = getResources().getDisplayMetrics().density;
        final float hoverLiftPx = 40f * density;      // hover pop forward
        final float popLiftPx = 56f * density;        // click pop even further
        final float hoverScale = 1.15f;               // hover scale - dramatic
        final float pressScale = 0.97f;               // brief compress on press
        final float popScale = 1.22f;                 // click pop - even bigger

        card.setFocusable(true);
        card.setClickable(true);

        // Pointer hover - dramatic pop forward with overshoot bounce
        card.setOnHoverListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_HOVER_ENTER:
                    v.animate().cancel();
                    v.animate()
                            .scaleX(hoverScale)
                            .scaleY(hoverScale)
                            .translationZ(hoverLiftPx)
                            .setDuration(220)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(1.8f))
                            .start();
                    break;
                case android.view.MotionEvent.ACTION_HOVER_EXIT:
                    v.animate().cancel();
                    v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .translationZ(0f)
                            .setDuration(200)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .start();
                    break;
            }
            return false;  // let click events through
        });

        // Keyboard / dpad focus mirrors hover with same drama
        card.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().cancel();
            if (hasFocus) {
                v.animate()
                        .scaleX(hoverScale)
                        .scaleY(hoverScale)
                        .translationZ(hoverLiftPx)
                        .setDuration(220)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.8f))
                        .start();
            } else {
                v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .translationZ(0f)
                        .setDuration(200)
                        .start();
            }
        });

        // Press feedback: brief compress + dramatic POP-OUT on release.
        // This makes the card visibly jump toward the user when clicked.
        card.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().cancel();
                    v.animate()
                            .scaleX(pressScale)
                            .scaleY(pressScale)
                            .setDuration(70)
                            .setInterpolator(new android.view.animation.AccelerateInterpolator())
                            .start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                    v.animate().cancel();
                    // POP-OUT: card jumps forward dramatically with overshoot,
                    // then settles to hover state (or rest if no longer hovered)
                    v.animate()
                            .scaleX(popScale)
                            .scaleY(popScale)
                            .translationZ(popLiftPx)
                            .setDuration(220)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(2.5f))
                            .withEndAction(() -> {
                                // After the pop, ease back to hover/rest state
                                float endScale = v.isHovered() || v.isFocused() ? hoverScale : 1f;
                                float endLift = v.isHovered() || v.isFocused() ? hoverLiftPx : 0f;
                                v.animate()
                                        .scaleX(endScale)
                                        .scaleY(endScale)
                                        .translationZ(endLift)
                                        .setDuration(260)
                                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                                        .start();
                            })
                            .start();
                    break;
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().cancel();
                    float endScale = v.isHovered() || v.isFocused() ? hoverScale : 1f;
                    float endLift = v.isHovered() || v.isFocused() ? hoverLiftPx : 0f;
                    v.animate()
                            .scaleX(endScale)
                            .scaleY(endScale)
                            .translationZ(endLift)
                            .setDuration(180)
                            .start();
                    break;
            }
            return false;  // let click events through
        });
    }

    private void setupRecyclerView() {
        int columns = calculateOptimalColumns();

        GridLayoutManager layoutManager = new GridLayoutManager(this, columns) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        };

        appsGrid.setLayoutManager(layoutManager);
        appsGrid.setHasFixedSize(false);
        appsGrid.setItemViewCacheSize(50);
        appsGrid.setDrawingCacheEnabled(true);
        appsGrid.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_AUTO);
        appsGrid.setItemAnimator(null);

        // Entry animation is now handled programmatically per-card in
        // animateCardEntryIfNeeded() so each card can come from a different direction.

        float density = getResources().getDisplayMetrics().density;
        int spacingInPixels = (int) (2 * density);
        gridSpacingDecoration = new GridSpacingItemDecoration(columns, spacingInPixels, true);
        appsGrid.addItemDecoration(gridSpacingDecoration);

        // Listen for window resize - recalculate columns when appsGrid bounds change
        // This handles multi-window resizing on Quest (no Activity restart)
        appsGrid.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            int lastWidth = 0;
            int lastHeight = 0;
            Handler resizeHandler = new Handler();
            Runnable resizeRunnable;

            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int newWidth = right - left;
                int newHeight = bottom - top;

                if ((newWidth == lastWidth && newHeight == lastHeight) || newWidth == 0) return;
                lastWidth = newWidth;
                lastHeight = newHeight;

                // Debounce rapid resize events
                if (resizeRunnable != null) {
                    resizeHandler.removeCallbacks(resizeRunnable);
                }

                resizeRunnable = () -> {
                    // Recalculate columns for new width
                    int newColumns = calculateOptimalColumns();
                    GridLayoutManager glm = (GridLayoutManager) appsGrid.getLayoutManager();
                    if (glm != null && glm.getSpanCount() != newColumns) {
                        glm.setSpanCount(newColumns);

                        if (gridSpacingDecoration != null) {
                            gridSpacingDecoration.setSpanCount(newColumns);
                        }

                        // Force adapter to recalculate item sizes
                        if (appAdapter != null) {
                            appAdapter.notifyDataSetChanged();
                        }

                        // Force grid to redraw
                        appsGrid.invalidate();
                        appsGrid.requestLayout();
                    }
                };

                resizeHandler.postDelayed(resizeRunnable, 100);
            }
        });
    }

    public class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
        private int spanCount;
        private int spacing;
        private boolean includeEdge;

        public GridSpacingItemDecoration(int spanCount, int spacing, boolean includeEdge) {
            this.spanCount = spanCount;
            this.spacing = spacing;
            this.includeEdge = includeEdge;
        }

        public void setSpanCount(int spanCount) {
            this.spanCount = spanCount;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            int column = position % spanCount;

            if (includeEdge) {
                outRect.left = spacing - column * spacing / spanCount;
                outRect.right = (column + 1) * spacing / spanCount;

                if (position < spanCount) {
                    outRect.top = spacing;
                }
                outRect.bottom = spacing;
            } else {
                outRect.left = column * spacing / spanCount;
                outRect.right = spacing - (column + 1) * spacing / spanCount;
                if (position >= spanCount) {
                    outRect.top = spacing;
                }
            }
        }
    }

    /**
     * RESTORED: Calculate columns based on FIXED icon size (old launcher logic)
     * This calculates how many fixed-size cards fit in the available width
     * Instead of calculating icon size based on columns
     */
    private int calculateOptimalColumns() {
        // Get current window width - in multi-window mode this differs from full screen
        int usableWidthPx;
        if (appsGrid != null && appsGrid.getWidth() > 0) {
            usableWidthPx = appsGrid.getWidth();
        } else {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            usableWidthPx = displayMetrics.widthPixels;
        }

        // Get the FIXED icon size from user preference (restored old logic)
        int scaleIndex = prefs.getInt("icon_size_scale", DEFAULT_SCALE_INDEX);
        int iconSizeDp = ICON_SCALES_DP[scaleIndex];
        float density = getResources().getDisplayMetrics().density;
        int iconWidthPx = (int) (iconSizeDp * density);

        // Calculate total card width: icon width + horizontal overhead (margin + padding)
        int cardOverheadPx = (int) (CARD_HORIZONTAL_OVERHEAD_DP * density);
        int totalCardWidthPx = iconWidthPx + cardOverheadPx;

        // Calculate how many of these fixed-size cards fit in the available width
        int columns = usableWidthPx / totalCardWidthPx;
        columns = Math.max(1, columns);

        // Apply maximum column limits (prevent too many tiny cells)
        if (iconSizeDp <= 90) {
            columns = Math.min(columns, 15);
        } else if (iconSizeDp <= 110) {
            columns = Math.min(columns, 12);
        } else {
            columns = Math.min(columns, 10);
        }

        return columns;
    }

    private void loadUserApps() {
        appList.clear();

        List<ApplicationInfo> allApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo appInfo : allApps) {
            try {
                String packageName = appInfo.packageName;

                if (isInSystemPackageList(packageName)) {
                    continue;
                }

                // FIXED: Use safe boolean helper for hidden apps
                boolean isHidden = getBooleanPreference("hidden_" + packageName, false);
                if (isHidden) {
                    continue;
                }

                String appName = getAppName(packageName, appInfo);

                if (appName == null || appName.isEmpty()) {
                    continue;
                }

                Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
                if (launchIntent == null) {
                    Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
                    launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                    launcherIntent.setPackage(packageName);

                    List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0);
                    if (resolveInfos == null || resolveInfos.isEmpty()) {
                        continue;
                    }
                }

                AppInfo app = new AppInfo();
                app.label = appName;
                app.packageName = packageName;
                app.icon = appInfo.loadIcon(packageManager);
                app.isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                app.githubIconUrl = getGitHubIconUrl(packageName);
                app.isHidden = isHidden;

                // Get install/update info from PackageInfo
                try {
                    PackageInfo pkgInfo = packageManager.getPackageInfo(packageName, 0);
                    app.versionName = pkgInfo.versionName != null ? pkgInfo.versionName : "N/A";

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    app.firstInstallDate = sdf.format(new Date(pkgInfo.firstInstallTime));
                    app.lastUpdateDate = sdf.format(new Date(pkgInfo.lastUpdateTime));

                    // Check if from store
                    try {
                        String installer = packageManager.getInstallerPackageName(packageName);
                        app.isStoreApp = "com.oculus.store".equals(installer) ||
                                "com.android.vending".equals(installer) ||
                                "com.oculus.tw".equals(installer);
                        app.isSideloaded = !app.isSystemApp && !app.isStoreApp;
                    } catch (Exception e) {
                        app.isStoreApp = false;
                        app.isSideloaded = !app.isSystemApp;
                    }
                } catch (Exception e) {
                    app.versionName = "N/A";
                    app.firstInstallDate = "N/A";
                    app.lastUpdateDate = "N/A";
                }

                // Load saved category from preferences
                String savedCategory = prefs.getString("cat_" + packageName, "Uncategorized");
                // Defensive: if the saved category no longer exists (e.g., stale
                // data from a previous version), treat the app as uncategorized.
                if (!savedCategory.equals("Uncategorized") && !categories.containsKey(savedCategory)) {
                    savedCategory = "Uncategorized";
                }
                app.category = savedCategory;

                // Also check if it's in any category from categoryPrefs
                for (Map.Entry<String, Set<String>> entry : categories.entrySet()) {
                    if (entry.getValue().contains(packageName)) {
                        app.category = entry.getKey();
                        break;
                    }
                }

                appList.add(app);

            } catch (Exception e) {
                continue;
            }
        }

        appList.sort((a1, a2) -> {
            String label1 = a1.label != null ? a1.label.toLowerCase() : "";
            String label2 = a2.label != null ? a2.label.toLowerCase() : "";
            return label1.compareTo(label2);
        });

        preloadIcons();
    }

    private String getAppName(String packageName, ApplicationInfo appInfo) {
        String appName = null;

        CharSequence label = appInfo.loadLabel(packageManager);
        if (label != null && !label.toString().trim().isEmpty()) {
            appName = label.toString().trim();
        }

        if (appName == null || appName.isEmpty() || appName.equals(packageName) ||
                appName.replace(".", "").equals(packageName.replace(".", ""))) {
            try {
                PackageInfo pkgInfo = packageManager.getPackageInfo(packageName, 0);
                if (pkgInfo.applicationInfo != null) {
                    CharSequence pkgLabel = pkgInfo.applicationInfo.loadLabel(packageManager);
                    if (pkgLabel != null && !pkgLabel.toString().trim().isEmpty()) {
                        appName = pkgLabel.toString().trim();
                    }
                }
            } catch (Exception e) {
                // Continue to fallback
            }
        }

        if (appName == null || appName.isEmpty() || appName.equals(packageName) ||
                appName.length() < 3 || appName.replace(".", "").length() < 3) {
            appName = cleanUpPackageName(packageName);
        }

        return appName;
    }

    private String cleanUpPackageName(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return "App";
        }

        String name = packageName;
        if (name.startsWith("com.")) {
            name = name.substring(4);
        } else if (name.startsWith("org.")) {
            name = name.substring(4);
        } else if (name.startsWith("net.")) {
            name = name.substring(4);
        }

        String[] parts = name.split("\\.");
        if (parts.length > 0) {
            String lastPart = parts[parts.length - 1];

            if (lastPart.endsWith("VR")) {
                lastPart = lastPart.substring(0, lastPart.length() - 2);
            }
            if (lastPart.endsWith("vr")) {
                lastPart = lastPart.substring(0, lastPart.length() - 2);
            }
            if (lastPart.endsWith("Quest")) {
                lastPart = lastPart.substring(0, lastPart.length() - 5);
            }

            StringBuilder result = new StringBuilder();
            for (int i = 0; i < lastPart.length(); i++) {
                char c = lastPart.charAt(i);
                if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(lastPart.charAt(i - 1))) {
                    result.append(" ");
                }
                result.append(i == 0 ? Character.toUpperCase(c) : c);
            }

            return result.toString().trim();
        }

        return packageName;
    }

    private void preloadIcons() {
        if (!ENABLE_IMAGE_CACHING) return;

        List<AppInfo> snapshot;
        synchronized (appList) {
            snapshot = new ArrayList<>(appList);
        }

        executorService.execute(() -> {
            // Use the restored icon scale system
            int scaleIndex = prefs.getInt("icon_size_scale", DEFAULT_SCALE_INDEX);
            int iconSizeDp = ICON_SCALES_DP[scaleIndex];
            int iconHeightDp = (int) (iconSizeDp * 0.5625f);
            float density = getResources().getDisplayMetrics().density;
            int iconWidthPx = (int) (iconSizeDp * density);
            int iconHeightPx = (int) (iconHeightDp * density);

            for (AppInfo app : snapshot) {
                try {
                    Glide.with(MainActivity.this)
                            .load(app.githubIconUrl)
                            .preload(iconWidthPx, iconHeightPx);
                } catch (Exception e) {
                    // Ignore preload errors
                }
            }
        });
    }

    private String getGitHubIconUrl(String packageName) {
        return GITHUB_ICON_BASE_URL + packageName + ".jpg";
    }

    private boolean isSystemApp(ApplicationInfo appInfo) {
        boolean isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        boolean isUpdatedSystemApp = (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
        return isSystemApp || isUpdatedSystemApp;
    }

    private boolean isInSystemPackageList(String packageName) {
        for (String systemPackage : SYSTEM_PACKAGES) {
            if (packageName.startsWith(systemPackage)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAppLaunchable(String packageName) {
        try {
            Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                return true;
            }

            Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
            launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            launcherIntent.setPackage(packageName);

            List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0);
            return resolveInfos != null && !resolveInfos.isEmpty();

        } catch (Exception e) {
            return false;
        }
    }

    private void filterApps(String query) {
        List<AppInfo> newFilteredList = new ArrayList<>();
        FavoritesManager favManagerFilter = FavoritesManager.getInstance(this);

        if (query.isEmpty()) {
            if (currentCategory.equals("All Apps")) {
                for (AppInfo app : appList) {
                    if (favManagerFilter.isFavorite(app.packageName)) continue;  // Skip favorites
                    boolean isInAnyCategory = false;
                    for (Set<String> categoryApps : categories.values()) {
                        if (categoryApps.contains(app.packageName)) {
                            isInAnyCategory = true;
                            break;
                        }
                    }
                    if (!isInAnyCategory) {
                        newFilteredList.add(app);
                    }
                }
            } else {
                Set<String> pkgs = categories.get(currentCategory);
                if (pkgs != null) {
                    for (AppInfo app : appList) {
                        if (favManagerFilter.isFavorite(app.packageName)) continue;  // Skip favorites
                        if (pkgs.contains(app.packageName)) {
                            app.category = currentCategory;
                            newFilteredList.add(app);
                        }
                    }
                }
            }

            updateSearchStatus("", false);

        } else {
            // When searching, show ALL matching apps including favorites
            // (so user can find what to unfavorite or favorite)
            String lowerCaseQuery = query.toLowerCase();

            for (AppInfo app : appList) {
                boolean matchesSearch = app.label != null &&
                        (app.label.toLowerCase().contains(lowerCaseQuery) ||
                                app.packageName.toLowerCase().contains(lowerCaseQuery) ||
                                (app.category != null && app.category.toLowerCase().contains(lowerCaseQuery)));

                if (matchesSearch) {
                    newFilteredList.add(app);
                }
            }

            newFilteredList.sort((a1, a2) -> {
                String label1 = a1.label != null ? a1.label : "";
                String label2 = a2.label != null ? a2.label : "";
                return label1.compareToIgnoreCase(label2);
            });

            String status = "Found " + newFilteredList.size() + " app" + (newFilteredList.size() != 1 ? "s" : "");
            updateSearchStatus(status, true);
        }

        filteredList.clear();
        filteredList.addAll(newFilteredList);

        if (appAdapter != null) {
            appAdapter.notifyDataSetChanged();
        }

        ImageView clearSearch = findViewById(R.id.clearSearch);
        if (clearSearch != null) {
            clearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private void launchApp(AppInfo app) {
        try {
            Intent intent;

            if (app.activityName != null && !app.activityName.isEmpty()) {
                intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                intent.setComponent(new ComponentName(app.packageName, app.activityName));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            } else {
                intent = packageManager.getLaunchIntentForPackage(app.packageName);
                if (intent == null) {
                    Toast.makeText(this, "Cannot launch " + app.label, Toast.LENGTH_SHORT).show();
                    return;
                }
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }

            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } catch (Exception e) {
            Toast.makeText(this, "Error launching " + app.label, Toast.LENGTH_SHORT).show();
        }
    }

    public static class AppInfo {
        public String label;
        public String packageName;
        public String activityName = "";
        public Drawable icon;
        public boolean isSystemApp;
        public String githubIconUrl;
        public String category = "Uncategorized";
        public boolean isHidden = false;

        // NEW: Playtime tracking fields
        public long playtimeToday = 0;
        public long playtimeWeek = 0;
        public long playtimeMonth = 0;
        public long playtimeAllTime = 0;
        public boolean isCurrentlyRunning = false;

        // NEW: Install/Update info
        public String firstInstallDate = "";
        public String lastUpdateDate = "";
        public String versionName = "";
        public boolean isStoreApp = false;
        public boolean isSideloaded = false;
    }

    private void loadCategories() {
        categories.clear();
        Map<String, ?> all = categoryPrefs.getAll();
        for (String key : all.keySet()) {
            if (key.startsWith("cat_")) {
                String categoryName = key.substring(4);
                Set<String> packageSet = categoryPrefs.getStringSet(key, new HashSet<>());
                categories.put(categoryName, packageSet);
            }
        }
    }

    private int moveAppsToCategorySync(String targetCategory, Set<String> appsToMove) {
        int movedCount = 0;

        Map<String, Set<String>> updatedCategories = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : categories.entrySet()) {
            updatedCategories.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }

        if (!updatedCategories.containsKey(targetCategory)) {
            updatedCategories.put(targetCategory, new HashSet<>());
        }

        Set<String> targetSet = updatedCategories.get(targetCategory);

        for (String packageName : appsToMove) {
            // Remove from all existing categories
            for (Set<String> categorySet : updatedCategories.values()) {
                categorySet.remove(packageName);
            }

            // Add to target category
            targetSet.add(packageName);

            // Save to preferences
            prefs.edit()
                    .putString("cat_" + packageName, targetCategory)
                    .apply();

            // Update the app's category in the appList
            for (AppInfo app : appList) {
                if (app.packageName.equals(packageName)) {
                    app.category = targetCategory;
                    break;
                }
            }

            movedCount++;
        }

        // Save updated categories
        SharedPreferences.Editor editor = categoryPrefs.edit();
        for (Map.Entry<String, Set<String>> entry : updatedCategories.entrySet()) {
            editor.putStringSet("cat_" + entry.getKey(), entry.getValue());
        }
        editor.apply();

        loadCategories();

        return movedCount;
    }

    private int removeAppsFromCategoriesSync(Set<String> appsToRemove) {
        int removedCount = 0;

        List<String> appsList = new ArrayList<>(appsToRemove);

        for (String packageName : appsList) {
            try {
                AppInfo foundApp = null;
                for (AppInfo app : appList) {
                    if (app.packageName.equals(packageName)) {
                        foundApp = app;
                        break;
                    }
                }

                if (foundApp == null) continue;

                for (Map.Entry<String, Set<String>> entry : categories.entrySet()) {
                    if (entry.getValue().contains(foundApp.packageName)) {
                        Set<String> categorySet = new HashSet<>(entry.getValue());
                        categorySet.remove(foundApp.packageName);
                        categoryPrefs.edit().putStringSet("cat_" + entry.getKey(), categorySet).apply();

                        prefs.edit().remove("cat_" + foundApp.packageName).apply();
                        foundApp.category = "Uncategorized";
                        removedCount++;
                        break;
                    }
                }
            } catch (Exception e) {
                continue;
            }
        }

        loadCategories();

        return removedCount;
    }

    private void buildCategoryBar() {
        if (categoryBar == null) return;

        categoryBar.removeAllViews();

        int categoryCount = categories.size() + 1;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int availableWidth = screenWidth - 48;
        int maxButtonWidth = 120;
        int minButtonWidth = 80;

        float density = getResources().getDisplayMetrics().density;
        int suggestedWidth = Math.min(
                Math.max(availableWidth / Math.max(categoryCount, 3),
                        (int) (minButtonWidth * density)),
                (int) (maxButtonWidth * density)
        );

        Button allAppsBtn = new Button(this);
        allAppsBtn.setText("All Apps");
        allAppsBtn.setOnClickListener(v -> {
            runOnUiThread(() -> {
                try {
                    if (isEditMode && !selectedApps.isEmpty()) {
                        int removedCount = removeAppsFromCategoriesSync(selectedApps);
                        selectedApps.clear();
                        currentCategory = "All Apps";
                        saveCurrentCategory();
                        updateCategoryButtonStates("All Apps");

                        if (searchEditText != null) {
                            searchEditText.setText("");
                        }

                        loadCategories();
                        filterApps("");

                        if (appAdapter != null) {
                            appAdapter.notifyDataSetChanged();
                        }

                        Toast.makeText(this, "Removed " + removedCount + " app(s) from categories", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.i("MainActivity", "All Apps button clicked");
                        switchToCategoryAnimated("All Apps");
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        try {
            android.graphics.drawable.Drawable drawable = getResources().getDrawable(R.drawable.category_button_background);
            if (drawable != null) {
                allAppsBtn.setBackground(drawable);
            } else {
                allAppsBtn.setBackgroundColor(Color.parseColor("#2D2D2D"));
            }
        } catch (Exception e) {
            allAppsBtn.setBackgroundColor(Color.parseColor("#2D2D2D"));
        }

        allAppsBtn.setTextColor(Color.WHITE);
        allAppsBtn.setPadding(8, 4, 8, 4);
        allAppsBtn.setAllCaps(false);
        allAppsBtn.setTextSize(11);

        LinearLayout.LayoutParams allAppsParams = new LinearLayout.LayoutParams(
                suggestedWidth,
                (int) (28 * density)
        );
        allAppsParams.setMargins(4, 0, 4, 0);
        allAppsBtn.setLayoutParams(allAppsParams);

        allAppsBtn.setBackgroundColor(currentCategory.equals("All Apps") ?
                Color.parseColor("#6B8EFF") : Color.parseColor("#2D2D2D"));

        // VR polish: hover/press effects
        applyButtonInteractionEffects(allAppsBtn);

        categoryBar.addView(allAppsBtn);

        for (String category : categories.keySet()) {
            addCategoryButton(category, () -> switchToCategoryAnimated(category));
        }

        // Schedule the initial entry animation (only runs once per session).
        // For subsequent buildCategoryBar calls (e.g. after a category is
        // added in Settings), animate only the NEW buttons.
        if (!categoryBarEntryAnimated) {
            categoryBar.postDelayed(this::animateCategoryBarEntry, 300);
        } else {
            // Animate any category buttons we haven't seen before
            for (int i = 0; i < categoryBar.getChildCount(); i++) {
                View btn = categoryBar.getChildAt(i);
                if (btn instanceof Button) {
                    String label = ((Button) btn).getText().toString();
                    if (!animatedCategoryNames.contains(label)) {
                        animatedCategoryNames.add(label);
                        animateCategoryButtonEntry(btn, i);
                    }
                }
            }
        }
    }

    private void addCategoryButton(String name, Runnable action) {
        if (categoryBar == null) return;

        int categoryCount = categories.size() + 1;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int availableWidth = screenWidth - 48;
        int maxButtonWidth = 120;
        int minButtonWidth = 80;

        float density = getResources().getDisplayMetrics().density;
        int suggestedWidth = Math.min(
                Math.max(availableWidth / Math.max(categoryCount, 3),
                        (int) (minButtonWidth * density)),
                (int) (maxButtonWidth * density)
        );

        Button btn = new Button(this);
        btn.setText(name);
        btn.setOnClickListener(v -> {
            runOnUiThread(() -> {
                try {
                    if (isEditMode && !selectedApps.isEmpty()) {
                        int movedCount = moveAppsToCategorySync(name, selectedApps);
                        selectedApps.clear();
                        currentCategory = name;
                        saveCurrentCategory();
                        updateCategoryButtonStates(name);

                        if (searchEditText != null) {
                            searchEditText.setText("");
                        }

                        loadCategories();
                        filterApps("");

                        if (appAdapter != null) {
                            appAdapter.notifyDataSetChanged();
                        }

                        Toast.makeText(this, "Moved " + movedCount + " app(s) to " + name, Toast.LENGTH_SHORT).show();
                    } else {
                        Log.i("MainActivity", "Category button clicked: " + name);
                        // Delegate everything to the action runnable
                        // (which calls switchToCategoryAnimated)
                        updateSearchStatus("", false);
                        action.run();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        try {
            android.graphics.drawable.Drawable drawable = getResources().getDrawable(R.drawable.category_button_background);
            if (drawable != null) {
                btn.setBackground(drawable);
            } else {
                btn.setBackgroundColor(Color.parseColor("#2D2D2D"));
            }
        } catch (Exception e) {
            btn.setBackgroundColor(Color.parseColor("#2D2D2D"));
        }

        btn.setTextColor(Color.WHITE);
        btn.setPadding(8, 4, 8, 4);
        btn.setAllCaps(false);
        btn.setTextSize(11);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                suggestedWidth,
                (int) (28 * density)
        );
        params.setMargins(4, 0, 4, 0);
        btn.setLayoutParams(params);

        if (name.equals(currentCategory)) {
            btn.setBackgroundColor(Color.parseColor("#6B8EFF"));
        } else {
            btn.setBackgroundColor(Color.parseColor("#2D2D2D"));
        }

        // VR polish: hover/press effects on every category button
        applyButtonInteractionEffects(btn);

        categoryBar.addView(btn);
    }

    private void updateCategoryButtonStates(String selectedCategory) {
        if (categoryBar == null) return;

        for (int i = 0; i < categoryBar.getChildCount(); i++) {
            View child = categoryBar.getChildAt(i);
            if (child instanceof Button) {
                Button btn = (Button) child;
                if (btn.getText().toString().equals(selectedCategory)) {
                    btn.setBackgroundColor(Color.parseColor("#6B8EFF"));
                } else {
                    try {
                        android.graphics.drawable.Drawable drawable = getResources().getDrawable(R.drawable.category_button_background);
                        if (drawable != null) {
                            btn.setBackground(drawable);
                        } else {
                            btn.setBackgroundColor(Color.parseColor("#2D2D2D"));
                        }
                    } catch (Exception e) {
                        btn.setBackgroundColor(Color.parseColor("#2D2D2D"));
                    }
                }
            }
        }
    }

    private void updateSearchStatus(String statusText, boolean isSearching) {
        runOnUiThread(() -> {
            TextView categoryTitle = findViewById(R.id.categoryTitle);
            TextView searchStatus = findViewById(R.id.searchStatus);

            if (categoryTitle != null) {
                categoryTitle.setVisibility(View.GONE);
            }

            if (searchStatus != null) {
                if (isSearching && !statusText.isEmpty()) {
                    searchStatus.setText(statusText);
                    searchStatus.setVisibility(View.VISIBLE);
                    searchStatus.setTextColor(Color.parseColor("#4CAF50"));
                } else {
                    searchStatus.setVisibility(View.GONE);
                }
            }
        });
    }

    private void toggleAppSelection(AppInfo app) {
        runOnUiThread(() -> {
            try {
                if (selectedApps.contains(app.packageName)) {
                    selectedApps.remove(app.packageName);
                } else {
                    selectedApps.add(app.packageName);
                }

                if (appAdapter != null) {
                    appAdapter.notifyDataSetChanged();
                }

                if (selectedApps.size() > 0) {
                    Toast.makeText(this, selectedApps.size() + " app(s) selected", Toast.LENGTH_SHORT).show();
                }

            } catch (Exception e) {
                // Ignore selection errors
            }
        });
    }

    private void showMultiAssignCategoryDialog() {
        if (selectedApps.isEmpty()) {
            Toast.makeText(this, "No apps selected", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> categoryNames = new ArrayList<>(categories.keySet());
        if (categoryNames.isEmpty()) {
            Toast.makeText(this, "No categories available. Create categories in Settings.", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Move " + selectedApps.size() + " app(s) to category");

        String[] categoriesArray = categoryNames.toArray(new String[0]);
        builder.setItems(categoriesArray, (dialog, which) -> {
            String selectedCategory = categoriesArray[which];
            moveMultipleAppsToCategory(selectedCategory, new HashSet<>(selectedApps));
        });

        builder.setNegativeButton("Cancel", null);
        ThemedDialog.showThemed(builder.create());
    }

    private void moveMultipleAppsToCategory(String targetCategory, Set<String> appsToMove) {
        runOnUiThread(() -> {
            try {
                int movedCount = 0;
                int alreadyInCategory = 0;
                int notFoundCount = 0;

                for (String packageName : appsToMove) {
                    AppInfo foundApp = null;
                    for (AppInfo app : appList) {
                        if (app.packageName.equals(packageName)) {
                            foundApp = app;
                            break;
                        }
                    }

                    if (foundApp == null) {
                        notFoundCount++;
                        continue;
                    }

                    Set<String> currentSet = categories.get(targetCategory);
                    if (currentSet != null && currentSet.contains(foundApp.packageName)) {
                        alreadyInCategory++;
                        continue;
                    }

                    for (Map.Entry<String, Set<String>> entry : categories.entrySet()) {
                        if (entry.getValue().contains(foundApp.packageName)) {
                            Set<String> oldSet = new HashSet<>(entry.getValue());
                            oldSet.remove(foundApp.packageName);
                            categoryPrefs.edit().putStringSet("cat_" + entry.getKey(), oldSet).apply();
                            break;
                        }
                    }

                    Set<String> newSet = new HashSet<>(categories.get(targetCategory));
                    newSet.add(foundApp.packageName);
                    categoryPrefs.edit().putStringSet("cat_" + targetCategory, newSet).apply();

                    prefs.edit().putString("cat_" + foundApp.packageName, targetCategory).apply();
                    foundApp.category = targetCategory;
                    movedCount++;
                }

                loadCategories();

                String message = "Moved " + movedCount + " app(s) to " + targetCategory;
                if (alreadyInCategory > 0) {
                    message += "\n" + alreadyInCategory + " app(s) were already in that category";
                }
                if (notFoundCount > 0) {
                    message += "\n" + notFoundCount + " app(s) not found";
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();

            } catch (Exception e) {
                Toast.makeText(this, "Error moving apps: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void removeSelectedAppsFromCategories(Set<String> appsToRemove) {
        runOnUiThread(() -> {
            try {
                int removedCount = 0;

                for (String packageName : appsToRemove) {
                    AppInfo foundApp = null;
                    for (AppInfo app : appList) {
                        if (app.packageName.equals(packageName)) {
                            foundApp = app;
                            break;
                        }
                    }

                    if (foundApp == null) continue;

                    for (Map.Entry<String, Set<String>> entry : categories.entrySet()) {
                        if (entry.getValue().contains(foundApp.packageName)) {
                            Set<String> categorySet = new HashSet<>(entry.getValue());
                            categorySet.remove(foundApp.packageName);
                            categoryPrefs.edit().putStringSet("cat_" + entry.getKey(), categorySet).apply();

                            prefs.edit().remove("cat_" + foundApp.packageName).apply();
                            foundApp.category = "Uncategorized";
                            removedCount++;
                            break;
                        }
                    }
                }

                loadCategories();

                Toast.makeText(this, "Removed " + removedCount + " app(s) from categories", Toast.LENGTH_SHORT).show();

                filterApps(searchEditText != null ? searchEditText.getText().toString() : "");

            } catch (Exception e) {
                Toast.makeText(this, "Error removing apps: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void clearSelection() {
        selectedApps.clear();
        appAdapter.notifyDataSetChanged();
        Toast.makeText(this, "Selection cleared", Toast.LENGTH_SHORT).show();
    }

    private void selectAllApps() {
        selectedApps.clear();
        for (AppInfo app : filteredList) {
            selectedApps.add(app.packageName);
        }
        appAdapter.notifyDataSetChanged();
        Toast.makeText(this, "Selected all " + filteredList.size() + " app(s)", Toast.LENGTH_SHORT).show();
    }

    private int getCategoryColor(String category) {
        switch (category.toLowerCase()) {
            case "games":
                return Color.parseColor("#FF6B8E");
            case "media":
                return Color.parseColor("#4CAF50");
            case "tools":
                return Color.parseColor("#2196F3");
            case "social":
                return Color.parseColor("#FF9800");
            default:
                return Color.parseColor("#9C27B0");
        }
    }

    private class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

        private List<AppInfo> apps;
        private SharedPreferences prefs;

        public AppAdapter(List<AppInfo> apps) {
            this.apps = apps;
            this.prefs = MainActivity.this.getSharedPreferences("VRLPrefs", MODE_PRIVATE);
        }


        @Override
        public long getItemId(int position) {
            return apps.get(position).packageName.hashCode();
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app, parent, false);

            ViewHolder holder = new ViewHolder(view);
            // Apply hover/focus/press animations to the card for VR polish
            applyCardInteractionEffects(holder.cardView);
            return holder;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            AppInfo app = apps.get(position);

            // Apply current theme to this card (handles recycled views)
            com.jarjarblinkz.EvolveLauncher.theme.Theme theme =
                    com.jarjarblinkz.EvolveLauncher.theme.ThemeManager.getInstance(MainActivity.this).getCurrentTheme();
            holder.cardView.setCardBackgroundColor(theme.bgSecondary);
            holder.appName.setTextColor(theme.textPrimary);

            // Apply custom label if user renamed this app
            CustomLabelManager labelManager = CustomLabelManager.getInstance(MainActivity.this);
            String displayLabel = labelManager.getDisplayLabel(app.packageName,
                    app.label != null ? app.label : app.packageName);
            holder.appName.setText(displayLabel);
            holder.appVersion.setVisibility(View.GONE); // Hide the version badge completely

            // FIXED: Use safe boolean helper for show_categories
            boolean showCategories = getBooleanPreference("show_categories", true);

            if (showCategories && app.category != null && !app.category.equals("Uncategorized")) {
                String badgeText = app.category.substring(0, 1).toUpperCase();
                holder.categoryBadge.setText(badgeText);
                holder.categoryBadge.setVisibility(View.VISIBLE);

                // Force badge to be on top
                holder.categoryBadge.bringToFront();
                holder.categoryBadge.invalidate();
                holder.categoryBadge.requestLayout();

                int color = getCategoryColor(app.category.toLowerCase());
                holder.categoryBadge.setBackgroundColor(color);
                holder.categoryBadge.setTextColor(Color.WHITE);
            } else {
                holder.categoryBadge.setVisibility(View.GONE);
            }

            loadAppIcon(holder, app);

            if (isEditMode && selectedApps.contains(app.packageName)) {
                holder.cardView.setStrokeWidth(4);
                holder.cardView.setStrokeColor(theme.accentPrimary);
            } else {
                holder.cardView.setStrokeWidth(1);
                holder.cardView.setStrokeColor(theme.borderPrimary);
            }

            if (isEditMode) {
                holder.cardView.setOnClickListener(v -> toggleAppSelection(app));
                holder.cardView.setOnLongClickListener(v -> {
                    showEditOptions(app);
                    return true;
                });
            } else {
                // Delay the launch so the pop-out animation is visible first.
                // Without this, the activity transition starts immediately and
                // cuts off the pop animation before the user sees it.
                holder.cardView.setOnClickListener(v -> v.postDelayed(() -> launchApp(app), 240));
                holder.cardView.setOnLongClickListener(v -> {
                    showVROptions(app);
                    return true;
                });
            }

            // FAVORITE STAR - tap to toggle favorite state
            if (holder.btnStarFavorite != null) {
                FavoritesManager favManager = FavoritesManager.getInstance(MainActivity.this);
                boolean isFav = favManager.isFavorite(app.packageName);

                holder.btnStarFavorite.setImageResource(
                        isFav ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off
                );
                holder.btnStarFavorite.setColorFilter(
                        isFav ? 0xFFFFD700 : 0xFF888888
                );

                holder.btnStarFavorite.setOnClickListener(v -> {
                    boolean nowFav = favManager.toggleFavorite(app.packageName);
                    holder.btnStarFavorite.setImageResource(
                            nowFav ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off
                    );
                    holder.btnStarFavorite.setColorFilter(nowFav ? 0xFFFFD700 : 0xFF888888);
                    Toast.makeText(MainActivity.this,
                            nowFav ? "⭐ Added to favorites" : "Removed from favorites",
                            Toast.LENGTH_SHORT).show();

                    // Refresh main grid - favorited apps should disappear from current view
                    String currentQuery = searchEditText != null ? searchEditText.getText().toString() : "";
                    filterApps(currentQuery);
                });
            }
        }

        private int getCategoryColor(String category) {
            switch (category) {
                case "games":
                    return Color.parseColor("#FF6B8E");
                case "media":
                    return Color.parseColor("#4CAF50");
                case "tools":
                    return Color.parseColor("#2196F3");
                case "social":
                    return Color.parseColor("#FF9800");
                default:
                    return Color.parseColor("#9C27B0");
            }
        }

        /**
         * RESTORED: loadAppIcon now uses the restored icon scale system
         * Icon width = selected scale from ICON_SCALES_DP (82, 99, 125, 165, 236 dp)
         * Icon height = width * 0.5625 (always 16:9 landscape)
         */
        private void loadAppIcon(ViewHolder holder, AppInfo app) {
            float density = holder.itemView.getResources().getDisplayMetrics().density;

            // Use the restored icon scale system (old launcher logic)
            int scaleIndex = prefs.getInt("icon_size_scale", DEFAULT_SCALE_INDEX);
            int iconSizeDp = ICON_SCALES_DP[scaleIndex];
            int iconWidthPx = (int) (iconSizeDp * density);
            int iconHeightPx = (int) (iconWidthPx * 0.5625f);  // 16:9 LANDSCAPE — always

            ViewGroup.LayoutParams params = holder.appIcon.getLayoutParams();
            params.width = iconWidthPx;
            params.height = iconHeightPx;
            holder.appIcon.setLayoutParams(params);
            holder.appIcon.requestLayout();

            if (ENABLE_IMAGE_CACHING && iconCache.containsKey(app.packageName)) {
                holder.appIcon.setImageDrawable(iconCache.get(app.packageName));
                return;
            }

            Glide.with(MainActivity.this)
                    .load(app.githubIconUrl)
                    .apply(new RequestOptions()
                            .placeholder(app.icon)
                            .error(app.icon)
                            .centerCrop()
                            .override(iconWidthPx, iconHeightPx)
                            .skipMemoryCache(false)
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .dontAnimate())
                    .into(holder.appIcon);
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView cardView;
            ImageView appIcon;
            TextView appName;
            TextView categoryBadge;
            TextView appVersion;
            ImageView btnStarFavorite;

            public ViewHolder(View itemView) {
                super(itemView);
                cardView = itemView.findViewById(R.id.cardApp);
                appIcon = itemView.findViewById(R.id.appIcon);
                appName = itemView.findViewById(R.id.appName);
                categoryBadge = itemView.findViewById(R.id.categoryBadge);
                appVersion = itemView.findViewById(R.id.appVersion);
                btnStarFavorite = itemView.findViewById(R.id.btnStarFavorite);
            }
        }
    }

    private void showVROptions(AppInfo app) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(app.label);

        // Build detailed info string
        String appInfo = "📱 " + app.label + "\n" +
                "────────────────\n" +
                "📦 Version: " + app.versionName + "\n" +
                "📅 Installed: " + app.firstInstallDate + "\n" +
                "🔄 Updated: " + app.lastUpdateDate + "\n" +
                "🏷️ Type: " + (app.isStoreApp ? "Store" : (app.isSideloaded ? "Sideloaded" : "System")) + "\n" +
                "────────────────\n\n" +
                "⏱️ Playtime:\n" +
                "  Today: " + PlaytimeTracker.formatPlaytime(app.playtimeToday) + "\n" +
                "  This Week: " + PlaytimeTracker.formatPlaytime(app.playtimeWeek) + "\n" +
                "  This Month: " + PlaytimeTracker.formatPlaytime(app.playtimeMonth) + "\n" +
                "  All Time: " + PlaytimeTracker.formatPlaytime(app.playtimeAllTime) + "\n" +
                (app.isCurrentlyRunning ? "\n▶️ Currently Running" : "");

        boolean isInCategory = false;
        String currentAppCategory = null;
        for (Map.Entry<String, Set<String>> entry : categories.entrySet()) {
            if (entry.getValue().contains(app.packageName)) {
                isInCategory = true;
                currentAppCategory = entry.getKey();
                break;
            }
        }

        final boolean finalIsInCategory = isInCategory;
        final String finalCategory = currentAppCategory;

        List<String> optionsList = new ArrayList<>();
        optionsList.add("Launch");
        optionsList.add("📊 Playtime Stats");
        optionsList.add("✏️ Rename");

        if (isInCategory) {
            optionsList.add("Remove from " + currentAppCategory);
        } else {
            optionsList.add("Add to Category");
        }

        optionsList.add("App Info");

        if (!app.isSystemApp) {
            optionsList.add("Uninstall");
        }

        String[] options = optionsList.toArray(new String[0]);

        builder.setItems(options, (dialog, which) -> {
            String selectedOption = options[which];

            if (selectedOption.equals("Launch")) {
                launchApp(app);
            } else if (selectedOption.equals("📊 Playtime Stats")) {
                showPlaytimeDetails(app);
            } else if (selectedOption.equals("✏️ Rename")) {
                showRenameDialog(app);
            } else if (selectedOption.startsWith("Remove from ")) {
                removeFromCategory(app, finalCategory);
            } else if (selectedOption.equals("Add to Category")) {
                showAssignCategoryDialog(app);
            } else if (selectedOption.equals("App Info")) {
                // Show detailed app info with install/update dates
                ThemedDialog.showThemed(new AlertDialog.Builder(this)
                        .setTitle("App Details")
                        .setMessage(appInfo)
                        .setPositiveButton("Settings", (d, w) -> {
                            try {
                                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(android.net.Uri.parse("package:" + app.packageName));
                                startActivity(intent);
                            } catch (Exception e) {
                                Toast.makeText(this, "Cannot open app settings", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Close", null)
                        .create());
            } else if (selectedOption.equals("Uninstall")) {
                uninstallApp(app);
            }
        });
        ThemedDialog.showThemed(builder.create());
    }

    /**
     * Show a dialog to rename an app's display label.
     * Empty input resets to original name.
     */
    private void showRenameDialog(AppInfo app) {
        CustomLabelManager labelManager = CustomLabelManager.getInstance(this);
        String currentDisplayLabel = labelManager.getDisplayLabel(app.packageName, app.label);
        boolean hasCustom = labelManager.hasCustomLabel(app.packageName);

        // Build the input view
        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, padding);

        android.widget.TextView instructions = new android.widget.TextView(this);
        instructions.setText(hasCustom
                ? "Current name: " + currentDisplayLabel + "\nOriginal: " + app.label + "\n\nEnter new name (leave blank to reset):"
                : "Original: " + app.label + "\n\nEnter new name:");
        instructions.setTextSize(12);
        instructions.setPadding(0, 0, 0, padding);
        container.addView(instructions);

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(app.label);
        input.setText(hasCustom ? currentDisplayLabel : "");
        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);
        container.addView(input);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("✏️ Rename App")
                .setView(container)
                .setPositiveButton("Save", (d, w) -> {
                    String newName = input.getText().toString().trim();
                    labelManager.setCustomLabel(app.packageName, newName);

                    String message;
                    if (newName.isEmpty()) {
                        message = "Reset to original: " + app.label;
                    } else {
                        message = "Renamed to: " + newName;
                    }
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

                    // Refresh the adapter to show new label
                    if (appAdapter != null) {
                        appAdapter.notifyDataSetChanged();
                    }
                })
                .setNegativeButton("Cancel", null);

        // Add Reset button if there's a custom label
        if (hasCustom) {
            builder.setNeutralButton("Reset to Original", (d, w) -> {
                labelManager.setCustomLabel(app.packageName, null);
                Toast.makeText(this, "Reset to: " + app.label, Toast.LENGTH_SHORT).show();
                if (appAdapter != null) {
                    appAdapter.notifyDataSetChanged();
                }
            });
        }

        ThemedDialog.showThemed(builder.create());
    }

    private void showEditOptions(AppInfo app) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit: " + app.label);

        boolean isInCategory = false;
        String currentAppCategory = null;
        for (Map.Entry<String, Set<String>> entry : categories.entrySet()) {
            if (entry.getValue().contains(app.packageName)) {
                isInCategory = true;
                currentAppCategory = entry.getKey();
                break;
            }
        }

        List<String> optionsList = new ArrayList<>();

        if (selectedApps.size() > 0) {
            optionsList.add("Move " + selectedApps.size() + " Selected to Category");
            optionsList.add("Clear Selection (" + selectedApps.size() + " selected)");
        } else {
            optionsList.add("Select All Apps");
        }

        if (isInCategory) {
            optionsList.add("Remove from " + currentAppCategory);
        } else {
            optionsList.add("Add to Category");
        }

        optionsList.add("✏️ Rename");
        optionsList.add("Hide App");
        optionsList.add("Unhide All Apps");
        optionsList.add("App Info");

        String[] options = optionsList.toArray(new String[0]);

        final String finalCategory = currentAppCategory;

        builder.setItems(options, (dialog, which) -> {
            String selectedOption = options[which];

            if (selectedOption.startsWith("Move ")) {
                showMultiAssignCategoryDialog();
            } else if (selectedOption.startsWith("Clear Selection")) {
                clearSelection();
            } else if (selectedOption.equals("Select All Apps")) {
                selectAllApps();
            } else if (selectedOption.startsWith("Remove from ")) {
                removeFromCategory(app, finalCategory);
            } else if (selectedOption.equals("Add to Category")) {
                showAssignCategoryDialog(app);
            } else if (selectedOption.equals("✏️ Rename")) {
                showRenameDialog(app);
            } else if (selectedOption.equals("Hide App")) {
                hideApp(app);
            } else if (selectedOption.equals("Unhide All Apps")) {
                unhideAllApps();
            } else if (selectedOption.equals("App Info")) {
                showAppInfo(app);
            }
        });
        ThemedDialog.showThemed(builder.create());
    }

    private void showAssignCategoryDialog(AppInfo app) {
        List<String> categoryNames = new ArrayList<>(categories.keySet());
        if (categoryNames.isEmpty()) {
            Toast.makeText(this, "No categories available. Create categories in Settings.", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add " + app.label + " to category");

        String[] categoriesArray = categoryNames.toArray(new String[0]);
        builder.setItems(categoriesArray, (dialog, which) -> {
            String selectedCategory = categoriesArray[which];

            Set<String> currentSet = categories.get(selectedCategory);
            if (currentSet != null && currentSet.contains(app.packageName)) {
                Toast.makeText(this, app.label + " is already in " + selectedCategory, Toast.LENGTH_SHORT).show();
                return;
            }

            String foundOldCategory = null;
            for (Map.Entry<String, Set<String>> entry : categories.entrySet()) {
                if (entry.getValue().contains(app.packageName)) {
                    foundOldCategory = entry.getKey();
                    break;
                }
            }

            final String finalCategory = selectedCategory;
            final String finalOldCategory = foundOldCategory;

            if (foundOldCategory != null) {
                AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(this);
                confirmBuilder.setTitle("Move App")
                        .setMessage(app.label + " is already in " + foundOldCategory + ".\n\nMove to " + selectedCategory + "?")
                        .setPositiveButton("Move", (d, w) -> {
                            moveAppToCategory(app, finalOldCategory, finalCategory);
                        })
                        .setNegativeButton("Cancel", null);
                ThemedDialog.showThemed(confirmBuilder.create());
            } else {
                AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(this);
                confirmBuilder.setTitle("Add to Category")
                        .setMessage("Add '" + app.label + "' to " + selectedCategory + "?")
                        .setPositiveButton("Add", (d, w) -> {
                            addAppToCategory(app, finalCategory);
                        })
                        .setNegativeButton("Cancel", null);
                ThemedDialog.showThemed(confirmBuilder.create());
            }
        });

        builder.setNegativeButton("Cancel", null);
        ThemedDialog.showThemed(builder.create());
    }

    private void moveAppToCategory(AppInfo app, String oldCategory, String newCategory) {
        Set<String> oldSet = new HashSet<>(categories.get(oldCategory));
        oldSet.remove(app.packageName);
        categoryPrefs.edit().putStringSet("cat_" + oldCategory, oldSet).apply();

        Set<String> newSet = new HashSet<>(categories.get(newCategory));
        newSet.add(app.packageName);
        categoryPrefs.edit().putStringSet("cat_" + newCategory, newSet).apply();

        prefs.edit().putString("cat_" + app.packageName, newCategory).apply();
        app.category = newCategory;

        loadCategories();

        // If we're currently viewing the old category, stay in it
        // If we're viewing the new category, stay in it
        // No need to change currentCategory

        filterApps(searchEditText != null ? searchEditText.getText().toString() : "");

        Toast.makeText(this, app.label + " moved to " + newCategory, Toast.LENGTH_SHORT).show();
    }

    private void addAppToCategory(AppInfo app, String category) {
        Set<String> set = new HashSet<>(categories.get(category));
        set.add(app.packageName);
        categoryPrefs.edit().putStringSet("cat_" + category, set).apply();

        prefs.edit().putString("cat_" + app.packageName, category).apply();
        app.category = category;

        loadCategories();

        if (currentCategory.equals("All Apps")) {
            filterApps(searchEditText.getText().toString());
        } else if (currentCategory.equals(category)) {
            filterApps(searchEditText.getText().toString());
        }

        Toast.makeText(this, app.label + " added to " + category, Toast.LENGTH_SHORT).show();
    }

    private void removeFromCategory(AppInfo app, String category) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Remove from Category")
                .setMessage("Remove '" + app.label + "' from " + category + "?\n\nIt will reappear in 'All Apps'.")
                .setPositiveButton("Remove", (dialog, which) -> {
                    Set<String> set = new HashSet<>(categories.get(category));
                    set.remove(app.packageName);
                    categoryPrefs.edit().putStringSet("cat_" + category, set).apply();

                    prefs.edit().remove("cat_" + app.packageName).apply();
                    app.category = "Uncategorized";

                    // Stay in current category - don't force reset to All Apps
                    loadCategories();
                    filterApps(searchEditText != null ? searchEditText.getText().toString() : "");

                    // If we're viewing the category we just removed from, refresh the view
                    if (currentCategory.equals(category)) {
                        filterApps(searchEditText != null ? searchEditText.getText().toString() : "");
                    }

                    Toast.makeText(this, app.label + " removed from " + category, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void hideApp(AppInfo app) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Hide App")
                .setMessage("Are you sure you want to hide " + app.label + "?")
                .setPositiveButton("Hide", (dialog, which) -> {
                    prefs.edit().putBoolean("hidden_" + app.packageName, true).apply();
                    appList.remove(app);
                    filterApps(searchEditText.getText().toString());
                    Toast.makeText(this, app.label + " hidden", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void unhideAllApps() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Unhide All Apps")
                .setMessage("Restore all hidden apps?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    for (String key : prefs.getAll().keySet()) {
                        if (key.startsWith("hidden_")) {
                            editor.remove(key);
                        }
                    }
                    editor.apply();

                    loadUserApps();
                    filterApps(searchEditText.getText().toString());
                    Toast.makeText(this, "All apps restored", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAppInfo(AppInfo app) {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + app.packageName));
            startActivity(intent);
        } catch (Exception e) {
            // Ignore app info errors
        }
    }

    private void uninstallApp(AppInfo app) {
        String packageToUninstall = app.packageName;

        appList.remove(app);
        filteredList.remove(app);
        appAdapter.notifyDataSetChanged();

        try {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(android.net.Uri.parse("package:" + packageToUninstall));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(android.net.Uri.parse("package:" + packageToUninstall));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);

                Toast.makeText(this, "Open App Info and tap 'Uninstall'", Toast.LENGTH_LONG).show();
            } catch (Exception e2) {
                Toast.makeText(this, "Cannot uninstall: " + app.label, Toast.LENGTH_SHORT).show();
                loadUserApps();
                filterApps(searchEditText.getText().toString());
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (isQuickSettingsVisible) {
            hideQuickSettings();
            return;
        }

        if (isEditMode && !selectedApps.isEmpty()) {
            clearSelection();
            return;
        }

        if (!searchEditText.getText().toString().isEmpty()) {
            searchEditText.setText("");
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Exit VR Launcher")
                    .setMessage("Return to Quest Home?")
                    .setPositiveButton("Exit", (dialog, which) -> finish())
                    .setNegativeButton("Stay", null)
                    .show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Re-apply theme to view hierarchy in case user changed it in Settings
        View themeRootView = findViewById(android.R.id.content);
        if (themeRootView != null) {
            ThemeApplier.applyThemeToHierarchy(themeRootView);
        }

        // Update floating favorites button color to match current theme
        refreshFloatingFavoritesColor();

        // Update the layout background (this respects user's background settings + theme image)
        updateBackground();

        // Restore last selected category
        String savedCategory = prefs.getString(KEY_LAST_CATEGORY, "All Apps");
        if (!savedCategory.equals(currentCategory)) {
            currentCategory = savedCategory;
            if (!currentCategory.equals("All Apps") && !categories.containsKey(currentCategory)) {
                currentCategory = "All Apps";
                saveCurrentCategory();
            }
            updateCategoryButtonStates(currentCategory);
            filterApps(searchEditText != null ? searchEditText.getText().toString() : "");
        }

        refreshAll(); // One-time refresh

        // CRITICAL: Restart periodic status updates
        startStatusUpdates();

        // One-time updates (harmless redundancy)
        updateTime();
        updateIPAddress();
        updateWifiSignal();
        updateBatteryLevel(null);

        // Check permission but don't show dialog if already granted
        if (!prefs.getBoolean(KEY_PERMISSION_GRANTED, false)) {
            // Only check if we haven't already verified
            if (playtimeTracker.hasPermission()) {
                prefs.edit().putBoolean(KEY_PERMISSION_GRANTED, true).apply();
                startPlaytimeUpdates();
            }
        }

        // --- DYNAMIC REGISTRATION OF SYSTEM PACKAGE BROADCASTS ---
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package"); // Important for package broadcasts

        registerReceiver(appChangeListener, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Stop status updates
        if (statusHandler != null && statusUpdateRunnable != null) {
            statusHandler.removeCallbacks(statusUpdateRunnable);
        }

        // Unregister the app change listener
        try {
            unregisterReceiver(appChangeListener);
        } catch (IllegalArgumentException e) {
            // Receiver not registered, safe to ignore
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (statusHandler != null && statusUpdateRunnable != null) {
            statusHandler.removeCallbacks(statusUpdateRunnable);
        }

        // Unregister theme change receiver
        try {
            unregisterReceiver(themeChangeReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver not registered, ignore
        }

        // Unregister category change receiver
        try {
            unregisterReceiver(categoryChangeReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver not registered, ignore
        }

        try {
            unregisterReceiver(appChangeListener);
        } catch (IllegalArgumentException e) {
            // Receiver was not registered, ignore
        }

        try {
            unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver was not registered, ignore
        }

        instance = null;

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }

        if (iconCache != null) {
            iconCache.clear();
        }
    }
}