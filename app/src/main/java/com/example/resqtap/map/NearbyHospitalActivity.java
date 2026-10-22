package com.example.resqtap.map;
import com.example.resqtap.R;

import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.utils.PermissionUtils;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import java.util.List;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Path;
import android.widget.ImageView;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * NearbyHospitalActivity
 * Cari hospital terdekat guna Google Places API: tunjuk jarak, nombor telefon, dan link direct ke Google Maps.
 */
public class NearbyHospitalActivity extends BaseActivity implements OnMapReadyCallback {
    private GoogleMap map;
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String[]> locationPerms;
    private CancellationTokenSource cancellationTokenSource;

    private LatLng currentLatLng;
    private Marker myLocationMarker;
    private final ArrayList<Marker> hospitalMarkers = new ArrayList<>();

    private BottomSheetBehavior<?> sheetBehavior;
    private View bottomSheetView;
    private boolean pendingSearch = false;
    private boolean gpsDialogShown = false;
    private boolean hasAutoCentered = false;
    private View mapControls;

    private Polyline routePolyline;
    private Polyline routeShadowPolyline;
    private HospitalItem activeNavHospital;

    private MaterialCardView cardNavigationInfo;
    private TextView navDestinationName;
    private TextView navEtaDistance;
    private TextView navAddress;
    private ImageView navCategoryIcon;
    private MaterialCardView navIconCard;
    private MaterialButton btnCloseNavigation;
    private MaterialButton btnToggle3d;
    private boolean isNavigating3D = true;
    private final ArrayList<LatLng> currentRoutePoints = new ArrayList<>();
    private float currentNavBearing = 0f;
    private LocationCallback navLocationCallback;
    private Bitmap navigationDirectionBitmap;

    private String myPhotoUri = "";
    private String myPhotoB64 = "";

    // Marker Callout Views (Admin Dashboard / Popup Info Style)
    private View markerCalloutLayout;
    private MaterialCardView calloutLogoRing;
    private ImageView calloutLogo;
    private TextView calloutTitle;
    private ImageView calloutBtnClose;
    private TextView calloutStatus;
    private TextView calloutDistance;
    private TextView calloutAddress;
    private MaterialButton calloutBtnDirections;
    private LatLng selectedCalloutLatLng;
    private HospitalItem selectedCalloutHospital;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ArrayList<HospitalItem> hospitals = new ArrayList<>();
    private final ArrayList<DisplayItem> displayItems = new ArrayList<>();
    private HospitalAdapter listAdapter;
    private RecyclerView recyclerView;
    private TextView status;
    private String currentCategory = "all";

    private static final int SEARCH_RADIUS_METERS = 5000;
    private static final int MAX_RESULTS_ALL_PER_CAT = 3;
    private static final int MAX_RESULTS_SINGLE_CAT = 5;

    private static final class HospitalItem {
        final String name;
        final String address;
        final double lat;
        final double lng;
        final String category;
        double distanceMeters;

        HospitalItem(String name, String address, double lat, double lng, String category) {
            this.name = name;
            this.address = address;
            this.lat = lat;
            this.lng = lng;
            this.category = category == null ? "hospital" : category;
            this.distanceMeters = -1d;
        }
    }

    private static final class DisplayItem {
        static final int TYPE_HEADER = 0;
        static final int TYPE_SERVICE = 1;

        final int type;
        final String category;
        final String headerTitle;
        final int count;
        final HospitalItem hospital;

        static DisplayItem createHeader(String category, String headerTitle, int count) {
            return new DisplayItem(TYPE_HEADER, category, headerTitle, count, null);
        }

        static DisplayItem createService(HospitalItem hospital) {
            return new DisplayItem(TYPE_SERVICE, hospital.category, null, 0, hospital);
        }

        private DisplayItem(int type, String category, String headerTitle, int count, HospitalItem hospital) {
            this.type = type;
            this.category = category;
            this.headerTitle = headerTitle;
            this.count = count;
            this.hospital = hospital;
        }
    }

    /** Keutamaan susunan kategori: 1 = Bomba, 2 = Medic / Hospital, 3 = Polis, 4 = Lain-lain. */
    private static int getCategoryPriority(String category) {
        if ("fire".equalsIgnoreCase(category)) return 1;
        if ("hospital".equalsIgnoreCase(category)) return 2;
        if ("police".equalsIgnoreCase(category)) return 3;
        return 4;
    }

    private String getCategoryHeaderTitle(String category) {
        if ("fire".equalsIgnoreCase(category)) {
            return getString(R.string.section_fire_services);
        } else if ("police".equalsIgnoreCase(category)) {
            return getString(R.string.section_police_services);
        } else {
            return getString(R.string.section_hospital_services);
        }
    }

    private void rebuildDisplayItems() {
        displayItems.clear();
        if (hospitals.isEmpty()) return;

        String currentCat = null;
        for (HospitalItem h : hospitals) {
            String cat = h.category == null ? "hospital" : h.category;
            if (!cat.equalsIgnoreCase(currentCat)) {
                currentCat = cat;
                int count = 0;
                for (HospitalItem item : hospitals) {
                    if (cat.equalsIgnoreCase(item.category)) count++;
                }
                displayItems.add(DisplayItem.createHeader(cat, getCategoryHeaderTitle(cat), count));
            }
            displayItems.add(DisplayItem.createService(h));
        }
    }

