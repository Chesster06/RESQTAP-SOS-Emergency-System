package com.example.resqtap.auth;
import com.example.resqtap.R;

import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.home.MainActivity;
import com.example.resqtap.friend.QrCodeUtils;
import com.example.resqtap.room.FirebaseRoomClient;
import com.example.resqtap.utils.AvatarUtils;
import com.example.resqtap.utils.CountryCodeHelper;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import com.example.resqtap.contacts.EmergencyContact;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.GoogleAuthProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.FirebaseDatabase;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.UUID;
import java.security.SecureRandom;

/**
 * RegisterActivity
 * Skrin Register (4 step wizard): kumpul info peribadi, info perubatan kecemasan, dan auto-generate Tag ID unik (contoh: User#4091).
 */
public class RegisterActivity extends BaseActivity {
    private static final String TAG = "RegisterActivity";
    private volatile boolean emailAvailable = true;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable emailDebounce;
    private boolean completeProfileMode = false;
    private boolean fromGoogleSignIn = false;
    private String googleIdToken = "";
    private String googlePhotoUrl = "";
    private String completeUid = "";
    private boolean authSwipeMode = false;
    private boolean isRegistrationCompleted = false;

    private View step1Credentials;
    private View step2PersonalInfo;
    private View step3MedicalEmergency;
    private View step4GetStarted;
    private TextView titleText;
    private TextView subtitleText;

    private String registeredEmail = "";
    private String registeredPassword = "";
    private String registeredUid = "";
    private String photoB64 = "";

    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private View btnSocialGoogle;
    private View layoutGoogleBtnContent;
    private View layoutGoogleLoading;
    private MaterialButton btnCreateAccount;

    private ActivityResultLauncher<String> requestCameraPermission;
    private ActivityResultLauncher<Intent> takePhotoLauncher;
    private Uri tempCameraUri;
    private ShapeableImageView imgAvatar;
    private Bitmap highResAvatarBitmap = null;

    protected boolean shouldAnimateContentIn() {
        return false;
    }

