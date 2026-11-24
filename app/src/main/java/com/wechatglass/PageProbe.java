package com.wechatglass;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.SectionIndexer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * P0 probe: reads the live object graph instead of guessing names.
 *
 * <p>We already hold the {@code WxViewPager} object; from it we take the
 * adapter <em>object</em> and hook the framework methods
 * {@code instantiateItem}/{@code destroyItem} on that object's class. That
 * gives us the real page object for every tab index, with no obfuscated class
 * name and no {@code getChildAt(i)} guessing. From each page object we then
 * walk children (objects) and log the list adapter object, its item type, its
 * {@link SectionIndexer} sections and the native {@code OnItemClickListener}
 * object.
 *
 * <p>Read-only: nothing is modified, no result is replaced. This class is a
 * probe and is expected to be deleted once the anchors are recorded.
 */
final class PageProbe {

    private static final String TAG = "WeChatGlassProbe";
    private static final int MAX_DEPTH = 7;
    private static final int MAX_CHILDREN = 40;

    private static Object hookedAdapter;
    private static final List<Integer> DUMPED_PAGES = new ArrayList<>();

    private PageProbe() {
    }

    /** Called from the home tick; cheap identity check, hooks at most once. */
    static void probe(View pager) {
        if (pager == null) {
            return;
        }
        try {
            Object adapter = XposedHelpers.callMethod(pager, "getAdapter");
            if (adapter == null || adapter == hookedAdapter) {
                return;
            }
            hookedAdapter = adapter;
            log("pager adapter class=" + adapter.getClass().getName()
                    + " pagerClass=" + pager.getClass().getName()
                    + " children=" + ((ViewGroup) pager).getChildCount());
            hookAdapter(adapter);
            dumpPager(pager);
        } catch (Throwable throwable) {
            log("probe failed: " + throwable);
        }
    }

