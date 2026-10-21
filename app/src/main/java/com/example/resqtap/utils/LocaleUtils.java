package com.example.resqtap.utils;

import com.example.resqtap.app.SplashActivity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.LocaleList;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;


/**
 * LocaleUtils
 * Helper switch bahasa app masa runtime.
 */
public final class LocaleUtils {
    private LocaleUtils() {
    }

    public static String getSavedLanguageTag(Context context) {
        String tag = String.valueOf(UserPrefs.getLanguage(context) == null ? "" : UserPrefs.getLanguage(context)).trim();
        if (tag.isEmpty()) return "en";
        tag = tag.toLowerCase(java.util.Locale.ROOT);
        if (tag.startsWith("ms")) return "ms";
        if (tag.startsWith("zh")) return "zh";
        if (tag.startsWith("ta")) return "ta";
        if (tag.startsWith("en")) return "en";
        return "en";
    }

    public static Context wrap(Context context) {
        if (context == null) return null;
        String tag = getSavedLanguageTag(context);
        Locale locale = localeForTag(tag);
        applyDefaultLocale(locale);

        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        config.setLocales(new LocaleList(locale));
        config.setLayoutDirection(locale);
        return context.createConfigurationContext(config);
    }

    public static void applySavedLocale(Context context) {
        if (context == null) return;
        try {
            String tag = getSavedLanguageTag(context);
            applyDefaultLocale(localeForTag(tag));
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
        } catch (Exception ignored) {
        }
    }

    public static void setLocaleAndPersist(Context context, String tag) {
        if (context == null) return;
        String normalized = String.valueOf(tag == null ? "" : tag).trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("ms")) normalized = "ms";
        else if (normalized.startsWith("zh")) normalized = "zh";
        else if (normalized.startsWith("ta")) normalized = "ta";
        else normalized = "en";
        try {
            UserPrefs.setLanguage(context, normalized);
        } catch (Exception ignored) {
        }
        try {
            applyDefaultLocale(localeForTag(normalized));
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(normalized));
        } catch (Exception ignored) {
        }
    }

    public static void restartApp(Activity activity) {
        if (activity == null) return;
        try {
            Intent i = new Intent(activity, SplashActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(i);
            activity.finish();
        } catch (Exception ignored) {
        }
    }

    private static Locale localeForTag(String tag) {
        String normalized = String.valueOf(tag == null ? "" : tag).trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("ms")) return new Locale("ms");
        if (normalized.startsWith("zh")) return Locale.CHINESE;
        if (normalized.startsWith("ta")) return new Locale("ta");
        return Locale.ENGLISH;
    }

    private static void applyDefaultLocale(Locale locale) {
        if (locale == null) return;
        Locale.setDefault(locale);
        LocaleList.setDefault(new LocaleList(locale));
    }
}
