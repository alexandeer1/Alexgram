package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class StarlightSparkleFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.STARLIGHT_SPARKLE;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int y = 0; y < h; y++) {
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                int i = rowOffset + x;
                int c = in[i];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;

                float brightness = (r + g + b) / (3f * 255f);
                float starBoost = (brightness > 0.72f) ? (float) Math.pow((brightness - 0.72f) / 0.28f, 2.0) * 80f : 0f;

                float outR = r * 1.05f + starBoost;
                float outG = g * 1.02f + starBoost * 0.9f;
                float outB = b * 1.10f + starBoost * 1.2f;

                int finalR = Math.min(255, Math.max(0, (int) (r * (1f - intensity) + outR * intensity)));
                int finalG = Math.min(255, Math.max(0, (int) (g * (1f - intensity) + outG * intensity)));
                int finalB = Math.min(255, Math.max(0, (int) (b * (1f - intensity) + outB * intensity)));

                out[i] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
            }
        }
    }
}
