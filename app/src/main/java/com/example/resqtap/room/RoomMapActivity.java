package com.example.resqtap.room;
import com.example.resqtap.R;

import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.auth.LoginActivity;
import com.example.resqtap.map.GpsUtils;
import com.example.resqtap.map.MapTypeBottomSheet;
import com.example.resqtap.notification.NotificationsActivity;
import com.example.resqtap.sos.SosBottomSheetController;
import com.example.resqtap.sos.SosPrefs;
import com.example.resqtap.sos.VibrateManager;
import com.example.resqtap.utils.BatteryUtils;
import com.example.resqtap.utils.PermissionUtils;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.LruCache;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import com.example.resqtap.friend.FirebaseFriendClient;
import com.example.resqtap.sos.SosAudioManager;
import com.google.android.gms.tasks.Tasks;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * RoomMapActivity
 * Live Map Tracking: tengok pergerakan ahli bilik realtime, route jalan raya OSRM, dan SOS bilik.
 */
public class RoomMapActivity extends BaseActivity implements OnMapReadyCallback {
    public static final String EXTRA_ROOM_CODE = "room_code";
    public static final String EXTRA_FOCUS_UID = "focus_uid";
    public static final String EXTRA_SOS_ALERT_ID = "sos_alert_id";

    private GoogleMap map;
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String[]> locationPerms;
    private CancellationTokenSource cancellationTokenSource;

    private final Map<String, Marker> markersByUid = new HashMap<>();
    private final Map<String, String> markerIconKeyByUid = new HashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ExecutorService pollingExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(2);
    private final ExecutorService geocodeExecutor = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> remotePhotoCache = new LruCache<>(40);
    private final Set<String> remotePhotoInflight = ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CopyOnWriteArrayList<BitmapHandler>> remotePhotoWaiters =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<String> photoUrlLookupInflight = ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.ConcurrentHashMap<String, String> resolvedPhotoUrlsByUid = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<String> photoB64LookupInflight = ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.ConcurrentHashMap<String, String> resolvedPhotoB64ByUid = new java.util.concurrent.ConcurrentHashMap<>();
    private final LruCache<String, Bitmap> b64BitmapCache = new LruCache<>(60);
    private final LruCache<String, String> addressCache = new LruCache<>(220);
    private final Set<String> geocodeInflight = ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.ConcurrentHashMap<String, String> lastAddrKeyByUid = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile boolean running = false;
    private LatLng restoredCameraTarget = null;
    private float restoredCameraZoom = -1f;

    private String roomCode;
    private String currentRoomName = "";
    private String uid;
    private String deviceId;
    private String displayName;
    private TextView membersListView;
    private TextView topSubtitle;
    private RecyclerView membersRecycler;
    private RoomMembersAdapter membersAdapter;
    private BottomSheetBehavior<?> membersSheetBehavior;
    private volatile boolean removeMode = false;
    private final Set<String> pendingKickUids = ConcurrentHashMap.newKeySet();
    private DatabaseReference rtdbConnRef;
    private ValueEventListener rtdbConnListener;
    private volatile boolean rtdbConnected = false;
    private volatile int lastMemberCount = 0;
    private int myBatteryPct = -1;
    private String myPhotoUri = "";
    private String myPhotoUrl = "";
    private String myPhotoB64 = "";
    private String creatorUid = "";
    private FirebaseRoomClient.RoomPermissions roomPermissions = new FirebaseRoomClient.RoomPermissions();
    private ValueEventListener roomPermissionsListener;
    private Marker myLocationMarker;
    private Marker selectedInfoMarker;
    private boolean suppressSelectedInfoWindow = false;
    private long lastInfoWindowReopenAtMs = 0L;
    private LocationCallback locationCallback;
    private boolean isRequestingUpdates = false;
    private ChildEventListener roomSosListener;
    private ValueEventListener forceLeaveListener;
    private ValueEventListener roomDeletedListener;
    private SosBottomSheetController sosSheet;
    private MaterialButton fabSos;
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private volatile String activeSosUid = "";
    private volatile long activeSosUntilMs = 0L;
    private Runnable clearActiveSosRunnable;
    private volatile String myActiveSosId = "";
    private volatile boolean cancelMySosWhenIdArrives = false;
    private volatile String pendingFocusUid = "";
    private volatile String pendingFocusAlertId = "";

