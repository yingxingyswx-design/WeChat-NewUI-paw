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

import java.util.ArrayList;
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
    private static final WeakHashMap<View, android.view.ViewOutlineProvider>
            STICKER_OUTLINES = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> AVATAR_CHAIN_LOGGED =
            new WeakHashMap<>();
    private static final WeakHashMap<View, String> DIAGNOSTIC_SNAPSHOTS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, String> APPLIED_DIAGNOSTICS =
            new WeakHashMap<>();
    private static final int MAX_DIAGNOSTIC_ITEMS = 48;
    private static final int MAX_DIAGNOSTIC_VIEWS = 32;
    private static final int MAX_DIAGNOSTIC_CANDIDATES = 12;
    private static int avatarChainDiag;
    private static final android.view.ViewOutlineProvider CIRCLE_OUTLINE =
            new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            };
    private static volatile boolean active;
    private static int diagnosticItemsLogged;

    private static final class Session {
        final android.os.Handler handler = new android.os.Handler(
                android.os.Looper.getMainLooper());
        Runnable poller;
        boolean enabled;
    }

    private static final class ViewState {
        final android.graphics.drawable.Drawable background;
        final android.content.res.ColorStateList backgroundTint;
        final android.graphics.PorterDuff.Mode backgroundTintMode;
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
            backgroundTint = android.os.Build.VERSION.SDK_INT >= 21
                    ? view.getBackgroundTintList() : null;
            backgroundTintMode = android.os.Build.VERSION.SDK_INT >= 21
                    ? view.getBackgroundTintMode() : null;
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
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                view.setBackgroundTintList(backgroundTint);
                view.setBackgroundTintMode(backgroundTintMode);
            }
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
            if (view instanceof android.widget.ImageView) {
                android.widget.ImageView image = (android.widget.ImageView) view;
                android.graphics.drawable.Drawable current = image.getDrawable();
                if (current instanceof RoundedRectClipDrawable) {
                    image.setImageDrawable(
                            ((RoundedRectClipDrawable) current).getWrappedDrawable());
                } else if (imageDrawable != null && current instanceof OvalClipDrawable
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
        STICKER_OUTLINES.clear();
        DIAGNOSTIC_SNAPSHOTS.clear();
        APPLIED_DIAGNOSTICS.clear();
        diagnosticItemsLogged = 0;
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
            View media = findMediaBubbleContainer(item, avatar, context);
            View card = media == null
                    ? findRichCardObject(item, avatar, null, context) : null;
            View selected = card == null ? media : card;
            String branch = card != null ? "rich-hit:no-text-target"
                    : media != null ? "media-hit:no-text-target" : "miss:no-text-target";
            logRuntimeDetection(item, avatar, target, null, selected,
                    branch, context, selected == null);
            if (media != null) {
                styleMediaBubble(media, item, avatar, context);
            } else if (card != null) {
                styleRichCard(card, item, null, avatar, context);
            }
            return;
        }
        if (containsDiagnosticMedia(item, avatar, 0)) {
            View media = findMediaBubbleContainer(item, avatar, context);
            if (media != null) {
                logRuntimeDetection(item, avatar, target, null, media,
                        "media-hit", context, false);
                styleMediaBubble(media, item, avatar, context);
                return;
            }
            View richCard = findRichCardObject(item, avatar, target, context);
            if (richCard != null) {
                logRuntimeDetection(item, avatar, target, null, richCard,
                        "rich-hit", context, false);
                styleRichCard(richCard, item, target, avatar, context);
                return;
            }
        }
        View parent = target.getParent() instanceof View
                ? (View) target.getParent() : null;
        View bubble = parent != null && parent != item ? parent : null;
        if (bubble == null) {
            bubble = item.findViewById(ID_BKJ);
        }
        String simpleFailure = bubble == null ? "bubble-missing"
                : bubble == item ? "bubble-is-row"
                : simpleBubbleFailure(item, bubble, target, avatar);
        if (simpleFailure != null) {
            View card = findRichCardObject(item, avatar, target, context);
            logRuntimeDetection(item, avatar, target, bubble, card,
                    card == null ? "miss:" + simpleFailure
                            : "rich-hit;simple-reject:" + simpleFailure,
                    context, card == null);
            if (card != null) {
                styleRichCard(card, item, target, avatar, context);
            }
            return;
        }
        logRuntimeDetection(item, avatar, target, bubble, null,
                containsDiagnosticMedia(item, avatar, 0)
                        ? "simple-hit;media-present" : "simple-hit",
                context, containsDiagnosticMedia(item, avatar, 0));

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
        M3ColorScheme scheme = M3ColorScheme.forContext(context, true);
        int color = scheme.primary;
        int textColor = scheme.onPrimary;

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
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            bubble.setBackgroundTintList(null);
        }
        bubble.invalidate();
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
        logAppliedSurface("text", bubble);
    }

    /**
     * Emit bounded, text-free diagnostics for the live message objects. The
     * poller runs every 600 ms, so identical view trees are logged only once.
     */
    private static void logRuntimeDetection(View item, View avatar, View target,
                                            View bubble, View card, String branch,
                                            Context context, boolean explainCards) {
        ArrayList<String> views = new ArrayList<>();
        collectDiagnosticViews(item, avatar, target, bubble, card, 0, views);
        String summary = "branch=" + branch
                + " item=" + objectName(item)
                + " avatar=" + objectName(avatar)
                + " target=" + objectName(target)
                + " bubble=" + objectName(bubble)
                + " card=" + objectName(card)
                + " textCount=" + countVisibleTextObjects(item, target, 0)
                + " imageCount=" + countVisibleContentImages(item, avatar, 0)
                + " interactive=" + hasInteractiveContent(item, item, target, 0)
                + " surface=" + hasBackgroundSurface(item, avatar, 0);
        String signature = summary + views.toString();
        if (signature.equals(DIAGNOSTIC_SNAPSHOTS.get(item))) {
            return;
        }
        DIAGNOSTIC_SNAPSHOTS.put(item, signature);
        if (diagnosticItemsLogged >= MAX_DIAGNOSTIC_ITEMS) {
            return;
        }
        diagnosticItemsLogged++;
        android.util.Log.i("WeChatGlass", "CB detect " + summary);
        for (int i = 0; i < views.size(); i++) {
            android.util.Log.i("WeChatGlass", "CB object " + i + " " + views.get(i));
        }
        if (explainCards) {
            int[] logged = {0};
            logCardCandidateReasons(item, item, avatar, target, context, 0, logged);
        }
    }

    private static void collectDiagnosticViews(View view, View avatar, View target,
                                              View bubble, View card, int depth,
                                              ArrayList<String> out) {
        if (view == null || depth > 10 || out.size() >= MAX_DIAGNOSTIC_VIEWS) {
            return;
        }
        String background = view.getBackground() == null ? "-"
                : view.getBackground().getClass().getSimpleName();
        String foreground = android.os.Build.VERSION.SDK_INT < 23
                || view.getForeground() == null ? "-"
                : view.getForeground().getClass().getSimpleName();
        int children = view instanceof ViewGroup ? ((ViewGroup) view).getChildCount() : 0;
        String role = (view == avatar ? "A" : "")
                + (view == target ? "T" : "")
                + (view == bubble ? "B" : "")
                + (view == card ? "C" : "");
        out.add("depth=" + depth + " class=" + view.getClass().getName()
                + " object=" + Integer.toHexString(System.identityHashCode(view))
                + " id=0x" + Integer.toHexString(view.getId())
                + " at=" + view.getLeft() + "," + view.getTop()
                + " size=" + view.getWidth() + "x" + view.getHeight()
                + " bg=" + background + " fg=" + foreground
                + " visible=" + (view.getVisibility() == View.VISIBLE)
                + " click=" + view.isClickable()
                + " longClick=" + view.isLongClickable()
                + " children=" + children + " role=" + role);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount()
                    && out.size() < MAX_DIAGNOSTIC_VIEWS; i++) {
                collectDiagnosticViews(group.getChildAt(i), avatar, target, bubble,
                        card, depth + 1, out);
            }
        }
    }

    private static void logCardCandidateReasons(View item, View view, View avatar,
                                                View target, Context context,
                                                int depth, int[] logged) {
        if (view == null || depth > 18 || logged[0] >= MAX_DIAGNOSTIC_CANDIDATES
                || view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0f) {
            return;
        }
        if (view != item && view != avatar && view instanceof ViewGroup
                && view.getWidth() >= M3Theme.dp(context, 48f)
                && view.getHeight() >= M3Theme.dp(context, 24f)) {
            int textCount = countVisibleTextObjects(view, target, 0);
            int imageCount = countVisibleContentImages(view, avatar, 0);
            boolean interactive = view.isClickable() || view.isLongClickable()
                    || hasInteractiveContent(view, view, target, 0);
            boolean background = hasBackgroundSurface(view, avatar, 0);
            boolean targetInside = target == null || isDescendantOf(target, view);
            boolean avatarInside = isDescendantOf(avatar, view);
            String reason = cardCandidateRejection(item, view, target, avatar,
                    context, textCount, imageCount, background,
                    targetInside, avatarInside);
            android.util.Log.i("WeChatGlass", "CB candidate class="
                    + view.getClass().getName()
                    + " object=" + Integer.toHexString(System.identityHashCode(view))
                    + " id=0x" + Integer.toHexString(view.getId())
                    + " size=" + view.getWidth() + "x" + view.getHeight()
                    + " text=" + textCount + " image=" + imageCount
                    + " interactive=" + interactive + " surface=" + background
                    + " targetInside=" + targetInside
                    + " avatarInside=" + avatarInside + " result=" + reason);
            logged[0]++;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount()
                    && logged[0] < MAX_DIAGNOSTIC_CANDIDATES; i++) {
                logCardCandidateReasons(item, group.getChildAt(i), avatar, target,
                        context, depth + 1, logged);
            }
        }
    }

    private static String cardCandidateRejection(View item, View view, View target,
                                                 View avatar, Context context,
                                                 int textCount, int imageCount,
                                                 boolean background,
                                                 boolean targetInside,
                                                 boolean avatarInside) {
        if (avatarInside) return "contains-avatar";
        if (view.getWidth() < M3Theme.dp(context, 88f)
                || view.getHeight() < M3Theme.dp(context, 36f)) {
            return "too-small";
        }
        if (view.getWidth() > item.getWidth() * 96 / 100
                || view.getHeight() > item.getHeight() * 98 / 100) {
            return "too-large";
        }
        boolean cardContent = textCount >= 2;
        if (!cardContent) return "insufficient-content";
        if (imageCount > 2) return "too-many-images";
        if (!background) return "no-background";
        return "would-match";
    }

    private static String objectName(View view) {
        return view == null ? "-" : view.getClass().getName() + "#"
                + Integer.toHexString(System.identityHashCode(view)) + "/"
                + view.getWidth() + "x" + view.getHeight();
    }

    private static boolean containsDiagnosticMedia(View view, View avatar, int depth) {
        if (view == null || view == avatar || isDescendantOf(view, avatar) || depth > 18
                || view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0f) {
            return false;
        }
        if (view.getWidth() > 0 && view.getHeight() > 0
                && (view instanceof android.widget.ImageView
                || view instanceof android.view.SurfaceView
                || view instanceof android.view.TextureView
                || isStickerView(view))) {
            return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsDiagnosticMedia(group.getChildAt(i), avatar, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static View findMediaBubbleContainer(View item, View avatar,
                                                Context context) {
        View[] media = {null};
        long[] largestArea = {0L};
        findLargestMediaVisual(item, avatar, context, 0, media, largestArea);
        View visual = media[0];
        if (visual == null) {
            return null;
        }

        View best = null;
        int bestExtra = Integer.MAX_VALUE;
        View current = visual.getParent() instanceof View
                ? (View) visual.getParent() : null;
        while (current != null && current != item) {
            if (current instanceof ViewGroup && current != avatar
                    && !isDescendantOf(avatar, current)
                    && current.getWidth() >= visual.getWidth()
                    && current.getHeight() >= visual.getHeight()
                    && current.getWidth() <= item.getWidth() * 96 / 100
                    && current.getHeight() <= item.getHeight() * 98 / 100
                    && current.getWidth() - visual.getWidth()
                    <= M3Theme.dp(context, 120f)
                    && current.getHeight() - visual.getHeight()
                    <= M3Theme.dp(context, 120f)) {
                int extra = current.getWidth() - visual.getWidth()
                        + current.getHeight() - visual.getHeight();
                if (extra < bestExtra) {
                    best = current;
                    bestExtra = extra;
                }
            }
            current = current.getParent() instanceof View
                    ? (View) current.getParent() : null;
        }
        return best != null ? best : visual;
    }

    private static void findLargestMediaVisual(View view, View avatar,
                                               Context context, int depth,
                                               View[] best, long[] bestArea) {
        if (view == null || view == avatar || isDescendantOf(view, avatar) || depth > 20
                || view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0f) {
            return;
        }
        int width = view.getWidth();
        int height = view.getHeight();
        boolean mediaVisual = false;
        if (width > 0 && height > 0) {
            if (view instanceof android.widget.ImageView) {
                mediaVisual = width >= M3Theme.dp(context, 72f)
                        && height >= M3Theme.dp(context, 56f);
            } else if (view instanceof android.view.SurfaceView
                    || view instanceof android.view.TextureView) {
                mediaVisual = width >= M3Theme.dp(context, 72f)
                        && height >= M3Theme.dp(context, 56f);
            } else if (isStickerView(view)) {
                mediaVisual = true;
            } else if (!(view instanceof ViewGroup) && !(view instanceof TextView)
                    && width >= M3Theme.dp(context, 120f)
                    && height >= M3Theme.dp(context, 72f)) {
                mediaVisual = true;
            }
        }
        if (mediaVisual) {
            long area = (long) width * height;
            if (area > bestArea[0]) {
                best[0] = view;
                bestArea[0] = area;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                findLargestMediaVisual(group.getChildAt(i), avatar, context,
                        depth + 1, best, bestArea);
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
        boolean outlineChanged = false;
        if (avatar.getOutlineProvider() != CIRCLE_OUTLINE) {
            remember(avatar);
            avatar.setOutlineProvider(CIRCLE_OUTLINE);
            outlineChanged = true;
        }
        if (!avatar.getClipToOutline()) {
            remember(avatar);
            avatar.setClipToOutline(true);
            outlineChanged = true;
        }
        if (outlineChanged) {
            avatar.invalidateOutline();
        }
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

    /** Round rendered stickers in a visible message or sticker grid. */
    static void roundStickerPanel(View view, Context context) {
        roundStickers(view, context, 0, true);
    }

    private static void roundStickers(View view, Context context, int depth) {
        roundStickers(view, context, depth, false);
    }

    private static void roundStickers(View view, Context context, int depth,
                                      boolean panelThumbnails) {
        if (view == null || depth > 16) {
            return;
        }
        boolean panelThumbnail = panelThumbnails
                && isPanelStickerThumbnail(view, context);
        if (isStickerView(view) || panelThumbnail) {
            final float radius = M3Theme.dpF(context, panelThumbnail ? 12f : 20f);
            boolean firstClip = ROUNDED_STICKERS.put(view, Boolean.TRUE) == null;
            android.view.ViewOutlineProvider stickerOutline = STICKER_OUTLINES.get(view);
            if (stickerOutline == null) {
                stickerOutline = new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(View target, android.graphics.Outline outline) {
                        outline.setRoundRect(0, 0, target.getWidth(), target.getHeight(), radius);
                    }
                };
                STICKER_OUTLINES.put(view, stickerOutline);
            }
            boolean outlineChanged = false;
            if (view.getOutlineProvider() != stickerOutline) {
                remember(view);
                view.setOutlineProvider(stickerOutline);
                outlineChanged = true;
            }
            if (!view.getClipToOutline()) {
                remember(view);
                view.setClipToOutline(true);
                outlineChanged = true;
            }
            if (outlineChanged) {
                view.invalidateOutline();
                view.invalidate();
            }
            if (firstClip) {
                android.util.Log.i("WeChatGlass", "CB rounded sticker "
                        + (panelThumbnails ? "panel " : "message ")
                        + view.getClass().getName() + " " + view.getWidth()
                        + "x" + view.getHeight() + " clip=" + view.getClipToOutline());
            }
            if (view instanceof android.widget.ImageView) {
                android.widget.ImageView image = (android.widget.ImageView) view;
                android.graphics.drawable.Drawable drawable = image.getDrawable();
                boolean alreadyRounded = drawable instanceof RoundedRectClipDrawable
                        && ((RoundedRectClipDrawable) drawable).hasRadius(radius);
                if (drawable != null && !alreadyRounded) {
                    if (drawable instanceof RoundedRectClipDrawable) {
                        drawable = ((RoundedRectClipDrawable) drawable).getWrappedDrawable();
                    }
                    remember(view);
                    image.setImageDrawable(new RoundedRectClipDrawable(drawable, radius));
                    android.util.Log.i("WeChatGlass", "CB sticker drawable clip "
                            + (panelThumbnails ? "panel " : "message ")
                            + view.getClass().getName() + " " + view.getWidth()
                            + "x" + view.getHeight());
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                roundStickers(group.getChildAt(i), context, depth + 1,
                        panelThumbnails);
            }
        }
    }

    private static boolean isPanelStickerThumbnail(View view, Context context) {
        if (!(view instanceof android.widget.ImageView)
                || view.getVisibility() != View.VISIBLE
                || ((android.widget.ImageView) view).getDrawable() == null) {
            return false;
        }
        int minSide = M3Theme.dp(context, 44f);
        if (view.getWidth() < minSide || view.getHeight() < minSide) {
            return false;
        }
        float aspect = view.getWidth() / (float) view.getHeight();
        return aspect >= 0.62f && aspect <= 1.62f;
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
        return loc[0] + avatar.getWidth() / 2 > screen / 2;
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
                && view.getWidth() > 0 && view.getHeight() > 0
                && !view.isClickable() && !view.isLongClickable()) {
            int score = view.getWidth() * view.getHeight() * 2;
            if (score > bestScore) {
                best = view;
                bestScore = score;
            }
        }
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            CharSequence cs = tv.getText();
            if (cs != null && cs.length() > 0
                    && tv.getVisibility() == View.VISIBLE
                    && tv.getWidth() > 0 && tv.getHeight() > 0
                    && !tv.isClickable() && !tv.isLongClickable()
                    && !isChromeText(cs.toString())) {
                int score = tv.getWidth() * Math.max(1, cs.length());
                if (score > bestScore) {
                    best = tv;
                    bestScore = score;
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findBubbleTarget(group.getChildAt(i), best, bestScore);
                if (found != null) {
                    best = found;
                    bestScore = bubbleTargetScore(found);
                }
            }
        }
        return best;
    }

    private static int bubbleTargetScore(View view) {
        if (view == null) {
            return 0;
        }
        String name = view.getClass().getName();
        if (name.contains("MMNeat7extView") || name.contains("NeatTextView")) {
            return view.getWidth() * view.getHeight() * 2;
        }
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            return view.getWidth() * Math.max(1, text == null ? 1 : text.length());
        }
        return view.getWidth() * view.getHeight();
    }

    /**
     * Only reshape a compact, simple text/call bubble. Rich message cards such
     * as transfers and red packets expose several text/image/action objects;
     * leave those objects and their ancestors entirely native.
     */
    private static String simpleBubbleFailure(View item, View bubble,
                                              View target, View avatar) {
        if (!isDescendantOf(target, bubble) || bubble.getWidth() <= 0
                || bubble.getHeight() <= 0) {
            return "target-not-in-sized-bubble";
        }
        int extra = dp(48f);
        if (bubble.getWidth() > target.getWidth() + dp(96f)
                || bubble.getHeight() > target.getHeight() + extra) {
            return "bubble-larger-than-target";
        }
        int textCount = countVisibleTextObjects(bubble, target, 0);
        if (textCount != 1) {
            return "bubble-text-count-" + textCount;
        }
        if (hasInteractiveContent(bubble, bubble, target, 0)) {
            return "nested-interactive-content";
        }

        View ancestor = bubble.getParent() instanceof View
                ? (View) bubble.getParent() : null;
        int depth = 0;
        while (ancestor != null && ancestor != item && depth++ < 8) {
            if (countVisibleTextObjects(ancestor, target, 0) > 2
                    || countVisibleContentImages(ancestor, avatar, 0) > 1) {
                return "ancestor-has-rich-content";
            }
            ancestor = ancestor.getParent() instanceof View
                    ? (View) ancestor.getParent() : null;
        }
        return null;
    }

    private static View findRichCardObject(View item, View avatar, View target,
                                          Context context) {
        return findRichCardObject(item, item, avatar, target, context, 0);
    }

    private static View findRichCardObject(View item, View view, View avatar,
                                          View target, Context context, int depth) {
        if (view == null || view == avatar || depth > 18
                || view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0f) {
            return null;
        }
        boolean cardCandidate = view != item && view instanceof ViewGroup
                && !isDescendantOf(avatar, view)
                && view.getWidth() >= M3Theme.dp(context, 88f)
                && view.getHeight() >= M3Theme.dp(context, 36f)
                && view.getWidth() <= item.getWidth() * 96 / 100
                && view.getHeight() <= item.getHeight() * 98 / 100;
        if (cardCandidate) {
            int textCount = countVisibleTextObjects(view, target, 0);
            int imageCount = countVisibleContentImages(view, avatar, 0);
            boolean cardContent = textCount >= 2;
            cardCandidate = cardContent && imageCount <= 2
                    && hasBackgroundSurface(view, avatar, 0);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findRichCardObject(item, group.getChildAt(i), avatar,
                        target, context, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return cardCandidate ? view : null;
    }

    private static boolean hasBackgroundSurface(View view, View avatar, int depth) {
        if (view == null || view == avatar || depth > 16
                || view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0f) {
            return false;
        }
        if (view.getBackground() != null) {
            return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasBackgroundSurface(group.getChildAt(i), avatar, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void styleRichCard(View card, View item, View textTarget,
                                      View avatar, Context context) {
        int screen = context.getResources().getDisplayMetrics().widthPixels;
        boolean outgoing;
        if (avatar != null) {
            outgoing = isOutgoingByAvatar(avatar, screen);
        } else {
            int[] loc = new int[2];
            card.getLocationOnScreen(loc);
            outgoing = loc[0] > screen - (loc[0] + card.getWidth());
        }

        // Transaction/red-packet cards always use the selected dark-palette
        // primary role, regardless of the current page brightness.
        M3ColorScheme cardScheme = M3ColorScheme.forContext(context, true);
        int color = cardScheme.primary;
        int textColor = cardScheme.onPrimary;
        float radius = M3Theme.dpF(context, M3Shape.LARGE_INCREASED);
        float tail = M3Theme.dpF(context, 7f);
        BubbleDrawable shape = new BubbleDrawable(color, radius, tail, outgoing);
        BubbleDrawable previous = APPLIED.get(card);
        boolean alreadyApplied = previous != null && card.getBackground() == previous
                && previous.matches(color, radius, tail, outgoing);
        if (!alreadyApplied) {
            remember(card);
            card.setBackground(shape);
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                card.setBackgroundTintList(null);
            }
            APPLIED.put(card, shape);
            ViewState original = ORIGINAL.get(card);
            int inset = M3Theme.dp(context, 8f);
            if (original != null && original.paddingRelative) {
                card.setPaddingRelative(original.paddingStart + inset,
                        original.paddingTop + inset, original.paddingEnd + inset,
                        original.paddingBottom + inset);
            } else if (original != null) {
                card.setPadding(original.paddingLeft + inset,
                        original.paddingTop + inset, original.paddingRight + inset,
                        original.paddingBottom + inset);
            }
            if (card instanceof ViewGroup) {
                ((ViewGroup) card).setClipChildren(false);
            }
            if (card.getParent() instanceof ViewGroup) {
                ViewGroup parent = (ViewGroup) card.getParent();
                remember(parent);
                parent.setClipChildren(false);
            }
            card.requestLayout();
        }
        applyCardTheme(card, card, avatar, textTarget, color, textColor, 0);
        logAppliedSurface("card", card);
    }

    private static void styleMediaBubble(View media, View item, View avatar,
                                         Context context) {
        int screen = context.getResources().getDisplayMetrics().widthPixels;
        boolean outgoing;
        if (avatar != null) {
            outgoing = isOutgoingByAvatar(avatar, screen);
        } else {
            int[] loc = new int[2];
            media.getLocationOnScreen(loc);
            outgoing = loc[0] > screen - (loc[0] + media.getWidth());
        }
        M3ColorScheme scheme = M3ColorScheme.forContext(context, true);
        int color = scheme.primary;
        float radius = M3Theme.dpF(context, M3Shape.LARGE_INCREASED);
        float tail = M3Theme.dpF(context, 7f);
        BubbleDrawable shape = new BubbleDrawable(color, radius, tail, outgoing);
        BubbleDrawable previous = APPLIED.get(media);
        if (previous != null && media.getBackground() == previous
                && previous.matches(color, radius, tail, outgoing)) {
            return;
        }
        remember(media);
        media.setBackground(shape);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            media.setBackgroundTintList(null);
        }
        media.invalidate();
        APPLIED.put(media, shape);
        ViewState original = ORIGINAL.get(media);
        int inset = M3Theme.dp(context, 8f);
        if (original != null && original.paddingRelative) {
            media.setPaddingRelative(original.paddingStart + inset,
                    original.paddingTop + inset, original.paddingEnd + inset,
                    original.paddingBottom + inset);
        } else if (original != null) {
            media.setPadding(original.paddingLeft + inset,
                    original.paddingTop + inset, original.paddingRight + inset,
                    original.paddingBottom + inset);
        }
        if (media instanceof ViewGroup) {
            ((ViewGroup) media).setClipChildren(false);
        }
        if (media.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) media.getParent();
            remember(parent);
            parent.setClipChildren(false);
        }
        media.requestLayout();
        logAppliedSurface("media", media);
    }

    private static void logAppliedSurface(String kind, View view) {
        if (view == null) {
            return;
        }
        android.graphics.drawable.Drawable background = view.getBackground();
        android.graphics.drawable.Drawable foreground = android.os.Build.VERSION.SDK_INT < 23
                ? null : view.getForeground();
        String childState = "-";
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            StringBuilder children = new StringBuilder();
            for (int i = 0; i < group.getChildCount() && i < 8; i++) {
                View child = group.getChildAt(i);
                if (i > 0) children.append('|');
                android.graphics.drawable.Drawable childBg = child.getBackground();
                children.append(child.getClass().getSimpleName())
                        .append('@').append(child.getWidth()).append('x')
                        .append(child.getHeight()).append("/bg=")
                        .append(childBg == null ? "-" : childBg.getClass().getSimpleName())
                        .append("/vis=").append(child.getVisibility());
            }
            childState = children.toString();
        }
        String signature = kind + ":bg="
                + (background == null ? "-" : background.getClass().getSimpleName()
                + "@" + Integer.toHexString(System.identityHashCode(background)))
                + ",color=" + (background instanceof BubbleDrawable
                ? "#" + Integer.toHexString(((BubbleDrawable) background).fillColor()) : "-")
                + ",bounds=" + (background == null ? "-" : background.getBounds().toShortString())
                + ",fg=" + (foreground == null ? "-"
                : foreground.getClass().getSimpleName())
                + ",tint=" + (android.os.Build.VERSION.SDK_INT < 21
                || view.getBackgroundTintList() == null ? "-" : "set")
                + ",clip=" + view.getClipToOutline()
                + ",attached=" + view.isAttachedToWindow()
                + ",size=" + view.getWidth() + "x" + view.getHeight()
                + ",children=" + childState;
        if (signature.equals(APPLIED_DIAGNOSTICS.get(view))) {
            return;
        }
        APPLIED_DIAGNOSTICS.put(view, signature);
        android.util.Log.i("WeChatGlass", "CB applied " + signature);
    }

    private static void applyCardTheme(View view, View root, View avatar,
                                       View textTarget, int backgroundColor,
                                       int textColor, int depth) {
        if (view == null || view == avatar || depth > 18
                || view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0f) {
            return;
        }
        if (view != root && view.getBackground() != null
                && android.os.Build.VERSION.SDK_INT >= 21) {
            android.content.res.ColorStateList tint = view.getBackgroundTintList();
            if (tint == null || tint.getDefaultColor() != backgroundColor) {
                remember(view);
                view.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(backgroundColor));
            }
        }
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (text.getCurrentTextColor() != textColor) {
                remember(text);
                text.setTextColor(textColor);
            }
        } else if (view == textTarget) {
            remember(view);
            ViewState original = ORIGINAL.get(view);
            if (original != null && original.reflectedTextColor != null) {
                try {
                    view.getClass().getMethod("setTextColor", int.class)
                            .invoke(view, textColor);
                } catch (Throwable ignored) {
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyCardTheme(group.getChildAt(i), root, avatar, textTarget,
                        backgroundColor, textColor, depth + 1);
            }
        }
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
        if (view == null || view == avatar || isDescendantOf(view, avatar) || depth > 16
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
