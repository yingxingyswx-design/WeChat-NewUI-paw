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
    static final String ACTION_QUERY = "com.wechatglass.QUERY";
    static final String ACTION_STATE = "com.wechatglass.STATE";
    static final String PREFS = "wechatglass";
    static final String KEY_GLASS = "glass";
    static final String KEY_NATIVE_LIST = "native_list";
    static final String KEY_COLOR_MONET = "color_monet";
    static final String KEY_COLOR_SEED = "color_seed";
    static final String KEY_MESSAGE_TIME = "message_time";
    static final int DEFAULT_SEED = 0xFF07C160;

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);
    private static volatile Runnable onChange;

    private HookConfig() {
    }

    static void setOnChange(Runnable listener) {
        onChange = listener;
    }

    static void installReceiver(Context context) {
        if (context == null || !REGISTERED.compareAndSet(false, true)) {
            return;
        }
        try {
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (intent == null) {
                        return;
                    }
                    if (ACTION_QUERY.equals(intent.getAction())) {
                        Intent reply = new Intent(ACTION_STATE);
                        reply.setPackage("io.github.yingxingyswx_design.wechatglass");
                        reply.putExtra(KEY_GLASS, ctx.getSharedPreferences(PREFS, 0)
                                .getBoolean(KEY_GLASS, true));
                        reply.putExtra(KEY_NATIVE_LIST, ctx.getSharedPreferences(PREFS, 0)
                                .getBoolean(KEY_NATIVE_LIST, true));
                        reply.putExtra(KEY_COLOR_MONET, ctx.getSharedPreferences(PREFS, 0)
                                .getBoolean(KEY_COLOR_MONET, true));
                        reply.putExtra(KEY_COLOR_SEED, ctx.getSharedPreferences(PREFS, 0)
                                .getInt(KEY_COLOR_SEED, DEFAULT_SEED));
                        reply.putExtra(KEY_MESSAGE_TIME, messageTimeEnabled(ctx));
                        ctx.sendBroadcast(reply);
                        return;
                    }
                    if (!ACTION.equals(intent.getAction())) {
                        return;
                    }
                    boolean glass = intent.getBooleanExtra(KEY_GLASS, true);
                    boolean nativeList = intent.getBooleanExtra(KEY_NATIVE_LIST, false);
                    boolean monet = intent.getBooleanExtra(KEY_COLOR_MONET, true);
                    int seed = intent.getIntExtra(KEY_COLOR_SEED, DEFAULT_SEED);
                    boolean messageTime = intent.getBooleanExtra(KEY_MESSAGE_TIME, false);
                    ctx.getSharedPreferences(PREFS, 0).edit()
                            .putBoolean(KEY_GLASS, glass)
                            .putBoolean(KEY_NATIVE_LIST, nativeList)
                            .putBoolean(KEY_COLOR_MONET, monet)
                            .putInt(KEY_COLOR_SEED, seed)
                            .putBoolean(KEY_MESSAGE_TIME, messageTime)
                            .apply();
                    com.wechatglass.m3.M3Theme.refresh();
                    android.util.Log.i("WeChatGlass", "config broadcast glass=" + glass
                            + " nativeList=" + nativeList + " monet=" + monet
                            + " seed=#" + Integer.toHexString(seed));
                    Runnable listener = onChange;
                    if (listener != null) {
                        listener.run();
                    }
                }
            };
            IntentFilter filter = new IntentFilter(ACTION);
            filter.addAction(ACTION_QUERY);
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

    static boolean messageTimeEnabled(Context context) {
        try {
            de.robv.android.xposed.XSharedPreferences module =
                    new de.robv.android.xposed.XSharedPreferences(
                            "io.github.yingxingyswx_design.wechatglass", "settings");
            module.reload();
            if (module.contains(KEY_MESSAGE_TIME)) {
                return module.getBoolean(KEY_MESSAGE_TIME, false);
            }
        } catch (Throwable ignored) {
        }
        return context.getSharedPreferences(PREFS, 0)
                .getBoolean(KEY_MESSAGE_TIME, false);
    }

    static boolean nativeList(Context context) {
        return context == null || context.getSharedPreferences(PREFS, 0)
                .getBoolean(KEY_NATIVE_LIST, true);
    }
}
