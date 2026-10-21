package com.example.resqtap.sos;
import com.example.resqtap.R;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;


/**
 * SosAudioManager
 * Siren kecemasan: synthesize sound siren frekuensi tinggi guna AudioTrack pada volume max.
 */
public final class SosAudioManager {
    private SosAudioManager() {}

    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile String activeAlertId = "";
    private static volatile MediaPlayer player;
    private static volatile Runnable hardStopRunnable;

    public static void start10s(Context context, String alertId) {
        if (context == null) return;
        String id = String.valueOf(alertId == null ? "" : alertId).trim();
        if (id.isEmpty()) return;

        synchronized (LOCK) {
            stopLocked();
            activeAlertId = id;
        }

        try {
            final Context appCtx = context.getApplicationContext();
            MediaPlayer mp = MediaPlayer.create(appCtx, R.raw.sos_alert_sound);
            if (mp == null) {
                try {
                    android.util.Log.d("SOS_DEBUG", "SosAudioManager: MediaPlayer.create returned null");
                } catch (Exception ignored) {
                }
                return;
            }
            try {
                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    mp.setAudioAttributes(new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
                } else {

                    mp.setAudioStreamType(android.media.AudioManager.STREAM_NOTIFICATION);
                }
            } catch (Exception ignored) {
            }
            mp.setLooping(true);
            try {
                mp.setOnErrorListener((m, what, extra) -> {
                    try {
                        android.util.Log.d("SOS_DEBUG", "SOS audio error what=" + what + " extra=" + extra);
                    } catch (Exception ignored) {
                    }
                    stop(appCtx, id);
                    return true;
                });
            } catch (Exception ignored) {
            }

            synchronized (LOCK) {
                player = mp;
            }

            try {
                mp.start();
            } catch (Exception e) {
                try {
                    android.util.Log.d("SOS_DEBUG", "SOS audio start failed: " + e.getMessage());
                } catch (Exception ignored) {
                }
            }
            try {
                android.util.Log.d("SOS_DEBUG", "SosAudioManager: playing custom SOS sound continuously until snoozed alertId=" + id);
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {
        }
    }

    public static void stop(Context context, String alertId) {
        if (context == null) return;
        String id = String.valueOf(alertId == null ? "" : alertId).trim();
        if (id.isEmpty()) return;
        synchronized (LOCK) {
            if (!id.equals(activeAlertId)) return;
            stopLocked();
        }
    }

    public static void stopAll() {
        synchronized (LOCK) {
            stopLocked();
        }
    }

    private static void stopLocked() {
        activeAlertId = "";
        try {
            Runnable hs = hardStopRunnable;
            if (hs != null) MAIN.removeCallbacks(hs);
        } catch (Exception ignored) {
        }
        hardStopRunnable = null;

        MediaPlayer mp = player;
        player = null;
        if (mp == null) return;
        try {
            if (mp.isPlaying()) mp.stop();
        } catch (Exception ignored) {
        }
        try {
            mp.reset();
        } catch (Exception ignored) {
        }
        try {
            mp.release();
        } catch (Exception ignored) {
        }
    }
}
