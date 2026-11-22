package com.wechatglass.liquidtab;

import android.util.Log;

/**
 * Entry point for the ported bottom navigation bar. Called once from the
 * module's HookEntry in the main WeChat process.
 */
public final class LiquidTabHook {

    private LiquidTabHook() {
    }

    public static void install(ClassLoader classLoader) {
        try {
            TabBarBridge.install(HostApp.WECHAT, classLoader);
            Log.i("WeChatGlassTab", "bottom nav bridge installed");
        } catch (Throwable t) {
            Log.e("WeChatGlassTab", "bottom nav bridge failed", t);
        }
    }
}
