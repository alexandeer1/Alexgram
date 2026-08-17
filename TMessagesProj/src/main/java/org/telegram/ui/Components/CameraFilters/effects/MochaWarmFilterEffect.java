package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class MochaWarmFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.MOCHA_WARM;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int i = 0; i < in.length; i++) {
            int c = in[i];
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;

            float mr = (r / 255f) * 1.15f + 0.06f;
            float mg = (g / 255f) * 0.98f + 0.03f;
            float mb = (b / 255f) * 0.82f + 0.01f;

            int outR = Math.min(255, Math.max(0, (int) (mr * 255f)));
            int outG = Math.min(255, Math.max(0, (int) (mg * 255f)));
            int outB = Math.min(255, Math.max(0, (int) (mb * 255f)));

            int finalR = Math.min(255, (int) (r * (1f - intensity) + outR * intensity));
            int finalG = Math.min(255, (int) (g * (1f - intensity) + outG * intensity));
            int finalB = Math.min(255, (int) (b * (1f - intensity) + outB * intensity));

            out[i] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
        }
    }
}
