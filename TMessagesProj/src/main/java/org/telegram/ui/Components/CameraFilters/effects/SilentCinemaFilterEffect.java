package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class SilentCinemaFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.VINTAGE_SEPIA_FILM_SCRATCH;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int y = 0; y < h; y++) {
            int rowOffset = y * w;
            boolean scratch = (y % 40 == 0);
            for (int x = 0; x < w; x++) {
                int c = in[rowOffset + x];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;

                float lum = (r * 0.299f + g * 0.587f + b * 0.114f);
                float outR = (scratch ? 255f : lum * 1.15f + 25f);
                float outG = (scratch ? 240f : lum * 0.95f + 14f);
                float outB = (scratch ? 210f : lum * 0.65f + 4f);

                int finalR = Math.min(255, (int) (r * (1f - intensity) + outR * intensity));
                int finalG = Math.min(255, (int) (g * (1f - intensity) + outG * intensity));
                int finalB = Math.min(255, (int) (b * (1f - intensity) + outB * intensity));

                out[rowOffset + x] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
            }
        }
    }
}
