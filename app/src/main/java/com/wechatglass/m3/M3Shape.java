package com.wechatglass.m3;

/** M3 Expressive corner-radius scale. Values are dp; {@link #FULL} means a pill. */
public final class M3Shape {

    public static final float NONE = 0f;
    public static final float EXTRA_SMALL = 4f;
    public static final float SMALL = 8f;
    public static final float MEDIUM = 12f;
    public static final float LARGE = 16f;
    public static final float LARGE_INCREASED = 20f;
    public static final float EXTRA_LARGE = 28f;
    public static final float EXTRA_LARGE_INCREASED = 32f;
    public static final float EXTRA_EXTRA_LARGE = 48f;
    /** Render as a fully rounded pill. */
    public static final float FULL = -1f;

    private M3Shape() {
    }
}
