package com.wechatglass;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Background drawable for the floating input bar. */
final class GlassBarDrawable extends Drawable {

    private final GlassRenderer renderer;
    private final View target;
    private float topRadius;
    private float bottomRadius;
    private final int tintColor;
    private final int borderColor;
    private int bottomInset;

    GlassBarDrawable(GlassRenderer renderer, View target, float radius,
                     int tintColor, int borderColor, int bottomInset) {
        this.renderer = renderer;
        this.target = target;
        this.topRadius = radius;
        this.bottomRadius = radius;
        this.tintColor = tintColor;
        this.borderColor = borderColor;
        this.bottomInset = bottomInset;
    }

    void setRadii(float top, float bottom) {
        if (topRadius != top || bottomRadius != bottom) {
            topRadius = top;
            bottomRadius = bottom;
            invalidateSelf();
        }
    }

    int getBottomInset() {
        return bottomInset;
    }

    void setBottomInset(int bottomInset) {
        if (this.bottomInset != bottomInset) {
            this.bottomInset = bottomInset;
            invalidateSelf();
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (renderer.isCapturing()) {
            return;
        }
        Rect bounds = getBounds();
        int height = bounds.height() - bottomInset;
        if (height <= 0) {
            return;
        }
        renderer.drawGlass(canvas, target, 0f, 0f, bounds.width(), height,
                topRadius, bottomRadius, tintColor, borderColor);
    }

    @Override
    public void setAlpha(int alpha) {
        // The glass alpha is part of tintColor; keep this a no-op so the
        // platform does not fade the whole blurred layer unexpectedly.
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
