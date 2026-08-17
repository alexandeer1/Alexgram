package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class EmeraldForestFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.EMERALD_FOREST;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int i = 0; i < in.length; i++) {
            int c = in[i];
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;

            float er = (r / 255f) * 0.85f - 0.02f;
            float eg = (g / 255f) * 1.18f + 0.05f;
            float eb = (b / 255f) * 0.95f + 0.02f;

            int outR = Math.min(255, Math.max(0, (int) (er * 255f)));
            int outG = Math.min(255, Math.max(0, (int) (eg * 255f)));
            int outB = Math.min(255, Math.max(0, (int) (eb * 255f)));

            int finalR = Math.min(255, (int) (r * (1f - intensity) + outR * intensity));
            int finalG = Math.min(255, (int) (g * (1f - intensity) + outG * intensity));
            int finalB = Math.min(255, (int) (b * (1f - intensity) + outB * intensity));

            out[i] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
        }
    }
}
