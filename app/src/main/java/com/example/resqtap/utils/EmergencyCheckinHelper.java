package com.example.resqtap.utils;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import com.example.resqtap.R;
import com.example.resqtap.room.FirebaseRoomClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Helper untuk memaparkan Emergency Check-In Ping Dialog.
 * Membolehkan pengguna (termasuk introvert / yang enggan bercakap melalui panggilan)
 * mengesahkan status kecemasan dengan 1-ketikan senyap:
 * 1. "Saya Selamat (False Alarm)" -> Batalkan SOS & maklumkan admin.
 * 2. "Perlukan Bantuan Segera!" -> Sahkan status bahaya & hantar SOS serta-merta.
 */
public final class EmergencyCheckinHelper {

    private EmergencyCheckinHelper() {
    }

    public interface CheckinCallback {
        void onSafeSelected();
        void onDangerSelected();
    }

    /**
     * Memaparkan dialog Emergency Check-In dengan tindakan tersuai.
     */
    public static void showCheckInDialog(Activity activity, CheckinCallback callback) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_emergency_checkin, null);
        dialog.setContentView(view);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.90),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        View btnSafe = view.findViewById(R.id.btn_checkin_safe);
        View btnDanger = view.findViewById(R.id.btn_checkin_danger);

        if (btnSafe != null) {
            btnSafe.setOnClickListener(v -> {
                dialog.dismiss();
                Toast.makeText(activity, R.string.toast_checkin_safe, Toast.LENGTH_LONG).show();
                if (callback != null) {
                    callback.onSafeSelected();
                } else {
                    handleDefaultSafe(activity);
                }
            });
        }

        if (btnDanger != null) {
            btnDanger.setOnClickListener(v -> {
                dialog.dismiss();
                Toast.makeText(activity, R.string.toast_checkin_danger, Toast.LENGTH_LONG).show();
                if (callback != null) {
                    callback.onDangerSelected();
                } else {
                    handleDefaultDanger(activity);
                }
            });
        }

        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    /**
     * Paparan lalai yang terus membatalkan atau mencetuskan SOS Bilik aktif pengguna.
     */
    public static void showCheckInDialog(Activity activity) {
        showCheckInDialog(activity, null);
    }

    private static void handleDefaultSafe(Activity activity) {
        String dev = UserPrefs.getOrCreateDeviceId(activity);
        FirebaseUser cu = FirebaseAuth.getInstance().getCurrentUser();
        if (cu != null) {
            FirebaseRoomClient.cancelAllActiveSosForUser(cu.getUid(), dev);
        }
    }

    private static void handleDefaultDanger(Activity activity) {
        String code = UserPrefs.getActiveRoomCode(activity);
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u != null) {
            String dev = UserPrefs.getOrCreateDeviceId(activity);
            String name = UserPrefs.getName(activity);
            if (name == null || name.trim().isEmpty()) name = "User";
            String targetRoom = (code != null && !code.trim().isEmpty()) ? code.trim() : "DIRECT";
            FirebaseRoomClient.sendRoomSosQueued(targetRoom, u.getUid(), dev, name, null);
        }
    }
}
