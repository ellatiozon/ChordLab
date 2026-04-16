package com.example.chordlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class CircularProgressView extends View {

    private Paint trackPaint;
    private Paint progressPaint;
    private RectF oval;
    private float progress = 0f; // 0–100

    public CircularProgressView(Context context) {
        super(context);
        init();
    }

    public CircularProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(18f);
        trackPaint.setColor(0xFFE0E0F0);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(18f);
        progressPaint.setColor(0xFFE91E8C);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        oval = new RectF();
    }

    public void setProgress(float progress) {
        this.progress = Math.max(0, Math.min(100, progress));
        invalidate();
    }

    public float getProgress() {
        return progress;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float padding = 20f;
        oval.set(padding, padding, getWidth() - padding, getHeight() - padding);

        // Draw track
        canvas.drawArc(oval, -90, 360, false, trackPaint);

        // Draw progress
        float sweepAngle = 360f * (progress / 100f);
        canvas.drawArc(oval, -90, sweepAngle, false, progressPaint);
    }
}