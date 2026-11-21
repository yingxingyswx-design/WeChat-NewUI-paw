package com.wechatglass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

/**
 * Dedicated glass layer: records the content behind the bar into a RenderNode,
 * blurs it, and paints it clipped to each chip of the host overlay.
 *
 * <p>It exists because drawing a RenderNode from inside a chip's own onDraw
 * (nested in the bar's display list) both failed to blur and crashed hwui on
 * some devices. Here the node is drawn by a sibling View instead.
 */
final class GlassLayerView extends View {

    private final ViewGroup host;
    private ViewGroup backdrop;
    private RenderNode node;
    private RenderEffect effect;
    private final Path shape = new Path();
    private final RectF rect = new RectF();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    GlassLayerView(Context context, ViewGroup host) {
        super(context);
        this.host = host;
        setWillNotDraw(false);
        borderPaint.setStyle(Paint.Style.STROKE);
    }

    void setBackdrop(ViewGroup view) {
        backdrop = view;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (Build.VERSION.SDK_INT < 31 || backdrop == null
                || !backdrop.isShown() || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        int pad = (int) (6 * getResources().getDisplayMetrics().density);
        int width = getWidth() + pad * 2;
        int height = getHeight() + pad * 2;
        int[] loc = new int[2];
        getLocationOnScreen(loc);

        if (node == null) {
            node = new RenderNode("wxGlassLayer");
        }
        node.setPosition(0, 0, width, height);
        RecordingCanvas rc = node.beginRecording(width, height);
        rc.drawColor(0x00000000);
        BackdropLite.draw(rc, backdrop, loc[0] - pad, loc[1] - pad, width, height);
        node.endRecording();

        if (effect == null) {
            effect = RenderEffect.createBlurEffect(
                    pad * 2f, pad * 2f, Shader.TileMode.CLAMP);
        }
        node.setRenderEffect(effect);

        for (int i = 0; i < host.getChildCount(); i++) {
            View child = host.getChildAt(i);
            if (child == this || child.getVisibility() != View.VISIBLE
                    || child.getWidth() <= 0 || child.getHeight() <= 0) {
                continue;
            }
            float radius = child.getHeight() / 2f;
            shape.reset();
            rect.set(child.getLeft(), child.getTop(),
                    child.getRight(), child.getBottom());
            shape.addRoundRect(rect, radius, radius, Path.Direction.CW);

            int save = canvas.save();
            canvas.clipPath(shape);
            canvas.translate(-pad, -pad);
            canvas.drawRenderNode(node);
            canvas.restoreToCount(save);
        }
    }
}
