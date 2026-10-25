package com.example.resqtap.home;
import com.example.resqtap.R;

import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.auth.LoginActivity;
import com.example.resqtap.contacts.EmergencyContactsListActivity;
import com.example.resqtap.map.NearbyHospitalActivity;
import com.example.resqtap.notification.NotificationHelper;
import com.example.resqtap.notification.NotificationsActivity;
import com.example.resqtap.notification.NotificationUtils;
import com.example.resqtap.report.ReportActivity;
import com.example.resqtap.report.SupportActivity;
import com.example.resqtap.room.FirebaseRoomClient;
import com.example.resqtap.room.LiveRoomTrackingService;
import com.example.resqtap.sos.SosBottomSheetController;
import com.example.resqtap.sos.SosServiceStarter;
import com.example.resqtap.highlight.HighlightAdapter;
import com.example.resqtap.highlight.HighlightItem;
import com.example.resqtap.news.MedicalNewsActivity;
import com.example.resqtap.news.MedicalNewsAdapter;
import com.example.resqtap.news.MedicalNewsFetcher;
import com.example.resqtap.news.MedicalNewsItem;
import com.example.resqtap.utils.AvatarUtils;
import com.example.resqtap.utils.BatteryOptimizationHelper;
import com.example.resqtap.utils.BatteryUtils;
import com.example.resqtap.utils.BottomNavUtils;
import com.example.resqtap.utils.PermissionUtils;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;

import android.Manifest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;
import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * MainActivity
 * Main Dashboard: Butang OneTap SOS, grid menu pantas, status bateri, dan keselamatan peribadi.
 */
