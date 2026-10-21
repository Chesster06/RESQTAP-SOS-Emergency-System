package com.example.resqtap.room;
import com.example.resqtap.R;

import com.example.resqtap.call.CallSignalingClient;
import com.example.resqtap.call.IncomingCallActivity;
import com.example.resqtap.home.MainActivity;
import com.example.resqtap.map.GpsUtils;
import com.example.resqtap.notification.NotificationHelper;
import com.example.resqtap.notification.NotificationUtils;
import com.example.resqtap.sos.SosAudioManager;
import com.example.resqtap.sos.SosPrefs;
import com.example.resqtap.sos.VibrateManager;
import com.example.resqtap.utils.BatteryUtils;
import com.example.resqtap.utils.PermissionUtils;
import com.example.resqtap.utils.UserPrefs;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;

import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;


/**
 * LiveRoomTrackingService
 * Foreground Service untuk live location: stream GPS lat/lng ke RTDB tanpa kena kill oleh battery saver.
 */
public class LiveRoomTrackingService extends Service {
    /** Paparkan LowBatteryNotification pada skrin. */
    private void showLowBatteryNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        String channelId = "battery_alert_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(channelId, "Battery Alerts", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(chan);
        }
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_battery)
                .setContentTitle(getString(R.string.low_battery_alert))
                .setContentText(getString(R.string.low_battery_alert_desc))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        nm.notify(2102, builder.build());
    }
    public static final String ACTION_START = "com.example.resqtap.action.TRACK_START";
    public static final String ACTION_STOP = "com.example.resqtap.action.TRACK_STOP";
    public static final String EXTRA_ROOM_CODE = "room_code";
    public static final String EXTRA_UID = "uid";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_PHOTO_URL = "photo_url";
    public static final String EXTRA_PHOTO_B64 = "photo_b64";

    private static final String CHANNEL_ID = "live_room_tracking";
    private static final int NOTIF_ID = 2101;

    private FusedLocationProviderClient fused;
    private LocationCallback callback;
    private boolean running = false;
    private android.content.BroadcastReceiver batteryReceiver;
    private boolean hasNotifiedLowBattery = false;
    private final ConcurrentHashMap<String, RoomWatcher> roomWatchers = new ConcurrentHashMap<>();
    private ChildEventListener userRoomsListener;
    private ValueEventListener forceLeaveListener;
    private ValueEventListener roomDeletedListener;
    private final android.os.Handler sosHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private volatile Runnable sosStopWatchdog;
    private final android.os.Handler fgHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private volatile Runnable fgEnsureRunnable;

    private String roomCode = "";
    private String uid = "";
    private String name = "";
    private String photoUrl = "";
    private String photoB64 = "";
    private String deviceId = "";

    /** Kelas pembantu untuk mendengar SOS, Bell dan Permissions bagi setiap bilik. */
    private static class RoomWatcher {
        final String room;
        ValueEventListener permissionsListener;
        FirebaseRoomClient.RoomPermissions permissions = new FirebaseRoomClient.RoomPermissions();
        ChildEventListener sosListener;
        ChildEventListener bellListener;

        RoomWatcher(String room) {
            this.room = room;
        }
    }

    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel();
        fused = LocationServices.getFusedLocationProviderClient(this);
        batteryReceiver = new android.content.BroadcastReceiver() {
    @Override
    public void onReceive(android.content.Context context, Intent intent) {
                if (Intent.ACTION_BATTERY_LOW.equals(intent.getAction()) || Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                    if (UserPrefs.isLowBatteryAlert(context)) {
                        int pct = BatteryUtils.getBatteryPct(context);
                        if (pct >= 0 && pct <= 15 && !hasNotifiedLowBattery) {
                            showLowBatteryNotification();
                            hasNotifiedLowBattery = true;
                        } else if (pct > 20) {
                            hasNotifiedLowBattery = false;
                        }
                    }
                }
            }
        };
        android.content.IntentFilter filter = new android.content.IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        try {
            registerReceiver(batteryReceiver, filter);
        } catch (Exception ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureChannel();
        String action = intent == null ? "" : String.valueOf(intent.getAction());
        if (ACTION_STOP.equals(action)) {
            stopTracking();
            stopForegroundNotificationWatchdog();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null) {
            roomCode = String.valueOf(intent.getStringExtra(EXTRA_ROOM_CODE) == null ? "" : intent.getStringExtra(EXTRA_ROOM_CODE)).trim();
            uid = String.valueOf(intent.getStringExtra(EXTRA_UID) == null ? "" : intent.getStringExtra(EXTRA_UID)).trim();
            name = String.valueOf(intent.getStringExtra(EXTRA_NAME) == null ? "" : intent.getStringExtra(EXTRA_NAME)).trim();
            photoUrl = String.valueOf(intent.getStringExtra(EXTRA_PHOTO_URL) == null ? "" : intent.getStringExtra(EXTRA_PHOTO_URL)).trim();
            photoB64 = String.valueOf(intent.getStringExtra(EXTRA_PHOTO_B64) == null ? "" : intent.getStringExtra(EXTRA_PHOTO_B64)).trim();
        }
        deviceId = UserPrefs.getOrCreateDeviceId(this);

        Notification n = buildNotification(roomCode);
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                if (PermissionUtils.hasAnyLocation(this)) {
                    startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
                } else {
                    startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                }
            } else if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (Exception e) {
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                } else {
                    startForeground(NOTIF_ID, n);
                }
            } catch (Exception ignored) {}
        }
        startForegroundNotificationWatchdog();
        try {
            android.util.Log.d("SOS_DEBUG", "LiveRoomTrackingService started");
        } catch (Exception ignored) {
        }
        if (uid.isEmpty()) {
            stopTracking();
            stopForegroundNotificationWatchdog();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        // Always ensure multi-room SOS monitoring and incoming calls are active!
        startUserRoomsMultiWatcher();
        startIncomingCallListener();

        if (roomCode.isEmpty()) {
            // No active room selected, but user location is still actively tracked for safety & SOS alerts!
            stopForceLeaveListener();
            stopRoomDeletedListener();
            startTracking();
            return START_STICKY;
        }

        FirebaseRoomClient.verifyUserInRoom(uid, roomCode, isInRoom -> {
            if (!isInRoom) {
                UserPrefs.setActiveRoomCode(LiveRoomTrackingService.this, "");
                roomCode = "";
                stopForceLeaveListener();
                stopRoomDeletedListener();
                startTracking();
                return;
            }
            startForceLeaveListener();
            startRoomDeletedListener();
            startTracking();
        });
        return START_STICKY;
    }

    // SEKSYEN: ONDESTROY
    /** Pembersihan memori, buang listener, dan tutup sambungan. */
    @Override
    public void onDestroy() {
        stopForegroundNotificationWatchdog();
        try {
            if (batteryReceiver != null) unregisterReceiver(batteryReceiver);
        } catch (Exception ignored) {}
        try {
            if (fused != null && callback != null) {
                fused.removeLocationUpdates(callback);
            }
        } catch (Exception ignored) {
        }
        stopUserRoomsMultiWatcher();
        super.onDestroy();
    }

    private void startRoomDeletedListener() {
        if (roomDeletedListener != null) return;
        roomDeletedListener = FirebaseRoomClient.listenRoomDeleted(roomCode, () -> {
            try {
                android.util.Log.d("SOS_DEBUG", "Room deleted detected roomId=" + roomCode + " uid=" + uid);
            } catch (Exception ignored) {
            }
            new Thread(() -> {
                try {
                    FirebaseRoomClient.deleteUserRoom(uid, roomCode);
                } catch (Exception ignored) {
                }
                try {
                    UserPrefs.setActiveRoomCode(LiveRoomTrackingService.this, "");
                } catch (Exception ignored) {
                }
                roomCode = "";
                try { stopForceLeaveListener(); } catch (Exception ignored) {}
                try { stopRoomDeletedListener(); } catch (Exception ignored) {}
                try { startTracking(); } catch (Exception ignored) {}
            }).start();
        });
    }

    private void stopRoomDeletedListener() {
        if (roomDeletedListener == null) return;
        try {
            FirebaseRoomClient.removeRoomDeletedListener(roomCode, roomDeletedListener);
        } catch (Exception ignored) {
        }
        roomDeletedListener = null;
    }

    private void startForceLeaveListener() {
        if (forceLeaveListener != null) return;
        forceLeaveListener = FirebaseRoomClient.listenForceLeave(roomCode, uid, (byUid, atMillis) -> {
            try {
                android.util.Log.d("SOS_DEBUG", "ForceLeave received roomId=" + roomCode + " uid=" + uid);
            } catch (Exception ignored) {
            }
            new Thread(() -> {
                try {
                    FirebaseRoomClient.deleteUserRoom(uid, roomCode);
                } catch (Exception ignored) {
                }
                FirebaseRoomClient.clearForceLeaveRequestQueued(roomCode, uid);
                try {
                    UserPrefs.setActiveRoomCode(LiveRoomTrackingService.this, "");
                } catch (Exception ignored) {
                }
                roomCode = "";
                try { stopForceLeaveListener(); } catch (Exception ignored) {}
                try { stopRoomDeletedListener(); } catch (Exception ignored) {}
                try { startTracking(); } catch (Exception ignored) {}
            }).start();
        });
    }

    private void stopForceLeaveListener() {
        if (forceLeaveListener == null) return;
        try {
            FirebaseRoomClient.removeForceLeaveListener(roomCode, uid, forceLeaveListener);
        } catch (Exception ignored) {
        }
        forceLeaveListener = null;
    }

    private void startForegroundNotificationWatchdog() {

        stopForegroundNotificationWatchdog();
        fgEnsureRunnable = new Runnable() {
    @Override
    public void run() {
                try {
                    Notification n = buildNotification(roomCode);
                    if (Build.VERSION.SDK_INT >= 29) {
                        startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
                    } else {
                        startForeground(NOTIF_ID, n);
                    }
                } catch (Exception ignored) {
                }
                fgHandler.postDelayed(this, 5000L);
            }
        };
        fgHandler.postDelayed(fgEnsureRunnable, 5000L);
    }

    private void stopForegroundNotificationWatchdog() {
        try {
            Runnable r = fgEnsureRunnable;
            if (r != null) fgHandler.removeCallbacks(r);
        } catch (Exception ignored) {
        }
        fgEnsureRunnable = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startTracking() {
        if (running) return;
        if (!PermissionUtils.hasAnyLocation(this)) return;
        if (!GpsUtils.isGpsEnabled(this)) return;

        if (roomCode != null && !roomCode.trim().isEmpty()) {
            FirebaseRoomClient.upsertMemberPresenceQueued(roomCode, uid, name, photoUrl, photoB64, BatteryUtils.getBatteryPct(this));
        }

        boolean saver = UserPrefs.isBatterySaverMode(this);
        long interval = saver ? 30000L : 5000L;
        long minInterval = saver ? 15000L : 2500L;

        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
                .setMinUpdateIntervalMillis(minInterval)
                .setWaitForAccurateLocation(false)
                .build();

        if (callback == null) {
            callback = new LocationCallback() {
    @Override
    public void onLocationResult(LocationResult result) {
                    if (result == null) return;
                    android.location.Location loc = result.getLastLocation();
                    if (loc == null) return;
                    LatLng me = new LatLng(loc.getLatitude(), loc.getLongitude());
                    int bat = BatteryUtils.getBatteryPct(LiveRoomTrackingService.this);
                    FirebaseRoomClient.publishLocationQueued(roomCode, uid, name, photoUrl, photoB64, me.latitude, me.longitude, bat);
                    com.example.resqtap.supabase.SupabaseManager.getInstance().updateLiveLocation(uid, me.latitude, me.longitude, bat);
                }
            };
        }

        try {
            fused.requestLocationUpdates(req, callback, getMainLooper());
            running = true;
        } catch (SecurityException ignored) {
        }
    }

    private void stopTracking() {
        if (!running) return;
        if (callback == null) return;
        try {
            fused.removeLocationUpdates(callback);
        } catch (Exception ignored) {
        }
        running = false;
    }

    /** Memulakan pendengaran ke atas semua bilik pengguna secara dinamik. */
    private void startUserRoomsMultiWatcher() {
        if (userRoomsListener != null) return;
        if (uid == null || uid.trim().isEmpty()) return;

        // Sentiasa pastikan bilik aktif semasa dipantau
        if (roomCode != null && !roomCode.trim().isEmpty()) {
            ensureRoomWatcher(roomCode.trim());
        }

        try {
            userRoomsListener = new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                    if (snapshot == null || !snapshot.exists()) return;
                    String code = snapshot.getKey() == null ? "" : snapshot.getKey().trim();
                    if (!code.isEmpty()) {
                        ensureRoomWatcher(code);
                    }
                }

                @Override
                public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                    if (snapshot == null || !snapshot.exists()) return;
                    String code = snapshot.getKey() == null ? "" : snapshot.getKey().trim();
                    if (!code.isEmpty()) {
                        ensureRoomWatcher(code);
                    }
                }

                @Override
                public void onChildRemoved(DataSnapshot snapshot) {
                    if (snapshot == null) return;
                    String code = snapshot.getKey() == null ? "" : snapshot.getKey().trim();
                    if (!code.isEmpty()) {
                        removeRoomWatcher(code);
                    }
                }

                @Override public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}
                @Override public void onCancelled(DatabaseError error) {}
            };

            FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                    .getReference("userRooms")
                    .child(uid)
                    .addChildEventListener(userRoomsListener);
        } catch (Exception ignored) {
        }
    }

    /** Hentikan pendengaran nod userRooms. */
    private void stopUserRoomsMultiWatcher() {
        if (userRoomsListener != null) {
            try {
                FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                        .getReference("userRooms")
                        .child(uid)
                        .removeEventListener(userRoomsListener);
            } catch (Exception ignored) {}
            userRoomsListener = null;
        }
        for (String c : roomWatchers.keySet()) {
            removeRoomWatcher(c);
        }
        roomWatchers.clear();
    }

    /** Pastikan pemantau (Watcher) wujud dan aktif bagi bilik tertentu. */
    private synchronized void ensureRoomWatcher(String targetRoom) {
        if (targetRoom == null || targetRoom.trim().isEmpty()) return;
        final String code = targetRoom.trim().toUpperCase(java.util.Locale.ROOT);
        if (roomWatchers.containsKey(code)) return;

        RoomWatcher watcher = new RoomWatcher(code);
        roomWatchers.put(code, watcher);

        // 1. Dengar permissions untuk bilik ini
        try {
            watcher.permissionsListener = FirebaseRoomClient.listenRoomPermissions(code, p -> {
                if (p != null) {
                    watcher.permissions = p;
                    if (!watcher.permissions.allowSosAlarm) {
                        try {
                            VibrateManager.stopAll(LiveRoomTrackingService.this);
                            SosAudioManager.stopAll();
                        } catch (Exception ignored) {}
                    }
                }
            });
        } catch (Exception ignored) {}

        // 2. Dengar Bell untuk bilik ini
        try {
            watcher.bellListener = FirebaseRoomClient.listenBells(code, uid, deviceId, (fromUid, fromDeviceId, fromName, atMillis, id) -> {
                if (watcher.permissions != null && !watcher.permissions.allowTriggerBell) {
                    FirebaseRoomClient.ackBell(code, uid, id, deviceId);
                    return;
                }
                String from = String.valueOf(fromUid == null ? "" : fromUid).trim();
                String fromDev = String.valueOf(fromDeviceId == null ? "" : fromDeviceId).trim();
                String name = String.valueOf(fromName == null ? "" : fromName).trim();
                boolean sameAccount = uid != null && uid.trim().equals(from);
                boolean sameDevice = deviceId != null && !deviceId.trim().isEmpty() && deviceId.trim().equals(fromDev);
                if (sameAccount && sameDevice) {
                    FirebaseRoomClient.ackBell(code, uid, id, deviceId);
                    return;
                }
                triggerVibrate();
                NotificationUtils.notifyBell(LiveRoomTrackingService.this, code, name);
                FirebaseRoomClient.ackBell(code, uid, id, deviceId);
            });
        } catch (Exception ignored) {}

        // 3. Dengar SOS untuk bilik ini
        try {
            long localLastSeen = SosPrefs.getLastSeenAlertTime(this, code);
            FirebaseRoomClient.fetchServerNowQueued(serverNow -> {
                // Use localLastSeen as the query start, this catches any SOS fired while
                // the service was killed/restarting. Fall back to serverNow only on first
                // ever attach (localLastSeen == 0) to avoid replaying entire history.
                long baseline = localLastSeen > 0L ? localLastSeen : serverNow;
                SosPrefs.bumpBaselineAtListenerStart(LiveRoomTrackingService.this, code, baseline);
                try {
                    android.util.Log.d("SOS_DEBUG", "Multi-room SOS listener attached to: " + code);
                } catch (Exception ignored) {}
                logServiceEnvOnce("listenerAttach");

                watcher.sosListener = FirebaseRoomClient.listenRoomSosSince(code, deviceId, baseline, new FirebaseRoomClient.RoomSosHandler() {
                    @Override
                    public void onSos(String senderUid, String senderName, String roomId, long createdAtMs, String alertId, String status) {
                        try {
                            android.util.Log.d("SOS_DEBUG", "onSos entered in service: room=" + code + " id=" + alertId + " senderUid=" + senderUid + " createdAt=" + createdAtMs + " permissions=" + (watcher.permissions != null ? watcher.permissions.allowSosAlarm : "null"));
                        } catch (Exception ignored) {}

                        if (watcher.permissions != null && !watcher.permissions.allowSosAlarm) {
                            try { android.util.Log.d("SOS_DEBUG", "onSos dropped: allowSosAlarm is false"); } catch (Exception ignored) {}
                            return;
                        }
                        String from = String.valueOf(senderUid == null ? "" : senderUid).trim();
                        String nameSafe = String.valueOf(senderName == null ? "" : senderName).trim();
                        String sosId = String.valueOf(alertId == null ? "" : alertId).trim();
                        long createdAt = Math.max(0L, createdAtMs);

                        com.google.firebase.auth.FirebaseUser cu = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                        String currentUid = (cu != null && cu.getUid() != null) ? cu.getUid().trim() : (uid != null ? uid.trim() : com.example.resqtap.utils.UserPrefs.getUid(LiveRoomTrackingService.this));

                        if (!currentUid.isEmpty() && currentUid.equals(from)) {
                            try { android.util.Log.d("SOS_DEBUG", "onSos dropped: sender is self (currentUid=" + currentUid + ")"); } catch (Exception ignored) {}
                            return;
                        }

                        if (com.example.resqtap.sos.SendingAlarmActivity.isSendingAlarm) {
                            try { android.util.Log.d("SOS_DEBUG", "onSos dropped: device is active SOS sender"); } catch (Exception ignored) {}
                            return;
                        }

                        if (!SosPrefs.tryMarkHandled(LiveRoomTrackingService.this, code, sosId, createdAt)) {
                            try { android.util.Log.d("SOS_DEBUG", "onSos dropped: tryMarkHandled returned false for alertId=" + sosId); } catch (Exception ignored) {}
                            return;
                        }

                        try {
                            android.util.Log.d("SOS_DEBUG", "SOS alert received in service for room: " + code + " id=" + sosId + " status=" + status);
                        } catch (Exception ignored) {}
                        logServiceEnvOnce("handleSos");
                        try { NotificationHelper.ensureSosChannel(LiveRoomTrackingService.this); } catch (Exception ignored) {}

                        try {
                            UserPrefs.setPendingSosFocus(LiveRoomTrackingService.this, code, from, sosId, createdAt);
                        } catch (Exception ignored) {}

                        try {
                            long until = (createdAt > 0L ? createdAt : System.currentTimeMillis()) + 20_000L;
                            UserPrefs.setSosUiBlink(LiveRoomTrackingService.this, code, from, sosId, until);
                        } catch (Exception ignored) {}

                        NotificationHelper.showSosAlert(LiveRoomTrackingService.this, code, nameSafe, from, sosId);
                        VibrateManager.startEmergency10s(LiveRoomTrackingService.this, sosId);
                        SosAudioManager.start10s(LiveRoomTrackingService.this, sosId);

                        // Lancarkan skrin SosAlarmActivity secara terus jika peranti aktif/skrin hidup
                        try {
                            Intent alarmIntent = new Intent(LiveRoomTrackingService.this, com.example.resqtap.sos.SosAlarmActivity.class);
                            alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            alarmIntent.putExtra(com.example.resqtap.sos.SosAlarmActivity.EXTRA_ROOM_CODE, code);
                            alarmIntent.putExtra(com.example.resqtap.sos.SosAlarmActivity.EXTRA_ROOM_NAME, code);
                            alarmIntent.putExtra(com.example.resqtap.sos.SosAlarmActivity.EXTRA_SENDER_NAME, nameSafe);
                            alarmIntent.putExtra(com.example.resqtap.sos.SosAlarmActivity.EXTRA_SENDER_UID, from);
                            alarmIntent.putExtra(com.example.resqtap.sos.SosAlarmActivity.EXTRA_ALERT_ID, sosId);
                            LiveRoomTrackingService.this.startActivity(alarmIntent);
                        } catch (Exception e) {
                            android.util.Log.e("SOS_DEBUG", "Failed to start SosAlarmActivity directly: " + e.getMessage());
                        }

                        try {
                            android.util.Log.d("SOS_DEBUG", "LiveRoomTrackingService: SOS alarm started, will keep ringing until user snoozes.");
                        } catch (Exception ignored) {}
                    }

                    @Override
                    public void onSosCancelled(String senderUid, String roomId, String alertId, long cancelledAtMs) {
                        String sosId = String.valueOf(alertId == null ? "" : alertId).trim();
                        try {
                            android.util.Log.d("SOS_DEBUG", "SOS cancelled received for room " + code + ": " + sosId);
                        } catch (Exception ignored) {}
                        logServiceEnvOnce("handleCancel");

                        VibrateManager.stopAll(LiveRoomTrackingService.this);
                        SosAudioManager.stopAll();
                        if (!sosId.isEmpty()) {
                            try {
                                NotificationHelper.cancelSos(LiveRoomTrackingService.this, sosId);
                            } catch (Exception ignored) {}
                        }
                        try {
                            UserPrefs.clearSosUiBlinkIfAlert(LiveRoomTrackingService.this, code, sosId);
                        } catch (Exception ignored) {}

                        // Hantar broadcast pembatalan kepada SosAlarmActivity
                        try {
                            Intent cancelIntent = new Intent("com.example.resqtap.SOS_CANCELLED");
                            cancelIntent.putExtra("alertId", sosId);
                            cancelIntent.putExtra("roomId", code);
                            cancelIntent.putExtra("reason", "cancelled");
                            cancelIntent.setPackage(getPackageName());
                            sendBroadcast(cancelIntent);
                        } catch (Exception e) {
                            android.util.Log.e("SOS_DEBUG", "Failed to broadcast SOS_CANCELLED: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onSosResolved(String senderUid, String roomId, String alertId) {
                        String sosId = String.valueOf(alertId == null ? "" : alertId).trim();
                        try {
                            android.util.Log.d("SOS_DEBUG", "SOS resolved received for room " + code + ": " + sosId);
                        } catch (Exception ignored) {}

                        VibrateManager.stopAll(LiveRoomTrackingService.this);
                        SosAudioManager.stopAll();
                        if (!sosId.isEmpty()) {
                            try {
                                NotificationHelper.cancelSos(LiveRoomTrackingService.this, sosId);
                            } catch (Exception ignored) {}
                        }
                        try {
                            UserPrefs.clearSosUiBlinkIfAlert(LiveRoomTrackingService.this, code, sosId);
                        } catch (Exception ignored) {}

                        // Hantar broadcast resolusi kepada SosAlarmActivity
                        try {
                            Intent resolvedIntent = new Intent("com.example.resqtap.SOS_RESOLVED");
                            resolvedIntent.putExtra("alertId", sosId);
                            resolvedIntent.putExtra("roomId", code);
                            resolvedIntent.putExtra("reason", "resolved");
                            resolvedIntent.setPackage(getPackageName());
                            sendBroadcast(resolvedIntent);
                        } catch (Exception e) {
                            android.util.Log.e("SOS_DEBUG", "Failed to broadcast SOS_RESOLVED: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onSosServed(String senderUid, String roomId, String alertId, String servedByName) {
                        // Admin serves case in website - alarm on receiver devices MUST REMAIN ACTIVE until user slides to snooze!
                        try {
                            android.util.Log.d("SOS_DEBUG", "SOS served by " + servedByName + " - keeping alarm active on receiver phone until snoozed.");
                        } catch (Exception ignored) {}
                    }
                });
            });
        } catch (Exception ignored) {}
    }

    /** Buang pemantau dan listener bagi bilik tertentu. */
    private synchronized void removeRoomWatcher(String targetRoom) {
        if (targetRoom == null || targetRoom.trim().isEmpty()) return;
        final String code = targetRoom.trim().toUpperCase(java.util.Locale.ROOT);
        RoomWatcher watcher = roomWatchers.remove(code);
        if (watcher == null) return;

        if (watcher.permissionsListener != null) {
            try {
                FirebaseRoomClient.removeRoomPermissionsListener(code, watcher.permissionsListener);
            } catch (Exception ignored) {}
            watcher.permissionsListener = null;
        }

        if (watcher.bellListener != null) {
            try {
                FirebaseRoomClient.removeBellListener(code, uid, watcher.bellListener);
            } catch (Exception ignored) {}
            watcher.bellListener = null;
        }

        if (watcher.sosListener != null) {
            try {
                FirebaseRoomClient.removeRoomSosListener(code, watcher.sosListener);
            } catch (Exception ignored) {}
            watcher.sosListener = null;
        }
    }

    private static volatile boolean loggedEnv = false;

    private void logServiceEnvOnce(String reason) {
        if (loggedEnv) return;
        loggedEnv = true;
        try {
            boolean notifEnabled = true;
            try {
                notifEnabled = androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled();
            } catch (Exception ignored) {
            }

            boolean postNotifGranted = true;
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                postNotifGranted = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED;
            }

            android.os.Vibrator vib = (android.os.Build.VERSION.SDK_INT >= 31)
                    ? ((android.os.VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE)).getDefaultVibrator()
                    : (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            boolean hasVibrator = vib != null && vib.hasVibrator();

            android.util.Log.d("SOS_DEBUG", "ENV reason=" + reason
                    + " roomId=" + roomCode
                    + " uid=" + uid
                    + " postNotifGranted=" + postNotifGranted
                    + " notifEnabled=" + notifEnabled
                    + " hasVibrator=" + hasVibrator
                    + " sdk=" + android.os.Build.VERSION.SDK_INT);
        } catch (Exception ignored) {
        }
    }

    private void startIncomingCallListener() {
        if (uid == null || uid.trim().isEmpty()) return;
        if (com.example.resqtap.call.IncomingCallManager.getInstance() != null) {
            com.example.resqtap.call.IncomingCallManager.getInstance().startListening(uid);
        }
    }

    private void stopIncomingCallListener() {
        // Do not stop globally if user is still logged in, IncomingCallManager handles lifecycle
    }

    private void triggerVibrate() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                android.os.VibratorManager vm = (android.os.VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
                android.os.Vibrator v = vm == null ? null : vm.getDefaultVibrator();
                if (v == null) return;
                v.vibrate(android.os.VibrationEffect.createOneShot(300, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                android.os.Vibrator v = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (v == null) return;
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(android.os.VibrationEffect.createOneShot(300, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(300);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "ResQTap Active",
                NotificationManager.IMPORTANCE_LOW
        );
        ch.setDescription("Keeps SOS listener active in the background.");
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification(String room) {
        Intent open = new Intent(this, RoomMapActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.putExtra(RoomMapActivity.EXTRA_ROOM_CODE, room == null ? "" : room);
        PendingIntent openPi = PendingIntent.getActivity(
                this,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Intent stop = new Intent(this, LiveRoomTrackingService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(
                this,
                1,
                stop,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        String title = "ResQTap active";
        String text = (room == null || room.trim().isEmpty()) ? "SOS listener active" : ("SOS listener active • Room: " + room.trim());
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_menu_location)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(openPi)
                .addAction(new NotificationCompat.Action(0, "Stop", stopPi))
                .build();
        try {

            n.flags |= Notification.FLAG_NO_CLEAR;
            n.flags |= Notification.FLAG_ONGOING_EVENT;
        } catch (Exception ignored) {
        }
        return n;
    }
}
