package com.example.resqtap.contacts;

import com.example.resqtap.R;
import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.room.FirebaseRoomClient;
import com.example.resqtap.utils.BottomNavUtils;
import com.example.resqtap.utils.ThemeUtils;
import com.example.resqtap.utils.UserPrefs;

import android.content.Intent;
import android.net.Uri;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * EmergencyContactsListActivity
 * Senarai emergency contact dengan Native Bottom Sheet Swipe-Up untuk tambah & edit contact.
 */
public class EmergencyContactsListActivity extends BaseActivity {
    private EmergencyContactsAdapter adapter;
    private View emptyLayout;
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
        setContentView(R.layout.activity_emergency_contacts_list);

        View root = findViewById(R.id.main);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        BottomNavUtils.setup(bottomNav, this, R.id.nav_bottom_profile);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            try {
                if (bottomNav != null) {
                    int pl = bottomNav.getPaddingLeft();
                    int pt = bottomNav.getPaddingTop();
                    int pr = bottomNav.getPaddingRight();
                    bottomNav.setPadding(pl, pt, pr, systemBars.bottom);
                }
            } catch (Exception ignored) {
            }
            return insets;
        });

        View back = findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> finish());

        View add = findViewById(R.id.btn_add);
        if (add != null) {
            add.setOnClickListener(v -> showContactBottomSheet(null));
        }

        View sosBell = findViewById(R.id.btn_sos_bell);
        if (sosBell != null) {
            sosBell.setOnClickListener(v -> {
                startActivity(new Intent(this, com.example.resqtap.notification.NotificationsActivity.class));
            });
        }

        View callEmergencyServices = findViewById(R.id.btn_emergency_call_services);
        if (callEmergencyServices != null) {
            callEmergencyServices.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:999")));
                } catch (Exception ignored) {
                }
            });
        }

        emptyLayout = findViewById(R.id.tv_empty);

        RecyclerView rv = findViewById(R.id.rv_contacts);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EmergencyContactsAdapter(new EmergencyContactsAdapter.Listener() {
            @Override
            public void onCall(EmergencyContact c) {
                String phone = c == null ? "" : String.valueOf(c.phone).trim();
                if (phone.isEmpty()) return;
                try {
                    startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
                } catch (Exception ignored) {
                }
            }

            @Override
            public void onMessage(EmergencyContact c) {
                String phone = c == null ? "" : String.valueOf(c.phone).trim();
                if (phone.isEmpty()) return;
                try {
                    Intent smsIntent = new Intent(Intent.ACTION_SENDTO);
                    smsIntent.setData(Uri.parse("smsto:" + phone));
                    startActivity(smsIntent);
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("sms:" + phone)));
                    } catch (Exception ignored) {
                    }
                }
            }

            @Override
            public void onEdit(EmergencyContact c) {
                showContactBottomSheet(c);
            }

            @Override
            public void onDelete(EmergencyContact c) {
                confirmDelete(c);
            }
        });
        rv.setAdapter(adapter);

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        ArrayList<EmergencyContact> all = UserPrefs.getEmergencyContacts(this);
        adapter.submit(all);
        updateEmpty(all.size());
    }

    private void updateEmpty(int shownCount) {
        if (emptyLayout == null) return;
        emptyLayout.setVisibility(shownCount <= 0 ? View.VISIBLE : View.GONE);
    }

    /**
     * Memaparkan Bottom Sheet dengan animasi slide up yang lancar dari bawah
     */
    private void showContactBottomSheet(EmergencyContact contactToEdit) {
        BottomSheetDialog sheet = new BottomSheetDialog(this, R.style.Theme_ResQTap_BottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.dialog_add_emergency_contact, null);
        sheet.setContentView(view);

        if (sheet.getWindow() != null) {
            sheet.getWindow().setWindowAnimations(R.style.Animation_ResQTap_BottomSheetDialog);
            sheet.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        sheet.setOnShowListener(dialog -> {
            try {
                com.google.android.material.bottomsheet.BottomSheetBehavior<?> behavior = sheet.getBehavior();
                behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            } catch (Exception ignored) {
            }
        });

        boolean isEditMode = contactToEdit != null && contactToEdit.id != null && !contactToEdit.id.trim().isEmpty();
        String contactId = isEditMode ? contactToEdit.id.trim() : "";

        TextView tvTitle = view.findViewById(R.id.sheet_title);
        EditText inputName = view.findViewById(R.id.input_contact_name);
        View layoutCategory = view.findViewById(R.id.layout_category_picker);
        TextView tvCategory = view.findViewById(R.id.tv_category_value);
        EditText inputPhone = view.findViewById(R.id.input_contact_phone);
        EditText inputNotes = view.findViewById(R.id.input_contact_notes);

        MaterialButton btnSave = view.findViewById(R.id.btn_save_contact);
        MaterialButton btnDelete = view.findViewById(R.id.btn_delete_contact);
        View btnClose = view.findViewById(R.id.btn_close);

        if (isEditMode) {
            if (tvTitle != null) tvTitle.setText("Edit Emergency Contact");
            if (btnSave != null) btnSave.setText("Save Contact");
            if (inputName != null) inputName.setText(contactToEdit.name);
            if (tvCategory != null) tvCategory.setText(contactToEdit.relationship != null && !contactToEdit.relationship.isEmpty() ? contactToEdit.relationship : "Family");
            if (inputPhone != null) inputPhone.setText(contactToEdit.phone);
            if (inputNotes != null) inputNotes.setText(contactToEdit.notes);
            if (btnDelete != null) {
                btnDelete.setVisibility(View.VISIBLE);
                btnDelete.setOnClickListener(v -> {
                    sheet.dismiss();
                    confirmDelete(contactToEdit);
                });
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
            btnClose.setOnClickListener(v -> sheet.dismiss());
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
                sheet.dismiss();
                refresh();
            });
        }

        sheet.show();
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

    private void confirmDelete(EmergencyContact c) {
        if (c == null) return;
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_ResQTap_AlertDialog)
                .setBackground(ContextCompat.getDrawable(this, R.drawable.bg_dialog_rounded))
                .setMessage(R.string.emergency_contact_remove_confirm)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    UserPrefs.deleteEmergencyContactById(this, c.id);
                    syncDeleteToDb(c.id);
                    refresh();
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
