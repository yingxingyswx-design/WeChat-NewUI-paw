package com.wechatglass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

/** LiquidGlass-style layer: capture pre-draw, blur, clip per chip. */
final class GlassLayerView extends View {

    private final ViewGroup host;
    private final Path shape = new Path();
    private final RectF rect = new RectF();
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ViewGroup backdrop;
    private RenderNode node;
    private RenderEffect effect;
    private ViewTreeObserver.OnPreDrawListener preDraw;

    GlassLayerView(Context context, ViewGroup host) {
        super(context);
        this.host = host;
        setWillNotDraw(false);
        tintPaint.setStyle(Paint.Style.FILL);
        borderPaint.setStyle(Paint.Style.STROKE);
    }

    void setBackdrop(ViewGroup view) {
        int[] bl = new int[2];
        if (view != null) { view.getLocationOnScreen(bl); }
        android.util.Log.i("WeChatGlass", "GL setBackdrop "
                + (view == null ? "null" : view.getClass().getSimpleName())
                + " at=" + bl[0] + "," + bl[1]
                + " " + (view == null ? 0 : view.getWidth()) + "x"
                + (view == null ? 0 : view.getHeight()));
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
        if (getWindowVisibility() != View.VISIBLE) { return; } // foreground gate
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
        android.util.Log.i("WeChatGlass", "GL cap done " + width + "x" + height
                + " hasList=" + node.hasDisplayList()
                + " backdrop=" + (backdrop == null ? "null" : backdrop.getClass().getSimpleName()));
        if (effect == null) {
            effect = RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP);
        }
        node.setRenderEffect(effect);
        android.util.Log.i("WeChatGlass", "layer capture " + width + "x" + height
                + " backdrop=" + backdrop.getClass().getSimpleName()
                + " hasList=" + node.hasDisplayList());
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!canvas.isHardwareAccelerated()) { return; } // SW guard
        int[] gl = new int[2];
        getLocationOnScreen(gl);
        android.util.Log.i("WeChatGlass", "layer onDraw geom=" + gl[0] + "," + gl[1]
                + " " + getWidth() + "x" + getHeight());
        if (getWindowVisibility() != View.VISIBLE) { return; } // foreground gate
        if (node == null || !node.hasDisplayList() || host == null) {
            return;
        }
        RenderEffect fresh = RenderEffect.createBlurEffect(48f, 48f, Shader.TileMode.CLAMP); // fresh effect
        node.setRenderEffect(fresh);
        android.util.Log.i("WeChatGlass", "GL effect set blur=48 hasList=" + node.hasDisplayList());
        boolean night = Ui.isNight(getContext());
        for (int i = 0; i < host.getChildCount(); i++) {
            View child = host.getChildAt(i);
            if (child == this || !(child instanceof GlassChipView)
                    || child.getVisibility() != View.VISIBLE
                    || child.getWidth() <= 0 || child.getHeight() <= 0) {
                continue;
            }
            float radius = child.getHeight() / 2f;
            shape.reset();
            rect.set(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
            shape.addRoundRect(rect, radius, radius, Path.Direction.CW);

            int save = canvas.save();
            canvas.clipPath(shape);
            canvas.drawRenderNode(node);
            canvas.drawPath(shape, tintPaint);
            tintPaint.setColor(night ? 0x33000000 : 0x33FFFFFF);
            canvas.drawPath(shape, tintPaint);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(Math.max(1f, 1f * getResources().getDisplayMetrics().density));
            borderPaint.setColor(night ? 0x22FFFFFF : 0x33000000);
            canvas.drawPath(shape, borderPaint);
            canvas.restoreToCount(save);
        }
    }
}
