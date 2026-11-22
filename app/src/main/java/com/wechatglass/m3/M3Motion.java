package com.wechatglass.m3;

import com.wechatglass.liquid.Spring;

/**
 * M3 Expressive motion tokens. Spatial springs move geometry and are allowed to
 * overshoot; effects springs animate colour/alpha and are always critically
 * damped (damping 1.0), exactly as the Material token set specifies.
 */
public final class M3Motion {

    private static final float THRESHOLD = 0.003f;

    private M3Motion() {
    }

    public static Spring expressiveSpatialDefault(float initial) {
        return new Spring(0.8f, 380f, THRESHOLD, initial);
    }

    public static Spring expressiveSpatialFast(float initial) {
        return new Spring(0.6f, 800f, THRESHOLD, initial);
    }

    public static Spring expressiveSpatialSlow(float initial) {
        return new Spring(0.8f, 200f, THRESHOLD, initial);
    }

    public static Spring expressiveEffectsDefault(float initial) {
        return new Spring(1f, 1600f, THRESHOLD, initial);
    }

    public static Spring expressiveEffectsFast(float initial) {
        return new Spring(1f, 3800f, THRESHOLD, initial);
    }

    public static Spring expressiveEffectsSlow(float initial) {
        return new Spring(1f, 800f, THRESHOLD, initial);
    }

    public static Spring standardSpatialDefault(float initial) {
        return new Spring(0.9f, 700f, THRESHOLD, initial);
    }

    public static Spring standardSpatialFast(float initial) {
        return new Spring(0.9f, 1400f, THRESHOLD, initial);
    }

    public static Spring standardSpatialSlow(float initial) {
        return new Spring(0.9f, 300f, THRESHOLD, initial);
    }
}
