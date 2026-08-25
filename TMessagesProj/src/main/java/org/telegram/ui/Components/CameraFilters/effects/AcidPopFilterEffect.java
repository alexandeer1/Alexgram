package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class AcidPopFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.ACID_POP;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int i = 0; i < in.length; i++) {
            int c = in[i];
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;

            float ar = (r / 255f) * 1.35f;
            float ag = (g / 255f) * 1.35f;
            float ab = (b / 255f) * 1.35f;

            float avg = (ar + ag + ab) / 3f;
            ar = avg + (ar - avg) * 1.6f;
            ag = avg + (ag - avg) * 1.6f;
            ab = avg + (ab - avg) * 1.6f;

            int outR = Math.min(255, Math.max(0, (int) (ar * 255f)));
            int outG = Math.min(255, Math.max(0, (int) (ag * 255f)));
            int outB = Math.min(255, Math.max(0, (int) (ab * 255f)));

            int finalR = Math.min(255, (int) (r * (1f - intensity) + outR * intensity));
            int finalG = Math.min(255, (int) (g * (1f - intensity) + outG * intensity));
            int finalB = Math.min(255, (int) (b * (1f - intensity) + outB * intensity));

            out[i] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
        }
    }
}
