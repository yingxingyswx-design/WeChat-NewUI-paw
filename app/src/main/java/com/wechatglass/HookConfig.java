package com.wechatglass;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Settings live in WeChat's own SharedPreferences, written by the hook when it
 * receives a broadcast from the module's settings UI. Cross-app file access is
 * blocked on modern Android, so the broadcast is the transport.
 */
final class HookConfig {

    static final String ACTION = "com.wechatglass.CONFIG";
    static final String PREFS = "wechatglass";
    static final String KEY_GLASS = "glass";

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private HookConfig() {
    }

    static void installReceiver(Context context) {
        if (context == null || !REGISTERED.compareAndSet(false, true)) {
            return;
        }
        try {
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (intent == null || !KEY_GLASS.equals(intent.getAction())
                            && !ACTION.equals(intent.getAction())) {
                        return;
                    }
                    boolean glass = intent.getBooleanExtra(KEY_GLASS, true);
                    ctx.getSharedPreferences(PREFS, 0).edit()
                            .putBoolean(KEY_GLASS, glass).apply();
                    android.util.Log.i("WeChatGlass", "config broadcast glass=" + glass);
                }
            };
            IntentFilter filter = new IntentFilter(ACTION);
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
            android.util.Log.i("WeChatGlass", "config receiver registered");
        } catch (Throwable throwable) {
            android.util.Log.e("WeChatGlass", "config receiver failed", throwable);
        }
    }

    static boolean glassEnabled(Context context) {
        if (context == null) {
            return false;
        }
        boolean enabled = context.getSharedPreferences(PREFS, 0)
                .getBoolean(KEY_GLASS, true);
        android.util.Log.i("WeChatGlass", "config glass=" + enabled);
        return enabled;
    }
}
