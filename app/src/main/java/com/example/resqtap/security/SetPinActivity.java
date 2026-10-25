package com.example.resqtap.security;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.resqtap.R;
import com.example.resqtap.utils.LocaleUtils;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;

/**
 * SetPinActivity
 * Antara muka papan kekunci PIN (Numeric Keypad & Dots) untuk menetapkan atau
 * mengemas kini PIN keselamatan 4 digit aplikasi, menggantikan dialog input teks.
 */
public class SetPinActivity extends AppCompatActivity {

    public static final String EXTRA_IS_CHANGING = "extra_is_changing";
    public static final String EXTRA_IS_FOR_BIOMETRIC = "extra_is_for_biometric";

    private static final int STATE_ENTER_CURRENT = 0;
    private static final int STATE_ENTER_NEW = 1;
    private static final int STATE_CONFIRM_NEW = 2;

    private int currentState = STATE_ENTER_NEW;
    private boolean isChanging = false;
    private boolean isForBiometric = false;
    private String firstPin = null;

    private final StringBuilder enteredPin = new StringBuilder();
    private View dot1, dot2, dot3, dot4;
    private View dotsContainer;
    private TextView tvTitle;
    private TextView tvDesc;

    @Override
    protected void attachBaseContext(Context newBase) {
        Context wrapped = LocaleUtils.wrap(newBase);
        super.attachBaseContext(wrapped == null ? newBase : wrapped);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        // SEC-08 FIX: Prevent PIN theft via screenshots, recording, or app switcher preview
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_set_pin);

        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        isChanging = getIntent().getBooleanExtra(EXTRA_IS_CHANGING, false);
        isForBiometric = getIntent().getBooleanExtra(EXTRA_IS_FOR_BIOMETRIC, false);
        boolean hasExisting = isForBiometric ? UserPrefs.hasBiometricPin(this) : UserPrefs.hasAppLockPin(this);
        // SEC-02 FIX: Always require current PIN verification when one exists,
        // regardless of EXTRA_IS_CHANGING flag. Ignoring the flag closes the bypass
        // where launching this Activity directly (without the extra) would skip old-PIN
        // verification and allow any holder of the device to overwrite the PIN.
        if (hasExisting) {
            currentState = STATE_ENTER_CURRENT;
        } else {
            currentState = STATE_ENTER_NEW;
        }


        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackNavigation();
            }
        });

        initViews();
        setupKeypad();
        updateUIForState();
    }

    private void initViews() {
        dot1 = findViewById(R.id.dot_1);
        dot2 = findViewById(R.id.dot_2);
        dot3 = findViewById(R.id.dot_3);
        dot4 = findViewById(R.id.dot_4);
        dotsContainer = findViewById(R.id.dots_container);
        tvTitle = findViewById(R.id.tv_title);
        tvDesc = findViewById(R.id.tv_desc);

        ImageView btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> handleBackNavigation());
        }
    }

    private void handleBackNavigation() {
        if (currentState == STATE_CONFIRM_NEW) {
            // Revert back to entering new PIN step
            currentState = STATE_ENTER_NEW;
            firstPin = null;
            enteredPin.setLength(0);
            updateDots();
            updateUIForState();
        } else {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    private void updateUIForState() {
        if (tvTitle == null || tvDesc == null) return;

        switch (currentState) {
            case STATE_ENTER_CURRENT:
                tvTitle.setText(R.string.security_pin_step_current_title);
                tvDesc.setText(R.string.security_pin_step_current_desc);
                break;
            case STATE_ENTER_NEW:
                if (isChanging) {
                    tvTitle.setText(R.string.security_pin_step_enter_title);
                    tvDesc.setText(R.string.security_pin_step_enter_desc);
                } else {
                    tvTitle.setText(R.string.security_pin_step_enter_your_title);
                    tvDesc.setText(R.string.security_pin_step_enter_your_desc);
                }
                break;
            case STATE_CONFIRM_NEW:
                if (isChanging) {
                    tvTitle.setText(R.string.security_pin_step_confirm_title);
                    tvDesc.setText(R.string.security_pin_step_confirm_desc);
                } else {
                    tvTitle.setText(R.string.security_pin_step_confirm_your_title);
                    tvDesc.setText(R.string.security_pin_step_confirm_your_desc);
                }
                break;
        }
    }

    private void setupKeypad() {
        int[] keyIds = {
                R.id.key_0, R.id.key_1, R.id.key_2, R.id.key_3, R.id.key_4,
                R.id.key_5, R.id.key_6, R.id.key_7, R.id.key_8, R.id.key_9
        };

        for (int id : keyIds) {
            TextView key = findViewById(id);
            if (key != null) {
                key.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    appendDigit(key.getText().toString());
                });
            }
        }

        View backspace = findViewById(R.id.btn_backspace);
        if (backspace != null) {
            backspace.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                removeDigit();
            });
            backspace.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                enteredPin.setLength(0);
                updateDots();
                return true;
            });
        }
    }

    private void appendDigit(String digit) {
        if (enteredPin.length() < 4) {
            enteredPin.append(digit);
            updateDots();
            if (enteredPin.length() == 4) {
                handlePinCompleted();
            }
        }
    }

    private void removeDigit() {
        if (enteredPin.length() > 0) {
            enteredPin.deleteCharAt(enteredPin.length() - 1);
            updateDots();
        }
    }

    private void updateDots() {
        int len = enteredPin.length();
        if (dot1 != null) dot1.setBackgroundResource(len >= 1 ? R.drawable.bg_pin_dot_filled : R.drawable.bg_pin_dot_empty);
        if (dot2 != null) dot2.setBackgroundResource(len >= 2 ? R.drawable.bg_pin_dot_filled : R.drawable.bg_pin_dot_empty);
        if (dot3 != null) dot3.setBackgroundResource(len >= 3 ? R.drawable.bg_pin_dot_filled : R.drawable.bg_pin_dot_empty);
        if (dot4 != null) dot4.setBackgroundResource(len >= 4 ? R.drawable.bg_pin_dot_filled : R.drawable.bg_pin_dot_empty);
    }

    private void handlePinCompleted() {
        String input = enteredPin.toString();

        if (currentState == STATE_ENTER_CURRENT) {
            boolean isValid = isForBiometric ? UserPrefs.verifyBiometricPin(this, input) : UserPrefs.verifyAppLockPin(this, input);
            if (isValid) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    currentState = STATE_ENTER_NEW;
                    enteredPin.setLength(0);
                    updateDots();
                    updateUIForState();
                }, 200);
            } else {
                shakeDots();
                Toast.makeText(this, R.string.security_pin_incorrect, Toast.LENGTH_SHORT).show();
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    enteredPin.setLength(0);
                    updateDots();
                }, 300);
            }
        } else if (currentState == STATE_ENTER_NEW) {
            firstPin = input;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                currentState = STATE_CONFIRM_NEW;
                enteredPin.setLength(0);
                updateDots();
                updateUIForState();
            }, 200);
        } else if (currentState == STATE_CONFIRM_NEW) {
            if (input.equals(firstPin)) {
                if (isForBiometric) {
                    UserPrefs.setBiometricPin(this, firstPin);
                } else {
                    UserPrefs.setAppLockPin(this, firstPin);
                    UserPrefs.setAppLockEnabled(this, true);
                }
                AppLockManager.setUnlocked(true);
                Toast.makeText(this, R.string.security_pin_saved, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            } else {
                shakeDots();
                Toast.makeText(this, R.string.security_pin_mismatch, Toast.LENGTH_SHORT).show();
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    currentState = STATE_ENTER_NEW;
                    firstPin = null;
                    enteredPin.setLength(0);
                    updateDots();
                    updateUIForState();
                }, 300);
            }
        }
    }

    private void shakeDots() {
        if (dotsContainer != null) {
            dotsContainer.performHapticFeedback(HapticFeedbackConstants.REJECT);
            dotsContainer.animate().translationX(-24f).setDuration(50).withEndAction(() -> {
                dotsContainer.animate().translationX(24f).setDuration(50).withEndAction(() -> {
                    dotsContainer.animate().translationX(-16f).setDuration(50).withEndAction(() -> {
                        dotsContainer.animate().translationX(16f).setDuration(50).withEndAction(() -> {
                            dotsContainer.animate().translationX(0f).setDuration(50).start();
                        }).start();
                    }).start();
                }).start();
            }).start();
        }
    }
}
