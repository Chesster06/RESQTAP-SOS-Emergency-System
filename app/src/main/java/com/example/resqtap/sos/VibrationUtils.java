package com.example.resqtap.sos;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.os.Handler;
import android.os.Looper;


/**
 * VibrationUtils
 * Helper vibration feedback untuk butang dan Morse code SOS (... --- ...).
 */
public final class VibrationUtils {
    private VibrationUtils() {
    }

    public static void vibrateOnce(Context context, int durationMs) {
        if (context == null) return;
        int ms = Math.max(1, durationMs);
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                Vibrator v = vm == null ? null : vm.getDefaultVibrator();
                if (v == null) return;
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (v == null) return;
                if (Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(ms);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static void vibrateSosAlert(Context context, long totalMs) {
        if (context == null) return;
        long ms = Math.max(250L, totalMs);
        try {
            final Vibrator v;
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                v = vm == null ? null : vm.getDefaultVibrator();
            } else {
                v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (v == null) return;

            long[] pattern = new long[]{0L, 800L, 250L};
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                v.vibrate(pattern, 0);
            }

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    v.cancel();
                } catch (Exception ignored) {
                }
            }, ms);
        } catch (Exception ignored) {
        }
    }

    public static void cancelAll(Context context) {
        if (context == null) return;
        try {
            final Vibrator v;
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                v = vm == null ? null : vm.getDefaultVibrator();
            } else {
                v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (v == null) return;
            v.cancel();
        } catch (Exception ignored) {
        }
    }
}
