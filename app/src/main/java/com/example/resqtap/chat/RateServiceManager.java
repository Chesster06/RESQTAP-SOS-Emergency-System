package com.example.resqtap.chat;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.resqtap.R;
import com.example.resqtap.room.FirebaseRoomClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * RateServiceManager
 * Menguruskan paparan pop-up Rate Customer Service tepat 5 saat selepas admin menyelesaikan
 * sesi chat. Kad ini HANYA keluar SEKALI sahaja. Sebaik sahaja pengguna leret (swipe), tutup,
 * atau menilai, ia TIDAK AKAN keluar di mana-mana halaman lain lagi.
 */
public class RateServiceManager {
    private static final String TAG = "RateServiceManager";
    private static final String PREF_NAME = "ResQTap_rate_service_prefs";
    private static final String PREF_LAST_SHOWN_RESOLVED_AT = "last_shown_resolved_at";
    private static final String PREF_LAST_RATED_RESOLVED_AT = "last_rated_resolved_at";
    private static final String PREF_LAST_DISMISSED_RESOLVED_AT = "last_dismissed_resolved_at";
    public static final long DELAY_AFTER_RESOLVE_MS = 5000L; // 5 saat seperti yang diminta LO

    private static RateServiceManager instance;
    private final Application application;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WeakReference<Activity> currentActivityRef;
    private DatabaseReference supportMetaRef;
    private ValueEventListener supportMetaListener;
    private FirebaseAuth.AuthStateListener authStateListener;

    private Runnable pendingShowRunnable;
    private long scheduledResolvedAt = 0L;
    private Dialog activeDialog;

    private RateServiceManager(Application app) {
        this.application = app;
        registerActivityTracker();
        setupAuthListener();
    }

    public static synchronized void init(Application app) {
        if (instance == null) {
            instance = new RateServiceManager(app);
        }
    }

    public static synchronized RateServiceManager getInstance() {
        return instance;
    }

