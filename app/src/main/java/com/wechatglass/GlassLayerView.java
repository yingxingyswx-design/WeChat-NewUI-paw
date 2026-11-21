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

/**
 * Glass layer modelled on LiquidGlass's panel: records the content behind it
 * into a RenderNode and draws that node with a RenderEffect blur.
 */
final class GlassLayerView extends View {

    private ViewGroup backdrop;
    private RenderNode node;
    private RenderEffect effect;

    GlassLayerView(Context context) {
        super(context);
        setWillNotDraw(false);
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

        int save = canvas.save();
        canvas.clipRect(0, 0, width, height);
        canvas.drawRenderNode(node);
        canvas.restoreToCount(save);
    }
}
