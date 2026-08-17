package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class NeonWireframeFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.NEON_WIREFRAME_GRID;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int y = 0; y < h; y++) {
            int rowOffset = y * w;
            boolean isGrid = (y > h * 0.55f) && ((y % 16 < 2) || (Math.abs(w / 2 - (y * w) / (h * 2)) % 24 < 2));
            for (int x = 0; x < w; x++) {
                int c = in[rowOffset + x];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;

                float outR = r * 1.15f + (isGrid ? 220f : 20f);
                float outG = g * 0.70f + (isGrid ? 30f : 0f);
                float outB = b * 1.30f + (isGrid ? 255f : 40f);

                int finalR = Math.min(255, (int) (r * (1f - intensity) + outR * intensity));
                int finalG = Math.min(255, (int) (g * (1f - intensity) + outG * intensity));
                int finalB = Math.min(255, (int) (b * (1f - intensity) + outB * intensity));

                out[rowOffset + x] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
            }
        }
    }
}
