package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class VaporwaveFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.VAPORWAVE;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int y = 0; y < h; y++) {
            float v = (y + 0.5f) / h;
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                int c = in[idx];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                float lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;

                float gr = (1f - v) * 1.0f + v * 0.05f;
                float gg = (1f - v) * 0.15f + v * 0.85f;
                float gb = (1f - v) * 0.75f + v * 1.0f;

                float outR = (lum * gr * 1.4f) * 255f;
                float outG = (lum * gg * 1.2f) * 255f;
                float outB = (lum * gb * 1.5f) * 255f;

                int finalR = Math.min(255, (int) (r * (1f - intensity) + outR * intensity));
                int finalG = Math.min(255, (int) (g * (1f - intensity) + outG * intensity));
                int finalB = Math.min(255, (int) (b * (1f - intensity) + outB * intensity));

                out[idx] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
            }
        }
    }
}
