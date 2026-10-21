package com.example.resqtap.friend;

import android.os.Bundle;
import android.view.View;
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
import com.example.resqtap.notification.NotificationUtils;
import com.example.resqtap.room.FirebaseRoomClient;
import com.example.resqtap.utils.BottomNavUtils;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
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
 * FriendRequestsActivity
 * Senarai Friend Request masuk & keluar: butang Accept / Reject.
 */
public class FriendRequestsActivity extends BaseActivity {

    private RecyclerView rvRequests;
    private View emptyRequestsView;

    private FriendRequestsAdapter requestsAdapter;
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
        setContentView(R.layout.activity_friend_requests);

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
        if (btnBack != null) btnBack.setOnClickListener(v -> FriendNavigationHelper.navigate(this, FriendsActivity.class));

        setupRecyclerView();
        listenFirebaseData();
    }

    // SEKSYEN: ONDESTROY
    /** Pembersihan memori, buang listener, dan tutup sambungan. */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        stopListeners();
    }

    private String safePublicId(String rawUid) {
        return FirebaseFriendClient.format4DigitId(rawUid, "");
    }

    /** Setup dan konfigurasi RecyclerView. */
    private void setupRecyclerView() {
        rvRequests = findViewById(R.id.rv_requests);
        emptyRequestsView = findViewById(R.id.empty_requests_text);

        rvRequests.setLayoutManager(new LinearLayoutManager(this));
        requestsAdapter = new FriendRequestsAdapter(new FriendRequestsAdapter.OnRequestActionListener() {
    @Override
            public void onAccept(FirebaseFriendClient.FriendRequest request) {
                acceptRequest(request);
            }

    @Override
            public void onReject(FirebaseFriendClient.FriendRequest request) {
                rejectRequest(request);
            }
        });
        rvRequests.setAdapter(requestsAdapter);
    }

    private void acceptRequest(FirebaseFriendClient.FriendRequest request) {
        executor.execute(() -> {
            try {
                FirebaseFriendClient.acceptFriendRequest(this, uid, name, publicId, request);
                runOnUiThread(() -> Toast.makeText(this, R.string.friend_request_accepted, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.friend_error_prefix, e.getMessage()), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void rejectRequest(FirebaseFriendClient.FriendRequest request) {
        executor.execute(() -> {
            try {
                FirebaseFriendClient.rejectFriendRequest(uid, request.fromUid);
                NotificationUtils.playNotificationSound(this);
                runOnUiThread(() -> Toast.makeText(this, R.string.friend_request_rejected, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.friend_error_prefix, e.getMessage()), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void listenFirebaseData() {
        if (uid.isEmpty()) return;

        DatabaseReference root = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL).getReference();
        requestsRef = root.child("friendRequests").child(uid);
        requestsListener = new ValueEventListener() {
            /** Callback apabila data Firebase Realtime Database berubah. */
    @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<FirebaseFriendClient.FriendRequest> requests = new ArrayList<>();
                if (snapshot.exists()) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        FirebaseFriendClient.FriendRequest req = FirebaseFriendClient.FriendRequest.from(child);
                        if (req != null && "pending".equalsIgnoreCase(req.status)) {
                            requests.add(req);
                        }
                    }
                }
                requestsAdapter.setRequests(requests);
                if (emptyRequestsView != null) emptyRequestsView.setVisibility(requests.isEmpty() ? View.VISIBLE : View.GONE);
                if (rvRequests != null) rvRequests.setVisibility(requests.isEmpty() ? View.GONE : View.VISIBLE);
            }

            /** Callback sekiranya operasi Firebase dibatalkan atau gagal. */
    @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        requestsRef.addValueEventListener(requestsListener);
    }

    private void stopListeners() {
        try {
            if (requestsRef != null && requestsListener != null) {
                requestsRef.removeEventListener(requestsListener);
            }
        } catch (Exception ignored) {}
    }
}
