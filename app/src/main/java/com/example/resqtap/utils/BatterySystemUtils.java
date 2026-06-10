package com.example.resqtap.utils;

import android.content.Context;
import android.os.PowerManager;


/**
 * BatterySystemUtils
 * Check percentage bateri phone dan status charging.
 */
public class BatterySystemUtils {
    /** Semak status SystemBatterySaverEnabled. */
    public static boolean isSystemBatterySaverEnabled(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm == null) return false;
        return pm.isPowerSaveMode();
    }
}