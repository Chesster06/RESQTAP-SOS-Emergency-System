package com.example.resqtap.sos;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.example.resqtap.R;
import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.room.FirebaseRoomClient;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;
import com.example.resqtap.call.CallSignalingClient;
import com.example.resqtap.call.VideoCallActivity;
import com.example.resqtap.call.VoiceCallActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * SosLivechatActivity
 * Halaman Live Chat Khas untuk Kes SOS Aktif:
 * Menghubungkan mangsa kecemasan terus kepada Responder Admin secara realtime.
 */
public class SosLivechatActivity extends BaseActivity {

    public static final String EXTRA_ROOM_CODE = "roomCode";
    public static final String EXTRA_ALERT_ID = "alertId";
    public static final String EXTRA_RESPONDER_NAME = "responderName";
    public static final String EXTRA_RESPONDER_UID = "responderUid";

    private String roomCode = "";
    private String alertId = "";
    private String responderName = "Administrator";
    private String responderUid = "";
    private String myUid = "";
    private String myName = "User";
    private String myEmail = "";

    private TextView tvLivechatTitle;
    private NestedScrollView messagesScroll;
    private LinearLayout messagesContainer;
    private LinearLayout typingIndicatorContainer;
    private EditText etInput;
    private MaterialButton btnSend;
    private MaterialButton btnCall;
    private View btnBack;

    private DatabaseReference chatRef;
    private Query messagesQuery;
    private ValueEventListener messagesListener;
    private ValueEventListener metaListener;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable userTypingStopRunnable;
    private boolean isUserTypingActive = false;

    private BottomSheetDialog callWaitDialog;
    private DatabaseReference userCallRef;
    private ValueEventListener userCallListener;
    private DatabaseReference adminCallIncomingRef;
    private ValueEventListener adminCallIncomingListener;

    public static void launch(Context context, String roomCode, String alertId, String responderName, String responderUid) {
        if (context == null) return;
        Intent intent = new Intent(context, SosLivechatActivity.class);
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        intent.putExtra(EXTRA_ROOM_CODE, roomCode != null ? roomCode : "");
        intent.putExtra(EXTRA_ALERT_ID, alertId != null ? alertId : "");
        intent.putExtra(EXTRA_RESPONDER_NAME, responderName != null ? responderName : "");
        intent.putExtra(EXTRA_RESPONDER_UID, responderUid != null ? responderUid : "");
        context.startActivity(intent);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null) {
            String r = intent.getStringExtra(EXTRA_ROOM_CODE);
            String a = intent.getStringExtra(EXTRA_ALERT_ID);
            String n = intent.getStringExtra(EXTRA_RESPONDER_NAME);
            String u = intent.getStringExtra(EXTRA_RESPONDER_UID);
            if (r != null && !r.isEmpty()) roomCode = r;
            if (a != null && !a.isEmpty()) alertId = a;
            if (roomCode.isEmpty()) roomCode = UserPrefs.getActiveSosProgressRoom(this);
            if (alertId.isEmpty()) alertId = UserPrefs.getActiveSosProgressAlert(this);
            if (n != null && !n.trim().isEmpty()) responderName = n.trim();
            if (u != null) responderUid = u;
            setupFirebaseChat();
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sos_livechat);

