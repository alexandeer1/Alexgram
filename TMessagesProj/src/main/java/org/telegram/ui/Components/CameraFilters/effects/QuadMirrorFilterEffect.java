package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class QuadMirrorFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.MIRROR_QUAD_SPLIT;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        int halfW = w / 2;
        int halfH = h / 2;

        for (int y = 0; y < h; y++) {
            int srcY = (y < halfH) ? y : (h - 1 - y);
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                int srcX = (x < halfW) ? x : (w - 1 - x);
                int mirrorC = in[srcY * w + srcX];
                int origC = in[rowOffset + x];

                int mR = (mirrorC >> 16) & 0xFF;
                int mG = (mirrorC >> 8) & 0xFF;
                int mB = mirrorC & 0xFF;

                int oR = (origC >> 16) & 0xFF;
                int oG = (origC >> 8) & 0xFF;
                int oB = origC & 0xFF;

                int finalR = (int) (oR * (1f - intensity) + mR * intensity);
                int finalG = (int) (oG * (1f - intensity) + mG * intensity);
                int finalB = (int) (oB * (1f - intensity) + mB * intensity);

                out[rowOffset + x] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
            }
        }
    }
}
