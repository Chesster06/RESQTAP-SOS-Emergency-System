package com.example.resqtap.sos;

import com.example.resqtap.utils.UserPrefs;

import android.content.Context;


/**
 * SosPrefs
 * Helper SharedPreferences untuk simpan setting SOS.
 */
public final class SosPrefs {
    private SosPrefs() {}

    public static long getLastSeenAlertTime(Context context, String roomId) {
        return UserPrefs.getSosLastSeenAt(context, roomId);
    }

    public static void setLastSeenAlertTime(Context context, String roomId, long createdAtMs) {
        UserPrefs.setSosLastSeenAt(context, roomId, createdAtMs);
    }

    public static String getLastHandledAlertId(Context context, String roomId) {
        return UserPrefs.getSosLastHandledId(context, roomId);
    }

    public static void setLastHandledAlertId(Context context, String roomId, String alertId) {
        UserPrefs.setSosLastHandledId(context, roomId, alertId);
    }

    /**
     * Hanya set baseline ke serverNow jika tiada lastSeen sebelumnya (first-time).
     * Kalau ada lastSeen, biarkan, kita nak catch SOS yang fired masa service mati.
     */
    public static void bumpBaselineAtListenerStart(Context context, String roomId, long serverNowMsOrLocalNow) {
        long lastSeen = getLastSeenAlertTime(context, roomId);
        // Only initialise baseline on first ever attach; never overwrite an existing lastSeen with serverNow.
        // This lets the listener catch SOSes fired while the service was killed/restarting.
        if (lastSeen <= 0L) {
            long now = Math.max(0L, serverNowMsOrLocalNow);
            setLastSeenAlertTime(context, roomId, now);
        }
    }

    /**
     * Bagi sistem kecemasan: NO hard age limit. Kita cuma reject duplicate IDs.
     * SOS yang fired masa service mati kena tetap diterima bila service restart.
     */
    public static synchronized boolean tryMarkHandled(Context context, String roomId, String alertId, long createdAtMs) {
        if (context == null) return false;
        String room = String.valueOf(roomId == null ? "" : roomId).trim().toUpperCase(java.util.Locale.ROOT);
        String id = String.valueOf(alertId == null ? "" : alertId).trim();
        long createdAt = Math.max(0L, createdAtMs);
        if (room.isEmpty() || id.isEmpty() || createdAt <= 0L) return false;

        // Reject duplicates by alertId: same SOS fired twice won't alarm twice.
        String lastId = getLastHandledAlertId(context, room);
        if (!lastId.isEmpty() && lastId.equals(id)) return false;

        // Reject if this SOS is older than the last one we already handled for this room.
        long lastSeen = getLastSeenAlertTime(context, room);
        if (createdAt <= lastSeen) return false;

        setLastHandledAlertId(context, room, id);
        setLastSeenAlertTime(context, room, createdAt);
        return true;
    }
}
