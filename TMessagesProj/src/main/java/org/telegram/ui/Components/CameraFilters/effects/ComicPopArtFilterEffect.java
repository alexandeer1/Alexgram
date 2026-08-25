package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class ComicPopArtFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.COMIC_POPART;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int i = 0; i < in.length; i++) {
            int c = in[i];
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;

            float outR = Math.min(255f, (float) Math.floor((r / 255f) * 4.0f + 0.5f) / 4.0f * 1.15f * 255f);
            float outG = Math.min(255f, (float) Math.floor((g / 255f) * 4.0f + 0.5f) / 4.0f * 1.15f * 255f);
            float outB = Math.min(255f, (float) Math.floor((b / 255f) * 4.0f + 0.5f) / 4.0f * 1.15f * 255f);

            int finalR = Math.min(255, (int) (r * (1f - intensity) + outR * intensity));
            int finalG = Math.min(255, (int) (g * (1f - intensity) + outG * intensity));
            int finalB = Math.min(255, (int) (b * (1f - intensity) + outB * intensity));

            out[i] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
        }
    }
}
