package com.example.resqtap.utils;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;


/**
 * PermissionUtils
 * Helper check & request runtime permissions (Location, Camera, Mic, SMS).
 */
public final class PermissionUtils {
    private PermissionUtils() {
    }

    /** Semak dan sahkan FineLocation. */
    public static boolean hasFineLocation(Context context) {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Semak dan sahkan CoarseLocation. */
    public static boolean hasCoarseLocation(Context context) {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Semak dan sahkan AnyLocation. */
    public static boolean hasAnyLocation(Context context) {
        return hasFineLocation(context) || hasCoarseLocation(context);
    }
}
