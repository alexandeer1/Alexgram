package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class CrystalSphereFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.CHROMATIC_SPHERE;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float radius = Math.min(cx, cy) * 0.80f;

        for (int y = 0; y < h; y++) {
            float dy = y - cy;
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                float dx = x - cx;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                if (dist < radius) {
                    float p = dist / radius;
                    float invP = (float) Math.sin(p * Math.PI * 0.5);
                    int srcX = Math.max(0, Math.min(w - 1, (int) (cx - dx * invP)));
                    int srcY = Math.max(0, Math.min(h - 1, (int) (cy - dy * invP)));
                    int sphereC = in[srcY * w + srcX];

                    int r = (sphereC >> 16) & 0xFF;
                    int g = (sphereC >> 8) & 0xFF;
                    int b = sphereC & 0xFF;

                    float rim = (p > 0.85f) ? (p - 0.85f) / 0.15f * 120f : 0f;
                    float outR = r * 0.95f + rim;
                    float outG = g * 0.85f;
                    float outB = b * 1.25f + rim * 1.5f;

                    int origC = in[rowOffset + x];
                    int oR = (origC >> 16) & 0xFF;
                    int oG = (origC >> 8) & 0xFF;
                    int oB = origC & 0xFF;

                    int finalR = Math.min(255, (int) (oR * (1f - intensity) + outR * intensity));
                    int finalG = Math.min(255, (int) (oG * (1f - intensity) + outG * intensity));
                    int finalB = Math.min(255, (int) (oB * (1f - intensity) + outB * intensity));

                    out[rowOffset + x] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
                } else {
                    int c = in[rowOffset + x];
                    int r = (c >> 16) & 0xFF;
                    int g = (c >> 8) & 0xFF;
                    int b = c & 0xFF;
                    int finalR = (int) (r * 0.4f);
                    int finalG = (int) (g * 0.4f);
                    int finalB = (int) (b * 0.4f);
                    out[rowOffset + x] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
                }
            }
        }
    }
}
