package com.wechatglass.m3;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;

import java.util.Locale;

/**
 * The M3 colour roles used by this module.
 *
 * <p>On Android 12+ the palette comes from the system Monet resources
 * ({@code system_accent1_*} / {@code system_neutral1_*}). When those are not
 * available the scheme is generated from WeChat green {@code #07C160} with the
 * Material {@code FIDELITY} variant, which keeps {@code #07C160} as the
 * primary container and uses an accessible dark green for text.
 */
public final class M3ColorScheme {

    public final boolean dark;
    public final boolean dynamic;

    public final int primary;
    public final int onPrimary;
    public final int primaryContainer;
    public final int onPrimaryContainer;

    public final int secondary;
    public final int onSecondary;
    public final int secondaryContainer;
    public final int onSecondaryContainer;

    public final int tertiary;
    public final int onTertiary;
    public final int tertiaryContainer;
    public final int onTertiaryContainer;

    public final int error;
    public final int onError;
    public final int errorContainer;
    public final int onErrorContainer;

    public final int surface;
    public final int onSurface;
    public final int surfaceVariant;
    public final int onSurfaceVariant;

    public final int surfaceContainerLowest;
    public final int surfaceContainerLow;
    public final int surfaceContainer;
    public final int surfaceContainerHigh;
    public final int surfaceContainerHighest;

    public final int outline;
    public final int outlineVariant;

    public final int inverseSurface;
    public final int inverseOnSurface;
    public final int inversePrimary;

    public final int scrim;
    public final int shadow;
    public final int surfaceDim;
    public final int surfaceBright;

    private M3ColorScheme(boolean dark, boolean dynamic, int[] c) {
        this.dark = dark;
        this.dynamic = dynamic;
        this.primary = c[0];
        this.onPrimary = c[1];
        this.primaryContainer = c[2];
        this.onPrimaryContainer = c[3];
        this.secondary = c[4];
        this.onSecondary = c[5];
        this.secondaryContainer = c[6];
        this.onSecondaryContainer = c[7];
        this.tertiary = c[8];
        this.onTertiary = c[9];
        this.tertiaryContainer = c[10];
        this.onTertiaryContainer = c[11];
        this.error = c[12];
        this.onError = c[13];
        this.errorContainer = c[14];
        this.onErrorContainer = c[15];
        this.surface = c[16];
        this.onSurface = c[17];
        this.surfaceVariant = c[18];
        this.onSurfaceVariant = c[19];
        this.surfaceContainerLowest = c[20];
        this.surfaceContainerLow = c[21];
        this.surfaceContainer = c[22];
        this.surfaceContainerHigh = c[23];
        this.surfaceContainerHighest = c[24];
        this.outline = c[25];
        this.outlineVariant = c[26];
        this.inverseSurface = c[27];
        this.inverseOnSurface = c[28];
        this.inversePrimary = c[29];
        this.scrim = c[30];
        this.shadow = c[31];
        this.surfaceDim = c[32];
        this.surfaceBright = c[33];
    }

    public static final int DEFAULT_SEED = 0xFF07C160;

    public static M3ColorScheme forContext(Context context) {
        boolean dark = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        boolean monet = monetEnabled(context);
        int seed = seedColor(context);
        if (monet && Build.VERSION.SDK_INT >= 31) {
            try {
                return monet(context, dark);
            } catch (Throwable ignored) {
                // Some OEM ROMs ship without the system_* palette.
            }
        }
        return fromSeed(seed, dark);
    }

    /**
     * The hook stores colour config in WeChat's "wechatglass" prefs; the module
     * app stores it in "settings". Read whichever one actually has the keys.
     */
    private static android.content.SharedPreferences colorPrefs(Context context) {
        // LSPosed's XSharedPreferences sees the GUI's settings even when
        // WeChat was not running to receive the config broadcast. Prefer it,
        // otherwise a stale/default green value in WeChat's prefs wins.
        try {
            de.robv.android.xposed.XSharedPreferences module =
                    new de.robv.android.xposed.XSharedPreferences(
                            "io.github.yingxingyswx_design.wechatglass", "settings");
            module.reload();
            if (module.contains("color_monet") || module.contains("color_seed")) {
                return module;
            }
        } catch (Throwable ignored) {
        }
        android.content.SharedPreferences hook =
                context.getSharedPreferences("wechatglass", 0);
        if (hook.contains("color_monet") || hook.contains("color_seed")) {
            return hook;
        }
        return context.getSharedPreferences("settings", 0);
    }

    public static boolean monetEnabled(Context context) {
        return colorPrefs(context).getBoolean("color_monet", true);
    }

    public static int seedColor(Context context) {
        return colorPrefs(context).getInt("color_seed", DEFAULT_SEED);
    }

