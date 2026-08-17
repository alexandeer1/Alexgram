package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class GlitchDatamoshFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.GLITCH_DATAMOSH;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        int blockSize = 16;
        for (int y = 0; y < h; y += blockSize) {
            int blockShift = ((y / blockSize) % 5 == 1) ? Math.round(24 * intensity) : (((y / blockSize) % 7 == 3) ? -Math.round(18 * intensity) : 0);
            for (int dy = 0; dy < blockSize && (y + dy) < h; dy++) {
                int rowOffset = (y + dy) * w;
                for (int x = 0; x < w; x++) {
                    int srcX = Math.max(0, Math.min(w - 1, x + blockShift));
                    int srcC = in[rowOffset + srcX];
                    int origC = in[rowOffset + x];

                    int oR = (origC >> 16) & 0xFF;
                    int oG = (origC >> 8) & 0xFF;
                    int oB = origC & 0xFF;

                    int sR = (srcC >> 16) & 0xFF;
                    int sG = (srcC >> 8) & 0xFF;
                    int sB = srcC & 0xFF;

                    int finalR = (int) (oR * (1f - intensity) + sR * intensity);
                    int finalG = (int) (oG * (1f - intensity) + sG * intensity);
                    int finalB = (int) (oB * (1f - intensity) + sB * intensity);

                    out[rowOffset + x] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
                }
            }
        }
    }
}
