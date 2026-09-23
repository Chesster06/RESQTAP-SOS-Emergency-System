package com.example.resqtap.chat;
import com.example.resqtap.R;

import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.home.MainActivity;
import com.example.resqtap.utils.BottomNavUtils;
import com.example.resqtap.utils.LocaleUtils;
import com.example.resqtap.utils.ThemeUtils;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


/**
 * QuickMessageActivity
 * Quick Message (TTS & STT): tukar text-to-speech atau speech-to-text untuk komunikasi pantas waktu cemas.
 */
public class QuickMessageActivity extends BaseActivity {
    private static final String PREFS_TTS = "text_to_speech";
    private static final String KEY_SAVED_TEXT = "saved_text";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_SPEECH_RATE_INDEX = "speech_rate_index";
    private static final int REQUEST_RECORD_AUDIO = 4401;
    private static final String[] STT_LANGUAGE_TAGS = {"ms-MY", "en-US", "zh-CN", "ta-IN"};
    private static final int MAX_TEXT_LENGTH = 500;
    private static final int MAX_HISTORY_ITEMS = 5;
    private static final float[] SPEECH_RATES = {0.75f, 1f, 1.25f, 1.5f};
    private static final String[] SPEECH_RATE_LABELS = {"0.75x", "1x", "1.25x", "1.5x"};

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private EditText ttsInput;
    private Spinner languageSpinner;
    private TextView charCount;
    private TextView speedValue;
    private LinearLayout historyList;
    private TextView emptyHistory;
    private LinearLayout ttsPage;
    private LinearLayout sttPage;
    private LinearLayout tabTts;
    private LinearLayout tabStt;
    private ImageView tabTtsIcon;
    private ImageView tabSttIcon;
    private TextView tabTtsLabel;
    private TextView tabSttLabel;
    private TextView pageTitle;
    private TextView pageSubtitle;
    private TextView sttState;
    private TextView sttOutput;
    private Spinner sttLanguageSpinner;
    private MaterialButton sttHoldButton;
    private View sttRecordRingOuter;
    private View sttRecordRingInner;
    private View sttRecordRingCore;
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private AnimatorSet sttOuterPulse;
    private AnimatorSet sttInnerPulse;
    private AnimatorSet sttCorePulse;
    private final StringBuilder sttFinalText = new StringBuilder();
    private boolean showingStt = false;
    private boolean sttRecording = false;
    private long lastSttStopToneAt = 0L;
    private int selectedSpeechRateIndex = 1;

    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_quick_message);

        View root = findViewById(R.id.main);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        BottomNavUtils.setup(bottomNav, this, R.id.nav_bottom_quick_message);
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

        ttsInput = findViewById(R.id.tts_input);
        languageSpinner = findViewById(R.id.language_spinner);
        charCount = findViewById(R.id.tts_char_count);
        speedValue = findViewById(R.id.speed_value);
        historyList = findViewById(R.id.history_list);
        emptyHistory = findViewById(R.id.empty_history);
        ttsPage = findViewById(R.id.tts_page);
        sttPage = findViewById(R.id.stt_page);
        tabTts = findViewById(R.id.tab_tts);
        tabStt = findViewById(R.id.tab_stt);
        tabTtsIcon = findViewById(R.id.tab_tts_icon);
        tabSttIcon = findViewById(R.id.tab_stt_icon);
        tabTtsLabel = findViewById(R.id.tab_tts_label);
        tabSttLabel = findViewById(R.id.tab_stt_label);
        pageTitle = findViewById(R.id.title);
        pageSubtitle = findViewById(R.id.subtitle);
        sttState = findViewById(R.id.stt_state);
        sttOutput = findViewById(R.id.stt_output);
        sttLanguageSpinner = findViewById(R.id.stt_language_spinner);
        sttHoldButton = findViewById(R.id.btn_stt_hold);
        sttRecordRingOuter = findViewById(R.id.stt_record_ring_outer);
        sttRecordRingInner = findViewById(R.id.stt_record_ring_inner);
        sttRecordRingCore = findViewById(R.id.stt_record_ring_core);

        selectedSpeechRateIndex = getTtsPrefs().getInt(KEY_SPEECH_RATE_INDEX, 1);
        if (selectedSpeechRateIndex < 0 || selectedSpeechRateIndex >= SPEECH_RATES.length) {
            selectedSpeechRateIndex = 1;
        }
        updateSpeedLabel();

        String savedText = getTtsPrefs().getString(KEY_SAVED_TEXT, "");
        if (!TextUtils.isEmpty(savedText)) {
            ttsInput.setText(savedText);
            ttsInput.setSelection(ttsInput.getText().length());
        }
        updateCharacterCount();

        languageSpinner.setSelection(getInitialTtsLanguageIndex());
        sttLanguageSpinner.setSelection(getInitialTtsLanguageIndex());
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
    @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyTtsLanguage();
            }

    @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        sttLanguageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
    @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (sttRecording) {
                    stopSpeechToText();
                }
                updateSpeechRecognizerLanguage();
            }

    @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        ttsInput.addTextChangedListener(new TextWatcher() {
    @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

    @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateCharacterCount();
            }

    @Override
            public void afterTextChanged(Editable s) {
            }
        });

        ImageButton btnBack = findViewById(R.id.btn_top_back);
        ImageButton btnPasteText = findViewById(R.id.btn_paste_text);
        ImageButton btnClearText = findViewById(R.id.btn_clear_text);
        View btnVoice = findViewById(R.id.btn_voice);
        View actionCopy = findViewById(R.id.action_copy);
        View actionSave = findViewById(R.id.action_save);
        View speedChip = findViewById(R.id.speed_chip);

        btnBack.setOnClickListener(v -> goHome());
        tabTts.setOnClickListener(v -> setVoiceMode(false));
        tabStt.setOnClickListener(v -> setVoiceMode(true));
        btnPasteText.setOnClickListener(v -> pasteClipboardText());
        btnClearText.setOnClickListener(v -> {
            stopTts();
            ttsInput.setText("");
        });
        btnVoice.setOnClickListener(v -> {
            animateVoiceButton(v);
            speakInputText();
        });
        actionCopy.setOnClickListener(v -> copyText());
        actionSave.setOnClickListener(v -> saveText());
        speedChip.setOnClickListener(v -> cycleSpeechRate());
        findViewById(R.id.btn_stt_clear).setOnClickListener(v -> clearSttTranscript());
        sttHoldButton.setOnClickListener(v -> {
            if (sttRecording) {
                stopSpeechToText();
            } else {
                startSpeechToText();
            }
        });

        renderHistory();
        setupSpeechRecognizer();

        tts = new TextToSpeech(this, status -> {
            ttsReady = (status == TextToSpeech.SUCCESS);
            if (ttsReady) {
                applyTtsLanguage();
                applySpeechRate();
            }
        });

        root.post(this::startTtsPageAnimations);
    }

    private void setVoiceMode(boolean sttMode) {
        if (showingStt == sttMode) return;
        showingStt = sttMode;
        stopTts();
        if (!sttMode) {
            stopSpeechToText();
        }
        updateVoiceModeTabs();
        if (pageTitle != null) {
            pageTitle.setText(sttMode ? R.string.speech_to_text_title : R.string.text_to_speech_title);
        }
        if (pageSubtitle != null) {
            pageSubtitle.setText(sttMode ? R.string.speech_to_text_subtitle : R.string.text_to_speech_subtitle);
        }
        slideVoicePage(sttMode);
    }

    /** Simpan atau hantar data VoiceModeTabs. */
    private void updateVoiceModeTabs() {
        int activeBg = R.drawable.bg_voice_mode_active;
        int inactiveBg = android.R.color.transparent;
        int activeColor = getColor(R.color.white);
        int inactiveColor = getColor(R.color.text_secondary);

        tabTts.setBackgroundResource(showingStt ? inactiveBg : activeBg);
        tabStt.setBackgroundResource(showingStt ? activeBg : inactiveBg);
        tabTtsLabel.setTextColor(showingStt ? inactiveColor : activeColor);
        tabSttLabel.setTextColor(showingStt ? activeColor : inactiveColor);
        tabTtsIcon.setColorFilter(showingStt ? inactiveColor : activeColor);
        tabSttIcon.setColorFilter(showingStt ? activeColor : inactiveColor);
    }

    private void slideVoicePage(boolean toStt) {
        final View outgoing = toStt ? ttsPage : sttPage;
        final View incoming = toStt ? sttPage : ttsPage;
        if (outgoing == null || incoming == null) return;

        int width = Math.max(1, getResources().getDisplayMetrics().widthPixels);
        incoming.setVisibility(View.VISIBLE);
        incoming.setAlpha(0f);
        incoming.setTranslationX(toStt ? width : -width);
        incoming.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(330L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        outgoing.animate()
                .alpha(0f)
                .translationX(toStt ? -width : width)
                .setDuration(260L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    outgoing.setVisibility(View.GONE);
                    outgoing.setAlpha(1f);
                    outgoing.setTranslationX(0f);
                })
                .start();
    }

    // 2. SPEECH-TO-TEXT (STT RECOGNIZER)
    /** Inisialisasi pengecam suara Android SpeechRecognizer untuk transkripsi audio ke teks. */
    private void setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            if (sttState != null) sttState.setText(R.string.speech_to_text_unavailable);
            if (sttHoldButton != null) sttHoldButton.setEnabled(false);
            return;
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 6000L);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4500L);
        updateSpeechRecognizerLanguage();

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
    @Override
            public void onReadyForSpeech(Bundle params) {
                setSttRecordingUi(true, R.string.speech_to_text_recording);
            }

    @Override
            public void onBeginningOfSpeech() {
                setSttRecordingUi(true, R.string.speech_to_text_listening);
            }

    @Override
            public void onRmsChanged(float rmsdB) {
            }

    @Override
            public void onBufferReceived(byte[] buffer) {
            }

    @Override
            public void onEndOfSpeech() {
                if (sttState != null) sttState.setText(R.string.speech_to_text_processing);
            }

    @Override
            public void onError(int error) {
                sttRecording = false;
                restoreSttPlaceholderIfEmpty();
                playSttStopTone();
                setSttRecordingUi(false, sttFinalText.length() > 0 ? R.string.speech_to_text_captured : R.string.speech_to_text_ready);
            }

    @Override
            public void onResults(Bundle results) {
                appendRecognitionResults(results);
                sttRecording = false;
                restoreSttPlaceholderIfEmpty();
                playSttStopTone();
                setSttRecordingUi(false, sttFinalText.length() > 0 ? R.string.speech_to_text_captured : R.string.speech_to_text_ready);
            }

    @Override
            public void onPartialResults(Bundle partialResults) {
                showRecognitionPreview(partialResults);
            }

    @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });
    }

    private void startSpeechToText() {
        if (speechRecognizer == null || speechRecognizerIntent == null) {
            Toast.makeText(this, R.string.speech_to_text_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            Toast.makeText(this, R.string.speech_to_text_permission, Toast.LENGTH_SHORT).show();
            return;
        }
        if (sttRecording) return;

        try {
            updateSpeechRecognizerLanguage();
            sttFinalText.setLength(0);
            if (sttOutput != null) {
                sttOutput.setText(R.string.speech_to_text_listening_placeholder);
            }
            sttRecording = true;
            setSttRecordingUi(true, R.string.speech_to_text_recording);
            speechRecognizer.startListening(speechRecognizerIntent);
        } catch (Exception ignored) {
            sttRecording = false;
            setSttRecordingUi(false, R.string.speech_to_text_ready);
        }
    }

    private void stopSpeechToText() {
        if (!sttRecording || speechRecognizer == null) {
            setSttRecordingUi(false, sttFinalText.length() > 0 ? R.string.speech_to_text_captured : R.string.speech_to_text_ready);
            return;
        }
        sttRecording = false;
        if (sttState != null) sttState.setText(R.string.speech_to_text_processing);
        try {
            speechRecognizer.stopListening();
        } catch (Exception ignored) {
            restoreSttPlaceholderIfEmpty();
            playSttStopTone();
            setSttRecordingUi(false, R.string.speech_to_text_ready);
        }
    }

    private void setSttRecordingUi(boolean recording, int stateRes) {
        if (sttState != null) sttState.setText(stateRes);
        if (sttHoldButton != null) {
            sttHoldButton.setText(recording ? R.string.speech_to_text_listening : R.string.speech_to_text_hold);
            sttHoldButton.animate()
                    .scaleX(recording ? 1.04f : 1f)
                    .scaleY(recording ? 1.04f : 1f)
                    .setDuration(160L)
                    .start();
        }
        animateSttRing(sttRecordRingOuter, recording, 1.12f, 0.28f);
        animateSttRing(sttRecordRingInner, recording, 1.08f, 0.48f);
        animateSttRing(sttRecordRingCore, recording, 1.04f, 0.72f);
        animateSttWaveBars(recording);
    }

    private final List<ObjectAnimator> sttWaveAnimators = new ArrayList<>();

    private void animateSttWaveBars(boolean recording) {
        for (ObjectAnimator anim : sttWaveAnimators) {
            anim.cancel();
        }
        sttWaveAnimators.clear();

        int[] barIds = new int[]{
                R.id.stt_wave_bar_1,
                R.id.stt_wave_bar_2,
                R.id.stt_wave_bar_3,
                R.id.stt_wave_bar_4,
                R.id.stt_wave_bar_5,
                R.id.stt_wave_bar_6
        };

        for (int i = 0; i < barIds.length; i++) {
            View bar = findViewById(barIds[i]);
            if (bar == null) continue;
            bar.animate().cancel();
            if (!recording) {
                bar.setScaleY(1f);
                continue;
            }
            ObjectAnimator wave = ObjectAnimator.ofFloat(bar, View.SCALE_Y, 0.45f, 1.45f, 0.65f);
            wave.setDuration(600L + (i * 65L));
            wave.setStartDelay(i * 75L);
            wave.setRepeatCount(ValueAnimator.INFINITE);
            wave.setRepeatMode(ValueAnimator.REVERSE);
            wave.setInterpolator(new DecelerateInterpolator());
            wave.start();
            sttWaveAnimators.add(wave);
        }
    }

    /** Kendalikan animasi SttRing. */
    private void animateSttRing(View ring, boolean recording, float scale, float idleAlpha) {
        if (ring == null) return;
        ring.animate().cancel();
        cancelSttRing(ring);
        if (!recording) {
            ring.setScaleX(1f);
            ring.setScaleY(1f);
            ring.setAlpha(idleAlpha);
            return;
        }
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(ring, View.SCALE_X, 1f, scale);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(ring, View.SCALE_Y, 1f, scale);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(ring, View.ALPHA, idleAlpha, 0.08f);
        for (ObjectAnimator animator : new ObjectAnimator[]{scaleX, scaleY, alpha}) {
            animator.setDuration(760L);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.REVERSE);
            animator.setInterpolator(new DecelerateInterpolator());
        }
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY, alpha);
        if (ring == sttRecordRingOuter) {
            sttOuterPulse = set;
        } else if (ring == sttRecordRingInner) {
            sttInnerPulse = set;
        } else if (ring == sttRecordRingCore) {
            sttCorePulse = set;
        }
        set.start();
    }

    private void cancelSttRing(View ring) {
        if (ring == sttRecordRingOuter && sttOuterPulse != null) {
            sttOuterPulse.cancel();
            sttOuterPulse = null;
        } else if (ring == sttRecordRingInner && sttInnerPulse != null) {
            sttInnerPulse.cancel();
            sttInnerPulse = null;
        } else if (ring == sttRecordRingCore && sttCorePulse != null) {
            sttCorePulse.cancel();
            sttCorePulse = null;
        }
    }

    private void appendRecognitionResults(Bundle results) {
        if (results == null) return;
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) return;
        appendTranscript(matches.get(0));
    }

    /** Paparkan RecognitionPreview. */
    private void showRecognitionPreview(Bundle partialResults) {
        if (partialResults == null || sttOutput == null) return;
        ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        String partial = matches == null || matches.isEmpty() ? "" : matches.get(0).trim();
        String saved = sttFinalText.toString().trim();
        String combined = TextUtils.isEmpty(saved) ? partial : (TextUtils.isEmpty(partial) ? saved : saved + " " + partial);
        sttOutput.setText(TextUtils.isEmpty(combined) ? getString(R.string.speech_to_text_placeholder) : combined);
    }

    private void appendTranscript(String text) {
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) return;
        if (sttFinalText.length() > 0) {
            sttFinalText.append(' ');
        }
        sttFinalText.append(clean);
        if (sttOutput != null) {
            sttOutput.setText(sttFinalText.toString());
        }
    }

    private void restoreSttPlaceholderIfEmpty() {
        if (sttOutput != null && sttFinalText.length() == 0) {
            sttOutput.setText(R.string.speech_to_text_placeholder);
        }
    }

    /** Kendalikan animasi SttStopTone. */
    private void playSttStopTone() {
        long now = System.currentTimeMillis();
        if (now - lastSttStopToneAt < 600L) return;
        lastSttStopToneAt = now;
        try {
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 70);
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 120);
            View anchor = sttHoldButton != null ? sttHoldButton : findViewById(R.id.main);
            if (anchor != null) {
                anchor.postDelayed(tone::release, 220L);
            } else {
                tone.release();
            }
        } catch (Exception ignored) {
        }
    }

    /** Padam atau bersihkan SttTranscript. */
    private void clearSttTranscript() {
        sttFinalText.setLength(0);
        if (sttOutput != null) sttOutput.setText(R.string.speech_to_text_placeholder);
        if (sttState != null) sttState.setText(R.string.speech_to_text_ready);
    }

    private String getSelectedSttLanguageTag() {
        int selected = sttLanguageSpinner == null ? 0 : sttLanguageSpinner.getSelectedItemPosition();
        if (selected < 0 || selected >= STT_LANGUAGE_TAGS.length) {
            return STT_LANGUAGE_TAGS[0];
        }
        return STT_LANGUAGE_TAGS[selected];
    }

    /** Simpan atau hantar data SpeechRecognizerLanguage. */
    private void updateSpeechRecognizerLanguage() {
        if (speechRecognizerIntent == null) return;
        String languageTag = getSelectedSttLanguageTag();
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag);
    }

    /** Handle callback kebenaran sistem Android daripada pengguna. */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_RECORD_AUDIO) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (sttState != null) sttState.setText(R.string.speech_to_text_ready);
        } else {
            Toast.makeText(this, R.string.speech_to_text_permission, Toast.LENGTH_SHORT).show();
        }
    }

    private void goHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    /** Kendalikan animasi VoiceButton. */
    private void animateVoiceButton(View button) {
        if (button == null) return;
        button.animate()
                .scaleX(0.94f)
                .scaleY(0.94f)
                .setDuration(80L)
                .withEndAction(() -> button.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120L)
                        .start())
                .start();
    }

    private void startTtsPageAnimations() {
        animateEntrance(findViewById(R.id.top_bar), 0);
        animateEntrance(findViewById(R.id.tts_voice_hero), 70);
        animateEntrance(findViewById(R.id.tts_composer_card), 140);
        resetPlaybackVisuals();
    }

    /** Kendalikan animasi Entrance. */
    private void animateEntrance(View view, long delay) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setTranslationY(dp(18));
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(420L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void startPulse(View view, long delay, float scaleTo, float alphaFrom, float alphaTo) {
        if (view == null) return;
        view.setAlpha(alphaFrom);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, scaleTo);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, scaleTo);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, alphaFrom, alphaTo);
        for (ObjectAnimator animator : new ObjectAnimator[]{scaleX, scaleY, alpha}) {
            animator.setDuration(1900L);
            animator.setStartDelay(delay);
            animator.setRepeatCount(1);
            animator.setRepeatMode(ValueAnimator.RESTART);
            animator.setInterpolator(new DecelerateInterpolator());
        }
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY, alpha);
        set.start();
    }

    private void resetPlaybackVisuals() {
        int[] ringIds = {
                R.id.tts_play_ring_outer,
                R.id.tts_play_ring_mid,
                R.id.tts_play_ring_inner
        };
        float[] ringAlphas = {0.62f, 0.72f, 0.82f};
        for (int i = 0; i < ringIds.length; i++) {
            View ring = findViewById(ringIds[i]);
            if (ring == null) continue;
            ring.animate().cancel();
            ring.setScaleX(1f);
            ring.setScaleY(1f);
            ring.setAlpha(ringAlphas[i]);
        }
        int[] waveIds = getWaveBarIds();
        for (int id : waveIds) {
            View bar = findViewById(id);
            if (bar == null) continue;
            bar.animate().cancel();
            bar.setScaleY(1f);
            bar.setAlpha(1f);
        }
    }

    private void triggerPlaybackAnimation() {
        startPulse(findViewById(R.id.tts_play_ring_outer), 0, 1.14f, 0.62f, 0.12f);
        startPulse(findViewById(R.id.tts_play_ring_mid), 120, 1.10f, 0.72f, 0.20f);
        startPulse(findViewById(R.id.tts_play_ring_inner), 240, 1.06f, 0.82f, 0.32f);
        startWaveAnimation();
    }

    private void startWaveAnimation() {
        int[] ids = getWaveBarIds();
        for (int i = 0; i < ids.length; i++) {
            View bar = findViewById(ids[i]);
            if (bar == null) continue;
            bar.animate().cancel();
            ObjectAnimator wave = ObjectAnimator.ofFloat(bar, View.SCALE_Y, 0.58f, 1.16f, 0.72f);
            wave.setDuration(850L + (i * 70L));
            wave.setStartDelay(i * 90L);
            wave.setRepeatCount(2);
            wave.setRepeatMode(ValueAnimator.REVERSE);
            wave.setInterpolator(new DecelerateInterpolator());
            wave.start();
        }
    }
}
