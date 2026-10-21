package com.example.resqtap.app;
import com.example.resqtap.R;

import com.example.resqtap.auth.ForgotPasswordActivity;
import com.example.resqtap.auth.LoginActivity;
import com.example.resqtap.auth.RegisterActivity;
import com.example.resqtap.chat.ChatActivity;
import com.example.resqtap.chat.LivechatActivity;
import com.example.resqtap.chat.QuickMessageActivity;
import com.example.resqtap.friend.FriendsActivity;
import com.example.resqtap.home.MainActivity;
import com.example.resqtap.profile.ProfileActivity;
import com.example.resqtap.room.FirebaseRoomClient;
import com.example.resqtap.room.RoomHubActivity;
import com.example.resqtap.room.RoomListActivity;
import com.example.resqtap.sos.SosServiceStarter;
import com.example.resqtap.utils.LocaleUtils;
import com.example.resqtap.utils.UserPrefs;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.shape.RelativeCornerSize;
import com.google.android.material.shape.ShapeAppearanceModel;


/**
 * BaseActivity
 * Aktiviti asas (Base Activity) untuk setup Locale bahasa, animasi skrin, dan global UI.
 */
public abstract class BaseActivity extends AppCompatActivity {
    private static final String CHAT_FAB_PREFS = "ResQTap_chat_fab_v2";
    private static final String CHAT_FAB_SIDE = "side";
    private static final String CHAT_FAB_CENTER_Y = "center_y";
    private static final int CHAT_FAB_SIDE_RIGHT = 1;
    private static final int CHAT_FAB_SIDE_LEFT = 0;

    private DatabaseReference supportClaimRef;
    private ValueEventListener supportClaimListener;
    private boolean supportClaimBaselineLoaded = false;
    private long lastSupportClaimedAt = 0L;
    private android.widget.ImageButton globalChatFab;

    @Override
    protected void attachBaseContext(Context newBase) {
        Context wrapped = LocaleUtils.wrap(newBase);
        super.attachBaseContext(wrapped == null ? newBase : wrapped);
    }

