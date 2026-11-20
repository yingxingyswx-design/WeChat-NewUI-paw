package com.wechatglass;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Solid-color painter for every floating surface.
 *
 * The previous implementation recorded the whole chat window into a bitmap and
 * blurred it every other frame. That is a continuous screen-capture loop inside
 * a chat app, and it cost a 1/8-scale bitmap allocation ~30 times per second.
 * It has been removed: each surface now draws one opaque rounded rect, so the
 * cost is a single drawPath and the content behind the bar is never captured.
 */
final class GlassRenderer {

    private final View source;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final RectF rect = new RectF();

    GlassRenderer(View source) {
        this.source = source;
        fillPaint.setStyle(Paint.Style.FILL);
        borderPaint.setStyle(Paint.Style.STROKE);
    }

    // The blur pipeline is gone; these remain so callers stay unchanged.
    void setHiddenViews(View... views) {
    }

    void addClient(View view) {
    }

    void startFrameLoop() {
    }

    void requestUpdate() {
    }

    boolean isCapturing() {
        return false;
    }

    void drawGlass(Canvas canvas, View target, float left, float top, float right, float bottom,
                   float radius, int tintColor, int borderColor) {
        drawGlass(canvas, target, left, top, right, bottom,
                radius, radius, tintColor, borderColor);
    }

    void drawGlass(Canvas canvas, View target, float left, float top, float right, float bottom,
                   float topRadius, float bottomRadius, int tintColor, int borderColor) {
        if (canvas == null || right <= left || bottom <= top) {
            return;
        }
        shape.reset();
        rect.set(left, top, right, bottom);
        shape.addRoundRect(rect,
                new float[]{topRadius, topRadius, topRadius, topRadius,
                        bottomRadius, bottomRadius, bottomRadius, bottomRadius},
                Path.Direction.CW);

        fillPaint.setColor(tintColor);
        canvas.drawPath(shape, fillPaint);

        if (borderColor != 0) {
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(Math.max(1f, dp(1f)));
            borderPaint.setColor(borderColor);
            canvas.drawPath(shape, borderPaint);
        }
    }

    void drawGlassLegacy(Canvas canvas, View target, float left, float top, float right, float bottom,
                         float radius, int tintColor, int borderColor) {
        drawGlass(canvas, target, left, top, right, bottom, radius, tintColor, borderColor);
    }

    void destroy() {
    }

    private float dp(float value) {
        if (source == null || source.getResources() == null) {
            return value;
        }
        return value * source.getResources().getDisplayMetrics().density;
    }
}
