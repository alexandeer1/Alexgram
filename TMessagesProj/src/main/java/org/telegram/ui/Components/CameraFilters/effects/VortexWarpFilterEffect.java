package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class VortexWarpFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.VORTEX_WARP;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float maxR = Math.min(cx, cy);

        for (int y = 0; y < h; y++) {
            float dy = y - cy;
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                float dx = x - cx;
                float r = (float) Math.sqrt(dx * dx + dy * dy);

                if (r < maxR) {
                    float angle = (float) Math.atan2(dy, dx);
                    float twist = (1.0f - r / maxR) * 2.5f * intensity;
                    float newAngle = angle + twist;

                    int srcX = (int) (cx + r * Math.cos(newAngle));
                    int srcY = (int) (cy + r * Math.sin(newAngle));

                    srcX = Math.max(0, Math.min(w - 1, srcX));
                    srcY = Math.max(0, Math.min(h - 1, srcY));

                    out[rowOffset + x] = in[srcY * w + srcX];
                } else {
                    out[rowOffset + x] = in[rowOffset + x];
                }
            }
        }
    }
}
