package com.example.resqtap.room;
import com.example.resqtap.R;

import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.auth.LoginActivity;
import com.example.resqtap.home.MainActivity;
import com.example.resqtap.sos.SosServiceStarter;
import com.example.resqtap.utils.BottomNavUtils;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.switchmaterial.SwitchMaterial;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.resqtap.friend.FirebaseFriendClient;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * RoomListActivity
 * Senarai bilik keselamatan yang user tengah join.
 */
public class RoomListActivity extends BaseActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ArrayList<FirebaseRoomClient.RoomInfo> rooms = new ArrayList<>();
    private RoomListAdapter adapter;
    private TextInputEditText searchInput;
    private View emptyView;
    private TextView emptyTitle;
    private TextView emptyDesc;
    private String uid;
    private ChildEventListener userRoomsListener;

    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_room_list);
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

        emptyView = findViewById(R.id.tv_empty);
        emptyTitle = findViewById(R.id.tv_empty_title);
        emptyDesc = findViewById(R.id.tv_empty_desc);
        searchInput = findViewById(R.id.input_search);

        android.view.View back = findViewById(R.id.btn_back);
        if (back != null) {
            back.setOnClickListener(v -> {
                Intent i = new Intent(RoomListActivity.this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
                finish();
            });
        }

        RecyclerView rv = findViewById(R.id.rv_rooms);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RoomListAdapter(new RoomListAdapter.Listener() {
    @Override
            public void onView(FirebaseRoomClient.RoomInfo info) {
                Intent i = new Intent(RoomListActivity.this, RoomMapActivity.class);
                i.putExtra(RoomMapActivity.EXTRA_ROOM_CODE, info.code);
                startActivity(i);
            }

            @Override
            public void onEdit(FirebaseRoomClient.RoomInfo info) {
                showRoomSettingsDialog(info);
            }

            @Override
            public void onDelete(FirebaseRoomClient.RoomInfo info) {
                confirmDeleteRoom(info, null);
            }
        });
        rv.setAdapter(adapter);

        TextInputLayout searchLayout = findViewById(R.id.layout_search);
        if (searchLayout != null) {
            searchLayout.setEndIconOnClickListener(v -> showCreateRoomDialog());
        }

        if (searchInput != null) {
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter(); }
                @Override public void afterTextChanged(Editable s) {}
            });
        }
    }

    /** Paparkan CreateRoomDialog. */
    private void showCreateRoomDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_room, null);
        EditText inputRoomName = dialogView.findViewById(R.id.input_create_room_name);
        RecyclerView rvFriends = dialogView.findViewById(R.id.rv_select_friends);
        View tvNoFriends = dialogView.findViewById(R.id.tv_no_friends_prompt);
        View progress = dialogView.findViewById(R.id.progress_loading_friends);
        TextView tvError = dialogView.findViewById(R.id.tv_create_room_error);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel_create_room);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btn_confirm_create_room);

        FriendSelectAdapter selectAdapter = new FriendSelectAdapter();
        rvFriends.setLayoutManager(new LinearLayoutManager(this));
        rvFriends.setAdapter(selectAdapter);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        progress.setVisibility(View.VISIBLE);
        FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                .getReference("userFriends").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    /** Callback apabila data Firebase Realtime Database berubah. */
    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (isFinishing() || isDestroyed()) return;
                        progress.setVisibility(View.GONE);
                        List<FirebaseFriendClient.FriendInfo> friends = new ArrayList<>();
                        if (snapshot.exists()) {
                            for (DataSnapshot child : snapshot.getChildren()) {
                                FirebaseFriendClient.FriendInfo info = FirebaseFriendClient.FriendInfo.from(child);
                                if (info != null) friends.add(info);
                            }
                        }
                        if (friends.isEmpty()) {
                            tvNoFriends.setVisibility(View.VISIBLE);
                        } else {
                            tvNoFriends.setVisibility(View.GONE);
                            selectAdapter.setFriends(friends);
                        }
                    }

                    /** Callback sekiranya operasi Firebase dibatalkan atau gagal. */
    @Override
                    public void onCancelled(DatabaseError error) {
                        if (isFinishing() || isDestroyed()) return;
                        progress.setVisibility(View.GONE);
                        tvNoFriends.setVisibility(View.VISIBLE);
                    }
                });

        btnConfirm.setOnClickListener(v -> {
            String roomName = inputRoomName.getText() != null ? inputRoomName.getText().toString().trim() : "";
            if (roomName.isEmpty()) {
                tvError.setText(R.string.room_enter_name_error);
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            tvError.setVisibility(View.GONE);
            btnConfirm.setEnabled(false);
            btnConfirm.setText(R.string.room_creating_status);

            List<FirebaseFriendClient.FriendInfo> selectedFriends = selectAdapter.getSelectedFriends();
            String myName = UserPrefs.getName(this);
            if (myName == null || myName.trim().isEmpty()) myName = getString(R.string.friend_user_default);

            String finalMyName = myName;
            executor.execute(() -> {
                try {
                    String code = FirebaseRoomClient.createRoomWithFriends(roomName, uid, finalMyName, selectedFriends);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        dialog.dismiss();
                        try {
                            UserPrefs.setActiveRoomCode(RoomListActivity.this, code);
                            SosServiceStarter.start(RoomListActivity.this, code);
                        } catch (Exception ignored) {}
                        Intent i = new Intent(RoomListActivity.this, RoomMapActivity.class);
                        i.putExtra(RoomMapActivity.EXTRA_ROOM_CODE, code);
                        startActivity(i);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        btnConfirm.setEnabled(true);
                        btnConfirm.setText(R.string.room_create);
                        tvError.setText(getString(R.string.room_error_prefix, e.getMessage()));
                        tvError.setVisibility(View.VISIBLE);
                    });
                }
            });
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int maxPx = Math.round(380 * getResources().getDisplayMetrics().density);
            int width = Math.min((int) (screenWidth * 0.88f), maxPx);
            dialog.getWindow().setLayout(
                width,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
            dialog.getWindow().setGravity(android.view.Gravity.CENTER);
        }
    }

    /** Aktiviti aktif dan sedia untuk interaksi pengguna. */
    @Override
    protected void onResume() {
        super.onResume();
        startUserRoomsListener();
        reloadRooms();
    }

    /** Aktiviti dijeda sementara. */
    @Override
    protected void onPause() {
        super.onPause();
        stopUserRoomsListener();
    }

    private void reloadRooms() {
        executor.execute(() -> {
            ArrayList<FirebaseRoomClient.RoomInfo> loaded = new ArrayList<>();
            try {
                loaded = FirebaseRoomClient.fetchUserRooms(uid);
            } catch (Exception e) {
                if (e instanceof InterruptedException || isFinishing() || isDestroyed()) return;
                final String err = e.getMessage() != null ? e.getMessage() : e.toString();
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        android.widget.Toast.makeText(RoomListActivity.this, "Error fetching rooms: " + err, android.widget.Toast.LENGTH_LONG).show();
                    }
                });
            }
            ArrayList<FirebaseRoomClient.RoomInfo> finalLoaded = loaded;
            runOnUiThread(() -> {
                rooms.clear();
                rooms.addAll(finalLoaded);
                applyFilter();
            });
        });
    }

    private void startUserRoomsListener() {
        if (userRoomsListener != null) return;
        if (uid == null || uid.trim().isEmpty()) return;
        try {
            userRoomsListener = new ChildEventListener() {
                @Override public void onChildAdded(DataSnapshot snapshot, String previousChildName) { reloadRooms(); }
                @Override public void onChildChanged(DataSnapshot snapshot, String previousChildName) { reloadRooms(); }
                @Override public void onChildRemoved(DataSnapshot snapshot) { reloadRooms(); }
                @Override public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}
                @Override public void onCancelled(DatabaseError error) {}
            };
            FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                    .getReference("userRooms")
                    .child(uid)
                    .addChildEventListener(userRoomsListener);
        } catch (Exception ignored) {
        }
    }

    private void stopUserRoomsListener() {
        if (userRoomsListener == null) return;
        try {
            FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                    .getReference("userRooms")
                    .child(uid)
                    .removeEventListener(userRoomsListener);
        } catch (Exception ignored) {
        }
        userRoomsListener = null;
    }
}
