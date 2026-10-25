package com.example.resqtap.sos;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.resqtap.R;
import com.example.resqtap.home.MainActivity;
import com.example.resqtap.sos.SosAudioManager;
import com.example.resqtap.sos.VibrateManager;
import com.example.resqtap.notification.NotificationHelper;
import com.example.resqtap.room.RoomMapActivity;
import com.example.resqtap.room.FirebaseRoomClient;
import com.example.resqtap.friend.FirebaseFriendClient;
import com.example.resqtap.utils.AvatarUtils;
import com.example.resqtap.utils.LocaleUtils;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * SosAlarmActivity
 * Skrin UI Penggera SOS Masuk: dipaparkan di atas skrin kunci apabila penggera dicetuskan.
 * Menyediakan "Slide to snooze alarm" slider untuk mematikan siren dan getaran dengan serta-merta.
 */
public class SosAlarmActivity extends AppCompatActivity {

    public static final String EXTRA_ROOM_CODE = "roomCode";
    public static final String EXTRA_ROOM_NAME = "roomName";
    public static final String EXTRA_SENDER_NAME = "senderName";
    public static final String EXTRA_SENDER_UID = "senderUid";
    public static final String EXTRA_ALERT_ID = "alertId";

    private String roomCode = "";
    private String roomName = "";
    private String senderName = "";
    private String senderUid = "";
    private String alertId = "";

    private DatabaseReference activeSosRef = null;
    private ValueEventListener activeSosListener = null;

    private View pulseRing;
    private CircularProgressView circularProgressDial;
    private TextView tvSender;
    private TextView tvSubtitle;
    private TextView tvRoom;
    private TextView tvStatus;
    private View cardSlideContainer;
    private SeekBar seekSnooze;
    private View layoutSnoozedActions;
    private MaterialButton btnDismiss;
    private MaterialButton btnViewMap;

    private ObjectAnimator pulseAnimator;
    private ObjectAnimator progressAnimator;
    private boolean isSnoozed = false;

    private final BroadcastReceiver sosStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            String action = intent.getAction();