    private final class HospitalAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemViewType(int position) {
            return displayItems.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == DisplayItem.TYPE_HEADER) {
                View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hospital_header, parent, false);
                return new HeaderViewHolder(row);
            } else {
                View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hospital, parent, false);
                return new ServiceViewHolder(row);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            DisplayItem item = displayItems.get(position);
            if (holder instanceof HeaderViewHolder) {
                HeaderViewHolder vh = (HeaderViewHolder) holder;
                vh.title.setText(item.headerTitle);
                try {
                    vh.count.setText(getString(R.string.emergency_location_count, item.count));
                } catch (Exception e) {
                    vh.count.setText(String.valueOf(item.count));
                }

                vh.icon.setImageTintList(null);
                if ("fire".equalsIgnoreCase(item.category)) {
                    vh.icon.setImageResource(R.drawable.logo_bomba);
                    vh.iconCard.setCardBackgroundColor(Color.parseColor("#FFF7ED"));
                } else if ("police".equalsIgnoreCase(item.category)) {
                    vh.icon.setImageResource(R.drawable.logo_pdrm);
                    vh.iconCard.setCardBackgroundColor(Color.parseColor("#F0F4F9"));
                } else {
                    vh.icon.setImageResource(R.drawable.logo_kkm);
                    vh.iconCard.setCardBackgroundColor(Color.parseColor("#FFF0F2"));
                }
            } else if (holder instanceof ServiceViewHolder) {
                ServiceViewHolder vh = (ServiceViewHolder) holder;
                HospitalItem hospital = item.hospital;
                if (vh.name != null) vh.name.setText(hospital.name);
                if (vh.distance != null) vh.distance.setText(formatDistance(hospital.distanceMeters));
                if (vh.address != null) vh.address.setText(hospital.address == null ? "" : hospital.address);
                if (vh.direction != null) vh.direction.setOnClickListener(v -> openDirections(hospital));
                vh.itemView.setOnClickListener(v -> {
                    if (sheetBehavior != null) {
                        sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                    }
                    showMarkerCallout(hospital, new LatLng(hospital.lat, hospital.lng));
                });

                if (vh.imgCategory != null) {
                    vh.imgCategory.setImageTintList(null);
                    if ("police".equalsIgnoreCase(hospital.category)) {
                        vh.imgCategory.setImageResource(R.drawable.logo_pdrm);
                        if (vh.cardCategory != null) {
                            vh.cardCategory.setCardBackgroundColor(Color.parseColor("#F0F4F9"));
                        }
                    } else if ("fire".equalsIgnoreCase(hospital.category)) {
                        vh.imgCategory.setImageResource(R.drawable.logo_bomba);
                        if (vh.cardCategory != null) {
                            vh.cardCategory.setCardBackgroundColor(Color.parseColor("#FFF7ED"));
                        }
                    } else {
                        vh.imgCategory.setImageResource(R.drawable.logo_kkm);
                        if (vh.cardCategory != null) {
                            vh.cardCategory.setCardBackgroundColor(Color.parseColor("#FFF0F2"));
                        }
                    }
                }
            }
        }

        @Override
        public int getItemCount() {
            return displayItems.size();
        }

        final class ServiceViewHolder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView distance;
            final TextView address;
            final MaterialButton direction;
            final ImageView imgCategory;
            final MaterialCardView cardCategory;

            ServiceViewHolder(@NonNull View row) {
                super(row);
                name = row.findViewById(R.id.hospital_name);
                distance = row.findViewById(R.id.hospital_distance);
                address = row.findViewById(R.id.hospital_address);
                direction = row.findViewById(R.id.btn_direction);
                imgCategory = row.findViewById(R.id.img_category_icon);
                cardCategory = (MaterialCardView) row.findViewById(R.id.card_category_icon);
            }
        }

        final class HeaderViewHolder extends RecyclerView.ViewHolder {
            final MaterialCardView iconCard;
            final ImageView icon;
            final TextView title;
            final TextView count;

            HeaderViewHolder(@NonNull View row) {
                super(row);
                iconCard = (MaterialCardView) row.findViewById(R.id.header_icon_card);
                icon = row.findViewById(R.id.header_icon);
                title = row.findViewById(R.id.header_title);
                count = row.findViewById(R.id.header_count);
            }
        }
    }

    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_nearby_hospital);

        View main = findViewById(R.id.main);
        View overlayUi = findViewById(R.id.overlay_ui);
        bottomSheetView = findViewById(R.id.bottom_sheet);

        if (overlayUi != null) {
            ViewCompat.setOnApplyWindowInsetsListener(overlayUi, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
                return insets;
            });
        }
        if (bottomSheetView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomSheetView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                if (recyclerView != null) {
                    recyclerView.setPadding(
                            recyclerView.getPaddingLeft(),
                            recyclerView.getPaddingTop(),
                            recyclerView.getPaddingRight(),
                            dp(NearbyHospitalActivity.this, 16) + systemBars.bottom
                    );
                }
                applyMapControlsBottomOffset();
                return insets;
            });
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationPerms = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                this::onLocationPermissionResult
        );

        myPhotoUri = String.valueOf(UserPrefs.getPhotoUri(this) == null ? "" : UserPrefs.getPhotoUri(this)).trim();
        myPhotoB64 = String.valueOf(UserPrefs.getPhotoB64(this) == null ? "" : UserPrefs.getPhotoB64(this)).trim();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        MaterialButton btnZoomIn = findViewById(R.id.btn_map_zoom_in);
        MaterialButton btnZoomOut = findViewById(R.id.btn_map_zoom_out);
        MaterialButton btnMyLocation = findViewById(R.id.btn_map_my_location);
        MaterialButton btnMapType = findViewById(R.id.btn_map_type);
        MaterialButton btnBack = findViewById(R.id.btn_back);
        mapControls = findViewById(R.id.map_controls);

        if (btnZoomIn != null) btnZoomIn.setOnClickListener(v -> {
            if (map == null) return;
            map.animateCamera(CameraUpdateFactory.zoomIn());
        });
        if (btnZoomOut != null) btnZoomOut.setOnClickListener(v -> {
            if (map == null) return;
            map.animateCamera(CameraUpdateFactory.zoomOut());
        });
        if (btnMyLocation != null) btnMyLocation.setOnClickListener(v -> requestLocationIfNeeded(true));
        if (btnMapType != null) btnMapType.setOnClickListener(v -> toggleMapType());
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        MaterialButton btnSearch = findViewById(R.id.btn_search_hospital);
        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> {
                pendingSearch = true;
                fetchNearbyHospitals();
            });
        }

        ChipGroup chipGroupFilters = findViewById(R.id.chip_group_filters);
        if (chipGroupFilters != null) {
            com.google.android.material.chip.Chip chipHosp = findViewById(R.id.chip_hospital);
            if (chipHosp != null) chipHosp.setChipIconTint(null);
            com.google.android.material.chip.Chip chipPol = findViewById(R.id.chip_police);
            if (chipPol != null) chipPol.setChipIconTint(null);
            com.google.android.material.chip.Chip chipFir = findViewById(R.id.chip_fire);
            if (chipFir != null) chipFir.setChipIconTint(null);

            chipGroupFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.isEmpty()) return;
                int checkedId = checkedIds.get(0);
                if (checkedId == R.id.chip_hospital) {
                    currentCategory = "hospital";
                } else if (checkedId == R.id.chip_police) {
                    currentCategory = "police";
                } else if (checkedId == R.id.chip_fire) {
                    currentCategory = "fire";
                } else {
                    currentCategory = "all";
                }
                if (!hospitals.isEmpty()) {
                    pendingSearch = true;
                    fetchNearbyHospitals();
                }
            });
        }

        if (bottomSheetView != null) {
            sheetBehavior = BottomSheetBehavior.from(bottomSheetView);
            sheetBehavior.setHideable(false);
            sheetBehavior.setFitToContents(true);
            sheetBehavior.setSkipCollapsed(false);
            sheetBehavior.setPeekHeight(dp(this, 138));
            sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            sheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                @Override
                public void onStateChanged(@NonNull View bottomSheet, int newState) {
                    if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                        int targetPeek = dp(NearbyHospitalActivity.this, 138);
                        if (sheetBehavior != null && sheetBehavior.getPeekHeight() != targetPeek) {
                            sheetBehavior.setPeekHeight(targetPeek);
                        }
                    } else if (newState == BottomSheetBehavior.STATE_HIDDEN && (cardNavigationInfo == null || cardNavigationInfo.getVisibility() != View.VISIBLE)) {
                        sheetBehavior.setHideable(false);
                        sheetBehavior.setPeekHeight(dp(NearbyHospitalActivity.this, 138));
                        sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                    }
                }

                @Override
                public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                }
            });
        }
        applyMapControlsBottomOffset();

        cardNavigationInfo = findViewById(R.id.card_navigation_info);
        navDestinationName = findViewById(R.id.nav_destination_name);
        navEtaDistance = findViewById(R.id.nav_eta_distance);
        navAddress = findViewById(R.id.nav_address);
        navCategoryIcon = findViewById(R.id.nav_category_icon);
        navIconCard = (MaterialCardView) findViewById(R.id.nav_icon_card);
        btnCloseNavigation = findViewById(R.id.btn_close_navigation);
        btnToggle3d = findViewById(R.id.btn_toggle_3d);

        if (btnCloseNavigation != null) {
            btnCloseNavigation.setOnClickListener(v -> stopNavigation());
        }
        if (btnToggle3d != null) {
            btnToggle3d.setOnClickListener(v -> toggle3DNavigationMode());
        }

        // Setup Marker Callout Views
        markerCalloutLayout = findViewById(R.id.marker_callout_layout);
        calloutLogoRing = (MaterialCardView) findViewById(R.id.callout_logo_ring);
        calloutLogo = findViewById(R.id.callout_logo);
        calloutTitle = findViewById(R.id.callout_title);
        calloutBtnClose = findViewById(R.id.callout_btn_close);
        calloutStatus = findViewById(R.id.callout_status);
        calloutDistance = findViewById(R.id.callout_distance);
        calloutAddress = findViewById(R.id.callout_address);
        calloutBtnDirections = findViewById(R.id.callout_btn_directions);

        if (calloutBtnClose != null) {
            calloutBtnClose.setOnClickListener(v -> hideMarkerCallout());
        }
        if (calloutBtnDirections != null) {
            calloutBtnDirections.setOnClickListener(v -> {
                if (selectedCalloutHospital != null) {
                    HospitalItem target = selectedCalloutHospital;
                    hideMarkerCallout();
                    openDirections(target);
                }
            });
        }

        status = findViewById(R.id.status);
        recyclerView = findViewById(R.id.hospital_list);
        listAdapter = new HospitalAdapter();
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setAdapter(listAdapter);
            recyclerView.setHasFixedSize(false);
            recyclerView.setNestedScrollingEnabled(true);
        }

        maybePromptEnableGps();
        requestLocationIfNeeded(false);
    }

    /** Aktiviti aktif dan sedia untuk interaksi pengguna. */
    @Override
    protected void onResume() {
        super.onResume();
        if (GpsUtils.isGpsEnabled(this)) gpsDialogShown = false;
        maybePromptEnableGps();
        requestLocationIfNeeded(false);
        if (pendingSearch && PermissionUtils.hasAnyLocation(this) && GpsUtils.isGpsEnabled(this)) {
            fetchNearbyHospitals();
        }
    }

    /** Aktiviti dijeda sementara. */
    @Override
    protected void onPause() {
        super.onPause();
        stopNavigationLocationUpdates();
        if (cancellationTokenSource != null) {
            cancellationTokenSource.cancel();
            cancellationTokenSource = null;
        }
    }

    // SEKSYEN: ONDESTROY
    /** Pembersihan memori, buang listener, dan tutup sambungan. */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopNavigationLocationUpdates();
        if (navigationDirectionBitmap != null && !navigationDirectionBitmap.isRecycled()) {
            try { navigationDirectionBitmap.recycle(); } catch (Exception ignored) {}
            navigationDirectionBitmap = null;
        }
        executor.shutdownNow();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        applySavedMapType();
        map.getUiSettings().setMyLocationButtonEnabled(false);
        map.getUiSettings().setZoomControlsEnabled(false);
        map.getUiSettings().setCompassEnabled(false);
        map.getUiSettings().setMapToolbarEnabled(false);
        map.getUiSettings().setAllGesturesEnabled(true);

        LatLng fallback = new LatLng(3.1390, 101.6869); // Kuala Lumpur, Malaysia
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(fallback, 12f));

        map.setOnMarkerClickListener(marker -> {
            if (marker == null) return false;
            // Ignore click on self "You" location marker
            if (marker.equals(myLocationMarker)) return false;

            HospitalItem item = null;
            if (marker.getTag() instanceof HospitalItem) {
                item = (HospitalItem) marker.getTag();
            } else if (marker.getPosition() != null) {
                for (HospitalItem h : hospitals) {
                    if (Math.abs(h.lat - marker.getPosition().latitude) < 0.0005 &&
                        Math.abs(h.lng - marker.getPosition().longitude) < 0.0005) {
                        item = h;
                        break;
                    }
                }
            }

            if (item != null) {
                showMarkerCallout(item, marker.getPosition());
                return true;
            }
            return false;
        });

        map.setOnCameraMoveListener(this::updateCalloutPosition);

        map.setOnMapClickListener(latLng -> hideMarkerCallout());

        requestLocationIfNeeded(false);
    }

    private void applySavedMapType() {
        if (map == null) return;
        try {
            boolean satellite = UserPrefs.isMapTypeSatellite(this);
            map.setMapType(satellite ? GoogleMap.MAP_TYPE_SATELLITE : GoogleMap.MAP_TYPE_NORMAL);
            applyResQTapMapStyle(!satellite);
            map.setTrafficEnabled(UserPrefs.isMapTrafficEnabled(this));
        } catch (Exception ignored) {
        }
    }

    private void applyResQTapMapStyle(boolean enabled) {
        if (map == null) return;
        try {
            map.setMapStyle(null);
        } catch (Exception ignored) {
        }
    }

    private void applyMapControlsBottomOffset() {
        if (mapControls == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(mapControls, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int peek = 0;
            try {
                if (sheetBehavior != null) peek = sheetBehavior.getPeekHeight();
            } catch (Exception ignored) {
            }
            if (peek <= 0) peek = dp(this, 138);

            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp instanceof androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) {
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams clp = (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) lp;

                clp.bottomMargin = dp(this, 16) + peek;
                v.setLayoutParams(clp);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(mapControls);
    }

    private static int dp(Context ctx, int dp) {
        float density = ctx.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private static float dp(Context ctx, float dp) {
        return dp * ctx.getResources().getDisplayMetrics().density;
    }

    private void toggleMapType() {
        MapTypeBottomSheet.show(this, map);
    }

    private void requestLocationIfNeeded(boolean userInitiated) {
        if (map == null) return;
        if (!GpsUtils.isGpsEnabled(this)) {
            if (userInitiated || pendingSearch) maybePromptEnableGps();
            return;
        }
        if (PermissionUtils.hasAnyLocation(this)) {
            enableMyLocationAndCenter(userInitiated);
        } else {
            locationPerms.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void onLocationPermissionResult(Map<String, Boolean> result) {
        Boolean fine = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
        Boolean coarse = result.get(Manifest.permission.ACCESS_COARSE_LOCATION);
        boolean granted = Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse);
        if (!granted) {
            Toast.makeText(this, R.string.nearby_hospital_need_location, Toast.LENGTH_SHORT).show();
            return;
        }
        enableMyLocationAndCenter(true);
        if (pendingSearch) fetchNearbyHospitals();
    }

    private void enableMyLocationAndCenter(boolean userInitiated) {
        if (map == null) return;
        if (!PermissionUtils.hasAnyLocation(this)) return;
        try {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (map == null) return;
                if (location != null) {
                    currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                    if (userInitiated || !hasAutoCentered) {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f));
                        hasAutoCentered = true;
                    }
                    upsertMyLocationMarker(currentLatLng);
                    return;
                }
                cancellationTokenSource = new CancellationTokenSource();
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationTokenSource.getToken())
                        .addOnSuccessListener(current -> {
                            if (current == null || map == null) return;
                            currentLatLng = new LatLng(current.getLatitude(), current.getLongitude());
                            if (userInitiated || !hasAutoCentered) {
                                map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f));
                                hasAutoCentered = true;
                            }
                            upsertMyLocationMarker(currentLatLng);
                        });
            });
        } catch (SecurityException ignored) {
        }
    }

    private void fetchNearbyHospitals() {
        if (!PermissionUtils.hasAnyLocation(this)) {
            Toast.makeText(this, R.string.nearby_hospital_need_location, Toast.LENGTH_SHORT).show();
            requestLocationIfNeeded(true);
            return;
        }
        if (!GpsUtils.isGpsEnabled(this)) {
            maybePromptEnableGps();
            return;
        }
        if (sheetBehavior != null) sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);

        if (status != null) {
            status.setText(R.string.nearby_hospital_loading);
            status.setVisibility(View.VISIBLE);
        }

        hospitals.clear();
        displayItems.clear();
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
        clearHospitalMarkers();

        if (currentLatLng != null) {
            upsertMyLocationMarker(currentLatLng);
            doNearbySearch(currentLatLng);
            return;
        }

        try {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                    upsertMyLocationMarker(currentLatLng);
                    doNearbySearch(currentLatLng);
                    return;
                }
                cancellationTokenSource = new CancellationTokenSource();
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.getToken())
                        .addOnSuccessListener(current -> {
                            if (current == null) {
                                if (status != null) status.setText(R.string.nearby_hospital_need_location);
                                return;
                            }
                            currentLatLng = new LatLng(current.getLatitude(), current.getLongitude());
                            upsertMyLocationMarker(currentLatLng);
                            doNearbySearch(currentLatLng);
                        });
            });
        } catch (SecurityException e) {
            if (status != null) status.setText(R.string.nearby_hospital_need_location);
        }
    }

    private void doNearbySearch(LatLng center) {
        String apiKey = getString(R.string.google_maps_key);
        if (apiKey == null || apiKey.contains("YOUR_GOOGLE_MAPS_API_KEY")) {
            runOnUiThread(() -> {
                if (status != null) status.setText(R.string.nearby_hospital_error_missing_api_key);
            });
            return;
        }

        final String category = currentCategory == null ? "all" : currentCategory;
        executeGooglePlacesSearch(center, apiKey, category, new ArrayList<>());
    }

    private void executeGooglePlacesSearch(LatLng center, String apiKey, String category, ArrayList<HospitalItem> initialList) {
        executor.execute(() -> {
            try {
                ArrayList<HospitalItem> combined = new ArrayList<>();
                if (initialList != null && !initialList.isEmpty()) {
                    combined.addAll(initialList);
                }
                PlacesResponse lastDenied = null;

                if ("all".equalsIgnoreCase(category)) {
                    // 1. Bomba / Fire station search (Keutamaan 1)
                    PlacesResponse fResp = fetchPlacesNearby(center, apiKey, "fire_station", null, "fire");
                    if (fResp.isDenied()) lastDenied = fResp;
                    else combined.addAll(fResp.items);

                    // 2. Hospital / Medic search (Keutamaan 2)
                    PlacesResponse hResp = fetchPlacesNearby(center, apiKey, "hospital", null, "hospital");
                    if (hResp.isDenied()) lastDenied = hResp;
                    else combined.addAll(hResp.items);

                    // 3. Polis / Police search (Keutamaan 3)
                    PlacesResponse pResp = fetchPlacesNearby(center, apiKey, "police", null, "police");
                    if (pResp.isDenied()) lastDenied = pResp;
                    else combined.addAll(pResp.items);

                    if (combined.isEmpty() && lastDenied != null) {
                        PlacesResponse finalDenied = lastDenied;
                        runOnUiThread(() -> showPlacesDenied(finalDenied));
                        return;
                    }
                    runOnUiThread(() -> applyHospitals(combined));
                } else if ("police".equalsIgnoreCase(category)) {
                    PlacesResponse resp = fetchPlacesNearby(center, apiKey, "police", null, "police");
                    if (resp.isDenied()) {
                        if (combined.isEmpty()) {
                            runOnUiThread(() -> showPlacesDenied(resp));
                            return;
                        }
                    } else {
                        combined.addAll(resp.items);
                    }
                    runOnUiThread(() -> applyHospitals(combined));
                } else if ("fire".equalsIgnoreCase(category)) {
                    PlacesResponse resp = fetchPlacesNearby(center, apiKey, "fire_station", null, "fire");
                    if (resp.isDenied()) {
                        if (combined.isEmpty()) {
                            runOnUiThread(() -> showPlacesDenied(resp));
                            return;
                        }
                    } else {
                        combined.addAll(resp.items);
                    }
                    runOnUiThread(() -> applyHospitals(combined));
                } else {
                    // Default hospital
                    PlacesResponse first = fetchPlacesNearby(center, apiKey, "hospital", null, "hospital");
                    if (first.isDenied()) {
                        if (combined.isEmpty()) {
                            runOnUiThread(() -> showPlacesDenied(first));
                            return;
                        }
                    } else if (first.isOkWithResults()) {
                        combined.addAll(first.items);
                    } else {
                        PlacesResponse fallback = fetchPlacesNearby(center, apiKey, "health", null, "hospital");
                        if (!fallback.isDenied() && fallback.isOkWithResults()) {
                            combined.addAll(fallback.items);
                        }
                    }
                    runOnUiThread(() -> applyHospitals(combined));
                }
            } catch (Exception e) {
                if (initialList != null && !initialList.isEmpty()) {
                    runOnUiThread(() -> applyHospitals(initialList));
                } else {
                    runOnUiThread(() -> {
                        if (status != null) status.setText(R.string.nearby_hospital_error_failed_load);
                    });
                }
                pendingSearch = false;
            }
        });
    }

    private static final class PlacesResponse {
        final String status;
        final String errorMessage;
        final ArrayList<HospitalItem> items;

        PlacesResponse(String status, String errorMessage, ArrayList<HospitalItem> items) {
            this.status = status == null ? "" : status;
            this.errorMessage = errorMessage == null ? "" : errorMessage;
            this.items = items == null ? new ArrayList<>() : items;
        }

        boolean isDenied() {
            return "REQUEST_DENIED".equalsIgnoreCase(status) || "INVALID_REQUEST".equalsIgnoreCase(status);
        }

        boolean isOkWithResults() {
            return "OK".equalsIgnoreCase(status) && !items.isEmpty();
        }
    }

    /** Ambil data PlacesNearby mengikut jenis dan kategori item. */
    private PlacesResponse fetchPlacesNearby(LatLng center, String apiKey, String type, String keyword, String itemCategory) throws Exception {
        String location = center.latitude + "," + center.longitude;
        StringBuilder url = new StringBuilder("https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=");
        url.append(URLEncoder.encode(location, StandardCharsets.UTF_8.name()));
        url.append("&radius=").append(SEARCH_RADIUS_METERS);
        if (type != null && !type.trim().isEmpty()) {
            url.append("&type=").append(URLEncoder.encode(type, StandardCharsets.UTF_8.name()));
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            url.append("&keyword=").append(URLEncoder.encode(keyword, StandardCharsets.UTF_8.name()));
        }
        url.append("&language=").append(URLEncoder.encode(Locale.getDefault().getLanguage(), StandardCharsets.UTF_8.name()));
        url.append("&key=").append(URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name()));

        HttpURLConnection conn = (HttpURLConnection) new URL(url.toString()).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);

        int code = conn.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
                StandardCharsets.UTF_8
        ));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();

        JSONObject root = new JSONObject(sb.toString());
        String status = root.optString("status", "");
        String error = root.optString("error_message", "");

        JSONArray results = root.optJSONArray("results");
        ArrayList<HospitalItem> found = new ArrayList<>();
        if (results != null) {
            for (int i = 0; i < results.length() && i < 15; i++) {
                JSONObject r = results.getJSONObject(i);
                String defaultName = "hospital".equals(itemCategory) ? getString(R.string.filter_hospital)
                        : ("police".equals(itemCategory) ? getString(R.string.filter_police) : getString(R.string.filter_fire));
                String name = r.optString("name", defaultName);
                String address = r.optString("vicinity", "");
                JSONObject geometry = r.optJSONObject("geometry");
                JSONObject loc = geometry == null ? null : geometry.optJSONObject("location");
                if (loc == null) continue;
                double lat = loc.optDouble("lat", 0);
                double lng = loc.optDouble("lng", 0);
                found.add(new HospitalItem(name, address, lat, lng, itemCategory));
            }
        }
        return new PlacesResponse(status, error, found);
    }

    /** Paparkan PlacesDenied. */
    private void showPlacesDenied(PlacesResponse response) {
        if (status != null) status.setVisibility(View.VISIBLE);
        String msg = "Places API request denied.";
        if (!response.errorMessage.trim().isEmpty()) {
            msg += "\n" + response.errorMessage.trim();
        } else if (!response.status.trim().isEmpty()) {
            msg += "\nStatus: " + response.status.trim();
        }
        msg += "\n\nFix: Enable \"Places API\" in Google Cloud + use a Web Service key (not Android-restricted).";
        if (status != null) status.setText(msg);
        hospitals.clear();
        displayItems.clear();
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
        clearHospitalMarkers();
        pendingSearch = false;
    }

    private void applyHospitals(ArrayList<HospitalItem> found) {
        hospitals.clear();
        displayItems.clear();
        if (found == null || found.isEmpty()) {
            if (status != null) {
                status.setText(R.string.nearby_hospital_no_results);
                status.setVisibility(View.VISIBLE);
            }
            if (listAdapter != null) listAdapter.notifyDataSetChanged();
            clearHospitalMarkers();
            pendingSearch = false;
            return;
        }

        // 1. Kira jarak & buang duplikasi
        ArrayList<HospitalItem> allCandidates = new ArrayList<>();
        java.util.HashSet<String> seenKeys = new java.util.HashSet<>();
        for (HospitalItem h : found) {
            if (currentLatLng != null) {
                h.distanceMeters = distanceMeters(currentLatLng, h.lat, h.lng);
            }
            String key = (h.name == null ? "" : h.name.trim().toLowerCase()) + "_" + Math.round(h.lat * 1000) + "_" + Math.round(h.lng * 1000);
            if (seenKeys.add(key)) {
                allCandidates.add(h);
            }
        }

        // 2. Susun SEMUA calon mengikut jarak paling dekat dahulu
        Collections.sort(allCandidates, (a, b) -> {
            double distA = a.distanceMeters < 0 ? Double.MAX_VALUE : a.distanceMeters;
            double distB = b.distanceMeters < 0 ? Double.MAX_VALUE : b.distanceMeters;
            return Double.compare(distA, distB);
        });

        // 3. Tapis radius pintar: utamakan 5 km, jika sedikit kembangkan ke 10 km
        ArrayList<HospitalItem> radiusFiltered = new ArrayList<>();
        for (HospitalItem h : allCandidates) {
            if (h.distanceMeters >= 0 && h.distanceMeters <= 5000) {
                radiusFiltered.add(h);
            }
        }
        if (radiusFiltered.size() < 3) {
            radiusFiltered.clear();
            for (HospitalItem h : allCandidates) {
                if (h.distanceMeters >= 0 && h.distanceMeters <= 10000) {
                    radiusFiltered.add(h);
                }
            }
        }
        if (radiusFiltered.isEmpty()) {
            int take = Math.min(3, allCandidates.size());
            for (int i = 0; i < take; i++) {
                radiusFiltered.add(allCandidates.get(i));
            }
        }

        // 4. Hadkan kepada senarai terdekat sahaja (Top nearest)
        ArrayList<HospitalItem> topNearest = new ArrayList<>();
        if ("all".equalsIgnoreCase(currentCategory)) {
            int fireCount = 0;
            int hospitalCount = 0;
            int policeCount = 0;
            for (HospitalItem h : radiusFiltered) {
                if ("fire".equalsIgnoreCase(h.category)) {
                    if (fireCount < MAX_RESULTS_ALL_PER_CAT) {
                        topNearest.add(h);
                        fireCount++;
                    }
                } else if ("police".equalsIgnoreCase(h.category)) {
                    if (policeCount < MAX_RESULTS_ALL_PER_CAT) {
                        topNearest.add(h);
                        policeCount++;
                    }
                } else {
                    if (hospitalCount < MAX_RESULTS_ALL_PER_CAT) {
                        topNearest.add(h);
                        hospitalCount++;
                    }
                }
            }
        } else {
            int count = 0;
            for (HospitalItem h : radiusFiltered) {
                topNearest.add(h);
                count++;
                if (count >= MAX_RESULTS_SINGLE_CAT) break;
            }
        }

        // Susun mengikut keutamaan kategori (Bomba -> Hospital -> Polis) jika mod "all", dan jarak terdekat
        Collections.sort(topNearest, (a, b) -> {
            if ("all".equalsIgnoreCase(currentCategory)) {
                int pA = getCategoryPriority(a.category);
                int pB = getCategoryPriority(b.category);
                if (pA != pB) return Integer.compare(pA, pB);
            }
            double distA = a.distanceMeters < 0 ? Double.MAX_VALUE : a.distanceMeters;
            double distB = b.distanceMeters < 0 ? Double.MAX_VALUE : b.distanceMeters;
            return Double.compare(distA, distB);
        });

        hospitals.addAll(topNearest);
        rebuildDisplayItems();
        if (listAdapter != null) listAdapter.notifyDataSetChanged();

        if (hospitals.isEmpty()) {
            if (status != null) {
                status.setText(R.string.nearby_hospital_no_results);
                status.setVisibility(View.VISIBLE);
            }
        } else {
            if (status != null) status.setVisibility(View.GONE);
        }

        if (map != null) {
            clearHospitalMarkers();
            if (currentLatLng != null) upsertMyLocationMarker(currentLatLng);
            for (HospitalItem h : hospitals) {
                BitmapDescriptor iconDesc = getServiceMarkerDescriptor(h.category);
                Marker m = map.addMarker(new MarkerOptions()
                        .position(new LatLng(h.lat, h.lng))
                        .title(h.name)
                        .snippet(h.address)
                        .icon(iconDesc)
                        .anchor(0.5f, 0.94f));
                if (m != null) {
                    m.setTag(h);
                    hospitalMarkers.add(m);
                }
            }
            if (currentLatLng != null) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f));
            }
        }
        if (sheetBehavior != null) {
            sheetBehavior.setPeekHeight(dp(this, 138));
            sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
        pendingSearch = false;
    }

    /**
     * Paparkan kad maklumat timbul (Callout Popup) di atas pin penanda mengikut gaya admin dashboard
     * apabila marker ditekan, dan bukannya terus membuka navigasi arah.
     */
    private void showMarkerCallout(HospitalItem item, LatLng pos) {
        if (item == null || pos == null || markerCalloutLayout == null) return;
        selectedCalloutHospital = item;
        selectedCalloutLatLng = pos;

        if (calloutTitle != null) {
            calloutTitle.setText(item.name != null ? item.name : "");
        }
        if (calloutAddress != null) {
            if (item.address != null && !item.address.trim().isEmpty()) {
                calloutAddress.setText(item.address.trim());
                calloutAddress.setVisibility(View.VISIBLE);
            } else {
                calloutAddress.setVisibility(View.GONE);
            }
        }

        if (calloutDistance != null) {
            if (item.distanceMeters >= 0) {
                calloutDistance.setText(formatDistance(item.distanceMeters));
            } else if (currentLatLng != null) {
                double d = distanceMeters(currentLatLng, item.lat, item.lng);
                calloutDistance.setText(formatDistance(d));
            } else {
                calloutDistance.setText("—");
            }
        }

        int logoRes = R.drawable.logo_kkm;
        int strokeColor = Color.parseColor("#E53935");
        int bgColor = Color.parseColor("#FFF0F2");

        if ("police".equalsIgnoreCase(item.category)) {
            logoRes = R.drawable.logo_pdrm;
            strokeColor = Color.parseColor("#1565C0");
            bgColor = Color.parseColor("#F0F4F9");
        } else if ("fire".equalsIgnoreCase(item.category)) {
            logoRes = R.drawable.logo_bomba;
            strokeColor = Color.parseColor("#E65100");
            bgColor = Color.parseColor("#FFF7ED");
        }

        if (calloutLogo != null) {
            calloutLogo.setImageTintList(null);
            calloutLogo.setImageResource(logoRes);
        }
        if (calloutLogoRing != null) {
            calloutLogoRing.setStrokeColor(strokeColor);
            calloutLogoRing.setCardBackgroundColor(bgColor);
        }

        markerCalloutLayout.setVisibility(View.VISIBLE);
        markerCalloutLayout.setAlpha(0f);
        markerCalloutLayout.animate().alpha(1f).setDuration(180).start();

        updateCalloutPosition();

        if (map != null) {
            map.animateCamera(CameraUpdateFactory.newLatLng(pos));
        }
    }

    private void hideMarkerCallout() {
        if (markerCalloutLayout != null && markerCalloutLayout.getVisibility() == View.VISIBLE) {
            markerCalloutLayout.animate().alpha(0f).setDuration(120).withEndAction(() -> {
                markerCalloutLayout.setVisibility(View.GONE);
                selectedCalloutLatLng = null;
                selectedCalloutHospital = null;
            }).start();
        }
    }

    private void updateCalloutPosition() {
        if (map == null || markerCalloutLayout == null || selectedCalloutLatLng == null) return;
        if (markerCalloutLayout.getVisibility() != View.VISIBLE) return;

        try {
            android.graphics.Point screenPt = map.getProjection().toScreenLocation(selectedCalloutLatLng);
            if (screenPt == null) return;

            int w = markerCalloutLayout.getWidth();
            int h = markerCalloutLayout.getHeight();
            if (w == 0 || h == 0) {
                markerCalloutLayout.measure(
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                );
                w = markerCalloutLayout.getMeasuredWidth();
                h = markerCalloutLayout.getMeasuredHeight();
            }

            // Pin marker adalah 50dp tinggi + 3dp bayang, tip anchor di (0.5f, 0.94f).
            // Segitiga penunjuk callout berada tepat di atas kepala pin marker.
            float pinTopOffset = dp(this, 54);
            float targetX = screenPt.x - (w / 2f);
            float targetY = screenPt.y - pinTopOffset - h;

            markerCalloutLayout.setX(targetX);
            markerCalloutLayout.setY(targetY);
        } catch (Exception ignored) {
        }
    }

    /**
     * In-App Direction Navigation.
     * Melukis laluan terus di atas peta Google Map dalam aplikasi ResQTap tanpa melencong ke Google Maps luar.
     */
    private void openDirections(HospitalItem item) {
        if (item == null) return;
        hideMarkerCallout();
        activeNavHospital = item;

        // 1. Sorokkan terus bottom sheet senarai supaya peta luas sepenuhnya dan tidak terlindung
        if (sheetBehavior != null) {
            sheetBehavior.setHideable(true);
            sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        }
        if (bottomSheetView != null) {
            bottomSheetView.setVisibility(View.GONE);
        }
        updateMapControlsForNavigation(true);

        // 2. Paparkan kad maklumat navigasi di bawah skrin
        if (cardNavigationInfo != null) {
            cardNavigationInfo.setVisibility(View.VISIBLE);
            if (navDestinationName != null) navDestinationName.setText(item.name);
            if (navAddress != null) navAddress.setText(item.address == null ? "" : item.address);
            if (navEtaDistance != null) navEtaDistance.setText(R.string.nav_calculating_route);

            int iconRes = R.drawable.logo_kkm;
            int bgTint = Color.parseColor("#FFF0F2");

            if ("police".equalsIgnoreCase(item.category)) {
                iconRes = R.drawable.logo_pdrm;
                bgTint = Color.parseColor("#F0F4F9");
            } else if ("fire".equalsIgnoreCase(item.category)) {
                iconRes = R.drawable.logo_bomba;
                bgTint = Color.parseColor("#FFF7ED");
            }

            if (navCategoryIcon != null) {
                navCategoryIcon.setImageTintList(null);
                navCategoryIcon.setImageResource(iconRes);
            }
            if (navIconCard != null) {
                navIconCard.setCardBackgroundColor(bgTint);
            }
        }

        // 3. Pastikan koordinat pengguna tersedia
        LatLng target = new LatLng(item.lat, item.lng);
        if (currentLatLng == null) {
            if (map != null) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 16f));
            }
            Toast.makeText(this, R.string.nav_getting_gps, Toast.LENGTH_SHORT).show();
            requestLocationIfNeeded(true);
            return;
        }

        // 4. Tukar serta-merta ikon profil pengguna kepada Direction Icon dan mulakan tracking
        currentNavBearing = calculateBearing(currentLatLng, target);
        upsertMyLocationMarker(currentLatLng);
        startNavigationLocationUpdates();

        // 5. Lukis laluan navigasi polyline secara in-app
        fetchAndDrawInAppRoute(currentLatLng, target, item);
    }

    private void fetchAndDrawInAppRoute(LatLng origin, LatLng dest, HospitalItem item) {
        executor.execute(() -> {
            RouteResult result = tryFetchOsrmRoute(origin, dest);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || map == null) return;

                clearNavigationRoute();

                ArrayList<LatLng> pts = new ArrayList<>();
                int routeColor = ContextCompat.getColor(NearbyHospitalActivity.this, R.color.brand_primary);
                if ("police".equalsIgnoreCase(item.category)) {
                    routeColor = Color.parseColor("#1E88E5");
                } else if ("fire".equalsIgnoreCase(item.category)) {
                    routeColor = Color.parseColor("#FB8C00");
                }

                if (result != null && result.points != null && result.points.size() >= 2) {
                    pts.addAll(result.points);
                    if (navEtaDistance != null) {
                        int min = (int) Math.round(result.durationSeconds / 60.0);
                        if (min < 1) min = 1;
                        double km = result.distanceMeters / 1000.0;
                        String distStr = km < 1.0 ? String.format(Locale.US, "%d m", Math.round(result.distanceMeters)) : String.format(Locale.US, "%.1f km", km);
                        navEtaDistance.setText(min + " min • " + distStr);
                    }
                } else {
                    pts.add(origin);
                    pts.add(dest);
                    if (navEtaDistance != null) {
                        double dist = distanceMeters(origin, dest.latitude, dest.longitude);
                        int min = (int) Math.max(1, Math.round((dist / 1000.0) / 40.0 * 60.0));
                        String distStr = dist < 1000 ? String.format(Locale.US, "%d m", Math.round(dist)) : String.format(Locale.US, "%.1f km", dist / 1000.0);
                        navEtaDistance.setText(min + " min • " + distStr);
                    }
                }

                // Shadow line
                PolylineOptions shadowOptions = new PolylineOptions()
                        .addAll(pts)
                        .width(dp(NearbyHospitalActivity.this, 8))
                        .color(Color.argb(80, 0, 0, 0))
                        .startCap(new RoundCap())
                        .endCap(new RoundCap())
                        .jointType(JointType.ROUND)
                        .zIndex(20f);
                routeShadowPolyline = map.addPolyline(shadowOptions);

                // Main route line
                PolylineOptions routeOptions = new PolylineOptions()
                        .addAll(pts)
                        .width(dp(NearbyHospitalActivity.this, 6))
                        .color(routeColor)
                        .startCap(new RoundCap())
                        .endCap(new RoundCap())
                        .jointType(JointType.ROUND)
                        .zIndex(21f);
                routePolyline = map.addPolyline(routeOptions);

                // Save current points for toggle and updates
                currentRoutePoints.clear();
                currentRoutePoints.addAll(pts);

                // Default to 3D Navigation mode
                isNavigating3D = true;
                updateToggle3DIcon();
                animateTo3DNavigation(origin, pts);
            });
        });
    }

    private void animateTo3DNavigation(LatLng userPos, List<LatLng> points) {
        if (map == null || userPos == null) return;
        try {
            float bearing = 0f;
            LatLng startPt = userPos;
            LatLng nextPt = (points != null && !points.isEmpty()) ? points.get(0) : null;

            if (points != null && points.size() > 1 && nextPt != null) {
                if (Math.abs(startPt.latitude - nextPt.latitude) < 0.00001 &&
                    Math.abs(startPt.longitude - nextPt.longitude) < 0.00001) {
                    nextPt = points.get(1);
                }
            }

            if (nextPt != null) {
                bearing = calculateBearing(startPt, nextPt);
            }
            currentNavBearing = bearing;
            upsertMyLocationMarker(userPos);

            // Pad camera so route ahead is nicely visible above bottom sheet
            int padBottom = dp(this, 140);
            map.setPadding(0, 0, 0, padBottom);

            com.google.android.gms.maps.model.CameraPosition cameraPosition = new com.google.android.gms.maps.model.CameraPosition.Builder()
                    .target(userPos)
                    .zoom(18.5f)
                    .bearing(bearing)
                    .tilt(60f)
                    .build();

            map.stopAnimation();
            map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 1200, null);
        } catch (Exception ignored) {
        }
    }

    private void zoomToFullRoute() {
        if (map == null || currentRoutePoints.isEmpty()) return;
        try {
            LatLngBounds.Builder b = new LatLngBounds.Builder();
            for (LatLng p : currentRoutePoints) {
                b.include(p);
            }
            int padLeft = dp(this, 40);
            int padTop = dp(this, 120);
            int padRight = dp(this, 40);
            int padBottom = dp(this, 160);
            map.setPadding(padLeft, padTop, padRight, padBottom);
            int padding = dp(this, 40);

            com.google.android.gms.maps.model.CameraPosition overviewPos = new com.google.android.gms.maps.model.CameraPosition.Builder()
                    .target(b.build().getCenter())
                    .zoom(map.getCameraPosition().zoom)
                    .bearing(0f)
                    .tilt(0f)
                    .build();
            map.stopAnimation();
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), padding));
        } catch (Exception e) {
            if (activeNavHospital != null) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(activeNavHospital.lat, activeNavHospital.lng), 15f));
            }
        }
    }

    private void toggle3DNavigationMode() {
        if (map == null) return;
        isNavigating3D = !isNavigating3D;
        updateToggle3DIcon();
        if (isNavigating3D) {
            LatLng origin = currentLatLng != null ? currentLatLng : (!currentRoutePoints.isEmpty() ? currentRoutePoints.get(0) : null);
            if (origin != null) {
                animateTo3DNavigation(origin, currentRoutePoints);
            }
        } else {
            zoomToFullRoute();
        }
    }

    private void updateToggle3DIcon() {
        if (btnToggle3d == null) return;
        if (isNavigating3D) {
            btnToggle3d.setIconResource(R.drawable.ic_route_overview);
            btnToggle3d.setContentDescription(getString(R.string.nav_view_2d));
        } else {
            btnToggle3d.setIconResource(R.drawable.ic_navigation_3d);
            btnToggle3d.setContentDescription(getString(R.string.nav_view_3d));
        }
    }

    private float calculateBearing(LatLng start, LatLng end) {
        double lat1 = Math.toRadians(start.latitude);
        double lng1 = Math.toRadians(start.longitude);
        double lat2 = Math.toRadians(end.latitude);
        double lng2 = Math.toRadians(end.longitude);

        double dLng = lng2 - lng1;

        double y = Math.sin(dLng) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (float) ((bearing + 360) % 360);
    }

    private void stopNavigation() {
        stopNavigationLocationUpdates();
        clearNavigationRoute();
        currentRoutePoints.clear();
        activeNavHospital = null;
        isNavigating3D = false;
        currentNavBearing = 0f;
        if (currentLatLng != null) {
            upsertMyLocationMarker(currentLatLng);
        }
        if (cardNavigationInfo != null) {
            cardNavigationInfo.setVisibility(View.GONE);
        }
        if (bottomSheetView != null) {
            bottomSheetView.setVisibility(View.VISIBLE);
            if (sheetBehavior != null) {
                sheetBehavior.setHideable(false);
                sheetBehavior.setPeekHeight(dp(this, 138));
                sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        }
        updateMapControlsForNavigation(false);
        if (map != null) {
            map.setPadding(0, 0, 0, 0);
            if (currentLatLng != null) {
                com.google.android.gms.maps.model.CameraPosition resetPos = new com.google.android.gms.maps.model.CameraPosition.Builder()
                        .target(currentLatLng)
                        .zoom(15f)
                        .bearing(0f)
                        .tilt(0f)
                        .build();
                map.animateCamera(CameraUpdateFactory.newCameraPosition(resetPos));
            }
        }
    }

    private void updateMapControlsForNavigation(boolean isNavigating) {
        if (mapControls == null) return;
        if (isNavigating) {
            mapControls.setVisibility(View.GONE);
        } else {
            mapControls.setVisibility(View.VISIBLE);
            ViewGroup.LayoutParams lp = mapControls.getLayoutParams();
            if (lp instanceof androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) {
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams clp = (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) lp;
                int peek = 0;
                try {
                    if (sheetBehavior != null) peek = sheetBehavior.getPeekHeight();
                } catch (Exception ignored) {}
                if (peek <= 0) peek = dp(this, 138);
                clp.bottomMargin = dp(this, 16) + peek;
                mapControls.setLayoutParams(clp);
            }
        }
    }

    private void clearNavigationRoute() {
        if (routePolyline != null) {
            try { routePolyline.remove(); } catch (Exception ignored) {}
            routePolyline = null;
        }
        if (routeShadowPolyline != null) {
            try { routeShadowPolyline.remove(); } catch (Exception ignored) {}
            routeShadowPolyline = null;
        }
    }

    private void launchExternalGoogleMaps(HospitalItem item) {
        if (item == null) return;
        Uri uri = Uri.parse("google.navigation:q=" + item.lat + "," + item.lng + "&mode=d");
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage("com.google.android.apps.maps");
        try {
            startActivity(intent);
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        }
    }

    private static final class RouteResult {
        double distanceMeters = 0;
        double durationSeconds = 0;
        final ArrayList<LatLng> points = new ArrayList<>();
    }

    private RouteResult tryFetchOsrmRoute(LatLng origin, LatLng dest) {
        String urlStr = "https://router.project-osrm.org/route/v1/driving/"
                + origin.longitude + "," + origin.latitude + ";"
                + dest.longitude + "," + dest.latitude
                + "?overview=full&geometries=geojson";
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "ResQTap/1.0 (Android)");
            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    return parseOsrmResponse(sb.toString());
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private RouteResult parseOsrmResponse(String jsonStr) {
        RouteResult res = new RouteResult();
        try {
            JSONObject root = new JSONObject(jsonStr);
            if ("Ok".equalsIgnoreCase(root.optString("code"))) {
                JSONArray routes = root.optJSONArray("routes");
                if (routes != null && routes.length() > 0) {
                    JSONObject r = routes.getJSONObject(0);
                    res.distanceMeters = r.optDouble("distance", 0);
                    res.durationSeconds = r.optDouble("duration", 0);
                    JSONObject geom = r.optJSONObject("geometry");
                    if (geom != null) {
                        JSONArray coords = geom.optJSONArray("coordinates");
                        if (coords != null) {
                            for (int i = 0; i < coords.length(); i++) {
                                JSONArray pt = coords.getJSONArray(i);
                                res.points.add(new LatLng(pt.getDouble(1), pt.getDouble(0)));
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return res;
    }

    /** Padam atau bersihkan HospitalMarkers. */
    private void clearHospitalMarkers() {
        hideMarkerCallout();
        for (Marker m : hospitalMarkers) {
            try {
                if (m != null) m.remove();
            } catch (Exception ignored) {
            }
        }
        hospitalMarkers.clear();
    }

    private boolean isNavigating() {
        return activeNavHospital != null;
    }

    private void upsertMyLocationMarker(LatLng pos) {
        if (map == null || pos == null) return;
        boolean navigating = isNavigating();
        Bitmap icon = navigating ? getNavigationDirectionBitmap() : buildSelfMarkerBitmap();
        float rot = navigating ? currentNavBearing : 0f;

        if (myLocationMarker == null) {
            myLocationMarker = map.addMarker(new MarkerOptions()
                    .position(pos)
                    .title("You")
                    .icon(BitmapDescriptorFactory.fromBitmap(icon))
                    .anchor(0.5f, 0.5f)
                    .flat(navigating)
                    .rotation(rot)
                    .zIndex(navigating ? 100f : 10f));
        } else {
            myLocationMarker.setPosition(pos);
            myLocationMarker.setIcon(BitmapDescriptorFactory.fromBitmap(icon));
            myLocationMarker.setAnchor(0.5f, 0.5f);
            myLocationMarker.setFlat(navigating);
            myLocationMarker.setRotation(rot);
            myLocationMarker.setZIndex(navigating ? 100f : 10f);
        }
    }

    private Bitmap getNavigationDirectionBitmap() {
        if (navigationDirectionBitmap == null || navigationDirectionBitmap.isRecycled()) {
            navigationDirectionBitmap = buildNavigationDirectionBitmap(this);
        }
        return navigationDirectionBitmap;
    }

    /**
     * 3D Navigation Direction Arrow / Chevron Icon matching modern navigation GPS style.
     * Mengubah ikon profil pengguna kepada anak panah panduan arah 3D (cyan / teal dengan bingkai putih dan bayang asas).
     */
    private static Bitmap buildNavigationDirectionBitmap(Context ctx) {
        final int sizePx = dp(ctx, 56);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        float cx = sizePx / 2f;
        float cy = sizePx / 2f;

        // 1. Bayang piring asas (Soft circular ground shadow halo di atas jalan raya)
        Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        haloPaint.setColor(Color.parseColor("#38475569"));
        haloPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, dp(ctx, 22f), haloPaint);

        Paint haloInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        haloInnerPaint.setColor(Color.parseColor("#14000000"));
        haloInnerPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, dp(ctx, 18f), haloInnerPaint);

        float w = dp(ctx, 30f);
        float h = dp(ctx, 32f);
        float notch = dp(ctx, 9.5f);

        // 2. 3D Bevel Bawah (Extrusion / ketebalan bawah memberi ilusi 3D tactile)
        Path bevelPath = createChevronPath(cx, cy + dp(ctx, 2.2f), w, h, notch);
        Paint bevelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bevelPaint.setColor(Color.parseColor("#80DEEA")); // Bayang cyan-mint
        bevelPaint.setStyle(Paint.Style.FILL);
        bevelPaint.setPathEffect(new CornerPathEffect(dp(ctx, 5f)));
        canvas.drawPath(bevelPath, bevelPaint);

        // 3. Bingkai Putih Timbul 3D (Thick rounded white frame)
        Path outerPath = createChevronPath(cx, cy, w, h, notch);
        Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePaint.setColor(Color.WHITE);
        whitePaint.setStyle(Paint.Style.FILL);
        whitePaint.setPathEffect(new CornerPathEffect(dp(ctx, 5f)));
        whitePaint.setShadowLayer(dp(ctx, 2.5f), 0f, dp(ctx, 1.2f), 0x33000000);
        canvas.drawPath(outerPath, whitePaint);

        // 4. Badan Utama Anak Panah (Vibrant Cyan / Turquoise Gradient)
        float innerW = w - dp(ctx, 6.2f);
        float innerH = h - dp(ctx, 6.2f);
        float innerNotch = notch - dp(ctx, 1.8f);
        Path innerPath = createChevronPath(cx, cy - dp(ctx, 0.4f), innerW, innerH, innerNotch);
        Paint cyanPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cyanPaint.setStyle(Paint.Style.FILL);
        cyanPaint.setPathEffect(new CornerPathEffect(dp(ctx, 3.8f)));

        LinearGradient gradient = new LinearGradient(
                cx, cy - (innerH / 2f),
                cx, cy + (innerH / 2f),
                Color.parseColor("#00D8F6"),
                Color.parseColor("#00ACC1"),
                Shader.TileMode.CLAMP
        );
        cyanPaint.setShader(gradient);
        canvas.drawPath(innerPath, cyanPaint);

        // 5. Kilauan Refleksi Atas (Glossy highlight)
        Path highlightPath = new Path();
        float topY = cy - (innerH / 2f) + dp(ctx, 1.5f);
        highlightPath.moveTo(cx, topY);
        highlightPath.lineTo(cx + (innerW * 0.28f), cy - dp(ctx, 2f));
        highlightPath.lineTo(cx - (innerW * 0.28f), cy - dp(ctx, 2f));
        highlightPath.close();

        Paint hlPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hlPaint.setColor(Color.argb(85, 255, 255, 255));
        hlPaint.setStyle(Paint.Style.FILL);
        hlPaint.setPathEffect(new CornerPathEffect(dp(ctx, 2.5f)));
        canvas.drawPath(highlightPath, hlPaint);

        return bitmap;
    }

    private static Path createChevronPath(float cx, float cy, float width, float height, float notchDepth) {
        Path path = new Path();
        float topY = cy - (height / 2f);
        float bottomY = cy + (height / 2f);
        float leftX = cx - (width / 2f);
        float rightX = cx + (width / 2f);
        float notchY = bottomY - notchDepth;

        path.moveTo(cx, topY);
        path.lineTo(rightX, bottomY);
        path.lineTo(cx, notchY);
        path.lineTo(leftX, bottomY);
        path.close();
        return path;
    }

    private void startNavigationLocationUpdates() {
        if (!PermissionUtils.hasAnyLocation(this)) return;
        if (navLocationCallback != null) return;

        navLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult == null || !isNavigating() || map == null) return;
                Location loc = locationResult.getLastLocation();
                if (loc == null) return;

                currentLatLng = new LatLng(loc.getLatitude(), loc.getLongitude());
                float bearing = (loc.hasBearing() && loc.getSpeed() > 0.5f) ? loc.getBearing() : currentNavBearing;

                if (!currentRoutePoints.isEmpty()) {
                    bearing = computeBearingAlongRoute(currentLatLng, currentRoutePoints, bearing);
                }
                currentNavBearing = bearing;
                upsertMyLocationMarker(currentLatLng);

                if (isNavigating3D) {
                    com.google.android.gms.maps.model.CameraPosition cameraPosition = new com.google.android.gms.maps.model.CameraPosition.Builder()
                            .target(currentLatLng)
                            .zoom(18.5f)
                            .bearing(currentNavBearing)
                            .tilt(60f)
                            .build();
                    map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 900, null);
                }
            }
        };

        try {
            LocationRequest req = new LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY, 2000L
            ).setMinUpdateIntervalMillis(1000L).build();

            fusedLocationClient.requestLocationUpdates(req, navLocationCallback, android.os.Looper.getMainLooper());
        } catch (SecurityException ignored) {
        }
    }

    private void stopNavigationLocationUpdates() {
        if (navLocationCallback != null && fusedLocationClient != null) {
            try {
                fusedLocationClient.removeLocationUpdates(navLocationCallback);
            } catch (Exception ignored) {}
            navLocationCallback = null;
        }
    }

    private float computeBearingAlongRoute(LatLng currentPos, List<LatLng> points, float fallbackBearing) {
        if (points == null || points.isEmpty() || currentPos == null) return fallbackBearing;

        int closestIdx = 0;
        double minDistance = Double.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            double d = distanceMeters(currentPos, points.get(i).latitude, points.get(i).longitude);
            if (d < minDistance) {
                minDistance = d;
                closestIdx = i;
            }
        }

        int targetIdx = Math.min(closestIdx + 1, points.size() - 1);
        if (targetIdx == closestIdx && targetIdx > 0) {
            return calculateBearing(points.get(targetIdx - 1), points.get(targetIdx));
        }

        LatLng targetPt = points.get(targetIdx);
        double dToTarget = distanceMeters(currentPos, targetPt.latitude, targetPt.longitude);
        if (dToTarget > 2.0) {
            return calculateBearing(currentPos, targetPt);
        }
        return fallbackBearing;
    }

    private void maybePromptEnableGps() {
        if (gpsDialogShown) return;
        if (GpsUtils.isGpsEnabled(this)) return;
        gpsDialogShown = true;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.nearby_hospital_enable_location_title)
                .setMessage(R.string.nearby_hospital_enable_location_msg)
                .setPositiveButton(R.string.nearby_hospital_enable_location_action, (d, which) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton(R.string.cancel, (d, which) -> d.dismiss())
                .show();
    }

    private static double distanceMeters(LatLng from, double toLat, double toLng) {
        if (from == null) return -1d;
        try {
            float[] out = new float[1];
            Location.distanceBetween(from.latitude, from.longitude, toLat, toLng, out);
            return out[0];
        } catch (Exception ignored) {
            return -1d;
        }
    }

    private String formatDistance(double meters) {
        if (meters < 0) return "";
        if (meters < 1000) return String.format(Locale.getDefault(), "%.0f m", meters);
        return String.format(Locale.getDefault(), "%.1f km", (meters / 1000d));
    }

    private Bitmap buildSelfMarkerBitmap() {
        Bitmap b = decodeBase64Avatar(myPhotoB64);
        if (b != null) return buildProfileMarkerBitmapFromPhoto(this, b);
        return buildProfileMarkerBitmap(this, myPhotoUri);
    }

    private static Bitmap decodeBase64Avatar(String b64) {
        String s = String.valueOf(b64 == null ? "" : b64).trim();
        if (s.isEmpty()) return null;
        try {
            byte[] bytes = android.util.Base64.decode(s, android.util.Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Bitmap buildProfileMarkerBitmap(Context ctx, String photoUri) {
        final int sizePx = dp(ctx, 44);
        final int borderPx = dp(ctx, 3);
        final int shadowPx = dp(ctx, 4);

        Bitmap src = loadProfileBitmap(ctx, photoUri);
        if (src == null) {
            return defaultAvatarMarkerBitmap(ctx, sizePx, borderPx, shadowPx);
        }
        Bitmap scaled = Bitmap.createScaledBitmap(src, sizePx, sizePx, true);
        return circularWithBorderAndShadow(scaled, borderPx, shadowPx);
    }

    private static Bitmap buildProfileMarkerBitmapFromPhoto(Context ctx, Bitmap src) {
        final int sizePx = dp(ctx, 44);
        final int borderPx = dp(ctx, 3);
        final int shadowPx = dp(ctx, 4);
        if (src == null) return defaultAvatarMarkerBitmap(ctx, sizePx, borderPx, shadowPx);
        Bitmap scaled = Bitmap.createScaledBitmap(src, sizePx, sizePx, true);
        return circularWithBorderAndShadow(scaled, borderPx, shadowPx);
    }

    private static Bitmap loadProfileBitmap(Context ctx, String photoUri) {
        String u = String.valueOf(photoUri == null ? "" : photoUri).trim();
        if (u.isEmpty()) return null;
        try {
            Uri uri = Uri.parse(u);
            try (java.io.InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                return BitmapFactory.decodeStream(in);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Bitmap bitmapFromDrawable(Context ctx, int resId, int w, int h) {
        try {
            android.graphics.drawable.Drawable d = ContextCompat.getDrawable(ctx, resId);
            if (d == null) return null;
            try {
                d = d.mutate();
            } catch (Exception ignored) {
            }
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, w, h);
            d.draw(c);
            return b;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Bitmap defaultAvatarMarkerBitmap(Context ctx, int sizePx, int borderPx, int shadowPx) {
        int outSize = sizePx + (shadowPx * 2);
        Bitmap out = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        float cx = outSize / 2f;
        float cy = outSize / 2f;
        float radius = sizePx / 2f;

        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(0xFFFFFFFF);
        shadow.setStyle(Paint.Style.FILL);
        shadow.setShadowLayer(shadowPx, 0f, shadowPx * 0.75f, 0x55000000);
        canvas.drawCircle(cx, cy, radius, shadow);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(0xFF0B1220);
        fill.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, radius - (shadowPx * 0.5f), fill);

        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(0xFFFFFFFF);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(borderPx);
        canvas.drawCircle(cx, cy, radius - (shadowPx * 0.5f) - (borderPx / 2f), border);

        int iconSize = Math.round(sizePx * 0.72f);
        Bitmap icon = bitmapFromDrawable(ctx, R.drawable.ic_avatar, iconSize, iconSize);
        if (icon != null) {
            float left = cx - (icon.getWidth() / 2f);
            float top = cy - (icon.getHeight() / 2f);
            canvas.drawBitmap(icon, left, top, new Paint(Paint.ANTI_ALIAS_FLAG));
            icon.recycle();
        }
        return out;
    }

    private static Bitmap circularWithBorderAndShadow(Bitmap src, int borderPx, int shadowPx) {
        int size = Math.min(src.getWidth(), src.getHeight());
        int outSize = size + (shadowPx * 2);
        Bitmap out = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);

        float cx = outSize / 2f;
        float cy = outSize / 2f;
        float radius = (size / 2f);
        float contentRadius = radius - borderPx;

        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(0xFFFFFFFF);
        shadow.setStyle(Paint.Style.FILL);
        shadow.setShadowLayer(shadowPx, 0f, shadowPx * 0.75f, 0x55000000);
        canvas.drawCircle(cx, cy, radius, shadow);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(0xFFFFFFFF);
        fill.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, radius - shadowPx * 0.5f, fill);

        Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Bitmap circle = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888);
        Canvas c2 = new Canvas(circle);

        Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
        mask.setColor(0xFF000000);
        c2.drawCircle(cx, cy, contentRadius, mask);
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));

        RectF dst = new RectF(cx - contentRadius, cy - contentRadius, cx + contentRadius, cy + contentRadius);
        c2.drawBitmap(src, null, dst, mask);
        canvas.drawBitmap(circle, 0, 0, imagePaint);
        circle.recycle();
        return out;
    }

    private BitmapDescriptor hospitalMarkerDesc;
    private BitmapDescriptor policeMarkerDesc;
    private BitmapDescriptor fireMarkerDesc;

    /** Ambil atau muat BitmapDescriptor untuk pin mengikut kategori (Hospital, Polis, Bomba). */
    private BitmapDescriptor getServiceMarkerDescriptor(String category) {
        if ("police".equalsIgnoreCase(category)) {
            if (policeMarkerDesc == null) {
                policeMarkerDesc = BitmapDescriptorFactory.fromBitmap(buildServiceMarkerBitmap(this, "police"));
            }
            return policeMarkerDesc;
        } else if ("fire".equalsIgnoreCase(category)) {
            if (fireMarkerDesc == null) {
                fireMarkerDesc = BitmapDescriptorFactory.fromBitmap(buildServiceMarkerBitmap(this, "fire"));
            }
            return fireMarkerDesc;
        } else {
            if (hospitalMarkerDesc == null) {
                hospitalMarkerDesc = BitmapDescriptorFactory.fromBitmap(buildServiceMarkerBitmap(this, "hospital"));
            }
            return hospitalMarkerDesc;
        }
    }

    /** Bina Bitmap pin penanda tersuai dengan bentuk teardrop, sempadan putih berkualiti, dan ikon kategori di tengah. */
    private static Bitmap buildServiceMarkerBitmap(Context ctx, String category) {
        int widthPx = dp(ctx, 38);
        int heightPx = dp(ctx, 50);
        int shadowPx = dp(ctx, 3);

        int totalWidth = widthPx + (shadowPx * 2);
        int totalHeight = heightPx + (shadowPx * 2);

        Bitmap bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        float cx = totalWidth / 2f;
        float radius = widthPx / 2f;
        float cy = radius + shadowPx;
        float tipX = cx;
        float tipY = totalHeight - shadowPx;

        int bgColor;
        int iconRes;
        if ("police".equalsIgnoreCase(category)) {
            bgColor = Color.parseColor("#1565C0"); // Biru Polis
            iconRes = R.drawable.logo_pdrm;
        } else if ("fire".equalsIgnoreCase(category)) {
            bgColor = Color.parseColor("#E65100"); // Jingga Bomba
            iconRes = R.drawable.logo_bomba;
        } else {
            bgColor = Color.parseColor("#E53935"); // Merah Hospital
            iconRes = R.drawable.logo_kkm;
        }

        // 1. Bayang tanah (Ground shadow) halus di bawah mata pin
        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(0x35000000);
        shadowPaint.setStyle(Paint.Style.FILL);
        canvas.drawOval(new RectF(cx - dp(ctx, 8), tipY - dp(ctx, 2), cx + dp(ctx, 8), tipY + dp(ctx, 3)), shadowPaint);

        // 2. Bentuk teardrop pin map
        Path pinPath = new Path();
        float leftX = cx - radius;
        float rightX = cx + radius;
        float controlY = cy + radius * 0.45f;

        pinPath.moveTo(tipX, tipY);
        pinPath.quadTo(cx - radius * 0.85f, controlY, leftX, cy);
        pinPath.arcTo(new RectF(leftX, cy - radius, rightX, cy + radius), 180, 180, false);
        pinPath.quadTo(cx + radius * 0.85f, controlY, tipX, tipY);
        pinPath.close();

        // 3. Warna latar belakang pin
        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(bgColor);
        fillPaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(pinPath, fillPaint);

        // 4. Sempadan putih premium
        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setColor(Color.WHITE);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(ctx, 2));
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawPath(pinPath, strokePaint);

        // 5. Bulatan putih di tengah kepala pin untuk mempamerkan logo rasmi secara berkualiti
        float innerDiscRadius = radius * 0.72f;
        Paint innerDiscPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerDiscPaint.setColor(Color.WHITE);
        innerDiscPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, innerDiscRadius, innerDiscPaint);

        // 6. Ikon logo rasmi di tengah kepala pin
        int iconSize = dp(ctx, 22);
        Bitmap icon = bitmapFromDrawable(ctx, iconRes, iconSize, iconSize);
        if (icon != null) {
            float iconLeft = cx - (icon.getWidth() / 2f);
            float iconTop = cy - (icon.getHeight() / 2f);
            canvas.drawBitmap(icon, iconLeft, iconTop, null);
            icon.recycle();
        }

        return bitmap;
    }

}
