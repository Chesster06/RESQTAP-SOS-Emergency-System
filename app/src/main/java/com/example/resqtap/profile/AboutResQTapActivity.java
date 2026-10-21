package com.example.resqtap.profile;
import com.example.resqtap.R;

import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.utils.ThemeUtils;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;


/**
 * AboutResQTapActivity
 * Info app ResQTap, version, dan privacy policy.
 */
public class AboutResQTapActivity extends BaseActivity {
    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_about_resqtap);

        View root = findViewById(R.id.main);
        MaterialToolbar topAppBar = findViewById(R.id.top_app_bar);
        View scroll = findViewById(R.id.scroll);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            if (topAppBar != null) {
                int pl = topAppBar.getPaddingLeft();
                int pr = topAppBar.getPaddingRight();
                int pb = topAppBar.getPaddingBottom();
                topAppBar.setPadding(pl, systemBars.top, pr, pb);
            }
            if (scroll != null) {
                int pl = scroll.getPaddingLeft();
                int pt = scroll.getPaddingTop();
                int pr = scroll.getPaddingRight();
                scroll.setPadding(pl, pt, pr, systemBars.bottom);
            }
            return insets;
        });

        if (topAppBar != null) topAppBar.setNavigationOnClickListener(v -> finish());

        View termsHeader = findViewById(R.id.terms_header);
        View termsBody = findViewById(R.id.terms_body);
        ImageView termsChevron = findViewById(R.id.terms_chevron);

        if (termsHeader != null && termsBody != null) {
            termsBody.setVisibility(View.GONE);
            termsHeader.setOnClickListener(v -> {
                boolean show = termsBody.getVisibility() != View.VISIBLE;
                termsBody.setVisibility(show ? View.VISIBLE : View.GONE);
                if (termsChevron != null) {
                    termsChevron.animate().rotation(show ? 90f : 0f).setDuration(150).start();
                }
            });
        }
    }
}
