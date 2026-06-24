package com.example.resqtap.notification;

import com.example.resqtap.room.FirebaseRoomClient;
import com.example.resqtap.utils.UserPrefs;

import android.content.Context;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;


/**
 * ResQTapMessagingService
 * FCM Service: handle push notification background untuk SOS alert, call, dan admin notice.
 */
public class ResQTapMessagingService extends FirebaseMessagingService {
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        persistToken(this, token);
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        if (message == null) return;

        Map<String, String> data = message.getData();
        String type = value(data, "type");
        if ("admin_notification".equalsIgnoreCase(type)) {
            String notificationId = value(data, "notificationId");
            String title = value(data, "title");
            String body = value(data, "message");
            if (body.isEmpty()) body = value(data, "body");
            RemoteMessage.Notification notification = message.getNotification();
            if (title.isEmpty() && notification != null) title = safe(notification.getTitle());
            if (body.isEmpty() && notification != null) body = safe(notification.getBody());

            if (!notificationId.isEmpty()) {
                UserPrefs.setLastAdminNotificationId(this, notificationId);
            }
            UserPrefs.incrementUnreadNotifications(this);
            NotificationUtils.notifyAdmin(this, notificationId, title, body);
            return;
        }

        if ("bell".equalsIgnoreCase(type)) {
            NotificationUtils.notifyBell(this, value(data, "room"), value(data, "fromName"));
            return;
        }

        if ("SOS_ALERT".equalsIgnoreCase(type)) {
            String sUid = value(data, "senderUid");
            com.google.firebase.auth.FirebaseUser cu = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            String myUid = (cu != null && cu.getUid() != null) ? cu.getUid().trim() : com.example.resqtap.utils.UserPrefs.getUid(this);
            if ((!myUid.isEmpty() && myUid.equals(sUid)) || com.example.resqtap.sos.SendingAlarmActivity.isSendingAlarm) {
                return;
            }
            NotificationHelper.showSosAlert(
                    this,
                    value(data, "roomId"),
                    value(data, "senderName"),
                    sUid,
                    value(data, "alertId")
            );
            return;
        }

        if ("SOS_CANCELLED".equalsIgnoreCase(type)) {
            NotificationHelper.cancelSos(this, value(data, "alertId"));
        }
    }

    /** Simpan atau hantar data CurrentToken. */
    public static void syncCurrentToken(Context context) {
        try {
            FirebaseMessaging.getInstance().getToken()
                    .addOnSuccessListener(token -> persistToken(context, token));
        } catch (Exception ignored) {
        }
    }

    private static void persistToken(Context context, String token) {
        String clean = safe(token);
        if (clean.isEmpty()) return;
        try {
            if (context != null) UserPrefs.setFcmToken(context, clean);
        } catch (Exception ignored) {
        }

        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) return;
            String deviceId = context == null ? "" : UserPrefs.getOrCreateDeviceId(context);
            FirebaseRoomClient.updateUserFcmTokenQueued(user.getUid(), deviceId, clean);
        } catch (Exception ignored) {
        }
    }

    private static String value(Map<String, String> data, String key) {
        if (data == null || key == null) return "";
        return safe(data.get(key));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
