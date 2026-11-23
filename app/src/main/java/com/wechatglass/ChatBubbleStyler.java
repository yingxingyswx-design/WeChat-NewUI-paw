package com.wechatglass;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.wechatglass.m3.M3ColorScheme;
import com.wechatglass.m3.M3Drawable;
import com.wechatglass.m3.M3Shape;
import com.wechatglass.m3.M3Theme;
import com.wechatglass.m3.M3Type;

import java.util.WeakHashMap;

/**
 * Restyles the text bubble inside WeChat's own chat item views.
 *
 * <p>Google Chat's text bubble is the reference: 20dp corners, 12x8dp padding,
 * incoming = neutral95, outgoing = a light primary tint, body-large text. The
 * item view, click listeners and message data are untouched; only the text
 * view's background, padding and type are changed.
 */
final class ChatBubbleStyler {

    private static final WeakHashMap<View, Boolean> DONE = new WeakHashMap<>();

    private ChatBubbleStyler() {
    }

    static void styleVisible(ViewGroup list, Context context) {
        if (list == null || context == null) {
            return;
        }
        for (int i = 0; i < list.getChildCount(); i++) {
            View item = list.getChildAt(i);
            if (item == null || DONE.containsKey(item)) {
                continue;
            }
            try {
                if (styleItem(item, context)) {
                    DONE.put(item, Boolean.TRUE);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean styleItem(View item, Context context) {
        TextView best = null;
        int bestArea = 0;
        best = findText(item, best, bestArea);
        if (best == null) {
            return false;
        }
        CharSequence text = best.getText();
        if (text == null || text.length() < 1 || looksLikeChrome(text.toString())) {
            return false;
        }
        int[] loc = new int[2];
        best.getLocationOnScreen(loc);
        int screen = context.getResources().getDisplayMetrics().widthPixels;
        boolean outgoing = loc[0] + best.getWidth() / 2 > screen / 2;
        M3ColorScheme scheme = M3Theme.colors(context);
        int color = outgoing
                ? M3ColorScheme.mix(scheme.surface, scheme.primary, 0.18f)
                : scheme.surfaceContainerHighest;

        // If the text sits in a tight bubble container, clear its old 9-patch;
        // the text view itself becomes the bubble.
        View parent = best.getParent() instanceof View ? (View) best.getParent() : null;
        if (parent != null
                && parent.getWidth() <= best.getWidth() + M3Theme.dp(context, 40f)
                && parent.getHeight() <= best.getHeight() + M3Theme.dp(context, 32f)) {
            parent.setBackground(null);
        }
        best.setBackground(M3Drawable.fill(context, color, M3Shape.LARGE_INCREASED));
        best.setPadding(M3Theme.dp(context, 12f), M3Theme.dp(context, 8f),
                M3Theme.dp(context, 12f), M3Theme.dp(context, 8f));
        best.setTextColor(scheme.onSurface);
        M3Type.apply(best, M3Type.BODY_LARGE);
        return true;
    }

    private static TextView findText(View view, TextView best, int bestArea) {
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            CharSequence text = tv.getText();
            if (text != null && text.length() > 0
                    && tv.getVisibility() == View.VISIBLE
                    && tv.getWidth() > 0 && tv.getHeight() > 0) {
                int area = tv.getWidth() * tv.getHeight();
                if (area > bestArea) {
                    best = tv;
                    bestArea = area;
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                best = findText(group.getChildAt(i), best, bestArea);
                if (best != null) {
                    bestArea = best.getWidth() * best.getHeight();
                }
            }
        }
        return best;
    }

    private static boolean looksLikeChrome(String text) {
        if (text.length() <= 2) {
            return true;
        }
        if (text.matches("\\d{1,2}:\\d{2}")) {
            return true;
        }
        return "昨天".equals(text) || "昨日".equals(text) || "星期".equals(text)
                || text.contains("已读") || text.contains("送信");
    }
}
