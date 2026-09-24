package com.example.resqtap.contacts;

import com.example.resqtap.R;
import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.room.FirebaseRoomClient;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * EmergencyContactsActivity
 * Borang tambah & edit emergency contact mengikut reka bentuk modal moden (Gambar Tengah).
 */
public class EmergencyContactsActivity extends BaseActivity {
    public static final String EXTRA_CONTACT_ID = "contact_id";
    public static final String EXTRA_CONTACT_NAME = "contact_name";
    public static final String EXTRA_CONTACT_REL = "contact_rel";
    public static final String EXTRA_CONTACT_PHONE = "contact_phone";
    public static final String EXTRA_CONTACT_NOTES = "contact_notes";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final String[] CATEGORY_OPTIONS = new String[]{
            "Doctor",
            "Babysitter",
            "School",
            "Family",
            "Friend",
            "Work",
            "Neighbor",
            "Other"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_emergency_contacts);

        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Intent intent = getIntent();
        String editingId = intent == null ? "" : intent.getStringExtra(EXTRA_CONTACT_ID);
        String editingName = intent == null ? "" : intent.getStringExtra(EXTRA_CONTACT_NAME);
        String editingRel = intent == null ? "" : intent.getStringExtra(EXTRA_CONTACT_REL);
        String editingPhone = intent == null ? "" : intent.getStringExtra(EXTRA_CONTACT_PHONE);
        String editingNotes = intent == null ? "" : intent.getStringExtra(EXTRA_CONTACT_NOTES);

        editingId = editingId == null ? "" : editingId.trim();
        editingName = editingName == null ? "" : editingName;
        editingRel = editingRel == null ? "" : editingRel;
        editingPhone = editingPhone == null ? "" : editingPhone;
        editingNotes = editingNotes == null ? "" : editingNotes;

        final boolean isEditMode = !editingId.isEmpty();
        final String contactId = editingId;

        TextView tvTitle = findViewById(R.id.sheet_title);
        EditText inputName = findViewById(R.id.input_contact_name);
        View layoutCategory = findViewById(R.id.layout_category_picker);
        TextView tvCategory = findViewById(R.id.tv_category_value);
        EditText inputPhone = findViewById(R.id.input_contact_phone);
        EditText inputNotes = findViewById(R.id.input_contact_notes);

        MaterialButton btnSave = findViewById(R.id.btn_save_contact);
        MaterialButton btnDelete = findViewById(R.id.btn_delete_contact);
        View btnClose = findViewById(R.id.btn_close);
        View backdrop = findViewById(R.id.backdrop);

        if (isEditMode) {
            if (tvTitle != null) tvTitle.setText("Edit Emergency Contact");
            if (btnSave != null) btnSave.setText("Save Contact");
            if (inputName != null) inputName.setText(editingName);
            if (tvCategory != null) tvCategory.setText(editingRel.isEmpty() ? "Family" : editingRel);
            if (inputPhone != null) inputPhone.setText(editingPhone);
            if (inputNotes != null) inputNotes.setText(editingNotes);
            if (btnDelete != null) {
                btnDelete.setVisibility(View.VISIBLE);
                btnDelete.setOnClickListener(v -> confirmDelete(contactId));
            }
        } else {
            if (tvTitle != null) tvTitle.setText("Add Emergency Contact");
            if (btnSave != null) btnSave.setText("Save Contact");
            if (tvCategory != null) tvCategory.setText("Family");
            if (btnDelete != null) btnDelete.setVisibility(View.GONE);
        }

        if (layoutCategory != null && tvCategory != null) {
            layoutCategory.setOnClickListener(v -> showCategoryPicker(tvCategory));
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> finish());
        }
        if (backdrop != null) {
            backdrop.setOnClickListener(v -> finish());
        }

        if (btnSave != null) {
            btnSave.setOnClickListener(v -> {
                String name = inputName == null || inputName.getText() == null ? "" : inputName.getText().toString().trim();
                String rel = tvCategory == null || tvCategory.getText() == null ? "Family" : tvCategory.getText().toString().trim();
                String phone = inputPhone == null || inputPhone.getText() == null ? "" : inputPhone.getText().toString().trim();
                String notes = inputNotes == null || inputNotes.getText() == null ? "" : inputNotes.getText().toString().trim();

                if (name.isEmpty() || phone.isEmpty()) {
                    Toast.makeText(this, R.string.toast_fill_all, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (UserPrefs.hasEmergencyContactNameDuplicate(this, isEditMode ? contactId : "", name)) {
                    Toast.makeText(this, R.string.toast_contact_phone_duplicate, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (UserPrefs.hasEmergencyContactPhoneDuplicate(this, isEditMode ? contactId : "", phone)) {
                    Toast.makeText(this, R.string.toast_contact_phone_duplicate, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (UserPrefs.hasEmergencyContactDuplicate(this, isEditMode ? contactId : "", name, phone)) {
                    Toast.makeText(this, R.string.toast_contact_duplicate, Toast.LENGTH_SHORT).show();
                    return;
                }

                if (isEditMode) {
                    UserPrefs.updateEmergencyContact(this, contactId, name, rel, phone, notes);
                    syncUpsertToDb(new EmergencyContact(contactId, name, rel, phone, notes));
                    Toast.makeText(this, R.string.toast_contact_updated, Toast.LENGTH_SHORT).show();
                } else {
                    String newId = UUID.randomUUID().toString();
                    EmergencyContact c = new EmergencyContact(newId, name, rel, phone, notes);
                    UserPrefs.addEmergencyContact(this, c);
                    syncUpsertToDb(c);
                    Toast.makeText(this, R.string.toast_contact_added, Toast.LENGTH_SHORT).show();
                }
                finish();
            });
        }
    }

    private void showCategoryPicker(TextView tvCategory) {
        String current = tvCategory.getText() == null ? "" : tvCategory.getText().toString();
        int checkedItem = 0;
        for (int i = 0; i < CATEGORY_OPTIONS.length; i++) {
            if (CATEGORY_OPTIONS[i].equalsIgnoreCase(current)) {
                checkedItem = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_ResQTap_AlertDialog)
                .setTitle("Select Category")
                .setBackground(ContextCompat.getDrawable(this, R.drawable.bg_dialog_rounded))
                .setSingleChoiceItems(CATEGORY_OPTIONS, checkedItem, (dialog, which) -> {
                    String selected = CATEGORY_OPTIONS[which];
                    tvCategory.setText(selected);
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDelete(String contactId) {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_ResQTap_AlertDialog)
                .setBackground(ContextCompat.getDrawable(this, R.drawable.bg_dialog_rounded))
                .setMessage(R.string.emergency_contact_remove_confirm)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    UserPrefs.deleteEmergencyContactById(this, contactId);
                    syncDeleteToDb(contactId);
                    Toast.makeText(this, "Contact removed", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .show();
    }

    private void syncUpsertToDb(EmergencyContact c) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String uid = user.getUid();
        executor.execute(() -> {
            try {
                FirebaseRoomClient.upsertEmergencyContact(uid, c);
            } catch (Exception ignored) {
            }
        });
    }

    private void syncDeleteToDb(String contactId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String uid = user.getUid();
        executor.execute(() -> {
            try {
                FirebaseRoomClient.deleteEmergencyContact(uid, contactId);
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            executor.shutdownNow();
        } catch (Exception ignored) {
        }
    }
}
