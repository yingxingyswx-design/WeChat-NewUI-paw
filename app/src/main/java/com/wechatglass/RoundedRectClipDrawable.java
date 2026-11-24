package com.wechatglass;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Draws an ImageView's current content through a rounded-rectangle clip. */
final class RoundedRectClipDrawable extends Drawable
        implements Drawable.Callback, android.graphics.drawable.Animatable {

    private final Drawable wrapped;
    private final float radius;
    private final int inset;
    private final float offsetX;
    private final Path clipPath = new Path();
    private final RectF clipRect = new RectF();
    private final Rect contentBounds = new Rect();

    RoundedRectClipDrawable(Drawable wrapped, float radius) {
        this(wrapped, radius, 0);
    }

    RoundedRectClipDrawable(Drawable wrapped, float radius, int inset) {
        this(wrapped, radius, inset, 0f);
    }

    RoundedRectClipDrawable(Drawable wrapped, float radius, int inset, float offsetX) {
        this.wrapped = wrapped;
        this.radius = radius;
        this.inset = Math.max(0, inset);
        this.offsetX = offsetX;
        wrapped.setCallback(this);
    }

    Drawable getWrappedDrawable() {
        return wrapped;
    }

    boolean hasRadius(float value) {
        return Float.compare(radius, value) == 0;
    }

    boolean hasRadiusAndInset(float value, int expectedInset) {
        return Float.compare(radius, value) == 0 && inset == expectedInset;
    }

    boolean hasRadiusInsetAndOffset(float value, int expectedInset, float expectedOffsetX) {
        return hasRadiusAndInset(value, expectedInset)
                && Float.compare(offsetX, expectedOffsetX) == 0;
    }

    int getInset() {
        return inset;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        int minSide = Math.min(bounds.width(), bounds.height());
        if (bounds.isEmpty() || minSide <= 0) {
            return;
        }
        float scale = Math.max(0f, (minSide - 2f * inset) / minSide);
        float cx = bounds.exactCenterX();
        float cy = bounds.exactCenterY();
        float halfWidth = bounds.width() * scale / 2f;
        float halfHeight = bounds.height() * scale / 2f;
        float maxOffset = Math.max(0f, bounds.width() / 2f - halfWidth);
        float appliedOffset = Math.max(-maxOffset, Math.min(maxOffset, offsetX));
        if (scale <= 0f) {
            return;
        }
        int save = canvas.save();
        clipRect.set(cx - halfWidth + appliedOffset, cy - halfHeight,
                cx + halfWidth + appliedOffset, cy + halfHeight);
        clipPath.reset();
        clipPath.addRoundRect(clipRect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);
        canvas.translate(appliedOffset, 0f);
        canvas.scale(scale, scale, cx, cy);
        wrapped.setBounds(bounds);
        wrapped.draw(canvas);
        canvas.restoreToCount(save);
    }

    @Override
    public void setAlpha(int alpha) {
        wrapped.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        wrapped.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        contentBounds.set(bounds);
        wrapped.setBounds(contentBounds);
    }

    @Override
    protected boolean onStateChange(int[] state) {
        return wrapped.setState(state);
    }

    @Override
    protected boolean onLevelChange(int level) {
        return wrapped.setLevel(level);
    }

    @Override
    public boolean isStateful() {
        return wrapped.isStateful();
    }

    @Override
    public int getIntrinsicWidth() {
        return wrapped.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return wrapped.getIntrinsicHeight();
    }

    @Override
    public void start() {
        if (wrapped instanceof android.graphics.drawable.Animatable) {
            ((android.graphics.drawable.Animatable) wrapped).start();
        }
    }

    @Override
    public void stop() {
        if (wrapped instanceof android.graphics.drawable.Animatable) {
            ((android.graphics.drawable.Animatable) wrapped).stop();
        }
    }

    @Override
    public boolean isRunning() {
        return wrapped instanceof android.graphics.drawable.Animatable
                && ((android.graphics.drawable.Animatable) wrapped).isRunning();
    }

    @Override
    public void invalidateDrawable(Drawable who) {
        invalidateSelf();
    }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) {
        scheduleSelf(what, when);
    }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) {
        unscheduleSelf(what);
    }
}
