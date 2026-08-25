package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class PixelArt8bitFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.PIXEL_ART_8BIT;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        int blockSize = Math.max(4, Math.round(12 * intensity));
        for (int y = 0; y < h; y += blockSize) {
            for (int x = 0; x < w; x += blockSize) {
                int sampleIdx = y * w + x;
                int c = in[sampleIdx];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;

                // 8-bit quantize (4 levels per channel)
                int qR = (r / 64) * 85;
                int qG = (g / 64) * 85;
                int qB = (b / 64) * 85;

                int blockColor = 0xFF000000 | (qR << 16) | (qG << 8) | qB;

                for (int dy = 0; dy < blockSize && (y + dy) < h; dy++) {
                    int rowOffset = (y + dy) * w;
                    for (int dx = 0; dx < blockSize && (x + dx) < w; dx++) {
                        int origC = in[rowOffset + x + dx];
                        int origR = (origC >> 16) & 0xFF;
                        int origG = (origC >> 8) & 0xFF;
                        int origB = origC & 0xFF;

                        int finalR = (int) (origR * (1f - intensity) + qR * intensity);
                        int finalG = (int) (origG * (1f - intensity) + qG * intensity);
                        int finalB = (int) (origB * (1f - intensity) + qB * intensity);

                        out[rowOffset + x + dx] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
                    }
                }
            }
        }
    }
}
