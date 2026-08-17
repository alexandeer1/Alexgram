package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class GameBoyFilterEffect implements ICameraFilterEffect {
    private static final int[] PALETTE = {
        0xFF0F380F, // Darkest green
        0xFF306230, // Dark green
        0xFF8BAC0F, // Light green
        0xFF9BBC0F  // Lightest green
    };

    @Override
    public int getFilterType() {
        return CameraFilterType.RETRO_GAMEBOY;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        int blockSize = 4;
        for (int y = 0; y < h; y += blockSize) {
            for (int x = 0; x < w; x += blockSize) {
                int sampleIdx = y * w + x;
                int c = in[sampleIdx];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;

                float lum = (r * 0.299f + g * 0.587f + b * 0.114f) / 255f;
                int palIdx = Math.min(3, Math.max(0, (int) (lum * 4.0f)));
                int gbColor = PALETTE[palIdx];

                for (int dy = 0; dy < blockSize && (y + dy) < h; dy++) {
                    int rowOffset = (y + dy) * w;
                    for (int dx = 0; dx < blockSize && (x + dx) < w; dx++) {
                        int origC = in[rowOffset + x + dx];
                        int oR = (origC >> 16) & 0xFF;
                        int oG = (origC >> 8) & 0xFF;
                        int oB = origC & 0xFF;

                        int gR = (gbColor >> 16) & 0xFF;
                        int gG = (gbColor >> 8) & 0xFF;
                        int gB = gbColor & 0xFF;

                        int finalR = (int) (oR * (1f - intensity) + gR * intensity);
                        int finalG = (int) (oG * (1f - intensity) + gG * intensity);
                        int finalB = (int) (oB * (1f - intensity) + gB * intensity);

                        out[rowOffset + x + dx] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
                    }
                }
            }
        }
    }
}
