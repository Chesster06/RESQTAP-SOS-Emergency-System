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
}
