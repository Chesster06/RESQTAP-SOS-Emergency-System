package com.example.resqtap.sos;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;


/**
 * VibrateManager
 * Controller getaran: handle vibration pattern waktu SOS aktif.
 */
public final class VibrateManager {
    private VibrateManager() {}

    private static final Object LOCK = new Object();
    private static volatile String activeAlertId = "";
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());
    private static volatile Runnable stopRunnable;
    private static volatile Vibrator activeVibrator;
    private static volatile VibratorManager activeVibratorManager;

    public static String getActiveAlertId() {
        return activeAlertId == null ? "" : activeAlertId;
    }

    public static void startEmergency10s(Context context, String alertId) {
        if (context == null) return;
        String id = String.valueOf(alertId == null ? "" : alertId).trim();
        if (id.isEmpty()) return;

        synchronized (LOCK) {
            activeAlertId = id;
        }

        try {
            cancelInternal(context);

            Vibrator v = getVibrator(context);
            if (v == null) return;
            try {
                if (!v.hasVibrator()) {
                    android.util.Log.d("SOS_DEBUG", "No vibrator available on this device");
                    return;
                }
            } catch (Exception ignored) {
            }

            activeVibrator = v;

            long[] pattern = new long[]{0, 1000, 250};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                v.vibrate(pattern, 0);
            }

            try {
                android.util.Log.d("SOS_DEBUG", "VibrateManager: continuous emergency vibration active for alertId=" + id);
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {
        }
    }

    public static void stop(Context context, String alertId) {
        if (context == null) return;
        String id = String.valueOf(alertId == null ? "" : alertId).trim();
        if (id.isEmpty()) return;

        synchronized (LOCK) {
            if (!id.equals(activeAlertId)) return;
            activeAlertId = "";
        }
        try {
            Runnable prev = stopRunnable;
            if (prev != null) MAIN.removeCallbacks(prev);
        } catch (Exception ignored) {
        }
        stopRunnable = null;
        try { SosAudioManager.stopAll(); } catch (Exception ignored) {}
        cancelInternal(context);
    }

    public static void stopAll(Context context) {
        synchronized (LOCK) {
            activeAlertId = "";
        }
        try {
            Runnable prev = stopRunnable;
            if (prev != null) MAIN.removeCallbacks(prev);
        } catch (Exception ignored) {
        }
        stopRunnable = null;
        try { SosAudioManager.stopAll(); } catch (Exception ignored) {}
        cancelInternal(context);
        try {
            android.util.Log.d("SOS_DEBUG", "VibrateManager.stopAll() called");
        } catch (Exception ignored) {
        }
    }

    private static void cancelInternal(Context context) {
        // 1. Cancel active tracked instances
        try {
            if (activeVibrator != null) {
                activeVibrator.cancel();
            }
        } catch (Exception ignored) {}
        try {
            if (Build.VERSION.SDK_INT >= 31 && activeVibratorManager != null) {
                activeVibratorManager.cancel();
            }
        } catch (Exception ignored) {}

        // 2. Cancel through passed context and applicationContext
        if (context != null) {
            cancelFromContext(context);
            Context appCtx = context.getApplicationContext();
            if (appCtx != null && appCtx != context) {
                cancelFromContext(appCtx);
            }
        }
        activeVibrator = null;
        activeVibratorManager = null;
    }

    private static void cancelFromContext(Context ctx) {
        if (ctx == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    try { vm.cancel(); } catch (Exception ignored) {}
                    try {
                        Vibrator defaultV = vm.getDefaultVibrator();
                        if (defaultV != null) defaultV.cancel();
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}

        try {
            Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.cancel();
            }
        } catch (Exception ignored) {}
    }

    private static Vibrator getVibrator(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                activeVibratorManager = vm;
                return vm == null ? null : vm.getDefaultVibrator();
            }
            return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        } catch (Exception e) {
            return null;
        }
    }
}
