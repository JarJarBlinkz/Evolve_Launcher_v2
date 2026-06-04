package com.jarjarblinkz.EvolveLauncher;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.jarjarblinkz.EvolveLauncher.theme.Theme;
import com.jarjarblinkz.EvolveLauncher.theme.ThemeApplier;
import com.jarjarblinkz.EvolveLauncher.theme.ThemeManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity that displays all bundled APKs with install/launch buttons
 */
public class BundledAppsActivity extends AppCompatActivity {

    private BundledAppsManager bundledAppsManager;
    private BundledAppsAdapter adapter;
    private List<BundledAppsManager.BundledApp> bundledApps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide the activity title bar that shows "VR Launcher"
        try {
            supportRequestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        } catch (Exception e) {
            if (getSupportActionBar() != null) {
                getSupportActionBar().hide();
            }
        }

        setContentView(R.layout.activity_bundled_apps);

        bundledAppsManager = new BundledAppsManager(this);

        // Setup back button (Close)
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // Setup RecyclerView with 3-column grid
        RecyclerView recyclerView = findViewById(R.id.bundledAppsList);
        recyclerView.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 3));

        adapter = new BundledAppsAdapter(bundledApps);
        recyclerView.setAdapter(adapter);

        loadBundledApps();

        // Apply theme to entire activity
        View rootView = findViewById(android.R.id.content);
        ThemeApplier.applyThemeToHierarchy(rootView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBundledApps();
        // Re-apply theme on resume
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            ThemeApplier.applyThemeToHierarchy(rootView);
        }
    }

    private void loadBundledApps() {
        bundledApps.clear();
        bundledApps.addAll(bundledAppsManager.getBundledApps());
        adapter.notifyDataSetChanged();

        // Show/hide empty state
        View emptyState = findViewById(R.id.emptyState);
        RecyclerView list = findViewById(R.id.bundledAppsList);

        if (bundledApps.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            list.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            list.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Adapter for bundled apps list
     */
    private class BundledAppsAdapter extends RecyclerView.Adapter<BundledAppsAdapter.ViewHolder> {

        private final List<BundledAppsManager.BundledApp> apps;

        public BundledAppsAdapter(List<BundledAppsManager.BundledApp> apps) {
            this.apps = apps;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_bundled_app, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            BundledAppsManager.BundledApp app = apps.get(position);

            // Apply theme to this card
            Theme theme = ThemeManager.getInstance(BundledAppsActivity.this).getCurrentTheme();
            if (holder.itemView instanceof androidx.cardview.widget.CardView) {
                ((androidx.cardview.widget.CardView) holder.itemView).setCardBackgroundColor(theme.bgSecondary);
            }
            holder.label.setTextColor(theme.textPrimary);
            holder.packageName.setTextColor(theme.textMuted);
            holder.version.setTextColor(theme.accentPrimary);

            // Set icon
            if (app.icon != null) {
                holder.icon.setImageDrawable(app.icon);
            } else {
                holder.icon.setImageResource(android.R.drawable.sym_def_app_icon);
            }

            // Set text
            holder.label.setText(app.label);
            holder.packageName.setText(app.packageName);

            if (app.isInstalled) {
                holder.version.setText("Bundled: v" + app.versionName + " • Installed: v" + app.installedVersion);
                holder.actionButton.setText("Launch");
                holder.actionButton.setBackgroundTintList(
                        getResources().getColorStateList(android.R.color.holo_green_dark, null));
                holder.actionButton.setOnClickListener(v -> {
                    boolean launched = bundledAppsManager.launchApp(app.packageName);
                    if (!launched) {
                        Toast.makeText(BundledAppsActivity.this,
                                "Cannot launch this app", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                holder.version.setText("Version " + app.versionName);
                holder.actionButton.setText("Install");
                holder.actionButton.setBackgroundTintList(
                        getResources().getColorStateList(android.R.color.holo_blue_dark, null));
                holder.actionButton.setOnClickListener(v -> {
                    Toast.makeText(BundledAppsActivity.this,
                            "Opening installer...", Toast.LENGTH_SHORT).show();
                    boolean started = bundledAppsManager.installApk(app.filename);
                    if (!started) {
                        Toast.makeText(BundledAppsActivity.this,
                                "❌ Failed to start installer", Toast.LENGTH_LONG).show();
                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView label;
            TextView packageName;
            TextView version;
            Button actionButton;

            ViewHolder(View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.appIcon);
                label = itemView.findViewById(R.id.appLabel);
                packageName = itemView.findViewById(R.id.appPackage);
                version = itemView.findViewById(R.id.appVersion);
                actionButton = itemView.findViewById(R.id.btnAction);
            }
        }
    }
}