package com.example.resqtap.utils;

import com.example.resqtap.R;

import com.example.resqtap.auth.LoginActivity;
import com.example.resqtap.room.FirebaseRoomClient;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * AccountDeletionUtils
 * Utility delete account: re-auth -> RTDB wipe -> Auth delete -> redirect.
 */
public final class AccountDeletionUtils {
    private static final String TAG = "AccountDeletionUtils";

    private AccountDeletionUtils() {
    }

    /** Dialog pengesahan awal sebelum padam akaun. */
    public static void confirmAndDelete(Activity activity, ExecutorService executor) {
        if (activity == null || executor == null) return;
        try {
            android.view.View content = android.view.LayoutInflater.from(activity)
                    .inflate(R.layout.dialog_delete_account_confirm, null, false);

            androidx.appcompat.app.AlertDialog d = new MaterialAlertDialogBuilder(activity)
                    .setView(content)
                    .create();

            if (d.getWindow() != null) {
                d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            android.view.View cancel = content.findViewById(R.id.btn_cancel);
            if (cancel != null) cancel.setOnClickListener(v -> d.dismiss());

            android.view.View delete = content.findViewById(R.id.btn_delete);
            if (delete != null) {
                delete.setOnClickListener(v -> {
                    d.dismiss();
                    showReauthDialog(activity, executor);
                });
            }

            d.show();
        } catch (Exception e) {
            new MaterialAlertDialogBuilder(activity)
                    .setMessage(R.string.delete_account_confirm)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(android.R.string.ok, (d, w) -> showReauthDialog(activity, executor))
                    .show();
        }
    }

    /** Dialog minta pengesahan dengan menaip 'DELETE' sebelum padam. */
    private static void showReauthDialog(Activity activity, ExecutorService executor) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(activity, R.string.delete_account_requires_relogin, Toast.LENGTH_LONG).show();
            return;
        }

        try {
            android.view.View content = android.view.LayoutInflater.from(activity)
                    .inflate(R.layout.dialog_delete_account_reauth, null, false);

            androidx.appcompat.app.AlertDialog d = new MaterialAlertDialogBuilder(activity)
                    .setView(content)
                    .create();

            if (d.getWindow() != null) {
                d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            android.widget.EditText confirmInput = content.findViewById(R.id.et_password);
            android.view.View cancel = content.findViewById(R.id.btn_cancel);
            if (cancel != null) cancel.setOnClickListener(v -> d.dismiss());

            android.view.View confirm = content.findViewById(R.id.btn_confirm_delete);
            if (confirm != null) {
                // Disable button initially until 'DELETE' is typed
                confirm.setEnabled(false);
                confirm.setAlpha(0.5f);

                if (confirmInput != null) {
                    confirmInput.addTextChangedListener(new android.text.TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {
                            String txt = s != null ? s.toString().trim() : "";
                            boolean matches = "DELETE".equalsIgnoreCase(txt);
                            confirm.setEnabled(matches);
                            confirm.setAlpha(matches ? 1.0f : 0.5f);
                        }

                        @Override
                        public void afterTextChanged(android.text.Editable s) {}
                    });
                }

                confirm.setOnClickListener(v -> {
                    String typed = (confirmInput != null && confirmInput.getText() != null)
                            ? confirmInput.getText().toString().trim() : "";
                    if (!"DELETE".equalsIgnoreCase(typed)) {
                        Toast.makeText(activity, R.string.delete_account_match_error, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    d.dismiss();
                    Toast.makeText(activity, R.string.toast_processing, Toast.LENGTH_SHORT).show();
                    executor.execute(() -> executeAccountDeletion(activity, user));
                });
            }

            d.show();
        } catch (Exception e) {
            TextInputLayout confirmLayout = new TextInputLayout(activity);
            confirmLayout.setHint(activity.getString(R.string.delete_account_password_hint));
            confirmLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);

            TextInputEditText confirmInput = new TextInputEditText(confirmLayout.getContext());
            confirmInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
            confirmLayout.addView(confirmInput);

            FrameLayout container = new FrameLayout(activity);
            int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
            container.setPadding(pad, pad, pad, 0);
            container.addView(confirmLayout, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.delete_account_reauth_title)
                    .setMessage(R.string.delete_account_reauth_desc)
                    .setView(container)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(R.string.delete_account_reauth_action, (d, w) -> {
                        String typed = confirmInput.getText() != null
                                ? confirmInput.getText().toString().trim() : "";
                        if (!"DELETE".equalsIgnoreCase(typed)) {
                            Toast.makeText(activity, R.string.delete_account_match_error, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Toast.makeText(activity, R.string.toast_processing, Toast.LENGTH_SHORT).show();
                        executor.execute(() -> executeAccountDeletion(activity, user));
                    })
                    .show();
        }
    }

    /** RTDB wipe -> Auth delete -> Google signout -> stop services + redirect. */
    private static void executeAccountDeletion(Activity activity, FirebaseUser user) {
        try {
            String uid = user.getUid() != null ? user.getUid().trim() : "";
            if (uid.isEmpty()) uid = UserPrefs.getUid(activity);
            final String finalUid = uid;

            // 1. Hentikan tracking bilik segera supaya service tidak hantar presence/lokasi lagi
            try {
                com.example.resqtap.sos.SosServiceStarter.stop(activity);
            } catch (Exception ignored) {}
            UserPrefs.setActiveRoomCode(activity, "");

            // 2. RTDB wipe dahulu (auth token masih sah sebelum user dipadam)
            if (finalUid != null && !finalUid.isEmpty()) {
                try {
                    FirebaseRoomClient.deleteAccountData(finalUid);
                    Log.i(TAG, "RTDB data wiped for uid=" + finalUid);
                } catch (Exception e) {
                    Log.e(TAG, "RTDB deleteAccountData failed uid=" + finalUid, e);
                }
            }

            // 3. Auth delete (jika sesi token Firebase perlukan re-auth di pelayan, jangan sekat proses padam data RTDB dan logout)
            try {
                Tasks.await(user.delete(), 15, TimeUnit.SECONDS);
                Log.i(TAG, "Firebase Auth account deleted successfully");
            } catch (Exception e) {
                Log.w(TAG, "Auth user.delete() encountered (proceeding anyway): " + e.getMessage());
            }

            // 4. Sign-out Google Client jika ada
            try {
                com.google.android.gms.auth.api.signin.GoogleSignInOptions gso =
                        new com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN).build();
                com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(activity, gso).signOut();
            } catch (Exception ignored) {}

            // 5. Bersihkan simpanan tempatan & kembali ke skrin login
            try {
                com.example.resqtap.sos.SosServiceStarter.stop(activity);
            } catch (Exception ignored) {}
            UserPrefs.clearAccountData(activity);
            activity.runOnUiThread(() -> goToLogin(activity));

        } catch (Exception e) {
            Log.e(TAG, "executeAccountDeletion failed", e);
            activity.runOnUiThread(() ->
                    Toast.makeText(activity, R.string.delete_account_failed, Toast.LENGTH_LONG).show());
        }
    }

    /** Redirect ke LoginActivity selepas akaun dipadam. */
    private static void goToLogin(Activity activity) {
        try {
            FirebaseAuth.getInstance().signOut();
        } catch (Exception ignored) {}
        Toast.makeText(activity, R.string.delete_account_success, Toast.LENGTH_SHORT).show();
        Intent i = new Intent(activity, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(i);
        activity.finish();
    }
}
