package com.example.resqtap.friend;

import android.app.Activity;
import android.content.Intent;


/**
 * FriendNavigationHelper
 * Helper navigasi antara menu kawan, scan QR, dan list request.
 */
public final class FriendNavigationHelper {
    private FriendNavigationHelper() {}

    public static void navigate(Activity from, Class<?> to) {
        if (from == null || to == null || from.getClass().equals(to)) return;
        Intent intent = new Intent(from, to);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        from.startActivity(intent);
        from.overridePendingTransition(0, 0);
        from.finish();
        from.overridePendingTransition(0, 0);
    }
}
