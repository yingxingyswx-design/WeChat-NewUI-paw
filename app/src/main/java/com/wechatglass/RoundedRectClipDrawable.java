package com.wechatglass;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Draws an ImageView's current content through a rounded-rectangle clip. */
final class RoundedRectClipDrawable extends Drawable implements Drawable.Callback {

    private final Drawable wrapped;
    private final float radius;
    private final Path clipPath = new Path();
    private final RectF clipRect = new RectF();

    RoundedRectClipDrawable(Drawable wrapped, float radius) {
        this.wrapped = wrapped;
        this.radius = radius;
        wrapped.setCallback(this);
    }

    Drawable getWrappedDrawable() {
        return wrapped;
    }

    boolean hasRadius(float value) {
        return Float.compare(radius, value) == 0;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }
        int save = canvas.save();
        clipRect.set(bounds);
        clipPath.reset();
        clipPath.addRoundRect(clipRect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);
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
        wrapped.setBounds(bounds);
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
