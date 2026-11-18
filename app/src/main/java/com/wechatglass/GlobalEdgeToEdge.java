package com.wechatglass;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;

/**
 * Global, window-level edge-to-edge for every WeChat Activity.
 *
 * The app-level views (ActionBarOverlayLayout / ContentFrameLayout) still
 * receive status bar insets and inset their children even after
 * setDecorFitsSystemWindows(false). To make the layout genuinely full-bleed we
 * zero only the status-bar inset on the decor view and let navigation/IME
 * insets through unchanged.
 */
final class GlobalEdgeToEdge {

    private GlobalEdgeToEdge() {
    }

    static void force(Activity activity) {
        if (activity == null) {
            return;
        }
        // Hard safety gate: never touch payment, transfer, red-packet or other
        // plugin Activities. Only the two UI shells this module was designed
        // for are allowed through.
        String name = activity.getClass().getName();
        if (!"com.tencent.mm.ui.LauncherUI".equals(name)
                && !"com.tencent.mm.ui.chatting.ChattingUI".equals(name)) {
            return;
        }
        try {
            Window window = activity.getWindow();
            if (window == null) {
                return;
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setDecorFitsSystemWindows(false);
            if (Build.VERSION.SDK_INT >= 30) {
                window.setStatusBarContrastEnforced(false);
            }
            View decor = window.getDecorView();
            android.util.Log.i("WeChatGlassEdge", "forceEtE " + activity.getClass().getName());
            decor.setFitsSystemWindows(false);
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            if (Build.VERSION.SDK_INT >= 30) {
                decor.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                    @Override
                    public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                        WindowInsets.Builder builder = new WindowInsets.Builder(insets);
                        builder.setInsets(WindowInsets.Type.statusBars(), Insets.NONE);
                        return builder.build();
                    }
                });
                decor.requestApplyInsets();
            }
        } catch (Throwable ignored) {
        }
    }
}
