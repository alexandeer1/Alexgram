package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class ThermalFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.THERMAL;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int i = 0; i < in.length; i++) {
            int c = in[i];
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;
            float lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;

            float tr = Math.max(0f, Math.min(1f, (float) Math.sin(lum * Math.PI - 1.5707963f)));
            float tg = Math.max(0f, Math.min(1f, (float) Math.sin(lum * Math.PI)));
            float tb = Math.max(0f, Math.min(1f, (float) Math.cos(lum * Math.PI)));

            if (lum > 0.75f) {
                float boost = (lum - 0.75f) * 3.0f;
                tr = Math.min(1f, tr + boost);
                tg = Math.min(1f, tg + boost);
                tb = Math.min(1f, tb + boost);
            }

            float outR = tr * 255f;
            float outG = tg * 255f;
            float outB = tb * 255f;

            int finalR = Math.min(255, (int) (r * (1f - intensity) + outR * intensity));
            int finalG = Math.min(255, (int) (g * (1f - intensity) + outG * intensity));
            int finalB = Math.min(255, (int) (b * (1f - intensity) + outB * intensity));

            out[i] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
        }
    }
}
