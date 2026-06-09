package com.example.resqtap.utils;
import com.example.resqtap.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;


/**
 * BatteryOptimizationHelper
 * Helper request ignore battery optimization supaya GPS background tak kena sleep.
 */
public final class BatteryOptimizationHelper {
    private BatteryOptimizationHelper() {}

    /** Semak dan sahkan IgnoringBatteryOptimizations. */
    public static boolean isIgnoringBatteryOptimizations(Context context) {
        if (context == null) return true;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm == null) return true;
            return pm.isIgnoringBatteryOptimizations(context.getPackageName());
        } catch (Exception e) {
            return true;
        }
    }

    public static void promptOnce(Activity activity) {
        if (activity == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            if (UserPrefs.isBatteryOptPrompted(activity)) return;
            if (isIgnoringBatteryOptimizations(activity)) {
                UserPrefs.setBatteryOptPrompted(activity, true);
                return;
            }
        } catch (Exception ignored) {
        }

        try {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.battery_optimization_title)
                    .setMessage(R.string.battery_optimization_message)
                    .setPositiveButton(R.string.battery_optimization_disable, (d, w) -> {
                        try {
                            UserPrefs.setBatteryOptPrompted(activity, true);
                        } catch (Exception ignored) {
                        }
}
