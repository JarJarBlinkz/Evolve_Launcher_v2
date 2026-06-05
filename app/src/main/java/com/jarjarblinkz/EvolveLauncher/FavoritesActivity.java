package com.jarjarblinkz.EvolveLauncher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.jarjarblinkz.EvolveLauncher.theme.Theme;
import com.jarjarblinkz.EvolveLauncher.theme.ThemeApplier;
import com.jarjarblinkz.EvolveLauncher.theme.ThemeManager;
import com.jarjarblinkz.EvolveLauncher.theme.ThemedDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated favorites screen with drag-to-reorder support.
 * Shows only apps the user has favorited, in user-defined order.
 */
public class FavoritesActivity extends AppCompatActivity {

    // Static instance so SettingsActivity can notify us when icon size changes
    public static FavoritesActivity instance;

    // Same URL pattern as MainActivity - landscape game covers from GitHub
    private static final String GITHUB_ICON_BASE_URL =
            "https://raw.githubusercontent.com/JarJarBlinkz/LauncherIcons/main/oculus_landscape/";

    private RecyclerView recyclerView;
    private GridLayoutManager gridLayoutManager;
    private FavoritesAdapter adapter;
    private final List<AppEntry> favoriteApps = new ArrayList<>();
    private FavoritesManager favoritesManager;
    private View emptyState;
    private TextView txtHeaderStats;

    // SharedPreferences for accessing icon_size (uniform with main launcher)
    private SharedPreferences prefs;

    /**
     * Simple data class for a favorite app
     */
    public static class AppEntry {
        public String packageName;
        public String label;
        public Drawable icon;
        public String githubIconUrl;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;

        try {
            supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        } catch (Exception e) {
            if (getSupportActionBar() != null) getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_favorites);

        // Larger window like AppManager
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

        favoritesManager = FavoritesManager.getInstance(this);

        // Same SharedPreferences as main launcher - reads icon_size from there
        prefs = getSharedPreferences("VRLPrefs", Context.MODE_PRIVATE);

        txtHeaderStats = findViewById(R.id.txtHeaderStats);
        emptyState = findViewById(R.id.emptyState);

        // Back button
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // Clear all button
        Button btnClearAll = findViewById(R.id.btnClearAll);
        if (btnClearAll != null) {
            btnClearAll.setOnClickListener(v -> confirmClearAll());
        }

        // Grid layout - adaptive column count based on icon_size pref
        recyclerView = findViewById(R.id.favoritesList);
        gridLayoutManager = new GridLayoutManager(this, calculateColumnCount(0));
        recyclerView.setLayoutManager(gridLayoutManager);

