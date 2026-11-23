package com.wechatglass;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.wechatglass.m3.M3ColorScheme;
import com.wechatglass.m3.M3Shape;
import com.wechatglass.m3.M3Theme;
import com.wechatglass.m3.M3Type;

import java.util.WeakHashMap;

/**
 * Styles only real chat-message items.
 *
 * <p>ChattingItemContainer gives the inflated message layout the id
 * 0x7f0a0f8a (R.id.bkj); the text inside is bkl. bkj itself is added with
 * MATCH_PARENT, so the bubble is applied to the tight parent of bkl instead.
 */
final class ChatBubbleStyler {

    private static final int ID_BKJ = 0x7f0a0f8a;
    private static final int ID_BKL = 0x7f0a0f8d;

    private static final WeakHashMap<android.app.Activity, Boolean> STARTED =
            new WeakHashMap<>();
    private static final WeakHashMap<View, BubbleDrawable> APPLIED =
            new WeakHashMap<>();
    private static final WeakHashMap<ViewGroup, Boolean> DECORATED =
            new WeakHashMap<>();
    private static volatile boolean active;
    private static int diag;
    // One structural trace per direction is enough to identify whether the
    // visible tail is our drawable or an unremoved WeChat child view. Do not
    // log message text here: chat content must never enter diagnostic logs.
    private static boolean tracedIncoming;
    private static boolean tracedOutgoing;

    private ChatBubbleStyler() {
    }

    static boolean isActive() {
        return active;
    }

