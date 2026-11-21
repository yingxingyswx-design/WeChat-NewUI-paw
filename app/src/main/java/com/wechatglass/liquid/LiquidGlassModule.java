package com.wechatglass.liquid;

import android.util.Log;

final class LiquidGlassModule {
    static void logErr(String msg, Throwable t) {
        Log.e("WeChatGlass", msg, t);
    }
}