package com.example.resqtap.sos;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.resqtap.R;
import com.example.resqtap.home.MainActivity;
import com.example.resqtap.room.FirebaseRoomClient;
import com.example.resqtap.utils.LocaleUtils;
import com.example.resqtap.utils.PermissionUtils;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SendingAlarmActivity
 * Skrin penghantaran SOS moden bertema Pink (mengikut rujukan UI InSafe):
 * - Radar bulatan berpusat dengan logo rasmi ResQTap dan animasi kelip-kelip / breathing pulse
 * - Animasi titik bergerak (...) pada "Emergency Calling..."
 * - Mod senyap pada peranti mangsa (tiada siren memekakkan telinga sendiri)
 * - Pemancaran automatik ke Firebase RTDB (Admin & Room) dengan koordinat GPS terkini
 * - Butang "I'm Safe Now" untuk membatalkan kecemasan
 */
public class SendingAlarmActivity extends AppCompatActivity {

    public static final String EXTRA_TARGET_ROOM = "targetRoom";
    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LNG = "lng";

    /** Bendera global untuk menyekat peranti ini daripada memaparkan skrin penerima SosAlarmActivity sendiri */
    public static volatile boolean isSendingAlarm = false;

    private String targetRoomCode = "";
    private final Map<String, String> activeSosIds = new HashMap<>();
    private final Map<DatabaseReference, ValueEventListener> reservationListeners = new HashMap<>();

    private View viewRadarOuterRing;
    private View viewRadarInnerRing;
    private View cardRadarCenterLogo;
    private View[] avatarNodes;

    private TextView tvCallingTitle;
    private MaterialButton btnSafeNow;

    private final List<AnimatorSet> activeAnimators = new ArrayList<>();
    private boolean isSosCancelled = false;

    private FusedLocationProviderClient fusedLocationClient;
    private double lastKnownLat = 0.0;
    private double lastKnownLng = 0.0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int dotsCount = 0;
    private String baseCallingTitle = "";

