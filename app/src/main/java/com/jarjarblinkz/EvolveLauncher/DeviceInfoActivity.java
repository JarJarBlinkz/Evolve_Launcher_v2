package com.jarjarblinkz.EvolveLauncher;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.text.format.Formatter;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

public class DeviceInfoActivity extends AppCompatActivity {

    private TextView txtInternalStorage, txtInternalUsed, txtInternalFree, txtInternalTotal;
    private ProgressBar internalStorageProgress;

    private TextView txtDeviceModel, txtManufacturer, txtAndroidVersion, txtApiLevel, txtBuildNumber;
    private TextView txtTotalRAM, txtAvailableRAM, txtRAMUsed, txtRAMFree, txtRAMTotal;
    private ProgressBar ramProgress;
    private TextView txtProcessor, txtCpuCores, txtResolution, txtDensity;
    private TextView txtDeviceIP, txtMacAddress, txtWifiSSID, txtSignalStrength, txtLinkSpeed;
    private TextView txtBatteryLevel, txtBatteryStatus, txtBatteryTemp, txtBatteryHealth, txtBatteryVoltage, txtBatteryTech;
    private TextView txtAppVersion, txtPackageName;
    private AppCompatButton btnBack;

    private WifiManager wifiManager;
    private ConnectivityManager connectivityManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_device_info);

        // Set dialog size
        Window window = getWindow();
        android.view.WindowManager.LayoutParams lp = window.getAttributes();
        lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.5);
        lp.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.95);
        window.setAttributes(lp);

        initViews();
        setupClickListeners();
        loadAllDeviceInfo();
    }

    private void initViews() {
        // Storage views - Internal only
        txtInternalStorage = findViewById(R.id.txtInternalStorage);
        txtInternalUsed = findViewById(R.id.txtInternalUsed);
        txtInternalFree = findViewById(R.id.txtInternalFree);
        txtInternalTotal = findViewById(R.id.txtInternalTotal);
        internalStorageProgress = findViewById(R.id.internalStorageProgress);

        // System views
        txtDeviceModel = findViewById(R.id.txtDeviceModel);
        txtManufacturer = findViewById(R.id.txtManufacturer);
        txtAndroidVersion = findViewById(R.id.txtAndroidVersion);
        txtApiLevel = findViewById(R.id.txtApiLevel);
        txtBuildNumber = findViewById(R.id.txtBuildNumber);

        // Hardware views
        txtTotalRAM = findViewById(R.id.txtTotalRAM);
        txtAvailableRAM = findViewById(R.id.txtAvailableRAM);
        txtRAMUsed = findViewById(R.id.txtRAMUsed);
        txtRAMFree = findViewById(R.id.txtRAMFree);
        txtRAMTotal = findViewById(R.id.txtRAMTotal);
        ramProgress = findViewById(R.id.ramProgress);
        txtProcessor = findViewById(R.id.txtProcessor);
        txtCpuCores = findViewById(R.id.txtCpuCores);
        txtResolution = findViewById(R.id.txtResolution);
        txtDensity = findViewById(R.id.txtDensity);

        // Network views
        txtDeviceIP = findViewById(R.id.txtDeviceIP);
        txtMacAddress = findViewById(R.id.txtMacAddress);
        txtWifiSSID = findViewById(R.id.txtWifiSSID);
        txtSignalStrength = findViewById(R.id.txtSignalStrength);
        txtLinkSpeed = findViewById(R.id.txtLinkSpeed);

        // Battery views
        txtBatteryLevel = findViewById(R.id.txtBatteryLevel);
        txtBatteryStatus = findViewById(R.id.txtBatteryStatus);
        txtBatteryTemp = findViewById(R.id.txtBatteryTemp);
        txtBatteryHealth = findViewById(R.id.txtBatteryHealth);
        txtBatteryVoltage = findViewById(R.id.txtBatteryVoltage);
        txtBatteryTech = findViewById(R.id.txtBatteryTech);

        // App views
        txtAppVersion = findViewById(R.id.txtAppVersion);
        txtPackageName = findViewById(R.id.txtPackageName);
        btnBack = findViewById(R.id.btnBack);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());
    }

    private void loadAllDeviceInfo() {
        loadStorageInfo();
        loadSystemInfo();
        loadHardwareInfo();
        loadNetworkInfo();
        loadBatteryInfo();
        loadAppInfo();
    }

    private void loadStorageInfo() {
        try {
            // Internal Storage
            StatFs internalStat = new StatFs(Environment.getExternalStorageDirectory().getPath());
            long blockSize = internalStat.getBlockSizeLong();
            long totalBlocks = internalStat.getBlockCountLong();
            long availableBlocks = internalStat.getAvailableBlocksLong();

            long totalSize = totalBlocks * blockSize;
            long freeSize = availableBlocks * blockSize;
            long usedSize = totalSize - freeSize;

            String total = Formatter.formatShortFileSize(this, totalSize);
            String used = Formatter.formatShortFileSize(this, usedSize);
            String free = Formatter.formatShortFileSize(this, freeSize);
            int percentUsed = (int) ((usedSize * 100) / totalSize);

            txtInternalStorage.setText(used + " / " + total + " (" + percentUsed + "%)");
            txtInternalUsed.setText("Used: " + used);
            txtInternalFree.setText("Free: " + free);
            txtInternalTotal.setText("Total: " + total);
            internalStorageProgress.setProgress(percentUsed);

        } catch (Exception e) {
            txtInternalStorage.setText("Unable to read storage info");
        }
    }

    private void loadSystemInfo() {
        txtDeviceModel.setText(Build.MODEL);
        txtManufacturer.setText(Build.MANUFACTURER);
        txtAndroidVersion.setText(Build.VERSION.RELEASE + " (" + getAndroidVersionName(Build.VERSION.SDK_INT) + ")");
        txtApiLevel.setText(String.valueOf(Build.VERSION.SDK_INT));
        txtBuildNumber.setText(Build.DISPLAY);
    }

    private String getAndroidVersionName(int apiLevel) {
        switch (apiLevel) {
            case Build.VERSION_CODES.Q: return "10";
            case Build.VERSION_CODES.R: return "11";
            case Build.VERSION_CODES.S: return "12";
            case Build.VERSION_CODES.S_V2: return "12L";
            case Build.VERSION_CODES.TIRAMISU: return "13";
            case Build.VERSION_CODES.UPSIDE_DOWN_CAKE: return "14";
            default: return "Unknown";
        }
    }

    private void loadHardwareInfo() {
        // RAM Info
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(mi);

        long totalRam = mi.totalMem;
        long availableRam = mi.availMem;
        long usedRam = totalRam - availableRam;
        int percentRamUsed = (int) ((usedRam * 100) / totalRam);

        String totalRamStr = Formatter.formatShortFileSize(this, totalRam);
        String availableRamStr = Formatter.formatShortFileSize(this, availableRam);
        String usedRamStr = Formatter.formatShortFileSize(this, usedRam);

        txtTotalRAM.setText(totalRamStr);
        txtAvailableRAM.setText(availableRamStr);
        txtRAMUsed.setText("Used: " + usedRamStr);
        txtRAMFree.setText("Free: " + availableRamStr);
        txtRAMTotal.setText("Total: " + totalRamStr);
        ramProgress.setProgress(percentRamUsed);

        // CPU Info
        txtProcessor.setText(getCPUName());
        txtCpuCores.setText(String.valueOf(Runtime.getRuntime().availableProcessors()));

        // Screen Info
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;
        float density = displayMetrics.density;
        int densityDpi = displayMetrics.densityDpi;

        txtResolution.setText(width + " x " + height + " px");
        txtDensity.setText(String.format(Locale.getDefault(), "%.2f (%d dpi)", density, densityDpi));
    }

    private String getCPUName() {
        try {
            BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("Processor") || line.contains("Hardware") || line.contains("model name")) {
                    String[] parts = line.split(":");
                    if (parts.length > 1) {
                        br.close();
                        return parts[1].trim();
                    }
                }
            }
            br.close();
        } catch (IOException e) {
            // Ignore
        }
        return Build.HARDWARE;
    }

    private void loadNetworkInfo() {
        try {
            // IP Address
            String ipAddress = getDeviceIPAddress();
            txtDeviceIP.setText(ipAddress != null ? ipAddress : "Not connected");

            // MAC Address
            txtMacAddress.setText(getMacAddress());

            // Wi-Fi Info
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                String ssid = wifiInfo.getSSID();
                if (ssid.equals("<unknown ssid>") || ssid.equals("0x")) {
                    ssid = "Hidden Network";
                } else {
                    ssid = ssid.replace("\"", "");
                }
                txtWifiSSID.setText(ssid);

                int rssi = wifiInfo.getRssi();
                int level = WifiManager.calculateSignalLevel(rssi, 5);
                String signalText;
                switch (level) {
                    case 0: signalText = "Very Weak (" + rssi + " dBm)"; break;
                    case 1: signalText = "Weak (" + rssi + " dBm)"; break;
                    case 2: signalText = "Good (" + rssi + " dBm)"; break;
                    case 3: signalText = "Strong (" + rssi + " dBm)"; break;
                    case 4: signalText = "Excellent (" + rssi + " dBm)"; break;
                    default: signalText = level + "/4 (" + rssi + " dBm)";
                }
                txtSignalStrength.setText(signalText);

                int speed = wifiInfo.getLinkSpeed();
                txtLinkSpeed.setText(speed + " Mbps");
            } else {
                txtWifiSSID.setText("Wi-Fi disabled");
                txtSignalStrength.setText("N/A");
                txtLinkSpeed.setText("N/A");
            }
        } catch (Exception e) {
            txtDeviceIP.setText("Error reading network info");
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

            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            if (activeNetwork != null && activeNetwork.isConnected()) {
                for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                    NetworkInterface intf = en.nextElement();
                    for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                        InetAddress inetAddress = enumIpAddr.nextElement();
                        if (!inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress()) {
                            return inetAddress.getHostAddress();
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private String getMacAddress() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

                byte[] macBytes = nif.getHardwareAddress();
                if (macBytes == null) return "";

                StringBuilder res1 = new StringBuilder();
                for (byte b : macBytes) {
                    res1.append(String.format("%02X:", b));
                }

                if (res1.length() > 0) {
                    res1.deleteCharAt(res1.length() - 1);
                }
                return res1.toString();
            }
        } catch (Exception ex) {
            // Ignore
        }
        return Settings.Secure.getString(getContentResolver(), "android_id");
    }

    private void loadBatteryInfo() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);

        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
            int temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            int voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            String technology = batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);

            float batteryPercent = (level / (float)scale) * 100;
            int roundedPercent = Math.round(batteryPercent);

            txtBatteryLevel.setText(roundedPercent + "%");

            // Status
            String statusText;
            switch (status) {
                case BatteryManager.BATTERY_STATUS_CHARGING: statusText = "Charging"; break;
                case BatteryManager.BATTERY_STATUS_DISCHARGING: statusText = "Discharging"; break;
                case BatteryManager.BATTERY_STATUS_FULL: statusText = "Full"; break;
                case BatteryManager.BATTERY_STATUS_NOT_CHARGING: statusText = "Not Charging"; break;
                default: statusText = "Unknown";
            }
            txtBatteryStatus.setText(statusText);

            // Health
            String healthText;
            switch (health) {
                case BatteryManager.BATTERY_HEALTH_GOOD: healthText = "Good"; break;
                case BatteryManager.BATTERY_HEALTH_OVERHEAT: healthText = "Overheat"; break;
                case BatteryManager.BATTERY_HEALTH_DEAD: healthText = "Dead"; break;
                case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: healthText = "Over Voltage"; break;
                case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: healthText = "Failure"; break;
                case BatteryManager.BATTERY_HEALTH_COLD: healthText = "Cold"; break;
                default: healthText = "Unknown";
            }
            txtBatteryHealth.setText(healthText);

            // Temperature (convert from tenths of degree)
            float tempCelsius = temperature / 10.0f;
            float tempFahrenheit = (tempCelsius * 9/5) + 32;
            txtBatteryTemp.setText(String.format(Locale.getDefault(), "%.1f°C / %.1f°F", tempCelsius, tempFahrenheit));

            // Voltage (convert from mV to V)
            float voltageVolts = voltage / 1000.0f;
            txtBatteryVoltage.setText(String.format(Locale.getDefault(), "%.2f V", voltageVolts));

            txtBatteryTech.setText(technology != null ? technology : "Unknown");
        }
    }

    private void loadAppInfo() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionName = pInfo.versionName;
            int versionCode = pInfo.versionCode;
            txtAppVersion.setText(versionName + " (" + versionCode + ")");
            txtPackageName.setText(getPackageName());
        } catch (PackageManager.NameNotFoundException e) {
            txtAppVersion.setText("Unknown");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh data when returning to the activity
        loadAllDeviceInfo();
    }
}