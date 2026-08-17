package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class DuotoneNeonFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.DUOTONE_NEON;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int i = 0; i < in.length; i++) {
            int c = in[i];
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;
            float lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;

            float dr = (1f - lum) * 0.30f + lum * 1.00f;
            float dg = (1f - lum) * 0.00f + lum * 0.80f;
            float db = (1f - lum) * 0.80f + lum * 0.10f;

            float outR = (dr * lum * 1.4f) * 255f;
            float outG = (dg * lum * 1.4f) * 255f;
            float outB = (db * lum * 1.4f) * 255f;

            int finalR = Math.min(255, (int) (r * (1f - intensity) + outR * intensity));
            int finalG = Math.min(255, (int) (g * (1f - intensity) + outG * intensity));
            int finalB = Math.min(255, (int) (b * (1f - intensity) + outB * intensity));

            out[i] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
        }
    }
}
