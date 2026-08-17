package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class ChronoSpeedBlurFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.CHRONO_SPEED_BLUR;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        float cx = w * 0.5f;
        float cy = h * 0.5f;

        for (int y = 0; y < h; y++) {
            float dy = y - cy;
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                float dx = x - cx;
                float r = (float) Math.sqrt(dx * dx + dy * dy);

                // Sample along radial streak vector
                int sumR = 0, sumG = 0, sumB = 0;
                int samples = 4;
                for (int s = 0; s < samples; s++) {
                    float factor = 1.0f - (s * 0.04f * intensity);
                    int sX = Math.max(0, Math.min(w - 1, (int) (cx + dx * factor)));
                    int sY = Math.max(0, Math.min(h - 1, (int) (cy + dy * factor)));
                    int sampleC = in[sY * w + sX];
                    sumR += (sampleC >> 16) & 0xFF;
                    sumG += (sampleC >> 8) & 0xFF;
                    sumB += sampleC & 0xFF;
                }

                int outR = sumR / samples;
                int outG = sumG / samples;
                int outB = sumB / samples;

                int origC = in[rowOffset + x];
                int oR = (origC >> 16) & 0xFF;
                int oG = (origC >> 8) & 0xFF;
                int oB = origC & 0xFF;

                int finalR = (int) (oR * (1f - intensity) + outR * intensity);
                int finalG = (int) (oG * (1f - intensity) + outG * intensity);
                int finalB = (int) (oB * (1f - intensity) + outB * intensity);

                out[rowOffset + x] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
            }
        }
    }
}
