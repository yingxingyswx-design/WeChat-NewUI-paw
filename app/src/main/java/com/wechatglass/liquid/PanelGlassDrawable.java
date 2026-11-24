package com.wechatglass.liquid;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

/**
 * Liquid glass for WeChat's footer panels (笑脸/表情 and "+"), drawn as the
 * panel's own background.
 *
 * <p>The panel is <em>not</em> an overlay: WeChat puts it in the footer, below
 * the message list, so no chat content sits behind it. What is behind it is
 * {@code ChattingContent}'s chat wallpaper {@code ImageView} — the two are
 * siblings in {@code ChattingScrollLayout}, and the wallpaper covers the whole
 * chat area — so sampling that container at the panel's rectangle yields the
 * wallpaper exactly under the panel. It is the same backdrop the working pills
 * use ({@code ensureLiquidPills} samples the same container), which is why this
 * can be a drawable instead of an extra View: nothing is added to WeChat's
 * footer, so none of its height/measure math is disturbed.
 *
 * <p>The effect stack matches {@link LiquidGlassPanel}, so the panel reads as
 * the same material as the top islands: saturation, a small blur, and the AGSL
 * rounded-rect lens on the rim (Android 13+). One deliberate difference: a
 * sheet this large needs a wash to keep the emoji grid legible, which the
 * pill-sized surfaces do not.
 */
public final class PanelGlassDrawable extends Drawable {

    /**
     * Blur radius, dp — LiquidGlassPanel's value, kept identical on purpose so
     * the panel is the same material as the pills.
     */
    private static final float BLUR_DP = 18f;
    /** KernelSU's vibrancy(): colorControls(saturation = 1.5). */
    private static final float SATURATION = 1.5f;
    /** Rim refraction band, dp — LiquidGlassPanel's value. */
    private static final float REFRACTION_DP = 24f;

    private static final String LENS_SHADER = ""
            + "uniform shader content;\n"
            + "uniform float2 size;\n"
            + "uniform float2 offset;\n"
            + "uniform float4 cornerRadii;\n"
            + "uniform float refractionHeight;\n"
            + "uniform float refractionAmount;\n"
            + "uniform float depthEffect;\n"
            + LiquidGlassPanel.SDF_SOURCE
            + "float circleMap(float x) { return 1.0 - sqrt(1.0 - x * x); }\n"
            + "half4 main(float2 coord) {\n"
            + "    float2 halfSize = size * 0.5;\n"
            + "    float2 centeredCoord = (coord + offset) - halfSize;\n"
            + "    float radius = radiusAt(coord, cornerRadii);\n"
            + "    float sd = sdRoundedRect(centeredCoord, halfSize, radius);\n"
            + "    if (-sd >= refractionHeight) { return content.eval(coord); }\n"
            + "    sd = min(sd, 0.0);\n"
            + "    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;\n"
            + "    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));\n"
            + "    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize,"
            + "            gradRadius) + depthEffect * normalize(centeredCoord));\n"
            + "    return content.eval(coord + d * grad);\n"
            + "}\n";

    private final View target;
    private final ViewGroup backdrop;
    private final float density;
    private final int pad;

    private final RenderNode node = new RenderNode("wxPanelGlass");
    private RenderEffect chain;
    private RuntimeShader lens;
    private int chainW;
    private int chainH;

    private final int[] capturedAt = new int[2];
    private boolean captured;
    private int capturedW;
    private int capturedH;

    /** Cleared on the first failure so the flat fallback takes over. */
    private boolean supported = true;

    private boolean night;
    private float topRadius;
    private float bottomRadius;

    private final Path clip = new Path();
    private final Path rimPath = new Path();
    private final RectF rect = new RectF();
    private final float[] radii = new float[8];
    private final Paint wash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flat = new Paint(Paint.ANTI_ALIAS_FLAG);

