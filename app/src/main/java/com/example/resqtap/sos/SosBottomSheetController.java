package com.example.resqtap.sos;
import com.example.resqtap.R;

import android.app.Activity;
import android.os.CountDownTimer;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetBehavior;


/**
 * SosBottomSheetController
 * Controller popup SOS: countdown 3 saat untuk elak tersalah tekan, lepas tu auto-trigger kecemasan.
 */
public final class SosBottomSheetController {
    private final Activity activity;
    private final SosCallbacks callbacks;

    private BottomSheetDialog dialog;
    private CountDownTimer timer;

    public interface SosCallbacks {
        default void onSosStarted() {}
        void onCountdownFinished();
        void onSosCancelled();
    }

    public SosBottomSheetController(Activity activity, Runnable onSosTriggered) {
        this(activity, new SosCallbacks() {
            @Override public void onCountdownFinished() {
                try { if (onSosTriggered != null) onSosTriggered.run(); } catch (Exception ignored) {}
            }
            @Override public void onSosCancelled() {}
        });
    }

    public SosBottomSheetController(Activity activity, SosCallbacks callbacks) {
        this.activity = activity;
        this.callbacks = callbacks;
    }

    /** Paparkan . */
    public void show() {
        if (activity == null) return;
        if (dialog != null && dialog.isShowing()) return;

        BottomSheetDialog d = new BottomSheetDialog(activity);
        android.view.View sheet = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_sos, null);
        d.setContentView(sheet);

        d.setCancelable(false);
        d.setCanceledOnTouchOutside(false);
        d.setOnDismissListener(di -> cancel());

        lockBottomSheet(d);

        TextView tvCount = sheet.findViewById(R.id.sos_sheet_count);
        android.widget.SeekBar seek = sheet.findViewById(R.id.sos_sheet_seek);
        if (seek != null) {
            seek.setProgress(0);
            seek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(android.widget.SeekBar s, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    if (progress >= 95) {
                        cancel();
                        try {
                            if (callbacks != null) callbacks.onSosCancelled();
                        } catch (Exception ignored) {
                        }
                        try { d.dismiss(); } catch (Exception ignored) {}
                    }
                }
                @Override public void onStartTrackingTouch(android.widget.SeekBar s) {}
                @Override public void onStopTrackingTouch(android.widget.SeekBar s) {
                    try {
                        if (s.getProgress() < 95) s.setProgress(0);
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        dialog = d;
        d.show();

        // JANGAN hantar sebarang isyarat SOS semasa kiraan 10 saat sedang berjalan
        startCountdown(tvCount);
    }

    private static void lockBottomSheet(BottomSheetDialog d) {
        if (d == null) return;
        try {
            android.widget.FrameLayout bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet == null) return;
            BottomSheetBehavior<android.widget.FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
            behavior.setHideable(false);
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            setDraggableCompat(behavior, false);
            behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
    @Override
                public void onStateChanged(@NonNull android.view.View bs, int newState) {
                    if (newState == BottomSheetBehavior.STATE_HIDDEN || newState == BottomSheetBehavior.STATE_COLLAPSED) {
                        try {
                            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                        } catch (Exception ignored) {
                        }
                    }
                }

    @Override
                public void onSlide(@NonNull android.view.View bs, float slideOffset) {

                }
            });
        } catch (Exception ignored) {
        }
    }

    private static void setDraggableCompat(BottomSheetBehavior<?> behavior, boolean draggable) {
        if (behavior == null) return;
        try {

            java.lang.reflect.Method m = behavior.getClass().getMethod("setDraggable", boolean.class);
            m.invoke(behavior, draggable);
        } catch (Exception ignored) {
        }
    }

    public void cancel() {
        cancelTimer();
        try {
            if (dialog != null && dialog.isShowing()) dialog.dismiss();
        } catch (Exception ignored) {
        }
        dialog = null;
    }

    private void startCountdown(TextView tvCount) {
        cancelTimer();
        final long totalMs = 10_000L;
        if (tvCount != null) tvCount.setText("10");
        timer = new CountDownTimer(totalMs, 1000L) {
    @Override
            public void onTick(long msUntilFinished) {
                long sec = Math.max(0, (msUntilFinished + 999L) / 1000L);
                if (tvCount != null) tvCount.setText(String.valueOf(sec));
            }

            @Override
            public void onFinish() {
                if (tvCount != null) tvCount.setText("0");
                try {
                    if (dialog != null) dialog.dismiss();
                } catch (Exception ignored) {
                }
                try {
                    if (callbacks != null) callbacks.onCountdownFinished();
                } catch (Exception ignored) {
                }
            }
        };
        timer.start();
    }

    private void cancelTimer() {
        try {
            if (timer != null) timer.cancel();
        } catch (Exception ignored) {
        }
        timer = null;
    }
}
