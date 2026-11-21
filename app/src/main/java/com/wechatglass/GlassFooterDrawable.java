package com.wechatglass;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

final class GlassFooterDrawable extends Drawable {
    private final View anchor;
    private final ViewGroup backdrop;
    private RenderNode node;
    private RenderEffect effect;
    private final Path shape = new Path();
    private final RectF rect = new RectF();

    GlassFooterDrawable(View anchor, ViewGroup backdrop) {
        this.anchor = anchor;
        this.backdrop = backdrop;
    }

    @Override
    public void draw(Canvas canvas) {
        int width = anchor.getWidth();
        int height = anchor.getHeight();
        if (Build.VERSION.SDK_INT < 31 || backdrop == null || width <= 0 || height <= 0) {
            return;
        }
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        if (node == null) {
            node = new RenderNode("wxGlassFooter");
        }
        node.setPosition(0, 0, width, height);
        RecordingCanvas rc = node.beginRecording(width, height);
        rc.drawColor(0x00000000);
        Backdrop.draw(rc, backdrop, loc[0], loc[1], width, height);
        node.endRecording();
        if (effect == null) {
            effect = RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP);
        }
        node.setRenderEffect(effect);
        float radius = 18f * anchor.getResources().getDisplayMetrics().density;
        shape.reset();
        rect.set(0, 0, width, height);
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