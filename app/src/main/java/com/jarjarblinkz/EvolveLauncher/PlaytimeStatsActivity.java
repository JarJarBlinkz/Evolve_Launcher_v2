package com.jarjarblinkz.EvolveLauncher;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.tabs.TabLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PlaytimeStatsActivity extends AppCompatActivity {

    private PlaytimeTracker playtimeTracker;
    private RecyclerView playtimeList;
    private PlaytimeAdapter adapter;
    private TabLayout tabTimeRange;
    private TextView txtTotalPlaytime;
    private TextView txtMostPlayed;
    private TextView txtGameCount;
    private TextView txtLastUpdated;
    private TextView txtCurrentlyPlaying;
    private TextView txtCurrentPlaytime;
    private MaterialCardView currentlyPlayingCard;
    private MaterialCardView permissionCard;
    private LinearLayout summaryStatsContainer;
    private LinearLayout listHeadersContainer;
    private ImageView btnResetStats;
    private ImageView btnRefresh;
    private ImageView btnViewToggle;
    private View btnBack;
    private View btnGrantPermission;

    private PlaytimeTracker.TimeRange currentRange = PlaytimeTracker.TimeRange.TODAY;
    private List<PlaytimeTracker.PlaytimeEntry> entries = new ArrayList<>();
    private Handler updateHandler = new Handler();
    private Runnable updateRunnable;

    private boolean isCardView = true; // Default to card view

    // Permanent permission storage
    private SharedPreferences prefs;
    private static final String PREFS_NAME = "VRLPrefs";
    private static final String KEY_PERMISSION_GRANTED = "usage_stats_permission_granted";
    private static final String KEY_PERMISSION_NEVER_ASK = "usage_stats_never_ask";

    // Cache the permission state permanently after first successful check
    private boolean permissionState = false;
    private boolean permissionChecked = false;

    // GitHub cover images URL
    private static final String GITHUB_ICON_BASE_URL =
            "https://raw.githubusercontent.com/JarJarBlinkz/LauncherIcons/main/oculus_landscape/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make window background transparent for true see-through effect
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        setContentView(R.layout.activity_playtime_stats);

        playtimeTracker = new PlaytimeTracker(this);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Initialize views
        playtimeList = findViewById(R.id.playtimeList);
        tabTimeRange = findViewById(R.id.tabTimeRange);
        txtTotalPlaytime = findViewById(R.id.txtTotalPlaytime);
        txtMostPlayed = findViewById(R.id.txtMostPlayed);
        txtGameCount = findViewById(R.id.txtGameCount);
        txtLastUpdated = findViewById(R.id.txtLastUpdated);
        txtCurrentlyPlaying = findViewById(R.id.txtCurrentlyPlaying);
        txtCurrentPlaytime = findViewById(R.id.txtCurrentPlaytime);
        currentlyPlayingCard = findViewById(R.id.currentlyPlayingCard);
        permissionCard = findViewById(R.id.permissionCard);
        summaryStatsContainer = findViewById(R.id.summaryStatsContainer);
        listHeadersContainer = findViewById(R.id.listHeadersContainer);
        btnResetStats = findViewById(R.id.btnResetStats);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnViewToggle = findViewById(R.id.btnViewToggle);
        btnBack = findViewById(R.id.btnBack);
        btnGrantPermission = findViewById(R.id.btnGrantPermission);

        // Setup RecyclerView with Grid Layout - 6 columns
        playtimeList.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 6));
        adapter = new PlaytimeAdapter();
        playtimeList.setAdapter(adapter);

        // Check permission once at startup
        checkPermissionOnce();

        // Setup tab listener
        tabTimeRange.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0: currentRange = PlaytimeTracker.TimeRange.TODAY; break;
                    case 1: currentRange = PlaytimeTracker.TimeRange.WEEK; break;
                    case 2: currentRange = PlaytimeTracker.TimeRange.MONTH; break;
                    case 3: currentRange = PlaytimeTracker.TimeRange.ALL_TIME; break;
                }
                refreshData();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Button listeners
        btnRefresh.setOnClickListener(v -> refreshData());
        btnBack.setOnClickListener(v -> finish());

        btnViewToggle.setOnClickListener(v -> {
            isCardView = !isCardView;
            switchViewMode();
        });

        btnResetStats.setOnClickListener(v -> {
            new AlertDialog.Builder(PlaytimeStatsActivity.this)
                    .setTitle("Clear All Statistics")
                    .setMessage("Are you sure you want to clear ALL playtime statistics?\n\nThis will reset all counts to zero and cannot be undone.")
                    .setPositiveButton("CLEAR ALL", (d, w) -> {
                        // Clear stats in tracker
                        playtimeTracker.clearAllStats();

                        // Clear the current entries list
                        entries.clear();
                        adapter.notifyDataSetChanged();

                        // Reset the summary stats to zero
                        txtTotalPlaytime.setText("0h");
                        txtMostPlayed.setText("None");
                        txtGameCount.setText("0");

                        // Update the timestamp
                        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                        txtLastUpdated.setText("Cleared at: " + sdf.format(new Date()));

                        Toast.makeText(PlaytimeStatsActivity.this, "All statistics cleared", Toast.LENGTH_LONG).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        btnGrantPermission.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                startActivity(intent);
                // Mark that we've shown the settings, but don't check yet
                prefs.edit().putBoolean(KEY_PERMISSION_NEVER_ASK, true).apply();
            } catch (Exception e) {
                Toast.makeText(PlaytimeStatsActivity.this, "Please grant permission manually via ADB", Toast.LENGTH_LONG).show();
                // Show ADB command
                new AlertDialog.Builder(PlaytimeStatsActivity.this)
                        .setTitle("ADB Command")
                        .setMessage("Run this command:\n\nadb shell pm grant " + getPackageName() + " android.permission.PACKAGE_USAGE_STATS")
                        .setPositiveButton("OK", null)
                        .show();
            }
        });

        // Start periodic updates
        startPeriodicUpdates();

        // Set initial UI state based on default view mode (card view)
        setInitialViewState();

        // Initial data load
        refreshData();
    }

    private void checkPermissionOnce() {
        // If we've already checked and determined permission is granted, never check again
        boolean permanentlyGranted = prefs.getBoolean(KEY_PERMISSION_GRANTED, false);

        if (permanentlyGranted) {
            permissionState = true;
            permissionChecked = true;
            permissionCard.setVisibility(View.GONE);
            playtimeList.setVisibility(View.VISIBLE);
            return;
        }

        // Otherwise, check once
        boolean hasPerm = playtimeTracker.hasPermission();
        if (hasPerm) {
            // Save that permission is permanently granted
            prefs.edit().putBoolean(KEY_PERMISSION_GRANTED, true).apply();
            permissionState = true;
            permissionCard.setVisibility(View.GONE);
            playtimeList.setVisibility(View.VISIBLE);
        } else {
            permissionState = false;
            permissionCard.setVisibility(View.VISIBLE);
            playtimeList.setVisibility(View.GONE);

            // If we've never asked before, show the ADB command
            if (!prefs.getBoolean(KEY_PERMISSION_NEVER_ASK, false)) {
                showAdbCommandDialog();
                prefs.edit().putBoolean(KEY_PERMISSION_NEVER_ASK, true).apply();
            }
        }
        permissionChecked = true;
    }

    private void showAdbCommandDialog() {
        String adbCommand = "adb shell pm grant " + getPackageName() + " android.permission.PACKAGE_USAGE_STATS";

        new AlertDialog.Builder(PlaytimeStatsActivity.this)
                .setTitle("Usage Stats Permission Required")
                .setMessage("To track playtime on Meta Quest, you need to grant this permission via ADB:\n\n" +
                        adbCommand + "\n\n" +
                        "1. Connect your Quest to a computer with ADB installed\n" +
                        "2. Put on your headset and accept any USB debugging prompt\n" +
                        "3. Open a command prompt/terminal on your computer\n" +
                        "4. Run the command above\n" +
                        "5. After running, restart this app\n\n" +
                        "Note: The Settings app method is NOT available on Meta Quest.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void startPeriodicUpdates() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateCurrentlyPlaying();
                updateHandler.postDelayed(this, 5000); // Update every 5 seconds
            }
        };
        updateHandler.post(updateRunnable);
    }

    private void updateCurrentlyPlaying() {
        // Only update if we have permission (using cached state)
        if (!prefs.getBoolean(KEY_PERMISSION_GRANTED, false)) {
            currentlyPlayingCard.setVisibility(View.GONE);
            return;
        }

        String currentPackage = playtimeTracker.getCurrentRunningApp();
        if (currentPackage != null) {
            PlaytimeTracker.AppInfo info = playtimeTracker.getAppInfo(currentPackage);
            if (info != null) {
                currentlyPlayingCard.setVisibility(View.VISIBLE);
                txtCurrentlyPlaying.setText("Currently Playing: " + info.label);

                // Get today's playtime for this app
                long todayPlaytime = playtimeTracker.getTodayPlaytime().getOrDefault(currentPackage, 0L);
                txtCurrentPlaytime.setText(PlaytimeTracker.formatPlaytime(todayPlaytime));
            } else {
                currentlyPlayingCard.setVisibility(View.GONE);
            }
        } else {
            currentlyPlayingCard.setVisibility(View.GONE);
        }
    }

    private void refreshData() {
        // Use the permanently stored permission state - don't recheck
        if (!prefs.getBoolean(KEY_PERMISSION_GRANTED, false)) {
            return;
        }

        // Get cleared timestamp
        long clearedAt = playtimeTracker.getStatsClearedAt();

        // Get leaderboard data
        playtimeTracker.getPlaytimeLeaderboard(50, currentRange, new PlaytimeTracker.LeaderboardCallback() {
            @Override
            public void onLeaderboardReady(List<PlaytimeTracker.PlaytimeEntry> leaderboard) {
                entries = leaderboard;
                runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    updateSummaryStats();
                    updateLastUpdated();

                    // If stats were recently cleared, show a note
                    if (clearedAt > System.currentTimeMillis() - 60000) { // Last minute
                        Toast.makeText(PlaytimeStatsActivity.this, "Stats cleared - new data will appear as you use apps", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void updateSummaryStats() {
        // Total playtime
        long totalMs = playtimeTracker.getTotalPlaytime(currentRange);
        String mostPlayedApp = "None";
        long mostPlayedTime = 0;

        for (PlaytimeTracker.PlaytimeEntry entry : entries) {
            if (entry.playtime > mostPlayedTime) {
                mostPlayedTime = entry.playtime;
                mostPlayedApp = entry.getAppName();
            }
        }

        txtTotalPlaytime.setText(PlaytimeTracker.formatPlaytime(totalMs));
        txtMostPlayed.setText(mostPlayedApp.length() > 15 ?
                mostPlayedApp.substring(0, 12) + "..." : mostPlayedApp);

        // Use getActiveAppsCount here if you want
        int activeCount = playtimeTracker.getActiveAppsCount(currentRange);
        txtGameCount.setText(String.valueOf(activeCount)); // or entries.size()
    }

    private void updateLastUpdated() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        txtLastUpdated.setText("Last updated: " + sdf.format(new Date()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Don't recheck permission - use stored state
        // But if we haven't checked yet (first launch), check once
        if (!permissionChecked) {
            checkPermissionOnce();
        }
        refreshData();
        startPeriodicUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
    }

    private String getGitHubIconUrl(String packageName) {
        return GITHUB_ICON_BASE_URL + packageName + ".jpg";
    }

    private void setInitialViewState() {
        // Set initial visibility based on default isCardView = true
        if (isCardView) {
            summaryStatsContainer.setVisibility(View.GONE);
            currentlyPlayingCard.setVisibility(View.GONE);
            listHeadersContainer.setVisibility(View.GONE);
        } else {
            summaryStatsContainer.setVisibility(View.VISIBLE);
            currentlyPlayingCard.setVisibility(View.VISIBLE);
            listHeadersContainer.setVisibility(View.VISIBLE);
        }
    }

    private void switchViewMode() {
        if (isCardView) {
            // Card view - hide summary stats, currently playing, and list headers
            summaryStatsContainer.setVisibility(View.GONE);
            currentlyPlayingCard.setVisibility(View.GONE);
            listHeadersContainer.setVisibility(View.GONE);
            playtimeList.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 6));
        } else {
            // List view - show all stats
            summaryStatsContainer.setVisibility(View.VISIBLE);
            currentlyPlayingCard.setVisibility(View.VISIBLE);
            listHeadersContainer.setVisibility(View.VISIBLE);
            playtimeList.setLayoutManager(new LinearLayoutManager(this));
        }

        // Recreate adapter with new layout
        adapter = new PlaytimeAdapter();
        playtimeList.setAdapter(adapter);
        adapter.notifyDataSetChanged();

        refreshData();
    }

    // Adapter class for RecyclerView
    private class PlaytimeAdapter extends RecyclerView.Adapter<PlaytimeAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layoutId = isCardView ? R.layout.item_playtime_card : R.layout.item_playtime_stat;
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(layoutId, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PlaytimeTracker.PlaytimeEntry entry = entries.get(position);

            // App name
            String appName = entry.getAppName();
            if (appName.isEmpty()) {
                appName = entry.getPackageName();
            }
            holder.txtAppName.setText(appName);

            // Load game cover from GitHub
            String githubIconUrl = getGitHubIconUrl(entry.getPackageName());

            Drawable appIcon = null;
            try {
                appIcon = getPackageManager().getApplicationIcon(entry.getPackageName());
            } catch (PackageManager.NameNotFoundException e) {
                appIcon = getDrawable(android.R.drawable.sym_def_app_icon);
            }

            Glide.with(PlaytimeStatsActivity.this)
                    .load(githubIconUrl)
                    .apply(new RequestOptions()
                            .placeholder(appIcon)
                            .error(appIcon)
                            .centerCrop()
                            .override(400, 225)
                            .skipMemoryCache(false)
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .dontAnimate())
                    .into(holder.imgGameCover);

            // Playtime
            holder.txtPlaytime.setText(entry.getFormattedPlaytime());

            // Percentage (relative to total)
            long totalMs = 0;
            for (PlaytimeTracker.PlaytimeEntry e : entries) {
                totalMs += e.playtime;
            }
            int percent = totalMs > 0 ? (int) ((entry.playtime * 100) / totalMs) : 0;
            int rank = position + 1; // Position starts at 0, rank starts at 1
            holder.txtPercentage.setText("Rank #" + rank + " (" + percent + "%)");

            if (entry.appInfo != null) {
                // Build+Version in format: v{build}+{version}
                StringBuilder buildVersionText = new StringBuilder("v");

                // Add build number first
                if (entry.appInfo.versionCode > 0) {
                    buildVersionText.append(entry.appInfo.versionCode);
                } else {
                    buildVersionText.append("?");
                }

                // Add + then version
                if (entry.appInfo.versionName != null && !entry.appInfo.versionName.equals("N/A")) {
                    buildVersionText.append("+").append(entry.appInfo.versionName);
                } else {
                    buildVersionText.append("+?");
                }

                holder.txtBuildVersion.setText(buildVersionText.toString());

                // App type
                holder.txtType.setText(entry.appInfo.getTypeString());
                holder.txtType.setTextColor(entry.appInfo.getTypeColor());

                // Install date
                if (entry.appInfo.firstInstallDate != null && !entry.appInfo.firstInstallDate.equals("N/A")) {
                    holder.txtInstallDate.setText(entry.appInfo.firstInstallDate);
                } else {
                    holder.txtInstallDate.setText("Unknown");
                }

// Update date
                if (entry.appInfo.lastUpdateDate != null && !entry.appInfo.lastUpdateDate.equals("N/A")) {
                    holder.txtUpdateDate.setText(entry.appInfo.lastUpdateDate);
                } else {
                    holder.txtUpdateDate.setText("Unknown");
                }
            } else {
                holder.txtBuildVersion.setText("v?+?");
                holder.txtType.setText("Unknown");
                holder.txtType.setTextColor(Color.GRAY);
                holder.txtInstallDate.setText("Unknown");
                holder.txtUpdateDate.setText("Unknown");
            }

            // Click listener for details - FIXED: use PlaytimeStatsActivity.this
            final PlaytimeTracker.PlaytimeEntry currentEntry = entry;
            holder.itemView.setOnClickListener(v -> {
                showAppDetails(currentEntry);
            });
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imgGameCover;
            TextView txtAppName;
            TextView txtPlaytime;
            TextView txtPercentage;
            TextView txtBuildVersion;  // Will show format like "v463+2.8.0"
            TextView txtType;
            TextView txtInstallDate;
            TextView txtUpdateDate;
            MaterialCardView cardView;

            ViewHolder(View itemView) {
                super(itemView);
                cardView = (MaterialCardView) itemView;
                imgGameCover = itemView.findViewById(R.id.imgGameCover);
                txtAppName = itemView.findViewById(R.id.txtAppName);
                txtPlaytime = itemView.findViewById(R.id.txtPlaytime);
                txtPercentage = itemView.findViewById(R.id.txtPercentage);
                txtBuildVersion = itemView.findViewById(R.id.txtBuildVersion);
                txtType = itemView.findViewById(R.id.txtType);
                txtInstallDate = itemView.findViewById(R.id.txtInstallDate);
                txtUpdateDate = itemView.findViewById(R.id.txtUpdateDate);
            }
        }
    }

    // Moved this method outside the adapter class
    private void showAppDetails(PlaytimeTracker.PlaytimeEntry entry) {
        if (entry == null || entry.appInfo == null) return;

        PlaytimeTracker.AppInfo info = entry.appInfo;

        String buildVersion = "v" + info.versionCode + "+" + info.versionName;

        String details = "📱 " + info.label + "\n\n" +
                "Package: " + info.packageName + "\n" +
                "Version: " + buildVersion + "\n" +  // Shows as "v463+2.8.0"
                "Type: " + info.getTypeString() + "\n" +
                "Installed: " + info.firstInstallDate + "\n" +
                "Last Update: " + info.lastUpdateDate + "\n\n" +
                "⏱️ Playtime (" + currentRange.getDisplayName() + "):\n" +
                "  " + entry.getFormattedPlaytimeDetailed() + "\n" +
                "  (" + entry.getFormattedPlaytime() + ")\n\n" +
                "Install Source: " + (info.storeApp ? "Oculus Store" : (info.sideloaded ? "Sideloaded" : "System"));

        new AlertDialog.Builder(PlaytimeStatsActivity.this)
                .setTitle("App Details")
                .setMessage(details)
                .setPositiveButton("App Info", (d, w) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + info.packageName));
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(PlaytimeStatsActivity.this, "Cannot open app info", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }
}