package com.jarjarblinkz.EvolveLauncher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import rikka.shizuku.Shizuku;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Manages Shizuku integration for shell-level command execution
 * Shizuku is embedded in the app - no external app installation needed!
 *
 * ONE-TIME SETUP REQUIRED:
 * User must enable wireless ADB once: adb tcpip 5555
 * Then start Shizuku server: adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
 *
 * After setup, commands work with shell privileges!
 */
public class ShizukuManager {
    private static final String TAG = "ShizukuManager";
    private static final int SHIZUKU_PERMISSION_REQUEST_CODE = 1001;

    private final Context context;
    private boolean isInitialized = false;
    private ShizukuStatusListener statusListener;

    // Shizuku status
    private boolean isShizukuAvailable = false;
    private boolean hasShizukuPermission = false;

    public interface ShizukuStatusListener {
        void onStatusChanged(boolean available, boolean hasPermission);
        void onCommandResult(boolean success, String output);
    }

    public ShizukuManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Initialize Shizuku - call this once on app startup
     */
    public void initialize(ShizukuStatusListener listener) {
        this.statusListener = listener;

        // Always register listeners FIRST so we get notified when Shizuku becomes available
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
            Shizuku.addBinderDeadListener(binderDeadListener);
            Shizuku.addRequestPermissionResultListener(permissionResultListener);
            Log.i(TAG, "Shizuku listeners registered");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register Shizuku listeners", e);
        }

        // Check initial state
        checkShizukuStatus();

        // Multiple retries - Shizuku binder might arrive after app starts
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.postDelayed(this::checkShizukuStatus, 1000);
        mainHandler.postDelayed(this::checkShizukuStatus, 3000);
        mainHandler.postDelayed(this::checkShizukuStatus, 5000);
        mainHandler.postDelayed(this::checkShizukuStatus, 10000);

