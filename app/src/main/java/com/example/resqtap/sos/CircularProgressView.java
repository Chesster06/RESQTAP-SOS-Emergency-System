package com.example.resqtap.sos;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * CircularProgressView
 * Melukis lengkok merah (arc) yang memanjang dari 0 sehingga penuh (100% / 360 darjah) mengikut saat.
 */
public class CircularProgressView extends View {

    private Paint progressPaint;
    private RectF arcBounds;
    private float progress = 1.0f; // Sentiasa penuh (100% / 1.0f) mengikut reka bentuk static

    public CircularProgressView(Context context) {
        super(context);
        init();
    }

    public CircularProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CircularProgressView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setColor(0xFFE60067); // ResQTap Brand Pink (#E60067)
        progressPaint.setStrokeCap(Paint.Cap.BUTT); // Clean flat cut matching mockup
        arcBounds = new RectF();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float strokePx = 4f * getResources().getDisplayMetrics().density;
        progressPaint.setStrokeWidth(strokePx);

        // Center 100, radius 90 out of 200 viewport = 90/100 (0.9) of half-size
        float cx = w / 2f;
        float cy = h / 2f;
        float radius = (Math.min(w, h) / 2f) * 0.90f;

        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (progress > 0f) {
            float sweepAngle = progress * 360f;
            // Bermula dari jam 12 (-90 darjah) dan berputar ikut arah jam
            canvas.drawArc(arcBounds, -90f, sweepAngle, false, progressPaint);
        }
    }

    /**
     * Tetapkan progress dari 0.0f hingga 1.0f
     */
    public void setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        invalidate();
    }

    public float getProgress() {
        return progress;
    }
}
