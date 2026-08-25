package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class FujiVelviaFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.FUJI_VELVIA;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int i = 0; i < in.length; i++) {
            int c = in[i];
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;

            float vr = (r / 255f) * 1.05f;
            float vg = (g / 255f) * 1.25f + 0.02f;
            float vb = (b / 255f) * 1.15f + 0.04f;

            float avg = (vr + vg + vb) / 3f;
            vr = avg + (vr - avg) * 1.35f;
            vg = avg + (vg - avg) * 1.35f;
            vb = avg + (vb - avg) * 1.35f;

            int outR = (int) (Math.min(1f, Math.max(0f, vr)) * 255f);
            int outG = (int) (Math.min(1f, Math.max(0f, vg)) * 255f);
            int outB = (int) (Math.min(1f, Math.max(0f, vb)) * 255f);

            int finalR = Math.min(255, (int) (r * (1f - intensity) + outR * intensity));
            int finalG = Math.min(255, (int) (g * (1f - intensity) + outG * intensity));
            int finalB = Math.min(255, (int) (b * (1f - intensity) + outB * intensity));

            out[i] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
        }
    }
}
