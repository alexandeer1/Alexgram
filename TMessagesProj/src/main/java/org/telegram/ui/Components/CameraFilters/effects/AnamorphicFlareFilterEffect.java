package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class AnamorphicFlareFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.ANAMORPHIC_FLARE;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int y = 0; y < h; y++) {
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                int i = rowOffset + x;
                int c = in[i];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;

                // Anamorphic horizontal streak bloom
                float streak = (float) Math.pow((r + g + b) / (3f * 255f), 3.0) * 120f;
                float outR = r * 0.88f + 4f;
                float outG = g * 0.98f + streak * 0.45f + 8f;
                float outB = b * 1.25f + streak * 1.10f + 25f;

                int finalR = Math.min(255, Math.max(0, (int) (r * (1f - intensity) + outR * intensity)));
                int finalG = Math.min(255, Math.max(0, (int) (g * (1f - intensity) + outG * intensity)));
                int finalB = Math.min(255, Math.max(0, (int) (b * (1f - intensity) + outB * intensity)));

                out[i] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
            }
        }
    }
}