    /**
     * Jejaki Activity semasa yang sedang berada di foreground.
     */
    private void registerActivityTracker() {
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                currentActivityRef = new WeakReference<>(activity);
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                if (currentActivityRef != null && currentActivityRef.get() == activity) {
                    currentActivityRef = null;
                }
            }

            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}
            @Override
            public void onActivityStarted(@NonNull Activity activity) {}
            @Override
            public void onActivityStopped(@NonNull Activity activity) {}
            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                if (activeDialog != null && activeDialog.getOwnerActivity() == activity) {
                    try {
                        activeDialog.dismiss();
                    } catch (Exception ignored) {}
                    activeDialog = null;
                }
            }
        });
    }

    /**
     * Pantau status pengesahan pengguna untuk attach listener Firebase.
     */
    private void setupAuthListener() {
        authStateListener = auth -> {
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                attachSupportMetaListener(user.getUid());
            } else {
                detachSupportMetaListener();
            }
        };
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener);
    }

    /**
     * Dengar perubahan status sokongan pada Firebase Realtime Database.
     */
    private void attachSupportMetaListener(String uid) {
        detachSupportMetaListener();
        try {
            supportMetaRef = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                    .getReference("supportChats")
                    .child(uid)
                    .child("meta");

            supportMetaListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (!snapshot.exists()) return;

                    String status = String.valueOf(snapshot.child("status").getValue() == null ? "" : snapshot.child("status").getValue()).trim();
                    long resolvedAt = 0L;
                    Object resObj = snapshot.child("resolvedAt").getValue();
                    if (resObj instanceof Number) {
                        resolvedAt = ((Number) resObj).longValue();
                    }

                    boolean isResolved = "resolved".equalsIgnoreCase(status) || "closed".equalsIgnoreCase(status);
                    if (!isResolved || resolvedAt <= 0L) return;

                    // Semak sama ada sesi ini sudah keluar sekali, sudah dinilai, atau ditolak
                    if (isSessionAlreadyHandled(resolvedAt, snapshot)) {
                        return;
                    }

                    // Jadualkan popup keluar tepat 5 saat selepas admin end chat
                    scheduleRatingPrompt(resolvedAt);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            };
            supportMetaRef.addValueEventListener(supportMetaListener);
        } catch (Exception e) {
            Log.e(TAG, "Error attaching supportMetaListener: " + e.getMessage());
        }
    }

    private void detachSupportMetaListener() {
        if (supportMetaRef != null && supportMetaListener != null) {
            try {
                supportMetaRef.removeEventListener(supportMetaListener);
            } catch (Exception ignored) {}
        }
        supportMetaRef = null;
        supportMetaListener = null;
    }

    /**
     * Semak sama ada sesi resolved ini telahpun dipaparkan atau diselesaikan.
     * Sekiranya sudah pernah keluar SEKALI, elakkan daripada keluar lagi.
     */
    private boolean isSessionAlreadyHandled(long resolvedAt, DataSnapshot metaSnapshot) {
        SharedPreferences prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long lastShown = prefs.getLong(PREF_LAST_SHOWN_RESOLVED_AT, 0L);
        long lastRated = prefs.getLong(PREF_LAST_RATED_RESOLVED_AT, 0L);
        long lastDismissed = prefs.getLong(PREF_LAST_DISMISSED_RESOLVED_AT, 0L);

        if (resolvedAt <= lastShown || resolvedAt <= lastRated || resolvedAt <= lastDismissed) {
            return true;
        }

        if (metaSnapshot != null) {
            if (metaSnapshot.child("rating").exists() || metaSnapshot.child("ratedAt").exists()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Jadualkan pop-up Rate Service untuk muncul tepat 5 saat selepas status resolved dikesan.
     * Hanya boleh dijadualkan sekali sahaja untuk sesi yang sama.
     */
    public synchronized void scheduleRatingPrompt(long resolvedAt) {
        if (resolvedAt <= 0L) resolvedAt = System.currentTimeMillis();

        SharedPreferences prefs = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long lastShown = prefs.getLong(PREF_LAST_SHOWN_RESOLVED_AT, 0L);
        long lastRated = prefs.getLong(PREF_LAST_RATED_RESOLVED_AT, 0L);
        long lastDismissed = prefs.getLong(PREF_LAST_DISMISSED_RESOLVED_AT, 0L);
        if (resolvedAt <= lastShown || resolvedAt <= lastRated || resolvedAt <= lastDismissed) {
            return;
        }

        // Elak pendua jika sesi yang sama sudah dijadualkan
        if (scheduledResolvedAt == resolvedAt && pendingShowRunnable != null) {
            return;
        }

        scheduledResolvedAt = resolvedAt;
        if (pendingShowRunnable != null) {
            mainHandler.removeCallbacks(pendingShowRunnable);
        }

        final long targetResolvedAt = resolvedAt;
        pendingShowRunnable = () -> {
            pendingShowRunnable = null;
            scheduledResolvedAt = 0L;

            // Pastikan belum pernah ditunjukkan
            SharedPreferences sp = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            if (targetResolvedAt <= sp.getLong(PREF_LAST_SHOWN_RESOLVED_AT, 0L)
                    || targetResolvedAt <= sp.getLong(PREF_LAST_DISMISSED_RESOLVED_AT, 0L)
                    || targetResolvedAt <= sp.getLong(PREF_LAST_RATED_RESOLVED_AT, 0L)) {
                return;
            }

            Activity activity = currentActivityRef != null ? currentActivityRef.get() : null;
            if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                showRatingDialog(activity, targetResolvedAt);
            }
        };

        // Tunggu 5 saat selepas admin tamatkan chat
        mainHandler.postDelayed(pendingShowRunnable, DELAY_AFTER_RESOLVE_MS);
        Log.d(TAG, "Scheduled single rating prompt for resolvedAt=" + resolvedAt + " in " + DELAY_AFTER_RESOLVE_MS + "ms");
    }

    /**
     * Paparkan dialog kad Rate Service di atas Activity semasa dengan latar belakang gelap (Dim Scrim).
     * Rekod serta-merta bahawa ia telah keluar SEKALI agar tidak muncul lagi pada mana-mana halaman lain.
     */
    public void showRatingDialog(Activity activity, long resolvedAt) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        // Tandakan sesi ini telah keluar sekali sahaja!
        markShown(resolvedAt);

        // Tutup dialog lama jika masih ada
        if (activeDialog != null) {
            try {
                activeDialog.dismiss();
            } catch (Exception ignored) {}
            activeDialog = null;
        }

        try {
            Dialog dialog = new Dialog(activity);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setContentView(R.layout.dialog_rate_service);
            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(true);

            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                window.setGravity(Gravity.BOTTOM);

                // Latar belakang gelap (Dim Scrim) di belakang kad
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                window.setDimAmount(0.68f);

                // Animasi Swipe Up / Slide In dari bawah
                window.setWindowAnimations(R.style.Animation_ResQTap_BottomSheetDialog);
            }

            setupDialogViews(dialog, activity, resolvedAt);

            // Sekiranya dialog ditutup (dileret/diswipe, ditekan luar, atau back), rekod dismiss
            dialog.setOnDismissListener(d -> {
                markDismissed(resolvedAt);
                if (activeDialog == dialog) {
                    activeDialog = null;
                }
            });

            dialog.setOnCancelListener(d -> markDismissed(resolvedAt));

            dialog.show();
            activeDialog = dialog;
        } catch (Exception e) {
            Log.e(TAG, "Error showing rating dialog: " + e.getMessage());
        }
    }

    /**
     * Konfigurasi elemen visual, butang tutup, gesture swipe ke bawah, dan bintang penilaian.
     */
    private void setupDialogViews(Dialog dialog, Activity activity, long resolvedAt) {
        ImageButton btnClose = dialog.findViewById(R.id.btn_close_rate_card);
        TextView tvThankYou = dialog.findViewById(R.id.tv_rate_thank_you);
        View cardView = dialog.findViewById(R.id.card_rate_service);

        // Sokongan leret ke bawah (Swipe down to dismiss)
        if (cardView != null) {
            final float swipeThreshold = 55f * activity.getResources().getDisplayMetrics().density;
            cardView.setOnTouchListener(new View.OnTouchListener() {
                private float startY = 0f;
                private boolean isSwiping = false;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            startY = event.getRawY();
                            isSwiping = false;
                            return false;
                        case MotionEvent.ACTION_MOVE:
                            float deltaY = event.getRawY() - startY;
                            if (deltaY > 15f || isSwiping) {
                                isSwiping = true;
                                if (deltaY > 0) {
                                    v.setTranslationY(deltaY);
                                }
                                return true;
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            if (isSwiping) {
                                float finalDeltaY = event.getRawY() - startY;
                                if (finalDeltaY > swipeThreshold) {
                                    // Pengguna leret ke bawah: Sembunyikan kad dan jangan keluar di page lain lagi!
                                    markDismissed(resolvedAt);
                                    v.animate()
                                            .translationY(v.getHeight() + 250f)
                                            .alpha(0f)
                                            .setDuration(220)
                                            .withEndAction(() -> {
                                                try {
                                                    dialog.dismiss();
                                                } catch (Exception ignored) {}
                                            })
                                            .start();
                                } else {
                                    // Tarik semula ke kedudukan asal jika leretan tidak melepasi had
                                    v.animate().translationY(0f).setDuration(160).start();
                                }
                                return true;
                            }
                            break;
                    }
                    return false;
                }
            });
        }

        FrameLayout[] starButtons = new FrameLayout[5];
        ImageView[] starIcons = new ImageView[5];
        View[] starHighlights = new View[5];

        int[] btnIds = {R.id.btn_star_1, R.id.btn_star_2, R.id.btn_star_3, R.id.btn_star_4, R.id.btn_star_5};
        int[] iconIds = {R.id.star_img_1, R.id.star_img_2, R.id.star_img_3, R.id.star_img_4, R.id.star_img_5};
        int[] highlightIds = {R.id.star_highlight_1, R.id.star_highlight_2, R.id.star_highlight_3, R.id.star_highlight_4, R.id.star_highlight_5};

        for (int i = 0; i < 5; i++) {
            starButtons[i] = dialog.findViewById(btnIds[i]);
            starIcons[i] = dialog.findViewById(iconIds[i]);
            starHighlights[i] = dialog.findViewById(highlightIds[i]);

            final int rating = i + 1;
            final int index = i;
            if (starButtons[i] != null) {
                starButtons[i].setOnClickListener(v -> {
                    handleStarClick(dialog, activity, rating, index, starButtons, starIcons, starHighlights, tvThankYou, resolvedAt);
                });
            }
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                markDismissed(resolvedAt);
                dialog.dismiss();
            });
        }
    }

    /**
     * Kendalikan klik bintang, animasi pantulan, kemas kini ikon, dan simpan penilaian.
     */
    private void handleStarClick(Dialog dialog, Activity activity, int rating, int selectedIndex,
                                 FrameLayout[] starButtons, ImageView[] starIcons, View[] starHighlights,
                                 TextView tvThankYou, long resolvedAt) {
        int darkStarColor = ContextCompat.getColor(activity, R.color.text_primary);
        int lightStarColor = ContextCompat.getColor(activity, R.color.text_secondary);

        for (int j = 0; j < 5; j++) {
            if (starIcons[j] != null) {
                if (j < rating) {
                    starIcons[j].setImageResource(R.drawable.ic_star_filled_24);
                    starIcons[j].setImageTintList(ColorStateList.valueOf(darkStarColor));
                } else {
                    starIcons[j].setImageResource(R.drawable.ic_star_outline_24);
                    starIcons[j].setImageTintList(ColorStateList.valueOf(lightStarColor));
                }
            }
            if (starHighlights[j] != null) {
                starHighlights[j].setVisibility(j == selectedIndex ? View.VISIBLE : View.GONE);
            }
            if (starButtons[j] != null) {
                starButtons[j].setClickable(false);
            }
        }

        // Animasi pantulan (bouncing scale) pada bintang yang ditekan
        if (selectedIndex >= 0 && selectedIndex < 5 && starButtons[selectedIndex] != null) {
            View selectedBtn = starButtons[selectedIndex];
            selectedBtn.animate()
                    .scaleX(1.35f)
                    .scaleY(1.35f)
                    .setDuration(130)
                    .withEndAction(() -> selectedBtn.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(130)
                            .start())
                    .start();
        }

        // Paparkan teks ucapan terima kasih dengan fade in
        if (tvThankYou != null) {
            tvThankYou.setVisibility(View.VISIBLE);
            tvThankYou.setAlpha(0f);
            tvThankYou.animate().alpha(1f).setDuration(250).start();
        }

        // Simpan data rating ke Firebase & SharedPreferences
        saveRating(rating, resolvedAt);

        // Tutup dialog secara automatik selepas 2.2 saat
        mainHandler.postDelayed(() -> {
            try {
                dialog.dismiss();
            } catch (Exception ignored) {}
        }, 2200L);
    }

    /**
     * Rekod bahawa dialog ini telah keluar SEKALI sahaja untuk sesi ini.
     */
    private void markShown(long resolvedAt) {
        application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(PREF_LAST_SHOWN_RESOLVED_AT, resolvedAt)
                .apply();
        scheduledResolvedAt = 0L;
    }

    /**
     * Simpan rekod penilaian ke Firebase RTDB dan SharedPreferences.
     */
    private void saveRating(int rating, long resolvedAt) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String uid = user.getUid();

        try {
            DatabaseReference chatRef = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                    .getReference("supportChats")
                    .child(uid);

            Map<String, Object> ratingData = new HashMap<>();
            ratingData.put("rating", rating);
            ratingData.put("ratedAt", ServerValue.TIMESTAMP);

            chatRef.child("rating").setValue(ratingData);

            // Kekalkan status sebagai resolved pada meta supaya tidak sesekali dibuka semula di dashboard admin
            Map<String, Object> metaUpdates = new HashMap<>();
            metaUpdates.put("rating", rating);
            metaUpdates.put("ratedAt", ServerValue.TIMESTAMP);
            metaUpdates.put("status", "resolved");
            chatRef.child("meta").updateChildren(metaUpdates);

            // Simpan resolvedAt supaya tidak meminta penilaian semula untuk sesi ini
            application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(PREF_LAST_RATED_RESOLVED_AT, resolvedAt)
                    .putLong(PREF_LAST_SHOWN_RESOLVED_AT, resolvedAt)
                    .apply();

            scheduledResolvedAt = 0L;
        } catch (Exception e) {
            Log.e(TAG, "Error saving rating: " + e.getMessage());
        }
    }

    /**
     * Rekod bahawa pengguna menolak/menutup/leret (swipe) penilaian sesi ini.
     * Memastikan ia TIDAK AKAN keluar di mana-mana halaman lain lagi.
     */
    private void markDismissed(long resolvedAt) {
        application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(PREF_LAST_DISMISSED_RESOLVED_AT, resolvedAt)
                .putLong(PREF_LAST_SHOWN_RESOLVED_AT, resolvedAt)
                .apply();
        scheduledResolvedAt = 0L;
        if (pendingShowRunnable != null) {
            mainHandler.removeCallbacks(pendingShowRunnable);
            pendingShowRunnable = null;
        }
    }
}
