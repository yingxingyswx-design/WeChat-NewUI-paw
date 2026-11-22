package com.wechatglass.m3;

import android.graphics.Typeface;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * The 15 baseline M3 type styles plus their emphasized counterparts. Sizes and
 * line heights are the official token values; emphasized keeps the size and
 * line height and only raises weight / tracking.
 */
public final class M3Type {

    public static final int DISPLAY_LARGE = 0;
    public static final int DISPLAY_MEDIUM = 1;
    public static final int DISPLAY_SMALL = 2;
    public static final int HEADLINE_LARGE = 3;
    public static final int HEADLINE_MEDIUM = 4;
    public static final int HEADLINE_SMALL = 5;
    public static final int TITLE_LARGE = 6;
    public static final int TITLE_MEDIUM = 7;
    public static final int TITLE_SMALL = 8;
    public static final int BODY_LARGE = 9;
    public static final int BODY_MEDIUM = 10;
    public static final int BODY_SMALL = 11;
    public static final int LABEL_LARGE = 12;
    public static final int LABEL_MEDIUM = 13;
    public static final int LABEL_SMALL = 14;

    private static final float[] SIZE = {
            57f, 45f, 36f, 32f, 28f, 24f, 22f, 16f, 14f, 16f, 14f, 12f, 14f, 12f, 11f
    };
    private static final float[] LINE = {
            64f, 52f, 44f, 40f, 36f, 32f, 28f, 24f, 20f, 24f, 20f, 16f, 20f, 16f, 16f
    };
    private static final float[] TRACKING = {
            -0.2f, 0f, 0f, 0f, 0f, 0f, 0f, 0.2f, 0.1f, 0.5f, 0.2f, 0.4f, 0.1f, 0.5f, 0.5f
    };
    private static final int[] WEIGHT = {
            400, 400, 400, 400, 400, 400, 400, 500, 500, 400, 400, 400, 500, 500, 500
    };
    private static final float[] EMPH_TRACKING = {
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.15f, 0.1f, 0.15f, 0.25f, 0.4f, 0.1f, 0.5f, 0.5f
    };
    private static final int[] EMPH_WEIGHT = {
            500, 500, 500, 500, 500, 500, 500, 700, 700, 500, 500, 500, 700, 700, 700
    };

    private M3Type() {
    }

    public static void apply(TextView view, int style) {
        apply(view, style, false);
    }

    public static void apply(TextView view, int style, boolean emphasized) {
        if (view == null || style < 0 || style >= SIZE.length) {
            return;
        }
        float size = SIZE[style];
        float line = LINE[style];
        float tracking = emphasized ? EMPH_TRACKING[style] : TRACKING[style];
        int weight = emphasized ? EMPH_WEIGHT[style] : WEIGHT[style];
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setLetterSpacing(tracking / size);
        view.setLineSpacing(0f, line / size);
        view.setIncludeFontPadding(false);
        view.setTypeface(typeface(weight));
    }

    public static float sizeSp(int style) {
        return SIZE[style];
    }

    public static float lineHeightSp(int style) {
        return LINE[style];
    }

    public static Typeface typeface(int weight) {
        if (weight >= 700) {
            return Typeface.create("sans-serif", Typeface.BOLD);
        }
        if (weight >= 500) {
            return Typeface.create("sans-serif-medium", Typeface.NORMAL);
        }
        return Typeface.create("sans-serif", Typeface.NORMAL);
    }
}