    /** One of the GUI swatches; unknown seeds fall back to WeChat green. */
    public static M3ColorScheme fromSeed(int seed, boolean dark) {
        int key = seed & 0x00FFFFFF;
        for (int i = 0; i < M3Palettes.SEEDS.length; i++) {
            if ((M3Palettes.SEEDS[i] & 0x00FFFFFF) == key) {
                return new M3ColorScheme(dark, false,
                        dark ? M3Palettes.DARK[i] : M3Palettes.LIGHT[i]);
            }
        }
        return fallback(dark);
    }

    /** Monet palette, mapped onto M3 tones. */
    private static M3ColorScheme monet(Context c, boolean dark) {
        int a1_0 = sys(c, android.R.color.system_accent1_0);
        int a1_100 = sys(c, android.R.color.system_accent1_100);
        int a1_200 = sys(c, android.R.color.system_accent1_200);
        int a1_300 = sys(c, android.R.color.system_accent1_300);
        int a1_400 = sys(c, android.R.color.system_accent1_400);
        int a1_600 = sys(c, android.R.color.system_accent1_600);
        int a1_700 = sys(c, android.R.color.system_accent1_700);
        int a1_800 = sys(c, android.R.color.system_accent1_800);
        int a1_900 = sys(c, android.R.color.system_accent1_900);
        int a1_1000 = sys(c, android.R.color.system_accent1_1000);

        int a2_0 = sys(c, android.R.color.system_accent2_0);
        int a2_100 = sys(c, android.R.color.system_accent2_100);
        int a2_200 = sys(c, android.R.color.system_accent2_200);
        int a2_300 = sys(c, android.R.color.system_accent2_300);
        int a2_400 = sys(c, android.R.color.system_accent2_400);
        int a2_600 = sys(c, android.R.color.system_accent2_600);
        int a2_700 = sys(c, android.R.color.system_accent2_700);
        int a2_800 = sys(c, android.R.color.system_accent2_800);
        int a2_900 = sys(c, android.R.color.system_accent2_900);
        int a2_1000 = sys(c, android.R.color.system_accent2_1000);

        int a3_0 = sys(c, android.R.color.system_accent3_0);
        int a3_100 = sys(c, android.R.color.system_accent3_100);
        int a3_200 = sys(c, android.R.color.system_accent3_200);
        int a3_300 = sys(c, android.R.color.system_accent3_300);
        int a3_400 = sys(c, android.R.color.system_accent3_400);
        int a3_600 = sys(c, android.R.color.system_accent3_600);
        int a3_700 = sys(c, android.R.color.system_accent3_700);
        int a3_800 = sys(c, android.R.color.system_accent3_800);
        int a3_900 = sys(c, android.R.color.system_accent3_900);
        int a3_1000 = sys(c, android.R.color.system_accent3_1000);

        int n1_0 = sys(c, android.R.color.system_neutral1_0);
        int n1_100 = sys(c, android.R.color.system_neutral1_100);
        int n1_200 = sys(c, android.R.color.system_neutral1_200);
        int n1_900 = sys(c, android.R.color.system_neutral1_900);
        int n1_1000 = sys(c, android.R.color.system_neutral1_1000);

        int n2_100 = sys(c, android.R.color.system_neutral2_100);
        int n2_200 = sys(c, android.R.color.system_neutral2_200);
        int n2_300 = sys(c, android.R.color.system_neutral2_300);
        int n2_500 = sys(c, android.R.color.system_neutral2_500);
        int n2_600 = sys(c, android.R.color.system_neutral2_600);
        int n2_800 = sys(c, android.R.color.system_neutral2_800);
        int n2_900 = sys(c, android.R.color.system_neutral2_900);

        int[] v = new int[34];
        if (!dark) {
            // Tone 40 / 100 / 90 / 10: accent values are named by
            // (100 - tone) * 10 on Android, so a1_600 is tone 40.
            v[0] = a1_600;
            v[1] = a1_0;
            v[2] = a1_100;
            v[3] = a1_900;
            v[4] = a2_600;
            v[5] = a2_0;
            v[6] = a2_100;
            v[7] = a2_900;
            v[8] = a3_600;
            v[9] = a3_0;
            v[10] = a3_100;
            v[11] = a3_900;
            v[16] = n1_0;
            v[17] = n1_1000;
            v[18] = n2_100;
            v[19] = n2_800;
            v[20] = n1_0;
            v[21] = mix(n1_0, n1_100, 0.08f);
            v[22] = mix(n1_0, n1_100, 0.12f);
            v[23] = mix(n1_0, n1_100, 0.16f);
            v[24] = mix(n1_0, n1_100, 0.20f);
            v[25] = n2_500;
            v[26] = n2_200;
            v[27] = n1_900;
            v[28] = n1_100;
            v[29] = a1_200;
            v[32] = mix(n1_0, n1_100, 0.32f);
            v[33] = n1_0;
        } else {
            v[0] = a1_200;
            v[1] = a1_900;
            v[2] = a1_700;
            v[3] = a1_100;
            v[4] = a2_200;
            v[5] = a2_900;
            v[6] = a2_700;
            v[7] = a2_100;
            v[8] = a3_200;
            v[9] = a3_900;
            v[10] = a3_700;
            v[11] = a3_100;
            v[16] = n1_900;
            v[17] = n1_0;
            v[18] = n2_800;
            v[19] = n2_300;
            v[20] = n1_1000;
            v[21] = mix(n1_900, n1_1000, 0.08f);
            v[22] = mix(n1_900, n1_1000, 0.12f);
            v[23] = mix(n1_900, n1_1000, 0.16f);
            v[24] = mix(n1_900, n1_1000, 0.20f);
            v[25] = n2_500;
            v[26] = n2_800;
            v[27] = n1_0;
            v[28] = n1_900;
            v[29] = a1_600;
            v[32] = n1_900;
            v[33] = mix(n1_900, n1_0, 0.24f);
        }
        error(v, dark);
        v[30] = 0xFF000000;
        v[31] = 0xFF000000;
        return new M3ColorScheme(dark, true, v);
    }

