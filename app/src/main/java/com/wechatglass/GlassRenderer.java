package com.wechatglass;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.view.Choreographer;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;

/**
 * CPU fallback blur source.
 *
 * RenderNode + RenderEffect caused a native recursion/SIGSEGV inside hwui when
 * the recorded window contained the glass surfaces, so this path deliberately
 * records a small bitmap instead. 1/8 downscale keeps the cost low enough for
 * scrolling; the blur is shared by every glass surface.
 */
final class GlassRenderer {

    private static final String TAG = "WeChatGlass";
    private static final float SCALE = 0.125f;

    private final View source;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<View> clients = new ArrayList<>();
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix shaderMatrix = new Matrix();
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();
    private final int[] sourceLocation = new int[2];
    private final int[] targetLocation = new int[2];

    private Bitmap blurBitmap;
    private BitmapShader blurShader;
    private boolean capturing;
    private RenderNode gpuNode;
    private RenderEffect gpuEffect;
    private int gpuW, gpuH;
    private boolean gpuFailed;
    private boolean frameLoopStarted;
    private int frameTick;
    private static java.lang.reflect.Method drawChildMethod;
    private static boolean drawChildResolved;
    private boolean pending;
    private boolean destroyed;
    private View[] hiddenViews = new View[0];

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            pending = false;
            updateNow();
        }
    };

    GlassRenderer(View source) {
        this.source = source;
        ColorMatrix saturation = new ColorMatrix();
        saturation.setSaturation(1.12f);
        bitmapPaint.setColorFilter(new ColorMatrixColorFilter(saturation));
    }

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (destroyed) return;
            frameLoopStarted = false;
            startFrameLoop();
            if (source.isShown() && !clients.isEmpty()) {
                for (int i = 0; i < clients.size(); i++) {
                    clients.get(i).invalidate();
                }
                if (++frameTick % 2 == 0 && !gpuFailed) {
                    requestUpdate();
                }
            }
        }
    };

    void startFrameLoop() {
        if (destroyed || frameLoopStarted) return;
        frameLoopStarted = true;
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    void setHiddenViews(View... views) {
        hiddenViews = views == null ? new View[0] : views;
    }

    void addClient(View view) {
        if (view != null && !clients.contains(view)) {
            clients.add(view);
        }
        startFrameLoop();
    }

    boolean isCapturing() {
        return capturing;
    }

    void requestUpdate() {
        if (destroyed || pending) {
            return;
        }
        pending = true;
        source.postOnAnimation(updateRunnable);
    }

    private void updateNow() {
        if (destroyed || capturing || source.getWidth() <= 0 || source.getHeight() <= 0) {
            return;
        }

        capturing = true;
        float[] oldAlpha = new float[hiddenViews.length];
        try {
            for (int i = 0; i < hiddenViews.length; i++) {
                View view = hiddenViews[i];
                if (view != null) {
                    oldAlpha[i] = view.getAlpha();
                    view.setAlpha(0f);
                }
            }

            int width = source.getWidth();
            int height = source.getHeight();
            int bitmapWidth = Math.max(1, (int) (width * SCALE));
            int bitmapHeight = Math.max(1, (int) (height * SCALE));
            Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Ui.isNight(source.getContext()) ? 0xFF101114 : 0xFFEDEDED);
            canvas.scale(bitmapWidth / (float) width, bitmapHeight / (float) height);
            source.draw(canvas);

            int radius = Math.max(2,
                    Math.round(Ui.dp(source.getContext(), 18f) * (bitmapWidth / (float) width)));
            BlurUtil.stackBlur(bitmap, radius);

            Bitmap old = blurBitmap;
            blurBitmap = bitmap;
            blurShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            if (old != null) {
                old.recycle();
            }
        } catch (Throwable throwable) {
            Log.e(TAG, "blur update failed", throwable);
        } finally {
            for (int i = 0; i < hiddenViews.length; i++) {
                View view = hiddenViews[i];
                if (view != null) {
                    view.setAlpha(oldAlpha[i]);
                }
            }
            capturing = false;
            for (int i = 0; i < clients.size(); i++) {
                clients.get(i).invalidate();
            }
        }
    }

    void drawGlass(Canvas canvas, View target, float left, float top, float right, float bottom,
                   float radius, int tintColor, int borderColor) {
        drawGlass(canvas, target, left, top, right, bottom, radius, radius, tintColor, borderColor);
    }

    void drawGlass(Canvas canvas, View target, float left, float top, float right, float bottom,
                   float topRadius, float bottomRadius, int tintColor, int borderColor) {
        if (canvas == null || target == null || right <= left || bottom <= top) {
            return;
        }
        if (false && !gpuFailed && canvas.isHardwareAccelerated()
                && android.os.Build.VERSION.SDK_INT >= 31) {
            try {
                drawGpu(canvas, target, left, top, right, bottom,
                        topRadius, tintColor, borderColor);
                return;
            } catch (Throwable t) {
                gpuFailed = true;
                Log.e(TAG, "gpu glass failed, fallback", t);
            }
        }
        Path shape = new Path();
        shape.addRoundRect(new RectF(left, top, right, bottom),
                new float[]{topRadius, topRadius, topRadius, topRadius,
                        bottomRadius, bottomRadius, bottomRadius, bottomRadius},
                Path.Direction.CW);
        dstRect.set(left, top, right, bottom);
        {
            if (blurBitmap != null && !blurBitmap.isRecycled()) {
                drawBitmapPath(canvas, target, left, top, right, bottom, shape);
            } else {
                tintPaint.setColor(Ui.isNight(source.getContext()) ? 0xFF202124 : 0xFFE8E8E8);
                canvas.drawPath(shape, tintPaint);
            }
            tintPaint.setColor(tintColor);
            canvas.drawPath(shape, tintPaint);
            if (borderColor != 0) {
                borderPaint.setStyle(Paint.Style.STROKE);
                borderPaint.setStrokeWidth(Math.max(1.2f, Ui.dp(source.getContext(), 1.2f)));
                borderPaint.setColor(borderColor);
                canvas.drawPath(shape, borderPaint);
            }
        }
    }

    private void drawBitmapPath(Canvas canvas, View target, float left, float top,
                                float right, float bottom, Path shape) {
        source.getLocationOnScreen(sourceLocation);
        target.getLocationOnScreen(targetLocation);
        float offsetX = targetLocation[0] - sourceLocation[0];
        float offsetY = targetLocation[1] - sourceLocation[1];
        float scaleX = blurBitmap.getWidth() / (float) source.getWidth();
        float scaleY = blurBitmap.getHeight() / (float) source.getHeight();
        int sx = Math.round((offsetX + left) * scaleX);
        int sy = Math.round((offsetY + top) * scaleY);
        int sw = Math.max(1, Math.round((right - left) * scaleX));
        int sh = Math.max(1, Math.round((bottom - top) * scaleY));
        sx = Math.max(0, Math.min(sx, blurBitmap.getWidth() - 1));
        sy = Math.max(0, Math.min(sy, blurBitmap.getHeight() - 1));
        sw = Math.min(sw, blurBitmap.getWidth() - sx);
        sh = Math.min(sh, blurBitmap.getHeight() - sy);
        srcRect.set(sx, sy, sx + sw, sy + sh);
        float dstScaleX = (right - left) / (float) srcRect.width();
        float dstScaleY = (bottom - top) / (float) srcRect.height();
        shaderMatrix.reset();
        shaderMatrix.setScale(dstScaleX, dstScaleY);
        shaderMatrix.postTranslate(left - srcRect.left * dstScaleX,
                top - srcRect.top * dstScaleY);
        blurShader.setLocalMatrix(shaderMatrix);
        bitmapPaint.setShader(blurShader);
        canvas.drawPath(shape, bitmapPaint);
        bitmapPaint.setShader(null);
    }

    void drawGlassLegacy(Canvas canvas, View target, float left, float top, float right, float bottom,
                   float radius, int tintColor, int borderColor) {
        if (canvas == null || target == null || right <= left || bottom <= top) {
            return;
        }

        dstRect.set(left, top, right, bottom);
        float corner = Math.max(0f, Math.min(radius, Math.min(right - left, bottom - top) / 2f));

        if (!gpuFailed && canvas.isHardwareAccelerated()
                && android.os.Build.VERSION.SDK_INT >= 31) {
            try {
                drawGpu(canvas, target, left, top, right, bottom, corner, tintColor, borderColor);
                return;
            } catch (Throwable t) {
                gpuFailed = true;
                Log.e(TAG, "gpu glass failed, fallback", t);
            }
        }

        if (blurBitmap != null && !blurBitmap.isRecycled()) {
            drawBitmapRegion(canvas, target, left, top, right, bottom, corner);
        } else {
            tintPaint.setColor(Ui.isNight(source.getContext()) ? 0xFF202124 : 0xFFE8E8E8);
            canvas.drawRoundRect(dstRect, corner, corner, tintPaint);
        }

        tintPaint.setColor(tintColor);
        canvas.drawRoundRect(dstRect, corner, corner, tintPaint);

        sheenPaint.setShader(new LinearGradient(0f, top, 0f, bottom,
                new int[]{0x26FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(dstRect, corner, corner, sheenPaint);
        sheenPaint.setShader(null);

        if (borderColor != 0) {
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(Math.max(1.2f, Ui.dp(source.getContext(), 1.2f)));
            borderPaint.setColor(borderColor);
            float inset = borderPaint.getStrokeWidth() / 2f;
            RectF borderRect = new RectF(left + inset, top + inset, right - inset, bottom - inset);
            canvas.drawRoundRect(borderRect, Math.max(0f, corner - inset),
                    Math.max(0f, corner - inset), borderPaint);
        }
    }

    private boolean containsTarget(View view, View target) {
        if (view == target) return true;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsTarget(group.getChildAt(i), target)) return true;
            }
        }
        return false;
    }

    private boolean isHidden(View view) {
        for (int i = 0; i < hiddenViews.length; i++) {
            if (hiddenViews[i] == view) return true;
        }
        return false;
    }

    private void drawChildTree(RecordingCanvas rc, View target) {
        if (!(source instanceof android.view.ViewGroup)) {
            Log.e(TAG, "gpu source is not ViewGroup: " + source.getClass());
            gpuFailed = true;
            return;
        }
        if (!drawChildResolved) {
            drawChildResolved = true;
            try {
                drawChildMethod = android.view.ViewGroup.class.getDeclaredMethod(
                        "drawChild", Canvas.class, View.class, long.class);
                drawChildMethod.setAccessible(true);
            } catch (Throwable ignored) {
            }
        }
        if (drawChildMethod == null) {
            gpuFailed = true;
            return;
        }
        android.view.ViewGroup group = (android.view.ViewGroup) source;
        rc.drawColor(Ui.isNight(source.getContext()) ? 0xFF101114 : 0xFFEDEDED);
        long drawingTime = source.getDrawingTime();
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE || isHidden(child)
                    || containsTarget(child, target)) continue;
            try {
                drawChildMethod.invoke(group, rc, child, drawingTime);
            } catch (Throwable t) {
                Throwable cause = t.getCause() != null ? t.getCause() : t;
                Log.e(TAG, "gpu drawChild failed: " + cause, cause);
                gpuFailed = true;
                return;
            }
        }
    }

    private void drawGpu(Canvas canvas, View target, float left, float top,
                         float right, float bottom, float corner,
                         int tintColor, int borderColor) {
        int w = Math.max(1, Math.round(right - left));
        int h = Math.max(1, Math.round(bottom - top));
        if (gpuNode == null) {
            gpuNode = new RenderNode("wxGlass");
        Log.i(TAG, "gpu enter w=" + w + " h=" + h + " hw=" + canvas.isHardwareAccelerated());
        }
        if (gpuEffect == null || gpuW != w || gpuH != h) {
            float radius = Math.max(1f, Ui.dp(source.getContext(), 14f));
            gpuEffect = RenderEffect.createBlurEffect(radius, radius,
                    Shader.TileMode.CLAMP);
            gpuW = w;
            gpuH = h;
        }
        gpuNode.setPosition(0, 0, w, h);
        RecordingCanvas rc = gpuNode.beginRecording(w, h);
        capturing = true;
        try {
            source.getLocationOnScreen(sourceLocation);
            target.getLocationOnScreen(targetLocation);
            rc.translate(sourceLocation[0] - targetLocation[0] + left,
                    sourceLocation[1] - targetLocation[1] + top);
            drawChildTree(rc, target);
        } finally {
            capturing = false;
            gpuNode.endRecording();
        }
        gpuNode.setRenderEffect(gpuEffect);
        int save = canvas.save();
        RectF clip = new RectF(left, top, right, bottom);
        Path path = new Path();
        path.addRoundRect(clip, corner, corner, Path.Direction.CW);
        canvas.clipPath(path);
        canvas.drawRenderNode(gpuNode);
        canvas.restoreToCount(save);

        tintPaint.setColor(tintColor);
        canvas.drawRoundRect(dstRect, corner, corner, tintPaint);
        if (borderColor != 0) {
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(Math.max(1.2f, Ui.dp(source.getContext(), 1.2f)));
            borderPaint.setColor(borderColor);
            float inset = borderPaint.getStrokeWidth() / 2f;
            canvas.drawRoundRect(new RectF(left + inset, top + inset,
                    right - inset, bottom - inset), Math.max(0f, corner - inset),
                    Math.max(0f, corner - inset), borderPaint);
        }
    }

    private void drawBitmapRegion(Canvas canvas, View target, float left, float top,
                                  float right, float bottom, float corner) {
        source.getLocationOnScreen(sourceLocation);
        target.getLocationOnScreen(targetLocation);
        float offsetX = targetLocation[0] - sourceLocation[0];
        float offsetY = targetLocation[1] - sourceLocation[1];

        float scaleX = blurBitmap.getWidth() / (float) source.getWidth();
        float scaleY = blurBitmap.getHeight() / (float) source.getHeight();

        int sx = Math.round((offsetX + left) * scaleX);
        int sy = Math.round((offsetY + top) * scaleY);
        int sw = Math.max(1, Math.round((right - left) * scaleX));
        int sh = Math.max(1, Math.round((bottom - top) * scaleY));
        sx = Math.max(0, Math.min(sx, blurBitmap.getWidth() - 1));
        sy = Math.max(0, Math.min(sy, blurBitmap.getHeight() - 1));
        sw = Math.min(sw, blurBitmap.getWidth() - sx);
        sh = Math.min(sh, blurBitmap.getHeight() - sy);
        srcRect.set(sx, sy, sx + sw, sy + sh);

        float dstScaleX = (right - left) / (float) srcRect.width();
        float dstScaleY = (bottom - top) / (float) srcRect.height();
        shaderMatrix.reset();
        shaderMatrix.setScale(dstScaleX, dstScaleY);
        shaderMatrix.postTranslate(left - srcRect.left * dstScaleX,
                top - srcRect.top * dstScaleY);
        blurShader.setLocalMatrix(shaderMatrix);
        bitmapPaint.setShader(blurShader);
        canvas.drawRoundRect(dstRect, corner, corner, bitmapPaint);
        bitmapPaint.setShader(null);
    }

    void destroy() {
        destroyed = true;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        handler.removeCallbacks(updateRunnable);
        source.removeCallbacks(updateRunnable);
        clients.clear();
        if (blurBitmap != null) {
            blurBitmap.recycle();
            blurBitmap = null;
        }
        blurShader = null;
    }
}
