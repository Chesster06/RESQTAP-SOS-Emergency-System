package com.example.resqtap.chat;
import com.example.resqtap.R;

import com.example.resqtap.app.BaseActivity;
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
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


/**
 * LivechatActivity
 * Livechat Support: chat direct dengan admin support secara realtime, support hantar gambar/dokumen.
 */
public class LivechatActivity extends BaseActivity {
    private static final long AUTO_REPLY_DELAY_MS = 3000L;
    private static final long ADMIN_TYPING_STALE_MS = 7000L;

    private View startForm;
    private View chatCard;
    private View composerWrap;
    private LinearLayout messagesContainer;
    private ScrollView messagesScroll;
    private TextView statusView;
    private TextView topicTitleView;
    private TextView composerAttachmentLabel;
    private EditText topicInput;
    private EditText initialMessageInput;
    private EditText inputView;
    private Button startButton;
    private Button sendButton;
    private View attachButton;
    private View typingIndicatorView;
    private TextView typingDotsView;

    private final Handler livechatHandler = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<String[]> pickAttachment;
    private ActivityResultLauncher<Void> takeCameraPhoto;
    private DatabaseReference chatRef;
    private Query messagesQuery;
    private ValueEventListener messagesListener;
    private ValueEventListener metaListener;

    private Uri selectedAttachmentUri;
    private byte[] selectedAttachmentBytes;
    private String selectedAttachmentName = "";
    private String selectedAttachmentMime = "";
    private long selectedAttachmentSize = 0L;
    private boolean threadHasMessages = false;
    private boolean chatVisible = false;
    private boolean initialLivechatStateApplied = false;
    private boolean autoReplyTypingActive = false;
    private boolean adminTypingActive = false;
    private boolean destroyed = false;
    private int typingDotsStep = 0;
    private Runnable typingDotsRunnable;
    private String uid = "";
    private String userName = "";
    private String userEmail = "";
    private String currentTopic = "";

    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String room = UserPrefs.getActiveSosProgressRoom(this);
        String alertId = UserPrefs.getActiveSosProgressAlert(this);
        com.example.resqtap.sos.SosLivechatActivity.launch(this, room, alertId, "", "");
        finish();
    }

    // SEKSYEN: ONDESTROY
    /** Pembersihan memori, buang listener, dan tutup sambungan. */
    @Override
    protected void onDestroy() {
        destroyed = true;
        removeTypingIndicator();
        super.onDestroy();
        if (messagesQuery != null && messagesListener != null) {
            messagesQuery.removeEventListener(messagesListener);
        }
        if (chatRef != null && metaListener != null) {
            chatRef.child("meta").removeEventListener(metaListener);
        }
    }

    /** Setup dan konfigurasi Livechat. */
    private void setupLivechat() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            setComposerEnabled(false);
            showStartForm();
            Toast.makeText(this, R.string.support_livechat_login_required, Toast.LENGTH_SHORT).show();
            return;
        }

        uid = safe(user.getUid());
        userEmail = safe(user.getEmail());
        if (userEmail.isEmpty()) userEmail = safe(UserPrefs.getEmail(this));
        userName = safe(UserPrefs.getName(this));
        if (userName.isEmpty()) userName = safe(user.getDisplayName());
        if (userName.isEmpty()) userName = userEmail.isEmpty() ? "User" : userEmail;

        chatRef = FirebaseDatabase
                .getInstance(FirebaseRoomClient.DATABASE_URL)
                .getReference()
                .child("supportChats")
                .child(uid);
        messagesQuery = chatRef.child("messages").orderByChild("createdAt").limitToLast(80);

        metaListener = new ValueEventListener() {
            /** Callback apabila data Firebase Realtime Database berubah. */
    @Override
            public void onDataChange(DataSnapshot snapshot) {
                currentTopic = safe(snapshot.child("topic").getValue(String.class));
                updateTopicTitle();
                boolean adminTyping = Boolean.TRUE.equals(snapshot.child("adminTyping").getValue(Boolean.class));
                long typingAt = asLong(snapshot.child("adminTypingAt").getValue());
                long age = System.currentTimeMillis() - typingAt;
                setAdminTypingActive(adminTyping && typingAt > 0L && age <= ADMIN_TYPING_STALE_MS);
            }

            /** Callback sekiranya operasi Firebase dibatalkan atau gagal. */
    @Override
            public void onCancelled(DatabaseError error) {
            }
        };
        chatRef.child("meta").addValueEventListener(metaListener);

        messagesListener = new ValueEventListener() {
            /** Callback apabila data Firebase Realtime Database berubah. */
    @Override
            public void onDataChange(DataSnapshot snapshot) {
                threadHasMessages = snapshot.hasChildren();
                renderMessages(snapshot);
                if (!initialLivechatStateApplied) {
                    initialLivechatStateApplied = true;
                    if (threadHasMessages) {
                        showChatSession();
                    } else {
                        showStartForm();
                    }
                }
                if (snapshot.hasChildren() && chatVisible) {
                    chatRef.child("meta").child("userLastReadAt").setValue(ServerValue.TIMESTAMP);
                }
            }

            /** Callback sekiranya operasi Firebase dibatalkan atau gagal. */
    @Override
            public void onCancelled(DatabaseError error) {
                if (chatVisible) statusView.setText(R.string.support_livechat_sync_failed);
                Toast.makeText(LivechatActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        messagesQuery.addValueEventListener(messagesListener);
    }

    /** Paparkan InitialSupportState. */
    private void showInitialSupportState() {
        startForm.setVisibility(View.GONE);
        chatCard.setVisibility(View.GONE);
        composerWrap.setVisibility(View.GONE);
        chatVisible = false;
    }

    /** Paparkan StartForm. */
    private void showStartForm() {
        clearAttachmentSelection();
        startForm.setVisibility(View.VISIBLE);
        chatCard.setVisibility(View.GONE);
        composerWrap.setVisibility(View.GONE);
        chatVisible = false;
        topicInput.requestFocus();
    }

    /** Paparkan ChatSession. */
    private void showChatSession() {
        startForm.setVisibility(View.GONE);
        chatCard.setVisibility(View.VISIBLE);
        composerWrap.setVisibility(View.VISIBLE);
        chatVisible = true;
        statusView.setText(R.string.support_livechat_desc);
        updateTopicTitle();
        clearAttachmentSelection();
        messagesScroll.post(() -> messagesScroll.fullScroll(View.FOCUS_DOWN));
        if (chatRef != null && threadHasMessages) {
            chatRef.child("meta").child("userLastReadAt").setValue(ServerValue.TIMESTAMP);
        }
    }

    private void startSupportSession() {
        if (chatRef == null) {
            Toast.makeText(this, R.string.support_livechat_login_required, Toast.LENGTH_SHORT).show();
            return;
        }

        String topic = safe(topicInput.getText()).trim();
        String message = safe(initialMessageInput.getText()).trim();
        boolean hasAttachment = hasSelectedAttachment();
        if (topic.isEmpty()) {
            Toast.makeText(this, R.string.support_livechat_topic_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (message.isEmpty() && !hasAttachment) {
            Toast.makeText(this, R.string.support_livechat_message_required, Toast.LENGTH_SHORT).show();
            return;
        }

        currentTopic = topic;
        setSending(true);
        sendMessageToFirebase(topic, message, true);
    }

    /** Simpan atau hantar data SupportMessage. */
    private void sendSupportMessage() {
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

        setSending(true);
        sendMessageToFirebase(currentTopic, message, false);
    }

    /** Simpan atau hantar data MessageToFirebase. */
    private void sendMessageToFirebase(String topic, String message, boolean includeAutoReply) {
        DatabaseReference messageRef = chatRef.child("messages").push();
        String messageId = messageRef.getKey();
        if (messageId == null || messageId.trim().isEmpty()) {
            setSending(false);
            Toast.makeText(this, R.string.support_livechat_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> chatMessage = new HashMap<>();
        chatMessage.put("text", message);
        chatMessage.put("topic", safe(topic));
        chatMessage.put("sender", "user");
        chatMessage.put("senderUid", uid);
        chatMessage.put("senderName", userName);
        chatMessage.put("createdAt", ServerValue.TIMESTAMP);
        chatMessage.put("source", "app");

        AttachmentInfo attachment = (selectedAttachmentUri == null && selectedAttachmentBytes == null) ? null : new AttachmentInfo(
                selectedAttachmentUri,
                selectedAttachmentBytes,
                selectedAttachmentName,
                selectedAttachmentMime,
                selectedAttachmentSize
        );
        String lastMessage = message.isEmpty() && attachment != null
                ? getString(R.string.support_livechat_attachment) + ": " + attachment.name
                : message;
        Map<String, Object> updates = threadMetaUpdates(lastMessage, topic);
        updates.put("messages/" + messageId, chatMessage);
        if (attachment == null) {
            saveMessageUpdates(updates, includeAutoReply, topic);
            return;
        }

        statusView.setText(R.string.support_livechat_attachment_uploading);
        if (isImageAttachment(attachment)) {
            Map<String, Object> inlineAttachment = inlineImageAttachmentMap(attachment);
            if (!inlineAttachment.isEmpty()) {
                chatMessage.put("attachment", inlineAttachment);
                saveMessageUpdates(updates, includeAutoReply, topic);
                return;
            }
        }
        uploadAttachment(messageId, attachment, attachmentMap -> {
            chatMessage.put("attachment", attachmentMap);
            saveMessageUpdates(updates, includeAutoReply, topic);
        });
    }

    /** Simpan atau hantar data MessageUpdates. */
    private void saveMessageUpdates(Map<String, Object> updates, boolean includeAutoReply, String topic) {
        chatRef.updateChildren(updates).addOnCompleteListener(task -> {
            setSending(false);
            if (task.isSuccessful()) {
                inputView.setText("");
                initialMessageInput.setText("");
                clearAttachmentSelection();
                showChatSession();
                if (includeAutoReply) scheduleAutoReply(topic);
                Toast.makeText(this, R.string.support_livechat_sent, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.support_livechat_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void scheduleAutoReply(String topic) {
        if (chatRef == null) return;
        DatabaseReference autoReplyRef = chatRef.child("messages").push();
        String autoReplyId = autoReplyRef.getKey();
        if (autoReplyId == null || autoReplyId.trim().isEmpty()) return;

        Map<String, Object> autoReply = autoReplyMessage(topic);
        autoReplyTypingActive = true;
        updateTypingIndicator();
        livechatHandler.postDelayed(() -> {
            autoReplyRef.setValue(autoReply);
            autoReplyTypingActive = false;
            if (!destroyed) updateTypingIndicator();
        }, AUTO_REPLY_DELAY_MS);
    }

    private void uploadAttachment(String messageId, AttachmentInfo attachment, AttachmentUploaded callback) {
        String safeName = sanitizeFileName(attachment.name.isEmpty() ? "attachment" : attachment.name);
        StorageReference ref = FirebaseStorage.getInstance()
                .getReference()
                .child("supportChats")
                .child(uid)
                .child(messageId)
                .child(safeName);

        if (attachment.bytes != null) {
            ref.putBytes(attachment.bytes)
                    .addOnSuccessListener(snapshot -> handleUploadedAttachment(ref, attachment, callback))
                .addOnFailureListener(error -> {
                    statusView.setText(R.string.support_livechat_desc);
                    setSending(false);
                    Toast.makeText(this, R.string.support_livechat_failed, Toast.LENGTH_SHORT).show();
                });
            return;
        }

        ref.putFile(attachment.uri)
                .addOnSuccessListener(snapshot -> handleUploadedAttachment(ref, attachment, callback))
                .addOnFailureListener(error -> {
                    statusView.setText(R.string.support_livechat_desc);
                    setSending(false);
                    Toast.makeText(this, R.string.support_livechat_failed, Toast.LENGTH_SHORT).show();
                });
    }

    private void handleUploadedAttachment(StorageReference ref, AttachmentInfo attachment, AttachmentUploaded callback) {
        ref.getDownloadUrl()
                .addOnSuccessListener(downloadUri -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("name", attachment.name);
                    data.put("mimeType", attachment.mimeType);
                    data.put("size", attachment.size);
                    data.put("downloadUrl", downloadUri.toString());
                    data.put("storagePath", ref.getPath());
                    callback.onUploaded(data);
                })
                .addOnFailureListener(error -> {
                    statusView.setText(R.string.support_livechat_desc);
                    setSending(false);
                    Toast.makeText(this, R.string.support_livechat_failed, Toast.LENGTH_SHORT).show();
                });
    }

    private Map<String, Object> inlineImageAttachmentMap(AttachmentInfo attachment) {
        Map<String, Object> data = new HashMap<>();
        String b64 = "";
        String mimeType = safe(attachment.mimeType);
        if (mimeType.isEmpty()) mimeType = "image/jpeg";

        if (attachment.bytes != null && attachment.bytes.length > 0) {
            b64 = Base64.encodeToString(attachment.bytes, Base64.NO_WRAP);
        } else if (attachment.uri != null) {
            b64 = encodeImageToBase64(attachment.uri, 1280, 82);
            mimeType = "image/jpeg";
        }

        if (b64.isEmpty()) return data;
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

    private Map<String, Object> threadMetaUpdates(String lastMessage, String topic) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("meta/userUid", uid);
        updates.put("meta/userName", userName);
        updates.put("meta/userEmail", userEmail);
        updates.put("meta/publicId", safe(UserPrefs.getPublicId(this)));
        updates.put("meta/source", "app");
        updates.put("meta/status", "open");
        updates.put("meta/topic", safe(topic));
        updates.put("meta/lastMessage", lastMessage);
        updates.put("meta/lastSender", "user");
        updates.put("meta/updatedAt", ServerValue.TIMESTAMP);
        return updates;
    }

    private Map<String, Object> autoReplyMessage(String topic) {
        Map<String, Object> message = new HashMap<>();
        message.put("text", getString(R.string.support_livechat_auto_reply));
        message.put("topic", safe(topic));
        message.put("sender", "system");
        message.put("senderUid", uid);
        message.put("senderName", "ResQTap Support");
        message.put("createdAt", ServerValue.TIMESTAMP);
        message.put("source", "auto-support");
        return message;
    }

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
        int yOffset = -(attachButton.getHeight() + menu.getMeasuredHeight() + dp(10));
        popup.showAsDropDown(attachButton, 0, yOffset, Gravity.START);
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

    /** Semak dan sahkan SelectedAttachment. */
    private boolean hasSelectedAttachment() {
        return selectedAttachmentUri != null || selectedAttachmentBytes != null;
    }

    /** Semak dan sahkan ImageAttachment. */
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

    /** Simpan atau hantar data AttachmentLabels. */
    private void updateAttachmentLabels() {
        String label = selectedAttachmentUri == null && selectedAttachmentBytes == null
                ? getString(R.string.support_livechat_no_attachment)
                : getString(R.string.support_livechat_attachment_selected, selectedAttachmentName);
        composerAttachmentLabel.setText(label);
        composerAttachmentLabel.setVisibility(hasSelectedAttachment() ? View.VISIBLE : View.GONE);
    }

    /** Padam atau bersihkan AttachmentSelection. */
    private void clearAttachmentSelection() {
        selectedAttachmentUri = null;
        selectedAttachmentBytes = null;
        selectedAttachmentName = "";
        selectedAttachmentMime = "";
        selectedAttachmentSize = 0L;
        composerAttachmentLabel.setText(R.string.support_livechat_no_attachment);
        composerAttachmentLabel.setVisibility(View.GONE);
    }

    private void renderMessages(DataSnapshot snapshot) {
        messagesContainer.removeAllViews();
        if (!snapshot.hasChildren()) {
            renderEmptyMessage();
            return;
        }

        for (DataSnapshot child : snapshot.getChildren()) {
            String text = safe(child.child("text").getValue(String.class)).trim();
            DataSnapshot attachmentSnap = child.child("attachment");
            String attachmentUrl = safe(attachmentSnap.child("downloadUrl").getValue(String.class));
            String attachmentName = safe(attachmentSnap.child("name").getValue(String.class));
            String attachmentMime = safe(attachmentSnap.child("mimeType").getValue(String.class));
            if (text.isEmpty() && attachmentUrl.isEmpty()) continue;

            String sender = safe(child.child("sender").getValue(String.class));
            String senderUid = safe(child.child("senderUid").getValue(String.class));
            String senderName = safe(child.child("senderName").getValue(String.class));
            boolean mine = "user".equals(sender) || (sender.isEmpty() && uid.equals(senderUid));
            String label = mine
                    ? getString(R.string.support_livechat_you)
                    : (senderName.isEmpty() ? getString(R.string.support_livechat_admin) : senderName);
            long createdAt = asLong(child.child("createdAt").getValue());
            addMessageBubble(text, mine, label, createdAt, attachmentName, attachmentUrl, attachmentMime);
        }

        updateTypingIndicator();
        messagesScroll.post(() -> messagesScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void setAdminTypingActive(boolean active) {
        if (adminTypingActive == active) return;
        adminTypingActive = active;
        updateTypingIndicator();
    }

    /** Simpan atau hantar data TypingIndicator. */
    private void updateTypingIndicator() {
        removeTypingIndicator();
        if (!chatVisible || messagesContainer == null || !(autoReplyTypingActive || adminTypingActive)) return;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.START);
        row.setPadding(0, 0, 0, dp(10));

        TextView label = new TextView(this);
        label.setText("ResQTap");
        label.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        label.setTextSize(11);
        label.setGravity(Gravity.START);
        row.addView(label);

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.HORIZONTAL);
        bubble.setGravity(Gravity.CENTER_VERTICAL);
        bubble.setBackgroundResource(R.drawable.bg_livechat_bubble_admin);
        bubble.setPadding(dp(12), dp(9), dp(12), dp(9));

        TextView typing = new TextView(this);
        typingDotsView = typing;
        typing.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        typing.setTextSize(14);
        bubble.addView(typing);

        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bubbleParams.topMargin = dp(3);
        row.addView(bubble, bubbleParams);

        typingIndicatorView = row;
        messagesContainer.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        startTypingAnimation();
        messagesScroll.post(() -> messagesScroll.fullScroll(View.FOCUS_DOWN));
    }

    /** Padam atau bersihkan TypingIndicator. */
    private void removeTypingIndicator() {
        if (typingDotsRunnable != null) {
            livechatHandler.removeCallbacks(typingDotsRunnable);
            typingDotsRunnable = null;
        }
        if (typingIndicatorView != null) {
            ViewGroup parent = (ViewGroup) typingIndicatorView.getParent();
            if (parent != null) parent.removeView(typingIndicatorView);
            typingIndicatorView = null;
        }
        typingDotsView = null;
    }

    private void startTypingAnimation() {
        typingDotsStep = 0;
        typingDotsRunnable = new Runnable() {
    @Override
            public void run() {
                if (typingDotsView == null) return;
                int dots = typingDotsStep % 4;
                StringBuilder text = new StringBuilder(getString(R.string.support_livechat_typing));
                for (int i = 0; i < dots; i++) text.append('.');
                typingDotsView.setText(text.toString());
                typingDotsStep++;
                livechatHandler.postDelayed(this, 450L);
            }
        };
        livechatHandler.post(typingDotsRunnable);
    }

    private void renderEmptyMessage() {
        messagesContainer.removeAllViews();
        TextView empty = new TextView(this);
        empty.setText(R.string.support_livechat_empty);
        empty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        empty.setTextSize(14);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(16), dp(28), dp(16), dp(28));
        messagesContainer.addView(empty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private void addMessageBubble(String message, boolean mine, String labelText, long createdAt,
                                  String attachmentName, String attachmentUrl, String attachmentMime) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(mine ? Gravity.END : Gravity.START);
        row.setPadding(0, 0, 0, dp(10));

        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        label.setTextSize(11);
        label.setGravity(mine ? Gravity.END : Gravity.START);
        row.addView(label);

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackgroundResource(mine ? R.drawable.bg_livechat_bubble_user : R.drawable.bg_livechat_bubble_admin);
        bubble.setPadding(dp(12), dp(9), dp(12), dp(9));
        int bubbleMaxWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.72f);

        if (!message.isEmpty()) {
            TextView messageView = new TextView(this);
            messageView.setText(message);
            messageView.setTextSize(14);
            messageView.setLineSpacing(0, 1.08f);
            messageView.setMaxWidth(bubbleMaxWidth);
            messageView.setTextColor(ContextCompat.getColor(this, mine ? R.color.white : R.color.text_primary));
            bubble.addView(messageView);
        }

        if (!attachmentUrl.isEmpty()) {
            if (attachmentUrl.startsWith("data:image/")) {
                Bitmap image = bitmapFromDataUrl(attachmentUrl);
                if (image != null) {
                    ImageView preview = new ImageView(this);
                    preview.setAdjustViewBounds(true);
                    preview.setMaxWidth(bubbleMaxWidth);
                    preview.setMaxHeight(dp(220));
                    preview.setImageBitmap(image);
                    preview.setPadding(0, message.isEmpty() ? 0 : dp(6), 0, 0);
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
                        : attachmentName);
                attachmentButton.setTextSize(12);
                attachmentButton.setTextColor(ContextCompat.getColor(this, mine ? R.color.white : R.color.brand_primary));
                attachmentButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                attachmentButton.setMaxWidth(bubbleMaxWidth);
                attachmentButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
                attachmentButton.setPadding(0, message.isEmpty() ? 0 : dp(6), 0, 0);
                attachmentButton.setOnClickListener(v -> openAttachment(attachmentUrl, attachmentMime));
                bubble.addView(attachmentButton, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
            }
        }

        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bubbleParams.topMargin = dp(3);
        row.addView(bubble, bubbleParams);

        String time = formatTime(createdAt);
        if (!time.isEmpty()) {
            TextView meta = new TextView(this);
            meta.setText(time);
            meta.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            meta.setTextSize(11);
            meta.setGravity(mine ? Gravity.END : Gravity.START);
            LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            metaParams.topMargin = dp(3);
            row.addView(meta, metaParams);
        }

        messagesContainer.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
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

    private void setComposerEnabled(boolean enabled) {
        inputView.setEnabled(enabled);
        sendButton.setEnabled(enabled);
        startButton.setEnabled(enabled);
        attachButton.setEnabled(enabled);
    }

    private void setSending(boolean sending) {
        setComposerEnabled(!sending);
        if (sending && chatVisible) statusView.setText(R.string.support_livechat_attachment_uploading);
    }

    /** Simpan atau hantar data TopicTitle. */
    private void updateTopicTitle() {
        if (topicTitleView == null) return;
        String topic = safe(currentTopic);
        topicTitleView.setText(topic.isEmpty()
                ? getString(R.string.support_livechat_title)
                : getString(R.string.support_livechat_title) + ": " + topic);
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return safe(cursor.getString(index));
            }
        } catch (Exception ignored) {
        }
        return safe(uri.getLastPathSegment());
    }

    private long querySize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0) return cursor.getLong(index);
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    private String sanitizeFileName(String name) {
        String clean = safe(name).replaceAll("[\\\\/:*?\"<>|#\\[\\]$]", "_");
        if (clean.isEmpty()) clean = "attachment";
        return String.format(Locale.US, "%d_%s", System.currentTimeMillis(), clean);
    }

    private String safe(Object value) {
        return String.valueOf(value == null ? "" : value).trim();
    }

    private long asLong(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(safe(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String formatTime(long ms) {
        if (ms <= 0) return "";
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(ms));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
