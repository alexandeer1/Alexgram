package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class PrismRefractionFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.PRISM_REFRACTION;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int y = 0; y < h; y++) {
            float v = (y + 0.5f) / h;
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                float u = (x + 0.5f) / w;

                float diag = (u + v) * 0.5f;
                float pr = 0.5f + 0.5f * (float) Math.cos(diag * 6.28318f);
                float pg = 0.5f + 0.5f * (float) Math.cos((diag + 0.33f) * 6.28318f);
                float pb = 0.5f + 0.5f * (float) Math.cos((diag + 0.67f) * 6.28318f);

                int c = in[idx];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;

                float outR = (r / 255f) * 0.85f + pr * 0.25f;
                float outG = (g / 255f) * 0.85f + pg * 0.25f;
                float outB = (b / 255f) * 0.85f + pb * 0.25f;

                int finalR = Math.min(255, (int) (r * (1f - intensity) + outR * 255f * intensity));
                int finalG = Math.min(255, (int) (g * (1f - intensity) + outG * 255f * intensity));
                int finalB = Math.min(255, (int) (b * (1f - intensity) + outB * 255f * intensity));

                out[idx] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
            }
        }
    }
}
