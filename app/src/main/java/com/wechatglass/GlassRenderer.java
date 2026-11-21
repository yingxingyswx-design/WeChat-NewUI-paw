package com.wechatglass;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

/**
 * Solid or GPU-frosted painter for the floating surfaces.
 *
 * GPU path: record the backdrop's already-built display lists into a small
 * RenderNode (only the bar rect), apply a RenderEffect blur, then draw the node
 * clipped to the capsule/circle path. No screen capture, no full-window redraw.
 */
final class GlassRenderer {

    private final View source;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final RectF rect = new RectF();
    private final ArrayList<View> clients = new ArrayList<>();

    private ViewGroup backdrop;
    private boolean glass;
    private RenderNode node;
    private RenderEffect effect;
    private boolean loggedGpu;
    private int draws;

    GlassRenderer(View source) {
        this.source = source;
        fillPaint.setStyle(Paint.Style.FILL);
        borderPaint.setStyle(Paint.Style.STROKE);
    }

    void setBackdrop(ViewGroup view) {
        backdrop = view;
        Log.i("WeChatGlass", "renderer backdrop=" + (view == null ? "null"
                : view.getClass().getName() + " " + view.getWidth() + "x" + view.getHeight()));
    }

    void setGlassEnabled(boolean enabled) {
        glass = enabled && Build.VERSION.SDK_INT >= 31;
        Log.i("WeChatGlass", "renderer glass requested=" + enabled
                + " sdk=" + Build.VERSION.SDK_INT + " -> glass=" + glass);
    }

    boolean isGlassEnabled() {
        return glass;
    }

    void setHiddenViews(View... views) {
    }

    void addClient(View view) {
        if (view != null && !clients.contains(view)) {
            clients.add(view);
        }
    }

    void startFrameLoop() {
    }

    void requestUpdate() {
        for (int i = 0; i < clients.size(); i++) {
            clients.get(i).invalidate();
        }
    }

    boolean isCapturing() {
        return false;
    }

    void drawGlass(Canvas canvas, View target, float left, float top, float right, float bottom,
                   float radius, int tintColor, int borderColor) {
        drawGlass(canvas, target, left, top, right, bottom,
                radius, radius, tintColor, borderColor);
    }

    void drawGlass(Canvas canvas, View target, float left, float top, float right, float bottom,
                   float topRadius, float bottomRadius, int tintColor, int borderColor) {
        if (canvas == null || target == null || right <= left || bottom <= top) {
            return;
        }
        shape.reset();
        rect.set(left, top, right, bottom);
        shape.addRoundRect(rect,
                new float[]{topRadius, topRadius, topRadius, topRadius,
                        bottomRadius, bottomRadius, bottomRadius, bottomRadius},
                Path.Direction.CW);

        if (draws < 24) {
            draws++;
            Log.i("WeChatGlass", "draw " + target.getClass().getSimpleName()
                    + " glass=" + glass
                    + " backdrop=" + (backdrop == null ? "null"
                    : backdrop.getClass().getSimpleName()
                    + "@" + backdrop.getWidth() + "x" + backdrop.getHeight())
                    + " bounds=" + (int) left + "," + (int) top + ","
                    + (int) right + "," + (int) bottom);
        }

        if (glass && backdrop != null && Build.VERSION.SDK_INT >= 31) {
            try {
                drawGpu(canvas, target, left, top, right, bottom, tintColor);
            } catch (Throwable throwable) {
                Log.e("WeChatGlass", "gpu glass failed, fallback solid", throwable);
                glass = false;
                drawSolid(canvas, tintColor, borderColor);
            }
        } else {
            if (!loggedGpu) {
                loggedGpu = true;
                Log.i("WeChatGlass", "solid path glass=" + glass
                        + " backdrop=" + (backdrop == null ? "null" : "ok"));
            }
            drawSolid(canvas, tintColor, borderColor);
        }
    }

    private void drawSolid(Canvas canvas, int tintColor, int borderColor) {
        fillPaint.setColor(tintColor);
        canvas.drawPath(shape, fillPaint);
        drawBorder(canvas, borderColor);
    }

    private void drawGpu(Canvas canvas, View target, float left, float top,
                         float right, float bottom, int tintColor) {
        int pad = (int) dp(10f);
        int width = Math.max(1, (int) Math.ceil(right - left) + pad * 2);
        int height = Math.max(1, (int) Math.ceil(bottom - top) + pad * 2);
        Log.i("WeChatGlass", "gpu draw target=" + target.getClass().getSimpleName()
                + " bounds=" + left + "," + top + "," + right + "," + bottom
                + " node=" + width + "x" + height);

        if (node == null) {
            node = new RenderNode("wxGlassBar");
        }
        node.setPosition(0, 0, width, height);
        RecordingCanvas rc = node.beginRecording(width, height);
        int night = Ui.isNight(source.getContext()) ? 0xFF191919 : 0xFFFFFFFF;
        rc.drawColor(night);
        int[] loc = new int[2];
        target.getLocationOnScreen(loc);
        BackdropLite.draw(rc, backdrop,
                loc[0] + (int) left - pad, loc[1] + (int) top - pad, width, height);
        node.endRecording();

        if (effect == null) {
            effect = RenderEffect.createBlurEffect(
                    dp(16f), dp(16f), Shader.TileMode.CLAMP);
        }
        node.setRenderEffect(effect);
        node.setAlpha(0.92f);

        if (!loggedGpu) {
            loggedGpu = true;
            Log.i("WeChatGlass", "gpu glass captured " + width + "x" + height
                    + " backdrop=" + backdrop.getClass().getName()
                    + " " + backdrop.getWidth() + "x" + backdrop.getHeight());
        }

        // Tinted base under the glass; alpha low enough that the blur reads.
        fillPaint.setColor(Ui.isNight(source.getContext())
                ? 0x4D000000 : 0x59FFFFFF);
        canvas.drawPath(shape, fillPaint);

        int save = canvas.save();
        canvas.clipPath(shape);
        canvas.translate(left - pad, top - pad);
        canvas.drawRenderNode(node);
        canvas.restoreToCount(save);

        // Frost wash + border on top of the blur.
        fillPaint.setColor(Ui.isNight(source.getContext())
                ? 0x26000000 : 0x33FFFFFF);
        canvas.drawPath(shape, fillPaint);
    }

    private void drawBorder(Canvas canvas, int borderColor) {
        if (borderColor == 0) {
            return;
        }
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(Math.max(1f, dp(1f)));
        borderPaint.setColor(borderColor);
        canvas.drawPath(shape, borderPaint);
    }

    void drawGlassLegacy(Canvas canvas, View target, float left, float top, float right, float bottom,
                         float radius, int tintColor, int borderColor) {
        drawGlass(canvas, target, left, top, right, bottom, radius, tintColor, borderColor);
    }

    void destroy() {
        clients.clear();
        backdrop = null;
        node = null;
        effect = null;
    }

    private float dp(float value) {
        if (source == null || source.getResources() == null) {
            return value;
        }
        return value * source.getResources().getDisplayMetrics().density;
    }
}
