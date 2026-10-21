package com.example.resqtap.profile;
import com.example.resqtap.R;

import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.auth.LoginActivity;
import com.example.resqtap.contacts.EmergencyContactsListActivity;
import com.example.resqtap.report.SupportActivity;

import com.example.resqtap.utils.BottomNavUtils;
import com.example.resqtap.utils.LocaleUtils;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Locale;


/**
 * ProfileActivity
 * Skrin Profil: paparan maklumat peribadi dan info perubatan kecemasan.
 */
public class ProfileActivity extends BaseActivity {
    private TextView headerNameView;
    private TextView headerEmailView;
    private ShapeableImageView headerPhotoView;
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();
    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        // SEC-08 FIX: Prevent screenshots and recent-apps preview leakage of personal & medical information
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile);
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

        headerPhotoView = findViewById(R.id.profile_photo);
        headerNameView = findViewById(R.id.tv_profile_name);
        headerEmailView = findViewById(R.id.tv_profile_subinfo);
        if (headerEmailView != null) {
            headerEmailView.setOnClickListener(v -> {
                String fullUidText = headerEmailView.getText().toString();
                String cleanUid = fullUidText.replace("UID:", "").trim();
                if (!cleanUid.isEmpty()) {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("UID", cleanUid);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(ProfileActivity.this, "UID copied to clipboard", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
        refreshHeader();

        android.view.View editBadge = findViewById(R.id.btn_avatar_edit_badge);
        if (editBadge != null) {
            editBadge.setOnClickListener(v -> startActivity(new Intent(this, EditProfileActivity.class)));
        }
        if (headerPhotoView != null) {
            headerPhotoView.setOnClickListener(v -> startActivity(new Intent(this, EditProfileActivity.class)));
        }

        android.view.View logoutIcon = findViewById(R.id.btn_logout_icon);
        if (logoutIcon != null) {
            logoutIcon.setOnClickListener(v -> {
                try {
                    android.view.View content = android.view.LayoutInflater.from(ProfileActivity.this)
                            .inflate(R.layout.dialog_logout_confirm, null, false);

                    androidx.appcompat.app.AlertDialog d = new MaterialAlertDialogBuilder(ProfileActivity.this)
                            .setView(content)
                            .create();

                    if (d.getWindow() != null) {
                        d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                    }

                    android.view.View cancel = content.findViewById(R.id.btn_cancel);
                    if (cancel != null) cancel.setOnClickListener(v2 -> d.dismiss());

                    android.view.View closeBtn = content.findViewById(R.id.btn_close);
                    if (closeBtn != null) closeBtn.setOnClickListener(v2 -> d.dismiss());

                    android.view.View logout = content.findViewById(R.id.btn_logout);
                    if (logout != null) {
                        logout.setOnClickListener(v2 -> {
                            try {
                                com.example.resqtap.sos.SosServiceStarter.stop(ProfileActivity.this);
                            } catch (Exception ignored) {
                            }
                            try {
                                FirebaseAuth.getInstance().signOut();
                            } catch (Exception ignored) {
                            }
                            try {
                                UserPrefs.clearAccountData(ProfileActivity.this);
                            } catch (Exception ignored) {
                            }
                            try {
                                Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            } catch (Exception ignored) {
                            }
                            try {
                                d.dismiss();
                            } catch (Exception ignored) {
                            }
                        });
                    }

                    d.show();
                } catch (Exception ignored) {
                }
            });
        }

        setupMenuItems();
    }

    /** Aktiviti aktif dan sedia untuk interaksi pengguna. */
    @Override
    protected void onResume() {
        super.onResume();
        refreshHeader();
    }

    private void refreshHeader() {
        String name = UserPrefs.getName(this);
        String email = UserPrefs.getEmail(this);
        String photoUri = UserPrefs.getPhotoUri(this);
        String photoUrl = UserPrefs.getPhotoUrl(this);
        String photoB64 = UserPrefs.getPhotoB64(this);

        String phone = UserPrefs.getPhoneNumber(this);
        String dob = UserPrefs.getDateOfBirth(this);
        String blood = UserPrefs.getBloodType(this);
        String publicId = UserPrefs.getPublicId(this);
        String uid = com.example.resqtap.utils.DeviceIdUtils.getStableUid(this);

        if (headerNameView != null) {
            String shownName = (name == null || name.trim().isEmpty()) ? getString(R.string.profile_name_default) : name.trim();
            headerNameView.setText(shownName);
        }
        if (headerEmailView != null) {
            String shownUid = (publicId != null && !publicId.trim().isEmpty()) ? publicId.trim().toUpperCase() : uid;
            headerEmailView.setText("UID: " + shownUid);
        }

        if (headerPhotoView != null) {
            if (photoB64 != null && !photoB64.trim().isEmpty()) {
                try {
                    byte[] bytes = android.util.Base64.decode(photoB64.trim(), android.util.Base64.DEFAULT);
                    android.graphics.Bitmap b = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (b != null) headerPhotoView.setImageBitmap(b);
                    else headerPhotoView.setImageResource(R.drawable.ic_avatar);
                } catch (Exception ignored) {
                    headerPhotoView.setImageResource(R.drawable.ic_avatar);
                }
                return;
            }
            if (photoUri != null && !photoUri.trim().isEmpty()) {
                try {
                    headerPhotoView.setImageURI(Uri.parse(photoUri));
                    return;
                } catch (Exception ignored) {
                }
            }
            if (photoUrl != null && !photoUrl.trim().isEmpty()) {
                headerPhotoView.setImageResource(R.drawable.ic_avatar);
                loadRemotePhotoInto(headerPhotoView, photoUrl.trim());
                return;
            }
            headerPhotoView.setImageResource(R.drawable.ic_avatar);
        }
    }

    private void loadRemotePhotoInto(ShapeableImageView view, String url) {
        if (view == null) return;
        final String u = url == null ? "" : url.trim();
        if (u.isEmpty()) return;
        if (u.startsWith("gs://")) {
            try {
                com.google.firebase.storage.StorageReference ref =
                        com.google.firebase.storage.FirebaseStorage.getInstance("gs://resqtap-b9ff5.firebasestorage.app").getReferenceFromUrl(u);
                ref.getBytes(4L * 1024L * 1024L)
                        .addOnSuccessListener(bytes -> {
                            if (bytes == null || bytes.length == 0) return;
                            android.graphics.Bitmap b = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            if (b == null) return;
                            runOnUiThread(() -> view.setImageBitmap(b));
                        });
            } catch (Exception ignored) {
            }
            return;
        }
        if (!u.startsWith("http")) return;

        imageExecutor.execute(() -> {
            android.graphics.Bitmap b = downloadBitmap(u);
            if (b == null) return;
            runOnUiThread(() -> view.setImageBitmap(b));
        });
    }

    private static android.graphics.Bitmap downloadBitmap(String url) {
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) return null;
            try (java.io.InputStream in = conn.getInputStream()) {
                return android.graphics.BitmapFactory.decodeStream(in);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    // SEKSYEN: ONDESTROY
    /** Pembersihan memori, buang listener, dan tutup sambungan. */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            imageExecutor.shutdownNow();
        } catch (Exception ignored) {
        }
    }

    /** Setup dan konfigurasi MenuItems dengan ikon berwarna ceria (Colorful Icons). */
    private void setupMenuItems() {
        setupItem(
                R.id.item_personal,
                R.drawable.ic_ios_person,
                0xFFE60067, // Vibrant Rose Pink
                0xFFFFE8F0, // Soft Rose Tint
                getString(R.string.profile_personal_info),
                "Personal info & medical details",
                null,
                () -> startActivity(new Intent(this, EditProfileActivity.class))
        );

        setupItem(
                R.id.item_contacts,
                R.drawable.ic_ios_contacts,
                0xFFFF7043, // Coral Orange
                0xFFFFF3E0, // Soft Coral Tint
                getString(R.string.profile_emergency_contacts),
                "Trusted contacts for SOS alerts",
                null,
                () -> startActivity(new Intent(this, EmergencyContactsListActivity.class))
        );

        setupItem(
                R.id.item_security,
                R.drawable.ic_ios_security,
                0xFF10B981, // Vibrant Emerald Green
                0xFFD1FAE5, // Soft Emerald Tint
                getString(R.string.profile_security),
                "App lock, biometrics & account security",
                null,
                () -> startActivity(new Intent(this, SecuritySettingsActivity.class))
        );


        String lang = getLanguageLabel();
        setupItem(
                R.id.item_language,
                R.drawable.ic_ios_globe,
                0xFF0284C7, // Vibrant Sky Blue
                0xFFE0F2FE, // Soft Sky Blue Tint
                getString(R.string.profile_language),
                "Change app display language",
                lang,
                this::showLanguageDropdown
        );

        setupItem(
                R.id.item_settings,
                R.drawable.ic_ios_settings,
                0xFF6366F1, // Indigo / Slate
                0xFFEEF2FF, // Soft Indigo Tint
                getString(R.string.bottom_nav_settings),
                "Preferences, theme & system options",
                null,
                () -> startActivity(new Intent(this, SettingsActivity.class))
        );

        setupItem(
                R.id.item_help,
                R.drawable.ic_ios_help_circle,
                0xFFA855F7, // Violet Purple
                0xFFF3E8FF, // Soft Violet Tint
                getString(R.string.profile_help_support),
                "Guides, FAQs & live support",
                null,
                () -> startActivity(new Intent(this, SupportActivity.class))
        );

        setupItem(
                R.id.item_about,
                R.drawable.ic_ios_info_circle,
                0xFF0D9488, // Deep Teal
                0xFFCCFBF1, // Soft Teal Tint
                getString(R.string.profile_about),
                "App version, privacy policy & terms",
                null,
                () -> startActivity(new Intent(this, AboutResQTapActivity.class))
        );
    }

    /** Setup dan konfigurasi Item dengan container warna latar unik. */
    private void setupItem(int includeId, int iconRes, int iconTint, int bgTint, String title, String subtitle, String value, Runnable onClick) {
        android.view.View root = findViewById(includeId);
        if (root == null) return;

        android.view.View iconContainer = root.findViewById(R.id.icon_container);
        ImageView icon = root.findViewById(R.id.item_icon);
        TextView titleView = root.findViewById(R.id.item_title);
        TextView subtitleView = root.findViewById(R.id.item_subtitle);
        TextView valueView = root.findViewById(R.id.item_value);

        if (iconContainer != null) {
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            int radiusPx = (int) (12 * getResources().getDisplayMetrics().density + 0.5f);
            gd.setCornerRadius(radiusPx);
            boolean isNight = com.example.resqtap.utils.ThemeUtils.isNightMode(this);
            int actualBg = isNight ? ((iconTint & 0x00FFFFFF) | 0x33000000) : bgTint;
            gd.setColor(actualBg);
            iconContainer.setBackground(gd);
        }

        if (icon != null) {
            icon.setImageResource(iconRes);
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(iconTint));
        }
        if (titleView != null) {
            titleView.setText(title);
        }
        if (subtitleView != null) {
            if (subtitle == null || subtitle.trim().isEmpty()) {
                subtitleView.setVisibility(android.view.View.GONE);
            } else {
                subtitleView.setVisibility(android.view.View.VISIBLE);
                subtitleView.setText(subtitle);
            }
        }
        if (valueView != null) {
            if (value == null || value.trim().isEmpty()) {
                valueView.setVisibility(android.view.View.GONE);
            } else {
                valueView.setVisibility(android.view.View.VISIBLE);
                valueView.setText(value);
            }
        }

        root.setOnClickListener(v -> {
            if (onClick != null) onClick.run();
        });
    }

    private String getLanguageLabel() {
        String tag = UserPrefs.getLanguage(this);
        if (tag == null) tag = "ms";
        tag = tag.trim().toLowerCase(Locale.ROOT);
        if (tag.startsWith("en")) return getString(R.string.lang_en);
        if (tag.startsWith("zh")) return getString(R.string.lang_zh);
        if (tag.startsWith("ta")) return getString(R.string.lang_ta);
        return getString(R.string.lang_ms);
    }

    /** Paparkan LanguageDropdown. */
    private void showLanguageDropdown() {
        String[] labels = new String[]{
                getString(R.string.lang_ms),
                getString(R.string.lang_en),
                getString(R.string.lang_zh),
                getString(R.string.lang_ta)
        };
        String[] tags = new String[]{"ms", "en", "zh", "ta"};

        String current = UserPrefs.getLanguage(this);
        int checked = 0;
        if (current != null) {
            String normalized = current.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("en")) checked = 1;
            else if (normalized.startsWith("zh")) checked = 2;
            else if (normalized.startsWith("ta")) checked = 3;
        }

        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.profile_language)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    int safe = Math.max(0, Math.min(which, tags.length - 1));
                    String next = tags[safe];
                    String prev = LocaleUtils.getSavedLanguageTag(ProfileActivity.this);
                    if (next.equalsIgnoreCase(prev)) {
                        d.dismiss();
                        return;
                    }
                    LocaleUtils.setLocaleAndPersist(ProfileActivity.this, next);
                    setupMenuItems();
                    d.dismiss();
                    try {
                        recreate();
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        try {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_primary));
        } catch (Exception ignored) {
        }
    }

}
