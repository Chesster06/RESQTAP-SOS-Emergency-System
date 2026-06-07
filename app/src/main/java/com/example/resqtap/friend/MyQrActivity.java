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
    private void showSharePreviewDialog() {
        if (currentQrBitmap == null) {
            String payload = QrCodeUtils.createQrPayload(uid, publicId, name);
            currentQrBitmap = QrCodeUtils.generateQrCodeBitmap(payload, 600, 600);
        }
        if (currentQrBitmap == null) {
            Toast.makeText(this, "QR Code is still generating...", Toast.LENGTH_SHORT).show();
            return;
        }

        Bitmap posterBitmap = generatePosterBitmap(currentQrBitmap);
        if (posterBitmap == null) {
            Toast.makeText(this, "Failed to generate poster preview", Toast.LENGTH_SHORT).show();
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_ResQTap_BottomSheetDialog);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_qr_share_preview, null);
        dialog.setContentView(dialogView);

        ImageView ivPreview = dialogView.findViewById(R.id.iv_poster_preview);
        View btnConfirm = dialogView.findViewById(R.id.btn_confirm_share);
        View btnCancel = dialogView.findViewById(R.id.btn_cancel_share);

        if (ivPreview != null) {
            ivPreview.setImageBitmap(posterBitmap);
        }

        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                dialog.dismiss();
                sharePosterImage(posterBitmap);
            });
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    /** Hasilkan Bitmap berkualiti tinggi dari layout_qr_share_poster. */
    private Bitmap generatePosterBitmap(Bitmap qrBitmap) {
        try {
            View posterView = getLayoutInflater().inflate(R.layout.layout_qr_share_poster, null);

            TextView tvName = posterView.findViewById(R.id.poster_user_name);
            TextView tvTag = posterView.findViewById(R.id.poster_user_tag);
            ImageView ivQr = posterView.findViewById(R.id.poster_qr_image);
            ImageView ivPosterAvatar = posterView.findViewById(R.id.poster_user_avatar);

            if (tvName != null) tvName.setText(name);
            if (tvTag != null) tvTag.setText(name + "#" + publicId);
            if (ivQr != null) ivQr.setImageBitmap(qrBitmap);

            String photoB64 = UserPrefs.getPhotoB64(this);
            String photoUri = UserPrefs.getPhotoUri(this);
            String photoUrl = UserPrefs.getPhotoUrl(this);
            boolean hasCustomPhoto = (photoB64 != null && !photoB64.trim().isEmpty())
                    || (photoUri != null && !photoUri.trim().isEmpty())
                    || (photoUrl != null && !photoUrl.trim().isEmpty());
            if (ivPosterAvatar != null) {
                AvatarUtils.applyAvatar(ivPosterAvatar, photoB64, photoUrl, photoUri, R.drawable.ic_avatar);
            }

            int widthPx = 1080;
            int widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);

            posterView.measure(widthSpec, heightSpec);
            int heightPx = posterView.getMeasuredHeight();
            posterView.layout(0, 0, widthPx, heightPx);

            Bitmap bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            posterView.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            android.util.Log.e("MyQrActivity", "Error generating poster bitmap", e);
            return null;
        }
    }

    /** Simpan poster ke cache dan kongsikan melalui Intent ACTION_SEND. */
    private void sharePosterImage(Bitmap posterBitmap) {
        try {
            File cacheDir = new File(getCacheDir(), "shared_posters");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            File imageFile = new File(cacheDir, "resqtap_qr_" + System.currentTimeMillis() + ".png");
            FileOutputStream fos = new FileOutputStream(imageFile);
            posterBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", imageFile);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_TEXT, buildShareMessage());
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share ResQTap Emergency QR"));
        } catch (Exception e) {
            android.util.Log.e("MyQrActivity", "Error sharing poster image", e);
            Toast.makeText(this, "Failed to share image", Toast.LENGTH_SHORT).show();
        }
    }

    /** Bina teks pesanan perkongsian rasmi ResQTap Emergency bersama pautan aplikasi & laman web. */
    private String buildShareMessage() {
        String encodedName = "";
        try {
            encodedName = java.net.URLEncoder.encode(name, "UTF-8");
        } catch (Exception ignored) {
            encodedName = name;
        }

        String appLink = "https://resqtap.web.app/connect?uid=" + uid + "&id=" + publicId + "&name=" + encodedName;
        String downloadLink = "https://resqtap.web.app/download";

        return "🚨 ResQTap Emergency Application 🚨\n\n"
                + "Hi! I’m sharing my personal ResQTap Emergency QR Code with you.\n\n"
                + "This QR Code is connected to my ResQTap emergency profile and can help you access important information or connect with me when needed. Please keep this QR Code saved in a safe place for future reference.\n\n"
                + "In the event of an emergency, having access to this QR Code may help provide faster assistance and make it easier for trusted contacts to respond quickly.\n\n"
                + "📱 Simply scan the QR Code using the ResQTap Emergency Application to access the available information.\n\n"
                + "🔗 Connect with me directly on ResQTap:\n"
                + appLink + "\n\n"
                + "📲 Don't have the app yet? Download ResQTap here:\n"
                + downloadLink + "\n\n"
                + "Thank you for being one of my trusted contacts. Please keep this information private and only use it when necessary.\n\n"
                + "❤️ ResQTap: Stay Connected, Stay Safe.";
    }
}