        View root = findViewById(R.id.sos_livechat_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomInset = Math.max(systemBars.bottom, ime.bottom);
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomInset);
            return insets;
        });

        roomCode = getIntent().getStringExtra(EXTRA_ROOM_CODE);
        alertId = getIntent().getStringExtra(EXTRA_ALERT_ID);
        String rName = getIntent().getStringExtra(EXTRA_RESPONDER_NAME);
        String rUid = getIntent().getStringExtra(EXTRA_RESPONDER_UID);

        if (roomCode == null) roomCode = "";
        if (alertId == null) alertId = "";
        if (roomCode.isEmpty()) {
            roomCode = UserPrefs.getActiveSosProgressRoom(this);
        }
        if (alertId.isEmpty()) {
            alertId = UserPrefs.getActiveSosProgressAlert(this);
        }
        if (rName != null && !rName.trim().isEmpty()) responderName = rName.trim();
        if (rUid != null) responderUid = rUid;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            myUid = user.getUid();
            myName = UserPrefs.getName(this);
            if (myName.isEmpty()) myName = user.getDisplayName() != null ? user.getDisplayName() : "User";
            myEmail = user.getEmail() != null ? user.getEmail() : "";
        }

        bindViews();
        setupListeners();
        setupFirebaseChat();
    }

    private void bindViews() {
        btnBack = findViewById(R.id.btn_back_sos_livechat);
        btnCall = findViewById(R.id.btn_call_from_livechat);
        tvLivechatTitle = findViewById(R.id.tv_livechat_title);
        messagesScroll = findViewById(R.id.sos_livechat_scroll);
        messagesContainer = findViewById(R.id.sos_messages_container);
        typingIndicatorContainer = findViewById(R.id.typing_indicator_container);
        etInput = findViewById(R.id.et_sos_chat_input);
        btnSend = findViewById(R.id.btn_sos_chat_send);
    }

    private void setupListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (btnCall != null) {
            btnCall.setOnClickListener(v -> showCallOptionsBottomSheet());
        }

        if (btnSend != null) {
            btnSend.setOnClickListener(v -> sendMessage());
        }

        // Quick chip shortcuts
        bindQuickChip(R.id.chip_quick_1, getString(R.string.sos_livechat_chip_where));
        bindQuickChip(R.id.chip_quick_2, getString(R.string.sos_livechat_chip_eta));
        bindQuickChip(R.id.chip_quick_3, getString(R.string.sos_livechat_chip_safe_location));
        bindQuickChip(R.id.chip_quick_4, getString(R.string.sos_livechat_chip_need_medical));

        // Typing listener
        etInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                dispatchUserTyping(s.length() > 0);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void bindQuickChip(int chipId, String text) {
        View chip = findViewById(chipId);
        if (chip != null) {
            chip.setOnClickListener(v -> {
                etInput.setText(text);
                etInput.setSelection(text.length());
                sendMessage();
            });
        }
    }

    private void setupFirebaseChat() {
        if (myUid.isEmpty()) return;

        String cleanRoom = roomCode != null ? roomCode.trim().toUpperCase(Locale.ROOT) : "";
        String cleanAlert = alertId != null ? alertId.trim() : "";

        if (!cleanRoom.isEmpty() && !cleanAlert.isEmpty()) {
            chatRef = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                    .getReference("rooms")
                    .child(cleanRoom)
                    .child("sosAlerts")
                    .child(cleanAlert)
                    .child("chat");
        } else if (!cleanAlert.isEmpty()) {
            chatRef = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                    .getReference("sosChats")
                    .child(cleanAlert);
        } else {
            chatRef = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                    .getReference("sosChats")
                    .child(myUid);
        }

        // Inisialisasi Meta sekiranya baru
        Map<String, Object> metaUpdates = new HashMap<>();
        String emergencyLabel = cleanRoom.isEmpty() ? getString(R.string.sos_topic_emergency) : cleanRoom;
        metaUpdates.put("topic", getString(R.string.sos_topic_format, emergencyLabel));
        metaUpdates.put("status", "open");
        metaUpdates.put("userUid", myUid);
        metaUpdates.put("userName", myName);
        metaUpdates.put("userEmail", myEmail);
        metaUpdates.put("sosAlertId", cleanAlert);
        metaUpdates.put("roomCode", cleanRoom);
        metaUpdates.put("isSosEmergency", true);
        metaUpdates.put("updatedAt", ServerValue.TIMESTAMP);
        chatRef.child("meta").updateChildren(metaUpdates);

        // Dengar Meta (cth: Typing Status dari Admin Responder)
        metaListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                Boolean adminTyping = snapshot.child("adminTyping").getValue(Boolean.class);
                Long typingAt = snapshot.child("adminTypingAt").getValue(Long.class);
                long now = System.currentTimeMillis();
                boolean isTyping = Boolean.TRUE.equals(adminTyping) && (typingAt != null && (now - typingAt) < 8000L);
                if (typingIndicatorContainer != null) {
                    typingIndicatorContainer.setVisibility(isTyping ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        chatRef.child("meta").addValueEventListener(metaListener);

        // Dengar Mesej
        messagesQuery = chatRef.child("messages").orderByChild("createdAt").limitToLast(120);
        messagesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                renderMessages(snapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        messagesQuery.addValueEventListener(messagesListener);
    }

    private boolean isSending = false;

    private void renderMessages(DataSnapshot snapshot) {
        if (messagesContainer == null) return;
        messagesContainer.removeAllViews();

        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
        java.util.Set<String> seenIds = new java.util.HashSet<>();
        String lastText = null;
        String lastSender = null;
        long lastTime = 0L;

        for (DataSnapshot child : snapshot.getChildren()) {
            String key = child.getKey();
            if (key != null && !seenIds.add(key)) {
                continue;
            }

            String text = child.child("text").getValue(String.class);
            if (text == null || text.trim().isEmpty()) {
                // Check attachment
                if (child.child("attachment").exists()) {
                    String attachName = child.child("attachment").child("name").getValue(String.class);
                    text = getString(R.string.sos_attachment_file_format, attachName != null ? attachName : "");
                } else {
                    continue;
                }
            }

            String sender = child.child("sender").getValue(String.class);
            String senderName = child.child("senderName").getValue(String.class);
            Long createdAt = child.child("createdAt").getValue(Long.class);
            long timeVal = createdAt != null ? createdAt : 0L;

            // Elak paparan duplikasi mesej berulang dalam masa singkat
            if (text.equals(lastText) && (sender != null && sender.equals(lastSender)) && Math.abs(timeVal - lastTime) < 10000L) {
                continue;
            }
            lastText = text;
            lastSender = sender;
            lastTime = timeVal;

            String timeStr = createdAt != null && createdAt > 0 ? sdf.format(new Date(createdAt)) : "";

            boolean isMe = "user".equalsIgnoreCase(sender);
            addMessageBubble(text, senderName, timeStr, isMe);
        }

        // Scroll ke bawah
        handler.postDelayed(() -> {
            if (messagesScroll != null) {
                messagesScroll.fullScroll(View.FOCUS_DOWN);
            }
        }, 80);
    }

    private void addMessageBubble(String text, String senderName, String timeStr, boolean isMe) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, 14);
        row.setLayoutParams(rowParams);
        row.setGravity(isMe ? Gravity.END : Gravity.START);

        // Bubble Box
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        int maxW = (int) (getResources().getDisplayMetrics().widthPixels * 0.78f);
        bubble.setLayoutParams(bubbleParams);
        bubble.setPadding(32, 22, 32, 22);

        if (isMe) {
            // User: Pink Bubble
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setColor(Color.parseColor("#E60067"));
            gd.setCornerRadii(new float[]{36, 36, 36, 36, 8, 8, 36, 36});
            bubble.setBackground(gd);
        } else {
            // Admin: Slate/White Bubble
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setColor(Color.parseColor("#F1F5F9"));
            gd.setStroke(2, Color.parseColor("#E2E8F0"));
            gd.setCornerRadii(new float[]{36, 36, 36, 36, 36, 36, 8, 8});
            bubble.setBackground(gd);

            // Sender Label
            TextView tvSender = new TextView(this);
            tvSender.setText("Administrator");
            tvSender.setTextSize(11f);
            tvSender.setTextColor(Color.parseColor("#B80053"));
            tvSender.setTypeface(null, Typeface.BOLD);
            tvSender.setPadding(0, 0, 0, 6);
            bubble.addView(tvSender);
        }

        // Message text
        TextView tvMsg = new TextView(this);
        tvMsg.setText(text);
        tvMsg.setTextSize(14.5f);
        tvMsg.setMaxWidth(maxW);
        tvMsg.setLineSpacing(4f, 1f);
        tvMsg.setTextColor(isMe ? Color.WHITE : Color.parseColor("#1E293B"));
        bubble.addView(tvMsg);

        // Time text
        if (!timeStr.isEmpty()) {
            TextView tvTime = new TextView(this);
            tvTime.setText(timeStr);
            tvTime.setTextSize(10f);
            tvTime.setTextColor(isMe ? Color.parseColor("#FFD1E3") : Color.parseColor("#94A3B8"));
            tvTime.setGravity(Gravity.END);
            tvTime.setPadding(0, 6, 0, 0);
            bubble.addView(tvTime);
        }

        row.addView(bubble);
        messagesContainer.addView(row);
    }

    private void sendMessage() {
        if (isSending) return;
        String text = etInput.getText().toString().trim();
        if (text.isEmpty()) return;

        isSending = true;
        if (btnSend != null) btnSend.setEnabled(false);
        etInput.setText("");

        if (chatRef == null) {
            isSending = false;
            if (btnSend != null) btnSend.setEnabled(true);
            return;
        }

        DatabaseReference newMsgRef = chatRef.child("messages").push();
        String msgId = newMsgRef.getKey();

        Map<String, Object> msg = new HashMap<>();
        msg.put("id", msgId);
        msg.put("text", text);
        msg.put("sender", "user");
        msg.put("senderUid", myUid);
        msg.put("senderName", myName);
        msg.put("createdAt", ServerValue.TIMESTAMP);

        newMsgRef.setValue(msg).addOnCompleteListener(task -> {
            isSending = false;
            if (btnSend != null) btnSend.setEnabled(true);
        });

        // Update meta
        Map<String, Object> metaUpdates = new HashMap<>();
        metaUpdates.put("lastMessage", text);
        metaUpdates.put("lastSender", "user");
        metaUpdates.put("lastSenderUid", myUid);
        metaUpdates.put("status", "open");
        metaUpdates.put("userTyping", false);
        metaUpdates.put("updatedAt", ServerValue.TIMESTAMP);
        chatRef.child("meta").updateChildren(metaUpdates);

        // Clear typing
        dispatchUserTyping(false);
    }

    private void dispatchUserTyping(boolean isTyping) {
        if (chatRef == null || myUid.isEmpty()) return;

        if (isTyping) {
            if (!isUserTypingActive) {
                isUserTypingActive = true;
                Map<String, Object> updates = new HashMap<>();
                updates.put("userTyping", true);
                updates.put("userTypingAt", ServerValue.TIMESTAMP);
                chatRef.child("meta").updateChildren(updates);
            }
            if (userTypingStopRunnable != null) handler.removeCallbacks(userTypingStopRunnable);
            userTypingStopRunnable = () -> {
                isUserTypingActive = false;
                chatRef.child("meta").child("userTyping").setValue(false);
            };
            handler.postDelayed(userTypingStopRunnable, 4000L);
        } else {
            if (isUserTypingActive) {
                isUserTypingActive = false;
                chatRef.child("meta").child("userTyping").setValue(false);
            }
            if (userTypingStopRunnable != null) handler.removeCallbacks(userTypingStopRunnable);
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
        if (roomCode.isEmpty() && alertId.isEmpty()) {
            Toast.makeText(this, R.string.sos_livechat_no_active_case, Toast.LENGTH_SHORT).show();
            return;
        }

        // Mark that user is actively awaiting response for outgoing SOS call
        CallSignalingClient.setAwaitingSosResponse(true);

        // Hantar permintaan panggilan ke Firebase RTDB
        DatabaseReference rtdb = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL).getReference();

        Map<String, Object> callReq = new HashMap<>();
        callReq.put("status", "ringing");
        callReq.put("callType", callType);
        callReq.put("alertId", alertId);
        callReq.put("roomId", roomCode);
        callReq.put("callerUid", myUid);
        callReq.put("callerName", myName);
        callReq.put("timestamp", ServerValue.TIMESTAMP);

        // Update dalam kes SOS dan adminCalls/incoming
        if (!roomCode.isEmpty() && !alertId.isEmpty()) {
            rtdb.child("rooms").child(roomCode).child("sosAlerts").child(alertId).child("callRequest").setValue(callReq);
        }
        rtdb.child("adminCalls").child("incoming").setValue(callReq);

        showCallWaitBottomSheet(callType);
    }

    private void showCallWaitBottomSheet(String callType) {
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
                    Toast.makeText(SosLivechatActivity.this, R.string.sos_call_admin_busy, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        adminCallIncomingRef.addValueEventListener(adminCallIncomingListener);

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
                        CallSignalingClient.setAwaitingSosResponse(false);
                        stopCallListening();
                        if (callWaitDialog != null && callWaitDialog.isShowing()) {
                            callWaitDialog.dismiss();
                        }

                        CallSignalingClient.getInstance().acceptCall(myUid, callId);

                        Intent intent = new Intent(SosLivechatActivity.this, "voice".equalsIgnoreCase(serverCallType) ? VoiceCallActivity.class : VideoCallActivity.class);
                        intent.putExtra("callId", callId);
                        intent.putExtra("callerName", responderName);
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
            rtdb.child("rooms").child(roomCode).child("sosAlerts").child(alertId).child("callRequest").child("status").setValue("cancelled");
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCallListening();
        if (callWaitDialog != null && callWaitDialog.isShowing()) {
            callWaitDialog.dismiss();
            callWaitDialog = null;
        }
        if (chatRef != null && myUid != null && !myUid.isEmpty()) {
            chatRef.child("meta").child("userTyping").setValue(false);
        }
        if (messagesQuery != null && messagesListener != null) {
            messagesQuery.removeEventListener(messagesListener);
        }
        if (chatRef != null && metaListener != null) {
            chatRef.child("meta").removeEventListener(metaListener);
        }
        if (userTypingStopRunnable != null) {
            handler.removeCallbacks(userTypingStopRunnable);
        }
    }
}
