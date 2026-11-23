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


    private static volatile boolean active;
    private static final WeakHashMap<TextView, Boolean> MESSAGES = new WeakHashMap<>();

    static boolean isActive() {
        return active;
    }

    static void detach(android.app.Activity activity) {
        active = false;
        if (activity != null) {
            STARTED.remove(activity);
        }
    }

    private static boolean inChatList(View v) {
        for (int i = 0; i < 10 && v != null; i++) {
            if (v.getClass().getName().contains("ScrollControlRecyclerView")) {
                return true;
            }
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        return false;
    }

    static void mark(final TextView text) {
        if (text == null) {
            return;
        }
        MESSAGES.put(text, Boolean.TRUE);
        text.postDelayed(new Runnable() {
            @Override public void run() {
                try {
                    if (text.getWidth() > 0 && text.getHeight() > 0
                            && inChatList(text)) {
                        View p = text.getParent() instanceof View
                                ? (View) text.getParent() : null;
                        android.util.Log.i("WeChatGlass", "CB marked txt="
                                + shorten(String.valueOf(text.getText()))
                                + " " + text.getWidth() + "x" + text.getHeight()
                                + " parent=" + (p == null ? "null"
                                : p.getClass().getSimpleName() + " "
                                + p.getWidth() + "x" + p.getHeight()));
                        styleX2C(text, text.getContext());
                    }
                } catch (Throwable ignored) {
                }
            }
        }, 180L);
    }

    static void styleMarked(Context context) {
        for (TextView text : MESSAGES.keySet().toArray(new TextView[0])) {
            if (text == null || text.getWidth() <= 0 || text.getHeight() <= 0) {
                continue;
            }
            try {
                styleX2C(text, context);
            } catch (Throwable ignored) {
            }
        }
    }
    private static final WeakHashMap<android.app.Activity, Boolean> STARTED =
            new WeakHashMap<>();

    static void attach(android.app.Activity activity) {
        if (activity == null || STARTED.containsKey(activity)) {
            return;
        }
        STARTED.put(activity, Boolean.TRUE);
        active = true;
        android.util.Log.i("WeChatGlass", "CB attach " + activity.getClass().getName());
        final android.os.Handler handler = new android.os.Handler(
                android.os.Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    View decor = activity.getWindow() == null
                            ? null : activity.getWindow().getDecorView();
                    logLists(decor, 0);
                    View list = decor == null ? null
                            : Ui.findByClassContains(decor, "ScrollControlRecyclerView");
                    if (list == null && decor != null) {
                        list = Ui.findByClassContains(decor, "RecyclerView");
                    }
                    android.util.Log.i("WeChatGlass", "CB tick list="
                            + (list == null ? "null" : list.getClass().getName())
                            + " children=" + (list instanceof ViewGroup
                            ? ((ViewGroup) list).getChildCount() : -1));
                    if (list instanceof ViewGroup) {
                        styleVisible((ViewGroup) list, activity);
                    }
                } catch (Throwable ignored) {
                }
                handler.postDelayed(this, 600L);
            }
        }, 300L);
    }

    private ChatBubbleStyler() {
    }

    static void styleVisible(ViewGroup list, Context context) {
        if (list == null || context == null) {
            return;
        }
        for (int i = 0; i < list.getChildCount(); i++) {
            View item = list.getChildAt(i);
            if (item == null) {
                continue;
            }
            try {
                styleItem(item, context);
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean dumped;

    private static boolean ancestorLogged;

    private static void styleDecor(View view, Context context) {
        if (view == null) return;
        if (!ancestorLogged && view instanceof TextView
                && String.valueOf(((TextView) view).getText()).contains("干嘛")) {
            ancestorLogged = true;
            View cur = view;
            for (int i = 0; i < 8 && cur != null; i++) {
                android.util.Log.i("WeChatGlass", "CB anc " + i + " "
                        + cur.getClass().getSimpleName() + " id=0x"
                        + Integer.toHexString(cur.getId()) + " " + cur.getWidth()
                        + "x" + cur.getHeight());
                cur = cur.getParent() instanceof View ? (View) cur.getParent() : null;
            }
        }
        int id = resolveId(context, "bkj");
        if (view.getId() == id) {
            View text = findById(view, context, "bkl");
            if (text instanceof TextView) {
                styleText(view, (TextView) text, view, context);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) {
                styleDecor(g.getChildAt(i), context);
            }
        }
    }

    static boolean isChromeText(String s) {
        return s.matches("\\d{1,2}:\\d{2}") || s.contains("昨日")
                || s.contains("昨日") || s.contains("星期")
                || s.matches("\\d{1,2}/\\d{1,2}");
    }

    private static void styleX2C(TextView text, Context context) {
        View bubble = text;
        View parent = text.getParent() instanceof View ? (View) text.getParent() : null;
        int screen0 = context.getResources().getDisplayMetrics().widthPixels;
        if (parent instanceof ViewGroup
                && parent.getWidth() > 0 && parent.getHeight() > 0
                && parent.getWidth() <= screen0 * 86 / 100
                && parent.getHeight() <= text.getHeight() + M3Theme.dp(context, 48f)) {
            bubble = parent;
        }
        int[] loc = new int[2];
        text.getLocationOnScreen(loc);
        int screen = context.getResources().getDisplayMetrics().widthPixels;
        boolean outgoing = loc[0] + text.getWidth() / 2 > screen / 2;
        M3ColorScheme scheme = M3Theme.colors(context);
        int color = outgoing
                ? M3ColorScheme.mix(scheme.surface, scheme.primary, 0.18f)
                : scheme.surfaceContainerHighest;
        View cur = text;
        for (int i = 0; i < 6 && cur != null && cur != bubble; i++) {
            if (cur.getBackground() != null) {
                cur.setBackground(null);
            }
            cur = cur.getParent() instanceof View ? (View) cur.getParent() : null;
        }
        android.graphics.drawable.GradientDrawable shape =
                M3Drawable.fill(context, color, M3Shape.LARGE_INCREASED);
        bubble.setBackground(shape);
        bubble.setPadding(M3Theme.dp(context, 12f), M3Theme.dp(context, 8f),
                M3Theme.dp(context, 12f), M3Theme.dp(context, 8f));
        if (bubble != text) {
            if (text.getBackground() != null) text.setBackground(null);
            text.setPadding(0, 0, 0, 0);
        }
        text.setTextColor(scheme.onSurface);
        M3Type.apply(text, M3Type.BODY_LARGE);
        android.util.Log.i("WeChatGlass", "CB x2c txt=" + shorten(String.valueOf(text.getText()))
                + " bubble=" + bubble.getClass().getSimpleName()
                + " color=#" + Integer.toHexString(color));
    }

    private static boolean styleItem(View item, Context context) {
        if (!dumped && item != null) {
            dumped = true;
            dump(item, "  ", 0);
        }
        android.util.Log.i("WeChatGlass", "CB scan item="
                + item.getClass().getName() + " children="
                + (item instanceof ViewGroup ? ((ViewGroup) item).getChildCount() : -1)
                + " bkjId=0x" + Integer.toHexString(resolveId(context, "bkj")));
        View bkj = findById(item, context, "bkj");
        if (bkj == null) {
            return false;
        }
        View raw = findById(item, context, "bkl");
        TextView best = raw instanceof TextView ? (TextView) raw : null;
        if (best == null) {
            int bestArea = 0;
            best = findText(item, null, bestArea);
        }
        if (best == null) {
            return false;
        }
        android.util.Log.i("WeChatGlass", "CB item=" + item.getClass().getSimpleName()
                + " text=" + best.getClass().getSimpleName()
                + " txt=" + shorten(String.valueOf(best.getText()))
                + " bkj=" + bkj.getClass().getSimpleName()
                + " " + bkj.getWidth() + "x" + bkj.getHeight()
                + " bg=" + (bkj.getBackground() == null ? "null"
                : bkj.getBackground().getClass().getSimpleName()));
        return styleText(item, best, bkj, context);
    }

    private static String shorten(String s) {
        return s.length() > 16 ? s.substring(0, 16) : s;
    }

    /** Called straight from MMNeat7extView.setBackgroundResource. */
    static void attachText(final TextView text) {
        if (text == null) return;
        android.util.Log.i("WeChatGlass", "CB attachText txt="
                + shorten(String.valueOf(text.getText())));
        View cur = text;
        for (int i = 0; i < 7 && cur != null; i++) {
            android.util.Log.i("WeChatGlass", "CB text anc " + i + " "
                    + cur.getClass().getSimpleName() + " id=0x"
                    + Integer.toHexString(cur.getId()) + " " + cur.getWidth()
                    + "x" + cur.getHeight());
            cur = cur.getParent() instanceof View ? (View) cur.getParent() : null;
        }
        text.post(new Runnable() {
            @Override public void run() { force(text); }
        });
    }

    static void force(final TextView text) {
        if (text == null) {
            return;
        }
        text.post(new Runnable() {
            @Override
            public void run() {
                try {
                    View item = text.getParent() instanceof View
                            ? (View) text.getParent() : text;
                    View bkj = findById(item, text.getContext(), "bkj");
                    styleText(item, text, bkj, text.getContext());
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static boolean styleText(View item, TextView best, View bkj, Context context) {
        CharSequence text = best.getText();
        if (text == null || text.length() < 1) {
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

        View bubble = bkj;
        if (bubble == null) {
            View parent = best.getParent() instanceof View
                    ? (View) best.getParent() : null;
            int sw = context.getResources().getDisplayMetrics().widthPixels;
            if (parent instanceof ViewGroup
                    && parent.getWidth() > 0 && parent.getHeight() > 0
                    && parent.getWidth() <= sw * 86 / 100
                    && parent.getHeight() <= best.getHeight() + M3Theme.dp(context, 48f)) {
                bubble = parent;
            } else {
                bubble = best;
            }
        }
        android.graphics.drawable.GradientDrawable shape =
                M3Drawable.fill(context, color, M3Shape.LARGE_INCREASED);
        bubble.setBackground(shape);
        bubble.setPadding(M3Theme.dp(context, 12f), M3Theme.dp(context, 8f),
                M3Theme.dp(context, 12f), M3Theme.dp(context, 8f));
        if (bubble instanceof ViewGroup) {
            clearInnerBackgrounds((ViewGroup) bubble, color);
        }
        if (best.getBackground() != null) {
            best.setBackground(null);
        }
        best.setPadding(0, 0, 0, 0);
        best.setTextColor(scheme.onSurface);
        M3Type.apply(best, M3Type.BODY_LARGE);
        android.util.Log.i("WeChatGlass", "CB apply to="
                + bubble.getClass().getSimpleName() + " color=#"
                + Integer.toHexString(color) + " textBg="
                + (best.getBackground() == null ? "null"
                : best.getBackground().getClass().getSimpleName()));
        return true;
    }

    /** Clear the old WeChat 9-patch from inner wrappers, keep the tail view. */
    private static void clearInnerBackgrounds(ViewGroup group, int color) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getId() == View.NO_ID) {
                // Keep the native tail, but dye it to the bubble color.
                if (child.getBackground() != null) {
                    child.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(color));
                }
                continue;
            }
            if (child.getBackground() != null) {
                child.setBackground(null);
            }
            if (child instanceof ViewGroup) {
                clearInnerBackgrounds((ViewGroup) child, color);
            }
        }
    }

    private static void logLists(View v, int depth) {
        if (v == null || depth > 8) return;
        if (v.getClass().getName().contains("RecyclerView")) {
            android.util.Log.i("WeChatGlass", "CB lists " + v.getClass().getName()
                    + " children=" + (v instanceof ViewGroup
                    ? ((ViewGroup) v).getChildCount() : -1)
                    + " " + v.getWidth() + "x" + v.getHeight());
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                logLists(g.getChildAt(i), depth + 1);
            }
        }
    }

    private static void dump(View v, String indent, int depth) {
        if (v == null || depth > 4) return;
        android.util.Log.i("WeChatGlass", "CB tree " + indent
                + v.getClass().getSimpleName() + " id=0x"
                + Integer.toHexString(v.getId()) + " " + v.getWidth()
                + "x" + v.getHeight());
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                dump(g.getChildAt(i), indent + "  ", depth + 1);
            }
        }
    }

    private static int resolveId(Context context, String name) {
        if ("bkj".equals(name)) {
            return 0x7f0a0f8a;
        }
        if ("bkl".equals(name)) {
            return 0x7f0a0f8d;
        }
        int id = context.getResources().getIdentifier(name, "id", "com.tencent.mm");
        if (id != 0) {
            return id;
        }
        try {
            Class<?> r = context.getClassLoader().loadClass(
                    "com.tencent.mm.R$id");
            return r.getField(name).getInt(null);
        } catch (Throwable ignored) {
            // WeChat 8.0.79 resource ids (resources are renamed at runtime).
            if ("bkj".equals(name)) {
                return 0x7f0a0f8a;
            }
            if ("bkl".equals(name)) {
                return 0x7f0a0f8d;
            }
            return 0;
        }
    }

    private static View findById(View item, Context context, String name) {
        if (item == null || context == null) {
            return null;
        }
        int id = resolveId(context, name);
        if (id == 0) {
            return null;
        }
        View current = item;
        for (int depth = 0; depth < 4 && current != null; depth++) {
            if (current.getId() == id) {
                return current;
            }
            if (current instanceof ViewGroup) {
                View found = ((ViewGroup) current).findViewById(id);
                if (found != null) {
                    return found;
                }
            }
            current = current.getParent() instanceof View
                    ? (View) current.getParent() : null;
        }
        return null;
    }

    /**
     * WeChat has no outer bubble view; insert one around MMNeat7extView without
     * changing the native text view or its click/selection behaviour.
     */
    private static View ensureBubble(TextView text, Context context) {
        if (!(text.getParent() instanceof View)) {
            return null;
        }
        View current = (View) text.getParent();
        if ("wxglass_bubble".equals(current.getTag())) {
            return current;
        }
        if (!(current instanceof android.view.ViewGroup)) {
            return null;
        }
        android.view.ViewGroup parent = (android.view.ViewGroup) current;
        int index = parent.indexOfChild(text);
        if (index < 0) {
            return null;
        }
        android.view.ViewGroup.LayoutParams lp = text.getLayoutParams();
        if (lp != null) {
            lp.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            if (lp instanceof android.widget.LinearLayout.LayoutParams) {
                ((android.widget.LinearLayout.LayoutParams) lp).weight = 0f;
            }
        }
        parent.removeView(text);
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(context);
        wrap.setTag("wxglass_bubble");
        wrap.setClipChildren(false);
        wrap.setClipToPadding(false);
        android.widget.FrameLayout.LayoutParams inner =
                new android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        inner.gravity = android.view.Gravity.CENTER_VERTICAL;
        wrap.addView(text, inner);
        if (lp != null) {
            parent.addView(wrap, index, lp);
        } else {
            parent.addView(wrap, index);
        }
        return wrap;
    }

    /** The tightest parent that can act as the outer message bubble. */
    private static View findBubble(TextView text, Context context, int screen) {
        View best = text;
        View current = text.getParent() instanceof View
                ? (View) text.getParent() : null;
        int maxWidth = screen * 86 / 100;
        int maxHeight = text.getHeight() + M3Theme.dp(context, 48f);
        for (int depth = 0; depth < 3 && current != null; depth++) {
            if (current.getWidth() > 0 && current.getHeight() > 0
                    && current.getWidth() <= maxWidth
                    && current.getHeight() >= text.getHeight()
                    && current.getHeight() <= maxHeight) {
                best = current;
            }
            current = current.getParent() instanceof View
                    ? (View) current.getParent() : null;
        }
        return best;
    }

    private static TextView findText(View view, TextView best, int bestArea) {
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            CharSequence text = tv.getText();
            if (text != null && text.length() > 0
                    && tv.getVisibility() == View.VISIBLE
                    && tv.getWidth() > 0 && tv.getHeight() > 0) {
                int area = tv.getWidth() * tv.getHeight();
                if (tv.getClass().getName().contains("MMNeat7extView")) {
                    area += 1_000_000;
                }
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
