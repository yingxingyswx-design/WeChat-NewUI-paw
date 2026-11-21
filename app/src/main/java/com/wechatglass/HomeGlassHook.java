package com.wechatglass;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Home (LauncherUI) top bar: same three floating glass islands as the chat top
 * bar, plus transparent status bar. Only the home ActionBar and list padding are
 * touched.
 */
final class HomeGlassHook {

    private static final String TAG = "WeChatGlassHome";
    private static final boolean DEBUG_HIDE_OVERLAY = false;
    private static final Map<Activity, HomeGlassHook> INSTANCES = new WeakHashMap<>();
    private static volatile boolean nativeBarAllowed;

    static boolean isNativeBarAllowed() {
        return nativeBarAllowed;
    }

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int tint;
    private final int border;

    private View decor;
    private View actionBar;
    private View toolbar;
    private View titleView;
    private View leftView;
    private View rightView;
    private View searchView;
    private View plusView;

    private FrameLayout overlay;
    private GlassLayerView glassLayer;
    private int scrollLogCount;
    private GlassChipView leftChip;
    private GlassChipView titleChip;
    private GlassChipView searchChip;
    private GlassChipView plusChip;
    private View topMask;
    private int tabMode; // 0 = full, 1 = title only, 2 = no bar
    private int actionBarHeight;
    private int legacyTopInset;
    private boolean legacyRemoved;
    private View frostedContent;
    private int lastFrostTop = -1;
    private GlassRenderer renderer;
    private View list;
    private int listBaseTop;
    private int listBaseBottom;
    private boolean installed;
    private int attempts;
    private boolean overlaySet;
    private long lastGeometrySignature = Long.MIN_VALUE;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!installed) {
                return;
            }
            // WeChat re-shows its ActionBar on tab switches; keep ours on top
            // and the native one hidden without running the full geometry pass.
            keepNativeBarHidden();
            maybeUpdateGeometry();
            handler.postDelayed(this, 600L);
        }
    };

    private void keepNativeBarHidden() {
        if (actionBar != null) {
            if (actionBar.getVisibility() != View.GONE) {
                actionBar.setVisibility(View.GONE);
            }
            if (actionBar.getAlpha() != 0f) {
                actionBar.setAlpha(0f);
            }
        }
        if (toolbar != null) {
            if (toolbar.getVisibility() != View.GONE) {
                toolbar.setVisibility(View.GONE);
            }
            if (toolbar.getAlpha() != 0f) {
                toolbar.setAlpha(0f);
            }
        }
        if (overlay != null && overlay.getParent() instanceof ViewGroup) {
            overlay.bringToFront();
        }
    }

    private void maybeUpdateGeometry() {
        long signature = geometrySignature();
        if (signature == lastGeometrySignature) {
            return;
        }
        lastGeometrySignature = signature;
        updateGeometry();
    }

    private long geometrySignature() {
        int decorWidth = decor == null ? 0 : decor.getWidth();
        int decorHeight = decor == null ? 0 : decor.getHeight();
        int barWidth = actionBar == null ? 0 : actionBar.getWidth();
        int barHeight = actionBar == null ? 0 : actionBar.getHeight();
        int barTop = actionBar == null ? 0 : actionBar.getTop();
        int listPadding = list == null ? 0 : list.getPaddingTop();
        long signature = decorWidth;
        signature = signature * 31 + decorHeight;
        signature = signature * 31 + barWidth;
        signature = signature * 31 + barHeight;
        signature = signature * 31 + barTop;
        signature = signature * 31 + listPadding;
        signature = signature * 31 + tabMode;
        signature = signature * 31 + (toolbar == null ? 0 : toolbar.getVisibility());
        return signature;
    }

    private HomeGlassHook(Activity activity) {
        this.activity = activity;
        boolean night = Ui.isNight(activity);
        // Solid WeChat white / dark grey; see ChatGlassHook for rationale.
        this.tint = night ? 0xFF2C2C2C : 0xFFFFFFFF;
        this.border = night ? 0x1AFFFFFF : 0x1A000000;
    }

    static void attach(Activity activity) {
        if (activity == null) {
            return;
        }
        HomeGlassHook old = INSTANCES.get(activity);
        if (old != null) {
            old.destroy();
        }
        HomeGlassHook hook = new HomeGlassHook(activity);
        INSTANCES.put(activity, hook);
        hook.install();
    }

    static void detach(Activity activity) {
        HomeGlassHook hook = INSTANCES.remove(activity);
        if (hook != null) {
            hook.destroy();
        }
    }

    static void reapplyStatusBar(Activity activity) {
        HomeGlassHook hook = INSTANCES.get(activity);
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
                    }
                }
            }
        });
    }

    private boolean tryInstall() {
        if (installed) {
            return true;
        }
        View ab = Ui.findByClass(decor, "androidx.appcompat.widget.ActionBarContainer");
        if (ab == null) {
            return false;
        }
        actionBar = ab;
        toolbar = Ui.findByClass(ab, "androidx.appcompat.widget.Toolbar");
        if (toolbar == null) {
            toolbar = ab;
        }

        applyStatusBar();
        Ui.clearActionBarBackground(actionBar);
        Ui.transparent(toolbar);

        titleView = findTitleView();
        leftView = findLeftView();
        rightView = findRightView();
        Ui.transparent(leftView);

        View rightGroup = toolbar.findViewById(0x7f0a183e);
        if (rightGroup instanceof ViewGroup) {
            ViewGroup rg = (ViewGroup) rightGroup;
            if (rg.getChildCount() > 0) {
                searchView = rg.getChildAt(0);
            }
            if (rg.getChildCount() > 1) {
                plusView = rg.getChildAt(1);
            }
        }
        frostedContent = Ui.findByClass(decor, "com.tencent.mm.ui.FrostedContentView");
        Log.i("WeChatGlassFrost", "find frost=" + (frostedContent == null ? "null"
                : frostedContent.getClass().getName()));

        HookConfig.installReceiver(activity);
        renderer = new GlassRenderer(decor);
        renderer.setGlassEnabled(HookConfig.glassEnabled(activity));

        actionBarHeight = actionBar.getHeight() > 0 ? actionBar.getHeight()
                : Ui.dp(activity, 56f) + Ui.statusBarHeight(decor);
        legacyTopInset = actionBar.getTop() + actionBar.getHeight();
        if (legacyTopInset < actionBarHeight) {
            legacyTopInset = Ui.statusBarHeight(decor) + actionBarHeight;
        }
        if (legacyTopInset <= 0) {
            legacyTopInset = Ui.dp(activity, 80f);
        }
        // Remove the old ActionBar visuals but keep its layout height.
        actionBar.setAlpha(0f);
        disableWindowContentOverlay();

        int chipHeight = Ui.dp(activity, 40f);
        overlay = new FrameLayout(activity);
        overlay.setClipChildren(false);
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, actionBarHeight);
        overlayParams.gravity = Gravity.TOP;
        ((ViewGroup) decor).addView(overlay, overlayParams);

        leftChip = new GlassChipView(activity, renderer, chipHeight / 2f, tint, border);
        titleChip = new GlassChipView(activity, renderer, Ui.dp(activity, 20f), tint, border);
        searchChip = new GlassChipView(activity, renderer, chipHeight / 2f, tint, border);
        plusChip = new GlassChipView(activity, renderer, chipHeight / 2f, tint, border);

        leftChip.setIcon(copyIcon(leftView, Ui.isNight(activity)), Ui.dp(activity, 22f));
        searchChip.setIcon(copyIcon(searchView, Ui.isNight(activity)), Ui.dp(activity, 22f));
        plusChip.setIcon(copyIcon(plusView, Ui.isNight(activity)), Ui.dp(activity, 22f));
        clickThrough(leftChip, leftView);
        clickThrough(searchChip, searchView);
        clickThrough(plusChip, plusView);
        renderer.setHiddenViews(overlay);

        addChip(overlay, titleChip, chipHeight);
        addChip(overlay, searchChip, chipHeight);
        addChip(overlay, plusChip, chipHeight);
        renderer.addSkipTarget(titleChip);
        renderer.addSkipTarget(searchChip);
        renderer.addSkipTarget(plusChip);
        renderer.addClient(titleChip);
        renderer.addClient(searchChip);
        renderer.addClient(plusChip);

        actionBar.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                maybeUpdateGeometry();
            }
        });

        enableOverlay();
        forceContentBehindTop();
        setupList();
        if (list != null && list.getParent() instanceof ViewGroup) {
            renderer.setBackdrop((ViewGroup) list.getParent());
        }
        if (list instanceof ViewGroup && overlay != null
                && HookConfig.glassEnabled(activity)) {
            renderer.setLayerMode(true);
            boolean darkTheme = Ui.isNight(activity);
            if (titleView instanceof android.widget.TextView) {
                int tc = ((android.widget.TextView) titleView).getCurrentTextColor();
                darkTheme = (tc & 0xFFFFFF) > 0x999999;
            }
            final int tintColor = darkTheme ? 0x33000000 : 0x33FFFFFF;
            android.util.Log.i("WeChatGlass", "HL theme dark=" + darkTheme + " tint=" + Integer.toHexString(tintColor));
            final View[] blurChips = {titleChip, searchChip, plusChip};
            overlay.post(new Runnable() {
                @Override public void run() {
                    boolean any = false;
                    for (View ch : blurChips) {
                        if (ch == null || ch.getWidth() <= 0 || ch.getHeight() <= 0) {
                            continue;
                        }
                        View bv = WeChatBlurBar.create(activity, (ViewGroup) decor,
                                ch.getWidth(), ch.getHeight(), tintColor);
                        if (bv == null) {
                            continue;
                        }
                        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                                ch.getWidth(), ch.getHeight());
                        lp.leftMargin = ch.getLeft();
                        lp.topMargin = ch.getTop();
                        overlay.addView(bv, 0, lp);
                        any = true;
                    }
                    overlay.bringChildToFront(titleChip);
                    overlay.bringChildToFront(searchChip);
                    overlay.bringChildToFront(plusChip);
                    android.util.Log.i("WeChatGlass", "HL blur chips any=" + any);
                }
            });
        }

        decor.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (renderer != null) {
                            renderer.requestUpdate();
                        }
                    }
                });

        installed = true;
        updateGeometry();
        lastGeometrySignature = geometrySignature();
        handler.postDelayed(tick, 600L);
        if (renderer != null) {
            renderer.requestUpdate();
        }
        Log.i(TAG, "home glass attached");
        return true;
    }

    private void disableWindowContentOverlay() {
        try {
            View overlayLayout = Ui.findByClass(decor,
                    "androidx.appcompat.widget.ActionBarOverlayLayout");
            if (overlayLayout == null) return;
            java.lang.reflect.Field field = overlayLayout.getClass().getDeclaredField("i");
            field.setAccessible(true);
            field.set(overlayLayout, null);
            overlayLayout.setWillNotDraw(true);
            overlayLayout.invalidate();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Hide the 1px divider instead of detaching it. A GONE child contributes
     * nothing to LinearLayout measurement, so the visual result is identical
     * while WeChat keeps a valid view tree.
     */
    private void removeTaskBarLine() {
        try {
            View taskBar = Ui.findByClass(decor,
                    "com.tencent.mm.plugin.taskbar.ui.TaskBarContainer");
            hideOnePixelLine(taskBar);
        } catch (Throwable ignored) {
        }
    }

    private boolean hideOnePixelLine(View view) {
        if (view == null) return false;
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (view.getClass() == View.class && lp != null && lp.height <= 1
                && lp.width == ViewGroup.LayoutParams.MATCH_PARENT) {
            if (view.getVisibility() != View.GONE) {
                view.setVisibility(View.GONE);
            }
            if (lp.height != 0) {
                lp.height = 0;
                view.setLayoutParams(lp);
            }
            return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hideOnePixelLine(group.getChildAt(i))) return true;
            }
        }
        return false;
    }

    /**
     * Hide TaskBarContainer's fake top bar (cxw.xml) instead of removing it.
     * Its parent is a FrameLayout, which ignores GONE children, so the layout
     * is unchanged while TaskBarContainer.o keeps pointing at a live view.
     */
    private void removeTaskBarHeader() {
        try {
            View content = decor.findViewById(android.R.id.content);
            View taskBar = Ui.findByClass(decor,
                    "com.tencent.mm.plugin.taskbar.ui.TaskBarContainer");
            if (taskBar == null && content != null) {
                View list = Ui.findByClassContains(content, "ConversationListView");
                if (list != null) {
                    Object value = getField(list, "i");
                    if (value instanceof View) {
                        taskBar = (View) value;
                    }
                }
            }
            if (taskBar == null) {
                return;
            }
            Object fake = getField(taskBar, "o");
            if (fake instanceof View) {
                View fakeBar = (View) fake;
                if (fakeBar.getVisibility() != View.GONE) {
                    fakeBar.setVisibility(View.GONE);
                }
                if (fakeBar.getAlpha() != 0f) {
                    fakeBar.setAlpha(0f);
                }
                ViewGroup.LayoutParams lp = fakeBar.getLayoutParams();
                if (lp != null && lp.height != 0) {
                    lp.height = 0;
                    fakeBar.setLayoutParams(lp);
                }
            }
        } catch (Throwable throwable) {
            Log.w("WeChatGlassTaskBar", "hide header failed", throwable);
        }
    }

    private Object getField(Object target, String name) {
        Class<?> cls = target.getClass();
        while (cls != null) {
            try {
                java.lang.reflect.Field field = cls.getDeclaredField(name);
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

    private void forceWrapperEtE() {
        EdgeForce.apply(activity);
    }

    private void setField(Object target, String name, Object value) {
        Class<?> cls = target.getClass();
        while (cls != null) {
            try {
                java.lang.reflect.Field field = cls.getDeclaredField(name);
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

    private void applyStatusBar() {
        try {
            Window window = activity.getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
            decor.setBackgroundColor(Color.TRANSPARENT);
            View overlayLayout = Ui.findByClass(decor,
                    "androidx.appcompat.widget.ActionBarOverlayLayout");
            if (overlayLayout != null) {
                overlayLayout.setBackground(null);
            }
            View content = decor.findViewById(android.R.id.content);
            if (content != null) {
                content.setBackgroundColor(Color.TRANSPARENT);
            }
            if (frostedContent != null) {
                frostedContent.setBackgroundColor(Color.TRANSPARENT);
            }
            if (list != null) {
                list.setBackgroundColor(Color.TRANSPARENT);
            }
            if (Build.VERSION.SDK_INT >= 30) {
                window.setDecorFitsSystemWindows(false);
                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
                    controller.setSystemBarsAppearance(
                            Ui.isNight(activity) ? 0 : mask, mask);
                }
            } else {
                decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "status bar failed", throwable);
        }
    }

    private View findTitleView() {
        View text = toolbar.findViewById(android.R.id.title);
        if (text == null) {
            text = toolbar.findViewById(android.R.id.text1);
        }
        if (text != null) {
            return text;
        }
        return findText(toolbar, 0);
    }

    private View findText(View root, int depth) {
        if (root == null || depth > 4) {
            return null;
        }
        if (root instanceof TextView) {
            CharSequence text = ((TextView) root).getText();
            if (text != null && text.length() > 0 && root.getVisibility() == View.VISIBLE) {
                return root;
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findText(group.getChildAt(i), depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private View findLeftView() {
        if (!(toolbar instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) toolbar;
        View best = null;
        int width = toolbar.getWidth();
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE || child.getWidth() <= 0) {
                continue;
            }
            if (width <= 0 || child.getLeft() < width / 3) {
                if (best == null || child.getLeft() < best.getLeft()) {
                    best = child;
                }
            }
        }
        return best;
    }

    private View findRightView() {
        View menu = Ui.findByClass(toolbar, "androidx.appcompat.widget.ActionMenuView");
        if (menu != null && menu.getWidth() > 0) {
            return menu;
        }
        if (!(toolbar instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) toolbar;
        View best = null;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE || child.getWidth() <= 0) {
                continue;
            }
            if (best == null || child.getRight() > best.getRight()) {
                best = child;
            }
        }
        return best;
    }

    /**
     * WeChat's home list is ConversationListView (not a plain RecyclerView) and
     * the page is wrapped in a NestedScrollView. Both carry a 180px top padding
     * even when the window itself is already full-bleed.
     */
    private void clearTopPaddings(View view) {
        if (view == null) {
            return;
        }
        String name = view.getClass().getName();
        if (name.contains("ConversationListView")
                || name.contains("NestedScrollView")
                || name.contains("RecyclerView")
                || name.contains("ListView")) {
            if (view.getPaddingTop() != 0) {
                view.setPadding(view.getPaddingLeft(), 0,
                        view.getPaddingRight(), view.getPaddingBottom());
            }
            if (view instanceof ViewGroup) {
                ((ViewGroup) view).setClipToPadding(false);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                clearTopPaddings(group.getChildAt(i));
            }
        }
    }

    /**
     * Hide legacy ActionBar/Toolbar/ActionMenuView in place. GONE contributes
     * zero height to both FrameLayout and ActionBarOverlayLayout, so the layout
     * is identical to detaching them, but WeChat keeps a valid view tree.
     */
    private void hideLegacyBars(View view) {
        if (view == null) {
            return;
        }
        String name = view.getClass().getName();
        if (name.contains("ActionBarContainer")
                || name.endsWith(".Toolbar")
                || name.contains("ActionMenuView")) {
            if (view.getVisibility() != View.GONE) {
                view.setVisibility(View.GONE);
            }
            view.setAlpha(0f);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                hideLegacyBars(group.getChildAt(i));
            }
        }
    }

    private void applyListTopInset(View contentRoot) {
        int inset = listTopInset();
        // Locale-independent: the discover page owns a PullDownListView. The
        // old code keyed this off the Chinese title "发现", which never matched
        // on Japanese/English builds.
        View discover = Ui.findByClassContains(contentRoot, "PullDownListView");
        if (discover != null && discover.isShown()) {
            int top = Ui.statusBarHeight(decor) + Ui.dp(activity, 8f);
            if (discover.getPaddingTop() != top) {
                discover.setPadding(discover.getPaddingLeft(), top,
                        discover.getPaddingRight(), discover.getPaddingBottom());
            }
            return;
        }
        if (tabMode != 0) {
            return;
        }
        String[] classes = {"ConversationListView", "ConversationRecyclerView"};
        for (String name : classes) {
            View list = Ui.findByClassContains(contentRoot, name);
            if (list != null && list.getPaddingTop() != inset) {
                list.setPadding(list.getPaddingLeft(), inset,
                        list.getPaddingRight(), list.getPaddingBottom());
                if (list instanceof ViewGroup) {
                    ((ViewGroup) list).setClipToPadding(false);
                }
            }
        }
    }

    private void clearAncestorPaddingForLists(View contentRoot) {
        if (contentRoot == null) {
            return;
        }
        String[] classes = {
                "ConversationListView",
                "PullDownListView",
                "WxRecyclerView",
                "RecyclerView"
        };
        for (String className : classes) {
            View list = Ui.findByClassContains(contentRoot, className);
            if (list == null) {
                continue;
            }
            View current = list;
            while (current != null && current != contentRoot) {
                if (current.getPaddingTop() != 0) {
                    current.setPadding(current.getPaddingLeft(), 0,
                            current.getPaddingRight(), current.getPaddingBottom());
                }
                if (current instanceof ViewGroup) {
                    ((ViewGroup) current).setClipToPadding(false);
                }
                android.view.ViewParent parent = current.getParent();
                current = parent instanceof View ? (View) parent : null;
            }
        }
    }

    private void addChip(ViewGroup parent, GlassChipView chip, int height) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(height, height);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        parent.addView(chip, 0, params);
    }

    private Drawable copyIcon(View root, boolean night) {
        View image = findImage(root);
        if (!(image instanceof ImageView)) {
            return null;
        }
        Drawable drawable = ((ImageView) image).getDrawable();
        if (drawable == null) {
            return null;
        }
        drawable = drawable.mutate();
        if (Build.VERSION.SDK_INT >= 21) {
            drawable.setTint(night ? Color.WHITE : 0xFF1A1A1A);
        }
        return drawable;
    }

    private View findImage(View root) {
        if (root == null) {
            return null;
        }
        if (root instanceof ImageView) {
            return root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findImage(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void clickThrough(GlassChipView chip, final View target) {
        if (target == null) {
            return;
        }
        chip.setClickable(true);
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                target.performClick();
            }
        });
    }

    private void updateTabState() {
        String text = titleView instanceof TextView
                ? String.valueOf(((TextView) titleView).getText()) : "";
        int mode = 0;
        if (text.contains("我")) {
            mode = 2;
        } else if (text.contains("通讯录") || text.contains("发现")) {
            mode = 1;
        }

        if (mode == 2) {
            if (overlay != null) {
                overlay.setVisibility(View.GONE);
            }
            if (actionBar != null) {
                actionBar.setVisibility(View.GONE);
            }
            setFrostEnabled(false, 0);
            tabMode = mode;
            return;
        }

        if (actionBar != null) {
            actionBar.setVisibility(View.GONE);
            actionBar.setAlpha(0f);
        }
        if (toolbar != null) {
            toolbar.setVisibility(View.GONE);
        }
        if (overlay != null) {
            overlay.setVisibility(View.VISIBLE);
        }
        tabMode = mode;
        boolean full = mode == 0;
        setChipVisible(leftChip, false);
        setChipVisible(searchChip, full);
        setChipVisible(plusChip, full);
        setChipVisible(titleChip, true);

        if (titleChip != null && !text.isEmpty()) {
            titleChip.setLabel(localizeTitle(text),
                    Ui.isNight(activity) ? 0xFFFFFFFF : 0xFF1A1A1A,
                    18f * activity.getResources().getDisplayMetrics().scaledDensity);
        }
        if (list != null) {
            list.setPadding(list.getPaddingLeft(), listTopInset(),
                    list.getPaddingRight(), listBaseBottom);
        }
    }

    /**
     * WeChat's own frosted layer is kept disabled: the bars are solid now, so
     * enabling it would reintroduce the blur we are removing.
     */
    private void configureNativeFrost(int actionBarBottomOnScreen) {
        setFrostEnabled(false, 0);
    }

    private void setFrostEnabled(boolean enabled, int topHeight) {
        if (frostedContent == null) {
            return;
        }
        try {
            // Only toggle WeChat's frosted layer; no blur radius/area config.
            frostedContent.getClass().getMethod("setFrostedEnabled", boolean.class)
                    .invoke(frostedContent, enabled);
            frostedContent.invalidate();
        } catch (Throwable throwable) {
            Log.w("WeChatGlassFrost", "frost toggle failed", throwable);
        }
    }

    private String localizeTitle(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        try {
            java.util.Locale locale = activity.getResources()
                    .getConfiguration().getLocales().get(0);
            if ("ja".equals(locale.getLanguage())
                    && ("Weixin".equalsIgnoreCase(text) || "WeChat".equalsIgnoreCase(text))) {
                return "ウィーシン";
            }
        } catch (Throwable ignored) {
        }
        return text;
    }

    private void setChipVisible(View chip, boolean visible) {
        if (chip != null) {
            chip.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void enableOverlay() {
        if (overlaySet || actionBar == null) {
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
                    overlaySet = true;
                } catch (Throwable ignored) {
                }
                break;
            }
            parent = parent.getParent();
        }
        View content = decor.findViewById(android.R.id.content);
        if (content != null) {
            content.setPadding(content.getPaddingLeft(), 0,
                    content.getPaddingRight(), content.getPaddingBottom());
        }
    }

    /** EtE content, while the list keeps a top height limit via padding. */
    private void forceContentBehindTop() {
        View content = decor.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) content;
        if (group.getChildCount() == 0) {
            return;
        }
        View root = group.getChildAt(0);
        if (root.getClass().getName().contains("ActionBarOverlayLayout")) {
            return;
        }
        int top = root.getTop();
        if (top > 0 && group.getHeight() > 0) {
            ViewGroup.LayoutParams params = root.getLayoutParams();
            if (params != null && params.height != group.getHeight()) {
                params.height = group.getHeight();
                root.setLayoutParams(params);
            }
            if (root.getTranslationY() != -top) {
                root.setTranslationY(-top);
            }
        } else if (top == 0 && root.getTranslationY() != 0f) {
            root.setTranslationY(0f);
        }
    }

    private int listTopInset() {
        if (tabMode == 1) {
            return Ui.statusBarHeight(decor) + actionBarHeight;
        }
        if (tabMode != 0) {
            return 0;
        }
        if (legacyTopInset > 0) {
            return Ui.statusBarHeight(decor) + Ui.dp(activity, 8f);
        }
        int status = Ui.statusBarHeight(decor);
        int bar = actionBar == null ? 0 : actionBar.getHeight();
        if (bar <= 0) {
            bar = actionBarHeight;
        }
        return status + bar;
    }

    private static ViewGroup findVisibleList(View root) {
        java.util.ArrayList<View> found = new java.util.ArrayList<>();
        collectLists(root, found);
        ViewGroup best = null;
        int bestX = Integer.MAX_VALUE;
        int[] loc = new int[2];
        for (int i = 0; i < found.size(); i++) {
            View view = found.get(i);
            if (!(view instanceof ViewGroup) || !view.isShown()) {
                continue;
            }
            view.getLocationOnScreen(loc);
            if (Math.abs(loc[0]) < bestX) {
                bestX = Math.abs(loc[0]);
                best = (ViewGroup) view;
            }
        }
        return best;
    }

    private static void collectLists(View view, java.util.List<View> out) {
        if (view == null) {
            return;
        }
        String name = view.getClass().getName();
        if (name.contains("ConversationListView") || name.contains("WxRecyclerView")) {
            out.add(view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectLists(group.getChildAt(i), out);
            }
        }
    }

    private void setupList() {
        View content = decor.findViewById(android.R.id.content);
        if (content == null) {
            content = decor;
        }
        list = Ui.findByClassContains(content, "RecyclerView");
        if (list == null) {
            return;
        }
        // Force a true full-bleed list: WeChat's fragments often set a top
        // padding equal to the ActionBar height.
        listBaseTop = 0;
        listBaseBottom = list.getPaddingBottom();
        if (list instanceof ViewGroup) {
            ((ViewGroup) list).setClipToPadding(false);
        }
        if (list != null) {
            int[] ll = new int[2];
            list.getLocationOnScreen(ll);
            android.util.Log.i("WeChatGlass", "HL geom listTop=" + ll[1]
                    + " listH=" + list.getHeight()
                    + " padTop=" + list.getPaddingTop()
                    + " clipPad=" + ((ViewGroup) list).getClipToPadding()
                    + " overlayTop=" + (overlay == null ? -1 : overlay.getTop()));
        }
        list.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX,
                                       int oldScrollY) {
                if (renderer != null) {
                    renderer.requestUpdate();
                }
                if (glassLayer != null) {
                    glassLayer.invalidate();
                    if (++scrollLogCount % 30 == 0) {
                        android.util.Log.i("WeChatGlass", "HL scroll invalidate n=" + scrollLogCount);
                    }
                }
            }
        });
    }

    private boolean isNonHomeSurface() {
        if (decor == null) {
            return true;
        }
        String[] markers = {"AppBrandDesktopView", "AppBrandDesktopContainerView"};
        for (String marker : markers) {
            View view = Ui.findByClassContains(decor, marker);
            if (view != null && view.getVisibility() == View.VISIBLE
                    && view.getWidth() > 0 && view.getHeight() > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Only 首页 / 通讯录 / 发现 keep our floating bar. Anything else (服务号,
     * 朋友圈, WebView, sub-activities sharing LauncherUI) stays native.
     */
    private boolean isSupportedPage(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        if (t.isEmpty()) {
            return false;
        }
        return "Weixin".equalsIgnoreCase(t)
                || "WeChat".equalsIgnoreCase(t)
                || "微信".equals(t)
                || t.contains("通讯录") || t.contains("通訊錄") || t.contains("連絡先")
                || "Contacts".equalsIgnoreCase(t)
                || t.contains("发现") || t.contains("發現") || t.contains("発見")
                || "Discover".equalsIgnoreCase(t);
    }

    private void switchToNativePage() {
        nativeBarAllowed = true;
        if (overlay != null && overlay.getVisibility() != View.GONE) {
            overlay.setVisibility(View.GONE);
        }
        if (actionBar != null) {
            if (actionBar.getAlpha() != 1f) {
                actionBar.setAlpha(1f);
            }
            if (actionBar.getVisibility() != View.VISIBLE) {
                actionBar.setVisibility(View.VISIBLE);
            }
        }
        if (toolbar != null) {
            if (toolbar.getAlpha() != 1f) {
                toolbar.setAlpha(1f);
            }
            if (toolbar.getVisibility() != View.VISIBLE) {
                toolbar.setVisibility(View.VISIBLE);
            }
        }
    }

    private static final String[] EXCLUDED_SURFACES = {
            "brandservice", "serviceaccount", "sns", "webview", "appbrand"
    };

    /**
     * Second-level pages (服务号 / 朋友圈 / 小程序 / WebView) are detected
     * structurally: if any visible view carries one of these class names, the
     * floating bar must not run.
     */
    private boolean hasVisibleExcludedSurface(View root) {
        if (root == null) {
            return false;
        }
        if (root.isShown() && root.getWidth() > 0) {
            String name = root.getClass().getName().toLowerCase(java.util.Locale.ROOT);
            for (String part : EXCLUDED_SURFACES) {
                if (name.contains(part)) {
                    return true;
                }
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasVisibleExcludedSurface(group.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void updateGeometry() {
        if (!installed && renderer == null) {
            return;
        }
        forceContentBehindTop();
        View contentRoot = decor.findViewById(android.R.id.content);
        clearTopPaddings(contentRoot);
        clearAncestorPaddingForLists(contentRoot);
        View freshTitle = findTitleView();
        if (freshTitle != null) {
            titleView = freshTitle;
        }
        applyListTopInset(contentRoot);
        hideLegacyBars(decor);
        removeTaskBarLine();
        removeTaskBarHeader();
        forceWrapperEtE();
        updateTabState();
        if (tabMode == 2 || actionBar == null) {
            return;
        }
        int width = actionBar.getWidth();
        if (width <= 0) {
            width = decor.getWidth();
        }
        int height = actionBar.getHeight();
        if (height > 0) {
            actionBarHeight = height;
        } else {
            height = actionBarHeight;
        }
        int chipHeight = Ui.dp(activity, 40f);
        int[] abLoc = new int[2];
        int[] childLoc = new int[2];
        actionBar.getLocationOnScreen(abLoc);
        configureNativeFrost(abLoc[1] + height);
        // Status insets are intentionally consumed globally, so query the
        // physical status bar height and keep the islands below it.
        int status = Math.max(Ui.statusBarHeight(decor), Ui.dp(activity, 24f));
        int top = Math.max(Ui.dp(activity, 4f), status + Ui.dp(activity, 4f) - abLoc[1]);
        top = Math.min(top, Math.max(0, height - chipHeight - Ui.dp(activity, 2f)));

        if (overlay != null) {
            int overlayHeight = Math.max(height, top + chipHeight + Ui.dp(activity, 4f));
            ViewGroup.LayoutParams op = overlay.getLayoutParams();
            if (op != null && op.height != overlayHeight) {
                op.height = overlayHeight;
                overlay.setLayoutParams(op);
            }
            overlay.setTranslationY(actionBar.getY());
            // HomeUI can add its FrostedContentView after our overlay, which
            // would cover the chips. Keep the overlay as the topmost decor
            // child.
            overlay.bringToFront();
        }

        if (actionBar != null) {
            actionBar.setAlpha(0f);
            if (tabMode != 2) {
                actionBar.setVisibility(View.GONE);
            }
        }
        if (toolbar != null && tabMode != 2) {
            toolbar.setVisibility(View.GONE);
        }

        int titleW;
        int titleX;
        if (titleView != null && titleView.getWidth() > 0) {
            titleView.getLocationOnScreen(childLoc);
            int rawX = childLoc[0] - abLoc[0];
            int rawW = titleView.getWidth();
            titleW = Math.max(Ui.dp(activity, 110f), rawW + Ui.dp(activity, 30f));
            titleX = rawX + rawW / 2 - titleW / 2;
        } else {
            titleW = Ui.dp(activity, 150f);
            titleX = (width - titleW) / 2;
        }
        titleX = clamp(titleX, Ui.dp(activity, 8f),
                width - titleW - Ui.dp(activity, 4f));

        // Fixed right-hand positions. Using the old toolbar child bounds made
        // the icons sit at the edge of the glass circles after the old bar was
        // hidden.
        int plusX = width - chipHeight - Ui.dp(activity, 8f);
        int searchX = plusX - chipHeight - Ui.dp(activity, 8f);

        int maxTitle = searchX - Ui.dp(activity, 4f) - titleX;
        if (titleW > maxTitle) {
            titleW = Math.max(Ui.dp(activity, 80f), maxTitle);
        }

        applyChip(titleChip, titleX, top, titleW, chipHeight, Ui.dp(activity, 20f));
        applyChip(searchChip, searchX, top, chipHeight, chipHeight, chipHeight / 2f);
        applyChip(plusChip, plusX, top, chipHeight, chipHeight, chipHeight / 2f);
        if (DEBUG_HIDE_OVERLAY && overlay != null) {
            overlay.setVisibility(View.GONE);
        }
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
        chip.setGlass(radius, tint, border);
    }

    private void destroy() {
        installed = false;
        handler.removeCallbacksAndMessages(null);
        if (renderer != null) {
            renderer.destroy();
            renderer = null;
        }
        if (overlay != null && overlay.getParent() instanceof ViewGroup) {
            ((ViewGroup) overlay.getParent()).removeView(overlay);
        }
        overlay = null;
        if (actionBar != null) {
            actionBar.setVisibility(View.VISIBLE);
            actionBar.setAlpha(1f);
        }
        if (toolbar != null && toolbar != actionBar) {
            toolbar.setVisibility(View.VISIBLE);
            toolbar.setAlpha(1f);
        }
        if (list != null) {
            list.setPadding(list.getPaddingLeft(), listTopInset(),
                    list.getPaddingRight(), listBaseBottom);
        }
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(value, max));
    }
}

