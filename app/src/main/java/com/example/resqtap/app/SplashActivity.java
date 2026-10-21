package com.example.resqtap.app;
import com.example.resqtap.R;

import com.example.resqtap.auth.GetStartedActivity;
import com.example.resqtap.auth.LoginActivity;
import com.example.resqtap.auth.RegisterActivity;
import com.example.resqtap.home.MainActivity;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;


/**
 * SplashActivity
 * Splash screen: check status login Firebase Auth sebelum masuk ke Main atau Login.
 */
public class SplashActivity extends BaseActivity {
    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        View logoWrapper = findViewById(R.id.logo_wrapper);
        View appName = findViewById(R.id.app_name);
        View tagline = findViewById(R.id.tagline);

        if (logoWrapper != null) {
            logoWrapper.setVisibility(View.VISIBLE);
            Animation logoAnim = AnimationUtils.loadAnimation(this, R.anim.splash_logo_in);
            logoWrapper.startAnimation(logoAnim);
        }

        if (appName != null) {
            appName.setVisibility(View.VISIBLE);
            Animation textAnim = AnimationUtils.loadAnimation(this, R.anim.splash_text_in);
            appName.startAnimation(textAnim);
        }

        if (tagline != null) {
            tagline.setVisibility(View.VISIBLE);
            Animation taglineAnim = AnimationUtils.loadAnimation(this, R.anim.splash_tagline_in);
            tagline.startAnimation(taglineAnim);
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            com.google.firebase.auth.FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
            if (current != null) {
                // Sahkan status akaun secara langsung dengan Firebase Auth (contoh: jika admin bersihkan DB dari website)
                current.reload().addOnCompleteListener(task -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (!task.isSuccessful() && task.getException() instanceof com.google.firebase.auth.FirebaseAuthInvalidUserException) {
                        // Akaun telah dipadam pada pelayan; bersihkan cache tempatan dan bawa ke LoginActivity
                        FirebaseAuth.getInstance().signOut();
                        UserPrefs.clearAccountData(SplashActivity.this);
                        Intent i = new Intent(SplashActivity.this, LoginActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                        try {
                            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                        } catch (Exception ignored) {}
                        finish();
                        return;
                    }

                    // Semak juga jika profil pengguna wujud di RTDB
                    com.google.firebase.database.FirebaseDatabase.getInstance(com.example.resqtap.room.FirebaseRoomClient.DATABASE_URL)
                            .getReference("users")
                            .child(current.getUid())
                            .get()
                            .addOnCompleteListener(dbTask -> {
                                if (isFinishing() || isDestroyed()) return;

                                if (dbTask.isSuccessful() && dbTask.getResult() != null && dbTask.getResult().exists()) {
                                    UserPrefs.applyUserSnapshot(SplashActivity.this, dbTask.getResult());
                                }

                                if (UserPrefs.isPersonalInfoComplete(SplashActivity.this)) {
                                    launchMain();
                                } else {
                                    // Jika pendaftaran belum selesai atau profil tidak lengkap
                                    Intent i = new Intent(SplashActivity.this, LoginActivity.class);
                                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(i);
                                    try {
                                        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                                    } catch (Exception ignored) {}
                                    finish();
                                }
                            });
                });
            } else {
                Intent i = new Intent(SplashActivity.this, GetStartedActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                try {
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                } catch (Exception ignored) {
                }
                finish();
            }
        }, 2500);
    }

    private void launchMain() {
        if (UserPrefs.isSosProgressActive(this)) {
            String room = UserPrefs.getActiveSosProgressRoom(this);
            String alertId = UserPrefs.getActiveSosProgressAlert(this);
            if (!alertId.isEmpty()) {
                com.example.resqtap.sos.SosProgressActivity.launch(SplashActivity.this, room, alertId);
                try {
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                } catch (Exception ignored) {
                }
                finish();
                return;
            }
        }

        Intent i = new Intent(SplashActivity.this, MainActivity.class);
        if (getIntent() != null && getIntent().getData() != null) {
            i.setData(getIntent().getData());
            i.setAction(getIntent().getAction());
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        try {
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        } catch (Exception ignored) {
        }
        finish();
    }
}

