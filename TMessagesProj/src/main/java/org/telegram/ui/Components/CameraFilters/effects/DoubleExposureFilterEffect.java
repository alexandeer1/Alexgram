package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class DoubleExposureFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.DOUBLE_EXPOSURE;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        int shift = Math.round(18 * intensity);
        for (int y = 0; y < h; y++) {
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                int c = in[rowOffset + x];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;

                int ghostX = Math.min(w - 1, x + shift);
                int ghostC = in[rowOffset + ghostX];
                int gR = (ghostC >> 16) & 0xFF;
                int gB = ghostC & 0xFF;

                float outR = Math.min(255, r * 0.7f + gR * 0.6f);
                float outG = g * 0.9f;
                float outB = Math.min(255, b * 0.7f + gB * 0.6f);

                int finalR = (int) (r * (1f - intensity) + outR * intensity);
                int finalG = (int) (g * (1f - intensity) + outG * intensity);
                int finalB = (int) (b * (1f - intensity) + outB * intensity);

                out[rowOffset + x] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
            }
        }
    }
}
