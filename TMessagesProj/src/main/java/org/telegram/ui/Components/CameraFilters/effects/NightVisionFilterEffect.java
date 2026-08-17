package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class NightVisionFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.NIGHT_VISION;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int y = 0; y < h; y++) {
            float v = (y + 0.5f) / h;
            float dv = v - 0.5f;
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                int c = in[idx];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                float lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;

                float u = (x + 0.5f) / w;
                float du = u - 0.5f;
                float dist = (float) Math.sqrt(du * du + dv * dv);
                float scopeT = Math.max(0f, Math.min(1f, (dist - 0.38f) / 0.12f));
                float scope = 1.0f - scopeT * scopeT * (3f - 2f * scopeT);

                float outR = 0.10f * lum * 255f * scope;
                float outG = (lum * 1.35f + 0.10f) * 255f * scope;
                float outB = 0.15f * lum * 255f * scope;

                int finalR = Math.min(255, (int) (r * (1f - intensity) + outR * intensity));
                int finalG = Math.min(255, (int) (g * (1f - intensity) + outG * intensity));
                int finalB = Math.min(255, (int) (b * (1f - intensity) + outB * intensity));

                out[idx] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
            }
        }
    }
}
