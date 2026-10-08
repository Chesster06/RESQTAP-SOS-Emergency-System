package com.example.resqtap.profile;

import com.example.resqtap.R;
import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.utils.BottomNavUtils;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import com.example.resqtap.security.SetPinActivity;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.concurrent.Executor;

import android.animation.ValueAnimator;
import android.hardware.fingerprint.FingerprintManager;
import android.os.CancellationSignal;
import android.view.HapticFeedbackConstants;
import android.view.TextureView;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * SecuritySettingsActivity
 * Tetapan Keselamatan: Kunci aplikasi (PIN 4 digit), Biometrik / Cap Jari,
 * Kemas kini kata laluan, Status keselamatan peranti, dan Pelindung Duress SOS.
 */
public class SecuritySettingsActivity extends BaseActivity {

    private boolean isSettingPinForBiometric = false;

    private SwitchMaterial toggleAppLock;
    private SwitchMaterial toggleBiometric;
    private View rowChangePin;
    private View rowChangeBiometricPin;
    private View rowBiometric;
    private boolean isSyncingUI = false;

    private final ActivityResultLauncher<Intent> setPinLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (isSettingPinForBiometric) {
                    if (result.getResultCode() == RESULT_OK) {
                        UserPrefs.setFingerprintEnabled(this, true);
                        Toast.makeText(this, R.string.biometric_and_pin_enabled_success, Toast.LENGTH_SHORT).show();
                    } else {
                        UserPrefs.setFingerprintEnabled(this, false);
                        UserPrefs.setBiometricPin(this, "");
                    }
                    isSettingPinForBiometric = false;
                }
                syncSecuritySettingsUI();
            });
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_security_settings);

        View root = findViewById(R.id.main);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);

        BottomNavUtils.setup(bottomNav, this, R.id.nav_bottom_profile);
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

        View back = findViewById(R.id.btn_back);
        if (back != null) {
            back.setOnClickListener(v -> finish());
        }

        initViews();
        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncSecuritySettingsUI();
    }

    private void initViews() {
        toggleAppLock = findViewById(R.id.toggle_app_lock);
        toggleBiometric = findViewById(R.id.toggle_biometric);
        rowChangePin = findViewById(R.id.row_change_pin);
        rowChangeBiometricPin = findViewById(R.id.row_change_biometric_pin);
        rowBiometric = findViewById(R.id.row_biometric);

        syncSecuritySettingsUI();
    }

    private void syncSecuritySettingsUI() {
        isSyncingUI = true;
        try {
            boolean appLockOn = UserPrefs.isAppLockEnabled(this) && UserPrefs.hasAppLockPin(this);
            boolean biometricOn = UserPrefs.isFingerprintEnabled(this);
            boolean hasBiometricPin = UserPrefs.hasBiometricPin(this);
            boolean duressOn = UserPrefs.isDuressSafeguardEnabled(this);

            if (toggleAppLock != null) {
                toggleAppLock.setChecked(appLockOn);
            }

            // Semak sokongan perkakasan biometrik (Fingerprint / Device PIN)
            BiometricManager biometricManager = BiometricManager.from(this);
            int authenticators = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ? (BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    : (BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL);

            int canAuth = biometricManager.canAuthenticate(authenticators);

            if (canAuth == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ||
                canAuth == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE) {
                // Peranti tiada sebarang sensor atau kunci peranti - sembunyikan baris
                if (rowBiometric != null) {
                    rowBiometric.setVisibility(View.GONE);
                }
                if (toggleBiometric != null) {
                    toggleBiometric.setChecked(false);
                    toggleBiometric.setEnabled(false);
                }
                UserPrefs.setFingerprintEnabled(this, false);
            } else {
                // Peranti menyokong biometrik / kunci peranti
                if (rowBiometric != null) {
                    rowBiometric.setVisibility(View.VISIBLE);
                }
                if (toggleBiometric != null) {
                    toggleBiometric.setChecked(biometricOn);
                    toggleBiometric.setEnabled(true);
                }
            }

            if (rowChangePin != null) {
                rowChangePin.setVisibility(appLockOn ? View.VISIBLE : View.GONE);
            }
            if (rowChangeBiometricPin != null) {
                rowChangeBiometricPin.setVisibility((biometricOn && hasBiometricPin) ? View.VISIBLE : View.GONE);
            }
        } finally {
            isSyncingUI = false;
        }
    }

    private void setupListeners() {
        View rowAppLock = findViewById(R.id.row_app_lock);
        if (rowAppLock != null && toggleAppLock != null) {
            rowAppLock.setOnClickListener(v -> toggleAppLock.toggle());
        }

        if (rowBiometric != null && toggleBiometric != null) {
            rowBiometric.setOnClickListener(v -> toggleBiometric.toggle());
        }

        if (toggleAppLock != null) {
            toggleAppLock.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isSyncingUI) return;
                if (isChecked) {
                    // Setiap kali Pin Lock di-enable semula, buka UI Pin (SetPinActivity)
                    if (!UserPrefs.isAppLockEnabled(this) || !UserPrefs.hasAppLockPin(this)) {
                        openSetPin(false);
                    } else {
                        if (rowChangePin != null) rowChangePin.setVisibility(View.VISIBLE);
                    }
                } else {
                    boolean wasEnabled = UserPrefs.isAppLockEnabled(this);
                    UserPrefs.setAppLockEnabled(this, false);
                    UserPrefs.setAppLockPin(this, "");
                    if (rowChangePin != null) {
                        rowChangePin.setVisibility(View.GONE);
                    }
                    if (wasEnabled) {
                        Toast.makeText(this, R.string.security_app_lock_disabled, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        if (toggleBiometric != null) {
            toggleBiometric.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isSyncingUI) return;
                if (isChecked) {
                    BiometricManager bm = BiometricManager.from(this);
                    int authenticators = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                            ? (BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                            : (BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL);

                    int authResult = bm.canAuthenticate(authenticators);
                    if (authResult == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                        Toast.makeText(this, R.string.security_biometric_not_enrolled, Toast.LENGTH_LONG).show();
                        toggleBiometric.setChecked(false);
                        UserPrefs.setFingerprintEnabled(this, false);
                        try {
                            Intent enrollIntent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                            startActivity(enrollIntent);
                        } catch (Exception ignored) {
                        }
                        return;
                    } else if (authResult != BiometricManager.BIOMETRIC_SUCCESS) {
                        Toast.makeText(this, R.string.security_biometric_unsupported, Toast.LENGTH_SHORT).show();
                        toggleBiometric.setChecked(false);
                        UserPrefs.setFingerprintEnabled(this, false);
                        return;
                    }

                    // Start step 1: Touch sensor to register fingerprint
                    startBiometricRegistrationFlow();
                } else {
                    UserPrefs.setFingerprintEnabled(this, false);
                    UserPrefs.setBiometricPin(this, "");
                    if (rowChangeBiometricPin != null) {
                        rowChangeBiometricPin.setVisibility(View.GONE);
                    }
                    Toast.makeText(this, R.string.security_biometric_disabled, Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (rowChangePin != null) {
            rowChangePin.setOnClickListener(v -> openSetPin(true, false));
        }

        if (rowChangeBiometricPin != null) {
            rowChangeBiometricPin.setOnClickListener(v -> openSetPin(true, true));
        }

        View rowChangePassword = findViewById(R.id.row_change_password);
        if (rowChangePassword != null) {
            rowChangePassword.setOnClickListener(v -> handlePasswordReset());
        }
    }

    private void startBiometricRegistrationFlow() {
        try {
            BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
            View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_confirm_fingerprint, null);
            bottomSheet.setContentView(sheetView);

            TextView tvTitle = sheetView.findViewById(R.id.tv_sheet_title);
            TextView tvSubtitle = sheetView.findViewById(R.id.tv_sheet_subtitle);
            CircularProgressIndicator progressRing = sheetView.findViewById(R.id.progress_fingerprint_ring);
            FrameLayout targetLayout = sheetView.findViewById(R.id.layout_fingerprint_target);
            ImageView ivFingerprint = sheetView.findViewById(R.id.iv_sheet_fingerprint);
            ImageView ivCheck = sheetView.findViewById(R.id.iv_sheet_check);
            TextView tvHint = sheetView.findViewById(R.id.tv_sheet_hint);

            // Initial UI state for Touch 1: "Register Fingerprint" / "Touch sensor to register"
            if (tvTitle != null) tvTitle.setText(R.string.biometric_register_title);
            if (tvSubtitle != null) tvSubtitle.setText(R.string.biometric_register_subtitle);
            if (progressRing != null) {
                progressRing.setProgress(0);
                progressRing.setIndicatorColor(android.graphics.Color.parseColor("#E53935"));
            }
            if (ivFingerprint != null) {
                ivFingerprint.setVisibility(View.VISIBLE);
                ivFingerprint.setAlpha(1.0f);
            }
            if (ivCheck != null) {
                ivCheck.setVisibility(View.GONE);
            }
            if (tvHint != null) {
                tvHint.setText(R.string.biometric_sheet_touch_sensor);
                tvHint.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            }

            int[] step = new int[]{1}; // 1 = wait touch 1, 2 = wait touch 2, 3 = completed
            boolean[] isTransitioning = new boolean[]{false};
            CancellationSignal[] activeSignal = new CancellationSignal[]{null};

            FingerprintManager fm = null;
            try {
                fm = getSystemService(FingerprintManager.class);
            } catch (Exception ignored) {
            }
            final FingerprintManager finalFm = fm;

            // Touch 2 (Confirm): Complete ring from 50 to 100, turn green, show checkmark tick
            Runnable onTouchTwoSuccess = () -> {
                if (step[0] != 2 || isTransitioning[0]) return;
                isTransitioning[0] = true;
                step[0] = 3;

                try {
                    if (activeSignal[0] != null) {
                        activeSignal[0].cancel();
                        activeSignal[0] = null;
                    }
                } catch (Exception ignored) {
                }

                try {
                    sheetView.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                } catch (Exception ignored) {
                }

                // Animate progress ring from 50 to 100
                ValueAnimator animator = ValueAnimator.ofInt(50, 100);
                animator.setDuration(350);
                animator.addUpdateListener(animation -> {
                    if (progressRing != null) {
                        progressRing.setProgress((int) animation.getAnimatedValue());
                    }
                });
                animator.start();

                // Turn green and show checkmark
                if (progressRing != null) {
                    progressRing.setIndicatorColor(ContextCompat.getColor(SecuritySettingsActivity.this, R.color.success_green));
                }
                if (ivFingerprint != null) {
                    ivFingerprint.animate().alpha(0f).setDuration(200).start();
                }
                if (ivCheck != null) {
                    ivCheck.setVisibility(View.VISIBLE);
                    ivCheck.setAlpha(0f);
                    ivCheck.setScaleX(0.6f);
                    ivCheck.setScaleY(0.6f);
                    ivCheck.animate()
                            .alpha(1f)
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(350)
                            .setInterpolator(new android.view.animation.OvershootInterpolator())
                            .start();
                }
                if (tvHint != null) {
                    tvHint.setText(R.string.biometric_confirm_sheet_success);
                    tvHint.setTextColor(ContextCompat.getColor(SecuritySettingsActivity.this, R.color.success_green));
                }

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        bottomSheet.dismiss();
                    } catch (Exception ignored) {
                    }
                    Toast.makeText(SecuritySettingsActivity.this, R.string.biometric_prompt_verified_need_pin, Toast.LENGTH_LONG).show();
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        isSettingPinForBiometric = true;
                        openSetPin(false, true);
                    }, 300);
                }, 650);
            };

            // Touch 1 (Register): Animate ring 0% to 50% (red arc), change title to 'Lift, then touch again', NO green tick!
            Runnable onTouchOneSuccess = () -> {
                if (step[0] != 1 || isTransitioning[0]) return;
                isTransitioning[0] = true;

                try {
                    if (activeSignal[0] != null) {
                        activeSignal[0].cancel();
                        activeSignal[0] = null;
                    }
                } catch (Exception ignored) {
                }

                try {
                    sheetView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                } catch (Exception ignored) {
                }

                // Animate progress smoothly from 0 to 50 (forms the red arc from the reference design)
                ValueAnimator animator = ValueAnimator.ofInt(0, 50);
                animator.setDuration(350);
                animator.addUpdateListener(animation -> {
                    if (progressRing != null) {
                        progressRing.setProgress((int) animation.getAnimatedValue());
                    }
                });
                animator.start();

                // Transition UI to Step 2 ("Lift, then touch again" with red hint)
                if (tvTitle != null) {
                    tvTitle.setText(R.string.biometric_confirm_sheet_title);
                }
                if (tvSubtitle != null) {
                    tvSubtitle.setText(R.string.biometric_confirm_sheet_subtitle);
                }
                if (tvHint != null) {
                    tvHint.setText(R.string.biometric_confirm_sheet_hint);
                    tvHint.setTextColor(android.graphics.Color.parseColor("#E53935"));
                }

                // Gentle pulse animation on fingerprint icon to acknowledge 1st touch
                if (ivFingerprint != null) {
                    ivFingerprint.animate()
                            .scaleX(1.15f)
                            .scaleY(1.15f)
                            .setDuration(160)
                            .withEndAction(() -> {
                                if (ivFingerprint != null) {
                                    ivFingerprint.animate()
                                            .scaleX(1.0f)
                                            .scaleY(1.0f)
                                            .setDuration(160)
                                            .start();
                                }
                            })
                            .start();
                }

                // Allow 450ms for user to lift finger before arming touch 2
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    step[0] = 2;
                    isTransitioning[0] = false;
                    listenFingerprintStep(finalFm, activeSignal, onTouchTwoSuccess);
                }, 450);
            };

            // Tapping on screen target triggers step
            if (targetLayout != null) {
                targetLayout.setOnClickListener(v -> {
                    if (isTransitioning[0]) return;
                    if (step[0] == 1) {
                        onTouchOneSuccess.run();
                    } else if (step[0] == 2) {
                        onTouchTwoSuccess.run();
                    }
                });
            }

            bottomSheet.setOnDismissListener(dialog -> {
                try {
                    if (activeSignal[0] != null) {
                        activeSignal[0].cancel();
                        activeSignal[0] = null;
                    }
                } catch (Exception ignored) {
                }
                if (step[0] != 3) {
                    if (toggleBiometric != null) {
                        toggleBiometric.setChecked(false);
                    }
                    UserPrefs.setFingerprintEnabled(SecuritySettingsActivity.this, false);
                    UserPrefs.setBiometricPin(SecuritySettingsActivity.this, "");
                }
            });

            // Start listening for Touch 1
            listenFingerprintStep(finalFm, activeSignal, onTouchOneSuccess);

            bottomSheet.show();
        } catch (Exception e) {
            if (toggleBiometric != null) {
                toggleBiometric.setChecked(false);
            }
            UserPrefs.setFingerprintEnabled(this, false);
        }
    }

    private void listenFingerprintStep(FingerprintManager fm, CancellationSignal[] activeSignal, Runnable onSuccess) {
        if (fm != null && fm.isHardwareDetected() && fm.hasEnrolledFingerprints()) {
            try {
                if (activeSignal[0] != null) {
                    activeSignal[0].cancel();
                }
            } catch (Exception ignored) {
            }
            activeSignal[0] = new CancellationSignal();
            try {
                fm.authenticate(null, activeSignal[0], 0, new FingerprintManager.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
                        runOnUiThread(onSuccess);
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                    }
                }, null);
            } catch (Exception ignored) {
            }
        }
    }
}
