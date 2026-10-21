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
                    boolean isActive = snapshot != null && snapshot.exists()
                            && ("open".equalsIgnoreCase(status) || "active".equalsIgnoreCase(status) || "answered".equalsIgnoreCase(status));

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
                    } else {
                        // Tiada sesi livechat aktif di server (status resolved / closed / tiada rekod)
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
                        boolean trulyActive = snapshot != null && snapshot.exists()
                                && ("open".equalsIgnoreCase(status) || "active".equalsIgnoreCase(status) || "answered".equalsIgnoreCase(status));
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
                            sendPresetPrompt("Hubungi Sokongan Admin");
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        sendPresetPrompt("Hubungi Sokongan Admin");
                    }
                });
                return;
            }
            Toast.makeText(this, R.string.livechat_active_cannot_reset, Toast.LENGTH_SHORT).show();
            return;
        }
        sendPresetPrompt("Hubungi Sokongan Admin");
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

    private void ensureLiveChatRef() {
        if (liveChatRef != null) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        currentUid = user.getUid();
        liveChatRef = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                .getReference("supportChats")
                .child(currentUid);
    }

    private void setupLiveChatSystem() {
        detachLiveChatSystem();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        currentUid = user.getUid();

        liveChatRef = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                .getReference("supportChats")
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
                boolean isClosedStatus = snapshot.exists() && ("resolved".equalsIgnoreCase(status) || "closed".equalsIgnoreCase(status));
                boolean isOpenStatus = snapshot.exists() && ("open".equalsIgnoreCase(status) || "active".equalsIgnoreCase(status) || "answered".equalsIgnoreCase(status));

                if (isClosedStatus) {
                    if (isLiveChatMode) {
                        // Sesi hanya ditamatkan sekiranya rekod penutupan berlaku SELEPAS sesi livechat dimulakan.
                        // Sekiranya resolvedAt <= liveChatSessionStartTime atau resolvedAt == 0, ini adalah status dari sesi terdahulu; abaikan!
                        if (liveChatSessionStartTime > 0 && (resolvedAt <= liveChatSessionStartTime || resolvedAt == 0)) {
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

                        ChatMessage endedNotice = new ChatMessage("system", getString(R.string.livechat_session_ended_notice), System.currentTimeMillis());
                        messages.add(endedNotice);
                        addSystemNoticeBubble(endedNotice.content);
                        scrollToBottom();
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
                    if (sessionStart > 0L && liveChatSessionStartTime == 0L) {
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
                } else {
                    // Nod tidak wujud atau status kosong/bukan aktif (cth: dipadam oleh admin / DB direset)
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
                        if (liveChatSessionStartTime > 0 && createdAt > 0 && createdAt < liveChatSessionStartTime - 2000L) {
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
                            if (liveChatSessionStartTime > 0 && t > 0 && t < liveChatSessionStartTime - 2000L) {
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

                        long createdAt = snapshotLong(child.child("createdAt"));

                        // FILTER KETAT SESI BAHARU: Jangan sekali-kali restore mesej dari sesi terdahulu yang sudah ditutup
                        if (liveChatSessionStartTime > 0 && createdAt > 0 && createdAt < liveChatSessionStartTime - 2000L) {
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
                    // Mod AI biasa: Hanya dengar sekiranya terdapat mesej admin baharu
                    boolean hasNewAdmin = false;
                    for (DataSnapshot child : snapshot.getChildren()) {
                        String text = safe(child.child("text").getValue(String.class)).trim();
                        DataSnapshot attSnap = child.child("attachment");
                        String attUrl = safe(attSnap.child("downloadUrl").getValue(String.class));
                        String attName = safe(attSnap.child("name").getValue(String.class));
                        String attMime = safe(attSnap.child("mimeType").getValue(String.class));
                        if (text.isEmpty() && attUrl.isEmpty()) continue;

                        String sender = safe(child.child("sender").getValue(String.class));
                        String senderName = safe(child.child("senderName").getValue(String.class));
                        long createdAt = snapshotLong(child.child("createdAt"));
                        String key = child.getKey();

                        if ("admin".equalsIgnoreCase(sender)) {
                            // Jangan paparkan mesej admin lama sebelum sesi semasa dibuka
                            if (createdAt <= activityStartedAt) {
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
                                hasNewAdmin = true;
                                ChatMessage adminMsg = new ChatMessage("admin", text, createdAt, senderName, attName, attUrl, attMime, key);
                                messages.add(adminMsg);
                                addMessageBubble(adminMsg);
                                if (heroGreetingLayout != null) {
                                    heroGreetingLayout.setVisibility(View.GONE);
                                }
                            }
                        }
                    }

                    if (hasNewAdmin) {
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
        updates.put("meta/unreadByUser", false);
        updates.put("messages/" + messageId, msg);

        liveChatRef.updateChildren(updates);
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
    private void saveLocalExpiry(long expiresAt) {
        chatPrefs().edit().putLong(CHAT_PREF_EXPIRES_AT, expiresAt).apply();
        scheduleChatExpiry(expiresAt);
    }

    /** Padam atau bersihkan LocalChat. */
    private void clearLocalChat() {
        chatPrefs().edit()
                .remove(CHAT_PREF_MESSAGES)
                .remove(CHAT_PREF_EXPIRES_AT)
                .apply();
    }

    private ChatMessage readStoredMessage(DataSnapshot snapshot) {
        String role = safe(snapshot.child("role").getValue(String.class));
        if (role.isEmpty()) role = safe(snapshot.child("sender").getValue(String.class));
        if ("ai".equals(role)) role = "assistant";
        if (!"user".equals(role) && !"assistant".equals(role)) return null;

        String text = safe(snapshot.child("text").getValue(String.class));
        if (text.isEmpty()) text = safe(snapshot.child("content").getValue(String.class));
        if (text.isEmpty()) return null;

        long createdAt = snapshotLong(snapshot.child("createdAt"));
        if (createdAt <= 0L) createdAt = snapshotLong(snapshot.child("clientAt"));
        if (createdAt <= 0L) createdAt = System.currentTimeMillis();
        return new ChatMessage(role, text, createdAt);
    }

    private long snapshotLong(DataSnapshot snapshot) {
        Object value = snapshot == null ? null : snapshot.getValue();
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(safe(value));
        } catch (Exception error) {
            return 0L;
        }
    }

    /** Semak dan sahkan Expired. */
    private boolean isExpired(long expiresAt) {
        return expiresAt > 0L && expiresAt <= System.currentTimeMillis();
    }

    private void scheduleChatExpiry(long expiresAt) {
        if (expiryRunnable != null) {
            mainHandler.removeCallbacks(expiryRunnable);
            expiryRunnable = null;
        }
        if (expiresAt <= 0L) return;

        long delay = Math.max(0L, expiresAt - System.currentTimeMillis());
        expiryRunnable = this::resetExpiredChat;
        mainHandler.postDelayed(expiryRunnable, delay);
    }

    private void resetExpiredChat() {
        if (aiChatRef != null) {
            aiChatRef.removeValue();
        }
        clearLocalChat();
        setSending(false);
        removeTypingIndicator();
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

    /** Simpan atau hantar data Message. */
    private void sendMessage() {
        String message = safe(inputView.getText()).trim();
        boolean hasAttachment = (selectedAttachmentUri != null || selectedAttachmentBytes != null);
        if (message.isEmpty() && !hasAttachment) {
            Toast.makeText(this, R.string.ai_chat_empty_message, Toast.LENGTH_SHORT).show();
            return;
        }
        if (sending) return;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, R.string.ai_chat_login_required, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }

        long now = System.currentTimeMillis();
        String name = UserPrefs.getName(this);
        if (name == null || name.trim().isEmpty()) name = user.getDisplayName();
        if (name == null || name.trim().isEmpty()) name = "User";

        // Read attachment if present
        String dataUrl = "";
        String attName = selectedAttachmentName;
        String attMime = selectedAttachmentMime;
        if (hasAttachment) {
            byte[] bytes = selectedAttachmentBytes;
            if (bytes == null && selectedAttachmentUri != null) {
                bytes = readUriBytes(selectedAttachmentUri);
            }
            if (bytes != null && bytes.length > 0) {
                try {
                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bmp != null) {
                        int maxDim = 1080;
                        int w = bmp.getWidth();
                        int h = bmp.getHeight();
                        if (w > maxDim || h > maxDim) {
                            float ratio = Math.min((float) maxDim / w, (float) maxDim / h);
                            bmp = Bitmap.createScaledBitmap(bmp, Math.round(w * ratio), Math.round(h * ratio), true);
                        }
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        bmp.compress(Bitmap.CompressFormat.JPEG, 75, baos);
                        bytes = baos.toByteArray();
                    }
                } catch (Throwable ignored) {}
                String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                attMime = selectedAttachmentMime.isEmpty() ? "image/jpeg" : selectedAttachmentMime;
                dataUrl = "data:" + attMime + ";base64," + b64;
            }
        }

        String userMsgText = message;
        if (userMsgText.isEmpty() && hasAttachment) {
            userMsgText = "[Lampiran]";
        }

        if (liveChatRef == null) {
            setupLiveChatSystem();
        }
        String liveMsgKey = (liveChatRef != null) ? liveChatRef.child("messages").push().getKey() : null;
        if (liveMsgKey == null || liveMsgKey.isEmpty()) {
            liveMsgKey = "m" + now + "_" + Math.abs(userMsgText.hashCode());
        }
        pendingClientMessageId = liveMsgKey;
        if (currentSessionId.isEmpty()) {
            currentSessionId = "session_" + now;
            currentSessionTopic = userMsgText;
        }

        ChatMessage userMsg = new ChatMessage("user", userMsgText, now, name, attName, dataUrl, attMime, pendingClientMessageId);
        messages.add(userMsg);
        saveCurrentSessionToHistory();
        saveLocalMessages();
        saveLocalExpiry(now + CHAT_EXPIRE_AFTER_MS);
        saveUserMessage(user, userMsgText);

        inputView.setText("");
        clearAttachmentSelection();
        setSending(true);
        renderMessages();

        // Sekiranya sudah berada dalam sesi Livechat bersama Admin
        if (isLiveChatMode) {
            syncUserMessageToLiveSupport(user, userMsgText, attName, dataUrl, attMime, liveMsgKey);

            // Sekiranya Live Agent belum claim atau balas, berikan auto chat respon daripada pembantu
            if (!isLiveAgentJoined) {
                showThinkingIndicator(message);
                String reply = getFaqAnswer(message);
                mainHandler.postDelayed(() -> handleChatReply(reply), 1500L);
            }

            setSending(false);
            return;
        }

        // Check if user is asking for admin or live support
        String lower = message.toLowerCase(Locale.ROOT);
        boolean asksSupport = lower.contains("livechat") || lower.contains("live chat") || lower.contains("admin") || lower.contains("sokongan") || lower.contains("human") || lower.contains("operator") || lower.contains("pegawai") || lower.contains("agent") || lower.contains("support");

        if (asksSupport) {
            isLiveChatMode = true;
            isLiveAgentJoined = false;
            long sessionStart = System.currentTimeMillis();
            liveChatSessionStartTime = sessionStart;
            chatPrefs().edit()
                    .putBoolean(PREF_ACTIVE_LIVECHAT, true)
                    .putLong(PREF_LIVECHAT_START_TIME, sessionStart)
                    .apply();
            updateUiForLiveChat(true);

            ensureLiveChatRef();

            // Sesi Baharu (New Session): Padam mesej lama di Firebase RTDB agar sesi baharu bermula bersih
            if (liveChatRef != null) {
                liveChatRef.child("messages").removeValue();

                Map<String, Object> metaOpen = new HashMap<>();
                metaOpen.put("status", "open");
                metaOpen.put("sessionStartTime", sessionStart);
                metaOpen.put("sessionId", "session_" + sessionStart);
                metaOpen.put("createdAt", ServerValue.TIMESTAMP);
                metaOpen.put("resolvedAt", 0);
                metaOpen.put("resolvedBy", null);
                metaOpen.put("claimedAt", 0);
                metaOpen.put("claimedBy", null);
                metaOpen.put("claimedByName", null);
                metaOpen.put("claimNotice", null);
                metaOpen.put("adminTyping", false);
                metaOpen.put("updatedAt", ServerValue.TIMESTAMP);
                metaOpen.put("lastMessage", userMsgText);
                metaOpen.put("lastSender", "user");
                metaOpen.put("userUid", user.getUid());
                metaOpen.put("userName", name);
                metaOpen.put("userEmail", safe(UserPrefs.getEmail(this)));
                metaOpen.put("unreadByUser", false);
                liveChatRef.child("meta").updateChildren(metaOpen);
            }

            // Simpan perbualan AI terdahulu ke arkib sejarah dan bersihkan paparan untuk sesi Livechat baharu
            saveCurrentSessionToHistory();
            messages.clear();
            messages.add(userMsg);

            long noticeTime = now + 1;
            ChatMessage transferNotice = new ChatMessage("system", getString(R.string.chat_transferred_to_admin_desc), noticeTime);
            messages.add(transferNotice);

            renderMessages();
            scrollToBottom();

            setupLiveChatSystem();

            // Mesej permintaan user & notis transfer dihantar ke Meja Bantuan Admin
            syncUserMessageToLiveSupport(user, userMsgText, attName, dataUrl, attMime, liveMsgKey);
            syncSystemNoticeToLiveSupport(transferNotice, transferNotice.content);

            // Tunjukkan status pending seperti chatbot lain sebelum mesej auto dihantar
            showThinkingIndicator("livechat");

            mainHandler.postDelayed(() -> {
                if (!isLiveChatMode || isFinishing() || isDestroyed()) return;
                String autoReplyText = getString(R.string.support_livechat_waiting_agent_auto);
                showAssistantReply(autoReplyText, "auto-support");
                setSending(false);
            }, 3000L);

            return;
        }

        if (hasAttachment && message.isEmpty()) {
            showThinkingIndicator("Foto lampiran dihantar");
            mainHandler.postDelayed(() -> {
                String reply = "Lampiran anda telah berjaya dimuat naik ke perbualan dan direkodkan untuk rujukan admin sokongan. Ada apa-apa lagi yang boleh saya bantu mengenai aplikasi ResQTap?";
                handleChatReply(reply);
            }, 8000L);
            return;
        }

        showThinkingIndicator(message);
        String reply = getFaqAnswer(message);
        mainHandler.postDelayed(() -> handleChatReply(reply), 8000L);
    }

    /** Simpan atau hantar data UserMessage. */
    private void saveUserMessage(FirebaseUser user, String text) {
        if (!USE_DATABASE_CHAT_STORE) return;
        if (user == null) return;
        ensureAiChatReference(user);
        if (aiChatRef == null) return;

        long now = System.currentTimeMillis();
        String messageId = aiChatRef.child("messages").push().getKey();
        if (messageId == null) return;

        Map<String, Object> chatMessage = new HashMap<>();
        chatMessage.put("role", "user");
        chatMessage.put("sender", "user");
        chatMessage.put("senderUid", user.getUid());
        chatMessage.put("senderName", displayName(user));
        chatMessage.put("text", safe(text));
        chatMessage.put("createdAt", ServerValue.TIMESTAMP);
        chatMessage.put("clientAt", now);
        chatMessage.put("source", "app");

        Map<String, Object> updates = baseChatMetaUpdates(user);
        updates.put("messages/" + messageId, chatMessage);
        updates.put("meta/status", "active");
        updates.put("meta/lastMessage", safe(text));
        updates.put("meta/lastSender", "user");
        updates.put("meta/lastUserAt", ServerValue.TIMESTAMP);
        updates.put("meta/lastUserClientAt", now);
        updates.put("meta/updatedAt", ServerValue.TIMESTAMP);
        updates.put("meta/expiresAt", now + CHAT_EXPIRE_AFTER_MS);
        aiChatRef.updateChildren(updates).addOnFailureListener(error -> {
            Log.e(TAG, "Unable to save user AI chat.", error);
            Toast.makeText(this, "AI chat monitor sync failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
        });
        scheduleChatExpiry(now + CHAT_EXPIRE_AFTER_MS);
    }

    /** Simpan atau hantar data AssistantMessage. */
    private void saveAssistantMessage(String text, String source) {
        if (!USE_DATABASE_CHAT_STORE) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        ensureAiChatReference(user);
        if (aiChatRef == null) return;

        String messageId = aiChatRef.child("messages").push().getKey();
        if (messageId == null) return;

        Map<String, Object> chatMessage = new HashMap<>();
        chatMessage.put("role", "assistant");
        chatMessage.put("sender", "assistant");
        chatMessage.put("senderUid", user.getUid());
        chatMessage.put("senderName", getString(R.string.ai_chat_assistant));
        chatMessage.put("text", safe(text));
        chatMessage.put("createdAt", ServerValue.TIMESTAMP);
        chatMessage.put("clientAt", System.currentTimeMillis());
        chatMessage.put("source", safe(source).isEmpty() ? "assistant" : safe(source));

        Map<String, Object> updates = baseChatMetaUpdates(user);
        updates.put("messages/" + messageId, chatMessage);
        updates.put("meta/status", "answered");
        updates.put("meta/lastMessage", safe(text));
        updates.put("meta/lastSender", "assistant");
        updates.put("meta/updatedAt", ServerValue.TIMESTAMP);
        aiChatRef.updateChildren(updates).addOnFailureListener(error -> {
            Log.e(TAG, "Unable to save assistant AI chat.", error);
            Toast.makeText(this, "AI chat monitor sync failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void ensureAiChatReference(FirebaseUser user) {
        if (!USE_DATABASE_CHAT_STORE) return;
        if (user == null) return;
        if (aiChatRef != null && user.getUid().equals(currentUid)) return;
        currentUid = user.getUid();
        FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL).goOnline();
        aiChatRef = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                .getReference("supportChats")
                .child(currentUid)
                .child("aiAssistant");
        attachAiChatListeners();
    }

    private Map<String, Object> baseChatMetaUpdates(FirebaseUser user) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("meta/userName", displayName(user));
        updates.put("meta/userEmail", safe(user.getEmail()).isEmpty() ? safe(UserPrefs.getEmail(this)) : safe(user.getEmail()));
        updates.put("meta/publicId", safe(UserPrefs.getPublicId(this)));
        updates.put("meta/photoUrl", safe(UserPrefs.getPhotoUrl(this)));
        updates.put("meta/photoB64", safe(UserPrefs.getPhotoB64(this)));
        updates.put("meta/source", "ai-assistant");
        return updates;
    }

    /** Paparkan Name. */
    private String displayName(FirebaseUser user) {
        String name = safe(UserPrefs.getName(this));
        if (!name.isEmpty()) return name;
        if (user != null && !safe(user.getDisplayName()).isEmpty()) return safe(user.getDisplayName());
        if (user != null && !safe(user.getEmail()).isEmpty()) return safe(user.getEmail());
        return "User";
    }

    private void handleChatReply(String reply) {
        runAfterTypingDelay(() -> showAssistantReply(safe(reply), "faq"));
    }

    private void handleChatFailure(String message) {
        runAfterTypingDelay(() -> {
            String clean = safe(message);
            String reply = getString(R.string.ai_chat_login_required).equals(clean)
                    ? clean
                    : localAssistantReply(lastUserMessage());
            showAssistantReply(reply, "local-fallback");
        });
    }

    private void runAfterTypingDelay(Runnable action) {
        long elapsed = System.currentTimeMillis() - typingStartedAt;
        long delay = Math.max(0L, AI_TYPING_DELAY_MS - elapsed);
        if (pendingReplyRunnable != null) mainHandler.removeCallbacks(pendingReplyRunnable);
        pendingReplyRunnable = action;
        mainHandler.postDelayed(action, delay);
    }

    /** Bersihkan format markdown (seperti **) dan sebarang emoji daripada jawapan AI. */
    private String cleanAiReply(String text) {
        if (text == null) return "";
        // Buang markdown asterisks (** atau *)
        String cleaned = text.replace("**", "").replace("*", "");
        // Buang semua karakter emoji dan simbol grafik
        cleaned = cleaned.replaceAll("[\\p{So}\\p{Cn}\\p{Cs}\\x{1F300}-\\x{1F9FF}\\x{2600}-\\x{26FF}\\x{2700}-\\x{27BF}]", "");
        return cleaned.trim();
    }

    /** Paparkan AssistantReply. */
    private void showAssistantReply(String reply, String source) {
        removeTypingIndicator();
        String fullText = cleanAiReply(safe(reply));
        if (fullText.isEmpty()) fullText = getString(R.string.ai_chat_failed);

        ChatMessage message = new ChatMessage("assistant", "", System.currentTimeMillis());
        messages.add(message);
        TextView bubble = addMessageBubble(message);
        saveAssistantMessage(fullText, source);
        if (isLiveChatMode) {
            syncAssistantMessageToLiveSupport(message, fullText, source);
        }
        animateAssistantReply(message, bubble, fullText, source);
    }

    /** Kendalikan animasi AssistantReply. */
    private void animateAssistantReply(ChatMessage message, TextView bubble, String fullText, String source) {
        if (typewriterRunnable != null) mainHandler.removeCallbacks(typewriterRunnable);
        final int[] index = {0};
        final int step = fullText.length() > 240 ? 4 : (fullText.length() > 120 ? 2 : 1);

        typewriterRunnable = new Runnable() {
    @Override
            public void run() {
                int next = Math.min(fullText.length(), index[0] + step);
                message.content = fullText.substring(0, next);
                if (bubble != null) {
                    bubble.setVisibility(View.VISIBLE);
                    bubble.setText(message.content);
                }
                scrollToBottom();

                if (next < fullText.length()) {
                    index[0] = next;
                    mainHandler.postDelayed(this, TYPEWRITER_INTERVAL_MS);
                    return;
                }

                setSending(false);
                saveLocalMessages();
                saveCurrentSessionToHistory();
                typewriterRunnable = null;
            }
        };
        mainHandler.post(typewriterRunnable);
    }

    private String lastUserMessage() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage item = messages.get(i);
            if ("user".equals(item.role)) return item.content;
        }
        return "";
    }

    private String getFaqAnswer(String input) {
        String trimmed = safe(input).trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        if (awaitingLiveChatChoice) {
            if (lower.equals("y") || lower.equals("yes")) {
                awaitingLiveChatChoice = false;
                return "Meja Bantuan ResQTap (Admin) telah dimaklumkan. Anda boleh terus menghantar sebarang mesej atau lampiran di sini pada bila-bila masa!";
            } else if (lower.equals("n") || lower.equals("no")) {
                awaitingLiveChatChoice = false;
                return "Sila beritahu saya sekiranya ada soalan lain mengenai ciri aplikasi ResQTap atau bantuan kecemasan!";
            } else {
                return getString(R.string.faq_offer_livechat);
            }
        }

        switch (trimmed) {
            case "1":
                return getString(R.string.faq_ans_1);
            case "2":
                return getString(R.string.faq_ans_2);
            case "3":
                return getString(R.string.faq_ans_3);
            case "4":
                return getString(R.string.faq_ans_4);
            case "5":
                return getString(R.string.faq_ans_5);
            case "6":
                return getString(R.string.faq_ans_6);
            default:
                return localAssistantReply(trimmed);
        }
    }

    private String localAssistantReply(String prompt) {
        String q = safe(prompt).toLowerCase(Locale.ROOT);
        String language = detectLanguage(prompt);

        if (q.isEmpty() || isGreeting(q)) {
            return localizedReply(language, "greeting");
        }
        if (containsAny(q, "terima kasih", "thank you", "thanks", "tq", "syukran", "mantap", "terbaik", "good job", "hebat", "ok faham", "faham", "baiklah", "noted", "ok tq", "nice", "ok")) {
            return localizedReply(language, "thanks");
        }
        if (containsAny(q, "siapa awak", "who are you", "apa guna", "what is resqtap", "fungsi app", "what can you do", "buat apa tu", "apa boleh buat", "kenapa nama resqtap", "apa kelebihan", "overview", "ciri app", "features", "perkenalkan diri")) {
            return localizedReply(language, "identity");
        }
        if (containsAny(q, "tolong", "help", "bantu", "i need help", "panik", "cemas", "sesat", "lost", "bahaya", "danger", "takut", "apa patut buat")) {
            return localizedReply(language, "sos_help");
        }
        if (containsAny(q, "beacon", "suar", "lampu", "strobe", "flashing", "cahaya suar", "신호", "비콘")) {
            return localizedReply(language, "beacon");
        }
        if (containsAny(q, "geofence", "geofencing", "safe zone", "zon selamat", "perimeter", "keluar kawasan", "masuk kawasan", "radius", "sempadan")) {
            return localizedReply(language, "geofencing");
        }
        if (containsAny(q, "qr", "poster", "scan", "imbas", "share qr", "kawan", "friend", "friends", "add friend", "jemput", "kad qr", "kod qr")) {
            return localizedReply(language, "qr_share");
        }
        if (containsAny(q, "hospital", "klinik", "clinic", "rawatan", "doktor", "medical", "cedera", "kecederaan", "ubat", "perubatan", "pesakit", "ambulans", "ambulance", "병원", "근처")) {
            return localizedReply(language, "hospital");
        }
        if (containsAny(q, "sos", "one tap", "tekan sos", "hold 3", "3 saat", "butang sos", "hantar amaran", "siren", "batal sos", "cancel sos", "batal", "emergency", "alert", "cancel", "kecemasan", "긴급", "구조", "취소", "알림")) {
            return localizedReply(language, "sos");
        }
        if (containsAny(q, "location", "gps", "map", "peta", "lokasi", "track", "jejak", "live location", "koordinat", "realtime", "berdekatan", "위치", "지도")) {
            return localizedReply(language, "location");
        }
        if (containsAny(q, "room", "bilik", "kod bilik", "room code", "join room", "create room", "cipta bilik", "masuk bilik", "ahli bilik", "keluarga", "member", "sertai", "cipta", "방", "멤버", "참여", "생성")) {
            return localizedReply(language, "room");
        }
        if (containsAny(q, "contact", "contacts", "kenalan", "telefon", "nombor kecemasan", "hotline", "999", "polis", "bomba", "phone", "speed dial", "family", "keluarga", "연락처", "가족", "전화")) {
            return localizedReply(language, "contacts");
        }
        if (containsAny(q, "offline", "internet", "tiada line", "no internet", "data habis", "luar talian", "sms fallback", "tiada data", "kuota")) {
            return localizedReply(language, "offline");
        }
        if (containsAny(q, "notification", "notify", "bell", "unread", "notifikasi", "pemberitahuan", "loceng", "bunyi amaran", "siren alert", "알림", "읽지")) {
            return localizedReply(language, "notifications");
        }
        if (containsAny(q, "dark mode", "mod gelap", "tema", "theme", "setting", "settings", "language", "bahasa", "tukar bahasa", "battery", "profile", "tetapan", "bateri", "profil", "설정", "언어", "배터리", "프로필")) {
            return localizedReply(language, "settings");
        }
        if (containsAny(q, "login", "register", "password", "kata laluan", "akaun", "account", "daftar", "log masuk", "lupa password", "forgot password", "로그인", "가입", "비밀번호", "계정")) {
            return localizedReply(language, "account");
        }
        if (containsAny(q, "livechat", "support", "admin", "bantuan admin", "helpdesk", "sokongan", "hubungi admin", "customer service", "도움", "지원", "관리자")) {
            return localizedReply(language, "support");
        }
        if (containsAny(q, "tts", "text to speech", "quick message", "mesej pantas", "suara", "audio", "bisu", "mute", "tak boleh cakap", "message", "mesej", "teks", "음성", "메시지")) {
            return localizedReply(language, "message");
        }
        if (containsAny(q, "error", "problem", "issue", "not working", "troubleshoot", "ralat", "masalah", "tak berfungsi", "crash", "stuck", "sangkit", "rosak", "안됨", "오류", "문제")) {
            return localizedReply(language, "troubleshoot");
        }

        return localizedReply(language, "general");
    }

    /** Semak dan sahkan Greeting. */
    private boolean isGreeting(String q) {
        return q.equals("hi")
                || q.equals("hello")
                || q.equals("hey")
                || q.equals("hai")
                || q.equals("helo")
                || q.equals("salam")
                || q.equals("assalamualaikum")
                || q.equals("selamat pagi")
                || q.equals("selamat petang")
                || q.equals("selamat malam")
                || q.equals("good morning")
                || q.equals("good afternoon")
                || q.equals("good evening")
                || q.equals("good night")
                || q.equals("안녕")
                || q.equals("안녕하세요");
    }

    /** Semak dan sahkan ResQTapQuestion. */
    private boolean isResQTapQuestion(String q) {
        return true;
    }

    private String detectLanguage(String prompt) {
        String raw = safe(prompt);
        String q = raw.toLowerCase(Locale.ROOT);
        if (containsHangul(raw)) return "ko";
        if (containsAny(q,
                "apa", "macam", "bagaimana", "tolong", "bantu", "saya", "nak", "tak",
                "tidak", "kenapa", "guna", "cara", "tetapan", "bahasa", "lokasi",
                "kecemasan", "hospital", "akaun", "daftar", "masuk", "kata laluan",
                "bateri", "hubungi", "notifikasi", "pemberitahuan", "bilik", "kenalan",
                "suar", "zon", "kawan", "terima", "kasih", "faham", "siapa", "buat")) {
            return "ms";
        }
        String savedLang = com.example.resqtap.utils.LocaleUtils.getSavedLanguageTag(this);
        return (savedLang != null && !savedLang.isEmpty()) ? savedLang : "en";
    }

    private boolean containsHangul(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '\uAC00' && c <= '\uD7AF') return true;
            if (c >= '\u1100' && c <= '\u11FF') return true;
            if (c >= '\u3130' && c <= '\u318F') return true;
        }
        return false;
    }

    private String localizedReply(String language, String key) {
        if ("ms".equals(language)) {
            if ("greeting".equals(key)) return "Hai! Saya Pembantu Keselamatan Pintar ResQTap anda. Saya sedia membantu anda menguruskan ciri kecemasan, penjejakan lokasi bilik, zon selamat, dan panduan keselamatan bila-bila masa. Ada apa yang boleh saya bantu?";
            if ("thanks".equals(key)) return "Sama-sama! Keselamatan anda adalah keutamaan kami. Jika anda perlukan sebarang bantuan atau panduan mengenai ciri ResQTap, saya sentiasa bersedia 24/7.";
            if ("identity".equals(key)) return "Saya adalah Pembantu AI Rasmi ResQTap. Aplikasi ResQTap direka khusus untuk melindungi anda dan keluarga melalui:\n\n• One-Tap SOS: Penyiaran isyarat kecemasan pantas dalam 3 saat bersama GPS masa nyata.\n• Suar SOS (Beacon): Amaran siren nyaring dan strob visual untuk menarik perhatian penyelamat.\n• Bilik Keselamatan (Room Hub): Perkongsian lokasi langsung dan penjejakan ahli keluarga di atas peta interaktif.\n• Poster Kod QR: Tambah kenalan dipercayai dan sertai bilik dengan mudah.\n• Zon Selamat Pintar (Geofencing): Amaran automatik apabila ahli keluarga masuk atau keluar kawasan selamat.";
            if ("sos_help".equals(key)) return "Langkah Tindakan Kecemasan Segera:\n\n1. Bertenang dan cari tempat perlindungan yang selamat.\n2. Tekan dan tahan butang SOS pada skrin utama selama 3 saat untuk menyiarkan isyarat kecemasan segera.\n3. Sistem akan menghantar koordinat GPS masa nyata anda kepada semua kenalan kecemasan dan ahli Bilik (Room).\n4. Anda juga boleh membuka menu Hospital untuk mendapatkan laluan navigasi ke klinik atau hospital terdekat.";
            if ("sos".equals(key)) return "Panduan Ciri One-Tap SOS:\n\n• Pengaktifan: Tekan dan tahan butang SOS besar selama 3 saat untuk mengelakkan amaran palsu.\n• Penyiaran Automatik: Menghantar mesej amaran kecemasan, siren notifikasi, dan koordinat GPS langsung ke semua kenalan kecemasan dan bilik anda.\n• Pembatalan Amaran: Jika situasi telah selamat atau amaran tidak sengaja ditekan, leret atau tekan butang Batal SOS untuk menamatkan isyarat.";
            if ("beacon".equals(key)) return "Cara Suar SOS (SOS Beacon) Berfungsi:\n\n• Penyiaran Lokasi Langsung: Menyiarkan isyarat kecemasan berkeutamaan tinggi bersama koordinat GPS tepat anda.\n• Peta Bilik Masa Nyata: Ahli bilik dapat melihat pergerakan dan kedudukan anda di atas peta secara langsung.\n• Amaran Siren Nyaring: Ahli bilik dan kenalan kecemasan akan menerima notifikasi kecemasan berbunyi siren.\n• Strob Cahaya Suar: Membantu pasukan penyelamat melihat kedudukan anda dalam kegelapan.";
            if ("geofencing".equals(key)) return "Panduan Zon Selamat Pintar (Smart Geofencing):\n\n• Fungsi: Membolehkan anda menetapkan perimeter zon selamat (contohnya Rumah, Sekolah, atau Pejabat) untuk ahli bilik.\n• Notifikasi Automatik: Anda akan menerima pemberitahuan automatik serta-merta apabila ahli keluarga tiba atau meninggalkan zon selamat tersebut.\n• Cara Guna: Buka tab Room dan pilih tetapan Geofencing untuk menetapkan jejari zon selamat pada peta.";
            if ("qr_share".equals(key)) return "Panduan Kod QR & Poster Rakan:\n\n• Imbas Kod QR: Buka tab Friends / Contacts dan tekan ikon kamera untuk mengimbas kod QR rakan bagi menambah kenalan serta-merta.\n• Poster Perkongsian: Anda boleh menjana poster profil dengan kod QR beresolusi tinggi untuk dikongsi ke WhatsApp atau media sosial bagi menjemput keluarga menyertai bilik keselamatan anda.";
            if ("hospital".equals(key)) return "Panduan Pencari Hospital & Klinik Berdekatan:\n\n• Buka tab Hospital pada menu utama.\n• Aplikasi akan menyenaraikan fasiliti perubatan terdekat berdasarkan kedudukan GPS anda.\n• Anda boleh menekan butang Navigasi untuk panduan arah peta atau butang Telefon untuk menghubungi talian kecemasan hospital tersebut secara langsung.";
            if ("location".equals(key)) return "Panduan GPS & Penjejakan Peta Masa Nyata:\n\n• Keperluan Akses: Pastikan kebenaran lokasi ditetapkan kepada Sentiasa Benarkan (Always Allow) dalam tetapan telefon anda.\n• Kegunaan: ResQTap menggunakan GPS untuk penyiaran amaran SOS masa nyata, penjejakan ahli bilik di atas peta interaktif, dan navigasi ke fasiliti perubatan terdekat.";
            if ("room".equals(key)) return "Panduan Bilik Keselamatan (Room Hub):\n\n• Cipta Bilik: Buka tab Room dan tekan Cipta Bilik untuk menjana kod bilik 6-digit yang unik.\n• Sertai Bilik: Masukkan kod 6-digit atau imbas kod QR bilik daripada keluarga/rakan anda.\n• Pemantauan Bersama: Semua ahli dalam bilik dapat melihat lokasi langsung sesama sendiri dan menerima amaran kecemasan serta-merta jika mana-mana ahli mengaktifkan SOS.";
            if ("contacts".equals(key)) return "Panduan Kenalan Kecemasan & Talian Bantuan 999:\n\n1. Buka tab Friends / Contacts di bar navigasi.\n2. Tekan butang + untuk menambah nombor telefon kenalan dipercayai.\n3. Talian Kecemasan Nasional: Anda juga boleh membuat panggilan terus ke talian 999 (Polis, Ambulans, Bomba) secara satu sentuhan melalui menu kecemasan.";
            if ("offline".equals(key)) return "Mod Luar Talian & Sandaran SMS (Offline Fallback):\n\n• Jika tiada sambungan data internet semasa kecemasan, ResQTap akan menggunakan sistem sandaran SMS untuk menghantar koordinat GPS terakhir anda terus ke nombor telefon kenalan kecemasan anda.";
            if ("notifications".equals(key)) return "Panduan Notifikasi & Amaran Siren:\n\n• Pastikan kebenaran notifikasi diaktifkan dalam tetapan telefon anda.\n• ResQTap menggunakan saluran amaran berkeutamaan tinggi (High Priority Alert) supaya siren amaran kecemasan tetap berbunyi walaupun telefon berada dalam mod Jangan Ganggu (DND).";
            if ("settings".equals(key)) return "Panduan Tetapan, Bahasa & Mod Gelap:\n\n• Mod Gelap (Dark Mode): Anda boleh menukar tema kepada Mod Gelap untuk menjimatkan bateri dan keselesaan visual pada waktu malam.\n• Pilihan Bahasa: Buka tetapan Profile untuk menukar bahasa antara Bahasa Melayu dan Bahasa Inggeris.\n• Maklumat Perubatan: Anda boleh mengisi maklumat jenis darah dan alahan pada profil kecemasan anda.";
            if ("account".equals(key)) return "Panduan Akaun & Keselamatan:\n\n• Log Masuk / Daftar: Gunakan emel dan kata laluan untuk mengakses akaun anda.\n• Lupa Kata Laluan: Tekan pautan Forgot Password pada skrin login untuk menerima pautan penetapan semula kata laluan melalui emel anda.";
            if ("support".equals(key)) return "Sokongan Langsung (Livechat Admin):\n\n• Buka menu Help & Support untuk berhubung secara langsung dengan pasukan admin teknikal ResQTap jika anda memerlukan bantuan lanjut.";
            if ("message".equals(key)) return "Panduan Text-to-Speech & Mesej Pantas (TTS):\n\n• Ciri ini membolehkan anda memainkan pesanan suara ringkas yang telah disediakan terlebih dahulu ketika anda sukar atau tidak selamat untuk bersuara dalam situasi cemas.";
            if ("troubleshoot".equals(key)) return "Penyelesaian Masalah Teknikal:\n\n1. Pastikan GPS dan perkhidmatan lokasi dihidupkan.\n2. Tetapkan kebenaran bateri kepada Tidak Dihadkan (Unrestricted) agar penjejakan di latar belakang tidak dimatikan.\n3. Periksa sambungan data internet atau gunakan sandaran SMS jika berada di kawasan luar liputan.";
            return "Saya faham apa yang anda maksudkan. Sebagai Pembantu Pintar ResQTap, saya sedia membantu anda dalam pelbagai aspek keselamatan dan pengurusan kecemasan.\n\nAntara ciri utama yang boleh anda terokai:\n• One-Tap SOS: Tahan butang SOS selama 3 saat untuk menyiarkan amaran kecemasan.\n• SOS Beacon: Aktifkan suar kecemasan berlampu strob dan siren amaran.\n• Bilik Keselamatan (Room): Cipta bilik bersama keluarga dan pantau lokasi GPS langsung di peta.\n• Kod QR & Poster: Imbas kod QR untuk menambah kenalan dan berkongsi bilik.\n• Zon Selamat (Geofencing): Terima notifikasi automatik apabila ahli bilik tiba di destinasi.\n\nAda apa-apa ciri khusus yang ingin anda ketahui lebih lanjut?";
        }
        if ("ko".equals(language)) {
            if ("greeting".equals(key)) return "안녕하세요! ResQTap 스마트 안전 어시스턴트입니다. 긴급 SOS, 방 기능, 비상 연락처, 병원 찾기 등에 대해 도와드릴 수 있습니다. 무엇을 도와드릴까요?";
            if ("thanks".equals(key)) return "천만에요! 사용자의 안전이 저희의 최우선입니다. 도움이 필요하시면 언제든 말씀해 주세요.";
            if ("sos".equals(key)) return "긴급 상황 안내:\n\n1. 침착함을 유지하고 안전한 위치를 확보하세요.\n2. 홈 화면의 SOS 버튼을 3초간 길게 누르면 즉시 긴급 알림이 전송됩니다.\n3. 실시간 GPS 위치가 방 멤버와 비상 연락처에 전달됩니다.";
            if ("beacon".equals(key)) return "SOS 비콘 작동 원리:\n\n• 비콘 활성화 시 실시간 위치가 방 지도에 표시됩니다.\n• 방 멤버에게 즉각적인 사이렌 알림이 전송됩니다.\n• 언제든지 SOS 취소 버튼으로 알림을 중단할 수 있습니다.";
            return "ResQTap 긴급 기능, 비콘, 위치, 방, 연락처, 병원 찾기에 대해 질문해 주세요.";
        }

        if ("greeting".equals(key)) return "Hello! I am your ResQTap Intelligent Safety Assistant. I am here to guide you through emergency features, room tracking, safe zones, and personal protection. How can I assist you today?";
        if ("thanks".equals(key)) return "You are very welcome! Your safety and peace of mind are our top priority. Feel free to ask anytime if you need help exploring ResQTap.";
        if ("identity".equals(key)) return "I am the official ResQTap AI Safety Assistant. ResQTap is designed to keep you and your loved ones secure through:\n\n• One-Tap SOS: Rapid emergency distress broadcast in 3 seconds with real-time GPS.\n• SOS Beacon: High-visibility strobe light and siren alarm for rapid rescue spotting.\n• Safety Rooms (Room Hub): Live member location tracking and mutual alerts on an interactive map.\n• QR Share Posters: Instant friend connections and seamless room invites via QR scan.\n• Smart Geofencing: Automated arrival and departure alerts for home, school, or work safe zones.";
        if ("sos_help".equals(key)) return "Immediate Emergency Response Steps:\n\n1. Stay calm and move to a safe, secure location.\n2. Press and hold the SOS button on the home screen for 3 seconds to broadcast an instant emergency signal.\n3. Your live GPS coordinates will be automatically transmitted to all trusted contacts and Room members.\n4. Open the Hospital tab to locate and navigate to the nearest medical clinic or hospital.";
        if ("sos".equals(key)) return "One-Tap SOS Guide:\n\n• Triggering: Press and hold the SOS button for 3 seconds to prevent accidental presses.\n• Automated Broadcast: Instantly dispatches loud siren notifications, distress messages, and live GPS coordinates to your emergency contacts and room members.\n• Cancelling SOS: If you are safe, slide or tap the Cancel SOS button to terminate the distress signal.";
        if ("beacon".equals(key)) return "How SOS Beacon Works:\n\n• Live Distress Broadcast: Transmits a high-priority distress signal with your exact GPS coordinates.\n• Real-Time Room Map: Room members can track your live movement on an interactive map.\n• Loud Siren Alert: Emergency contacts and room members receive immediate siren alarms.\n• Visual Strobe Beacon: Flashes your screen/light to help rescue teams spot your exact location in the dark.";
        if ("geofencing".equals(key)) return "Smart Geofencing Guide:\n\n• Safe Zone Perimeters: Set designated safe areas (such as Home, School, or Office) for room members.\n• Automated Alerts: Receive instant push notifications whenever family members enter or leave designated safe zones.\n• How to Use: Open the Room tab and access Geofencing settings to adjust safe zone radiuses on the map.";
        if ("qr_share".equals(key)) return "QR Code & Friend Poster Guide:\n\n• Scan QR Code: Open the Friends / Contacts tab and tap the camera icon to scan a friend's QR code and connect instantly.\n• Poster Sharing: Generate a high-resolution QR Poster to share on WhatsApp or social media for instant room invitations.";
        if ("hospital".equals(key)) return "Nearby Hospital & Medical Finder:\n\n• Open the Hospital tab on the main screen.\n• View all nearby clinics and emergency medical centers sorted by distance from your current GPS position.\n• Tap Navigate for turn-by-turn directions or tap Call to contact the hospital emergency desk directly.";
        if ("location".equals(key)) return "GPS & Real-Time Map Tracking:\n\n• Permissions: Make sure location permission is set to Always Allow in your Android settings for reliable tracking.\n• Capabilities: Powers live SOS broadcasts, real-time family tracking inside safety rooms, and nearby clinic discovery.";
        if ("room".equals(key)) return "Safety Rooms (Room Hub):\n\n• Create a Room: Tap Create Room to generate a unique 6-digit room code.\n• Join a Room: Enter a 6-digit room code or scan a room QR code from family or friends.\n• Live Monitoring: All members in a room can view each other's live map position and receive immediate group emergency alerts.";
        if ("contacts".equals(key)) return "Emergency Contacts & 999 Hotline:\n\n1. Open the Friends / Contacts tab on the bottom navigation bar.\n2. Tap + to add trusted family and emergency phone numbers.\n3. National Emergency Hotline: Tap the emergency dialer to instantly reach national emergency services (999/112).";
        if ("offline".equals(key)) return "Offline Mode & SMS Fallback:\n\n• If internet connectivity is unavailable during a crisis, ResQTap automatically switches to SMS Fallback to dispatch your last known GPS coordinates to emergency contacts.";
        if ("notifications".equals(key)) return "Notifications & Siren Alarms:\n\n• Ensure notification permissions are enabled in Android settings.\n• ResQTap utilizes high-priority alert channels so emergency sirens can sound even when your phone is in Do Not Disturb (DND) mode.";
        if ("settings".equals(key)) return "Settings, Language & Dark Mode:\n\n• Dark Mode: Switch between Light and Dark themes for enhanced battery longevity and night-time viewing.\n• Language: Switch between English and Bahasa Melayu in your Profile settings.\n• Medical Details: Save your blood type and allergies in your profile for emergency first responders.";
        if ("account".equals(key)) return "Account & Security:\n\n• Login & Register: Access your account securely with email and password.\n• Password Recovery: Use the Forgot Password option on the login screen to receive a secure password reset link.";
        if ("support".equals(key)) return "Live Support (Admin Helpdesk):\n\n• Navigate to Help & Support to chat live with ResQTap technical support agents.";
        if ("message".equals(key)) return "TTS & Quick Crisis Messages:\n\n• Text-To-Speech allows you to play pre-configured emergency voice messages when speaking out loud is unsafe or impossible.";
        if ("troubleshoot".equals(key)) return "Troubleshooting & Optimization:\n\n1. Verify GPS and location services are enabled.\n2. Set battery optimization to Unrestricted for uninterrupted background safety monitoring.\n3. Verify internet connectivity or rely on SMS fallback in remote areas.";
        return "I understand your query. As the ResQTap Intelligent Safety Assistant, I am designed to assist you across all safety, tracking, and emergency preparedness needs.\n\nHere are core features you can explore:\n• One-Tap SOS: Press and hold the SOS button for 3 seconds to trigger an instant emergency broadcast.\n• SOS Beacon: Activate a visual strobe beacon and loud alarm for rapid rescue spotting.\n• Safety Rooms: Form private rooms with loved ones and monitor live GPS movement on an interactive map.\n• QR Codes & Posters: Scan QR codes to easily connect trusted friends and share room invites.\n• Smart Geofencing: Get automated entry/exit alerts when members arrive at safe zones.\n\nWhich feature would you like to know more about?";
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }

    private void renderMessages() {
        if (heroGreetingLayout != null) {
            heroGreetingLayout.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
        }
        messagesContainer.removeAllViews();
        for (ChatMessage message : messages) {
            addMessageBubble(message);
        }
        if (typingView != null) messagesContainer.addView(typingView);
        scrollToBottom();
    }

    private TextView addMessageBubble(ChatMessage message) {
        if ("system".equalsIgnoreCase(message.role)) {
            addSystemNoticeBubble(message.content);
            return null;
        }
        if ("admin".equalsIgnoreCase(message.role)) {
            return addAdminMessageBubble(message);
        }

        boolean mine = "user".equals(message.role);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(mine ? Gravity.END : Gravity.START);
        row.setPadding(0, 0, 0, dp(12));

        TextView label = new TextView(this);
        label.setText(mine ? R.string.ai_chat_you : R.string.ai_chat_assistant);
        label.setTextColor(ContextCompat.getColor(this, mine ? R.color.brand_primary : R.color.text_secondary));
        label.setTextSize(11);
        label.setGravity(mine ? Gravity.END : Gravity.START);
        row.addView(label);

        LinearLayout bubbleWrap = new LinearLayout(this);
        bubbleWrap.setOrientation(LinearLayout.VERTICAL);
        bubbleWrap.setBackgroundResource(mine ? R.drawable.bg_livechat_bubble_user : R.drawable.bg_ai_chat_bubble_assistant_light);
        bubbleWrap.setPadding(dp(14), dp(10), dp(14), dp(10));
        int bubbleMaxWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.78f);

        TextView bubble = new TextView(this);
        bubble.setText(message.content);
        bubble.setTextSize(14);
        bubble.setLineSpacing(0, 1.15f);
        bubble.setMaxWidth(bubbleMaxWidth);
        bubble.setTextColor(ContextCompat.getColor(this, mine ? R.color.white : R.color.text_primary));
        bubbleWrap.addView(bubble);
        if (message.content.isEmpty() && !safe(message.attachmentUrl).isEmpty()) {
            bubble.setVisibility(View.GONE);
        }

        if (!safe(message.attachmentUrl).isEmpty()) {
            if (message.attachmentUrl.startsWith("data:image/")) {
                Bitmap img = bitmapFromDataUrl(message.attachmentUrl);
                if (img != null) {
                    ImageView preview = new ImageView(this);
                    preview.setAdjustViewBounds(true);
                    preview.setMaxWidth(bubbleMaxWidth);
                    preview.setMaxHeight(dp(220));
                    preview.setImageBitmap(img);
                    preview.setPadding(0, message.content.isEmpty() ? 0 : dp(8), 0, 0);
                    bubbleWrap.addView(preview, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    ));
                }
            } else {
                Button attBtn = new Button(this);
                attBtn.setAllCaps(false);
                attBtn.setText(message.attachmentName.isEmpty() ? getString(R.string.support_livechat_open_attachment) : message.attachmentName);
                attBtn.setTextSize(12);
                attBtn.setTextColor(ContextCompat.getColor(this, mine ? R.color.white : R.color.brand_primary));
                attBtn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                attBtn.setMaxWidth(bubbleMaxWidth);
                attBtn.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
                attBtn.setPadding(0, message.content.isEmpty() ? 0 : dp(6), 0, 0);
                attBtn.setOnClickListener(v -> openAttachment(message.attachmentUrl, message.attachmentMime));
                bubbleWrap.addView(attBtn, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
            }
        }

        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bubbleParams.topMargin = dp(4);
        row.addView(bubbleWrap, bubbleParams);

        TextView time = new TextView(this);
        time.setText(formatTime(message.createdAt));
        time.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        time.setTextSize(11);
        time.setGravity(mine ? Gravity.END : Gravity.START);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        timeParams.topMargin = dp(4);
        row.addView(time, timeParams);

        // Tambah Butang Like & Dislike (Thumbs Up & Thumbs Down) di bawah jawapan AI Assistant
        if (!mine) {
            LinearLayout feedbackRow = new LinearLayout(this);
            feedbackRow.setOrientation(LinearLayout.HORIZONTAL);
            feedbackRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams feedbackParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            feedbackParams.topMargin = dp(6);

            ImageButton btnLike = new ImageButton(this);
            btnLike.setImageResource(R.drawable.ic_thumb_up_24);
            btnLike.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary));
            btnLike.setBackgroundResource(R.drawable.bg_feedback_btn_unselected);
            btnLike.setPadding(dp(7), dp(7), dp(7), dp(7));
            LinearLayout.LayoutParams likeParams = new LinearLayout.LayoutParams(dp(32), dp(32));
            btnLike.setLayoutParams(likeParams);

            ImageButton btnDislike = new ImageButton(this);
            btnDislike.setImageResource(R.drawable.ic_thumb_down_24);
            btnDislike.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary));
            btnDislike.setBackgroundResource(R.drawable.bg_feedback_btn_unselected);
            btnDislike.setPadding(dp(7), dp(7), dp(7), dp(7));
            LinearLayout.LayoutParams dislikeParams = new LinearLayout.LayoutParams(dp(32), dp(32));
            dislikeParams.setMarginStart(dp(8));
            dislikeParams.leftMargin = dp(8);
            btnDislike.setLayoutParams(dislikeParams);

            final boolean[] isLiked = {false};
            final boolean[] isDisliked = {false};

            btnLike.setOnClickListener(v -> {
                if (isLiked[0]) {
                    isLiked[0] = false;
                    btnLike.setBackgroundResource(R.drawable.bg_feedback_btn_unselected);
                    btnLike.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary));
                } else {
                    isLiked[0] = true;
                    isDisliked[0] = false;
                    btnLike.setBackgroundResource(R.drawable.bg_feedback_btn_selected);
                    btnLike.setColorFilter(ContextCompat.getColor(this, R.color.brand_primary));
                    btnDislike.setBackgroundResource(R.drawable.bg_feedback_btn_unselected);
                    btnDislike.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary));
                    Toast.makeText(this, getString(R.string.ai_feedback_thanks), Toast.LENGTH_SHORT).show();
                }
            });

            btnDislike.setOnClickListener(v -> {
                if (isDisliked[0]) {
                    isDisliked[0] = false;
                    btnDislike.setBackgroundResource(R.drawable.bg_feedback_btn_unselected);
                    btnDislike.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary));
                } else {
                    isDisliked[0] = true;
                    isLiked[0] = false;
                    btnDislike.setBackgroundResource(R.drawable.bg_feedback_btn_selected);
                    btnDislike.setColorFilter(ContextCompat.getColor(this, R.color.brand_primary));
                    btnLike.setBackgroundResource(R.drawable.bg_feedback_btn_unselected);
                    btnLike.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary));
                    Toast.makeText(this, getString(R.string.ai_feedback_improve), Toast.LENGTH_SHORT).show();
                }
            });

            feedbackRow.addView(btnLike);
            feedbackRow.addView(btnDislike);
            row.addView(feedbackRow, feedbackParams);
        }

        messagesContainer.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return bubble;
    }

    private TextView addAdminMessageBubble(ChatMessage message) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.START);
        row.setPadding(0, 0, 0, dp(12));

        TextView label = new TextView(this);
        String name = safe(message.senderName);
        if (name.isEmpty() || "ResQTap".equalsIgnoreCase(name) || name.toLowerCase().contains("bot") || name.toLowerCase().contains("admin")) {
            name = getString(R.string.ai_chat_assistant);
        } else {
            name = getString(R.string.ai_chat_assistant);
        }
        label.setText(name);
        label.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
        label.setTextSize(11);
        label.setTypeface(null, Typeface.BOLD);
        row.addView(label);

        LinearLayout bubbleWrap = new LinearLayout(this);
        bubbleWrap.setOrientation(LinearLayout.VERTICAL);
        bubbleWrap.setBackgroundResource(R.drawable.bg_livechat_bubble_admin);
        bubbleWrap.setPadding(dp(14), dp(10), dp(14), dp(10));
        int bubbleMaxWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.78f);

        TextView messageView = new TextView(this);
        messageView.setText(message.content);
        messageView.setTextSize(14);
        messageView.setLineSpacing(0, 1.15f);
        messageView.setMaxWidth(bubbleMaxWidth);
        messageView.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        bubbleWrap.addView(messageView);
        if (message.content.isEmpty() && !safe(message.attachmentUrl).isEmpty()) {
            messageView.setVisibility(View.GONE);
        }

        if (!safe(message.attachmentUrl).isEmpty()) {
            if (message.attachmentUrl.startsWith("data:image/")) {
                Bitmap img = bitmapFromDataUrl(message.attachmentUrl);
                if (img != null) {
                    ImageView preview = new ImageView(this);
                    preview.setAdjustViewBounds(true);
                    preview.setMaxWidth(bubbleMaxWidth);
                    preview.setMaxHeight(dp(220));
                    preview.setImageBitmap(img);
                    preview.setPadding(0, message.content.isEmpty() ? 0 : dp(8), 0, 0);
                    bubbleWrap.addView(preview, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    ));
                }
            } else {
                Button attBtn = new Button(this);
                attBtn.setAllCaps(false);
                attBtn.setText(message.attachmentName.isEmpty() ? getString(R.string.support_livechat_open_attachment) : message.attachmentName);
                attBtn.setTextSize(12);
                attBtn.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
                attBtn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                attBtn.setMaxWidth(bubbleMaxWidth);
                attBtn.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
                attBtn.setPadding(0, message.content.isEmpty() ? 0 : dp(6), 0, 0);
                attBtn.setOnClickListener(v -> openAttachment(message.attachmentUrl, message.attachmentMime));
                bubbleWrap.addView(attBtn, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
            }
        }

        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bubbleParams.topMargin = dp(4);
        row.addView(bubbleWrap, bubbleParams);

        String timeStr = formatTime(message.createdAt);
        if (!timeStr.isEmpty()) {
            TextView time = new TextView(this);
            time.setText(timeStr);
            time.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            time.setTextSize(11);
            time.setGravity(Gravity.START);
            LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            timeParams.topMargin = dp(4);
            row.addView(time, timeParams);
        }

        messagesContainer.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return messageView;
    }

    private void addSystemNoticeBubble(String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(12), dp(8), dp(12), dp(12));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER);
        card.setBackgroundResource(R.drawable.bg_card_admin_transfer);
        card.setPadding(dp(18), dp(13), dp(18), dp(13));

        TextView desc = new TextView(this);
        String descText = (text == null || text.trim().isEmpty()) ? getString(R.string.chat_transferred_to_admin_desc) : text.trim();
        desc.setText(descText);
        desc.setTextSize(12.5f);
        desc.setGravity(Gravity.CENTER);
        desc.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
        desc.setTypeface(null, Typeface.NORMAL);
        desc.setLineSpacing(0, 1.25f);

        card.addView(desc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row.addView(card, cardParams);
        messagesContainer.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Paparkan proses berfikir & penyelesaian masalah AI (Thinking & Troubleshooting Process). */
    private void showThinkingIndicator(String userPrompt) {
        removeTypingIndicator();
        typingStartedAt = System.currentTimeMillis();

        String q = safe(userPrompt).toLowerCase(Locale.ROOT);
        String lang = detectLanguage(userPrompt);
        boolean isMs = "ms".equals(lang);

        thinkingSteps.clear();
        if (containsAny(q, "error", "problem", "issue", "not working", "troubleshoot", "ralat", "masalah", "tak berfungsi", "rosak")) {
            if (isMs) {
                thinkingSteps.add("Mendiagnosis pertanyaan & isu sistem...");
                thinkingSteps.add("Menjalankan analisis pangkalan data & log...");
                thinkingSteps.add("Menguji keserasian modul & integrasi aplikasi...");
                thinkingSteps.add("Mengesahkan rumusan dan langkah penyelesaian...");
            } else {
                thinkingSteps.add("Diagnosing system & issue context...");
                thinkingSteps.add("Running database diagnostics & logs...");
                thinkingSteps.add("Testing module compatibility & app integration...");
                thinkingSteps.add("Verifying solution steps & resolution...");
            }
        } else if (containsAny(q, "sos", "beacon", "emergency", "suar", "kecemasan", "cemas")) {
            if (isMs) {
                thinkingSteps.add("Menganalisis jenis kecemasan yang dilaporkan...");
                thinkingSteps.add("Menyemak koordinat GPS & protokol keselamatan...");
                thinkingSteps.add("Memadankan panduan bertindak pantas & bantuan...");
                thinkingSteps.add("Menyusun langkah keselamatan keutamaan...");
            } else {
                thinkingSteps.add("Analyzing reported emergency context...");
                thinkingSteps.add("Cross-referencing GPS & safety database...");
                thinkingSteps.add("Matching rapid response guidelines & clinics...");
                thinkingSteps.add("Structuring priority safety protocols...");
            }
        } else if (containsAny(q, "livechat", "live chat", "admin", "sokongan", "support", "agent", "human", "pegawai", "bantuan admin")) {
            if (isMs) {
                thinkingSteps.add("Menghubungkan ke Meja Bantuan Sokongan...");
                thinkingSteps.add("Menyediakan maklumat sementara menunggu Live Agent...");
            } else {
                thinkingSteps.add("Connecting to Live Support Desk...");
                thinkingSteps.add("Preparing guidance while waiting for Live Agent...");
            }
        } else {
            if (isMs) {
                thinkingSteps.add("Memahami konteks pertanyaan anda...");
                thinkingSteps.add("Mencari panduan lengkap dalam arkib bantuan...");
                thinkingSteps.add("Menganalisis maklumat ciri keselamatan berkaitan...");
                thinkingSteps.add("Menyediakan jawapan terperinci...");
            } else {
                thinkingSteps.add("Understanding inquiry context...");
                thinkingSteps.add("Searching help archive & safety knowledge base...");
                thinkingSteps.add("Analyzing relevant safety features...");
                thinkingSteps.add("Formulating comprehensive response...");
            }
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.START);
        row.setPadding(0, 0, 0, dp(12));

        TextView label = new TextView(this);
        label.setText(R.string.ai_chat_assistant);
        label.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        label.setTextSize(11);
        row.addView(label);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(2), dp(4), dp(8), dp(4));

        View sparkleView = createGeminiSparkleView();
        card.addView(sparkleView);

        TextView tv = new TextView(this);
        tv.setText(thinkingSteps.get(0));
        tv.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        tv.setTextSize(13);
        tv.setTypeface(null, Typeface.ITALIC);
        card.addView(tv);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.topMargin = dp(4);
        row.addView(card, cardParams);

        typingView = row;
        thinkingStatusText = tv;
        messagesContainer.addView(typingView);
        scrollToBottom();

        // Jadualkan pertukaran langkah thinking secara dinamik merentasi ~6.8 saat (5-10 saat)
        currentThinkingStepIndex = 0;
        scheduleNextThinkingStep();
    }

    /** Cipta animasi bintang berkembar AI: bintang kecil membesar ke posisi bintang besar dan bintang besar bergerak mengecil ke posisi bintang kecil. */
    private View createGeminiSparkleView() {
        FrameLayout container = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(26), dp(26));
        lp.setMarginEnd(dp(10));
        lp.rightMargin = dp(10);
        container.setLayoutParams(lp);

        ImageView star1 = new ImageView(this);
        star1.setImageResource(R.drawable.ic_single_star_sparkle);
        star1.setColorFilter(ContextCompat.getColor(this, R.color.brand_primary));
        FrameLayout.LayoutParams p1 = new FrameLayout.LayoutParams(dp(16), dp(16));
        star1.setLayoutParams(p1);

        ImageView star2 = new ImageView(this);
        star2.setImageResource(R.drawable.ic_single_star_sparkle);
        star2.setColorFilter(ContextCompat.getColor(this, R.color.brand_primary));
        FrameLayout.LayoutParams p2 = new FrameLayout.LayoutParams(dp(16), dp(16));
        star2.setLayoutParams(p2);

        container.addView(star1);
        container.addView(star2);

        final float startX1 = 0f;
        final float startY1 = dp(7);
        final float endX1 = dp(10);
        final float endY1 = 0f;

        final float startScale1 = 1.0f;
        final float endScale1 = 0.42f;

        star1.setTranslationX(startX1);
        star1.setTranslationY(startY1);
        star1.setScaleX(startScale1);
        star1.setScaleY(startScale1);

        star2.setTranslationX(endX1);
        star2.setTranslationY(endY1);
        star2.setScaleX(endScale1);
        star2.setScaleY(endScale1);

        if (sparkleSwapAnimator != null) {
            sparkleSwapAnimator.cancel();
        }

        sparkleSwapAnimator = ValueAnimator.ofFloat(0f, 1f);
        sparkleSwapAnimator.setDuration(1100L);
        sparkleSwapAnimator.setRepeatCount(ValueAnimator.INFINITE);
        sparkleSwapAnimator.setRepeatMode(ValueAnimator.REVERSE);
        sparkleSwapAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        sparkleSwapAnimator.addUpdateListener(anim -> {
            float f = (float) anim.getAnimatedValue();

            // Star 1: Dari bawah-kiri ke atas-kanan sambil mengecil (1.0 -> 0.42)
            star1.setTranslationX(startX1 + (endX1 - startX1) * f);
            star1.setTranslationY(startY1 + (endY1 - startY1) * f);
            float scale1 = startScale1 + (endScale1 - startScale1) * f;
            star1.setScaleX(scale1);
            star1.setScaleY(scale1);
            star1.setRotation(f * 35f);

            // Star 2: Dari atas-kanan ke bawah-kiri sambil membesar (0.42 -> 1.0)
            star2.setTranslationX(endX1 + (startX1 - endX1) * f);
            star2.setTranslationY(endY1 + (startY1 - endY1) * f);
            float scale2 = endScale1 + (startScale1 - endScale1) * f;
            star2.setScaleX(scale2);
            star2.setScaleY(scale2);
            star2.setRotation(-f * 35f);
        });
        sparkleSwapAnimator.start();

        return container;
    }

    /** Jadualkan langkah proses berfikir AI secara berperingkat (8 saat: 4 langkah x 2 saat). */
    private void scheduleNextThinkingStep() {
        if (thinkingStepRunnable != null) {
            mainHandler.removeCallbacks(thinkingStepRunnable);
        }
        thinkingStepRunnable = new Runnable() {
            @Override
            public void run() {
                currentThinkingStepIndex++;
                if (currentThinkingStepIndex < thinkingSteps.size() && thinkingStatusText != null) {
                    thinkingStatusText.animate()
                            .alpha(0.2f)
                            .setDuration(160L)
                            .withEndAction(() -> {
                                if (thinkingStatusText != null && currentThinkingStepIndex < thinkingSteps.size()) {
                                    thinkingStatusText.setText(thinkingSteps.get(currentThinkingStepIndex));
                                    thinkingStatusText.animate().alpha(1f).setDuration(220L).start();
                                }
                            })
                            .start();
                    mainHandler.postDelayed(this, 2000L);
                }
            }
        };
        mainHandler.postDelayed(thinkingStepRunnable, 2000L);
    }

    /** Padam atau bersihkan ThinkingIndicator. */
    private void removeTypingIndicator() {
        if (sparkleSwapAnimator != null) {
            sparkleSwapAnimator.cancel();
            sparkleSwapAnimator = null;
        }
        if (thinkingStepRunnable != null) {
            mainHandler.removeCallbacks(thinkingStepRunnable);
            thinkingStepRunnable = null;
        }
        if (typingDotsRunnable != null) {
            mainHandler.removeCallbacks(typingDotsRunnable);
            typingDotsRunnable = null;
        }
        if (typingView == null) return;
        ViewGroup parent = (ViewGroup) typingView.getParent();
        if (parent != null) parent.removeView(typingView);
        typingView = null;
        typingTextView = null;
        thinkingStatusText = null;
    }

    private void setSending(boolean value) {
        sending = value;
        sendButton.setEnabled(!value);
        inputView.setEnabled(!value);
    }

    private void scrollToBottom() {
        messagesScroll.post(() -> messagesScroll.fullScroll(View.FOCUS_DOWN));
    }

    private String formatTime(long ms) {
        if (ms <= 0) return "";
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(ms));
    }

    /** Format tarikh dan waktu untuk paparan sejarah sesi. */
    private String formatDateTime(long ms) {
        if (ms <= 0) return "";
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault());
            return sdf.format(new Date(ms));
        } catch (Exception e) {
            return formatTime(ms);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Paparkan dialog Bottom Sheet untuk melihat Sejarah Sesi Perbualan (Recent Topic Sessions). */
    private void showRecentChatHistoryBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_ai_chat_history, null);
        dialog.setContentView(view);

        ImageButton btnClose = view.findViewById(R.id.btn_close_history);
        LinearLayout container = view.findViewById(R.id.history_container);
        View emptyState = view.findViewById(R.id.history_empty_state);
        View actionsLayout = view.findViewById(R.id.history_actions_layout);
        Button btnClear = view.findViewById(R.id.btn_clear_history);

        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        ArrayList<ChatSession> sessionList = loadAllSavedSessions();
        if (sessionList.isEmpty()) {
            if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
            if (actionsLayout != null) actionsLayout.setVisibility(View.GONE);
        } else {
            if (emptyState != null) emptyState.setVisibility(View.GONE);
            if (actionsLayout != null) actionsLayout.setVisibility(View.VISIBLE);
            populateSessionItems(container, sessionList, dialog);
        }

        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                clearAllChatSessions();
                if (container != null) container.removeAllViews();
                if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
                if (actionsLayout != null) actionsLayout.setVisibility(View.GONE);
                Toast.makeText(this, "All chat history cleared", Toast.LENGTH_SHORT).show();
            });
        }

        dialog.show();
    }

    /** Muat senarai kad sesi topik ke dalam LinearLayout container. */
    private void populateSessionItems(LinearLayout container, ArrayList<ChatSession> sessionList, BottomSheetDialog dialog) {
        if (container == null) return;
        container.removeAllViews();

        for (ChatSession session : sessionList) {
            View itemView = getLayoutInflater().inflate(R.layout.item_ai_chat_session, container, false);
            TextView txtTopic = itemView.findViewById(R.id.txt_session_topic);
            TextView txtMeta = itemView.findViewById(R.id.txt_session_meta);
            ImageButton btnDelete = itemView.findViewById(R.id.btn_delete_session);
            View card = itemView.findViewById(R.id.card_session_item);

            if (txtTopic != null) txtTopic.setText(session.title);
            if (txtMeta != null) {
                txtMeta.setText(formatDateTime(session.updatedAt));
            }

            // KLIK KAD SESI: AUTOMATIK MASUK SEMULA KE CHAT TERSEBUT!
            if (card != null) {
                card.setOnClickListener(v -> {
                    currentSessionId = session.id;
                    currentSessionTopic = session.title;
                    messages.clear();
                    messages.addAll(session.messages);
                    renderMessages();
                    dialog.dismiss();
                    scrollToBottom();
                    Toast.makeText(this, "Reopened: " + session.title, Toast.LENGTH_SHORT).show();
                });
            }

            if (btnDelete != null) {
                btnDelete.setOnClickListener(v -> {
                    deleteChatSession(session.id);
                    container.removeView(itemView);
                    if (container.getChildCount() == 0) {
                        View emptyState = dialog.findViewById(R.id.history_empty_state);
                        View actionsLayout = dialog.findViewById(R.id.history_actions_layout);
                        if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
                        if (actionsLayout != null) actionsLayout.setVisibility(View.GONE);
                    }
                    Toast.makeText(this, "Chat removed", Toast.LENGTH_SHORT).show();
                });
            }

            container.addView(itemView);
        }
    }

    /** Simpan atau kemas kini sesi perbualan semasa ke dalam senarai sejarah sesi perbualan. */
    private synchronized void saveCurrentSessionToHistory() {
        if (currentSessionId.isEmpty() || messages.isEmpty()) return;
        try {
            ArrayList<ChatSession> sessions = loadAllSavedSessions();
            ChatSession match = null;
            for (ChatSession s : sessions) {
                if (currentSessionId.equals(s.id)) {
                    match = s;
                    break;
                }
            }

            if (match != null) {
                match.updatedAt = System.currentTimeMillis();
                match.messages = new ArrayList<>(messages);
                if (match.title.isEmpty() || "Emergency Query".equals(match.title)) {
                    match.title = currentSessionTopic;
                }
            } else {
                ChatSession newSession = new ChatSession(
                        currentSessionId,
                        currentSessionTopic.isEmpty() ? "Emergency Query" : currentSessionTopic,
                        System.currentTimeMillis(),
                        new ArrayList<>(messages)
                );
                sessions.add(0, newSession);
            }

            if (sessions.size() > 40) {
                sessions = new ArrayList<>(sessions.subList(0, 40));
            }
            saveAllSessions(sessions);
        } catch (Exception ignored) {}
    }

    /** Muat semua sesi perbualan tersimpan daripada SharedPreferences. */
    private ArrayList<ChatSession> loadAllSavedSessions() {
        ArrayList<ChatSession> list = new ArrayList<>();
        try {
            String jsonStr = chatPrefs().getString(CHAT_PREF_SAVED_SESSIONS, "[]");
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) continue;
                String id = safe(obj.optString("id"));
                String title = safe(obj.optString("title"));
                long updatedAt = obj.optLong("updatedAt", 0L);

                ArrayList<ChatMessage> msgs = new ArrayList<>();
                JSONArray msgsArr = obj.optJSONArray("messages");
                if (msgsArr != null) {
                    for (int j = 0; j < msgsArr.length(); j++) {
                        JSONObject mObj = msgsArr.optJSONObject(j);
                        if (mObj == null) continue;
                        String role = safe(mObj.optString("role"));
                        String content = safe(mObj.optString("content"));
                        long createdAt = mObj.optLong("createdAt", 0L);
                        if (!content.isEmpty()) {
                            msgs.add(new ChatMessage(role, content, createdAt));
                        }
                    }
                }
                if (!msgs.isEmpty()) {
                    list.add(new ChatSession(id, title, updatedAt, msgs));
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    /** Simpan senarai sesi perbualan ke dalam SharedPreferences. */
    private void saveAllSessions(ArrayList<ChatSession> sessions) {
        try {
            JSONArray arr = new JSONArray();
            for (ChatSession s : sessions) {
                JSONObject obj = new JSONObject();
                obj.put("id", s.id);
                obj.put("title", s.title);
                obj.put("updatedAt", s.updatedAt);

                JSONArray msgsArr = new JSONArray();
                for (ChatMessage m : s.messages) {
                    JSONObject mObj = new JSONObject();
                    mObj.put("role", m.role);
                    mObj.put("content", m.content);
                    mObj.put("createdAt", m.createdAt);
                    msgsArr.put(mObj);
                }
                obj.put("messages", msgsArr);
                arr.put(obj);
            }
            chatPrefs().edit().putString(CHAT_PREF_SAVED_SESSIONS, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    /** Padam satu sesi perbualan tertentu mengikut ID. */
    private void deleteChatSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return;
        ArrayList<ChatSession> sessions = loadAllSavedSessions();
        ArrayList<ChatSession> updated = new ArrayList<>();
        for (ChatSession s : sessions) {
            if (!sessionId.equals(s.id)) {
                updated.add(s);
            }
        }
        saveAllSessions(updated);
        if (sessionId.equals(currentSessionId)) {
            currentSessionId = "";
            currentSessionTopic = "";
            messages.clear();
            renderMessages();
        }
    }

    /** Padamkan semua sejarah sesi perbualan. */
    private void clearAllChatSessions() {
        chatPrefs().edit().remove(CHAT_PREF_SAVED_SESSIONS).apply();
        currentSessionId = "";
        currentSessionTopic = "";
        messages.clear();
        renderMessages();
    }

    private String safe(Object value) {
        return String.valueOf(value == null ? "" : value).trim();
    }

    public static final class ChatSession {
        public String id;
        public String title;
        public long updatedAt;
        public ArrayList<ChatMessage> messages = new ArrayList<>();

        public ChatSession(String id, String title, long updatedAt, ArrayList<ChatMessage> messages) {
            this.id = id == null ? "" : id;
            this.title = title == null ? "Emergency Query" : title;
            this.updatedAt = updatedAt > 0 ? updatedAt : System.currentTimeMillis();
            this.messages = messages != null ? messages : new ArrayList<>();
        }
    }

    private static final class ChatMessage {
        final String role;
        String content;
        final long createdAt;
        String senderName;
        String attachmentName;
        String attachmentUrl;
        String attachmentMime;
        String messageId;

        ChatMessage(String role, String content, long createdAt) {
            this(role, content, createdAt, "", "", "", "", "");
        }

        ChatMessage(String role, String content, long createdAt, String senderName, String attachmentName, String attachmentUrl, String attachmentMime, String messageId) {
            this.role = role == null ? "assistant" : role.toLowerCase(Locale.ROOT);
            this.content = content == null ? "" : content;
            this.createdAt = createdAt;
            this.senderName = senderName == null ? "" : senderName;
            this.attachmentName = attachmentName == null ? "" : attachmentName;
            this.attachmentUrl = attachmentUrl == null ? "" : attachmentUrl;
            this.attachmentMime = attachmentMime == null ? "" : attachmentMime;
            this.messageId = messageId == null ? "" : messageId;
        }
    }

    public static final class LiveMessageItem {
        public final String id;
        public final String text;
        public final String sender;
        public final String senderUid;
        public final String senderName;
        public final long createdAt;
        public final String attachmentName;
        public final String attachmentUrl;
        public final String attachmentMime;

        public LiveMessageItem(String id, String text, String sender, String senderUid, String senderName, long createdAt, String attachmentName, String attachmentUrl, String attachmentMime) {
            this.id = id == null ? "" : id;
            this.text = text == null ? "" : text;
            this.sender = sender == null ? "user" : sender;
            this.senderUid = senderUid == null ? "" : senderUid;
            this.senderName = senderName == null ? "" : senderName;
            this.createdAt = createdAt;
            this.attachmentName = attachmentName == null ? "" : attachmentName;
            this.attachmentUrl = attachmentUrl == null ? "" : attachmentUrl;
            this.attachmentMime = attachmentMime == null ? "" : attachmentMime;
        }
    }
}
