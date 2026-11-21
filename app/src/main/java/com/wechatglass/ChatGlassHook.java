package com.wechatglass;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-ChattingUI controller.
 *
 * The chat is restructured into a real edge-to-edge screen:
 *   - ActionBar is moved out of the vertical layout and overlaid on the chat
 *     content frame.
 *   - ChatFooter is kept in ChattingScrollLayout but its top margin is made
 *     negative by its own height, so the message list gets the full height and
 *     the footer draws on top of it.
 *   - The RecyclerView gets top/bottom padding so messages do not hide behind
 *     either floating bar.
 *
 * Only view backgrounds, padding, margins and drawing are touched. No message,
 * network, account or payment classes are referenced.
 */
final class ChatGlassHook {

    private static final String TAG = "WeChatGlass";

    // Resource ids from WeChat 8.0.79. Used as hints; every lookup is validated
    // by view class before use.
    private static final int ID_ACTION_BAR_CONTAINER = 0x7f0a00d3;
    private static final int ID_ACTION_BAR_ROOT = 0x7f0a00d8;
    private static final int ID_BACK = 0x7f0a0157;
    private static final int ID_BACK_BTN = 0x7f0a0158;
    private static final int ID_TITLE = 0x7f0a6b98;
    private static final int ID_MENU = 0x7f0a184a;
    private static final int ID_MENU_INNER = 0x7f0a1849;
    private static final int ID_FOOTER = 0x7f0a4d21;
    private static final int ID_FOOTER_BOTTOM = 0x7f0a0f7e;
    private static final int ID_FOOTER_PANEL = 0x7f0a0f08;

    private static final Map<Activity, ChatGlassHook> INSTANCES = new WeakHashMap<>();

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int tint;
    private final int border;
    private final int chipTint;
    private final int chipBorder;

    private GlassRenderer renderer;
    private View decor;
    private View actionBar;
    private View toolbar;
    private View backView;
    private View titleView;
    private View menuView;
    private View footer;
    private View footerPanel;
    private View footerBottom;

    private View chatRoot;
    private View navFiller;
    private View statusScrim;
    private int normalPanelBaseHeight = -1;
    private int normalPanelParentBaseHeight = -1;
    private boolean normalPanelShrunk;
    private ViewGroup contentFrame;
    private View scrollLayout;
    private View list;

    private int listBaseLeft;
    private int listBaseTop;
    private int listBaseRight;
    private int listBaseBottom;
    private boolean listBaseSaved;
    private int appliedTopPad = -1;
    private int appliedBottomPad = -1;

    private GlassChipView backChip;
    private GlassChipView titleChip;
    private GlassChipView menuChip;

    private boolean attached;
    private boolean supportActionBar;
    private int attempts;
    private int lastFooterHeight = -1;
    private int footerOriginalBottomMargin = Integer.MIN_VALUE;
    private int lastVisibleFooterHeight = -1;
    private boolean supportActionBarOverlaySet;

    // Original host of the reparented ActionBar, so destroy() can put it back.
    private ViewGroup actionBarOriginalParent;
    private int actionBarOriginalIndex = -1;
    private ViewGroup.LayoutParams actionBarOriginalParams;

