package com.example.resqtap.chat;

import com.example.resqtap.R;
import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.call.CallSignalingClient;
import com.example.resqtap.call.VideoCallActivity;
import com.example.resqtap.call.VoiceCallActivity;
import com.example.resqtap.room.FirebaseRoomClient;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

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

import java.io.ByteArrayOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * LivechatActivity (Report Livechat)
 * Halaman Live Chat Khas untuk Laporan ResQTap:
 * Reka bentuk padan 1:1 dengan SOS Livechat, khas untuk pertanyaan dan pengurusan laporan.
 */
public class LivechatActivity extends BaseActivity {

    private TextView tvLivechatTitle;
    private TextView tvLivechatSubtitle;
    private NestedScrollView messagesScroll;
    private LinearLayout messagesContainer;
    private LinearLayout typingIndicatorContainer;
    private EditText inputView;
    private MaterialButton btnSend;
    private View btnAttach;
    private View btnBack;
    private View btnDeleteChat;
    private View btnCall;
    private TextView composerAttachmentLabel;

    private DatabaseReference chatRef;
    private Query messagesQuery;
    private ValueEventListener messagesListener;
    private ValueEventListener metaListener;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable userTypingStopRunnable;
    private boolean isUserTypingActive = false;
    private boolean isSending = false;

    private BottomSheetDialog callWaitDialog;
    private DatabaseReference userCallRef;
    private ValueEventListener userCallListener;
    private DatabaseReference adminCallIncomingRef;
    private ValueEventListener adminCallIncomingListener;

    private ActivityResultLauncher<String[]> pickAttachment;
    private ActivityResultLauncher<Void> takeCameraPhoto;

    private Uri selectedAttachmentUri;
    private byte[] selectedAttachmentBytes;
    private String selectedAttachmentName = "";
    private String selectedAttachmentMime = "";
    private long selectedAttachmentSize = 0L;

