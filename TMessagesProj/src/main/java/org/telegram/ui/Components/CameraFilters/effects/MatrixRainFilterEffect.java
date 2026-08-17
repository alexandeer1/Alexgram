package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class MatrixRainFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.CYBER_MATRIX_RAIN;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int y = 0; y < h; y++) {
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                int c = in[rowOffset + x];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;

                float lum = (r * 0.299f + g * 0.587f + b * 0.114f) / 255f;
                float rainFactor = ((x / 12 + y / 6) % 7 == 0) ? 1.45f : 0.85f;

                float outR = lum * 20f;
                float outG = Math.min(255f, lum * 240f * rainFactor + 30f);
                float outB = lum * 40f;

                int finalR = (int) (r * (1f - intensity) + outR * intensity);
                int finalG = (int) (g * (1f - intensity) + outG * intensity);
                int finalB = (int) (b * (1f - intensity) + outB * intensity);

                out[rowOffset + x] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
            }
        }
    }
}
