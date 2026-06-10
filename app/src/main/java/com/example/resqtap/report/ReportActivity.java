package com.example.resqtap.report;
import com.example.resqtap.R;

import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.home.MainActivity;
import com.example.resqtap.room.FirebaseRoomClient;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.example.resqtap.map.MapTypeBottomSheet;
import com.example.resqtap.room.FirebaseRoomClient;
import com.example.resqtap.sos.SosLivechatActivity;
import com.example.resqtap.call.CallSignalingClient;
import com.example.resqtap.call.VoiceCallActivity;
import com.example.resqtap.call.VideoCallActivity;
import com.example.resqtap.utils.UserPrefs;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import android.view.LayoutInflater;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.android.gms.tasks.CancellationTokenSource;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import com.google.android.material.card.MaterialCardView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * ReportActivity
 * Borang Lapor Insiden: pilih kategori kecemasan, attach lokasi GPS, dan upload gambar/video bukti.
 */
public class ReportActivity extends BaseActivity implements OnMapReadyCallback {
    private static final int MAX_ATTACHMENTS = 5;

    private GoogleMap map;
    private Marker marker;
    private FusedLocationProviderClient fusedLocationClient;
    private CancellationTokenSource locationTokenSource;
    private final ExecutorService geocoderExecutor = Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<String[]> locationPerms;
    private ActivityResultLauncher<String[]> pickMedia;
    private ActivityResultLauncher<Void> takeCameraPhoto;
    private ActivityResultLauncher<String> cameraPermLauncher;

    private TextView addressView;
    private TextView charCountView;
    private TextView attachmentSummaryView;
    private LinearLayout attachmentPreviews;
    private View attachmentPreviewScroll;
    private View successOverlay;
    private View successSheet;
    private View submitContainer;
    private EditText detailsInput;
    private ChipGroup categoryGroup;
    private MaterialButton submitButton;

    private LatLng incidentLatLng;
    private String incidentAddress = "";
    private boolean submitting = false;
    private boolean reportSubmitted = false;
    private final ArrayList<AttachmentInfo> attachments = new ArrayList<>();

    // Progress page components
    private View reportScroll;
    private View reportProgressScroll;
    private TextView reportTitleView;
    private View cardActiveReportBanner;
    private TextView tvActiveReportBannerTitle;
    private TextView tvActiveReportBannerSub;

    private TextView tvProgressStatusBadge;
    private TextView tvProgressReportId;
    private TextView tvProgressTime;
    private TextView tvProgressMessage;
    private ImageView btnCopyReportId;

    private FrameLayout step1Circle;
    private FrameLayout step2Circle;
    private FrameLayout step3Circle;
    private FrameLayout step4Circle;
    private ImageView step1Icon;
    private ImageView step2Icon;
    private ImageView step3Icon;
    private ImageView step4Icon;
    private View stepLine12;
    private View stepLine23;
    private View stepLine34;
    private TextView tvStep1Title;
    private TextView tvStep1Desc;
    private TextView tvStep2Title;
    private TextView tvStep2Desc;
    private TextView tvStep3Title;
    private TextView tvStep3Desc;
    private TextView tvStep4Title;
    private TextView tvStep4Desc;

    private TextView tvProgressCategory;
    private TextView tvProgressAddress;
    private TextView tvProgressDetails;
    private ImageView imgProgressCategoryIcon;
    private HorizontalScrollView progressAttachmentsScroll;
    private LinearLayout progressAttachmentsContainer;
    private View btnNewReportTop;
    private View btnRefreshProgress;
    private ImageView ivRefreshIcon;

    private ValueEventListener activeReportListener;
    private DatabaseReference activeReportRef;
    private ValueEventListener userReportIdListener;
    private DatabaseReference userReportIdRef;
    private String currentReportId;
    private String currentReportStatus;
    private boolean isShowingProgress = false;

