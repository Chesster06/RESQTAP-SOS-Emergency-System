package com.example.resqtap.auth;
import com.example.resqtap.R;

import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.home.MainActivity;
import com.example.resqtap.room.FirebaseRoomClient;
import com.example.resqtap.utils.AvatarUtils;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.resqtap.contacts.EmergencyContact;
import com.example.resqtap.sos.SosServiceStarter;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.GoogleAuthProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.auth.SignInMethodQueryResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * LoginActivity
 * Handles user authentication through Firebase Email/Password and Google OAuth sign-in.
 * Features live debounce email pre-checking against registered records, strict domain validation,
 * automatic profile hydration into UserPrefs, and conditional navigation to MainActivity or RegisterActivity.
 */
public class LoginActivity extends BaseActivity {
    private static final String TAG = "LoginActivity";

    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private View btnSocialGoogle;
    private View layoutGoogleBtnContent;
    private View layoutGoogleLoading;

    /**
     * Disables default base activity entrance animations in favor of custom coordinator transitions.
     */
    @Override
    protected boolean shouldAnimateContentIn() {
        return false;
    }

    /**
     * Initializes activity views, sets up authentication providers, and restores user sessions.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        // Configure Edge-to-Edge full display support
        EdgeToEdge.enable(this);

        // Configure Google Sign-In options with Web Client ID for OAuth token verification
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // ActivityResult launcher for modern Google Sign-In intent result handling
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        setGoogleLoading(true);
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        handleGoogleSignInResult(task);
                    } else if (result.getResultCode() == RESULT_CANCELED) {
                        setGoogleLoading(false);
                        Toast.makeText(LoginActivity.this, R.string.toast_google_sign_in_cancelled, Toast.LENGTH_SHORT).show();
                    } else {
                        setGoogleLoading(false);
                        Toast.makeText(LoginActivity.this, R.string.toast_google_sign_in_failed, Toast.LENGTH_SHORT).show();
                    }
                }
        );

        setContentView(R.layout.activity_login);

        // Apply system window insets to adapt to status bar, navigation bar, and keyboard
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            View authForm = findViewById(R.id.auth_form);
            if (authForm != null) {
                int basePaddingBottom = (int) (36 * getResources().getDisplayMetrics().density);
                authForm.setPadding(
                        authForm.getPaddingLeft(),
                        authForm.getPaddingTop(),
                        authForm.getPaddingRight(),
                        basePaddingBottom + systemBars.bottom
                );
            }
            return insets;
        });

        // Check existing authenticated user session on launch
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current != null) {
            // Fast-path: If profile is already complete locally in UserPrefs, directly navigate to MainActivity
            if (UserPrefs.isPersonalInfoComplete(this)) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return;
            }
            // Slow-path: Check Supabase for complete profile before routing
            com.example.resqtap.supabase.SupabaseManager.getInstance().getProfile(current.getUid(), new com.example.resqtap.supabase.SupabaseManager.Callback<org.json.JSONObject>() {
                @Override
                public void onSuccess(org.json.JSONObject profile) {
                    if (profile != null) {
                        applySupabaseProfile(profile);
                        if (UserPrefs.isPersonalInfoComplete(LoginActivity.this)) {
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        }
                    }
                }
                @Override
                public void onError(Exception e) {
                    // Suppress failure and allow user to authenticate manually
                }
            });
        }

        // Show registration success banner if routed from RegisterActivity
        if (getIntent() != null && getIntent().getBooleanExtra("registered_success", false)) {
            Toast.makeText(this, R.string.toast_register_success_login, Toast.LENGTH_LONG).show();
        }

        // Initialize UI view bindings
        androidx.core.widget.NestedScrollView authPanel = findViewById(R.id.auth_panel);
        TextInputLayout emailLayout = findViewById(R.id.layout_email);
        TextInputLayout passwordLayout = findViewById(R.id.layout_password);
        TextInputEditText email = findViewById(R.id.input_email);
        TextInputEditText password = findViewById(R.id.input_password);
        MaterialButton btnLogin = findViewById(R.id.btn_login);
        TextView btnGoRegister = findViewById(R.id.btn_go_register);
        TextView forgotPassword = findViewById(R.id.tv_forgot_password);
        forceHeavyText(findViewById(R.id.title));
        forceHeavyText(btnGoRegister);

        if (btnGoRegister != null) {
            btnGoRegister.setOnClickListener(v -> {
                Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });
        }

        btnSocialGoogle = findViewById(R.id.btn_social_google);
        layoutGoogleBtnContent = findViewById(R.id.layout_google_btn_content);
        layoutGoogleLoading = findViewById(R.id.layout_google_loading);

        // Google Sign-In button trigger: signs out any existing Google client session before launching picker
        if (btnSocialGoogle != null) {
            btnSocialGoogle.setOnClickListener(v -> {
                if (googleSignInClient != null && googleSignInLauncher != null) {
                    setGoogleLoading(true);
                    googleSignInClient.signOut().addOnCompleteListener(t -> {
                        if (isFinishing() || isDestroyed()) return;
                        try {
                            Intent signInIntent = googleSignInClient.getSignInIntent();
                            googleSignInLauncher.launch(signInIntent);
                        } catch (Exception ex) {
                            setGoogleLoading(false);
                            Log.e(TAG, "Failed to launch Google Sign-In intent", ex);
                            Toast.makeText(LoginActivity.this, R.string.toast_google_sign_in_failed, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        }

        playLoginEntranceAnimations();

        emailLayout.setEndIconOnClickListener(v -> {});
        emailLayout.setEndIconCheckable(false);

        // Live validation and debounce pre-check for email input
        email.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                String value = s == null ? "" : s.toString().trim();
                // Cancel pending debounce checks on every new keystroke
                if (loginEmailDebounce != null) loginHandler.removeCallbacks(loginEmailDebounce);
                loginEmailKnownRegistered = false;
                btnLogin.setEnabled(true);

                // Reset error and indicator if field is cleared
                if (value.isEmpty()) {
                    emailLayout.setError(null); emailLayout.setErrorEnabled(false);
                    emailLayout.setEndIconDrawable(null);
                    emailLayout.setEndIconTintList(null);
                    return;
                }

                // Enforce allowed email domain restrictions (@gmail.com or official @resqtap.com)
                if (!isGmail(value)) {
                    emailLayout.setErrorEnabled(true);
                    emailLayout.setError(getString(R.string.gmail_only_warning));
                    emailLayout.setEndIconDrawable(R.drawable.ic_error_circle_24);
                    emailLayout.setEndIconTintList(null);
                    return;
                }

                // Clear previous formatting errors once domain is valid
                if (emailLayout.getError() != null) {
                    emailLayout.setError(null); emailLayout.setErrorEnabled(false);
                }
                emailLayout.setEndIconDrawable(null);
                emailLayout.setEndIconTintList(null);

                // Schedule background verification against registered user records (80ms debounce)
                loginEmailDebounce = () -> checkLoginEmailRegistered(value, emailLayout, btnLogin);
                loginHandler.postDelayed(loginEmailDebounce, 80);
            }
        });

        // Pre-fill email if passed from Registration or other screens
        String passedEmail = getIntent() != null ? getIntent().getStringExtra("email") : null;
        if (passedEmail != null && !passedEmail.trim().isEmpty()) {
            email.setText(passedEmail.trim());
            password.requestFocus();
        }

        // Real-time password input listener: clears error indicator upon user interaction
        password.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (passwordLayout != null) { passwordLayout.setError(null); passwordLayout.setErrorEnabled(false); }
            }
        });

        // Primary Login Button Click Listener: Validates form inputs and initiates authentication
        btnLogin.setOnClickListener(view -> {
            String emailValue = email.getText() == null ? "" : email.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
            String passwordValue = password.getText() == null ? "" : password.getText().toString();

            // Validate non-empty email
            if (emailValue.isEmpty()) {
                emailLayout.setError("Please enter your email address");
                emailLayout.setEndIconDrawable(R.drawable.ic_error_circle_24);
                emailLayout.setEndIconTintList(null);
                scrollToField(authPanel, emailLayout);
                return;
            }

            // Verify email domain constraint
            if (!isGmail(emailValue)) {
                emailLayout.setError(getString(R.string.gmail_only_warning));
                emailLayout.setEndIconDrawable(R.drawable.ic_error_circle_24);
                emailLayout.setEndIconTintList(null);
                scrollToField(authPanel, emailLayout);
                return;
            }

            // Validate non-empty password
            if (passwordValue.isEmpty()) {
                if (passwordLayout != null) {
                    passwordLayout.setError("Please enter your password");
                    scrollToField(authPanel, passwordLayout);
                }
                return;
            }

            // Disable button during network request to prevent duplicate submissions
            btnLogin.setEnabled(false);
            // Execute Firebase email/password authentication and retrieve user profile
            signInAndLoadProfile(emailValue, passwordValue, btnLogin);
        });

        // Forgot password navigation: passes current email input to ForgotPasswordActivity
        forgotPassword.setOnClickListener(view -> {
            Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            String emailValue = email.getText() == null ? "" : email.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
            if (!emailValue.isEmpty()) intent.putExtra("email", emailValue);
            startActivity(intent);
        });

    }

    /**
     * Smoothly scrolls the NestedScrollView to bring the invalid input field into user viewport.
     * Focuses the target view and offsets scroll by 80px for visual breathing room.
     */
    private void scrollToField(androidx.core.widget.NestedScrollView scroll, View targetView) {
        if (targetView == null) return;
        targetView.requestFocus();
        if (scroll != null) {
            scroll.post(() -> {
                int[] targetLoc = new int[2];
                targetView.getLocationOnScreen(targetLoc);
                int[] scrollLoc = new int[2];
                scroll.getLocationOnScreen(scrollLoc);
                int scrollY = targetLoc[1] - scrollLoc[1] + scroll.getScrollY();
                scroll.smoothScrollTo(0, Math.max(0, scrollY - 80));
            });
        }
    }