        isInitialized = true;
    }

    /**
     * Check current Shizuku status
     */
    private void checkShizukuStatus() {
        try {
            boolean wasAvailable = isShizukuAvailable;
            isShizukuAvailable = Shizuku.pingBinder();

            if (isShizukuAvailable) {
                try {
                    hasShizukuPermission = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to check permission", e);
                    hasShizukuPermission = false;
                }
            } else {
                hasShizukuPermission = false;
            }

            if (wasAvailable != isShizukuAvailable) {
                Log.i(TAG, "Shizuku status changed - Available: " + isShizukuAvailable +
                        ", Permission: " + hasShizukuPermission);
            }

            notifyStatusChanged();

        } catch (Exception e) {
            Log.e(TAG, "Error checking Shizuku status", e);
            isShizukuAvailable = false;
            hasShizukuPermission = false;
            notifyStatusChanged();
        }
    }

    /**
     * Manually trigger a status check (useful for retry button)
     */
    public void recheckStatus() {
        checkShizukuStatus();
    }

    /**
     * Request Shizuku permission from user
     */
    public void requestPermission() {
        if (!isShizukuAvailable) {
            Log.w(TAG, "Cannot request permission - Shizuku not available");
            return;
        }

        if (hasShizukuPermission) {
            Log.i(TAG, "Already have Shizuku permission");
            return;
        }

        try {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE);
            Log.i(TAG, "Requested Shizuku permission");
        } catch (Exception e) {
            Log.e(TAG, "Failed to request Shizuku permission", e);
        }
    }

    /**
     * Execute a shell command with Shizuku privileges
     * @param command Shell command to execute (e.g., "pm clear com.oculus.vrshell")
     */
    public void executeShellCommand(String command) {
        if (!isShizukuAvailable) {
            notifyCommandResult(false, "Shizuku not available. Setup wireless ADB on Quest first.");
            return;
        }

        if (!hasShizukuPermission) {
            notifyCommandResult(false, "Shizuku permission not granted. Tap to grant permission.");
            requestPermission();
            return;
        }

        // Execute in background thread
        new Thread(() -> {
            try {
                Log.i(TAG, "Executing: " + command);

                // Execute command via Shizuku using reflection
                // (newProcess is package-private in Shizuku 13.x)
                Process process = newShizukuProcess(
                        new String[]{"sh", "-c", command},
                        null,
                        null
                );

                if (process == null) {
                    notifyCommandResult(false, "Failed to create Shizuku process");
                    return;
                }

                // Read output
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream())
                );
                BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream())
                );

                StringBuilder output = new StringBuilder();
                StringBuilder error = new StringBuilder();

                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                while ((line = errorReader.readLine()) != null) {
                    error.append(line).append("\n");
                }

                int exitCode = process.waitFor();

                String result = output.toString() + error.toString();
                boolean success = exitCode == 0;

                Log.i(TAG, String.format("Command completed - Exit: %d, Output: %s", exitCode, result));

                notifyCommandResult(success, result.isEmpty() ? "Command executed successfully" : result);

            } catch (Exception e) {
                Log.e(TAG, "Failed to execute command: " + command, e);
                notifyCommandResult(false, "Error: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Access Shizuku.newProcess() via reflection since it's package-private in 13.x
     */
    private Process newShizukuProcess(String[] cmd, String[] env, String dir) throws Exception {
        java.lang.reflect.Method newProcessMethod = Shizuku.class.getDeclaredMethod(
                "newProcess",
                String[].class,
                String[].class,
                String.class
        );
        newProcessMethod.setAccessible(true);
        return (Process) newProcessMethod.invoke(null, cmd, env, dir);
    }

    /**
     * Clear data for a package (like pm clear)
     */
    public void clearPackageData(String packageName) {
        executeShellCommand("pm clear " + packageName);
    }

    /**
     * Force stop a package (like am force-stop)
     */
    public void forceStopPackage(String packageName) {
        executeShellCommand("am force-stop " + packageName);
    }

    /**
     * Start an activity
     */
    public void startActivity(String packageName, String activityName) {
        executeShellCommand(String.format("am start -n %s/%s", packageName, activityName));
    }

    /**
     * Check if Shizuku is available and has permission
     */
    public boolean isReady() {
        return isShizukuAvailable && hasShizukuPermission;
    }

    /**
     * Get status message for UI
     */
    public String getStatusMessage() {
        if (!isShizukuAvailable) {
            return "⚠️ Shizuku not running - Setup wireless ADB on Quest";
        }
        if (!hasShizukuPermission) {
            return "🔓 Tap to grant Shizuku permission";
        }
        return "✅ Shizuku ready";
    }

    /**
     * Get setup instructions
     */
    public String getSetupInstructions() {
        return "QUEST SETUP - WIRELESS ADB:\n\n" +
                "1. Open Evolve Settings > SYSTEM\n" +
                "   • Tap 'Open' next to Native Settings\n" +
                "   • Android Settings will open\n\n" +
                "2. In Android Settings:\n" +
                "   • Navigate to 'About phone'\n" +
                "   • Tap 'Build number' 7 times\n" +
                "   • Developer Mode enabled!\n\n" +
                "3. Go to Developer options\n" +
                "   • Enable 'Wireless debugging'\n" +
                "   • Tap LEFT of 'Wireless debugging' text\n" +
                "   • Select 'Pair device with pairing code'\n" +
                "   • Note the IP, port, and pairing code\n\n" +
                "4. Open Shizuku app on Quest\n" +
                "   • Use the pairing code to connect\n" +
                "   • Start Shizuku server\n\n" +
                "5. Restart Evolve and grant permission\n\n" +
                "After setup, commands work with shell privileges!";
    }

    // ===== SHIZUKU LISTENERS =====

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = new Shizuku.OnBinderReceivedListener() {
        @Override
        public void onBinderReceived() {
            Log.i(TAG, "Shizuku binder received");
            isShizukuAvailable = true;
            hasShizukuPermission = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            notifyStatusChanged();
        }
    };

    private final Shizuku.OnBinderDeadListener binderDeadListener = new Shizuku.OnBinderDeadListener() {
        @Override
        public void onBinderDead() {
            Log.w(TAG, "Shizuku binder died");
            isShizukuAvailable = false;
            hasShizukuPermission = false;
            notifyStatusChanged();
        }
    };

    private final Shizuku.OnRequestPermissionResultListener permissionResultListener = new Shizuku.OnRequestPermissionResultListener() {
        @Override
        public void onRequestPermissionResult(int requestCode, int grantResult) {
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                hasShizukuPermission = grantResult == PackageManager.PERMISSION_GRANTED;
                Log.i(TAG, "Shizuku permission result: " + (hasShizukuPermission ? "GRANTED" : "DENIED"));
                notifyStatusChanged();
            }
        }
    };

    // ===== NOTIFICATION HELPERS =====

    private void notifyStatusChanged() {
        if (statusListener != null) {
            new Handler(Looper.getMainLooper()).post(() ->
                    statusListener.onStatusChanged(isShizukuAvailable, hasShizukuPermission)
            );
        }
    }

    private void notifyCommandResult(boolean success, String output) {
        if (statusListener != null) {
            new Handler(Looper.getMainLooper()).post(() ->
                    statusListener.onCommandResult(success, output)
            );
        }
    }

    /**
     * Cleanup when done
     */
    public void cleanup() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener);
            Shizuku.removeBinderDeadListener(binderDeadListener);
            Shizuku.removeRequestPermissionResultListener(permissionResultListener);
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup", e);
        }
    }
}