package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class SunsetPeachFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.SUNSET_PEACH;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int i = 0; i < in.length; i++) {
            int c = in[i];
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;

            float pr = (r / 255f) * 1.18f + 0.08f;
            float pg = (g / 255f) * 1.02f + 0.04f;
            float pb = (b / 255f) * 0.90f + 0.02f;

            int outR = Math.min(255, Math.max(0, (int) (pr * 255f)));
            int outG = Math.min(255, Math.max(0, (int) (pg * 255f)));
            int outB = Math.min(255, Math.max(0, (int) (pb * 255f)));

            int finalR = Math.min(255, (int) (r * (1f - intensity) + outR * intensity));
            int finalG = Math.min(255, (int) (g * (1f - intensity) + outG * intensity));
            int finalB = Math.min(255, (int) (b * (1f - intensity) + outB * intensity));

            out[i] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
        }
    }
}