    /** Aktiviti aktif dan sedia untuk interaksi pengguna. */
    @Override
    protected void onResume() {
        super.onResume();
        com.example.resqtap.security.AppLockManager.checkLock(this);
        updateGlobalChatFab();


        try {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u == null) return;
            startSupportClaimListener(u);
            if (com.example.resqtap.call.IncomingCallManager.getInstance() != null) {
                com.example.resqtap.call.IncomingCallManager.getInstance().startListening(u.getUid());
            }
            String code = String.valueOf(UserPrefs.getActiveRoomCode(this) == null ? "" : UserPrefs.getActiveRoomCode(this)).trim();
            if (!code.isEmpty()) {
                com.example.resqtap.room.FirebaseRoomClient.verifyUserInRoom(u.getUid(), code, isInRoom -> {
                    if (isInRoom) {
                        SosServiceStarter.start(BaseActivity.this, code);
                    } else {
                        UserPrefs.setActiveRoomCode(BaseActivity.this, "");
                        SosServiceStarter.start(BaseActivity.this, "");
                    }
                });
            } else {
                SosServiceStarter.start(BaseActivity.this, "");
            }
        } catch (Exception ignored) {
        }
    }

    /** Aktiviti dijeda sementara. */
    @Override
    protected void onPause() {
        super.onPause();
        stopSupportClaimListener();
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        installGlobalChatFab();
        if (shouldAnimateContentIn()) animateContentIn();
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        installGlobalChatFab();
        if (shouldAnimateContentIn()) animateContentIn();
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        installGlobalChatFab();
        if (shouldAnimateContentIn()) animateContentIn();
    }

    protected boolean shouldAnimateContentIn() {
        return false;
    }

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
        applyFade();
    }

    @Override
    public void startActivity(Intent intent, @Nullable Bundle options) {
        super.startActivity(intent, options);
        applyFade();
    }

    @Override
    public void finish() {
        super.finish();
        applyPop();
    }

    private void applyFade() {
        try {
            overridePendingTransition(R.anim.resqtap_enter, R.anim.resqtap_exit);
        } catch (Exception ignored) {
        }
    }

    private void applyPop() {
        try {
            overridePendingTransition(R.anim.resqtap_pop_enter, R.anim.resqtap_pop_exit);
        } catch (Exception ignored) {
        }
    }

    /** Kendalikan animasi ContentIn. */
    private void animateContentIn() {
        ViewGroup content = findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout) || content.getChildCount() <= 0) return;
        View root = content.getChildAt(0);
        if (root == null || root == globalChatFab) return;
        root.setAlpha(0f);
        root.setScaleX(0.965f);
        root.setScaleY(0.965f);
        root.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withLayer()
                .start();
        staggerChildren(root);
    }

    private void staggerChildren(View root) {
        if (!(root instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) root;
        int animated = 0;
        for (int i = 0; i < group.getChildCount() && animated < 6; i++) {
            View child = group.getChildAt(i);
            if (child == null || child == globalChatFab || child.getVisibility() != View.VISIBLE) continue;
            child.setAlpha(0f);
            child.setTranslationY(baseDp(18));
            child.setScaleX(0.985f);
            child.setScaleY(0.985f);
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(280L)
                    .setStartDelay(50L + (animated * 30L))
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .withLayer()
                    .start();
            animated++;
        }
    }

    private void installGlobalChatFab() {
        if (globalChatFab != null) return;
        if (!canHostGlobalChatFab() || !isUserLoggedIn()) return;

        ViewGroup content = findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout)) return;

        android.widget.ImageButton fab = new android.widget.ImageButton(this);
        fab.setId(View.generateViewId());
        fab.setImageResource(R.drawable.ic_ai_sparkles);
        fab.setContentDescription(getString(R.string.ai_chat_fab));
        fab.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_fab_ai_gradient));
        fab.setElevation(baseDp(10));
        fab.setTranslationZ(baseDp(20));
        fab.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        fab.setPadding(baseDp(12), baseDp(12), baseDp(12), baseDp(12));
        fab.setAlpha(0f);
        fab.setScaleX(0.88f);
        fab.setScaleY(0.88f);
        fab.setOnClickListener(v -> {
            v.animate()
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .setDuration(80L)
                    .withEndAction(() -> {
                        v.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(120L)
                                .start();
                        if (this instanceof ChatActivity) {
                            ChatActivity chatAct = (ChatActivity) this;
                            if (chatAct.isLiveChatActive()) {
                                android.widget.Toast.makeText(this, R.string.livechat_active_cannot_reset, android.widget.Toast.LENGTH_SHORT).show();
                                return;
                            }
                            chatAct.startNewChatSession();
                            return;
                        }
                        Intent chatIntent = new Intent(this, ChatActivity.class);
                        chatIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(chatIntent);
                    })
                    .start();
        });

        int size = baseDp(56);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.setMargins(0, 0, baseDp(16), baseDp(122));
        ((FrameLayout) content).addView(fab, params);
        globalChatFab = fab;

        ViewCompat.setOnApplyWindowInsetsListener(fab, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
            if (lp != null) {
                lp.bottomMargin = systemBars.bottom + baseDp(106);
                lp.rightMargin = baseDp(16);
                v.setLayoutParams(lp);
            }
            return insets;
        });

        revealGlobalChatFab();
        updateGlobalChatFab();
    }

    /** Simpan atau hantar data GlobalChatFab. */
    private void updateGlobalChatFab() {
        if (!canHostGlobalChatFab() || !isUserLoggedIn()) {
            if (globalChatFab != null) globalChatFab.setVisibility(View.GONE);
            return;
        }
        if (globalChatFab == null) {
            installGlobalChatFab();
            return;
        }
        globalChatFab.setVisibility(View.VISIBLE);
        globalChatFab.bringToFront();
    }

    private void revealGlobalChatFab() {
        if (globalChatFab == null) return;
        globalChatFab.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(260L)
                .setInterpolator(new OvershootInterpolator(0.7f))
                .start();
    }

    private boolean canHostGlobalChatFab() {
        return (this instanceof MainActivity)
                || (this instanceof QuickMessageActivity)
                || (this instanceof RoomListActivity)
                || (this instanceof RoomHubActivity)
                || (this instanceof FriendsActivity)
                || (this instanceof ProfileActivity);
    }

    /** Semak dan sahkan UserLoggedIn. */
    private boolean isUserLoggedIn() {
        try {
            return FirebaseAuth.getInstance().getCurrentUser() != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private int baseDp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void startSupportClaimListener(FirebaseUser user) {
        // Pop-up "Agent has been claimed your ticket" dimatikan atas permintaan LO
    }

    private void stopSupportClaimListener() {
        try {
            if (supportClaimRef != null && supportClaimListener != null) {
                supportClaimRef.removeEventListener(supportClaimListener);
            }
        } catch (Exception ignored) {
        }
        supportClaimRef = null;
        supportClaimListener = null;
        supportClaimBaselineLoaded = false;
        lastSupportClaimedAt = 0L;
    }

    private long asLong(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(String.valueOf(value == null ? "" : value).trim());
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
