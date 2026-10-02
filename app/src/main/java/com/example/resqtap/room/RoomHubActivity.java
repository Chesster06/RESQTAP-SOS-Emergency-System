package com.example.resqtap.room;
import com.example.resqtap.R;

import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.auth.LoginActivity;
import com.example.resqtap.sos.SosServiceStarter;
import com.example.resqtap.utils.BottomNavUtils;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.Toast;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * RoomHubActivity
 * Hab Safety Room: create room baru, join guna passcode, atau tengok list bilik.
 */
public class RoomHubActivity extends BaseActivity {
    private static final int ROOM_CODE_LEN = 6;
    private static final int ROOM_CODE_MAX = 12;
    private static final String TAG = "RoomHubActivity";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String uid;

    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_room_hub);
        android.view.View root = findViewById(R.id.main);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        BottomNavUtils.setup(bottomNav, this, R.id.nav_bottom_room);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            try {
                int pl = bottomNav.getPaddingLeft();
                int pt = bottomNav.getPaddingTop();
                int pr = bottomNav.getPaddingRight();
                bottomNav.setPadding(pl, pt, pr, systemBars.bottom);
            } catch (Exception ignored) {
            }
            return insets;
        });

        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        uid = current.getUid();

        TextInputEditText inputRoomCode = findViewById(R.id.input_room_code);
        TextInputEditText inputRoomPassword = findViewById(R.id.input_room_password);
        MaterialButton btnCreate = findViewById(R.id.btn_create_room);
        MaterialButton btnJoin = findViewById(R.id.btn_join_room);
        android.view.View btnBack = findViewById(R.id.btn_back);

        btnCreate.setOnClickListener(v -> {
            String rawCode = inputRoomCode.getText() == null ? "" : inputRoomCode.getText().toString().trim().toUpperCase();
            String code = rawCode.isEmpty() ? RoomCodeUtils.generate(ROOM_CODE_LEN) : sanitizeCode(rawCode);
            String password = inputRoomPassword.getText() == null ? "" : inputRoomPassword.getText().toString().trim();
            if (password.isEmpty()) {
                Toast.makeText(this, R.string.toast_room_password_required, Toast.LENGTH_SHORT).show();
                return;
            }
            executor.execute(() -> {
                try {
                    String name = UserPrefs.getName(this);
                    FirebaseRoomClient.createRoom(code, uid, name, password);
                    runOnUiThread(() -> {
                        try {
                            UserPrefs.setActiveRoomCode(RoomHubActivity.this, code);
                            android.util.Log.d("SOS_DEBUG", "Saved currentRoomId: " + code);
                            android.util.Log.d("SOS_DEBUG", "Login success, starting SOS service");
                        } catch (Exception ignored) {
                        }
                        SosServiceStarter.start(RoomHubActivity.this, code);
                        try {
                            com.example.resqtap.notification.NotificationUtils.notifyRoomCreated(RoomHubActivity.this, code);
                        } catch (Exception ignored) {
                        }
                        try {
                            android.util.Log.d("SOS_DEBUG", "LiveRoomTrackingService started");
                        } catch (Exception ignored) {
                        }
                        Toast.makeText(this, R.string.room_created_success, Toast.LENGTH_SHORT).show();
                        finish();
                    });
                } catch (Exception e) {
                    Log.e(TAG, "createRoom failed. code=" + code + " uid=" + uid, e);
                    runOnUiThread(() -> {
                        String msg = e.getMessage() == null ? "" : e.getMessage().trim();
                        if ("already_joined".equalsIgnoreCase(msg) || "owner_room".equalsIgnoreCase(msg)) {
                            Toast.makeText(this, R.string.room_already_joined, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if ("room_code_exists".equalsIgnoreCase(msg)) {
                            Toast.makeText(this, R.string.room_code_exists, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Toast.makeText(this, toUserMessage("Create room", e), Toast.LENGTH_LONG).show();
                    });
                }
            });
        });

        btnJoin.setOnClickListener(v -> {
            String code = inputRoomCode.getText() == null ? "" : inputRoomCode.getText().toString().trim().toUpperCase();
            if (code.length() < 4) {
                Toast.makeText(this, R.string.room_code_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            code = sanitizeCode(code);
            String password = inputRoomPassword.getText() == null ? "" : inputRoomPassword.getText().toString().trim();
            if (password.isEmpty()) {
                Toast.makeText(this, R.string.toast_room_password_required, Toast.LENGTH_SHORT).show();
                return;
            }
            String name = UserPrefs.getName(this);
            if (name == null) name = "";
            String finalCode = code;
            String finalName = name;
            String finalPassword = password;
            executor.execute(() -> {
                try {
                    FirebaseRoomClient.joinRoom(finalCode, uid, finalName, finalPassword);
                    runOnUiThread(() -> {
                        try {
                            UserPrefs.setActiveRoomCode(RoomHubActivity.this, finalCode);
                            android.util.Log.d("SOS_DEBUG", "Saved currentRoomId: " + finalCode);
                            android.util.Log.d("SOS_DEBUG", "Login success, starting SOS service");
                        } catch (Exception ignored) {
                        }
                        SosServiceStarter.start(RoomHubActivity.this, finalCode);
                        try {
                            com.example.resqtap.notification.NotificationUtils.notifyRoomJoined(RoomHubActivity.this, finalCode);
                        } catch (Exception ignored) {
                        }
                        try {
                            android.util.Log.d("SOS_DEBUG", "LiveRoomTrackingService started");
                        } catch (Exception ignored) {
                        }
                        Toast.makeText(this, R.string.room_joined_success, Toast.LENGTH_SHORT).show();
                        finish();
                    });
                } catch (Exception e) {
                    Log.e(TAG, "joinRoom failed. code=" + finalCode + " uid=" + uid, e);
                    runOnUiThread(() -> {
                        String msg = e.getMessage() == null ? "" : e.getMessage();
                        if ("already_joined".equalsIgnoreCase(msg) || "owner_room".equalsIgnoreCase(msg)) {
                            Toast.makeText(this, R.string.room_already_joined, Toast.LENGTH_SHORT).show();
                        } else if ("invalid_room_code".equalsIgnoreCase(msg)) {
                            Toast.makeText(this, R.string.room_code_invalid, Toast.LENGTH_SHORT).show();
                        } else if ("room_code_exists".equalsIgnoreCase(msg)) {
                            Toast.makeText(this, R.string.room_code_exists, Toast.LENGTH_SHORT).show();
                        } else if ("incorrect_password".equalsIgnoreCase(msg)) {
                            Toast.makeText(this, R.string.toast_incorrect_room_password, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, toUserMessage("Join room", e), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });
        });

        btnBack.setOnClickListener(v -> finish());

        inputRoomCode.setFilters(new InputFilter[]{new InputFilter.LengthFilter(ROOM_CODE_MAX)});
        inputRoomCode.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    @Override
            public void afterTextChanged(Editable s) {
                if (s == null) return;
                String up = s.toString().toUpperCase();
                if (!up.equals(s.toString())) {
                    inputRoomCode.removeTextChangedListener(this);
                    inputRoomCode.setText(up);
                    inputRoomCode.setSelection(up.length());
                    inputRoomCode.addTextChangedListener(this);
                }
            }
        });
    }

    private String sanitizeCode(String input) {
        String code = input.trim().toUpperCase();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < code.length() && sb.length() < ROOM_CODE_MAX; i++) {
            char c = code.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) sb.append(c);
        }
        return sb.toString();
    }

    private String toUserMessage(String action, Exception e) {
        if (e == null) return action + " failed.";
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();

        String msg = e.getMessage() == null ? "" : e.getMessage().trim();
        if ("backend_unreachable".equalsIgnoreCase(msg)) return action + " failed: backend unreachable.";
        if ("backend_timeout".equalsIgnoreCase(msg)) return action + " failed: backend timeout (check internet / rules).";
        if ("permission_denied".equalsIgnoreCase(msg)) return action + " failed: permission denied (Firebase Rules).";
        if ("create_room_createdAt_denied".equalsIgnoreCase(msg)) return action + " failed: rules deny rooms/{code}/createdAt.";
        if ("create_room_instanceId_denied".equalsIgnoreCase(msg)) return action + " failed: rules deny rooms/{code}/instanceId.";
        if ("create_room_members_denied".equalsIgnoreCase(msg)) return action + " failed: rules deny rooms/{code}/members/{uid}.";
        if ("create_room_userRooms_denied".equalsIgnoreCase(msg)) return action + " failed: rules deny userRooms/{uid}/{code}.";
        if ("invalid_room_code".equalsIgnoreCase(msg)) return action + " failed: invalid room code.";
        if ("invalid_uid".equalsIgnoreCase(msg)) return action + " failed: invalid uid.";
        if ("request_failed".equalsIgnoreCase(msg)) return action + " failed: request_failed.";
        if ("invalid_response".equalsIgnoreCase(msg)) return action + " failed: invalid_response.";

        if (!msg.isEmpty()) return action + " failed: " + msg;
        return action + " failed: " + root.getClass().getSimpleName();
    }
}
