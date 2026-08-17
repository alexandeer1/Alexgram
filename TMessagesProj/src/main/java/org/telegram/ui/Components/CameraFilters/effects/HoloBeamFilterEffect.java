package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class HoloBeamFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.HOLOGRAM_BLUE_GLITCH;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int y = 0; y < h; y++) {
            int rowOffset = y * w;
            float scanline = (y % 4 < 2) ? 0.70f : 1.25f;
            int jitter = (y % 8 == 0) ? Math.round(6 * intensity) : 0;
            for (int x = 0; x < w; x++) {
                int srcX = Math.max(0, Math.min(w - 1, x + jitter));
                int c = in[rowOffset + srcX];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;

                float lum = (r * 0.299f + g * 0.587f + b * 0.114f);
                float outR = lum * 0.15f * scanline;
                float outG = lum * 0.90f * scanline + 15f;
                float outB = Math.min(255f, lum * 1.45f * scanline + 55f);

                int origC = in[rowOffset + x];
                int oR = (origC >> 16) & 0xFF;
                int oG = (origC >> 8) & 0xFF;
                int oB = origC & 0xFF;

                int finalR = (int) (oR * (1f - intensity) + outR * intensity);
                int finalG = (int) (oG * (1f - intensity) + outG * intensity);
                int finalB = (int) (oB * (1f - intensity) + outB * intensity);

                out[rowOffset + x] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
            }
        }
    }
}