    private ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener;
    private ViewTreeObserver.OnScrollChangedListener scrollListener;
    private long lastGeometrySignature = Long.MIN_VALUE;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!attached) {
                return;
            }
            // Footer geometry depends on WeChat's own re-measure; keep the
            // 600ms refresh here so the input bar never drifts off-screen.
            applyEdgeLayout();
            updateTopGeometry();
            handler.postDelayed(this, 600L);
        }
    };

    private void maybeUpdateGeometry() {
        long signature = geometrySignature();
        if (signature == lastGeometrySignature) {
            return;
        }
        lastGeometrySignature = signature;
        applyEdgeLayout();
        updateTopGeometry();
    }

    private long geometrySignature() {
        int decorWidth = decor == null ? 0 : decor.getWidth();
        int decorHeight = decor == null ? 0 : decor.getHeight();
        int barWidth = actionBar == null ? 0 : actionBar.getWidth();
        int barHeight = actionBar == null ? 0 : actionBar.getHeight();
        int barTop = actionBar == null ? 0 : actionBar.getTop();
        int footerHeight = footer == null ? 0 : footer.getHeight();
        int listPadding = list == null ? 0 : list.getPaddingTop();
        long signature = decorWidth;
        signature = signature * 31 + decorHeight;
        signature = signature * 31 + barWidth;
        signature = signature * 31 + barHeight;
        signature = signature * 31 + barTop;
        signature = signature * 31 + footerHeight;
        signature = signature * 31 + listPadding;
        return signature;
    }

    private ChatGlassHook(Activity activity) {
        this.activity = activity;
        boolean night = Ui.isNight(activity);
        // Solid WeChat white / dark grey. Opaque on purpose: the blur path is
        // gone, so every surface is a flat fill instead of a translucent tint.
        this.tint = night ? 0xFF2C2C2C : 0xFFFFFFFF;
        this.border = night ? 0x1AFFFFFF : 0x1A000000;
        this.chipTint = this.tint;
        this.chipBorder = this.border;
    }

    static void attach(Activity activity) {
        if (activity == null) {
            return;
        }
        ChatGlassHook existing = INSTANCES.get(activity);
        if (existing != null) {
            existing.destroy();
        }
        ChatGlassHook hook = new ChatGlassHook(activity);
        INSTANCES.put(activity, hook);
        hook.install();
    }

    static void detach(Activity activity) {
        ChatGlassHook hook = INSTANCES.remove(activity);
        if (hook != null) {
            hook.destroy();
        }
    }

    static void reapplyStatusBar(Activity activity) {
        ChatGlassHook hook = INSTANCES.get(activity);
        if (hook != null) {
            hook.applyStatusBar();
        }
    }

    private void install() {
        decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        if (decor == null) {
            return;
        }
        decor.post(new Runnable() {
            @Override
            public void run() {
                if (!tryInstall()) {
                    attempts++;
                    if (attempts < 20) {
                        decor.postDelayed(this, 150L);
                    } else {
                        Log.w(TAG, "chat glass: views not found, give up");
                    }
                }
            }
        });
    }

    private boolean tryInstall() {
        if (attached) {
            return true;
        }
        if (decor == null || !decor.isAttachedToWindow()) {
            Log.i(TAG, "chat tryInstall decor attached=" + (decor != null && decor.isAttachedToWindow()));
            return false;
        }

        View f = decor.findViewById(ID_FOOTER);
        if (f == null || !f.getClass().getName().contains("ChatFooter")) {
            f = Ui.findByClassContains(decor, "ChatFooter");
        }
        View scroll = Ui.findByClass(decor,
                "com.tencent.mm.pluginsdk.ui.chat.ChattingScrollLayout");
        if (scroll == null) {
            scroll = Ui.findByClassContains(decor, "ChattingScrollLayout");
        }
        if (f == null || scroll == null) {
            Log.i(TAG, "chat tryInstall footer=" + f + " scroll=" + scroll);
            return false;
        }

        ViewParent contentParent = scroll.getParent();
        if (!(contentParent instanceof ViewGroup)) {
            Log.i(TAG, "chat tryInstall contentParent=" + contentParent);
            return false;
        }
        ViewGroup content = (ViewGroup) contentParent;
        ViewParent rootParent = content.getParent();
        if (!(rootParent instanceof ViewGroup)) {
            Log.i(TAG, "chat tryInstall rootParent=" + rootParent);
            return false;
        }

        chatRoot = (View) rootParent;
        clearChatBackgrounds();
        scrollLayout = scroll;
        contentFrame = content;
        footer = f;
        applyStatusBar();

        // Only the chat's own ActionBar may be moved. The Activity's AppCompat
        // ActionBar lives inside ActionBarOverlayLayout and must stay where it
        // is, otherwise ActionBarOverlayLayout.onMeasure() throws
        // ClassCastException (FrameLayout.LayoutParams -> its own params).
        View ab = chatRoot.findViewById(ID_ACTION_BAR_CONTAINER);
        if (ab == null || !ab.getClass().getName().contains("ActionBarContainer")) {
            ab = Ui.findByClassContains(chatRoot, "ActionBarContainer");
        }
        if (ab == null) {
            // No ActionBar inside the chat layout: WeChat is using the
            // Activity's support ActionBar. Use it for styling/chips, but never
            // move it (ActionBarOverlayLayout owns it).
            ab = decor.findViewById(ID_ACTION_BAR_CONTAINER);
            if (ab == null || !ab.getClass().getName().contains("ActionBarContainer")) {
                ab = Ui.findByClass(decor, "androidx.appcompat.widget.ActionBarContainer");
            }
            if (ab != null) {
                supportActionBar = true;
            }
        }
        actionBar = ab;
        Log.i(TAG, "actionBar=" + (ab == null ? "null" : ab.getClass().getName())
                + " parent=" + (ab == null || ab.getParent() == null ? "null"
                : ab.getParent().getClass().getName())
                + " support=" + supportActionBar
                + " h=" + (ab == null ? -1 : ab.getHeight()));
        toolbar = ab == null ? null : Ui.findByClass(ab, "androidx.appcompat.widget.Toolbar");
        dumpTopBars(decor, 0);
        if (toolbar == null) {
            toolbar = ab;
        }

        HookConfig.installReceiver(activity);
        renderer = new GlassRenderer(decor);
        renderer.setHiddenViews(actionBar, footer);
        renderer.setGlassEnabled(HookConfig.glassEnabled(activity));
        // The footer lives inside ChattingScrollLayout, so using that as the
        // backdrop would record the footer's own glass and recurse. Use the
        // content list instead, which never contains our surfaces.
        View chatContent = Ui.findByClassContains(scrollLayout, "ChattingContent");
        if (chatContent instanceof ViewGroup) {
            renderer.setBackdrop((ViewGroup) chatContent);
        }
        renderer.addClient(footer);

        if (actionBar != null) {
            setupTopBar();
            renderer.addClient(backChip);
            renderer.addClient(titleChip);
            renderer.addClient(menuChip);
        }
        setupFooter();
        if (footer != null && scrollLayout != null
                && HookConfig.glassEnabled(activity)) {
            ViewGroup page = null;
            for (int i = 0; i < ((ViewGroup) scrollLayout).getChildCount(); i++) {
                View ch = ((ViewGroup) scrollLayout).getChildAt(i);
                if (ch.getClass().getName().contains("ChattingContent") && ch instanceof ViewGroup) {
                    int[] l = new int[2];
                    ch.getLocationOnScreen(l);
                    if (Math.abs(l[0]) < 10) { page = (ViewGroup) ch; break; }
                }
            }
            if (page != null) {
                View mr = Ui.findByClassContains(page, "RecyclerView");
                if (mr instanceof ViewGroup) { page = (ViewGroup) mr; }
                View bar = Ui.findByClassContains(footer, "ChatFooterBottom");
                footer.setBackground(new GlassFooterDrawable(bar != null ? bar : footer, page));
            }
        }
        setupList();
        applyEdgeLayout();

        ViewTreeObserver observer = decor.getViewTreeObserver();
        globalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (!attached) {
                    return;
                }
                applyEdgeLayout();
            }
        };
        observer.addOnGlobalLayoutListener(globalLayoutListener);

        scrollListener = new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                if (renderer != null) {
                    renderer.requestUpdate();
                }
            }
        };
        scrollLayout.getViewTreeObserver().addOnScrollChangedListener(scrollListener);
        attached = true;
        updateTopGeometry();
        lastGeometrySignature = geometrySignature();
        handler.postDelayed(tick, 600L);
        renderer.requestUpdate();
        Log.i(TAG, "chat glass attached: content=" + contentFrame.getClass().getName()
                + " scroll=" + scrollLayout.getClass().getName());
        return true;
    }

    private void limitPanelHeight() {
        if (footerPanel == null) return;
        int target = Ui.dp(activity, 170f);
        ViewGroup.LayoutParams lp = footerPanel.getLayoutParams();
        if (lp != null && lp.height != target) {
            lp.height = target;
            footerPanel.setLayoutParams(lp);
        }
        try {
            java.lang.reflect.Field field = footerPanel.getClass()
                    .getDeclaredField("p0");
            field.setAccessible(true);
            field.setInt(footerPanel, target);
            footerPanel.requestLayout();
        } catch (Throwable ignored) {
        }
    }

    private void clearChatBackgrounds() {
        String[] names = {
                "com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout",
                "com.tencent.mm.pluginsdk.ui.chat.ChattingScrollLayout",
                "com.tencent.mm.pluginsdk.ui.chat.ChattingContent"
        };
        for (String name : names) {
            View view = Ui.findByClass(decor, name);
            if (view != null) {
                view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
        }
    }

    private void dumpTopBars(View view, int depth) {
        if (view == null) return;
        String n = view.getClass().getName();
        if (n.contains("ActionBar") || n.contains("Toolbar") || n.contains("Title")) {
            int[] loc = new int[2];
            view.getLocationInWindow(loc);
            Log.i(TAG, "topbar depth=" + depth + " " + n
                    + " vis=" + view.getVisibility() + " h=" + view.getHeight()
                    + " y=" + loc[1] + " a=" + view.getAlpha()
                    + " parent=" + (view.getParent() == null ? "null"
                    : view.getParent().getClass().getName()));
        }
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) dumpTopBars(g.getChildAt(i), depth + 1);
        }
    }

    private void extendChatToBottom() {
        if (decor == null) return;
        View wrapper = Ui.findByClass(decor,
                "com.tencent.mm.ui.widget.EdgeToEdgeWrapperLayout");
        if (wrapper != null) {
            wrapper.setPadding(wrapper.getPaddingLeft(), 0,
                    wrapper.getPaddingRight(), 0);
        }
        if (navFiller != null) {
            navFiller.setVisibility(View.GONE);
        }
        if (chatRoot != null && chatRoot.getLayoutParams() != null) {
            ViewGroup.LayoutParams lp = chatRoot.getLayoutParams();
            if (lp.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                chatRoot.setLayoutParams(lp);
                chatRoot.requestLayout();
            }
        }
    }

    private void applyStatusBar() {
        try {
            // Status bar is transparent; WeChat's own edge-to-edge owns insets.
            activity.getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        } catch (Throwable throwable) {
            Log.w(TAG, "status bar color failed", throwable);
        }
    }

    private void setupTopBar() {
        Ui.clearActionBarBackground(actionBar);
        Ui.transparent(toolbar);
        Ui.transparent(actionBar.findViewById(ID_ACTION_BAR_ROOT));
        toolbar.setBackground(null);
        actionBar.setBackground(null);

        backView = actionBar.findViewById(ID_BACK);
        if (backView == null) {
            backView = actionBar.findViewById(ID_BACK_BTN);
        }
        titleView = actionBar.findViewById(ID_TITLE);
        menuView = actionBar.findViewById(ID_MENU);
        if (menuView == null || menuView.getWidth() == 0) {
            View innerMenu = actionBar.findViewById(ID_MENU_INNER);
            if (innerMenu != null) {
                menuView = innerMenu;
            }
        }

        if (titleView == null) {
            View text = toolbar.findViewById(android.R.id.title);
            if (text == null) {
                text = toolbar.findViewById(android.R.id.text1);
            }
            if (text != null) {
                ViewGroup parent = text.getParent() instanceof ViewGroup
                        ? (ViewGroup) text.getParent() : null;
                titleView = parent != null ? parent : text;
            }
        }
        if (backView == null) {
            backView = Ui.findByClassContains(actionBar, "WeImageView");
        }
        // WeChat 8.0.79 draws its own white capsule behind the title; it would
        // sit on top of our glass, so hide the native title layer completely.
        if (titleView != null) {
            titleView.setAlpha(0f);
            if (titleView.getParent() instanceof View
                    && ((View) titleView.getParent()).getBackground() != null) {
                ((View) titleView.getParent()).setBackground(null);
            }
        }

        ViewGroup group = (ViewGroup) actionBar;
        int chipHeight = Ui.dp(activity, 44f);
        backChip = new GlassChipView(activity, renderer, chipHeight / 2f, chipTint, chipBorder);
        titleChip = new GlassChipView(activity, renderer, Ui.dp(activity, 22f), chipTint, chipBorder);
        menuChip = new GlassChipView(activity, renderer, chipHeight / 2f, chipTint, chipBorder);

        addChip(group, backChip, chipHeight);
        addChip(group, titleChip, chipHeight);
        addChip(group, menuChip, chipHeight);
        if (scrollLayout instanceof ViewGroup && HookConfig.glassEnabled(activity)) {
            ViewGroup tp = null;
            ViewGroup sl = (ViewGroup) scrollLayout;
            for (int i = 0; i < sl.getChildCount(); i++) {
                View ch = sl.getChildAt(i);
                if (ch.getClass().getName().contains("ChattingContent") && ch instanceof ViewGroup) {
                    int[] l = new int[2];
                    ch.getLocationOnScreen(l);
                    if (Math.abs(l[0]) < 10) { tp = (ViewGroup) ch; break; }
                }
            }
            if (tp != null) {
                View tmr = Ui.findByClassContains(tp, "RecyclerView");
                if (tmr instanceof ViewGroup) { tp = (ViewGroup) tmr; }
                renderer.setLayerMode(true);
                GlassLayerView layer = new GlassLayerView(activity, group);
                group.addView(layer, 0, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, chipHeight));
                layer.setBackdrop(tp);
            }
        }

        View.OnLayoutChangeListener listener = new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                updateTopGeometry();
            }
        };
        actionBar.addOnLayoutChangeListener(listener);
        if (toolbar != actionBar) {
            toolbar.addOnLayoutChangeListener(listener);
        }
        if (backView != null) {
            backView.addOnLayoutChangeListener(listener);
        }
        if (titleView != null) {
            titleView.addOnLayoutChangeListener(listener);
        }
        if (menuView != null) {
            menuView.addOnLayoutChangeListener(listener);
        }
    }

    private void addChip(ViewGroup parent, GlassChipView chip, int height) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(height, height);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        parent.addView(chip, 0, params);
    }

    /**
     * Move the ActionBar out of the vertical chat layout and into the content
     * FrameLayout. Because it is added after ChattingScrollLayout it draws on
     * top, and the list/wallpaper can occupy the full height.
     */
    private void reparentActionBar() {
        if (supportActionBar) {
            return;
        }
        if (actionBar == null || contentFrame == null || chatRoot == null) {
            return;
        }
        if (!isDescendant(actionBar, chatRoot)) {
            return;
        }
        ViewParent parent = actionBar.getParent();
        if (parent == contentFrame) {
            return;
        }
        if (parent != null && parent.getClass().getName().contains("ActionBarOverlayLayout")) {
            // Never pull the support ActionBar out of its overlay host.
            return;
        }
        if (parent instanceof ViewGroup) {
            if (actionBarOriginalParent == null) {
                actionBarOriginalParent = (ViewGroup) parent;
                actionBarOriginalIndex = actionBarOriginalParent.indexOfChild(actionBar);
                actionBarOriginalParams = actionBar.getLayoutParams();
            }
            ((ViewGroup) parent).removeView(actionBar);
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP;
        contentFrame.addView(actionBar, params);
    }

    private static boolean isDescendant(View child, View ancestor) {
        ViewParent parent = child.getParent();
        while (parent instanceof View) {
            if (parent == ancestor) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    /**
     * Ask AppCompat's ActionBarOverlayLayout to lay the content out behind the
     * support ActionBar instead of below it. This is the supported overlay
     * path and avoids moving the ActionBar out of its host.
     */
    private void enableSupportActionBarOverlay() {
        if (!supportActionBar || supportActionBarOverlaySet || actionBar == null) {
            return;
        }
        ViewParent parent = actionBar.getParent();
        while (parent instanceof View) {
            if (parent.getClass().getName().contains("ActionBarOverlayLayout")) {
                try {
                    Method method = parent.getClass().getMethod("setOverlayMode", boolean.class);
                    method.invoke(parent, true);
                    View overlay = (View) parent;
                    overlay.requestLayout();
                    overlay.requestApplyInsets();
                    supportActionBarOverlaySet = true;
                } catch (Throwable throwable) {
                    Log.w(TAG, "setOverlayMode failed", throwable);
                }
                return;
            }
            parent = parent.getParent();
        }
    }

    /**
     * The support ActionBarOverlayLayout leaves a top inset on the content
     * frame (ChattingUILayout starts at y=actionbarHeight). Remove that inset
     * so the chat wallpaper/list really draw under the top bar.
     */
    private void forceContentBehindTop() {
        if (chatRoot == null) {
            return;
        }
        ViewParent parent = chatRoot.getParent();
        if (!(parent instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) parent;
        if (group.getPaddingTop() != 0) {
            group.setPadding(group.getPaddingLeft(), 0,
                    group.getPaddingRight(), group.getPaddingBottom());
        }

        int top = chatRoot.getTop();
        if (top > 0 && group.getHeight() > 0) {
            ViewGroup.LayoutParams params = chatRoot.getLayoutParams();
            if (params != null && params.height != group.getHeight()) {
                params.height = group.getHeight();
                chatRoot.setLayoutParams(params);
            }
            if (chatRoot.getTranslationY() != -top) {
                chatRoot.setTranslationY(-top);
            }
        } else if (top == 0 && chatRoot.getTranslationY() != 0f) {
            chatRoot.setTranslationY(0f);
        }
    }

    private void dumpFooterEditors(View view, int depth) {
        if (view == null) return;
        String name = view.getClass().getName();
        if (name.contains("EditText") || name.contains("MMFlexEditText")) {
            Log.i(TAG, "editor depth=" + depth + " " + name
                    + " vis=" + view.getVisibility()
                    + " h=" + view.getHeight()
                    + " shown=" + view.isShown()
                    + " id=" + Integer.toHexString(view.getId())
                    + " parent=" + (view.getParent() == null ? "null"
                    : view.getParent().getClass().getName()));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                dumpFooterEditors(group.getChildAt(i), depth + 1);
            }
        }
    }

    private void dumpVisibleGeometry() {
        View input = footer == null ? null : footer.findViewById(0x7f0a0f81);
        View button = null;
        if (footer instanceof ViewGroup) {
            button = findVisibleButton((ViewGroup) footer);
        }
        int[] iLoc = new int[2];
        int[] bLoc = new int[2];
        int[] fLoc = new int[2];
        if (input != null) input.getLocationInWindow(iLoc);
        if (button != null) button.getLocationInWindow(bLoc);
        if (footer != null) footer.getLocationInWindow(fLoc);
        if (input instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) input;
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                Log.i(TAG, "input child=" + c.getClass().getName()
                        + " top=" + c.getTop() + " h=" + c.getHeight()
                        + " padt=" + c.getPaddingTop() + " padb=" + c.getPaddingBottom()
                        + " bg=" + c.getBackground());
            }
        }
        if (input != null) {
            View p = (View) input.getParent();
            for (int i = 0; i < 5 && p != null; i++) {
                Log.i(TAG, "visible ancestor " + i + " " + p.getClass().getName()
                        + " h=" + p.getHeight() + " top=" + p.getTop()
                        + " vis=" + p.getVisibility());
                p = p.getParent() instanceof View ? (View) p.getParent() : null;
            }
        }
        if (input instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) input;
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                if (c instanceof android.widget.EditText) {
                    android.widget.EditText et = (android.widget.EditText) c;
                    Log.i(TAG, "edit metrics minH=" + et.getMinHeight()
                            + " lineH=" + et.getLineHeight()
                            + " lines=" + et.getLineCount()
                            + " gravity=" + et.getGravity()
                            + " fontPad=" + et.getIncludeFontPadding()
                            + " pad=" + et.getPaddingTop() + "/" + et.getPaddingBottom()
                            + " h=" + et.getHeight());
                }
            }
        }
        Log.i(TAG, "visible input=" + (input == null ? "null"
                : iLoc[1] + "/" + input.getHeight() + "/" + input.isShown())
                + " button=" + (button == null ? "null"
                : bLoc[1] + "/" + button.getHeight() + "/" + button.isShown())
                + " footer=" + fLoc[1] + "/" + (footer == null ? -1 : footer.getHeight()));
    }

    private View findVisibleButton(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getClass().getName().contains("WeImageButton")
                    && child.isShown() && child.getHeight() > 0) {
                return child;
            }
            if (child instanceof ViewGroup) {
                View found = findVisibleButton((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void setupFooter() {
        Ui.transparent(footer);
        clearFooterBackgrounds();
        footerBottom = Ui.findByClassContains(footer, "ChatFooterBottom");
        if (footerBottom == null) {
            footerBottom = footer.findViewById(ID_FOOTER_BOTTOM);
        }
        footerPanel = Ui.findByClassContains(footer, "AppPanel");
        if (footerPanel == null) {
            footerPanel = footer.findViewById(ID_FOOTER_PANEL);
        }
        if (footerBottom != null) {
            Ui.transparent(footerBottom);
            removePanelSpacer(footerBottom);
        }
        Log.i(TAG, "footerBottom=" + (footerBottom == null ? "null"
                : footerBottom.getClass().getName())
                + " footerPanel=" + (footerPanel == null ? "null"
                : footerPanel.getClass().getName()));

        int bottomInset = Ui.dp(activity, 8f);
        footer.setPadding(footer.getPaddingLeft(), footer.getPaddingTop(),
                footer.getPaddingRight(), footer.getPaddingBottom() + bottomInset);
        footer.setBackground(new GlassBarDrawable(renderer, footer,
                Ui.dp(activity, 26f), tint, border, bottomInset));

        dumpFooterEditors(footer, 0);
        dumpVisibleGeometry();

        footer.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                v.invalidate();
            }
        });
    }

    /**
     * Clears the opaque background chain from the footer root down to the
     * EditText without touching emoji/voice button backgrounds. Done by
     * structure so it survives small WeChat resource-id changes.
     */
    private void clearFooterBackgrounds() {
        if (footer instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) footer;
            if (group.getChildCount() > 0) {
                Ui.transparent(group.getChildAt(0));
            }
        }
        View edit = Ui.findByClassContains(footer, "EditText");
        View current = edit;
        while (current != null && current != footer) {
            if (current instanceof ViewGroup) {
                current.setBackground(null);
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
    }

    /**
     * t8.xml has an 8dp spacer View between the input row and the 400dp
     * ChatFooterBottom panel. Remove it so the plus panel is visually the same
     * continuous glass card as the input row.
     */
    private void removePanelSpacer(View panel) {
        ViewParent parent = panel.getParent();
        if (!(parent instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) parent;
        int index = group.indexOfChild(panel);
        if (index <= 0) {
            return;
        }
        View previous = group.getChildAt(index - 1);
        if (previous != null && previous.getClass() == View.class) {
            ViewGroup.LayoutParams params = previous.getLayoutParams();
            if (params != null && params.height <= Ui.dp(activity, 16f)) {
                previous.setVisibility(View.GONE);
                params.height = 0;
                previous.setLayoutParams(params);
            }
        }
    }

    /**
     * Clears opaque backgrounds on the big panel containers only. Buttons and
     * small icon tiles are intentionally left alone.
     */
    private void clearLargePanelBackgrounds(View root, int depth) {
        if (root == null || depth > 2 || footer == null) {
            return;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            boolean button = root.getClass().getName().contains("Button");
            if (!button && group.getBackground() != null
                    && group.getWidth() > footer.getWidth() * 0.6f
                    && group.getHeight() > Ui.dp(activity, 60f)) {
                group.setBackground(null);
            }
            for (int i = 0; i < group.getChildCount(); i++) {
                clearLargePanelBackgrounds(group.getChildAt(i), depth + 1);
            }
        }
    }

    private void setupList() {
        list = Ui.findByClassContains(scrollLayout, "RecyclerView");
        if (list == null) {
            return;
        }
        listBaseLeft = list.getPaddingLeft();
        listBaseTop = list.getPaddingTop();
        listBaseRight = list.getPaddingRight();
        listBaseBottom = list.getPaddingBottom();
        listBaseSaved = true;
        if (list instanceof ViewGroup) {
            ((ViewGroup) list).setClipToPadding(false);
        }
        list.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX,
                                       int oldScrollY) {
                if (renderer != null) {
                    renderer.requestUpdate();
                }
            }
        });
    }

    /**
     * Applies the actual edge-to-edge geometry. Idempotent; called from the
     * ticker as heights settle and from global layout.
     */
    private void applyEdgeLayout() {
        if (renderer == null) {
            return;
        }
        // Re-apply after WeChat's own edge-to-edge reset.
        applyStatusBar();
        extendChatToBottom();
        reparentActionBar();
        ensureStatusScrim();
        if (actionBar != null) {
            actionBar.setTranslationY(Ui.statusBarHeight(decor));
        }
        // WeChat's own bottom inset shrinks the chat container by the status
        // bar height (107px on this device); stretch it back to full height.
        int decorHeight = decor == null ? 0 : decor.getHeight();
        if (decorHeight > 0 && scrollLayout != null
                && scrollLayout.getHeight() > 0
                && scrollLayout.getHeight() < decorHeight) {
            ViewGroup.LayoutParams scrollParams = scrollLayout.getLayoutParams();
            if (scrollParams != null && scrollParams.height != decorHeight) {
                scrollParams.height = decorHeight;
                scrollLayout.setLayoutParams(scrollParams);
            }
        }
        enableSupportActionBarOverlay();
        forceContentBehindTop();

        if (contentFrame != null && contentFrame.getParent() instanceof ViewGroup) {
            ViewGroup.LayoutParams params = contentFrame.getLayoutParams();
            if (params instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) params;
                if (lp.height != ViewGroup.LayoutParams.MATCH_PARENT
                        || lp.topMargin != 0 || lp.bottomMargin != 0) {
                    lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    lp.topMargin = 0;
                    lp.bottomMargin = footerOriginalBottomMargin;
                    contentFrame.setLayoutParams(lp);
                }
            }
        }

        int footerHeight = footer == null ? 0 : footer.getHeight();
        if (footerHeight > 0 && footerHeight != lastFooterHeight) {
            lastFooterHeight = footerHeight;
            ViewGroup.LayoutParams params = footer.getLayoutParams();
            if (params instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) params;
                if (footerOriginalBottomMargin == Integer.MIN_VALUE) {
                    footerOriginalBottomMargin = lp.bottomMargin;
                }
                // ChatFooter contains a 400dp hidden panel (emoji/more). Keep
                // the original negative bottom margin so that panel stays
                // below the screen, and only lift the visible input row.
                int visibleHeight = footerHeight + footerOriginalBottomMargin;
                if (visibleHeight <= 0) {
                    visibleHeight = footerHeight;
                }
                lastVisibleFooterHeight = visibleHeight;
                lp.topMargin = -visibleHeight;
                lp.bottomMargin = footerOriginalBottomMargin;
                lp.leftMargin = Ui.dp(activity, 10f);
                lp.rightMargin = Ui.dp(activity, 10f);
                footer.setLayoutParams(lp);

                if (footer.getBackground() instanceof GlassBarDrawable) {
                    ((GlassBarDrawable) footer.getBackground()).setBottomInset(
                            Math.max(0, footerHeight - visibleHeight) + Ui.dp(activity, 8f));
                }
            }
        }

        // WeChat resets these panel backgrounds when it opens the panel.
        if (footerBottom != null && footerBottom.getBackground() != null) {
            footerBottom.setBackground(null);
        }
        View emojiPanel = Ui.findByClassContains(footer, "EmojiPanelGroupView");
        if (emojiPanel != null && emojiPanel.getBackground() != null) {
            emojiPanel.setBackground(null);
        }
        View nestedBounce = Ui.findByClassContains(footer, "NestedBounceView");
        if (nestedBounce != null && nestedBounce.getBackground() != null) {
            nestedBounce.setBackground(null);
        }

        // When the plus/emoji panel is open WeChat translates the footer up and
        // reveals the 400dp panel. Let the same glass cover the whole footer so
        // the input row and the panel look like one floating card.
        if (footer.getBackground() instanceof GlassBarDrawable) {
            boolean panelOpen = footerPanel != null
                    && footerPanel.getVisibility() == View.VISIBLE
                    && footerPanel.getHeight() > 0;
            GlassBarDrawable glass = (GlassBarDrawable) footer.getBackground();
            if (panelOpen) {
                glass.setBottomInset(Ui.dp(activity, 8f));
                clearLargePanelBackgrounds(footerBottom, 0);
                clearLargePanelBackgrounds(footerPanel, 0);
            } else if (footerHeight > 0 && lastVisibleFooterHeight > 0) {
                glass.setBottomInset(Math.max(0, footerHeight - lastVisibleFooterHeight)
                        + Ui.dp(activity, 8f));
            }
        }

        applyListPadding(lastVisibleFooterHeight > 0 ? lastVisibleFooterHeight : footerHeight);
        applyInputHeight();
        styleEmojiPanel();
        ensureNavFiller();
    }

    private int navigationBarHeight() {
        int nav = 0;
        android.view.WindowInsets insets = decor == null ? null : decor.getRootWindowInsets();
        if (insets != null) {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                nav = insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom;
            } else {
                nav = insets.getSystemWindowInsetBottom();
            }
        }
        if (nav <= 0 && scrollLayout != null && decor != null) {
            nav = decor.getHeight() - scrollLayout.getHeight();
        }
        return nav;
    }

    private void ensureNavFiller() {
        if (!(decor instanceof ViewGroup)) return;
        int nav = navigationBarHeight();
        if (nav <= 0) {
            if (navFiller != null) navFiller.setVisibility(View.GONE);
            return;
        }
        if (navFiller == null) {
            View fill = new View(activity);
            fill.setClickable(false);
            fill.setFocusable(false);
            fill.setBackground(new GlassBarDrawable(renderer, fill, 0f,
                    tint, border, 0));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, nav, Gravity.BOTTOM);
            ((ViewGroup) decor).addView(fill, lp);
            navFiller = fill;
        } else {
            ViewGroup.LayoutParams lp = navFiller.getLayoutParams();
            if (lp.height != nav) {
                lp.height = nav;
                navFiller.setLayoutParams(lp);
            }
            navFiller.setVisibility(View.VISIBLE);
        }
    }

    private void styleFooterPanel() {
        if (footerPanel == null || footerPanel.getVisibility() != View.VISIBLE) {
            return;
        }
        footerPanel.setBackground(new GlassBarDrawable(renderer, footerPanel,
                Ui.dp(activity, 18f), tint, border, 0));
        clearPanelBackgrounds(footerPanel, 0);
    }

    private void clearPanelBackgrounds(View root, int depth) {
        if (root == null || depth > 6) return;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            if (depth > 0 && !root.getClass().getName().contains("Image")) {
                group.setBackground(null);
            }
            for (int i = 0; i < group.getChildCount(); i++) {
                clearPanelBackgrounds(group.getChildAt(i), depth + 1);
            }
        }
    }

    private void styleEmojiPanel() {
        if (footer == null || renderer == null) return;
        View panel = Ui.findByClassContains(footer, "SmileyPanelImpl");
        if (panel == null) {
            panel = Ui.findByClassContains(footer, "ChatFooterPanel");
        }
        if (panel == null || !panel.isShown() || panel.getHeight() <= 0) {
            if (footer.getBackground() instanceof GlassBarDrawable) {
                ((GlassBarDrawable) footer.getBackground()).setRadii(
                        Ui.dp(activity, 26f), Ui.dp(activity, 26f));
            }
            return;
        }
        GlassBarDrawable panelGlass = new GlassBarDrawable(renderer, panel,
                Ui.dp(activity, 26f), tint, border, 0);
        panelGlass.setRadii(0f, Ui.dp(activity, 26f));
        panel.setBackground(panelGlass);
        if (footer.getBackground() instanceof GlassBarDrawable) {
            ((GlassBarDrawable) footer.getBackground()).setRadii(
                    Ui.dp(activity, 26f), 0f);
        }
        clearEmojiBackgrounds(panel, 0);
        View tabBar = Ui.findByClassContains(panel, "RecyclerView");
        if (tabBar != null) {
            tabBar.setBackground(null);
        }
        View nested = Ui.findByClassContains(panel, "NestedBounceView");
        if (nested != null) {
            nested.setBackground(null);
        }
        View emojiGrid = Ui.findByClassContains(panel, "EmojiPanelGroupView");
        if (emojiGrid != null) {
            emojiGrid.setBackground(null);
        }
    }

    private void clearEmojiBackgrounds(View root, int depth) {
        if (root == null || depth > 6) return;
        if (depth > 0 && !root.getClass().getName().contains("Image")) {
            root.setBackground(null);
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                clearEmojiBackgrounds(group.getChildAt(i), depth + 1);
            }
        }
    }

    private void applyInputHeight() {
        if (footer == null) return;
        View input = footer.findViewById(0x7f0a0f81);
        if (input == null || !input.isShown() || input.getHeight() == 0) return;
        View editView = null;
        if (input instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) input;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof android.widget.EditText && child.isShown()) {
                    editView = child;
                    break;
                }
            }
        }
        if (editView == null) return;
        android.widget.EditText edit = (android.widget.EditText) editView;
        int lines = Math.max(1, edit.getLineCount());
        int content = Math.max(1, edit.getLineHeight()) * lines;
        int minHeight = Ui.dp(activity, 32f);
        int pad = Ui.dp(activity, 12f);
        int target = Math.max(minHeight, content + pad);
        int extra = Math.max(0, target - content);
        int top = extra / 2;
        int bottom = extra - top;
        if (edit.getMinHeight() != target) edit.setMinHeight(target);
        if (edit.getPaddingTop() != top || edit.getPaddingBottom() != bottom) {
            edit.setPadding(edit.getPaddingLeft(), top, edit.getPaddingRight(), bottom);
        }
        ViewGroup.LayoutParams lp = input.getLayoutParams();
        if (lp != null && lp.height != target) {
            lp.height = target;
            input.setLayoutParams(lp);
        }
        View parent = (View) input.getParent();
        if (parent != null) {
            ViewGroup.LayoutParams pp = parent.getLayoutParams();
            if (pp != null && pp.height != target) {
                pp.height = target;
                parent.setLayoutParams(pp);
            }
        }
    }

    private void applyListPadding(int footerHeight) {
        if (list == null || !listBaseSaved) {
            return;
        }
        int actionBarHeight = actionBar == null ? 0 : actionBar.getHeight();
        int topPad = actionBarHeight + Ui.dp(activity, 6f);
        int bottomPad = Math.max(0, footerHeight) + Ui.dp(activity, 14f);
        int expectedTop = listBaseTop + topPad;
        int expectedBottom = listBaseBottom + bottomPad;
        if (list.getPaddingTop() == expectedTop
                && list.getPaddingBottom() == expectedBottom
                && list.getPaddingLeft() == listBaseLeft
                && list.getPaddingRight() == listBaseRight) {
            return;
        }
        appliedTopPad = topPad;
        appliedBottomPad = bottomPad;
        list.setPadding(listBaseLeft, expectedTop, listBaseRight, expectedBottom);
    }

    private void updateTopGeometry() {
        if (!attached || actionBar == null || actionBar.getWidth() <= 0
                || actionBar.getHeight() <= 0) {
            return;
        }

        int abWidth = actionBar.getWidth();
        int abHeight = actionBar.getHeight();
        int chipHeight = Ui.dp(activity, 44f);
        int padLeft = actionBar.getPaddingLeft();
        int padTop = actionBar.getPaddingTop();

        int[] abLoc = new int[2];
        int[] childLoc = new int[2];
        actionBar.getLocationOnScreen(abLoc);

        int contentTop = Ui.dp(activity, 4f);
        if (backView != null && backView.getHeight() > 0) {
            backView.getLocationOnScreen(childLoc);
            int backTop = childLoc[1] - abLoc[1] - padTop;
            contentTop = backTop + (backView.getHeight() - chipHeight) / 2;
        }
        // If the window is already drawing behind the status bar, keep the
        // islands below it; if WeChat already insets the ActionBar, this is 0.
        int minTopForStatus = Math.max(0, Ui.statusBarHeight(decor)
                + Ui.dp(activity, 4f) - abLoc[1]);
        int top = Math.max(contentTop, minTopForStatus);
        // Do not clamp the floating bar back into the status bar when the
        // ActionBar itself is shorter than status + chip.        

        int backX;
        if (backView != null && backView.getWidth() > 0) {
            backView.getLocationOnScreen(childLoc);
            backX = childLoc[0] - abLoc[0] - padLeft
                    + backView.getWidth() / 2 - chipHeight / 2;
        } else {
            backX = Ui.dp(activity, 8f);
        }
        backX = clamp(backX, Ui.dp(activity, 4f), abWidth - chipHeight - Ui.dp(activity, 4f));

        int titleX;
        int titleW;
        if (titleView != null && titleView.getWidth() > 0) {
            titleView.getLocationOnScreen(childLoc);
            int rawX = childLoc[0] - abLoc[0] - padLeft;
            int rawW = titleView.getWidth();
            titleW = Math.max(Ui.dp(activity, 120f), rawW + Ui.dp(activity, 28f));
            titleX = rawX + rawW / 2 - titleW / 2;
        } else {
            titleW = Math.min(Ui.dp(activity, 220f), abWidth - Ui.dp(activity, 120f));
            titleX = (abWidth - titleW) / 2;
        }
        titleX = clamp(titleX, backX + chipHeight + Ui.dp(activity, 4f),
                abWidth - titleW - Ui.dp(activity, 4f));

        int menuX;
        int menuW;
        if (menuView != null && menuView.getWidth() > 0) {
            menuView.getLocationOnScreen(childLoc);
            int rawX = childLoc[0] - abLoc[0] - padLeft;
            menuW = Math.max(chipHeight, menuView.getWidth() + Ui.dp(activity, 20f));
            menuX = rawX + menuView.getWidth() - menuW + Ui.dp(activity, 8f);
        } else {
            menuW = chipHeight;
            menuX = abWidth - menuW - Ui.dp(activity, 8f);
        }
        menuX = clamp(menuX, titleX + titleW + Ui.dp(activity, 4f),
                abWidth - menuW - Ui.dp(activity, 4f));

        int maxTitleW = menuX - Ui.dp(activity, 4f) - titleX;
        if (titleW > maxTitleW) {
            titleW = Math.max(Ui.dp(activity, 90f), maxTitleW);
        }
        if (titleX + titleW > abWidth - Ui.dp(activity, 4f)) {
            titleX = Math.max(Ui.dp(activity, 4f), abWidth - Ui.dp(activity, 4f) - titleW);
        }

        applyChip(backChip, backX, top, chipHeight, chipHeight, chipHeight / 2f);
        applyChip(titleChip, titleX, top, titleW, chipHeight, Ui.dp(activity, 22f));
        applyChip(menuChip, menuX, top, menuW, chipHeight, chipHeight / 2f);
    }

    private void applyChip(GlassChipView chip, int x, int y, int width, int height, float radius) {
        if (chip == null) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) chip.getLayoutParams();
        if (params == null) {
            params = new FrameLayout.LayoutParams(width, height);
            params.gravity = Gravity.TOP | Gravity.LEFT;
        }
        boolean changed = params.width != width || params.height != height
                || params.leftMargin != x || params.topMargin != y;
        params.width = width;
        params.height = height;
        params.leftMargin = x;
        params.topMargin = y;
        if (changed) {
            chip.setLayoutParams(params);
        }
        chip.setGlass(radius, chipTint, chipBorder);
    }

    /**
     * Solid-to-transparent status-bar gradient (white in light mode, black in
     * dark). Inserted before the floating top bar so chips stay on top.
     */
    private void ensureStatusScrim() {
        if (statusScrim != null || decor == null) {
            return;
        }
        boolean night = Ui.isNight(activity);
        int height = Ui.statusBarHeight(decor) + Ui.dp(activity, 8f);
        View scrim = new View(activity);
        scrim.setClickable(false);
        scrim.setFocusable(false);
        scrim.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                night
                        ? new int[]{0xFF000000, 0xD9000000, 0x00000000}
                        : new int[]{0xFFFFFFFF, 0xD9FFFFFF, 0x00FFFFFF}));
        int status = Ui.statusBarHeight(decor);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                status * 2 + Ui.dp(activity, 8f), Gravity.TOP);
        // Draw inside the floating top bar, shifted up to cover the status bar,
        // so it stays above chat content and below the chips.
        lp.topMargin = -status;
        ViewGroup parent = null;
        if (actionBar instanceof ViewGroup) {
            parent = (ViewGroup) actionBar;
            parent.setClipChildren(false);
            if (parent.getParent() instanceof ViewGroup) {
                ((ViewGroup) parent.getParent()).setClipChildren(false);
            }
        }
        if (parent != null) {
            parent.addView(scrim, 0, lp);
        } else if (contentFrame != null) {
            contentFrame.addView(scrim, lp);
        } else if (decor instanceof ViewGroup) {
            ((ViewGroup) decor).addView(scrim, lp);
        } else {
            return;
        }
        statusScrim = scrim;
    }

    private void destroy() {
        attached = false;
        handler.removeCallbacksAndMessages(null);
        if (globalLayoutListener != null && decor != null) {
            decor.getViewTreeObserver().removeOnGlobalLayoutListener(globalLayoutListener);
            globalLayoutListener = null;
        }
        if (scrollListener != null && scrollLayout != null) {
            scrollLayout.getViewTreeObserver().removeOnScrollChangedListener(scrollListener);
            scrollListener = null;
        }
        if (statusScrim != null && statusScrim.getParent() instanceof ViewGroup) {
            ((ViewGroup) statusScrim.getParent()).removeView(statusScrim);
        }
        statusScrim = null;
        if (list != null && listBaseSaved) {
            list.setPadding(listBaseLeft, listBaseTop, listBaseRight, listBaseBottom);
        }
        if (renderer != null) {
            renderer.destroy();
            renderer = null;
        }
        if (actionBar instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) actionBar;
            if (backChip != null) {
                group.removeView(backChip);
            }
            if (titleChip != null) {
                group.removeView(titleChip);
            }
            if (menuChip != null) {
                group.removeView(menuChip);
            }
        }
        if (navFiller != null) {
            if (navFiller.getParent() instanceof ViewGroup) {
                ((ViewGroup) navFiller.getParent()).removeView(navFiller);
            }
            navFiller = null;
        }
        restoreActionBarParent();
    }

    /** Put the chat ActionBar back where WeChat had it before we reparented it. */
    private void restoreActionBarParent() {
        if (actionBar == null || actionBarOriginalParent == null) {
            return;
        }
        if (actionBar.getParent() == actionBarOriginalParent) {
            actionBarOriginalParent = null;
            return;
        }
        ViewParent current = actionBar.getParent();
        if (current instanceof ViewGroup) {
            ((ViewGroup) current).removeView(actionBar);
        }
        ViewGroup.LayoutParams lp = actionBarOriginalParams;
        int index = actionBarOriginalIndex;
        try {
            if (lp != null && index >= 0) {
                actionBarOriginalParent.addView(actionBar, index, lp);
            } else if (lp != null) {
                actionBarOriginalParent.addView(actionBar, lp);
            } else if (index >= 0) {
                actionBarOriginalParent.addView(actionBar, index);
            } else {
                actionBarOriginalParent.addView(actionBar);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "restore actionbar failed", throwable);
        }
        actionBarOriginalParent = null;
        actionBarOriginalParams = null;
        actionBarOriginalIndex = -1;
    }

    private void dumpTree(View view, int depth) {
        if (view == null || depth > 40) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            builder.append("  ");
        }
        builder.append(view.getClass().getName());
        int id = view.getId();
        if (id != View.NO_ID) {
            builder.append(" id=0x").append(Integer.toHexString(id));
        }
        builder.append(" ").append(view.getLeft()).append(',').append(view.getTop())
                .append(',').append(view.getRight()).append(',').append(view.getBottom());
        builder.append(" vis=").append(view.getVisibility());
        builder.append(" a=").append(view.getAlpha());
        builder.append(" ty=").append(view.getTranslationY());
        if (view.getBackground() != null) {
            builder.append(" bg=").append(view.getBackground().getClass().getSimpleName());
        }
        Log.i(TAG, builder.toString());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                dumpTree(group.getChildAt(i), depth + 1);
            }
        }
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(value, max));
    }
}