    /** Animasi titik bergerak: Calling -> Calling. -> Calling.. -> Calling... */
    private final Runnable dotsRunnable = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed()) return;
            dotsCount = (dotsCount + 1) % 4; // 0, 1, 2, 3
            StringBuilder sb = new StringBuilder(baseCallingTitle);
            for (int i = 0; i < dotsCount; i++) {
                sb.append(".");
            }
            if (tvCallingTitle != null) {
                tvCallingTitle.setText(sb.toString());
            }
            handler.postDelayed(this, 400);
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        Context wrapped = LocaleUtils.wrap(newBase);
        super.attachBaseContext(wrapped == null ? newBase : wrapped);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);

        // Kunci bendera penyiaran aktif
        isSendingAlarm = true;

        // Pastikan skrin hidup dan dipaparkan di atas lockscreen
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

        setContentView(R.layout.activity_sending_alarm);

        Intent intent = getIntent();
        if (intent != null) {
            if (intent.hasExtra(EXTRA_TARGET_ROOM)) {
                targetRoomCode = String.valueOf(intent.getStringExtra(EXTRA_TARGET_ROOM)).trim();
            }
            if (intent.hasExtra(EXTRA_LAT)) {
                lastKnownLat = intent.getDoubleExtra(EXTRA_LAT, 0.0);
            }
            if (intent.hasExtra(EXTRA_LNG)) {
                lastKnownLng = intent.getDoubleExtra(EXTRA_LNG, 0.0);
            }
        }

        bindViews();
        silenceLocalDevice();
        startRadarAnimations();

        // Setiap kali SendingAlarmActivity dipanggil, kita memulakan pemancaran SOS baharu
        UserPrefs.clearActiveSosProgress(this);

        initGpsAndDispatchSos();

        // Tangani butang back sistem
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showSafeConfirmationDialog();
            }
        });

        simulateReserveReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                android.util.Log.d("SendingAlarmActivity", "Broadcast received: SIMULATE_ADMIN_RESERVE");
                simulateAdminReserveForActiveAlerts();
            }
        };
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(simulateReserveReceiver, new android.content.IntentFilter("com.example.resqtap.SIMULATE_ADMIN_RESERVE"), Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(simulateReserveReceiver, new android.content.IntentFilter("com.example.resqtap.SIMULATE_ADMIN_RESERVE"));
            }
        } catch (Exception e) {
            android.util.Log.w("SendingAlarmActivity", "Failed to register simulateReserveReceiver: " + e.getMessage());
        }
    }

    private void bindViews() {
        btnSafeNow = findViewById(R.id.btn_sending_alarm_safe_now);
        tvCallingTitle = findViewById(R.id.tv_sending_alarm_calling_title);

        viewRadarOuterRing = findViewById(R.id.view_radar_outer_ring);
        viewRadarInnerRing = findViewById(R.id.view_radar_inner_ring);
        cardRadarCenterLogo = findViewById(R.id.card_radar_center_logo);

        avatarNodes = new View[]{
                findViewById(R.id.avatar_node_1),
                findViewById(R.id.avatar_node_2),
                findViewById(R.id.avatar_node_3),
                findViewById(R.id.avatar_node_4),
                findViewById(R.id.avatar_node_5),
                findViewById(R.id.avatar_node_6)
        };

        if (cardRadarCenterLogo != null) {
            cardRadarCenterLogo.setClickable(true);
            cardRadarCenterLogo.setFocusable(true);
            cardRadarCenterLogo.setOnLongClickListener(v -> {
                simulateAdminReserveForActiveAlerts();
                return true;
            });
            cardRadarCenterLogo.setOnClickListener(v -> {
                simulateAdminReserveForActiveAlerts();
            });
        }

        if (btnSafeNow != null) {
            btnSafeNow.setOnClickListener(v -> showSafeConfirmationDialog());
        }

        // Mulakan animasi teks "Calling..." dengan titik bergerak
        if (tvCallingTitle != null) {
            String raw = getString(R.string.sending_alarm_calling_title);
            baseCallingTitle = raw.replace("…", "").replace("...", "").trim();
            tvCallingTitle.setText(baseCallingTitle);
            handler.post(dotsRunnable);
        }
    }

    /** Memastikan peranti penghantar kekal senyap (tiada siren/getaran membingitkan pada telefon mangsa) */
    private void silenceLocalDevice() {
        try {
            SosAudioManager.stopAll();
            VibrateManager.stopAll(this);
        } catch (Exception ignored) {}
    }

    /** Memulakan animasi orbit radar (kelip-kelip / breathing pulse) dan apungan avatar */
    private void startRadarAnimations() {
        // 1. Animasi nafas & kelip-kelip untuk gegelung luar
        if (viewRadarOuterRing != null) {
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(viewRadarOuterRing, "scaleX", 0.95f, 1.08f);
            scaleX.setDuration(1600);
            scaleX.setRepeatCount(ValueAnimator.INFINITE);
            scaleX.setRepeatMode(ValueAnimator.REVERSE);

            ObjectAnimator scaleY = ObjectAnimator.ofFloat(viewRadarOuterRing, "scaleY", 0.95f, 1.08f);
            scaleY.setDuration(1600);
            scaleY.setRepeatCount(ValueAnimator.INFINITE);
            scaleY.setRepeatMode(ValueAnimator.REVERSE);

            ObjectAnimator alpha = ObjectAnimator.ofFloat(viewRadarOuterRing, "alpha", 0.40f, 0.95f);
            alpha.setDuration(1600);
            alpha.setRepeatCount(ValueAnimator.INFINITE);
            alpha.setRepeatMode(ValueAnimator.REVERSE);

            AnimatorSet setOuter = new AnimatorSet();
            setOuter.playTogether(scaleX, scaleY, alpha);
            setOuter.start();
            activeAnimators.add(setOuter);
        }

        // 2. Animasi nafas & kelip-kelip untuk gegelung dalam
        if (viewRadarInnerRing != null) {
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(viewRadarInnerRing, "scaleX", 0.94f, 1.10f);
            scaleX.setDuration(1300);
            scaleX.setRepeatCount(ValueAnimator.INFINITE);
            scaleX.setRepeatMode(ValueAnimator.REVERSE);

            ObjectAnimator scaleY = ObjectAnimator.ofFloat(viewRadarInnerRing, "scaleY", 0.94f, 1.10f);
            scaleY.setDuration(1300);
            scaleY.setRepeatCount(ValueAnimator.INFINITE);
            scaleY.setRepeatMode(ValueAnimator.REVERSE);

            ObjectAnimator alpha = ObjectAnimator.ofFloat(viewRadarInnerRing, "alpha", 0.50f, 1.0f);
            alpha.setDuration(1300);
            alpha.setRepeatCount(ValueAnimator.INFINITE);
            alpha.setRepeatMode(ValueAnimator.REVERSE);

            AnimatorSet setInner = new AnimatorSet();
            setInner.playTogether(scaleX, scaleY, alpha);
            setInner.start();
            activeAnimators.add(setInner);
        }

        // 3. Denyutan hub logo app di pusat
        if (cardRadarCenterLogo != null) {
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(cardRadarCenterLogo, "scaleX", 1.0f, 1.06f);
            scaleX.setDuration(950);
            scaleX.setRepeatCount(ValueAnimator.INFINITE);
            scaleX.setRepeatMode(ValueAnimator.REVERSE);

            ObjectAnimator scaleY = ObjectAnimator.ofFloat(cardRadarCenterLogo, "scaleY", 1.0f, 1.06f);
            scaleY.setDuration(950);
            scaleY.setRepeatCount(ValueAnimator.INFINITE);
            scaleY.setRepeatMode(ValueAnimator.REVERSE);

            AnimatorSet setCenter = new AnimatorSet();
            setCenter.playTogether(scaleX, scaleY);
            setCenter.start();
            activeAnimators.add(setCenter);
        }

        // 4. Animasi apungan halus untuk setiap avatar node
        if (avatarNodes != null) {
            for (int i = 0; i < avatarNodes.length; i++) {
                View node = avatarNodes[i];
                if (node == null) continue;

                float translationDelta = (i % 2 == 0) ? 6f : -6f;
                ObjectAnimator transY = ObjectAnimator.ofFloat(node, "translationY", 0f, translationDelta, 0f);
                transY.setDuration(1800 + (i * 200));
                transY.setRepeatCount(ValueAnimator.INFINITE);
                transY.setRepeatMode(ValueAnimator.REVERSE);

                ObjectAnimator nodeScaleX = ObjectAnimator.ofFloat(node, "scaleX", 1.0f, 1.04f, 1.0f);
                nodeScaleX.setDuration(1800 + (i * 200));
                nodeScaleX.setRepeatCount(ValueAnimator.INFINITE);
                nodeScaleX.setRepeatMode(ValueAnimator.RESTART);

                ObjectAnimator nodeScaleY = ObjectAnimator.ofFloat(node, "scaleY", 1.0f, 1.04f, 1.0f);
                nodeScaleY.setDuration(1800 + (i * 200));
                nodeScaleY.setRepeatCount(ValueAnimator.INFINITE);
                nodeScaleY.setRepeatMode(ValueAnimator.RESTART);

                AnimatorSet nodeSet = new AnimatorSet();
                nodeSet.playTogether(transY, nodeScaleX, nodeScaleY);
                nodeSet.start();
                activeAnimators.add(nodeSet);
            }
        }
    }

    private BroadcastReceiver simulateReserveReceiver;

    private void initGpsAndDispatchSos() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, R.string.sos_login_required, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        final String uid = user.getUid();
        final String deviceId = UserPrefs.getOrCreateDeviceId(this);
        final String rawName = UserPrefs.getName(this);
        final String name = (rawName == null || rawName.trim().isEmpty()) ? "User" : rawName.trim();

        final java.util.concurrent.atomic.AtomicBoolean dispatched = new java.util.concurrent.atomic.AtomicBoolean(false);
        final Runnable fallbackDispatch = () -> {
            if (dispatched.compareAndSet(false, true)) {
                android.util.Log.d("SendingAlarmActivity", "Dispatching SOS via fallback coordinates (" + lastKnownLat + ", " + lastKnownLng + ")");
                dispatchAlertToFirebase(uid, deviceId, name, lastKnownLat, lastKnownLng);
            }
        };

        // Pastikan SOS tetap dihantar dalam masa 800ms walaupun GPS mengambil masa lama
        handler.postDelayed(fallbackDispatch, 800);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        if (PermissionUtils.hasAnyLocation(this)) {
            try {
                fusedLocationClient.getLastLocation().addOnSuccessListener(loc -> {
                    if (loc != null) {
                        lastKnownLat = loc.getLatitude();
                        lastKnownLng = loc.getLongitude();
                    }
                    if (dispatched.compareAndSet(false, true)) {
                        handler.removeCallbacks(fallbackDispatch);
                        android.util.Log.d("SendingAlarmActivity", "Dispatching SOS with fresh GPS coordinates (" + lastKnownLat + ", " + lastKnownLng + ")");
                        dispatchAlertToFirebase(uid, deviceId, name, lastKnownLat, lastKnownLng);
                    }
                }).addOnFailureListener(e -> {
                    fallbackDispatch.run();
                });
                return;
            } catch (SecurityException ignored) {}
        }

        fallbackDispatch.run();
    }

    private void dispatchAlertToFirebase(String uid, String dev, String name, double lat, double lng) {
        if (isSosCancelled) return;

        // Sekiranya dipanggil dari bilik spesifik
        if (targetRoomCode != null && !targetRoomCode.trim().isEmpty()) {
            final String code = targetRoomCode.trim().toUpperCase(Locale.ROOT);
            sendSosToRoom(code, uid, dev, name, lat, lng);
            return;
        }

        // Dapatkan semua bilik pengguna; jika tiada, hantar ke DIRECT
        FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                .getReference("userRooms")
                .child(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (isSosCancelled) return;

                    List<String> roomCodes = new ArrayList<>();
                    if (snapshot != null && snapshot.exists()) {
                        for (DataSnapshot child : snapshot.getChildren()) {
                            if (child == null || child.getKey() == null) continue;
                            final String code = child.getKey().trim().toUpperCase(Locale.ROOT);
                            if (!code.isEmpty()) {
                                roomCodes.add(code);
                            }
                        }
                    }

                    if (roomCodes.isEmpty()) {
                        roomCodes.add("DIRECT");
                    }

                    for (String code : roomCodes) {
                        if (isSosCancelled) return;
                        sendSosToRoom(code, uid, dev, name, lat, lng);
                    }
                })
                .addOnFailureListener(e -> {
                    if (isSosCancelled) return;
                    sendSosToRoom("DIRECT", uid, dev, name, lat, lng);
                });
    }

    private void sendSosToRoom(String code, String uid, String dev, String name, double lat, double lng) {
        android.util.Log.d("SendingAlarmActivity", "sendSosToRoom to room: " + code);
        FirebaseRoomClient.sendRoomSosQueued(code, uid, dev, name, lat, lng, id -> {
            String sosId = String.valueOf(id == null ? "" : id).trim();
            if (sosId.isEmpty()) return;

            android.util.Log.d("SendingAlarmActivity", "sendRoomSosQueued registered alertId: " + sosId + " in room: " + code);
            if (isSosCancelled) {
                FirebaseRoomClient.cancelRoomSosQueued(code, sosId, dev);
            } else {
                activeSosIds.put(code, sosId);
                // Hanya perhatikan respon admin; jangan set activeSosProgress sebelum di-reserve
                watchSosCaseReservation(code, sosId);
            }
        });
    }
}
