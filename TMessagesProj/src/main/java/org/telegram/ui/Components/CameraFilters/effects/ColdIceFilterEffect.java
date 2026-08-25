package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class ColdIceFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.COLD_ICE;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int i = 0; i < in.length; i++) {
            int c = in[i];
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;

            float outR = Math.max(0f, r * 0.85f - 10.2f);
            float outG = g * 1.05f + 7.6f;
            float outB = b * 1.35f + 28.1f;

            int finalR = Math.min(255, Math.max(0, (int) (r * (1f - intensity) + outR * intensity)));
            int finalG = Math.min(255, Math.max(0, (int) (g * (1f - intensity) + outG * intensity)));
            int finalB = Math.min(255, Math.max(0, (int) (b * (1f - intensity) + outB * intensity)));

            out[i] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
        }
    }
}
