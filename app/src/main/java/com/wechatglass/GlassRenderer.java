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
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
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

    void setHiddenViews(View... views) {
        hiddenViews = views == null ? new View[0] : views;
    }

    void addClient(View view) {
        if (view != null && !clients.contains(view)) {
            clients.add(view);
        }
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
        if (canvas == null || target == null || right <= left || bottom <= top) {
            return;
        }

        dstRect.set(left, top, right, bottom);
        float corner = Math.max(0f, Math.min(radius, Math.min(right - left, bottom - top) / 2f));

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
