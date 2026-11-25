package com.wechatglass;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * P0 probe: how does WeChat store/reference the chat wallpaper?
 *
 * <p>Dev-only (gated by {@link BuildFlags#devProbesAllowed()}). Read-only: logs
 * prefs keys, candidate files under WeChat's data dir, and any view that paints
 * a bitmap background on the chat page.
 */
final class WallpaperProbe {

    private static final String TAG = "WeChatGlassWall";

    private WallpaperProbe() {
    }

    static void install(ClassLoader cl) {
        if (!BuildFlags.devProbesAllowed()) {
            return;
        }
        try {
            XposedHelpers.findAndHookMethod(
                    "com.tencent.mm.ui.chatting.ChattingUI", cl, "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!(param.thisObject instanceof android.app.Activity)) {
                                return;
                            }
                            final android.app.Activity activity =
                                    (android.app.Activity) param.thisObject;
                            View decor = activity.getWindow().getDecorView();
                            decor.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    dump(activity);
                                }
                            }, 1500L);
                        }
                    });
            android.util.Log.i(TAG, "wallpaper probe hooked");
        } catch (Throwable throwable) {
            android.util.Log.i(TAG, "wallpaper probe skipped: " + throwable);
        }
    }

    private static void dump(Context context) {
        try {
            dumpPrefs(context);
        } catch (Throwable throwable) {
            android.util.Log.w(TAG, "prefs dump failed", throwable);
        }
        try {
            dumpFiles(context);
        } catch (Throwable throwable) {
            android.util.Log.w(TAG, "files dump failed", throwable);
        }
        try {
            View decor = ((android.app.Activity) context).getWindow().getDecorView();
            dumpViews(decor, 0);
            View content = findContent(decor);
            if (content == null) {
                android.util.Log.i(TAG, "no ChattingContent found");
            } else {
                android.util.Log.i(TAG, "---- ChattingContent subtree ----");
                dumpSubtree(content, 0);
                dumpMessageRows(content);
            }
        } catch (Throwable throwable) {
            android.util.Log.w(TAG, "view dump failed", throwable);
        }
    }

    /**
     * Message rows: shows where group-member names sit relative to the bubble
     * target, so the styler can skip them instead of wrapping them in a bubble.
     */
    private static void dumpMessageRows(View content) {
        View list = findRecycler(content);
        if (!(list instanceof ViewGroup)) {
            android.util.Log.i(TAG, "no message list");
            return;
        }
        ViewGroup group = (ViewGroup) list;
        int rows = Math.min(group.getChildCount(), 4);
        for (int i = 0; i < rows; i++) {
            android.util.Log.i(TAG, "---- row[" + i + "] ----");
            dumpRow(group.getChildAt(i), 0, 5);
        }
    }

    private static View findRecycler(View view) {
        if (view == null) {
            return null;
        }
        if (view.getClass().getSimpleName().contains("ScrollControlRecyclerView")) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findRecycler(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void dumpRow(View view, int depth, int maxDepth) {
        if (view == null || depth > maxDepth) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append("    ");
        }
        sb.append("row-").append(view.getClass().getSimpleName())
                .append(" id=0x").append(Integer.toHexString(view.getId()))
                .append(" ").append(view.getWidth()).append("x").append(view.getHeight())
                .append(" bg=").append(view.getBackground() == null ? "null"
                        : view.getBackground().getClass().getSimpleName());
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null && text.length() > 0) {
                String flat = text.toString().replace('\n', ' ');
                sb.append(" text=\"").append(flat.length() > 24
                        ? flat.substring(0, 24) + "…" : flat).append('"');
            }
        }
        android.util.Log.i(TAG, sb.toString());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                dumpRow(group.getChildAt(i), depth + 1, maxDepth);
            }
        }
    }

    private static View findContent(View view) {
        if (view == null) {
            return null;
        }
        if (view.getClass().getSimpleName().contains("ChattingContent")) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findContent(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Full detail for the chat background container's own subtree. */
    private static void dumpSubtree(View view, int depth) {
        if (view == null || depth > 6) {
            return;
        }
        Drawable bg = view.getBackground();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        sb.append("- ").append(view.getClass().getSimpleName())
                .append(" id=0x").append(Integer.toHexString(view.getId()))
                .append(" ").append(view.getWidth()).append("x").append(view.getHeight())
                .append(" bg=").append(bg == null ? "null" : bg.getClass().getSimpleName());
        if (bg instanceof BitmapDrawable
                && ((BitmapDrawable) bg).getBitmap() != null) {
            sb.append(" bgBitmap=").append(((BitmapDrawable) bg).getBitmap().getWidth())
                    .append("x").append(((BitmapDrawable) bg).getBitmap().getHeight());
        }
        if (view instanceof ImageView) {
            Drawable drawable = ((ImageView) view).getDrawable();
            sb.append(" drawable=").append(drawable == null ? "null"
                    : drawable.getClass().getSimpleName());
            if (drawable instanceof BitmapDrawable
                    && ((BitmapDrawable) drawable).getBitmap() != null) {
                sb.append(" bmp=").append(((BitmapDrawable) drawable).getBitmap().getWidth())
                        .append("x").append(((BitmapDrawable) drawable).getBitmap().getHeight());
            }
        }
        android.util.Log.i(TAG, sb.toString());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                dumpSubtree(group.getChildAt(i), depth + 1);
            }
        }
    }

    private static void dumpPrefs(Context context) {
        File dir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
        File[] files = dir.listFiles();
        if (files == null) {
            android.util.Log.i(TAG, "no shared_prefs dir");
            return;
        }
        android.util.Log.i(TAG, "prefs files=" + files.length);
        for (File file : files) {
            String name = file.getName();
            if (!name.endsWith(".xml")) {
                continue;
            }
            String base = name.substring(0, name.length() - 4);
            if (!(base.contains("setting") || base.contains("chat")
                    || base.contains("background") || base.contains("wall")
                    || base.contains("mm"))) {
                continue;
            }
            try {
                SharedPreferences sp = context.getSharedPreferences(base, 0);
                Map<String, ?> all = sp.getAll();
                int hits = 0;
                for (Map.Entry<String, ?> entry : all.entrySet()) {
                    String key = entry.getKey();
                    String lower = key.toLowerCase();
                    if (lower.contains("bg") || lower.contains("wall")
                            || lower.contains("background")) {
                        android.util.Log.i(TAG, "pref " + base + " :: " + key + " = "
                                + shorten(entry.getValue()));
                        if (++hits >= 12) {
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void dumpFiles(Context context) {
        File data = new File(context.getApplicationInfo().dataDir);
        walk(data, data, 0);
    }

    private static void walk(File root, File dir, int depth) {
        if (dir == null || depth > 3) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            String name = child.getName().toLowerCase();
            boolean image = name.endsWith(".jpg") || name.endsWith(".png")
                    || name.endsWith(".webp") || name.endsWith(".jpeg");
            if (child.isFile() && (name.contains("bg") || name.contains("wall")
                    || name.contains("background") || name.contains("chatbg")
                    || (image && child.length() > 60_000))) {
                android.util.Log.i(TAG, "file " + child.getAbsolutePath()
                        + " size=" + child.length());
            }
            if (child.isDirectory()) {
                walk(root, child, depth + 1);
            }
        }
    }

    private static void dumpViews(View view, int depth) {
        if (view == null || depth > 14) {
            return;
        }
        Drawable bg = view.getBackground();
        String name = view.getClass().getSimpleName();
        View root = view.getRootView();
        boolean big = root != null && root.getWidth() > 0
                && view.getWidth() >= root.getWidth() * 0.9f
                && view.getHeight() >= root.getHeight() * 0.4f;
        boolean interesting = big
                || name.toLowerCase(java.util.Locale.ROOT).contains("wall")
                || name.toLowerCase(java.util.Locale.ROOT).contains("bg")
                || name.toLowerCase(java.util.Locale.ROOT).contains("background")
                || name.contains("ChattingContent");
        BitmapDrawable bitmap = null;
        if (bg instanceof BitmapDrawable) {
            bitmap = (BitmapDrawable) bg;
        } else if (view instanceof ImageView) {
            Drawable drawable = ((ImageView) view).getDrawable();
            if (drawable instanceof BitmapDrawable) {
                bitmap = (BitmapDrawable) drawable;
            }
        }
        if (bitmap != null && bitmap.getBitmap() != null) {
            android.util.Log.i(TAG, "view " + name
                    + " id=0x" + Integer.toHexString(view.getId())
                    + " size=" + view.getWidth() + "x" + view.getHeight()
                    + " bitmap=" + bitmap.getBitmap().getWidth() + "x"
                    + bitmap.getBitmap().getHeight()
                    + " bg=" + (bg instanceof BitmapDrawable ? "background" : "drawable"));
        } else if (interesting) {
            android.util.Log.i(TAG, "view " + name
                    + " id=0x" + Integer.toHexString(view.getId())
                    + " size=" + view.getWidth() + "x" + view.getHeight()
                    + " bg=" + (bg == null ? "null" : bg.getClass().getName())
                    + (view instanceof ImageView
                    ? " drawable=" + (((ImageView) view).getDrawable() == null ? "null"
                    : ((ImageView) view).getDrawable().getClass().getName())
                    : ""));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                dumpViews(group.getChildAt(i), depth + 1);
            }
        }
    }

    private static String shorten(Object value) {
        String text = String.valueOf(value);
        return text.length() > 120 ? text.substring(0, 120) + "…" : text;
    }
}
