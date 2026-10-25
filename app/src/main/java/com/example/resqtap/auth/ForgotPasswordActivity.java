package com.example.resqtap.auth;
import com.example.resqtap.R;

import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.utils.ThemeUtils;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;


/**
 * ForgotPasswordActivity
 * Skrin Forgot Password: hantar link reset password ke email user guna Firebase Auth.
 */
public class ForgotPasswordActivity extends BaseActivity {
    private static final String TAG = "ForgotPasswordActivity";

    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_forgot_password);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        TextInputLayout emailLayout = findViewById(R.id.layout_email);
        TextInputEditText email = findViewById(R.id.input_email);
        MaterialButton btnSend = findViewById(R.id.btn_send_reset_email);
        MaterialButton btnBack = findViewById(R.id.btn_back_login);

        String presetEmail = getIntent() == null ? "" : String.valueOf(getIntent().getStringExtra("email"));
        presetEmail = "null".equals(presetEmail) ? "" : presetEmail.trim().toLowerCase(java.util.Locale.ROOT);
        if (!presetEmail.isEmpty()) email.setText(presetEmail);

        emailLayout.setEndIconOnClickListener(v -> {});
        emailLayout.setEndIconCheckable(false);
        email.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                String value = s == null ? "" : s.toString().trim();
                emailLayout.setError(null);
                emailLayout.setEndIconDrawable(null);
                emailLayout.setEndIconTintList(null);
                btnSend.setEnabled(true);

                if (value.isEmpty()) return;

                if (!isGmail(value)) {
                    emailLayout.setError(getString(R.string.gmail_only_warning));
                    emailLayout.setEndIconDrawable(R.drawable.ic_error_24);
                    emailLayout.setEndIconTintList(ColorStateList.valueOf(ContextCompat.getColor(ForgotPasswordActivity.this, R.color.brand_red)));
                    btnSend.setEnabled(false);
                    return;
                }

                emailLayout.setEndIconDrawable(R.drawable.ic_check_24);
                emailLayout.setEndIconTintList(ColorStateList.valueOf(ContextCompat.getColor(ForgotPasswordActivity.this, R.color.success_green)));
            }
        });

        btnSend.setOnClickListener(view -> {
            String emailValue = email.getText() == null ? "" : email.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
            sendPasswordReset(emailValue, emailLayout, btnSend);
        });
        btnBack.setOnClickListener(view -> finish());
    }

    /** Semak dan sahkan Gmail. */
    private boolean isGmail(String email) {
        String e = String.valueOf(email == null ? "" : email).trim().toLowerCase();
        return (e.endsWith("@gmail.com") || e.endsWith("@resqtap.com"))
                && (e.length() > "@gmail.com".length() || e.length() > "@resqtap.com".length());
    }

    /** Simpan atau hantar data PasswordReset. */
    private void sendPasswordReset(String emailValue, TextInputLayout emailLayout, MaterialButton btnSend) {
        if (emailValue.isEmpty()) {
            Toast.makeText(this, R.string.toast_reset_email_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isGmail(emailValue)) {
            emailLayout.setError(getString(R.string.gmail_only_warning));
            Toast.makeText(this, R.string.gmail_only_warning, Toast.LENGTH_LONG).show();
            return;
        }

        btnSend.setEnabled(false);
        FirebaseAuth.getInstance()
                .sendPasswordResetEmail(emailValue)
                .addOnSuccessListener(unused -> {
                    btnSend.setEnabled(true);
                    Toast.makeText(this, R.string.toast_reset_email_sent, Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    btnSend.setEnabled(true);
                    Log.e(TAG, "sendPasswordResetEmail failed. email=" + emailValue, e);
                    if (e instanceof FirebaseAuthException
                            && "ERROR_TOO_MANY_REQUESTS".equalsIgnoreCase(((FirebaseAuthException) e).getErrorCode())) {
                        Toast.makeText(this, R.string.toast_too_many_requests, Toast.LENGTH_LONG).show();
                        return;
                    }
                    Toast.makeText(this, R.string.toast_reset_email_failed, Toast.LENGTH_LONG).show();
                });
    }
}