    PanelGlassDrawable(View target, ViewGroup backdrop, float density, boolean night,
                       float topRadius, float bottomRadius) {
        this.target = target;
        this.backdrop = backdrop;
        this.density = density;
        // The lens samples outside its own bounds, so the capture is grown by
        // the refraction amount on every side.
        this.pad = Math.round(REFRACTION_DP * density);
        this.night = night;
        this.topRadius = topRadius;
        this.bottomRadius = bottomRadius;
        if (Build.VERSION.SDK_INT >= LiquidGlassPanel.LENS_MIN_SDK) {
            try {
                lens = new RuntimeShader(LENS_SHADER);
            } catch (Throwable t) {
                lens = null;
                LiquidGlassModule.logErr("panel lens shader rejected", t);
            }
        }
        paintSpec();
        buildPaths(0, 0);
    }

    private void paintSpec() {
        // LiquidGlassPanel's surface wash, verbatim: KernelSU's
        // containerColor = surfaceContainer.copy(0.4f). This is the layer that
        // makes the surface read as glass; our LiquidGlassPanel had it edited
        // down to 0x00 ("wash off for test"), which is why a panel with no
        // wash of its own looked like plain transparency.
        wash.setStyle(Paint.Style.FILL);
        applyWashColor();
        wash.setShader(null);
        // iosIndicatorSpecular: BloomStroke(white @ 0.12), width 1.dp, alpha 0.75.
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, density));
        rim.setColor(night ? 0x1FFFFFFF : 0x2EFFFFFF);
        flat.setStyle(Paint.Style.FILL);
        flat.setColor(night ? 0xFF2C2C2C : 0xFFFFFFFF);
    }


    void setTheme(boolean isNight) {
        if (night == isNight) {
            return;
        }
        night = isNight;
        paintSpec();
        captured = false;
        invalidateSelf();
    }

    boolean matchesTheme(boolean isNight) {
        return night == isNight;
    }

    /** Uptime of the last draw pass of {@code d}, or 0 when it is not panel glass. */
    public static long lastDrawAt(android.graphics.drawable.Drawable d) {
        return d instanceof PanelGlassDrawable ? ((PanelGlassDrawable) d).lastDrawAt : 0L;
    }

    /** Discards the cached capture so the next draw re-records it. */
    void invalidateCapture() {
        captured = false;
        invalidateSelf();
    }

    void setRadii(float top, float bottom) {
        if (topRadius == top && bottomRadius == bottom) {
            return;
        }
        topRadius = top;
        bottomRadius = bottom;
        Rect b = getBounds();
        buildPaths(b.width(), b.height());
        captured = false;
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        buildPaths(bounds.width(), bounds.height());
        captured = false;
    }

    /** Clip and rim live in bounds space; rebuilt only when those change. */
    private void buildPaths(int w, int h) {
        Rect b = getBounds();
        radii[0] = topRadius;
        radii[1] = topRadius;
        radii[2] = topRadius;
        radii[3] = topRadius;
        radii[4] = bottomRadius;
        radii[5] = bottomRadius;
        radii[6] = bottomRadius;
        radii[7] = bottomRadius;
        clip.reset();
        rect.set(b.left, b.top, b.left + Math.max(0, w), b.top + Math.max(0, h));
        clip.addRoundRect(rect, radii, Path.Direction.CW);

        float half = rim.getStrokeWidth() * 0.5f;
        float[] inset = new float[8];
        for (int i = 0; i < 8; i++) {
            inset[i] = Math.max(0f, radii[i] - half);
        }
        rimPath.reset();
        rect.set(b.left + half, b.top + half, b.right - half, b.bottom - half);
        rimPath.addRoundRect(rect, inset, Path.Direction.CW);
    }

    @Override
    public void draw(Canvas canvas) {
        lastDrawAt = android.os.SystemClock.uptimeMillis();
        Rect b = getBounds();
        int w = b.width();
        int h = b.height();
        if (w <= 0 || h <= 0) {
            return;
        }
        // Debug switches, live from `adb shell setprop`, so the capture can be
        // inspected without rebuilding the module:
        //   debug.wxglass.panel.raw=1  draw the unblurred capture, no wash/rim
        //   debug.wxglass.panel.log=1  log which path the panel took, and why
        boolean raw = prop("debug.wxglass.panel.raw");
        // Identity matters here: WeChat rebuilds panels between opens, so a
        // drawable that never logs its own object id cannot be matched against
        // the view that is actually on screen.
        if (prop("debug.wxglass.panel.log")) {
            long tick = android.os.SystemClock.uptimeMillis();
            if (tick - lastObjectLog > 500L) {
                lastObjectLog = tick;
                Log.i("WeChatGlass", "panel draw obj=" + Integer.toHexString(
                        System.identityHashCode(this))
                        + " hw=" + canvas.isHardwareAccelerated()
                        + " bounds=" + b.left + "," + b.top + "," + b.right + "," + b.bottom
                        + " target=" + (target == null ? "null" : Integer.toHexString(
                                System.identityHashCode(target)))
                        + " supported=" + supported);
            }
        }
        // Always-on, rate-limited: proves whether this drawable is painted at
        // all while the panel is supposedly open.
        long tick = android.os.SystemClock.uptimeMillis();
        if (tick - lastDrawLog > 900L) {
            lastDrawLog = tick;
            LiquidGlassModule.logPanelDraw(true, supported, backdrop,
                    Build.VERSION.SDK_INT, canvas.isHardwareAccelerated(), w, h, raw);
        }
        boolean log = prop("debug.wxglass.panel.log");
        applyTuning();
        if (prop("debug.wxglass.panel.probe")) {
            // Where is this drawable actually painted? Solid magenta answers it.
            Paint probe = new Paint(Paint.ANTI_ALIAS_FLAG);
            probe.setColor(0xCCFF00FF);
            canvas.drawPath(clip, probe);
            return;
        }
        boolean drawn = false;
        if (supported && backdrop != null
                && Build.VERSION.SDK_INT >= LiquidGlassPanel.MIN_SDK
                && canvas.isHardwareAccelerated()) {
            try {
                drawGlass(canvas, b, w, h, raw);
                drawn = true;
            } catch (Throwable t) {
                supported = false;
                LiquidGlassModule.logErr("panel glass failed, flat fallback", t);
            }
        }
        if (log && drawsLogged < 12) {
            drawsLogged++;
            LiquidGlassModule.logPanelDraw(drawn, supported, backdrop,
                    Build.VERSION.SDK_INT, canvas.isHardwareAccelerated(),
                    w, h, raw);
        }
        if (!drawn) {
            canvas.drawPath(clip, flat);
        }
        if (!raw) {
            canvas.drawPath(clip, wash);
            canvas.drawPath(rimPath, rim);
        }
    }

    private int drawsLogged;
    private long lastTune;
    private float tuneBlur = BLUR_DP;
    // No wash: it read as a white film over the chat wallpaper. The panel is
    // pure glass — blur, saturation and rim only. debug.wxglass.panel.wash can
    // still raise it on device without a rebuild.
    private int tuneWash = 0;

    /**
     * Live tuning from `adb shell setprop`, so the glass can be dialled in on
     * the phone instead of through a rebuild per value:
     *   debug.wxglass.panel.blur  blur radius in dp (default 18)
     *   debug.wxglass.panel.wash  wash alpha 0-255 (default 102 = 40%)
     */
    private void applyTuning() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastTune < 700L) {
            return;
        }
        lastTune = now;
        float blur = propFloat("debug.wxglass.panel.blur", tuneBlur);
        int wash = (int) propFloat("debug.wxglass.panel.wash", tuneWash);
        if (wash < 0) { wash = 0; }
        if (wash > 255) { wash = 255; }
        if (blur != tuneBlur) {
            tuneBlur = blur;
            chain = null;
            invalidateSelf();
        }
        if (wash != tuneWash) {
            tuneWash = wash;
            applyWashColor();
            invalidateSelf();
        }
    }

    private void applyWashColor() {
        int rgb = night ? 0x2C2C2E : 0xF2F2F7;
        wash.setColor((tuneWash << 24) | rgb);
    }

    private static float propFloat(String key, float fallback) {
        try {
            Object value = Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class, String.class)
                    .invoke(null, key, "");
            if (value instanceof String && !((String) value).isEmpty()) {
                return Float.parseFloat((String) value);
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }
    private long lastDrawLog;
    private long lastObjectLog;
    /** Uptime of the last draw() pass; read by the owner to detect a frozen panel. */
    private volatile long lastDrawAt;

    private static boolean prop(String key) {
        try {
            Object value = Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class, String.class)
                    .invoke(null, key, "");
            return value instanceof String && "1".equals(value);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Records the wallpaper right under the panel.
     *
     * <p>The panel's own screen position cannot be used for this. WeChat slides
     * the footer by translating the canvas of its parent, so
     * {@code getLocationOnScreen} keeps reporting where the footer sits in the
     * layout — measured here at y=2381 of a 2400px screen while the panel was
     * plainly painted at y≈1130. A capture taken at the reported position
     * samples the page fill instead of the wallpaper, which is exactly the flat
     * block that appeared on every first open.
     *
     * <p>What does hold: an open panel is a bottom sheet. Its bottom edge is the
     * bottom of the chat page, and it is centred horizontally, so the region to
     * sample is the backdrop's bottom {@code h} pixels. Nothing here depends on
     * where the panel believes it is, which also keeps the capture valid for the
     * whole slide-in animation.
     */
    private void drawGlass(Canvas canvas, Rect b, int w, int h, boolean raw) {
        int[] bl = new int[2];
        backdrop.getLocationOnScreen(bl);
        int bw = backdrop.getWidth();
        int bh = backdrop.getHeight();
        int capLeft = bl[0] + Math.max(0, (bw - w) / 2);
        int capTop = bl[1] + Math.max(0, bh - h);

        int nw = w + pad * 2;
        int nh = h + pad * 2;
        boolean stale = !captured || capturedW != w || capturedH != h
                || capturedAt[0] != capLeft || capturedAt[1] != capTop;
        if (stale) {
            node.setPosition(0, 0, nw, nh);
            RecordingCanvas rc = node.beginRecording(nw, nh);
            boolean wallpaper = Backdrop.captureWallpaper;
            try {
                // The panel sits on WeChat's own chat wallpaper, not the
                // launcher one; letting the launcher bitmap in would show a
                // different photo than the page above it.
                Backdrop.captureWallpaper = false;
                Backdrop.draw(rc, backdrop, night, capLeft - pad, capTop - pad, nw, nh);
            } finally {
                Backdrop.captureWallpaper = wallpaper;
                node.endRecording();
            }
            capturedAt[0] = capLeft;
            capturedAt[1] = capTop;
            capturedW = w;
            capturedH = h;
            captured = true;
            LiquidGlassModule.logPanelCapture(nw, nh, capLeft, capTop);
        }

        if (chain == null || chainW != w || chainH != h) {
            float blur = tuneBlur * density;
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(SATURATION);
            RenderEffect frost = RenderEffect.createBlurEffect(blur, blur,
                    RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(cm)),
                    Shader.TileMode.CLAMP);
            if (lens != null) {
                lens.setFloatUniform("size", w, h);
                lens.setFloatUniform("offset", -pad, -pad);
                lens.setFloatUniform("cornerRadii", topRadius, topRadius,
                        bottomRadius, bottomRadius);
                lens.setFloatUniform("refractionHeight", REFRACTION_DP * density);
                lens.setFloatUniform("refractionAmount", -REFRACTION_DP * density);
                lens.setFloatUniform("depthEffect", 0f);
                chain = RenderEffect.createChainEffect(
                        RenderEffect.createRuntimeShaderEffect(lens, "content"), frost);
            } else {
                chain = frost;
            }
            chainW = w;
            chainH = h;
        }
        node.setRenderEffect(raw ? null : chain);

        canvas.save();
        canvas.clipPath(clip);
        canvas.translate(b.left - pad, b.top - pad);
        canvas.drawRenderNode(node);
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        // Alpha is carried by the wash and the capture, not by fading the node.
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
