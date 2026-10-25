package com.example.resqtap.auth;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.resqtap.R;
import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.utils.ThemeUtils;

/**
 * GetStartedActivity
 * Onboarding screen matching the modern reference design with polished fluid transitions.
 */
public class GetStartedActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_get_started);

        View heroArea = findViewById(R.id.hero_area);
        View bottomCard = findViewById(R.id.bottom_card);
        Button btnGetStarted = findViewById(R.id.btn_get_started);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);

            if (bottomCard != null) {
                int basePaddingBottom = (int) (36 * getResources().getDisplayMetrics().density);
                bottomCard.setPadding(
                        bottomCard.getPaddingLeft(),
                        bottomCard.getPaddingTop(),
                        bottomCard.getPaddingRight(),
                        basePaddingBottom + systemBars.bottom
                );
            }
            return insets;
        });

        // Animasi kemasukan (Entrance Animation)
        if (heroArea != null) {
            heroArea.setAlpha(0f);
            heroArea.setScaleX(0.92f);
            heroArea.setScaleY(0.92f);
            heroArea.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(550)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        if (bottomCard != null) {
            bottomCard.setAlpha(0f);
            bottomCard.setTranslationY(75f);
            bottomCard.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(520)
                    .setStartDelay(100)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        // Animasi keluar dan transisi peralihan ke LoginActivity
        if (btnGetStarted != null) {
            btnGetStarted.setOnClickListener(v -> {
                btnGetStarted.setEnabled(false);

                // Efek spring bounce pada butang
                btnGetStarted.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(90)
                        .withEndAction(() -> {
                            btnGetStarted.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(100)
                                    .start();

                            // Animasi keluar bagi ilustrasi hero dan kad bawah
                            if (heroArea != null) {
                                heroArea.animate()
                                        .alpha(0f)
                                        .translationY(-70f)
                                        .setDuration(240)
                                        .setInterpolator(new AccelerateInterpolator())
                                        .start();
                            }

                            if (bottomCard != null) {
                                bottomCard.animate()
                                        .alpha(0f)
                                        .translationY(70f)
                                        .setDuration(220)
                                        .setInterpolator(new AccelerateInterpolator())
                                        .start();
                            }

                            v.postDelayed(() -> {
                                if (isFinishing() || isDestroyed()) return;
                                Intent intent = new Intent(GetStartedActivity.this, LoginActivity.class);
                                startActivity(intent);
                                if (Build.VERSION.SDK_INT >= 34) {
                                    overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.fade_in, R.anim.fade_out);
                                } else {
                                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                                }
                                finish();
                            }, 180);
                        })
                        .start();
            });
        }
    }
}
