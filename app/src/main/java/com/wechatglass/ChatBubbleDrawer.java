package com.wechatglass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.wechatglass.m3.M3ColorScheme;
import com.wechatglass.m3.M3Shape;
import com.wechatglass.m3.M3Theme;

/**
 * Draws the bubble before WeChat draws the chat item. This deliberately avoids
 * the item's view hierarchy: no ids, no reparenting, no container search.
 */
final class ChatBubbleDrawer {

    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path TAIL = new Path();
    private static final RectF RECT = new RectF();
    private static boolean logged;

    private ChatBubbleDrawer() {
    }

    static void draw(Canvas canvas, ViewGroup parent, View child) {
        if (parent == null || child == null
                || !parent.getClass().getName().contains("ScrollControlRecyclerView")) {
            return;
        }
        TextView text = findText(child);
        if (text == null || text.getWidth() <= 0 || text.getHeight() <= 0) {
            return;
        }
        CharSequence cs = text.getText();
        if (cs == null || cs.length() == 0
                || ChatBubbleStyler.isChromeText(cs.toString())) {
            return;
        }
        int[] pl = new int[2];
        int[] tl = new int[2];
        parent.getLocationOnScreen(pl);
        text.getLocationOnScreen(tl);
        float left = tl[0] - pl[0] - M3Theme.dpF(parent.getContext(), 12f);
        float top = tl[1] - pl[1] - M3Theme.dpF(parent.getContext(), 8f);
        float right = tl[0] - pl[0] + text.getWidth()
                + M3Theme.dpF(parent.getContext(), 12f);
        float bottom = tl[1] - pl[1] + text.getHeight()
                + M3Theme.dpF(parent.getContext(), 8f);
        boolean outgoing = (left + right) / 2f > parent.getWidth() / 2f;

        Context context = parent.getContext();
        M3ColorScheme scheme = M3Theme.colors(context);
        int color = outgoing
                ? M3ColorScheme.mix(scheme.surface, scheme.primary, 0.18f)
                : scheme.surfaceContainerHighest;

        // Remove WeChat's own bubble backing from the text and its ancestors.
        View v = text;
        while (v != null && v != parent) {
            if (v.getBackground() != null) {
                v.setBackground(null);
            }
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }

        float radius = M3Theme.dpF(context, M3Shape.LARGE_INCREASED);
        FILL.setColor(color);
        RECT.set(left, top, right, bottom);
        canvas.drawRoundRect(RECT, radius, radius, FILL);

        // MD soul: a small tail, same color, on the sender's side.
        float tail = M3Theme.dpF(context, 7f);
        float tailTop = bottom - M3Theme.dpF(context, 18f);
        TAIL.reset();
        if (outgoing) {
            TAIL.moveTo(right - 1f, tailTop);
            TAIL.lineTo(right + tail, tailTop + tail);
            TAIL.lineTo(right - 1f, tailTop + tail * 2f);
        } else {
            TAIL.moveTo(left + 1f, tailTop);
            TAIL.lineTo(left - tail, tailTop + tail);
            TAIL.lineTo(left + 1f, tailTop + tail * 2f);
        }
        TAIL.close();
        canvas.drawPath(TAIL, FILL);

        if (!logged) {
            logged = true;
            android.util.Log.i("WeChatGlass", "CB drawChild txt=" + cs
                    + " outgoing=" + outgoing + " rect=" + RECT);
        }
    }

    private static TextView findText(View view) {
        if (view instanceof TextView) {
            CharSequence cs = ((TextView) view).getText();
            if (cs != null && cs.length() > 0) {
                return (TextView) view;
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
