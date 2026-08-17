package com.example.resqtap.chat;
import com.example.resqtap.R;

import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.auth.LoginActivity;
import com.example.resqtap.room.FirebaseRoomClient;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ImageView;
import android.content.res.ColorStateList;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * ChatActivity
 * Pembantu keselamatan & kecemasan ResQTap (FAQ & Meja Bantuan).
 */
public class ChatActivity extends BaseActivity {
    private static final String TAG = "ResQTapAiChat";
    private static final long AI_TYPING_DELAY_MS = 1000L;
    private static final long TYPING_DOT_INTERVAL_MS = 420L;
    private static final long TYPEWRITER_INTERVAL_MS = 22L;
    private static final long CHAT_EXPIRE_AFTER_MS = 5 * 60 * 1000L;
    private static final String CHAT_PREFS = "ResQTap_ai_chat";
    private static final String CHAT_PREF_MESSAGES = "messages";
    private static final String CHAT_PREF_EXPIRES_AT = "expires_at";
    private static final String CHAT_PREF_SAVED_SESSIONS = "ai_chat_saved_sessions";
    private static final boolean USE_DATABASE_CHAT_STORE = false;


    private TextView headerTitle;
    private View badgeBeta;
    private View badgeLiveStatus;
    private View bannerLiveMsgAlert;
    private TextView tvBannerLiveText;
    private TextView tvChatDisclaimer;
    private View composerAttachmentStrip;
    private TextView composerAttachmentLabel;
    private View btnClearAttachment;
    private ImageButton btnCamera;
    private View bottomSuggestionsScroll;

    private ActivityResultLauncher<String[]> pickAttachmentLauncher;
    private ActivityResultLauncher<Void> takeCameraLauncher;
    private Uri selectedAttachmentUri;
    private byte[] selectedAttachmentBytes;
    private String selectedAttachmentName = "";
    private String selectedAttachmentMime = "";
    private long selectedAttachmentSize = 0L;

    // Livechat system variables
    private static final String PREF_ACTIVE_LIVECHAT = "active_livechat_session";
    private static final String PREF_LIVECHAT_START_TIME = "livechat_session_start_time";
    private boolean isLiveChatMode = false;
    private boolean isLiveAgentJoined = false;
    private long liveChatSessionStartTime = 0L;

    /** Semak status sama ada pengguna kini sedang berada dalam sesi Livechat aktif. */
    public boolean isLiveChatActive() {
        return isLiveChatMode;
    }
    private View chipLivechatView;
    private View cardLivechatView;
    private DatabaseReference liveChatRef;
    private Query liveMessagesQuery;
    private ValueEventListener liveMessagesListener;
    private ValueEventListener liveMetaListener;
    private final ArrayList<LiveMessageItem> liveMessages = new ArrayList<>();
    private boolean adminTypingActive = false;
    private View liveTypingIndicatorView;
    private TextView liveTypingDotsView;
    private int liveTypingDotsStep = 0;
    private Runnable liveTypingDotsRunnable;

    private final ArrayList<ChatMessage> messages = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private String currentSessionId = "";
    private String currentSessionTopic = "";