    static void attach(final android.app.Activity activity) {
        if (activity == null || STARTED.containsKey(activity)) {
            return;
        }
        STARTED.put(activity, Boolean.TRUE);
        active = true;
        final android.os.Handler handler = new android.os.Handler(
                android.os.Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    View decor = activity.getWindow() == null
                            ? null : activity.getWindow().getDecorView();
                    View list = decor == null ? null
                            : Ui.findByClassContains(decor, "ScrollControlRecyclerView");
                    android.util.Log.i("WeChatGlass", "CB list="
                            + (list == null ? "null" : list.getClass().getName()));
                    if (list instanceof ViewGroup) {
                        ViewGroup group = (ViewGroup) list;
                        for (int i = 0; i < group.getChildCount(); i++) {
                            styleItem(group.getChildAt(i), activity);
                        }
                    }
                } catch (Throwable ignored) {
                }
                handler.postDelayed(this, 600L);
            }
        }, 300L);
    }

    static void force(TextView text) {
    }

    static void attachText(TextView text) {
    }

    static void mark(TextView text) {
    }

    static void detach(android.app.Activity activity) {
        active = false;
        if (activity != null) {
            STARTED.remove(activity);
        }
    }

    private static void styleItem(View item, Context context) {
        if (item == null) {
            return;
        }
        View target = findBubbleTarget(item, null, 0);
        if (target == null || target.getWidth() <= 0 || target.getHeight() <= 0
                || target.getVisibility() != View.VISIBLE) {
            return;
        }
        View parent = target.getParent() instanceof View
                ? (View) target.getParent() : null;
        View bubble = parent != null && parent != item ? parent : null;
        if (bubble == null) {
            bubble = item.findViewById(ID_BKJ);
        }
        if (bubble == null || bubble == item) {
            return;
        }
        roundAvatar(findAvatar(item));
        if (diag < 30) {
            diag++;
            View avatar = findAvatar(item);
            int[] ax = new int[2];
            if (avatar != null) avatar.getLocationOnScreen(ax);
            ViewGroup.LayoutParams lp = bubble.getLayoutParams();
            android.util.Log.i("WeChatGlass", "CB layout item="
                    + item.getClass().getSimpleName()
                    + " bubble=" + bubble.getClass().getSimpleName()
                    + " bx=" + bubble.getLeft() + " bw=" + bubble.getWidth()
                    + " lp=" + (lp == null ? "null" : lp.getClass().getSimpleName())
                    + " avatar=" + (avatar == null ? "null" : avatar.getClass().getSimpleName())
                    + "@" + ax[0] + " " + (avatar == null ? 0 : avatar.getWidth()));
            android.util.Log.i("WeChatGlass", "CB item="
                    + item.getClass().getSimpleName()
                    + " target=" + target.getClass().getSimpleName()
                    + " " + target.getWidth() + "x" + target.getHeight()
                    + " bubble=" + bubble.getClass().getSimpleName()
                    + " " + bubble.getWidth() + "x" + bubble.getHeight());
        }

        int screen = context.getResources().getDisplayMetrics().widthPixels;
        int[] bl = new int[2];
        bubble.getLocationOnScreen(bl);
        // Use the actual bubble container, not the text child, so the tail
        // side follows the message alignment.
        // The avatar is always on the opposite side of the sender and its
        // center never depends on message length. Fall back to margins only
        // when an item has no avatar (system/call rows).
        boolean outgoing = isOutgoingByAvatar(item, screen);
        if (!hasAvatar(item)) {
            int rightMargin = screen - (bl[0] + bubble.getWidth());
            outgoing = bl[0] > rightMargin;
        }
        traceItemOnce(item, bubble, target, outgoing);
        int[] txl = new int[2];
        target.getLocationOnScreen(txl);
        android.util.Log.i("WeChatGlass", "CB dir outgoing=" + outgoing
                + " bx=" + bl[0] + " bw=" + bubble.getWidth()
                + " targetX=" + txl[0]
                + " text=" + (target instanceof TextView
                ? String.valueOf(((TextView) target).getText()) : target.getClass().getSimpleName()));
        M3ColorScheme scheme = M3Theme.colors(context);
        boolean night = isDarkTheme(context, item);
        // Google Chat message tokens, independent of the accent color.
        int color = outgoing
                ? (night ? 0xFF1F3A5F : 0xFFD3E3FD)
                : (night ? 0xFF2B2B2B : 0xFFF2F2F2);
        int textColor = outgoing
                ? (night ? 0xFFFFFFFF : 0xFF001D35)
                : (night ? 0xFFE6E6E6 : 0xFF1F1F1F);

        View cur = target;
        while (cur != null && cur != bubble) {
            if (cur.getBackground() != null) {
                cur.setBackground(null);
            }
            cur = cur.getParent() instanceof View ? (View) cur.getParent() : null;
        }

        // Keep our own Drawable on bubble, but clear native backgrounds in
        // its children. This makes the APPLIED cache meaningful again.
        clearNative(item, bubble);

        float radius = M3Theme.dpF(context, M3Shape.LARGE_INCREASED);
        float tail = M3Theme.dpF(context, 7f);
        BubbleDrawable shape = new BubbleDrawable(color, radius, tail, outgoing);
        BubbleDrawable previous = APPLIED.get(bubble);
        if (previous != null && bubble.getBackground() == previous
                && previous.matches(color, radius, tail, outgoing)) {
            return;
        }
        bubble.setBackground(shape);
        APPLIED.put(bubble, shape);
        bubble.setPadding(M3Theme.dp(context, 14f), M3Theme.dp(context, 10f),
                M3Theme.dp(context, 14f), M3Theme.dp(context, 10f));
        bubble.requestLayout();
        if (bubble.getParent() instanceof ViewGroup) {
            ((ViewGroup) bubble.getParent()).setClipChildren(false);
        }
        if (bubble instanceof ViewGroup) {
            ((ViewGroup) bubble).setClipChildren(false);
        }
        if (bubble != target) {
            target.setBackground(null);
            target.setPadding(0, 0, 0, 0);
        }
        if (target instanceof TextView) {
            ((TextView) target).setTextColor(textColor);
            M3Type.apply((TextView) target, M3Type.BODY_LARGE);
        } else {
            // MMNeat7extView extends View, not TextView.
            try {
                target.getClass().getMethod("setTextColor", int.class)
                        .invoke(target, textColor);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void traceItemOnce(View item, View bubble, View target,
                                      boolean outgoing) {
        if (outgoing ? tracedOutgoing : tracedIncoming) {
            return;
        }
        if (outgoing) {
            tracedOutgoing = true;
        } else {
            tracedIncoming = true;
        }
        android.util.Log.i("WeChatGlass", "CB tree begin direction="
                + (outgoing ? "outgoing" : "incoming"));
        logTree(item, bubble, target, 0);
        android.util.Log.i("WeChatGlass", "CB tree end direction="
                + (outgoing ? "outgoing" : "incoming"));
    }

    private static void logTree(View view, View bubble, View target, int depth) {
        if (view == null || depth > 6) {
            return;
        }
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        String bg = view.getBackground() == null ? "-"
                : view.getBackground().getClass().getSimpleName();
        String fg = "-";
        if (android.os.Build.VERSION.SDK_INT >= 23 && view.getForeground() != null) {
            fg = view.getForeground().getClass().getSimpleName();
        }
        int children = view instanceof ViewGroup ? ((ViewGroup) view).getChildCount() : 0;
        String role = view == bubble ? " bubble" : view == target ? " target" : "";
        android.util.Log.i("WeChatGlass", "CB tree " + depth + role
                + " class=" + view.getClass().getName()
                + " id=0x" + Integer.toHexString(view.getId())
                + " screen=" + loc[0] + "," + loc[1]
                + " size=" + view.getWidth() + "x" + view.getHeight()
                + " vis=" + view.getVisibility()
                + " bg=" + bg + " fg=" + fg + " children=" + children);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                logTree(group.getChildAt(i), bubble, target, depth + 1);
            }
        }
    }

    private static int dp(float value) {
        return Math.round(value * android.content.res.Resources.getSystem()
                .getDisplayMetrics().density);
    }

    private static void roundAvatar(View avatar) {
        if (avatar == null || avatar.getWidth() <= 0 || avatar.getHeight() <= 0) {
            return;
        }
        avatar.setClipToOutline(true);
        avatar.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        avatar.invalidate();
    }

    private static boolean hasAvatar(View item) {
        return findAvatar(item) != null;
    }

    private static boolean isOutgoingByAvatar(View item, int screen) {
        View avatar = findAvatar(item);
        if (avatar == null) {
            return false;
        }
        int[] loc = new int[2];
        avatar.getLocationOnScreen(loc);
        boolean out = loc[0] + avatar.getWidth() / 2 > screen / 2;
        android.util.Log.i("WeChatGlass", "CB avatar="
                + avatar.getClass().getName() + " left=" + avatar.getLeft()
                + " x=" + loc[0] + " " + avatar.getWidth() + "x"
                + avatar.getHeight() + " outgoing=" + out);
        return out;
    }

    private static View findAvatar(View item) {
        View strict = findAvatar(item, true, null, 0);
        return strict != null ? strict : findAvatar(item, false, null, 0);
    }

    private static View findAvatar(View view, boolean strictOnly,
                                   View best, int bestScore) {
        if (view instanceof android.widget.ImageView && view.getWidth() > 0
                && view.getHeight() > 0 && view.getVisibility() == View.VISIBLE) {
            String n = view.getClass().getName();
            boolean strict = n.contains("ChattingAvatarImageView")
                    || n.contains("Avatar") || n.contains("Mask");
            if (!strictOnly || strict) {
                int score = view.getWidth() * view.getHeight()
                        * (strict ? 100 : 1);
                if (score > bestScore) {
                    best = view;
                    bestScore = score;
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findAvatar(group.getChildAt(i), strictOnly,
                        best, bestScore);
                if (found != null) {
                    best = found;
                    bestScore = found.getWidth() * found.getHeight()
                            * (found.getClass().getName().contains("Avatar")
                            || found.getClass().getName().contains("Mask") ? 100 : 1);
                }
            }
        }
        return best;
    }

    private static View findBubbleTarget(View view, View best, int bestScore) {
        if (view == null) {
            return best;
        }
        boolean neat = view.getClass().getName().contains("MMNeat7extView")
                || view.getClass().getName().contains("NeatTextView");
        if (neat && view.getVisibility() == View.VISIBLE
                && view.getWidth() > 0 && view.getHeight() > 0) {
            int score = view.getWidth() * view.getHeight() * 2;
            if (score > bestScore) {
                return view;
            }
        }
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            CharSequence cs = tv.getText();
            if (cs != null && cs.length() > 0
                    && tv.getVisibility() == View.VISIBLE
                    && tv.getWidth() > 0 && tv.getHeight() > 0
                    && !isChromeText(cs.toString())) {
                int score = tv.getWidth() * Math.max(1, cs.length());
                if (score > bestScore) {
                    return tv;
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findBubbleTarget(group.getChildAt(i), best, bestScore);
                if (found != null) {
                    best = found;
                    bestScore = found.getWidth() * Math.max(1, found.getHeight());
                }
            }
        }
        return best;
    }

    private static boolean isDarkTheme(Context context, View item) {
        if (M3Theme.colors(context).dark) {
            return true;
        }
        View v = item;
        for (int i = 0; i < 8 && v != null; i++) {
            android.graphics.drawable.Drawable d = v.getBackground();
            if (d instanceof android.graphics.drawable.ColorDrawable) {
                int c = ((android.graphics.drawable.ColorDrawable) d).getColor();
                if (android.graphics.Color.alpha(c) > 0) {
                    double lum = 0.2126 * android.graphics.Color.red(c)
                            + 0.7152 * android.graphics.Color.green(c)
                            + 0.0722 * android.graphics.Color.blue(c);
                    return lum < 115;
                }
            }
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        return false;
    }

    private static void clearNative(View view, View keep) {
        if (view == null) {
            return;
        }
        if (view == keep) {
            if (view instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) view;
                for (int i = 0; i < g.getChildCount(); i++) {
                    clearNative(g.getChildAt(i), null);
                }
            }
            return;
        }
        String n = view.getClass().getSimpleName();
        boolean image = n.contains("Image") || n.contains("Avatar")
                || n.contains("Mask") || n.contains("CheckBox");
        if (!image) {
            if (view.getBackground() != null) {
                view.setBackground(null);
            }
            if (android.os.Build.VERSION.SDK_INT >= 23
                    && view.getForeground() != null) {
                view.setForeground(null);
            }
            // Native bubble tails are tiny id-less plain Views. Clearing
            // their background is not enough if they draw in onDraw.
            if (view.getClass() == View.class && view.getId() == View.NO_ID
                    && view.getWidth() > 0 && view.getHeight() > 0
                    && view.getWidth() < dp(56) && view.getHeight() < dp(56)) {
                view.setVisibility(View.INVISIBLE);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                clearNative(group.getChildAt(i), keep);
            }
        }
    }

    private static View contentContainer(View item, TextView text) {
        View parent = text.getParent() instanceof View ? (View) text.getParent() : null;
        View best = parent;
        while (parent != null && parent != item) {
            String n = parent.getClass().getSimpleName();
            if (n.contains("Avatar") || n.contains("Mask") || n.contains("CheckBox")) {
                break;
            }
            if (parent.getWidth() > 0 && parent.getHeight() > 0) {
                best = parent;
            }
            parent = parent.getParent() instanceof View ? (View) parent.getParent() : null;
        }
        return best;
    }

    /** Prefer the text's tight parent, never the full-width bkj row. */
    private static View tightParent(TextView text, Context context, int screen) {
        View parent = text.getParent() instanceof View
                ? (View) text.getParent() : null;
        if (parent instanceof ViewGroup
                && parent.getWidth() > 0 && parent.getHeight() > 0
                && parent.getWidth() <= screen * 86 / 100
                && parent.getHeight() <= text.getHeight()
                + M3Theme.dp(context, 48f)) {
            return parent;
        }
        return text;
    }

    private static TextView findMessageText(View view) {
        return findBest(view, null, 0);
    }

    private static TextView findBest(View view, TextView best, int bestScore) {
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            CharSequence cs = tv.getText();
            if (cs != null && cs.length() > 0
                    && tv.getVisibility() == View.VISIBLE
                    && tv.getWidth() > 0 && tv.getHeight() > 0
                    && !isChromeText(cs.toString())) {
                int score = tv.getWidth() * Math.max(1, cs.length());
                if (score > bestScore) {
                    return tv;
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findBest(group.getChildAt(i), best, bestScore);
                if (found != null) {
                    best = found;
                    bestScore = found.getWidth() * Math.max(1,
                            found.getText() == null ? 1 : found.getText().length());
                }
            }
        }
        return best;
    }

    static boolean isChromeText(String s) {
        return s.matches("\\d{1,2}:\\d{2}") || s.matches("\\d{1,2}/\\d{1,2}")
                || s.contains("昨日") || s.contains("星期")
                || s.contains("メッセージはもうありません")
                || s.contains("以下は新しいメッセージです");
    }

    private static TextView findText(View view) {
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            CharSequence cs = tv.getText();
            if (cs != null && cs.length() > 0
                    && tv.getVisibility() == View.VISIBLE
                    && tv.getWidth() > 0 && tv.getHeight() > 0) {
                return tv;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