    private String uid = "";
    private String userName = "";
    private String userEmail = "";
    private String currentTopic = "Pertanyaan Laporan";
    private String reportId = "";
    private String reportCategory = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_livechat);

        View root = findViewById(R.id.livechat_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomInset = Math.max(systemBars.bottom, ime.bottom);
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomInset);
            return insets;
        });

        pickAttachment = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) return;
                    try {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {}
                    setSelectedAttachment(uri);
                }
        );

        takeCameraPhoto = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                bitmap -> {
                    if (bitmap == null) return;
                    setSelectedCameraAttachment(bitmap);
                }
        );

        String repId = getIntent().getStringExtra("reportId");
        String repCat = getIntent().getStringExtra("reportCategory");
        if (repId != null && !repId.trim().isEmpty()) {
            reportId = repId.trim();
        }
        if (repCat != null && !repCat.trim().isEmpty()) {
            reportCategory = repCat.trim();
        }

        bindViews();
        setupListeners();
        setupUserAndChat();
    }

    private void bindViews() {
        btnBack = findViewById(R.id.btn_back);
        btnDeleteChat = findViewById(R.id.btn_delete_livechat);
        if (btnDeleteChat != null) {
            btnDeleteChat.setVisibility(View.GONE);
        }
        btnCall = findViewById(R.id.btn_call_from_livechat);
        tvLivechatTitle = findViewById(R.id.tv_livechat_title);
        tvLivechatSubtitle = findViewById(R.id.tv_livechat_subtitle);
        messagesScroll = findViewById(R.id.livechat_scroll);
        messagesContainer = findViewById(R.id.livechat_messages);
        typingIndicatorContainer = findViewById(R.id.typing_indicator_container);
        inputView = findViewById(R.id.livechat_input);
        btnSend = findViewById(R.id.livechat_send);
        btnAttach = findViewById(R.id.livechat_attach);
        composerAttachmentLabel = findViewById(R.id.livechat_composer_attachment_label);

        if (tvLivechatTitle != null) {
            tvLivechatTitle.setText("Report Livechat");
        }

        if (reportId != null && !reportId.isEmpty()) {
            String cleanRepId = reportId.length() > 8 ? reportId.substring(0, 8) : reportId;
            currentTopic = "Laporan #" + cleanRepId + (!reportCategory.isEmpty() ? " - " + reportCategory : "");
            if (tvLivechatSubtitle != null) {
                tvLivechatSubtitle.setText("Perbualan langsung berkenaan Laporan #" + cleanRepId);
                tvLivechatSubtitle.setVisibility(View.VISIBLE);
            }
        } else if (tvLivechatSubtitle != null) {
            tvLivechatSubtitle.setText("Perbualan langsung bersama Responder Admin");
            tvLivechatSubtitle.setVisibility(View.VISIBLE);
        }
    }

    private void setupListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (btnCall != null) {
            btnCall.setOnClickListener(v -> showCallOptionsBottomSheet());
        }

        if (btnSend != null) {
            btnSend.setOnClickListener(v -> sendSupportMessage());
        }

        if (btnAttach != null) {
            btnAttach.setOnClickListener(v -> chooseAttachment());
        }

        if (inputView != null) {
            inputView.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendSupportMessage();
                    return true;
                }
                return false;
            });

            userTypingStopRunnable = () -> dispatchUserTyping(false);

            inputView.addTextChangedListener(new TextWatcher() {
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

        if (composerAttachmentLabel != null) {
            composerAttachmentLabel.setOnClickListener(v -> clearAttachmentSelection());
        }

        // Quick Reply Chips
        bindQuickChip(R.id.chip_quick_1, "Status laporan terkini?");
        bindQuickChip(R.id.chip_quick_2, "Bila tindakan diambil?");
        bindQuickChip(R.id.chip_quick_3, "Perlu maklumat lanjut?");
        bindQuickChip(R.id.chip_quick_4, "Terima kasih atas bantuan");
    }

    private void bindQuickChip(int chipId, String text) {
        View chip = findViewById(chipId);
        if (chip != null) {
            chip.setOnClickListener(v -> {
                if (inputView != null) {
                    inputView.setText(text);
                    inputView.setSelection(text.length());
                    sendSupportMessage();
                }
            });
        }
    }

    private void setupUserAndChat() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, R.string.support_livechat_login_required, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        uid = safe(user.getUid());
        userEmail = safe(user.getEmail());
        if (userEmail.isEmpty()) userEmail = safe(UserPrefs.getEmail(this));
        userName = safe(UserPrefs.getName(this));
        if (userName.isEmpty()) userName = safe(user.getDisplayName());
        if (userName.isEmpty()) userName = userEmail.isEmpty() ? "User" : userEmail;

        chatRef = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                .getReference()
                .child("supportChats")
                .child(uid);

        // Inisialisasi Meta Thread
        Map<String, Object> metaUpdates = new HashMap<>();
        metaUpdates.put("userUid", uid);
        metaUpdates.put("userName", userName);
        metaUpdates.put("userEmail", userEmail);
        metaUpdates.put("publicId", safe(UserPrefs.getPublicId(this)));
        metaUpdates.put("source", "app");
        metaUpdates.put("status", "open");
        metaUpdates.put("chatType", "report");
        metaUpdates.put("isReportChat", true);
        metaUpdates.put("topic", currentTopic);
        metaUpdates.put("updatedAt", ServerValue.TIMESTAMP);
        if (reportId != null && !reportId.isEmpty()) {
            metaUpdates.put("reportId", reportId);
        }
        chatRef.child("meta").updateChildren(metaUpdates);

        // Dengar Meta (Typing indicator dari Admin Responder)
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

        // Dengar Mesej Realtime
        messagesQuery = chatRef.child("messages").orderByChild("createdAt").limitToLast(120);
        messagesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                renderMessages(snapshot);
                if (snapshot.hasChildren()) {
                    chatRef.child("meta").child("userLastReadAt").setValue(ServerValue.TIMESTAMP);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(LivechatActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        messagesQuery.addValueEventListener(messagesListener);
    }

    private void renderMessages(DataSnapshot snapshot) {
        if (messagesContainer == null) return;
        messagesContainer.removeAllViews();

        if (!snapshot.hasChildren()) {
            renderEmptyMessage();
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
        java.util.Set<String> seenIds = new java.util.HashSet<>();
        String lastText = null;
        String lastSender = null;
        long lastTime = 0L;

        for (DataSnapshot child : snapshot.getChildren()) {
            String key = child.getKey();
            if (key != null && !seenIds.add(key)) continue;

            String text = safe(child.child("text").getValue(String.class)).trim();
            DataSnapshot attachmentSnap = child.child("attachment");
            String attachmentUrl = safe(attachmentSnap.child("downloadUrl").getValue(String.class));
            String attachmentName = safe(attachmentSnap.child("name").getValue(String.class));
            String attachmentMime = safe(attachmentSnap.child("mimeType").getValue(String.class));

            if (text.isEmpty() && attachmentUrl.isEmpty()) {
                if (attachmentSnap.exists()) {
                    text = attachmentName.isEmpty() ? "[Lampiran Fail]" : "[Fail: " + attachmentName + "]";
                } else {
                    continue;
                }
            }
            if (text.toLowerCase(Locale.ROOT).contains("claimed your ticket")) {
                continue;
            }

            String sender = safe(child.child("sender").getValue(String.class));
            String senderUid = safe(child.child("senderUid").getValue(String.class));
            String senderName = safe(child.child("senderName").getValue(String.class));
            Long createdAt = child.child("createdAt").getValue(Long.class);
            long timeVal = createdAt != null ? createdAt : 0L;

            if (text.equals(lastText) && sender.equals(lastSender) && Math.abs(timeVal - lastTime) < 10000L) {
                continue;
            }
            lastText = text;
            lastSender = sender;
            lastTime = timeVal;

            String timeStr = createdAt != null && createdAt > 0 ? sdf.format(new Date(createdAt)) : "";
            boolean isMe = "user".equalsIgnoreCase(sender) || (sender.isEmpty() && uid.equals(senderUid));

            addMessageBubble(key, text, isMe, senderName, timeStr, attachmentName, attachmentUrl, attachmentMime);
        }

        handler.postDelayed(() -> {
            if (messagesScroll != null) {
                messagesScroll.fullScroll(View.FOCUS_DOWN);
            }
        }, 80);
    }

    private void renderEmptyMessage() {
        if (messagesContainer == null) return;
        messagesContainer.removeAllViews();
        TextView empty = new TextView(this);
        empty.setText(R.string.support_livechat_empty);
        empty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        empty.setTextSize(13.5f);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(16), dp(48), dp(16), dp(48));
        messagesContainer.addView(empty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private void addMessageBubble(String messageKey, String text, boolean isMe, String senderName, String timeStr,
                                  String attachmentName, String attachmentUrl, String attachmentMime) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, dp(12));
        row.setLayoutParams(rowParams);
        row.setGravity(isMe ? Gravity.END : Gravity.START);

        // Bubble Box
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        int maxW = (int) (getResources().getDisplayMetrics().widthPixels * 0.78f);
        bubble.setLayoutParams(bubbleParams);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));

        if (isMe) {
            // User: Pink Bubble matching SOS Livechat
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(Color.parseColor("#E60067"));
            gd.setCornerRadii(new float[]{dp(18), dp(18), dp(18), dp(18), dp(4), dp(4), dp(18), dp(18)});
            bubble.setBackground(gd);
        } else {
            // Admin: Slate/White Bubble matching SOS Livechat
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(Color.parseColor("#F1F5F9"));
            gd.setStroke(dp(1), Color.parseColor("#E2E8F0"));
            gd.setCornerRadii(new float[]{dp(18), dp(18), dp(18), dp(18), dp(18), dp(18), dp(4), dp(4)});
            bubble.setBackground(gd);

            // Sender Label
            TextView tvSender = new TextView(this);
            tvSender.setText(!senderName.isEmpty() ? senderName : "Administrator");
            tvSender.setTextSize(11f);
            tvSender.setTextColor(Color.parseColor("#B80053"));
            tvSender.setTypeface(null, Typeface.BOLD);
            tvSender.setPadding(0, 0, 0, dp(4));
            bubble.addView(tvSender);
        }

        // Message text
        if (!text.isEmpty()) {
            TextView tvMsg = new TextView(this);
            tvMsg.setText(text);
            tvMsg.setTextSize(14.5f);
            tvMsg.setMaxWidth(maxW);
            tvMsg.setLineSpacing(4f, 1f);
            tvMsg.setTextColor(isMe ? Color.WHITE : Color.parseColor("#1E293B"));
            bubble.addView(tvMsg);
        }

        // Attachment Preview
        if (!attachmentUrl.isEmpty()) {
            if (attachmentUrl.startsWith("data:image/")) {
                Bitmap image = bitmapFromDataUrl(attachmentUrl);
                if (image != null) {
                    ImageView preview = new ImageView(this);
                    preview.setAdjustViewBounds(true);
                    preview.setMaxWidth(maxW);
                    preview.setMaxHeight(dp(220));
                    preview.setImageBitmap(image);
                    preview.setPadding(0, text.isEmpty() ? 0 : dp(6), 0, 0);
                    bubble.addView(preview, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    ));
                }
            } else {
                Button attachmentButton = new Button(this);
                attachmentButton.setAllCaps(false);
                attachmentButton.setText(attachmentName.isEmpty()
                        ? getString(R.string.support_livechat_open_attachment)
                        : "📎 " + attachmentName);
                attachmentButton.setTextSize(12f);
                attachmentButton.setTextColor(isMe ? Color.WHITE : Color.parseColor("#E60067"));
                attachmentButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                attachmentButton.setMaxWidth(maxW);
                attachmentButton.setBackgroundColor(Color.TRANSPARENT);
                attachmentButton.setPadding(0, text.isEmpty() ? 0 : dp(6), 0, 0);
                attachmentButton.setOnClickListener(v -> openAttachment(attachmentUrl, attachmentMime));
                bubble.addView(attachmentButton, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
            }
        }

        // Timestamp
        if (!timeStr.isEmpty()) {
            TextView tvTime = new TextView(this);
            tvTime.setText(timeStr);
            tvTime.setTextSize(10f);
            tvTime.setTextColor(isMe ? Color.parseColor("#FFD1E3") : Color.parseColor("#94A3B8"));
            tvTime.setGravity(Gravity.END);
            tvTime.setPadding(0, dp(4), 0, 0);
            bubble.addView(tvTime);
        }

        row.addView(bubble);
        messagesContainer.addView(row);
    }

    private void sendSupportMessage() {
        if (isSending) return;
        if (chatRef == null) {
            Toast.makeText(this, R.string.support_livechat_login_required, Toast.LENGTH_SHORT).show();
            return;
        }

        String message = safe(inputView.getText()).trim();
        boolean hasAttachment = hasSelectedAttachment();
        if (message.isEmpty() && !hasAttachment) {
            Toast.makeText(this, R.string.support_livechat_message_required, Toast.LENGTH_SHORT).show();
            return;
        }

        isSending = true;
        if (btnSend != null) btnSend.setEnabled(false);
        inputView.setText("");

        DatabaseReference messageRef = chatRef.child("messages").push();
        String messageId = messageRef.getKey();
        if (messageId == null || messageId.trim().isEmpty()) {
            isSending = false;
            if (btnSend != null) btnSend.setEnabled(true);
            Toast.makeText(this, R.string.support_livechat_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> chatMessage = new HashMap<>();
        chatMessage.put("id", messageId);
        chatMessage.put("text", message);
        chatMessage.put("topic", currentTopic);
        chatMessage.put("sender", "user");
        chatMessage.put("senderUid", uid);
        chatMessage.put("senderName", userName);
        chatMessage.put("createdAt", ServerValue.TIMESTAMP);
        chatMessage.put("source", "app");
        if (reportId != null && !reportId.isEmpty()) {
            chatMessage.put("reportId", reportId);
        }

        AttachmentInfo attachment = (selectedAttachmentUri == null && selectedAttachmentBytes == null) ? null : new AttachmentInfo(
                selectedAttachmentUri,
                selectedAttachmentBytes,
                selectedAttachmentName,
                selectedAttachmentMime,
                selectedAttachmentSize
        );

        String lastMsg = message.isEmpty() && attachment != null
                ? getString(R.string.support_livechat_attachment) + ": " + attachment.name
                : message;

        Map<String, Object> updates = new HashMap<>();
        updates.put("meta/userUid", uid);
        updates.put("meta/userName", userName);
        updates.put("meta/userEmail", userEmail);
        updates.put("meta/publicId", safe(UserPrefs.getPublicId(this)));
        updates.put("meta/source", "app");
        updates.put("meta/status", "open");
        updates.put("meta/chatType", "report");
        updates.put("meta/isReportChat", true);
        updates.put("meta/topic", currentTopic);
        updates.put("meta/lastMessage", lastMsg);
        updates.put("meta/lastSender", "user");
        updates.put("meta/lastSenderUid", uid);
        updates.put("meta/userTyping", false);
        updates.put("meta/updatedAt", ServerValue.TIMESTAMP);
        if (reportId != null && !reportId.isEmpty()) {
            updates.put("meta/reportId", reportId);
            updates.put("meta/isReportChat", true);
        }

        updates.put("messages/" + messageId, chatMessage);

        if (attachment == null) {
            commitMessageUpdates(updates);
            return;
        }

        if (isImageAttachment(attachment)) {
            Map<String, Object> inlineAttachment = inlineImageAttachmentMap(attachment);
            if (!inlineAttachment.isEmpty()) {
                chatMessage.put("attachment", inlineAttachment);
                commitMessageUpdates(updates);
                return;
            }
        }

        uploadAttachment(messageId, attachment, attachmentMap -> {
            chatMessage.put("attachment", attachmentMap);
            commitMessageUpdates(updates);
        });
    }

    private void commitMessageUpdates(Map<String, Object> updates) {
        chatRef.updateChildren(updates).addOnCompleteListener(task -> {
            isSending = false;
            if (btnSend != null) btnSend.setEnabled(true);
            clearAttachmentSelection();
            dispatchUserTyping(false);
            if (!task.isSuccessful()) {
                Toast.makeText(this, R.string.support_livechat_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmDeleteChat() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Padam Livechat")
                .setMessage("Adakah anda pasti ingin memadam semua mesej dalam perbualan ini?")
                .setPositiveButton("Padam", (dialog, which) -> {
                    if (chatRef != null) {
                        chatRef.child("messages").removeValue();
                        chatRef.child("meta").child("lastMessage").setValue("");
                        Toast.makeText(this, "Perbualan telah dipadam", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void dispatchUserTyping(boolean isTyping) {
        if (chatRef == null || uid.isEmpty()) return;

        if (isTyping) {
            if (!isUserTypingActive) {
                isUserTypingActive = true;
                Map<String, Object> updates = new HashMap<>();
                updates.put("meta/userTyping", true);
                updates.put("meta/userTypingAt", ServerValue.TIMESTAMP);
                chatRef.updateChildren(updates);
            }
            handler.removeCallbacks(userTypingStopRunnable);
            handler.postDelayed(userTypingStopRunnable, 4000L);
        } else {
            isUserTypingActive = false;
            handler.removeCallbacks(userTypingStopRunnable);
            Map<String, Object> updates = new HashMap<>();
            updates.put("meta/userTyping", false);
            updates.put("meta/userTypingAt", 0L);
            chatRef.updateChildren(updates);
        }
    }

    // DIRECT RESPONDER CALLING (Voice / Video)
    private void showCallOptionsBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_ResQTap_BottomSheetDialog);
        dialog.setCanceledOnTouchOutside(true);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_sos_call_options, null);
        dialog.setContentView(view);

        View optVideo = view.findViewById(R.id.option_call_video);
        View optVoice = view.findViewById(R.id.option_call_voice);
        View btnClose = view.findViewById(R.id.btn_close_call_options);

        if (optVideo != null) {
            optVideo.setOnClickListener(v -> {
                dialog.dismiss();
                initiateAdminCall("video");
            });
        }

        if (optVoice != null) {
            optVoice.setOnClickListener(v -> {
                dialog.dismiss();
                initiateAdminCall("voice");
            });
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    private void initiateAdminCall(String callType) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Sila log masuk untuk membuat panggilan.", Toast.LENGTH_SHORT).show();
            return;
        }

        CallSignalingClient.setAwaitingSosResponse(true);

        DatabaseReference rtdb = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL).getReference();

        Map<String, Object> callReq = new HashMap<>();
        callReq.put("status", "ringing");
        callReq.put("callType", callType);
        callReq.put("reportId", reportId != null ? reportId : "");
        callReq.put("alertId", "");
        callReq.put("roomId", "");
        callReq.put("isReportCall", true);
        callReq.put("callerUid", uid);
        callReq.put("callerName", userName);
        callReq.put("timestamp", ServerValue.TIMESTAMP);

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
                    Toast.makeText(LivechatActivity.this, R.string.sos_call_admin_busy, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        adminCallIncomingRef.addValueEventListener(adminCallIncomingListener);

        if (!uid.isEmpty()) {
            userCallRef = rtdb.child("userCalls").child(uid).child("currentCall");
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

                        CallSignalingClient.getInstance().acceptCall(uid, callId);

                        Intent intent = new Intent(LivechatActivity.this, "voice".equalsIgnoreCase(serverCallType) ? VoiceCallActivity.class : VideoCallActivity.class);
                        intent.putExtra("callId", callId);
                        intent.putExtra("callerName", "Administrator");
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
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try {
                rtdb.child("adminCalls").child("incoming").removeValue();
            } catch (Exception ignored) {}
        }, 2000);
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

    // ATTACHMENT METHODS
    private void chooseAttachment() {
        String[] options = new String[]{
                getString(R.string.support_livechat_attach_document),
                getString(R.string.support_livechat_attach_photo),
                getString(R.string.support_livechat_attach_camera),
                getString(R.string.support_livechat_attach_audio),
                getString(R.string.support_livechat_attach_calendar)
        };
        int[] icons = new int[]{
                R.drawable.ic_livechat_document,
                R.drawable.ic_livechat_photo,
                R.drawable.ic_livechat_camera,
                R.drawable.ic_livechat_audio,
                R.drawable.ic_livechat_calendar
        };
        int[] colors = new int[]{
                Color.rgb(124, 92, 255),
                Color.rgb(0, 168, 255),
                Color.rgb(255, 47, 128),
                Color.rgb(255, 114, 44),
                Color.rgb(255, 47, 128)
        };

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(8), dp(8), dp(8), dp(8));
        menu.setBackgroundResource(R.drawable.bg_livechat_attach_menu);

        final PopupWindow[] popupRef = new PopupWindow[1];
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            View item = createAttachmentMenuItem(options[i], icons[i], colors[i]);
            item.setOnClickListener(v -> {
                if (popupRef[0] != null) popupRef[0].dismiss();
                handleAttachmentOption(index);
            });
            menu.addView(item);
        }

        int popupWidth = dp(214);
        PopupWindow popup = new PopupWindow(menu, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupRef[0] = popup;
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(12));

        menu.measure(
                View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int yOffset = -(btnAttach.getHeight() + menu.getMeasuredHeight() + dp(10));
        popup.showAsDropDown(btnAttach, 0, yOffset, Gravity.START);
    }

    private View createAttachmentMenuItem(String label, int iconRes, int iconColor) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(10), 0, dp(12), 0);
        row.setBackgroundResource(R.drawable.bg_livechat_attach_menu_item);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(iconColor);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(22), dp(22));
        iconParams.setMarginEnd(dp(14));
        row.addView(icon, iconParams);

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextColor(Color.WHITE);
        text.setTextSize(15);
        text.setTypeface(text.getTypeface(), Typeface.BOLD);
        text.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        row.setMinimumHeight(dp(44));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        ));
        return row;
    }

    private void handleAttachmentOption(int which) {
        if (which == 0) launchAttachmentPicker(new String[]{
                "application/pdf",
                "text/plain",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        });
        if (which == 1) launchAttachmentPicker(new String[]{"image/*", "video/*"});
        if (which == 2) takeCameraPhoto.launch(null);
        if (which == 3) launchAttachmentPicker(new String[]{"audio/*"});
        if (which == 4) launchAttachmentPicker(new String[]{"text/calendar", "application/ics"});
    }

    private void launchAttachmentPicker(String[] mimeTypes) {
        pickAttachment.launch(mimeTypes);
    }

    private boolean hasSelectedAttachment() {
        return selectedAttachmentUri != null || selectedAttachmentBytes != null;
    }

    private boolean isImageAttachment(AttachmentInfo attachment) {
        String mimeType = safe(attachment.mimeType).toLowerCase(Locale.ROOT);
        if (mimeType.startsWith("image/")) return true;
        String name = safe(attachment.name).toLowerCase(Locale.ROOT);
        return name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".png")
                || name.endsWith(".webp")
                || name.endsWith(".gif");
    }

    private void setSelectedAttachment(Uri uri) {
        selectedAttachmentUri = uri;
        selectedAttachmentBytes = null;
        selectedAttachmentName = queryDisplayName(uri);
        selectedAttachmentMime = safe(getContentResolver().getType(uri));
        selectedAttachmentSize = querySize(uri);
        if (selectedAttachmentName.isEmpty()) selectedAttachmentName = "attachment";
        updateAttachmentLabels();
    }

    private void setSelectedCameraAttachment(Bitmap bitmap) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out);
        selectedAttachmentBytes = out.toByteArray();
        selectedAttachmentUri = null;
        selectedAttachmentMime = "image/jpeg";
        selectedAttachmentSize = selectedAttachmentBytes.length;
        selectedAttachmentName = "camera_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
        updateAttachmentLabels();
    }

    private void updateAttachmentLabels() {
        if (composerAttachmentLabel == null) return;
        String label = selectedAttachmentUri == null && selectedAttachmentBytes == null
                ? getString(R.string.support_livechat_no_attachment)
                : getString(R.string.support_livechat_attachment_selected, selectedAttachmentName) + " (Ketik untuk padam)";
        composerAttachmentLabel.setText(label);
        composerAttachmentLabel.setVisibility(hasSelectedAttachment() ? View.VISIBLE : View.GONE);
    }

    private void clearAttachmentSelection() {
        selectedAttachmentUri = null;
        selectedAttachmentBytes = null;
        selectedAttachmentName = "";
        selectedAttachmentMime = "";
        selectedAttachmentSize = 0L;
        if (composerAttachmentLabel != null) {
            composerAttachmentLabel.setText(R.string.support_livechat_no_attachment);
            composerAttachmentLabel.setVisibility(View.GONE);
        }
    }

    private Map<String, Object> inlineImageAttachmentMap(AttachmentInfo attachment) {
        Map<String, Object> data = new HashMap<>();
        String b64 = "";
        if (attachment.bytes != null && attachment.bytes.length > 0) {
            b64 = Base64.encodeToString(attachment.bytes, Base64.NO_WRAP);
        } else if (attachment.uri != null) {
            b64 = encodeImageToBase64(attachment.uri, 720, 75);
        }
        if (b64.isEmpty()) return data;

        String mimeType = attachment.mimeType.isEmpty() ? "image/jpeg" : attachment.mimeType;
        data.put("name", attachment.name);
        data.put("mimeType", mimeType);
        data.put("size", attachment.size);
        data.put("downloadUrl", "data:" + mimeType + ";base64," + b64);
        data.put("inline", true);
        return data;
    }

    private String encodeImageToBase64(Uri localUri, int maxDim, int jpegQuality) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (java.io.InputStream in = getContentResolver().openInputStream(localUri)) {
                if (in == null) return "";
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int w = Math.max(1, bounds.outWidth);
            int h = Math.max(1, bounds.outHeight);
            int sample = 1;
            int max = Math.max(w, h);
            while (max / sample > maxDim * 2) sample *= 2;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap;
            try (java.io.InputStream in2 = getContentResolver().openInputStream(localUri)) {
                if (in2 == null) return "";
                bitmap = BitmapFactory.decodeStream(in2, null, opts);
            }
            if (bitmap == null) return "";
            int bw = bitmap.getWidth();
            int bh = bitmap.getHeight();
            int largest = Math.max(bw, bh);
            if (largest > maxDim) {
                float scale = maxDim / (float) largest;
                int nw = Math.max(1, Math.round(bw * scale));
                int nh = Math.max(1, Math.round(bh * scale));
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, nw, nh, true);
                bitmap.recycle();
                bitmap = scaled;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, Math.max(40, Math.min(jpegQuality, 95)), out);
            bitmap.recycle();
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception ignored) {
            return "";
        }
    }

    private void uploadAttachment(String messageId, AttachmentInfo attachment, AttachmentUploaded callback) {
        // Fallback simpan metadata asas jika storan awan tidak dipasang
        Map<String, Object> map = new HashMap<>();
        map.put("name", attachment.name);
        map.put("mimeType", attachment.mimeType);
        map.put("size", attachment.size);
        map.put("downloadUrl", "");
        callback.onUploaded(map);
    }

    private void openAttachment(String url, String mimeType) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (!safe(mimeType).isEmpty()) intent.setDataAndType(Uri.parse(url), mimeType);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(this, R.string.support_livechat_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap bitmapFromDataUrl(String dataUrl) {
        try {
            int comma = dataUrl.indexOf(',');
            if (comma < 0 || comma >= dataUrl.length() - 1) return null;
            byte[] bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return safe(cursor.getString(index));
            }
        } catch (Exception ignored) {}
        return safe(uri.getLastPathSegment());
    }

    private long querySize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0) return cursor.getLong(index);
            }
        } catch (Exception ignored) {}
        return 0L;
    }

    private String safe(Object value) {
        return String.valueOf(value == null ? "" : value).trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        stopCallListening();
        super.onDestroy();
        if (messagesQuery != null && messagesListener != null) {
            messagesQuery.removeEventListener(messagesListener);
        }
        if (chatRef != null && metaListener != null) {
            chatRef.child("meta").removeEventListener(metaListener);
        }
    }

    private static final class AttachmentInfo {
        final Uri uri;
        final byte[] bytes;
        final String name;
        final String mimeType;
        final long size;

        AttachmentInfo(Uri uri, byte[] bytes, String name, String mimeType, long size) {
            this.uri = uri;
            this.bytes = bytes;
            this.name = name == null ? "" : name;
            this.mimeType = mimeType == null ? "" : mimeType;
            this.size = size;
        }
    }

    private interface AttachmentUploaded {
        void onUploaded(Map<String, Object> attachmentMap);
    }
}
