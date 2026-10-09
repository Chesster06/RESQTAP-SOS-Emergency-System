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

    private void openSetPin(boolean isChanging) {
        openSetPin(isChanging, false);
    }

    private void openSetPin(boolean isChanging, boolean isForBiometric) {
        Intent intent = new Intent(this, SetPinActivity.class);
        intent.putExtra(SetPinActivity.EXTRA_IS_CHANGING, isChanging);
        intent.putExtra(SetPinActivity.EXTRA_IS_FOR_BIOMETRIC, isForBiometric);
        setPinLauncher.launch(intent);
    }

    private void handlePasswordReset() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String email = user != null ? user.getEmail() : UserPrefs.getEmail(this);
        if (user == null || email == null || email.trim().isEmpty()) {
            Toast.makeText(this, R.string.security_password_reset_no_email, Toast.LENGTH_SHORT).show();
            return;
        }

        View content = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null, false);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(content)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        EditText inputCurrent = content.findViewById(R.id.input_current_password);
        EditText inputNew = content.findViewById(R.id.input_new_password);
        EditText inputConfirm = content.findViewById(R.id.input_confirm_password);

        ImageView toggleCurrent = content.findViewById(R.id.btn_toggle_current_password);
        ImageView toggleNew = content.findViewById(R.id.btn_toggle_new_password);
        ImageView toggleConfirm = content.findViewById(R.id.btn_toggle_confirm_password);

        setupPasswordVisibilityToggle(inputCurrent, toggleCurrent);
        setupPasswordVisibilityToggle(inputNew, toggleNew);
        setupPasswordVisibilityToggle(inputConfirm, toggleConfirm);

        View btnClose = content.findViewById(R.id.btn_close_password);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        ImageView iconLength = content.findViewById(R.id.icon_rule_length);
        TextView tvLength = content.findViewById(R.id.tv_rule_length);
        ImageView iconCasing = content.findViewById(R.id.icon_rule_casing);
        TextView tvCasing = content.findViewById(R.id.tv_rule_casing);
        ImageView iconNumber = content.findViewById(R.id.icon_rule_number);
        TextView tvNumber = content.findViewById(R.id.tv_rule_number);
        ImageView iconSpecial = content.findViewById(R.id.icon_rule_special);
        TextView tvSpecial = content.findViewById(R.id.tv_rule_special);
        ImageView iconCommon = content.findViewById(R.id.icon_rule_common);
        TextView tvCommon = content.findViewById(R.id.tv_rule_common);

        int colorPink = getResources().getColor(R.color.brand_primary);
        int colorPrimaryText = getResources().getColor(R.color.text_primary);
        int colorMuted = getResources().getColor(R.color.text_secondary);

        if (inputNew != null) {
            inputNew.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    String val = s != null ? s.toString() : "";
                    boolean hasLength = val.length() >= 8;
                    boolean hasUpper = !val.equals(val.toLowerCase());
                    boolean hasLower = !val.equals(val.toUpperCase());
                    boolean hasCasing = hasUpper && hasLower;
                    boolean hasNumber = val.matches(".*\\d.*");
                    boolean hasSpecial = val.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*");
                    String cur = inputCurrent != null && inputCurrent.getText() != null
                            ? inputCurrent.getText().toString().trim() : "";
                    boolean isNotCommon = !val.equalsIgnoreCase("password")
                            && !val.equals("12345678")
                            && (!val.isEmpty() && !val.equals(cur));

                    updateRuleUi(iconLength, tvLength, hasLength, colorPrimaryText, colorMuted);
                    updateRuleUi(iconCasing, tvCasing, hasCasing, colorPrimaryText, colorMuted);
                    updateRuleUi(iconNumber, tvNumber, hasNumber, colorPrimaryText, colorMuted);
                    updateRuleUi(iconSpecial, tvSpecial, hasSpecial, colorPrimaryText, colorMuted);
                    updateRuleUi(iconCommon, tvCommon, isNotCommon, colorPrimaryText, colorMuted);
                }
            });
        }

        TextView btnCancel = content.findViewById(R.id.btn_cancel_password);
        com.google.android.material.button.MaterialButton btnAction = content.findViewById(R.id.btn_action_password);

        TextView tvTitle = content.findViewById(R.id.tv_password_dialog_title);
        TextView tvDesc = content.findViewById(R.id.tv_password_dialog_desc);

        View layoutStep1 = content.findViewById(R.id.layout_step1_current);
        View layoutStep2 = content.findViewById(R.id.layout_step2_new);
        View layoutStep3 = content.findViewById(R.id.layout_step3_confirm);

        final int[] currentStep = new int[]{1};

        Runnable updateStepUi = () -> {
            if (currentStep[0] == 1) {
                if (tvTitle != null) tvTitle.setText(R.string.security_change_password_title);
                if (tvDesc != null) tvDesc.setText(R.string.security_change_password_step1_desc);
                if (layoutStep1 != null) layoutStep1.setVisibility(View.VISIBLE);
                if (layoutStep2 != null) layoutStep2.setVisibility(View.GONE);
                if (layoutStep3 != null) layoutStep3.setVisibility(View.GONE);
                if (btnAction != null) {
                    btnAction.setText(R.string.security_next);
                    btnAction.setIconResource(R.drawable.ic_lock_outline);
                }
                if (btnCancel != null) btnCancel.setText(R.string.cancel);
                if (inputCurrent != null) inputCurrent.requestFocus();
            } else if (currentStep[0] == 2) {
                if (tvTitle != null) tvTitle.setText(R.string.security_change_password_step2_title);
                if (tvDesc != null) tvDesc.setText(R.string.security_change_password_step2_desc);
                if (layoutStep1 != null) layoutStep1.setVisibility(View.GONE);
                if (layoutStep2 != null) layoutStep2.setVisibility(View.VISIBLE);
                if (layoutStep3 != null) layoutStep3.setVisibility(View.GONE);
                if (btnAction != null) {
                    btnAction.setText(R.string.security_next);
                    btnAction.setIconResource(R.drawable.ic_lock_outline);
                }
                if (btnCancel != null) btnCancel.setText(R.string.cancel);
                if (inputNew != null) inputNew.requestFocus();
            } else {
                if (tvTitle != null) tvTitle.setText(R.string.security_change_password_step3_title);
                if (tvDesc != null) tvDesc.setText(R.string.security_change_password_step3_desc);
                if (layoutStep1 != null) layoutStep1.setVisibility(View.GONE);
                if (layoutStep2 != null) layoutStep2.setVisibility(View.GONE);
                if (layoutStep3 != null) layoutStep3.setVisibility(View.VISIBLE);
                if (btnAction != null) {
                    btnAction.setText(R.string.security_change_password_title);
                    btnAction.setIconResource(R.drawable.ic_lock_outline);
                }
                if (btnCancel != null) btnCancel.setText(R.string.cancel);
                if (inputConfirm != null) inputConfirm.requestFocus();
            }
        };

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> {
                if (currentStep[0] > 1) {
                    currentStep[0]--;
                    updateStepUi.run();
                } else {
                    dialog.dismiss();
                }
            });
        }

        if (btnAction != null) {
            btnAction.setOnClickListener(v -> {
                String curPass = inputCurrent != null ? inputCurrent.getText().toString().trim() : "";
                String newPass = inputNew != null ? inputNew.getText().toString().trim() : "";
                String confirmPass = inputConfirm != null ? inputConfirm.getText().toString().trim() : "";

                if (currentStep[0] == 1) {
                    if (curPass.isEmpty()) {
                        Toast.makeText(this, R.string.security_current_password_hint, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    currentStep[0] = 2;
                    updateStepUi.run();
                } else if (currentStep[0] == 2) {
                    if (newPass.length() < 6) {
                        Toast.makeText(this, R.string.security_pwd_rule_length, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newPass.equals(curPass)) {
                        Toast.makeText(this, R.string.security_password_same_as_current, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    currentStep[0] = 3;
                    updateStepUi.run();
                } else {
                    if (confirmPass.isEmpty()) {
                        Toast.makeText(this, R.string.security_confirm_password_hint, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newPass.equals(curPass)) {
                        Toast.makeText(this, R.string.security_password_same_as_current, Toast.LENGTH_SHORT).show();
                        currentStep[0] = 2;
                        updateStepUi.run();
                        return;
                    }
                    if (!newPass.equals(confirmPass)) {
                        Toast.makeText(this, R.string.security_password_mismatch, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    btnAction.setEnabled(false);

                    // Re-authenticate user with current password
                    com.google.firebase.auth.AuthCredential credential =
                            com.google.firebase.auth.EmailAuthProvider.getCredential(email.trim(), curPass);

                    user.reauthenticate(credential)
                            .addOnSuccessListener(aVoid -> {
                                user.updatePassword(newPass)
                                        .addOnSuccessListener(unused -> {
                                            Toast.makeText(this, R.string.security_password_updated, Toast.LENGTH_SHORT).show();
                                            dialog.dismiss();
                                        })
                                        .addOnFailureListener(e -> {
                                            btnAction.setEnabled(true);
                                            Toast.makeText(this, e.getLocalizedMessage() != null ? e.getLocalizedMessage() : e.getMessage(), Toast.LENGTH_SHORT).show();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                btnAction.setEnabled(true);
                                Toast.makeText(this, R.string.security_password_incorrect, Toast.LENGTH_SHORT).show();
                                currentStep[0] = 1;
                                updateStepUi.run();
                            });
                }
            });
        }

        dialog.show();
    }

    private void setupPasswordVisibilityToggle(EditText editText, ImageView toggleButton) {
        if (editText == null || toggleButton == null) return;

        final boolean[] isVisible = new boolean[]{false};
        toggleButton.setOnClickListener(v -> {
            isVisible[0] = !isVisible[0];
            if (isVisible[0]) {
                editText.setTransformationMethod(android.text.method.HideReturnsTransformationMethod.getInstance());
                toggleButton.setImageResource(R.drawable.ic_eye);
                toggleButton.setContentDescription("Hide password");
            } else {
                editText.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
                toggleButton.setImageResource(R.drawable.ic_eye_off);
                toggleButton.setContentDescription("Show password");
            }
            // Keep cursor position at the end of text
            try {
                editText.setSelection(editText.getText().length());
            } catch (Exception ignored) {
            }
        });
    }

    private void updateRuleUi(ImageView icon, TextView tv, boolean passed, int colorPassed, int colorMuted) {
        if (icon == null || tv == null) return;
        if (passed) {
            icon.setImageResource(R.drawable.ic_check_circle_pink);
            tv.setTextColor(colorPassed);
        } else {
            icon.setImageResource(R.drawable.ic_check_circle_grey);
            tv.setTextColor(colorMuted);
        }
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
