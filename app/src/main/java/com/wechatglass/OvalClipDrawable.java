package com.wechatglass;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Clips a single chat avatar drawable to a circle; no global Hook involved. */
final class OvalClipDrawable extends Drawable {

    private final Drawable wrapped;
    private final Path path = new Path();

    OvalClipDrawable(Drawable wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) {
            return;
        }
        int save = canvas.save();
        path.reset();
        path.addOval(b.left, b.top, b.right, b.bottom, Path.Direction.CW);
        canvas.clipPath(path);
        wrapped.setBounds(b);
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
    public int getIntrinsicWidth() {
        return wrapped.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return wrapped.getIntrinsicHeight();
    }
}
