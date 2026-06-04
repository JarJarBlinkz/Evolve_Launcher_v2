package com.jarjarblinkz.EvolveLauncher;

import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.storage.StorageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jarjarblinkz.EvolveLauncher.theme.Theme;
import com.jarjarblinkz.EvolveLauncher.theme.ThemeApplier;
import com.jarjarblinkz.EvolveLauncher.theme.ThemeManager;
import com.jarjarblinkz.EvolveLauncher.theme.ThemedDialog;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Storage Manager - Android-style storage manager for user-installed apps.
 * Uses Shizuku for clear data/cache operations.
 */
public class AppManagerActivity extends AppCompatActivity implements ShizukuManager.ShizukuStatusListener {

    public enum SortMode {
        NAME, TOTAL_SIZE, CACHE_SIZE, DATA_SIZE
    }

    // Same system package blocklist as MainActivity launcher
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

    private AppManagerAdapter adapter;
    private final List<ManagedApp> sideloadedApps = new ArrayList<>();
    private SortMode currentSort = SortMode.TOTAL_SIZE;
    private TextView txtHeaderStats;
    private TextView txtSortMode;
    private ProgressBar progressLoading;

    // Grid layout for dynamic column count
    private RecyclerView recyclerView;
    private GridLayoutManager gridLayoutManager;

    // Shizuku status banner
    private LinearLayout shizukuBanner;
    private TextView txtShizukuStatus;
    private Button btnShizukuAction;

    // Shizuku
    private ShizukuManager shizukuManager;

    // Tracking pending operation for result callback
    private String pendingSuccessMsg;
    private String pendingErrorMsg;
    private Runnable pendingOnComplete;

    public static class ManagedApp {
        public String packageName;
        public String label;
        public String versionName;
        public Drawable icon;
        public long appSize;
        public long dataSize;
        public long cacheSize;
        public long totalSize;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide the activity title bar that shows "VR Launcher"
        try {
            supportRequestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        } catch (Exception e) {
            // Fall back to hiding action bar
            if (getSupportActionBar() != null) {
                getSupportActionBar().hide();
            }
        }

        setContentView(R.layout.activity_app_manager);

        // Make this dialog activity much larger - nearly full screen
        try {
            android.view.Window window = getWindow();
            if (window != null) {
                android.view.WindowManager.LayoutParams params = window.getAttributes();
                params.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
                params.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
                window.setAttributes(params);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        txtHeaderStats = findViewById(R.id.txtHeaderStats);
        txtSortMode = findViewById(R.id.txtSortMode);
        progressLoading = findViewById(R.id.progressLoading);

        // Shizuku banner
        shizukuBanner = findViewById(R.id.shizukuBanner);
        txtShizukuStatus = findViewById(R.id.txtShizukuStatus);
        btnShizukuAction = findViewById(R.id.btnShizukuAction);

        // Back button
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // Sort button
        Button btnSort = findViewById(R.id.btnSort);
        if (btnSort != null) {
            btnSort.setOnClickListener(v -> showSortDialog());
        }

        // RecyclerView with adaptive column count - 4 minimum, more as window widens
        recyclerView = findViewById(R.id.appsList);
        gridLayoutManager = new GridLayoutManager(this, calculateColumnCount(0));
        recyclerView.setLayoutManager(gridLayoutManager);

        // Watch for window resize - adjust columns dynamically
        recyclerView.getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (recyclerView == null || gridLayoutManager == null) return;
                        int width = recyclerView.getWidth();
                        int newColumns = calculateColumnCount(width);
                        if (gridLayoutManager.getSpanCount() != newColumns) {
                            gridLayoutManager.setSpanCount(newColumns);
                            if (adapter != null) {
                                adapter.notifyDataSetChanged();
                            }
                        }
                    }
                });

        adapter = new AppManagerAdapter(sideloadedApps);
        recyclerView.setAdapter(adapter);

        // Initialize Shizuku
        shizukuManager = new ShizukuManager(this);
        shizukuManager.initialize(this);

        loadSideloadedApps();

        // Apply theme
        View rootView = findViewById(android.R.id.content);
        ThemeApplier.applyThemeToHierarchy(rootView);

