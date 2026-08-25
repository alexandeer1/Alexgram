package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class FisheyeFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.FISHEYE;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int y = 0; y < h; y++) {
            float v = (y + 0.5f) / h;
            float dv = v - 0.5f;
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                float u = (x + 0.5f) / w;
                float du = u - 0.5f;
                float r = (float) Math.sqrt(du * du + dv * dv);

                int origC = in[idx];
                int origR = (origC >> 16) & 0xFF;
                int origG = (origC >> 8) & 0xFF;
                int origB = origC & 0xFF;

                if (r > 0.0001f) {
                    float fishR = r + r * r * 0.45f * intensity;
                    float scale = fishR / r;
                    float fishU = 0.5f + du * scale;
                    float fishV = 0.5f + dv * scale;

                    int sx = (int) (fishU * w);
                    int sy = (int) (fishV * h);

                    float sr = 0, sg = 0, sb = 0;
                    if (sx >= 0 && sx < w && sy >= 0 && sy < h) {
                        int sampledC = in[sy * w + sx];
                        sr = (sampledC >> 16) & 0xFF;
                        sg = (sampledC >> 8) & 0xFF;
                        sb = sampledC & 0xFF;
                    }

                    float vigT = Math.max(0f, Math.min(1f, (r - 0.42f) / (0.52f - 0.42f)));
                    float fishVig = 1.0f - vigT * vigT * (3f - 2f * vigT);

                    float outR = sr * fishVig;
                    float outG = sg * fishVig;
                    float outB = sb * fishVig;

                    int finalR = Math.min(255, Math.max(0, (int) (origR * (1f - intensity) + outR * intensity)));
                    int finalG = Math.min(255, Math.max(0, (int) (origG * (1f - intensity) + outG * intensity)));
                    int finalB = Math.min(255, Math.max(0, (int) (origB * (1f - intensity) + outB * intensity)));

                    out[idx] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
                } else {
                    out[idx] = origC;
                }
            }
        }
    }
}
