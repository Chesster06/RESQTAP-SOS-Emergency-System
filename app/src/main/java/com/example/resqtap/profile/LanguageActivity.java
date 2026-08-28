package com.example.resqtap.profile;
import com.example.resqtap.R;

import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.utils.LocaleUtils;
import com.example.resqtap.utils.ThemeUtils;

import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;


/**
 * LanguageActivity
 * Pilih bahasa app (Melayu, English, Mandarin, Tamil) tanpa perlu restart phone.
 */
public class LanguageActivity extends BaseActivity {
    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_language);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        RadioGroup group = findViewById(R.id.lang_group);
        RadioButton ms = findViewById(R.id.lang_ms);
        RadioButton en = findViewById(R.id.lang_en);
        RadioButton zh = findViewById(R.id.lang_zh);
        RadioButton ta = findViewById(R.id.lang_ta);

        String current = LocaleUtils.getSavedLanguageTag(this);
        if ("ms".equalsIgnoreCase(current)) ms.setChecked(true);
        else if ("zh".equalsIgnoreCase(current)) zh.setChecked(true);
        else if ("ta".equalsIgnoreCase(current)) ta.setChecked(true);
        else en.setChecked(true);

        group.setOnCheckedChangeListener((g, checkedId) -> {
            String next;
            if (checkedId == R.id.lang_ms) next = "ms";
            else if (checkedId == R.id.lang_zh) next = "zh";
            else if (checkedId == R.id.lang_ta) next = "ta";
            else next = "en";
            String prev = LocaleUtils.getSavedLanguageTag(LanguageActivity.this);
            if (next.equalsIgnoreCase(prev)) return;
            LocaleUtils.setLocaleAndPersist(LanguageActivity.this, next);

            try {
                recreate();
            } catch (Exception ignored) {
            }
}
