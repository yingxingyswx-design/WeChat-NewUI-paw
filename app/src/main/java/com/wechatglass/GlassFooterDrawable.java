package com.wechatglass;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

final class GlassFooterDrawable extends Drawable {
    private final View footer;
    private final ViewGroup backdrop;
    private RenderNode node;
    private RenderEffect effect;
    private final Path shape = new Path();
    private final RectF rect = new RectF();

    GlassFooterDrawable(View footer, ViewGroup backdrop) {
        this.footer = footer;
        this.backdrop = backdrop;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        int width = bounds.width();
        int height = bounds.height();
        if (Build.VERSION.SDK_INT < 31 || backdrop == null || width <= 0 || height <= 0) {
            return;
        }
        int[] loc = new int[2];
        footer.getLocationOnScreen(loc);
        if (node == null) {
            node = new RenderNode("wxGlassFooter");
        }
        node.setPosition(0, 0, width, height);
        RecordingCanvas rc = node.beginRecording(width, height);
        rc.drawColor(0x00000000);
        Backdrop.draw(rc, backdrop, bounds.left + loc[0], bounds.top + loc[1], width, height);
        node.endRecording();
        if (effect == null) {
            effect = RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP);
        }
        node.setRenderEffect(effect);
        float radius = 18f * footer.getResources().getDisplayMetrics().density;
        shape.reset();
        rect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
        shape.addRoundRect(rect, radius, radius, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(shape);
        canvas.drawRenderNode(node);
        canvas.restoreToCount(save);
    }

    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
    @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
}