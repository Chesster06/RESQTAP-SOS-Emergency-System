package com.example.resqtap.profile;

import com.example.resqtap.R;
import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.room.FirebaseRoomClient;
import com.example.resqtap.utils.AccountDeletionUtils;
import com.example.resqtap.utils.AvatarUtils;
import com.example.resqtap.utils.DeviceIdUtils;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;

import android.app.DatePickerDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.core.content.ContextCompat;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * EditProfileActivity
 * Tampilan moden Edit Profile mengikut reka bentuk kemas:
 * Avatar tengah, Card 1 (Name, Droply benefits), Card 2 (Private Info: Email, Birthdate, Gender, Weight, Height),
 * dan Card 3 (Medical & Contact).
 */
public class EditProfileActivity extends BaseActivity {
    private static final String TAG = "EditProfileActivity";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ActivityResultLauncher<String[]> pickImage;
    private ActivityResultLauncher<Void> takeCameraPhoto;
    private ActivityResultLauncher<String> requestCameraPermission;

    private ShapeableImageView profilePhoto;
    private TextView tvValueName;
    private TextView tvValueEmail;
    private TextView tvValueDob;
    private TextView tvValueGender;
    private TextView tvValueWeight;
    private TextView tvValueHeight;
    private TextView tvValuePhone;
    private TextView tvValueBloodType;
    private TextView tvValueAllergies;
    private TextView tvValueConditions;
    private TextView tvValueAddress;
    private TextView tvUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        // SEC-08 FIX: Prevent screenshots and screen recording on sensitive PII (IC, address, emergency info)
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_profile);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        loadProfileData();
        setupListeners();
        setupImagePicker();
    }

    private void initViews() {
        profilePhoto = findViewById(R.id.profile_photo);
        tvValueName = findViewById(R.id.tv_value_name);
        tvValueEmail = findViewById(R.id.tv_value_email);
        tvValueDob = findViewById(R.id.tv_value_dob);
        tvValueGender = findViewById(R.id.tv_value_gender);
        tvValueWeight = findViewById(R.id.tv_value_weight);
        tvValueHeight = findViewById(R.id.tv_value_height);
        tvValuePhone = findViewById(R.id.tv_value_phone);
        tvValueBloodType = findViewById(R.id.tv_value_blood_type);
        tvValueAllergies = findViewById(R.id.tv_value_allergies);
        tvValueConditions = findViewById(R.id.tv_value_conditions);
        tvValueAddress = findViewById(R.id.tv_value_address);
        tvUid = findViewById(R.id.profile_uid);
    }

    private void loadProfileData() {
        String uid = DeviceIdUtils.getStableUid(this);
        String publicId = UserPrefs.getPublicId(this);
        String name = UserPrefs.getName(this);
        String email = UserPrefs.getEmail(this);
        String photoUri = UserPrefs.getPhotoUri(this);
        String photoB64 = UserPrefs.getPhotoB64(this);
        String dob = UserPrefs.getDateOfBirth(this);
        String gender = UserPrefs.getGender(this);
        String weight = UserPrefs.getWeight(this);
        String height = UserPrefs.getHeight(this);
        String phone = UserPrefs.getPhoneNumber(this);
        String bloodType = UserPrefs.getBloodType(this);
        String allergies = UserPrefs.getAllergies(this);
        String conditions = UserPrefs.getExistingConditions(this);
        String address = UserPrefs.getAddress(this);

        // Header Avatar
        AvatarUtils.applyAvatar(profilePhoto, photoB64, photoUri, R.drawable.ic_avatar);

        // UID
        String shownUid = (publicId == null || publicId.trim().isEmpty()) ? uid : publicId.trim().toUpperCase();
        tvUid.setText("UID: " + shownUid);

        // Card 1
        tvValueName.setText(isNotEmpty(name) ? name : getString(R.string.profile_name_default));

        // Card 2: Private Info
        tvValueEmail.setText(isNotEmpty(email) ? email : "Not set");
        tvValueDob.setText(isNotEmpty(dob) ? dob : "Not set");
        tvValueGender.setText(isNotEmpty(gender) ? gender : "Not set");
        tvValueWeight.setText(isNotEmpty(weight) ? weight : "Not set");
        tvValueHeight.setText(isNotEmpty(height) ? height : "Not set");

        // Card 3: Medical & Contact
        tvValuePhone.setText(isNotEmpty(phone) ? phone : "Not set");
        tvValueBloodType.setText(isNotEmpty(bloodType) ? bloodType : "Not set");
        tvValueAllergies.setText(isNotEmpty(allergies) ? allergies : "None");
        String displayedConditions = isNotEmpty(conditions) ? conditions : (isNotEmpty(UserPrefs.getMedications(this)) ? UserPrefs.getMedications(this) : "None");
        tvValueConditions.setText(displayedConditions);
        tvValueAddress.setText(isNotEmpty(address) ? address.toUpperCase(Locale.ROOT) : "Not set");
    }

    private void setupListeners() {
        // Back
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Avatar Pick & Camera BottomSheet
        View.OnClickListener photoClickListener = v -> showPhotoBottomSheet();
        if (profilePhoto != null) profilePhoto.setOnClickListener(photoClickListener);
        View btnPick = findViewById(R.id.btn_pick_photo);
        if (btnPick != null) btnPick.setOnClickListener(photoClickListener);

        // Card 1
        findViewById(R.id.row_name).setOnClickListener(v -> showEditNameDialog());

        // Card 2: Private Info (Email, DOB, and Gender are uneditable)
        findViewById(R.id.row_weight).setOnClickListener(v -> showEditWeightDialog());
        findViewById(R.id.row_height).setOnClickListener(v -> showEditHeightDialog());

        // Card 3: Medical & Contact
        findViewById(R.id.row_phone).setOnClickListener(v -> showEditPhoneDialog());
        findViewById(R.id.row_blood_type).setOnClickListener(v -> showBloodTypeDialog());
        findViewById(R.id.row_allergies).setOnClickListener(v -> showEditAllergiesDialog());
        findViewById(R.id.row_conditions).setOnClickListener(v -> showEditConditionsDialog());
        findViewById(R.id.row_address).setOnClickListener(v -> showEditAddressDialog());

        // Delete Account
        findViewById(R.id.btn_delete_account).setOnClickListener(v -> AccountDeletionUtils.confirmAndDelete(this, executor));
    }

    private void setupImagePicker() {
        pickImage = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) return;
                    try {
                        getContentResolver().takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {}

                    UserPrefs.setPhotoUri(this, uri.toString());
                    UserPrefs.setPendingPhotoUri(this, uri.toString());
                    if (profilePhoto != null) {
                        try {
                            profilePhoto.setImageURI(uri);
                        } catch (Exception ignored) {}
                    }
                    uploadPhotoToStorage(DeviceIdUtils.getStableUid(this), uri);
                }
        );

        takeCameraPhoto = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                bitmap -> {
                    if (bitmap == null) return;
                    if (profilePhoto != null) {
                        profilePhoto.setImageBitmap(bitmap);
                    }
                    uploadBitmapPhoto(bitmap);
                }
        );

        requestCameraPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (Boolean.TRUE.equals(isGranted)) {
                        launchCamera();
                    } else {
                        Toast.makeText(this, "Camera permission is required to take a picture", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void launchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            try {
                takeCameraPhoto.launch(null);
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch camera", e);
                Toast.makeText(this, "Could not open camera", Toast.LENGTH_SHORT).show();
            }
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchImagePicker() {
        try {
            pickImage.launch(new String[]{"image/*"});
        } catch (Exception e) {
            Toast.makeText(this, R.string.toast_not_implemented, Toast.LENGTH_SHORT).show();
        }
    }

    private void showPhotoBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_ResQTap_BottomSheetDialog);
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_pfp_picker, null);
        dialog.setContentView(sheetView);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setWindowAnimations(R.style.Animation_ResQTap_BottomSheetDialog);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialog.setOnShowListener(d -> {
            try {
                BottomSheetBehavior<?> behavior = dialog.getBehavior();
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            } catch (Exception ignored) {}
        });

        // 1. Take Picture
        View btnTake = sheetView.findViewById(R.id.btn_option_take_photo);
        if (btnTake != null) {
            btnTake.setOnClickListener(v -> {
                dialog.dismiss();
                launchCamera();
            });
        }



        // 3. Remove PFP
        View btnRemove = sheetView.findViewById(R.id.btn_option_remove_photo);
        if (btnRemove != null) {
            btnRemove.setOnClickListener(v -> {
                dialog.dismiss();
                clearProfilePhoto();
            });
        }

        dialog.show();
    }

    private void showPhotoOptionsDialog() {
        showPhotoBottomSheet();
    }

    private void showPhotoPreviewDialog() {
        android.app.Dialog d = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        d.setCancelable(true);
        d.setCanceledOnTouchOutside(true);

        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.setBackgroundColor(0xB3000000);
        root.setOnClickListener(v -> d.dismiss());

        android.widget.ImageView big = new android.widget.ImageView(this);
        big.setAdjustViewBounds(true);
        big.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        if (profilePhoto != null && profilePhoto.getDrawable() != null) {
            big.setImageDrawable(profilePhoto.getDrawable());
        } else {
            big.setImageResource(R.drawable.ic_avatar);
        }

        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        big.setMaxWidth(Math.round(dm.widthPixels * 0.92f));
        big.setMaxHeight(Math.round(dm.heightPixels * 0.70f));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = android.view.Gravity.CENTER;
        big.setLayoutParams(lp);

        root.addView(big);
        d.setContentView(root);
        d.show();
    }

    private void clearProfilePhoto() {
        String uid = DeviceIdUtils.getStableUid(this);
        UserPrefs.setPhotoB64(this, "");
        UserPrefs.setPhotoUrl(this, "");
        UserPrefs.setPhotoUri(this, "");
        UserPrefs.setPendingPhotoUri(this, "");
        if (isNotEmpty(uid)) FirebaseRoomClient.clearUserPhotoQueued(uid);
        if (profilePhoto != null) profilePhoto.setImageResource(R.drawable.ic_avatar);
        Toast.makeText(this, R.string.toast_profile_photo_removed, Toast.LENGTH_SHORT).show();
    }

    // ──────────────────────────────
    // DIALOGS: EDIT FIELDS
    // ──────────────────────────────

    private void showEditNameDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_name, null);
        com.google.android.material.textfield.TextInputLayout layoutFirstName = dialogView.findViewById(R.id.layout_dialog_first_name);
        com.google.android.material.textfield.TextInputEditText inputFirstName = dialogView.findViewById(R.id.input_dialog_first_name);
        com.google.android.material.textfield.TextInputLayout layoutLastName = dialogView.findViewById(R.id.layout_dialog_last_name);
        com.google.android.material.textfield.TextInputEditText inputLastName = dialogView.findViewById(R.id.input_dialog_last_name);
        com.google.android.material.button.MaterialButton btnCancel = dialogView.findViewById(R.id.btn_dialog_name_cancel);
        com.google.android.material.button.MaterialButton btnSave = dialogView.findViewById(R.id.btn_dialog_name_save);

        // Force ALL CAPS on both fields
        InputFilter[] capsFilter = new InputFilter[]{new InputFilter.AllCaps()};
        if (inputFirstName != null) inputFirstName.setFilters(capsFilter);
        if (inputLastName != null) inputLastName.setFilters(capsFilter);

        // Pre-fill existing first & last name
        String currentFirst = UserPrefs.getFirstName(this);
        String currentLast = UserPrefs.getLastName(this);
        if (currentFirst.isEmpty() && currentLast.isEmpty()) {
            String existingFullName = UserPrefs.getName(this).trim();
            if (!existingFullName.isEmpty()) {
                String[] parts = existingFullName.split("\\s+", 2);
                currentFirst = parts[0];
                if (parts.length > 1) {
                    currentLast = parts[1];
                }
            }
        }

        if (inputFirstName != null && isNotEmpty(currentFirst)) {
            inputFirstName.setText(currentFirst.toUpperCase(Locale.ROOT));
            inputFirstName.setSelection(inputFirstName.getText().length());
        }
        if (inputLastName != null && isNotEmpty(currentLast)) {
            inputLastName.setText(currentLast.toUpperCase(Locale.ROOT));
            inputLastName.setSelection(inputLastName.getText().length());
        }

        if (inputFirstName != null) {
            inputFirstName.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (layoutFirstName != null && layoutFirstName.getError() != null) {
                        layoutFirstName.setError(null);
                    }
                }
                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_ResQTap_AlertDialog)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnSave != null) {
            btnSave.setOnClickListener(v -> {
                String first = inputFirstName != null && inputFirstName.getText() != null
                        ? inputFirstName.getText().toString().trim().toUpperCase(Locale.ROOT) : "";
                String last = inputLastName != null && inputLastName.getText() != null
                        ? inputLastName.getText().toString().trim().toUpperCase(Locale.ROOT) : "";

                if (first.isEmpty()) {
                    if (layoutFirstName != null) {
                        layoutFirstName.setError("First name is required");
                    }
                    if (inputFirstName != null) {
                        inputFirstName.requestFocus();
                    }
                    return;
                } else if (layoutFirstName != null) {
                    layoutFirstName.setError(null);
                }

                UserPrefs.setFirstName(this, first);
                UserPrefs.setLastName(this, last);

                String fullName = (first + " " + last).trim();
                UserPrefs.setName(this, fullName);

                if (tvValueName != null) {
                    tvValueName.setText(fullName);
                }

                syncProfileToFirebase();
                showSavedToast("Name updated");
                dialog.dismiss();
            });
        }

        dialog.show();
        if (inputFirstName != null) {
            inputFirstName.requestFocus();
        }
    }

    private void showEditEmailDialog() {
        showInputDialog("Edit Email", UserPrefs.getEmail(this), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, newText -> {
            if (newText.isEmpty()) return;
            UserPrefs.setEmail(this, newText);
            tvValueEmail.setText(newText);
            syncProfileToFirebase();
            showSavedToast("Email updated");
        });
    }

    private void showDatePickerDialog() {
        Calendar cal = Calendar.getInstance();
        String currentDob = UserPrefs.getDateOfBirth(this);
        try {
            if (isNotEmpty(currentDob)) {
                // Try parse formats: dd-MMMM-yyyy or dd/MM/yyyy
                if (currentDob.contains("-")) {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd-MMMM-yyyy", Locale.ENGLISH);
                    Date date = sdf.parse(currentDob);
                    if (date != null) cal.setTime(date);
                } else if (currentDob.contains("/")) {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH);
                    Date date = sdf.parse(currentDob);
                    if (date != null) cal.setTime(date);
                }
            }
        } catch (Exception ignored) {}

        int y = cal.get(Calendar.YEAR);
        int m = cal.get(Calendar.MONTH);
        int d = cal.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dpd = new DatePickerDialog(this, R.style.DatePickerTheme, (view, year, month, dayOfMonth) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(year, month, dayOfMonth);
            SimpleDateFormat fmt = new SimpleDateFormat("dd-MMMM-yyyy", Locale.ENGLISH);
            String formattedDate = fmt.format(selected.getTime());

            UserPrefs.setDateOfBirth(this, formattedDate);
            tvValueDob.setText(formattedDate);
            syncProfileToFirebase();
            showSavedToast("Birthdate updated");
        }, y, m, d);

        dpd.show();
    }

    private void showGenderPickerDialog() {
        String[] genders = new String[]{"Male", "Female"};
        String current = UserPrefs.getGender(this);
        int checkedItem = 0;
        for (int i = 0; i < genders.length; i++) {
            if (genders[i].equalsIgnoreCase(current)) {
                checkedItem = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_ResQTap_AlertDialog)
                .setTitle("Select Gender")
                .setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_dialog_rounded))
                .setSingleChoiceItems(genders, checkedItem, (dialog, which) -> {
                    String selected = genders[which];
                    UserPrefs.setGender(this, selected);
                    tvValueGender.setText(selected);
                    syncProfileToFirebase();
                    showSavedToast("Gender updated");
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
