package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class SinCityRedFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.SINCITY_RED;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int i = 0; i < in.length; i++) {
            int c = in[i];
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;
            float lum = (0.299f * r + 0.587f * g + 0.114f * b);

            float mono = Math.min(255f, Math.max(0f, (lum - 30f) * 1.35f));

            float redDominance = (r - Math.max(g, b)) / 255f;
            int outR, outG, outB;
            if (redDominance > 0.15f && r > 90) {
                outR = Math.min(255, (int) (r * 1.25f + 20));
                outG = (int) (g * 0.35f);
                outB = (int) (b * 0.35f);
            } else {
                outR = (int) mono;
                outG = (int) mono;
                outB = (int) mono;
            }

            int finalR = Math.min(255, (int) (r * (1f - intensity) + outR * intensity));
            int finalG = Math.min(255, (int) (g * (1f - intensity) + outG * intensity));
            int finalB = Math.min(255, (int) (b * (1f - intensity) + outB * intensity));

            out[i] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
        }
    }
}
