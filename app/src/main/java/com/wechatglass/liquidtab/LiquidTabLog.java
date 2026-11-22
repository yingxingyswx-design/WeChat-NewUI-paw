package com.wechatglass.liquidtab;

import android.util.Log;

/** Logging shim for the ported navigation bar. */
final class LiquidTabLog {

    private static final String TAG = "WeChatGlassTab";

    private LiquidTabLog() {
    }

    static void log(int level, String message) {
        Log.println(level, TAG, message);
    }

    static void logErr(String message, Throwable throwable) {
        Log.e(TAG, message, throwable);
    }
}
