package com.wechatglass;

import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.wechatglass.m3.M3ColorScheme;
import com.wechatglass.m3.M3Shape;
import com.wechatglass.m3.M3Theme;

/**
 * C fallback. WeChat's ScrollControlRecyclerView extends its own copy of
 * RecyclerView, so adding an androidx ItemDecoration is not reliable across
 * class loaders. Instead this is called from a hook on the list's onDraw,
 * before WeChat draws its children.
 */
final class ChatBubblePainter {

    private static boolean logged;

    private ChatBubblePainter() {
    }

    static void draw(Canvas canvas, ViewGroup list) {
        if (canvas == null || list == null || !ChatBubbleStyler.isActive()) {
            return;
        }
        if (!logged) {
            logged = true;
            android.util.Log.i("WeChatGlass", "CB painter children="
                    + list.getChildCount());
        }
        int screen = list.getResources().getDisplayMetrics().widthPixels;
        int[] pl = new int[2];
        list.getLocationOnScreen(pl);
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            TextView text = bestText(child, null, 0);
            if (text == null) {
                continue;
            }
            int[] tl = new int[2];
            text.getLocationOnScreen(tl);
            float left = tl[0] - pl[0] - M3Theme.dpF(list.getContext(), 12f);
            float top = tl[1] - pl[1] - M3Theme.dpF(list.getContext(), 8f);
            float right = tl[0] - pl[0] + text.getWidth()
                    + M3Theme.dpF(list.getContext(), 12f);
            float bottom = tl[1] - pl[1] + text.getHeight()
                    + M3Theme.dpF(list.getContext(), 8f);
            if (right <= left || bottom <= top) {
                continue;
            }
            boolean outgoing = (left + right) / 2f > screen / 2f;
            M3ColorScheme scheme = M3Theme.colors(list.getContext());
            int color = outgoing
                    ? M3ColorScheme.mix(scheme.surface, scheme.primary, 0.18f)
                    : scheme.surfaceContainerHighest;

            clearBackgrounds(text, child);
            BubbleDrawable bubble = new BubbleDrawable(color,
                    M3Theme.dpF(list.getContext(), M3Shape.LARGE_INCREASED),
                    M3Theme.dpF(list.getContext(), 7f), outgoing);
            bubble.setBounds(Math.round(left), Math.round(top),
                    Math.round(right), Math.round(bottom));
            bubble.draw(canvas);
        }
    }

    private static void clearBackgrounds(View text, View child) {
        View cur = text;
        while (cur != null) {
            if (cur.getBackground() != null) {
                cur.setBackground(null);
            }
            if (cur == child) {
                break;
            }
            cur = cur.getParent() instanceof View ? (View) cur.getParent() : null;
        }
    }

    private static TextView bestText(View view, TextView best, int bestScore) {
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            CharSequence cs = tv.getText();
            if (cs != null && cs.length() > 0
                    && tv.getVisibility() == View.VISIBLE
                    && tv.getWidth() > 0 && tv.getHeight() > 0
                    && !ChatBubbleStyler.isChromeText(cs.toString())) {
                int score = tv.getWidth() * Math.max(1, cs.length());
                if (score > bestScore) {
                    return tv;
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = bestText(group.getChildAt(i), best, bestScore);
                if (found != null) {
                    best = found;
                    bestScore = found.getWidth() * Math.max(1,
                            found.getText() == null ? 1 : found.getText().length());
                }
            }
        }
        return best;
    }
}
