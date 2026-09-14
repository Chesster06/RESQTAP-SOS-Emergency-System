package com.example.resqtap.security;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.HapticFeedbackConstants;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.concurrent.Executor;

import com.example.resqtap.R;
import com.example.resqtap.auth.LoginActivity;
import com.example.resqtap.utils.LocaleUtils;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;

/**
 * AppLockActivity
 * Skrin Kunci PIN Aplikasi - Meminta PIN 4 digit atau Biometrik
 * sebelum membenarkan pengguna mengakses aplikasi.
 */
public class AppLockActivity extends AppCompatActivity {

    private final StringBuilder enteredPin = new StringBuilder();
    private View dot1, dot2, dot3, dot4;
    private View dotsContainer;
    private View btnBiometricKey;

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
        setContentView(R.layout.activity_app_lock);

        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Intercept back button: minimise app instead of bypassing
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                moveTaskToBack(true);
            }
        });

        initViews();
        setupKeypad();

        // Auto trigger biometric if enabled
        if (UserPrefs.isFingerprintEnabled(this)) {
            new Handler(Looper.getMainLooper()).postDelayed(this::authenticateBiometric, 300);
        }
    }

    private void initViews() {
        dot1 = findViewById(R.id.dot_1);
        dot2 = findViewById(R.id.dot_2);
        dot3 = findViewById(R.id.dot_3);
        dot4 = findViewById(R.id.dot_4);
        dotsContainer = findViewById(R.id.dots_container);
        btnBiometricKey = findViewById(R.id.btn_biometric_key);

        boolean biometricOn = UserPrefs.isFingerprintEnabled(this);
        boolean hasAppLockPin = UserPrefs.isAppLockEnabled(this) && UserPrefs.hasAppLockPin(this);
        boolean hasBiometricPin = UserPrefs.isFingerprintEnabled(this) && UserPrefs.hasBiometricPin(this);
        boolean hasAnyPin = hasAppLockPin || hasBiometricPin;

        TextView tvTitle = findViewById(R.id.tv_title);
        TextView tvDesc = findViewById(R.id.tv_desc);
        if (hasAnyPin) {
            if (tvTitle != null) tvTitle.setText(R.string.app_lock_screen_title);
            if (tvDesc != null) tvDesc.setText(R.string.app_lock_screen_desc);
        } else if (biometricOn) {
            if (tvTitle != null) {
                tvTitle.setText(R.string.security_biometric_title);
            }
            if (tvDesc != null) tvDesc.setText(R.string.app_lock_use_biometric);
        }

        BiometricManager bm = BiometricManager.from(this);
        int authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK;

        boolean isSupported = bm.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS;
        boolean showBiometricKey = biometricOn && isSupported;

        if (btnBiometricKey != null) {
            btnBiometricKey.setVisibility(showBiometricKey ? View.VISIBLE : View.INVISIBLE);
            if (showBiometricKey) {
                ImageView iconBiometric = findViewById(R.id.icon_biometric);
                if (iconBiometric != null) {
                    iconBiometric.setImageResource(R.drawable.ic_fingerprint_24);
                }
                btnBiometricKey.setOnClickListener(v -> authenticateBiometric());
            }
        }

        View forgotPin = findViewById(R.id.btn_forgot_pin);
        if (forgotPin != null) {
            forgotPin.setVisibility(hasAnyPin ? View.VISIBLE : View.GONE);
            forgotPin.setOnClickListener(v -> showForgotPinDialog());
        }

        if (dotsContainer != null && biometricOn && !hasAnyPin) {
            dotsContainer.setOnClickListener(v -> authenticateBiometric());
        }
    }

    private void setupKeypad() {
        int[] keyIds = {
                R.id.key_0, R.id.key_1, R.id.key_2, R.id.key_3, R.id.key_4,
                R.id.key_5, R.id.key_6, R.id.key_7, R.id.key_8, R.id.key_9
        };

        boolean hasAppLockPin = UserPrefs.isAppLockEnabled(this) && UserPrefs.hasAppLockPin(this);
        boolean hasBiometricPin = UserPrefs.isFingerprintEnabled(this) && UserPrefs.hasBiometricPin(this);
        boolean hasAnyPin = hasAppLockPin || hasBiometricPin;

        for (int id : keyIds) {
            TextView key = findViewById(id);
            if (key != null) {
                key.setOnClickListener(v -> {
                    if (hasAnyPin) {
                        appendDigit(key.getText().toString());
                    } else {
                        authenticateBiometric();
                    }
                });
            }
        }

        View backspace = findViewById(R.id.btn_backspace);
        if (backspace != null) {
            backspace.setOnClickListener(v -> {
                if (hasAnyPin) {
                    deleteDigit();
                } else {
                    authenticateBiometric();
                }
            });
            backspace.setOnLongClickListener(v -> {
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
                new Handler(Looper.getMainLooper()).postDelayed(this::verifyPin, 150);
            }
        }
    }

    private void deleteDigit() {
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

    private void verifyPin() {
        String input = enteredPin.toString();
        boolean appLockValid = UserPrefs.isAppLockEnabled(this) && UserPrefs.verifyAppLockPin(this, input);
        boolean biometricPinValid = UserPrefs.isFingerprintEnabled(this) && UserPrefs.verifyBiometricPin(this, input);

        if (appLockValid || biometricPinValid) {
            unlockSuccess();
        } else {
            shakeDots();
            Toast.makeText(this, R.string.app_lock_incorrect_pin, Toast.LENGTH_SHORT).show();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                enteredPin.setLength(0);
                updateDots();
            }, 300);
        }
    }

    private void shakeDots() {
        if (dotsContainer != null) {
            Animation shake = AnimationUtils.loadAnimation(this, R.anim.splash_logo_in);
            dotsContainer.startAnimation(shake);
        }
    }

    private void unlockSuccess() {
        AppLockManager.setUnlocked(true);
        finish();
        try {
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        } catch (Exception ignored) {
        }
    }

    private void authenticateBiometric() {
        if (isFinishing()) return;

        try {
            BiometricManager biometricManager = BiometricManager.from(this);
            int authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK;
            int canAuth = biometricManager.canAuthenticate(authenticators);

            if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
                return;
            }

            Executor executor = ContextCompat.getMainExecutor(this);
            BiometricPrompt prompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    unlockSuccess();
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                }

                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    // User canceled, tapped outside, or clicked "Guna PIN Aplikasi"
                    // Biometric prompt closes automatically and leaves keypad active for user to input PIN
                }
            });

            BiometricPrompt.PromptInfo.Builder builder = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(getString(R.string.app_lock_biometric_prompt_title))
                    .setSubtitle(getString(R.string.app_lock_biometric_prompt_subtitle))
                    .setDescription(getString(R.string.security_biometric_desc))
                    .setNegativeButtonText(getString(R.string.app_lock_use_pin_button));

            prompt.authenticate(builder.build());
        } catch (Exception ignored) {
        }
    }

    private void showForgotPinDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_lock_forgot_pin)
                .setMessage(R.string.delete_account_requires_relogin)
                .setPositiveButton(R.string.logout, (dialog, which) -> {
                    try {
                        FirebaseAuth.getInstance().signOut();
                    } catch (Exception ignored) {
                    }
                    try {
                        UserPrefs.clearAccountData(this);
                    } catch (Exception ignored) {
                    }
                    AppLockManager.setUnlocked(true);
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