    /** WeChat-green FIDELITY scheme, used when Monet is unavailable. */
    public static M3ColorScheme fallback(boolean dark) {
        int[] v = new int[34];
        if (!dark) {
            v[0] = 0xFF006D33;
            v[1] = 0xFFFFFFFF;
            v[2] = 0xFF07C160;
            v[3] = 0xFF00471F;
            v[4] = 0xFF2F6A3F;
            v[5] = 0xFFFFFFFF;
            v[6] = 0xFFB1F2BB;
            v[7] = 0xFF357044;
            v[8] = 0xFFA23D33;
            v[9] = 0xFFFFFFFF;
            v[10] = 0xFFFF8475;
            v[11] = 0xFF741C16;
            v[16] = 0xFFF3FCF0;
            v[17] = 0xFF161D16;
            v[18] = 0xFFD7E7D5;
            v[19] = 0xFF3D4A3D;
            v[20] = 0xFFFFFFFF;
            v[21] = 0xFFEDF6EA;
            v[22] = 0xFFE8F0E4;
            v[23] = 0xFFE2EBDF;
            v[24] = 0xFFDCE5D9;
            v[25] = 0xFF6C7B6C;
            v[26] = 0xFFBBCBBA;
            v[27] = 0xFF2A322B;
            v[28] = 0xFFEAF3E7;
            v[29] = 0xFF45E17C;
            v[32] = 0xFFD4DDD1;
            v[33] = 0xFFF3FCF0;
        } else {
            v[0] = 0xFF45E17C;
            v[1] = 0xFF003917;
            v[2] = 0xFF07C160;
            v[3] = 0xFF00471F;
            v[4] = 0xFF96D5A0;
            v[5] = 0xFF003917;
            v[6] = 0xFF16542B;
            v[7] = 0xFF88C793;
            v[8] = 0xFFFFB4AA;
            v[9] = 0xFF630F0A;
            v[10] = 0xFFFF8475;
            v[11] = 0xFF741C16;
            v[16] = 0xFF0E150E;
            v[17] = 0xFFDCE5D9;
            v[18] = 0xFF3D4A3D;
            v[19] = 0xFFBBCBBA;
            v[20] = 0xFF081009;
            v[21] = 0xFF161D16;
            v[22] = 0xFF1A221A;
            v[23] = 0xFF242C24;
            v[24] = 0xFF2F372F;
            v[25] = 0xFF869585;
            v[26] = 0xFF3D4A3D;
            v[27] = 0xFFDCE5D9;
            v[28] = 0xFF2A322B;
            v[29] = 0xFF006D33;
            v[32] = 0xFF0E150E;
            v[33] = 0xFF333B33;
        }
        error(v, dark);
        v[30] = 0xFF000000;
        v[31] = 0xFF000000;
        return new M3ColorScheme(dark, false, v);
    }

    private static void error(int[] v, boolean dark) {
        v[12] = dark ? 0xFFFFB4AB : 0xFFBA1A1A;
        v[13] = dark ? 0xFF690005 : 0xFFFFFFFF;
        v[14] = dark ? 0xFF93000A : 0xFFFFDAD6;
        v[15] = dark ? 0xFFFFDAD6 : 0xFF93000A;
    }

    private static int sys(Context c, int id) {
        return c.getColor(id);
    }

    public static int mix(int a, int b, float t) {
        float q = Math.max(0f, Math.min(1f, t));
        int r = Math.round(Color.red(a) * (1f - q) + Color.red(b) * q);
        int g = Math.round(Color.green(a) * (1f - q) + Color.green(b) * q);
        int bl = Math.round(Color.blue(a) * (1f - q) + Color.blue(b) * q);
        return Color.rgb(r, g, bl);
    }

    public static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    public String describe() {
        return String.format(Locale.US,
                "M3ColorScheme dark=%s dynamic=%s primary=#%06X surface=#%06X",
                dark, dynamic, primary & 0xFFFFFF, surface & 0xFFFFFF);
    }
}
