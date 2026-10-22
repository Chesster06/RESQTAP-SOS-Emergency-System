package com.example.resqtap.sos;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.resqtap.R;
import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.home.MainActivity;
import com.example.resqtap.room.FirebaseRoomClient;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import android.view.LayoutInflater;
import com.example.resqtap.call.CallSignalingClient;
import com.example.resqtap.call.VideoCallActivity;
import com.example.resqtap.call.VoiceCallActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ServerValue;

/**
 * SosProgressActivity
 * Paparan Kemajuan SOS Masa Nyata:
 * Menunjukkan status tindakan admin setelah kes SOS di-reserve (Dispatched, En Route, On Scene, Resolved).
 */
public class SosProgressActivity extends BaseActivity {

    public static final String EXTRA_ROOM_CODE = "roomCode";
    public static final String EXTRA_ALERT_ID = "alertId";

    private String roomCode = "";
    private String alertId = "";

    private TextView tvRoomCode;
    private TextView tvStatusMain;
    private TextView tvUpdatedTime;
    private TextView tvResponderName;
    private TextView tvProgressNote;

    private FrameLayout step1Circle;
    private FrameLayout step2Circle;
    private FrameLayout step3Circle;
    private FrameLayout step4Circle;

    private ImageView step1Icon;
    private ImageView step2Icon;
    private ImageView step3Icon;
    private ImageView step4Icon;

    private View stepLine12;
    private View stepLine23;
    private View stepLine34;

    private TextView tvStep1Title;
    private TextView tvStep2Title;
    private TextView tvStep3Title;
    private TextView tvStep4Title;

    private TextView tvStep1Desc;
    private TextView tvStep2Desc;
    private TextView tvStep3Desc;
    private TextView tvStep4Desc;

    private MaterialButton btnCancelSos;
    private MaterialButton btnCallPlaceholder;
    private MaterialButton btnMessagePlaceholder;
    private View btnBack;
    private boolean isResolved = false;

    private String currentResponderName = "Administrator";
    private String currentResponderUid = "";

    private DatabaseReference alertRef;
    private ValueEventListener alertListener;

    private com.google.android.material.bottomsheet.BottomSheetDialog callWaitDialog;
    private DatabaseReference userCallRef;
    private ValueEventListener userCallListener;
    private DatabaseReference adminCallIncomingRef;
    private ValueEventListener adminCallIncomingListener;
    private boolean autoClearScheduled = false;
    private boolean hadExistingSnapshot = false;
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable staleAlertTimeoutRunnable;

