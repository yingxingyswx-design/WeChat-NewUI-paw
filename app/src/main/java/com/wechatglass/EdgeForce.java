package com.wechatglass;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowInsets;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Forces WeChat's own EdgeToEdgeWrapperLayout/DrawStatusBarFrameLayout to stop padding/drawing. */
final class EdgeForce {

    private EdgeForce() {
    }

    static void apply(Activity activity) {
        if (activity == null) {
            return;
        }
        try {
            Window window = activity.getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setDecorFitsSystemWindows(false);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                window.setStatusBarContrastEnforced(false);
                window.setNavigationBarContrastEnforced(false);
            }
            View decor = window.getDecorView();
            decor.setFitsSystemWindows(false);
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            applyWrappers(decor);
        } catch (Throwable ignored) {
        }
    }

    private static void applyWrappers(View view) {
        if (view == null) {
            return;
        }
        if ("com.tencent.mm.ui.widget.EdgeToEdgeWrapperLayout"
                .equals(view.getClass().getName())) {
            applyWrapper(view);
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyWrappers(group.getChildAt(i));
            }
        }
    }

    private static void applyWrapper(View wrapper) {
        try {
            Object oldH = getField(wrapper, "h");
            Object oldI = getField(wrapper, "i");
            Object oldM = getField(wrapper, "m");
            Object oldF = getField(wrapper, "f1484n");
            android.util.Log.i("WeChatGlassEdge", "wrapper before h=" + oldH
                    + " i=" + oldI + " m=" + oldM + " f=" + oldF
                    + " pad=" + wrapper.getPaddingTop());

            // Clear state first. Strategy reflection must not block this.
            setField(wrapper, "h", 0);
            setField(wrapper, "i", 0);
            setField(wrapper, "m", 0);
            setField(wrapper, "f1484n", true);

            int bottom = wrapper.getPaddingBottom();
            if (bottom == 0) {
                WindowInsets insets = wrapper.getRootWindowInsets();
                if (insets != null) {
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                    } else {
                        bottom = insets.getSystemWindowInsetBottom();
                    }
                }
            }
            wrapper.setPadding(0, 0, 0, bottom);

            // Resolve the enum through the wrapper's own field type. This avoids
            // ClassNotFoundException from classloader isolation in ChattingUI.
            try {
                Field strategyField = wrapper.getClass().getField("statusBarStrategy");
                Class<?> strategyClass = strategyField.getType();
                Object alwaysHide = strategyClass.getField("d").get(null);
                Method setter = wrapper.getClass()
                        .getMethod("setStatusBarStrategy", strategyClass);
                setter.invoke(wrapper, alwaysHide);
            } catch (Throwable ignored) {
            }

            wrapper.invalidate();
        } catch (Throwable throwable) {
            android.util.Log.e("WeChatGlassEdge",
                    "applyWrapper failed " + wrapper.getClass().getName(), throwable);
        }
    }

    private static Object getField(Object target, String name) {
        Class<?> cls = target.getClass();
        while (cls != null) {
            try {
                Field field = cls.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                cls = cls.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static void setField(Object target, String name, Object value) {
        Class<?> cls = target.getClass();
        while (cls != null) {
            try {
                Field field = cls.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                cls = cls.getSuperclass();
            } catch (Throwable ignored) {
                return;
            }
        }
    }
}
