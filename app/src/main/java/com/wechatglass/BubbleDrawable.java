package com.wechatglass;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Restored pre-deletion tail state: body starts below the top tail area,
 * tail protrudes at the top corner, tail side is square.
 */
final class BubbleDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int color;
    private final Path body = new Path();
    private final Path tail = new Path();
    private final RectF rect = new RectF();
    private final float[] radii = new float[8];
    private final float radius;
    private final float tailSize;
    private final boolean outgoing;
    private final RectF contentBounds;
    private boolean drawLogged;

    BubbleDrawable(int color, float radius, float tailSize, boolean outgoing) {
        this(color, radius, tailSize, outgoing, null);
    }

    BubbleDrawable(int color, float radius, float tailSize, boolean outgoing,
                   RectF contentBounds) {
        this.color = color;
        this.paint.setColor(color);
        this.radius = radius;
        this.tailSize = tailSize;
        this.outgoing = outgoing;
        this.contentBounds = contentBounds == null ? null : new RectF(contentBounds);
    }

    boolean matches(int color, float radius, float tailSize, boolean outgoing) {
        return this.color == color && this.radius == radius
                && this.tailSize == tailSize && this.outgoing == outgoing
                && contentBounds == null;
    }

    boolean matches(int color, float radius, float tailSize, boolean outgoing,
                    RectF contentBounds) {
        if (this.color != color || this.radius != radius
                || this.tailSize != tailSize || this.outgoing != outgoing) {
            return false;
        }
        if (this.contentBounds == null || contentBounds == null) {
            return this.contentBounds == null && contentBounds == null;
        }
        return this.contentBounds.equals(contentBounds);
    }

    int fillColor() {
        return color;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) {
            logFirstDraw(b);
            return;
        }
        logFirstDraw(b);

        float contentLeft = contentBounds == null ? b.left : b.left + contentBounds.left;
        float contentTop = contentBounds == null ? b.top : b.top + contentBounds.top;
        float contentRight = contentBounds == null ? b.right : b.left + contentBounds.right;
        float contentBottom = contentBounds == null ? b.bottom : b.top + contentBounds.bottom;
        contentLeft = Math.max(b.left, Math.min(contentLeft, b.right));
        contentRight = Math.max(contentLeft, Math.min(contentRight, b.right));
        contentTop = Math.max(b.top, Math.min(contentTop, b.bottom));
        contentBottom = Math.max(contentTop, Math.min(contentBottom, b.bottom));
        float tailHeight = Math.min(tailSize, Math.max(0f, contentBottom - contentTop));
        float top = contentTop;
        rect.set(contentLeft + (outgoing ? 0 : tailSize), top,
                contentRight - (outgoing ? tailSize : 0), contentBottom);

        if (outgoing) {
            radii[0] = radius;
            radii[1] = radius;
            radii[2] = 0f;
            radii[3] = 0f;
            radii[4] = radius;
            radii[5] = radius;
            radii[6] = radius;
            radii[7] = radius;
        } else {
            radii[0] = 0f;
            radii[1] = 0f;
            radii[2] = radius;
            radii[3] = radius;
            radii[4] = radius;
            radii[5] = radius;
            radii[6] = radius;
            radii[7] = radius;
        }
        body.reset();
        body.addRoundRect(rect, radii, Path.Direction.CW);

        tail.reset();
        if (outgoing) {
            tail.moveTo(rect.right, top);
            tail.lineTo(contentRight, top);
            // End on the body's right edge rather than the outer edge. The
            // previous triangle touched the rounded body at only one point,
            // which made Path.Op.UNION produce a visually detached tail.
            tail.lineTo(rect.right, top + tailHeight);
        } else {
            tail.moveTo(rect.left, top);
            tail.lineTo(contentLeft, top);
            tail.lineTo(rect.left, top + tailHeight);
        }
        tail.close();
        // Draw the tail first and the body over its base. This avoids relying
        // on Path.op(), which can return an empty path on vendor renderers.
        canvas.drawPath(tail, paint);
        canvas.drawPath(body, paint);
    }

    private void logFirstDraw(Rect bounds) {
        if (drawLogged) {
            return;
        }
        drawLogged = true;
        android.util.Log.i("WeChatGlass", "CB drawable draw bounds="
                + bounds.toShortString() + " color=#"
                + Integer.toHexString(color));
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
