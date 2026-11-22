package com.wechatglass.m3;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;

/** Small drawable helpers shared by the hand-drawn M3 components. */
public final class M3Drawable {

    private M3Drawable() {
    }

    public static GradientDrawable fill(Context context, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(radiusPx(context, radiusDp));
        return d;
    }

    public static GradientDrawable outline(Context context, int color, float widthDp,
                                           float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(Color.TRANSPARENT);
        d.setCornerRadius(radiusPx(context, radiusDp));
        d.setStroke(Math.max(1, M3Theme.dp(context, widthDp)), color);
        return d;
    }

    public static RippleDrawable ripple(Context context, int color, Drawable content,
                                        float radiusDp) {
        int pressed = M3ColorScheme.withAlpha(color, 0.10f);
        Drawable mask = fill(context, Color.WHITE, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(pressed), content, mask);
    }

    public static Drawable pressedOverlay(Context context, int color, float radiusDp,
                                          float alpha) {
        return fill(context, M3ColorScheme.withAlpha(color, alpha), radiusDp);
    }

    /** Resolve a dp radius, treating {@link M3Shape#FULL} as a pill. */
    public static float radiusPx(Context context, float radiusDp) {
        if (radiusDp == M3Shape.FULL) {
            return 10000f;
        }
        return M3Theme.dpF(context, radiusDp);
    }
}
