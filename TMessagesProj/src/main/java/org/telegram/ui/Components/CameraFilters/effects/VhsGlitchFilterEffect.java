package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class VhsGlitchFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.VHS_GLITCH;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        int shift = Math.max(2, Math.round(w * 0.012f * intensity));
        for (int y = 0; y < h; y++) {
            float scanline = (float) Math.sin(y * 1.5) * 0.06f * 255f * intensity;
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                int rx = Math.max(0, x - shift);
                int bx = Math.min(w - 1, x + shift);

                int r = (in[y * w + rx] >> 16) & 0xFF;
                int g = (in[idx] >> 8) & 0xFF;
                int b = in[y * w + bx] & 0xFF;

                int origC = in[idx];
                int origR = (origC >> 16) & 0xFF;
                int origG = (origC >> 8) & 0xFF;
                int origB = origC & 0xFF;

                float outR = Math.max(0f, r - scanline);
                float outG = Math.max(0f, g - scanline);
                float outB = Math.max(0f, b - scanline);

                int finalR = Math.min(255, (int) (origR * (1f - intensity) + outR * intensity));
                int finalG = Math.min(255, (int) (origG * (1f - intensity) + outG * intensity));
                int finalB = Math.min(255, (int) (origB * (1f - intensity) + outB * intensity));

                out[idx] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
            }
        }
    }
}
