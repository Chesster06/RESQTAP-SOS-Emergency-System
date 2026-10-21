package com.example.resqtap.profile;
import com.example.resqtap.R;

import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.utils.AccessibilityUtils;
import com.example.resqtap.utils.BatterySystemUtils;
import com.example.resqtap.utils.BottomNavUtils;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;

import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.switchmaterial.SwitchMaterial;


/**
 * SettingsActivity
 * Tetapan app: theme gelap/cerah, SOS timer, vibration, dan delete account.
 */
public class SettingsActivity extends BaseActivity {
    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        android.view.View root = findViewById(R.id.main);
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

        SwitchMaterial toggleDarkMode = findViewById(R.id.toggle_dark_mode);
        SwitchMaterial toggleTts = findViewById(R.id.toggle_tts);
        SwitchMaterial toggleFullscreenQuick = findViewById(R.id.toggle_fullscreen_quick_message);

        SwitchMaterial toggleLowBatteryAlert = findViewById(R.id.toggle_low_battery_alert);
        SwitchMaterial toggleBatterySaver = findViewById(R.id.toggle_battery_saver);

        android.view.View back = findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> finish());

        bindSwitchRow(R.id.row_dark_mode, toggleDarkMode);
        bindSwitchRow(R.id.row_tts, toggleTts);
        bindSwitchRow(R.id.row_fullscreen_quick_message, toggleFullscreenQuick);
        bindSwitchRow(R.id.row_low_battery_alert, toggleLowBatteryAlert);
        bindSwitchRow(R.id.row_battery_saver, toggleBatterySaver);

        if (toggleDarkMode != null) {
            toggleDarkMode.setChecked(UserPrefs.getNightMode(this) == AppCompatDelegate.MODE_NIGHT_YES);
            toggleDarkMode.setOnCheckedChangeListener((b, isChecked) -> {
                int mode = isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
                UserPrefs.setNightMode(SettingsActivity.this, mode);
                AppCompatDelegate.setDefaultNightMode(mode);
            });
        }



        toggleTts.setChecked(AccessibilityUtils.isTalkBackEnabled(this));
        toggleTts.setOnCheckedChangeListener((b, isChecked) -> {
            if (isChecked != AccessibilityUtils.isTalkBackEnabled(SettingsActivity.this)) {
                startActivity(new android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });

        toggleFullscreenQuick.setChecked(UserPrefs.isFullscreenQuickMessage(this));
        toggleFullscreenQuick.setOnCheckedChangeListener((b, isChecked) -> UserPrefs.setFullscreenQuickMessage(this, isChecked));

        toggleLowBatteryAlert.setChecked(UserPrefs.isLowBatteryAlert(this));
        toggleLowBatteryAlert.setOnCheckedChangeListener((b, isChecked) -> UserPrefs.setLowBatteryAlert(this, isChecked));

        toggleBatterySaver.setChecked(BatterySystemUtils.isSystemBatterySaverEnabled(this));
        toggleBatterySaver.setOnCheckedChangeListener((b, isChecked) -> {
            if (isChecked != BatterySystemUtils.isSystemBatterySaverEnabled(SettingsActivity.this)) {
                try {
                    startActivity(new android.content.Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS));
                } catch (Exception e) {
                    startActivity(new android.content.Intent(android.provider.Settings.ACTION_SETTINGS));
                }
            }
        });
    }

    /** Setup dan konfigurasi SwitchRow. */
    private void bindSwitchRow(int rowId, com.google.android.material.switchmaterial.SwitchMaterial toggle) {
        android.view.View row = findViewById(rowId);
        if (row == null || toggle == null) return;
        row.setOnClickListener(v -> toggle.toggle());
    }

    /** Aktiviti aktif dan sedia untuk interaksi pengguna. */
    @Override
    protected void onResume() {
        super.onResume();
        com.google.android.material.switchmaterial.SwitchMaterial toggleTts = findViewById(R.id.toggle_tts);
        if (toggleTts != null) {
            toggleTts.setOnCheckedChangeListener(null);
            toggleTts.setChecked(AccessibilityUtils.isTalkBackEnabled(this));
            toggleTts.setOnCheckedChangeListener((b, isChecked) -> {
                if (isChecked != AccessibilityUtils.isTalkBackEnabled(SettingsActivity.this)) {
                    startActivity(new android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
                }
            });
        }
        com.google.android.material.switchmaterial.SwitchMaterial toggleBatterySaver = findViewById(R.id.toggle_battery_saver);
        if (toggleBatterySaver != null) {
            toggleBatterySaver.setOnCheckedChangeListener(null);
            toggleBatterySaver.setChecked(BatterySystemUtils.isSystemBatterySaverEnabled(this));
            toggleBatterySaver.setOnCheckedChangeListener((b, isChecked) -> {
                if (isChecked != BatterySystemUtils.isSystemBatterySaverEnabled(SettingsActivity.this)) {
                    try {
                        startActivity(new android.content.Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS));
                    } catch (Exception e) {
                        startActivity(new android.content.Intent(android.provider.Settings.ACTION_SETTINGS));
                    }
                }
            });
        }
}
}
