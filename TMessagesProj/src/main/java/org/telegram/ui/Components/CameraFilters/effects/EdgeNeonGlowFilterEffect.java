package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class EdgeNeonGlowFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.EDGE_NEON_GLOW;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int y = 1; y < h - 1; y++) {
            int rowOffset = y * w;
            for (int x = 1; x < w - 1; x++) {
                int c = in[rowOffset + x];
                int cR = in[rowOffset + x + 1];
                int cD = in[(y + 1) * w + x];

                int lumC = ((c >> 16) & 0xFF) + ((c >> 8) & 0xFF) + (c & 0xFF);
                int lumR = ((cR >> 16) & 0xFF) + ((cR >> 8) & 0xFF) + (cR & 0xFF);
                int lumD = ((cD >> 16) & 0xFF) + ((cD >> 8) & 0xFF) + (cD & 0xFF);

                int edge = Math.min(255, (Math.abs(lumC - lumR) + Math.abs(lumC - lumD)) * 2);
                float edgeNorm = edge / 255f;

                int origR = (c >> 16) & 0xFF;
                int origG = (c >> 8) & 0xFF;
                int origB = c & 0xFF;

                float outR = origR * 0.25f + edgeNorm * 255f;
                float outG = origG * 0.25f + edgeNorm * 50f;
                float outB = origB * 0.25f + edgeNorm * 220f;

                int finalR = Math.min(255, (int) (origR * (1f - intensity) + outR * intensity));
                int finalG = Math.min(255, (int) (origG * (1f - intensity) + outG * intensity));
                int finalB = Math.min(255, (int) (origB * (1f - intensity) + outB * intensity));

                out[rowOffset + x] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
            }
        }
    }
}
