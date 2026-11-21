package com.wechatglass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

/** LiquidGlass-style layer: capture pre-draw, draw node with blur (no clipping yet). */
final class GlassLayerView extends View {

    private ViewGroup backdrop;
    private RenderNode node;
    private RenderEffect effect;
    private ViewTreeObserver.OnPreDrawListener preDraw;

    GlassLayerView(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    void setBackdrop(ViewGroup view) {
        detach();
        backdrop = view;
        attach();
        invalidate();
    }

    private void attach() {
        if (backdrop == null || preDraw != null) {
            return;
        }
        preDraw = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                capture();
                return true;
            }
        };
        backdrop.getViewTreeObserver().addOnPreDrawListener(preDraw);
    }

    private void detach() {
        if (preDraw != null && backdrop != null) {
            backdrop.getViewTreeObserver().removeOnPreDrawListener(preDraw);
        }
        preDraw = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        detach();
        super.onDetachedFromWindow();
    }

    private void capture() {
        if (Build.VERSION.SDK_INT < 31 || backdrop == null
                || !backdrop.isShown() || getWidth() <= 0 || getHeight() <= 0
                || !isShown()) {
            return;
        }
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        int width = getWidth();
        int height = getHeight();
        if (node == null) {
            node = new RenderNode("wxGlassLayer");
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
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (node == null || !node.hasDisplayList()) {
            return;
        }
        int save = canvas.save();
        canvas.clipRect(0, 0, getWidth(), getHeight());
        canvas.drawRenderNode(node);
        canvas.restoreToCount(save);
    }
}
