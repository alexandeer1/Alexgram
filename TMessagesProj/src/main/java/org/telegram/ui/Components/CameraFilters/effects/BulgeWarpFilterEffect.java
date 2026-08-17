package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class BulgeWarpFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.PINCH_BULGE_LENS;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float radius = Math.min(cx, cy) * 0.85f;

        for (int y = 0; y < h; y++) {
            float dy = y - cy;
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                float dx = x - cx;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                if (dist < radius) {
                    float percent = dist / radius;
                    float bulgeFactor = (float) Math.pow(percent, 0.5f + 0.5f * (1.0f - intensity));
                    int srcX = Math.max(0, Math.min(w - 1, (int) (cx + dx * bulgeFactor)));
                    int srcY = Math.max(0, Math.min(h - 1, (int) (cy + dy * bulgeFactor)));
                    out[rowOffset + x] = in[srcY * w + srcX];
                } else {
                    out[rowOffset + x] = in[rowOffset + x];
                }
            }
        }
    }
}
