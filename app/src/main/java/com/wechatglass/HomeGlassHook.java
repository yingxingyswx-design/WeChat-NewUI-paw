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
    private View aiSlot;

    private FrameLayout overlay;
    private GlassChipView leftChip;
    private GlassChipView titleChip;
    private GlassChipView aiChip;
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

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!installed) {
                return;
            }
            updateGeometry();
            handler.postDelayed(this, 600L);
        }
    };

    private HomeGlassHook(Activity activity) {
        this.activity = activity;
        boolean night = Ui.isNight(activity);
        this.tint = night ? 0x8C1C1C1E : 0x8CFFFFFF;
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
        aiSlot = toolbar.findViewById(0x7f0a385a);
        frostedContent = Ui.findByClass(decor, "com.tencent.mm.ui.FrostedContentView");
        Log.i("WeChatGlassFrost", "find frost=" + (frostedContent == null ? "null"
                : frostedContent.getClass().getName()));

        renderer = new GlassRenderer(decor);

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
        aiChip = new GlassChipView(activity, renderer, chipHeight / 2f, tint, border);
        searchChip = new GlassChipView(activity, renderer, chipHeight / 2f, tint, border);
        plusChip = new GlassChipView(activity, renderer, chipHeight / 2f, tint, border);
        aiChip.setLabel("●  ●", Ui.isNight(activity) ? 0xFFFFFFFF : 0xFF1A1A1A,
                14f * activity.getResources().getDisplayMetrics().scaledDensity);
        aiChip.setContentDescription("小微AI");

        leftChip.setIcon(copyIcon(leftView, Ui.isNight(activity)), Ui.dp(activity, 22f));
        searchChip.setIcon(copyIcon(searchView, Ui.isNight(activity)), Ui.dp(activity, 22f));
        plusChip.setIcon(copyIcon(plusView, Ui.isNight(activity)), Ui.dp(activity, 22f));
        clickThrough(leftChip, leftView);
        clickThrough(searchChip, searchView);
        clickThrough(plusChip, plusView);
        clickThrough(aiChip, aiSlot);
        leftChip.setBlurBackground(true);
        titleChip.setBlurBackground(true);
        aiChip.setBlurBackground(true);
        searchChip.setBlurBackground(true);
        plusChip.setBlurBackground(true);
        renderer.setHiddenViews(overlay);

        // 小微 takes the far-left slot; the old left/back button is removed.
        addChip(overlay, titleChip, chipHeight);
        addChip(overlay, aiChip, chipHeight);
        addChip(overlay, searchChip, chipHeight);
        addChip(overlay, plusChip, chipHeight);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                dumpTree(decor, 0, new int[]{0});
                dumpLayoutParams();
            }
        }, 6000L);

        actionBar.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                updateGeometry();
            }
        });

        enableOverlay();
        forceContentBehindTop();
        setupList();

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
        handler.postDelayed(tick, 600L);
        if (renderer != null) {
            renderer.requestUpdate();
        }
        decor.postDelayed(new Runnable() {
            @Override
            public void run() {
                dumpTop(toolbar, 0);
            }
        }, 800L);
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

    private void removeTaskBarLine() {
        try {
            View taskBar = Ui.findByClass(decor,
                    "com.tencent.mm.plugin.taskbar.ui.TaskBarContainer");
            removeOnePixelLine(taskBar);
        } catch (Throwable ignored) {
        }
    }

    private boolean removeOnePixelLine(View view) {
        if (view == null) return false;
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (view.getClass() == View.class && lp != null && lp.height == 1
                && lp.width == ViewGroup.LayoutParams.MATCH_PARENT) {
            android.view.ViewParent parent = view.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(view);
                return true;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (removeOnePixelLine(group.getChildAt(i))) return true;
            }
        }
        return false;
    }

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
                android.view.ViewParent fakeParent = ((View) fake).getParent();
                if (fakeParent instanceof ViewGroup) {
                    ((ViewGroup) fakeParent).removeView((View) fake);
                }
            }
            Log.i("WeChatGlassTaskBar", "removed fake TaskBar top bar");
        } catch (Throwable throwable) {
            Log.w("WeChatGlassTaskBar", "remove header failed", throwable);
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
     * Fragment roots (FindMoreFriendsUI.N1, MoreTabUI) add paddingTop directly
     * to their root View, not only to the list. Clear every ancestor between the
     * list and android.R.id.content so no invisible top bar strip remains.
     */
    private void removeLegacyBars(View view) {
        if (view == null) {
            return;
        }
        java.util.ArrayList<View> remove = new java.util.ArrayList<>();
        collectLegacyBars(view, remove);
        for (View bar : remove) {
            android.view.ViewParent parent = bar.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(bar);
            }
            bar.setLayerType(View.LAYER_TYPE_NONE, null);
            bar.destroyDrawingCache();
        }
        if (!remove.isEmpty()) {
            decor.postInvalidateOnAnimation();
            if (frostedContent != null) {
                frostedContent.postInvalidateOnAnimation();
            }
        }
    }

    private void collectLegacyBars(View view, java.util.ArrayList<View> out) {
        if (view == null || view == overlay) {
            return;
        }
        String name = view.getClass().getName();
        if (name.contains("ActionBarContainer")
                || name.endsWith(".Toolbar")
                || name.contains("ActionMenuView")) {
            out.add(view);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectLegacyBars(group.getChildAt(i), out);
            }
        }
    }

    private void hideLegacyBars(View view) {
        if (view == null) {
            return;
        }
        String name = view.getClass().getName();
        if (name.contains("ActionBarContainer")
                || name.endsWith(".Toolbar")
                || name.contains("ActionMenuView")) {
            if (tabMode != 2) {
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
        if (tabMode == 1) {
            View discover = Ui.findByClassContains(contentRoot, "PullDownListView");
            if (discover != null) {
                int top = Ui.statusBarHeight(decor) + actionBarHeight;
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

    private void dumpLayoutParams() {
        try {
            View content = decor.findViewById(android.R.id.content);
            Log.i("WeChatGlassParams", "content=" + (content == null ? "null"
                    : content.getClass().getName() + " " + content.getWidth() + "x"
                    + content.getHeight() + " pad=" + content.getPaddingTop()));
            if (content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0) {
                View child = ((ViewGroup) content).getChildAt(0);
                ViewGroup.LayoutParams lp = child.getLayoutParams();
                String extra = "";
                if (lp instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
                    extra = " margins=" + m.leftMargin + "," + m.topMargin + ","
                            + m.rightMargin + "," + m.bottomMargin;
                }
                Log.i("WeChatGlassParams", "child0=" + child.getClass().getName()
                        + " " + child.getWidth() + "x" + child.getHeight()
                        + " top=" + child.getTop() + " ty=" + child.getTranslationY()
                        + " lp=" + (lp == null ? "null" : lp.getClass().getSimpleName()
                        + " w=" + lp.width + " h=" + lp.height) + extra);
            }
            if (actionBar != null) {
                Log.i("WeChatGlassParams", "actionBar=" + actionBar.getClass().getName()
                        + " " + actionBar.getWidth() + "x" + actionBar.getHeight()
                        + " top=" + actionBar.getTop() + " alpha=" + actionBar.getAlpha()
                        + " vis=" + actionBar.getVisibility());
            }
        } catch (Throwable throwable) {
            Log.w("WeChatGlassParams", "dump failed", throwable);
        }
    }

    private void dumpTree(View view, int depth, int[] budget) {
        if (view == null || depth > 28 || budget[0] > 180) {
            return;
        }
        budget[0]++;
        String id;
        try {
            id = view.getId() == View.NO_ID ? "" : "0x" + Integer.toHexString(view.getId());
        } catch (Throwable throwable) {
            id = "?";
        }
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            indent.append("  ");
        }
        String label = "";
        if (view instanceof TextView) {
            label = " text=" + ((TextView) view).getText();
        }
        CharSequence desc = view.getContentDescription();
        if (desc != null && desc.length() > 0) {
            label += " desc=" + desc;
        }
        Log.i("WeChatGlassDump", indent + view.getClass().getSimpleName()
                + " id=" + id
                + " " + view.getLeft() + "," + view.getTop()
                + "," + view.getRight() + "," + view.getBottom()
                + " vis=" + view.getVisibility()
                + " alpha=" + view.getAlpha()
                + " ty=" + view.getTranslationY()
                + " pad=" + view.getPaddingTop() + "/" + view.getPaddingBottom()
                + label);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                dumpTree(group.getChildAt(i), depth + 1, budget);
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
        setChipVisible(aiChip, full);
        setChipVisible(searchChip, full);
        setChipVisible(plusChip, full);
        setChipVisible(titleChip, true);

        if (titleChip != null && !text.isEmpty()) {
            titleChip.setLabel(text, Ui.isNight(activity) ? 0xFFFFFFFF : 0xFF1A1A1A,
                    18f * activity.getResources().getDisplayMetrics().scaledDensity);
        }
        if (list != null) {
            list.setPadding(list.getPaddingLeft(), listTopInset(),
                    list.getPaddingRight(), listBaseBottom);
        }
    }

    /**
     * Reserve the original top-bar height as transparent space, without
     * drawing a bar or an opaque background.
     */
    /**
     * Enable WeChat's own FrostedContentView. It records only its children and
     * draws a blurred top strip, so it is both 60fps and recursion-safe.
     */
    private void configureNativeFrost(int actionBarBottomOnScreen) {
        if (frostedContent == null) {
            return;
        }
        int[] loc = new int[2];
        frostedContent.getLocationOnScreen(loc);
        int top = Math.max(0, actionBarBottomOnScreen - loc[1]);
        setFrostEnabled(top > 0, top);
    }

    private void setFrostEnabled(boolean enabled, int topHeight) {
        if (frostedContent == null) {
            return;
        }
        try {
            if (lastFrostTop != topHeight) {
                lastFrostTop = topHeight;
                frostedContent.getClass().getMethod("setBlurRadius", int.class)
                        .invoke(frostedContent, 50);
                frostedContent.getClass().getMethod("setTopBlurAreaHeight", int.class)
                        .invoke(frostedContent, topHeight);
                frostedContent.getClass().getMethod("setFrostedColor", int.class)
                        .invoke(frostedContent, Ui.isNight(activity) ? 0x661C1C1E : 0x66FFFFFF);
                frostedContent.getClass().getMethod("setFrostedEnabled", boolean.class)
                        .invoke(frostedContent, enabled);
                frostedContent.invalidate();
            }
            Log.i("WeChatGlassFrost", "frost top=" + topHeight + " enabled=" + enabled
                    + " view=" + (frostedContent == null ? "null" : frostedContent.getClass().getName()));
        } catch (Throwable throwable) {
            Log.w("WeChatGlassFrost", "frost failed top=" + topHeight, throwable);
        }
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
            return legacyTopInset;
        }
        int status = Ui.statusBarHeight(decor);
        int bar = actionBar == null ? 0 : actionBar.getHeight();
        if (bar <= 0) {
            bar = actionBarHeight;
        }
        return status + bar;
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

    private void updateGeometry() {
        if (!installed && renderer == null) {
            return;
        }
        forceContentBehindTop();
        View contentRoot = decor.findViewById(android.R.id.content);
        clearTopPaddings(contentRoot);
        clearAncestorPaddingForLists(contentRoot);
        applyListTopInset(contentRoot);
        hideLegacyBars(decor);
        removeLegacyBars(decor);
        removeTaskBarLine();
        removeTaskBarHeader();
        forceWrapperEtE();
        View freshTitle = findTitleView();
        if (freshTitle != null) {
            titleView = freshTitle;
        }
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

        int aiWidth = Ui.dp(activity, 72f);
        int aiX = Ui.dp(activity, 8f);

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
        titleX = clamp(titleX, aiX + aiWidth + Ui.dp(activity, 4f),
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
        applyChip(aiChip, aiX, top, aiWidth, chipHeight, chipHeight / 2f);
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

    private void dumpTop(View view, int depth) {
        if (view == null || depth > 5) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            builder.append("  ");
        }
        builder.append(view.getClass().getName());
        builder.append(" id=0x").append(Integer.toHexString(view.getId()));
        builder.append(" ").append(view.getLeft()).append(',').append(view.getTop())
                .append(',').append(view.getRight()).append(',').append(view.getBottom());
        builder.append(" vis=").append(view.getVisibility());
        if (view instanceof TextView) {
            builder.append(" text=").append(((TextView) view).getText());
        }
        CharSequence desc = view.getContentDescription();
        if (desc != null) {
            builder.append(" desc=").append(desc);
        }
        Log.i(TAG, builder.toString());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                dumpTop(group.getChildAt(i), depth + 1);
            }
        }
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