    // Responder Direct Communication Views & Signaling
    private View btnTopChatAdmin;
    private View btnTopCallAdmin;
    private BottomSheetDialog callWaitDialog;
    private DatabaseReference userCallRef;
    private ValueEventListener userCallListener;
    private DatabaseReference adminCallIncomingRef;
    private ValueEventListener adminCallIncomingListener;

    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_report);

        View main = findViewById(R.id.main);
        submitContainer = findViewById(R.id.submit_container);
        ViewCompat.setOnApplyWindowInsetsListener(main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            if (submitContainer != null) {
                submitContainer.setPadding(
                        submitContainer.getPaddingLeft(),
                        submitContainer.getPaddingTop(),
                        submitContainer.getPaddingRight(),
                        systemBars.bottom + dp(10)
                );
            }
            return insets;
        });

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        bindLaunchers();
        bindViews();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.report_map_fragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        checkExistingReport();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentReportId != null) {
            verifyReportExistsInDatabase(currentReportId);
        }
    }

    // SEKSYEN: ONDESTROY
    /** Pembersihan memori, buang listener, dan tutup sambungan. */
    @Override
    protected void onDestroy() {
        if (locationTokenSource != null) locationTokenSource.cancel();
        geocoderExecutor.shutdownNow();
        detachActiveReportListener();
        stopCallListening();
        if (callWaitDialog != null && callWaitDialog.isShowing()) {
            callWaitDialog.dismiss();
            callWaitDialog = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        handleBackPress();
    }

    private void handleBackPress() {
        boolean isResolved = "resolved".equalsIgnoreCase(currentReportStatus);
        if (isShowingProgress && isResolved && reportScroll != null && !reportSubmitted) {
            showFormMode();
        } else {
            finish();
        }
    }

    /** Setup dan konfigurasi Launchers. */
    private void bindLaunchers() {
        locationPerms = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fine = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                    Boolean coarse = result.get(Manifest.permission.ACCESS_COARSE_LOCATION);
                    if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                        updateLocation();
                    } else {
                        addressView.setText(R.string.report_location_permission_required);
                    }
                }
        );

        pickMedia = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                uris -> {
                    if (uris == null || uris.isEmpty()) return;
                    for (Uri uri : uris) {
                        if (attachments.size() >= MAX_ATTACHMENTS) {
                            Toast.makeText(this, R.string.report_attachment_full, Toast.LENGTH_SHORT).show();
                            break;
                        }
                        try {
                            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (Exception ignored) {
                        }
                        attachments.add(AttachmentInfo.fromUri(
                                uri,
                                queryDisplayName(uri),
                                safe(getContentResolver().getType(uri)),
                                querySize(uri)
                        ));
                    }
                    updateAttachmentSummary();
                }
        );

        takeCameraPhoto = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                bitmap -> {
                    if (bitmap == null) return;
                    if (attachments.size() >= MAX_ATTACHMENTS) {
                        Toast.makeText(this, R.string.report_attachment_full, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out);
                    String name = "report_camera_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
                    attachments.add(AttachmentInfo.fromBytes(out.toByteArray(), name, "image/jpeg"));
                    updateAttachmentSummary();
                }
        );

        cameraPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (Boolean.TRUE.equals(isGranted)) {
                        launchCamera();
                    } else {
                        Toast.makeText(this, R.string.report_camera_permission_required, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    /** Buka aplikasi kamera dengan semakan kebenaran RUNTIME. */
    private void launchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            try {
                takeCameraPhoto.launch(null);
            } catch (Exception e) {
                Toast.makeText(this, R.string.report_camera_unavailable, Toast.LENGTH_SHORT).show();
            }
        } else {
            cameraPermLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    // SEKSYEN: BINDVIEWS
    /** Setup dan konfigurasi Views. */
    private void bindViews() {
        View back = findViewById(R.id.btn_back);
        View updateLocationButton = findViewById(R.id.btn_update_location);
        View pickMediaButton = findViewById(R.id.btn_pick_media);
        View takePhotoButton = findViewById(R.id.btn_take_photo);

        addressView = findViewById(R.id.report_address);
        charCountView = findViewById(R.id.report_char_count);
        attachmentSummaryView = findViewById(R.id.report_attachment_summary);
        attachmentPreviews = findViewById(R.id.report_attachment_previews);
        attachmentPreviewScroll = findViewById(R.id.report_preview_scroll);
        successOverlay = findViewById(R.id.report_success_overlay);
        successSheet = findViewById(R.id.report_success_sheet);
        detailsInput = findViewById(R.id.report_details);
        categoryGroup = findViewById(R.id.report_category_group);
        submitButton = findViewById(R.id.btn_submit_report);
        MaterialButton successOk = findViewById(R.id.btn_success_ok);

        reportScroll = findViewById(R.id.report_scroll);
        reportProgressScroll = findViewById(R.id.report_progress_scroll);
        reportTitleView = findViewById(R.id.report_title);
        cardActiveReportBanner = findViewById(R.id.card_active_report_banner);
        tvActiveReportBannerTitle = findViewById(R.id.tv_active_report_banner_title);
        tvActiveReportBannerSub = findViewById(R.id.tv_active_report_banner_sub);

        tvProgressStatusBadge = findViewById(R.id.tv_progress_status_badge);
        tvProgressReportId = findViewById(R.id.tv_progress_report_id);
        tvProgressTime = findViewById(R.id.tv_progress_time);
        tvProgressMessage = findViewById(R.id.tv_progress_message);
        btnCopyReportId = findViewById(R.id.btn_copy_report_id);

        step1Circle = findViewById(R.id.step_1_circle);
        step2Circle = findViewById(R.id.step_2_circle);
        step3Circle = findViewById(R.id.step_3_circle);
        step4Circle = findViewById(R.id.step_4_circle);
        step1Icon = findViewById(R.id.step_1_icon);
        step2Icon = findViewById(R.id.step_2_icon);
        step3Icon = findViewById(R.id.step_3_icon);
        step4Icon = findViewById(R.id.step_4_icon);
        stepLine12 = findViewById(R.id.step_line_1_2);
        stepLine23 = findViewById(R.id.step_line_2_3);
        stepLine34 = findViewById(R.id.step_line_3_4);
        tvStep1Title = findViewById(R.id.tv_step_1_title);
        tvStep1Desc = findViewById(R.id.tv_step_1_desc);
        tvStep2Title = findViewById(R.id.tv_step_2_title);
        tvStep2Desc = findViewById(R.id.tv_step_2_desc);
        tvStep3Title = findViewById(R.id.tv_step_3_title);
        tvStep3Desc = findViewById(R.id.tv_step_3_desc);
        tvStep4Title = findViewById(R.id.tv_step_4_title);
        tvStep4Desc = findViewById(R.id.tv_step_4_desc);

        tvProgressCategory = findViewById(R.id.tv_progress_category);
        tvProgressAddress = findViewById(R.id.tv_progress_address);
        tvProgressDetails = findViewById(R.id.tv_progress_details);
        imgProgressCategoryIcon = findViewById(R.id.img_progress_category_icon);
        progressAttachmentsScroll = findViewById(R.id.progress_attachments_scroll);
        progressAttachmentsContainer = findViewById(R.id.progress_attachments_container);
        btnNewReportTop = findViewById(R.id.btn_new_report_top);
        btnRefreshProgress = findViewById(R.id.btn_refresh_progress);
        ivRefreshIcon = findViewById(R.id.iv_refresh_icon);

        btnTopChatAdmin = findViewById(R.id.btn_top_chat_admin);
        btnTopCallAdmin = findViewById(R.id.btn_top_call_admin);

        if (btnTopChatAdmin != null) {
            btnTopChatAdmin.setOnClickListener(v -> openAdminChatForReport());
        }
        if (btnTopCallAdmin != null) {
            btnTopCallAdmin.setOnClickListener(v -> showCallOptionsBottomSheet());
        }

        if (cardActiveReportBanner != null) {
            cardActiveReportBanner.setOnClickListener(v -> {
                if (currentReportId != null) {
                    showProgressMode();
                }
            });
        }
        if (btnNewReportTop != null) {
            btnNewReportTop.setOnClickListener(v -> startNewReport());
        }
        if (btnRefreshProgress != null) {
            btnRefreshProgress.setOnClickListener(v -> refreshProgress());
        }
        if (btnCopyReportId != null) {
            btnCopyReportId.setOnClickListener(v -> copyReportIdToClipboard());
        }

        if (back != null) {
            back.setOnClickListener(v -> handleBackPress());
        }
        View btnMapType = findViewById(R.id.btn_map_type);
        View btnZoomIn = findViewById(R.id.btn_map_zoom_in);
        View btnZoomOut = findViewById(R.id.btn_map_zoom_out);
        if (btnMapType != null) {
            btnMapType.setOnClickListener(v -> toggleMapType());
        }
        if (btnZoomIn != null) {
            btnZoomIn.setOnClickListener(v -> {
                if (map != null) {
                    map.animateCamera(CameraUpdateFactory.zoomIn());
                }
            });
        }
        if (btnZoomOut != null) {
            btnZoomOut.setOnClickListener(v -> {
                if (map != null) {
                    map.animateCamera(CameraUpdateFactory.zoomOut());
                }
            });
        }

        if (updateLocationButton != null) updateLocationButton.setOnClickListener(v -> requestLocation());
        if (pickMediaButton != null) pickMediaButton.setOnClickListener(v -> pickMedia.launch(new String[]{"image/*", "video/*", "audio/*", "application/pdf"}));
        if (takePhotoButton != null) takePhotoButton.setOnClickListener(v -> launchCamera());
        if (categoryGroup != null) categoryGroup.check(R.id.category_crime);
        if (submitButton != null) submitButton.setOnClickListener(v -> submitReport());
        if (successOk != null) {
            successOk.setText(R.string.report_success_ok);
            successOk.setOnClickListener(v -> {
                if (successOverlay != null) successOverlay.setVisibility(View.GONE);
                if (currentReportId != null) {
                    showProgressMode();
                } else {
                    goToMainPage();
                }
            });
        }

        detailsInput.addTextChangedListener(new TextWatcher() {
    @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

    @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                charCountView.setText(getString(R.string.report_char_count, s == null ? 0 : s.length()));
                if (s != null && s.toString().trim().length() > 0) {
                    detailsInput.setError(null);
                }
            }

    @Override
            public void afterTextChanged(Editable s) {
            }
        });
        updateAttachmentSummary();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        try {
            map.getUiSettings().setMapToolbarEnabled(false);
            map.getUiSettings().setCompassEnabled(false);
            map.getUiSettings().setZoomControlsEnabled(false);
            map.getUiSettings().setScrollGesturesEnabled(true);
            map.getUiSettings().setZoomGesturesEnabled(true);
            map.getUiSettings().setRotateGesturesEnabled(true);
            map.getUiSettings().setTiltGesturesEnabled(true);

            boolean isSat = UserPrefs.isMapTypeSatellite(this);
            if (isSat) {
                map.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
            } else {
                map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                map.setMapStyle(null);
            }
        } catch (Exception ignored) {
        }

        map.setOnMapClickListener(this::applySelectedLatLng);
        map.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(Marker marker) {}

            @Override
            public void onMarkerDrag(Marker marker) {}

            @Override
            public void onMarkerDragEnd(Marker marker) {
                if (marker != null) {
                    applySelectedLatLng(marker.getPosition());
                }
            }
        });

        requestLocation();
    }

    /** Memaparkan dialog pemilihan jenis paparan peta (Street/Default atau Realistic/Satellite). */
    private void toggleMapType() {
        if (map != null) {
            MapTypeBottomSheet.show(this, map);
        }
    }

    /** Memilih atau memindahkan lokasi insiden mengikut posisi pin baharu. */
    private void applySelectedLatLng(LatLng latLng) {
        if (latLng == null) return;
        incidentLatLng = latLng;
        incidentAddress = formatLatLng(incidentLatLng);
        addressView.setText(incidentAddress);

        if (map != null) {
            if (marker == null) {
                marker = map.addMarker(new MarkerOptions()
                        .position(incidentLatLng)
                        .draggable(true)
                        .title(getString(R.string.report_location_title)));
            } else {
                marker.setPosition(incidentLatLng);
                marker.setDraggable(true);
            }
        }

        geocoderExecutor.execute(() -> {
            String resolved = resolveAddress(incidentLatLng);
            if (resolved.isEmpty()) return;
            runOnUiThread(() -> {
                incidentAddress = resolved;
                addressView.setText(resolved);
            });
        });
    }

    private void requestLocation() {
        if (hasLocationPermission()) {
            updateLocation();
            return;
        }
        locationPerms.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    /** Semak dan sahkan LocationPermission. */
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    /** Simpan atau hantar data Location. */
    private void updateLocation() {
        if (!hasLocationPermission()) return;
        addressView.setText(R.string.report_location_loading);
        if (locationTokenSource != null) locationTokenSource.cancel();
        locationTokenSource = new CancellationTokenSource();

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, locationTokenSource.getToken())
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        applyLocation(location);
                    } else {
                        fusedLocationClient.getLastLocation()
                                .addOnSuccessListener(last -> {
                                    if (last != null) applyLocation(last);
                                    else addressView.setText(R.string.report_location_unavailable);
                                })
                                .addOnFailureListener(error -> addressView.setText(R.string.report_location_unavailable));
                    }
                })
                .addOnFailureListener(error -> addressView.setText(R.string.report_location_unavailable));
    }

    private void applyLocation(Location location) {
        incidentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        incidentAddress = formatLatLng(incidentLatLng);
        addressView.setText(incidentAddress);

        if (map != null) {
            if (marker == null) {
                marker = map.addMarker(new MarkerOptions()
                        .position(incidentLatLng)
                        .draggable(true)
                        .title(getString(R.string.report_location_title)));
            } else {
                marker.setPosition(incidentLatLng);
                marker.setDraggable(true);
            }
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(incidentLatLng, 16f));
        }

        geocoderExecutor.execute(() -> {
            String resolved = resolveAddress(incidentLatLng);
            if (resolved.isEmpty()) return;
            runOnUiThread(() -> {
                incidentAddress = resolved;
                addressView.setText(resolved);
            });
        });
    }

    private String resolveAddress(LatLng latLng) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
            if (addresses == null || addresses.isEmpty()) return "";
            Address address = addresses.get(0);
            ArrayList<String> parts = new ArrayList<>();
            for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                String line = safe(address.getAddressLine(i));
                if (!line.isEmpty()) parts.add(line);
            }
            return parts.isEmpty() ? "" : android.text.TextUtils.join(", ", parts);
        } catch (Exception ignored) {
            return "";
        }
    }

    private void submitReport() {
        if (submitting || reportSubmitted) return;
        if (currentReportStatus != null && !"resolved".equalsIgnoreCase(currentReportStatus)) {
            Toast.makeText(this, R.string.report_active_pending_resolution, Toast.LENGTH_SHORT).show();
            showProgressMode();
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, R.string.support_livechat_login_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (incidentLatLng == null) {
            Toast.makeText(this, R.string.report_location_required, Toast.LENGTH_SHORT).show();
            requestLocation();
            return;
        }

        int checkedId = categoryGroup == null ? View.NO_ID : categoryGroup.getCheckedChipId();
        String category = categoryValue(checkedId);
        if (category.isEmpty()) {
            Toast.makeText(this, R.string.report_category_required, Toast.LENGTH_SHORT).show();
            return;
        }

        String details = detailsInput == null ? "" : safe(detailsInput.getText());
        if (details.isEmpty()) {
            if (detailsInput != null) {
                detailsInput.setError(getString(R.string.report_details_required));
                detailsInput.requestFocus();
            }
            Toast.makeText(this, R.string.report_details_required, Toast.LENGTH_SHORT).show();
            return;
        }

        if (attachments.isEmpty()) {
            Toast.makeText(this, R.string.report_attachment_required, Toast.LENGTH_SHORT).show();
            return;
        }

        setSubmitting(true, getString(R.string.report_attachment_uploading));
        String tempUploadId = java.util.UUID.randomUUID().toString();

        uploadAttachments(user.getUid(), tempUploadId, 0, new ArrayList<>(), uploaded -> {
            String uId = user.getUid();
            String uName = safe(UserPrefs.getName(ReportActivity.this));
            if (uName.isEmpty()) uName = safe(user.getDisplayName());
            if (uName.isEmpty()) uName = "User";

            String rTitle = safe(categoryLabel(category));
            if (rTitle.isEmpty()) rTitle = category;

            String rDesc = detailsInput != null ? safe(detailsInput.getText().toString().trim()) : "";
            String rAddr = incidentAddress != null ? incidentAddress : "";
            double rLat = incidentLatLng != null ? incidentLatLng.latitude : 0.0;
            double rLng = incidentLatLng != null ? incidentLatLng.longitude : 0.0;

            String firstImageUrl = null;
            if (!uploaded.isEmpty() && uploaded.get(0).containsKey("downloadUrl")) {
                firstImageUrl = String.valueOf(uploaded.get(0).get("downloadUrl"));
            }

            com.example.resqtap.supabase.SupabaseManager.getInstance().submitReport(
                    uId, uName, rTitle, rDesc, category, rLat, rLng, rAddr, firstImageUrl,
                    new com.example.resqtap.supabase.SupabaseManager.Callback<String>() {
                        @Override
                        public void onSuccess(String newReportId) {
                            runOnUiThread(() -> {
                                markReportSubmitted();
                                currentReportStatus = "new";
                                UserPrefs.setLastIncidentReportStatus(ReportActivity.this, "new");
                                UserPrefs.setLastIncidentReportId(ReportActivity.this, newReportId);

                                Map<String, Object> report = buildReportPayload(newReportId, user, category, uploaded);
                                try {
                                    FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                                            .getReference("incidentReports")
                                            .child(newReportId)
                                            .setValue(report);
                                    FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                                            .getReference("users")
                                            .child(user.getUid())
                                            .child("lastIncidentReportId")
                                            .setValue(newReportId);
                                    FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                                            .getReference("users")
                                            .child(user.getUid())
                                            .child("lastIncidentReportStatus")
                                            .setValue("new");
                                } catch (Exception ignored) {}

                                if (successOverlay != null) successOverlay.setVisibility(View.GONE);
                                showProgressMode(newReportId, report);
                                Toast.makeText(ReportActivity.this, R.string.report_submitted, Toast.LENGTH_SHORT).show();
                            });
                        }

                        @Override
                        public void onError(Exception error) {
                            runOnUiThread(() -> {
                                setSubmitting(false, getString(R.string.report_continue));
                                Toast.makeText(ReportActivity.this, R.string.report_submit_failed, Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
            );
        }, error -> {
            setSubmitting(false, getString(R.string.report_continue));
            Toast.makeText(this, R.string.report_submit_failed, Toast.LENGTH_SHORT).show();
        });
    }

    private Map<String, Object> buildReportPayload(String reportId, FirebaseUser user, String category, ArrayList<Map<String, Object>> uploaded) {
        String name = safe(UserPrefs.getName(this));
        if (name.isEmpty()) name = safe(user.getDisplayName());
        if (name.isEmpty()) name = "User";

        Map<String, Object> report = new HashMap<>();
        report.put("reportId", reportId);
        report.put("senderUid", user.getUid());
        report.put("senderName", name);
        report.put("senderEmail", safe(user.getEmail()));
        report.put("publicId", safe(UserPrefs.getPublicId(this)));
        report.put("category", category);
        report.put("categoryLabel", categoryLabel(category));
        report.put("details", safe(detailsInput.getText()));
        report.put("address", safe(incidentAddress));
        report.put("latitude", incidentLatLng.latitude);
        report.put("longitude", incidentLatLng.longitude);
        report.put("status", "new");
        report.put("source", "android_app");
        report.put("createdAt", ServerValue.TIMESTAMP);
        report.put("updatedAt", ServerValue.TIMESTAMP);
        if (!uploaded.isEmpty()) report.put("attachments", uploaded);
        return report;
    }

    private void uploadAttachments(String uid, String reportId, int index, ArrayList<Map<String, Object>> uploaded,
                                   AttachmentUploadDone done, AttachmentUploadFailed failed) {
        if (index >= attachments.size()) {
            done.onDone(uploaded);
            return;
        }

        AttachmentInfo attachment = attachments.get(index);
        if (isImageAttachment(attachment)) {
            Map<String, Object> inline = inlineImageAttachmentMap(attachment);
            if (!inline.isEmpty()) {
                uploaded.add(inline);
                uploadAttachments(uid, reportId, index + 1, uploaded, done, failed);
                return;
            }
        }

        String safeName = sanitizeFileName(attachment.name.isEmpty() ? "attachment" : attachment.name);
        StorageReference target = FirebaseStorage.getInstance()
                .getReference()
                .child("incidentReports")
                .child(uid)
                .child(reportId)
                .child(index + "_" + safeName);

        if (attachment.bytes != null) {
            target.putBytes(attachment.bytes)
                    .addOnSuccessListener(snapshot -> handleUploadedAttachment(target, attachment, uploaded, () ->
                            uploadAttachments(uid, reportId, index + 1, uploaded, done, failed), failed))
                    .addOnFailureListener(failed::onFailed);
            return;
        }

        target.putFile(attachment.uri)
                .addOnSuccessListener(snapshot -> handleUploadedAttachment(target, attachment, uploaded, () ->
                        uploadAttachments(uid, reportId, index + 1, uploaded, done, failed), failed))
                .addOnFailureListener(failed::onFailed);
    }

    private void handleUploadedAttachment(StorageReference target, AttachmentInfo attachment,
                                          ArrayList<Map<String, Object>> uploaded, Runnable next,
                                          AttachmentUploadFailed failed) {
        target.getDownloadUrl()
                .addOnSuccessListener(downloadUri -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("name", attachment.name);
                    data.put("mimeType", attachment.mimeType);
                    data.put("size", attachment.size);
                    data.put("downloadUrl", downloadUri.toString());
                    data.put("storagePath", target.getPath());
                    uploaded.add(data);
                    next.run();
                })
                .addOnFailureListener(failed::onFailed);
    }

    private String categoryValue(int checkedId) {
        if (checkedId == R.id.category_fire) return "fire";
        if (checkedId == R.id.category_crime) return "crime";
        if (checkedId == R.id.category_medical) return "medical_aid";
        if (checkedId == R.id.category_humanitarian) return "humanitarian_aid";
        if (checkedId == R.id.category_sea) return "sea_emergency";
        return "";
    }

    private String categoryLabel(String category) {
        if ("fire".equals(category)) return getString(R.string.report_category_fire);
        if ("crime".equals(category)) return getString(R.string.report_category_crime);
        if ("medical_aid".equals(category)) return getString(R.string.report_category_medical);
        if ("humanitarian_aid".equals(category)) return getString(R.string.report_category_humanitarian);
        if ("sea_emergency".equals(category)) return getString(R.string.report_category_sea);
        return category;
    }

    private void setSubmitting(boolean sending, String label) {
        submitting = sending;
        submitButton.setEnabled(!sending);
        submitButton.setText(label);
    }

    private void markReportSubmitted() {
        submitting = false;
        reportSubmitted = true;
        if (submitButton != null) {
            submitButton.setEnabled(false);
            submitButton.setText(R.string.report_continue);
        }
    }

    private void goToMainPage() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    /** Semak laporan sedia ada pengguna untuk paparan kemajuan secara automatik dari Supabase. */
    private void checkExistingReport() {
        String lastReportId = UserPrefs.getLastIncidentReportId(this);
        if (lastReportId != null && !lastReportId.trim().isEmpty()) {
            currentReportId = lastReportId.trim();
            String cachedStatus = UserPrefs.getLastIncidentReportStatus(this);
            if (cachedStatus != null) {
                currentReportStatus = cachedStatus;
            }
            if (!"resolved".equalsIgnoreCase(cachedStatus)) {
                showProgressMode();
            }
            attachActiveReportListener(lastReportId.trim());
            verifyReportExistsInDatabase(lastReportId.trim());
        }
    }

    /** Sahkan bahawa laporan masih wujud di dalam incidentReports. */
    private final Handler reportPollHandler = new Handler(Looper.getMainLooper());
    private Runnable reportPollRunnable;

    /** Sahkan bahawa laporan masih wujud di dalam Supabase. */
    private void verifyReportExistsInDatabase(String reportId) {
        if (reportId == null || reportId.trim().isEmpty()) return;
        com.example.resqtap.supabase.SupabaseManager.getInstance().getReport(reportId.trim(),
                new com.example.resqtap.supabase.SupabaseManager.Callback<org.json.JSONObject>() {
                    @Override
                    public void onSuccess(org.json.JSONObject snapshot) {
                        runOnUiThread(() -> {
                            if (snapshot == null) {
                                handleReportDeleted();
                            } else {
                                Map<String, Object> data = jsonToReportMap(snapshot);
                                renderProgressData(reportId.trim(), data);
                            }
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        android.util.Log.w("ReportActivity", "verifyReportExistsInDatabase error: " + e.getMessage());
                    }
                });
    }

    /** Pasang listener realtime (Firebase RTDB WebSocket <50ms) berserta polling Supabase. */
    private void attachActiveReportListener(String reportId) {
        detachActiveReportListener();
        if (reportId == null || reportId.trim().isEmpty()) return;
        currentReportId = reportId.trim();

        // 1. PUSH REALTIME WEBSOCKET MENERUSI FIREBASE REALTIME DATABASE (SUB-SAAT)
        try {
            activeReportRef = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                    .getReference("incidentReports")
                    .child(currentReportId);

            activeReportListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (!snapshot.exists()) return;
                    runOnUiThread(() -> {
                        Map<String, Object> data = snapshotToReportMap(snapshot);
                        String status = String.valueOf(data.get("status"));
                        if (status != null && !status.isEmpty() && !"null".equalsIgnoreCase(status)) {
                            currentReportStatus = status;
                            UserPrefs.setLastIncidentReportStatus(ReportActivity.this, status);
                            renderProgressData(currentReportId, data);
                        }
                    });
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    android.util.Log.w("ReportActivity", "activeReportListener cancelled: " + error.getMessage());
                }
            };
            activeReportRef.addValueEventListener(activeReportListener);
        } catch (Exception e) {
            android.util.Log.w("ReportActivity", "attachActiveReportListener RTDB error: " + e.getMessage());
        }

        // 2. SEGERA DENGAR PADA NOD PROFIL PENGGUNA SENDIRI UNTUK PUSH LANGSUNG DARI WEB
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            try {
                userReportIdRef = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                        .getReference("users")
                        .child(user.getUid());

                userReportIdListener = new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) return;
                        String lastId = snapshot.child("lastIncidentReportId").getValue(String.class);
                        String lastStatus = snapshot.child("lastIncidentReportStatus").getValue(String.class);
                        String lastNotes = snapshot.child("lastIncidentReportNotes").getValue(String.class);

                        if (currentReportId != null && currentReportId.equals(lastId) && lastStatus != null && !lastStatus.isEmpty()) {
                            runOnUiThread(() -> {
                                currentReportStatus = lastStatus;
                                UserPrefs.setLastIncidentReportStatus(ReportActivity.this, lastStatus);
                                updateStatusAndStepperUI(lastStatus);
                                boolean isResolved = "resolved".equalsIgnoreCase(lastStatus);
                                if (!isResolved && !isShowingProgress) {
                                    showProgressMode();
                                }
                                if (btnNewReportTop != null) {
                                    btnNewReportTop.setVisibility(isShowingProgress && isResolved ? View.VISIBLE : View.GONE);
                                }
                                if (cardActiveReportBanner != null) {
                                    cardActiveReportBanner.setVisibility(View.GONE);
                                }
                                if (lastNotes != null && !lastNotes.trim().isEmpty() && tvProgressDetails != null) {
                                    tvProgressDetails.setText(lastNotes.trim());
                                    tvProgressDetails.setVisibility(View.VISIBLE);
                                }
                            });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                };
                userReportIdRef.addValueEventListener(userReportIdListener);
            } catch (Exception e) {
                android.util.Log.w("ReportActivity", "attachActiveReportListener user node error: " + e.getMessage());
            }
        }

        // 3. POLLING BERKALA SUPABASE SEBAGAI FALLBACK SANDARAN
        reportPollRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentReportId == null || currentReportId.isEmpty()) return;
                com.example.resqtap.supabase.SupabaseManager.getInstance().getReport(currentReportId,
                        new com.example.resqtap.supabase.SupabaseManager.Callback<org.json.JSONObject>() {
                            @Override
                            public void onSuccess(org.json.JSONObject reportObj) {
                                runOnUiThread(() -> {
                                    if (reportObj == null) {
                                        return;
                                    }
                                    Map<String, Object> data = jsonToReportMap(reportObj);
                                    String status = String.valueOf(data.get("status"));
                                    currentReportStatus = status;
                                    UserPrefs.setLastIncidentReportStatus(ReportActivity.this, status);
                                    renderProgressData(currentReportId, data);

                                boolean isResolved = "resolved".equalsIgnoreCase(status);
                                if (!isResolved) {
                                    if (!isShowingProgress) showProgressMode();
                                    if (btnNewReportTop != null) btnNewReportTop.setVisibility(View.GONE);
                                    if (cardActiveReportBanner != null) cardActiveReportBanner.setVisibility(View.GONE);
                                    reportPollHandler.postDelayed(reportPollRunnable, 2000);
                                } else {
                                    if (btnNewReportTop != null && isShowingProgress) {
                                        btnNewReportTop.setVisibility(View.VISIBLE);
                                    }
                                    reportPollHandler.postDelayed(reportPollRunnable, 3500);
                                }
                                });
                            }

                            @Override
                            public void onError(Exception error) {
                                reportPollHandler.postDelayed(reportPollRunnable, 3000);
                            }
                        });
            }
        };
        reportPollHandler.post(reportPollRunnable);
    }

    /** Tukar DataSnapshot RTDB kepada format Map laporan serasi. */
    private Map<String, Object> snapshotToReportMap(DataSnapshot snapshot) {
        Map<String, Object> map = new HashMap<>();
        if (snapshot == null || !snapshot.exists()) return map;

        String status = snapshot.child("status").getValue(String.class);
        if (status == null || status.isEmpty()) status = "new";
        map.put("status", status);

        String title = snapshot.child("title").getValue(String.class);
        map.put("title", title != null ? title : "");

        String category = snapshot.child("category").getValue(String.class);
        map.put("category", category != null ? category : "");
        map.put("categoryLabel", categoryLabel(category != null ? category : ""));

        String desc = snapshot.child("description").getValue(String.class);
        if (desc == null || desc.isEmpty()) desc = snapshot.child("details").getValue(String.class);
        if (desc == null || desc.isEmpty()) desc = snapshot.child("progressNotes").getValue(String.class);
        if (desc == null || desc.isEmpty()) desc = snapshot.child("adminNotes").getValue(String.class);
        map.put("details", desc != null ? desc : "");
        map.put("description", desc != null ? desc : "");

        String address = snapshot.child("address").getValue(String.class);
        map.put("address", address != null ? address : "");

        Double lat = snapshot.child("latitude").getValue(Double.class);
        map.put("latitude", lat != null ? lat : 0.0);
        Double lng = snapshot.child("longitude").getValue(Double.class);
        map.put("longitude", lng != null ? lng : 0.0);

        String senderName = snapshot.child("senderName").getValue(String.class);
        map.put("senderName", senderName != null ? senderName : "");
        String senderUid = snapshot.child("senderUid").getValue(String.class);
        map.put("senderUid", senderUid != null ? senderUid : "");

        Long createdAt = snapshot.child("createdAt").getValue(Long.class);
        map.put("createdAt", createdAt != null ? createdAt : System.currentTimeMillis());

        return map;
    }

    /** Tanggalkan listener kemajuan laporan aktif jika ada. */
    private void detachActiveReportListener() {
        if (reportPollRunnable != null) {
            reportPollHandler.removeCallbacks(reportPollRunnable);
            reportPollRunnable = null;
        }
        if (activeReportRef != null && activeReportListener != null) {
            try {
                activeReportRef.removeEventListener(activeReportListener);
            } catch (Exception ignored) {}
            activeReportListener = null;
            activeReportRef = null;
        }
        if (userReportIdRef != null && userReportIdListener != null) {
            try {
                userReportIdRef.removeEventListener(userReportIdListener);
            } catch (Exception ignored) {}
            userReportIdListener = null;
            userReportIdRef = null;
        }
    }

    /** Apabila laporan telah dipadam dari pangkalan data/web admin. */
    private void handleReportDeleted() {
        detachActiveReportListener();
        UserPrefs.clearLastIncidentReportId(ReportActivity.this);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            try {
                FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                        .getReference("users")
                        .child(user.getUid())
                        .child("lastIncidentReportId")
                        .removeValue();
            } catch (Exception ignored) {
            }
        }
        currentReportId = null;
        currentReportStatus = null;
        reportSubmitted = false;
        submitting = false;

        if (tvProgressReportId != null) tvProgressReportId.setText("");
        if (tvProgressTime != null) tvProgressTime.setText("");
        if (tvProgressCategory != null) tvProgressCategory.setText("");
        if (tvProgressAddress != null) tvProgressAddress.setText("");
        if (tvProgressDetails != null) {
            tvProgressDetails.setText("");
            tvProgressDetails.setVisibility(View.GONE);
        }
        if (progressAttachmentsContainer != null) {
            progressAttachmentsContainer.removeAllViews();
        }
        if (progressAttachmentsScroll != null) {
            progressAttachmentsScroll.setVisibility(View.GONE);
        }

        if (cardActiveReportBanner != null) {
            cardActiveReportBanner.setVisibility(View.GONE);
        }
        if (btnNewReportTop != null) {
            btnNewReportTop.setVisibility(View.GONE);
        }

        if (detailsInput != null) detailsInput.setText("");
        attachments.clear();
        updateAttachmentSummary();
        if (submitButton != null) {
            submitButton.setEnabled(true);
            submitButton.setText(R.string.report_continue);
        }

        isShowingProgress = false;
        if (reportProgressScroll != null) reportProgressScroll.setVisibility(View.GONE);
        if (reportScroll != null) reportScroll.setVisibility(View.VISIBLE);
        if (submitContainer != null) submitContainer.setVisibility(View.VISIBLE);
        if (reportTitleView != null) {
            reportTitleView.setText(R.string.report_title);
        }

        Toast.makeText(this, R.string.report_deleted_or_closed, Toast.LENGTH_SHORT).show();
    }

    /** Paparkan paparan kemajuan (Progress Page). */
    private void showProgressMode() {
        if (currentReportId == null) return;
        isShowingProgress = true;
        if (successOverlay != null) successOverlay.setVisibility(View.GONE);
        if (reportScroll != null) reportScroll.setVisibility(View.GONE);
        if (submitContainer != null) submitContainer.setVisibility(View.GONE);
        if (reportProgressScroll != null) {
            reportProgressScroll.setVisibility(View.VISIBLE);
            reportProgressScroll.scrollTo(0, 0);
        }
        if (reportTitleView != null) {
            reportTitleView.setText(R.string.report_progress_title);
        }
        if (btnTopChatAdmin != null) {
            btnTopChatAdmin.setVisibility(View.VISIBLE);
        }
        if (btnTopCallAdmin != null) {
            btnTopCallAdmin.setVisibility(View.VISIBLE);
        }
        if (btnNewReportTop != null) {
            boolean isResolved = "resolved".equalsIgnoreCase(currentReportStatus);
            btnNewReportTop.setVisibility(isResolved ? View.VISIBLE : View.GONE);
        }
    }

    /** Paparkan paparan kemajuan berserta data laporan terus. */
    private void showProgressMode(String reportId, Map<String, Object> data) {
        currentReportId = reportId;
        showProgressMode();
        renderProgressData(reportId, data);
        attachActiveReportListener(reportId);
    }

    /** Paparkan semula borang input Make a Report. */
}