    private static void hookAdapter(final Object adapter) {
        try {
            XposedHelpers.findAndHookMethod(adapter.getClass(), "instantiateItem",
                    ViewGroup.class, int.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Object page = param.getResult();
                                int index = (Integer) param.args[1];
                                log("instantiateItem index=" + index
                                        + " pageClass=" + (page == null ? "null"
                                        : page.getClass().getName())
                                        + " hash=" + System.identityHashCode(page));
                                dumpPage("page" + index, page, 0);
                            } catch (Throwable throwable) {
                                log("instantiateItem dump failed: " + throwable);
                            }
                        }
                    });
            log("hooked instantiateItem on " + adapter.getClass().getName());
        } catch (Throwable throwable) {
            log("instantiateItem hook failed: " + throwable);
        }
        try {
            XposedHelpers.findAndHookMethod(adapter.getClass(), "destroyItem",
                    ViewGroup.class, int.class, Object.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            log("destroyItem index=" + param.args[1]
                                    + " pageClass=" + (param.args[2] == null ? "null"
                                    : param.args[2].getClass().getName()));
                        }
                    });
        } catch (Throwable throwable) {
            log("destroyItem hook failed: " + throwable);
        }
    }

    /** Logs every pager child with its identity so we can map index -> object. */
    static void dumpPager(View pager) {
        try {
            if (!(pager instanceof ViewGroup)) {
                return;
            }
            ViewGroup group = (ViewGroup) pager;
            log("---- pager children " + group.getChildCount() + " ----");
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                log("child[" + i + "] class=" + child.getClass().getName()
                        + " id=0x" + Integer.toHexString(child.getId())
                        + " hash=" + System.identityHashCode(child)
                        + " vis=" + child.getVisibility()
                        + " size=" + child.getWidth() + "x" + child.getHeight());
                // Only the page that is actually on screen is worth a full walk.
                if (child.getVisibility() == View.VISIBLE && child.getWidth() > 0) {
                    dumpPage("child" + i, child, 1);
                }
            }
        } catch (Throwable throwable) {
            log("dumpPager failed: " + throwable);
        }
    }

    /** Walks a page/view object and reports lists, adapters and listeners. */
    static void dumpPage(String label, Object node, int depth) {
        if (!(node instanceof View)) {
            return;
        }
        View view = (View) node;
        if (depth == 0) {
            log("---- " + label + " class=" + view.getClass().getName()
                    + " hash=" + System.identityHashCode(view)
                    + " size=" + view.getWidth() + "x" + view.getHeight());
        }
        if (depth > MAX_DEPTH) {
            return;
        }
        if (view instanceof AbsListView || view.getClass().getName().contains("RecyclerView")) {
            dumpList(view, depth);
        }
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        int count = Math.min(group.getChildCount(), MAX_CHILDREN);
        for (int i = 0; i < count; i++) {
            View child = group.getChildAt(i);
            StringBuilder line = new StringBuilder();
            for (int d = 0; d < depth; d++) {
                line.append("  ");
            }
            line.append("- ").append(child.getClass().getSimpleName())
                    .append(" id=0x").append(Integer.toHexString(child.getId()))
                    .append(" hash=").append(System.identityHashCode(child))
                    .append(" vis=").append(child.getVisibility())
                    .append(" w=").append(child.getWidth())
                    .append(" h=").append(child.getHeight());
            log(line.toString());
            if (child instanceof ViewGroup && depth + 1 <= MAX_DEPTH) {
                dumpPage(label, child, depth + 1);
            }
        }
        if (group.getChildCount() > MAX_CHILDREN) {
            log("(" + label + " truncated " + (group.getChildCount() - MAX_CHILDREN) + " children)");
        }
    }

    private static void dumpList(View view, int depth) {
        try {
            Object adapter = XposedHelpers.callMethod(view, "getAdapter");
            log("LIST " + view.getClass().getName()
                    + " adapter=" + (adapter == null ? "null" : adapter.getClass().getName()));
            if (adapter == null) {
                return;
            }
            Object count = safeCall(adapter, "getCount");
            log("  count=" + count);
            Object item0 = safeCall(adapter, "getItem", 0);
            log("  item0=" + (item0 == null ? "null" : item0.getClass().getName())
                    + " [" + item0 + "]");
            if (item0 == null) {
                dumpAdapterShape(adapter);
            } else {
                int prefCount = 0;
                Object c = safeCall(adapter, "getCount");
                if (c instanceof Integer) {
                    prefCount = (Integer) c;
                }
                dumpPreferences(adapter, prefCount);
            }
            if (adapter instanceof SectionIndexer) {
                Object[] sections = ((SectionIndexer) adapter).getSections();
                StringBuilder sb = new StringBuilder();
                if (sections != null) {
                    for (int i = 0; i < Math.min(sections.length, 40); i++) {
                        sb.append(i == 0 ? "" : ",").append(sections[i]);
                        sb.append("@").append(((SectionIndexer) adapter)
                                .getPositionForSection(i));
                    }
                }
                log("  sections=" + sb);
            }
            Object listener = readField(view, "mOnItemClickListener");
            log("  onItemClickListener=" + (listener == null ? "null"
                    : listener.getClass().getName()));
            Object longListener = readField(view, "mOnItemLongClickListener");
            log("  onItemLongClickListener=" + (longListener == null ? "null"
                    : longListener.getClass().getName()));
        } catch (Throwable throwable) {
            log("  dumpList failed: " + throwable);
        }
    }

    /**
     * RecyclerView adapters have no getItem(); their data lives in fields. List
     * every field that holds a collection and dump the first element's fields so
     * the row object (and the value we would show) is known.
     */
    private static void dumpAdapterShape(Object adapter) {
        Class<?> type = adapter.getClass();
        while (type != null && !"java.lang.Object".equals(type.getName())) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(adapter);
                    if (value instanceof List) {
                        List<?> list = (List<?>) value;
                        log("  field " + field.getName() + " List size=" + list.size());
                        if (!list.isEmpty()) {
                            int[] probes = {0, 1, 2, 5};
                            for (int idx : probes) {
                                if (idx < list.size()) {
                                    dumpShallow("    " + field.getName() + "[" + idx + "]",
                                            list.get(idx));
                                }
                            }
                        }
                    } else if (value instanceof java.util.Map) {
                        java.util.Map<?, ?> map = (java.util.Map<?, ?>) value;
                        log("  field " + field.getName() + " Map size=" + map.size());
                        int shown = 0;
                        for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                            if (shown++ >= 3) {
                                break;
                            }
                            log("    key=" + shorten(entry.getKey()));
                            dumpShallow("    value", entry.getValue());
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
    }

    /** Field names and primitive values only; objects are shown by type. */
    /** Every Preference row with the values our re-skin would render. */
    private static void dumpPreferences(Object adapter, int count) {
        int limit = Math.min(count, 16);
        log("  preferences count=" + count);
        for (int i = 0; i < limit; i++) {
            Object pref = safeCall(adapter, "getItem", i);
            if (pref == null) {
                log("  pref[" + i + "] = null");
                continue;
            }
            Object icon = safeCall(pref, "getIcon");
            Object summary = safeCall(pref, "getSummary");
            log("  pref[" + i + "] " + pref.getClass().getSimpleName()
                    + " h=" + shorten(readField(pref, "h"))
                    + " e=" + shorten(readField(pref, "e"))
                    + " f=" + shorten(readField(pref, "f"))
                    + " title=" + shorten(safeCall(pref, "getTitle"))
                    + " summary=" + shorten(summary)
                    + " icon=" + (icon == null ? "null" : icon.getClass().getSimpleName())
                    + " iconRes=" + readField(pref, "i")
                    + " layout=" + readField(pref, "F"));
        }
    }

    /** Logs the AppCompat ActionBar object chain (replaces class-name lookups). */
    static void probeActionBar(android.app.Activity activity) {
        try {
            Object bar = safeCall(activity, "getSupportActionBar");
            log("actionBar=" + (bar == null ? "null" : bar.getClass().getName())
                    + " hash=" + System.identityHashCode(bar));
            if (bar == null) {
                return;
            }
            Object custom = safeCall(bar, "getCustomView");
            log("customView=" + (custom == null ? "null"
                    : custom.getClass().getName())
                    + " hash=" + System.identityHashCode(custom));
            // WeChat uses a Toolbar instead of a custom view, so the bar object
            // itself must hand us the container view: read its View fields.
            Class<?> type = bar.getClass();
            while (type != null && !"java.lang.Object".equals(type.getName())) {
                for (Field field : type.getDeclaredFields()) {
                    try {
                        field.setAccessible(true);
                        Object value = field.get(bar);
                        if (value instanceof View) {
                            log("  bar." + field.getName() + " -> "
                                    + value.getClass().getName()
                                    + " hash=" + System.identityHashCode(value));
                        }
                    } catch (Throwable ignored) {
                    }
                }
                type = type.getSuperclass();
            }
            if (custom instanceof View) {
                dumpTree("custom", (View) custom, 0, 4);
            }
        } catch (Throwable throwable) {
            log("actionBar probe failed: " + throwable);
        }
    }

    private static void dumpTree(String label, View view, int depth, int maxDepth) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        sb.append("- ").append(view.getClass().getSimpleName())
                .append(" id=0x").append(Integer.toHexString(view.getId()))
                .append(" vis=").append(view.getVisibility())
                .append(" w=").append(view.getWidth())
                .append(" h=").append(view.getHeight());
        if (view instanceof android.widget.TextView) {
            sb.append(" text=").append(shorten(((android.widget.TextView) view).getText()));
        }
        log(label + sb);
        if (depth >= maxDepth || !(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < Math.min(group.getChildCount(), 20); i++) {
            dumpTree(label, group.getChildAt(i), depth + 1, maxDepth);
        }
    }

    private static void dumpShallow(String label, Object item) {
        if (item == null) {
            log(label + " = null");
            return;
        }
        log(label + " class=" + item.getClass().getName() + " [" + shorten(item) + "]");
        Class<?> type = item.getClass();
        while (type != null && !"java.lang.Object".equals(type.getName())
                && !type.getName().startsWith("java.")) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(item);
                    if (value instanceof String || value instanceof Number
                            || value instanceof Boolean || value == null) {
                        log("      ." + field.getName() + " = " + shorten(value));
                    } else {
                        log("      ." + field.getName() + " : "
                                + value.getClass().getSimpleName());
                    }
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
    }

    private static void dumpItem(String label, Object item) {
        dumpItem(label, item, 0);
    }

    private static void dumpItem(String label, Object item, int depth) {
        if (item == null) {
            log(label + " = null");
            return;
        }
        log(label + " class=" + item.getClass().getName() + " [" + shorten(item) + "]");
        if (depth > 2) {
            return;
        }
        Class<?> type = item.getClass();
        while (type != null && !"java.lang.Object".equals(type.getName())
                && !type.getName().startsWith("java.")) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(item);
                    if (value instanceof String || value instanceof Number
                            || value instanceof Boolean || value == null) {
                        log("    " + dots(depth) + "." + field.getName() + " = "
                                + shorten(value));
                    } else if (value.getClass().getName().startsWith("com.tencent.")) {
                        log("    " + dots(depth) + "." + field.getName() + " -> "
                                + value.getClass().getName());
                        dumpItem("    " + dots(depth) + "." + field.getName(),
                                value, depth + 1);
                    } else if (value instanceof java.util.List) {
                        List<?> list = (List<?>) value;
                        log("    " + dots(depth) + "." + field.getName()
                                + " List size=" + list.size());
                        if (!list.isEmpty()) {
                            dumpItem("    " + dots(depth) + ".  [0]", list.get(0), depth + 1);
                        }
                    } else {
                        log("    " + dots(depth) + "." + field.getName()
                                + " : " + value.getClass().getSimpleName());
                    }
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
    }

    private static String dots(int depth) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append(". ");
        }
        return sb.toString();
    }

    private static String shorten(Object value) {
        String text = String.valueOf(value);
        return text.length() > 80 ? text.substring(0, 80) + "…" : text;
    }

    private static Object safeCall(Object target, String method, Object... args) {
        try {
            return XposedHelpers.callMethod(target, method, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object readField(Object target, String name) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static void log(String message) {
        android.util.Log.i(TAG, message);
    }
}