    /** Inisialisasi paparan dan komponen UI. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        setGoogleLoading(true);
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        handleGoogleSignInResult(task);
                    } else if (result.getResultCode() == RESULT_CANCELED) {
                        setGoogleLoading(false);
                        Toast.makeText(RegisterActivity.this, R.string.toast_google_sign_in_cancelled, Toast.LENGTH_SHORT).show();
                    } else {
                        setGoogleLoading(false);
                        Toast.makeText(RegisterActivity.this, R.string.toast_google_sign_in_failed, Toast.LENGTH_SHORT).show();
                    }
                }
        );

        UserPrefs.setActiveRoomCode(this, "");
        setContentView(R.layout.activity_register);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            View step1 = findViewById(R.id.step1_credentials);
            if (step1 != null) {
                int basePaddingBottom = (int) (36 * getResources().getDisplayMetrics().density);
                step1.setPadding(
                        step1.getPaddingLeft(),
                        step1.getPaddingTop(),
                        step1.getPaddingRight(),
                        basePaddingBottom + systemBars.bottom
                );
            }
            View step4 = findViewById(R.id.step4_get_started);
            if (step4 != null) {
                int basePaddingBottom = (int) (24 * getResources().getDisplayMetrics().density);
                step4.setPadding(
                        step4.getPaddingLeft(),
                        step4.getPaddingTop(),
                        step4.getPaddingRight(),
                        basePaddingBottom + systemBars.bottom
                );
            }
            return insets;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isRegistrationCompleted) {
                    finishToLogin();
                } else {
                    cancelRegistrationAndGoLogin();
                }
            }
        });

        titleText = findViewById(R.id.title);
        subtitleText = findViewById(R.id.subtitle);
        if (titleText != null) titleText.setText(R.string.register_step1_title);
        if (subtitleText != null) {
            subtitleText.setText(R.string.register_step1_subtitle);
            subtitleText.setVisibility(View.VISIBLE);
        }

        step1Credentials = findViewById(R.id.step1_credentials);
        step2PersonalInfo = findViewById(R.id.step2_personal_info);
        step3MedicalEmergency = findViewById(R.id.step3_medical_emergency);
        step4GetStarted = findViewById(R.id.step4_get_started);

        TextInputLayout emailLayout = findViewById(R.id.layout_email);
        TextInputEditText email = findViewById(R.id.input_email);
        TextInputEditText password = findViewById(R.id.input_password);
        TextInputEditText passwordConfirm = findViewById(R.id.input_password_confirm);
        MaterialButton btnCreate = findViewById(R.id.btn_create_account);
        btnCreateAccount = btnCreate;
        View btnBack = findViewById(R.id.btn_back_login);

        btnSocialGoogle = findViewById(R.id.btn_social_google);
        layoutGoogleBtnContent = findViewById(R.id.layout_google_btn_content);
        layoutGoogleLoading = findViewById(R.id.layout_google_loading);
        if (btnSocialGoogle != null) {
            btnSocialGoogle.setOnClickListener(v -> {
                if (googleSignInClient != null && googleSignInLauncher != null) {
                    setGoogleLoading(true);
                    googleSignInClient.signOut().addOnCompleteListener(t -> {
                        if (isFinishing() || isDestroyed()) return;
                        try {
                            Intent signInIntent = googleSignInClient.getSignInIntent();
                            googleSignInLauncher.launch(signInIntent);
                        } catch (Exception ex) {
                            setGoogleLoading(false);
                            Log.e(TAG, "Failed to launch Google Sign-In intent", ex);
                            Toast.makeText(RegisterActivity.this, R.string.toast_google_sign_in_failed, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        }

        View tvHelp = findViewById(R.id.tv_help);
        if (tvHelp != null) {
            tvHelp.setOnClickListener(v -> Toast.makeText(this, "Need assistance? Contact support@resqtap.com", Toast.LENGTH_LONG).show());
        }

        imgAvatar = findViewById(R.id.img_profile_avatar);
        MaterialButton btnUploadPhoto = findViewById(R.id.btn_upload_photo);
        TextInputLayout firstNameLayout = findViewById(R.id.layout_first_name);
        TextInputLayout lastNameLayout = findViewById(R.id.layout_last_name);
        TextInputLayout icLayout = findViewById(R.id.layout_ic);
        TextInputLayout genderLayout = findViewById(R.id.layout_gender);
        TextInputLayout phoneLayout = findViewById(R.id.layout_phone);
        TextInputLayout addressLayout = findViewById(R.id.layout_address);
        TextInputLayout religionLayout = findViewById(R.id.layout_religion);
        TextInputLayout layoutReligionOther = findViewById(R.id.layout_religion_other);
        TextInputLayout dobLayout = findViewById(R.id.layout_dob);
        TextInputLayout ethnicityLayout = findViewById(R.id.layout_ethnicity);
        TextInputLayout layoutEthnicityOther = findViewById(R.id.layout_ethnicity_other);

        TextInputEditText firstNameInput = findViewById(R.id.input_first_name);
        TextInputEditText lastNameInput = findViewById(R.id.input_last_name);
        TextInputEditText icInput = findViewById(R.id.input_ic);
        MaterialAutoCompleteTextView genderInput = findViewById(R.id.input_gender);
        TextInputEditText phoneInput = findViewById(R.id.input_phone);
        View btnCountryPicker = findViewById(R.id.btn_country_picker);
        TextView tvCountryFlag = findViewById(R.id.tv_country_flag);
        TextView tvCountryCode = findViewById(R.id.tv_country_code);
        if (btnCountryPicker != null) {
            btnCountryPicker.setOnClickListener(v -> {
                CountryCodeHelper.showCountryPicker(this, btnCountryPicker, country -> {
                    if (tvCountryFlag != null) tvCountryFlag.setText(country.flag);
                    if (tvCountryCode != null) tvCountryCode.setText(country.dialCode);
                });
            });
        }
        TextInputEditText addressInput = findViewById(R.id.input_address);
        MaterialAutoCompleteTextView religionInput = findViewById(R.id.input_religion);
        TextInputEditText religionOtherInput = findViewById(R.id.input_religion_other);
        if (religionOtherInput != null) {
            religionOtherInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.AllCaps()});
            religionOtherInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (layoutReligionOther != null) { layoutReligionOther.setError(null); layoutReligionOther.setErrorEnabled(false); }
                }
            });
        }

        TextInputEditText dobInput = findViewById(R.id.input_dob);
        if (dobInput != null) {
            dobInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (dobLayout != null) { dobLayout.setError(null); dobLayout.setErrorEnabled(false); }
                }
            });
        }

        MaterialAutoCompleteTextView ethnicityInput = findViewById(R.id.input_ethnicity);
        TextInputEditText ethnicityOtherInput = findViewById(R.id.input_ethnicity_other);
        if (ethnicityOtherInput != null) {
            ethnicityOtherInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.AllCaps()});
            ethnicityOtherInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (layoutEthnicityOther != null) { layoutEthnicityOther.setError(null); layoutEthnicityOther.setErrorEnabled(false); }
                }
            });
        }

        MaterialButton btnNextToStep3 = findViewById(R.id.btn_next_to_step3);

        if (firstNameInput != null) {
            firstNameInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.AllCaps()});
            firstNameInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (firstNameLayout != null) { firstNameLayout.setError(null); firstNameLayout.setErrorEnabled(false); }
                }
            });
        }
        if (lastNameInput != null) {
            lastNameInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.AllCaps()});
            lastNameInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (lastNameLayout != null) { lastNameLayout.setError(null); lastNameLayout.setErrorEnabled(false); }
                }
            });
        }
        if (icInput != null) {
            icInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (icLayout != null) { icLayout.setError(null); icLayout.setErrorEnabled(false); }
                }
            });
        }
        if (genderInput != null) {
            genderInput.setOnItemClickListener((parent, view, position, id) -> {
                if (genderLayout != null) { genderLayout.setError(null); genderLayout.setErrorEnabled(false); }
            });
            genderInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (genderLayout != null) { genderLayout.setError(null); genderLayout.setErrorEnabled(false); }
                }
            });
        }
        if (phoneInput != null) {
            phoneInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (phoneLayout != null) { phoneLayout.setError(null); phoneLayout.setErrorEnabled(false); }
                }
            });
        }
        if (addressInput != null) {
            addressInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.AllCaps()});
            addressInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (addressLayout != null) { addressLayout.setError(null); addressLayout.setErrorEnabled(false); }
                }
            });
        }

        TextInputLayout layoutBloodType = findViewById(R.id.layout_blood_type);
        MaterialAutoCompleteTextView bloodType = findViewById(R.id.input_blood_type);

        TextInputLayout layoutAllergies = findViewById(R.id.layout_allergies);
        MaterialAutoCompleteTextView allergiesInput = findViewById(R.id.input_allergies);
        TextInputLayout layoutAllergiesOther = findViewById(R.id.layout_allergies_other);
        TextInputEditText allergiesOtherInput = findViewById(R.id.input_allergies_other);

        TextInputLayout layoutMedications = findViewById(R.id.layout_medications);
        MaterialAutoCompleteTextView medicationsInput = findViewById(R.id.input_medications);
        TextInputLayout layoutMedicationsOther = findViewById(R.id.layout_medications_other);
        TextInputEditText medicationsOtherInput = findViewById(R.id.input_medications_other);

        TextInputLayout layoutOrganDonor = findViewById(R.id.layout_organ_donor);
        MaterialAutoCompleteTextView organDonorInput = findViewById(R.id.input_organ_donor);

        TextInputLayout layoutEmergencyName = findViewById(R.id.layout_emergency_name);
        TextInputEditText emergencyNameInput = findViewById(R.id.input_emergency_name);

        TextInputLayout layoutEmergencyPhone = findViewById(R.id.layout_emergency_phone);
        TextInputEditText emergencyPhoneInput = findViewById(R.id.input_emergency_phone);
        View btnEmergencyCountryPicker = findViewById(R.id.btn_emergency_country_picker);
        TextView tvEmergencyCountryFlag = findViewById(R.id.tv_emergency_country_flag);
        TextView tvEmergencyCountryCode = findViewById(R.id.tv_emergency_country_code);
        if (btnEmergencyCountryPicker != null) {
            btnEmergencyCountryPicker.setOnClickListener(v -> {
                CountryCodeHelper.showCountryPicker(this, btnEmergencyCountryPicker, country -> {
                    if (tvEmergencyCountryFlag != null) tvEmergencyCountryFlag.setText(country.flag);
                    if (tvEmergencyCountryCode != null) tvEmergencyCountryCode.setText(country.dialCode);
                });
            });
        }
        TextInputLayout layoutEmergencyRelation = findViewById(R.id.layout_emergency_relation);
        MaterialAutoCompleteTextView emergencyRelationInput = findViewById(R.id.input_emergency_relation);
        TextInputLayout layoutEmergencyRelationOther = findViewById(R.id.layout_emergency_relation_other);
        TextInputEditText emergencyRelationOtherInput = findViewById(R.id.input_emergency_relation_other);
        MaterialButton btnSaveDetails = findViewById(R.id.btn_save_details);

        MaterialButton btnGetStarted = findViewById(R.id.btn_get_started);

        MaterialButton btnBackStep2 = findViewById(R.id.btn_back_login_step2);
        MaterialButton btnBackStep3 = findViewById(R.id.btn_back_login_step3);

        forceHeavyText(titleText);
        if (btnBack != null) btnBack.setOnClickListener(v -> cancelRegistrationAndGoLogin());
        if (btnBackStep2 != null) btnBackStep2.setOnClickListener(v -> cancelRegistrationAndGoLogin());
        if (btnBackStep3 != null) btnBackStep3.setOnClickListener(v -> cancelRegistrationAndGoLogin());

        authSwipeMode = getIntent() != null && getIntent().getBooleanExtra("auth_swipe", false);
        completeProfileMode = getIntent() != null && getIntent().getBooleanExtra("complete_profile", false);
        fromGoogleSignIn = getIntent() != null && getIntent().getBooleanExtra("from_google_sign_in", false);
        googleIdToken = getIntent() == null ? "" : String.valueOf(getIntent().getStringExtra("google_id_token") == null ? "" : getIntent().getStringExtra("google_id_token"));
        googlePhotoUrl = getIntent() == null ? "" : String.valueOf(getIntent().getStringExtra("google_photo_url") == null ? "" : getIntent().getStringExtra("google_photo_url"));
        completeUid = getIntent() == null ? "" : String.valueOf(getIntent().getStringExtra("uid") == null ? "" : getIntent().getStringExtra("uid"));
        String presetEmail = getIntent() == null ? "" : String.valueOf(getIntent().getStringExtra("email"));

        try {
            String[] genders = getResources().getStringArray(R.array.gender_options);
            genderInput.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown_popup, genders));
            genderInput.setDropDownBackgroundResource(R.drawable.bg_dropdown_popup);
        } catch (Exception ignored) {
        }

        try {
            String[] religions = getResources().getStringArray(R.array.religion_options);
            religionInput.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown_popup, religions));
            religionInput.setDropDownBackgroundResource(R.drawable.bg_dropdown_popup);
        } catch (Exception ignored) {
        }

        if (religionInput != null) {
            religionInput.setOnItemClickListener((parent, view, position, id) -> {
                String selected = religionInput.getText() == null ? "" : religionInput.getText().toString();
                if (isOtherSelected(selected)) {
                    if (layoutReligionOther != null) layoutReligionOther.setVisibility(View.VISIBLE);
                    if (religionOtherInput != null) religionOtherInput.requestFocus();
                } else {
                    if (layoutReligionOther != null) layoutReligionOther.setVisibility(View.GONE);
                    if (religionOtherInput != null) religionOtherInput.setText("");
                }
                if (religionLayout != null) {
                    religionLayout.setError(null);
                    religionLayout.setErrorEnabled(false);
                }
            });
            religionInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    String selected = s == null ? "" : s.toString();
                    if (isOtherSelected(selected)) {
                        if (layoutReligionOther != null) layoutReligionOther.setVisibility(View.VISIBLE);
                    } else {
                        if (layoutReligionOther != null) layoutReligionOther.setVisibility(View.GONE);
                    }
                    if (religionLayout != null) {
                        religionLayout.setError(null);
                        religionLayout.setErrorEnabled(false);
                    }
                }
            });
        }

        try {
            String[] ethnicities = getResources().getStringArray(R.array.ethnicity_options);
            ethnicityInput.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown_popup, ethnicities));
            ethnicityInput.setDropDownBackgroundResource(R.drawable.bg_dropdown_popup);
        } catch (Exception ignored) {
        }

        if (ethnicityInput != null) {
            ethnicityInput.setOnItemClickListener((parent, view, position, id) -> {
                String selected = ethnicityInput.getText() == null ? "" : ethnicityInput.getText().toString();
                if (isOtherSelected(selected)) {
                    if (layoutEthnicityOther != null) layoutEthnicityOther.setVisibility(View.VISIBLE);
                    if (ethnicityOtherInput != null) ethnicityOtherInput.requestFocus();
                } else {
                    if (layoutEthnicityOther != null) layoutEthnicityOther.setVisibility(View.GONE);
                    if (ethnicityOtherInput != null) ethnicityOtherInput.setText("");
                }
                if (ethnicityLayout != null) {
                    ethnicityLayout.setError(null);
                    ethnicityLayout.setErrorEnabled(false);
                }
            });
            ethnicityInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    String selected = s == null ? "" : s.toString();
                    if (isOtherSelected(selected)) {
                        if (layoutEthnicityOther != null) layoutEthnicityOther.setVisibility(View.VISIBLE);
                    } else {
                        if (layoutEthnicityOther != null) layoutEthnicityOther.setVisibility(View.GONE);
                    }
                    if (ethnicityLayout != null) {
                        ethnicityLayout.setError(null);
                        ethnicityLayout.setErrorEnabled(false);
                    }
                }
            });
        }

        String[] bloodTypes = new String[]{"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "Unknown"};
        bloodType.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown_popup, bloodTypes));
        bloodType.setDropDownBackgroundResource(R.drawable.bg_dropdown_popup);
        if (bloodType != null) {
            bloodType.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (layoutBloodType != null) {
                        layoutBloodType.setError(null);
                        layoutBloodType.setErrorEnabled(false);
                    }
                }
            });
        }

        View.OnClickListener allergiesClick = v -> {
            String[] allergiesList;
            try {
                allergiesList = getResources().getStringArray(R.array.allergy_options);
            } catch (Exception e) {
                allergiesList = new String[]{"None", "Peanuts / Nuts", "Seafood / Shellfish", "Dairy / Lactose", "Eggs", "Wheat / Gluten", "Penicillin / Antibiotics", "Aspirin / NSAIDs", "Latex", "Dust / Pollen", "Others"};
            }
            showMultiSelectRegisterDialog(getString(R.string.select_allergies_title), allergiesList,
                    allergiesInput, layoutAllergies, layoutAllergiesOther, allergiesOtherInput);
        };
        if (allergiesInput != null) {
            allergiesInput.setOnClickListener(allergiesClick);
        }
        if (layoutAllergies != null) {
            layoutAllergies.setEndIconOnClickListener(allergiesClick);
        }

        if (allergiesOtherInput != null) {
            allergiesOtherInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (layoutAllergiesOther != null) {
                        layoutAllergiesOther.setError(null);
                        layoutAllergiesOther.setErrorEnabled(false);
                    }
                }
            });
        }

        View.OnClickListener medicationsClick = v -> {
            String[] medicationsList;
            try {
                medicationsList = getResources().getStringArray(R.array.medication_options);
            } catch (Exception e) {
                medicationsList = new String[]{"None", "Painkillers (Paracetamol / Panadol)", "Antibiotics", "Antihistamines (Allergy)", "Inhaler (Asthma)", "Insulin / Diabetes Meds", "Blood Pressure Meds (Hypertension)", "Heart / Cholesterol Meds", "Gastric / Antacid Meds", "Others"};
            }
            showMultiSelectRegisterDialog(getString(R.string.select_conditions_title), medicationsList,
                    medicationsInput, layoutMedications, layoutMedicationsOther, medicationsOtherInput);
        };
        if (medicationsInput != null) {
            medicationsInput.setOnClickListener(medicationsClick);
        }
        if (layoutMedications != null) {
            layoutMedications.setEndIconOnClickListener(medicationsClick);
        }

        if (medicationsOtherInput != null) {
            medicationsOtherInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (layoutMedicationsOther != null) {
                        layoutMedicationsOther.setError(null);
                        layoutMedicationsOther.setErrorEnabled(false);
                    }
                }
            });
        }

        try {
            String[] donors = getResources().getStringArray(R.array.organ_donor_options);
            organDonorInput.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown_popup, donors));
            organDonorInput.setDropDownBackgroundResource(R.drawable.bg_dropdown_popup);
        } catch (Exception ignored) {
        }

        if (organDonorInput != null) {
            organDonorInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (layoutOrganDonor != null) {
                        layoutOrganDonor.setError(null);
                        layoutOrganDonor.setErrorEnabled(false);
                    }
                }
            });
        }

        if (emergencyNameInput != null) {
            emergencyNameInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.AllCaps()});
            emergencyNameInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (layoutEmergencyName != null) {
                        layoutEmergencyName.setError(null);
                        layoutEmergencyName.setErrorEnabled(false);
                    }
                }
            });
        }

        if (emergencyPhoneInput != null) {
            emergencyPhoneInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (layoutEmergencyPhone != null) {
                        layoutEmergencyPhone.setError(null);
                        layoutEmergencyPhone.setErrorEnabled(false);
                    }
                }
            });
        }

        try {
            String[] relations = getResources().getStringArray(R.array.relationship_options);
            emergencyRelationInput.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown_popup, relations));
            emergencyRelationInput.setDropDownBackgroundResource(R.drawable.bg_dropdown_popup);
        } catch (Exception ignored) {
        }

        if (emergencyRelationInput != null) {
            emergencyRelationInput.setOnItemClickListener((parent, view, position, id) -> {
                String selected = emergencyRelationInput.getText() == null ? "" : emergencyRelationInput.getText().toString();
                if (isOtherSelected(selected)) {
                    if (layoutEmergencyRelationOther != null) layoutEmergencyRelationOther.setVisibility(View.VISIBLE);
                    if (emergencyRelationOtherInput != null) emergencyRelationOtherInput.requestFocus();
                } else {
                    if (layoutEmergencyRelationOther != null) layoutEmergencyRelationOther.setVisibility(View.GONE);
                    if (emergencyRelationOtherInput != null) emergencyRelationOtherInput.setText("");
                }
            });
            emergencyRelationInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    String selected = s == null ? "" : s.toString();
                    if (isOtherSelected(selected)) {
                        if (layoutEmergencyRelationOther != null) layoutEmergencyRelationOther.setVisibility(View.VISIBLE);
                    } else {
                        if (layoutEmergencyRelationOther != null) layoutEmergencyRelationOther.setVisibility(View.GONE);
                    }
                    if (layoutEmergencyRelation != null) {
                        layoutEmergencyRelation.setError(null);
                        layoutEmergencyRelation.setErrorEnabled(false);
                    }
                }
            });
        }

        if (emergencyRelationOtherInput != null) {
            emergencyRelationOtherInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.AllCaps()});
            emergencyRelationOtherInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (layoutEmergencyRelationOther != null) {
                        layoutEmergencyRelationOther.setError(null);
                        layoutEmergencyRelationOther.setErrorEnabled(false);
                    }
                }
            });
        }

        if (dobInput != null) {
            dobInput.setOnClickListener(v -> showDobPicker(dobInput));
            dobInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) showDobPicker(dobInput);
            });
        }

        requestCameraPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        launchFrontCamera();
                    } else {
                        Toast.makeText(this, "Camera permission is required to take a profile photo", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        takePhotoLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Bitmap bitmap = null;

                        // 1. Cuba muat dari tempCameraUri (FileProvider)
                        if (tempCameraUri != null) {
                            try (java.io.InputStream is = getContentResolver().openInputStream(tempCameraUri)) {
                                if (is != null) {
                                    bitmap = BitmapFactory.decodeStream(is);
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "Failed to decode from tempCameraUri", e);
                            }
                        }

                        // 2. Fallback kepada intent data extras thumbnail
                        if (bitmap == null && result.getData() != null) {
                            if (result.getData().getExtras() != null) {
                                Object data = result.getData().getExtras().get("data");
                                if (data instanceof Bitmap) {
                                    bitmap = (Bitmap) data;
                                }
                            }
                            if (bitmap == null && result.getData().getData() != null) {
                                try (java.io.InputStream is = getContentResolver().openInputStream(result.getData().getData())) {
                                    if (is != null) {
                                        bitmap = BitmapFactory.decodeStream(is);
                                    }
                                } catch (Exception ignored) {}
                            }
                        }

                        if (bitmap != null) {
                            // Skalakan bitmap jika saiz terlalu besar untuk elakkan masalah memori
                            if (bitmap.getWidth() > 960 || bitmap.getHeight() > 960) {
                                float ratio = Math.min(960f / bitmap.getWidth(), 960f / bitmap.getHeight());
                                int w = Math.round(bitmap.getWidth() * ratio);
                                int h = Math.round(bitmap.getHeight() * ratio);
                                bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true);
                            }

                            highResAvatarBitmap = bitmap;
                            photoB64 = bitmapToBase64(bitmap);
                            if (imgAvatar != null) {
                                imgAvatar.setImageBitmap(bitmap);
                                imgAvatar.setImageTintList(null);
                            }
                            Toast.makeText(this, "Profile photo updated!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Photo capture returned empty, please try again", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        if (btnUploadPhoto != null) {
            btnUploadPhoto.setOnClickListener(v -> launchFrontCamera());
        }
        if (imgAvatar != null) {
            imgAvatar.setOnClickListener(v -> showAvatarPreviewDialog());
        }

        if (completeProfileMode) {
            registeredEmail = presetEmail == null ? "" : presetEmail;
            registeredUid = FirebaseAuth.getInstance().getCurrentUser() == null ? completeUid : FirebaseAuth.getInstance().getCurrentUser().getUid();

            // Auto-populate existing user data dari UserPrefs supaya pengguna tidak nampak form kosong
            String savedFirstName = UserPrefs.getFirstName(this).trim();
            String savedLastName = UserPrefs.getLastName(this).trim();
            if (!savedFirstName.isEmpty()) {
                if (firstNameInput != null) firstNameInput.setText(savedFirstName);
                if (lastNameInput != null) lastNameInput.setText(savedLastName);
            } else if (!UserPrefs.getName(this).isEmpty()) {
                String savedFullName = UserPrefs.getName(this).trim();
                int spaceIdx = savedFullName.indexOf(' ');
                if (spaceIdx > 0) {
                    if (firstNameInput != null) firstNameInput.setText(savedFullName.substring(0, spaceIdx).trim());
                    if (lastNameInput != null) lastNameInput.setText(savedFullName.substring(spaceIdx + 1).trim());
                } else {
                    if (firstNameInput != null) firstNameInput.setText(savedFullName);
                }
            }
            if (fromGoogleSignIn) {
                // Sign in dengan Google: Jangan auto-fill sebarang nombor (IC & telefon), hanya nama sahaja
                if (icInput != null) icInput.setText("");
                if (phoneInput != null) phoneInput.setText("");
                if (emergencyNameInput != null) emergencyNameInput.setText("");
                if (emergencyPhoneInput != null) emergencyPhoneInput.setText("");

                // Muat turun dan pasang gambar profil Google secara automatik jika ada
                if (googlePhotoUrl != null && !googlePhotoUrl.trim().isEmpty() && imgAvatar != null) {
                    loadAndSetGoogleProfilePhoto(googlePhotoUrl.trim());
                }
            } else {
                if (icInput != null && !UserPrefs.getIcNumber(this).isEmpty()) icInput.setText(UserPrefs.getIcNumber(this));
                if (genderInput != null && !UserPrefs.getGender(this).isEmpty()) genderInput.setText(UserPrefs.getGender(this), false);
                if (phoneInput != null && !UserPrefs.getPhoneNumber(this).isEmpty()) {
                    String savedPhone = UserPrefs.getPhoneNumber(this).trim();
                    CountryCodeHelper.Country matched = null;
                    if (savedPhone.startsWith("+")) {
                        for (CountryCodeHelper.Country c : CountryCodeHelper.getAllCountries()) {
                            if (savedPhone.startsWith(c.dialCode)) {
                                matched = c;
                                break;
                            }
                        }
                    }
                    if (matched != null) {
                        if (tvCountryFlag != null) tvCountryFlag.setText(matched.flag);
                        if (tvCountryCode != null) tvCountryCode.setText(matched.dialCode);
                        phoneInput.setText(savedPhone.substring(matched.dialCode.length()).trim());
                    } else {
                        phoneInput.setText(savedPhone);
                    }
                }
                if (addressInput != null && !UserPrefs.getAddress(this).isEmpty()) addressInput.setText(UserPrefs.getAddress(this));
                if (religionInput != null && !UserPrefs.getReligion(this).isEmpty()) {
                    String savedRel = UserPrefs.getReligion(this);
                    if (isOtherSelected(savedRel)) {
                        religionInput.setText(savedRel, false);
                        if (layoutReligionOther != null) layoutReligionOther.setVisibility(View.VISIBLE);
                    } else {
                        boolean isStandard = false;
                        try {
                            String[] options = getResources().getStringArray(R.array.religion_options);
                            for (String opt : options) {
                                if (opt.equalsIgnoreCase(savedRel)) {
                                    isStandard = true;
                                    break;
                                }
                            }
                        } catch (Exception ignored) {}
                        if (isStandard) {
                            religionInput.setText(savedRel, false);
                        } else {
                            religionInput.setText("OTHER", false);
                            if (layoutReligionOther != null) layoutReligionOther.setVisibility(View.VISIBLE);
                            if (religionOtherInput != null) religionOtherInput.setText(savedRel);
                        }
                    }
                }
                if (dobInput != null && !UserPrefs.getDateOfBirth(this).isEmpty()) dobInput.setText(UserPrefs.getDateOfBirth(this));
                if (ethnicityInput != null && !UserPrefs.getEthnicity(this).isEmpty()) {
                    String savedEth = UserPrefs.getEthnicity(this);
                    if (isOtherSelected(savedEth)) {
                        ethnicityInput.setText(savedEth, false);
                        if (layoutEthnicityOther != null) layoutEthnicityOther.setVisibility(View.VISIBLE);
                    } else {
                        boolean isStandard = false;
                        try {
                            String[] options = getResources().getStringArray(R.array.ethnicity_options);
                            for (String opt : options) {
                                if (opt.equalsIgnoreCase(savedEth)) {
                                    isStandard = true;
                                    break;
                                }
                            }
                        } catch (Exception ignored) {}
                        if (isStandard) {
                            ethnicityInput.setText(savedEth, false);
                        } else {
                            ethnicityInput.setText("OTHER", false);
                            if (layoutEthnicityOther != null) layoutEthnicityOther.setVisibility(View.VISIBLE);
                            if (ethnicityOtherInput != null) ethnicityOtherInput.setText(savedEth);
                        }
                    }
                }

                if (bloodType != null && !UserPrefs.getBloodType(this).isEmpty()) bloodType.setText(UserPrefs.getBloodType(this), false);
                if (allergiesInput != null && !UserPrefs.getAllergies(this).isEmpty()) allergiesInput.setText(UserPrefs.getAllergies(this), false);
                if (medicationsInput != null && !UserPrefs.getMedications(this).isEmpty()) medicationsInput.setText(UserPrefs.getMedications(this), false);
                if (organDonorInput != null && !UserPrefs.getOrganDonor(this).isEmpty()) organDonorInput.setText(UserPrefs.getOrganDonor(this), false);

                java.util.ArrayList<com.example.resqtap.contacts.EmergencyContact> contacts = UserPrefs.getEmergencyContacts(this);
                if (!contacts.isEmpty()) {
                    com.example.resqtap.contacts.EmergencyContact firstContact = contacts.get(0);
                    if (emergencyNameInput != null) emergencyNameInput.setText(firstContact.name);
                    if (emergencyPhoneInput != null && firstContact.phone != null) {
                        String emSaved = firstContact.phone.trim();
                        CountryCodeHelper.Country emMatched = null;
                        if (emSaved.startsWith("+")) {
                            for (CountryCodeHelper.Country c : CountryCodeHelper.getAllCountries()) {
                                if (emSaved.startsWith(c.dialCode)) {
                                    emMatched = c;
                                    break;
                                }
                            }
                        }
                        if (emMatched != null) {
                            if (tvEmergencyCountryFlag != null) tvEmergencyCountryFlag.setText(emMatched.flag);
                            if (tvEmergencyCountryCode != null) tvEmergencyCountryCode.setText(emMatched.dialCode);
                            emergencyPhoneInput.setText(emSaved.substring(emMatched.dialCode.length()).trim());
                        } else {
                            emergencyPhoneInput.setText(emSaved);
                        }
                    }
                    if (emergencyRelationInput != null) emergencyRelationInput.setText(firstContact.relationship, false);
                }
            }

            showStep2PersonalInfo();
        }

        emailLayout.setEndIconOnClickListener(v -> {});
        emailLayout.setEndIconCheckable(false);

        email.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                String value = s == null ? "" : s.toString().trim();
                emailAvailable = true;
                btnCreate.setEnabled(true);

                if (emailDebounce != null) handler.removeCallbacks(emailDebounce);
                if (value.isEmpty()) {
                    emailAvailable = true;
                    btnCreate.setEnabled(true);
                    emailLayout.setError(null); emailLayout.setErrorEnabled(false);
                    emailLayout.setEndIconDrawable(null);
                    emailLayout.setEndIconTintList(null);
                    return;
                }

                if (!isGmail(value)) {
                    emailAvailable = false;
                    emailLayout.setErrorEnabled(true);
                    emailLayout.setError(getString(R.string.gmail_only_warning));
                    emailLayout.setEndIconDrawable(R.drawable.ic_error_24);
                    emailLayout.setEndIconTintList(ColorStateList.valueOf(ContextCompat.getColor(RegisterActivity.this, R.color.brand_red)));
                    btnCreate.setEnabled(false);
                    return;
                }

                if (emailLayout.getError() != null) {
                    emailLayout.setError(null); emailLayout.setErrorEnabled(false);
                }
                emailLayout.setEndIconDrawable(null);
                emailLayout.setEndIconTintList(null);
                emailDebounce = () -> checkEmailAvailability(value, emailLayout, btnCreate);
                handler.postDelayed(emailDebounce, 200);
            }
        });

        TextInputLayout passwordLayout = findViewById(R.id.layout_password);
        TextInputLayout passwordConfirmLayout = findViewById(R.id.layout_password_confirm);

        password.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (passwordLayout != null) { passwordLayout.setError(null); passwordLayout.setErrorEnabled(false); }
            }
        });

        passwordConfirm.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (passwordConfirmLayout != null) { passwordConfirmLayout.setError(null); passwordConfirmLayout.setErrorEnabled(false); }
            }
        });

        btnCreate.setOnClickListener(v -> {
            String emailValue = email.getText() == null ? "" : email.getText().toString().trim();
            String passValue = password.getText() == null ? "" : password.getText().toString();
            String passConfirmValue = passwordConfirm.getText() == null ? "" : passwordConfirm.getText().toString();

            if (emailValue.isEmpty()) {
                emailLayout.setError("Please enter your email address");
                emailLayout.setEndIconDrawable(R.drawable.ic_error_circle_24);
                emailLayout.setEndIconTintList(null);
                scrollToField(emailLayout);
                return;
            }

            if (!isGmail(emailValue)) {
                emailLayout.setError(getString(R.string.gmail_only_warning));
                emailLayout.setEndIconDrawable(R.drawable.ic_error_circle_24);
                emailLayout.setEndIconTintList(null);
                scrollToField(emailLayout);
                return;
            }

            if (!emailAvailable) {
                emailLayout.setError(getString(R.string.toast_email_already_exists));
                emailLayout.setEndIconDrawable(R.drawable.ic_error_circle_24);
                emailLayout.setEndIconTintList(null);
                showAccountExistsDialog(emailValue);
                scrollToField(emailLayout);
                return;
            }

            if (passValue.isEmpty()) {
                if (passwordLayout != null) {
                    passwordLayout.setError("Please enter a password");
                    scrollToField(passwordLayout);
                }
                return;
            }

            String sopError = getPasswordSopError(passValue);
            if (sopError != null) {
                if (passwordLayout != null) {
                    passwordLayout.setError(sopError);
                    scrollToField(passwordLayout);
                }
                return;
            }

            if (passConfirmValue.isEmpty()) {
                if (passwordConfirmLayout != null) {
                    passwordConfirmLayout.setError("Please confirm your password");
                    scrollToField(passwordConfirmLayout);
                }
                return;
            }

            if (!passValue.equals(passConfirmValue)) {
                if (passwordConfirmLayout != null) {
                    passwordConfirmLayout.setError("Passwords do not match");
                    scrollToField(passwordConfirmLayout);
                }
                return;
            }

            com.google.android.material.checkbox.MaterialCheckBox cbTerms = findViewById(R.id.cb_terms);
            if (cbTerms != null && !cbTerms.isChecked()) {
                Toast.makeText(this, "Please agree to Safety Terms to continue", Toast.LENGTH_SHORT).show();
                scrollToField(cbTerms);
                return;
            }

            btnCreate.setEnabled(false);

            // Semak ketersediaan e-mel secara komprehensif merentasi Firebase Auth dan Supabase
            verifyEmailAvailabilityAsync(emailValue, isAvailable -> {
                btnCreate.setEnabled(true);
                if (!isAvailable) {
                    emailAvailable = false;
                    emailLayout.setError(getString(R.string.toast_email_already_exists));
                    emailLayout.setEndIconDrawable(R.drawable.ic_error_circle_24);
                    emailLayout.setEndIconTintList(null);
                    showAccountExistsDialog(emailValue);
                    scrollToField(emailLayout);
                    return;
                }

                emailAvailable = true;
                emailLayout.setError(null);
                emailLayout.setErrorEnabled(false);
                registeredEmail = emailValue;
                registeredPassword = passValue;
                showStep2PersonalInfo();
            });
        });

        btnNextToStep3.setOnClickListener(v -> {
            String firstNameValue = firstNameInput.getText() == null ? "" : firstNameInput.getText().toString().trim();
            String lastNameValue = lastNameInput.getText() == null ? "" : lastNameInput.getText().toString().trim();
            String icValue = icInput.getText() == null ? "" : icInput.getText().toString().trim();
            String genderValue = genderInput.getText() == null ? "" : genderInput.getText().toString().trim();
            String rawPhone = phoneInput.getText() == null ? "" : phoneInput.getText().toString().trim();
            String addressValue = addressInput.getText() == null ? "" : addressInput.getText().toString().trim();
            String religionSelection = religionInput.getText() == null ? "" : religionInput.getText().toString().trim();
            String religionOther = religionOtherInput == null || religionOtherInput.getText() == null ? "" : religionOtherInput.getText().toString().trim();
            String dobValue = dobInput.getText() == null ? "" : dobInput.getText().toString().trim();
            String ethnicitySelection = ethnicityInput.getText() == null ? "" : ethnicityInput.getText().toString().trim();
            String ethnicityOther = ethnicityOtherInput == null || ethnicityOtherInput.getText() == null ? "" : ethnicityOtherInput.getText().toString().trim();

            if (firstNameValue.isEmpty()) {
                if (firstNameLayout != null) {
                    firstNameLayout.setError(getString(R.string.error_required_first_name));
                    scrollToField(firstNameLayout);
                }
                return;
            }

            if (lastNameValue.isEmpty()) {
                if (lastNameLayout != null) {
                    lastNameLayout.setError(getString(R.string.error_required_last_name));
                    scrollToField(lastNameLayout);
                }
                return;
            }

            if (icValue.isEmpty()) {
                if (icLayout != null) {
                    icLayout.setError(getString(R.string.error_required_ic));
                    scrollToField(icLayout);
                }
                return;
            }

            String icDigits = icValue.replaceAll("[^0-9]", "");
            if (icDigits.length() < 6) {
                if (icLayout != null) {
                    icLayout.setError(getString(R.string.error_invalid_ic));
                    scrollToField(icLayout);
                }
                return;
            }

            if (genderValue.isEmpty()) {
                if (genderLayout != null) {
                    genderLayout.setError(getString(R.string.error_required_gender));
                    scrollToField(genderLayout);
                }
                return;
            }

            if (rawPhone.isEmpty()) {
                if (phoneLayout != null) {
                    phoneLayout.setError(getString(R.string.error_required_phone));
                    scrollToField(phoneLayout);
                }
                return;
            }

            String phoneDigits = rawPhone.replaceAll("[^0-9]", "");
            if (phoneDigits.length() < 7) {
                if (phoneLayout != null) {
                    phoneLayout.setError(getString(R.string.error_invalid_phone));
                    scrollToField(phoneLayout);
                }
                return;
            }

            if (addressValue.isEmpty()) {
                if (addressLayout != null) {
                    addressLayout.setError(getString(R.string.error_required_address));
                    scrollToField(addressLayout);
                }
                return;
            }

            if (religionSelection.isEmpty()) {
                if (religionLayout != null) {
                    religionLayout.setError(getString(R.string.error_required_religion));
                    scrollToField(religionLayout);
                }
                return;
            }

            if (isOtherSelected(religionSelection) && religionOther.isEmpty()) {
                if (layoutReligionOther != null) {
                    layoutReligionOther.setError(getString(R.string.error_specify_other));
                    scrollToField(layoutReligionOther);
                }
                return;
            }

            if (dobValue.isEmpty()) {
                if (dobLayout != null) {
                    dobLayout.setError(getString(R.string.error_required_dob));
                    scrollToField(dobLayout);
                }
                return;
            }

            if (ethnicitySelection.isEmpty()) {
                if (ethnicityLayout != null) {
                    ethnicityLayout.setError(getString(R.string.error_required_ethnicity));
                    scrollToField(ethnicityLayout);
                }
                return;
            }

            if (isOtherSelected(ethnicitySelection) && ethnicityOther.isEmpty()) {
                if (layoutEthnicityOther != null) {
                    layoutEthnicityOther.setError(getString(R.string.error_specify_other));
                    scrollToField(layoutEthnicityOther);
                }
                return;
            }

            showStep3MedicalEmergency();
        });

        btnSaveDetails.setOnClickListener(v -> {
            String firstNameValue = firstNameInput.getText() == null ? "" : firstNameInput.getText().toString().trim().toUpperCase(java.util.Locale.ROOT);
            String lastNameValue = lastNameInput.getText() == null ? "" : lastNameInput.getText().toString().trim().toUpperCase(java.util.Locale.ROOT);
            String nameValue = (firstNameValue + " " + lastNameValue).trim();
            String icValue = icInput.getText() == null ? "" : icInput.getText().toString().trim();
            String genderValue = genderInput.getText() == null ? "" : genderInput.getText().toString().trim();

            String rawPhone = phoneInput.getText() == null ? "" : phoneInput.getText().toString().trim();
            String codeVal = tvCountryCode != null ? tvCountryCode.getText().toString().trim() : "+60";
            String phoneValue;
            if (rawPhone.isEmpty()) {
                phoneValue = "";
            } else if (rawPhone.startsWith("+")) {
                phoneValue = rawPhone;
            } else {
                String clean = rawPhone.startsWith("0") ? rawPhone.substring(1) : rawPhone;
                phoneValue = codeVal + clean;
            }

            String addressValue = addressInput.getText() == null ? "" : addressInput.getText().toString().trim().toUpperCase(java.util.Locale.ROOT);
            String religionSelection = religionInput.getText() == null ? "" : religionInput.getText().toString().trim().toUpperCase(java.util.Locale.ROOT);
            String religionOther = religionOtherInput == null || religionOtherInput.getText() == null ? "" : religionOtherInput.getText().toString().trim().toUpperCase(java.util.Locale.ROOT);
            String religionValue = isOtherSelected(religionSelection) ? (!religionOther.isEmpty() ? religionOther : religionSelection) : religionSelection;

            String dobValue = dobInput.getText() == null ? "" : dobInput.getText().toString().trim();

            String ethnicitySelection = ethnicityInput.getText() == null ? "" : ethnicityInput.getText().toString().trim().toUpperCase(java.util.Locale.ROOT);
            String ethnicityOther = ethnicityOtherInput == null || ethnicityOtherInput.getText() == null ? "" : ethnicityOtherInput.getText().toString().trim().toUpperCase(java.util.Locale.ROOT);
            String ethnicityValue = isOtherSelected(ethnicitySelection) ? (!ethnicityOther.isEmpty() ? ethnicityOther : ethnicitySelection) : ethnicitySelection;

            String bloodValue = bloodType.getText() == null ? "" : bloodType.getText().toString().trim();

            String allergiesSelection = allergiesInput.getText() == null ? "" : allergiesInput.getText().toString().trim();
            String allergiesOther = allergiesOtherInput == null || allergiesOtherInput.getText() == null ? "" : allergiesOtherInput.getText().toString().trim();
            String allergiesValue = buildMultiSelectValue(allergiesSelection, allergiesOther);

            String medicationsSelection = medicationsInput.getText() == null ? "" : medicationsInput.getText().toString().trim();
            String medicationsOther = medicationsOtherInput == null || medicationsOtherInput.getText() == null ? "" : medicationsOtherInput.getText().toString().trim();
            String medicationsValue = buildMultiSelectValue(medicationsSelection, medicationsOther);

            String organDonorValue = organDonorInput.getText() == null ? "" : organDonorInput.getText().toString().trim();

            String emNameValue = emergencyNameInput.getText() == null ? "" : emergencyNameInput.getText().toString().trim().toUpperCase(java.util.Locale.ROOT);

            String rawEmPhone = emergencyPhoneInput.getText() == null ? "" : emergencyPhoneInput.getText().toString().trim();
            String emCodeVal = tvEmergencyCountryCode != null ? tvEmergencyCountryCode.getText().toString().trim() : "+60";
            String emPhoneValue;
            if (rawEmPhone.isEmpty()) {
                emPhoneValue = "";
            } else if (rawEmPhone.startsWith("+")) {
                emPhoneValue = rawEmPhone;
            } else {
                String cleanEm = rawEmPhone.startsWith("0") ? rawEmPhone.substring(1) : rawEmPhone;
                emPhoneValue = emCodeVal + cleanEm;
            }

            String emRelationSelection = emergencyRelationInput.getText() == null ? "" : emergencyRelationInput.getText().toString().trim();
            String emRelationOther = emergencyRelationOtherInput == null || emergencyRelationOtherInput.getText() == null ? "" : emergencyRelationOtherInput.getText().toString().trim().toUpperCase(java.util.Locale.ROOT);
            String emRelationValue = isOtherSelected(emRelationSelection) ? (!emRelationOther.isEmpty() ? emRelationOther : emRelationSelection) : emRelationSelection;

            // Personal info validation
            if (firstNameValue.isEmpty() || lastNameValue.isEmpty() || icValue.isEmpty() || genderValue.isEmpty()
                    || phoneValue.isEmpty() || addressValue.isEmpty() || religionSelection.isEmpty() || dobValue.isEmpty() || ethnicitySelection.isEmpty()) {
                showStep2PersonalInfo();
                Toast.makeText(this, "Please complete all personal information fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Medical and emergency info validation
            if (bloodValue.isEmpty()) {
                if (layoutBloodType != null) {
                    layoutBloodType.setError(getString(R.string.error_required_blood_type));
                    scrollToField(layoutBloodType);
                }
                return;
            }

            if (allergiesSelection.isEmpty()) {
                if (layoutAllergies != null) {
                    layoutAllergies.setError(getString(R.string.error_required_allergies));
                    scrollToField(layoutAllergies);
                }
                return;
            }

            if (isOtherSelected(allergiesSelection) && allergiesOther.isEmpty()) {
                if (layoutAllergiesOther != null) {
                    layoutAllergiesOther.setError(getString(R.string.error_specify_other));
                    scrollToField(layoutAllergiesOther);
                }
                return;
            }

            if (medicationsSelection.isEmpty()) {
                if (layoutMedications != null) {
                    layoutMedications.setError(getString(R.string.error_required_medications));
                    scrollToField(layoutMedications);
                }
                return;
            }

            if (isOtherSelected(medicationsSelection) && medicationsOther.isEmpty()) {
                if (layoutMedicationsOther != null) {
                    layoutMedicationsOther.setError(getString(R.string.error_specify_other));
                    scrollToField(layoutMedicationsOther);
                }
                return;
            }

            if (organDonorValue.isEmpty()) {
                if (layoutOrganDonor != null) {
                    layoutOrganDonor.setError(getString(R.string.error_required_organ_donor));
                    scrollToField(layoutOrganDonor);
                }
                return;
            }

            if (emNameValue.isEmpty()) {
                if (layoutEmergencyName != null) {
                    layoutEmergencyName.setError(getString(R.string.error_required_emergency_name));
                    scrollToField(layoutEmergencyName);
                }
                return;
            }

            if (rawEmPhone.isEmpty()) {
                if (layoutEmergencyPhone != null) {
                    layoutEmergencyPhone.setError(getString(R.string.error_required_emergency_phone));
                    scrollToField(layoutEmergencyPhone);
                }
                return;
            }

            String emDigits = rawEmPhone.replaceAll("[^0-9]", "");
            if (emDigits.length() < 7) {
                if (layoutEmergencyPhone != null) {
                    layoutEmergencyPhone.setError(getString(R.string.error_invalid_emergency_phone));
                    scrollToField(layoutEmergencyPhone);
                }
                return;
            }

            if (emRelationSelection.isEmpty()) {
                if (layoutEmergencyRelation != null) {
                    layoutEmergencyRelation.setError(getString(R.string.error_required_emergency_relation));
                    scrollToField(layoutEmergencyRelation);
                }
                return;
            }

            if (isOtherSelected(emRelationSelection) && emRelationOther.isEmpty()) {
                if (layoutEmergencyRelationOther != null) {
                    layoutEmergencyRelationOther.setError(getString(R.string.error_specify_other));
                    scrollToField(layoutEmergencyRelationOther);
                }
                return;
            }

            btnSaveDetails.setEnabled(false);

            // Jikalau pengguna sudah ada sesi login (cth: complete profile mode sedia ada)
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                registeredUid = currentUser.getUid();
                saveProfileToDatabase(registeredUid, registeredEmail, firstNameValue, lastNameValue, nameValue, icValue, genderValue, phoneValue, addressValue, religionValue, dobValue, ethnicityValue, bloodValue, allergiesValue, medicationsValue, organDonorValue, emNameValue, emPhoneValue, emRelationValue, btnSaveDetails);
                return;
            }

            // Jika pendaftaran berasal daripada Google Sign-In, cipta sesi Firebase Auth di sini sahaja
            if (fromGoogleSignIn) {
                AuthCredential credential = null;
                if (googleIdToken != null && !googleIdToken.trim().isEmpty()) {
                    credential = GoogleAuthProvider.getCredential(googleIdToken, null);
                } else {
                    GoogleSignInAccount gAccount = GoogleSignIn.getLastSignedInAccount(this);
                    if (gAccount != null && gAccount.getIdToken() != null) {
                        credential = GoogleAuthProvider.getCredential(gAccount.getIdToken(), null);
                    }
                }

                if (credential != null) {
                    FirebaseAuth.getInstance().signInWithCredential(credential)
                            .addOnSuccessListener(result -> {
                                if (result.getUser() == null) {
                                    btnSaveDetails.setEnabled(true);
                                    Toast.makeText(this, R.string.toast_register_failed, Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                registeredUid = result.getUser().getUid();
                                saveProfileToDatabase(registeredUid, registeredEmail, firstNameValue, lastNameValue, nameValue, icValue, genderValue, phoneValue, addressValue, religionValue, dobValue, ethnicityValue, bloodValue, allergiesValue, medicationsValue, organDonorValue, emNameValue, emPhoneValue, emRelationValue, btnSaveDetails);
                            })
                            .addOnFailureListener(e -> {
                                btnSaveDetails.setEnabled(true);
                                Log.e(TAG, "Google sign in failed at final save", e);
                                Toast.makeText(this, e.getMessage() != null ? e.getMessage() : getString(R.string.toast_register_failed), Toast.LENGTH_LONG).show();
                            });
                    return;
                }
            }

            // Cipta akaun Firebase Auth dan simpan ke database sekaligus untuk pendaftaran e-mel
            FirebaseAuth.getInstance()
                    .createUserWithEmailAndPassword(registeredEmail, registeredPassword)
                    .addOnSuccessListener(result -> {
                        if (result.getUser() == null) {
                            btnSaveDetails.setEnabled(true);
                            Toast.makeText(this, R.string.toast_register_failed, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        registeredUid = result.getUser().getUid();
                        saveProfileToDatabase(registeredUid, registeredEmail, firstNameValue, lastNameValue, nameValue, icValue, genderValue, phoneValue, addressValue, religionValue, dobValue, ethnicityValue, bloodValue, allergiesValue, medicationsValue, organDonorValue, emNameValue, emPhoneValue, emRelationValue, btnSaveDetails);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "createUserWithEmailAndPassword failed at final save", e);
                        if (e instanceof FirebaseAuthUserCollisionException) {
                            btnSaveDetails.setEnabled(true);
                            Toast.makeText(this, R.string.toast_email_already_exists, Toast.LENGTH_LONG).show();
                            showAccountExistsDialog(registeredEmail);
                            return;
                        }
                        btnSaveDetails.setEnabled(true);
                        if (e instanceof FirebaseAuthWeakPasswordException) {
                            Toast.makeText(this, R.string.toast_register_weak_password, Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (e instanceof FirebaseAuthInvalidCredentialsException) {
                            Toast.makeText(this, R.string.toast_register_invalid_email, Toast.LENGTH_LONG).show();
                            return;
                        }
                        String detail = e.getMessage() == null ? "" : e.getMessage().trim();
                        if (!detail.isEmpty()) {
                            Toast.makeText(this, getString(R.string.toast_register_failed) + " " + detail, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, R.string.toast_register_failed, Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        btnGetStarted.setOnClickListener(v -> showTermsOfServiceDialog());

        btnBack.setOnClickListener(v -> cancelRegistrationAndGoLogin());
    }

    /**
     * Paparkan dialog Terms of Service selepas pendaftaran selesai.
     * Pengguna wajib skrol kandungan hingga ke bawah sebelum butang ACCEPT diaktifkan.
     */
    private void showTermsOfServiceDialog() {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_terms_of_service, null);
        androidx.core.widget.NestedScrollView scrollTos = dialogView.findViewById(R.id.scroll_tos);
        com.google.android.material.button.MaterialButton btnAccept = dialogView.findViewById(R.id.btn_accept_tos);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.Theme_ResQTap_CustomDialog)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            int dialogWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.88);
            dialog.getWindow().setLayout(dialogWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(android.view.Gravity.CENTER);
        }

        if (scrollTos != null && btnAccept != null) {
            scrollTos.setOnScrollChangeListener((androidx.core.widget.NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                android.view.View child = v.getChildAt(0);
                if (child != null) {
                    int diff = (child.getBottom() - (v.getHeight() + scrollY));
                    if (diff <= 35) {
                        if (!btnAccept.isEnabled()) {
                            btnAccept.setEnabled(true);
                            btnAccept.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK));
                            btnAccept.setTextColor(android.graphics.Color.WHITE);
                        }
                    }
                }
            });

            // Semak sekiranya teks muat sepenuhnya pada skrin tanpa perlu skrol
            scrollTos.post(() -> {
                android.view.View child = scrollTos.getChildAt(0);
                if (child != null && child.getHeight() <= scrollTos.getHeight()) {
                    btnAccept.setEnabled(true);
                    btnAccept.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK));
                    btnAccept.setTextColor(android.graphics.Color.WHITE);
                }
            });

            btnAccept.setOnClickListener(v -> {
                UserPrefs.setTosAccepted(RegisterActivity.this, true);
                dialog.dismiss();
                Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            });
        }
    }

    /** Simpan profil penuh pengguna ke Firebase Realtime Database dan selesaikan pendaftaran. */
    private void saveProfileToDatabase(String uid, String emailVal, String firstNameValue, String lastNameValue, String nameValue, String icValue, String genderValue,
                                       String phoneValue, String addressValue, String religionValue, String dobValue,
                                       String ethnicityValue, String bloodValue, String allergiesValue,
                                       String medicationsValue, String organDonorValue, String emNameValue,
                                       String emPhoneValue, String emRelationValue, MaterialButton btnSaveDetails) {
        Map<String, Object> user = new HashMap<>();
        user.put("name", nameValue);
        user.put("firstName", firstNameValue);
        user.put("lastName", lastNameValue);
        user.put("email", String.valueOf(emailVal == null ? "" : emailVal).trim().toLowerCase(java.util.Locale.ROOT));
        user.put("icNumber", icValue);
        user.put("gender", genderValue);
        user.put("phone", phoneValue);
        user.put("address", addressValue);
        user.put("religion", religionValue);
        user.put("dob", dobValue);
        user.put("ethnicity", ethnicityValue);

        user.put("bloodType", bloodValue);
        user.put("allergies", allergiesValue);
        user.put("existingConditions", medicationsValue);
        user.put("medications", medicationsValue);
        user.put("organDonor", organDonorValue);

        if (!photoB64.isEmpty()) {
            user.put("photoB64", photoB64);
        }
        user.put("createdAt", System.currentTimeMillis());

        ArrayList<EmergencyContact> contactList = new ArrayList<>();
        if (!emNameValue.isEmpty() || !emPhoneValue.isEmpty()) {
            EmergencyContact contact = new EmergencyContact(
                    UUID.randomUUID().toString(),
                    emNameValue,
                    emRelationValue,
                    emPhoneValue
            );
            contactList.add(contact);
            user.put("emergencyContacts", contactList);
        }

        // Simpan UserPrefs segera
        UserPrefs.setActiveRoomCode(this, "");
        UserPrefs.setUid(this, uid);
        UserPrefs.setEmail(this, emailVal);
        UserPrefs.setName(this, nameValue);
        UserPrefs.setFirstName(this, firstNameValue);
        UserPrefs.setLastName(this, lastNameValue);
        UserPrefs.setIcNumber(this, icValue);
        UserPrefs.setGender(this, genderValue);
        UserPrefs.setPhoneNumber(this, phoneValue);
        UserPrefs.setAddress(this, addressValue);
        UserPrefs.setReligion(this, religionValue);
        UserPrefs.setDateOfBirth(this, dobValue);
        UserPrefs.setEthnicity(this, ethnicityValue);
        UserPrefs.setBloodType(this, bloodValue);
        UserPrefs.setAllergies(this, allergiesValue);
        UserPrefs.setExistingConditions(this, medicationsValue);
        UserPrefs.setMedications(this, medicationsValue);
        UserPrefs.setOrganDonor(this, organDonorValue);
        if (!contactList.isEmpty()) {
            UserPrefs.setEmergencyContacts(this, contactList);
        }
        if (!photoB64.isEmpty()) {
            UserPrefs.setPhotoB64(this, photoB64);
        }

        // Simpan profil & kad perubatan secara langsung dan eksklusif ke SUPABASE (tiada di Firebase RTDB)
        try {
            com.example.resqtap.supabase.SupabaseManager.getInstance().upsertProfile(uid, emailVal, nameValue, phoneValue, photoB64, null);
            com.example.resqtap.supabase.SupabaseManager.getInstance().upsertMedicalCard(uid, bloodValue, allergiesValue, medicationsValue, "", null);
        } catch (Exception ignored) {
        }

        // Profil kini lengkap
        isRegistrationCompleted = true;

        // Terus tunjuk step 4
        showStep4GetStarted();

        assignPublicId(uid, publicId -> {
            UserPrefs.setPublicId(RegisterActivity.this, publicId);
        });
    }

    /**
     * Transitions UI to Step 2 (Personal Information).
     * Validates Step 1 credentials, creates the initial Firebase Authentication user,
     * and displays profile inputs (Name, IC, Phone, DOB, Gender, Avatar upload).
     */
    private void showStep2PersonalInfo() {
        if (titleText != null) titleText.setText(R.string.register_step2_title);
        if (subtitleText != null) {
            subtitleText.setText(R.string.register_step2_subtitle);
            subtitleText.setVisibility(View.VISIBLE);
        }

        if (step1Credentials != null) step1Credentials.setVisibility(View.GONE);
        if (step3MedicalEmergency != null) step3MedicalEmergency.setVisibility(View.GONE);
        if (step4GetStarted != null) step4GetStarted.setVisibility(View.GONE);

        if (step2PersonalInfo != null) {
            step2PersonalInfo.setVisibility(View.VISIBLE);
            step2PersonalInfo.setAlpha(0f);
            step2PersonalInfo.animate().alpha(1f).setDuration(220).start();
        }
    }

    /**
     * Transitions UI to Step 3 (Medical & Emergency Information).
     * Validates Step 2 fields and collects critical first-responder data (Blood type, Allergies, Emergency contact).
     */
    private void showStep3MedicalEmergency() {
        if (titleText != null) titleText.setText(R.string.register_step3_title);
        if (subtitleText != null) {
            subtitleText.setText(R.string.register_step3_subtitle);
            subtitleText.setVisibility(View.VISIBLE);
        }

        if (step1Credentials != null) step1Credentials.setVisibility(View.GONE);
        if (step2PersonalInfo != null) step2PersonalInfo.setVisibility(View.GONE);
        if (step4GetStarted != null) step4GetStarted.setVisibility(View.GONE);

        if (step3MedicalEmergency != null) {
            step3MedicalEmergency.setVisibility(View.VISIBLE);
            step3MedicalEmergency.setAlpha(0f);
            step3MedicalEmergency.animate().alpha(1f).setDuration(220).start();
        }
    }

    /**
     * Finalizes registration (Step 4).
     * Generates a unique user Public Tag via atomic transaction, persists full profile to /users/{uid},
     * stores user session flags, and displays the onboarding completion screen.
     */
    private void showStep4GetStarted() {
        if (titleText != null) titleText.setText(R.string.register_step4_title);
        if (subtitleText != null) {
            subtitleText.setText(R.string.register_step4_subtitle);
            subtitleText.setVisibility(View.VISIBLE);
        }

        if (step1Credentials != null) step1Credentials.setVisibility(View.GONE);
        if (step2PersonalInfo != null) step2PersonalInfo.setVisibility(View.GONE);
        if (step3MedicalEmergency != null) step3MedicalEmergency.setVisibility(View.GONE);

        if (step4GetStarted != null) {
            step4GetStarted.setVisibility(View.VISIBLE);
            step4GetStarted.setAlpha(0f);
            step4GetStarted.setTranslationY(320f);

            // 1. Animasi kad meluncur naik dari bawah (slide up from bottom)
            step4GetStarted.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(450)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f))
                    .start();

            // 2. Animasi bulatan hijau pudar mengembang
            View circleBg = findViewById(R.id.view_circle_bg);
            if (circleBg != null) {
                circleBg.setScaleX(0.2f);
                circleBg.setScaleY(0.2f);
                circleBg.setAlpha(0f);
                circleBg.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setStartDelay(100)
                        .setDuration(380)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.3f))
                        .start();
            }

            // 3. Animasi tanda semak hijau muncul dengan lantunan spring (animated tick pop-in)
            View successTick = findViewById(R.id.img_success_tick);
            if (successTick != null) {
                successTick.setScaleX(0f);
                successTick.setScaleY(0f);
                successTick.setAlpha(0f);
                successTick.setRotation(-40f);
                successTick.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .rotation(0f)
                        .alpha(1f)
                        .setStartDelay(220)
                        .setDuration(480)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f))
                        .start();
            }
        }
    }

    /** Paparkan DobPicker. */
    private void showDobPicker(TextInputEditText dobInput) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int year = cal.get(java.util.Calendar.YEAR) - 20;
        int month = cal.get(java.util.Calendar.MONTH);
        int day = cal.get(java.util.Calendar.DAY_OF_MONTH);

        String current = dobInput.getText() == null ? "" : dobInput.getText().toString().trim();
        if (!current.isEmpty()) {
            try {
                String[] parts = current.split("/");
                if (parts.length == 3) {
                    day = Integer.parseInt(parts[0]);
                    month = Integer.parseInt(parts[1]) - 1;
                    year = Integer.parseInt(parts[2]);
                }
            } catch (Exception ignored) {}
        }

        android.app.DatePickerDialog dialog = new android.app.DatePickerDialog(this, R.style.DatePickerTheme, (view, y, m, d) -> {
            String formatted = String.format(java.util.Locale.US, "%02d/%02d/%04d", d, m + 1, y);
            dobInput.setText(formatted);
        }, year, month, day);

        dialog.setOnShowListener(d -> {
            try {
                android.widget.Button pos = dialog.getButton(android.app.DatePickerDialog.BUTTON_POSITIVE);
                android.widget.Button neg = dialog.getButton(android.app.DatePickerDialog.BUTTON_NEGATIVE);
                if (pos != null) {
                    pos.setTextColor(android.graphics.Color.parseColor("#D81B60"));
                    pos.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
                    pos.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
                }
                if (neg != null) {
                    neg.setTextColor(android.graphics.Color.parseColor("#475569"));
                    neg.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
                    neg.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
                }
                android.view.Window window = dialog.getWindow();
                if (window != null) {
                    window.setGravity(android.view.Gravity.CENTER);
                    window.setLayout(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                }
            } catch (Exception ignored) {}
        });

        dialog.show();
    }

    private void scrollToField(View targetView) {
        if (targetView == null) return;
        targetView.requestFocus();
        androidx.core.widget.NestedScrollView authPanel = findViewById(R.id.auth_panel);
        if (authPanel != null) {
            authPanel.post(() -> {
                int[] targetLoc = new int[2];
                targetView.getLocationOnScreen(targetLoc);
                int[] scrollLoc = new int[2];
                authPanel.getLocationOnScreen(scrollLoc);
                int scrollY = targetLoc[1] - scrollLoc[1] + authPanel.getScrollY();
                authPanel.smoothScrollTo(0, Math.max(0, scrollY - 80));
            });
        }
    }

    public static String sanitizeEmailForDb(String email) {
        if (email == null) return "";
        return email.trim().toLowerCase(java.util.Locale.ROOT)
                .replace(".", "_")
                .replace("@", "_at_");
    }

    private interface EmailAvailabilityCheckCallback {
        void onResult(boolean isAvailable);
    }

    /**
     * Semak ketersediaan e-mel secara komprehensif merentasi Firebase Auth dan Supabase.
     * Jika e-mel sudah didaftarkan melalui Google Sign-In atau e-mel/kata laluan, pendaftaran e-mel dihalang serta-merta.
     */
    private void verifyEmailAvailabilityAsync(String email, EmailAvailabilityCheckCallback callback) {
        String query = String.valueOf(email == null ? "" : email).trim().toLowerCase(java.util.Locale.ROOT);
        if (query.isEmpty() || !isGmail(query)) {
            if (callback != null) callback.onResult(false);
            return;
        }

        // 1. Semak Firebase Authentication (Google SignIn / Email-Password)
        FirebaseAuth.getInstance().fetchSignInMethodsForEmail(query).addOnCompleteListener(authTask -> {
            if (authTask.isSuccessful() && authTask.getResult() != null) {
                java.util.List<String> methods = authTask.getResult().getSignInMethods();
                if (methods != null && !methods.isEmpty()) {
                    // E-mel sudah wujud dalam Firebase Auth (Google atau Password)!
                    if (callback != null) callback.onResult(false);
                    return;
                }
            }

            // 2. Semak pangkalan data Supabase profiles
            com.example.resqtap.supabase.SupabaseManager.getInstance().checkEmailExists(query, new com.example.resqtap.supabase.SupabaseManager.Callback<Boolean>() {
                @Override
                public void onSuccess(Boolean existsInSupabase) {
                    if (Boolean.TRUE.equals(existsInSupabase)) {
                        if (callback != null) callback.onResult(false);
                    } else {
                        if (callback != null) callback.onResult(true);
                    }
                }

                @Override
                public void onError(Exception error) {
                    if (callback != null) callback.onResult(true);
                }
            });
        });
    }

    /** Semak dan sahkan EmailAvailability secara langsung melalui Firebase Auth dan Supabase. */
    private void checkEmailAvailability(String email, TextInputLayout emailLayout, MaterialButton btnCreate) {
        String query = email.trim().toLowerCase(java.util.Locale.ROOT);
        if (query.isEmpty() || !isGmail(query)) return;

        TextInputEditText inputEmail = findViewById(R.id.input_email);
        String currentInput = inputEmail != null && inputEmail.getText() != null ? inputEmail.getText().toString().trim().toLowerCase(java.util.Locale.ROOT) : "";
        if (!query.equals(currentInput)) return;

        verifyEmailAvailabilityAsync(query, isAvailable -> {
            TextInputEditText currentEditText = findViewById(R.id.input_email);
            String liveInput = currentEditText != null && currentEditText.getText() != null ? currentEditText.getText().toString().trim().toLowerCase(java.util.Locale.ROOT) : "";
            if (!query.equals(liveInput)) return;

            emailAvailable = isAvailable;
            if (isAvailable) {
                emailLayout.setError(null);
                emailLayout.setErrorEnabled(false);
                emailLayout.setEndIconDrawable(R.drawable.ic_check_24);
                emailLayout.setEndIconTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.success_green)));
                btnCreate.setEnabled(true);
            } else {
                emailLayout.setError(getString(R.string.toast_email_already_exists));
                emailLayout.setEndIconDrawable(R.drawable.ic_error_circle_24);
                emailLayout.setEndIconTintList(null);
                btnCreate.setEnabled(false);
            }
        });
    }

    private interface PublicIdCallback {
        void onAssigned(String publicId);
    }

    /**
     * Generates and assigns a unique public identifier tag (e.g. Username#1234).
     * Uses a SecureRandom 4-digit code and checks availability to prevent duplicate identity tags.
     *
     * @param uid Target Firebase user UID
     * @param callback Callback invoked once tag is assigned
     */
    private void assignPublicId(String uid, PublicIdCallback callback) {
        String publicId = com.example.resqtap.friend.FirebaseFriendClient.format4DigitId(uid, "");
        if (callback != null) {
            callback.onAssigned(publicId);
        }
    }

    /** Semak dan sahkan Gmail. */
    private boolean isGmail(String email) {
        String e = String.valueOf(email == null ? "" : email).trim().toLowerCase();
        return isAllowedEmailDomain(e);
    }

    /** Semak dan sahkan AllowedEmailDomain. */
    private boolean isAllowedEmailDomain(String email) {
        if (email == null) return false;
        String e = email.trim().toLowerCase();
        return (e.endsWith("@gmail.com") || e.endsWith("@resqtap.com"))
                && (e.length() > "@gmail.com".length() || e.length() > "@resqtap.com".length());
    }

    private String uriToBase64(Uri uri) {
        try {
            java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(inputStream);
            if (bitmap == null) return "";
            java.io.ByteArrayOutputStream byteArrayOutputStream = new java.io.ByteArrayOutputStream();
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            return android.util.Base64.encodeToString(byteArray, android.util.Base64.DEFAULT);
        } catch (Exception e) {
            return "";
        }
    }

    private void forceHeavyText(TextView textView) {
        if (textView == null) return;
        textView.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        textView.getPaint().setFakeBoldText(true);
        textView.invalidate();
    }

    private void cancelRegistrationAndGoLogin() {
        String uid = registeredUid.isEmpty()
                ? (FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : completeUid)
                : registeredUid;
        if (uid == null || uid.trim().isEmpty()) {
            uid = UserPrefs.getUid(this);
        }

        final String targetUid = String.valueOf(uid == null ? "" : uid).trim();

        if (!targetUid.isEmpty()) {
            try {
                FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                        .getReference("users")
                        .child(targetUid)
                        .removeValue();
            } catch (Exception e) {
                Log.e(TAG, "failed to remove user node on cancel", e);
            }
        }

        // Padam rekod e-mel jika sempat didaftarkan
        String emailToClean = !registeredEmail.isEmpty() ? registeredEmail : UserPrefs.getEmail(this);
        if (emailToClean.isEmpty() && FirebaseAuth.getInstance().getCurrentUser() != null) {
            emailToClean = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        }
        if (emailToClean != null && !emailToClean.isEmpty()) {
            String sanitized = sanitizeEmailForDb(emailToClean);
            if (!sanitized.isEmpty()) {
                try {
                    FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                            .getReference("registeredEmails")
                            .child(sanitized)
                            .removeValue();
                } catch (Exception ignored) {}
            }
        }

        // Sign out Google Client supaya sesi Google dibersihkan
        try {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build();
            GoogleSignIn.getClient(this, gso).signOut();
        } catch (Exception ignored) {}

        try {
            com.google.firebase.auth.FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
            if (current != null && !fromGoogleSignIn && !completeProfileMode) {
                current.delete().addOnCompleteListener(task -> {
                    FirebaseAuth.getInstance().signOut();
                    UserPrefs.clearAccountData(this);
                    finishToLogin();
                });
                return;
            }
        } catch (Exception ignored) {}
        FirebaseAuth.getInstance().signOut();
        UserPrefs.clearAccountData(this);
        finishToLogin();
    }

    /** Paparkan dialog jika akaun sudah berdaftar dan beri pilihan Login atau Reset Password. */
    private void showAccountExistsDialog(String emailValue) {
        if (isFinishing() || isDestroyed()) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.account_exists_dialog_title)
                .setMessage(getString(R.string.account_exists_dialog_msg, emailValue))
                .setPositiveButton(R.string.go_to_login, (d, w) -> finishToLogin(emailValue))
                .setNegativeButton(R.string.reset_password, (d, w) -> {
                    Intent intent = new Intent(this, ForgotPasswordActivity.class);
                    intent.putExtra("email", emailValue);
                    startActivity(intent);
                })
                .setNeutralButton(R.string.cancel, (d, w) -> d.dismiss())
                .show();
    }

    private void finishToLogin() {
        finishToLogin(registeredEmail);
    }

    private void finishToLogin(String emailToPass) {
        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (emailToPass != null && !emailToPass.trim().isEmpty()) {
            intent.putExtra("email", emailToPass.trim().toLowerCase(java.util.Locale.ROOT));
        }
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!isRegistrationCompleted && isFinishing()) {
            try {
                // Jangan padam akaun Google atau sesi pengguna jika tidak sengaja keluar
                FirebaseAuth.getInstance().signOut();
            } catch (Exception ignored) {}
        }
    }

    /** Buka kamera hadapan sahaja secara langsung untuk swafoto profil. */
    private void launchFrontCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission.launch(Manifest.permission.CAMERA);
            return;
        }

        try {
            java.io.File cacheDir = new java.io.File(getCacheDir(), "camera");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            java.io.File photoFile = new java.io.File(cacheDir, "selfie_" + System.currentTimeMillis() + ".jpg");
            tempCameraUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    photoFile
            );

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, tempCameraUri);
            // Meminta kamera depan secara eksplisit merentasi pelbagai pengeluar peranti Android
            intent.putExtra("android.intent.extras.CAMERA_FACING", 1); // 1 = Front Camera
            intent.putExtra("android.intent.extras.LENS_FACING_FRONT", 1);
            intent.putExtra("android.intent.extra.USE_FRONT_CAMERA", true);
            intent.putExtra("camerafacing", "front");
            intent.putExtra("previous_mode", "front");
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            takePhotoLauncher.launch(intent);
        } catch (Exception e) {
            Log.w(TAG, "FileProvider camera launch failed, fallback to basic intent", e);
            Intent fallbackIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            fallbackIntent.putExtra("android.intent.extras.CAMERA_FACING", 1);
            fallbackIntent.putExtra("android.intent.extras.LENS_FACING_FRONT", 1);
            fallbackIntent.putExtra("android.intent.extra.USE_FRONT_CAMERA", true);
            fallbackIntent.putExtra("camerafacing", "front");
            fallbackIntent.putExtra("previous_mode", "front");
            try {
                takePhotoLauncher.launch(fallbackIntent);
            } catch (Exception ex) {
                Toast.makeText(this, "Could not launch camera: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    /** Muat turun gambar profil Google dan tetapkan pada avatar serta tukar ke photoB64 secara automatik. */
    private void loadAndSetGoogleProfilePhoto(String photoUrl) {
        if (photoUrl == null || photoUrl.trim().isEmpty()) return;
        final String rawUrl = photoUrl.trim();
        final String highResUrl = AvatarUtils.getHighResUrl(rawUrl);
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Bitmap bitmap = downloadBitmapFromUrl(highResUrl);
                if (bitmap == null && !highResUrl.equals(rawUrl)) {
                    bitmap = downloadBitmapFromUrl(rawUrl);
                }
                if (bitmap != null) {
                    highResAvatarBitmap = bitmap;
                    Bitmap b64Bitmap = bitmap;
                    if (b64Bitmap.getWidth() > 512 || b64Bitmap.getHeight() > 512) {
                        float ratio = Math.min(512f / b64Bitmap.getWidth(), 512f / b64Bitmap.getHeight());
                        int w = Math.round(b64Bitmap.getWidth() * ratio);
                        int h = Math.round(b64Bitmap.getHeight() * ratio);
                        b64Bitmap = Bitmap.createScaledBitmap(b64Bitmap, w, h, true);
                    }
                    final Bitmap finalBmp = bitmap;
                    final String b64 = bitmapToBase64(b64Bitmap);
                    handler.post(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        photoB64 = b64;
                        UserPrefs.setPhotoB64(RegisterActivity.this, b64);
                        if (imgAvatar != null) {
                            imgAvatar.setImageBitmap(finalBmp);
                            imgAvatar.setImageTintList(null);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to download Google profile photo", e);
            }
        });
    }

    private Bitmap downloadBitmapFromUrl(String endpoint) {
        if (endpoint == null || endpoint.trim().isEmpty()) return null;
        java.io.InputStream in = null;
        java.net.HttpURLConnection conn = null;
        try {
            java.net.URL url = new java.net.URL(endpoint.trim());
            conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setDoInput(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);
            conn.connect();
            if (conn.getResponseCode() == java.net.HttpURLConnection.HTTP_OK) {
                in = conn.getInputStream();
                return BitmapFactory.decodeStream(in);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to download bitmap from: " + endpoint, e);
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (conn != null) conn.disconnect(); } catch (Exception ignored) {}
        }
        return null;
    }

    /** Paparkan dialog pratonton foto profil bersaiz besar dalam bentuk bulatan kemas di tengah skrin dengan background blur dan animasi zoom lancar. */
    private void showAvatarPreviewDialog() {
        if (isFinishing() || isDestroyed()) return;

        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_circle_avatar_preview, null);
        dialog.setContentView(dialogView);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        android.widget.ImageView imgBlurBg = dialogView.findViewById(R.id.img_dialog_blur_bg);
        com.google.android.material.imageview.ShapeableImageView previewImg = dialogView.findViewById(R.id.img_preview_circle);
        View btnClose = dialogView.findViewById(R.id.btn_close_avatar_preview);
        View root = dialogView.findViewById(R.id.dialog_avatar_root);
        View previewContent = dialogView.findViewById(R.id.layout_preview_content);
        View frostedScrim = dialogView.findViewById(R.id.view_frosted_scrim);

        // Koordinat dan dimensi asal imgAvatar untuk animasi zoom masuk/keluar
        final int[] startLoc = new int[2];
        final int startWidth = imgAvatar != null ? imgAvatar.getWidth() : 0;
        final int startHeight = imgAvatar != null ? imgAvatar.getHeight() : 0;
        if (imgAvatar != null) {
            imgAvatar.getLocationOnScreen(startLoc);
        }

        // 1. Tangkap skrin latar belakang dan kaburkan (blur effect) dengan avatar dipastikan bulat
        if (imgBlurBg != null) {
            try {
                View decorView = getWindow().getDecorView();
                int w = Math.max(1, decorView.getWidth() / 2);
                int h = Math.max(1, decorView.getHeight() / 2);
                if (w > 0 && h > 0) {
                    Bitmap screenshot = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas canvas = new android.graphics.Canvas(screenshot);
                    canvas.scale(0.5f, 0.5f);
                    decorView.draw(canvas);

                    // Pastikan kawasan avatar di belakang dilukis semula dalam bentuk bulatan sempurna (tiada petak kabur)
                    if (imgAvatar != null && startWidth > 0 && startHeight > 0) {
                        int[] decorLoc = new int[2];
                        decorView.getLocationOnScreen(decorLoc);
                        float relX = startLoc[0] - decorLoc[0];
                        float relY = startLoc[1] - decorLoc[1];

                        android.graphics.drawable.Drawable avDrawable = imgAvatar.getDrawable();
                        if (avDrawable != null) {
                            Bitmap avBitmap = null;
                            if (highResAvatarBitmap != null && !highResAvatarBitmap.isRecycled()) {
                                avBitmap = highResAvatarBitmap;
                            } else if (avDrawable instanceof android.graphics.drawable.BitmapDrawable) {
                                avBitmap = ((android.graphics.drawable.BitmapDrawable) avDrawable).getBitmap();
                            }
                            if (avBitmap != null) {
                                Bitmap circularAv = AvatarUtils.getCircularBitmap(avBitmap);
                                if (circularAv != null) {
                                    android.graphics.RectF dst = new android.graphics.RectF(
                                            relX,
                                            relY,
                                            relX + startWidth,
                                            relY + startHeight
                                    );
                                    android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
                                    canvas.drawBitmap(circularAv, null, dst, p);

                                    // Lukis stroke putih kemas bulat
                                    android.graphics.Paint strokePaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                                    strokePaint.setStyle(android.graphics.Paint.Style.STROKE);
                                    strokePaint.setColor(0xFFFFFFFF);
                                    strokePaint.setStrokeWidth(getResources().getDisplayMetrics().density * 2.5f);
                                    canvas.drawCircle(dst.centerX(), dst.centerY(), dst.width() / 2f, strokePaint);
                                }
                            }
                        }
                    }

                    imgBlurBg.setImageBitmap(screenshot);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        imgBlurBg.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                                30f, 30f, android.graphics.Shader.TileMode.CLAMP));
                    }
                    imgBlurBg.setVisibility(android.view.View.VISIBLE);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to capture background for blur", e);
            }
        }

        // 2. Pasang gambar resolusi tinggi pada lingkaran pratonton
        if (previewImg != null) {
            if (highResAvatarBitmap != null && !highResAvatarBitmap.isRecycled()) {
                previewImg.setImageBitmap(highResAvatarBitmap);
                previewImg.setImageTintList(null);
            } else if (imgAvatar != null && imgAvatar.getDrawable() != null) {
                android.graphics.drawable.Drawable.ConstantState cs = imgAvatar.getDrawable().getConstantState();
                if (cs != null) {
                    previewImg.setImageDrawable(cs.newDrawable().mutate());
                } else {
                    previewImg.setImageDrawable(imgAvatar.getDrawable());
                }
                previewImg.setImageTintList(null);
            } else if (!photoB64.isEmpty() || (googlePhotoUrl != null && !googlePhotoUrl.isEmpty())) {
                AvatarUtils.applyAvatar(previewImg, photoB64, AvatarUtils.getHighResUrl(googlePhotoUrl), R.drawable.ic_avatar);
            } else {
                previewImg.setImageResource(R.drawable.ic_avatar);
            }
        }

        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(android.view.Gravity.CENTER);
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0f);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                window.getAttributes().setBlurBehindRadius(35);
            }
        }

        // 3. Animasi Smooth Transition Zoom Masuk & Keluar (Expand from thumbnail into center dialog)
        final View animTarget = previewContent != null ? previewContent : previewImg;
        if (animTarget != null) {
            animTarget.setAlpha(0f);
            animTarget.setScaleX(0.35f);
            animTarget.setScaleY(0.35f);
            if (startWidth > 0 && startHeight > 0) {
                animTarget.post(() -> {
                    int[] targetLoc = new int[2];
                    animTarget.getLocationOnScreen(targetLoc);
                    float targetCenterX = targetLoc[0] + animTarget.getWidth() / 2f;
                    float targetCenterY = targetLoc[1] + (previewImg != null ? previewImg.getHeight() / 2f : animTarget.getHeight() / 2f);
                    float startCenterX = startLoc[0] + startWidth / 2f;
                    float startCenterY = startLoc[1] + startHeight / 2f;

                    float deltaX = startCenterX - targetCenterX;
                    float deltaY = startCenterY - targetCenterY;

                    animTarget.setTranslationX(deltaX);
                    animTarget.setTranslationY(deltaY);
                    float initialScale = Math.max(0.25f, (float) startWidth / Math.max(1, previewImg != null ? previewImg.getWidth() : animTarget.getWidth()));
                    animTarget.setScaleX(initialScale);
                    animTarget.setScaleY(initialScale);
                    animTarget.setAlpha(0f);

                    if (frostedScrim != null) {
                        frostedScrim.setAlpha(0f);
                        frostedScrim.animate().alpha(1f).setDuration(280).start();
                    }
                    if (imgBlurBg != null) {
                        imgBlurBg.setAlpha(0f);
                        imgBlurBg.animate().alpha(1f).setDuration(280).start();
                    }

                    animTarget.animate()
                            .translationX(0f)
                            .translationY(0f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(320)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f))
                            .start();
                });
            } else {
                animTarget.post(() -> {
                    animTarget.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(280)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f))
                            .start();
                });
            }
        }

        final Runnable dismissWithAnimation = () -> {
            if (animTarget != null && startWidth > 0 && startHeight > 0) {
                int[] targetLoc = new int[2];
                animTarget.getLocationOnScreen(targetLoc);
                float targetCenterX = targetLoc[0] + animTarget.getWidth() / 2f;
                float targetCenterY = targetLoc[1] + (previewImg != null ? previewImg.getHeight() / 2f : animTarget.getHeight() / 2f);
                float startCenterX = startLoc[0] + startWidth / 2f;
                float startCenterY = startLoc[1] + startHeight / 2f;

                float deltaX = startCenterX - targetCenterX;
                float deltaY = startCenterY - targetCenterY;
                float endScale = Math.max(0.25f, (float) startWidth / Math.max(1, previewImg != null ? previewImg.getWidth() : animTarget.getWidth()));

                if (frostedScrim != null) {
                    frostedScrim.animate().alpha(0f).setDuration(220).start();
                }
                if (imgBlurBg != null) {
                    imgBlurBg.animate().alpha(0f).setDuration(220).start();
                }

                animTarget.animate()
                        .translationX(deltaX)
                        .translationY(deltaY)
                        .scaleX(endScale)
                        .scaleY(endScale)
                        .alpha(0f)
                        .setDuration(240)
                        .setInterpolator(new android.view.animation.AccelerateInterpolator(1.6f))
                        .withEndAction(() -> {
                            try { dialog.dismiss(); } catch (Exception ignored) {}
                        })
                        .start();
            } else {
                try { dialog.dismiss(); } catch (Exception ignored) {}
            }
        };

        if (root != null) root.setOnClickListener(v -> dismissWithAnimation.run());
        if (btnClose != null) btnClose.setOnClickListener(v -> dismissWithAnimation.run());
        if (previewImg != null) previewImg.setOnClickListener(v -> dismissWithAnimation.run());

        dialog.show();
    }

    /** Tukar Bitmap foto kamera kepada format Base64. */
    private String bitmapToBase64(Bitmap bitmap) {
        if (bitmap == null) return "";
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, outputStream);
            byte[] byteArray = outputStream.toByteArray();
            return Base64.encodeToString(byteArray, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "failed to convert bitmap to base64", e);
            return "";
        }
    }

    /** Semak ketetapan SOP Kata Laluan (Min 8 aksara, huruf besar, huruf kecil, nombor, & simbol khas). */
    public static String getPasswordSopError(String password) {
        if (password == null || password.length() < 8) {
            return "Password must be at least 8 characters long.";
        }
        if (!password.matches(".*[A-Z].*")) {
            return "Password must contain at least one uppercase letter (A-Z).";
        }
        if (!password.matches(".*[a-z].*")) {
            return "Password must contain at least one lowercase letter (a-z).";
        }
        if (!password.matches(".*[0-9].*")) {
            return "Password must contain at least one digit (0-9).";
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?~`].*")) {
            return "Password must contain at least one special symbol (!@#$%^&*...).";
        }
        return null;
    }

    private boolean isOtherSelected(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase(java.util.Locale.ROOT);
        if (t.equals("others") || t.equals("other") || t.equals("lain-lain") || t.equals("其他") || t.equals("மற்றவை")) {
            return true;
        }
        if (t.contains(",")) {
            for (String part : t.split(",")) {
                String p = part.trim();
                if (p.equals("others") || p.equals("other") || p.equals("lain-lain") || p.equals("其他") || p.equals("மற்றவை")) {
                    return true;
                }
            }
        }
        return false;
    }

    private String buildMultiSelectValue(String selection, String otherText) {
        if (selection == null || selection.trim().isEmpty()) return "";
        if (!isOtherSelected(selection)) return selection.trim();
        String[] parts = selection.split(",");
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String p : parts) {
            String item = p.trim();
            if (isOtherSelected(item)) {
                if (otherText != null && !otherText.trim().isEmpty()) {
                    list.add(otherText.trim());
                }
            } else if (!item.isEmpty()) {
                list.add(item);
            }
        }
        return android.text.TextUtils.join(", ", list);
    }

    private void showMultiSelectRegisterDialog(String title, String[] options,
                                               MaterialAutoCompleteTextView targetInput,
                                               TextInputLayout targetLayout,
                                               TextInputLayout otherLayout,
                                               TextInputEditText otherInput) {
        if (options == null || options.length == 0 || targetInput == null) return;
        boolean[] checkedItems = new boolean[options.length];
        String currentText = targetInput.getText() == null ? "" : targetInput.getText().toString().trim();
        java.util.Set<String> selectedSet = new java.util.HashSet<>();
        if (!currentText.isEmpty()) {
            for (String part : currentText.split(",")) {
                String t = part.trim();
                if (!t.isEmpty()) selectedSet.add(t.toLowerCase(java.util.Locale.ROOT));
            }
        }
        for (int i = 0; i < options.length; i++) {
            if (selectedSet.contains(options[i].toLowerCase(java.util.Locale.ROOT))) {
                checkedItems[i] = true;
            }
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_ResQTap_AlertDialog);
        builder.setTitle(title);
        builder.setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_dialog_rounded));
        builder.setMultiChoiceItems(options, checkedItems, (dialog, which, isChecked) -> {
            checkedItems[which] = isChecked;
            androidx.appcompat.app.AlertDialog alertDialog = (androidx.appcompat.app.AlertDialog) dialog;
            android.widget.ListView listView = alertDialog.getListView();
            if (which == 0) { // "None"
                if (isChecked) {
                    for (int i = 1; i < options.length; i++) {
                        checkedItems[i] = false;
                        if (listView != null) listView.setItemChecked(i, false);
                    }
                }
            } else {
                if (isChecked) {
                    checkedItems[0] = false;
                    if (listView != null) listView.setItemChecked(0, false);
                }
            }
        });

        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            java.util.List<String> chosen = new java.util.ArrayList<>();
            boolean hasOther = false;
            for (int i = 0; i < options.length; i++) {
                if (checkedItems[i]) {
                    chosen.add(options[i]);
                    if (isOtherSelected(options[i])) {
                        hasOther = true;
                    }
                }
            }
            if (chosen.isEmpty()) {
                chosen.add(options[0]);
            }
            String result = android.text.TextUtils.join(", ", chosen);
            targetInput.setText(result);
            if (targetLayout != null) {
                targetLayout.setError(null);
                targetLayout.setErrorEnabled(false);
            }
            if (hasOther) {
                if (otherLayout != null) otherLayout.setVisibility(View.VISIBLE);
                if (otherInput != null) otherInput.requestFocus();
            } else {
                if (otherLayout != null) otherLayout.setVisibility(View.GONE);
                if (otherInput != null) otherInput.setText("");
            }
        });

        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    /** Kawal paparan progress loading pada butang Sign in with Google semasa proses pengesahan berjalan. */
    private void setGoogleLoading(boolean loading) {
        if (isFinishing() || isDestroyed()) return;
        if (layoutGoogleBtnContent != null) {
            layoutGoogleBtnContent.setVisibility(loading ? View.GONE : View.VISIBLE);
        }
        if (layoutGoogleLoading != null) {
            layoutGoogleLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
            if (layoutGoogleLoading instanceof GoogleDotsLoadingView) {
                if (loading) {
                    ((GoogleDotsLoadingView) layoutGoogleLoading).start();
                } else {
                    ((GoogleDotsLoadingView) layoutGoogleLoading).stop();
                }
            }
        }
        if (btnSocialGoogle != null) {
            btnSocialGoogle.setEnabled(!loading);
            btnSocialGoogle.setAlpha(loading ? 0.75f : 1.0f);
        }
        if (btnCreateAccount != null) {
            btnCreateAccount.setEnabled(!loading);
        }
    }

    /** Mengendalikan hasil daripada Google Sign-In Intent. */
    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account != null && account.getIdToken() != null) {
                firebaseAuthWithGoogle(account);
            } else {
                setGoogleLoading(false);
                Toast.makeText(this, R.string.toast_google_sign_in_failed, Toast.LENGTH_SHORT).show();
            }
        } catch (ApiException e) {
            setGoogleLoading(false);
            Log.e(TAG, "Google sign in failed with code: " + e.getStatusCode(), e);
            Toast.makeText(this, getString(R.string.toast_google_sign_in_failed) + " (" + e.getStatusCode() + ")", Toast.LENGTH_SHORT).show();
        }
    }

    /** Sambungkan akaun Google dengan Firebase Auth. */
    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        FirebaseUser user = task.getResult().getUser();
                        if (user != null) {
                            handlePostGoogleSignIn(user, acct);
                        } else {
                            setGoogleLoading(false);
                            Toast.makeText(this, R.string.toast_login_failed, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        setGoogleLoading(false);
                        Exception e = task.getException();
                        Log.e(TAG, "FirebaseAuthWithGoogle failed", e);
                        String errMsg = e != null && e.getMessage() != null ? e.getMessage() : getString(R.string.toast_login_failed);
                        Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    /** Kendalikan semakan profil pengguna dan navigasi selepas log masuk Google di skrin pendaftaran. */
    private void handlePostGoogleSignIn(FirebaseUser user, GoogleSignInAccount acct) {
        String uid = user.getUid();
        String email = user.getEmail() != null ? user.getEmail() : (acct.getEmail() != null ? acct.getEmail() : "");
        UserPrefs.setUid(this, uid);
        if (!email.isEmpty()) {
            UserPrefs.setEmail(this, email);
        }

        final String googlePhoto = acct.getPhotoUrl() != null ? AvatarUtils.getHighResUrl(acct.getPhotoUrl().toString()) : "";
        fromGoogleSignIn = true;
        googleIdToken = acct.getIdToken() != null ? acct.getIdToken() : "";
        googlePhotoUrl = googlePhoto;
        registeredUid = uid;
        registeredEmail = email;

        // Semak profil dari Supabase terlebih dahulu
        com.example.resqtap.supabase.SupabaseManager.getInstance().getProfile(uid, new com.example.resqtap.supabase.SupabaseManager.Callback<org.json.JSONObject>() {
            @Override
            public void onSuccess(org.json.JSONObject profile) {
                if (profile != null) {
                    applySupabaseProfile(profile);
                    if (UserPrefs.isPersonalInfoComplete(RegisterActivity.this)) {
                        setGoogleLoading(false);
                        startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                        finish();
                        return;
                    }
                }
                setupGoogleProfileStep2(acct, googlePhoto);
            }

            @Override
            public void onError(Exception e) {
                // Fallback: semak RTDB jika ada rekod lama
                FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                        .getReference("users")
                        .child(uid)
                        .get()
                        .addOnSuccessListener(snapshot -> {
                            if (snapshot != null && snapshot.exists()) {
                                applyExistingGoogleUserSnapshot(snapshot);
                                if (UserPrefs.isPersonalInfoComplete(RegisterActivity.this)) {
                                    setGoogleLoading(false);
                                    startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                                    finish();
                                    return;
                                }
                            }
                            setupGoogleProfileStep2(acct, googlePhoto);
                        })
                        .addOnFailureListener(err -> setupGoogleProfileStep2(acct, googlePhoto));
            }
        });
    }

    private void applySupabaseProfile(org.json.JSONObject profile) {
        if (profile == null) return;
        try {
            String name = profile.optString("full_name", "");
            if (!name.isEmpty()) UserPrefs.setName(this, name);
            String phone = profile.optString("phone", "");
            if (phone.isEmpty()) phone = profile.optString("phone_number", "");
            if (!phone.isEmpty()) UserPrefs.setPhoneNumber(this, phone);
            String ic = profile.optString("ic_number", "");
            if (!ic.isEmpty()) UserPrefs.setIcNumber(this, ic);
            String gender = profile.optString("gender", "");
            if (!gender.isEmpty()) UserPrefs.setGender(this, gender);
            String dob = profile.optString("dob", "");
            if (!dob.isEmpty()) UserPrefs.setDateOfBirth(this, dob);
            String address = profile.optString("address", "");
            if (!address.isEmpty()) UserPrefs.setAddress(this, address);
            String photo = profile.optString("photo_url", "");
            if (photo.isEmpty()) photo = profile.optString("avatar_url", "");
            if (!photo.isEmpty()) UserPrefs.setPhotoUrl(this, photo);

            String uid = profile.optString("id", "");
            String pubId = com.example.resqtap.friend.FirebaseFriendClient.format4DigitId(uid, "");
            UserPrefs.setPublicId(this, pubId);
        } catch (Exception ignored) {}
    }

    private void setupGoogleProfileStep2(GoogleSignInAccount acct, String googlePhoto) {
        setGoogleLoading(false);

        String displayName = acct.getDisplayName();
        String firstName = "";
        String lastName = "";
        if (displayName != null && !displayName.trim().isEmpty()) {
            String cleanName = displayName.trim();
            int spaceIdx = cleanName.indexOf(' ');
            if (spaceIdx > 0) {
                firstName = cleanName.substring(0, spaceIdx).trim();
                lastName = cleanName.substring(spaceIdx + 1).trim();
            } else {
                firstName = cleanName;
            }
            UserPrefs.setName(RegisterActivity.this, cleanName);
        }
        UserPrefs.setFirstName(RegisterActivity.this, firstName);
        UserPrefs.setLastName(RegisterActivity.this, lastName);
        UserPrefs.setIcNumber(RegisterActivity.this, "");
        UserPrefs.setPhoneNumber(RegisterActivity.this, "");

        TextInputEditText firstNameInput = findViewById(R.id.input_first_name);
        TextInputEditText lastNameInput = findViewById(R.id.input_last_name);
        TextInputEditText icInput = findViewById(R.id.input_ic);
        TextInputEditText phoneInput = findViewById(R.id.input_phone);
        TextInputEditText emergencyNameInput = findViewById(R.id.input_emergency_name);
        TextInputEditText emergencyPhoneInput = findViewById(R.id.input_emergency_phone);

        if (firstNameInput != null && !firstName.isEmpty()) firstNameInput.setText(firstName);
        if (lastNameInput != null && !lastName.isEmpty()) lastNameInput.setText(lastName);
        if (icInput != null) icInput.setText("");
        if (phoneInput != null) phoneInput.setText("");
        if (emergencyNameInput != null) emergencyNameInput.setText("");
        if (emergencyPhoneInput != null) emergencyPhoneInput.setText("");

        if (googlePhoto != null && !googlePhoto.trim().isEmpty() && imgAvatar != null) {
            loadAndSetGoogleProfilePhoto(googlePhoto.trim());
        }

        showStep2PersonalInfo();
    }

    private void applyExistingGoogleUserSnapshot(DataSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) return;
        Object nameObj = snapshot.child("name").getValue();
        if (nameObj != null) UserPrefs.setName(this, String.valueOf(nameObj));
        Object bloodObj = snapshot.child("bloodType").getValue();
        if (bloodObj != null) UserPrefs.setBloodType(this, String.valueOf(bloodObj));
        Object allergiesObj = snapshot.child("allergies").getValue();
        if (allergiesObj != null) UserPrefs.setAllergies(this, String.valueOf(allergiesObj));
        Object weightObj = snapshot.child("weight").getValue();
        UserPrefs.setWeight(this, weightObj == null ? "" : String.valueOf(weightObj));
        Object heightObj = snapshot.child("height").getValue();
        UserPrefs.setHeight(this, heightObj == null ? "" : String.valueOf(heightObj));
        Object genderObj = snapshot.child("gender").getValue();
        if (genderObj != null) UserPrefs.setGender(this, String.valueOf(genderObj));
        Object icObj = snapshot.child("icNumber").getValue();
        if (icObj != null) UserPrefs.setIcNumber(this, String.valueOf(icObj));

        Object medObj = snapshot.child("medications").getValue();
        String medications = medObj == null ? "" : String.valueOf(medObj).trim();
        Object condObj = snapshot.child("existingConditions").getValue();
        String conditions = condObj == null ? "" : String.valueOf(condObj).trim();
        if (conditions.isEmpty() && !medications.isEmpty()) {
            conditions = medications;
        }
        UserPrefs.setExistingConditions(this, conditions);
        UserPrefs.setMedications(this, medications);

        Object organObj = snapshot.child("organDonor").getValue();
        if (organObj != null) UserPrefs.setOrganDonor(this, String.valueOf(organObj));
        Object emailObj = snapshot.child("email").getValue();
        if (emailObj != null) UserPrefs.setEmail(this, String.valueOf(emailObj));
        Object publicIdObj = snapshot.child("publicId").getValue();
        String pubId = publicIdObj == null ? "" : String.valueOf(publicIdObj).trim();
        if (pubId.isEmpty()) {
            pubId = com.example.resqtap.friend.FirebaseFriendClient.format4DigitId(snapshot.getKey(), "");
        } else {
            pubId = com.example.resqtap.friend.FirebaseFriendClient.format4DigitId(snapshot.getKey(), pubId);
        }
        UserPrefs.setPublicId(this, pubId);

        Object photoUrlObj = snapshot.child("photoUrl").getValue();
        String photoUrl = photoUrlObj == null ? "" : String.valueOf(photoUrlObj);
        if (photoUrl == null) photoUrl = "";
        photoUrl = photoUrl.trim();
        if (photoUrl.isEmpty()) {
            Object photoUriObj = snapshot.child("photoUri").getValue();
            photoUrl = photoUriObj == null ? "" : String.valueOf(photoUriObj);
            if (photoUrl == null) photoUrl = "";
            photoUrl = photoUrl.trim();
        }
        if (!photoUrl.isEmpty()) UserPrefs.setPhotoUrl(this, photoUrl);

        Object photoB64Obj = snapshot.child("photoB64").getValue();
        if (photoB64Obj != null) {
            String b64 = String.valueOf(photoB64Obj);
            if (b64 != null && !b64.trim().isEmpty()) UserPrefs.setPhotoB64(this, b64.trim());
        }

        Object addrObj = snapshot.child("address").getValue();
        if (addrObj != null) UserPrefs.setAddress(this, String.valueOf(addrObj));
        Object relObj = snapshot.child("religion").getValue();
        if (relObj != null) UserPrefs.setReligion(this, String.valueOf(relObj));
        Object phoneObj = snapshot.child("phoneNumber").getValue();
        if (phoneObj != null) UserPrefs.setPhoneNumber(this, String.valueOf(phoneObj));
        Object dobObj = snapshot.child("dateOfBirth").getValue();
        if (dobObj != null) UserPrefs.setDateOfBirth(this, String.valueOf(dobObj));
        Object ethObj = snapshot.child("ethnicity").getValue();
        if (ethObj != null) UserPrefs.setEthnicity(this, String.valueOf(ethObj));
    }
}

