package com.example.resqtap.map;

import android.content.Context;
import android.location.LocationManager;


/**
 * GpsUtils
 * Utility check GPS hardware dan prompt user hidupkan location.
 */
public final class GpsUtils {
    private GpsUtils() {
    }

    /** Semak dan sahkan GpsEnabled. */
    public static boolean isGpsEnabled(Context context) {
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return false;
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ignored) {
            return false;
        }
    }
}

