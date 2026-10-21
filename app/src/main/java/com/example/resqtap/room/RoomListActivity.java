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

    private void applyFilter() {
        String q = searchInput == null || searchInput.getText() == null ? "" : searchInput.getText().toString().trim();
        if (q.isEmpty()) {
            adapter.submit(new ArrayList<>(rooms));
            updateEmpty(rooms.size(), false);
            return;
        }

        String qq = q.toLowerCase(Locale.ROOT);
        ArrayList<FirebaseRoomClient.RoomInfo> filtered = new ArrayList<>();
        for (FirebaseRoomClient.RoomInfo r : rooms) {
            if (r == null) continue;
            String code = r.code == null ? "" : r.code.toLowerCase(Locale.ROOT);
            String role = r.role == null ? "" : r.role.toLowerCase(Locale.ROOT);
            String name = r.name == null ? "" : r.name.toLowerCase(Locale.ROOT);
            if (code.contains(qq) || role.contains(qq) || name.contains(qq)) filtered.add(r);
        }
        adapter.submit(filtered);
        updateEmpty(filtered.size(), true);
    }

    /** Simpan atau hantar data Empty. */
    private void updateEmpty(int count, boolean isSearching) {
        if (emptyView == null) return;
        if (count <= 0) {
            emptyView.setVisibility(View.VISIBLE);
            if (emptyTitle != null) {
                emptyTitle.setText(isSearching ? R.string.room_search_not_found : R.string.room_empty_title);
            }
            if (emptyDesc != null) {
                emptyDesc.setText(isSearching ? R.string.room_search_empty_desc : R.string.room_empty_desc);
            }
        } else {
            emptyView.setVisibility(View.GONE);
        }
    }

    /** Memaparkan dialog tetapan dan kebenaran bilik (Room Settings & Permissions). */
    private void showRoomSettingsDialog(FirebaseRoomClient.RoomInfo info) {
        if (info == null) return;
        final String code = info.code == null ? "" : info.code.trim();
        if (code.isEmpty()) return;

        View v = LayoutInflater.from(this).inflate(R.layout.dialog_room_settings, null);
        TextView tvRoomCode = v.findViewById(R.id.tv_settings_room_code);
        EditText inputName = v.findViewById(R.id.input_settings_room_name);
        SwitchMaterial swSos = v.findViewById(R.id.sw_allow_sos);
        SwitchMaterial swBell = v.findViewById(R.id.sw_allow_bell);
        SwitchMaterial swInvites = v.findViewById(R.id.sw_allow_invites);
        TextView tvError = v.findViewById(R.id.tv_settings_error);
        ProgressBar progressLoading = v.findViewById(R.id.progress_settings_loading);
        ImageButton btnClose = v.findViewById(R.id.btn_close_settings);
        Handler autoSaveHandler = new Handler(Looper.getMainLooper());
        final boolean isCreator = "creator".equalsIgnoreCase(info.role);

        if (tvRoomCode != null) {
            String roleLabel = isCreator ? "Creator" : "Member";
            tvRoomCode.setText(roleLabel + " • " + code);
        }

        String initialName = info.name == null ? "" : info.name.trim();
        if (initialName.isEmpty()) initialName = code;
        if (inputName != null) {
            inputName.setText(initialName);
            inputName.setSelection(initialName.length());
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(v)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(click -> dialog.dismiss());
        }
        dialog.setOnDismissListener(d -> autoSaveHandler.removeCallbacksAndMessages(null));

        final boolean[] isInitialLoading = {true};

        Runnable triggerAutoSave = () -> {
            if (isFinishing() || isDestroyed()) return;
            String n = inputName != null && inputName.getText() != null ? inputName.getText().toString().trim() : "";
            if (n.isEmpty()) {
                if (tvError != null) {
                    tvError.setText(R.string.room_name_hint);
                    tvError.setVisibility(View.VISIBLE);
                }
                return;
            }

            boolean allowSos = swSos != null && swSos.isChecked();
            boolean allowBell = swBell != null && swBell.isChecked();
            boolean allowInvites = swInvites != null && swInvites.isChecked();
            FirebaseRoomClient.RoomPermissions p = new FirebaseRoomClient.RoomPermissions(allowSos, allowBell, true, allowInvites);

            if (tvError != null) tvError.setVisibility(View.GONE);
            if (progressLoading != null) progressLoading.setVisibility(View.VISIBLE);

            executor.execute(() -> {
                try {
                    FirebaseRoomClient.updateRoomSettingsAndName(uid, code, n, p);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (progressLoading != null) progressLoading.setVisibility(View.GONE);
                        reloadRooms();
                    });
                } catch (Exception e) {
                    final String errMsg = e.getMessage() != null ? e.getMessage() : "Update failed";
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (progressLoading != null) progressLoading.setVisibility(View.GONE);
                        if (tvError != null) {
                            tvError.setText(getString(R.string.toast_failed_with_reason, errMsg));
                            tvError.setVisibility(View.VISIBLE);
                        }
                    });
                }
            });
        };

        if (!isCreator) {
            if (inputName != null) {
                inputName.setEnabled(false);
                inputName.setFocusable(false);
            }
            if (swSos != null) swSos.setEnabled(false);
            if (swBell != null) swBell.setEnabled(false);
            if (swInvites != null) swInvites.setEnabled(false);
        } else {
            if (swSos != null) {
                swSos.setOnCheckedChangeListener((btn, isChecked) -> {
                    if (!isInitialLoading[0]) triggerAutoSave.run();
                });
            }
            if (swBell != null) {
                swBell.setOnCheckedChangeListener((btn, isChecked) -> {
                    if (!isInitialLoading[0]) triggerAutoSave.run();
                });
            }
            if (swInvites != null) {
                swInvites.setOnCheckedChangeListener((btn, isChecked) -> {
                    if (!isInitialLoading[0]) triggerAutoSave.run();
                });
            }

            View rowSos = v.findViewById(R.id.row_permission_sos);
            if (rowSos != null && swSos != null) rowSos.setOnClickListener(click -> swSos.toggle());
            View rowBell = v.findViewById(R.id.row_permission_bell);
            if (rowBell != null && swBell != null) rowBell.setOnClickListener(click -> swBell.toggle());
            View rowInv = v.findViewById(R.id.row_permission_invites);
            if (rowInv != null && swInvites != null) rowInv.setOnClickListener(click -> swInvites.toggle());

            if (inputName != null) {
                inputName.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    @Override
                    public void afterTextChanged(Editable s) {
                        if (isInitialLoading[0]) return;
                        autoSaveHandler.removeCallbacksAndMessages(null);
                        autoSaveHandler.postDelayed(triggerAutoSave, 600);
                    }
                });
            }
        }

        if (progressLoading != null) progressLoading.setVisibility(View.VISIBLE);
        FirebaseRoomClient.fetchRoomPermissions(code, permissions -> {
            if (isFinishing() || isDestroyed()) return;
            runOnUiThread(() -> {
                if (progressLoading != null) progressLoading.setVisibility(View.GONE);
                if (swSos != null) swSos.setChecked(permissions.allowSosAlarm);
                if (swBell != null) swBell.setChecked(permissions.allowTriggerBell);
                if (swInvites != null) swInvites.setChecked(permissions.allowMemberInvite);
                isInitialLoading[0] = false;
            });
        });

        View btnDeleteRoom = v.findViewById(R.id.btn_delete_room_dialog);
        if (btnDeleteRoom != null) {
            if (btnDeleteRoom instanceof com.google.android.material.button.MaterialButton) {
                com.google.android.material.button.MaterialButton mb = (com.google.android.material.button.MaterialButton) btnDeleteRoom;
                mb.setText(isCreator ? R.string.room_delete_room : R.string.room_leave);
                mb.setIconResource(isCreator ? R.drawable.ic_delete : R.drawable.ic_leave);
            }
            btnDeleteRoom.setOnClickListener(click -> confirmDeleteRoom(info, dialog));
        }

        dialog.show();
    }

    /**
     * Pengesahan padam atau keluar dari bilik.
     * Jika pembuat bilik (creator), pengguna wajib menaip nama bilik untuk mengesahkan pemadaman.
     * Jika ahli biasa (member), paparkan dialog pengesahan keluar bilik.
     */
    private void confirmDeleteRoom(FirebaseRoomClient.RoomInfo info, androidx.appcompat.app.AlertDialog parentDialog) {
        if (info == null) return;
        final boolean isCreator = "creator".equalsIgnoreCase(info.role);
        final String roomName = (info.name != null && !info.name.trim().isEmpty()) ? info.name.trim() : (info.code != null ? info.code.trim() : "");

        if (!isCreator) {
            new MaterialAlertDialogBuilder(RoomListActivity.this)
                    .setMessage(R.string.room_leave_confirm)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        if (parentDialog != null) {
                            try { parentDialog.dismiss(); } catch (Exception ignored) {}
                        }
                        executeRoomDeletion(info, false);
                    })
                    .show();
            return;
        }

        View confirmView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete_room, null);
        TextView tvTargetName = confirmView.findViewById(R.id.tv_delete_target_name);
        TextView tvPrompt = confirmView.findViewById(R.id.tv_delete_prompt);
        EditText inputConfirmName = confirmView.findViewById(R.id.input_confirm_room_name);
        TextView tvError = confirmView.findViewById(R.id.tv_confirm_delete_error);
        MaterialButton btnCancel = confirmView.findViewById(R.id.btn_cancel_delete_confirm);
        MaterialButton btnDelete = confirmView.findViewById(R.id.btn_action_delete_confirm);

        if (tvTargetName != null) {
            tvTargetName.setText(roomName);
        }
        if (tvPrompt != null) {
            tvPrompt.setText(getString(R.string.room_delete_confirm_prompt, roomName));
        }

        androidx.appcompat.app.AlertDialog confirmDialog = new MaterialAlertDialogBuilder(this)
                .setView(confirmView)
                .setCancelable(true)
                .create();

        if (confirmDialog.getWindow() != null) {
            confirmDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> confirmDialog.dismiss());
        }

        if (btnDelete != null) {
            btnDelete.setOnClickListener(v -> {
                String typed = inputConfirmName != null && inputConfirmName.getText() != null
                        ? inputConfirmName.getText().toString().trim() : "";
                if (!typed.equalsIgnoreCase(roomName)) {
                    if (tvError != null) {
                        tvError.setText(R.string.room_delete_name_mismatch);
                        tvError.setVisibility(View.VISIBLE);
                    }
                    return;
                }

                if (tvError != null) tvError.setVisibility(View.GONE);
                confirmDialog.dismiss();
                if (parentDialog != null) {
                    try { parentDialog.dismiss(); } catch (Exception ignored) {}
                }
                executeRoomDeletion(info, true);
            });
        }

        confirmDialog.show();
    }

    /** Pelaksanaan padam atau keluar bilik di latar belakang. */
    private void executeRoomDeletion(FirebaseRoomClient.RoomInfo info, boolean isCreator) {
        executor.execute(() -> {
            String err = "";
            try {
                if (isCreator) {
                    FirebaseRoomClient.deleteRoomAsCreator(uid, info.code);
                } else {
                    FirebaseRoomClient.deleteUserRoom(uid, info.code);
                }
                try {
                    com.example.resqtap.notification.NotificationUtils.notifyRoomDeleted(RoomListActivity.this, info.code, isCreator);
                } catch (Exception ignored) {
                }
            } catch (Exception e) {
                err = String.valueOf(e.getMessage() == null ? "" : e.getMessage()).trim();
            }
            try {
                String active = String.valueOf(UserPrefs.getActiveRoomCode(RoomListActivity.this) == null ? "" : UserPrefs.getActiveRoomCode(RoomListActivity.this)).trim().toUpperCase(java.util.Locale.ROOT);
                String code = String.valueOf(info.code == null ? "" : info.code).trim().toUpperCase(java.util.Locale.ROOT);
                if (!code.isEmpty() && code.equals(active)) {
                    UserPrefs.setActiveRoomCode(RoomListActivity.this, "");
                    SosServiceStarter.stop(RoomListActivity.this);
                }
            } catch (Exception ignored) {
            }
            String finalErr = err;
            if (!finalErr.isEmpty()) {
                runOnUiThread(() -> android.widget.Toast.makeText(
                        RoomListActivity.this,
                        getString(R.string.toast_failed_with_reason, finalErr),
                        android.widget.Toast.LENGTH_LONG
                ).show());
            }
            runOnUiThread(RoomListActivity.this::reloadRooms);
        });
    }

    @Override
    public void onBackPressed() {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
            return;
        } catch (Exception ignored) {
        }
        super.onBackPressed();
    }

    // SEKSYEN: ONDESTROY
    /** Pembersihan memori, buang listener, dan tutup sambungan. */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            executor.shutdownNow();
        } catch (Exception ignored) {
        }
    }
}
