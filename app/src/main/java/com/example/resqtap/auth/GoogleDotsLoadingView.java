package com.example.resqtap.auth;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

/**
 * GoogleDotsLoadingView
 * Penunjuk animasi gelombang 5 titik warna Google (Hijau, Merah, Biru, Kuning, Jingga)
 * yang melompat lembut secara berurutan menyerupai animasi Google Assistant / Sign In.
 */
public class GoogleDotsLoadingView extends View {

    // 5 warna ikonik Google mengikut turutan gambar: Hijau, Merah, Biru, Kuning, Jingga
    private static final int[] DOT_COLORS = new int[]{
            0xFF34A853, // Hijau (Google Green)
            0xFFEA4335, // Merah (Google Red)
            0xFF4285F4, // Biru (Google Blue)
            0xFFFBBC05, // Kuning (Google Yellow)
            0xFFFA7B17  // Jingga (Google Orange)
    };

    private static final int DOT_COUNT = 5;

    private Paint paint;
    private ValueAnimator animator;
    private float animatedFraction = 0f;

    private float dotRadius;
    private float dotSpacing;
    private float maxBouncePx;

    public GoogleDotsLoadingView(Context context) {
        super(context);
        init();
    }

    public GoogleDotsLoadingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GoogleDotsLoadingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);

        float density = getResources().getDisplayMetrics().density;
        dotRadius = 4.8f * density;    // ~10dp diameter
        dotSpacing = 16f * density;    // 16dp spacing center-to-center
        maxBouncePx = 7.5f * density;  // Ketinggian lompatan 7.5dp

        setupAnimator();
    }

    private void setupAnimator() {
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1250);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            animatedFraction = (float) animation.getAnimatedValue();
            invalidate();
        });
    }

    public void start() {
        if (animator != null && !animator.isRunning()) {
            animator.start();
        }
    }

    public void stop() {
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
        animatedFraction = 0f;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int desiredWidth = Math.round((DOT_COUNT - 1) * dotSpacing + dotRadius * 2 + 16 * density);
        int desiredHeight = Math.round(36 * density);

        int width = resolveSize(desiredWidth, widthMeasureSpec);
        int height = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float totalWidth = (DOT_COUNT - 1) * dotSpacing + (dotRadius * 2);
        float startX = (getWidth() - totalWidth) / 2f + dotRadius;
        float baseCenterY = getHeight() / 2f + (maxBouncePx * 0.35f);

        for (int i = 0; i < DOT_COUNT; i++) {
            float x = startX + (i * dotSpacing);

            // Pengiraan gelombang lompat sinusoidal mengikut urutan titik (left to right)
            double phase = (animatedFraction * 2.0 * Math.PI) - (i * 0.65);
            double sin = Math.sin(phase);

            // Hanya melompat ke atas apabila fasa sin > 0, berada di paras dasar apabila negatif
            float bounce = sin > 0 ? (float) Math.pow(sin, 1.8) : 0f;
            float currentY = baseCenterY - (bounce * maxBouncePx);
            float currentRadius = dotRadius * (1f + (bounce * 0.12f));

            paint.setColor(DOT_COLORS[i]);
            canvas.drawCircle(x, currentY, currentRadius, paint);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getVisibility() == VISIBLE) {
            start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            start();
        } else {
            stop();
        }
    }
}
