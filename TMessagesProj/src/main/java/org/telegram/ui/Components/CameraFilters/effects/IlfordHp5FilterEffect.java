package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class IlfordHp5FilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.ILFORD_HP5;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int i = 0; i < in.length; i++) {
            int c = in[i];
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;

            float lum = (r * 0.299f + g * 0.587f + b * 0.114f) / 255.0f;
            float contrastLum = (lum - 0.5f) * 1.45f + 0.5f;
            float outMono = Math.min(255f, Math.max(0f, contrastLum * 255f));

            int finalR = (int) (r * (1f - intensity) + outMono * intensity);
            int finalG = (int) (g * (1f - intensity) + outMono * intensity);
            int finalB = (int) (b * (1f - intensity) + outMono * intensity);

            out[i] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
        }
    }
}
