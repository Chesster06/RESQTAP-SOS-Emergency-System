package com.example.resqtap.report;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * FrameLayout khas untuk membungkus Google Map dalam NestedScrollView.
 * Mengelakkan NestedScrollView daripada memintas (intercept) sentuhan drag/pan
 * supaya pengguna boleh drag dan gerakkan peta dengan lancar.
 */
public class TouchableFrameLayout extends FrameLayout {

    public TouchableFrameLayout(@NonNull Context context) {
        super(context);
    }

    public TouchableFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TouchableFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
}
