package com.wechatglass.liquid;

import android.util.Log;

final class LiquidGlassModule {

    private static int captures;

    static void logErr(String msg, Throwable t) {
        Log.e("WeChatGlass", msg, t);
    }

    /** First few panel captures only; the draw path may run every frame. */
    static void logPanelCapture(int w, int h, int x, int y) {
        if (captures < 12) {
            captures++;
            Log.i("WeChatGlass", "panel glass capture " + w + "x" + h
                    + " at " + x + "," + y);
        }
    }

    /** One line per draw while debug.wxglass.panel.log=1; explains the path. */
    static void logPanelDraw(boolean glass, boolean supported,
                             android.view.ViewGroup backdrop, int sdk,
                             boolean hw, int w, int h, boolean raw) {
        Log.i("WeChatGlass", "panel draw glass=" + glass
                + " supported=" + supported
                + " sdk=" + sdk + " hw=" + hw + " raw=" + raw
                + " size=" + w + "x" + h
                + " backdrop=" + (backdrop == null ? "null"
                : backdrop.getClass().getSimpleName() + "@"
                + backdrop.getWidth() + "x" + backdrop.getHeight()
                + " shown=" + backdrop.isShown()));
    }

    /**
     * The panel's layout position against where the canvas actually puts it.
     * They disagree when an ancestor slides its children by translating the
     * canvas instead of by moving the views.
     */
    static void logPanelCanvas(int screenX, int screenY, int left, int top,
                               float translationY, float canvasX, float canvasY) {
        Log.i("WeChatGlass", "panel pos screen=" + screenX + "," + screenY
                + " layout=" + left + "," + top
                + " ty=" + translationY
                + " canvasOrigin=" + (int) canvasX + "," + (int) canvasY);
    }
}
