package com.wechatglass;

import android.widget.ImageView;

/**
 * Loads WeChat's own avatar drawable for a username.
 *
 * <p>{@code com.tencent.mm.pluginsdk.ui.u.b(imageView, username, alpha, round)}
 * installs WeChat's avatar drawable, which loads asynchronously and falls back
 * to its own placeholder. The ImageView in the list clips that drawable to a
 * rounded square.
 */
final class AvatarLoader {

    private static Class<?> avatarClass;

    private AvatarLoader() {
    }

    static void load(ImageView view, String username) {
        if (view == null || username == null || username.isEmpty()) {
            return;
        }
        try {
            if (avatarClass == null) {
                avatarClass = de.robv.android.xposed.XposedHelpers.findClass(
                        "com.tencent.mm.pluginsdk.ui.u",
                        view.getContext().getClassLoader());
            }
            de.robv.android.xposed.XposedHelpers.callStaticMethod(
                    avatarClass, "b", view, username, 0.1f, false);
        } catch (Throwable throwable) {
            android.util.Log.w("WeChatGlass", "avatar load failed: " + username, throwable);
        }
    }
}
