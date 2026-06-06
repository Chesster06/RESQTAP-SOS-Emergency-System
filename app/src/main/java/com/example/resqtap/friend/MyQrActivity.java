package com.example.resqtap.friend;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.resqtap.R;
import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.utils.AvatarUtils;
import com.example.resqtap.utils.BottomNavUtils;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * MyQrActivity
 * Skrin QR Code saya: tunjuk QR code profil untuk kawan scan dan add terus.
 */
public class MyQrActivity extends BaseActivity {

    private TextView qrUserName;
    private TextView qrUserPublicId;
    private ImageView ivMyQrCode;

    private String uid = "";
    private String name = "";
    private String publicId = "";
    private Bitmap currentQrBitmap = null;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_my_qr);

        View root = findViewById(R.id.main);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        BottomNavUtils.setup(bottomNav, this, R.id.nav_bottom_friends);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            try {
                int pl = bottomNav.getPaddingLeft();
                int pt = bottomNav.getPaddingTop();
                int pr = bottomNav.getPaddingRight();
                bottomNav.setPadding(pl, pt, pr, systemBars.bottom);
            } catch (Exception ignored) {}
            return insets;
        });

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            uid = user.getUid();
        }
        name = UserPrefs.getName(this);
        if (name == null || name.trim().isEmpty()) name = user != null && user.getDisplayName() != null ? user.getDisplayName() : "User";
        publicId = FirebaseFriendClient.format4DigitId(uid, UserPrefs.getPublicId(this));

        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> FriendNavigationHelper.navigate(this, FriendsActivity.class));

        qrUserName = findViewById(R.id.qr_user_name);
        qrUserPublicId = findViewById(R.id.qr_user_public_id);
        ivMyQrCode = findViewById(R.id.iv_my_qr_code);
        ImageView ivUserAvatar = findViewById(R.id.qr_user_avatar);

        final String userTag = name + "#" + publicId;
        qrUserName.setText(name);
        qrUserPublicId.setText(userTag);

        String photoB64 = UserPrefs.getPhotoB64(this);
        String photoUri = UserPrefs.getPhotoUri(this);
        String photoUrl = UserPrefs.getPhotoUrl(this);
        boolean hasCustomPhoto = (photoB64 != null && !photoB64.trim().isEmpty())
                || (photoUri != null && !photoUri.trim().isEmpty())
                || (photoUrl != null && !photoUrl.trim().isEmpty());
        if (ivUserAvatar != null) {
            AvatarUtils.applyAvatar(ivUserAvatar, photoB64, photoUrl, photoUri, R.drawable.ic_avatar);
        }

        MaterialButton btnCopy = findViewById(R.id.btn_copy_public_id);
        if (btnCopy != null) {
            btnCopy.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("User ID", userTag);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, getString(R.string.friend_id_copied, userTag), Toast.LENGTH_SHORT).show();
                }
            });
        }

        MaterialButton btnShare = findViewById(R.id.btn_share_qr);
        if (btnShare != null) {
            btnShare.setOnClickListener(v -> showSharePreviewDialog());
        }

        generateAndDisplayMyQr();
    }

    // SEKSYEN: ONDESTROY
    /** Pembersihan memori, buang listener, dan tutup sambungan. */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void generateAndDisplayMyQr() {
        executor.execute(() -> {
            String payload = QrCodeUtils.createQrPayload(uid, publicId, name);
            Bitmap bitmap = QrCodeUtils.generateQrCodeBitmap(payload, 600, 600);
            runOnUiThread(() -> {
                if (bitmap != null) {
                    currentQrBitmap = bitmap;
                    if (ivMyQrCode != null) {
                        ivMyQrCode.setImageBitmap(bitmap);
                    }
                }
            });
        });
    }

    /** Paparkan dialog pratonton (Preview) sebelum berkongsi poster gambar QR. */
}
