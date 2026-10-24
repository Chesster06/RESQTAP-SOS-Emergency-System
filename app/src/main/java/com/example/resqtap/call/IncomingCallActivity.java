package com.example.resqtap.call;
import com.example.resqtap.R;

import com.example.resqtap.utils.AvatarUtils;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import com.example.resqtap.app.BaseActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;


/**
 * IncomingCallActivity
 * Skrin Call Masuk: popup atas lockscreen dengan ringtone & vibration bila ada call masuk.
 */
public class IncomingCallActivity extends BaseActivity {

    private String callId;
    private String callerName;
    private String callerPhotoUrl;
    private String callType = "video";
    private Ringtone ringtone;
    private Vibrator vibrator;
    private boolean isHandled = false;
    private com.google.firebase.database.ValueEventListener callStatusListener;

    private final BroadcastReceiver cancelReceiver = new BroadcastReceiver() {
    @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.resqtap.CALL_CANCELLED".equals(intent.getAction())) {
                String cancelledCallId = intent.getStringExtra("callId");
                if (callId != null && callId.equals(cancelledCallId)) {
                    finishAndRemoveTask();
                }
            }
        }
    };

    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) {
                km.requestDismissKeyguard(this, null);
            }
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }

        setContentView(R.layout.activity_incoming_call);

        callId = getIntent().getStringExtra("callId");
        callerName = getIntent().getStringExtra("callerName");
        callerPhotoUrl = getIntent().getStringExtra("callerPhotoUrl");
        String incomingType = getIntent().getStringExtra("callType");
        if (incomingType != null && !incomingType.trim().isEmpty()) {
            callType = incomingType.trim().toLowerCase();
        }

        if (callId == null || callId.isEmpty()) {
            finish();
            return;
        }

        TextView tvName = findViewById(R.id.caller_name);
        if (callerName != null && !callerName.isEmpty()) {
            tvName.setText(callerName);
        }

        TextView tvStatus = findViewById(R.id.caller_status);
        if (tvStatus != null) {
            tvStatus.setText("voice".equals(callType) ? "Incoming Voice Call..." : "Incoming Video Call...");
        }

        ShapeableImageView ivPhoto = findViewById(R.id.caller_photo);
        if (callerPhotoUrl != null && !callerPhotoUrl.isEmpty()) {
            AvatarUtils.applyAvatar(ivPhoto, "", callerPhotoUrl, R.drawable.ic_avatar);
        }

        FloatingActionButton btnAccept = findViewById(R.id.btn_accept_call);
        FloatingActionButton btnReject = findViewById(R.id.btn_reject_call);
        com.google.android.material.button.MaterialButton btnSilentChat = findViewById(R.id.btn_silent_chat);

        btnAccept.setOnClickListener(v -> acceptCall());
        btnReject.setOnClickListener(v -> rejectCall());
        if (btnSilentChat != null) {
            btnSilentChat.setOnClickListener(v -> declineWithSilentChat());
        }

        startRinging();
        listenForCallEnd();
        CallNotificationHelper.dismissIncomingCallNotification(this);

        IntentFilter filter = new IntentFilter("com.example.resqtap.CALL_CANCELLED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(cancelReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(cancelReceiver, filter);
        }
    }

    private void declineWithSilentChat() {
        if (isHandled) return;
        isHandled = true;
        stopRinging();
        CallNotificationHelper.dismissIncomingCallNotification(this);

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        CallSignalingClient.getInstance().declineWithChat(uid, callId);

        // Hantar mesej automatik atau buka perbualan terus
        try {
            boolean isSosActive = com.example.resqtap.utils.UserPrefs.isSosProgressActive(this);
            String activeSosRoom = isSosActive ? com.example.resqtap.utils.UserPrefs.getActiveSosProgressRoom(this) : "";
            String activeSosAlert = isSosActive ? com.example.resqtap.utils.UserPrefs.getActiveSosProgressAlert(this) : "";
            String myName = com.example.resqtap.utils.UserPrefs.getName(this);
            if (myName == null || myName.isEmpty()) myName = "User";

            String chatReason = "🔇 " + getString(R.string.call_declined_silent_reason);

            // 1. Sentiasa hantar mesej ke sosChats/uid agar kedua-dua pihak (Android SOS Live Support & Web Admin SOS Livechat) dapat berkomunikasi terus
            if (uid != null && !uid.isEmpty()) {
                com.google.firebase.database.DatabaseReference sosChatRef = com.google.firebase.database.FirebaseDatabase.getInstance(com.example.resqtap.room.FirebaseRoomClient.DATABASE_URL)
                        .getReference("sosChats").child(uid);
                com.google.firebase.database.DatabaseReference newMsgRef = sosChatRef.child("messages").push();
                String msgId = newMsgRef.getKey();
                java.util.Map<String, Object> msg = new java.util.HashMap<>();
                msg.put("id", msgId);
                msg.put("text", chatReason);
                msg.put("sender", "user");
                msg.put("senderUid", uid);
                msg.put("senderName", myName);
                msg.put("createdAt", com.google.firebase.database.ServerValue.TIMESTAMP);
                newMsgRef.setValue(msg);

                java.util.Map<String, Object> metaUpdates = new java.util.HashMap<>();
                metaUpdates.put("lastMessage", chatReason);
                metaUpdates.put("lastSender", "user");
                metaUpdates.put("lastSenderUid", uid);
                metaUpdates.put("status", "open");
                metaUpdates.put("userUid", uid);
                metaUpdates.put("userName", myName);
                metaUpdates.put("topic", "SOS Emergency Call Livechat");
                metaUpdates.put("isSosEmergency", true);
                metaUpdates.put("updatedAt", com.google.firebase.database.ServerValue.TIMESTAMP);
                sosChatRef.child("meta").updateChildren(metaUpdates);
            }

            // 2. Jika ada sesi amaran SOS bilik yang aktif, hantar juga salinan mesej ke chat bilik tersebut
            if (activeSosAlert != null && !activeSosAlert.trim().isEmpty() && activeSosRoom != null && !activeSosRoom.trim().isEmpty()) {
                String cleanRoom = activeSosRoom.trim().toUpperCase(java.util.Locale.ROOT);
                com.google.firebase.database.DatabaseReference roomChatRef = com.google.firebase.database.FirebaseDatabase.getInstance(com.example.resqtap.room.FirebaseRoomClient.DATABASE_URL)
                        .getReference("rooms").child(cleanRoom).child("sosAlerts").child(activeSosAlert.trim()).child("chat");
                com.google.firebase.database.DatabaseReference newMsgRef2 = roomChatRef.child("messages").push();
                String msgId2 = newMsgRef2.getKey();
                java.util.Map<String, Object> msg2 = new java.util.HashMap<>();
                msg2.put("id", msgId2);
                msg2.put("text", chatReason);
                msg2.put("sender", "user");
                msg2.put("senderUid", uid);
                msg2.put("senderName", myName);
                msg2.put("createdAt", com.google.firebase.database.ServerValue.TIMESTAMP);
                newMsgRef2.setValue(msg2);

                java.util.Map<String, Object> meta2 = new java.util.HashMap<>();
                meta2.put("lastMessage", chatReason);
                meta2.put("lastSender", "user");
                meta2.put("lastSenderUid", uid);
                meta2.put("status", "open");
                meta2.put("updatedAt", com.google.firebase.database.ServerValue.TIMESTAMP);
                roomChatRef.child("meta").updateChildren(meta2);

                com.example.resqtap.sos.SosLivechatActivity.launch(this, activeSosRoom, activeSosAlert, callerName, "");
            } else {
                // Buka terus SOS Live Support aktiviti dengan sasaran sosChats/uid
                com.example.resqtap.sos.SosLivechatActivity.launch(this, "", "", callerName, "");
            }
        } catch (Exception ignored) {
        }

        finishAndRemoveTask();
    }

    private void acceptCall() {
        if (isHandled) return;
        isHandled = true;
        stopRinging();
        CallNotificationHelper.dismissIncomingCallNotification(this);

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        CallSignalingClient.getInstance().acceptCall(uid, callId);

        Intent intent = new Intent(this, "voice".equals(callType) ? VoiceCallActivity.class : VideoCallActivity.class);
        intent.putExtra("callId", callId);
        intent.putExtra("callerName", callerName);
        intent.putExtra("callerPhotoUrl", callerPhotoUrl);
        intent.putExtra("callType", callType);
        startActivity(intent);

        finish();
    }

    private void rejectCall() {
        if (isHandled) return;
        isHandled = true;
        stopRinging();
        CallNotificationHelper.dismissIncomingCallNotification(this);

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        CallSignalingClient.getInstance().rejectCall(uid, callId);

        finishAndRemoveTask();
    }

    private void startRinging() {
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            ringtone = RingtoneManager.getRingtone(getApplicationContext(), uri);
            if (ringtone != null) {
                ringtone.play();
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) vibrator = vm.getDefaultVibrator();
            } else {
                vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 1000, 1000};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 1));
                } else {
                    vibrator.vibrate(pattern, 1);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("SOS_DEBUG", "Error playing ringtone", e);
        }
    }

    private void stopRinging() {
        try {
            if (ringtone != null && ringtone.isPlaying()) {
                ringtone.stop();
            }
            if (vibrator != null) {
                vibrator.cancel();
            }
        } catch (Exception ignored) {}
    }

    // SEKSYEN: ONDESTROY
    /** Pembersihan memori, buang listener, dan tutup sambungan. */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRinging();
        CallNotificationHelper.dismissIncomingCallNotification(this);

        if (callStatusListener != null && FirebaseAuth.getInstance().getCurrentUser() != null) {
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            com.google.firebase.database.FirebaseDatabase.getInstance().getReference("userCalls")
                    .child(uid).child("currentCall").child("status").removeEventListener(callStatusListener);
        }

        try {
            unregisterReceiver(cancelReceiver);
        } catch (Exception ignored) {}

        if (!isHandled) {
            String uid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
            CallSignalingClient.getInstance().rejectCall(uid, callId);
        }
    }

    /** Pantau status panggilan sekiranya ditamatkan dari jauh. */
    private void listenForCallEnd() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        com.google.firebase.database.DatabaseReference statusRef = com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("userCalls").child(uid).child("currentCall").child("status");

        callStatusListener = new com.google.firebase.database.ValueEventListener() {
            /** Callback apabila data Firebase Realtime Database berubah. */
    @Override
            public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot snapshot) {
                String status = snapshot.getValue(String.class);
                if ("ended".equals(status)) {
                    finishAndRemoveTask();
                }
            }
            /** Callback sekiranya operasi Firebase dibatalkan atau gagal. */
    @Override
            public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError error) {}
        };
        statusRef.addValueEventListener(callStatusListener);
    }
}

