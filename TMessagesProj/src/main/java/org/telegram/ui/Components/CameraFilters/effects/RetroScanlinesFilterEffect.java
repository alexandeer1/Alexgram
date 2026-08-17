package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class RetroScanlinesFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.RETRO_SCANLINES;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int y = 0; y < h; y++) {
            float scanFactor = (y % 4 < 2) ? 0.78f : 1.12f;
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                int i = rowOffset + x;
                int c = in[i];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;

                float outR = r * scanFactor * 1.05f;
                float outG = g * scanFactor * 1.12f;
                float outB = b * scanFactor * 1.02f;

                int finalR = Math.min(255, Math.max(0, (int) (r * (1f - intensity) + outR * intensity)));
                int finalG = Math.min(255, Math.max(0, (int) (g * (1f - intensity) + outG * intensity)));
                int finalB = Math.min(255, Math.max(0, (int) (b * (1f - intensity) + outB * intensity)));

                out[i] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
            }
        }
    }
}
