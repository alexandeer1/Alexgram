package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class BwNoirFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.BW_NOIR;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int y = 0; y < h; y++) {
            float v = (y + 0.5f) / h;
            float dv = v - 0.5f;
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                int c = in[idx];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                float lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;

                float t = Math.max(0f, Math.min(1f, (lum - 0.10f) / 0.80f));
                float curvedLum = t * t * (3f - 2f * t);

                float u = (x + 0.5f) / w;
                float du = u - 0.5f;
                float dist = (float) Math.sqrt(du * du + dv * dv);
                float vt = Math.max(0f, Math.min(1f, (dist - 0.25f) / 0.50f));
                float vig = 1.0f - vt * vt * (3f - 2f * vt);

                float outVal = curvedLum * (0.65f + 0.35f * vig) * 255f;

                int finalR = Math.min(255, Math.max(0, (int) (r * (1f - intensity) + outVal * intensity)));
                int finalG = Math.min(255, Math.max(0, (int) (g * (1f - intensity) + outVal * intensity)));
                int finalB = Math.min(255, Math.max(0, (int) (b * (1f - intensity) + outVal * intensity)));

                out[idx] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
            }
        }
    }
}