    private Polyline navigationPolyline;
    private String navigationTargetUid = null;
    private LatLng lastNavigatedSelfLatLng;
    private LatLng lastNavigatedTargetLatLng;

    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_room_map);
        sosSheet = new SosBottomSheetController(this, new SosBottomSheetController.SosCallbacks() {
            @Override
            public void onCountdownFinished() {
                if (roomPermissions != null && !roomPermissions.allowSosAlarm) {
                    runOnUiThread(() -> Toast.makeText(RoomMapActivity.this, R.string.sos_disabled_by_room_permission, Toast.LENGTH_LONG).show());
                    return;
                }
                Intent triggerIntent = new Intent(RoomMapActivity.this, com.example.resqtap.sos.SendingAlarmActivity.class);
                triggerIntent.putExtra(com.example.resqtap.sos.SendingAlarmActivity.EXTRA_TARGET_ROOM, roomCode);
                startActivity(triggerIntent);
            }

            @Override
            public void onSosCancelled() {
                // Dibatalkan semasa 10s countdown: Tiada sebarang isyarat dihantar ke bilik/admin
            }
        });
        try {
            if (savedInstanceState != null && savedInstanceState.containsKey("cam_lat") && savedInstanceState.containsKey("cam_lng")) {
                double la = savedInstanceState.getDouble("cam_lat", 0d);
                double ln = savedInstanceState.getDouble("cam_lng", 0d);
                float z = savedInstanceState.getFloat("cam_zoom", -1f);
                restoredCameraTarget = new LatLng(la, ln);
                restoredCameraZoom = z;
            }
        } catch (Exception ignored) {
        }
        android.view.View main = findViewById(R.id.main);
        android.view.View overlayUi = findViewById(R.id.overlay_ui);
        android.view.View bottomSheetForInsets = findViewById(R.id.bottom_sheet);

        ViewCompat.setOnApplyWindowInsetsListener(main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });
        if (overlayUi != null) {
            ViewCompat.setOnApplyWindowInsetsListener(overlayUi, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
                return insets;
            });
        }
        if (bottomSheetForInsets != null) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomSheetForInsets, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
                return insets;
            });
        }

        roomCode = getIntent().getStringExtra(EXTRA_ROOM_CODE);
        if (roomCode == null) roomCode = "";
        roomCode = roomCode.trim().toUpperCase(java.util.Locale.ROOT);
        try {
            String focusUid = String.valueOf(getIntent().getStringExtra(EXTRA_FOCUS_UID) == null ? "" : getIntent().getStringExtra(EXTRA_FOCUS_UID)).trim();
            String sosAlertId = String.valueOf(getIntent().getStringExtra(EXTRA_SOS_ALERT_ID) == null ? "" : getIntent().getStringExtra(EXTRA_SOS_ALERT_ID)).trim();
            if (!focusUid.isEmpty()) {
                pendingFocusUid = focusUid;
                pendingFocusAlertId = sosAlertId;
            }
        } catch (Exception ignored) {
        }
        restoreBlinkFromPrefs();
        try {

            UserPrefs.setActiveRoomCode(this, roomCode);
        } catch (Exception ignored) {
        }

        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        uid = current.getUid();
        deviceId = UserPrefs.getOrCreateDeviceId(this);
        displayName = UserPrefs.getName(this);
        if (displayName == null || displayName.trim().isEmpty()) displayName = "User";

        TextView roomLabel = findViewById(R.id.room_code_label);
        roomLabel.setText(getString(R.string.room_code_label, roomCode));
        if (!roomCode.isEmpty()) {
            executor.execute(() -> {
                try {
                    String c = FirebaseRoomClient.fetchCreatorUid(roomCode);
                    if (c != null && !c.isEmpty()) {
                        creatorUid = c;
                    }
                } catch (Exception ignored) {}
            });
            roomPermissionsListener = FirebaseRoomClient.listenRoomPermissions(roomCode, permissions -> {
                if (isFinishing() || isDestroyed()) return;
                runOnUiThread(() -> {
                    this.roomPermissions = permissions;
                    updateRoomFooterVisibility();
                    updateSosFabVisibility();
                    if (roomPermissions != null && !roomPermissions.allowSosAlarm) {
                        activeSosUid = "";
                        activeSosUntilMs = 0L;
                        if (membersAdapter != null) membersAdapter.setActiveSos("", 0L);
                        VibrateManager.stopAll(RoomMapActivity.this);
                        SosAudioManager.stopAll();
                    }
                    if (membersAdapter != null) {
                        membersAdapter.notifyDataSetChanged();
                    }
                });
            });

            FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                    .getReference("rooms").child(roomCode).child("name")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        /** Callback apabila data Firebase Realtime Database berubah. */
    @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists() && snapshot.getValue() != null) {
                                String n = String.valueOf(snapshot.getValue()).trim();
                                if (!n.isEmpty()) {
                                     currentRoomName = n;
                                     roomLabel.setText(n);
                                }
                            }
                        }
                        /** Callback sekiranya operasi Firebase dibatalkan atau gagal. */
    @Override
                        public void onCancelled(@NonNull DatabaseError error) {}
                    });
        }

        topSubtitle = findViewById(R.id.search_hint);
        membersListView = findViewById(R.id.members_list);
        membersRecycler = findViewById(R.id.rv_room_members);
        try {
            android.view.View bottomSheet = findViewById(R.id.bottom_sheet);
            if (bottomSheet != null) membersSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        } catch (Exception ignored) {
            membersSheetBehavior = null;
        }

        try {
            final android.widget.TextView sheetTitle = findViewById(R.id.sheet_title);
            if (membersSheetBehavior != null && sheetTitle != null) {
                sheetTitle.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
    @Override
                    public void onGlobalLayout() {
                        try {
                            sheetTitle.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        } catch (Exception ignored) {
                        }
                        try {
                            int titleBottom = sheetTitle.getBottom();

                            int peek = titleBottom + dp(RoomMapActivity.this, 24);

                            int min = dp(RoomMapActivity.this, 130);
                            int max = dp(RoomMapActivity.this, 220);
                            if (peek < min) peek = min;
                            if (peek > max) peek = max;
                            membersSheetBehavior.setPeekHeight(peek, false);
                            if (membersSheetBehavior.getState() != BottomSheetBehavior.STATE_EXPANDED) {
                                membersSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                });
            }
            android.view.View.OnClickListener toggleSheet = v -> {
                if (membersSheetBehavior != null) {
                    int state = membersSheetBehavior.getState();
                    membersSheetBehavior.setState(state == BottomSheetBehavior.STATE_EXPANDED
                            ? BottomSheetBehavior.STATE_COLLAPSED
                            : BottomSheetBehavior.STATE_EXPANDED);
                }
            };
            if (sheetTitle != null) sheetTitle.setOnClickListener(toggleSheet);
            android.view.View handle = findViewById(R.id.sheet_handle);
            if (handle != null) handle.setOnClickListener(toggleSheet);
        } catch (Exception ignored) {
        }
        if (membersRecycler != null) {
            membersRecycler.setLayoutManager(new LinearLayoutManager(this));
            membersRecycler.setItemAnimator(null);
            membersAdapter = new RoomMembersAdapter(uid);
            membersRecycler.setAdapter(membersAdapter);
            updateRoomFooterVisibility();
            try {

                membersAdapter.setActiveSos(activeSosUid, activeSosUntilMs);
            } catch (Exception ignored) {
            }
        }
        updateMyBatteryPct();
        myPhotoUri = String.valueOf(UserPrefs.getPhotoUri(this) == null ? "" : UserPrefs.getPhotoUri(this)).trim();
        myPhotoUrl = String.valueOf(UserPrefs.getPhotoUrl(this) == null ? "" : UserPrefs.getPhotoUrl(this)).trim();
        myPhotoB64 = String.valueOf(UserPrefs.getPhotoB64(this) == null ? "" : UserPrefs.getPhotoB64(this)).trim();
        if (membersAdapter != null) {
            membersAdapter.setMyBatteryPct(myBatteryPct);
            membersAdapter.setMyPhotoUri(myPhotoUri);
            membersAdapter.setMyPhotoUrl(myPhotoUrl);
            membersAdapter.setMyPhotoB64(myPhotoB64);
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationPerms = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> requestLocationIfNeeded()
        );

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        MaterialButton btnZoomIn = findViewById(R.id.btn_map_zoom_in);
        MaterialButton btnZoomOut = findViewById(R.id.btn_map_zoom_out);
        MaterialButton btnMyLocation = findViewById(R.id.btn_map_my_location);
        MaterialButton btnMapType = findViewById(R.id.btn_map_type);
        MaterialButton btnRoomInbox = findViewById(R.id.btn_room_inbox);
        MaterialButton btnRoomChat = findViewById(R.id.btn_room_chat);
        btnZoomIn.setOnClickListener(v -> {
            if (map == null) return;
            map.animateCamera(CameraUpdateFactory.zoomIn());
        });
        btnZoomOut.setOnClickListener(v -> {
            if (map == null) return;
            map.animateCamera(CameraUpdateFactory.zoomOut());
        });
        btnMyLocation.setOnClickListener(v -> requestLocationIfNeeded());
        btnMapType.setOnClickListener(v -> toggleMapType());
        if (btnRoomInbox != null) {
            btnRoomInbox.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(this, NotificationsActivity.class));
                } catch (Exception ignored) {
                }
            });
        }
        if (btnRoomChat != null) {
            btnRoomChat.setOnClickListener(v -> {
                try {
                    RoomChatActivity.start(this, roomCode, currentRoomName);
                } catch (Exception ignored) {
                }
            });
        }
        updateInboxUnreadBadge();

        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                finish();
            });
        }

        fabSos = findViewById(R.id.fab_sos);
        if (fabSos != null) {
            fabSos.setOnClickListener(v -> {
                if (roomPermissions != null && !roomPermissions.allowSosAlarm) {
                    Toast.makeText(RoomMapActivity.this, R.string.sos_disabled_by_room_permission, Toast.LENGTH_LONG).show();
                    return;
                }
                if (sosSheet != null) sosSheet.show();
            });
        }
        updateSosFabVisibility();

        android.view.View btnNavClose = findViewById(R.id.btn_nav_close);
        if (btnNavClose != null) {
            btnNavClose.setOnClickListener(v -> cancelNavigation());
        }

        android.view.View btnNavAltRoute = findViewById(R.id.btn_nav_alt_route);
        if (btnNavAltRoute != null) {
            btnNavAltRoute.setOnClickListener(v -> zoomToFullRoute());
        }
    }

    private static class RouteResult {
        java.util.List<LatLng> points = new java.util.ArrayList<>();
        double distanceMeters = 0;
        double durationSeconds = 0;
    }

    private void startNavigationTo(FirebaseRoomClient.Member m) {
        if (m == null) return;
        if (m.lat == 0d && m.lng == 0d) {
            Toast.makeText(this, R.string.toast_location_not_available_yet, Toast.LENGTH_SHORT).show();
            return;
        }

        LatLng myPos = null;
        if (myLocationMarker != null) {
            myPos = myLocationMarker.getPosition();
        }
        if (myPos == null && uid != null) {
            Marker mMarker = markersByUid.get(uid);
            if (mMarker != null) {
                myPos = mMarker.getPosition();
            }
        }
        if (myPos == null) {
            Toast.makeText(this, R.string.toast_my_location_not_available, Toast.LENGTH_SHORT).show();
            return;
        }

        navigationTargetUid = m.uid;
        lastNavigatedSelfLatLng = myPos;
        lastNavigatedTargetLatLng = new LatLng(m.lat, m.lng);

        android.view.View loading = findViewById(R.id.loading_route_overlay);
        if (loading != null) loading.setVisibility(android.view.View.VISIBLE);

        fetchRouteAndDraw(myPos, lastNavigatedTargetLatLng);

        try {
            if (membersSheetBehavior != null) {
                membersSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            }
        } catch (Exception ignored) {}
    }

    private void cancelNavigation() {
        if (navigationPolyline != null) {
            navigationPolyline.remove();
            navigationPolyline = null;
        }
        navigationTargetUid = null;
        lastNavigatedSelfLatLng = null;
        lastNavigatedTargetLatLng = null;
        android.view.View navSheet = findViewById(R.id.navigation_bottom_sheet);
        if (navSheet != null) navSheet.setVisibility(android.view.View.GONE);

        if (membersSheetBehavior != null) {
            membersSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
        if (map != null) {
            try {
                com.google.android.gms.maps.model.CameraPosition current = map.getCameraPosition();
                if (current != null) {
                    com.google.android.gms.maps.model.CameraPosition flat = new com.google.android.gms.maps.model.CameraPosition.Builder(current)
                            .tilt(0f)
                            .bearing(0f)
                            .build();
                    map.animateCamera(CameraUpdateFactory.newCameraPosition(flat));
                }
            } catch (Exception ignored) {}
        }
    }

    private void zoomToFullRoute() {
        if (map == null || navigationPolyline == null) return;

        java.util.List<LatLng> points = navigationPolyline.getPoints();
        if (points == null || points.isEmpty()) {
            if (lastNavigatedSelfLatLng != null && lastNavigatedTargetLatLng != null) {
                com.google.android.gms.maps.model.LatLngBounds.Builder builder = new com.google.android.gms.maps.model.LatLngBounds.Builder();
                builder.include(lastNavigatedSelfLatLng);
                builder.include(lastNavigatedTargetLatLng);
                try {
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 200));
                } catch (Exception ignored) {}
            }
            return;
        }

        com.google.android.gms.maps.model.LatLngBounds.Builder builder = new com.google.android.gms.maps.model.LatLngBounds.Builder();
        for (LatLng pt : points) {
            builder.include(pt);
        }

        try {
            int padding = 200;
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), padding));
        } catch (Exception ignored) {}
    }

    /** Simpan atau hantar data NavigationPolylinePoints. */
    private void updateNavigationPolylinePoints(LatLng myPos, LatLng targetPos) {
        if (navigationTargetUid == null) return;
        try {
            LatLng myLatLng = myPos;
            if (myLatLng == null && myLocationMarker != null) {
                myLatLng = myLocationMarker.getPosition();
            }
            if (myLatLng == null && uid != null) {
                Marker selfMarker = markersByUid.get(uid);
                if (selfMarker != null) {
                    myLatLng = selfMarker.getPosition();
                }
            }
            LatLng targetLatLng = targetPos;
            if (targetLatLng == null) {
                Marker mMarker = markersByUid.get(navigationTargetUid);
                if (mMarker != null) {
                    targetLatLng = mMarker.getPosition();
                }
            }
            if (myLatLng != null && targetLatLng != null) {
                boolean changed = false;
                if (lastNavigatedSelfLatLng == null || lastNavigatedTargetLatLng == null) {
                    changed = true;
                } else {
                    double dSelf = Math.max(Math.abs(myLatLng.latitude - lastNavigatedSelfLatLng.latitude),
                                            Math.abs(myLatLng.longitude - lastNavigatedSelfLatLng.longitude));
                    double dTarget = Math.max(Math.abs(targetLatLng.latitude - lastNavigatedTargetLatLng.latitude),
                                              Math.abs(targetLatLng.longitude - lastNavigatedTargetLatLng.longitude));
                    if (dSelf > 0.0001 || dTarget > 0.0001) {
                        changed = true;
                    }
                }

                if (changed) {
                    lastNavigatedSelfLatLng = myLatLng;
                    lastNavigatedTargetLatLng = targetLatLng;
                    fetchRouteAndDraw(myLatLng, targetLatLng);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void fetchRouteAndDraw(LatLng myPos, LatLng targetPos) {
        new Thread(() -> {
            RouteResult finalResult = null;
            if (targetPos != null) {
                finalResult = tryFetchRoute(myPos, targetPos);
            }

            if (finalResult == null || finalResult.points.isEmpty()) {
                double low = 0.0;
                double high = 1.0;
                RouteResult bestResult = null;

                for (int i = 0; i < 6; i++) {
                    double mid = (low + high) / 2.0;
                    double midLat = myPos.latitude + mid * (targetPos.latitude - myPos.latitude);
                    double midLng = myPos.longitude + mid * (targetPos.longitude - myPos.longitude);
                    LatLng midPos = new LatLng(midLat, midLng);

                    RouteResult res = tryFetchRoute(myPos, midPos);
                    if (res != null && !res.points.isEmpty()) {
                        bestResult = res;
                        low = mid;
                    } else {
                        high = mid;
                    }
                }

                finalResult = new RouteResult();
                if (bestResult != null && !bestResult.points.isEmpty()) {
                    finalResult = bestResult;
                    finalResult.points.add(targetPos);
                } else {
                    finalResult.points.add(myPos);
                    finalResult.points.add(targetPos);
                }
            }

            final RouteResult uiResult = finalResult;
            runOnUiThread(() -> {
                android.view.View loading = findViewById(R.id.loading_route_overlay);
                if (loading != null) loading.setVisibility(android.view.View.GONE);

                if (uiResult != null && uiResult.points.size() >= 2) {
                    android.view.View navSheet = findViewById(R.id.navigation_bottom_sheet);
                    if (navSheet != null) navSheet.setVisibility(android.view.View.VISIBLE);

                    TextView etaText = findViewById(R.id.nav_eta_text);
                    TextView distTimeText = findViewById(R.id.nav_distance_time_text);
                    if (etaText != null && distTimeText != null) {
                        int min = (int) Math.round(uiResult.durationSeconds / 60.0);
                        if (min < 1) min = 1;
                        etaText.setText(min + " min");

                        double km = uiResult.distanceMeters / 1000.0;
                        String distStr = String.format(java.util.Locale.US, "%.1f km", km);

                        java.util.Calendar cal = java.util.Calendar.getInstance();
                        cal.add(java.util.Calendar.SECOND, (int) uiResult.durationSeconds);
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("h:mm a z", java.util.Locale.US);
                        String timeStr = sdf.format(cal.getTime());

                        distTimeText.setText(distStr + " \u2022 " + timeStr);
                    }
                }

                if (!running) return;
                drawNavigationRoute(myPos, uiResult.points, targetPos);
            });
        }).start();
    }

    private RouteResult tryFetchRoute(LatLng myPos, LatLng targetPos) {
        String urlStr = "https://router.project-osrm.org/route/v1/driving/"
                + myPos.longitude + "," + myPos.latitude + ";"
                + targetPos.longitude + "," + targetPos.latitude
                + "?overview=full&geometries=geojson";
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "ResQTap/1.0 (Android)");
            if (conn.getResponseCode() == 200) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    return parseOsrmRoute(sb.toString());
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private RouteResult parseOsrmRoute(String jsonStr) {
        RouteResult result = new RouteResult();
        try {
            org.json.JSONObject root = new org.json.JSONObject(jsonStr);
            if ("Ok".equalsIgnoreCase(root.optString("code"))) {
                org.json.JSONArray routes = root.optJSONArray("routes");
                if (routes != null && routes.length() > 0) {
                    org.json.JSONObject route = routes.getJSONObject(0);
                    result.distanceMeters = route.optDouble("distance", 0);
                    result.durationSeconds = route.optDouble("duration", 0);

                    org.json.JSONObject geometry = route.optJSONObject("geometry");
                    if (geometry != null) {
                        org.json.JSONArray coords = geometry.optJSONArray("coordinates");
                        if (coords != null) {
                            for (int i = 0; i < coords.length(); i++) {
                                org.json.JSONArray pt = coords.getJSONArray(i);
                                double lng = pt.getDouble(0);
                                double lat = pt.getDouble(1);
                                result.points.add(new LatLng(lat, lng));
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private void drawNavigationRoute(LatLng myPos, java.util.List<LatLng> points, LatLng targetPos) {
        if (map == null || points == null || points.isEmpty()) return;

        try {
            if (navigationPolyline != null) {
                navigationPolyline.remove();
            }

            PolylineOptions options = new PolylineOptions()
                    .addAll(points)
                    .width(14f)
                    .color(ContextCompat.getColor(this, R.color.brand_primary))
                    .geodesic(true);
            navigationPolyline = map.addPolyline(options);

            float bearing = 0f;
            LatLng startPt = myPos;
            LatLng nextPt = points.size() > 0 ? points.get(0) : targetPos;

            if (startPt.latitude == nextPt.latitude && startPt.longitude == nextPt.longitude) {
                nextPt = points.size() > 1 ? points.get(1) : targetPos;
            }

            if (startPt != null && nextPt != null) {
                bearing = calculateBearing(startPt, nextPt);
            }

            com.google.android.gms.maps.model.CameraPosition cameraPosition = new com.google.android.gms.maps.model.CameraPosition.Builder()
                    .target(myPos)
                    .zoom(18.5f)
                    .bearing(bearing)
                    .tilt(60f)
                    .build();

            map.stopAnimation();
            map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 1500, null);
        } catch (Exception ignored) {
        }
    }

    private float calculateBearing(LatLng start, LatLng end) {
        double lat1 = Math.toRadians(start.latitude);
        double lng1 = Math.toRadians(start.longitude);
        double lat2 = Math.toRadians(end.latitude);
        double lng2 = Math.toRadians(end.longitude);

        double dLng = lng2 - lng1;

        double y = Math.sin(dLng) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (float) ((bearing + 360) % 360);
    }

    /** Simpan atau hantar data InboxUnreadBadge. */
    private void updateInboxUnreadBadge() {
        android.view.View dot = findViewById(R.id.inbox_unread_dot);
        if (dot != null) {
            boolean hasUnread = UserPrefs.getUnreadNotifications(this) > 0;
            dot.setVisibility(hasUnread ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    /** Handle intent baharu yang dihantar ke aktiviti. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) return;
        try {
            setIntent(intent);
        } catch (Exception ignored) {
        }
        try {
            String focusUid = String.valueOf(intent.getStringExtra(EXTRA_FOCUS_UID) == null ? "" : intent.getStringExtra(EXTRA_FOCUS_UID)).trim();
            String sosAlertId = String.valueOf(intent.getStringExtra(EXTRA_SOS_ALERT_ID) == null ? "" : intent.getStringExtra(EXTRA_SOS_ALERT_ID)).trim();
            if (!focusUid.isEmpty()) {
                pendingFocusUid = focusUid;
                pendingFocusAlertId = sosAlertId;
            }
        } catch (Exception ignored) {
        }
        restoreBlinkFromPrefs();
    }

    /** Aktiviti aktif dan sedia untuk interaksi pengguna. */
    @Override
    protected void onResume() {
        super.onResume();
        updateInboxUnreadBadge();
        try {
            UserPrefs.setRoomMapVisible(this, true);
        } catch (Exception ignored) {
        }
        startRtdbConnectionMonitor();
        startForceLeaveListener();
        startRoomDeletedListener();
        startRoomSosListener();
        startLiveRoomTrackingService();
        updateMyBatteryPct();
        myPhotoUri = String.valueOf(UserPrefs.getPhotoUri(this) == null ? "" : UserPrefs.getPhotoUri(this)).trim();
        myPhotoUrl = String.valueOf(UserPrefs.getPhotoUrl(this) == null ? "" : UserPrefs.getPhotoUrl(this)).trim();
        myPhotoB64 = String.valueOf(UserPrefs.getPhotoB64(this) == null ? "" : UserPrefs.getPhotoB64(this)).trim();
        if (membersAdapter != null) {
            membersAdapter.setMyBatteryPct(myBatteryPct);
            membersAdapter.setMyPhotoUri(myPhotoUri);
            membersAdapter.setMyPhotoUrl(myPhotoUrl);
            membersAdapter.setMyPhotoB64(myPhotoB64);
            membersAdapter.notifyDataSetChanged();
        }
        updateMyLocationMarkerIcon();

        updateMyBatteryPct();
        int pct = myBatteryPct;
        executor.execute(() -> FirebaseRoomClient.upsertMemberPresenceQueued(roomCode, uid, displayName, myPhotoUrl, myPhotoB64, pct));
        running = true;
        startPolling();
        requestLocationIfNeeded();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        try {
            if (map != null) {
                com.google.android.gms.maps.model.CameraPosition cp = map.getCameraPosition();
                if (cp != null && cp.target != null) {
                    outState.putDouble("cam_lat", cp.target.latitude);
                    outState.putDouble("cam_lng", cp.target.longitude);
                    outState.putFloat("cam_zoom", cp.zoom);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** Aktiviti dijeda sementara. */
    @Override
    protected void onPause() {
        super.onPause();
        cancelNavigation();
        try {
            UserPrefs.setRoomMapVisible(this, false);
        } catch (Exception ignored) {
        }
        running = false;
        stopLocationUpdates();
        stopRtdbConnectionMonitor();
        stopForceLeaveListener();
        stopRoomDeletedListener();
        try {
            if (sosSheet != null) sosSheet.cancel();
        } catch (Exception ignored) {
        }

        if (cancellationTokenSource != null) {
            cancellationTokenSource.cancel();
            cancellationTokenSource = null;
        }
    }

    // SEKSYEN: ONDESTROY
    /** Pembersihan memori, buang listener, dan tutup sambungan. */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roomPermissionsListener != null) {
            FirebaseRoomClient.removeRoomPermissionsListener(roomCode, roomPermissionsListener);
            roomPermissionsListener = null;
        }
        stopRoomSosListener();
        stopForceLeaveListener();
        stopRoomDeletedListener();
        executor.shutdownNow();
        pollingExecutor.shutdownNow();
        imageExecutor.shutdownNow();
        geocodeExecutor.shutdownNow();
    }

    private void startForceLeaveListener() {
        if (forceLeaveListener != null) return;
        forceLeaveListener = FirebaseRoomClient.listenForceLeave(roomCode, uid, (byUid, atMillis) -> {
            runOnUiThread(() -> Toast.makeText(RoomMapActivity.this, R.string.toast_removed_from_room, Toast.LENGTH_LONG).show());
            executor.execute(() -> {
                try {
                    FirebaseRoomClient.deleteUserRoom(uid, roomCode);
                    com.example.resqtap.notification.NotificationUtils.notifyRoomDeleted(RoomMapActivity.this, roomCode, false);
                } catch (Exception ignored) {
                }
                FirebaseRoomClient.clearForceLeaveRequestQueued(roomCode, uid);
                try {
                    UserPrefs.setActiveRoomCode(RoomMapActivity.this, "");
                } catch (Exception ignored) {
                }
                runOnUiThread(() -> {
                    try { stopLiveRoomTrackingService(); } catch (Exception ignored) {}
                    try { finish(); } catch (Exception ignored) {}
                });
            });
        });
    }

    private void stopForceLeaveListener() {
        if (forceLeaveListener == null) return;
        try {
            FirebaseRoomClient.removeForceLeaveListener(roomCode, uid, forceLeaveListener);
        } catch (Exception ignored) {
        }
        forceLeaveListener = null;
    }

    private void startRoomDeletedListener() {
        if (roomDeletedListener != null) return;
        roomDeletedListener = FirebaseRoomClient.listenRoomDeleted(roomCode, () -> {
            runOnUiThread(() -> Toast.makeText(RoomMapActivity.this, R.string.toast_room_deleted, Toast.LENGTH_LONG).show());
            executor.execute(() -> {
                try {
                    FirebaseRoomClient.deleteUserRoom(uid, roomCode);
                    com.example.resqtap.notification.NotificationUtils.notifyRoomDeleted(RoomMapActivity.this, roomCode, false);
                } catch (Exception ignored) {
                }
                try {
                    UserPrefs.setActiveRoomCode(RoomMapActivity.this, "");
                } catch (Exception ignored) {
                }
                runOnUiThread(() -> {
                    try { stopLiveRoomTrackingService(); } catch (Exception ignored) {}
                    try { finish(); } catch (Exception ignored) {}
                });
            });
        });
    }

    private void stopRoomDeletedListener() {
        if (roomDeletedListener == null) return;
        try {
            FirebaseRoomClient.removeRoomDeletedListener(roomCode, roomDeletedListener);
        } catch (Exception ignored) {
        }
        roomDeletedListener = null;
    }

    private void startRoomSosListener() {
        if (roomSosListener != null) return;
        try {
            long localLastSeen = UserPrefs.getSosLastSeenAt(this, roomCode);
            long storedBaseline = UserPrefs.getSosListenerBaselineAt(this, roomCode);
            FirebaseRoomClient.fetchServerNowQueued(serverNow -> {
                // Use localLastSeen as query start, catches SOSes fired while service was down.
                // Only fall back to serverNow if no prior lastSeen exists (first ever attach).
                long baseline = localLastSeen > 0L ? localLastSeen : Math.max(serverNow, storedBaseline);
                UserPrefs.setSosListenerBaselineAt(RoomMapActivity.this, roomCode, baseline);
                try {
                    android.util.Log.d("SOS_DEBUG", "Listener attached to roomId: " + roomCode);
                } catch (Exception ignored) {
                }

                SosPrefs.bumpBaselineAtListenerStart(RoomMapActivity.this, roomCode, baseline);
                roomSosListener = FirebaseRoomClient.listenRoomSosSince(roomCode, deviceId, baseline, new FirebaseRoomClient.RoomSosHandler() {
                @Override
                public void onSos(String senderUid, String senderName, String roomId, long createdAtMs, String alertId, String status) {
                    if (roomPermissions != null && !roomPermissions.allowSosAlarm) {
                        return;
                    }
                    String from = String.valueOf(senderUid == null ? "" : senderUid).trim();
                    String name = String.valueOf(senderName == null ? "" : senderName).trim();
                    String sosId = String.valueOf(alertId == null ? "" : alertId).trim();
                    long createdAt = Math.max(0L, createdAtMs);

                    try {
                        android.util.Log.d("SOS_DEBUG", "Alert received: " + sosId);
                        android.util.Log.d("SOS_DEBUG", "Sender: " + from + ", current: " + (uid == null ? "" : uid));
                    } catch (Exception ignored) {
                    }

                    runOnUiThread(() -> setActiveSosBlink(from, 20_000L));
                }

    @Override
                public void onSosCancelled(String senderUid, String roomId, String alertId, long cancelledAtMs) {
                    String from = String.valueOf(senderUid == null ? "" : senderUid).trim();
                    String sosId = String.valueOf(alertId == null ? "" : alertId).trim();
                    runOnUiThread(() -> {

                        VibrateManager.stopAll(RoomMapActivity.this);
                        SosAudioManager.stopAll();

                        if (from != null && from.equals(activeSosUid)) {
                            activeSosUid = "";
                            activeSosUntilMs = 0L;
                            if (membersAdapter != null) membersAdapter.setActiveSos("", 0L);
                        }
                    });
                }

    @Override
                public void onSosServed(String senderUid, String roomId, String alertId, String servedByName) {
                    // Do not stop alarm when admin serves - alarm remains until user snoozes
                }
            });
            });
        } catch (Exception ignored) {
        }
    }

    private void stopRoomSosListener() {
        if (roomSosListener == null) return;
        FirebaseRoomClient.removeRoomSosListener(roomCode, roomSosListener);
        roomSosListener = null;
    }

    private void setActiveSosBlink(String uid, long durationMs) {
        if (roomPermissions != null && !roomPermissions.allowSosAlarm) return;
        String u = String.valueOf(uid == null ? "" : uid).trim();
        if (u.isEmpty()) return;
        long until = System.currentTimeMillis() + Math.max(0L, durationMs);
        activeSosUid = u;
        activeSosUntilMs = until;
        if (membersAdapter != null) {
            membersAdapter.setActiveSos(activeSosUid, activeSosUntilMs);
        }
        try {
            if (clearActiveSosRunnable != null) mainHandler.removeCallbacks(clearActiveSosRunnable);
        } catch (Exception ignored) {
        }
        clearActiveSosRunnable = () -> {

            if (!u.equals(activeSosUid)) return;
            if (System.currentTimeMillis() < activeSosUntilMs) return;
            activeSosUid = "";
            activeSosUntilMs = 0L;
            if (membersAdapter != null) membersAdapter.setActiveSos("", 0L);
        };
        mainHandler.postDelayed(clearActiveSosRunnable, Math.max(0L, durationMs) + 100L);
    }

    private void triggerVibrate() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                Vibrator v = vm == null ? null : vm.getDefaultVibrator();
                if (v == null) return;
                v.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v == null) return;
                if (Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(250);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void startRtdbConnectionMonitor() {
        if (rtdbConnListener != null) return;
        try {
            rtdbConnRef = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL).getReference(".info/connected");
            rtdbConnListener = new ValueEventListener() {
                /** Callback apabila data Firebase Realtime Database berubah. */
    @Override
                public void onDataChange(DataSnapshot snapshot) {
                    boolean connected = snapshot != null && Boolean.TRUE.equals(snapshot.getValue(Boolean.class));
                    rtdbConnected = connected;
                    try {
                        android.util.Log.i("ResQTap", "RTDB .info/connected=" + connected);
                        logNetworkState();
                    } catch (Exception ignored) {
                    }
                    runOnUiThread(() -> updateTopSubtitle(lastMemberCount));
                }

                /** Callback sekiranya operasi Firebase dibatalkan atau gagal. */
    @Override
                public void onCancelled(DatabaseError error) {
                    rtdbConnected = false;
                    try {
                        android.util.Log.w("ResQTap", "RTDB .info/connected cancelled: " + (error == null ? "" : error.getMessage()));
                        logNetworkState();
                    } catch (Exception ignored) {
                    }
                    runOnUiThread(() -> updateTopSubtitle(lastMemberCount));
                }
            };
            rtdbConnRef.addValueEventListener(rtdbConnListener);
        } catch (Exception ignored) {
        }
    }

    private void stopRtdbConnectionMonitor() {
        if (rtdbConnRef == null || rtdbConnListener == null) return;
        try {
            rtdbConnRef.removeEventListener(rtdbConnListener);
        } catch (Exception ignored) {
        }
        rtdbConnListener = null;
        rtdbConnRef = null;
    }

    private void logNetworkState() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;
            android.net.Network nw = cm.getActiveNetwork();
            android.net.NetworkCapabilities caps = nw == null ? null : cm.getNetworkCapabilities(nw);
            boolean internet = caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
            boolean validated = caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            boolean wifi = caps != null && caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI);
            boolean cell = caps != null && caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR);
            android.util.Log.i("ResQTap", "NET internet=" + internet + " validated=" + validated + " wifi=" + wifi + " cell=" + cell);
        } catch (Exception ignored) {
        }
    }

    /** Simpan atau hantar data TopSubtitle. */
    private void updateTopSubtitle(int memberCount) {
        lastMemberCount = memberCount;
        if (topSubtitle == null) return;
        String left = memberCount <= 0 ? getString(R.string.room_members_empty) : ("Ahli: " + memberCount);
        String right = rtdbConnected ? "Status : Online" : "Status : Offline";
        String dot = " \u25CF";
        int dotColor = rtdbConnected ? 0xFF27AE60 : 0xFFE74C3C;
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        sb.append(left).append(" \u2022 ").append(right).append(dot);
        int dotStart = sb.length() - dot.length();
        sb.setSpan(new android.text.style.ForegroundColorSpan(dotColor), dotStart, sb.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        topSubtitle.setText(sb);
    }

    /** Simpan atau hantar data BellTo. */
    private void sendBellTo(String targetUid) {
        if (roomPermissions != null && !roomPermissions.allowTriggerBell) {
            Toast.makeText(this, R.string.bell_disabled_by_room_permission, Toast.LENGTH_SHORT).show();
            return;
        }
        String to = String.valueOf(targetUid == null ? "" : targetUid).trim();
        if (to.isEmpty()) return;
        String fromDev = String.valueOf(deviceId == null ? "" : deviceId).trim();

        FirebaseRoomClient.sendBellQueued(roomCode, uid, fromDev, displayName, to);

        runOnUiThread(() -> {
            try {
                boolean isSelf = (uid != null && uid.trim().equals(to));
                Toast.makeText(this, isSelf ? "Bell sent to your devices." : "Bell sent.", Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {
            }
        });
    }

    private void startLiveRoomTrackingService() {

        Intent i = new Intent(this, LiveRoomTrackingService.class);
        i.setAction(LiveRoomTrackingService.ACTION_START);
        i.putExtra(LiveRoomTrackingService.EXTRA_ROOM_CODE, roomCode);
        i.putExtra(LiveRoomTrackingService.EXTRA_UID, uid);
        i.putExtra(LiveRoomTrackingService.EXTRA_NAME, displayName);
        i.putExtra(LiveRoomTrackingService.EXTRA_PHOTO_URL, myPhotoUrl);
        i.putExtra(LiveRoomTrackingService.EXTRA_PHOTO_B64, myPhotoB64);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Exception ignored) {
        }
    }

    private void stopLiveRoomTrackingService() {
        Intent i = new Intent(this, LiveRoomTrackingService.class);
        i.setAction(LiveRoomTrackingService.ACTION_STOP);
        try {
            startService(i);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        applySavedMapType();
        map.setTrafficEnabled(UserPrefs.isMapTrafficEnabled(this));
        map.getUiSettings().setMyLocationButtonEnabled(false);
        map.getUiSettings().setZoomControlsEnabled(false);
        map.getUiSettings().setCompassEnabled(false);
        map.getUiSettings().setMapToolbarEnabled(false);
        map.getUiSettings().setAllGesturesEnabled(true);
        try {
            map.setMyLocationEnabled(false);
        } catch (SecurityException ignored) {
        }

        try {
            if (restoredCameraTarget != null) {
                float z = restoredCameraZoom > 0 ? restoredCameraZoom : 15f;
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(restoredCameraTarget, z));
            } else {
                LatLng fallback = new LatLng(1.3521, 103.8198);
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(fallback, 12f));
            }
        } catch (Exception ignored) {
        }

        map.setOnMarkerClickListener(marker -> {
            try {
                if (marker == null) return false;
                selectedInfoMarker = marker;
                suppressSelectedInfoWindow = false;
                Object tag = marker.getTag();
                String markerUid = tag == null ? "" : String.valueOf(tag).trim();
                LatLng p = marker.getPosition();
                if (p == null) return false;
                String key = buildAddrKey(markerUid, p.latitude, p.longitude);
                String cached = addressCache.get(key);
                if (cached != null && !cached.trim().isEmpty()) {
                    marker.setSnippet(cached.trim());
                    marker.showInfoWindow();
                    return true;
                }
                marker.setSnippet("Loading address…");
                marker.showInfoWindow();
                maybeReverseGeocode(markerUid, p.latitude, p.longitude, key);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        });

        map.setOnMapClickListener(latLng -> {
            try {
                if (selectedInfoMarker == null) return;
                if (suppressSelectedInfoWindow) return;
                if (selectedInfoMarker.isInfoWindowShown()) return;
                long now = System.currentTimeMillis();
                if (now - lastInfoWindowReopenAtMs < 800L) return;
                lastInfoWindowReopenAtMs = now;
                selectedInfoMarker.showInfoWindow();
            } catch (Exception ignored) {
            }
        });
        map.setOnCameraMoveStartedListener(reason -> {
            if (reason != GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) return;
            try {
                if (selectedInfoMarker != null) selectedInfoMarker.hideInfoWindow();
            } catch (Exception ignored) {
            }
            suppressSelectedInfoWindow = true;
        });

        requestLocationIfNeeded();
    }

    private void applySavedMapType() {
        if (map == null) return;
        try {
            boolean satellite = UserPrefs.isMapTypeSatellite(this);
            map.setMapType(satellite ? GoogleMap.MAP_TYPE_SATELLITE : GoogleMap.MAP_TYPE_NORMAL);
            applyResQTapMapStyle(!satellite);
        } catch (Exception ignored) {
        }
    }

    private void applyResQTapMapStyle(boolean enabled) {
        if (map == null) return;
        try {
            map.setMapStyle(null);
        } catch (Exception ignored) {
        }
    }

    private void toggleMapType() {
        MapTypeBottomSheet.show(this, map);
    }

    private void startPolling() {
        pollingExecutor.execute(() -> {

            try {
                FirebaseRoomClient.joinRoom(roomCode, uid, displayName, "");
            } catch (Exception ignored) {

            }

            try {
                creatorUid = FirebaseRoomClient.fetchCreatorUid(roomCode);
                runOnUiThread(() -> {
                    if (membersAdapter != null) membersAdapter.setCreatorUid(creatorUid);
                    updateRoomFooterVisibility();
                });
            } catch (Exception ignored) {
            }

            try {
                int pct = BatteryUtils.getBatteryPct(RoomMapActivity.this);
                FirebaseRoomClient.upsertMemberPresenceQueued(roomCode, uid, displayName, myPhotoUrl, myPhotoB64, pct);
            } catch (Exception ignored) {
            }
            while (running) {
                try {

                    ArrayList<FirebaseRoomClient.Member> members = FirebaseRoomClient.fetchMembers(roomCode, 0);
                    runOnUiThread(() -> applyMembers(members));
                } catch (Exception ignored) {
                }
                try {
                    Thread.sleep(2500);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
    }

    private void applyMembers(ArrayList<FirebaseRoomClient.Member> members) {
        if (map == null) return;
        if (members != null && !pendingKickUids.isEmpty()) {
            java.util.HashSet<String> stillPresent = new java.util.HashSet<>();
            ArrayList<FirebaseRoomClient.Member> filtered = new ArrayList<>();
            for (FirebaseRoomClient.Member m : members) {
                if (m == null) continue;
                String mu = String.valueOf(m.uid == null ? "" : m.uid).trim();
                if (mu.isEmpty()) continue;
                if (pendingKickUids.contains(mu)) {
                    stillPresent.add(mu);
                    continue;
                }
                filtered.add(m);
            }
            pendingKickUids.retainAll(stillPresent);
            members = filtered;
        }
        updateMembersList(members);
        updateMyBatteryPct();
        if (membersAdapter != null) {
            membersAdapter.setMyBatteryPct(myBatteryPct);
            android.os.Parcelable listState = null;
            try {
                if (membersRecycler != null && membersRecycler.getLayoutManager() != null) {
                    listState = membersRecycler.getLayoutManager().onSaveInstanceState();
                }
            } catch (Exception ignored) {
            }
            membersAdapter.submit(members);
            try {
                if (listState != null && membersRecycler != null && membersRecycler.getLayoutManager() != null) {
                    android.os.Parcelable finalState = listState;
                    membersRecycler.post(() -> {
                        try {
                            if (membersRecycler.getLayoutManager() != null) {
                                membersRecycler.getLayoutManager().onRestoreInstanceState(finalState);
                            }
                        } catch (Exception ignored) {
                        }
                    });
                }
            } catch (Exception ignored) {
            }
        }
        int count = members == null ? 0 : members.size();
        updateTopSubtitle(count);
        for (FirebaseRoomClient.Member m : members) {

            if (m != null && m.uid != null && !m.uid.trim().isEmpty()) {
                String currentRoomUrl = String.valueOf(m.photoUrl == null ? "" : m.photoUrl).trim();
                if (currentRoomUrl.isEmpty()) maybeLookupPhotoUrlForMember(m.uid);
                String currentRoomB64 = String.valueOf(m.photoB64 == null ? "" : m.photoB64).trim();
                if (currentRoomB64.isEmpty()) maybeLookupPhotoB64ForMember(m.uid);
            }
            LatLng pos = new LatLng(m.lat, m.lng);
            Marker marker = markersByUid.get(m.uid);
            String title = (m.name == null || m.name.trim().isEmpty()) ? "Member" : m.name;
            if (marker == null) {
                MarkerOptions opts = new MarkerOptions().position(pos).title(title);
                opts.snippet(getMarkerSnippet(m.uid, m.lat, m.lng));

                opts.icon(BitmapDescriptorFactory.fromBitmap(buildProfileMarkerBitmap(this, "")));
                opts.anchor(0.5f, 0.5f);
                marker = map.addMarker(opts);
                markersByUid.put(m.uid, marker);
            } else {
                marker.setPosition(pos);
                marker.setTitle(title);
                marker.setSnippet(getMarkerSnippet(m.uid, m.lat, m.lng));
                marker.setAnchor(0.5f, 0.5f);
            }
            if (marker != null) marker.setTag(m.uid);
            if (m.uid != null && m.uid.equals(navigationTargetUid)) {
                updateNavigationPolylinePoints(null, pos);
            }
            if (marker != null) {
                if (m.uid.equals(uid)) {
                    String iconKey = "self|" + myPhotoUri + "|" + myPhotoUrl + "|" + (myPhotoB64 == null ? "" : myPhotoB64.hashCode());
                    if (!iconKey.equals(markerIconKeyByUid.get(uid))) {
                        marker.setIcon(BitmapDescriptorFactory.fromBitmap(buildSelfMarkerBitmap()));
                        marker.setAnchor(0.5f, 0.5f);
                        markerIconKeyByUid.put(uid, iconKey);
                    }
                    if ((myPhotoUri == null || myPhotoUri.isEmpty())
                            && (myPhotoB64 == null || myPhotoB64.isEmpty())
                            && myPhotoUrl != null
                            && !myPhotoUrl.isEmpty()) {
                        maybeLoadRemoteMarkerIcon(uid, myPhotoUrl, marker);
                    }
                } else {
                    String b64 = String.valueOf(m.photoB64 == null ? "" : m.photoB64).trim();
                    if (b64.isEmpty()) {
                        String cachedB64 = resolvedPhotoB64ByUid.get(m.uid);
                        if (cachedB64 != null && !cachedB64.trim().isEmpty()) b64 = cachedB64.trim();
                    }
                    Bitmap b = decodeBase64Avatar(b64);
                    if (b != null) {
                        String iconKey = "b64|" + b64.hashCode();
                        if (!iconKey.equals(markerIconKeyByUid.get(m.uid))) {
                            marker.setIcon(BitmapDescriptorFactory.fromBitmap(buildProfileMarkerBitmapFromPhoto(this, b)));
                            marker.setAnchor(0.5f, 0.5f);
                            markerIconKeyByUid.put(m.uid, iconKey);
                        }
                    } else {
                        String url0 = String.valueOf(m.photoUrl == null ? "" : m.photoUrl).trim();
                        if (url0.isEmpty()) {
                            String iconKey = "default|empty";
                            if (!iconKey.equals(markerIconKeyByUid.get(m.uid))) {
                                marker.setIcon(BitmapDescriptorFactory.fromBitmap(buildProfileMarkerBitmap(this, "")));
                                marker.setAnchor(0.5f, 0.5f);
                                markerIconKeyByUid.put(m.uid, iconKey);
                            }
                        }

                        String url = String.valueOf(m.photoUrl == null ? "" : m.photoUrl).trim();
                        if (!url.isEmpty()) {
                            maybeLoadRemoteMarkerIcon(m.uid, url, marker);
                        } else {
                            maybeLookupPhotoUrlForMember(m.uid);
                        }
                    }
                }
            }

            if (marker != null && selectedInfoMarker != null) {
                try {
                    Object t = marker.getTag();
                    Object st = selectedInfoMarker.getTag();
                    if (t != null && st != null && String.valueOf(t).equals(String.valueOf(st))) selectedInfoMarker = marker;
                } catch (Exception ignored) {
                }
            }
        }
        maybeFocusPendingSender(members);
        if (!suppressSelectedInfoWindow && selectedInfoMarker != null) {
            try {
                if (!selectedInfoMarker.isInfoWindowShown()) {
                    long now = System.currentTimeMillis();
                    if (now - lastInfoWindowReopenAtMs >= 1200L) {
                        lastInfoWindowReopenAtMs = now;
                        selectedInfoMarker.showInfoWindow();
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void maybeFocusPendingSender(ArrayList<FirebaseRoomClient.Member> members) {
        try {
            String focusUid = String.valueOf(pendingFocusUid == null ? "" : pendingFocusUid).trim();
            if (focusUid.isEmpty()) return;
            if (members == null) return;
            FirebaseRoomClient.Member found = null;
            for (FirebaseRoomClient.Member m : members) {
                if (m == null || m.uid == null) continue;
                if (focusUid.equals(String.valueOf(m.uid).trim())) {
                    found = m;
                    break;
                }
            }
            if (found == null) return;
            pendingFocusUid = "";
            pendingFocusAlertId = "";
            focusMemberOnMap(found);
        } catch (Exception ignored) {
        }
    }

    private void restoreBlinkFromPrefs() {
        try {
            String room = String.valueOf(UserPrefs.getSosUiRoom(this) == null ? "" : UserPrefs.getSosUiRoom(this)).trim().toUpperCase(java.util.Locale.ROOT);
            if (room.isEmpty() || !room.equals(roomCode)) return;
            long until = UserPrefs.getSosUiUntilMs(this);
            long now = System.currentTimeMillis();
            if (until <= now) return;
            String senderUid = String.valueOf(UserPrefs.getSosUiSenderUid(this) == null ? "" : UserPrefs.getSosUiSenderUid(this)).trim();
            if (senderUid.isEmpty()) return;
            long remaining = Math.max(0L, until - now);
            setActiveSosBlink(senderUid, remaining);
            try {
                android.util.Log.d("SOS_DEBUG", "UI blink restored from prefs senderUid=" + senderUid + " remainingMs=" + remaining);
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {
        }
    }

    private void focusMemberOnMap(FirebaseRoomClient.Member m) {
        if (m == null) return;
        if (map == null) return;
        if (m.lat == 0d && m.lng == 0d) {
            Toast.makeText(this, R.string.toast_location_not_available_yet, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            try {
                if (membersSheetBehavior != null) {
                    membersSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            } catch (Exception ignored) {
            }

            String memberUid = String.valueOf(m.uid == null ? "" : m.uid).trim();
            Marker marker = memberUid.isEmpty() ? null : markersByUid.get(memberUid);
            LatLng pos = (marker != null && marker.getPosition() != null)
                    ? marker.getPosition()
                    : new LatLng(m.lat, m.lng);

            suppressSelectedInfoWindow = false;
            if (marker != null) {
                selectedInfoMarker = marker;
                Object tag = marker.getTag();
                String markerUid = tag == null ? "" : String.valueOf(tag).trim();
                String key = buildAddrKey(markerUid, pos.latitude, pos.longitude);
                String cached = addressCache.get(key);
                if (cached != null && !cached.trim().isEmpty()) marker.setSnippet(cached.trim());
            }

            float zoom = 16f;
            try {
                zoom = Math.max(zoom, map.getCameraPosition().zoom);
            } catch (Exception ignored) {
            }
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, zoom));

            if (marker != null) {
                lastInfoWindowReopenAtMs = System.currentTimeMillis();
                marker.showInfoWindow();
            }
        } catch (Exception ignored) {
        }
    }

    private void confirmRemoveMember(FirebaseRoomClient.Member m) {
        if (m == null) return;
        String targetUid = String.valueOf(m.uid == null ? "" : m.uid).trim();
        if (targetUid.isEmpty()) return;
        if (uid != null && uid.trim().equals(targetUid)) return;
        if (creatorUid == null || creatorUid.trim().isEmpty() || uid == null || !uid.trim().equals(creatorUid.trim())) return;

        String name = (m.name == null || m.name.trim().isEmpty()) ? "this person" : m.name.trim();
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.room_remove_person_title))
                .setMessage(getString(R.string.room_remove_person_msg, name))
                .setNegativeButton(getString(R.string.action_cancel), (d, w) -> {
                })
                .setPositiveButton(getString(R.string.action_remove), (d, w) -> removeMemberFromRoom(targetUid, name))
                .show();
    }

    /** Padam atau bersihkan MemberFromRoom. */
    private void removeMemberFromRoom(String targetUid, String targetName) {
        String u = String.valueOf(targetUid == null ? "" : targetUid).trim();
        if (u.isEmpty()) return;
        pendingKickUids.add(u);
        executor.execute(() -> FirebaseRoomClient.kickMemberAsCreatorQueued(uid, roomCode, u));
        try {
            Marker marker = markersByUid.remove(u);
            if (marker != null) marker.remove();
        } catch (Exception ignored) {
        }
        try {
            if (membersAdapter != null) membersAdapter.removeByUid(u);
        } catch (Exception ignored) {
        }
        Toast.makeText(this, getString(R.string.toast_removed_person, String.valueOf(targetName == null ? "" : targetName)), Toast.LENGTH_SHORT).show();
    }

    private boolean canUseRemoveMode() {
        return uid != null
                && creatorUid != null
                && !creatorUid.trim().isEmpty()
                && uid.trim().equals(creatorUid.trim());
    }

    private boolean canAddRoomMembers() {
        boolean isCreator = canUseRemoveMode();
        return isCreator || (roomPermissions == null || roomPermissions.allowMemberInvite);
    }

    private boolean shouldShowRoomFooter() {
        return canUseRemoveMode() || canAddRoomMembers();
    }

    private void updateRoomFooterVisibility() {
        if (membersAdapter != null) {
            membersAdapter.setRemoveFooterVisible(shouldShowRoomFooter());
        }
    }

    private void updateSosFabVisibility() {
        if (fabSos == null) return;
        boolean canSos = (roomPermissions == null || roomPermissions.allowSosAlarm);
        fabSos.setVisibility(canSos ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private void setRemoveMode(boolean enabled) {
        removeMode = enabled && canUseRemoveMode();
        if (membersAdapter != null) membersAdapter.setRemoveMode(removeMode);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        try {
            if (removeMode && ev != null && ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (membersRecycler != null) {
                    android.graphics.Rect r = new android.graphics.Rect();
                    if (membersRecycler.getGlobalVisibleRect(r) && r.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                        return super.dispatchTouchEvent(ev);
                    }
                }
                setRemoveMode(false);
            }
        } catch (Exception ignored) {
        }
        return super.dispatchTouchEvent(ev);
    }

    private String getMarkerSnippet(String memberUid, double lat, double lng) {
        String u = String.valueOf(memberUid == null ? "" : memberUid).trim();
        if (lat == 0 && lng == 0) return "";
        String key = buildAddrKey(u, lat, lng);
        String cached = addressCache.get(key);
        if (cached != null && !cached.trim().isEmpty()) return cached.trim();
        maybeReverseGeocode(u, lat, lng, key);
        return "";
    }

    private void maybeLookupPhotoUrlForMember(String memberUid) {
        String u = String.valueOf(memberUid == null ? "" : memberUid).trim();
        if (u.isEmpty()) return;
        if (!photoUrlLookupInflight.add(u)) return;
        FirebaseRoomClient.fetchUserPhotoUrlQueued(u, (id, url) -> {
            photoUrlLookupInflight.remove(id);
            String photoUrl = String.valueOf(url == null ? "" : url).trim();
            if (photoUrl.isEmpty()) return;
            resolvedPhotoUrlsByUid.put(id, photoUrl);
            FirebaseRoomClient.backfillMemberPhotoUrlQueued(roomCode, id, photoUrl);
            runOnUiThread(() -> {
                Marker m = markersByUid.get(id);
                if (m == null) return;
                maybeLoadRemoteMarkerIcon(id, photoUrl, m);
                if (membersAdapter != null) membersAdapter.notifyDataSetChanged();
            });
        });
    }

    private void maybeLookupPhotoB64ForMember(String memberUid) {
        String u = String.valueOf(memberUid == null ? "" : memberUid).trim();
        if (u.isEmpty()) return;
        if (!photoB64LookupInflight.add(u)) return;
        FirebaseRoomClient.fetchUserPhotoB64Queued(u, (id, b64) -> {
            photoB64LookupInflight.remove(id);
            String photoB64 = String.valueOf(b64 == null ? "" : b64).trim();
            if (photoB64.isEmpty()) return;
            resolvedPhotoB64ByUid.put(id, photoB64);
            FirebaseRoomClient.backfillMemberPhotoB64Queued(roomCode, id, photoB64);
            runOnUiThread(() -> {
                Marker m = markersByUid.get(id);
                if (m != null) {
                    Bitmap b = decodeBase64Avatar(photoB64);
                    if (b != null) {
                        m.setIcon(BitmapDescriptorFactory.fromBitmap(buildProfileMarkerBitmapFromPhoto(this, b)));
                        m.setAnchor(0.5f, 0.5f);
                    }
                }
                if (membersAdapter != null) membersAdapter.notifyDataSetChanged();
            });
        });
    }

    /** Simpan atau hantar data MembersList. */
    private void updateMembersList(ArrayList<FirebaseRoomClient.Member> members) {
        if (membersListView == null) return;
        boolean empty = (members == null || members.isEmpty());
        membersListView.setText(R.string.room_members_empty);
        membersListView.setVisibility(empty ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private final class RoomMembersAdapter extends RecyclerView.Adapter<RoomMembersAdapter.VH> {
        private static final int VT_MEMBER = 1;
        private static final int VT_REMOVE_FOOTER = 2;
        private final String myUid;
        private final ArrayList<FirebaseRoomClient.Member> items = new ArrayList<>();
        private String creatorUid = "";
        private int myBatteryPct = -1;
        private String myPhotoUri = "";
        private String myPhotoUrl = "";
        private String myPhotoB64 = "";
        private volatile boolean removeMode = false;
        private volatile boolean showRemoveFooter = false;
        private volatile String activeSosUid = "";
        private volatile long activeSosUntilMs = 0L;
        private final java.util.HashMap<String, Integer> sosOriginalOrder = new java.util.HashMap<>();

        RoomMembersAdapter(String myUid) {
            this.myUid = myUid == null ? "" : myUid;
            setHasStableIds(true);
        }

        void setCreatorUid(String uid) {
            creatorUid = String.valueOf(uid == null ? "" : uid).trim();
        }

        void setRemoveFooterVisible(boolean visible) {
            showRemoveFooter = visible;
            notifyDataSetChanged();
        }

        void setRemoveMode(boolean enabled) {
            removeMode = enabled;
            notifyDataSetChanged();
        }

        void setMyBatteryPct(int pct) {
            myBatteryPct = pct;
        }

        void setMyPhotoUri(String uri) {
            myPhotoUri = String.valueOf(uri == null ? "" : uri).trim();
        }

        void setMyPhotoUrl(String url) {
            myPhotoUrl = String.valueOf(url == null ? "" : url).trim();
        }

        void setMyPhotoB64(String b64) {
            myPhotoB64 = String.valueOf(b64 == null ? "" : b64).trim();
        }

        void submit(ArrayList<FirebaseRoomClient.Member> members) {
            if (isSameMembers(items, members)) return;
            items.clear();
            if (members != null) items.addAll(members);
            applySosPinIfNeeded();
            notifyDataSetChanged();
        }

        private boolean isSameMembers(List<FirebaseRoomClient.Member> a, List<FirebaseRoomClient.Member> b) {
            if (a == b) return true;
            if (a == null || b == null) return false;
            if (a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); i++) {
                FirebaseRoomClient.Member m1 = a.get(i);
                FirebaseRoomClient.Member m2 = b.get(i);
                if (m1 == null || m2 == null) {
                    if (m1 != m2) return false;
                    continue;
                }
                if (!java.util.Objects.equals(m1.uid, m2.uid)) return false;
                if (!java.util.Objects.equals(m1.name, m2.name)) return false;
                if (Double.compare(m1.lat, m2.lat) != 0) return false;
                if (Double.compare(m1.lng, m2.lng) != 0) return false;
                if (m1.batteryPct != m2.batteryPct) return false;
                if (!java.util.Objects.equals(m1.photoUrl, m2.photoUrl)) return false;
                if (!java.util.Objects.equals(m1.photoB64, m2.photoB64)) return false;
            }
            return true;
        }

        void setActiveSos(String uid, long untilMs) {
            String nextUid = String.valueOf(uid == null ? "" : uid).trim();
            long nextUntil = untilMs;

            String prevUid = String.valueOf(activeSosUid == null ? "" : activeSosUid).trim();
            boolean prevActive = !prevUid.isEmpty() && System.currentTimeMillis() <= activeSosUntilMs;
            boolean nextActive = !nextUid.isEmpty() && System.currentTimeMillis() <= nextUntil;

            if (!prevActive && nextActive) {
                sosOriginalOrder.clear();
                for (int i = 0; i < items.size(); i++) {
                    FirebaseRoomClient.Member m = items.get(i);
                    String id = m == null ? "" : String.valueOf(m.uid == null ? "" : m.uid).trim();
                    if (!id.isEmpty() && !sosOriginalOrder.containsKey(id)) sosOriginalOrder.put(id, i);
                }
            }

            activeSosUid = nextUid;
            activeSosUntilMs = nextUntil;

            if (nextActive) {
                applySosPinIfNeeded();
            } else if (prevActive && !nextActive) {

                restoreOriginalOrderIfAny();
                sosOriginalOrder.clear();
            }
            notifyDataSetChanged();
        }

        private void applySosPinIfNeeded() {
            String sosUid = String.valueOf(activeSosUid == null ? "" : activeSosUid).trim();
            if (sosUid.isEmpty()) return;
            if (System.currentTimeMillis() > activeSosUntilMs) return;

            if (!sosOriginalOrder.isEmpty()) {
                java.util.Collections.sort(items, (a, b) -> {
                    String au = a == null ? "" : String.valueOf(a.uid == null ? "" : a.uid).trim();
                    String bu = b == null ? "" : String.valueOf(b.uid == null ? "" : b.uid).trim();
                    Integer ai = sosOriginalOrder.get(au);
                    Integer bi = sosOriginalOrder.get(bu);
                    int av = ai == null ? Integer.MAX_VALUE : ai;
                    int bv = bi == null ? Integer.MAX_VALUE : bi;
                    if (av != bv) return av - bv;
                    return au.compareTo(bu);
                });
            }

            int idx = -1;
            for (int i = 0; i < items.size(); i++) {
                FirebaseRoomClient.Member m = items.get(i);
                String id = m == null ? "" : String.valueOf(m.uid == null ? "" : m.uid).trim();
                if (sosUid.equals(id)) {
                    idx = i;
                    break;
                }
            }
            if (idx > 0) {
                FirebaseRoomClient.Member m = items.remove(idx);
                items.add(0, m);
            }
        }

        private void restoreOriginalOrderIfAny() {
            if (sosOriginalOrder.isEmpty()) return;
            java.util.Collections.sort(items, (a, b) -> {
                String au = a == null ? "" : String.valueOf(a.uid == null ? "" : a.uid).trim();
                String bu = b == null ? "" : String.valueOf(b.uid == null ? "" : b.uid).trim();
                Integer ai = sosOriginalOrder.get(au);
                Integer bi = sosOriginalOrder.get(bu);
                int av = ai == null ? Integer.MAX_VALUE : ai;
                int bv = bi == null ? Integer.MAX_VALUE : bi;
                if (av != bv) return av - bv;
                return au.compareTo(bu);
            });
        }

        void removeByUid(String uid) {
            String u = String.valueOf(uid == null ? "" : uid).trim();
            if (u.isEmpty()) return;
            for (int i = items.size() - 1; i >= 0; i--) {
                FirebaseRoomClient.Member m = items.get(i);
                String id = m == null ? "" : String.valueOf(m.uid == null ? "" : m.uid).trim();
                if (u.equals(id)) {
                    items.remove(i);
                    notifyItemRemoved(i);
                    return;
                }
            }
        }

    @Override
        public long getItemId(int position) {
            if (getItemViewType(position) == VT_REMOVE_FOOTER) return ("remove_footer").hashCode();
            FirebaseRoomClient.Member m = (position >= 0 && position < items.size()) ? items.get(position) : null;
            String key = m == null ? "" : String.valueOf(m.uid);
            return key.hashCode();
        }

        /** Inflate susun atur XML untuk item ViewHolder. */
    @Override
        public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            android.view.LayoutInflater inflater = android.view.LayoutInflater.from(parent.getContext());
            if (viewType == VT_REMOVE_FOOTER) {
                android.view.View v = inflater.inflate(R.layout.item_room_remove_toggle, parent, false);
                return new VH(v, true);
            }
            android.view.View v = inflater.inflate(R.layout.item_room_member, parent, false);
            return new VH(v, false);
        }

        /** Bind data ke elemen paparan item. */
    @Override
        public void onBindViewHolder(VH h, int position) {
            if (getItemViewType(position) == VT_REMOVE_FOOTER) {
                boolean isCreator = RoomMapActivity.this.canUseRemoveMode();
                boolean canAdd = RoomMapActivity.this.canAddRoomMembers();

                if (h.removeFooter != null) {
                    if (isCreator) {
                        h.removeFooter.setVisibility(android.view.View.VISIBLE);
                        h.removeFooter.setAlpha(removeMode ? 1f : 0.85f);
                        h.removeFooter.setOnClickListener(v -> RoomMapActivity.this.setRemoveMode(!removeMode));
                    } else {
                        h.removeFooter.setVisibility(android.view.View.GONE);
                    }
                }
                if (h.addMemberFooter != null) {
                    if (canAdd) {
                        h.addMemberFooter.setVisibility(android.view.View.VISIBLE);
                        h.addMemberFooter.setOnClickListener(v -> RoomMapActivity.this.showAddRoomMemberDialog());
                    } else {
                        h.addMemberFooter.setVisibility(android.view.View.GONE);
                    }
                }
                return;
            }
            FirebaseRoomClient.Member m = items.get(position);
            String baseName = (m.name == null || m.name.trim().isEmpty()) ? "Member" : m.name.trim();
            boolean isSelf = (m.uid != null && m.uid.trim().equals(myUid));
            boolean isCreator = (m.uid != null && !creatorUid.isEmpty() && m.uid.trim().equals(creatorUid));
            String name = baseName;
            if (isSelf && isCreator) name = name + " (You, Creator)";
            else if (isSelf) name = name + " (You)";
            else if (isCreator) name = name + " (Creator)";
            h.name.setText(name);

            long now = System.currentTimeMillis();
            long ageMs = Math.max(0, now - m.updatedAt);
            boolean online = m.updatedAt > 0 && ageMs <= 30_000;
            h.online.setText(online ? "Online" : "Offline");
            h.online.setTextColor(androidx.core.content.ContextCompat.getColor(
                    h.itemView.getContext(),
                    online ? R.color.success_green : R.color.text_secondary
            ));

            h.lastSeen.setText(formatLastSeen(ageMs, online));
            if (isSelf && myBatteryPct >= 0 && myBatteryPct <= 100) {
                h.batteryPct.setText(myBatteryPct + "%");
            } else if (!isSelf && m.batteryPct >= 0 && m.batteryPct <= 100) {
                h.batteryPct.setText(m.batteryPct + "%");
            } else {
                h.batteryPct.setText("--%");
            }

            h.avatar.setImageTintList(null);
            String photoUri = isSelf ? myPhotoUri : "";
            String b64 = isSelf
                    ? myPhotoB64
                    : String.valueOf(m.photoB64 == null ? "" : m.photoB64).trim();
            if (b64.isEmpty() && m.uid != null) {
                String cachedB64 = resolvedPhotoB64ByUid.get(m.uid.trim());
                if (cachedB64 != null && !cachedB64.trim().isEmpty()) b64 = cachedB64.trim();
            }
            String url = isSelf ? myPhotoUrl : String.valueOf(m.photoUrl == null ? "" : m.photoUrl).trim();
            if (url.isEmpty() && m.uid != null) {
                String cached = resolvedPhotoUrlsByUid.get(m.uid.trim());
                if (cached != null && !cached.trim().isEmpty()) url = cached;
                else maybeLookupPhotoUrlForMember(m.uid);
            }
            if (m.uid != null && !m.uid.trim().isEmpty()) maybeLookupPhotoB64ForMember(m.uid);

            boolean loaded = false;
            if (!photoUri.isEmpty()) {
                try {
                    h.avatar.setImageURI(Uri.parse(photoUri));
                    if (h.avatar.getDrawable() != null) loaded = true;
                } catch (Exception ignored) {
                }
            }
            if (!loaded && !b64.isEmpty()) {
                Bitmap b = decodeBase64Avatar(b64);
                if (b != null) {
                    h.avatar.setImageBitmap(b);
                    loaded = true;
                }
            }
            if (!loaded) {
                h.avatar.setImageResource(R.drawable.ic_avatar);
                if (!url.isEmpty()) {
                    maybeLoadRemoteAvatarInto(h.avatar, url);
                }
            }

            boolean canShowBell = (roomPermissions == null || roomPermissions.allowTriggerBell) && !isSelf;
            if (h.bellPill != null) {
                h.bellPill.setVisibility(canShowBell ? android.view.View.VISIBLE : android.view.View.GONE);
            }
            if (canShowBell) {
                android.view.View.OnClickListener bellClick = v -> sendBellTo(m.uid);
                if (h.bell != null) h.bell.setOnClickListener(bellClick);
                if (h.bellPill != null) h.bellPill.setOnClickListener(bellClick);
            } else {
                if (h.bell != null) h.bell.setOnClickListener(null);
                if (h.bellPill != null) h.bellPill.setOnClickListener(null);
            }

            if (h.directionPill != null) {
                h.directionPill.setVisibility(isSelf ? android.view.View.GONE : android.view.View.VISIBLE);
                h.directionPill.setOnClickListener(v -> RoomMapActivity.this.startNavigationTo(m));
            }

            h.itemView.setOnClickListener(v -> {
                if (removeMode) {
                    return;
                }
                focusMemberOnMap(m);
            });

            boolean showRemove = removeMode && !isSelf && RoomMapActivity.this.canUseRemoveMode();
            if (h.removePill != null) {
                h.removePill.setVisibility(showRemove ? android.view.View.VISIBLE : android.view.View.GONE);
                android.view.View.OnClickListener removeClick = v -> confirmRemoveMember(m);
                h.removePill.setOnClickListener(removeClick);
                android.view.View removeIcon = h.itemView.findViewById(R.id.remove_icon);
                if (removeIcon != null) {
                    removeIcon.setOnClickListener(removeClick);
                }
            }

            bindAddressLine(h, m);

            boolean showSos = false;
            try {
                if (roomPermissions == null || roomPermissions.allowSosAlarm) {
                    String blinkUid = String.valueOf(activeSosUid == null ? "" : activeSosUid).trim();
                    showSos = !blinkUid.isEmpty()
                            && m.uid != null
                            && blinkUid.equals(m.uid.trim())
                            && System.currentTimeMillis() <= activeSosUntilMs;
                }
            } catch (Exception ignored) {
            }
            if (h.sosBlink != null) {
                if (showSos) startBlink(h.sosBlink);
                else stopBlink(h.sosBlink);
            }
        }

    @Override
        public int getItemViewType(int position) {
            if (showRemoveFooter && !items.isEmpty() && position == items.size()) return VT_REMOVE_FOOTER;
            return VT_MEMBER;
        }

        /** Setup dan konfigurasi AddressLine. */
        private void bindAddressLine(VH h, FirebaseRoomClient.Member m) {
            if (h == null || h.address == null || m == null) return;
            if (m.lat == 0 && m.lng == 0) {
                h.address.setVisibility(android.view.View.GONE);
                return;
            }
            String key = buildAddrKey(m.uid, m.lat, m.lng);
            String cached = addressCache.get(key);
            if (cached != null && !cached.trim().isEmpty()) {
                h.address.setText(cached);
                h.address.setVisibility(android.view.View.VISIBLE);
                return;
            }
            h.address.setVisibility(android.view.View.GONE);
            maybeReverseGeocode(m.uid, m.lat, m.lng, key);
        }

        private String formatLastSeen(long ageMs, boolean online) {
            if (online) return "Live now";
            long mins = ageMs / 60000L;
            if (mins <= 0) return "Seen just now";
            if (mins < 60) return "Seen " + mins + "m ago";
            long hours = mins / 60L;
            if (hours < 24) return "Seen " + hours + "h ago";
            long days = hours / 24L;
            return "Seen " + days + "d ago";
        }

    @Override
        public int getItemCount() {
            return items.size() + ((showRemoveFooter && !items.isEmpty()) ? 1 : 0);
        }

        final class VH extends RecyclerView.ViewHolder {
            final ShapeableImageView avatar;
            final android.view.View sosBlink;
            final TextView name;
            final TextView batteryPct;
            final TextView online;
            final TextView lastSeen;
            final TextView address;
            final android.widget.ImageView bell;
            final android.view.View batteryPill;
            final android.view.View bellPill;
            final android.view.View removePill;
            final android.view.View directionPill;
            final TextView removeFooter;
            final android.view.View addMemberFooter;

            VH(android.view.View itemView, boolean isFooter) {
                super(itemView);
                if (isFooter) {
                    avatar = null;
                    sosBlink = null;
                    name = null;
                    batteryPct = null;
                    online = null;
                    lastSeen = null;
                    address = null;
                    bell = null;
                    batteryPill = null;
                    bellPill = null;
                    removePill = null;
                    directionPill = null;
                    removeFooter = itemView.findViewById(R.id.remove_person_footer);
                    addMemberFooter = itemView.findViewById(R.id.btn_footer_add_member);
                } else {
                    avatar = itemView.findViewById(R.id.avatar);
                    sosBlink = itemView.findViewById(R.id.sos_blink);
                    name = itemView.findViewById(R.id.name);
                    batteryPct = itemView.findViewById(R.id.battery_pct);
                    online = itemView.findViewById(R.id.online_status);
                    lastSeen = itemView.findViewById(R.id.last_seen);
                    address = itemView.findViewById(R.id.address_line);
                    bell = itemView.findViewById(R.id.bell_icon);
                    batteryPill = itemView.findViewById(R.id.battery_pill);
                    bellPill = itemView.findViewById(R.id.bell_pill);
                    removePill = itemView.findViewById(R.id.remove_pill);
                    directionPill = itemView.findViewById(R.id.direction_pill);
                    removeFooter = null;
                    addMemberFooter = null;
                }
            }
        }

        private void startBlink(android.view.View v) {
            try {
                if (v.getVisibility() != android.view.View.VISIBLE) v.setVisibility(android.view.View.VISIBLE);
                Object tag = v.getTag();
                if (tag instanceof android.animation.ObjectAnimator) {
                    android.animation.ObjectAnimator existing = (android.animation.ObjectAnimator) tag;
                    if (existing.isStarted()) return;
                }
                android.animation.ObjectAnimator anim = android.animation.ObjectAnimator.ofFloat(v, "alpha", 0.15f, 1f);
                anim.setDuration(550L);
                anim.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                anim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                v.setAlpha(0.15f);
                v.setTag(anim);
                anim.start();
            } catch (Exception ignored) {
            }
        }

        private void stopBlink(android.view.View v) {
            try {
                Object tag = v.getTag();
                if (tag instanceof android.animation.ObjectAnimator) {
                    ((android.animation.ObjectAnimator) tag).cancel();
                }
            } catch (Exception ignored) {
            }
            try {
                v.setTag(null);
                v.setAlpha(0f);
                v.setVisibility(android.view.View.GONE);
            } catch (Exception ignored) {
            }
        }
    }

    private static String buildAddrKey(String uid, double lat, double lng) {
        String u = String.valueOf(uid == null ? "" : uid).trim();
        double rLat = Math.round(lat * 10000d) / 10000d;
        double rLng = Math.round(lng * 10000d) / 10000d;
        return u + ":" + rLat + "," + rLng;
    }

    private void maybeReverseGeocode(String uid, double lat, double lng, String key) {
        String u = String.valueOf(uid == null ? "" : uid).trim();
        if (u.isEmpty()) return;
        if (key == null || key.trim().isEmpty()) return;
        String lastKey = lastAddrKeyByUid.get(u);
        if (key.equals(lastKey)) return;
        lastAddrKeyByUid.put(u, key);
        if (!geocodeInflight.add(key)) return;

        geocodeExecutor.execute(() -> {
            String label = reverseGeocodeLabel(lat, lng);
            geocodeInflight.remove(key);
            if (label == null) label = "";
            String out = label.trim();
            if (out.isEmpty()) return;
            addressCache.put(key, out);
            runOnUiThread(() -> {
                try {
                    Marker marker = markersByUid.get(u);
                    if (marker != null) {
                        LatLng p = marker.getPosition();
                        String currentKey = p == null ? "" : buildAddrKey(u, p.latitude, p.longitude);
                        if (key.equals(currentKey)) {
                            marker.setSnippet(out);

                            if (!suppressSelectedInfoWindow
                                    && selectedInfoMarker != null
                                    && marker.equals(selectedInfoMarker)
                                    && marker.isInfoWindowShown()) {
                                marker.showInfoWindow();
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
                if (membersAdapter != null) membersAdapter.notifyDataSetChanged();
            });
        });
    }

    private String reverseGeocodeLabel(double lat, double lng) {
        try {
            if (!android.location.Geocoder.isPresent()) return "";
        } catch (Exception ignored) {
        }
        try {
            android.location.Geocoder g = new android.location.Geocoder(this, java.util.Locale.getDefault());
            java.util.List<android.location.Address> list = g.getFromLocation(lat, lng, 1);
            if (list == null || list.isEmpty()) return "";
            android.location.Address a = list.get(0);
            String road = safe(a.getThoroughfare());
            String sub = safe(a.getSubThoroughfare());
            String locality = safe(a.getLocality());
            String admin = safe(a.getAdminArea());
            String feature = safe(a.getFeatureName());

            StringBuilder sb = new StringBuilder();
            if (!road.isEmpty()) {
                sb.append(road);
                if (!sub.isEmpty()) sb.append(" ").append(sub);
            } else if (!feature.isEmpty()) {
                sb.append(feature);
            }
            if (!locality.isEmpty()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(locality);
            } else if (!admin.isEmpty()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(admin);
            }
            return sb.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String safe(String s) {
        if (s == null) return "";
        s = s.trim();
        return s;
    }

    private void requestLocationIfNeeded() {
        if (map == null) return;
        if (!GpsUtils.isGpsEnabled(this)) {
            Toast.makeText(this, R.string.toast_turn_on_location, Toast.LENGTH_SHORT).show();
            return;
        }
        if (PermissionUtils.hasAnyLocation(this)) {

            startLocationUpdates();
            centerCameraOnMe();
        } else {
            locationPerms.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void centerCameraOnMe() {
        if (map == null) return;
        try {
            if (myLocationMarker != null) {
                LatLng pos = myLocationMarker.getPosition();
                if (pos != null) {
                    if (navigationTargetUid != null && lastNavigatedTargetLatLng != null) {
                        fetchRouteAndDraw(pos, lastNavigatedTargetLatLng);
                    } else {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f));
                    }
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            fusedLocationClient.getLastLocation().addOnSuccessListener(loc -> {
                if (loc == null || map == null) return;
                LatLng me = new LatLng(loc.getLatitude(), loc.getLongitude());
                if (navigationTargetUid != null && lastNavigatedTargetLatLng != null) {
                    fetchRouteAndDraw(me, lastNavigatedTargetLatLng);
                } else {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(me, 16f));
                }
                upsertMyLocationMarker(me);
            });
        } catch (SecurityException ignored) {
        }
    }

    private void startLocationUpdates() {
        if (!PermissionUtils.hasAnyLocation(this)) return;
        if (map == null) return;
        if (isRequestingUpdates) return;

        try {
            map.setMyLocationEnabled(false);
        } catch (SecurityException ignored) {
        }

        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                .setMinUpdateIntervalMillis(2000L)
                .setWaitForAccurateLocation(false)
                .build();

        if (locationCallback == null) {
            locationCallback = new LocationCallback() {
    @Override
                public void onLocationResult(LocationResult result) {
                    if (result == null) return;
                    android.location.Location loc = result.getLastLocation();
                    if (loc == null) return;
                    LatLng me = new LatLng(loc.getLatitude(), loc.getLongitude());
                    upsertMyLocationMarker(me);
                    publishToBackend(me);
                }

    @Override
                public void onLocationAvailability(LocationAvailability availability) {

                }
            };
        }

        try {
            fusedLocationClient.requestLocationUpdates(req, locationCallback, getMainLooper());
            isRequestingUpdates = true;
        } catch (SecurityException ignored) {
        }

        try {
            fusedLocationClient.getLastLocation().addOnSuccessListener(loc -> {
                if (loc == null || map == null) return;
                LatLng me = new LatLng(loc.getLatitude(), loc.getLongitude());
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(me, 16f));
                upsertMyLocationMarker(me);
            });
        } catch (SecurityException ignored) {
        }
    }

    private void stopLocationUpdates() {
        if (!isRequestingUpdates) return;
        if (locationCallback == null) return;
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        } catch (Exception ignored) {
        }
        isRequestingUpdates = false;
    }

    private void publishToBackend(LatLng me) {
        updateMyBatteryPct();
        int pct = myBatteryPct;
        executor.execute(() -> FirebaseRoomClient.publishLocationQueued(roomCode, uid, displayName, myPhotoUrl, myPhotoB64, me.latitude, me.longitude, pct));
    }

    private void upsertMyLocationMarker(LatLng pos) {
        if (map == null || pos == null) return;
        Bitmap icon = buildSelfMarkerBitmap();
        if (myLocationMarker == null) {
            myLocationMarker = map.addMarker(new MarkerOptions()
                    .position(pos)
                    .title(displayName == null || displayName.trim().isEmpty() ? "You" : displayName.trim())
                    .icon(BitmapDescriptorFactory.fromBitmap(icon))
                    .anchor(0.5f, 0.5f));
        } else {
            myLocationMarker.setPosition(pos);
            myLocationMarker.setIcon(BitmapDescriptorFactory.fromBitmap(icon));
            myLocationMarker.setAnchor(0.5f, 0.5f);
        }

        if (uid != null && !uid.trim().isEmpty()) markersByUid.put(uid, myLocationMarker);
        if ((myPhotoUri == null || myPhotoUri.isEmpty()) && (myPhotoB64 == null || myPhotoB64.isEmpty()) && myPhotoUrl != null && !myPhotoUrl.isEmpty()) {
            maybeLoadRemoteMarkerIcon(uid, myPhotoUrl, myLocationMarker);
        }
        updateNavigationPolylinePoints(pos, null);
    }

    /** Simpan atau hantar data MyLocationMarkerIcon. */
    private void updateMyLocationMarkerIcon() {
        if (map == null) return;
        if (myLocationMarker == null) {
            Marker existing = (uid == null) ? null : markersByUid.get(uid);
            if (existing != null) myLocationMarker = existing;
        }
        if (myLocationMarker == null) return;
        myLocationMarker.setIcon(BitmapDescriptorFactory.fromBitmap(buildSelfMarkerBitmap()));
        myLocationMarker.setAnchor(0.5f, 0.5f);
        if ((myPhotoUri == null || myPhotoUri.isEmpty()) && (myPhotoB64 == null || myPhotoB64.isEmpty())) {
            if (myPhotoUrl != null && !myPhotoUrl.isEmpty()) {
                maybeLoadRemoteMarkerIcon(uid, myPhotoUrl, myLocationMarker);
            }
        }
    }

    private Bitmap buildSelfMarkerBitmap() {
        try {
            Bitmap b = decodeBase64Avatar(myPhotoB64);
            if (b != null) return buildProfileMarkerBitmapFromPhoto(this, b);
        } catch (Exception ignored) {
        }
        return buildProfileMarkerBitmap(this, myPhotoUri);
    }

    private static Bitmap buildProfileMarkerBitmap(Context ctx, String photoUri) {
        final int sizePx = dp(ctx, 44);
        final int borderPx = dp(ctx, 3);
        final int shadowPx = dp(ctx, 4);

        Bitmap src = loadProfileBitmap(ctx, photoUri);
        if (src == null) {
            return defaultAvatarMarkerBitmap(ctx, sizePx, borderPx, shadowPx);
        }
        Bitmap scaled = Bitmap.createScaledBitmap(src, sizePx, sizePx, true);
        return circularWithBorderAndShadow(scaled, borderPx, shadowPx);
    }

    private static Bitmap buildProfileMarkerBitmapFromPhoto(Context ctx, Bitmap src) {
        final int sizePx = dp(ctx, 44);
        final int borderPx = dp(ctx, 3);
        final int shadowPx = dp(ctx, 4);
        if (src == null) return defaultAvatarMarkerBitmap(ctx, sizePx, borderPx, shadowPx);
        Bitmap scaled = Bitmap.createScaledBitmap(src, sizePx, sizePx, true);
        return circularWithBorderAndShadow(scaled, borderPx, shadowPx);
    }

    private void maybeLoadRemoteMarkerIcon(String memberUid, String photoUrl, Marker marker) {
        String u = String.valueOf(memberUid == null ? "" : memberUid).trim();
        String url = String.valueOf(photoUrl == null ? "" : photoUrl).trim();
        if (u.isEmpty() || url.isEmpty() || marker == null) return;

        if (!url.startsWith("http") && !url.startsWith("gs://")) return;

        enqueueRemotePhoto(url, bmp -> {
            if (bmp == null) return;
            Marker current = markersByUid.get(u);
            if (current == null) return;
            String iconKey = "remote|" + url;
            if (!iconKey.equals(markerIconKeyByUid.get(u))) {
                current.setIcon(BitmapDescriptorFactory.fromBitmap(buildProfileMarkerBitmapFromPhoto(this, bmp)));
                current.setAnchor(0.5f, 0.5f);
                markerIconKeyByUid.put(u, iconKey);
            }
        });
    }

    private void maybeLoadRemoteAvatarInto(ShapeableImageView view, String photoUrl) {
        if (view == null) return;
        String url = String.valueOf(photoUrl == null ? "" : photoUrl).trim();
        if (url.isEmpty() || (!url.startsWith("http") && !url.startsWith("gs://"))) return;

        view.setTag(url);
        enqueueRemotePhoto(url, bmp -> {
            if (bmp == null) return;
            Object tag = view.getTag();
            if (tag == null || !url.equals(tag.toString())) return;
            view.setImageTintList(null);
            view.setImageBitmap(bmp);
        });
    }

    private interface BitmapHandler {
        void onBitmap(Bitmap bmp);
    }

    private void enqueueRemotePhoto(String url, BitmapHandler onReady) {
        if (url == null || url.trim().isEmpty() || onReady == null) return;
        String key = url.trim();
        Bitmap cached = remotePhotoCache.get(key);
        if (cached != null) {
            runOnUiThread(() -> onReady.onBitmap(cached));
            return;
        }

        remotePhotoWaiters.computeIfAbsent(key, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(onReady);
        if (!remotePhotoInflight.add(key)) return;

        imageExecutor.execute(() -> {
            Bitmap downloaded = key.startsWith("gs://") ? downloadBitmapFromStorage(key, 4096) : downloadBitmap(key, 4096);
            if (downloaded != null) remotePhotoCache.put(key, downloaded);
            remotePhotoInflight.remove(key);
            java.util.concurrent.CopyOnWriteArrayList<BitmapHandler> waiters = remotePhotoWaiters.remove(key);
            if (waiters == null || waiters.isEmpty()) return;
            runOnUiThread(() -> {
                for (BitmapHandler w : waiters) {
                    try {
                        w.onBitmap(downloaded);
                    } catch (Exception ignored) {
                    }
                }
            });
        });
    }

    private static Bitmap downloadBitmapFromStorage(String gsUrl, int maxDim) {
        try {
            com.google.firebase.storage.StorageReference ref =
                    com.google.firebase.storage.FirebaseStorage.getInstance("gs://resqtap-b9ff5.firebasestorage.app").getReferenceFromUrl(gsUrl);

            byte[] bytes = com.google.android.gms.tasks.Tasks.await(ref.getBytes(4L * 1024L * 1024L));
            if (bytes == null || bytes.length == 0) return null;
            Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (b == null) return null;
            int w = b.getWidth();
            int h = b.getHeight();
            int m = Math.max(w, h);
            if (m <= maxDim) return b;
            float s = maxDim / (float) m;
            int nw = Math.max(1, Math.round(w * s));
            int nh = Math.max(1, Math.round(h * s));
            Bitmap scaled = Bitmap.createScaledBitmap(b, nw, nh, true);
            b.recycle();
            return scaled;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Bitmap decodeBase64Avatar(String b64) {
        String s = String.valueOf(b64 == null ? "" : b64).trim();
        if (s.isEmpty()) return null;
        String key = Integer.toHexString(s.hashCode());
        Bitmap cached = b64BitmapCache.get(key);
        if (cached != null) return cached;
        try {
            byte[] bytes = android.util.Base64.decode(s, android.util.Base64.DEFAULT);
            if (bytes == null || bytes.length == 0) return null;
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp != null) b64BitmapCache.put(key, bmp);
            return bmp;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Bitmap downloadBitmap(String url, int maxDim) {
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) return null;
            try (java.io.InputStream in = conn.getInputStream()) {
                Bitmap b = BitmapFactory.decodeStream(in);
                if (b == null) return null;
                int w = b.getWidth();
                int h = b.getHeight();
                int m = Math.max(w, h);
                if (m <= maxDim) return b;
                float s = maxDim / (float) m;
                int nw = Math.max(1, Math.round(w * s));
                int nh = Math.max(1, Math.round(h * s));
                Bitmap scaled = Bitmap.createScaledBitmap(b, nw, nh, true);
                b.recycle();
                return scaled;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Bitmap defaultAvatarMarkerBitmap(Context ctx, int sizePx, int borderPx, int shadowPx) {
        int outSize = sizePx + (shadowPx * 2);
        Bitmap out = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);

        float cx = outSize / 2f;
        float cy = outSize / 2f;
        float radius = sizePx / 2f;

        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(0xFFFFFFFF);
        shadow.setStyle(Paint.Style.FILL);
        shadow.setShadowLayer(shadowPx, 0f, shadowPx * 0.75f, 0x55000000);
        canvas.drawCircle(cx, cy, radius, shadow);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(0xFF0B1220);
        fill.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, radius - (shadowPx * 0.5f), fill);

        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(0xFFFFFFFF);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(borderPx);
        canvas.drawCircle(cx, cy, radius - (shadowPx * 0.5f) - (borderPx / 2f), border);

        int iconSize = Math.round(sizePx * 0.72f);

        Bitmap icon = bitmapFromDrawable(ctx, R.drawable.ic_avatar, iconSize, iconSize);
        if (icon != null) {
            float left = cx - (icon.getWidth() / 2f);
            float top = cy - (icon.getHeight() / 2f);
            canvas.drawBitmap(icon, left, top, new Paint(Paint.ANTI_ALIAS_FLAG));
            icon.recycle();
        } else {

            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFFFFFFFF);
            float headR = radius * 0.28f;
            canvas.drawCircle(cx, cy - radius * 0.12f, headR, p);
            RectF body = new RectF(cx - radius * 0.42f, cy + radius * 0.08f, cx + radius * 0.42f, cy + radius * 0.68f);
            canvas.drawRoundRect(body, radius * 0.42f, radius * 0.42f, p);
        }
        return out;
    }

    private static Bitmap loadProfileBitmap(Context ctx, String photoUri) {
        String u = String.valueOf(photoUri == null ? "" : photoUri).trim();
        if (u.isEmpty()) return null;
        try {
            Uri uri = Uri.parse(u);
            try (java.io.InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                return BitmapFactory.decodeStream(in);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Bitmap bitmapFromDrawable(Context ctx, int resId, int w, int h) {
        try {
            Drawable d = ContextCompat.getDrawable(ctx, resId);
            if (d == null) return null;

            if (d instanceof androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
                    || d instanceof android.graphics.drawable.VectorDrawable) {
                try {
                    d = d.mutate();
                } catch (Exception ignored) {
                }
            }
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, w, h);
            d.draw(c);
            return b;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Bitmap circularWithBorderAndShadow(Bitmap src, int borderPx, int shadowPx) {
        int size = Math.min(src.getWidth(), src.getHeight());
        int outSize = size + (shadowPx * 2);
        Bitmap out = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);

        float cx = outSize / 2f;
        float cy = outSize / 2f;
        float radius = (size / 2f);
        float contentRadius = radius - borderPx;

        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(0xFFFFFFFF);
        shadow.setStyle(Paint.Style.FILL);
        shadow.setShadowLayer(shadowPx, 0f, shadowPx * 0.75f, 0x55000000);
        canvas.drawCircle(cx, cy, radius, shadow);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(0xFFFFFFFF);
        fill.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, radius - shadowPx * 0.5f, fill);

        Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Bitmap circle = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888);
        Canvas c2 = new Canvas(circle);

        Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
        mask.setColor(0xFF000000);
        c2.drawCircle(cx, cy, contentRadius, mask);
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));

        RectF dst = new RectF(cx - contentRadius, cy - contentRadius, cx + contentRadius, cy + contentRadius);
        c2.drawBitmap(src, null, dst, mask);
        canvas.drawBitmap(circle, 0, 0, imagePaint);
        circle.recycle();
        return out;
    }

    private static int dp(Context ctx, int dp) {
        float density = ctx.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    /** Simpan atau hantar data MyBatteryPct. */
    private void updateMyBatteryPct() {
        myBatteryPct = BatteryUtils.getBatteryPct(this);
    }

    /** Paparkan AddRoomMemberDialog. */
    private void showAddRoomMemberDialog() {
        boolean isCreator = (uid != null && !uid.isEmpty() && uid.equals(creatorUid));
        if (!isCreator && roomPermissions != null && !roomPermissions.allowMemberInvite) {
            Toast.makeText(this, R.string.invite_disabled_by_room_permission, Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_room_member, null);
        RecyclerView rvFriends = dialogView.findViewById(R.id.rv_select_friends);
        View tvNoFriends = dialogView.findViewById(R.id.tv_no_friends_prompt);
        View progress = dialogView.findViewById(R.id.progress_loading_friends);
        TextView tvError = dialogView.findViewById(R.id.tv_add_member_error);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel_add_member);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btn_confirm_add_member);

        FriendSelectAdapter selectAdapter = new FriendSelectAdapter();
        rvFriends.setLayoutManager(new LinearLayoutManager(this));
        rvFriends.setAdapter(selectAdapter);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        progress.setVisibility(View.VISIBLE);

        Set<String> existingMemberUids = new HashSet<>();
        existingMemberUids.add(uid);
        if (membersAdapter != null) {
            for (FirebaseRoomClient.Member m : membersAdapter.items) {
                if (m != null && m.uid != null && !m.uid.trim().isEmpty()) {
                    existingMemberUids.add(m.uid.trim());
                }
            }
        }

        FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                .getReference("userFriends").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    /** Callback apabila data Firebase Realtime Database berubah. */
    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (isFinishing() || isDestroyed()) return;
                        progress.setVisibility(View.GONE);
                        List<FirebaseFriendClient.FriendInfo> friends = new ArrayList<>();
                        if (snapshot.exists()) {
                            for (DataSnapshot child : snapshot.getChildren()) {
                                FirebaseFriendClient.FriendInfo info = FirebaseFriendClient.FriendInfo.from(child);
                                if (info != null && !existingMemberUids.contains(info.uid)) {
                                    friends.add(info);
                                }
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
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (isFinishing() || isDestroyed()) return;
                        progress.setVisibility(View.GONE);
                        tvNoFriends.setVisibility(View.VISIBLE);
                    }
                });

        btnConfirm.setOnClickListener(v -> {
            List<FirebaseFriendClient.FriendInfo> selectedFriends = selectAdapter.getSelectedFriends();
            if (selectedFriends.isEmpty()) {
                tvError.setText(R.string.room_select_at_least_one_friend);
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            tvError.setVisibility(View.GONE);
            btnConfirm.setEnabled(false);
            btnConfirm.setText(R.string.room_adding_member);

            final String safeRoomName = (currentRoomName == null || currentRoomName.trim().isEmpty()) ? getString(R.string.room_default_name) : currentRoomName;
            executor.execute(() -> {
                try {
                    for (FirebaseFriendClient.FriendInfo friend : selectedFriends) {
                        FirebaseRoomClient.addFriendToRoom(roomCode, safeRoomName, friend.uid, friend.name, displayName);
                    }
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        dialog.dismiss();
                        Toast.makeText(RoomMapActivity.this, R.string.room_member_added_success, Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        btnConfirm.setEnabled(true);
                        btnConfirm.setText(R.string.room_add_member_btn);
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
            int width = Math.min((int) (screenWidth * 0.88f), dp(this, 380));
            dialog.getWindow().setLayout(
                width,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
            dialog.getWindow().setGravity(android.view.Gravity.CENTER);
        }
    }
}

