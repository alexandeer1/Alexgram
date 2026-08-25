package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class CinemaTealFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.CINEMA_TEAL;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int i = 0; i < in.length; i++) {
            int c = in[i];
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;

            float cr = (r / 255f) * 1.25f + 0.06f;
            float cg = (g / 255f) * 1.05f;
            float cb = (b / 255f) * 1.30f + 0.07f;
            if (r > b) cr += 0.08f; else cb += 0.10f;

            float outR = cr * 255f;
            float outG = cg * 255f;
            float outB = cb * 255f;

            int finalR = Math.min(255, Math.max(0, (int) (r * (1f - intensity) + outR * intensity)));
            int finalG = Math.min(255, Math.max(0, (int) (g * (1f - intensity) + outG * intensity)));
            int finalB = Math.min(255, Math.max(0, (int) (b * (1f - intensity) + outB * intensity)));

            out[i] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
        }
    }
}
