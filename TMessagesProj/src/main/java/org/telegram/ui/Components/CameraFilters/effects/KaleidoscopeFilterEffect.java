package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class KaleidoscopeFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.KALEIDOSCOPE;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float segmentAngle = (float) (Math.PI / 3.0); // 6-fold

        for (int y = 0; y < h; y++) {
            float dy = y - cy;
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                float dx = x - cx;
                float r = (float) Math.sqrt(dx * dx + dy * dy);
                float a = (float) Math.atan2(dy, dx);
                if (a < 0) a += Math.PI * 2;

                float modA = a % segmentAngle;
                if (modA > segmentAngle * 0.5f) {
                    modA = segmentAngle - modA;
                }

                int srcX = (int) (cx + r * Math.cos(modA));
                int srcY = (int) (cy + r * Math.sin(modA));

                srcX = Math.max(0, Math.min(w - 1, srcX));
                srcY = Math.max(0, Math.min(h - 1, srcY));

                int origColor = in[rowOffset + x];
                int mappedColor = in[srcY * w + srcX];

                int r1 = (origColor >> 16) & 0xFF;
                int g1 = (origColor >> 8) & 0xFF;
                int b1 = origColor & 0xFF;

                int r2 = (mappedColor >> 16) & 0xFF;
                int g2 = (mappedColor >> 8) & 0xFF;
                int b2 = mappedColor & 0xFF;

                int finalR = (int) (r1 * (1f - intensity) + r2 * intensity);
                int finalG = (int) (g1 * (1f - intensity) + g2 * intensity);
                int finalB = (int) (b1 * (1f - intensity) + b2 * intensity);

                out[rowOffset + x] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
            }
        }
    }
}