        // Update Shizuku banner
        updateShizukuBanner();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSideloadedApps();
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            ThemeApplier.applyThemeToHierarchy(rootView);
        }
        // Re-check Shizuku status
        if (shizukuManager != null) {
            shizukuManager.recheckStatus();
        }
        // Update banner (may have changed if user installed Shizuku)
        updateShizukuBanner();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (shizukuManager != null) {
            shizukuManager.cleanup();
        }
    }

    // ====================
    // ShizukuStatusListener
    // ====================

    @Override
    public void onStatusChanged(boolean available, boolean hasPermission) {
        runOnUiThread(this::updateShizukuBanner);
    }

    @Override
    public void onCommandResult(boolean success, String output) {
        runOnUiThread(() -> {
            // Log the actual output for debugging
            android.util.Log.i("AppManager", "Command result - success: " + success);
            android.util.Log.i("AppManager", "Output: " + output);

            String message;
            if (success) {
                message = pendingSuccessMsg;
            } else {
                message = (pendingErrorMsg != null ? pendingErrorMsg : "Operation failed");
            }

            // Show short toast
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

            // If output has meaningful content (errors, etc), show a dialog with details
            if (output != null && !output.trim().isEmpty() && output.length() > 10) {
                // Only show dialog if there's an error indicator OR for debugging
                if (output.contains("Failed") || output.contains("Error") ||
                        output.contains("denied") || output.contains("Permission") ||
                        output.contains("Unknown")) {
                    ThemedDialog.showThemed(
                            new AlertDialog.Builder(this)
                                    .setTitle("Command Output")
                                    .setMessage(output)
                                    .setPositiveButton("OK", null)
                                    .create()
                    );
                }
            }

            Runnable callback = pendingOnComplete;
            pendingSuccessMsg = null;
            pendingErrorMsg = null;
            pendingOnComplete = null;

            if (callback != null) {
                callback.run();
            }
        });
    }

    /**
     * Open Shizuku app for the user to grant permission manually.
     * This is more reliable than Shizuku.requestPermission() which can
     * silently fail if user previously denied with "Don't ask again".
     */
    private void openShizukuForPermission(ShizukuInstaller installer) {
        // Still try to trigger the permission request in case it works
        if (shizukuManager != null) {
            shizukuManager.requestPermission();
        }

        // Always open Shizuku app so user can grant manually
        if (!installer.launchShizukuApp()) {
            Toast.makeText(this, "Could not open Shizuku app", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Find Evolve in Shizuku and toggle authorization ON", Toast.LENGTH_LONG).show();
    }

    /**
     * Calculate column count based on current window width.
     * Aims for ~280dp per card minimum to keep them readable.
     * Returns 4 minimum, more as window widens.
     */
    private int calculateColumnCount(int widthPixels) {
        if (widthPixels <= 0) {
            // Not yet measured - use display metrics as initial guess
            widthPixels = getResources().getDisplayMetrics().widthPixels;
        }
        float density = getResources().getDisplayMetrics().density;
        float dpWidth = widthPixels / density;
        // Aim for ~280dp per card
        int columns = (int) (dpWidth / 280);
        return Math.max(4, columns); // minimum 4 columns
    }

    /**
     * Update the Shizuku status banner based on current state
     */
    private void updateShizukuBanner() {        if (shizukuBanner == null) return;

        ShizukuInstaller installer = new ShizukuInstaller(this);
        ShizukuInstaller.InstallStatus status = installer.getStatus();

        if (status == ShizukuInstaller.InstallStatus.NOT_INSTALLED) {
            // Not installed - Red, install via bundled APK
            shizukuBanner.setVisibility(View.VISIBLE);
            shizukuBanner.setBackgroundColor(android.graphics.Color.parseColor("#3D1A1A"));
            txtShizukuStatus.setText("⚠️ Shizuku not installed - required for clearing data/cache");
            btnShizukuAction.setText("Install");
            btnShizukuAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#F44336")));
            btnShizukuAction.setOnClickListener(v -> {
                boolean started = installer.installShizuku();
                if (started) {
                    Toast.makeText(this, "Follow the installer prompts...", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Failed to start installer", Toast.LENGTH_LONG).show();
                }
            });
        } else if (status == ShizukuInstaller.InstallStatus.INSTALLED_NOT_RUNNING) {
            // Installed but not running - Orange, open Shizuku app
            shizukuBanner.setVisibility(View.VISIBLE);
            shizukuBanner.setBackgroundColor(android.graphics.Color.parseColor("#3D2D1A"));
            txtShizukuStatus.setText("⚠️ Shizuku not running - tap Open to start");
            btnShizukuAction.setText("Open");
            btnShizukuAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#FF9800")));
            btnShizukuAction.setOnClickListener(v -> {
                if (!installer.launchShizukuApp()) {
                    Toast.makeText(this, "Could not open Shizuku app", Toast.LENGTH_SHORT).show();
                }
            });
        } else if (status == ShizukuInstaller.InstallStatus.RUNNING &&
                shizukuManager != null && !shizukuManager.isReady()) {
            // Running but no permission - Yellow, open Shizuku to grant
            shizukuBanner.setVisibility(View.VISIBLE);
            shizukuBanner.setBackgroundColor(android.graphics.Color.parseColor("#3D3D1A"));
            txtShizukuStatus.setText("⚠️ Open Shizuku and authorize Evolve");
            btnShizukuAction.setText("Open");
            btnShizukuAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#FFC107")));
            btnShizukuAction.setOnClickListener(v -> openShizukuForPermission(installer));
        } else {
            // Ready - hide banner
            shizukuBanner.setVisibility(View.GONE);
        }

        // Tag banner button to ignore theme system (keep status colors)
        if (btnShizukuAction != null) {
            btnShizukuAction.setTag("theme_ignore");
        }
    }

    /**
     * Check if Shizuku is ready before running a command
     */
    private boolean requireShizuku() {
        ShizukuInstaller installer = new ShizukuInstaller(this);
        ShizukuInstaller.InstallStatus status = installer.getStatus();

        if (status == ShizukuInstaller.InstallStatus.NOT_INSTALLED) {
            ThemedDialog.showThemed(
                    new AlertDialog.Builder(this)
                            .setTitle("📦 Install Shizuku")
                            .setMessage("Shizuku is required to clear app data and cache.\n\n" +
                                    "✅ Shizuku APK is bundled with Evolve\n" +
                                    "✅ One-tap install - no downloads needed\n" +
                                    "✅ Free and open source\n\n" +
                                    "Would you like to install it now?")
                            .setPositiveButton("Install", (d, w) -> {
                                boolean started = installer.installShizuku();
                                if (started) {
                                    Toast.makeText(this, "Follow the installer prompts...", Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(this, "Failed to start installer", Toast.LENGTH_LONG).show();
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .create()
            );
            return false;
        }

        if (status == ShizukuInstaller.InstallStatus.INSTALLED_NOT_RUNNING) {
            ThemedDialog.showThemed(
                    new AlertDialog.Builder(this)
                            .setTitle("Shizuku Not Running")
                            .setMessage("Shizuku is installed but the server isn't running.\n\n" +
                                    "Open Shizuku and start the server.")
                            .setPositiveButton("Open Shizuku", (d, w) -> {
                                if (!installer.launchShizukuApp()) {
                                    Toast.makeText(this, "Could not open Shizuku app", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .create()
            );
            return false;
        }

        if (shizukuManager == null || !shizukuManager.isReady()) {
            ThemedDialog.showThemed(
                    new AlertDialog.Builder(this)
                            .setTitle("Permission Needed")
                            .setMessage("Shizuku needs permission to manage other apps.\n\n" +
                                    "Tap Open Shizuku, then find Evolve in the list and toggle authorization ON.")
                            .setPositiveButton("Open Shizuku", (d, w) -> openShizukuForPermission(installer))
                            .setNegativeButton("Cancel", null)
                            .create()
            );
            return false;
        }
        return true;
    }

    // ====================
    // App loading & filtering
    // ====================

    private boolean isInSystemPackageList(String packageName) {
        for (String systemPackage : SYSTEM_PACKAGES) {
            if (packageName.startsWith(systemPackage)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Load apps using SAME filter as the launcher (user-installed only)
     */
    private void loadSideloadedApps() {
        progressLoading.setVisibility(View.VISIBLE);
        sideloadedApps.clear();

        PackageManager pm = getPackageManager();
        StorageStatsManager statsManager = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            statsManager = (StorageStatsManager) getSystemService(Context.STORAGE_STATS_SERVICE);
        }

        List<ApplicationInfo> allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo info : allApps) {
            try {
                String packageName = info.packageName;

                // Same filter as MainActivity launcher
                if (isInSystemPackageList(packageName)) continue;
                if (packageName.equals(getPackageName())) continue;

                // Must be launchable
                Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
                if (launchIntent == null) {
                    Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
                    launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                    launcherIntent.setPackage(packageName);
                    if (pm.queryIntentActivities(launcherIntent, 0).isEmpty()) {
                        continue;
                    }
                }

                ManagedApp app = new ManagedApp();
                app.packageName = packageName;
                app.label = pm.getApplicationLabel(info).toString();
                try {
                    app.versionName = pm.getPackageInfo(packageName, 0).versionName;
                } catch (Exception e) {
                    app.versionName = "?";
                }
                try {
                    app.icon = pm.getApplicationIcon(info);
                } catch (Exception ignored) {}

                // Get storage stats
                if (statsManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        StorageStats stats = statsManager.queryStatsForPackage(
                                StorageManager.UUID_DEFAULT,
                                packageName,
                                Process.myUserHandle()
                        );
                        app.appSize = stats.getAppBytes();
                        app.dataSize = stats.getDataBytes() - stats.getCacheBytes();
                        app.cacheSize = stats.getCacheBytes();
                        app.totalSize = app.appSize + app.dataSize + app.cacheSize;
                    } catch (Exception e) {
                        app.appSize = 0;
                        app.dataSize = 0;
                        app.cacheSize = 0;
                        app.totalSize = 0;
                    }
                }

                sideloadedApps.add(app);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        sortApps();
        updateHeaderStats();

        adapter.notifyDataSetChanged();
        progressLoading.setVisibility(View.GONE);

        // Empty state
        View emptyState = findViewById(R.id.emptyState);
        RecyclerView list = findViewById(R.id.appsList);
        if (emptyState != null && list != null) {
            if (sideloadedApps.isEmpty()) {
                emptyState.setVisibility(View.VISIBLE);
                list.setVisibility(View.GONE);
            } else {
                emptyState.setVisibility(View.GONE);
                list.setVisibility(View.VISIBLE);
            }
        }
    }

    // ====================
    // Sorting
    // ====================

    private void sortApps() {
        final Collator collator = Collator.getInstance();
        switch (currentSort) {
            case NAME:
                Collections.sort(sideloadedApps, (a, b) -> collator.compare(a.label, b.label));
                txtSortMode.setText("Sort: Name");
                break;
            case TOTAL_SIZE:
                Collections.sort(sideloadedApps, (a, b) -> Long.compare(b.totalSize, a.totalSize));
                txtSortMode.setText("Sort: Total Size");
                break;
            case CACHE_SIZE:
                Collections.sort(sideloadedApps, (a, b) -> Long.compare(b.cacheSize, a.cacheSize));
                txtSortMode.setText("Sort: Cache Size");
                break;
            case DATA_SIZE:
                Collections.sort(sideloadedApps, (a, b) -> Long.compare(b.dataSize, a.dataSize));
                txtSortMode.setText("Sort: Data Size");
                break;
        }
    }

    private void updateHeaderStats() {
        long totalSize = 0;
        long totalCache = 0;
        for (ManagedApp app : sideloadedApps) {
            totalSize += app.totalSize;
            totalCache += app.cacheSize;
        }

        txtHeaderStats.setText(
                sideloadedApps.size() + " apps  •  " +
                        formatBytes(totalSize) + " used  •  " +
                        formatBytes(totalCache) + " cache"
        );
    }

    public static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
        return String.format("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0);
    }

    // ====================
    // Dialogs
    // ====================

    private void showSortDialog() {
        String[] options = {"Total Size (Largest)", "Cache Size (Largest)", "Data Size (Largest)", "Name (A-Z)"};
        ThemedDialog.showThemed(
                new AlertDialog.Builder(this)
                        .setTitle("Sort By")
                        .setItems(options, (d, which) -> {
                            switch (which) {
                                case 0: currentSort = SortMode.TOTAL_SIZE; break;
                                case 1: currentSort = SortMode.CACHE_SIZE; break;
                                case 2: currentSort = SortMode.DATA_SIZE; break;
                                case 3: currentSort = SortMode.NAME; break;
                            }
                            sortApps();
                            adapter.notifyDataSetChanged();
                        })
                        .setNegativeButton("Cancel", null)
                        .create()
        );
    }

    /**
     * Open Quest's native app info page for the package.
     * From there, the user can use Quest's trusted UI to clear cache/data.
     *
     * Tries Shizuku first to bypass the App Info intermediate screen,
     * falls back to standard Settings intent if Shizuku isn't ready.
     */
    private void openAppManage(ManagedApp app) {
        // Method 1: Shizuku direct launch (best UX - no intermediate screens)
        if (shizukuManager != null && shizukuManager.isReady()) {
            try {
                shizukuManager.executeShellCommand(
                        "am start -n com.android.settings/.applications.InstalledAppDetails -d package:" + app.packageName
                );
                Toast.makeText(this, "Opening " + app.label + " - use Quest's Clear cache/data buttons", Toast.LENGTH_LONG).show();
                return;
            } catch (Exception e) {
                android.util.Log.w("AppManager", "Shizuku launch failed, trying intent", e);
            }
        }

        // Method 2: Standard Settings intent fallback
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + app.packageName));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
            startActivity(intent);
            Toast.makeText(this, "Use the Clear buttons in Settings", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open app info: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ====================
    // Adapter
    // ====================

    private class AppManagerAdapter extends RecyclerView.Adapter<AppManagerAdapter.ViewHolder> {

        private final List<ManagedApp> apps;

        public AppManagerAdapter(List<ManagedApp> apps) {
            this.apps = apps;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_managed_app, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ManagedApp app = apps.get(position);

            // Apply theme
            Theme theme = ThemeManager.getInstance(AppManagerActivity.this).getCurrentTheme();
            if (holder.cardView != null) {
                holder.cardView.setCardBackgroundColor(theme.bgSecondary);
            }
            holder.label.setTextColor(theme.textPrimary);
            holder.packageName.setTextColor(theme.textMuted);
            holder.version.setTextColor(theme.accentPrimary);
            holder.totalLabel.setTextColor(theme.textPrimary);
            holder.totalValue.setTextColor(theme.accentPrimary);

            // Icon
            if (app.icon != null) {
                holder.icon.setImageDrawable(app.icon);
            } else {
                holder.icon.setImageResource(android.R.drawable.sym_def_app_icon);
            }

            // Text
            holder.label.setText(app.label);
            holder.packageName.setText(app.packageName);
            holder.version.setText("v" + (app.versionName != null ? app.versionName : "?"));

            // Sizes
            holder.appSize.setText(formatBytes(app.appSize));
            holder.dataSize.setText(formatBytes(app.dataSize));
            holder.cacheSize.setText(formatBytes(app.cacheSize));
            holder.totalValue.setText(formatBytes(app.totalSize));

            // Buttons
            holder.btnManage.setOnClickListener(v -> openAppManage(app));
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            CardView cardView;
            ImageView icon;
            TextView label;
            TextView packageName;
            TextView version;
            TextView appSize;
            TextView dataSize;
            TextView cacheSize;
            TextView totalLabel;
            TextView totalValue;
            Button btnManage;

            ViewHolder(View itemView) {
                super(itemView);
                cardView = (itemView instanceof CardView) ? (CardView) itemView : null;
                icon = itemView.findViewById(R.id.appIcon);
                label = itemView.findViewById(R.id.appLabel);
                packageName = itemView.findViewById(R.id.appPackage);
                version = itemView.findViewById(R.id.appVersion);
                appSize = itemView.findViewById(R.id.txtAppSize);
                dataSize = itemView.findViewById(R.id.txtDataSize);
                cacheSize = itemView.findViewById(R.id.txtCacheSize);
                totalLabel = itemView.findViewById(R.id.txtTotalLabel);
                totalValue = itemView.findViewById(R.id.txtTotalValue);
                btnManage = itemView.findViewById(R.id.btnManage);
            }
        }
    }
}