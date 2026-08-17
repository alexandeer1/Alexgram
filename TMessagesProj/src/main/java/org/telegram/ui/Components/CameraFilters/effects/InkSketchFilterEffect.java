package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class InkSketchFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.CROSS_HATCH_SKETCH;
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
                float ink = 1.0f;
                if (lum < 0.8f && (x + y) % 8 == 0) ink -= 0.35f;
                if (lum < 0.5f && (x - y) % 8 == 0) ink -= 0.35f;
                if (lum < 0.25f && x % 4 == 0) ink -= 0.35f;

                int inkVal = Math.min(255, Math.max(0, (int) (ink * 255f)));

                int finalR = (int) (r * (1f - intensity) + inkVal * intensity);
                int finalG = (int) (g * (1f - intensity) + inkVal * intensity);
                int finalB = (int) (b * (1f - intensity) + inkVal * intensity);

                out[rowOffset + x] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
            }
        }
    }
}
