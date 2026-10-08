package com.example.resqtap.utils;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;


/**
 * ThemeUtils
 * Helper switch tema Light / Dark / System mode.
 */
public final class ThemeUtils {
    private ThemeUtils() {
    }

    public static void applySavedNightMode(Context context) {
        int mode = context == null
                ? AppCompatDelegate.MODE_NIGHT_NO
                : UserPrefs.getNightMode(context);
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