    public static void launch(Context context, String roomCode, String alertId) {
        if (context == null) return;
        Intent intent = new Intent(context, SosProgressActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(EXTRA_ROOM_CODE, roomCode);
        intent.putExtra(EXTRA_ALERT_ID, alertId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        androidx.activity.EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sos_progress);

        View root = findViewById(R.id.sos_progress_root);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        roomCode = getIntent().getStringExtra(EXTRA_ROOM_CODE);
        alertId = getIntent().getStringExtra(EXTRA_ALERT_ID);

        if (roomCode == null) roomCode = "";
        if (alertId == null) alertId = "";

        if (roomCode.isEmpty()) {
            roomCode = UserPrefs.getActiveSosProgressRoom(this);
        }
        if (alertId.isEmpty()) {
            alertId = UserPrefs.getActiveSosProgressAlert(this);
        }

        if (!roomCode.isEmpty() && !alertId.isEmpty()) {
            UserPrefs.setActiveSosProgress(this, roomCode, alertId);
        }

        bindViews();
        setupListeners();
        attachAlertListener();
    }

    private void bindViews() {
        btnBack = findViewById(R.id.btn_back_sos_progress);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (isResolved) {
                    cleanUpResolvedAlertNode();
                }
                finish();
            });
            btnBack.setVisibility(View.GONE);
        }

        tvRoomCode = findViewById(R.id.tv_sos_room_code);
        tvStatusMain = findViewById(R.id.tv_sos_status_main);
        tvUpdatedTime = findViewById(R.id.tv_sos_updated_time);
        tvResponderName = findViewById(R.id.tv_sos_responder_name);
        tvProgressNote = findViewById(R.id.tv_sos_progress_note);

        step1Circle = findViewById(R.id.sos_step_1_circle);
        step2Circle = findViewById(R.id.sos_step_2_circle);
        step3Circle = findViewById(R.id.sos_step_3_circle);
        step4Circle = findViewById(R.id.sos_step_4_circle);

        step1Icon = findViewById(R.id.sos_step_1_icon);
        step2Icon = findViewById(R.id.sos_step_2_icon);
        step3Icon = findViewById(R.id.sos_step_3_icon);
        step4Icon = findViewById(R.id.sos_step_4_icon);

        stepLine12 = findViewById(R.id.sos_step_line_1_2);
        stepLine23 = findViewById(R.id.sos_step_line_2_3);
        stepLine34 = findViewById(R.id.sos_step_line_3_4);

        tvStep1Title = findViewById(R.id.tv_sos_step_1_title);
        tvStep2Title = findViewById(R.id.tv_sos_step_2_title);
        tvStep3Title = findViewById(R.id.tv_sos_step_3_title);
        tvStep4Title = findViewById(R.id.tv_sos_step_4_title);

        tvStep1Desc = findViewById(R.id.tv_sos_step_1_desc);
        tvStep2Desc = findViewById(R.id.tv_sos_step_2_desc);
        tvStep3Desc = findViewById(R.id.tv_sos_step_3_desc);
        tvStep4Desc = findViewById(R.id.tv_sos_step_4_desc);

        btnCancelSos = findViewById(R.id.btn_cancel_sos_progress);
        btnCallPlaceholder = findViewById(R.id.btn_sos_call_placeholder);
        btnMessagePlaceholder = findViewById(R.id.btn_sos_message_placeholder);

        if (tvRoomCode != null) {
            tvRoomCode.setVisibility(View.GONE);
        }
    }

    private void setupListeners() {
        if (btnCancelSos != null) {
            btnCancelSos.setOnClickListener(v -> cancelSosByVictim());
        }

        if (btnCallPlaceholder != null) {
            btnCallPlaceholder.setOnClickListener(v -> showCallOptionsBottomSheet());
        }

        if (btnMessagePlaceholder != null) {
            btnMessagePlaceholder.setOnClickListener(v -> {
                String targetRoom = (roomCode != null && !roomCode.isEmpty()) ? roomCode : UserPrefs.getActiveSosProgressRoom(SosProgressActivity.this);
                String targetAlert = (alertId != null && !alertId.isEmpty()) ? alertId : UserPrefs.getActiveSosProgressAlert(SosProgressActivity.this);
                SosLivechatActivity.launch(SosProgressActivity.this, targetRoom, targetAlert, currentResponderName, currentResponderUid);
            });
        }

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isResolved) {
                    cleanUpResolvedAlertNode();
                    finish();
                } else {
                    Toast.makeText(SosProgressActivity.this, R.string.sos_progress_cannot_exit, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void cancelSosByVictim() {
        UserPrefs.clearActiveSosProgress(this);
        String dev = UserPrefs.getOrCreateDeviceId(this);
        if (!roomCode.isEmpty() && !alertId.isEmpty()) {
            FirebaseRoomClient.cancelRoomSosQueued(roomCode, alertId, dev);
        }

        com.google.firebase.auth.FirebaseUser cu = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (cu != null) {
            FirebaseRoomClient.cancelAllActiveSosForUser(cu.getUid(), dev);
        }

        Toast.makeText(this, R.string.sos_progress_cancelled, Toast.LENGTH_SHORT).show();
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(mainIntent);
        finish();
    }

    private void attachAlertListener() {
        if (roomCode.isEmpty() || alertId.isEmpty()) return;

        alertRef = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                .getReference("rooms")
                .child(roomCode.toUpperCase(Locale.ROOT))
                .child("sosAlerts")
                .child(alertId);

        if (staleAlertTimeoutRunnable != null) {
            timeoutHandler.removeCallbacks(staleAlertTimeoutRunnable);
        }
        staleAlertTimeoutRunnable = () -> {
            if (!hadExistingSnapshot) {
                UserPrefs.clearActiveSosProgress(SosProgressActivity.this);
                finish();
            }
        };
        timeoutHandler.postDelayed(staleAlertTimeoutRunnable, 2500);

        alertListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot == null || !snapshot.exists()) {
                    if (hadExistingSnapshot) {
                        UserPrefs.clearActiveSosProgress(SosProgressActivity.this);
                        Intent intent = new Intent(SosProgressActivity.this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                        finish();
                    }
                    return;
                }

                hadExistingSnapshot = true;
                if (staleAlertTimeoutRunnable != null) {
                    timeoutHandler.removeCallbacks(staleAlertTimeoutRunnable);
                }

                String status = String.valueOf(snapshot.child("status").getValue() == null ? "active" : snapshot.child("status").getValue());
                boolean isCancelled = "cancelled".equalsIgnoreCase(status)
                        || snapshot.child("cancelledAt").exists()
                        || snapshot.child("cancelledByAdmin").exists()
                        || snapshot.child("cancelledClientAt").exists()
                        || (snapshot.child("active").exists() && Boolean.FALSE.equals(snapshot.child("active").getValue(Boolean.class)));
                Long stepVal = snapshot.child("progressStep").getValue(Long.class);
                int step = stepVal != null ? stepVal.intValue() : 1;
                boolean resolved = "resolved".equalsIgnoreCase(status) || snapshot.child("resolvedAt").exists() || step >= 4;

                if (isCancelled) {
                    UserPrefs.clearActiveSosProgress(SosProgressActivity.this);
                    Toast.makeText(SosProgressActivity.this, R.string.sos_progress_cancelled, Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(SosProgressActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                    return;
                }

                if (resolved) {
                    UserPrefs.clearActiveSosProgress(SosProgressActivity.this);
                    isResolved = true;
                    step = 4;
                    scheduleAutoClearOnResolved();
                } else {
                    isResolved = false;
                    UserPrefs.setActiveSosProgress(SosProgressActivity.this, roomCode, alertId);
                }

                String rawResponder = String.valueOf(snapshot.child("servedByName").getValue() == null ? "" : snapshot.child("servedByName").getValue()).trim();
                String responderName = formatResponderName(rawResponder);
                currentResponderName = responderName;
                if (snapshot.hasChild("servedBy") && snapshot.child("servedBy").getValue() != null) {
                    currentResponderUid = String.valueOf(snapshot.child("servedBy").getValue()).trim();
                }
                String progressStatus = String.valueOf(snapshot.child("progressStatus").getValue() == null ? "Admin Dispatched" : snapshot.child("progressStatus").getValue());
                String notes = String.valueOf(snapshot.child("progressNotes").getValue() == null ? "Emergency assistance is assigned and responders have been notified." : snapshot.child("progressNotes").getValue());

                Long updatedTimeMs = snapshot.child("updatedAt").getValue(Long.class);
                if (updatedTimeMs == null) updatedTimeMs = snapshot.child("servedAt").getValue(Long.class);
                if (updatedTimeMs == null) updatedTimeMs = snapshot.child("createdAt").getValue(Long.class);

                updateUi(step, progressStatus, responderName, notes, updatedTimeMs);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (error != null && (error.getCode() == DatabaseError.PERMISSION_DENIED || !hadExistingSnapshot)) {
                    UserPrefs.clearActiveSosProgress(SosProgressActivity.this);
                    finish();
                }
            }
        };

        alertRef.addValueEventListener(alertListener);
    }

    private void updateUi(int step, String statusText, String responder, String notes, Long timeMs) {
        if (tvStatusMain != null) tvStatusMain.setText(statusText);
        if (tvResponderName != null) tvResponderName.setText(responder);
        if (tvProgressNote != null) tvProgressNote.setText(notes);

        if (tvUpdatedTime != null && timeMs != null && timeMs > 0L) {
            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
            tvUpdatedTime.setText(getString(R.string.sos_progress_updated, sdf.format(new Date(timeMs))));
        }

        renderStepper(step);
    }

    private String formatResponderName(String raw) {
        return "Administrator";
    }

    private void renderStepper(int step) {
        // Colors
        int green = ContextCompat.getColor(this, R.color.brand_primary); // Brand theme color
        int activePink = 0xFFE60067;
        int doneGreen = 0xFF10B981;
        int pendingGray = 0xFFCBD5E1;
        int textDark = ContextCompat.getColor(this, R.color.text_primary);
        int textMuted = ContextCompat.getColor(this, R.color.text_secondary);

        // Step 1
        if (step >= 1) {
            step1Circle.setBackgroundResource(step > 1 ? R.drawable.bg_step_circle_done : R.drawable.bg_step_circle_active);
            step1Icon.setColorFilter(0xFFFFFFFF);
            tvStep1Title.setTextColor(textDark);
            tvStep1Desc.setTextColor(textMuted);
        }

        // Line 1-2
        stepLine12.setBackgroundColor(step > 1 ? doneGreen : pendingGray);

        // Step 2
        if (step >= 2) {
            step2Circle.setBackgroundResource(step > 2 ? R.drawable.bg_step_circle_done : R.drawable.bg_step_circle_active);
            step2Icon.setColorFilter(0xFFFFFFFF);
            tvStep2Title.setTextColor(textDark);
            tvStep2Desc.setTextColor(textMuted);
        } else {
            step2Circle.setBackgroundResource(R.drawable.bg_step_circle_pending);
            step2Icon.setColorFilter(0xFF64748B);
            tvStep2Title.setTextColor(textMuted);
            tvStep2Desc.setTextColor(textMuted);
        }

        // Line 2-3
        stepLine23.setBackgroundColor(step > 2 ? doneGreen : pendingGray);

        // Step 3
        if (step >= 3) {
            step3Circle.setBackgroundResource(step > 3 ? R.drawable.bg_step_circle_done : R.drawable.bg_step_circle_active);
            step3Icon.setColorFilter(0xFFFFFFFF);
            tvStep3Title.setTextColor(textDark);
            tvStep3Desc.setTextColor(textMuted);
        } else {
            step3Circle.setBackgroundResource(R.drawable.bg_step_circle_pending);
            step3Icon.setColorFilter(0xFF64748B);
            tvStep3Title.setTextColor(textMuted);
            tvStep3Desc.setTextColor(textMuted);
        }

        // Line 3-4
        stepLine34.setBackgroundColor(step > 3 ? doneGreen : pendingGray);

        // Step 4
        if (step >= 4) {
            isResolved = true;
            step4Circle.setBackgroundResource(R.drawable.bg_step_circle_done);
            step4Icon.setColorFilter(0xFFFFFFFF);
            tvStep4Title.setTextColor(textDark);
            tvStep4Desc.setTextColor(textMuted);
            if (btnCancelSos != null) btnCancelSos.setVisibility(View.GONE);
            if (btnBack != null) btnBack.setVisibility(View.VISIBLE);
        } else {
            isResolved = false;
            step4Circle.setBackgroundResource(R.drawable.bg_step_circle_pending);
            step4Icon.setColorFilter(0xFF64748B);
            tvStep4Title.setTextColor(textMuted);
            tvStep4Desc.setTextColor(textMuted);
            if (btnCancelSos != null) btnCancelSos.setVisibility(View.VISIBLE);
            if (btnBack != null) btnBack.setVisibility(View.GONE);
        }
    }

    /**
     * Paparkan Pilihan Panggilan (Video Call / Voice Call)
     */
    private void showCallOptionsBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_ResQTap_BottomSheetDialog);
        dialog.setCanceledOnTouchOutside(false);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_sos_call_options, null);
        dialog.setContentView(view);

        View optVideo = view.findViewById(R.id.option_call_video);
        View optVoice = view.findViewById(R.id.option_call_voice);
        View btnClose = view.findViewById(R.id.btn_close_call_options);

        if (optVideo != null) {
            optVideo.setOnClickListener(v -> {
                dialog.dismiss();
                initiateEmergencyCall("video");
            });
        }

        if (optVoice != null) {
            optVoice.setOnClickListener(v -> {
                dialog.dismiss();
                initiateEmergencyCall("voice");
            });
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    private void initiateEmergencyCall(String callType) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String myUid = user != null ? user.getUid() : "";
        String myName = UserPrefs.getName(this);
        if (myName.isEmpty() && user != null && user.getDisplayName() != null) {
            myName = user.getDisplayName();
        }
        if (myName.isEmpty()) myName = "Mangsa SOS";

        // Mark that user is actively awaiting response for outgoing SOS call
        CallSignalingClient.setAwaitingSosResponse(true);

        DatabaseReference rtdb = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL).getReference();

        Map<String, Object> callReq = new HashMap<>();
        callReq.put("status", "ringing");
        callReq.put("callType", callType);
        callReq.put("alertId", alertId);
        callReq.put("roomId", roomCode);
        callReq.put("callerUid", myUid);
        callReq.put("callerName", myName);
        callReq.put("timestamp", ServerValue.TIMESTAMP);

        if (!roomCode.isEmpty() && !alertId.isEmpty()) {
            rtdb.child("rooms").child(roomCode.toUpperCase(Locale.ROOT)).child("sosAlerts").child(alertId).child("callRequest").setValue(callReq);
        }
        rtdb.child("adminCalls").child("incoming").setValue(callReq);

        // Paparkan dialog menunggu panggilan disambungkan
        showCallWaitBottomSheet(callType, myUid);
    }

    private void showCallWaitBottomSheet(String callType, String myUid) {
        stopCallListening();

        callWaitDialog = new BottomSheetDialog(this, R.style.Theme_ResQTap_BottomSheetDialog);
        callWaitDialog.setCanceledOnTouchOutside(false);
        callWaitDialog.setCancelable(false);
        com.google.android.material.bottomsheet.BottomSheetBehavior<?> waitBehavior = callWaitDialog.getBehavior();
        if (waitBehavior != null) {
            waitBehavior.setHideable(false);
        }
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_sos_call_options, null);
        callWaitDialog.setContentView(view);

        // Customize the view for ringing state
        TextView tvTitle = view.findViewById(R.id.tv_call_options_title);
        TextView tvSub = view.findViewById(R.id.tv_call_options_subtitle);
        View optVideo = view.findViewById(R.id.option_call_video);
        View optVoice = view.findViewById(R.id.option_call_voice);
        View btnClose = view.findViewById(R.id.btn_close_call_options);

        if (tvTitle != null) {
            tvTitle.setText(R.string.sos_call_connecting_title);
        }
        if (tvSub != null) {
            tvSub.setText("voice".equals(callType) ? R.string.sos_call_connecting_wait_voice : R.string.sos_call_connecting_wait_video);
        }
        if (optVideo != null) optVideo.setVisibility(View.GONE);
        if (optVoice != null) optVoice.setVisibility(View.GONE);

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                cancelCallRequest();
                if (callWaitDialog != null) callWaitDialog.dismiss();
            });
        }

        callWaitDialog.setOnDismissListener(d -> {
            CallSignalingClient.setAwaitingSosResponse(false);
            stopCallListening();
        });
        callWaitDialog.show();

        DatabaseReference rtdb = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL).getReference();

        // Dengar status jika responder admin menolak panggilan
        adminCallIncomingRef = rtdb.child("adminCalls").child("incoming");
        adminCallIncomingListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                String status = snapshot.child("status").getValue(String.class);
                if ("declined".equalsIgnoreCase(status)) {
                    stopCallListening();
                    CallSignalingClient.setAwaitingSosResponse(false);
                    if (callWaitDialog != null && callWaitDialog.isShowing()) {
                        callWaitDialog.dismiss();
                    }
                    android.widget.Toast.makeText(SosProgressActivity.this, R.string.sos_call_admin_busy, android.widget.Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        adminCallIncomingRef.addValueEventListener(adminCallIncomingListener);

        // Dengar userCalls/<myUid>/currentCall sekiranya admin menjawab dan mula WebRTC
        if (myUid != null && !myUid.isEmpty()) {
            userCallRef = rtdb.child("userCalls").child(myUid).child("currentCall");

            userCallListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (!snapshot.exists()) return;
                    String status = snapshot.child("status").getValue(String.class);
                    String callId = snapshot.child("callId").getValue(String.class);
                    String serverCallType = snapshot.child("callType").getValue(String.class);
                    if (serverCallType == null || serverCallType.isEmpty()) serverCallType = callType;

                    if ("ringing".equalsIgnoreCase(status) && callId != null && !callId.isEmpty()) {
                        // Admin telah menjawab dan menghantar panggilan balik!
                        CallSignalingClient.setAwaitingSosResponse(false);
                        stopCallListening();
                        if (callWaitDialog != null && callWaitDialog.isShowing()) {
                            callWaitDialog.dismiss();
                        }

                        // Accept call
                        CallSignalingClient.getInstance().acceptCall(myUid, callId);

                        // Launch video/voice call activity
                        Intent intent = new Intent(SosProgressActivity.this, "voice".equalsIgnoreCase(serverCallType) ? VoiceCallActivity.class : VideoCallActivity.class);
                        intent.putExtra("callId", callId);
                        intent.putExtra("callerName", currentResponderName);
                        intent.putExtra("callType", serverCallType);
                        startActivity(intent);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            };
            userCallRef.addValueEventListener(userCallListener);
        }
    }

    private void cancelCallRequest() {
        CallSignalingClient.setAwaitingSosResponse(false);
        DatabaseReference rtdb = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL).getReference();
        rtdb.child("adminCalls").child("incoming").child("status").setValue("cancelled");
        if (!roomCode.isEmpty() && !alertId.isEmpty()) {
            rtdb.child("rooms").child(roomCode.toUpperCase(Locale.ROOT)).child("sosAlerts").child(alertId).child("callRequest").child("status").setValue("cancelled");
        }
    }

    private void stopCallListening() {
        if (userCallRef != null && userCallListener != null) {
            userCallRef.removeEventListener(userCallListener);
            userCallListener = null;
            userCallRef = null;
        }
        if (adminCallIncomingRef != null && adminCallIncomingListener != null) {
            adminCallIncomingRef.removeEventListener(adminCallIncomingListener);
            adminCallIncomingListener = null;
            adminCallIncomingRef = null;
        }
    }

    private void scheduleAutoClearOnResolved() {
        if (autoClearScheduled) return;
        autoClearScheduled = true;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            cleanUpResolvedAlertNode();
        }, 15000);
    }

    private void cleanUpResolvedAlertNode() {
        String code = (roomCode != null && !roomCode.isEmpty()) ? roomCode : UserPrefs.getActiveSosProgressRoom(this);
        String id = (alertId != null && !alertId.isEmpty()) ? alertId : UserPrefs.getActiveSosProgressAlert(this);
        if (code != null && id != null && !code.isEmpty() && !id.isEmpty()) {
            FirebaseRoomClient.deleteSosAlert(code, id);
        }
    }

    @Override
    protected void onDestroy() {
        if (staleAlertTimeoutRunnable != null) {
            timeoutHandler.removeCallbacks(staleAlertTimeoutRunnable);
            staleAlertTimeoutRunnable = null;
        }
        if (isResolved) {
            cleanUpResolvedAlertNode();
        }
        super.onDestroy();
        stopCallListening();
        if (callWaitDialog != null && callWaitDialog.isShowing()) {
            callWaitDialog.dismiss();
            callWaitDialog = null;
        }
        if (alertRef != null && alertListener != null) {
            alertRef.removeEventListener(alertListener);
            alertListener = null;
        }
    }
}
