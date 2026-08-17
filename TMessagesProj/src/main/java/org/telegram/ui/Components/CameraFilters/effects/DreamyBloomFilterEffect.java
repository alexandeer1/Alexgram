package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class DreamyBloomFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.DREAMY_BLOOM;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int i = 0; i < in.length; i++) {
            int c = in[i];
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;

            float br = (r / 255f);
            float bg = (g / 255f);
            float bb = (b / 255f);

            float bloomR = br * 1.15f + (br * br) * 0.25f + 0.05f;
            float bloomG = bg * 1.12f + (bg * bg) * 0.22f + 0.04f;
            float bloomB = bb * 1.18f + (bb * bb) * 0.28f + 0.06f;

            int outR = (int) (Math.min(1f, Math.max(0f, bloomR)) * 255f);
            int outG = (int) (Math.min(1f, Math.max(0f, bloomG)) * 255f);
            int outB = (int) (Math.min(1f, Math.max(0f, bloomB)) * 255f);

            int finalR = Math.min(255, (int) (r * (1f - intensity) + outR * intensity));
            int finalG = Math.min(255, (int) (g * (1f - intensity) + outG * intensity));
            int finalB = Math.min(255, (int) (b * (1f - intensity) + outB * intensity));

            out[i] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
        }
    }
}