    private LinearLayout messagesContainer;
    private androidx.core.widget.NestedScrollView messagesScroll;
    private EditText inputView;
    private View sendButton;
    private View heroGreetingLayout;
    private View typingView;
    private TextView typingTextView;
    private TextView thinkingStatusText;
    private final ArrayList<String> thinkingSteps = new ArrayList<>();
    private int currentThinkingStepIndex = 0;
    private Runnable thinkingStepRunnable;
    private ValueAnimator sparkleSwapAnimator;
    private Runnable typingDotsRunnable;
    private Runnable pendingReplyRunnable;
    private Runnable typewriterRunnable;
    private Runnable expiryRunnable;
    private String pendingClientMessageId = "";
    private DatabaseReference aiChatRef;
    private Query messagesQuery;
    private ValueEventListener messagesListener;
    private ValueEventListener metaListener;
    private String currentUid = "";
    private boolean sending = false;
    private long typingStartedAt = 0L;
    private int typingDotsStep = 0;
    private int invalidInputCount = 0;
    private boolean awaitingLiveChatChoice = false;
    private boolean isShowingInitialWelcome = false;
    private long activityStartedAt = System.currentTimeMillis();

    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomInset = Math.max(systemBars.bottom, ime.bottom);
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomInset);
            return insets;
        });

        setupAttachmentLaunchers();

        ImageButton back = findViewById(R.id.btn_back);
        ImageButton btnNewChat = findViewById(R.id.btn_new_chat);
        ImageButton btnHistory = findViewById(R.id.btn_chat_history);
        btnCamera = findViewById(R.id.btn_attach_camera);
        headerTitle = findViewById(R.id.title);
        badgeBeta = findViewById(R.id.badge_beta);
        heroGreetingLayout = findViewById(R.id.hero_greeting_layout);
        messagesContainer = findViewById(R.id.chat_messages);
        messagesScroll = findViewById(R.id.chat_scroll);
        inputView = findViewById(R.id.chat_input);
        sendButton = findViewById(R.id.chat_send);

        badgeLiveStatus = findViewById(R.id.badge_live_status);
        bannerLiveMsgAlert = findViewById(R.id.banner_live_msg_alert);
        tvBannerLiveText = findViewById(R.id.tv_banner_live_text);
        tvChatDisclaimer = findViewById(R.id.tv_chat_disclaimer);
        composerAttachmentStrip = findViewById(R.id.composer_attachment_strip);
        composerAttachmentLabel = findViewById(R.id.composer_attachment_label);
        btnClearAttachment = findViewById(R.id.btn_clear_attachment);
        bottomSuggestionsScroll = findViewById(R.id.bottom_suggestions_scroll);

        if (back != null) back.setOnClickListener(v -> finish());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        if (btnNewChat != null) {
            btnNewChat.setOnClickListener(v -> {
                if (isLiveChatMode) {
                    Toast.makeText(this, R.string.livechat_active_cannot_reset, Toast.LENGTH_SHORT).show();
                    return;
                }
                startNewChatSession();
            });
        }
        if (btnHistory != null) btnHistory.setOnClickListener(v -> showRecentChatHistoryBottomSheet());
        if (btnCamera != null) btnCamera.setOnClickListener(v -> showAttachmentOptionsBottomSheet());
        if (btnClearAttachment != null) btnClearAttachment.setOnClickListener(v -> clearAttachmentSelection());

        setupSuggestionCards();

        chipLivechatView = findViewById(R.id.chip_switch_to_livechat);
        cardLivechatView = findViewById(R.id.card_prompt_livechat);
        if (cardLivechatView != null) cardLivechatView.setVisibility(View.VISIBLE);

        if (sendButton != null) sendButton.setOnClickListener(v -> onSendClicked());
        if (inputView != null) {
            inputView.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    onSendClicked();
                    return true;
                }
                return false;
            });
        }

        // Semak status sesi livechat dari cache lokal terlebih dahulu untuk kelancaran paparan
        boolean savedLivechatPref = chatPrefs().getBoolean(PREF_ACTIVE_LIVECHAT, false);
        long savedStartTime = chatPrefs().getLong(PREF_LIVECHAT_START_TIME, 0L);

        // Sentiasa pastikan rujukan livechat bersedia
        ensureLiveChatRef();

        if (savedLivechatPref) {
            // Sesi livechat aktif belum ditutup: jangan reset chat, kembali semula ke sesi yang masih aktif
            isLiveChatMode = true;
            liveChatSessionStartTime = savedStartTime;
            updateUiForLiveChat(true);
            setupLiveChatSystem();
        } else {
            isLiveChatMode = false;
            updateUiForLiveChat(false);
            activityStartedAt = System.currentTimeMillis();
            clearLocalChat();
            currentSessionId = "";
            currentSessionTopic = "";
            messages.clear();
            renderMessages();
            setupLiveChatSystem();
        }

        // Sahkan status sebenar daripada Firebase RTDB sebagai punca kebenaran mutlak (source of truth)
        if (liveChatRef != null) {
            liveChatRef.child("meta").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    if (isDestroyed() || isFinishing()) return;
                    String status = snapshot != null ? safe(snapshot.child("status").getValue(String.class)) : "";
                    boolean isClosed = snapshot == null || !snapshot.exists() || isLiveChatClosedStatus(status);
                    boolean isActive = !isClosed && (isLiveChatOpenStatus(status) || !status.isEmpty());

                    if (isActive) {
                        // Sesi livechat masih aktif di server! Jangan reset chat, kekalkan sesi ini
                        long sessionStart = snapshotLong(snapshot.child("sessionStartTime"));
                        if (sessionStart <= 0L) {
                            sessionStart = snapshotLong(snapshot.child("createdAt"));
                        }
                        if (sessionStart > 0L) {
                            liveChatSessionStartTime = sessionStart;
                        }
                        chatPrefs().edit()
                                .putBoolean(PREF_ACTIVE_LIVECHAT, true)
                                .putLong(PREF_LIVECHAT_START_TIME, liveChatSessionStartTime)
                                .apply();

                        if (!isLiveChatMode) {
                            isLiveChatMode = true;
                            updateUiForLiveChat(true);
                            setupLiveChatSystem();
                        }
                    } else if (isClosed) {
                        // Sesi ditamatkan secara sah di server (status resolved / closed / tiada rekod)
                        chatPrefs().edit()
                                .putBoolean(PREF_ACTIVE_LIVECHAT, false)
                                .putLong(PREF_LIVECHAT_START_TIME, 0L)
                                .apply();

                        if (isLiveChatMode) {
                            isLiveChatMode = false;
                            isLiveAgentJoined = false;
                            liveChatSessionStartTime = 0L;
                            updateUiForLiveChat(false);
                            detachLiveChatSystem();
                            startNewChatSession(true);
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {}
            });
        }

        setupAiChatStore();
    }

    /** Kemas kini antara muka pengguna (UI) mengikut mod Live Support atau AI Assistant. */
    private void updateUiForLiveChat(boolean isLive) {
        this.isLiveChatMode = isLive;
        if (headerTitle != null) {
            headerTitle.setText("AI Assistant");
        }
        if (badgeBeta != null) {
            badgeBeta.setVisibility(View.VISIBLE);
        }
        if (badgeLiveStatus != null) {
            badgeLiveStatus.setVisibility(View.GONE);
        }
        if (bottomSuggestionsScroll != null) {
            bottomSuggestionsScroll.setVisibility(isLive ? View.GONE : View.VISIBLE);
        }
        if (inputView != null) {
            inputView.setHint(isLive ? R.string.chat_input_hint_live : R.string.chat_input_hint_ai);
        }
        if (tvChatDisclaimer != null) {
            tvChatDisclaimer.setText(isLive ? R.string.chat_disclaimer_live : R.string.chat_disclaimer_unified);
        }
        if (chipLivechatView != null) {
            chipLivechatView.setVisibility(isLive ? View.GONE : View.VISIBLE);
        }
        if (cardLivechatView != null) {
            cardLivechatView.setVisibility(View.VISIBLE);
        }
    }

    /** Setup klik kad cadangan soalan (Suggestion Prompt Cards). */
    private void setupSuggestionCards() {
        View card1 = findViewById(R.id.card_prompt_1);
        View btn1 = findViewById(R.id.btn_prompt_1);
        View card2 = findViewById(R.id.card_prompt_2);
        View btn2 = findViewById(R.id.btn_prompt_2);
        View card3 = findViewById(R.id.card_prompt_3);
        View btn3 = findViewById(R.id.btn_prompt_3);
        View cardLivechat = findViewById(R.id.card_prompt_livechat);
        View btnLivechat = findViewById(R.id.btn_prompt_livechat);

        View.OnClickListener listener1 = v -> sendPresetPrompt("What to do in an emergency?");
        View.OnClickListener listener2 = v -> sendPresetPrompt("How does SOS beacon work?");
        View.OnClickListener listener3 = v -> sendPresetPrompt("How to add emergency contacts?");
        View.OnClickListener listenerLivechat = v -> triggerLiveChatSupport();

        if (card1 != null) card1.setOnClickListener(listener1);
        if (btn1 != null) btn1.setOnClickListener(listener1);
        if (card2 != null) card2.setOnClickListener(listener2);
        if (btn2 != null) btn2.setOnClickListener(listener2);
        if (card3 != null) card3.setOnClickListener(listener3);
        if (btn3 != null) btn3.setOnClickListener(listener3);
        if (cardLivechat != null) cardLivechat.setOnClickListener(listenerLivechat);
        if (btnLivechat != null) btnLivechat.setOnClickListener(listenerLivechat);

        View chipBeacon = findViewById(R.id.chip_prompt_beacon);
        View chipGeofence = findViewById(R.id.chip_prompt_geofence);
        View chipRoom = findViewById(R.id.chip_prompt_room);
        View chipHospital = findViewById(R.id.chip_prompt_hospital);
        View chipLivechat = findViewById(R.id.chip_switch_to_livechat);

        if (chipBeacon != null) chipBeacon.setOnClickListener(v -> sendPresetPrompt("How does SOS Beacon work?"));
        if (chipGeofence != null) chipGeofence.setOnClickListener(v -> sendPresetPrompt("How to set Safe Zones?"));
        if (chipRoom != null) chipRoom.setOnClickListener(v -> sendPresetPrompt("How to join a Room?"));
        if (chipHospital != null) chipHospital.setOnClickListener(v -> sendPresetPrompt("Find nearby hospitals"));
        if (chipLivechat != null) chipLivechat.setOnClickListener(listenerLivechat);
    }

    /** Cetus permintaan perbualan live support bersama admin. */
    private void triggerLiveChatSupport() {
        if (isLiveChatMode) {
            ensureLiveChatRef();
            if (liveChatRef != null) {
                liveChatRef.child("meta").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String status = snapshot != null ? safe(snapshot.child("status").getValue(String.class)) : "";
                        boolean trulyActive = snapshot != null && snapshot.exists() && isLiveChatOpenStatus(status);
                        if (trulyActive) {
                            Toast.makeText(ChatActivity.this, R.string.livechat_active_cannot_reset, Toast.LENGTH_SHORT).show();
                        } else {
                            isLiveChatMode = false;
                            isLiveAgentJoined = false;
                            liveChatSessionStartTime = 0L;
                            chatPrefs().edit()
                                    .putBoolean(PREF_ACTIVE_LIVECHAT, false)
                                    .putLong(PREF_LIVECHAT_START_TIME, 0L)
                                    .apply();
                            updateUiForLiveChat(false);
                            sendPresetPrompt("Talk to Agent");
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        sendPresetPrompt("Talk to Agent");
                    }
                });
                return;
            }
            Toast.makeText(this, R.string.livechat_active_cannot_reset, Toast.LENGTH_SHORT).show();
            return;
        }
        sendPresetPrompt("Talk to Agent");
    }

    /** Hantar soalan cadangan terus ke chat. */
    private void sendPresetPrompt(String query) {
        if (inputView != null) {
            inputView.setText(query);
            sendMessage();
        }
    }

    /** Mulakan sesi perbualan baharu (Wipe Firebase and local storage). */
    public void startNewChatSession() {
        startNewChatSession(false);
    }

    public void startNewChatSession(boolean force) {
        if (isLiveChatMode && !force) {
            Toast.makeText(this, R.string.livechat_active_cannot_reset, Toast.LENGTH_SHORT).show();
            return;
        }
        if (isLiveChatMode) {
            isLiveChatMode = false;
            isLiveAgentJoined = false;
            liveChatSessionStartTime = 0L;
            chatPrefs().edit()
                    .putBoolean(PREF_ACTIVE_LIVECHAT, false)
                    .putLong(PREF_LIVECHAT_START_TIME, 0L)
                    .apply();
            detachLiveChatSystem();
            updateUiForLiveChat(false);
        }
        activityStartedAt = System.currentTimeMillis();
        if (headerTitle != null) headerTitle.setText("AI Assistant");
        if (badgeBeta != null) badgeBeta.setVisibility(View.VISIBLE);
        if (badgeLiveStatus != null) badgeLiveStatus.setVisibility(View.GONE);
        if (bottomSuggestionsScroll != null) bottomSuggestionsScroll.setVisibility(View.VISIBLE);
        if (cardLivechatView != null) cardLivechatView.setVisibility(View.VISIBLE);
        if (inputView != null) inputView.setHint(R.string.chat_input_hint_ai);
        if (tvChatDisclaimer != null) tvChatDisclaimer.setText(R.string.chat_disclaimer_unified);
        clearAttachmentSelection();
        if (composerAttachmentStrip != null) composerAttachmentStrip.setVisibility(View.GONE);
        saveCurrentSessionToHistory();
        if (aiChatRef != null) {
            aiChatRef.removeValue();
        }
        clearLocalChat();
        currentSessionId = "";
        currentSessionTopic = "";
        messages.clear();
        setSending(false);
        removeTypingIndicator();
        removeLiveTypingIndicator();
        if (typewriterRunnable != null) {
            mainHandler.removeCallbacks(typewriterRunnable);
            typewriterRunnable = null;
        }
        if (pendingReplyRunnable != null) {
            mainHandler.removeCallbacks(pendingReplyRunnable);
            pendingReplyRunnable = null;
        }
        renderMessages();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (isLiveChatMode) {
            // Sesi livechat belum diclose, jangan reset chat
            return;
        }
        boolean startNew = intent != null && intent.getBooleanExtra("EXTRA_START_NEW_CHAT", false);
        if (startNew) {
            startNewChatSession(false);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        saveCurrentSessionToHistory();
    }

    // SEKSYEN: ONDESTROY
    /** Pembersihan memori, buang listener, dan padam sesi Firebase aktif. */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveCurrentSessionToHistory();
        if (aiChatRef != null) {
            aiChatRef.removeValue();
        }
        clearLocalChat();
        detachAiChatStore();
        detachLiveChatSystem();
        mainHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }

    // SEKSYEN: LIVECHAT & MODE SWITCHER SYSTEM
    private void setupAttachmentLaunchers() {
        pickAttachmentLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) return;
                    try {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {}
                    setSelectedAttachment(uri);
                }
        );
        takeCameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                bitmap -> {
                    if (bitmap == null) return;
                    setSelectedCameraAttachment(bitmap);
                }
        );
    }

    private void onSendClicked() {
        sendMessage();
    }

    private boolean isLiveChatOpenStatus(String status) {
        if (status == null || status.trim().isEmpty()) return false;
        String s = status.trim().toLowerCase(Locale.ROOT);
        return "open".equals(s) || "active".equals(s) || "answered".equals(s)
                || "in_progress".equals(s) || "claimed".equals(s) || "pending".equals(s)
                || "waiting".equals(s) || "unread".equals(s);
    }

    private boolean isLiveChatClosedStatus(String status) {
        if (status == null || status.trim().isEmpty()) return false;
        String s = status.trim().toLowerCase(Locale.ROOT);
        return "resolved".equals(s) || "closed".equals(s) || "cancelled".equals(s) || "done".equals(s);
    }

    private void ensureLiveChatRef() {
        if (liveChatRef != null) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        currentUid = user.getUid();
        liveChatRef = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                .getReference("ticketChats")
                .child(currentUid);
    }

    private void setupLiveChatSystem() {
        detachLiveChatSystem();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        currentUid = user.getUid();

        liveChatRef = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                .getReference("ticketChats")
                .child(currentUid);

        liveMetaListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot == null) return;
                long adminTypingAt = snapshotLong(snapshot.child("adminTypingAt"));
                Boolean typingVal = snapshot.child("adminTyping").getValue(Boolean.class);
                boolean adminTyping = Boolean.TRUE.equals(typingVal) && (System.currentTimeMillis() - adminTypingAt < 10000L);
                setAdminTypingActive(adminTyping);

                long claimedAt = snapshotLong(snapshot.child("claimedAt"));
                if (claimedAt > 0) {
                    isLiveAgentJoined = true;
                }

                String status = safe(snapshot.child("status").getValue(String.class));
                long resolvedAt = snapshotLong(snapshot.child("resolvedAt"));
                boolean isClosedStatus = snapshot.exists() && isLiveChatClosedStatus(status);
                boolean isOpenStatus = snapshot.exists() && isLiveChatOpenStatus(status);

                if (isClosedStatus) {
                    if (isLiveChatMode) {
                        // Sesi hanya ditamatkan sekiranya rekod penutupan berlaku SELEPAS sesi livechat dimulakan.
                        // Berikan toleransi 10 saat untuk perbezaan jam client/server
                        if (liveChatSessionStartTime > 0 && resolvedAt > 0 && resolvedAt < (liveChatSessionStartTime - 10000L)) {
                            return;
                        }
                        isLiveChatMode = false;
                        isLiveAgentJoined = false;
                        liveChatSessionStartTime = 0L;
                        chatPrefs().edit()
                                .putBoolean(PREF_ACTIVE_LIVECHAT, false)
                                .putLong(PREF_LIVECHAT_START_TIME, 0L)
                                .apply();
                        updateUiForLiveChat(false);

                        boolean alreadyHasResolvedNotice = false;
                        for (ChatMessage m : messages) {
                            if (m.content != null && (m.content.toLowerCase(Locale.ROOT).contains("marked resolved")
                                    || m.content.toLowerCase(Locale.ROOT).contains("diselesaikan dan ditamatkan")
                                    || m.content.toLowerCase(Locale.ROOT).contains("session has been marked resolved"))) {
                                alreadyHasResolvedNotice = true;
                                break;
                            }
                        }
                        if (!alreadyHasResolvedNotice) {
                            ChatMessage endedNotice = new ChatMessage("system", getString(R.string.livechat_session_ended_notice), System.currentTimeMillis());
                            messages.add(endedNotice);
                            addSystemNoticeBubble(endedNotice.content);
                            scrollToBottom();
                        }
                        if (RateServiceManager.getInstance() != null) {
                            RateServiceManager.getInstance().scheduleRatingPrompt(resolvedAt > 0 ? resolvedAt : System.currentTimeMillis());
                        }
                    } else {
                        chatPrefs().edit()
                                .putBoolean(PREF_ACTIVE_LIVECHAT, false)
                                .putLong(PREF_LIVECHAT_START_TIME, 0L)
                                .apply();
                    }
                } else if (isOpenStatus) {
                    long sessionStart = snapshotLong(snapshot.child("sessionStartTime"));
                    if (sessionStart <= 0L) {
                        sessionStart = snapshotLong(snapshot.child("createdAt"));
                    }
                    if (sessionStart > 0L) {
                        liveChatSessionStartTime = sessionStart;
                    }
                    chatPrefs().edit()
                            .putBoolean(PREF_ACTIVE_LIVECHAT, true)
                            .putLong(PREF_LIVECHAT_START_TIME, liveChatSessionStartTime)
                            .apply();

                    if (!isLiveChatMode) {
                        isLiveChatMode = true;
                        updateUiForLiveChat(true);
                    }
                } else if (!snapshot.exists()) {
                    // Nod dipadam oleh admin / DB direset
                    if (isLiveChatMode) {
                        isLiveChatMode = false;
                        isLiveAgentJoined = false;
                        liveChatSessionStartTime = 0L;
                        chatPrefs().edit()
                                .putBoolean(PREF_ACTIVE_LIVECHAT, false)
                                .putLong(PREF_LIVECHAT_START_TIME, 0L)
                                .apply();
                        updateUiForLiveChat(false);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        };
        liveChatRef.child("meta").addValueEventListener(liveMetaListener);

        liveMessagesQuery = liveChatRef.child("messages").orderByChild("createdAt").limitToLast(100);
        liveMessagesListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot == null) return;

                if (isLiveChatMode) {
                    // Semasa dalam sesi Livechat: Paparkan mesej penuh sesi semasa sahaja
                    boolean hasSystemNoticeInDb = false;
                    for (DataSnapshot c : snapshot.getChildren()) {
                        long createdAt = snapshotLong(c.child("createdAt"));
                        if (liveChatSessionStartTime > 0 && createdAt > 0 && createdAt < liveChatSessionStartTime - 60000L) {
                            continue;
                        }
                        if ("system".equalsIgnoreCase(c.child("sender").getValue(String.class))) {
                            hasSystemNoticeInDb = true;
                            break;
                        }
                    }
                    if (messages.isEmpty() && snapshot.hasChildren() && !hasSystemNoticeInDb) {
                        long firstTime = System.currentTimeMillis();
                        for (DataSnapshot first : snapshot.getChildren()) {
                            long t = snapshotLong(first.child("createdAt"));
                            if (liveChatSessionStartTime > 0 && t > 0 && t < liveChatSessionStartTime - 60000L) {
                                continue;
                            }
                            if (t > 0) {
                                firstTime = t - 1;
                                break;
                            }
                        }
                        ChatMessage transferNotice = new ChatMessage("system", getString(R.string.chat_transferred_to_admin_desc), firstTime);
                        messages.add(transferNotice);
                        addMessageBubble(transferNotice);
                    }

                    boolean hasNew = false;
                    for (DataSnapshot child : snapshot.getChildren()) {
                        String text = safe(child.child("text").getValue(String.class)).trim();
                        DataSnapshot attSnap = child.child("attachment");
                        String attUrl = safe(attSnap.child("downloadUrl").getValue(String.class));
                        String attName = safe(attSnap.child("name").getValue(String.class));
                        String attMime = safe(attSnap.child("mimeType").getValue(String.class));
                        if (text.isEmpty() && attUrl.isEmpty()) continue;
                        if (text.toLowerCase(Locale.ROOT).contains("claimed your ticket")) continue;
                        if (text.toLowerCase(Locale.ROOT).contains("marked resolved") || text.toLowerCase(Locale.ROOT).contains("session has been marked resolved") || text.toLowerCase(Locale.ROOT).contains("diselesaikan dan ditamatkan")) {
                            boolean hasNotice = false;
                            for (ChatMessage m : messages) {
                                if (m.content != null && (m.content.toLowerCase(Locale.ROOT).contains("marked resolved")
                                        || m.content.toLowerCase(Locale.ROOT).contains("session has been marked resolved")
                                        || m.content.toLowerCase(Locale.ROOT).contains("diselesaikan dan ditamatkan"))) {
                                    hasNotice = true;
                                    break;
                                }
                            }
                            if (hasNotice) continue;
                        }

                        long createdAt = snapshotLong(child.child("createdAt"));
                        if (createdAt <= 0L) {
                            createdAt = System.currentTimeMillis();
                        }

                        // Jangan restore mesej lama dari sesi yang sudah ditutup sebelum ini (>1 minit sebelum sesi ini bermula)
                        if (liveChatSessionStartTime > 0 && createdAt > 0 && createdAt < liveChatSessionStartTime - 60000L) {
                            continue;
                        }

                        String sender = safe(child.child("sender").getValue(String.class));
                        String senderName = safe(child.child("senderName").getValue(String.class));
                        String key = child.getKey();

                        boolean exists = false;
                        for (ChatMessage m : messages) {
                            if (key != null && key.equals(m.messageId)) {
                                exists = true;
                                break;
                            }
                        }

                        if (!exists) {
                            hasNew = true;
                            String role = "admin".equalsIgnoreCase(sender) ? "admin"
                                    : ("system".equalsIgnoreCase(sender) ? "system"
                                    : ("ai".equalsIgnoreCase(sender) || "assistant".equalsIgnoreCase(sender) ? "assistant" : "user"));
                            if ("admin".equals(role)) {
                                isLiveAgentJoined = true;
                            }
                            ChatMessage msg = new ChatMessage(role, text, createdAt, senderName, attName, attUrl, attMime, key);
                            messages.add(msg);
                            addMessageBubble(msg);
                            if (heroGreetingLayout != null) {
                                heroGreetingLayout.setVisibility(View.GONE);
                            }
                        }
                    }

                    if (hasNew) {
                        markLiveChatRead();
                        scrollToBottom();
                    }
                } else {
                    // Mod AI biasa: Terima mesej admin atau sistem jika ada kemas kini
                    boolean hasNewIncoming = false;
                    for (DataSnapshot child : snapshot.getChildren()) {
                        String text = safe(child.child("text").getValue(String.class)).trim();
                        DataSnapshot attSnap = child.child("attachment");
                        String attUrl = safe(attSnap.child("downloadUrl").getValue(String.class));
                        String attName = safe(attSnap.child("name").getValue(String.class));
                        String attMime = safe(attSnap.child("mimeType").getValue(String.class));
                        if (text.isEmpty() && attUrl.isEmpty()) continue;
                        if (text.toLowerCase(Locale.ROOT).contains("claimed your ticket")) continue;
                        if (text.toLowerCase(Locale.ROOT).contains("marked resolved") || text.toLowerCase(Locale.ROOT).contains("session has been marked resolved") || text.toLowerCase(Locale.ROOT).contains("diselesaikan dan ditamatkan")) {
                            boolean hasNotice = false;
                            for (ChatMessage m : messages) {
                                if (m.content != null && (m.content.toLowerCase(Locale.ROOT).contains("marked resolved")
                                        || m.content.toLowerCase(Locale.ROOT).contains("session has been marked resolved")
                                        || m.content.toLowerCase(Locale.ROOT).contains("diselesaikan dan ditamatkan"))) {
                                    hasNotice = true;
                                    break;
                                }
                            }
                            if (hasNotice) continue;
                        }

                        String sender = safe(child.child("sender").getValue(String.class));
                        String senderName = safe(child.child("senderName").getValue(String.class));
                        long createdAt = snapshotLong(child.child("createdAt"));
                        String key = child.getKey();

                        if ("admin".equalsIgnoreCase(sender) || "system".equalsIgnoreCase(sender)) {
                            if (createdAt > 0 && createdAt <= activityStartedAt - 60000L) {
                                continue;
                            }
                            boolean exists = false;
                            for (ChatMessage m : messages) {
                                if (key != null && key.equals(m.messageId)) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                hasNewIncoming = true;
                                String role = "admin".equalsIgnoreCase(sender) ? "admin" : "system";
                                ChatMessage incomingMsg = new ChatMessage(role, text, createdAt > 0 ? createdAt : System.currentTimeMillis(), senderName, attName, attUrl, attMime, key);
                                messages.add(incomingMsg);
                                addMessageBubble(incomingMsg);
                                if (heroGreetingLayout != null) {
                                    heroGreetingLayout.setVisibility(View.GONE);
                                }
                                if ("admin".equalsIgnoreCase(sender)) {
                                    isLiveAgentJoined = true;
                                    isLiveChatMode = true;
                                    updateUiForLiveChat(true);
                                }
                            }
                        }
                    }

                    if (hasNewIncoming) {
                        markLiveChatRead();
                        scrollToBottom();
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        };
        liveMessagesQuery.addValueEventListener(liveMessagesListener);
    }

    private void detachLiveChatSystem() {
        if (liveMessagesQuery != null && liveMessagesListener != null) {
            liveMessagesQuery.removeEventListener(liveMessagesListener);
        }
        if (liveChatRef != null && liveMetaListener != null) {
            liveChatRef.child("meta").removeEventListener(liveMetaListener);
        }
        removeLiveTypingIndicator();
        liveMessagesQuery = null;
        liveMessagesListener = null;
        liveMetaListener = null;
    }

    private void markLiveChatRead() {
        if (liveChatRef != null) {
            liveChatRef.child("meta").child("userLastReadAt").setValue(ServerValue.TIMESTAMP);
            liveChatRef.child("meta").child("unreadByUser").setValue(false);
        }
    }

    private void syncUserMessageToLiveSupport(FirebaseUser user, String text, String attName, String dataUrl, String attMime) {
        syncUserMessageToLiveSupport(user, text, attName, dataUrl, attMime, null);
    }

    private void syncUserMessageToLiveSupport(FirebaseUser user, String text, String attName, String dataUrl, String attMime, String customKey) {
        if (!isLiveChatMode) return;
        if (user == null) return;
        if (liveChatRef == null) {
            setupLiveChatSystem();
            if (liveChatRef == null) return;
        }
        String uid = user.getUid();
        String name = UserPrefs.getName(this);
        if (name == null || name.trim().isEmpty()) name = user.getDisplayName();
        if (name == null || name.trim().isEmpty()) name = "User";

        String messageId = (customKey != null && !customKey.isEmpty()) ? customKey : liveChatRef.child("messages").push().getKey();
        if (messageId == null) return;

        Map<String, Object> msg = new HashMap<>();
        msg.put("text", text);
        msg.put("sender", "user");
        msg.put("senderUid", uid);
        msg.put("senderName", name);
        msg.put("createdAt", ServerValue.TIMESTAMP);
        msg.put("source", "app");

        if (dataUrl != null && !dataUrl.isEmpty()) {
            Map<String, Object> attMap = new HashMap<>();
            attMap.put("name", attName == null || attName.isEmpty() ? "image.jpg" : attName);
            attMap.put("mimeType", attMime == null || attMime.isEmpty() ? "image/jpeg" : attMime);
            attMap.put("downloadUrl", dataUrl);
            attMap.put("storageType", "inline_base64");
            msg.put("attachment", attMap);
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("meta/updatedAt", ServerValue.TIMESTAMP);
        updates.put("meta/lastMessage", text.isEmpty() ? (attName == null || attName.isEmpty() ? "Attachment" : attName) : text);
        updates.put("meta/lastSender", "user");
        updates.put("meta/status", "open");
        updates.put("meta/userUid", uid);
        updates.put("meta/userName", name);
        updates.put("meta/userEmail", safe(UserPrefs.getEmail(this)));
        updates.put("meta/source", "ai_assistant");
        updates.put("meta/chatType", "ticket");
        updates.put("meta/unreadByUser", false);
        updates.put("messages/" + messageId, msg);

        liveChatRef.updateChildren(updates);
        try {
            FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                    .getReference("supportChats")
                    .child(uid)
                    .updateChildren(updates);
        } catch (Throwable ignored) {}
    }

    private void syncAssistantMessageToLiveSupport(ChatMessage aiMsg, String text, String source) {
        if (!isLiveChatMode) return;
        if (liveChatRef == null) {
            setupLiveChatSystem();
            if (liveChatRef == null) return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String messageId = liveChatRef.child("messages").push().getKey();
        if (messageId == null) return;
        if (aiMsg != null) {
            aiMsg.messageId = messageId;
        }

        Map<String, Object> msg = new HashMap<>();
        msg.put("text", safe(text));
        msg.put("sender", "ai");
        msg.put("role", "assistant");
        msg.put("senderUid", user.getUid());
        msg.put("senderName", "ResQTap Support");
        msg.put("createdAt", ServerValue.TIMESTAMP);
        msg.put("source", safe(source).isEmpty() ? "ai" : safe(source));

        Map<String, Object> updates = new HashMap<>();
        updates.put("meta/updatedAt", ServerValue.TIMESTAMP);
        updates.put("meta/lastMessage", safe(text));
        updates.put("meta/lastSender", "ai");
        updates.put("messages/" + messageId, msg);

        liveChatRef.updateChildren(updates);
        try {
            FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                    .getReference("supportChats")
                    .child(user.getUid())
                    .updateChildren(updates);
        } catch (Throwable ignored) {}
    }

    private void syncSystemNoticeToLiveSupport(ChatMessage noticeMsg, String text) {
        if (!isLiveChatMode) return;
        if (liveChatRef == null) {
            setupLiveChatSystem();
            if (liveChatRef == null) return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = (user != null) ? user.getUid() : "system";

        String messageId = liveChatRef.child("messages").push().getKey();
        if (messageId == null) return;
        if (noticeMsg != null) {
            noticeMsg.messageId = messageId;
        }

        Map<String, Object> msg = new HashMap<>();
        msg.put("text", safe(text));
        msg.put("sender", "system");
        msg.put("role", "system");
        msg.put("senderUid", uid);
        msg.put("senderName", "ResQTap");
        msg.put("createdAt", ServerValue.TIMESTAMP);
        msg.put("source", "auto-support");

        Map<String, Object> updates = new HashMap<>();
        updates.put("messages/" + messageId, msg);

        liveChatRef.updateChildren(updates);
        try {
            FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                    .getReference("supportChats")
                    .child(uid)
                    .updateChildren(updates);
        } catch (Throwable ignored) {}
    }

    private void setAdminTypingActive(boolean active) {
        // Admin typing indicator dimatikan atas permintaan LO
        removeLiveTypingIndicator();
        adminTypingActive = false;
    }

    private void showLiveAdminTypingIndicator() {
        // Admin typing bubble dimatikan atas permintaan LO
        removeLiveTypingIndicator();
    }

    private void removeLiveTypingIndicator() {
        if (liveTypingDotsRunnable != null) {
            mainHandler.removeCallbacks(liveTypingDotsRunnable);
            liveTypingDotsRunnable = null;
        }
        if (liveTypingIndicatorView != null && messagesContainer != null) {
            messagesContainer.removeView(liveTypingIndicatorView);
            liveTypingIndicatorView = null;
        }
        liveTypingDotsView = null;
    }

    private void startLiveTypingAnimation() {
        liveTypingDotsStep = 0;
        liveTypingDotsRunnable = new Runnable() {
            @Override
            public void run() {
                if (liveTypingDotsView == null) return;
                int dots = liveTypingDotsStep % 4;
                StringBuilder text = new StringBuilder(getString(R.string.live_support_admin_typing));
                for (int i = 0; i < dots; i++) text.append('.');
                liveTypingDotsView.setText(text.toString());
                liveTypingDotsStep++;
                mainHandler.postDelayed(this, 450L);
            }
        };
        mainHandler.post(liveTypingDotsRunnable);
    }

    private void showAttachmentOptionsBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_ResQTap_BottomSheetDialog);
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_chat_attachment, null);
        dialog.setContentView(sheetView);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setWindowAnimations(R.style.Animation_ResQTap_BottomSheetDialog);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        View btnTake = sheetView.findViewById(R.id.btn_option_take_camera);
        if (btnTake != null) {
            btnTake.setOnClickListener(v -> {
                dialog.dismiss();
                if (takeCameraLauncher != null) takeCameraLauncher.launch(null);
            });
        }

        View btnSelect = sheetView.findViewById(R.id.btn_option_select_file);
        if (btnSelect != null) {
            btnSelect.setOnClickListener(v -> {
                dialog.dismiss();
                if (pickAttachmentLauncher != null) {
                    pickAttachmentLauncher.launch(new String[]{"image/*", "application/pdf", "*/*"});
                }
            });
        }

        dialog.show();
    }

    private void setSelectedAttachment(Uri uri) {
        selectedAttachmentUri = uri;
        selectedAttachmentBytes = null;
        selectedAttachmentName = queryDisplayName(uri);
        selectedAttachmentMime = safe(getContentResolver().getType(uri));
        selectedAttachmentSize = querySize(uri);
        if (selectedAttachmentName.isEmpty()) selectedAttachmentName = "attachment";
        updateAttachmentLabels();
    }

    private void setSelectedCameraAttachment(Bitmap bitmap) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
        selectedAttachmentBytes = out.toByteArray();
        selectedAttachmentUri = null;
        selectedAttachmentMime = "image/jpeg";
        selectedAttachmentSize = selectedAttachmentBytes.length;
        selectedAttachmentName = "camera_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
        updateAttachmentLabels();
    }

    private void clearAttachmentSelection() {
        selectedAttachmentUri = null;
        selectedAttachmentBytes = null;
        selectedAttachmentName = "";
        selectedAttachmentMime = "";
        selectedAttachmentSize = 0L;
        if (composerAttachmentStrip != null) {
            composerAttachmentStrip.setVisibility(View.GONE);
        }
    }

    private void updateAttachmentLabels() {
        boolean hasAtt = (selectedAttachmentUri != null || selectedAttachmentBytes != null);
        if (composerAttachmentStrip != null) {
            composerAttachmentStrip.setVisibility(hasAtt ? View.VISIBLE : View.GONE);
        }
        if (composerAttachmentLabel != null) {
            composerAttachmentLabel.setText(selectedAttachmentName.isEmpty() ? "Attachment Selected" : selectedAttachmentName);
        }
    }

    private byte[] readUriBytes(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) return null;
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return safe(cursor.getString(index));
            }
        } catch (Exception ignored) {}
        return safe(uri.getLastPathSegment());
    }

    private long querySize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0) return cursor.getLong(index);
            }
        } catch (Exception ignored) {}
        return 0L;
    }

    private Bitmap bitmapFromDataUrl(String dataUrl) {
        try {
            int comma = dataUrl.indexOf(',');
            if (comma < 0 || comma >= dataUrl.length() - 1) return null;
            byte[] bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void openAttachment(String url, String mimeType) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (!safe(mimeType).isEmpty()) intent.setDataAndType(Uri.parse(url), mimeType);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(this, R.string.support_livechat_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /** Setup dan konfigurasi AiChatStore (Sentiasa bermula dengan sesi perbualan baharu). */
    private void setupAiChatStore() {
        if (!USE_DATABASE_CHAT_STORE) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        currentUid = user.getUid();
        FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL).goOnline();
        aiChatRef = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                .getReference("supportChats")
                .child(currentUid)
                .child("aiAssistant");

        // Padam sebarang nod lama di Firebase & local chat agar sentiasa bermula New Chat
        aiChatRef.removeValue();
        clearLocalChat();
        attachAiChatListeners();
    }

    private void attachAiChatListeners() {
        if (!USE_DATABASE_CHAT_STORE) return;
        if (aiChatRef == null || messagesListener != null) return;

        metaListener = new ValueEventListener() {
            /** Callback apabila data Firebase Realtime Database berubah. */
    @Override
            public void onDataChange(DataSnapshot snapshot) {
                long expiresAt = snapshotLong(snapshot.child("expiresAt"));
                if (isExpired(expiresAt)) {
                    resetExpiredChat();
                    return;
                }
                scheduleChatExpiry(expiresAt);
            }

            /** Callback sekiranya operasi Firebase dibatalkan atau gagal. */
    @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(ChatActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        aiChatRef.child("meta").addValueEventListener(metaListener);

        messagesListener = new ValueEventListener() {
            /** Callback apabila data Firebase Realtime Database berubah. */
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (sending || typewriterRunnable != null) {
                    return;
                }
                ArrayList<ChatMessage> loaded = new ArrayList<>();
                for (DataSnapshot item : snapshot.getChildren()) {
                    ChatMessage message = readStoredMessage(item);
                    if (message != null && !isWelcomeMessage(message.content)) {
                        loaded.add(message);
                    }
                }
                if (!loaded.isEmpty()) {
                    messages.clear();
                    messages.addAll(loaded);
                    saveLocalMessages();
                    renderMessages();
                }
            }

            /** Callback sekiranya operasi Firebase dibatalkan atau gagal. */
    @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(ChatActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        messagesQuery = aiChatRef.child("messages").orderByChild("createdAt").limitToLast(80);
        messagesQuery.addValueEventListener(messagesListener);
    }

    private void detachAiChatStore() {
        if (messagesQuery != null && messagesListener != null) {
            messagesQuery.removeEventListener(messagesListener);
        }
        if (aiChatRef != null && metaListener != null) {
            aiChatRef.child("meta").removeEventListener(metaListener);
        }
        if (expiryRunnable != null) {
            mainHandler.removeCallbacks(expiryRunnable);
            expiryRunnable = null;
        }
        messagesQuery = null;
        messagesListener = null;
        metaListener = null;
        aiChatRef = null;
    }

    /** Paparkan WelcomeMessage. */
    private void showWelcomeMessage() {
        messages.clear();
        isShowingInitialWelcome = true;
        showThinkingIndicator("greeting");
        mainHandler.postDelayed(() -> {
            isShowingInitialWelcome = false;
            removeTypingIndicator();
            showAssistantReply(getString(R.string.faq_menu), "faq-welcome");
        }, 3000);
    }

    /** Fungsi pembantu untuk showTypingIndicator. */
    private void showTypingIndicator() {
        showThinkingIndicator("");
    }

    private SharedPreferences chatPrefs() {
        return getSharedPreferences(CHAT_PREFS, MODE_PRIVATE);
    }

    private void loadLocalMessages() {
        long expiresAt = chatPrefs().getLong(CHAT_PREF_EXPIRES_AT, 0L);
        if (isExpired(expiresAt)) {
            clearLocalChat();
            messages.clear();
            renderMessages();
            return;
        }

        ArrayList<ChatMessage> loaded = new ArrayList<>();
        try {
            JSONArray items = new JSONArray(chatPrefs().getString(CHAT_PREF_MESSAGES, "[]"));
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                String role = safe(item.optString("role"));
                String content = safe(item.optString("content"));
                long createdAt = item.optLong("createdAt", 0L);
                if (!content.isEmpty() && !isWelcomeMessage(content) && ("user".equals(role) || "assistant".equals(role) || "system".equals(role) || "admin".equals(role))) {
                    loaded.add(new ChatMessage(role, content, createdAt > 0L ? createdAt : System.currentTimeMillis()));
                }
            }
        } catch (Exception ignored) {
            loaded.clear();
        }

        messages.clear();
        messages.addAll(loaded);
        saveLocalMessages();
        renderMessages();
        scheduleChatExpiry(expiresAt);
    }

    /** Semak dan sahkan WelcomeMessage lama. */
    private boolean isWelcomeMessage(String content) {
        if (content == null) return false;
        String c = content.trim();
        return c.startsWith("Hi! I am ResQTap Assistant.\nPlease select common issues:")
                || c.startsWith("Hai! Saya Pembantu ResQTap.\nPilih isu lazim:")
                || c.contains("select common issues:")
                || c.contains("Pilih isu lazim:")
                || c.equalsIgnoreCase(safe(getString(R.string.faq_menu)).trim());
    }

    /** Simpan atau hantar data LocalMessages. */
    private void saveLocalMessages() {
        try {
            JSONArray items = new JSONArray();
            for (ChatMessage message : messages) {
                JSONObject item = new JSONObject();
                item.put("role", message.role);
                item.put("content", message.content);
                item.put("createdAt", message.createdAt);
                items.put(item);
            }
            chatPrefs().edit().putString(CHAT_PREF_MESSAGES, items.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** Simpan atau hantar data LocalExpiry. */
}