        // Adaptive column count on layout
        recyclerView.getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (recyclerView == null) return;
                        int width = recyclerView.getWidth();
                        int newColumns = calculateColumnCount(width);
                        if (gridLayoutManager.getSpanCount() != newColumns) {
                            gridLayoutManager.setSpanCount(newColumns);
                            if (adapter != null) adapter.notifyDataSetChanged();
                        }
                    }
                });

        adapter = new FavoritesAdapter();
        recyclerView.setAdapter(adapter);

        loadFavorites();

        // Apply theme
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            ThemeApplier.applyThemeToHierarchy(rootView);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Apply theme to activity chrome FIRST (header, buttons, etc.)
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            ThemeApplier.applyThemeToHierarchy(rootView);
        }

        // Recalculate column count in case icon_size pref changed in Settings
        if (gridLayoutManager != null && recyclerView != null) {
            int width = recyclerView.getWidth();
            int newColumns = calculateColumnCount(width);
            if (gridLayoutManager.getSpanCount() != newColumns) {
                gridLayoutManager.setSpanCount(newColumns);
            }
        }

        // Load favorites and rebind cards - this applies per-card theme via onBindViewHolder
        loadFavorites();
    }

    /**
     * Called by SettingsActivity when the user changes icon size.
     * Refreshes the grid with new column count and triggers icon size update.
     */
    public void updateIconSizes() {
        if (gridLayoutManager == null || adapter == null) return;
        int columns = calculateColumnCount(0);
        // Create a NEW GridLayoutManager (same approach as MainActivity)
        gridLayoutManager = new GridLayoutManager(this, columns);
        recyclerView.setLayoutManager(gridLayoutManager);
        adapter.notifyDataSetChanged();
        Toast.makeText(this, "Favorites layout updated: " + columns + " columns", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (instance == this) {
            instance = null;
        }
    }

    /**
     * Calculate column count - IDENTICAL logic to MainActivity.calculateOptimalColumns()
     * Uses screen dimensions, not recyclerView width
     */
    private int calculateColumnCount(int widthPixels) {
        android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenWidthPx = displayMetrics.widthPixels;
        int screenHeightPx = displayMetrics.heightPixels;

        int usableWidthPx = Math.min(screenWidthPx, screenHeightPx);
        int iconSizeDp = prefs.getInt("icon_size", 110);
        int marginDp = 8;

        float density = getResources().getDisplayMetrics().density;
        int iconWidthPx = (int) (iconSizeDp * density);
        int marginPx = (int) (marginDp * density);

        int availableWidth = usableWidthPx - (marginPx * 2);
        int columns = availableWidth / (iconWidthPx + marginPx);

        columns = Math.max(1, columns);

        if (iconSizeDp <= 90) {
            columns = Math.min(columns, 15);
        } else if (iconSizeDp <= 110) {
            columns = Math.min(columns, 12);
        } else {
            columns = Math.min(columns, 10);
        }

        boolean isLandscape = screenWidthPx > screenHeightPx;
        if (isLandscape && iconSizeDp <= 110) {
            columns = (int) (columns * 1.3f);
            columns = Math.min(columns, 15);
        }

        return columns;
    }

    /**
     * Load favorite apps with their details from PackageManager
     */
    private void loadFavorites() {
        favoriteApps.clear();
        PackageManager pm = getPackageManager();
        List<String> favPackages = favoritesManager.getFavorites();

        for (String packageName : favPackages) {
            try {
                ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
                AppEntry entry = new AppEntry();
                entry.packageName = packageName;

                // Use custom label if user renamed this app, otherwise original
                String originalLabel = pm.getApplicationLabel(info).toString();
                entry.label = CustomLabelManager.getInstance(this)
                        .getDisplayLabel(packageName, originalLabel);

                entry.githubIconUrl = GITHUB_ICON_BASE_URL + packageName + ".jpg";
                try {
                    entry.icon = pm.getApplicationIcon(info);
                } catch (Exception ignored) {}
                favoriteApps.add(entry);
            } catch (PackageManager.NameNotFoundException e) {
                // App was uninstalled - silently skip
            }
        }

        // Cleanup uninstalled favorites
        if (favoriteApps.size() != favPackages.size()) {
            List<String> stillInstalled = new ArrayList<>();
            for (AppEntry app : favoriteApps) stillInstalled.add(app.packageName);
            favoritesManager.cleanupUninstalled(stillInstalled);
        }

        // Sort alphabetically (no more drag-to-reorder)
        java.util.Collections.sort(favoriteApps, (a, b) -> {
            String la = a.label != null ? a.label : "";
            String lb = b.label != null ? b.label : "";
            return la.compareToIgnoreCase(lb);
        });

        adapter.notifyDataSetChanged();
        updateHeader();
        updateEmptyState();
    }

    private void updateHeader() {
        if (txtHeaderStats != null) {
            int count = favoriteApps.size();
            txtHeaderStats.setText(count + " favorite" + (count == 1 ? "" : "s") +
                    "  •  Tap star to remove");
        }
    }

    private void updateEmptyState() {
        if (emptyState != null && recyclerView != null) {
            if (favoriteApps.isEmpty()) {
                emptyState.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                emptyState.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        }
    }

    /**
     /**
     * Launch an app
     */
    private void launchApp(AppEntry app) {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(app.packageName);
            if (intent != null) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Cannot launch " + app.label, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error launching: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Remove a favorite (with confirmation)
     */
    private void removeFavorite(AppEntry app, int position) {
        favoriteApps.remove(position);
        adapter.notifyItemRemoved(position);
        adapter.notifyItemRangeChanged(position, favoriteApps.size());
        favoritesManager.removeFavorite(app.packageName);
        updateHeader();
        updateEmptyState();
        Toast.makeText(this, app.label + " removed from favorites", Toast.LENGTH_SHORT).show();
    }

    /**
     * Clear all favorites confirmation
     */
    private void confirmClearAll() {
        if (favoriteApps.isEmpty()) {
            Toast.makeText(this, "No favorites to clear", Toast.LENGTH_SHORT).show();
            return;
        }

        ThemedDialog.showThemed(
                new AlertDialog.Builder(this)
                        .setTitle("Clear All Favorites?")
                        .setMessage("Remove all " + favoriteApps.size() + " favorites? This cannot be undone.")
                        .setPositiveButton("Clear", (d, w) -> {
                            favoritesManager.clearAll();
                            favoriteApps.clear();
                            adapter.notifyDataSetChanged();
                            updateHeader();
                            updateEmptyState();
                            Toast.makeText(this, "Favorites cleared", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .create()
        );
    }

    // =====================
    // Adapter
    // =====================

    private class FavoritesAdapter extends RecyclerView.Adapter<FavoritesAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_favorite_app, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppEntry app = favoriteApps.get(position);

            Theme theme = ThemeManager.getInstance(FavoritesActivity.this).getCurrentTheme();
            if (holder.cardView != null) {
                holder.cardView.setCardBackgroundColor(theme.bgSecondary);

                // Add themed 1dp border (since regular CardView doesn't support stroke natively)
                float density = holder.itemView.getResources().getDisplayMetrics().density;
                android.graphics.drawable.GradientDrawable border = new android.graphics.drawable.GradientDrawable();
                border.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                border.setCornerRadius(20 * density);  // Matches card corner radius
                border.setStroke((int) (1 * density), theme.borderPrimary);
                border.setColor(android.graphics.Color.TRANSPARENT);
                holder.cardView.setForeground(border);
            }
            holder.appName.setTextColor(theme.textPrimary);

            // SAME ICON SIZING AS MAIN LAUNCHER - reads icon_size preference
            int iconSizeDp = prefs.getInt("icon_size", 110);
            int iconHeightDp = (int) (iconSizeDp * 0.5625f);  // 16:9 landscape ratio
            float density = holder.itemView.getResources().getDisplayMetrics().density;
            int iconWidthPx = (int) (iconSizeDp * density);
            int iconHeightPx = (int) (iconHeightDp * density);

            ViewGroup.LayoutParams iconParams = holder.appIcon.getLayoutParams();
            iconParams.width = iconWidthPx;
            iconParams.height = iconHeightPx;
            holder.appIcon.setLayoutParams(iconParams);
            holder.appIcon.requestLayout();

            // Load landscape game cover via Glide (same as MainActivity)
            // Falls back to app's default icon if GitHub cover not available
            if (app.githubIconUrl != null) {
                Glide.with(FavoritesActivity.this)
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
            } else if (app.icon != null) {
                holder.appIcon.setImageDrawable(app.icon);
            } else {
                holder.appIcon.setImageResource(android.R.drawable.sym_def_app_icon);
            }
            holder.appName.setText(app.label);

            // Star stays gold #FFD700 (XML tint - same as favorited stars on main launcher)
            holder.starButton.setColorFilter(0xFFFFD700);

            // Tap card → launch
            holder.cardView.setOnClickListener(v -> launchApp(app));

            // Tap star → remove from favorites
            holder.starButton.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && pos < favoriteApps.size()) {
                    removeFavorite(favoriteApps.get(pos), pos);
                }
            });
        }

        @Override
        public int getItemCount() {
            return favoriteApps.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            androidx.cardview.widget.CardView cardView;
            ImageView appIcon;
            TextView appName;
            ImageView starButton;

            ViewHolder(View itemView) {
                super(itemView);
                if (itemView instanceof androidx.cardview.widget.CardView) {
                    cardView = (androidx.cardview.widget.CardView) itemView;
                } else {
                    cardView = null;
                }
                appIcon = itemView.findViewById(R.id.appIcon);
                appName = itemView.findViewById(R.id.appName);
                starButton = itemView.findViewById(R.id.btnStar);
            }
        }
    }
}