    private Runnable loginEmailDebounce;
    private final Handler loginHandler = new Handler(Looper.getMainLooper());
    private boolean loginEmailKnownRegistered = false;

    /**
     * Sanitizes email addresses to safely use as Firebase Realtime Database node keys.
     * Replaces periods with underscores and '@' with '_at_'.
     */
    public static String sanitizeEmailForDb(String email) {
        if (email == null) return "";
        return email.trim().toLowerCase(java.util.Locale.ROOT)
                .replace(".", "_")
                .replace("@", "_at_");
    }

    /**
     * Asynchronously verifies if the entered email is registered in Firebase Realtime Database.
     * Updates TextInputLayout end-icon indicators (green checkmark for registered, red error for unregistered).
     * Includes fallback check via FirebaseAuth fetchSignInMethodsForEmail and allows @resqtap.com admin accounts.
     */
    /**
     * Semak status pendaftaran e-mel merentasi Supabase, Firebase Auth, dan RTDB secara asynchronously.
     */
    private void verifyLoginEmailRegisteredAsync(String emailStr, BoolCallback callback) {
        String query = String.valueOf(emailStr == null ? "" : emailStr).trim().toLowerCase(java.util.Locale.ROOT);
        if (query.isEmpty() || !isGmail(query)) {
            if (callback != null) callback.onResult(false);
            return;
        }

        if (query.endsWith("@resqtap.com")) {
            if (callback != null) callback.onResult(true);
            return;
        }

        // 1. Semak database utama Supabase profiles
        com.example.resqtap.supabase.SupabaseManager.getInstance().checkEmailExists(query, new com.example.resqtap.supabase.SupabaseManager.Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean exists) {
                if (Boolean.TRUE.equals(exists)) {
                    if (callback != null) callback.onResult(true);
                    return;
                }

                // 2. Semak Firebase Authentication (Google/Password)
                FirebaseAuth.getInstance().fetchSignInMethodsForEmail(query).addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        java.util.List<String> methods = task.getResult().getSignInMethods();
                        if (methods != null && !methods.isEmpty()) {
                            if (callback != null) callback.onResult(true);
                            return;
                        }
                    }

                    // 3. Semak cache registeredEmails dalam RTDB
                    String sanitized = sanitizeEmailForDb(query);
                    FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                            .getReference("registeredEmails")
                            .child(sanitized)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot snapshot) {
                                    boolean reg = snapshot != null && snapshot.exists() && Boolean.TRUE.equals(snapshot.getValue(Boolean.class));
                                    if (callback != null) callback.onResult(reg);
                                }

