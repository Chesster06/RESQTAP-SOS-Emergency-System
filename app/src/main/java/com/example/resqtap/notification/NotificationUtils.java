package com.example.resqtap.notification;
import com.example.resqtap.R;

import com.example.resqtap.room.RoomMapActivity;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;


/**
 * NotificationUtils
 * Helper untuk trigger popup notifikasi dengan custom alert sound.
 */
public final class NotificationUtils {
    private NotificationUtils() {
    }

    public static final String CHANNEL_ID_BELL = "bell_alerts_v4_custom";
    public static final String CHANNEL_ID_ADMIN = "admin_notifications_v1";

    public static void ensureBellChannel(Context context) {
        if (context == null) return;
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel existing = nm.getNotificationChannel(CHANNEL_ID_BELL);

        try {
            Uri want = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.notify_sound);
            Uri have = existing == null ? null : existing.getSound();
            if (existing != null && (have == null || !want.equals(have))) {
                nm.deleteNotificationChannel(CHANNEL_ID_BELL);
                existing = null;
            }
        } catch (Exception ignored) {
        }
        if (existing != null) return;

        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID_BELL,
                "Bell Alerts",
                NotificationManager.IMPORTANCE_HIGH
        );
        ch.setDescription("Bell notifications with sound and vibration.");
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 180, 120, 220});

        Uri sound = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.notify_sound);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        ch.setSound(sound, attrs);
        nm.createNotificationChannel(ch);
    }

    public static void notifyBell(Context context, String roomCode, String fromName) {
        if (context == null) return;
        ensureBellChannel(context);

        String safeName = fromName == null ? "" : fromName.trim();
        if ("null".equalsIgnoreCase(safeName)) safeName = "";

        if (safeName.isEmpty()) return;

        String title = "ROOM";
        String text = safeName + " pinged you.";

        Intent open = new Intent(context, RoomMapActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (roomCode != null && !roomCode.trim().isEmpty()) {
            open.putExtra(RoomMapActivity.EXTRA_ROOM_CODE, roomCode.trim());
        }
        PendingIntent openPi = PendingIntent.getActivity(
                context,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID_BELL)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

                .setDefaults(NotificationCompat.DEFAULT_LIGHTS | NotificationCompat.DEFAULT_VIBRATE)
                .setContentIntent(openPi);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify((int) (System.currentTimeMillis() % Integer.MAX_VALUE), b.build());
        }
    }

    public static void ensureAdminChannel(Context context) {
        if (context == null) return;
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel existing = nm.getNotificationChannel(CHANNEL_ID_ADMIN);
        if (existing != null) return;

        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID_ADMIN,
                "Admin Notifications",
                NotificationManager.IMPORTANCE_HIGH
        );
        ch.setDescription("Announcements sent by ResQTap administrators.");
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 160, 100, 180});
        nm.createNotificationChannel(ch);
    }

    public static void notifyAdmin(Context context, String notificationId, String title, String body) {
        if (context == null) return;
        ensureAdminChannel(context);

        String safeTitle = title == null || title.trim().isEmpty() ? "ResQTap" : title.trim();
        String safeBody = body == null ? "" : body.trim();

        Intent open = new Intent(context, NotificationsActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(
                context,
                300,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID_ADMIN)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(safeTitle)
                .setContentText(safeBody)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(safeBody))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_LIGHTS | NotificationCompat.DEFAULT_VIBRATE)
                .setContentIntent(openPi);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            String safeId = notificationId == null ? "" : notificationId.trim();
            int id = safeId.isEmpty()
                    ? (int) (System.currentTimeMillis() % Integer.MAX_VALUE)
                    : 30000 + (safeId.hashCode() & 0x7fffffff);
            nm.notify(id, b.build());
        }
    }

    public static void notifySosAlert(Context context, String roomCode, String fromName) {
        if (context == null) return;
        ensureBellChannel(context);

        String safeName = fromName == null ? "" : fromName.trim();
        if ("null".equalsIgnoreCase(safeName)) safeName = "";

        String title = "SOS ALERT";
        String text = safeName.isEmpty()
                ? "Someone triggered SOS in your room."
                : (safeName + " triggered SOS in your room.");

        Intent open = new Intent(context, RoomMapActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (roomCode != null && !roomCode.trim().isEmpty()) {
            open.putExtra(RoomMapActivity.EXTRA_ROOM_CODE, roomCode.trim());
        }
        PendingIntent openPi = PendingIntent.getActivity(
                context,
                1,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID_BELL)
                .setSmallIcon(R.drawable.ic_error_24)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

                .setDefaults(NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_LIGHTS)
                .setContentIntent(openPi);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify((int) (System.currentTimeMillis() % Integer.MAX_VALUE), b.build());
        }
    }

    /** Kendalikan animasi NotificationSound. */
    public static void playNotificationSound(Context context) {
        if (context == null) return;
        try {
            Uri soundUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.notify_sound);
            android.media.Ringtone r = android.media.RingtoneManager.getRingtone(context.getApplicationContext(), soundUri);
            if (r != null) {
                r.play();
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            Uri defaultUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION);
            android.media.Ringtone r = android.media.RingtoneManager.getRingtone(context.getApplicationContext(), defaultUri);
            if (r != null) r.play();
        } catch (Exception ignored) {
        }
    }

    public static void notifyRoomCreated(Context context, String roomCode) {
        if (context == null) return;
        try {
            com.example.resqtap.utils.UserPrefs.incrementUnreadNotifications(context);
        } catch (Exception ignored) {}

        playNotificationSound(context);

        ensureBellChannel(context);
        String code = roomCode == null ? "" : roomCode.trim();
        String title = context.getString(R.string.notif_room_created_title);
        String text = context.getString(R.string.notif_room_created_msg, code);

        Intent open = new Intent(context, NotificationsActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(
                context,
                400,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID_BELL)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setSound(Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.notify_sound))
                .setDefaults(NotificationCompat.DEFAULT_LIGHTS | NotificationCompat.DEFAULT_VIBRATE)
                .setContentIntent(openPi);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify((int) (System.currentTimeMillis() % Integer.MAX_VALUE), b.build());
        }
    }

    public static void notifyRoomJoined(Context context, String roomCode) {
        if (context == null) return;
        try {
            com.example.resqtap.utils.UserPrefs.incrementUnreadNotifications(context);
        } catch (Exception ignored) {}

        playNotificationSound(context);

        ensureBellChannel(context);
        String code = roomCode == null ? "" : roomCode.trim();
        String title = context.getString(R.string.notif_room_joined_title);
        String text = context.getString(R.string.notif_room_joined_msg, code);

        Intent open = new Intent(context, NotificationsActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(
                context,
                401,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID_BELL)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setSound(Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.notify_sound))
                .setDefaults(NotificationCompat.DEFAULT_LIGHTS | NotificationCompat.DEFAULT_VIBRATE)
                .setContentIntent(openPi);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify((int) (System.currentTimeMillis() % Integer.MAX_VALUE), b.build());
        }
    }

    public static void notifyRoomDeleted(Context context, String roomCode, boolean isCreator) {
        if (context == null) return;
        try {
            com.example.resqtap.utils.UserPrefs.incrementUnreadNotifications(context);
        } catch (Exception ignored) {}

        playNotificationSound(context);

        ensureBellChannel(context);
        String code = roomCode == null ? "" : roomCode.trim();
        String title = isCreator ? context.getString(R.string.notif_room_deleted_title) : context.getString(R.string.notif_room_left_title);
        String text = isCreator
                ? context.getString(R.string.notif_room_deleted_msg, code)
                : context.getString(R.string.notif_room_left_msg, code);

        Intent open = new Intent(context, NotificationsActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(
                context,
                402,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID_BELL)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setSound(Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.notify_sound))
                .setDefaults(NotificationCompat.DEFAULT_LIGHTS | NotificationCompat.DEFAULT_VIBRATE)
                .setContentIntent(openPi);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify((int) (System.currentTimeMillis() % Integer.MAX_VALUE), b.build());
        }
    }
}
