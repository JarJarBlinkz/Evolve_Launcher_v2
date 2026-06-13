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
        // Hide grid until entry animation kicks off
        recyclerView.setAlpha(0f);
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

        // Force rebind after layout settles - applies icon sizes correctly from first frame
        recyclerView.post(() -> {
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        });

        loadFavorites();

        // VR POLISH: schedule the directional entry animation
        scheduleEntryAnimation();

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
     */
    public void updateIconSizes() {
        if (gridLayoutManager == null || adapter == null) return;
        int columns = calculateColumnCount(0);

        // Update existing layout manager (keep listeners and state intact)
        gridLayoutManager.setSpanCount(columns);

        // Refresh all visible items so they re-bind with new slider value
        adapter.notifyDataSetChanged();

        Toast.makeText(this, "Favorites updated: " + columns + " columns", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (instance == this) {
            instance = null;
        }
    }

    /**
     * Calculate column count - matches MainActivity logic exactly.
     * Uses the FULL card footprint (icon + padding + margin) so cards never shrink.
     */
    /**
     * VR POLISH: batch entry animation - all visible cards animate in from
     * 8 directions, triggered once after the grid finishes its initial layout.
     */
    private boolean entryAnimationPlayed = false;

    private void playEntryAnimation() {
        if (entryAnimationPlayed || recyclerView == null) return;
        if (recyclerView.getChildCount() == 0) return;
        entryAnimationPlayed = true;

        float density = getResources().getDisplayMetrics().density;
        float distance = 300f * density;

        // STEP 1: set every visible child to its off-screen start state
        // while grid is still alpha=0 (so user doesn't see static frame)
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View card = recyclerView.getChildAt(i);
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

        // STEP 2: reveal grid (children still alpha 0, so still invisible)
        recyclerView.setAlpha(1f);

        // Track when the last card's animation finishes - we'll do a
        // notifyDataSetChanged at the end to fix hover hitboxes (mimics
        // what icon-resize does to restore hover dispatch on some headsets)
        final int totalCards = recyclerView.getChildCount();
        long maxDelay = (totalCards - 1) * 45L + 650L + 100L;

        // STEP 3: animate each child in
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            final View card = recyclerView.getChildAt(i);
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
                    .withEndAction(() -> {
                        card.setTranslationX(0f);
                        card.setTranslationY(0f);
                        card.setAlpha(1f);
                        card.setScaleX(1f);
                        card.setScaleY(1f);
                        card.setRotation(0f);
                    })
                    .start();
        }

        // Final cleanup - mimics the resize fix
        recyclerView.postDelayed(() -> {
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            recyclerView.requestLayout();
        }, maxDelay);
    }

    private void scheduleEntryAnimation() {
        if (recyclerView == null) return;
        // Initial delay lets the adaptive column listener settle first,
        // otherwise its notifyDataSetChanged would destroy our animations
        recyclerView.postDelayed(() -> tryPlayEntryAnimation(0), 300);
    }

    private void tryPlayEntryAnimation(int attempt) {
        if (entryAnimationPlayed || recyclerView == null) return;
        if (attempt > 20) {
            // Safety - never hide grid forever
            recyclerView.setAlpha(1f);
            entryAnimationPlayed = true;
            return;
        }

        if (recyclerView.getChildCount() > 0) {
            playEntryAnimation();
        } else {
            recyclerView.postDelayed(() -> tryPlayEntryAnimation(attempt + 1), 100);
        }
    }

    /**
     * VR polish: hover/focus/press animations for cards.
     * Same behavior as the main launcher for consistency.
     * - Hover/focus: dramatic pop forward with overshoot bounce
     * - Press: brief compress + even bigger pop-out on release
     */
    private void applyCardInteractionEffects(View card) {
        if (card == null) return;

        final float density = getResources().getDisplayMetrics().density;
        final float hoverLiftPx = 40f * density;
        final float popLiftPx = 56f * density;
        final float hoverScale = 1.15f;
        final float pressScale = 0.97f;
        final float popScale = 1.22f;

        card.setFocusable(true);
        card.setClickable(true);

        card.setOnHoverListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_HOVER_ENTER:
                    v.animate().cancel();
                    v.animate()
                            .scaleX(hoverScale).scaleY(hoverScale)
                            .translationZ(hoverLiftPx).setDuration(220)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(1.8f))
                            .start();
                    break;
                case android.view.MotionEvent.ACTION_HOVER_EXIT:
                    v.animate().cancel();
                    v.animate()
                            .scaleX(1f).scaleY(1f).translationZ(0f)
                            .setDuration(200)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .start();
                    break;
            }
            return false;
        });

        card.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().cancel();
            if (hasFocus) {
                v.animate()
                        .scaleX(hoverScale).scaleY(hoverScale)
                        .translationZ(hoverLiftPx).setDuration(220)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.8f))
                        .start();
            } else {
                v.animate()
                        .scaleX(1f).scaleY(1f).translationZ(0f)
                        .setDuration(200).start();
            }
        });

        card.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().cancel();
                    v.animate()
                            .scaleX(pressScale).scaleY(pressScale)
                            .setDuration(70)
                            .setInterpolator(new android.view.animation.AccelerateInterpolator())
                            .start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                    v.animate().cancel();
                    v.animate()
                            .scaleX(popScale).scaleY(popScale)
                            .translationZ(popLiftPx)
                            .setDuration(220)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(2.5f))
                            .withEndAction(() -> {
                                float endScale = v.isHovered() || v.isFocused() ? hoverScale : 1f;
                                float endLift = v.isHovered() || v.isFocused() ? hoverLiftPx : 0f;
                                v.animate()
                                        .scaleX(endScale).scaleY(endScale)
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
                            .scaleX(endScale).scaleY(endScale)
                            .translationZ(endLift).setDuration(180).start();
                    break;
            }
            return false;
        });
    }

    private int calculateColumnCount(int widthPixels) {
        int usableWidthPx;
        if (recyclerView != null && recyclerView.getWidth() > 0) {
            usableWidthPx = recyclerView.getWidth();
        } else {
            android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            int screenWidthPx = displayMetrics.widthPixels;
            int screenHeightPx = displayMetrics.heightPixels;
            usableWidthPx = Math.min(screenWidthPx, screenHeightPx);
        }

        int iconSizeDp = prefs.getInt("icon_size", 110);
        float density = getResources().getDisplayMetrics().density;

        // Same calculation as MainActivity - use FULL card footprint
        int iconWidthPx = (int) (iconSizeDp * density);
        int cardExtraSpacePx = (int) (24 * density);  // 16 padding + 8 margin
        int cellWidthPx = iconWidthPx + cardExtraSpacePx;

        int columns = usableWidthPx / cellWidthPx;
        columns = Math.max(1, columns);

        if (iconSizeDp <= 90) {
            columns = Math.min(columns, 15);
        } else if (iconSizeDp <= 110) {
            columns = Math.min(columns, 12);
        } else {
            columns = Math.min(columns, 10);
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
            ViewHolder vh = new ViewHolder(view);

            // Pre-size icon to current slider value
            int iconSizeDp = prefs.getInt("icon_size", 110);
            float density = parent.getResources().getDisplayMetrics().density;
            int iconWidthPx = (int) (iconSizeDp * density);
            int iconHeightPx = (int) (iconSizeDp * 0.5625f * density);
            ViewGroup.LayoutParams iconParams = vh.appIcon.getLayoutParams();
            iconParams.width = iconWidthPx;
            iconParams.height = iconHeightPx;
            vh.appIcon.setLayoutParams(iconParams);
            vh.appName.setMaxWidth(iconWidthPx);

            // VR polish: hover/focus/press animations
            applyCardInteractionEffects(vh.cardView);

            return vh;
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

            // Icon size ALWAYS from slider, ALWAYS landscape 16:9
            int iconSizeDp = prefs.getInt("icon_size", 110);
            float density = holder.itemView.getResources().getDisplayMetrics().density;
            int iconWidthPx = (int) (iconSizeDp * density);
            int iconHeightPx = (int) (iconSizeDp * 0.5625f * density);

            ViewGroup.LayoutParams iconParams = holder.appIcon.getLayoutParams();
            iconParams.width = iconWidthPx;
            iconParams.height = iconHeightPx;
            holder.appIcon.setLayoutParams(iconParams);
            holder.appIcon.requestLayout();

            // Lock app name max width to icon width - prevents card from stretching
            holder.appName.setMaxWidth(iconWidthPx);

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
            holder.cardView.setOnClickListener(v -> v.postDelayed(() -> launchApp(app), 240));

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
                // itemView is now the FrameLayout wrapper - find the CardView inside
                cardView = itemView.findViewById(R.id.cardFavorite);
                appIcon = itemView.findViewById(R.id.appIcon);
                appName = itemView.findViewById(R.id.appName);
                starButton = itemView.findViewById(R.id.btnStar);
            }
        }
    }
}