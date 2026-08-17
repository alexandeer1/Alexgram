package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class HolographicFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.HOLOGRAPHIC;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int y = 0; y < h; y++) {
            float v = (y + 0.5f) / h;
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                float u = (x + 0.5f) / w;

                int c = in[idx];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                float lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;

                float wave = (float) Math.sin((u * 4f + v * 4f + lum * 5f) * 3.14159f);
                float hr = 0.5f + 0.5f * (float) Math.sin(wave * 3.14159f);
                float hg = 0.5f + 0.5f * (float) Math.sin(wave * 3.14159f + 2.094f);
                float hb = 0.5f + 0.5f * (float) Math.sin(wave * 3.14159f + 4.188f);

                float outR = ((r / 255f) * 0.7f + hr * 0.45f) * 255f;
                float outG = ((g / 255f) * 0.7f + hg * 0.45f) * 255f;
                float outB = ((b / 255f) * 0.7f + hb * 0.45f) * 255f;

                int finalR = Math.min(255, (int) (r * (1f - intensity) + outR * intensity));
                int finalG = Math.min(255, (int) (g * (1f - intensity) + outG * intensity));
                int finalB = Math.min(255, (int) (b * (1f - intensity) + outB * intensity));

                out[idx] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
            }
        }
    }
}
