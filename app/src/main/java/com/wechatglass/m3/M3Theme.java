package com.wechatglass.m3;

import android.content.Context;

/** Cached M3 colour scheme + density helpers for the module's hand-drawn UI. */
public final class M3Theme {

    private static M3ColorScheme sScheme;
    private static int sUiMode = Integer.MIN_VALUE;

    private M3Theme() {
    }

    public static synchronized M3ColorScheme colors(Context context) {
        int mode = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        if (sScheme == null || sUiMode != mode) {
            sScheme = M3ColorScheme.forContext(context);
            sUiMode = mode;
        }
        return sScheme;
    }

    public static synchronized void refresh() {
        sScheme = null;
        sUiMode = Integer.MIN_VALUE;
    }

    public static boolean isDark(Context context) {
        return colors(context).dark;
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static float dpF(Context context, float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }
}
