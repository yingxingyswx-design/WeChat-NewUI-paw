package com.wechatglass;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;

/** Small platform-only helpers. No AndroidX dependency. */
final class Ui {

    private Ui() {
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static boolean isNight(Context context) {
        int mode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    static int statusBarHeight(Context context) {
        int id = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id > 0) {
            int value = context.getResources().getDimensionPixelSize(id);
            if (value > 0) {
                return value;
            }
        }
        return dp(context, 24);
    }

    static int statusBarHeight(View view) {
        if (view == null) {
            return 0;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsets insets = view.getRootWindowInsets();
            if (insets != null) {
                int top = insets.getInsets(android.view.WindowInsets.Type.statusBars()).top;
                if (top > 0) {
                    return top;
                }
            }
        }
        return statusBarHeight(view.getContext());
    }

    static void transparent(View view) {
        if (view == null) {
            return;
        }
        view.setBackground(null);
        if (Build.VERSION.SDK_INT >= 21) {
            view.setElevation(0f);
        }
    }

    /**
     * AppCompat's ActionBarContainer keeps its own mBackground/mSplitBackground
     * fields and draws them in draw(), even when setBackground(null) is called.
     */
    static void clearActionBarBackground(View view) {
        transparent(view);
        if (view == null) {
            return;
        }
        Class<?> type = view.getClass();
        while (type != null) {
            clearField(view, type, "mBackground");
            clearField(view, type, "mSplitBackground");
            type = type.getSuperclass();
        }
        view.invalidate();
    }

    private static void clearField(View target, Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, null);
        } catch (Throwable ignored) {
            // Field is absent on this AppCompat version.
        }
    }

    static void setMargins(View view, int left, int top, int right, int bottom) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
        margins.leftMargin = left;
        margins.topMargin = top;
        margins.rightMargin = right;
        margins.bottomMargin = bottom;
        view.setLayoutParams(margins);
    }

    static View findByClass(View root, String className) {
        if (root == null) {
            return null;
        }
        if (className.equals(root.getClass().getName())) {
            return root;
        }
        if (!(root instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findByClass(group.getChildAt(i), className);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    static View findByClassContains(View root, String classPart) {
        if (root == null) {
            return null;
        }
        if (root.getClass().getName().contains(classPart)) {
            return root;
        }
        if (!(root instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findByClassContains(group.getChildAt(i), classPart);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
