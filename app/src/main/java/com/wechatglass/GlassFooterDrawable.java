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
    private final View footer;
    private final View anchor;
    private final ViewGroup backdrop;
    private RenderNode node;
    private RenderEffect effect;
    private final Path shape = new Path();
    private final android.graphics.Paint tintPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    GlassFooterDrawable(View footer, View anchor, ViewGroup backdrop) {
        this.footer = footer;
        this.anchor = anchor;
        this.backdrop = backdrop;
    }

    @Override
    public void draw(Canvas canvas) {
        int width = anchor.getWidth();
        int screenH = footer.getResources().getDisplayMetrics().heightPixels;
        int[] al0 = new int[2];
        anchor.getLocationOnScreen(al0);
        int height = Math.min(anchor.getHeight(), Math.max(1, screenH - al0[1]));
        if (Build.VERSION.SDK_INT < 31 || backdrop == null || width <= 0 || height <= 0) {
            return;
        }
        android.util.Log.i("WeChatGlass", "footer drawable called anchor=" + anchor.getClass().getSimpleName()
                + " w=" + width + " h=" + height);
        int[] fl = new int[2];
        footer.getLocationOnScreen(fl);
        int[] al = new int[2];
        anchor.getLocationOnScreen(al);
        if (node == null) {
            node = new RenderNode("wxGlassFooter");
        }
        node.setPosition(0, 0, width, height);
        RecordingCanvas rc = node.beginRecording(width, height);
        rc.drawColor(0x00000000);
        Backdrop.draw(rc, backdrop, al[0], al[1], width, height);
        node.endRecording();
        if (effect == null) {
            effect = RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP);
        }
        node.setRenderEffect(effect);
        float radius = height / 2f;
        shape.reset();
        rect.set(0, 0, width, height);
        shape.addRoundRect(rect, radius, radius, Path.Direction.CW);
        android.graphics.Rect cb = new android.graphics.Rect();
        canvas.getClipBounds(cb);
        android.util.Log.i("WeChatGlass", "draw diag anchor=" + anchor.getClass().getSimpleName()
                + " aLoc=" + al[0] + "," + al[1] + " hw=" + width + "x" + height
                + " clip=" + cb + " host=" + footer.getClass().getSimpleName());
        int save = canvas.save();
        canvas.translate(al[0] - fl[0], al[1] - fl[1]);
        canvas.clipPath(shape);
        canvas.drawRenderNode(node);
        // WeChat-gray hairline around the capsule
        tintPaint.setStyle(android.graphics.Paint.Style.STROKE);
        tintPaint.setStrokeWidth(footer.getResources().getDisplayMetrics().density);
        tintPaint.setColor(Ui.isNight(footer.getContext()) ? 0x22FFFFFF : 0x33000000);
        canvas.drawPath(shape, tintPaint);
        tintPaint.setStyle(android.graphics.Paint.Style.FILL);
        canvas.restoreToCount(save);
    }

    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
    @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
}
