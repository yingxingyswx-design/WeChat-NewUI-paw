package com.wechatglass;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
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

    private Bitmap bandBitmap;
    private Canvas bandCanvas;
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    private void drawGpu(Canvas canvas, View target, float left, float top,
                         float right, float bottom, int tintColor) {
        int pad = (int) dp(6f);
        int bandWidth = Math.max(1, source.getWidth() + pad * 2);
        int height = Math.max(1, (int) Math.ceil(bottom - top) + pad * 2);
        int[] loc = new int[2];
        target.getLocationOnScreen(loc);
        int bandLeft = -pad;
        int bandTop = loc[1] + (int) top - pad;

        float scale = 0.25f;
        int bw = Math.max(1, (int) (bandWidth * scale));
        int bh = Math.max(1, (int) (height * scale));
        if (bandBitmap == null || bandBitmap.getWidth() != bw
                || bandBitmap.getHeight() != bh) {
            bandBitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            bandCanvas = new Canvas(bandBitmap);
        } else {
            bandBitmap.eraseColor(0);
            bandCanvas.setBitmap(bandBitmap);
        }

        bandCanvas.save();
        bandCanvas.scale(scale, scale);
        bandCanvas.clipRect(bandLeft, bandTop, bandLeft + bandWidth, bandTop + height);
        bandCanvas.translate(-bandLeft, -bandTop);
        try {
            backdrop.draw(bandCanvas);
        } catch (Throwable throwable) {
            Log.e("WeChatGlass", "backdrop software draw failed", throwable);
        }
        bandCanvas.restore();

        StackBlur.blur(bandBitmap, 8);

        if (draws < 12) {
            draws++;
            Log.i("WeChatGlass", "cpu glass band=" + bandWidth + "x" + height
                    + " bmp=" + bw + "x" + bh
                    + " backdrop=" + backdrop.getClass().getSimpleName());
        }

        int save = canvas.save();
        canvas.clipPath(shape);
        canvas.translate(bandLeft - loc[0], bandTop - loc[1]);
        srcRect.set(0, 0, bw, bh);
        dstRect.set(0, 0, bandWidth, height);
        canvas.drawBitmap(bandBitmap, srcRect, dstRect, bitmapPaint);
        canvas.restoreToCount(save);
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
