package com.jarjarblinkz.EvolveLauncher;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PlaytimeTracker {

    private static final String TAG = "PlaytimeTracker";
    private static final String PREFS_PLAYTIME = "vr_playtime";
    private static final String KEY_LAST_QUERY = "last_playtime_query";
    private static final long DAY_IN_MS = 24 * 60 * 60 * 1000L;
    private static final String PREFS_CLEAR_STATS = "vr_playtime_cleared";

    private final UsageStatsManager usageStatsManager;
    private final PackageManager pm;
    private final Context context;
    private final SharedPreferences playtimePrefs;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // System packages to exclude
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
            "com.facebook.katana",
            "com.facebook.orca",
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
            "com.meta",
            "com.meta.view",
            "com.meta.systemu",
            "com.oculus.horizon",
            "com.oculus.vrshell",
            "com.oculus.home",
            "com.oculus.systemactivities",
            "com.oculus.store",
            "com.oculus.browser",
            "com.oculus.guardian",
            "com.oculus.socialplatform"
    };

    public PlaytimeTracker(Context context) {
        this.context = context;
        this.usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        this.pm = context.getPackageManager();
        this.playtimePrefs = context.getSharedPreferences(PREFS_PLAYTIME, Context.MODE_PRIVATE);
    }

    /**
     * Update persistent storage with current playtime data
     * This is called periodically to save playtime data
     */
    public void updatePersistentPlaytime() {
        // This method exists for compatibility - the actual persistence
        // is handled by querying UsageStats directly each time
        // No implementation needed as we query fresh data each time
        // But we keep the method to avoid breaking existing code
    }

    /** Check if usage stats permission is granted */
    public boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                long now = System.currentTimeMillis();
                List<UsageStats> stats = usageStatsManager.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY, now - 1000, now);
                return stats != null && !stats.isEmpty();
            } catch (SecurityException e) {
                return false;
            }
        }
        return false;
    }

    /** Check if package is a system app that should be excluded */
    private boolean isSystemPackage(String packageName) {
        // Check against system package list
        for (String systemPackage : SYSTEM_PACKAGES) {
            if (packageName.startsWith(systemPackage)) {
                return true;
            }
        }

        // Also check via PackageManager flags
        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            boolean isSystem = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean isUpdatedSystem = (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            return isSystem || isUpdatedSystem;
        } catch (PackageManager.NameNotFoundException e) {
            return true; // If we can't find it, exclude it to be safe
        }
    }

    /** Query UsageStats for a time range, filtering out system apps */
    private Map<String, Long> getPlaytimeInRange(long startTime, long endTime) {
        Map<String, Long> result = new HashMap<>();

        // Check if stats were cleared
        long clearedAt = playtimePrefs.getLong("stats_cleared_at", 0);

        try {
            List<UsageStats> stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_BEST, startTime, endTime);

            if (stats != null) {
                for (UsageStats usage : stats) {
                    long time = usage.getTotalTimeInForeground();
                    String pkg = usage.getPackageName();
                    long lastUsed = usage.getLastTimeUsed();

                    // Skip system apps
                    if (time > 0 && !isSystemPackage(pkg)) {
                        // If stats were cleared, only count data after the clear
                        if (clearedAt > 0 && lastUsed < clearedAt) {
                            // This usage was before clear, skip it
                            continue;
                        }

                        result.put(pkg, result.getOrDefault(pkg, 0L) + time);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying usage stats", e);
        }

        return result;
    }

    /** Helper to get today, week, last7days, last30days, allTime */
    public Map<String, Long> getTodayPlaytime() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return getPlaytimeInRange(cal.getTimeInMillis(), System.currentTimeMillis());
    }

    public Map<String, Long> getThisWeekPlaytime() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return getPlaytimeInRange(cal.getTimeInMillis(), System.currentTimeMillis());
    }

    public Map<String, Long> getLast7DaysPlaytime() {
        long now = System.currentTimeMillis();
        return getPlaytimeInRange(now - 7 * DAY_IN_MS, now);
    }

    public Map<String, Long> getLast30DaysPlaytime() {
        long now = System.currentTimeMillis();
        long startTime = now - 30 * DAY_IN_MS;
        Log.d("PLAYTIME_DEBUG", "Last 30 days: from " + new Date(startTime) + " to " + new Date(now));
        Map<String, Long> result = getPlaytimeInRange(startTime, now);
        Log.d("PLAYTIME_DEBUG", "Last 30 days - found " + result.size() + " apps");
        return result;
    }

    public Map<String, Long> getAllTimePlaytime() {
        long now = System.currentTimeMillis();
        // Get up to 90 days of data (UsageStats might not have older data)
        long startTime = now - 90 * DAY_IN_MS;
        Log.d("PLAYTIME_DEBUG", "All time (90 days): from " + new Date(startTime) + " to " + new Date(now));

        // ADD THIS DEBUG CODE
        long clearedAt = getStatsClearedAt();
        if (clearedAt > 0) {
            Log.d("PLAYTIME_DEBUG", "Stats were cleared at: " + new Date(clearedAt));
            Log.d("PLAYTIME_DEBUG", "Days since clear: " + ((now - clearedAt) / DAY_IN_MS));
            Log.d("PLAYTIME_DEBUG", "Clear time relative to range: " +
                    (clearedAt > startTime ? "WITHIN range" : "BEFORE range"));
        } else {
            Log.d("PLAYTIME_DEBUG", "Stats have never been cleared");
        }

        Map<String, Long> result = getPlaytimeInRange(startTime, now);
        Log.d("PLAYTIME_DEBUG", "All time - found " + result.size() + " apps");

        // Log the top apps
        for (Map.Entry<String, Long> entry : result.entrySet()) {
            if (entry.getValue() > 3600000) { // More than 1 hour
                Log.d("PLAYTIME_DEBUG", "  " + entry.getKey() + ": " + formatPlaytime(entry.getValue()));
            }
        }

        return result;
    }

    public void clearAllStats() {
        long clearTime = System.currentTimeMillis();
        Log.d("PLAYTIME_DEBUG", "Clearing stats at: " + new Date(clearTime));
        playtimePrefs.edit().putLong("stats_cleared_at", clearTime).apply();
        playtimePrefs.edit().remove("cached_playtime").apply();
    }

    /**
     * Get the timestamp when stats were last cleared
     */
    public long getStatsClearedAt() {
        return playtimePrefs.getLong("stats_cleared_at", 0);
    }

    /** Format milliseconds as human-readable */
    public static String formatPlaytime(long ms) {
        if (ms < 60_000) return "<1 min";
        long h = TimeUnit.MILLISECONDS.toHours(ms);
        long m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60;
        return h > 0 ? String.format(Locale.US, "%dh %dm", h, m) : String.format(Locale.US, "%dm", m);
    }

    /** Format milliseconds as HH:MM:SS */
    public static String formatPlaytimeDetailed(long ms) {
        long h = TimeUnit.MILLISECONDS.toHours(ms);
        long m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60;
        long s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }

    /** Get currently foreground app (only if it's not a system app) */
    public String getCurrentRunningApp() {
        try {
            long now = System.currentTimeMillis();
            List<UsageStats> stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, now - 10_000, now);
            UsageStats recent = null;
            if (stats != null) {
                for (UsageStats u : stats) {
                    // Skip system apps
                    if (isSystemPackage(u.getPackageName())) {
                        continue;
                    }
                    if (recent == null || u.getLastTimeUsed() > recent.getLastTimeUsed()) {
                        recent = u;
                    }
                }
            }
            return recent != null ? recent.getPackageName() : null;
        } catch (Exception e) {
            Log.e(TAG, "Error getting current app", e);
            return null;
        }
    }


    /** Get package info safely, including system/store/sideloaded classification */
    public AppInfo getAppInfo(String packageName) {
        try {
            PackageInfo info = pm.getPackageInfo(packageName, 0);
            ApplicationInfo appInfo = info.applicationInfo;
            boolean system = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;

            // Check if it's a store app
            boolean store = false;
            try {
                String installer = pm.getInstallerPackageName(packageName);

                // Oculus/Meta Store installers
                store = "com.oculus.store".equals(installer) ||
                        "com.oculus.tw".equals(installer) ||
                        "com.oculus.horizon".equals(installer) ||
                        "com.oculus.vrshell".equals(installer) ||
                        "com.oculus.ocms".equals(installer) ||  // ADD THIS LINE
                        "com.meta.store".equals(installer) ||
                        "com.meta.horizon".equals(installer) ||

                        // Other stores
                        "com.android.vending".equals(installer) || // Google Play
                        "com.amazon.venezia".equals(installer) ||  // Amazon App Store
                        "com.sec.android.app.samsungapps".equals(installer); // Samsung Galaxy Store

                Log.d("PlaytimeTracker", "Package: " + packageName + " installer: " + installer + " store: " + store);

            } catch (Exception e) {
                Log.d("PlaytimeTracker", "No installer for: " + packageName);
            }

            boolean sideloaded = !system && !store;

            long firstInstall = info.firstInstallTime;
            long lastUpdate = info.lastUpdateTime;

            String firstInstallStr = firstInstall > 0 ? formatDate(firstInstall) : "N/A";
            String lastUpdateStr = lastUpdate > 0 ? formatDate(lastUpdate) : "N/A";

            // Get app label
            String label = appInfo.loadLabel(pm).toString();

            // Get version code (different for different Android versions)
            long versionCode;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                versionCode = info.getLongVersionCode();
            } else {
                versionCode = info.versionCode;
            }

            Log.d("PlaytimeTracker", "App: " + label + " - system: " + system + " store: " + store + " sideloaded: " + sideloaded);

            return new AppInfo(
                    packageName,
                    label,
                    info.versionName != null ? info.versionName : "N/A",
                    versionCode,
                    system, store, sideloaded,
                    firstInstallStr,
                    lastUpdateStr
            );
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** Format timestamp nicely */
    private static String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return sdf.format(new Date(timestamp));
    }

    /**
     * Get playtime leaderboard synchronously (games/apps only, no system apps)
     */
    /**
     * Get playtime leaderboard synchronously (games/apps only, no system apps)
     */
    /**
     * Get playtime leaderboard synchronously (games/apps only, no system apps)
     */
    public List<PlaytimeEntry> getPlaytimeLeaderboard(int limit, TimeRange range) {
        Map<String, Long> playtimeMap;
        switch (range) {
            case TODAY: playtimeMap = getTodayPlaytime(); break;
            case WEEK: playtimeMap = getThisWeekPlaytime(); break;
            case MONTH: playtimeMap = getLast30DaysPlaytime(); break;
            case ALL_TIME: default: playtimeMap = getAllTimePlaytime(); break;
        }

        Log.d(TAG, "getPlaytimeLeaderboard for " + range + " - map size: " + playtimeMap.size());

        List<PlaytimeEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Long> e : playtimeMap.entrySet()) {
            if (e.getValue() > 60_000) { // Only include if more than 1 minute
                AppInfo info = getAppInfo(e.getKey());
                if (info != null) {
                    entries.add(new PlaytimeEntry(info, e.getValue()));
                    Log.d(TAG, "Added entry for: " + info.label + " - playtime: " + e.getValue());
                } else {
                    // Create a minimal AppInfo for apps that might not be found
                    AppInfo minimalInfo = new AppInfo(
                            e.getKey(),
                            cleanUpPackageName(e.getKey()),
                            "N/A",
                            0,  // versionCode
                            false, false, false,
                            "N/A", "N/A"
                    );
                    entries.add(new PlaytimeEntry(minimalInfo, e.getValue()));
                    Log.d(TAG, "Added minimal entry for: " + e.getKey());
                }
            }
        }

        Collections.sort(entries, (a, b) -> Long.compare(b.playtime, a.playtime));

        if (entries.size() > limit) {
            entries = entries.subList(0, limit);
        }

        Log.d(TAG, "Returning " + entries.size() + " entries");
        return entries;
    }

    /**
     * Get playtime leaderboard asynchronously
     */
    public void getPlaytimeLeaderboard(int limit, TimeRange range, LeaderboardCallback callback) {
        Executors.newSingleThreadExecutor().submit(() -> {
            List<PlaytimeEntry> entries = getPlaytimeLeaderboard(limit, range);
            mainHandler.post(() -> callback.onLeaderboardReady(entries));
        });
    }

    /**
     * Clean up package name to make it readable
     */
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

    /**
     * Get total playtime for all games/apps in a time range
     */
    public long getTotalPlaytime(TimeRange range) {
        Map<String, Long> playtimeMap;
        switch (range) {
            case TODAY: playtimeMap = getTodayPlaytime(); break;
            case WEEK: playtimeMap = getThisWeekPlaytime(); break;
            case MONTH: playtimeMap = getLast30DaysPlaytime(); break;
            case ALL_TIME: default: playtimeMap = getAllTimePlaytime(); break;
        }

        long total = 0;
        for (long time : playtimeMap.values()) {
            total += time;
        }
        return total;
    }

    /**
     * Get count of games/apps with playtime > 0
     */
    public int getActiveAppsCount(TimeRange range) {
        Map<String, Long> playtimeMap;
        switch (range) {
            case TODAY: playtimeMap = getTodayPlaytime(); break;
            case WEEK: playtimeMap = getThisWeekPlaytime(); break;
            case MONTH: playtimeMap = getLast30DaysPlaytime(); break;
            case ALL_TIME: default: playtimeMap = getAllTimePlaytime(); break;
        }

        int count = 0;
        for (long time : playtimeMap.values()) {
            if (time > 60_000) { // More than 1 minute
                count++;
            }
        }
        return count;
    }

    /** Interfaces & classes */
    public interface LeaderboardCallback {
        void onLeaderboardReady(List<PlaytimeEntry> leaderboard);
    }

    public static class PlaytimeEntry {
        public final AppInfo appInfo;
        public final long playtime;

        public PlaytimeEntry(AppInfo appInfo, long playtime) {
            this.appInfo = appInfo;
            this.playtime = playtime;
        }

        public String getFormattedPlaytime() {
            return formatPlaytime(playtime);
        }

        public String getFormattedPlaytimeDetailed() {
            return formatPlaytimeDetailed(playtime);
        }

        public String getPackageName() {
            return appInfo != null ? appInfo.packageName : "";
        }

        public String getAppName() {
            return appInfo != null ? appInfo.label : "";
        }
    }

    public static class AppInfo {
        public final String packageName;
        public final String label;
        public final String versionName;
        public final long versionCode;
        public final boolean systemApp;
        public final boolean storeApp;
        public final boolean sideloaded;
        public final String firstInstallDate;
        public final String lastUpdateDate;

        public AppInfo(String packageName, String label, String versionName, long versionCode,  // ADD versionCode HERE
                       boolean systemApp, boolean storeApp, boolean sideloaded,
                       String firstInstallDate, String lastUpdateDate) {
            this.packageName = packageName;
            this.label = label;
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.systemApp = systemApp;
            this.storeApp = storeApp;
            this.sideloaded = sideloaded;
            this.firstInstallDate = firstInstallDate;
            this.lastUpdateDate = lastUpdateDate;
        }

        public String getTypeString() {
            if (systemApp) return "System";
            if (storeApp) return "Store";
            if (sideloaded) return "Sideloaded";
            return "Unknown";
        }

        public int getTypeColor() {
            if (systemApp) return 0xFF808080; // Gray
            if (storeApp) return 0xFF4CAF50;  // Green
            if (sideloaded) return 0xFFFF9800; // Orange
            return 0xFF6B8EFF; // Blue
        }

        public boolean isGame() {
            return !systemApp;
        }
    }

    public enum TimeRange {
        TODAY,
        WEEK,
        MONTH,
        ALL_TIME;

        public String getDisplayName() {
            switch (this) {
                case TODAY: return "Today";
                case WEEK: return "This Week";
                case MONTH: return "This Month";
                case ALL_TIME: return "All Time";
                default: return "";
            }
        }
    }
}