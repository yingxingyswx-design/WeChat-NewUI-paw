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
 *
 * <p>For media/sticker messages the owner passes a positive {@code outset} so the
 * bubble is drawn past the media's own bounds — the media fills those bounds, so
 * a bubble inside them would be invisible. The media view keeps its size and the
 * bubble grows around it, which keeps it centred without adding a view.
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
    private final float outsetX;
    private final float outsetY;
    private boolean drawLogged;

    BubbleDrawable(int color, float radius, float tailSize, boolean outgoing) {
        this(color, radius, tailSize, outgoing, 0f, 0f);
    }

    BubbleDrawable(int color, float radius, float tailSize, boolean outgoing,
                   float outsetX, float outsetY) {
        this.color = color;
        this.paint.setColor(color);
        this.radius = radius;
        this.tailSize = tailSize;
        this.outgoing = outgoing;
        this.outsetX = outsetX;
        this.outsetY = outsetY;
    }

    boolean matches(int color, float radius, float tailSize, boolean outgoing) {
        return matches(color, radius, tailSize, outgoing, 0f, 0f);
    }

    boolean matches(int color, float radius, float tailSize, boolean outgoing,
                    float outsetX, float outsetY) {
        return this.color == color && this.radius == radius
                && this.tailSize == tailSize && this.outgoing == outgoing
                && this.outsetX == outsetX && this.outsetY == outsetY;
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

        float tailHeight = tailSize;
        // The bubble grows past the bounds by the outset, so it is larger than
        // whatever sits in those bounds and the content ends up centred.
        float left = b.left - outsetX;
        float top = b.top - outsetY;
        float right = b.right + outsetX;
        float bottom = b.bottom + outsetY;
        rect.set(left + (outgoing ? 0 : tailSize), top,
                right - (outgoing ? tailSize : 0), bottom);

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
            tail.lineTo(right, top);
            // End on the body's right edge rather than the outer edge. The
            // previous triangle touched the rounded body at only one point,
            // which made Path.Op.UNION produce a visually detached tail.
            tail.lineTo(rect.right, top + tailHeight);
        } else {
            tail.moveTo(rect.left, top);
            tail.lineTo(left, top);
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
