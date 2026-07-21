package com.example.resqtap.friend;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.resqtap.R;
import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.notification.NotificationUtils;
import com.example.resqtap.utils.BottomNavUtils;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.CompoundBarcodeView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * ScanQrActivity
 * Camera QR Scanner: scan QR kawan untuk auto-send friend request.
 */
public class ScanQrActivity extends BaseActivity {

    private CompoundBarcodeView qrScannerView;
    private MaterialButton btnToggleFlashlight;
    private boolean isFlashlightOn = false;

    private String uid = "";
    private String name = "";
    private String publicId = "";
    private long lastScanTime = 0L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String> requestCameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startQrScanner();
                } else {
                    Toast.makeText(this, R.string.friend_camera_perm_required, Toast.LENGTH_LONG).show();
                    FriendNavigationHelper.navigate(this, FriendsActivity.class);
                }
            });

    private final ActivityResultLauncher<String> pickGalleryImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    processGalleryImageUri(uri);
                }
            });

    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_scan_qr);

        View root = findViewById(R.id.main);
        View headerBar = findViewById(R.id.header_bar);
        View bottomCardContainer = findViewById(R.id.bottom_card_container);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (headerBar != null) {
                headerBar.setPadding(
                        headerBar.getPaddingLeft(),
                        systemBars.top + 16,
                        headerBar.getPaddingRight(),
                        headerBar.getPaddingBottom()
                );
            }
            if (bottomCardContainer != null) {
                bottomCardContainer.setPadding(
                        bottomCardContainer.getPaddingLeft(),
                        bottomCardContainer.getPaddingTop(),
                        bottomCardContainer.getPaddingRight(),
                        systemBars.bottom + 24
                );
            }
            return insets;
        });

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            uid = user.getUid();
        }
        name = UserPrefs.getName(this);
        if (name == null || name.trim().isEmpty()) name = user != null && user.getDisplayName() != null ? user.getDisplayName() : getString(R.string.friend_user_default);
        publicId = FirebaseFriendClient.format4DigitId(uid, UserPrefs.getPublicId(this));

        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> FriendNavigationHelper.navigate(this, FriendsActivity.class));

        MaterialButton btnPickGallery = findViewById(R.id.btn_pick_gallery);
        if (btnPickGallery != null) {
            btnPickGallery.setOnClickListener(v -> pickGalleryImageLauncher.launch("image/*"));
        }

        qrScannerView = findViewById(R.id.qr_scanner_view);
        btnToggleFlashlight = findViewById(R.id.btn_toggle_flashlight);
        MaterialButton btnEnterCode = findViewById(R.id.btn_enter_code);
        if (btnEnterCode != null) {
            btnEnterCode.setOnClickListener(v -> showAddFriendByCodeDialog());
        }

        setupFlashlight();
        checkCameraPermissionAndStartScanner();
        handleIncomingConnectIntent(getIntent());
    }

    /** Paparkan AddFriendByCodeDialog. */
    private void showAddFriendByCodeDialog() {
        if (qrScannerView != null) qrScannerView.pause();

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_friend_by_code, null);
        EditText inputCode = dialogView.findViewById(R.id.input_friend_code);
        TextView tvError = dialogView.findViewById(R.id.tv_dialog_error);
        View progress = dialogView.findViewById(R.id.progress_dialog_search);
        View cardFoundUser = dialogView.findViewById(R.id.card_found_user);
        TextView tvFoundName = dialogView.findViewById(R.id.tv_found_user_name);
        TextView tvFoundTag = dialogView.findViewById(R.id.tv_found_user_tag);
        MaterialButton btnInvite = dialogView.findViewById(R.id.btn_invite_user);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_dialog_cancel);
        MaterialButton btnSearch = dialogView.findViewById(R.id.btn_dialog_search);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialog.setOnDismissListener(d -> {
            if (qrScannerView != null) qrScannerView.resume();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        Runnable performSearch = () -> {
            String code = inputCode.getText() != null ? inputCode.getText().toString().trim() : "";
            if (code.isEmpty()) {
                tvError.setText(R.string.friend_enter_user_id);
                tvError.setVisibility(View.VISIBLE);
                cardFoundUser.setVisibility(View.GONE);
                return;
            }

            tvError.setVisibility(View.GONE);
            cardFoundUser.setVisibility(View.GONE);
            progress.setVisibility(View.VISIBLE);
            btnSearch.setEnabled(false);

            executor.execute(() -> {
                try {
                    FirebaseFriendClient.FriendInfo friend = FirebaseFriendClient.findUserByCode(code);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        progress.setVisibility(View.GONE);
                        btnSearch.setEnabled(true);

                        if (friend == null) {
                            tvError.setText(getString(R.string.friend_user_not_found, code));
                            tvError.setVisibility(View.VISIBLE);
                            cardFoundUser.setVisibility(View.GONE);
                            return;
                        }

                        if (uid.equalsIgnoreCase(friend.uid)) {
                            tvError.setText(R.string.friend_cannot_add_self);
                            tvError.setVisibility(View.VISIBLE);
                            cardFoundUser.setVisibility(View.GONE);
                            return;
                        }

                        tvError.setVisibility(View.GONE);
                        tvFoundName.setText(friend.name);
                        tvFoundTag.setText(friend.name + "#" + friend.publicId);
                        com.google.android.material.imageview.ShapeableImageView ivFoundAvatar = dialogView.findViewById(R.id.iv_found_user_avatar);
                        if (ivFoundAvatar != null) {
                            com.example.resqtap.utils.AvatarUtils.applyAvatar(ivFoundAvatar, friend.photoB64, friend.photoUrl, R.drawable.ic_avatar);
                        }
                        btnInvite.setEnabled(true);
                        btnInvite.setText(R.string.friend_invite_btn);
                        btnInvite.setIconResource(R.drawable.ic_person_add);

                        btnInvite.setOnClickListener(v -> {
                            btnInvite.setEnabled(false);
                            btnInvite.setText(R.string.friend_sending_request);
                            executor.execute(() -> {
                                try {
                                    FirebaseFriendClient.sendFriendRequest(this, uid, name, publicId, friend.uid);
                                    runOnUiThread(() -> {
                                        if (isFinishing() || isDestroyed()) return;
                                        btnInvite.setText(R.string.friend_request_sent_btn);
                                        btnInvite.setIcon(null);
                                        Toast.makeText(this, R.string.friend_request_sent, Toast.LENGTH_SHORT).show();
                                    });
                                } catch (Exception e) {
                                    String msg = e.getMessage() == null ? "" : e.getMessage().trim();
                                    runOnUiThread(() -> {
                                        if (isFinishing() || isDestroyed()) return;
                                        btnInvite.setEnabled(true);
                                        btnInvite.setText(R.string.friend_invite_btn);
                                        if ("already_friends".equalsIgnoreCase(msg)) {
                                            tvError.setText(getString(R.string.friend_already_added));
                                        } else if ("request_already_pending".equalsIgnoreCase(msg)) {
                                            tvError.setText(getString(R.string.friend_request_already_sent));
                                        } else {
                                            tvError.setText(getString(R.string.friend_failed_prefix, msg));
                                        }
                                        tvError.setVisibility(View.VISIBLE);
                                    });
                                }
                            });
                        });

                        cardFoundUser.setVisibility(View.VISIBLE);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        progress.setVisibility(View.GONE);
                        btnSearch.setEnabled(true);
                        tvError.setText(getString(R.string.friend_error_prefix, e.getMessage()));
                        tvError.setVisibility(View.VISIBLE);
                        cardFoundUser.setVisibility(View.GONE);
                    });
                }
            });
        };

        btnSearch.setOnClickListener(v -> performSearch.run());
        inputCode.setOnEditorActionListener((v, actionId, event) -> {
            performSearch.run();
            return true;
        });

        dialog.show();
    }

    /** Aktiviti aktif dan sedia untuk interaksi pengguna. */
    @Override
    protected void onResume() {
        super.onResume();
        if (qrScannerView != null) {
            qrScannerView.resume();
        }
    }

    /** Aktiviti dijeda sementara. */
    @Override
    protected void onPause() {
        super.onPause();
        if (qrScannerView != null) {
            qrScannerView.pause();
        }
    }

    // SEKSYEN: ONDESTROY
    /** Pembersihan memori, buang listener, dan tutup sambungan. */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private String safePublicId(String rawUid) {
        return FirebaseFriendClient.format4DigitId(rawUid, "");
    }

    /** Setup dan konfigurasi Flashlight. */
    private void setupFlashlight() {
        if (btnToggleFlashlight != null) {
            btnToggleFlashlight.setOnClickListener(v -> {
                if (qrScannerView == null) return;
                isFlashlightOn = !isFlashlightOn;
                if (isFlashlightOn) {
                    qrScannerView.setTorchOn();
                } else {
                    qrScannerView.setTorchOff();
                }
            });
        }
    }

    /** Semak dan sahkan CameraPermissionAndStartScanner. */
    private void checkCameraPermissionAndStartScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startQrScanner();
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void startQrScanner() {
        if (qrScannerView == null) return;
        qrScannerView.setStatusText("");
        qrScannerView.resume();
        qrScannerView.decodeContinuous(new BarcodeCallback() {
    @Override
            public void barcodeResult(BarcodeResult result) {
                if (result == null || result.getText() == null) return;
                long now = System.currentTimeMillis();
                if (now - lastScanTime < 2500) return;
                lastScanTime = now;

                String scannedText = result.getText();
                handleScannedQrCode(scannedText);
            }

            @Override
            public void possibleResultPoints(List<com.google.zxing.ResultPoint> resultPoints) {}
        });
    }

    /** Proses imej galeri dan scan kod QR daripada bitmap. */
    private void processGalleryImageUri(android.net.Uri uri) {
        if (uri == null) return;
        if (qrScannerView != null) qrScannerView.pause();

        executor.execute(() -> {
            try {
                android.graphics.Bitmap bitmap = null;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    android.graphics.ImageDecoder.Source source = android.graphics.ImageDecoder.createSource(getContentResolver(), uri);
                    bitmap = android.graphics.ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                        decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
                        decoder.setMutableRequired(true);
                    });
                } else {
                    bitmap = android.provider.MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                }

                if (bitmap == null) {
                    try (java.io.InputStream is = getContentResolver().openInputStream(uri)) {
                        bitmap = android.graphics.BitmapFactory.decodeStream(is);
                    } catch (Exception ignored) {}
                }

                if (bitmap != null) {
                    String qrContent = QrCodeUtils.decodeQrFromBitmap(bitmap);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (qrContent != null && !qrContent.trim().isEmpty()) {
                            handleScannedQrCode(qrContent.trim());
                        } else {
                            Toast.makeText(this, R.string.friend_qr_no_qr_found, Toast.LENGTH_LONG).show();
                            if (qrScannerView != null) qrScannerView.resume();
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            Toast.makeText(this, R.string.friend_qr_no_qr_found, Toast.LENGTH_SHORT).show();
                            if (qrScannerView != null) qrScannerView.resume();
                        }
                    });
                }
            } catch (Exception e) {
                android.util.Log.e("ScanQrActivity", "Error processing gallery image", e);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(this, R.string.friend_qr_no_qr_found, Toast.LENGTH_SHORT).show();
                        if (qrScannerView != null) qrScannerView.resume();
                    }
                });
            }
        });
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingConnectIntent(intent);
    }

    private void handleIncomingConnectIntent(android.content.Intent intent) {
        if (intent == null) return;
        String payload = intent.getStringExtra("extra_connect_payload");
        if ((payload == null || payload.trim().isEmpty()) && intent.getData() != null) {
            payload = intent.getData().toString();
        }
        if (payload != null && !payload.trim().isEmpty()) {
            handleScannedQrCode(payload.trim());
        }
    }

    private void handleScannedQrCode(String scannedData) {
        if (qrScannerView != null) qrScannerView.pause();
        QrCodeUtils.QrPayload payload = QrCodeUtils.parseQrPayload(scannedData);
        if (!payload.isValid()) {
            Toast.makeText(this, R.string.friend_qr_invalid, Toast.LENGTH_SHORT).show();
            if (qrScannerView != null) qrScannerView.resume();
            return;
        }

        if (uid.equalsIgnoreCase(payload.uid)) {
            Toast.makeText(this, R.string.cannot_add_self, Toast.LENGTH_SHORT).show();
            if (qrScannerView != null) qrScannerView.resume();
            return;
        }

        String friendName = payload.name.isEmpty() ? getString(R.string.friend_user_default) : payload.name;

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.friend_add_title)
                .setMessage(getString(R.string.friend_send_request_prompt, friendName))
                .setNegativeButton(R.string.cancel, (d, w) -> {
                    d.dismiss();
                    if (qrScannerView != null) qrScannerView.resume();
                })
                .setPositiveButton(R.string.friend_send_request_btn, (d, w) -> {
                    sendFriendRequest(payload.uid, payload.name, payload.publicId);
                })
                .setOnDismissListener(d -> {
                    if (qrScannerView != null) qrScannerView.resume();
                })
                .show();
    }

    /** Simpan atau hantar data FriendRequest. */
    private void sendFriendRequest(String targetUid, String targetName, String targetPublicId) {
        executor.execute(() -> {
            try {
                FirebaseFriendClient.sendFriendRequest(this, uid, name, publicId, targetUid);
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.friend_request_sent, Toast.LENGTH_LONG).show();
                    FriendNavigationHelper.navigate(this, FriendsActivity.class);
                });
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "" : e.getMessage().trim();
                runOnUiThread(() -> {
                    if ("already_friends".equalsIgnoreCase(msg)) {
                        Toast.makeText(this, R.string.friend_already_added, Toast.LENGTH_SHORT).show();
                    } else if ("request_already_pending".equalsIgnoreCase(msg)) {
                        Toast.makeText(this, R.string.friend_request_already_sent, Toast.LENGTH_SHORT).show();
                    } else if ("cannot_add_self".equalsIgnoreCase(msg)) {
                        Toast.makeText(this, R.string.cannot_add_self, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, getString(R.string.friend_send_failed, msg), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
}
