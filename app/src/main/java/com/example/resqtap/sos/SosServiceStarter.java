package com.example.resqtap.sos;

import com.example.resqtap.room.LiveRoomTrackingService;
import com.example.resqtap.utils.UserPrefs;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;


/**
 * SosServiceStarter
 * Helper untuk start background service bila SOS jalan.
 */
public final class SosServiceStarter {
    private SosServiceStarter() {}

    public static void start(Context context, String roomId) {
        if (context == null) return;
        String code = String.valueOf(roomId == null ? "" : roomId).trim().toUpperCase(java.util.Locale.ROOT);

        FirebaseUser u = null;
        try { u = FirebaseAuth.getInstance().getCurrentUser(); } catch (Exception ignored) {}
        if (u == null) return;

        String uid = String.valueOf(u.getUid() == null ? "" : u.getUid()).trim();
        if (uid.isEmpty()) return;

        String name = UserPrefs.getName(context);
        if (name == null || name.trim().isEmpty()) name = "User";
        String photoUrl = String.valueOf(UserPrefs.getPhotoUrl(context) == null ? "" : UserPrefs.getPhotoUrl(context)).trim();
        String photoB64 = String.valueOf(UserPrefs.getPhotoB64(context) == null ? "" : UserPrefs.getPhotoB64(context)).trim();

        Intent i = new Intent(context, LiveRoomTrackingService.class);
        i.setAction(LiveRoomTrackingService.ACTION_START);
        i.putExtra(LiveRoomTrackingService.EXTRA_ROOM_CODE, code);
        i.putExtra(LiveRoomTrackingService.EXTRA_UID, uid);
        i.putExtra(LiveRoomTrackingService.EXTRA_NAME, name);
        i.putExtra(LiveRoomTrackingService.EXTRA_PHOTO_URL, photoUrl);
        i.putExtra(LiveRoomTrackingService.EXTRA_PHOTO_B64, photoB64);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(i);
            else context.startService(i);
        } catch (Exception ignored) {
        }
}
