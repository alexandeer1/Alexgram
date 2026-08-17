package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class TunnelZoomFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.TUNNEL_ZOOM_WARP;
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

                float modDist = dist % (Math.min(cx, cy) * 0.35f);
                float scale = 0.5f + (modDist / (Math.min(cx, cy) * 0.35f)) * 0.5f;

                int srcX = Math.max(0, Math.min(w - 1, (int) (cx + dx * scale)));
                int srcY = Math.max(0, Math.min(h - 1, (int) (cy + dy * scale)));

                int origC = in[rowOffset + x];
                int tunC = in[srcY * w + srcX];

                int tR = (tunC >> 16) & 0xFF;
                int tG = (tunC >> 8) & 0xFF;
                int tB = tunC & 0xFF;

                int oR = (origC >> 16) & 0xFF;
                int oG = (origC >> 8) & 0xFF;
                int oB = origC & 0xFF;

                int finalR = (int) (oR * (1f - intensity) + tR * intensity);
                int finalG = (int) (oG * (1f - intensity) + tG * intensity);
                int finalB = (int) (oB * (1f - intensity) + tB * intensity);

                out[rowOffset + x] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
            }
        }
    }
}
