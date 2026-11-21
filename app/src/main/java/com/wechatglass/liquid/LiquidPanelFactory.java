package com.wechatglass.liquid;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

public final class LiquidPanelFactory {

    private LiquidPanelFactory() {
    }

    public static View create(Context ctx, ViewGroup backdrop, float density, boolean night) {
        try {
            return new LiquidGlassPanel(ctx, backdrop, density, night);
        } catch (Throwable t) {
            LiquidGlassModule.logErr("panel create failed", t);
            return null;
        }
    }




    public static View createRounded(Context ctx, ViewGroup backdrop, float density,
                                     boolean night, float radiusPx) {
        View v = create(ctx, backdrop, density, night);
        if (v instanceof LiquidGlassPanel) {
            ((LiquidGlassPanel) v).setCornerRadius(radiusPx);
        }
        return v;
    }

    /**
     * Liquid glass as a background drawable, for surfaces that must not gain a
     * child view (WeChat's footer panels: adding a child would change how the
     * footer measures itself).
     *
     * @param backdrop the container the wallpaper/chat content lives in; the
     *                 panel samples this at its own rectangle
     */
    public static android.graphics.drawable.Drawable createPanelGlass(
            View target, ViewGroup backdrop, boolean night,
            float topRadius, float bottomRadius) {
        if (target == null) {
            return null;
        }
        try {
            float density = target.getResources().getDisplayMetrics().density;
            return new PanelGlassDrawable(target, backdrop, density, night,
                    topRadius, bottomRadius);
        } catch (Throwable t) {
            LiquidGlassModule.logErr("panel glass create failed", t);
            return null;
        }
    }

    public static boolean isPanelGlass(android.graphics.drawable.Drawable d) {
        return d instanceof PanelGlassDrawable;
    }

    public static void setPanelRadii(android.graphics.drawable.Drawable d,
                                     float topRadius, float bottomRadius) {
        if (d instanceof PanelGlassDrawable) {
            ((PanelGlassDrawable) d).setRadii(topRadius, bottomRadius);
        }
    }

    public static void setPanelTheme(android.graphics.drawable.Drawable d, boolean night) {
        if (d instanceof PanelGlassDrawable) {
            ((PanelGlassDrawable) d).setTheme(night);
        }
    }

    public static void setWallpaper(android.graphics.Bitmap bmp) {
        Backdrop.sWallpaper = bmp;
    }

    public static void setWallpaperEnabled(ViewGroup pager, boolean on) {
        Backdrop.setWallpaperEnabled(pager, on);
    }

    public static void enableCaptureWallpaper(boolean on) {
        Backdrop.captureWallpaper = on;
    }

    public static void forceRecapture(View panel) {
        if (panel instanceof LiquidGlassPanel) {
            ((LiquidGlassPanel) panel).forceRecapture();
        }
    }

    public static void setTheme(View panel, boolean night) {
        if (panel instanceof LiquidGlassPanel) {
            ((LiquidGlassPanel) panel).setTheme(night);
        }
    }
}