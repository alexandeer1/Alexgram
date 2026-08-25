package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class Portra400FilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.PORTRA_400;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int i = 0; i < in.length; i++) {
            int c = in[i];
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;

            float pr = (r / 255f) * 1.12f + 0.04f;
            float pg = (g / 255f) * 1.05f + 0.02f;
            float pb = (b / 255f) * 0.92f + 0.05f;

            float outR = Math.min(1f, Math.max(0f, pr)) * 255f;
            float outG = Math.min(1f, Math.max(0f, pg)) * 255f;
            float outB = Math.min(1f, Math.max(0f, pb)) * 255f;

            int finalR = Math.min(255, (int) (r * (1f - intensity) + outR * intensity));
            int finalG = Math.min(255, (int) (g * (1f - intensity) + outG * intensity));
            int finalB = Math.min(255, (int) (b * (1f - intensity) + outB * intensity));

            out[i] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
        }
    }
}
