package com.example.resqtap.room;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.resqtap.R;
import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.auth.LoginActivity;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class RoomChatActivity extends BaseActivity {

    public static final String EXTRA_ROOM_CODE = "extra_room_code";
    public static final String EXTRA_ROOM_NAME = "extra_room_name";

    private String roomCode = "";
    private String roomName = "";
    private String currentUid = "";
    private String currentDisplayName = "";
    private String currentPhotoUrl = "";

    private RecyclerView rvMessages;
    private RoomChatAdapter adapter;
    private EditText etInput;
    private MaterialButton btnSend;
    private View layoutEmpty;
    private TextView tvRoomName;
    private TextView tvRoomStatus;
    private TextView tvRoomBadge;

    private ValueEventListener messagesListener;
    private ValueEventListener roomNameListener;

    public static void start(Context context, String roomCode, String roomName) {
        Intent intent = new Intent(context, RoomChatActivity.class);
        intent.putExtra(EXTRA_ROOM_CODE, roomCode);
        intent.putExtra(EXTRA_ROOM_NAME, roomName);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_room_chat);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        currentUid = user.getUid();
        currentDisplayName = UserPrefs.getName(this);
        if (currentDisplayName == null || currentDisplayName.trim().isEmpty()) {
            currentDisplayName = user.getDisplayName() != null ? user.getDisplayName() : "User";
        }
        currentPhotoUrl = UserPrefs.getPhotoUrl(this);

        roomCode = getIntent().getStringExtra(EXTRA_ROOM_CODE);
        if (roomCode == null) roomCode = "";
        roomCode = roomCode.trim().toUpperCase(java.util.Locale.ROOT);

        roomName = getIntent().getStringExtra(EXTRA_ROOM_NAME);
        if (roomName == null) roomName = "";

        initViews();
        setupWindowInsets();
        setupRecyclerView();
        setupListeners();
        loadRoomData();
    }

    private void initViews() {
        MaterialButton btnBack = findViewById(R.id.btn_back);
        tvRoomName = findViewById(R.id.tv_room_name);
        tvRoomStatus = findViewById(R.id.tv_room_status);
        tvRoomBadge = findViewById(R.id.room_chip_badge);
        rvMessages = findViewById(R.id.rv_chat_messages);
        etInput = findViewById(R.id.et_message_input);
        btnSend = findViewById(R.id.btn_send_message);
        layoutEmpty = findViewById(R.id.layout_empty_state);

        btnBack.setOnClickListener(v -> finish());

        if (!roomName.isEmpty()) {
            tvRoomName.setText(roomName);
        } else {
            tvRoomName.setText(getString(R.string.room_chat_title));
        }

        if (!roomCode.isEmpty()) {
            tvRoomBadge.setText(roomCode);
        }
    }

    private void setupWindowInsets() {
        View main = findViewById(R.id.main);
        View topBar = findViewById(R.id.top_bar);
        View bottomComposer = findViewById(R.id.bottom_composer);

        ViewCompat.setOnApplyWindowInsetsListener(main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

            // Top padding for status bar
            if (topBar != null) {
                topBar.setPadding(topBar.getPaddingLeft(), systemBars.top + 8, topBar.getPaddingRight(), topBar.getPaddingBottom());
            }

            // Bottom padding for keyboard / navigation bar
            int bottomInset = Math.max(systemBars.bottom, ime.bottom);
            if (bottomComposer != null) {
                bottomComposer.setPadding(
                        bottomComposer.getPaddingLeft(),
                        bottomComposer.getPaddingTop(),
                        bottomComposer.getPaddingRight(),
                        bottomInset > 0 ? bottomInset : 10
                );
            }

            return insets;
        });
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);

        adapter = new RoomChatAdapter(this, currentUid);
        rvMessages.setAdapter(adapter);

        // Scroll down when keyboard appears
        rvMessages.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom < oldBottom && adapter.getItemCount() > 0) {
                rvMessages.postDelayed(() -> rvMessages.smoothScrollToPosition(adapter.getItemCount() - 1), 100);
            }
        });
    }

    private void setupListeners() {
        btnSend.setOnClickListener(v -> sendMessage());

        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        // Quick Emergency Preset Chips
        TextView chipDanger = findViewById(R.id.chip_danger);
        TextView chipMedical = findViewById(R.id.chip_medical);
        TextView chipLocation = findViewById(R.id.chip_location);
        TextView chipHelpComing = findViewById(R.id.chip_help_coming);
        TextView chipSafe = findViewById(R.id.chip_safe);

        if (chipDanger != null) chipDanger.setOnClickListener(v -> sendDirectMessage(getString(R.string.chip_emergency_danger)));
        if (chipMedical != null) chipMedical.setOnClickListener(v -> sendDirectMessage(getString(R.string.chip_emergency_medical)));
        if (chipLocation != null) chipLocation.setOnClickListener(v -> sendDirectMessage(getString(R.string.chip_emergency_location)));
        if (chipHelpComing != null) chipHelpComing.setOnClickListener(v -> sendDirectMessage(getString(R.string.chip_emergency_help_coming)));
        if (chipSafe != null) chipSafe.setOnClickListener(v -> sendDirectMessage(getString(R.string.chip_emergency_safe)));
    }

    private void sendDirectMessage(String text) {
        if (text == null || text.trim().isEmpty()) return;
        FirebaseRoomClient.sendRoomMessage(roomCode, currentUid, currentDisplayName, currentPhotoUrl, text.trim(), (ok, err) -> {
            if (!ok && !isFinishing()) {
                runOnUiThread(() -> Toast.makeText(RoomChatActivity.this, "Failed to send message", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void loadRoomData() {
        if (roomCode.isEmpty()) return;

        // Fetch room name if not available
        roomNameListener = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                .getReference("rooms").child(roomCode).child("name")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists() && snapshot.getValue() != null) {
                            String name = String.valueOf(snapshot.getValue()).trim();
                            if (!name.isEmpty()) {
                                roomName = name;
                                tvRoomName.setText(roomName);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });

        // Listen for messages
        messagesListener = FirebaseRoomClient.listenRoomMessages(roomCode, messages -> {
            if (isFinishing() || isDestroyed()) return;

            runOnUiThread(() -> {
                if (messages == null || messages.isEmpty()) {
                    layoutEmpty.setVisibility(View.VISIBLE);
                    adapter.setMessages(new ArrayList<>());
                } else {
                    layoutEmpty.setVisibility(View.GONE);
                    adapter.setMessages(messages);
                    rvMessages.scrollToPosition(messages.size() - 1);
                }
            });
        });
    }

    private void sendMessage() {
        String text = etInput.getText() != null ? etInput.getText().toString().trim() : "";
        if (text.isEmpty()) return;

        etInput.setText("");

        FirebaseRoomClient.sendRoomMessage(roomCode, currentUid, currentDisplayName, currentPhotoUrl, text, (ok, err) -> {
            if (!ok && !isFinishing()) {
                runOnUiThread(() -> Toast.makeText(RoomChatActivity.this, "Failed to send message", Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messagesListener != null && !roomCode.isEmpty()) {
            FirebaseRoomClient.stopListeningRoomMessages(roomCode, messagesListener);
        }
        if (roomNameListener != null && !roomCode.isEmpty()) {
            FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                    .getReference("rooms").child(roomCode).child("name")
                    .removeEventListener(roomNameListener);
        }
    }
}
