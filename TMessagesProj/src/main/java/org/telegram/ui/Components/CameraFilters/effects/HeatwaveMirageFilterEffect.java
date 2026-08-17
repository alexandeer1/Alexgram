package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class HeatwaveMirageFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.HEATWAVE_MIRAGE;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int y = 0; y < h; y++) {
            int waveX = (int) (Math.sin(y * 0.06f) * 12f * intensity);
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                int srcX = Math.max(0, Math.min(w - 1, x + waveX));
                int c = in[rowOffset + srcX];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;

                float outR = r * 1.18f + 14f;
                float outG = g * 1.05f + 8f;
                float outB = b * 0.85f;

                int origC = in[rowOffset + x];
                int oR = (origC >> 16) & 0xFF;
                int oG = (origC >> 8) & 0xFF;
                int oB = origC & 0xFF;

                int finalR = Math.min(255, (int) (oR * (1f - intensity) + outR * intensity));
                int finalG = Math.min(255, (int) (oG * (1f - intensity) + outG * intensity));
                int finalB = Math.min(255, (int) (oB * (1f - intensity) + outB * intensity));

                out[rowOffset + x] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
            }
        }
    }
}