                                @Override
                                public void onCancelled(DatabaseError error) {
                                    if (callback != null) callback.onResult(false);
                                }
                            });
                });
            }

            @Override
            public void onError(Exception error) {
                // Fallback sekiranya rangkaian Supabase ralat
                FirebaseAuth.getInstance().fetchSignInMethodsForEmail(query).addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        java.util.List<String> methods = task.getResult().getSignInMethods();
                        if (methods != null && !methods.isEmpty()) {
                            if (callback != null) callback.onResult(true);
                            return;
                        }
                    }
                    String sanitized = sanitizeEmailForDb(query);
                    FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                            .getReference("registeredEmails")
                            .child(sanitized)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot snapshot) {
                                    boolean reg = snapshot != null && snapshot.exists() && Boolean.TRUE.equals(snapshot.getValue(Boolean.class));
                                    if (callback != null) callback.onResult(reg);
                                }

                                @Override
                                public void onCancelled(DatabaseError error) {
                                    if (callback != null) callback.onResult(true);
                                }
                            });
                });
            }
        });
    }

    /**
     * Asynchronously verifies if the entered email is registered in Supabase or Firebase.
     * Updates TextInputLayout end-icon indicators (green checkmark for registered, red error for unregistered).
     */
    private void checkLoginEmailRegistered(String emailStr, TextInputLayout emailLayout, MaterialButton btnLogin) {
        String query = emailStr.trim().toLowerCase(java.util.Locale.ROOT);
        if (query.isEmpty() || !isGmail(query)) {
            return;
        }

        verifyLoginEmailRegisteredAsync(query, isRegistered -> {
            TextInputEditText inputEmail = findViewById(R.id.input_email);
            String liveInput = inputEmail != null && inputEmail.getText() != null ? inputEmail.getText().toString().trim().toLowerCase(java.util.Locale.ROOT) : "";
            if (!query.equals(liveInput)) return;

            if (isRegistered) {
                loginEmailKnownRegistered = true;
                emailLayout.setError(null);
                emailLayout.setErrorEnabled(false);
                emailLayout.setEndIconDrawable(R.drawable.ic_check_24);
                emailLayout.setEndIconTintList(ColorStateList.valueOf(ContextCompat.getColor(LoginActivity.this, R.color.success_green)));
                btnLogin.setEnabled(true);
            } else {
                loginEmailKnownRegistered = false;
                emailLayout.setErrorEnabled(true);
                emailLayout.setError(getString(R.string.toast_email_not_registered));
                emailLayout.setEndIconDrawable(R.drawable.ic_error_circle_24);
                emailLayout.setEndIconTintList(null);
            }
        });
    }

    /**
     * Checks whether an email address belongs to the allowed domains.
     */
    private boolean isGmail(String email) {
        String e = String.valueOf(email == null ? "" : email).trim().toLowerCase();
        return isAllowedEmailDomain(e);
    }

    /**
     * Validates email domain whitelist: accepts standard @gmail.com or official @resqtap.com admin emails.
     */
    private boolean isAllowedEmailDomain(String email) {
        if (email == null) return false;
        String e = email.trim().toLowerCase();
        return (e.endsWith("@gmail.com") || e.endsWith("@resqtap.com"))
                && (e.length() > "@gmail.com".length() || e.length() > "@resqtap.com".length());
    }

    /**
     * Applies bold styling and paint flags to ensure prominent typography.
     */
    private void forceHeavyText(TextView textView) {
        if (textView == null) return;
        textView.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        textView.getPaint().setFakeBoldText(true);
        textView.invalidate();
    }

    /**
     * Navigates to RegisterActivity with a smooth fade-in/fade-out activity transition.
     */
    private void openRegisterWithSwipe() {
        Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    /**
     * Sequentially animates alpha of all authentication input fields with a staggered delay.
     */
    private void fadeAuthFields(float fromAlpha, float toAlpha, Runnable endAction) {
        android.widget.LinearLayout form = findViewById(R.id.auth_form);
        if (form == null) {
            if (endAction != null) endAction.run();
            return;
        }
        int duration = 180;
        int animated = 0;
        for (int i = 1; i < form.getChildCount(); i++) {
            View child = form.getChildAt(i);
            child.animate().cancel();
            child.setAlpha(fromAlpha);
            child.animate().alpha(toAlpha).setDuration(duration).setStartDelay(i * 12L).start();
            animated++;
        }
        if (endAction == null) return;
        long delay = duration + (animated + 1L) * 12L;
        form.postDelayed(endAction, delay);
    }

    /**
     * Executes initial entrance animations for the top brand header and authentication form card.
     * Uses DecelerateInterpolator with subtle vertical translation and opacity fade.
     */
    private void playLoginEntranceAnimations() {
        View header = findViewById(R.id.header_area);
        View authPanel = findViewById(R.id.auth_panel);

        if (header != null) {
            header.setAlpha(0f);
            header.setTranslationY(-45f);
            header.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(480)
                    .setStartDelay(60)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }

        if (authPanel != null) {
            authPanel.setAlpha(0f);
            authPanel.setTranslationY(90f);
            authPanel.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(520)
                    .setStartDelay(140)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
    }

    /**
     * Builds a composite SpannableString for the registration prompt text.
     * Attaches a ClickableSpan to the action phrase ('Sign up') with custom primary brand styling.
     */
    private void setupRegisterPrompt(TextView registerPrompt) {
        registerPrompt.setText(R.string.login_signup_action);
        registerPrompt.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        registerPrompt.setOnClickListener(view -> openRegisterWithSwipe());
        registerPrompt.setHighlightColor(android.graphics.Color.TRANSPARENT);
        if (registerPrompt.getId() == R.id.btn_go_register) {
            return;
        }

        String fullText = getString(R.string.login_signup_prompt);
        String actionText = getString(R.string.login_signup_action);
        int start = fullText.indexOf(actionText);
        if (start < 0) {
            registerPrompt.setOnClickListener(view -> openRegisterWithSwipe());
            return;
        }
        int end = start + actionText.length();
        SpannableString spannable = new SpannableString(fullText);
        spannable.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                openRegisterWithSwipe();
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(ContextCompat.getColor(LoginActivity.this, R.color.brand_primary));
                ds.setUnderlineText(true);
                ds.setFakeBoldText(true);
            }
        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        registerPrompt.setText(spannable);
        registerPrompt.setMovementMethod(LinkMovementMethod.getInstance());
        registerPrompt.setHighlightColor(android.graphics.Color.TRANSPARENT);
    }

    /**
     * Authenticates the user with Firebase Authentication using email and password credentials.
     * On authentication success, fetches the user profile from Realtime Database under 'users/{uid}'.
     * If the profile node is missing, redirects to RegisterActivity to complete onboarding.
     * Once loaded, caches user data into UserPrefs, initiates background sync tasks, and navigates to MainActivity.
     */
    private void signInAndLoadProfile(String emailValue, String passwordValue, MaterialButton btnLogin) {
        FirebaseAuth.getInstance()
                .signInWithEmailAndPassword(emailValue, passwordValue)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        btnLogin.setEnabled(true);
                        Toast.makeText(this, R.string.toast_login_failed, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String uid = user.getUid();

                    // Retrieve user profile from Supabase
                    com.example.resqtap.supabase.SupabaseManager.getInstance().getProfile(uid, new com.example.resqtap.supabase.SupabaseManager.Callback<org.json.JSONObject>() {
                        @Override
                        public void onSuccess(org.json.JSONObject profile) {
                            if (profile == null) {
                                if (UserPrefs.isPersonalInfoComplete(LoginActivity.this)) {
                                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                    finish();
                                    return;
                                }
                                btnLogin.setEnabled(true);
                                Toast.makeText(LoginActivity.this, R.string.toast_login_profile_missing, Toast.LENGTH_LONG).show();
                                Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                                intent.putExtra("complete_profile", true);
                                intent.putExtra("uid", uid);
                                intent.putExtra("email", emailValue);
                                startActivity(intent);
                                finish();
                                return;
                            }

                            UserPrefs.setUid(LoginActivity.this, uid);
                            UserPrefs.setEmail(LoginActivity.this, emailValue);
                            applySupabaseProfile(profile);

                            ensureNotificationsPermissionBestEffort();

                            // Restore active room subscription
                            FirebaseRoomClient.fetchMostRecentUserRoomCodeQueued(uid, code -> {
                                String c = String.valueOf(code == null ? "" : code).trim().toUpperCase(java.util.Locale.ROOT);
                                if (c.isEmpty()) return;
                                try {
                                    UserPrefs.setActiveRoomCode(LoginActivity.this, c);
                                    SosServiceStarter.start(LoginActivity.this, c);
                                } catch (Exception ignored) {}
                            });

                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        }

                        @Override
                        public void onError(Exception error) {
                            if (UserPrefs.isPersonalInfoComplete(LoginActivity.this)) {
                                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                finish();
                                return;
                            }
                            btnLogin.setEnabled(true);
                            Toast.makeText(LoginActivity.this, R.string.toast_login_profile_missing, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "signInWithEmailAndPassword failed. email=" + emailValue, e);
                    showAuthError(e, btnLogin, emailValue);
                });
    }

    /**
     * Toggles UI loading states during the Google Sign-In process.
     * Hides standard button content and displays the animated GoogleDotsLoadingView while disabling inputs.
     */
    private void setGoogleLoading(boolean loading) {
        if (isFinishing() || isDestroyed()) return;
        if (layoutGoogleBtnContent != null) {
            layoutGoogleBtnContent.setVisibility(loading ? View.GONE : View.VISIBLE);
        }
        if (layoutGoogleLoading != null) {
            layoutGoogleLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
            if (layoutGoogleLoading instanceof GoogleDotsLoadingView) {
                if (loading) {
                    ((GoogleDotsLoadingView) layoutGoogleLoading).start();
                } else {
                    ((GoogleDotsLoadingView) layoutGoogleLoading).stop();
                }
            }
        }
        if (btnSocialGoogle != null) {
            btnSocialGoogle.setEnabled(!loading);
            btnSocialGoogle.setAlpha(loading ? 0.75f : 1.0f);
        }
        MaterialButton btnLogin = findViewById(R.id.btn_login);
        if (btnLogin != null) {
            btnLogin.setEnabled(!loading);
        }
    }

    /**
     * Processes Google Sign-In intent result task.
     * Extracts GoogleSignInAccount and proceeds to Firebase credential authentication.
     */
    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account != null && account.getIdToken() != null) {
                firebaseAuthWithGoogle(account);
            } else {
                setGoogleLoading(false);
                Toast.makeText(this, R.string.toast_google_sign_in_failed, Toast.LENGTH_SHORT).show();
            }
        } catch (ApiException e) {
            setGoogleLoading(false);
            Log.e(TAG, "Google sign in failed with code: " + e.getStatusCode(), e);
            Toast.makeText(this, getString(R.string.toast_google_sign_in_failed) + " (" + e.getStatusCode() + ")", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Bridges Google ID token credential into Firebase Authentication.
     */
    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        FirebaseUser user = task.getResult().getUser();
                        if (user != null) {
                            handlePostGoogleSignIn(user, acct);
                        } else {
                            setGoogleLoading(false);
                            Toast.makeText(this, R.string.toast_login_failed, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        setGoogleLoading(false);
                        Exception e = task.getException();
                        Log.e(TAG, "FirebaseAuthWithGoogle failed", e);
                        String errMsg = e != null && e.getMessage() != null ? e.getMessage() : getString(R.string.toast_login_failed);
                        Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Verifies user profile state in Realtime Database post Google authentication.
     * If user profile does not exist or personal info (IC/Phone) is incomplete,
     * deletes temporary Auth record and routes user to RegisterActivity with pre-filled Google claims.
     */
    private void handlePostGoogleSignIn(FirebaseUser user, GoogleSignInAccount acct) {
        String uid = user.getUid();
        String email = user.getEmail() != null ? user.getEmail() : (acct.getEmail() != null ? acct.getEmail() : "");
        UserPrefs.setUid(this, uid);
        if (!email.isEmpty()) {
            UserPrefs.setEmail(this, email);
        }

        com.example.resqtap.supabase.SupabaseManager.getInstance().getProfile(uid, new com.example.resqtap.supabase.SupabaseManager.Callback<org.json.JSONObject>() {
            @Override
            public void onSuccess(org.json.JSONObject profile) {
                if (profile != null) {
                    applySupabaseProfile(profile);
                    final String googlePhoto = acct.getPhotoUrl() != null ? AvatarUtils.getHighResUrl(acct.getPhotoUrl().toString()) : "";
                    if (!UserPrefs.isPersonalInfoComplete(LoginActivity.this)) {
                        final String googleToken = acct.getIdToken();
                        Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                        intent.putExtra("complete_profile", true);
                        intent.putExtra("from_google_sign_in", true);
                        intent.putExtra("google_id_token", googleToken);
                        intent.putExtra("google_photo_url", googlePhoto);
                        intent.putExtra("uid", uid);
                        intent.putExtra("email", email);
                        startActivity(intent);
                        finish();
                        return;
                    }

                    ensureNotificationsPermissionBestEffort();

                    // Sinkronkan profil Google ke Supabase
                    try {
                        String uName = UserPrefs.getName(LoginActivity.this);
                        if (uName.isEmpty()) uName = acct.getDisplayName() != null ? acct.getDisplayName() : "User";
                        String uPhone = UserPrefs.getPhoneNumber(LoginActivity.this);
                        String uPhoto = acct.getPhotoUrl() != null ? acct.getPhotoUrl().toString() : UserPrefs.getPhotoUrl(LoginActivity.this);
                        com.example.resqtap.supabase.SupabaseManager.getInstance().upsertProfile(uid, email, uName, uPhone, uPhoto, null);
                    } catch (Exception ignored) {}

                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                } else {
                    // Populate display name and split names for new Google registration
                    String displayName = acct.getDisplayName();
                    if (displayName != null && !displayName.trim().isEmpty()) {
                        String cleanName = displayName.trim();
                        int spaceIdx = cleanName.indexOf(' ');
                        if (spaceIdx > 0) {
                            UserPrefs.setFirstName(LoginActivity.this, cleanName.substring(0, spaceIdx).trim());
                            UserPrefs.setLastName(LoginActivity.this, cleanName.substring(spaceIdx + 1).trim());
                        } else {
                            UserPrefs.setFirstName(LoginActivity.this, cleanName);
                            UserPrefs.setLastName(LoginActivity.this, "");
                        }
                        UserPrefs.setName(LoginActivity.this, cleanName);
                    }

                    // Forward new Google user to registration flow to complete IC, phone, and emergency contacts
                    final String googleToken = acct.getIdToken();
                    final String googlePhoto = acct.getPhotoUrl() != null ? AvatarUtils.getHighResUrl(acct.getPhotoUrl().toString()) : "";
                    Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                    intent.putExtra("complete_profile", true);
                    intent.putExtra("from_google_sign_in", true);
                    intent.putExtra("google_id_token", googleToken);
                    intent.putExtra("google_photo_url", googlePhoto);
                    intent.putExtra("uid", uid);
                    intent.putExtra("email", email);
                    startActivity(intent);
                    finish();
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to fetch user from Supabase after Google sign-in", e);
                final String googleToken = acct.getIdToken();
                final String googlePhoto = acct.getPhotoUrl() != null ? AvatarUtils.getHighResUrl(acct.getPhotoUrl().toString()) : "";
                Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                intent.putExtra("complete_profile", true);
                intent.putExtra("from_google_sign_in", true);
                intent.putExtra("google_id_token", googleToken);
                intent.putExtra("google_photo_url", googlePhoto);
                intent.putExtra("uid", uid);
                intent.putExtra("email", email);
                startActivity(intent);
                finish();
            }
        });
    }

    /**
     * Parses Firebase authentication exceptions and displays user-friendly toast messages.
     * Categorizes rate limits, non-existent accounts, and invalid credentials accurately.
     */
    private void showAuthError(Exception e, MaterialButton btnLogin, String emailValue) {
        if (e instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) e).getErrorCode();
            if ("ERROR_TOO_MANY_REQUESTS".equalsIgnoreCase(code)) {
                btnLogin.setEnabled(true);
                Toast.makeText(this, R.string.toast_too_many_requests, Toast.LENGTH_LONG).show();
                return;
            }
            if ("ERROR_USER_NOT_FOUND".equalsIgnoreCase(code)) {
                btnLogin.setEnabled(true);
                Toast.makeText(this, R.string.toast_account_not_exist, Toast.LENGTH_LONG).show();
                return;
            }
            if ("ERROR_WRONG_PASSWORD".equalsIgnoreCase(code)
                    || "ERROR_INVALID_CREDENTIAL".equalsIgnoreCase(code)
                    || "ERROR_INVALID_LOGIN_CREDENTIALS".equalsIgnoreCase(code)) {
                btnLogin.setEnabled(true);
                checkRegisteredEmail(emailValue, registered -> {
                    if (registered) {
                        Toast.makeText(this, R.string.toast_wrong_password_google_hint, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, R.string.toast_account_not_exist, Toast.LENGTH_LONG).show();
                    }
                });
                return;
            }
        }
        if (e instanceof com.google.firebase.auth.FirebaseAuthInvalidUserException) {
            btnLogin.setEnabled(true);
            Toast.makeText(this, R.string.toast_account_not_exist, Toast.LENGTH_LONG).show();
            return;
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            btnLogin.setEnabled(true);
            checkRegisteredEmail(emailValue, registered -> {
                if (registered) {
                    Toast.makeText(this, R.string.toast_wrong_password_google_hint, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, R.string.toast_account_not_exist, Toast.LENGTH_LONG).show();
                }
            });
            return;
        }
        String detail = e.getMessage() == null ? "" : e.getMessage().trim();
        btnLogin.setEnabled(true);
        if (!detail.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_login_failed) + " (" + detail + ")", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, R.string.toast_login_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Best-effort check and request for POST_NOTIFICATIONS runtime permission on Android 13+ (API 33+).
     */
    private void ensureNotificationsPermissionBestEffort() {
        if (android.os.Build.VERSION.SDK_INT < 33) return;
        try {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) return;
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 9901);
        } catch (Exception ignored) {
        }
    }

    private interface BoolCallback {
        void onResult(boolean value);
    }

    private interface VoidCallback {
        void run();
    }

    /**
     * Checks if the given email exists in Supabase, registeredEmails, or FirebaseAuth.
     * Invokes callback with true if registered, otherwise false.
     */
    private void checkRegisteredEmail(String email, BoolCallback callback) {
        String query = String.valueOf(email == null ? "" : email).trim().toLowerCase(java.util.Locale.ROOT);
        verifyLoginEmailRegisteredAsync(query, callback);
    }

    /**
     * Semak kewujudan akaun e-mel melalui FirebaseAuth.
     */
    private void checkEmailExistsInDb(String emailLower, BoolCallback onSuccess, VoidCallback onFailure) {
        checkRegisteredEmail(emailLower, exists -> {
            if (onSuccess != null) onSuccess.onResult(exists);
        });
    }

    /**
     * Hydrates local SharedPreferences (UserPrefs) from a remote Firebase Realtime Database DataSnapshot.
     * Parses personal identity details, medical metadata (blood type, allergies, conditions),
     * profile photo assets, and ensures a valid 4-digit public identifier is populated.
     */
    private void applyUserSnapshot(DataSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) return;
        Object nameObj = snapshot.child("name").getValue();
        if (nameObj != null) UserPrefs.setName(this, String.valueOf(nameObj));
        Object bloodObj = snapshot.child("bloodType").getValue();
        if (bloodObj != null) UserPrefs.setBloodType(this, String.valueOf(bloodObj));
        Object allergiesObj = snapshot.child("allergies").getValue();
        if (allergiesObj != null) UserPrefs.setAllergies(this, String.valueOf(allergiesObj));
        Object weightObj = snapshot.child("weight").getValue();
        UserPrefs.setWeight(this, weightObj == null ? "" : String.valueOf(weightObj));
        Object heightObj = snapshot.child("height").getValue();
        UserPrefs.setHeight(this, heightObj == null ? "" : String.valueOf(heightObj));
        Object genderObj = snapshot.child("gender").getValue();
        if (genderObj != null) UserPrefs.setGender(this, String.valueOf(genderObj));
        Object icObj = snapshot.child("icNumber").getValue();
        if (icObj != null) UserPrefs.setIcNumber(this, String.valueOf(icObj));

        Object medObj = snapshot.child("medications").getValue();
        String medications = medObj == null ? "" : String.valueOf(medObj).trim();
        Object condObj = snapshot.child("existingConditions").getValue();
        String conditions = condObj == null ? "" : String.valueOf(condObj).trim();
        if (conditions.isEmpty() && !medications.isEmpty()) {
            conditions = medications;
        }
        UserPrefs.setExistingConditions(this, conditions);
        UserPrefs.setMedications(this, medications);

        Object organObj = snapshot.child("organDonor").getValue();
        if (organObj != null) UserPrefs.setOrganDonor(this, String.valueOf(organObj));
        Object emailObj = snapshot.child("email").getValue();
        if (emailObj != null) UserPrefs.setEmail(this, String.valueOf(emailObj));
        Object publicIdObj = snapshot.child("publicId").getValue();
        String pubId = publicIdObj == null ? "" : String.valueOf(publicIdObj).trim();
        if (pubId.isEmpty()) {
            pubId = com.example.resqtap.friend.FirebaseFriendClient.format4DigitId(snapshot.getKey(), "");
        } else {
            pubId = com.example.resqtap.friend.FirebaseFriendClient.format4DigitId(snapshot.getKey(), pubId);
        }
        UserPrefs.setPublicId(this, pubId);

        Object photoUrlObj = snapshot.child("photoUrl").getValue();
        String photoUrl = photoUrlObj == null ? "" : String.valueOf(photoUrlObj);
        if (photoUrl == null) photoUrl = "";
        photoUrl = photoUrl.trim();
        if (photoUrl.isEmpty()) {
            Object photoUriObj = snapshot.child("photoUri").getValue();
            photoUrl = photoUriObj == null ? "" : String.valueOf(photoUriObj);
            if (photoUrl == null) photoUrl = "";
            photoUrl = photoUrl.trim();
        }
        if (!photoUrl.isEmpty()) UserPrefs.setPhotoUrl(this, photoUrl);

        Object photoB64Obj = snapshot.child("photoB64").getValue();
        if (photoB64Obj != null) {
            String b64 = String.valueOf(photoB64Obj);
            if (b64 != null && !b64.trim().isEmpty()) UserPrefs.setPhotoB64(this, b64.trim());
        }

        String pendingPhotoUri = String.valueOf(UserPrefs.getPendingPhotoUri(this) == null ? "" : UserPrefs.getPendingPhotoUri(this)).trim();
        if (!pendingPhotoUri.isEmpty()) {
            retryPendingPhotoSaveToDb(currentUidFromAuth(), pendingPhotoUri);
        }

        Object addrObj = snapshot.child("address").getValue();
        if (addrObj != null) UserPrefs.setAddress(this, String.valueOf(addrObj));
        Object relObj = snapshot.child("religion").getValue();
        if (relObj != null) UserPrefs.setReligion(this, String.valueOf(relObj));
        Object phoneObj = snapshot.child("phoneNumber").getValue();
        if (phoneObj != null) UserPrefs.setPhoneNumber(this, String.valueOf(phoneObj));
        Object dobObj = snapshot.child("dateOfBirth").getValue();
        if (dobObj != null) UserPrefs.setDateOfBirth(this, String.valueOf(dobObj));
        Object ethObj = snapshot.child("ethnicity").getValue();
        if (ethObj != null) UserPrefs.setEthnicity(this, String.valueOf(ethObj));

        try {
            DataSnapshot contactsSnap = snapshot.child("emergencyContacts");
            if (contactsSnap != null && contactsSnap.exists()) {
                java.util.ArrayList<EmergencyContact> contacts = new java.util.ArrayList<>();
                for (DataSnapshot child : contactsSnap.getChildren()) {
                    if (child == null) continue;
                    String id = child.child("id").getValue() == null ? "" : String.valueOf(child.child("id").getValue());
                    if (id.trim().isEmpty()) id = child.getKey() == null ? "" : child.getKey();
                    String name = child.child("name").getValue() == null ? "" : String.valueOf(child.child("name").getValue());
                    String rel = child.child("relationship").getValue() == null ? "" : String.valueOf(child.child("relationship").getValue());
                    String phone = child.child("phone").getValue() == null ? "" : String.valueOf(child.child("phone").getValue());
                    if (id == null) id = "";
                    if (name == null) name = "";
                    if (rel == null) rel = "";
                    if (phone == null) phone = "";
                    if (id.trim().isEmpty()) continue;
                    contacts.add(new EmergencyContact(id.trim(), name, rel, phone));
                }
                UserPrefs.setEmergencyContacts(this, contacts);
            }
        } catch (Exception ignored) {
        }
    }

    private void applySupabaseProfile(org.json.JSONObject profile) {
        if (profile == null) return;
        try {
            String name = profile.optString("full_name", "");
            if (!name.isEmpty()) UserPrefs.setName(this, name);
            String phone = profile.optString("phone", "");
            if (phone.isEmpty()) phone = profile.optString("phone_number", "");
            if (!phone.isEmpty()) UserPrefs.setPhoneNumber(this, phone);
            String ic = profile.optString("ic_number", "");
            if (!ic.isEmpty()) UserPrefs.setIcNumber(this, ic);
            String gender = profile.optString("gender", "");
            if (!gender.isEmpty()) UserPrefs.setGender(this, gender);
            String dob = profile.optString("dob", "");
            if (!dob.isEmpty()) UserPrefs.setDateOfBirth(this, dob);
            String address = profile.optString("address", "");
            if (!address.isEmpty()) UserPrefs.setAddress(this, address);
            String photo = profile.optString("photo_url", "");
            if (photo.isEmpty()) photo = profile.optString("avatar_url", "");
            if (!photo.isEmpty()) UserPrefs.setPhotoUrl(this, photo);

            String uid = profile.optString("id", "");
            String pubId = com.example.resqtap.friend.FirebaseFriendClient.format4DigitId(uid, "");
            UserPrefs.setPublicId(this, pubId);
        } catch (Exception ignored) {}
    }

    /**
     * Extracts the current Firebase authenticated user's unique identifier (UID).
     */
    private String currentUidFromAuth() {
        try {
            com.google.firebase.auth.FirebaseUser u = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            return u == null ? "" : String.valueOf(u.getUid() == null ? "" : u.getUid()).trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * Retries uploading any pending local avatar URI to the Realtime Database as Base64.
     * Compresses the bitmap to 256px max dimension at 72% JPEG quality.
     */
    private void retryPendingPhotoSaveToDb(String uid, String localUri) {
        try {
            String u = String.valueOf(uid == null ? "" : uid).trim();
            String uriStr = String.valueOf(localUri == null ? "" : localUri).trim();
            if (u.isEmpty() || uriStr.isEmpty()) return;
            android.net.Uri uri = android.net.Uri.parse(uriStr);
            String b64 = encodeAvatarToBase64(uri, 256, 72);
            if (b64 == null || b64.trim().isEmpty()) return;
            UserPrefs.setPhotoB64(this, b64.trim());
            UserPrefs.setPendingPhotoUri(this, "");
            FirebaseRoomClient.updateUserPhotoB64Queued(u, b64.trim());
        } catch (Exception ignored) {
        }
    }

    /**
     * Encodes a local image URI to a Base64-encoded JPEG string with memory-safe downsampling.
     * Uses inJustDecodeBounds to determine aspect ratio before allocation, downsamples to maxDim,
     * and compresses with the specified JPEG quality to prevent OutOfMemory errors.
     */
    private String encodeAvatarToBase64(android.net.Uri localUri, int maxDim, int jpegQuality) {
        try {
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (java.io.InputStream in = getContentResolver().openInputStream(localUri)) {
                if (in == null) return "";
                android.graphics.BitmapFactory.decodeStream(in, null, bounds);
            }
            int w = Math.max(1, bounds.outWidth);
            int h = Math.max(1, bounds.outHeight);
            int sample = 1;
            int m = Math.max(w, h);
            while (m / sample > maxDim * 2) sample *= 2;

            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888;
            android.graphics.Bitmap b;
            try (java.io.InputStream in2 = getContentResolver().openInputStream(localUri)) {
                if (in2 == null) return "";
                b = android.graphics.BitmapFactory.decodeStream(in2, null, opts);
            }
            if (b == null) return "";
            int bw = b.getWidth();
            int bh = b.getHeight();
            int mm = Math.max(bw, bh);
            if (mm > maxDim) {
                float s = maxDim / (float) mm;
                int nw = Math.max(1, Math.round(bw * s));
                int nh = Math.max(1, Math.round(bh * s));
                android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(b, nw, nh, true);
                b.recycle();
                b = scaled;
            }
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            b.compress(android.graphics.Bitmap.CompressFormat.JPEG, Math.max(10, Math.min(jpegQuality, 95)), baos);
            b.recycle();
            byte[] bytes = baos.toByteArray();
            return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
        } catch (Exception ignored) {
            return "";
        }
    }
}

