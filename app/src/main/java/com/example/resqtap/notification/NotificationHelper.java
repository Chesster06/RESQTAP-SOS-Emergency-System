package com.example.resqtap.notification;
import com.example.resqtap.R;

import com.example.resqtap.home.MainActivity;
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
import androidx.core.app.NotificationManagerCompat;


/**
 * NotificationHelper
 * Setup notification channels Android (SOS, Call, Room, Notice).
 */
public final class NotificationHelper {
    private NotificationHelper() {}

    public static final String CHANNEL_ID_SOS = "sos_emergency_v3";
    public static final String CHANNEL_ID_LOCAL = "local_notifications_v1";
    private static final String SOS_TAG = "sos_alert";

    public static void createNotificationChannel(Context context) {
        if (context == null) return;
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL_ID_LOCAL) != null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID_LOCAL,
                "Local Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Notifications shown locally by the app.");
        nm.createNotificationChannel(channel);
    }

    /** Paparkan LocalNotification. */
    public static void showLocalNotification(Context context, String title, String text) {
        if (context == null) return;
        createNotificationChannel(context);

        String safeTitle = title == null || title.trim().isEmpty() ? "ResQTap" : title.trim();
        String safeText = text == null ? "" : text.trim();

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context,
                400,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_LOCAL)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(safeTitle)
                .setContentText(safeText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(safeText))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(openPendingIntent);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        if (!notificationManager.areNotificationsEnabled()) return;
        notificationManager.notify((int) (System.currentTimeMillis() % Integer.MAX_VALUE), builder.build());
    }

    /**
     * Memaparkan notifikasi khas Emergency Check-in Ping.
     * Apabila ditekan, akan terus membuka aplikasi dan memaparkan dialog 1-ketikan
     * status keselamatan (Selamat vs Perlukan Bantuan) untuk pengguna / introvert.
     */
    public static void showEmergencyCheckinNotification(Context context, String title, String text) {
        if (context == null) return;
        createNotificationChannel(context);

        String safeTitle = title == null || title.trim().isEmpty() ? context.getString(R.string.checkin_title) : title.trim();
        String safeText = text == null || text.trim().isEmpty() ? context.getString(R.string.checkin_desc) : text.trim();

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.putExtra("trigger_checkin", true);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context,
                401,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_LOCAL)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(safeTitle)
                .setContentText(safeText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(safeText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openPendingIntent);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        if (!notificationManager.areNotificationsEnabled()) return;
        notificationManager.notify(9911, builder.build());
    }

    public static void ensureSosChannel(Context context) {
        if (context == null) return;
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel existing = nm.getNotificationChannel(CHANNEL_ID_SOS);
        if (existing != null) {

            try {
                existing.enableVibration(false);
                existing.setVibrationPattern(null);
                nm.createNotificationChannel(existing);
            } catch (Exception ignored) {
            }
            return;
        }

        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID_SOS,
                "SOS Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH
        );
        ch.setDescription("High priority SOS emergency alerts.");

        ch.enableVibration(false);
        ch.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);

        ch.setSound(null, null);

        nm.createNotificationChannel(ch);
    }

    /** Paparkan SosAlert. */
    public static void showSosAlert(Context context, String roomId, String senderName, String senderUid, String alertId) {
        if (context == null) return;
        String focusUid = String.valueOf(senderUid == null ? "" : senderUid).trim();

        com.google.firebase.auth.FirebaseUser cu = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        String myUid = (cu != null && cu.getUid() != null) ? cu.getUid().trim() : com.example.resqtap.utils.UserPrefs.getUid(context);
        if ((!myUid.isEmpty() && myUid.equals(focusUid)) || com.example.resqtap.sos.SendingAlarmActivity.isSendingAlarm) {
            // Jangan bunyikan atau tunjukkan notifikasi penggera bilik kepada penghantar sendiri
            return;
        }

        ensureSosChannel(context);

        String safeRoom = String.valueOf(roomId == null ? "" : roomId).trim();
        String safeName = String.valueOf(senderName == null ? "" : senderName).trim();
        if ("null".equalsIgnoreCase(safeName)) safeName = "";

        String title = "SOS ALERT";
        String text = safeName.isEmpty()
                ? "Emergency SOS in your room."
                : (safeName + " triggered SOS in your room.");

        Intent open = new Intent(context, RoomMapActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (!safeRoom.isEmpty()) open.putExtra(RoomMapActivity.EXTRA_ROOM_CODE, safeRoom);
        String aId = String.valueOf(alertId == null ? "" : alertId).trim();
        if (!focusUid.isEmpty()) open.putExtra(RoomMapActivity.EXTRA_FOCUS_UID, focusUid);
        if (!aId.isEmpty()) open.putExtra(RoomMapActivity.EXTRA_SOS_ALERT_ID, aId);
        PendingIntent openPi = PendingIntent.getActivity(
                context,
                200,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        // Full-screen Intent untuk popup skrin SosAlarmActivity atas lockscreen
        Intent fullScreenIntent = new Intent(context, com.example.resqtap.sos.SosAlarmActivity.class);
        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (!safeRoom.isEmpty()) fullScreenIntent.putExtra(com.example.resqtap.sos.SosAlarmActivity.EXTRA_ROOM_CODE, safeRoom);
        if (!safeName.isEmpty()) fullScreenIntent.putExtra(com.example.resqtap.sos.SosAlarmActivity.EXTRA_SENDER_NAME, safeName);
        if (!focusUid.isEmpty()) fullScreenIntent.putExtra(com.example.resqtap.sos.SosAlarmActivity.EXTRA_SENDER_UID, focusUid);
        if (!aId.isEmpty()) fullScreenIntent.putExtra(com.example.resqtap.sos.SosAlarmActivity.EXTRA_ALERT_ID, aId);
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                201,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        // Action Snooze (Padamkan Siren)
        Intent snoozeIntent = new Intent(context, com.example.resqtap.sos.SosSnoozeReceiver.class);
        snoozeIntent.setAction(com.example.resqtap.sos.SosSnoozeReceiver.ACTION_SNOOZE_SOS);
        snoozeIntent.putExtra(com.example.resqtap.sos.SosSnoozeReceiver.EXTRA_ALERT_ID, aId);
        snoozeIntent.putExtra(com.example.resqtap.sos.SosSnoozeReceiver.EXTRA_ROOM_ID, safeRoom);
        PendingIntent snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                202,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID_SOS)
                .setSmallIcon(R.drawable.ic_error_24)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openPi)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .addAction(R.drawable.ic_bell, context.getString(R.string.sos_action_snooze), snoozePendingIntent)
                .addAction(R.drawable.ic_menu_location, context.getString(R.string.sos_alarm_view_map), openPi)
                .setSilent(true)
                .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
                .setVibrate(new long[]{0});

        try {
            NotificationManagerCompat nm = NotificationManagerCompat.from(context);
            if (!nm.areNotificationsEnabled()) {
                android.util.Log.d("SOS_DEBUG", "Notifications disabled at OS level; cannot post SOS notification");
                return;
            }
            nm.notify(SOS_TAG, notificationIdForAlert(aId), b.build());
        } catch (Exception e) {
            try {
                android.util.Log.d("SOS_DEBUG", "notify failed: " + e.getMessage());
            } catch (Exception ignored) {
            }
        }
    }

    public static void cancelSos(Context context, String alertId) {
        if (context == null) return;
        String aId = String.valueOf(alertId == null ? "" : alertId).trim();
        NotificationManagerCompat.from(context).cancel(SOS_TAG, notificationIdForAlert(aId));
    }

    private static int notificationIdForAlert(String alertId) {
        String aId = String.valueOf(alertId == null ? "" : alertId).trim();
        if (aId.isEmpty()) {

            return (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
        }

        return 10000 + (aId.hashCode() & 0x7fffffff);
    }
}
