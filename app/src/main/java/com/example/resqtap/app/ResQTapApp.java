package com.example.resqtap.app;

import com.example.resqtap.notification.NotificationHelper;
import com.example.resqtap.notification.NotificationUtils;
import com.example.resqtap.notification.ResQTapMessagingService;
import com.example.resqtap.room.FirebaseRoomClient;
import com.example.resqtap.room.LiveRoomTrackingService;
import com.example.resqtap.utils.LocaleUtils;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;

import android.app.Application;
import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;


/**
 * ResQTapApp
 * Application class: setup notification channel, tema gelap/cerah, dan config awal app.
 */
public class ResQTapApp extends Application {
    private static ResQTapApp instance;

    public static ResQTapApp getInstance() {
        return instance;
    }

    @Override
    protected void attachBaseContext(Context base) {
        Context wrapped = LocaleUtils.wrap(base);
        super.attachBaseContext(wrapped == null ? base : wrapped);
    }

    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
}
