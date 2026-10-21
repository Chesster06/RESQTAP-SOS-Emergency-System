package com.example.resqtap.friend;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.resqtap.R;
import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.home.MainActivity;
import com.example.resqtap.notification.NotificationUtils;
import com.example.resqtap.room.FirebaseRoomClient;
import com.example.resqtap.utils.BottomNavUtils;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * FriendsActivity
 * Hab Senarai Kawan: tengok status online, direct WebRTC call, dan invite masuk bilik.
 */
public class FriendsActivity extends BaseActivity {

    private EditText inputSearch;
    private ImageButton btnClearSearch;
    private TextView tvFriendsSectionTitle;

    private RecyclerView rvFriends;
    private View emptyFriendsView;
    private FriendsAdapter friendsAdapter;
    private View badgeFriendRequests;

    private int pendingRequestsCount = 0;

    private DatabaseReference friendsRef;
    private ValueEventListener friendsListener;
    private DatabaseReference requestsRef;
    private ValueEventListener requestsListener;

    private String uid = "";
    private String name = "";
    private String publicId = "";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_friends);

        View root = findViewById(R.id.main);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        BottomNavUtils.setup(bottomNav, this, R.id.nav_bottom_friends);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            try {
                int pl = bottomNav.getPaddingLeft();
                int pt = bottomNav.getPaddingTop();
                int pr = bottomNav.getPaddingRight();
                bottomNav.setPadding(pl, pt, pr, systemBars.bottom);
            } catch (Exception ignored) {}
            return insets;
        });

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            uid = user.getUid();
        }
        name = UserPrefs.getName(this);
        if (name == null || name.trim().isEmpty()) name = user != null && user.getDisplayName() != null ? user.getDisplayName() : "User";
        publicId = FirebaseFriendClient.format4DigitId(uid, UserPrefs.getPublicId(this));

        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> navigateToHome());
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navigateToHome();
            }
        });

        View btnAddFriend = findViewById(R.id.btn_add_friend);
        badgeFriendRequests = findViewById(R.id.badge_friend_requests);
        if (btnAddFriend != null) {
            btnAddFriend.setOnClickListener(v -> showFriendMenu());
        }

        inputSearch = findViewById(R.id.input_search_friends);
        btnClearSearch = findViewById(R.id.btn_clear_search);
        tvFriendsSectionTitle = findViewById(R.id.tv_friends_section_title);

        if (inputSearch != null) {
            inputSearch.addTextChangedListener(new TextWatcher() {
    @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String query = s != null ? s.toString() : "";
                    if (friendsAdapter != null) {
                        friendsAdapter.filter(query);
                        updateEmptyStateForFriends();
                    }
                    if (btnClearSearch != null) {
                        btnClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                }

    @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        if (btnClearSearch != null) {
            btnClearSearch.setOnClickListener(v -> {
                if (inputSearch != null) inputSearch.setText("");
            });
        }

        setupRecyclerView();
        listenFirebaseData();
    }

    /** Paparkan FriendMenu. */
    private void showFriendMenu() {
        String reqTitle = pendingRequestsCount > 0 ? getString(R.string.friend_requests_with_count, pendingRequestsCount) : getString(R.string.friend_requests_no_count);
        String[] options = new String[]{getString(R.string.friend_option_scan), getString(R.string.friend_option_id), getString(R.string.friend_option_my_qr), reqTitle};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.friend_manage_title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        FriendNavigationHelper.navigate(this, ScanQrActivity.class);
                    } else if (which == 1) {
                        showAddFriendByCodeDialog();
                    } else if (which == 2) {
                        FriendNavigationHelper.navigate(this, MyQrActivity.class);
                    } else if (which == 3) {
                        FriendNavigationHelper.navigate(this, FriendRequestsActivity.class);
                    }
                })
                .show();
    }

    /** Paparkan AddFriendByCodeDialog. */
    private void showAddFriendByCodeDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_friend_by_code, null);
        EditText inputCode = dialogView.findViewById(R.id.input_friend_code);
        TextView tvError = dialogView.findViewById(R.id.tv_dialog_error);
        View progress = dialogView.findViewById(R.id.progress_dialog_search);
        View cardFoundUser = dialogView.findViewById(R.id.card_found_user);
        TextView tvFoundName = dialogView.findViewById(R.id.tv_found_user_name);
        TextView tvFoundTag = dialogView.findViewById(R.id.tv_found_user_tag);
        MaterialButton btnInvite = dialogView.findViewById(R.id.btn_invite_user);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_dialog_cancel);
        MaterialButton btnSearch = dialogView.findViewById(R.id.btn_dialog_search);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        Runnable performSearch = () -> {
            String code = inputCode.getText() != null ? inputCode.getText().toString().trim() : "";
            if (code.isEmpty()) {
                tvError.setText(R.string.friend_enter_user_id);
                tvError.setVisibility(View.VISIBLE);
                cardFoundUser.setVisibility(View.GONE);
                return;
            }

            tvError.setVisibility(View.GONE);
            cardFoundUser.setVisibility(View.GONE);
            progress.setVisibility(View.VISIBLE);
            btnSearch.setEnabled(false);

            executor.execute(() -> {
                try {
                    FirebaseFriendClient.FriendInfo friend = FirebaseFriendClient.findUserByCode(code);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        progress.setVisibility(View.GONE);
                        btnSearch.setEnabled(true);

                        if (friend == null) {
                            tvError.setText(getString(R.string.friend_user_not_found, code));
                            tvError.setVisibility(View.VISIBLE);
                            cardFoundUser.setVisibility(View.GONE);
                            return;
                        }

                        if (uid.equalsIgnoreCase(friend.uid)) {
                            tvError.setText(R.string.friend_cannot_add_self);
                            tvError.setVisibility(View.VISIBLE);
                            cardFoundUser.setVisibility(View.GONE);
                            return;
                        }

                        tvError.setVisibility(View.GONE);
                        tvFoundName.setText(friend.name);
                        tvFoundTag.setText(friend.name + "#" + friend.publicId);
                        com.google.android.material.imageview.ShapeableImageView ivFoundAvatar = dialogView.findViewById(R.id.iv_found_user_avatar);
                        if (ivFoundAvatar != null) {
                            com.example.resqtap.utils.AvatarUtils.applyAvatar(ivFoundAvatar, friend.photoB64, friend.photoUrl, R.drawable.ic_avatar);
                        }
                        btnInvite.setEnabled(true);
                        btnInvite.setText(R.string.friend_invite_btn);
                        btnInvite.setIconResource(R.drawable.ic_person_add);

                        btnInvite.setOnClickListener(v -> {
                            btnInvite.setEnabled(false);
                            btnInvite.setText(R.string.friend_sending_request);
                            executor.execute(() -> {
                                try {
                                    FirebaseFriendClient.sendFriendRequest(this, uid, name, publicId, friend.uid);
                                    runOnUiThread(() -> {
                                        if (isFinishing() || isDestroyed()) return;
                                        btnInvite.setText(R.string.friend_request_sent_btn);
                                        btnInvite.setIcon(null);
                                        Toast.makeText(this, R.string.friend_request_sent, Toast.LENGTH_SHORT).show();
                                    });
                                } catch (Exception e) {
                                    String msg = e.getMessage() == null ? "" : e.getMessage().trim();
                                    runOnUiThread(() -> {
                                        if (isFinishing() || isDestroyed()) return;
                                        btnInvite.setEnabled(true);
                                        btnInvite.setText(R.string.friend_invite_btn);
                                        if ("already_friends".equalsIgnoreCase(msg)) {
                                            tvError.setText(getString(R.string.friend_already_added));
                                        } else if ("request_already_pending".equalsIgnoreCase(msg)) {
                                            tvError.setText(getString(R.string.friend_request_already_sent));
                                        } else {
                                            tvError.setText(getString(R.string.friend_failed_prefix, msg));
                                        }
                                        tvError.setVisibility(View.VISIBLE);
                                    });
                                }
                            });
                        });

                        cardFoundUser.setVisibility(View.VISIBLE);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        progress.setVisibility(View.GONE);
                        btnSearch.setEnabled(true);
                        tvError.setText(getString(R.string.friend_error_prefix, e.getMessage()));
                        tvError.setVisibility(View.VISIBLE);
                        cardFoundUser.setVisibility(View.GONE);
                    });
                }
            });
        };

        btnSearch.setOnClickListener(v -> performSearch.run());
        inputCode.setOnEditorActionListener((v, actionId, event) -> {
            performSearch.run();
            return true;
        });

        dialog.show();
    }

    // SEKSYEN: ONDESTROY
    /** Pembersihan memori, buang listener, dan tutup sambungan. */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        stopListeners();
    }

    /** Navigasi kembali ke halaman Home (MainActivity). */
    private void navigateToHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        try {
            overridePendingTransition(R.anim.resqtap_enter, R.anim.resqtap_exit);
        } catch (Exception ignored) {}
        finish();
    }

    private String safePublicId(String rawUid) {
        return FirebaseFriendClient.format4DigitId(rawUid, "");
    }

    /** Setup dan konfigurasi RecyclerView. */
    private void setupRecyclerView() {
        rvFriends = findViewById(R.id.rv_friends);
        emptyFriendsView = findViewById(R.id.empty_friends_text);

        rvFriends.setLayoutManager(new LinearLayoutManager(this));
        friendsAdapter = new FriendsAdapter(friend -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.friend_delete_title)
                    .setMessage(getString(R.string.friend_delete_confirm_msg, friend.name))
                    .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(R.string.friend_delete_action, (d, w) -> removeFriend(friend))
                    .show();
        });
        friendsAdapter.setCurrentUid(uid);
        rvFriends.setAdapter(friendsAdapter);
    }

    /** Simpan atau hantar data EmptyStateForFriends. */
    private void updateEmptyStateForFriends() {
        if (friendsAdapter == null) return;
        boolean hasItems = friendsAdapter.getItemCount() > 0;
        if (emptyFriendsView != null) {
            emptyFriendsView.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        }
        if (rvFriends != null) {
            rvFriends.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        }
        if (tvFriendsSectionTitle != null) {
            int total = friendsAdapter.getTotalCount();
            tvFriendsSectionTitle.setText(total > 0 ? getString(R.string.friend_suggested_section_count, total) : getString(R.string.friend_suggested_section));
        }
    }

    /** Padam atau bersihkan Friend. */
    private void removeFriend(FirebaseFriendClient.FriendInfo friend) {
        executor.execute(() -> {
            try {
                FirebaseFriendClient.removeFriend(uid, friend.uid);
                runOnUiThread(() -> Toast.makeText(this, R.string.friend_removed, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.friend_error_prefix, e.getMessage()), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void listenFirebaseData() {
        if (uid.isEmpty()) return;

        DatabaseReference root = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL).getReference();

        friendsRef = root.child("userFriends").child(uid);
        friendsListener = new ValueEventListener() {
            /** Callback apabila data Firebase Realtime Database berubah. */
    @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<FirebaseFriendClient.FriendInfo> friends = new ArrayList<>();
                if (snapshot.exists()) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        FirebaseFriendClient.FriendInfo info = FirebaseFriendClient.FriendInfo.from(child);
                        if (info != null) friends.add(info);
                    }
                }
                friendsAdapter.setFriends(friends);
                updateEmptyStateForFriends();
            }

            /** Callback sekiranya operasi Firebase dibatalkan atau gagal. */
    @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        friendsRef.addValueEventListener(friendsListener);

        requestsRef = root.child("friendRequests").child(uid);
        requestsListener = new ValueEventListener() {
            /** Callback apabila data Firebase Realtime Database berubah. */
    @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int pendingCount = 0;
                if (snapshot.exists()) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        FirebaseFriendClient.FriendRequest req = FirebaseFriendClient.FriendRequest.from(child);
                        if (req != null && "pending".equalsIgnoreCase(req.status)) {
                            pendingCount++;
                        }
                    }
                }
                pendingRequestsCount = pendingCount;
                if (badgeFriendRequests != null) {
                    badgeFriendRequests.setVisibility(pendingCount > 0 ? View.VISIBLE : View.GONE);
                }
            }

            /** Callback sekiranya operasi Firebase dibatalkan atau gagal. */
    @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        requestsRef.addValueEventListener(requestsListener);
    }

    private void stopListeners() {
        try {
            if (friendsRef != null && friendsListener != null) {
                friendsRef.removeEventListener(friendsListener);
            }
            if (requestsRef != null && requestsListener != null) {
                requestsRef.removeEventListener(requestsListener);
            }
        } catch (Exception ignored) {}
    }
}
