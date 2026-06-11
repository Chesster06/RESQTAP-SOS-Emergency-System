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
}
