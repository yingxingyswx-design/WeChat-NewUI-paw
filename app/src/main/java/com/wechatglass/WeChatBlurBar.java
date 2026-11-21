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
        return create(context, decor, widthPx, heightPx, tint, 0);
    }

    static View create(Context context, ViewGroup decor, int widthPx, int heightPx, int tint, int radiusPx) {
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
            Drawable bg = decor.getBackground();
            if (bg != null) {
                f.getMethod("j", Drawable.class).invoke(ctl, bg);
            }
            f.getMethod("h", float.class).invoke(ctl, 15f);
            f.getMethod("k", boolean.class).invoke(ctl, true);
            f.getMethod("e", boolean.class).invoke(ctl, false);
            f.getMethod("i", boolean.class).invoke(ctl, true);

            float density = context.getResources().getDisplayMetrics().density;
            bvCls.getField("f").setFloat(bv, radiusPx); // 0 = no internal clip, wrapper clips
            bvCls.getField("e").setInt(bv, tint);
            float r = heightPx / 2f;
            bvCls.getField("i").set(bv, new float[]{r, r, r, r, r, r, r, r});
            bvCls.getField("m").setBoolean(bv, true);

            View view = (View) bv;
            view.setLayoutParams(new FrameLayout.LayoutParams(widthPx, heightPx));
            final Object ctlRef = ctl;
            final Class<?> fCls = f;
            final View fv = view;
            view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override public void onLayoutChange(View v, int l, int t, int r2, int b2,
                        int ol, int ot, int or, int ob) {
                    try {
                        fCls.getMethod("g").invoke(ctlRef); // g() after real size
                        Log.i(TAG, "WeChatBlurBar g() after layout " + (r2 - l) + "x" + (b2 - t));
                        java.lang.reflect.Field cf = ctlRef.getClass().getField("e");
                        Object cv = cf.get(ctlRef);
                        if (cv == null && (r2 - l) > 0 && (b2 - t) > 0) {
                            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                                    r2 - l, b2 - t, android.graphics.Bitmap.Config.ARGB_8888);
                            cf.set(ctlRef, new android.graphics.Canvas(bmp));
                            try {
                                ctlRef.getClass().getField("f").set(ctlRef, bmp);
                            } catch (Throwable ignored) { }
                            Log.i(TAG, "WeChatBlurBar canvas forced");
                        }
                    } catch (Throwable e) {
                        Log.e(TAG, "g() failed", e);
                    }
                }
            });
            for (int i = 1; i <= 10; i++) {
                fv.postDelayed(new Runnable() {
                    @Override public void run() {
                        try { fCls.getMethod("g").invoke(ctlRef); } catch (Throwable ignored) { }
                    }
                }, 100L * i);
            }
            Log.i(TAG, "WeChatBlurBar created h=" + heightPx + " r=" + r);
            return view;
        } catch (Throwable t) {
            Log.e(TAG, "WeChatBlurBar failed", t);
            return null;
        }
    }
}
