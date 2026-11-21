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