package com.example.resqtap.notification;
import com.example.resqtap.R;

import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.room.FirebaseRoomClient;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


/**
 * NotificationsActivity
 * Inbox notifikasi: simpan history SOS alert dan announcement sistem.
 */
public class NotificationsActivity extends BaseActivity {
    private LinearLayout notificationsList;
    private ScrollView notificationsScroll;
    private TextView empty;
    private View listContent;
    private View detailContent;
    private TextView detailTitle;
    private TextView detailTime;
    private TextView detailMessage;
    private TextView detailSource;
    private MaterialButton btnDetailCheckin;
    private DatabaseReference notificationsRef;
    private ValueEventListener notificationsListener;
    private DatabaseReference userNotifRef;
    private ValueEventListener userNotifListener;
    private DataSnapshot globalSnapshot;
    private DataSnapshot userSnapshot;
    private final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault());
    private boolean showingDetail = false;

    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_notifications);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        View back = findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> finish());
        View detailBack = findViewById(R.id.btn_detail_back);
        if (detailBack != null) detailBack.setOnClickListener(v -> showNotificationsList());

        View btnClearAll = findViewById(R.id.btn_clear_all);
        if (btnClearAll != null) {
            btnClearAll.setOnClickListener(v -> confirmClearAllNotifications());
        }

        listContent = findViewById(R.id.list_content);
        detailContent = findViewById(R.id.notification_detail_content);
        detailTitle = findViewById(R.id.notification_detail_title);
        detailTime = findViewById(R.id.notification_detail_time);
        detailMessage = findViewById(R.id.notification_detail_message);
        detailSource = findViewById(R.id.notification_detail_source);
        btnDetailCheckin = findViewById(R.id.btn_detail_checkin);
        if (btnDetailCheckin != null) {
            btnDetailCheckin.setOnClickListener(v -> com.example.resqtap.utils.EmergencyCheckinHelper.showCheckInDialog(this));
        }
        notificationsList = findViewById(R.id.notifications_list);
        notificationsScroll = findViewById(R.id.notifications_scroll);
        empty = findViewById(R.id.empty);
        listenForNotifications();

        UserPrefs.setUnreadNotifications(this, 0);
    }

    @Override
    public void onBackPressed() {
        if (showingDetail) {
            showNotificationsList();
            return;
        }
        super.onBackPressed();
    }

    // SEKSYEN: ONDESTROY
    /** Pembersihan memori, buang listener, dan tutup sambungan. */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (notificationsRef != null && notificationsListener != null) {
                notificationsRef.removeEventListener(notificationsListener);
            }
            if (userNotifRef != null && userNotifListener != null) {
                userNotifRef.removeEventListener(userNotifListener);
            }
        } catch (Exception ignored) {
        }
    }

    private void listenForNotifications() {
        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        String uid = currentUser != null ? currentUser.getUid() : "";

        try {
            DatabaseReference ref = FirebaseDatabase
                    .getInstance(FirebaseRoomClient.DATABASE_URL)
                    .getReference("broadcastNotifications");
            notificationsRef = ref;
            notificationsListener = new ValueEventListener() {
                /** Callback apabila data Firebase Realtime Database berubah. */
    @Override
                public void onDataChange(DataSnapshot snapshot) {
                    globalSnapshot = snapshot;
                    combineAndRender();
                }

                /** Callback sekiranya operasi Firebase dibatalkan atau gagal. */
    @Override
                public void onCancelled(DatabaseError error) {
                    renderError(error == null ? getString(R.string.toast_generic_error) : error.getMessage());
                }
            };
            notificationsRef.addValueEventListener(notificationsListener);

            if (!uid.isEmpty()) {
                userNotifRef = FirebaseDatabase
                        .getInstance(FirebaseRoomClient.DATABASE_URL)
                        .getReference("userNotifications")
                        .child(uid);
                userNotifListener = new ValueEventListener() {
                    /** Callback apabila data Firebase Realtime Database berubah. */
    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        userSnapshot = snapshot;
                        combineAndRender();
                    }

                    /** Callback sekiranya operasi Firebase dibatalkan atau gagal. */
    @Override
                    public void onCancelled(DatabaseError error) {}
                };
                userNotifRef.addValueEventListener(userNotifListener);
            }
        } catch (Exception e) {
            renderError(e.getMessage());
        }
    }

    private void combineAndRender() {
        Map<String, Notice> byId = new LinkedHashMap<>();

        if (globalSnapshot != null && globalSnapshot.exists()) {
            Notice current = Notice.from(globalSnapshot.child("current"));
            if (current != null) byId.put(current.id, current);

            DataSnapshot history = globalSnapshot.child("history");
            for (DataSnapshot child : history.getChildren()) {
                Notice item = Notice.from(child);
                if (item != null) byId.put(item.id, item);
            }
        }

        if (userSnapshot != null && userSnapshot.exists()) {
            for (DataSnapshot child : userSnapshot.getChildren()) {
                Notice item = Notice.from(child);
                if (item != null) byId.put(item.id, item);
            }
        }

        List<Notice> items = new ArrayList<>(byId.values());
        Collections.sort(items, (a, b) -> Long.compare(b.time, a.time));
        render(items);
    }

    private void render(List<Notice> items) {
        if (notificationsList == null || notificationsScroll == null || empty == null) return;
        notificationsList.removeAllViews();
        boolean hasItems = items != null && !items.isEmpty();
        notificationsScroll.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        empty.setVisibility(hasItems ? View.GONE : View.VISIBLE);

        View btnClearAll = findViewById(R.id.btn_clear_all);
        if (btnClearAll != null) {
            btnClearAll.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        }

        if (!hasItems) return;

        for (Notice item : items) {
            MaterialCardView card = new MaterialCardView(this);
            card.setCardElevation(dp(1));
            card.setRadius(dp(16));
            card.setUseCompatPadding(false);
            card.setStrokeWidth(1);
            card.setStrokeColor(getColor(R.color.tts_card_stroke));
            card.setCardBackgroundColor(getColor(R.color.panel));
            card.setClickable(true);
            card.setFocusable(true);
            applySelectableForeground(card);
            card.setOnClickListener(v -> showNotificationDetail(item));

            LinearLayout body = new LinearLayout(this);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(dp(18), dp(16), dp(18), dp(16));

            TextView title = new TextView(this);
            title.setText(getLocalizedTitle(item.title));
            title.setTextColor(getColor(R.color.text_primary));
            title.setTextSize(15.5f);
            title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            body.addView(title);

            TextView message = new TextView(this);
            message.setText(getLocalizedMessage(item.message));
            message.setTextColor(getColor(R.color.text_primary));
            message.setTextSize(13);
            message.setPadding(0, dp(5), 0, 0);
            message.setMaxLines(2);
            message.setEllipsize(android.text.TextUtils.TruncateAt.END);
            body.addView(message);

            if (!item.imageUrl.isEmpty()) {
                TextView image = new TextView(this);
                image.setText(item.imageUrl);
                image.setTextColor(getColor(R.color.text_secondary));
                image.setTextSize(12);
                image.setPadding(0, dp(7), 0, 0);
                body.addView(image);
            }

            TextView meta = new TextView(this);
            meta.setText(dateFormat.format(new Date(item.time)));
            meta.setTextColor(getColor(R.color.text_secondary));
            meta.setTextSize(12);
            meta.setPadding(0, dp(7), 0, 0);
            body.addView(meta);

            card.addView(body);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 0, 0, dp(8));
            notificationsList.addView(card, lp);
        }
    }

    private String getLocalizedTitle(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String s = raw.trim();
        if (s.equalsIgnoreCase("Bilik Berjaya Dicipta") || s.equalsIgnoreCase("Room Created Successfully")) {
            return getString(R.string.notif_room_created_title);
        }
        if (s.equalsIgnoreCase("Sertai Bilik Berjaya") || s.equalsIgnoreCase("Joined Room Successfully")) {
            return getString(R.string.notif_room_joined_title);
        }
        if (s.equalsIgnoreCase("Bilik Dipadamkan") || s.equalsIgnoreCase("Room Deleted")) {
            return getString(R.string.notif_room_deleted_title);
        }
        if (s.equalsIgnoreCase("Meninggalkan Bilik") || s.equalsIgnoreCase("Left Room")) {
            return getString(R.string.notif_room_left_title);
        }
        if (s.equalsIgnoreCase("Permintaan Rakan") || s.equalsIgnoreCase("Friend Request")) {
            return getString(R.string.notif_friend_request_title);
        }
        if (s.equalsIgnoreCase("Permintaan Rakan Diterima") || s.equalsIgnoreCase("Friend Request Accepted")) {
            return getString(R.string.notif_friend_accepted_title);
        }
        return raw;
    }

    private String getLocalizedMessage(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String s = raw.trim();
        if (s.startsWith("Anda telah berjaya mencipta bilik ")) {
            String code = s.substring("Anda telah berjaya mencipta bilik ".length()).replace(".", "").trim();
            return getString(R.string.notif_room_created_msg, code);
        }
        if (s.startsWith("You have successfully created room ")) {
            String code = s.substring("You have successfully created room ".length()).replace(".", "").trim();
            return getString(R.string.notif_room_created_msg, code);
        }
        if (s.startsWith("Anda telah berjaya menyertai bilik ")) {
            String code = s.substring("Anda telah berjaya menyertai bilik ".length()).replace(".", "").trim();
            return getString(R.string.notif_room_joined_msg, code);
        }
        if (s.startsWith("You have successfully joined room ")) {
            String code = s.substring("You have successfully joined room ".length()).replace(".", "").trim();
            return getString(R.string.notif_room_joined_msg, code);
        }
        if (s.startsWith("Anda telah memadamkan bilik ")) {
            String code = s.substring("Anda telah memadamkan bilik ".length()).replace(".", "").trim();
            return getString(R.string.notif_room_deleted_msg, code);
        }
        if (s.startsWith("You have deleted room ")) {
            String code = s.substring("You have deleted room ".length()).replace(".", "").trim();
            return getString(R.string.notif_room_deleted_msg, code);
        }
        if (s.startsWith("Anda telah keluar dari bilik ")) {
            String code = s.substring("Anda telah keluar dari bilik ".length()).replace(".", "").trim();
            return getString(R.string.notif_room_left_msg, code);
        }
        if (s.startsWith("You have left room ")) {
            String code = s.substring("You have left room ".length()).replace(".", "").trim();
            return getString(R.string.notif_room_left_msg, code);
        }
        if (s.endsWith(" telah menghantar permintaan rakan.")) {
            String who = s.substring(0, s.length() - " telah menghantar permintaan rakan.".length()).trim();
            return getString(R.string.notif_friend_request_msg, who);
        }
        if (s.endsWith(" sent you a friend request.")) {
            String who = s.substring(0, s.length() - " sent you a friend request.".length()).trim();
            return getString(R.string.notif_friend_request_msg, who);
        }
        if (s.endsWith(" telah menerima permintaan rakan anda!")) {
            String who = s.substring(0, s.length() - " telah menerima permintaan rakan anda!".length()).trim();
            return getString(R.string.notif_friend_accepted_msg, who);
        }
        if (s.endsWith(" accepted your friend request!")) {
            String who = s.substring(0, s.length() - " accepted your friend request!".length()).trim();
            return getString(R.string.notif_friend_accepted_msg, who);
        }
        return raw;
    }

    /** Paparkan NotificationDetail. */
    private void showNotificationDetail(Notice item) {
        if (item == null) return;
        if (detailTitle != null) detailTitle.setText(getLocalizedTitle(item.title));
        if (detailTime != null) {
            detailTime.setText(getString(R.string.notification_detail_sent, dateFormat.format(new Date(item.time))));
        }
        if (detailMessage != null) detailMessage.setText(getLocalizedMessage(item.message));
        if (detailSource != null) {
            if (item.imageUrl.isEmpty()) {
                detailSource.setVisibility(View.GONE);
            } else {
                detailSource.setText(getString(R.string.notification_detail_source, item.imageUrl));
                detailSource.setVisibility(View.VISIBLE);
            }
        }
        if (btnDetailCheckin != null) {
            String combined = ((item.title != null ? item.title : "") + " " + (item.message != null ? item.message : "")).toLowerCase(Locale.ROOT);
            boolean isEmergencyRelated = combined.contains("sos") || combined.contains("kecemasan") || combined.contains("emergency") || combined.contains("check-in") || combined.contains("checkin") || combined.contains("bantuan") || combined.contains("alert");
            btnDetailCheckin.setVisibility(isEmergencyRelated ? View.VISIBLE : View.GONE);
        }
        showingDetail = true;
        if (listContent != null) listContent.setVisibility(View.GONE);
        if (detailContent != null) detailContent.setVisibility(View.VISIBLE);
    }

    /** Paparkan NotificationsList. */
    private void showNotificationsList() {
        showingDetail = false;
        if (detailContent != null) detailContent.setVisibility(View.GONE);
        if (listContent != null) listContent.setVisibility(View.VISIBLE);
    }

    private void applySelectableForeground(MaterialCardView card) {
        try {
            TypedValue outValue = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            card.setForeground(androidx.core.content.ContextCompat.getDrawable(this, outValue.resourceId));
        } catch (Exception ignored) {
        }
    }

    private void renderError(String message) {
        if (notificationsScroll != null) notificationsScroll.setVisibility(View.GONE);
        if (empty != null) {
            empty.setVisibility(View.VISIBLE);
            empty.setText(message == null || message.trim().isEmpty()
                    ? getString(R.string.toast_generic_error)
                    : message);
        }
    }

    private void confirmClearAllNotifications() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.notification_clear_all_confirm_title)
                .setMessage(R.string.notification_clear_all_confirm_msg)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.room_delete, (dialog, which) -> clearAllNotifications())
                .show();
    }

    /** Padam atau bersihkan AllNotifications. */
    private void clearAllNotifications() {
        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        String uid = currentUser != null ? currentUser.getUid() : "";

        if (!uid.isEmpty()) {
            try {
                FirebaseDatabase
                        .getInstance(FirebaseRoomClient.DATABASE_URL)
                        .getReference("userNotifications")
                        .child(uid)
                        .removeValue();
            } catch (Exception ignored) {
            }
        }

        userSnapshot = null;
        globalSnapshot = null;
        UserPrefs.setUnreadNotifications(this, 0);
        combineAndRender();

        android.widget.Toast.makeText(this, R.string.notification_cleared_toast, android.widget.Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class Notice {
        final String id;
        final String title;
        final String message;
        final String imageUrl;
        final long time;

        Notice(String id, String title, String message, String imageUrl, long time) {
            this.id = id;
            this.title = title;
            this.message = message;
            this.imageUrl = imageUrl;
            this.time = time;
        }

        static Notice from(DataSnapshot snap) {
            if (snap == null || !snap.exists()) return null;
            String title = str(snap.child("title").getValue());
            String message = str(snap.child("message").getValue());
            String imageUrl = str(snap.child("imageUrl").getValue());
            String id = str(snap.child("id").getValue());
            if (id.isEmpty()) id = str(snap.getKey());
            long sentAt = num(snap.child("sentAt").getValue());
            long createdAt = num(snap.child("createdAt").getValue());
            long time = sentAt > 0 ? sentAt : createdAt;
            if (title.isEmpty() && message.isEmpty()) return null;
            return new Notice(
                    id.isEmpty() ? String.valueOf(time) : id,
                    title.isEmpty() ? "ResQTap" : title,
                    message,
                    imageUrl,
                    time > 0 ? time : System.currentTimeMillis()
            );
        }

        private static String str(Object value) {
            return value == null ? "" : String.valueOf(value).trim();
        }

        private static long num(Object value) {
            if (value instanceof Number) return ((Number) value).longValue();
            try {
                return Long.parseLong(str(value));
            } catch (Exception e) {
                return 0L;
            }
        }
    }
}
