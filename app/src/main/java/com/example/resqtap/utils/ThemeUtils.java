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

    /** Semak dan sahkan NightMode. */
    public static boolean isNightMode(Context context) {
        if (context == null) return false;
        int mode = UserPrefs.getNightMode(context);
        if (mode == AppCompatDelegate.MODE_NIGHT_YES) return true;
        if (mode == AppCompatDelegate.MODE_NIGHT_NO) return false;
        int nightModeFlags = context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }
}