public class MainActivity extends BaseActivity {
    private TextView subtitle;
    private String phone;
    private ObjectAnimator sosPulseAnimator;
    private Animator sosRingOuterAnimator;
    private Animator sosRingMidAnimator;
    private ObjectAnimator sosGlowAnimator;
    private boolean entranceAnimationPlayed = false;
    private TextView title;
    private ShapeableImageView headerPhoto;
    private MaterialButton btnInbox;
    private android.view.View inboxUnreadDot;
    private ActivityResultLauncher<String> notifPerm;
    private android.view.View medicalCard;
    private TextView medicalBlood;
    private TextView medicalAllergies;
    private TextView medicalConditions;
    private SosBottomSheetController sosSheet;
    private String activeRoomCode = "";
    private DatabaseReference adminNotificationRef;
    private ValueEventListener adminNotificationListener;
    private String pendingAdminNotificationId = "";
    private String pendingAdminNotificationTitle = "";
    private String pendingAdminNotificationMessage = "";
    private boolean adminNotificationBaselineLoaded = false;
    private volatile boolean restoringActiveRoom = false;
    // Multi-room SOS state: roomCode -> sosId for every room we fired to
    private final java.util.concurrent.ConcurrentHashMap<String, String> activeSosIds = new java.util.concurrent.ConcurrentHashMap<>();
    // Set when user slides-to-cancel before all Firebase IDs have arrived
    private volatile boolean sosCancelledWhilePending = false;
    private RecyclerView rvMedicalNews;
    private MedicalNewsAdapter medicalNewsAdapter;
    private android.view.View newsProgress;
    private RecyclerView rvHighlights;
    private HighlightAdapter highlightAdapter;
    private final Handler highlightPollHandler = new Handler(Looper.getMainLooper());
    private final Runnable highlightPollRunnable = new Runnable() {
        @Override
        public void run() {
            loadHighlightsFromSupabase();
            highlightPollHandler.postDelayed(this, 4000);
        }
    };
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String[]> locationPermLauncher;
    private double lastKnownLat = 0;
    private double lastKnownLng = 0;

    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
            return;
        }
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        android.view.View root = findViewById(R.id.main);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        BottomNavUtils.setup(bottomNav, this, R.id.nav_bottom_home);
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

        notifPerm = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted && !pendingAdminNotificationId.isEmpty()) {
                        postAdminNotification(
                                pendingAdminNotificationId,
                                pendingAdminNotificationTitle,
                                pendingAdminNotificationMessage
                        );
                        pendingAdminNotificationId = "";
                        pendingAdminNotificationTitle = "";
                        pendingAdminNotificationMessage = "";
                    }
                }
        );

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean fine = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                    boolean coarse = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                    if (fine || coarse) {
                        syncCurrentLocationToFirebase();
                        ensureActiveRoomTracking();
                    }
                }
        );

        if (PermissionUtils.hasAnyLocation(this)) {
            syncCurrentLocationToFirebase();
        } else {
            try {
                locationPermLauncher.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                });
            } catch (Exception ignored) {}
        }

        headerPhoto = findViewById(R.id.header_profile_photo);
        title = findViewById(R.id.title);
        subtitle = findViewById(R.id.subtitle);
        medicalCard = findViewById(R.id.medical_card);
        medicalBlood = findViewById(R.id.tv_medical_blood);
        medicalAllergies = findViewById(R.id.tv_medical_allergies);
        medicalConditions = findViewById(R.id.tv_medical_conditions);

        View cardBlood = findViewById(R.id.card_medical_blood);
        View cardAllergies = findViewById(R.id.card_medical_allergies);
        View cardConditions = findViewById(R.id.card_medical_conditions);
        View.OnClickListener openSummaryListener = v -> showMedicalSummaryBottomSheet();
        if (cardBlood != null) cardBlood.setOnClickListener(openSummaryListener);
        if (cardAllergies != null) cardAllergies.setOnClickListener(openSummaryListener);
        if (cardConditions != null) cardConditions.setOnClickListener(openSummaryListener);
        if (medicalBlood != null) medicalBlood.setOnClickListener(openSummaryListener);
        if (medicalAllergies != null) medicalAllergies.setOnClickListener(openSummaryListener);
        if (medicalConditions != null) medicalConditions.setOnClickListener(openSummaryListener);

        phone = getIntent().getStringExtra("phone");

        MaterialButton btnSos = findViewById(R.id.btn_sos);
        btnInbox = findViewById(R.id.btn_inbox);
        inboxUnreadDot = findViewById(R.id.inbox_unread_dot);
        sosSheet = new SosBottomSheetController(this, new SosBottomSheetController.SosCallbacks() {
            @Override
            public void onCountdownFinished() {
                // Countdown 10 saat selesai: Buka skrin baharu SendingAlarmActivity di mana penggera dihantar secara rasmi
                Intent triggerIntent = new Intent(MainActivity.this, com.example.resqtap.sos.SendingAlarmActivity.class);
                if (lastKnownLat != 0.0 && lastKnownLng != 0.0) {
                    triggerIntent.putExtra(com.example.resqtap.sos.SendingAlarmActivity.EXTRA_LAT, lastKnownLat);
                    triggerIntent.putExtra(com.example.resqtap.sos.SendingAlarmActivity.EXTRA_LNG, lastKnownLng);
                }
                startActivity(triggerIntent);
            }

            @Override
            public void onSosCancelled() {
                // Dibatalkan semasa 10s countdown: Tiada sebarang isyarat pernah dihantar ke RTDB/admin
            }
        });

        android.view.View featureHospital = findViewById(R.id.feature_hospital);
        android.view.View featureContacts = findViewById(R.id.feature_contacts);
        android.view.View featureSupport = findViewById(R.id.feature_support);
        android.view.View featureLanguage = findViewById(R.id.feature_language);

        if (featureHospital != null) featureHospital.setOnClickListener(v -> startActivity(new Intent(this, NearbyHospitalActivity.class)));
        if (featureContacts != null) featureContacts.setOnClickListener(v -> startActivity(new Intent(this, EmergencyContactsListActivity.class)));
        if (featureSupport != null) featureSupport.setOnClickListener(v -> startActivity(new Intent(this, SupportActivity.class)));
        if (featureLanguage != null) featureLanguage.setOnClickListener(v -> startActivity(new Intent(this, ReportActivity.class)));

        btnSos.setOnClickListener(v -> {
            triggerSosFlow();
        });

        if (btnInbox != null) btnInbox.setOnClickListener(v -> startActivity(new Intent(this, NotificationsActivity.class)));

        ensureNotificationsPermission();
        try { NotificationHelper.createNotificationChannel(this); } catch (Exception ignored) {}
        try { NotificationHelper.ensureSosChannel(this); } catch (Exception ignored) {}
        try { NotificationUtils.ensureAdminChannel(this); } catch (Exception ignored) {}
        try { BatteryOptimizationHelper.promptOnce(this); } catch (Exception ignored) {}
        startAdminNotificationListener();

        refreshProfileUi();
        refreshInboxBadge();
        setupHighlights();
        setupMedicalNews();

        playEntranceAnimations();
        handleIncomingDeepLink(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingDeepLink(intent);
    }

    private void handleIncomingDeepLink(Intent intent) {
        if (intent == null) return;
        android.net.Uri data = intent.getData();
        if (data != null) {
            String scheme = data.getScheme();
            String host = data.getHost();
            String path = data.getPath();

            boolean isWebConnect = ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    && ("resqtap.web.app".equalsIgnoreCase(host) || "your-project.web.app".equalsIgnoreCase(host))
                    && path != null && path.startsWith("/connect");

            boolean isSchemeConnect = "resqtap".equalsIgnoreCase(scheme)
                    && ("connect".equalsIgnoreCase(host) || "friend".equalsIgnoreCase(host));

            if (isWebConnect || isSchemeConnect) {
                Intent connectIntent = new Intent(this, com.example.resqtap.friend.ScanQrActivity.class);
                connectIntent.putExtra("extra_connect_payload", data.toString());
                startActivity(connectIntent);
            }
        }
    }

    private void setupHighlights() {
        rvHighlights = findViewById(R.id.rv_highlights);
        if (rvHighlights == null) return;

        rvHighlights.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        highlightAdapter = new HighlightAdapter(this);
        rvHighlights.setAdapter(highlightAdapter);

        // Enable ultra smooth horizontal scrolling within vertical NestedScrollView
        rvHighlights.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            private float startX = 0f;
            private float startY = 0f;

            @Override
            public boolean onInterceptTouchEvent(RecyclerView rv, android.view.MotionEvent e) {
                switch (e.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        startX = e.getX();
                        startY = e.getY();
                        break;
                    case android.view.MotionEvent.ACTION_MOVE:
                        float dx = Math.abs(e.getX() - startX);
                        float dy = Math.abs(e.getY() - startY);
                        if (dx > dy && dx > 8f) {
                            if (rv.getParent() != null) {
                                rv.getParent().requestDisallowInterceptTouchEvent(true);
                            }
                        } else if (dy > dx && dy > 8f) {
                            if (rv.getParent() != null) {
                                rv.getParent().requestDisallowInterceptTouchEvent(false);
                            }
                        }
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        if (rv.getParent() != null) {
                            rv.getParent().requestDisallowInterceptTouchEvent(false);
                        }
                        break;
                }
                return false;
            }

            @Override
            public void onTouchEvent(RecyclerView rv, android.view.MotionEvent e) {}

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
        });

        loadHighlightsFromSupabase();
    }

    private void loadHighlightsFromSupabase() {
        com.example.resqtap.supabase.SupabaseManager.getInstance().getHighlights(new com.example.resqtap.supabase.SupabaseManager.Callback<org.json.JSONArray>() {
            @Override
            public void onSuccess(org.json.JSONArray sResult) {
                java.util.List<HighlightItem> sList = new java.util.ArrayList<>();
                if (sResult != null && sResult.length() > 0) {
                    for (int i = 0; i < sResult.length(); i++) {
                        try {
                            org.json.JSONObject obj = sResult.getJSONObject(i);
                            String id = obj.optString("id", "hl_" + i);
                            String title = obj.optString("title", "");
                            String imgUrl = obj.optString("image_url", "");
                            String actUrl = obj.optString("action_url", "");
                            int ord = obj.optInt("display_order", i + 1);
                            boolean act = obj.optBoolean("active", true);
                            if (act) {
                                sList.add(new HighlightItem(id, title, imgUrl, actUrl, true, System.currentTimeMillis(), ord));
                            }
                        } catch (Exception ignored) {}
                    }
                }
                if (sList.isEmpty()) {
                    sList.add(new HighlightItem("hl_cpr_2026", "Latihan CPR & Pertolongan Cemas Komuniti 2026", R.drawable.img_highlight_1, "https://www.moh.gov.my"));
                    sList.add(new HighlightItem("hl_derma_darah", "Kempen Derma Darah Selamatkan Nyawa (PDN)", R.drawable.img_highlight_2, "https://pdn.gov.my"));
                    sList.add(new HighlightItem("hl_peka_b40", "Skim Saringan Kesihatan Percuma PeKa B40", R.drawable.img_highlight_3, "https://pekab40.com.my"));
                }
                sortAndSubmitHighlights(sList);
            }

            @Override
            public void onError(Exception error) {
                java.util.List<HighlightItem> def = new java.util.ArrayList<>();
                def.add(new HighlightItem("hl_cpr_2026", "Latihan CPR & Pertolongan Cemas Komuniti 2026", R.drawable.img_highlight_1, "https://www.moh.gov.my"));
                def.add(new HighlightItem("hl_derma_darah", "Kempen Derma Darah Selamatkan Nyawa (PDN)", R.drawable.img_highlight_2, "https://pdn.gov.my"));
                def.add(new HighlightItem("hl_peka_b40", "Skim Saringan Kesihatan Percuma PeKa B40", R.drawable.img_highlight_3, "https://pekab40.com.my"));
                sortAndSubmitHighlights(def);
            }
        });
    }

    private void sortAndSubmitHighlights(java.util.List<HighlightItem> list) {
        if (list == null) return;
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.List<HighlightItem> clean = new java.util.ArrayList<>();
        for (HighlightItem it : list) {
            String key = (it.getTitle() != null ? it.getTitle().trim().toLowerCase() : "");
            if (!key.isEmpty() && seen.contains(key)) continue;
            if (!key.isEmpty()) seen.add(key);
            clean.add(it);
        }

        java.util.Collections.sort(clean, (a, b) -> {
            int cmp = Integer.compare(a.getOrder(), b.getOrder());
            if (cmp != 0) return cmp;
            return Long.compare(b.getCreatedAt(), a.getCreatedAt());
        });

        if (highlightAdapter != null) {
            highlightAdapter.submitList(clean);
        }
    }

    private void setupMedicalNews() {
        rvMedicalNews = findViewById(R.id.rv_medical_news);
        newsProgress = findViewById(R.id.news_progress);
        android.view.View btnSeeAll = findViewById(R.id.btn_see_all_news);

        if (rvMedicalNews != null) {
            rvMedicalNews.setLayoutManager(new LinearLayoutManager(this));
            medicalNewsAdapter = new MedicalNewsAdapter(this);
            rvMedicalNews.setAdapter(medicalNewsAdapter);
        }

        if (btnSeeAll != null) {
            btnSeeAll.setOnClickListener(v -> {
                Intent intent = new Intent(this, MedicalNewsActivity.class);
                startActivity(intent);
            });
        }

        loadMedicalNews();
    }

    private void loadMedicalNews() {
        if (newsProgress != null) newsProgress.setVisibility(android.view.View.VISIBLE);
        MedicalNewsFetcher.fetchMedicalNews(5, new MedicalNewsFetcher.NewsCallback() {
            @Override
            public void onSuccess(java.util.List<MedicalNewsItem> newsList) {
                if (newsProgress != null) newsProgress.setVisibility(android.view.View.GONE);
                if (medicalNewsAdapter != null) {
                    medicalNewsAdapter.submitList(newsList);
                }
            }

            @Override
            public void onError(Exception e) {
                if (newsProgress != null) newsProgress.setVisibility(android.view.View.GONE);
            }
        });
    }



    // 1. ENTRANCE ANIMATIONS
    /** Mainkan animasi slaid dan pudar semasa kali pertama membuka dashboard. */
    private void playEntranceAnimations() {
        if (entranceAnimationPlayed) return;
        entranceAnimationPlayed = true;

        android.view.View headerCard = findViewById(R.id.header_card);
        if (headerCard != null) {
            Animation headerAnim = AnimationUtils.loadAnimation(this, R.anim.item_fade_slide_up);
            headerAnim.setStartOffset(80);
            headerCard.startAnimation(headerAnim);
        }

        android.view.View sosHint = findViewById(R.id.sos_hint);
        MaterialButton btnSos2 = findViewById(R.id.btn_sos);
        if (btnSos2 != null) {
            btnSos2.setAlpha(0f);
            btnSos2.setScaleX(0.7f);
            btnSos2.setScaleY(0.7f);
            btnSos2.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(400)
                    .setStartDelay(100)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
                    .withLayer()
                    .start();
        }
        if (sosHint != null) {
            sosHint.setAlpha(0f);
            sosHint.animate().alpha(1f).setDuration(350).setStartDelay(250).withLayer().start();
        }

        int[] cardIds = {R.id.feature_hospital, R.id.feature_contacts, R.id.feature_support, R.id.feature_language};
        long[] delays = {120, 180, 240, 300};
        for (int i = 0; i < cardIds.length; i++) {
            android.view.View card = findViewById(cardIds[i]);
            if (card == null) continue;
            card.setAlpha(0f);
            card.setTranslationY(40f);
            card.setScaleX(0.96f);
            card.setScaleY(0.96f);
            final int idx = i;
            card.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(320)
                    .setStartDelay(delays[idx])
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .withLayer()
                    .start();
        }

        android.view.View featuresTitle = findViewById(R.id.features_title);
        if (featuresTitle != null) {
            featuresTitle.setAlpha(0f);
            featuresTitle.animate().alpha(1f).setDuration(300).setStartDelay(100).withLayer().start();
        }
    }

    /** Aktiviti aktif dan sedia untuk interaksi pengguna. */
    @Override
    protected void onResume() {
        super.onResume();
        if (PermissionUtils.hasAnyLocation(this)) {
            syncCurrentLocationToFirebase();
        }
        checkActiveSosCase();
        refreshProfileUi();
        refreshInboxBadge();
        highlightPollHandler.removeCallbacks(highlightPollRunnable);
        highlightPollHandler.post(highlightPollRunnable);
        try { BatteryOptimizationHelper.promptOnce(this); } catch (Exception ignored) {}
        ensureActiveRoomTracking();
        startSosRingPulse();
        checkCheckinTrigger(getIntent());
    }

    private void syncCurrentLocationToFirebase() {
        if (!PermissionUtils.hasAnyLocation(this) || fusedLocationClient == null) return;
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) return;
        final String uid = u.getUid();
        final String rawName = UserPrefs.getName(this);
        final String name = (rawName == null || rawName.trim().isEmpty()) ? "User" : rawName.trim();
        final String photoUrl = String.valueOf(UserPrefs.getPhotoUrl(this) == null ? "" : UserPrefs.getPhotoUrl(this)).trim();
        final String photoB64 = String.valueOf(UserPrefs.getPhotoB64(this) == null ? "" : UserPrefs.getPhotoB64(this)).trim();
        final int bat = BatteryUtils.getBatteryPct(this);

        try {
            fusedLocationClient.getLastLocation().addOnSuccessListener(loc -> {
                if (loc != null) {
                    lastKnownLat = loc.getLatitude();
                    lastKnownLng = loc.getLongitude();
                    FirebaseRoomClient.publishLocationQueued("", uid, name, photoUrl, photoB64, lastKnownLat, lastKnownLng, bat);
                    com.example.resqtap.supabase.SupabaseManager.getInstance().updateLiveLocation(uid, lastKnownLat, lastKnownLng, bat);
                } else {
                    try {
                        fusedLocationClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
                                .addOnSuccessListener(curLoc -> {
                                    if (curLoc != null) {
                                        lastKnownLat = curLoc.getLatitude();
                                        lastKnownLng = curLoc.getLongitude();
                                        FirebaseRoomClient.publishLocationQueued("", uid, name, photoUrl, photoB64, lastKnownLat, lastKnownLng, bat);
                                        com.example.resqtap.supabase.SupabaseManager.getInstance().updateLiveLocation(uid, lastKnownLat, lastKnownLng, bat);
                                    } else {
                                        FirebaseRoomClient.publishLocationQueued("", uid, name, photoUrl, photoB64, 0.0, 0.0, bat);
                                        com.example.resqtap.supabase.SupabaseManager.getInstance().updateLiveLocation(uid, 0.0, 0.0, bat);
                                    }
                                });
                    } catch (SecurityException ignored) {
                        FirebaseRoomClient.publishLocationQueued("", uid, name, photoUrl, photoB64, 0.0, 0.0, bat);
                        com.example.resqtap.supabase.SupabaseManager.getInstance().updateLiveLocation(uid, 0.0, 0.0, bat);
                    }
                }
            }).addOnFailureListener(e -> {
                FirebaseRoomClient.publishLocationQueued("", uid, name, photoUrl, photoB64, 0.0, 0.0, bat);
                com.example.resqtap.supabase.SupabaseManager.getInstance().updateLiveLocation(uid, 0.0, 0.0, bat);
            });
        } catch (SecurityException ignored) {}
    }

    private void checkCheckinTrigger(Intent intent) {
        if (intent != null && (intent.getBooleanExtra("trigger_checkin", false) || intent.hasExtra("checkin"))) {
            intent.removeExtra("trigger_checkin");
            intent.removeExtra("checkin");
            com.example.resqtap.utils.EmergencyCheckinHelper.showCheckInDialog(this, new com.example.resqtap.utils.EmergencyCheckinHelper.CheckinCallback() {
                @Override
                public void onSafeSelected() {
                    cancelActiveSosIfAny();
                }

                @Override
                public void onDangerSelected() {
                    triggerSosFlow();
                }
            });
        }
    }

    private void cancelActiveSosIfAny() {
        sosCancelledWhilePending = true;
        stopWatchingSosCaseReservations();
        final String dev = UserPrefs.getOrCreateDeviceId(MainActivity.this);
        for (java.util.Map.Entry<String, String> e : new java.util.HashMap<>(activeSosIds).entrySet()) {
            if (!e.getKey().isEmpty() && !e.getValue().isEmpty()) {
                FirebaseRoomClient.cancelRoomSosQueued(e.getKey(), e.getValue(), dev);
            }
        }
        activeSosIds.clear();
        FirebaseUser cu = FirebaseAuth.getInstance().getCurrentUser();
        if (cu != null) {
            FirebaseRoomClient.cancelAllActiveSosForUser(cu.getUid(), dev);
        }
        if (sosSheet != null) {
            try {
                sosSheet.cancel();
            } catch (Exception ignored) {
            }
        }
    }

    private final java.util.Map<DatabaseReference, ValueEventListener> sosReservationListeners = new java.util.HashMap<>();

    private void watchSosCaseReservation(String roomCode, String sosAlertId) {
        if (roomCode == null || sosAlertId == null || roomCode.isEmpty() || sosAlertId.isEmpty()) return;
        DatabaseReference ref = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                .getReference("rooms")
                .child(roomCode.toUpperCase(java.util.Locale.ROOT))
                .child("sosAlerts")
                .child(sosAlertId);

        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot == null || !snapshot.exists()) return;
                Boolean served = snapshot.child("served").getValue(Boolean.class);
                String servedBy = snapshot.child("servedBy").getValue(String.class);

                // Jika admin reserve kes (served == true atau servedBy ada value)
                if ((served != null && served) || (servedBy != null && !servedBy.trim().isEmpty())) {
                    // Berhenti mendengar reservation listener yang lain untuk elak duplicate launch
                    stopWatchingSosCaseReservations();

                    // Tutup bottom sheet SOS jika sedang terbuka
                    if (sosSheet != null) {
                        try {
                            sosSheet.cancel();
                        } catch (Exception ignored) {}
                    }

                    // Bersihkan activeSosIds supaya tidak mengganggu sesi seterusnya
                    activeSosIds.clear();

                    // Simpan sesi aktif ke UserPrefs supaya kekal jika keluar masuk app
                    UserPrefs.setActiveSosProgress(MainActivity.this, roomCode, sosAlertId);

                    // Bawa pengguna ke SosProgressActivity!
                    com.example.resqtap.sos.SosProgressActivity.launch(MainActivity.this, roomCode, sosAlertId);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        };

        synchronized (sosReservationListeners) {
            sosReservationListeners.put(ref, listener);
        }
        ref.addValueEventListener(listener);
    }

    /**
     * Semak jika ada kes SOS aktif yang belum di-resolved oleh admin.
     * Jika ada, bawa user terus ke SosProgressActivity.
     */
    private void checkActiveSosCase() {
        if (UserPrefs.isSosProgressActive(this)) {
            String room = UserPrefs.getActiveSosProgressRoom(this);
            String alertId = UserPrefs.getActiveSosProgressAlert(this);
            if (!alertId.isEmpty()) {
                com.example.resqtap.sos.SosProgressActivity.launch(this, room, alertId);
                return;
            }
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        final String uid = user.getUid();

        // Semak di RTDB sekiranya ada kes SOS aktif yang sedang dikendalikan admin (served == true)
        FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                .getReference("userRooms")
                .child(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (isFinishing() || isDestroyed()) return;

                    java.util.List<String> roomsToCheck = new java.util.ArrayList<>();
                    if (snapshot != null && snapshot.exists()) {
                        for (DataSnapshot child : snapshot.getChildren()) {
                            if (child == null || child.getKey() == null) continue;
                            String roomCode = child.getKey().trim().toUpperCase(java.util.Locale.ROOT);
                            if (roomCode.length() >= 4) roomsToCheck.add(roomCode);
                        }
                    }
                    // Sentiasa semak DIRECT emergency room sekiranya pengguna tiada bilik
                    roomsToCheck.add("DIRECT");

                    for (String roomCode : roomsToCheck) {
                        FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                                .getReference("rooms")
                                .child(roomCode)
                                .child("sosAlerts")
                                .get()
                                .addOnSuccessListener(alertSnap -> {
                                    if (alertSnap == null || !alertSnap.exists() || isFinishing() || isDestroyed()) return;
                                    for (DataSnapshot alertChild : alertSnap.getChildren()) {
                                        if (alertChild == null || alertChild.getKey() == null) continue;
                                        String senderUid = String.valueOf(alertChild.child("senderUid").getValue() == null
                                                ? alertChild.child("fromUid").getValue() : alertChild.child("senderUid").getValue()).trim();
                                        if (!uid.equals(senderUid)) continue;

                                        String status = String.valueOf(alertChild.child("status").getValue() == null ? "active" : alertChild.child("status").getValue());
                                        boolean isCancelled = "cancelled".equalsIgnoreCase(status) || alertChild.child("cancelledAt").exists();
                                        Long stepVal = alertChild.child("progressStep").getValue(Long.class);
                                        int step = stepVal != null ? stepVal.intValue() : 1;
                                        boolean isResolved = "resolved".equalsIgnoreCase(status) || alertChild.child("resolvedAt").exists() || step >= 4;

                                        Boolean served = alertChild.child("served").getValue(Boolean.class);
                                        String servedBy = alertChild.child("servedBy").getValue(String.class);
                                        boolean isServed = (served != null && served) || (servedBy != null && !servedBy.trim().isEmpty());

                                        if (!isCancelled && !isResolved && isServed) {
                                            String alertId = alertChild.getKey();
                                            UserPrefs.setActiveSosProgress(MainActivity.this, roomCode, alertId);
                                            com.example.resqtap.sos.SosProgressActivity.launch(MainActivity.this, roomCode, alertId);
                                            return;
                                        }
                                    }
                                });
                    }
                });
    }

    private void stopWatchingSosCaseReservations() {
        synchronized (sosReservationListeners) {
            for (java.util.Map.Entry<DatabaseReference, ValueEventListener> entry : sosReservationListeners.entrySet()) {
                try {
                    entry.getKey().removeEventListener(entry.getValue());
                } catch (Exception ignored) {}
            }
            sosReservationListeners.clear();
        }
    }

    private void dispatchSosBlasting(String uid, String dev, String name, double lat, double lng) {
        // Grab all rooms this user is in; if no rooms, broadcast to DIRECT fallback room
        FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                .getReference("userRooms")
                .child(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (sosCancelledWhilePending) return;

                    java.util.List<String> roomCodes = new java.util.ArrayList<>();
                    if (snapshot != null && snapshot.exists()) {
                        for (DataSnapshot child : snapshot.getChildren()) {
                            if (child == null || child.getKey() == null) continue;
                            final String code = child.getKey().trim().toUpperCase(java.util.Locale.ROOT);
                            if (!code.isEmpty()) {
                                roomCodes.add(code);
                            }
                        }
                    }

                    // Jika pengguna tiada sebarang room, hantar SOS ke fallback emergency channel DIRECT
                    if (roomCodes.isEmpty()) {
                        roomCodes.add("DIRECT");
                    }

                    for (String code : roomCodes) {
                        if (sosCancelledWhilePending) return;
                        FirebaseRoomClient.sendRoomSosQueued(code, uid, dev, name, lat, lng, id -> {
                            String sosId = String.valueOf(id == null ? "" : id).trim();
                            if (sosId.isEmpty()) return;
                            if (sosCancelledWhilePending) {
                                // User cancelled before this ID arrived, kill it immediately
                                FirebaseRoomClient.cancelRoomSosQueued(code, sosId, dev);
                            } else {
                                activeSosIds.put(code, sosId);
                                // Listen for admin reservation (served: true)
                                watchSosCaseReservation(code, sosId);
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> {
                    if (sosCancelledWhilePending) return;
                    // Fallback jika query userRooms gagal
                    FirebaseRoomClient.sendRoomSosQueued("DIRECT", uid, dev, name, lat, lng, id -> {
                        String sosId = String.valueOf(id == null ? "" : id).trim();
                        if (sosId.isEmpty()) return;
                        if (sosCancelledWhilePending) {
                            FirebaseRoomClient.cancelRoomSosQueued("DIRECT", sosId, dev);
                        } else {
                            activeSosIds.put("DIRECT", sosId);
                            watchSosCaseReservation("DIRECT", sosId);
                        }
                    });
                });
    }

    /** Tekan SOS: sheet muncul, onSosStarted akan broadcast ke semua room atau DIRECT fallback. */
    private void triggerSosFlow() {
        if (sosSheet == null) return;
        FirebaseUser cu = FirebaseAuth.getInstance().getCurrentUser();
        if (cu == null) {
            startActivity(new Intent(this, com.example.resqtap.auth.LoginActivity.class));
            return;
        }
        sosSheet.show();
    }

    private void ensureNotificationsPermission() {
        if (android.os.Build.VERSION.SDK_INT < 33) return;
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) return;
            notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        } catch (Exception ignored) {
        }
    }

    private void startAdminNotificationListener() {
        if (adminNotificationListener != null) return;
        adminNotificationRef = FirebaseDatabase
                .getInstance(FirebaseRoomClient.DATABASE_URL)
                .getReference("broadcastNotifications/current");
        adminNotificationListener = new ValueEventListener() {
            /** Callback apabila data Firebase Realtime Database berubah. */
    @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                Boolean activeValue = snapshot.child("active").getValue(Boolean.class);
                if (activeValue != null && !activeValue) return;

                String id = value(snapshot.child("id"));
                String message = value(snapshot.child("message"));
                if (id.isEmpty() || message.isEmpty()) return;

                if (!adminNotificationBaselineLoaded) {
                    adminNotificationBaselineLoaded = true;
                    String lastId = UserPrefs.getLastAdminNotificationId(MainActivity.this);
                    if (!id.equals(lastId)) {
                        UserPrefs.setLastAdminNotificationId(MainActivity.this, id);
                        UserPrefs.incrementUnreadNotifications(MainActivity.this);
                        refreshInboxBadge();
                    }
                    return;
                }

                String lastId = UserPrefs.getLastAdminNotificationId(MainActivity.this);
                if (id.equals(lastId)) {
                    refreshInboxBadge();
                    return;
                }

                String title = value(snapshot.child("title"));
                if (title.isEmpty()) title = "ResQTap";
                UserPrefs.setLastAdminNotificationId(MainActivity.this, id);
                UserPrefs.incrementUnreadNotifications(MainActivity.this);
                refreshInboxBadge();
                if (!hasNotificationPermission()) {
                    pendingAdminNotificationId = id;
                    pendingAdminNotificationTitle = title;
                    pendingAdminNotificationMessage = message;
                    ensureNotificationsPermission();
                    return;
                }

                postAdminNotification(id, title, message);
            }

            /** Callback sekiranya operasi Firebase dibatalkan atau gagal. */
    @Override
            public void onCancelled(DatabaseError error) {
                try {
                    android.util.Log.d("SOS_DEBUG", "Admin notification listener cancelled: " + error.getMessage());
                } catch (Exception ignored) {
                }
            }
        };
        adminNotificationRef.addValueEventListener(adminNotificationListener);
    }

    private void stopAdminNotificationListener() {
        if (adminNotificationRef != null && adminNotificationListener != null) {
            adminNotificationRef.removeEventListener(adminNotificationListener);
        }
        adminNotificationRef = null;
        adminNotificationListener = null;
    }

    private static String value(DataSnapshot snapshot) {
        Object raw = snapshot == null ? null : snapshot.getValue();
        return String.valueOf(raw == null ? "" : raw).trim();
    }

    /** Semak dan sahkan NotificationPermission. */
    private boolean hasNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < 33) return true;
        try {
            return androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void postAdminNotification(String id, String title, String message) {
        if (id == null || id.trim().isEmpty() || message == null || message.trim().isEmpty()) return;
        NotificationHelper.showLocalNotification(this, title, message);
    }

    private void refreshInboxBadge() {
        if (inboxUnreadDot != null) {
            boolean hasUnread = UserPrefs.getUnreadNotifications(this) > 0;
            inboxUnreadDot.setVisibility(hasUnread ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    /** Paparkan JoinOrCreateRoomDialog. */
    private void showJoinOrCreateRoomDialog() {
        try {
            int pad = (int) (20 * getResources().getDisplayMetrics().density);

            android.widget.LinearLayout root = new android.widget.LinearLayout(this);
            root.setOrientation(android.widget.LinearLayout.VERTICAL);
            root.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            root.setPadding(pad, pad, pad, pad);

            android.widget.TextView tv = new android.widget.TextView(this);
            tv.setText(R.string.dialog_room_required_message);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setTextAlignment(android.view.View.TEXT_ALIGNMENT_CENTER);
            tv.setTextSize(14);
            tv.setTextColor(getColor(R.color.text_primary));
            root.addView(tv, new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            com.google.android.material.button.MaterialButton ok = new com.google.android.material.button.MaterialButton(this);
            ok.setText(android.R.string.ok);
            ok.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.white));
            ok.setTextSize(15);
            ok.setTypeface(null, android.graphics.Typeface.BOLD);
            ok.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(this, R.color.filled_button_color)));
            ok.setCornerRadius((int) (22 * getResources().getDisplayMetrics().density));
            android.widget.LinearLayout.LayoutParams okLp = new android.widget.LinearLayout.LayoutParams(
                    (int) (130 * getResources().getDisplayMetrics().density),
                    (int) (48 * getResources().getDisplayMetrics().density)
            );
            okLp.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
            okLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            root.addView(ok, okLp);

            final androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                    .setView(root)
                    .create();
            ok.setOnClickListener(v -> dialog.dismiss());
            dialog.show();
        } catch (Exception ignored) {
        }
    }

    private void refreshProfileUi() {
        String name = UserPrefs.getName(this);
        String photoUri = UserPrefs.getPhotoUri(this);
        String photoB64 = UserPrefs.getPhotoB64(this);
        String bloodType = UserPrefs.getBloodType(this);
        String allergies = UserPrefs.getAllergies(this);
        String existingConditions = UserPrefs.getExistingConditions(this);
        if (existingConditions == null || existingConditions.trim().isEmpty()) {
            existingConditions = UserPrefs.getMedications(this);
        }
        boolean hideMedical = UserPrefs.isHideMedicalInfo(this);

        String shownName = (name == null || name.trim().isEmpty()) ? getString(R.string.profile_name_default) : name.trim();

        if (title != null) {
            title.setText(getString(R.string.home_greeting, shownName));
        }
        if (subtitle != null) {
            subtitle.setText(getString(R.string.home_welcome_back));
        }
        if (headerPhoto != null) {
            AvatarUtils.applyAvatar(headerPhoto, photoB64, photoUri, R.drawable.ic_avatar);
        }

        if (medicalCard != null) {
            medicalCard.setVisibility(hideMedical ? android.view.View.GONE : android.view.View.VISIBLE);
        }
        if (!hideMedical) {
            String bt = bloodType == null ? "" : bloodType.trim();
            if (bt.isEmpty()) bt = "-";
            if (medicalBlood != null) medicalBlood.setText(bt);

            String al = allergies == null ? "" : allergies.trim();
            if (medicalAllergies != null) {
                medicalAllergies.setText(formatMedicalCardSummary(al, R.string.medical_allergies_none));
            }

            String ex = existingConditions == null ? "" : existingConditions.trim();
            if (medicalConditions != null) {
                medicalConditions.setText(formatMedicalCardSummary(ex, R.string.medical_conditions_none));
            }
        }
    }

    private String formatMedicalCardSummary(String raw, int noneResId) {
        if (raw == null || raw.trim().isEmpty()) {
            return getString(noneResId);
        }
        java.util.List<String> items = parseMedicalItems(raw);
        if (items.isEmpty()) {
            return getString(noneResId);
        }
        if (items.size() == 1) {
            return items.get(0);
        }
        return items.get(0) + " (+" + (items.size() - 1) + ")";
    }

    private java.util.List<String> parseMedicalItems(String raw) {
        java.util.List<String> items = new java.util.ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return items;
        String[] parts = raw.split(",");
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty() && !trimmed.equalsIgnoreCase("None") && !trimmed.equalsIgnoreCase("Tiada")) {
                items.add(trimmed);
            }
        }
        return items;
    }

    private void showMedicalSummaryBottomSheet() {
        if (isFinishing() || isDestroyed()) return;
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.Theme_ResQTap_BottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_medical_summary, null);
        sheet.setContentView(view);

        if (sheet.getWindow() != null) {
            sheet.getWindow().setWindowAnimations(R.style.Animation_ResQTap_BottomSheetDialog);
            sheet.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Blood Type
        TextView tvBlood = view.findViewById(R.id.tv_summary_blood_type);
        String bloodType = UserPrefs.getBloodType(this);
        if (tvBlood != null) {
            tvBlood.setText((bloodType == null || bloodType.trim().isEmpty()) ? "-" : bloodType.trim());
        }

        // Allergies
        String rawAllergies = UserPrefs.getAllergies(this);
        java.util.List<String> allergyList = parseMedicalItems(rawAllergies);
        TextView tvBadgeAllergies = view.findViewById(R.id.tv_badge_allergies_count);
        TextView tvAllergiesNone = view.findViewById(R.id.tv_allergies_none);
        com.google.android.material.chip.ChipGroup chipGroupAllergies = view.findViewById(R.id.chip_group_allergies);

        if (tvBadgeAllergies != null) {
            tvBadgeAllergies.setText(allergyList.size() + (allergyList.size() == 1 ? " Item" : " Items"));
        }
        if (allergyList.isEmpty()) {
            if (tvAllergiesNone != null) tvAllergiesNone.setVisibility(View.VISIBLE);
            if (chipGroupAllergies != null) chipGroupAllergies.setVisibility(View.GONE);
        } else {
            if (tvAllergiesNone != null) tvAllergiesNone.setVisibility(View.GONE);
            if (chipGroupAllergies != null) {
                chipGroupAllergies.setVisibility(View.VISIBLE);
                chipGroupAllergies.removeAllViews();
                for (String item : allergyList) {
                    TextView badge = new TextView(this);
                    badge.setText(item);
                    badge.setTextColor(android.graphics.Color.WHITE);
                    badge.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f);
                    badge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    
                    float density = getResources().getDisplayMetrics().density;
                    int padH = (int) (10 * density);
                    int padV = (int) (4.5f * density);
                    badge.setPadding(padH, padV, padH, padV);

                    android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                    gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                    gd.setColor(android.graphics.Color.parseColor("#EA580C")); // Solid vibrant orange like [Hot]
                    gd.setCornerRadius(6.5f * density);
                    badge.setBackground(gd);

                    chipGroupAllergies.addView(badge);
                }
            }
        }

        // Conditions
        String rawConditions = UserPrefs.getExistingConditions(this);
        if (rawConditions == null || rawConditions.trim().isEmpty()) {
            rawConditions = UserPrefs.getMedications(this);
        }
        java.util.List<String> conditionList = parseMedicalItems(rawConditions);
        TextView tvBadgeConditions = view.findViewById(R.id.tv_badge_conditions_count);
        TextView tvConditionsNone = view.findViewById(R.id.tv_conditions_none);
        com.google.android.material.chip.ChipGroup chipGroupConditions = view.findViewById(R.id.chip_group_conditions);

        if (tvBadgeConditions != null) {
            tvBadgeConditions.setText(conditionList.size() + (conditionList.size() == 1 ? " Item" : " Items"));
        }
        if (conditionList.isEmpty()) {
            if (tvConditionsNone != null) tvConditionsNone.setVisibility(View.VISIBLE);
            if (chipGroupConditions != null) chipGroupConditions.setVisibility(View.GONE);
        } else {
            if (tvConditionsNone != null) tvConditionsNone.setVisibility(View.GONE);
            if (chipGroupConditions != null) {
                chipGroupConditions.setVisibility(View.VISIBLE);
                chipGroupConditions.removeAllViews();
                for (String item : conditionList) {
                    TextView badge = new TextView(this);
                    badge.setText(item);
                    badge.setTextColor(android.graphics.Color.WHITE);
                    badge.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f);
                    badge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

                    float density = getResources().getDisplayMetrics().density;
                    int padH = (int) (10 * density);
                    int padV = (int) (4.5f * density);
                    badge.setPadding(padH, padV, padH, padV);

                    android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                    gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                    gd.setColor(android.graphics.Color.parseColor("#2563EB")); // Solid vibrant blue like [Beta]
                    gd.setCornerRadius(6.5f * density);
                    badge.setBackground(gd);

                    chipGroupConditions.addView(badge);
                }
            }
        }

        // Close Header Button
        View btnCloseHeader = view.findViewById(R.id.btn_close_header);
        if (btnCloseHeader != null) btnCloseHeader.setOnClickListener(v -> sheet.dismiss());

        sheet.show();
    }

    /** Aktiviti dijeda sementara. */
    @Override
    protected void onPause() {
        super.onPause();
        highlightPollHandler.removeCallbacks(highlightPollRunnable);
        MaterialButton btnSos = findViewById(R.id.btn_sos);
        if (btnSos != null) stopSosPulse(btnSos);
        stopSosRingPulse();
    }

    // SEKSYEN: ONDESTROY
    /** Pembersihan memori, buang listener, dan tutup sambungan. */
    @Override
    protected void onDestroy() {
        highlightPollHandler.removeCallbacks(highlightPollRunnable);
        stopWatchingSosCaseReservations();
        stopAdminNotificationListener();
        super.onDestroy();
    }

    private void ensureActiveRoomTracking() {
        try {
            activeRoomCode = String.valueOf(UserPrefs.getActiveRoomCode(this) == null ? "" : UserPrefs.getActiveRoomCode(this)).trim();
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u == null) return;
            String uid = u.getUid();

            if (activeRoomCode.isEmpty()) {
                SosServiceStarter.start(MainActivity.this, "");
                restoreActiveRoomIfMissing(uid);
                return;
            }

            FirebaseRoomClient.verifyUserInRoom(uid, activeRoomCode, isInRoom -> {
                if (isInRoom) {
                    SosServiceStarter.start(MainActivity.this, activeRoomCode);
                } else {
                    activeRoomCode = "";
                    UserPrefs.setActiveRoomCode(MainActivity.this, "");
                    SosServiceStarter.start(MainActivity.this, "");
                    restoreActiveRoomIfMissing(uid);
                }
            });
        } catch (Exception ignored) {
        }
    }

    private void restoreActiveRoomIfMissing(String uid) {
        if (restoringActiveRoom) return;
        restoringActiveRoom = true;
        FirebaseRoomClient.fetchMostRecentUserRoomCodeQueued(uid, code -> {
            restoringActiveRoom = false;
            String c = String.valueOf(code == null ? "" : code).trim();
            if (c.isEmpty()) return;
            try {
                UserPrefs.setActiveRoomCode(MainActivity.this, c);
            } catch (Exception ignored) {
            }
            runOnUiThread(this::ensureActiveRoomTracking);
        });
    }

    private void startSosPulse(MaterialButton btn) {
        stopSosPulse(btn);
        PropertyValuesHolder sx = PropertyValuesHolder.ofFloat("scaleX", 1f, 1.08f);
        PropertyValuesHolder sy = PropertyValuesHolder.ofFloat("scaleY", 1f, 1.08f);
        sosPulseAnimator = ObjectAnimator.ofPropertyValuesHolder(btn, sx, sy);
        sosPulseAnimator.setDuration(450);
        sosPulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        sosPulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        sosPulseAnimator.start();
    }

    private void stopSosPulse(MaterialButton btn) {
        if (sosPulseAnimator != null) {
            sosPulseAnimator.cancel();
            sosPulseAnimator = null;
        }
        btn.setScaleX(1f);
        btn.setScaleY(1f);
    }

    private void startSosRingPulse() {
        android.view.View ringOuter = findViewById(R.id.sos_ring_outer);
        android.view.View ringMid = findViewById(R.id.sos_ring_mid);
        android.view.View ringGlow = findViewById(R.id.sos_ring_glow);

        if (ringOuter != null && sosRingOuterAnimator == null) {
            ringOuter.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);
            try {
                sosRingOuterAnimator = AnimatorInflater.loadAnimator(this, R.animator.sos_ring_pulse_outer);
                sosRingOuterAnimator.setTarget(ringOuter);
                sosRingOuterAnimator.start();
            } catch (Exception ignored) {

                ObjectAnimator scaleX = ObjectAnimator.ofFloat(ringOuter, "scaleX", 1f, 1.22f);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(ringOuter, "scaleY", 1f, 1.22f);
                ObjectAnimator alpha = ObjectAnimator.ofFloat(ringOuter, "alpha", 0.75f, 0.35f);
                for (ObjectAnimator a : new ObjectAnimator[]{scaleX, scaleY, alpha}) {
                    a.setDuration(1600);
                    a.setRepeatCount(ValueAnimator.INFINITE);
                    a.setRepeatMode(ValueAnimator.REVERSE);
                }
                AnimatorSet set = new AnimatorSet();
                set.playTogether(scaleX, scaleY, alpha);
                set.start();
                sosRingOuterAnimator = set;
            }
        }

        if (ringMid != null && sosRingMidAnimator == null) {
            ringMid.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);
            try {
                sosRingMidAnimator = AnimatorInflater.loadAnimator(this, R.animator.sos_ring_pulse_mid);
                sosRingMidAnimator.setTarget(ringMid);
                sosRingMidAnimator.start();
            } catch (Exception ignored) {

                ObjectAnimator scaleX = ObjectAnimator.ofFloat(ringMid, "scaleX", 1f, 1.12f);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(ringMid, "scaleY", 1f, 1.12f);
                ObjectAnimator alpha = ObjectAnimator.ofFloat(ringMid, "alpha", 0.85f, 0.45f);
                for (ObjectAnimator a : new ObjectAnimator[]{scaleX, scaleY, alpha}) {
                    a.setDuration(1600);
                    a.setRepeatCount(ValueAnimator.INFINITE);
                    a.setRepeatMode(ValueAnimator.REVERSE);
                    a.setStartDelay(200);
                }
                AnimatorSet set = new AnimatorSet();
                set.playTogether(scaleX, scaleY, alpha);
                set.start();
                sosRingMidAnimator = set;
            }
        }

        if (ringGlow != null && sosGlowAnimator == null) {
            ringGlow.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);
            PropertyValuesHolder gsX = PropertyValuesHolder.ofFloat("scaleX", 1f, 1.08f);
            PropertyValuesHolder gsY = PropertyValuesHolder.ofFloat("scaleY", 1f, 1.08f);
            PropertyValuesHolder gAlpha = PropertyValuesHolder.ofFloat("alpha", 0.35f, 0.18f);
            sosGlowAnimator = ObjectAnimator.ofPropertyValuesHolder(ringGlow, gsX, gsY, gAlpha);
            sosGlowAnimator.setDuration(2000);
            sosGlowAnimator.setRepeatCount(ValueAnimator.INFINITE);
            sosGlowAnimator.setRepeatMode(ValueAnimator.REVERSE);
            sosGlowAnimator.start();
        }
    }

    private void stopSosRingPulse() {
        if (sosRingOuterAnimator != null) {
            sosRingOuterAnimator.cancel();
            sosRingOuterAnimator = null;
        }
        if (sosRingMidAnimator != null) {
            sosRingMidAnimator.cancel();
            sosRingMidAnimator = null;
        }
        if (sosGlowAnimator != null) {
            sosGlowAnimator.cancel();
            sosGlowAnimator = null;
        }

        android.view.View ringOuter = findViewById(R.id.sos_ring_outer);
        android.view.View ringMid = findViewById(R.id.sos_ring_mid);
        android.view.View ringGlow = findViewById(R.id.sos_ring_glow);
        if (ringOuter != null) { ringOuter.setScaleX(1f); ringOuter.setScaleY(1f); ringOuter.setLayerType(android.view.View.LAYER_TYPE_NONE, null); }
        if (ringMid != null) { ringMid.setScaleX(1f); ringMid.setScaleY(1f); ringMid.setLayerType(android.view.View.LAYER_TYPE_NONE, null); }
        if (ringGlow != null) { ringGlow.setScaleX(1f); ringGlow.setScaleY(1f); ringGlow.setLayerType(android.view.View.LAYER_TYPE_NONE, null); }
    }
}

