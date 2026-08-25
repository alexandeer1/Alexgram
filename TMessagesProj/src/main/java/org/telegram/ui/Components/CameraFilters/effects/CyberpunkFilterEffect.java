package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class CyberpunkFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.CYBERPUNK;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int i = 0; i < in.length; i++) {
            int c = in[i];
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;
            float lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;

            float cyanR = 0f;
            float cyanG = 0.9f * (lum * 1.3f) * 255f;
            float cyanB = 1.0f * (lum * 1.3f) * 255f;

            float magR = 1.0f * (0.3f + lum * 0.9f) * 255f;
            float magG = 0.05f * (0.3f + lum * 0.9f) * 255f;
            float magB = 0.7f * (0.3f + lum * 0.9f) * 255f;

            float outR = (1f - lum) * cyanR + lum * magR;
            float outG = (1f - lum) * cyanG + lum * magG;
            float outB = (1f - lum) * cyanB + lum * magB;

            int finalR = Math.min(255, Math.max(0, (int) (r * (1f - intensity) + outR * intensity)));
            int finalG = Math.min(255, Math.max(0, (int) (g * (1f - intensity) + outG * intensity)));
            int finalB = Math.min(255, Math.max(0, (int) (b * (1f - intensity) + outB * intensity)));

            out[i] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
        }
    }
}