            if ("com.example.resqtap.SOS_CANCELLED".equals(action)) {
                String cancelledAlertId = intent.getStringExtra("alertId");
                if (alertId.isEmpty() || alertId.equals(cancelledAlertId)) {
                    Log.d("SOS_DEBUG", "SosAlarmActivity auto-snooze: SOS was cancelled by admin/sender.");
                    snoozeAlarmWithReason(getString(R.string.sos_alarm_cancelled_auto_snoozed));
                }
            } else if ("com.example.resqtap.SOS_RESOLVED".equals(action)) {
                String resolvedAlertId = intent.getStringExtra("alertId");
                if (alertId.isEmpty() || alertId.equals(resolvedAlertId)) {
                    Log.d("SOS_DEBUG", "SosAlarmActivity auto-snooze: SOS was resolved by admin.");
                    snoozeAlarmWithReason(getString(R.string.sos_alarm_resolved_auto_snoozed));
                }
            } else if (SosSnoozeReceiver.ACTION_SOS_SNOOZED.equals(action)) {
                String snoozedAlertId = intent.getStringExtra(SosSnoozeReceiver.EXTRA_ALERT_ID);
                if (alertId.isEmpty() || alertId.equals(snoozedAlertId)) {
                    Log.d("SOS_DEBUG", "SosAlarmActivity: Alarm snoozed externally.");
                    snoozeAlarm();
                }
            }
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        Context wrapped = LocaleUtils.wrap(newBase);
        super.attachBaseContext(wrapped == null ? newBase : wrapped);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Konfigurasi paparan di atas skrin kunci (Show on Lockscreen & Turn Screen On)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) {
                km.requestDismissKeyguard(this, null);
            }
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            );
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_sos_alarm);

        // Ekstrak maklumat dari Intent
        Intent intent = getIntent();
        if (intent != null) {
            roomCode = String.valueOf(intent.getStringExtra(EXTRA_ROOM_CODE) == null ? "" : intent.getStringExtra(EXTRA_ROOM_CODE)).trim();
            roomName = String.valueOf(intent.getStringExtra(EXTRA_ROOM_NAME) == null ? "" : intent.getStringExtra(EXTRA_ROOM_NAME)).trim();
            senderName = String.valueOf(intent.getStringExtra(EXTRA_SENDER_NAME) == null ? "" : intent.getStringExtra(EXTRA_SENDER_NAME)).trim();
            senderUid = String.valueOf(intent.getStringExtra(EXTRA_SENDER_UID) == null ? "" : intent.getStringExtra(EXTRA_SENDER_UID)).trim();
            alertId = String.valueOf(intent.getStringExtra(EXTRA_ALERT_ID) == null ? "" : intent.getStringExtra(EXTRA_ALERT_ID)).trim();
        }

        // KESELAMATAN: Jangan sekali-kali paparkan skrin penerima SosAlarmActivity kepada penghantar penggera sendiri!
        com.google.firebase.auth.FirebaseUser cu = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        String myUid = (cu != null && cu.getUid() != null) ? cu.getUid().trim() : com.example.resqtap.utils.UserPrefs.getUid(this);
        if (SendingAlarmActivity.isSendingAlarm || (!senderUid.isEmpty() && senderUid.equals(myUid))) {
            Log.d("SOS_DEBUG", "SosAlarmActivity: Sender is self or SOS broadcast in progress. Refusing to open receiver alarm screen.");
            finish();
            return;
        }

        bindViews();
        setupData();
        setupSlider();

        // Daftarkan listener pembatalan / resolve / snooze dari luar
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.example.resqtap.SOS_CANCELLED");
        filter.addAction("com.example.resqtap.SOS_RESOLVED");
        filter.addAction(SosSnoozeReceiver.ACTION_SOS_SNOOZED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sosStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(sosStatusReceiver, filter);
        }

        attachSosAlertListener();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null) {
            roomCode = String.valueOf(intent.getStringExtra(EXTRA_ROOM_CODE) == null ? "" : intent.getStringExtra(EXTRA_ROOM_CODE)).trim();
            roomName = String.valueOf(intent.getStringExtra(EXTRA_ROOM_NAME) == null ? "" : intent.getStringExtra(EXTRA_ROOM_NAME)).trim();
            senderName = String.valueOf(intent.getStringExtra(EXTRA_SENDER_NAME) == null ? "" : intent.getStringExtra(EXTRA_SENDER_NAME)).trim();
            senderUid = String.valueOf(intent.getStringExtra(EXTRA_SENDER_UID) == null ? "" : intent.getStringExtra(EXTRA_SENDER_UID)).trim();
            alertId = String.valueOf(intent.getStringExtra(EXTRA_ALERT_ID) == null ? "" : intent.getStringExtra(EXTRA_ALERT_ID)).trim();
        }
        isSnoozed = false;
        if (cardSlideContainer != null) cardSlideContainer.setVisibility(View.VISIBLE);
        if (layoutSnoozedActions != null) layoutSnoozedActions.setVisibility(View.GONE);
        if (tvStatus != null) tvStatus.setVisibility(View.GONE);
        if (seekSnooze != null) seekSnooze.setProgress(0);
        setupData();
        startDialProgressAnimation();
        attachSosAlertListener();
    }

    private ImageView ivSenderAvatar;
    private TextView tvSenderRoomInfo;

    private void bindViews() {
        pulseRing = findViewById(R.id.sos_alarm_dial_base);
        circularProgressDial = findViewById(R.id.circular_progress_dial);
        tvSender = findViewById(R.id.tv_sos_alarm_sender);
        ivSenderAvatar = findViewById(R.id.iv_sos_sender_avatar);
        tvSenderRoomInfo = findViewById(R.id.tv_sos_sender_room_info);
        tvStatus = findViewById(R.id.tv_sos_alarm_status);
        cardSlideContainer = findViewById(R.id.card_slide_container);
        seekSnooze = findViewById(R.id.sos_alarm_seek);
        layoutSnoozedActions = findViewById(R.id.layout_snoozed_actions);
        btnDismiss = findViewById(R.id.btn_sos_dismiss);
        btnViewMap = findViewById(R.id.btn_sos_view_map);

        startDialProgressAnimation();
    }

    private void setupData() {
        String displayName = senderName.isEmpty() ? getString(R.string.sos_alert_title) : senderName;
        if (tvSender != null) {
            tvSender.setText(displayName);
        }

        if (tvSenderRoomInfo != null) {
            String roomDisplay = !roomName.isEmpty() ? roomName : roomCode;
            if (!roomDisplay.isEmpty()) {
                tvSenderRoomInfo.setText(getString(R.string.sos_alarm_room_format, roomDisplay));
                tvSenderRoomInfo.setVisibility(View.VISIBLE);
            } else {
                tvSenderRoomInfo.setVisibility(View.GONE);
            }
        }

        // Muat turun avatar / info pengguna jika senderUid tersedia
        if (!senderUid.isEmpty() && ivSenderAvatar != null) {
            FirebaseFriendClient.fetchUserDetailQueued(senderUid, detail -> {
                if (isFinishing() || isDestroyed()) return;
                if (detail != null) {
                    if (senderName.isEmpty() && !detail.name.isEmpty() && tvSender != null) {
                        tvSender.setText(detail.name);
                    }
                    AvatarUtils.applyAvatar(ivSenderAvatar, "", "", "", R.drawable.ic_avatar);
                }
            });
        }

        btnDismiss.setOnClickListener(v -> finishAndRemoveTask());

        btnViewMap.setOnClickListener(v -> {
            Intent mapIntent = new Intent(this, RoomMapActivity.class);
            mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (!roomCode.isEmpty()) mapIntent.putExtra(RoomMapActivity.EXTRA_ROOM_CODE, roomCode);
            if (!senderUid.isEmpty()) mapIntent.putExtra(RoomMapActivity.EXTRA_FOCUS_UID, senderUid);
            if (!alertId.isEmpty()) mapIntent.putExtra(RoomMapActivity.EXTRA_SOS_ALERT_ID, alertId);
            startActivity(mapIntent);
            finish();
        });
    }

    private void setupSlider() {
        seekSnooze.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && progress >= 90 && !isSnoozed) {
                    // Berjaya slide ke hujung -> Snooze penggera!
                    snoozeAlarm();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (!isSnoozed && seekBar.getProgress() < 90) {
                    // Kembalikan slider ke posisi awal jika belum capai hujung
                    seekBar.setProgress(0);
                }
            }
        });
    }

    private void snoozeAlarm() {
        snoozeAlarmWithReason(null);
    }

    private void snoozeAlarmWithReason(String reasonMessage) {
        isSnoozed = true;
        Log.d("SOS_DEBUG", "SosAlarmActivity: snoozeAlarm invoked, reason=" + reasonMessage);

        // 1. Matikan audio dan getaran serta-merta pada semua layer
        try {
            SosAudioManager.stopAll();
        } catch (Exception ignored) {}

        try {
            VibrateManager.stopAll(this);
        } catch (Exception ignored) {}

        // 2. Padamkan notifikasi SOS jika ada
        if (!alertId.isEmpty()) {
            try {
                NotificationHelper.cancelSos(this, alertId);
            } catch (Exception ignored) {}
        }

        // 3. Kemas kini UI kepada mod Snoozed
        onAlarmSnoozedUi(reasonMessage);
    }

    private void onAlarmSnoozedUi() {
        onAlarmSnoozedUi(null);
    }

    private void onAlarmSnoozedUi(String customMessage) {
        isSnoozed = true;
        if (tvStatus != null) {
            if (customMessage != null && !customMessage.trim().isEmpty()) {
                tvStatus.setText(customMessage);
            } else {
                tvStatus.setText(R.string.sos_alarm_snoozed_success);
            }
            tvStatus.setTextColor(0xFF10B981); // Emerald Green
            tvStatus.setVisibility(View.VISIBLE);
        }

        if (cardSlideContainer != null) {
            cardSlideContainer.setVisibility(View.GONE);
        }

        if (layoutSnoozedActions != null) {
            layoutSnoozedActions.setVisibility(View.VISIBLE);
        }

        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        if (progressAnimator != null) {
            progressAnimator.cancel();
            progressAnimator = null;
        }
        if (pulseRing != null) {
            pulseRing.setScaleX(1.0f);
            pulseRing.setScaleY(1.0f);
            pulseRing.setAlpha(1.0f);
        }
    }

    private void startDialProgressAnimation() {
        if (circularProgressDial == null) return;
        if (progressAnimator != null) {
            progressAnimator.cancel();
            progressAnimator = null;
        }

        // Lengkok dial merah sentiasa PENUH (100% / 1.0f) dan TIDAK berputar
        circularProgressDial.setProgress(1.0f);
    }

    private void attachSosAlertListener() {
        detachSosAlertListener();
        if (roomCode.isEmpty() || alertId.isEmpty()) return;
        try {
            activeSosRef = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                    .getReference("rooms")
                    .child(roomCode.toUpperCase(java.util.Locale.ROOT))
                    .child("sosAlerts")
                    .child(alertId);

            activeSosListener = new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    if (snapshot == null || !snapshot.exists() || isFinishing() || isDestroyed()) return;

                    Boolean served = snapshot.child("served").getValue(Boolean.class);
                    String servedBy = snapshot.child("servedBy").getValue(String.class);
                    boolean isServed = (served != null && served) || (servedBy != null && !servedBy.trim().isEmpty());

                    String status = String.valueOf(snapshot.child("status").getValue() == null ? "" : snapshot.child("status").getValue());
                    Long progressStepVal = snapshot.child("progressStep").getValue(Long.class);
                    long progressStep = progressStepVal == null ? 0L : progressStepVal;

                    // 1. Kes Resolved oleh Admin
                    boolean isResolved = "resolved".equalsIgnoreCase(status)
                            || snapshot.child("resolvedAt").exists()
                            || progressStep >= 4;

                    if (isResolved) {
                        Log.d("SOS_DEBUG", "SosAlarmActivity: SOS resolved detected via RTDB -> auto-snooze");
                        snoozeAlarmWithReason(getString(R.string.sos_alarm_resolved_auto_snoozed));
                        return;
                    }

                    // 2. Kes Dibatal oleh Admin atau Sender
                    boolean cancelled = "cancelled".equalsIgnoreCase(status)
                            || snapshot.child("cancelledByAdmin").exists()
                            || (!isServed && (snapshot.child("cancelledAt").exists() || snapshot.child("cancelledClientAt").exists()));
                    if (cancelled) {
                        Log.d("SOS_DEBUG", "SosAlarmActivity: SOS cancelled detected via RTDB -> auto-snooze");
                        snoozeAlarmWithReason(getString(R.string.sos_alarm_cancelled_auto_snoozed));
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {}
            };

            activeSosRef.addValueEventListener(activeSosListener);
        } catch (Exception e) {
            Log.e("SOS_DEBUG", "Failed to attach SosAlertListener: " + e.getMessage());
        }
    }

    private void detachSosAlertListener() {
        if (activeSosRef != null && activeSosListener != null) {
            try {
                activeSosRef.removeEventListener(activeSosListener);
            } catch (Exception ignored) {}
        }
        activeSosRef = null;
        activeSosListener = null;
    }

    private void startPulseAnimation() {
        // Disabled: dial circle remains static without resizing / pulsing animation
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detachSosAlertListener();
        try {
            unregisterReceiver(sosStatusReceiver);
        } catch (Exception ignored) {}

        try {
            SosAudioManager.stopAll();
        } catch (Exception ignored) {}

        try {
            VibrateManager.stopAll(this);
        } catch (Exception ignored) {}

        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        if (progressAnimator != null) {
            progressAnimator.cancel();
            progressAnimator = null;
        }
    }
}
