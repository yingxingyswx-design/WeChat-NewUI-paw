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

    private static final WeakHashMap<android.app.Activity, Session> SESSIONS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, BubbleDrawable> APPLIED =
            new WeakHashMap<>();
    private static final WeakHashMap<View, ViewState> ORIGINAL = new WeakHashMap<>();
    private static final WeakHashMap<ViewGroup, Boolean> DECORATED =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> ROUNDED_STICKERS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> AVATAR_CHAIN_LOGGED =
            new WeakHashMap<>();
    private static int avatarChainDiag;
    private static final android.view.ViewOutlineProvider CIRCLE_OUTLINE =
            new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            };
    private static volatile boolean active;
    private static int diag;
    // One structural trace per direction is enough to identify whether the
    // visible tail is our drawable or an unremoved WeChat child view. Do not
    // log message text here: chat content must never enter diagnostic logs.
    private static boolean tracedIncoming;
    private static boolean tracedOutgoing;

    private static final class Session {
        final android.os.Handler handler = new android.os.Handler(
                android.os.Looper.getMainLooper());
        Runnable poller;
        boolean enabled;
    }

    private static final class ViewState {
        final android.graphics.drawable.Drawable background;
        final android.graphics.drawable.Drawable foreground;
        final int visibility;
        final int paddingLeft;
        final int paddingTop;
        final int paddingRight;
        final int paddingBottom;
        final int paddingStart;
        final int paddingEnd;
        final boolean paddingRelative;
        final boolean clipToOutline;
        final android.view.ViewOutlineProvider outlineProvider;
        final boolean clipChildren;
        final android.graphics.drawable.Drawable imageDrawable;
        final android.content.res.ColorStateList textColors;
        final float textSize;
        final float letterSpacing;
        final float lineSpacingExtra;
        final float lineSpacingMultiplier;
        final boolean includeFontPadding;
        final android.graphics.Typeface typeface;
        final Integer reflectedTextColor;

        ViewState(View view) {
            background = view.getBackground();
            foreground = android.os.Build.VERSION.SDK_INT >= 23
                    ? view.getForeground() : null;
            visibility = view.getVisibility();
            paddingLeft = view.getPaddingLeft();
            paddingTop = view.getPaddingTop();
            paddingRight = view.getPaddingRight();
            paddingBottom = view.getPaddingBottom();
            paddingStart = view.getPaddingStart();
            paddingEnd = view.getPaddingEnd();
            paddingRelative = view.isPaddingRelative();
            clipToOutline = view.getClipToOutline();
            outlineProvider = view.getOutlineProvider();
            clipChildren = view instanceof ViewGroup
                    && ((ViewGroup) view).getClipChildren();
            imageDrawable = view instanceof android.widget.ImageView
                    ? ((android.widget.ImageView) view).getDrawable() : null;
            if (view instanceof TextView) {
                TextView text = (TextView) view;
                textColors = text.getTextColors();
                textSize = text.getTextSize();
                letterSpacing = text.getLetterSpacing();
                lineSpacingExtra = text.getLineSpacingExtra();
                lineSpacingMultiplier = text.getLineSpacingMultiplier();
                includeFontPadding = text.getIncludeFontPadding();
                typeface = text.getTypeface();
                reflectedTextColor = null;
            } else {
                textColors = null;
                textSize = 0f;
                letterSpacing = 0f;
                lineSpacingExtra = 0f;
                lineSpacingMultiplier = 1f;
                includeFontPadding = true;
                typeface = null;
                reflectedTextColor = readReflectedTextColor(view);
            }
        }

        void restore(View view) {
            view.setBackground(background);
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                view.setForeground(foreground);
            }
            view.setVisibility(visibility);
            if (paddingRelative) {
                view.setPaddingRelative(paddingStart, paddingTop, paddingEnd, paddingBottom);
            } else {
                view.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
            }
            if (view instanceof ViewGroup) {
                ((ViewGroup) view).setClipChildren(clipChildren);
            }
            view.setOutlineProvider(outlineProvider);
            view.setClipToOutline(clipToOutline);
            if (view instanceof android.widget.ImageView && imageDrawable != null) {
                android.widget.ImageView image = (android.widget.ImageView) view;
                android.graphics.drawable.Drawable current = image.getDrawable();
                if (current instanceof OvalClipDrawable
                        && ((OvalClipDrawable) current).wraps(imageDrawable)) {
                    image.setImageDrawable(imageDrawable);
                }
            }
            if (view instanceof TextView && textColors != null) {
                TextView text = (TextView) view;
                text.setTextColor(textColors);
                text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSize);
                text.setLetterSpacing(letterSpacing);
                text.setLineSpacing(lineSpacingExtra, lineSpacingMultiplier);
                text.setIncludeFontPadding(includeFontPadding);
                text.setTypeface(typeface);
            } else if (reflectedTextColor != null) {
                try {
                    view.getClass().getMethod("setTextColor", int.class)
                            .invoke(view, reflectedTextColor);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static Integer readReflectedTextColor(View view) {
        try {
            Object value = view.getClass().getMethod("getTextColor").invoke(view);
            return value instanceof Number ? ((Number) value).intValue() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private ChatBubbleStyler() {
    }

    static boolean isActive() {
        return active;
    }

    static void attach(final android.app.Activity activity) {
        if (activity == null || SESSIONS.containsKey(activity)) {
            return;
        }
        final Session session = new Session();
        SESSIONS.put(activity, session);
        session.poller = new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    detach(activity);
                    return;
                }
                try {
                    boolean enabled = !HookConfig.nativeList(activity);
                    session.enabled = enabled;
                    updateActive();
                    if (enabled) {
                        View decor = activity.getWindow() == null
                                ? null : activity.getWindow().getDecorView();
                        View list = decor == null ? null
                                : Ui.findByClassContains(decor, "ScrollControlRecyclerView");
                        if (list instanceof ViewGroup) {
                            ViewGroup group = (ViewGroup) list;
                            for (int i = 0; i < group.getChildCount(); i++) {
                                styleItem(group.getChildAt(i), activity);
                            }
                        }
                    } else {
                        restoreOriginalViews();
                    }
                } catch (Throwable ignored) {
                }
                session.handler.postDelayed(this, 600L);
            }
        };
        session.handler.postDelayed(session.poller, 300L);
    }

    static void force(TextView text) {
    }

    static void attachText(TextView text) {
    }

    static void mark(TextView text) {
    }

    static void detach(android.app.Activity activity) {
        if (activity != null) {
            Session session = SESSIONS.remove(activity);
            if (session != null && session.poller != null) {
                session.handler.removeCallbacks(session.poller);
            }
        }
        restoreOriginalViews();
        updateActive();
    }

    private static void updateActive() {
        active = false;
        for (Session session : SESSIONS.values()) {
            if (session != null && session.enabled) {
                active = true;
                return;
            }
        }
    }

    private static void remember(View view) {
        if (view != null && !ORIGINAL.containsKey(view)) {
            ORIGINAL.put(view, new ViewState(view));
        }
    }

    private static void restoreOriginalViews() {
        for (java.util.Map.Entry<View, ViewState> entry : ORIGINAL.entrySet()) {
            View view = entry.getKey();
            ViewState state = entry.getValue();
            if (view != null && state != null) {
                try {
                    state.restore(view);
                } catch (Throwable ignored) {
                }
            }
        }
        ORIGINAL.clear();
        APPLIED.clear();
        ROUNDED_STICKERS.clear();
    }

    private static void styleItem(View item, Context context) {
        if (item == null) {
            return;
        }
        // Sticker messages do not necessarily contain text, so apply their
        // visual treatment before looking for a text-bubble target.
        roundStickers(item, context, 0);
        View avatar = findAvatar(item);
        roundChatAvatars(item, avatar);
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
        if (bubble == null || bubble == item
                || !isSimpleBubbleObject(item, bubble, target, avatar)) {
            return;
        }
        if (diag < 30) {
            diag++;
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
        boolean outgoing = isOutgoingByAvatar(avatar, screen);
        if (avatar == null) {
            int rightMargin = screen - (bl[0] + bubble.getWidth());
            outgoing = bl[0] > rightMargin;
        }
        traceItemOnce(item, bubble, target, outgoing);
        android.util.Log.i("WeChatGlass", "CB dir outgoing=" + outgoing
                + " bx=" + bl[0] + " bw=" + bubble.getWidth());
        M3ColorScheme scheme = M3Theme.colors(context);
        // Use the selected M3 seed for sent messages; received messages stay
        // on the neutral surface roles so both remain legible in light/dark.
        int color = outgoing
                ? (scheme.dark ? scheme.secondaryContainer : scheme.primaryContainer)
                : scheme.surfaceContainerLow;
        int textColor = outgoing
                ? (scheme.dark ? 0xFFFFFFFF : scheme.onPrimaryContainer)
                : scheme.onSurface;

        View cur = target;
        while (cur != null && cur != bubble) {
            if (cur.getBackground() != null) {
                remember(cur);
                cur.setBackground(null);
            }
            cur = cur.getParent() instanceof View ? (View) cur.getParent() : null;
        }

        float radius = M3Theme.dpF(context, M3Shape.LARGE_INCREASED);
        float tail = M3Theme.dpF(context, 7f);
        BubbleDrawable shape = new BubbleDrawable(color, radius, tail, outgoing);
        BubbleDrawable previous = APPLIED.get(bubble);
        if (previous != null && bubble.getBackground() == previous
                && previous.matches(color, radius, tail, outgoing)) {
            return;
        }
        remember(bubble);
        bubble.setBackground(shape);
        APPLIED.put(bubble, shape);
        bubble.setPadding(M3Theme.dp(context, 14f), M3Theme.dp(context, 10f),
                M3Theme.dp(context, 14f), M3Theme.dp(context, 10f));
        bubble.requestLayout();
        if (bubble.getParent() instanceof ViewGroup) {
            ViewGroup bubbleParent = (ViewGroup) bubble.getParent();
            remember(bubbleParent);
            bubbleParent.setClipChildren(false);
        }
        if (bubble instanceof ViewGroup) {
            remember(bubble);
            ((ViewGroup) bubble).setClipChildren(false);
        }
        if (bubble != target) {
            remember(target);
            target.setBackground(null);
            target.setPadding(0, 0, 0, 0);
        }
        if (target instanceof TextView) {
            remember(target);
            ((TextView) target).setTextColor(textColor);
            M3Type.apply((TextView) target, M3Type.BODY_LARGE);
        } else {
            // MMNeat7extView extends View, not TextView.
            try {
                remember(target);
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
        if (avatar.getOutlineProvider() != CIRCLE_OUTLINE) {
            remember(avatar);
            avatar.setOutlineProvider(CIRCLE_OUTLINE);
        }
        if (!avatar.getClipToOutline()) {
            remember(avatar);
            avatar.setClipToOutline(true);
        }
        avatar.invalidateOutline();
        if (avatar instanceof android.widget.ImageView) {
            android.widget.ImageView image = (android.widget.ImageView) avatar;
            android.graphics.drawable.Drawable drawable = image.getDrawable();
            if (drawable != null && !(drawable instanceof OvalClipDrawable)) {
                remember(avatar);
                image.setImageDrawable(new OvalClipDrawable(drawable));
            }
        }
    }

    /** Locate and clip the avatar object by its geometry inside this message row. */
    private static void roundChatAvatars(View item, View avatar) {
        if (avatar == null) {
            return;
        }
        roundAvatar(avatar);
        if (avatarChainDiag < 4
                && AVATAR_CHAIN_LOGGED.put(avatar, Boolean.TRUE) == null) {
            avatarChainDiag++;
            logAvatarObjectChain(item, avatar);
        }

    }

    /** Log object identity and geometry only; never inspect or emit chat text. */
    private static void logAvatarObjectChain(View item, View avatar) {
        View current = avatar;
        int depth = 0;
        while (current != null && depth < 10) {
            int[] screen = new int[2];
            current.getLocationOnScreen(screen);
            View parent = current.getParent() instanceof View
                    ? (View) current.getParent() : null;
            android.graphics.drawable.Drawable background = current.getBackground();
            String bg = "none";
            if (background instanceof android.graphics.drawable.ColorDrawable) {
                bg = "color:#" + Integer.toHexString(
                        ((android.graphics.drawable.ColorDrawable) background).getColor());
            } else if (background instanceof android.graphics.drawable.GradientDrawable) {
                bg = "gradient";
            } else if (background != null) {
                bg = "set alpha=" + background.getAlpha();
            }
            int children = current instanceof ViewGroup
                    ? ((ViewGroup) current).getChildCount() : 0;
            android.util.Log.i("WeChatGlass", "CB avatar chain depth=" + depth
                    + " object="
                    + Integer.toHexString(System.identityHashCode(current))
                    + " parent=" + (parent == null ? "none"
                    : Integer.toHexString(System.identityHashCode(parent)))
                    + " bounds=" + current.getLeft() + "," + current.getTop()
                    + "," + current.getWidth() + "x" + current.getHeight()
                    + " screen=" + screen[0] + "," + screen[1]
                    + " bg=" + bg
                    + " fg=" + (current.getForeground() != null)
                    + " clip=" + current.getClipToOutline()
                    + " oval=" + (current.getOutlineProvider() == CIRCLE_OUTLINE)
                    + " children=" + children);
            if (current == item) {
                break;
            }
            current = parent;
            depth++;
        }
    }

    private static int dp(float density, float value) {
        return Math.round(density * value);
    }

    /**
     * WeChat renders custom stickers with MMAnimateView.  Constrain matching
     * views to a real chat item so the emoji picker, avatars and other WeChat
     * surfaces keep their native geometry.
     */
    private static void roundStickers(View view, Context context, int depth) {
        if (view == null || depth > 8) {
            return;
        }
        if (isStickerView(view) && ROUNDED_STICKERS.put(view, Boolean.TRUE) == null) {
            final float radius = M3Theme.dpF(context, 20f);
            remember(view);
            view.setClipToOutline(true);
            view.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View target, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, target.getWidth(), target.getHeight(), radius);
                }
            });
            view.invalidateOutline();
            view.invalidate();
            android.util.Log.i("WeChatGlass", "CB rounded sticker "
                    + view.getClass().getName() + " " + view.getWidth()
                    + "x" + view.getHeight());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                roundStickers(group.getChildAt(i), context, depth + 1);
            }
        }
    }

    private static boolean isStickerView(View view) {
        if (view.getVisibility() != View.VISIBLE || view.getWidth() < dp(32)
                || view.getHeight() < dp(32)) {
            return false;
        }
        String name = view.getClass().getName();
        return name.contains("MMAnimateView")
                || name.contains("EmojiImageView")
                || name.contains("EmojiView")
                || name.contains("GifImageView");
    }

    private static boolean isOutgoingByAvatar(View avatar, int screen) {
        if (avatar == null) {
            return false;
        }
        int[] loc = new int[2];
        avatar.getLocationOnScreen(loc);
        boolean out = loc[0] + avatar.getWidth() / 2 > screen / 2;
        android.util.Log.i("WeChatGlass", "CB avatar object="
                + Integer.toHexString(System.identityHashCode(avatar))
                + " left=" + avatar.getLeft()
                + " x=" + loc[0] + " " + avatar.getWidth() + "x"
                + avatar.getHeight() + " outgoing=" + out
                + " clip=" + avatar.getClipToOutline()
                + " oval=" + (avatar.getOutlineProvider() == CIRCLE_OUTLINE));
        return out;
    }

    private static View findAvatar(View item) {
        if (item == null || item.getWidth() <= 0) {
            return null;
        }
        AvatarSearch search = new AvatarSearch(item);
        findAvatar(item, search, 0);
        return search.best;
    }

    private static final class AvatarSearch {
        final int[] itemLocation = new int[2];
        final int itemWidth;
        final float density;
        View best;
        float bestScore = Float.MAX_VALUE;

        AvatarSearch(View item) {
            item.getLocationOnScreen(itemLocation);
            itemWidth = item.getWidth();
            density = item.getResources().getDisplayMetrics().density;
        }
    }

    private static void findAvatar(View view, AvatarSearch search, int depth) {
        if (view == null || depth > 24 || view.getVisibility() != View.VISIBLE
                || view.getAlpha() <= 0f) {
            return;
        }
        if (view instanceof android.widget.ImageView
                && view.getWidth() > 0 && view.getHeight() > 0) {
            float width = view.getWidth();
            float height = view.getHeight();
            float sizeDp = (width + height) / (2f * search.density);
            float aspect = width / height;
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            float leftGapDp = (location[0] - search.itemLocation[0]) / search.density;
            float rightGapDp = (search.itemLocation[0] + search.itemWidth
                    - location[0] - width) / search.density;
            float edgeGapDp = Math.min(leftGapDp, rightGapDp);
            if (sizeDp >= 24f && sizeDp <= 70f
                    && aspect >= 0.84f && aspect <= 1.19f
                    && edgeGapDp >= -4f && edgeGapDp <= 64f) {
                float score = Math.abs(sizeDp - 40f)
                        + Math.abs(aspect - 1f) * 80f
                        + Math.max(0f, edgeGapDp) * 2f;
                if (score < search.bestScore) {
                    search.best = view;
                    search.bestScore = score;
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                findAvatar(group.getChildAt(i), search, depth + 1);
            }
        }
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

    /**
     * Only reshape a compact, simple text/call bubble. Rich message cards such
     * as transfers and red packets expose several text/image/action objects;
     * leave those objects and their ancestors entirely native.
     */
    private static boolean isSimpleBubbleObject(View item, View bubble,
                                                View target, View avatar) {
        if (!isDescendantOf(target, bubble) || bubble.getWidth() <= 0
                || bubble.getHeight() <= 0) {
            return false;
        }
        int extra = dp(48f);
        if (bubble.getWidth() > target.getWidth() + dp(96f)
                || bubble.getHeight() > target.getHeight() + extra) {
            return false;
        }
        if (bubble.isClickable() || bubble.isLongClickable()
                || countVisibleTextObjects(bubble, target, 0) != 1
                || hasInteractiveContent(bubble, bubble, target, 0)) {
            return false;
        }

        View ancestor = bubble.getParent() instanceof View
                ? (View) bubble.getParent() : null;
        int depth = 0;
        while (ancestor != null && ancestor != item && depth++ < 8) {
            if (ancestor.isClickable() || ancestor.isLongClickable()
                    || countVisibleTextObjects(ancestor, target, 0) > 2
                    || countVisibleContentImages(ancestor, avatar, 0) > 1) {
                return false;
            }
            ancestor = ancestor.getParent() instanceof View
                    ? (View) ancestor.getParent() : null;
        }
        return true;
    }

    private static boolean isDescendantOf(View view, View ancestor) {
        View current = view;
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.getParent() instanceof View
                    ? (View) current.getParent() : null;
        }
        return false;
    }

    private static int countVisibleTextObjects(View view, View target, int depth) {
        if (view == null || depth > 16 || view.getVisibility() != View.VISIBLE
                || view.getAlpha() <= 0f) {
            return 0;
        }
        int count = 0;
        if (view == target) {
            count++;
        } else if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null && text.length() > 0 && !isChromeText(text.toString())) {
                count++;
            }
        }
        if (count >= 3 || !(view instanceof ViewGroup)) {
            return count;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount() && count < 3; i++) {
            count += countVisibleTextObjects(group.getChildAt(i), target, depth + 1);
        }
        return Math.min(count, 3);
    }

    private static int countVisibleContentImages(View view, View avatar, int depth) {
        if (view == null || view == avatar || depth > 16
                || view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0f) {
            return 0;
        }
        int count = view instanceof android.widget.ImageView
                && view.getWidth() > 0 && view.getHeight() > 0 ? 1 : 0;
        if (count >= 2 || !(view instanceof ViewGroup)) {
            return count;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount() && count < 2; i++) {
            count += countVisibleContentImages(group.getChildAt(i), avatar, depth + 1);
        }
        return Math.min(count, 2);
    }

    private static boolean hasInteractiveContent(View view, View root,
                                                  View target, int depth) {
        if (view == null || depth > 16 || view.getVisibility() != View.VISIBLE
                || view.getAlpha() <= 0f) {
            return false;
        }
        if (view != root && view != target
                && (view.isClickable() || view.isLongClickable())) {
            return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasInteractiveContent(group.getChildAt(i), root, target, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
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
