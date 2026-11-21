package com.wechatglass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.widget.FrameLayout;

/** FrameLayout that clips its content to a rect with per-corner radii. */
final class RoundClipLayout extends FrameLayout {

    private final Path path = new Path();
    private final float[] radii = new float[8];

    RoundClipLayout(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    void setRadii(float topLeft, float topRight, float bottomRight, float bottomLeft) {
        radii[0] = topLeft;  radii[1] = topLeft;
        radii[2] = topRight; radii[3] = topRight;
        radii[4] = bottomRight; radii[5] = bottomRight;
        radii[6] = bottomLeft;  radii[7] = bottomLeft;
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float max = Math.min(w, h) / 2f;
        float[] r = new float[8];
        for (int i = 0; i < 8; i++) {
            r[i] = Math.min(radii[i], max); // clamp
        }
        path.reset();
        path.addRoundRect(0, 0, w, h, r, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(path);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }
}
