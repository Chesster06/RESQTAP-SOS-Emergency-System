package com.example.resqtap.call;
import com.example.resqtap.R;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;


/**
 * CallService
 * Foreground Service untuk call: pastikan audio/video call tak terputus bila user switch app atau lock screen.
 */
public class CallService extends Service {

    private static final String CHANNEL_ID = "call_service_channel";
    private static final int NOTIF_ID = 2102;

    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel();
    }

    /**
     * Handles service launch intent. Extracts call metadata (callId, callerName, callType),
     * builds a persistent notification with a return PendingIntent, and calls startForeground.
     *
     * @param intent Launch intent containing call parameters
     * @param flags Start flags
     * @param startId Unique start identifier
     * @return START_NOT_STICKY to avoid automatic restart if killed
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String callId = intent != null ? intent.getStringExtra("callId") : "";
        String callerName = intent != null ? intent.getStringExtra("callerName") : "ResQTap Call";
        String callType = intent != null ? intent.getStringExtra("callType") : "video";
        boolean voiceCall = "voice".equalsIgnoreCase(callType);

        Intent open = new Intent(this, voiceCall ? VoiceCallActivity.class : VideoCallActivity.class);
        open.putExtra("callId", callId);
        open.putExtra("callerName", callerName);
        open.putExtra("callType", voiceCall ? "voice" : "video");
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(
                this,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_phone)
                .setContentTitle(voiceCall ? "Active Voice Call" : "Active Video Call")
                .setContentText("Tap to return to call with " + callerName)
                .setOngoing(true)
                .setContentIntent(openPi)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int serviceType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            if (!voiceCall) {
                serviceType |= android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
            }
            startForeground(NOTIF_ID, n, serviceType);
        } else {
            startForeground(NOTIF_ID, n);
        }

        return START_NOT_STICKY;
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID,
                        "Calls",
                        NotificationManager.IMPORTANCE_HIGH
                );
                nm.createNotificationChannel(ch);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
