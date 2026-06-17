package com.example.resqtap.utils;

import android.content.Context;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;


/**
 * DeviceIdUtils
 * Utility generate ID unik peranti.
 */
public final class DeviceIdUtils {
    private DeviceIdUtils() {}

    public static String getStableUid(Context context) {

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getUid() != null && !user.getUid().trim().isEmpty()) {
            UserPrefs.setUid(context, user.getUid());
            return user.getUid();
        }

        return UserPrefs.getOrCreateUid(context);
    }
}
