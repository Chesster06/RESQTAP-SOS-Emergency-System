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
import com.example.resqtap.utils.UserPrefs;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
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
        detachUserReportIdListener();
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

    private BitmapDescriptor incidentMarkerDesc;

    /** Muat atau jana BitmapDescriptor pin kecemasan tema pink ResQTap. */
    private BitmapDescriptor getIncidentMarkerDescriptor() {
        if (incidentMarkerDesc == null) {
            try {
                Bitmap bmp = BitmapFactory.decodeResource(getResources(), R.drawable.ic_marker_resqtap_pink);
                if (bmp != null) {
                    incidentMarkerDesc = BitmapDescriptorFactory.fromBitmap(bmp);
                }
            } catch (Exception ignored) {}
            if (incidentMarkerDesc == null) {
                incidentMarkerDesc = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE);
            }
        }
        return incidentMarkerDesc;
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
                        .icon(getIncidentMarkerDescriptor())
                        .anchor(0.5f, 0.922f)
                        .title(getString(R.string.report_location_title)));
            } else {
                marker.setPosition(incidentLatLng);
                marker.setIcon(getIncidentMarkerDescriptor());
                marker.setAnchor(0.5f, 0.922f);
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
                        .icon(getIncidentMarkerDescriptor())
                        .anchor(0.5f, 0.922f)
                        .title(getString(R.string.report_location_title)));
            } else {
                marker.setPosition(incidentLatLng);
                marker.setIcon(getIncidentMarkerDescriptor());
                marker.setAnchor(0.5f, 0.922f);
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
        DatabaseReference reportRef = FirebaseDatabase
                .getInstance(FirebaseRoomClient.DATABASE_URL)
                .getReference("incidentReports")
                .push();
        String reportId = reportRef.getKey();
        if (reportId == null || reportId.trim().isEmpty()) {
            setSubmitting(false, getString(R.string.report_continue));
            Toast.makeText(this, R.string.report_submit_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        uploadAttachments(user.getUid(), reportId, 0, new ArrayList<>(), uploaded -> {
            Map<String, Object> report = buildReportPayload(reportId, user, category, uploaded);
            reportRef.setValue(report).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    markReportSubmitted();
                    currentReportStatus = "new";
                    UserPrefs.setLastIncidentReportStatus(ReportActivity.this, "new");
                    UserPrefs.setLastIncidentReportId(ReportActivity.this, reportId);
                    try {
                        FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                                .getReference("users").child(user.getUid())
                                .child("lastIncidentReportId").setValue(reportId);
                    } catch (Exception ignored) {
                    }
                    // Juga simpan laporan ke Supabase
                    try {
                        String uId = user != null ? user.getUid() : "anon";
                        String uName = safe(UserPrefs.getName(ReportActivity.this));
                        String rTitle = report.containsKey("title") && report.get("title") != null ? String.valueOf(report.get("title")) : category;
                        String rDesc = report.containsKey("description") && report.get("description") != null ? String.valueOf(report.get("description")) : "";
                        String rAddr = report.containsKey("address") && report.get("address") != null ? String.valueOf(report.get("address")) : "";
                        double rLat = report.get("latitude") instanceof Number ? ((Number) report.get("latitude")).doubleValue() : 0.0;
                        double rLng = report.get("longitude") instanceof Number ? ((Number) report.get("longitude")).doubleValue() : 0.0;
                        com.example.resqtap.supabase.SupabaseManager.getInstance().submitReport(
                                uId, uName, rTitle, rDesc, category, rLat, rLng, rAddr, null
                        );
                    } catch (Exception ignored) {
                    }
                    if (successOverlay != null) successOverlay.setVisibility(View.GONE);
                    showProgressMode(reportId, report);
                    Toast.makeText(ReportActivity.this, R.string.report_submitted, Toast.LENGTH_SHORT).show();
                } else {
                    setSubmitting(false, getString(R.string.report_continue));
                    Toast.makeText(this, R.string.report_submit_failed, Toast.LENGTH_SHORT).show();
                }
            });
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

    /** Semak laporan sedia ada pengguna untuk paparan kemajuan secara automatik. */
    private void checkExistingReport() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            attachUserReportIdListener(user.getUid());
        }

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
            return;
        }

        if (user != null) {
            FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                    .getReference("users")
                    .child(user.getUid())
                    .child("lastIncidentReportId")
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot.exists()) {
                            String cloudReportId = snapshot.getValue(String.class);
                            if (cloudReportId != null && !cloudReportId.trim().isEmpty()) {
                                UserPrefs.setLastIncidentReportId(this, cloudReportId.trim());
                                currentReportId = cloudReportId.trim();
                                attachActiveReportListener(cloudReportId.trim());
                                verifyReportExistsInDatabase(cloudReportId.trim());
                            }
                        }
                    });
        }
    }

    /** Pasang listener untuk mengesan jika lastIncidentReportId pengguna dipadam oleh admin. */
    private void attachUserReportIdListener(String uid) {
        detachUserReportIdListener();
        userReportIdRef = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                .getReference("users")
                .child(uid)
                .child("lastIncidentReportId");
        userReportIdListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String cloudReportId = snapshot.getValue(String.class);
                if ((cloudReportId == null || cloudReportId.trim().isEmpty()) && currentReportId != null) {
                    handleReportDeleted();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
            }
        };
        userReportIdRef.addValueEventListener(userReportIdListener);
    }

    /** Tanggalkan listener lastIncidentReportId pengguna. */
    private void detachUserReportIdListener() {
        if (userReportIdRef != null && userReportIdListener != null) {
            userReportIdRef.removeEventListener(userReportIdListener);
            userReportIdListener = null;
            userReportIdRef = null;
        }
    }

    /** Sahkan bahawa laporan masih wujud di dalam incidentReports. */
    private void verifyReportExistsInDatabase(String reportId) {
        if (reportId == null || reportId.trim().isEmpty()) return;
        FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                .getReference("incidentReports")
                .child(reportId.trim())
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        handleReportDeleted();
                    } else {
                        Map<String, Object> data = (Map<String, Object>) snapshot.getValue();
                        renderProgressData(reportId.trim(), data);
                    }
                })
                .addOnFailureListener(e -> {
                    handleReportDeleted();
                });
    }

    /** Pasang listener masa nyata untuk status kemajuan laporan aktif. */
    private void attachActiveReportListener(String reportId) {
        detachActiveReportListener();
        currentReportId = reportId;
        activeReportRef = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                .getReference("incidentReports")
                .child(reportId);

        activeReportListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    handleReportDeleted();
                    return;
                }

                Map<String, Object> data = (Map<String, Object>) snapshot.getValue();
                if (data == null) data = new HashMap<>();

                String status = String.valueOf(data.get("status"));
                currentReportStatus = status;
                UserPrefs.setLastIncidentReportStatus(ReportActivity.this, status);

                renderProgressData(reportId, data);

                boolean isResolved = "resolved".equalsIgnoreCase(status);

                if (!isResolved) {
                    if (!isShowingProgress) {
                        showProgressMode();
                    }
                    if (btnNewReportTop != null) {
                        btnNewReportTop.setVisibility(View.GONE);
                    }
                    if (cardActiveReportBanner != null) {
                        cardActiveReportBanner.setVisibility(View.GONE);
                    }
                } else {
                    if (btnNewReportTop != null && isShowingProgress) {
                        btnNewReportTop.setVisibility(View.VISIBLE);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                if (error != null && (error.getCode() == DatabaseError.PERMISSION_DENIED
                        || error.getCode() == DatabaseError.DATA_STALE)) {
                    handleReportDeleted();
                }
            }
        };
        activeReportRef.addValueEventListener(activeReportListener);
    }

    /** Tanggalkan listener kemajuan laporan aktif jika ada. */
    private void detachActiveReportListener() {
        if (activeReportRef != null && activeReportListener != null) {
            activeReportRef.removeEventListener(activeReportListener);
            activeReportListener = null;
            activeReportRef = null;
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
    private void showFormMode() {
        if (currentReportStatus != null && !"resolved".equalsIgnoreCase(currentReportStatus)) {
            Toast.makeText(this, R.string.report_active_pending_resolution, Toast.LENGTH_SHORT).show();
            showProgressMode();
            return;
        }
        isShowingProgress = false;
        if (btnNewReportTop != null) {
            btnNewReportTop.setVisibility(View.GONE);
        }
        if (reportProgressScroll != null) reportProgressScroll.setVisibility(View.GONE);
        if (reportScroll != null) reportScroll.setVisibility(View.VISIBLE);
        if (submitContainer != null) submitContainer.setVisibility(View.VISIBLE);
        if (reportTitleView != null) {
            reportTitleView.setText(R.string.report_title);
        }
        if (currentReportId != null && cardActiveReportBanner != null) {
            cardActiveReportBanner.setVisibility(View.VISIBLE);
        }
    }

    /** Mulakan laporan baharu dan reset borang. */
    private void startNewReport() {
        if (currentReportStatus != null && !"resolved".equalsIgnoreCase(currentReportStatus)) {
            Toast.makeText(this, R.string.report_active_pending_resolution, Toast.LENGTH_SHORT).show();
            return;
        }
        if (detailsInput != null) detailsInput.setText("");
        attachments.clear();
        updateAttachmentSummary();
        submitting = false;
        reportSubmitted = false;
        if (submitButton != null) {
            submitButton.setEnabled(true);
            submitButton.setText(R.string.report_continue);
        }
        showFormMode();
    }

    /** Render data kemajuan laporan ke dalam UI. */
    private void renderProgressData(String reportId, Map<String, Object> data) {
        if (data == null) return;
        currentReportId = reportId;

        if (tvProgressReportId != null) {
            String shortId = reportId != null && reportId.length() > 8
                    ? reportId.substring(Math.max(0, reportId.length() - 8))
                    : reportId;
            tvProgressReportId.setText("#" + shortId);
        }

        String status = String.valueOf(data.get("status"));
        currentReportStatus = status;
        updateStatusAndStepperUI(status);
        boolean isResolved = "resolved".equalsIgnoreCase(status);
        if (btnNewReportTop != null) {
            btnNewReportTop.setVisibility(isShowingProgress && isResolved ? View.VISIBLE : View.GONE);
        }

        Object createdAtObj = data.get("createdAt");
        long timestamp = 0L;
        if (createdAtObj instanceof Number) {
            timestamp = ((Number) createdAtObj).longValue();
        }
        if (timestamp > 0L && tvProgressTime != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
            tvProgressTime.setText(sdf.format(new Date(timestamp)));
        } else if (tvProgressTime != null) {
            tvProgressTime.setText(R.string.report_status_new);
        }

        String category = String.valueOf(data.get("category"));
        String categoryLabel = String.valueOf(data.get("categoryLabel"));
        if (categoryLabel == null || categoryLabel.equals("null") || categoryLabel.isEmpty()) {
            categoryLabel = categoryLabel(category);
        }
        if (tvProgressCategory != null) {
            tvProgressCategory.setText(categoryLabel);
        }
        updateCategoryIcon(category);

        String address = String.valueOf(data.get("address"));
        if (tvProgressAddress != null) {
            if (address != null && !address.equals("null") && !address.trim().isEmpty()) {
                tvProgressAddress.setText(address);
            } else {
                tvProgressAddress.setText(R.string.report_location_unavailable);
            }
        }

        String details = String.valueOf(data.get("details"));
        if (tvProgressDetails != null) {
            if (details != null && !details.equals("null") && !details.trim().isEmpty()) {
                tvProgressDetails.setText(details);
                tvProgressDetails.setVisibility(View.VISIBLE);
            } else {
                tvProgressDetails.setVisibility(View.GONE);
            }
        }

        renderProgressAttachments(data.get("attachments"));
    }

    /** Kemas kini stepper timeline dan status badge. */
    private void updateStatusAndStepperUI(String status) {
        if (status == null) status = "new";
        status = status.toLowerCase(Locale.ROOT);

        int colorDone = 0xFF10B981;
        int colorActive = 0xFFE60067;
        int colorPending = 0xFFCBD5E1;

        if ("resolved".equals(status)) {
            if (tvProgressStatusBadge != null) {
                tvProgressStatusBadge.setText(R.string.report_status_resolved);
                tvProgressStatusBadge.setTextColor(0xFF059669);
                tvProgressStatusBadge.setBackgroundColor(0xFFD1FAE5);
            }
            if (tvProgressMessage != null) {
                tvProgressMessage.setText(R.string.report_step_4_desc);
            }

            setStepCircle(step1Circle, step1Icon, R.drawable.bg_step_circle_done, R.drawable.ic_check_24, 0xFFFFFFFF);
            if (stepLine12 != null) stepLine12.setBackgroundColor(colorDone);

            setStepCircle(step2Circle, step2Icon, R.drawable.bg_step_circle_done, R.drawable.ic_check_24, 0xFFFFFFFF);
            if (stepLine23 != null) stepLine23.setBackgroundColor(colorDone);

            setStepCircle(step3Circle, step3Icon, R.drawable.bg_step_circle_done, R.drawable.ic_check_24, 0xFFFFFFFF);
            if (stepLine34 != null) stepLine34.setBackgroundColor(colorDone);

            setStepCircle(step4Circle, step4Icon, R.drawable.bg_step_circle_done, R.drawable.ic_check_24, 0xFFFFFFFF);

            if (tvStep2Title != null) tvStep2Title.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            if (tvStep3Title != null) tvStep3Title.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            if (tvStep4Title != null) tvStep4Title.setTextColor(0xFF059669);

        } else if ("reviewing".equals(status)) {
            if (tvProgressStatusBadge != null) {
                tvProgressStatusBadge.setText(R.string.report_status_reviewing);
                tvProgressStatusBadge.setTextColor(0xFFD97706);
                tvProgressStatusBadge.setBackgroundColor(0xFFFEF3C7);
            }
            if (tvProgressMessage != null) {
                tvProgressMessage.setText(R.string.report_step_2_desc);
            }

            setStepCircle(step1Circle, step1Icon, R.drawable.bg_step_circle_done, R.drawable.ic_check_24, 0xFFFFFFFF);
            if (stepLine12 != null) stepLine12.setBackgroundColor(colorDone);

            setStepCircle(step2Circle, step2Icon, R.drawable.bg_step_circle_done, R.drawable.ic_check_24, 0xFFFFFFFF);
            if (stepLine23 != null) stepLine23.setBackgroundColor(colorActive);

            setStepCircle(step3Circle, step3Icon, R.drawable.bg_step_circle_active, R.drawable.ic_ios_security, 0xFFFFFFFF);
            if (stepLine34 != null) stepLine34.setBackgroundColor(colorPending);

            setStepCircle(step4Circle, step4Icon, R.drawable.bg_step_circle_pending, R.drawable.ic_verified_user_24, 0xFF64748B);

            if (tvStep2Title != null) tvStep2Title.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            if (tvStep3Title != null) tvStep3Title.setTextColor(colorActive);
            if (tvStep4Title != null) tvStep4Title.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));

        } else if ("rejected".equals(status)) {
            if (tvProgressStatusBadge != null) {
                tvProgressStatusBadge.setText(R.string.report_status_rejected);
                tvProgressStatusBadge.setTextColor(0xFFE11D48);
                tvProgressStatusBadge.setBackgroundColor(0xFFFFE4EE);
            }
            if (tvProgressMessage != null) {
                tvProgressMessage.setText(R.string.report_status_rejected);
            }
            setStepCircle(step1Circle, step1Icon, R.drawable.bg_step_circle_done, R.drawable.ic_check_24, 0xFFFFFFFF);
            if (stepLine12 != null) stepLine12.setBackgroundColor(0xFFE11D48);
            setStepCircle(step2Circle, step2Icon, R.drawable.bg_step_circle_pending, R.drawable.ic_close_24, 0xFFE11D48);

        } else {
            // "new" status
            if (tvProgressStatusBadge != null) {
                tvProgressStatusBadge.setText(R.string.report_status_new);
                tvProgressStatusBadge.setTextColor(0xFF0284C7);
                tvProgressStatusBadge.setBackgroundColor(0xFFE0F2FE);
            }
            if (tvProgressMessage != null) {
                tvProgressMessage.setText(R.string.report_step_1_desc);
            }

            setStepCircle(step1Circle, step1Icon, R.drawable.bg_step_circle_done, R.drawable.ic_check_24, 0xFFFFFFFF);
            if (stepLine12 != null) stepLine12.setBackgroundColor(colorActive);

            setStepCircle(step2Circle, step2Icon, R.drawable.bg_step_circle_active, R.drawable.ic_clock_24, 0xFFFFFFFF);
            if (stepLine23 != null) stepLine23.setBackgroundColor(colorPending);

            setStepCircle(step3Circle, step3Icon, R.drawable.bg_step_circle_pending, R.drawable.ic_ios_security, 0xFF64748B);
            if (stepLine34 != null) stepLine34.setBackgroundColor(colorPending);

            setStepCircle(step4Circle, step4Icon, R.drawable.bg_step_circle_pending, R.drawable.ic_verified_user_24, 0xFF64748B);

            if (tvStep2Title != null) tvStep2Title.setTextColor(colorActive);
            if (tvStep3Title != null) tvStep3Title.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            if (tvStep4Title != null) tvStep4Title.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    private void setStepCircle(FrameLayout circle, ImageView icon, int bgRes, int iconRes, int iconTint) {
        if (circle != null) circle.setBackgroundResource(bgRes);
        if (icon != null) {
            icon.setImageResource(iconRes);
            icon.setColorFilter(iconTint);
        }
    }

    private String getStatusLabel(String status) {
        if ("reviewing".equalsIgnoreCase(status)) return getString(R.string.report_status_reviewing);
        if ("resolved".equalsIgnoreCase(status)) return getString(R.string.report_status_resolved);
        if ("rejected".equalsIgnoreCase(status)) return getString(R.string.report_status_rejected);
        return getString(R.string.report_status_new);
    }

    private void updateCategoryIcon(String category) {
        if (imgProgressCategoryIcon == null) return;
        if ("fire".equals(category)) {
            imgProgressCategoryIcon.setImageResource(R.drawable.ic_flash_on_24);
            imgProgressCategoryIcon.setColorFilter(0xFFFF5722);
        } else if ("medical_aid".equals(category)) {
            imgProgressCategoryIcon.setImageResource(R.drawable.ic_medical_cross);
            imgProgressCategoryIcon.setColorFilter(0xFFE91E63);
        } else if ("humanitarian_aid".equals(category)) {
            imgProgressCategoryIcon.setImageResource(R.drawable.ic_heart_health);
            imgProgressCategoryIcon.setColorFilter(0xFF4CAF50);
        } else if ("sea_emergency".equals(category)) {
            imgProgressCategoryIcon.setImageResource(R.drawable.ic_alt_route);
            imgProgressCategoryIcon.setColorFilter(0xFF0284C7);
        } else {
            imgProgressCategoryIcon.setImageResource(R.drawable.ic_ios_security);
            imgProgressCategoryIcon.setColorFilter(0xFFE60067);
        }
    }

    private void copyReportIdToClipboard() {
        if (currentReportId == null || currentReportId.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("Report ID", currentReportId);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, R.string.report_copied_id, Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshProgress() {
        if (currentReportId == null) return;
        if (ivRefreshIcon != null) {
            ivRefreshIcon.animate()
                    .rotationBy(360f)
                    .setDuration(600)
                    .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                    .start();
        }
        FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                .getReference("incidentReports")
                .child(currentReportId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        Map<String, Object> data = (Map<String, Object>) snapshot.getValue();
                        renderProgressData(currentReportId, data);
                    } else {
                        handleReportDeleted();
                    }
                })
                .addOnFailureListener(e -> {
                    handleReportDeleted();
                });
    }

    private void renderProgressAttachments(Object attachmentsObj) {
        if (progressAttachmentsContainer == null || progressAttachmentsScroll == null) return;
        progressAttachmentsContainer.removeAllViews();

        if (attachmentsObj instanceof List) {
            List<?> list = (List<?>) attachmentsObj;
            if (list.isEmpty()) {
                progressAttachmentsScroll.setVisibility(View.GONE);
                return;
            }
            progressAttachmentsScroll.setVisibility(View.VISIBLE);
            for (Object item : list) {
                if (item instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) item;
                    String name = String.valueOf(map.get("name"));
                    String url = null;
                    if (map.containsKey("downloadUrl") && map.get("downloadUrl") != null) {
                        url = String.valueOf(map.get("downloadUrl"));
                    } else if (map.containsKey("url") && map.get("url") != null) {
                        url = String.valueOf(map.get("url"));
                    } else if (map.containsKey("inlineDataUrl") && map.get("inlineDataUrl") != null) {
                        url = String.valueOf(map.get("inlineDataUrl"));
                    }
                    if ("null".equals(url)) url = null;

                    MaterialCardView card = new MaterialCardView(this);
                    card.setRadius(dp(14));
                    card.setCardElevation(0f);
                    card.setStrokeWidth(dp(1));
                    card.setStrokeColor(ContextCompat.getColor(this, R.color.divider));
                    card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.panel));
                    card.setClickable(true);
                    card.setFocusable(true);
                    card.setRippleColor(ColorStateList.valueOf(0x1F000000));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.setMarginEnd(dp(10));
                    card.setLayoutParams(lp);

                    LinearLayout content = new LinearLayout(this);
                    content.setOrientation(LinearLayout.VERTICAL);
                    content.setPadding(dp(6), dp(6), dp(6), dp(6));

                    ImageView iv = new ImageView(this);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setBackgroundColor(ContextCompat.getColor(this, R.color.report_tile_bg));
                    content.addView(iv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80)));

                    TextView tv = new TextView(this);
                    tv.setText(name != null && !name.equals("null") && !name.isEmpty() ? name : "Attachment");
                    tv.setTextSize(11f);
                    tv.setMaxLines(1);
                    tv.setEllipsize(TextUtils.TruncateAt.END);
                    tv.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                    tv.setPadding(dp(2), dp(4), dp(2), dp(2));
                    content.addView(tv);

                    card.addView(content);

                    Bitmap bmp = null;
                    if (url != null && url.startsWith("data:image")) {
                        try {
                            int comma = url.indexOf(',');
                            if (comma != -1) {
                                byte[] bytes = Base64.decode(url.substring(comma + 1), Base64.DEFAULT);
                                bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    final String finalName = (name != null && !name.equals("null") && !name.trim().isEmpty()) ? name : "Image";
                    final String finalUrl = url;

                    if (bmp != null) {
                        iv.setImageBitmap(bmp);
                        iv.setPadding(0, 0, 0, 0);
                        iv.clearColorFilter();
                        final Bitmap previewBmp = bmp;
                        card.setOnClickListener(v -> showAttachmentViewer(finalName, previewBmp, finalUrl));
                    } else if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                        iv.setImageResource(R.drawable.ic_livechat_photo);
                        iv.setColorFilter(ContextCompat.getColor(this, R.color.report_tile_content));
                        iv.setPadding(dp(20), dp(20), dp(20), dp(20));

                        new Thread(() -> {
                            try {
                                URL u = new URL(finalUrl);
                                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                                conn.setConnectTimeout(8000);
                                conn.setReadTimeout(8000);
                                if (conn.getResponseCode() == 200) {
                                    InputStream is = conn.getInputStream();
                                    Bitmap netBmp = BitmapFactory.decodeStream(is);
                                    is.close();
                                    conn.disconnect();
                                    if (netBmp != null) {
                                        runOnUiThread(() -> {
                                            iv.setPadding(0, 0, 0, 0);
                                            iv.clearColorFilter();
                                            iv.setImageBitmap(netBmp);
                                            card.setOnClickListener(v -> showAttachmentViewer(finalName, netBmp, finalUrl));
                                        });
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }).start();

                        card.setOnClickListener(v -> showAttachmentViewer(finalName, null, finalUrl));
                    } else {
                        iv.setImageResource(R.drawable.ic_livechat_photo);
                        iv.setColorFilter(ContextCompat.getColor(this, R.color.report_tile_content));
                        iv.setPadding(dp(20), dp(20), dp(20), dp(20));
                    }

                    progressAttachmentsContainer.addView(card);
                }
            }
        } else {
            progressAttachmentsScroll.setVisibility(View.GONE);
        }
    }

    /** Paparkan gambar attachment dalam kad dialog kompak yang kemas. */
    private void showAttachmentViewer(String fileName, Bitmap bitmap, String fileUrl) {
        try {
            Dialog dialog = new Dialog(this);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setContentView(R.layout.dialog_attachment_preview);
            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(true);

            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                window.setGravity(Gravity.CENTER);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                window.setDimAmount(0.60f);
            }

            TextView tvTitle = dialog.findViewById(R.id.tv_preview_title);
            ImageButton btnClose = dialog.findViewById(R.id.btn_close_preview);
            ImageView ivPreview = dialog.findViewById(R.id.iv_preview_image);
            ProgressBar pbLoading = dialog.findViewById(R.id.pb_preview_loading);

            if (tvTitle != null) {
                tvTitle.setText(fileName != null && !fileName.isEmpty() ? fileName : "Attachment");
            }
            if (btnClose != null) {
                btnClose.setOnClickListener(v -> dialog.dismiss());
            }

            if (bitmap != null) {
                if (ivPreview != null) ivPreview.setImageBitmap(bitmap);
            } else if (fileUrl != null && !fileUrl.isEmpty()) {
                if (fileUrl.startsWith("data:image")) {
                    try {
                        int comma = fileUrl.indexOf(',');
                        if (comma != -1) {
                            byte[] bytes = Base64.decode(fileUrl.substring(comma + 1), Base64.DEFAULT);
                            Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            if (b != null && ivPreview != null) ivPreview.setImageBitmap(b);
                        }
                    } catch (Exception ignored) {
                    }
                } else if (fileUrl.startsWith("http://") || fileUrl.startsWith("https://")) {
                    if (pbLoading != null) pbLoading.setVisibility(View.VISIBLE);
                    new Thread(() -> {
                        try {
                            URL u = new URL(fileUrl);
                            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                            conn.setConnectTimeout(8000);
                            conn.setReadTimeout(8000);
                            if (conn.getResponseCode() == 200) {
                                InputStream is = conn.getInputStream();
                                Bitmap netBmp = BitmapFactory.decodeStream(is);
                                is.close();
                                conn.disconnect();
                                if (netBmp != null) {
                                    runOnUiThread(() -> {
                                        if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                                        if (ivPreview != null) ivPreview.setImageBitmap(netBmp);
                                    });
                                }
                            }
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                            });
                        }
                    }).start();
                }
            }

            dialog.show();
        } catch (Exception ignored) {
        }
    }

    /** Paparkan SuccessSheet. */
    private void showSuccessSheet() {
        if (successOverlay == null) {
            Toast.makeText(this, R.string.report_submitted, Toast.LENGTH_SHORT).show();
            goToMainPage();
            return;
        }
        if (submitContainer != null) submitContainer.setVisibility(View.GONE);
        successOverlay.bringToFront();
        successOverlay.setAlpha(0f);
        successOverlay.setVisibility(View.VISIBLE);
        successOverlay.animate().alpha(1f).setDuration(180).start();

        if (successSheet != null) {
            successSheet.post(() -> {
                successSheet.setTranslationY(successSheet.getHeight());
                successSheet.animate()
                        .translationY(0f)
                        .setDuration(280)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
            });
        }
    }

    /** Simpan atau hantar data AttachmentSummary. */
    private void updateAttachmentSummary() {
        if (attachmentSummaryView == null) return;
        if (attachments.isEmpty()) {
            attachmentSummaryView.setText(R.string.report_no_attachments);
        } else {
            attachmentSummaryView.setText(getString(R.string.report_attachment_count, attachments.size()));
        }
        renderAttachmentPreviews();
    }

    private void renderAttachmentPreviews() {
        if (attachmentPreviews == null || attachmentPreviewScroll == null) return;
        attachmentPreviews.removeAllViews();
        attachmentPreviewScroll.setVisibility(attachments.isEmpty() ? View.GONE : View.VISIBLE);
        for (int i = 0; i < attachments.size(); i++) {
            attachmentPreviews.addView(createPreviewTile(attachments.get(i), i));
        }
    }

    private View createPreviewTile(AttachmentInfo attachment, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackgroundResource(R.drawable.bg_report_preview_tile);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dp(108), ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMarginEnd(dp(10));
        card.setLayoutParams(cardParams);

        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackgroundColor(ContextCompat.getColor(this, R.color.report_tile_bg));
        Bitmap bitmap = isImageAttachment(attachment) ? previewBitmap(attachment, 220) : null;
        if (bitmap != null) {
            preview.setImageBitmap(bitmap);
        } else {
            preview.setImageResource(R.drawable.ic_livechat_document);
            preview.setColorFilter(ContextCompat.getColor(this, R.color.report_tile_content));
            preview.setPadding(dp(26), dp(26), dp(26), dp(26));
        }
        card.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(78)
        ));

        TextView name = new TextView(this);
        name.setText(attachment.name.isEmpty() ? "Attachment " + (index + 1) : attachment.name);
        name.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        name.setTextSize(11);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        nameParams.topMargin = dp(6);
        card.addView(name, nameParams);
        return card;
    }

    private Map<String, Object> inlineImageAttachmentMap(AttachmentInfo attachment) {
        Map<String, Object> data = new HashMap<>();
        String mimeType = safe(attachment.mimeType);
        if (mimeType.isEmpty() || !mimeType.startsWith("image/")) mimeType = "image/jpeg";
        String b64 = encodeImageToBase64(attachment, 1024, 74);
        if (b64.isEmpty()) return data;
        data.put("name", attachment.name);
        data.put("mimeType", "image/jpeg");
        data.put("size", attachment.size);
        data.put("downloadUrl", "data:image/jpeg;base64," + b64);
        data.put("inline", true);
        return data;
    }

    /** Semak dan sahkan ImageAttachment. */
    private boolean isImageAttachment(AttachmentInfo attachment) {
        String mimeType = safe(attachment.mimeType).toLowerCase(Locale.ROOT);
        if (mimeType.startsWith("image/")) return true;
        String name = safe(attachment.name).toLowerCase(Locale.ROOT);
        return name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".png")
                || name.endsWith(".webp");
    }

    private String encodeImageToBase64(AttachmentInfo attachment, int maxDim, int jpegQuality) {
        Bitmap bitmap = decodeAttachmentBitmap(attachment, maxDim);
        if (bitmap == null) return "";
        try {
            int largest = Math.max(bitmap.getWidth(), bitmap.getHeight());
            if (largest > maxDim) {
                float scale = maxDim / (float) largest;
                int width = Math.max(1, Math.round(bitmap.getWidth() * scale));
                int height = Math.max(1, Math.round(bitmap.getHeight() * scale));
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, width, height, true);
                bitmap.recycle();
                bitmap = scaled;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, Math.max(45, Math.min(jpegQuality, 90)), out);
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception ignored) {
            return "";
        } finally {
            bitmap.recycle();
        }
    }

    private Bitmap previewBitmap(AttachmentInfo attachment, int maxDim) {
        Bitmap bitmap = decodeAttachmentBitmap(attachment, maxDim);
        if (bitmap == null) return null;
        int largest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (largest <= maxDim) return bitmap;
        float scale = maxDim / (float) largest;
        int width = Math.max(1, Math.round(bitmap.getWidth() * scale));
        int height = Math.max(1, Math.round(bitmap.getHeight() * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, width, height, true);
        bitmap.recycle();
        return scaled;
    }

    private Bitmap decodeAttachmentBitmap(AttachmentInfo attachment, int maxDim) {
        try {
            if (attachment.bytes != null && attachment.bytes.length > 0) {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(attachment.bytes, 0, attachment.bytes.length, bounds);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDim);
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                return BitmapFactory.decodeByteArray(attachment.bytes, 0, attachment.bytes.length, opts);
            }
            if (attachment.uri == null) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (java.io.InputStream in = getContentResolver().openInputStream(attachment.uri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDim);
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (java.io.InputStream in = getContentResolver().openInputStream(attachment.uri)) {
                if (in == null) return null;
                return BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private int sampleSize(int width, int height, int maxDim) {
        int sample = 1;
        int largest = Math.max(Math.max(1, width), Math.max(1, height));
        while (largest / sample > maxDim * 2) sample *= 2;
        return sample;
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return safe(cursor.getString(index));
            }
        } catch (Exception ignored) {
        }
        return safe(uri.getLastPathSegment());
    }

    private long querySize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0) return cursor.getLong(index);
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    private String sanitizeFileName(String name) {
        String clean = safe(name).replaceAll("[\\\\/:*?\"<>|#\\[\\]$]", "_");
        if (clean.isEmpty()) clean = "attachment";
        return clean;
    }

    private String formatLatLng(LatLng latLng) {
        return String.format(Locale.US, "%.6f, %.6f", latLng.latitude, latLng.longitude);
    }

    private String safe(Object value) {
        return String.valueOf(value == null ? "" : value).trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class AttachmentInfo {
        final Uri uri;
        final byte[] bytes;
        final String name;
        final String mimeType;
        final long size;

        private AttachmentInfo(Uri uri, byte[] bytes, String name, String mimeType, long size) {
            this.uri = uri;
            this.bytes = bytes;
            this.name = name == null ? "" : name;
            this.mimeType = mimeType == null || mimeType.trim().isEmpty() ? "application/octet-stream" : mimeType;
            this.size = size;
        }

        static AttachmentInfo fromUri(Uri uri, String name, String mimeType, long size) {
            return new AttachmentInfo(uri, null, name, mimeType, size);
        }

        static AttachmentInfo fromBytes(byte[] bytes, String name, String mimeType) {
            return new AttachmentInfo(null, bytes, name, mimeType, bytes == null ? 0L : bytes.length);
        }
    }

    private interface AttachmentUploadDone {
        void onDone(ArrayList<Map<String, Object>> uploaded);
    }

    private interface AttachmentUploadFailed {
        void onFailed(Exception error);
    }
}
