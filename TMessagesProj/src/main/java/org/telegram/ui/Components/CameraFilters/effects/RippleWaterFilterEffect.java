package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class RippleWaterFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.RIPPLE_WATER_DROPS;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        float cx = w * 0.5f;
        float cy = h * 0.5f;

        for (int y = 0; y < h; y++) {
            float dy = y - cy;
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                float dx = x - cx;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float ripple = (float) Math.sin(dist * 0.08f) * 14f * intensity;

                int srcX = Math.max(0, Math.min(w - 1, (int) (x + (dx / Math.max(1f, dist)) * ripple)));
                int srcY = Math.max(0, Math.min(h - 1, (int) (y + (dy / Math.max(1f, dist)) * ripple)));

                int origC = in[rowOffset + x];
                int ripC = in[srcY * w + srcX];

                int r = (ripC >> 16) & 0xFF;
                int g = (ripC >> 8) & 0xFF;
                int b = ripC & 0xFF;

                float outR = r * 0.85f;
                float outG = g * 1.05f + 6f;
                float outB = b * 1.25f + 16f;

                int origR = (origC >> 16) & 0xFF;
                int origG = (origC >> 8) & 0xFF;
                int origB = origC & 0xFF;

                int finalR = Math.min(255, (int) (origR * (1f - intensity) + outR * intensity));
                int finalG = Math.min(255, (int) (origG * (1f - intensity) + outG * intensity));
                int finalB = Math.min(255, (int) (origB * (1f - intensity) + outB * intensity));

                out[rowOffset + x] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
            }
        }
    }
}
