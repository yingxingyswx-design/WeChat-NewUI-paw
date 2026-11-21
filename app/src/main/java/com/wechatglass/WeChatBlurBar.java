package com.wechatglass;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * Wraps WeChat's own com.tencent.mm.ui.blur.BlurView so the bar can use the
 * host's blur pipeline (it samples the window itself and clips to a Path).
 */
final class WeChatBlurBar {

    private static final String TAG = "WeChatGlass";

    private WeChatBlurBar() {
    }

    static View create(Context context, ViewGroup decor, int widthPx, int heightPx, int tint) {
        try {
            ClassLoader cl = context.getClassLoader();
            Class<?> bvCls = Class.forName("com.tencent.mm.ui.blur.BlurView", false, cl);
            Object bv = bvCls.getConstructor(Context.class).newInstance(context);
            bvCls.getMethod("c", ViewGroup.class).invoke(bv, decor);

            Object ctl = bvCls.getMethod("getBlurController").invoke(bv);
            Class<?> f = Class.forName("ll5.f", false, cl);
            Object algo = Class.forName("ll5.k", false, cl)
                    .getConstructor(Context.class).newInstance(context);
            f.getMethod("b", Class.forName("ll5.e", false, cl)).invoke(ctl, algo);
            f.getMethod("j", Drawable.class).invoke(ctl, decor.getBackground());
            f.getMethod("h", float.class).invoke(ctl, 15f);
            f.getMethod("k", boolean.class).invoke(ctl, true);
            f.getMethod("e", boolean.class).invoke(ctl, false);
            f.getMethod("i", boolean.class).invoke(ctl, true);

            float density = context.getResources().getDisplayMetrics().density;
            bvCls.getField("f").setFloat(bv, heightPx / 2f);
            bvCls.getField("e").setInt(bv, tint);
            float r = heightPx / 2f;
            bvCls.getField("i").set(bv, new float[]{r, r, r, r, r, r, r, r});
            bvCls.getField("m").setBoolean(bv, true);

            View view = (View) bv;
            view.setLayoutParams(new FrameLayout.LayoutParams(widthPx, heightPx));
            Log.i(TAG, "WeChatBlurBar created h=" + heightPx + " r=" + r);
            return view;
        } catch (Throwable t) {
            Log.e(TAG, "WeChatBlurBar failed", t);
            return null;
        }
    }
}
