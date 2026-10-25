package com.example.resqtap.sos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.resqtap.sos.SosAudioManager;
import com.example.resqtap.sos.VibrateManager;
import com.example.resqtap.notification.NotificationHelper;

/**
 * SosSnoozeReceiver
 * Mengendalikan aksi Snooze / Padam Bunyi penggera SOS dari notifikasi atau sistem.
 */
public class SosSnoozeReceiver extends BroadcastReceiver {

    public static final String ACTION_SNOOZE_SOS = "com.example.resqtap.ACTION_SNOOZE_SOS";
    public static final String ACTION_SOS_SNOOZED = "com.example.resqtap.SOS_SNOOZED";
    public static final String EXTRA_ALERT_ID = "alertId";
    public static final String EXTRA_ROOM_ID = "roomId";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        String action = intent.getAction();
        if (ACTION_SNOOZE_SOS.equals(action)) {
            String alertId = intent.getStringExtra(EXTRA_ALERT_ID);
            String roomId = intent.getStringExtra(EXTRA_ROOM_ID);

            Log.d("SOS_DEBUG", "SosSnoozeReceiver received ACTION_SNOOZE_SOS for alertId=" + alertId);

            // 1. Hentikan bunyi siren & getaran segera
            try {
                SosAudioManager.stopAll();
            } catch (Exception e) {
                Log.e("SOS_DEBUG", "Error stopping SosAudioManager: " + e.getMessage());
            }

            try {
                VibrateManager.stopAll(context);
            } catch (Exception e) {
                Log.e("SOS_DEBUG", "Error stopping VibrateManager: " + e.getMessage());
            }

            // 2. Batalkan notifikasi SOS jika ada alertId
            if (alertId != null && !alertId.trim().isEmpty()) {
                try {
                    NotificationHelper.cancelSos(context, alertId);
                } catch (Exception e) {
                    Log.e("SOS_DEBUG", "Error cancelling SOS notification: " + e.getMessage());
                }
            }

            // 3. Broadcast kepada mana-mana aktiviti terbuka (seperti SosAlarmActivity) bahawa penggera telah disnooze
            try {
                Intent snoozedIntent = new Intent(ACTION_SOS_SNOOZED);
                snoozedIntent.putExtra(EXTRA_ALERT_ID, alertId);
                snoozedIntent.putExtra(EXTRA_ROOM_ID, roomId);
                snoozedIntent.setPackage(context.getPackageName());
                context.sendBroadcast(snoozedIntent);
            } catch (Exception e) {
                Log.e("SOS_DEBUG", "Error sending SOS_SNOOZED broadcast: " + e.getMessage());
            }
        }
    }
}
