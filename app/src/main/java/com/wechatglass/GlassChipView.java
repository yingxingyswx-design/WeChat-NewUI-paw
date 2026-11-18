package com.wechatglass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.View;

/** A single floating glass island (back / title / menu / small text). */
final class GlassChipView extends View {

    private final GlassRenderer renderer;
    private float radius;
    private int tintColor;
    private int borderColor;
    private String label;
    private int labelColor;
    private Drawable icon;
    private boolean useBlur = true;
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flatPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    GlassChipView(Context context, GlassRenderer renderer, float radius,
                  int tintColor, int borderColor) {
        super(context);
        this.renderer = renderer;
        this.radius = radius;
        this.tintColor = tintColor;
        this.borderColor = borderColor;
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setGlass(float radius, int tintColor, int borderColor) {
        this.radius = radius;
        this.tintColor = tintColor;
        this.borderColor = borderColor;
        invalidate();
    }

    void setBlurBackground(boolean useBlur) {
        this.useBlur = useBlur;
        invalidate();
    }

    void setIcon(Drawable icon, int sizePx) {
        this.icon = icon;
        if (icon != null) {
            int half = sizePx / 2;
            icon.setBounds(-half, -half, half, half);
        }
        invalidate();
    }

    void setLabel(String label, int color, float textSizePx) {
        this.label = label;
        this.labelColor = color;
        textPaint.setTextSize(textSizePx);
        textPaint.setFakeBoldText(false);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (useBlur && renderer != null && !renderer.isCapturing()) {
            renderer.drawGlass(canvas, this, 0f, 0f, getWidth(), getHeight(),
                    radius, tintColor, borderColor);
        } else if (renderer == null || !renderer.isCapturing()) {
            flatPaint.setColor(tintColor);
            canvas.drawRoundRect(0f, 0f, getWidth(), getHeight(),
                    radius, radius, flatPaint);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(1.2f);
            strokePaint.setColor(borderColor);
            canvas.drawRoundRect(0.6f, 0.6f, getWidth() - 0.6f, getHeight() - 0.6f,
                    radius, radius, strokePaint);
        }
        if (icon != null) {
            int save = canvas.save();
            canvas.translate(getWidth() / 2f, getHeight() / 2f);
            icon.draw(canvas);
            canvas.restoreToCount(save);
        }
        if (label != null && label.length() > 0) {
            textPaint.setColor(labelColor);
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float baseline = getHeight() / 2f - (metrics.ascent + metrics.descent) / 2f;
            float width = textPaint.measureText(label);
            canvas.drawText(label, (getWidth() - width) / 2f, baseline, textPaint);
        }
    }
}
