package com.example.resqtap.call;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.resqtap.R;

/**
 * CallNotificationHelper
 * Handles high-priority incoming call notification channel and full-screen intent.
 * Crucial for Android 10+ / 12 / 13 / 14 real devices where background startActivity is restricted.
 */
public final class CallNotificationHelper {

    private static final String TAG = "CallNotificationHelper";
    public static final String CHANNEL_ID_INCOMING_CALL = "incoming_calls_v2";
    public static final int INCOMING_CALL_NOTIF_ID = 40401;

    private CallNotificationHelper() {}

    /**
     * Ensure dedicated high-priority channel for incoming calls.
     */
    public static void ensureIncomingCallChannel(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel existing = nm.getNotificationChannel(CHANNEL_ID_INCOMING_CALL);
        if (existing != null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID_INCOMING_CALL,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Full-screen notifications for incoming audio and video calls.");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 1000, 1000});

        Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        if (ringtoneUri == null) ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        channel.setSound(ringtoneUri, audioAttributes);

        nm.createNotificationChannel(channel);
    }

    /**
     * Show incoming call heads-up notification with Full Screen Intent.
     */
    public static void showIncomingCallNotification(Context context, String callId, String callerName, String callerPhotoUrl, String callType) {
        if (context == null) return;
        ensureIncomingCallChannel(context);

        String safeCaller = (callerName != null && !callerName.trim().isEmpty()) ? callerName.trim() : "ResQTap";
        String safeType = (callType != null && !callType.trim().isEmpty()) ? callType.trim().toLowerCase() : "video";
        boolean isVoice = "voice".equalsIgnoreCase(safeType);

        // Intent to launch IncomingCallActivity directly via FullScreenIntent
        Intent fullScreenIntent = new Intent(context, IncomingCallActivity.class);
        fullScreenIntent.putExtra("callId", callId);
        fullScreenIntent.putExtra("callerName", safeCaller);
        fullScreenIntent.putExtra("callerPhotoUrl", callerPhotoUrl);
        fullScreenIntent.putExtra("callType", safeType);
        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                INCOMING_CALL_NOTIF_ID,
                fullScreenIntent,
                flags
        );

        String title = isVoice ? "Incoming Voice Call" : "Incoming Video Call";
        String contentText = safeCaller + " is calling you...";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_INCOMING_CALL)
                .setSmallIcon(R.drawable.ic_phone)
                .setContentTitle(title)
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOngoing(true)
                .setContentIntent(fullScreenPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setVibrate(new long[]{0, 1000, 1000});

        Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        if (ringtoneUri != null) {
            builder.setSound(ringtoneUri);
        }

        try {
            NotificationManagerCompat nmc = NotificationManagerCompat.from(context);
            if (nmc.areNotificationsEnabled()) {
                nmc.notify(INCOMING_CALL_NOTIF_ID, builder.build());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to post incoming call notification", e);
        }
    }

    /**
     * Dismiss the incoming call notification.
     */
    public static void dismissIncomingCallNotification(Context context) {
        if (context == null) return;
        try {
            NotificationManagerCompat nmc = NotificationManagerCompat.from(context);
            nmc.cancel(INCOMING_CALL_NOTIF_ID);
        } catch (Exception ignored) {}
    }
}
