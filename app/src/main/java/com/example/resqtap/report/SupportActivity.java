package com.example.resqtap.report;
import com.example.resqtap.R;

import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.chat.LivechatActivity;
import com.example.resqtap.utils.ThemeUtils;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;


/**
 * SupportActivity
 * Pusat Bantuan: FAQ, emergency hotline, dan shortcut ke Livechat.
 */
public class SupportActivity extends BaseActivity {
    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_support);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        View back = findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> finish());

        View livechat = findViewById(R.id.card_livechat_entry);
        if (livechat != null) {
            livechat.setOnClickListener(v -> {
                Intent intent = new Intent(this, com.example.resqtap.chat.ChatActivity.class);
                startActivity(intent);
            });
        }
    }
}
