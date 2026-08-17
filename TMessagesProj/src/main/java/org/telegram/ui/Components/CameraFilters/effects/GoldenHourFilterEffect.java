package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class GoldenHourFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.GOLDEN_HOUR;
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

                float u = (x + 0.5f) / w;
                float du = u - 0.5f;
                float dist = (float) Math.sqrt(du * du + dv * dv);
                float vigT = Math.max(0f, Math.min(1f, (dist - 0.40f) / 0.45f));
                float vig = 1.0f - vigT * vigT * (3f - 2f * vigT);
                float vigMult = 0.82f + 0.18f * vig;

                float gr = (r * 1.22f + 17.8f) * vigMult;
                float gg = (g * 1.08f + 7.6f) * vigMult;
                float gb = Math.max(0f, (b * 0.78f - 10.2f)) * vigMult;

                int finalR = Math.min(255, Math.max(0, (int) (r * (1f - intensity) + gr * intensity)));
                int finalG = Math.min(255, Math.max(0, (int) (g * (1f - intensity) + gg * intensity)));
                int finalB = Math.min(255, Math.max(0, (int) (b * (1f - intensity) + gb * intensity)));

                out[idx] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
            }
        }
    }
}
