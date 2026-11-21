package com.wechatglass;

import android.graphics.RecordingCanvas;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;

/**
 * Replays the display lists already recorded by a content container into a
 * RenderNode. The container must not contain our bars, or this recurses.
 */
final class BackdropLite {

    private static Method sDrawChild;
    private static boolean sResolved;

    private BackdropLite() {
    }

    static void draw(RecordingCanvas canvas, ViewGroup root,
                     int left, int top, int width, int height) {
        if (canvas == null || root == null || width <= 0 || height <= 0) {
            return;
        }
        int[] loc = new int[2];
        root.getLocationOnScreen(loc);
        int save = canvas.save();
        try {
            canvas.translate(loc[0] - left, loc[1] - top);
            canvas.clipRect(left - loc[0], top - loc[1],
                    left - loc[0] + width, top - loc[1] + height);
            Method drawChild = drawChild();
            long time = root.getDrawingTime();
            for (int i = 0; i < root.getChildCount(); i++) {
                View child = root.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE
                        || child.getWidth() <= 0 || child.getHeight() <= 0) {
                    continue;
                }
                if (drawChild != null) {
                    drawChild.invoke(root, canvas, child, time);
                } else {
                    child.draw(canvas);
                }
            }
        } catch (Throwable ignored) {
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private static Method drawChild() {
        if (!sResolved) {
            sResolved = true;
            try {
                Method m = ViewGroup.class.getDeclaredMethod(
                        "drawChild", android.graphics.Canvas.class, View.class, long.class);
                m.setAccessible(true);
                sDrawChild = m;
            } catch (Throwable ignored) {
            }
        }
        return sDrawChild;
    }
}
