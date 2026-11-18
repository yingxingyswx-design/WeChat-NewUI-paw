package com.wechatglass;

import android.graphics.Bitmap;

/**
 * Small, dependency-free stack blur. It is applied to the already downscaled
 * screenshot (usually 1/4 size), so three box passes are cheap enough for the
 * chat list scroll rate.
 */
final class BlurUtil {

    private BlurUtil() {
    }

    static void stackBlur(Bitmap bitmap, int radius) {
        if (bitmap == null || radius < 1) {
            return;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width < 2 || height < 2) {
            return;
        }

        int[] pixels = new int[width * height];
        int[] temp = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int pass = 0; pass < 3; pass++) {
            boxBlurHorizontal(pixels, temp, width, height, radius);
            boxBlurVertical(temp, pixels, width, height, radius);
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    }

    private static void boxBlurHorizontal(int[] in, int[] out, int width, int height, int radius) {
        int window = radius * 2 + 1;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            long sumA = 0;
            long sumR = 0;
            long sumG = 0;
            long sumB = 0;

            for (int i = -radius; i <= radius; i++) {
                int color = in[row + clamp(i, width)];
                sumA += (color >>> 24) & 0xff;
                sumR += (color >> 16) & 0xff;
                sumG += (color >> 8) & 0xff;
                sumB += color & 0xff;
            }

            for (int x = 0; x < width; x++) {
                out[row + x] = ((int) (sumA / window) << 24)
                        | ((int) (sumR / window) << 16)
                        | ((int) (sumG / window) << 8)
                        | (int) (sumB / window);

                int remove = in[row + clamp(x - radius, width)];
                int add = in[row + clamp(x + radius + 1, width)];
                sumA += ((add >>> 24) & 0xff) - ((remove >>> 24) & 0xff);
                sumR += ((add >> 16) & 0xff) - ((remove >> 16) & 0xff);
                sumG += ((add >> 8) & 0xff) - ((remove >> 8) & 0xff);
                sumB += (add & 0xff) - (remove & 0xff);
            }
        }
    }

    private static void boxBlurVertical(int[] in, int[] out, int width, int height, int radius) {
        int window = radius * 2 + 1;
        for (int x = 0; x < width; x++) {
            long sumA = 0;
            long sumR = 0;
            long sumG = 0;
            long sumB = 0;

            for (int i = -radius; i <= radius; i++) {
                int color = in[clamp(i, height) * width + x];
                sumA += (color >>> 24) & 0xff;
                sumR += (color >> 16) & 0xff;
                sumG += (color >> 8) & 0xff;
                sumB += color & 0xff;
            }

            for (int y = 0; y < height; y++) {
                out[y * width + x] = ((int) (sumA / window) << 24)
                        | ((int) (sumR / window) << 16)
                        | ((int) (sumG / window) << 8)
                        | (int) (sumB / window);

                int remove = in[clamp(y - radius, height) * width + x];
                int add = in[clamp(y + radius + 1, height) * width + x];
                sumA += ((add >>> 24) & 0xff) - ((remove >>> 24) & 0xff);
                sumR += ((add >> 16) & 0xff) - ((remove >> 16) & 0xff);
                sumG += ((add >> 8) & 0xff) - ((remove >> 8) & 0xff);
                sumB += (add & 0xff) - (remove & 0xff);
            }
        }
    }

    private static int clamp(int value, int max) {
        if (value < 0) {
            return 0;
        }
        if (value >= max) {
            return max - 1;
        }
        return value;
    }
}
