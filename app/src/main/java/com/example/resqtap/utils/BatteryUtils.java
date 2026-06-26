package com.example.resqtap.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;


/**
 * BatteryUtils
 * Format alert bateri low (<20%) untuk notify ahli bilik.
 */
public final class BatteryUtils {
    private BatteryUtils() {}

    public static int getBatteryPct(Context ctx) {
        if (ctx == null) return -1;
        try {
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                int pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                if (pct >= 0 && pct <= 100) return pct;
            }
        } catch (Exception ignored) {
        }

        try {
            Intent sticky = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (sticky == null) return -1;
            int level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level < 0 || scale <= 0) return -1;
            int pct = Math.round((level * 100f) / scale);
            return (pct >= 0 && pct <= 100) ? pct : -1;
        } catch (Exception ignored) {
            return -1;
        }
    }
}

