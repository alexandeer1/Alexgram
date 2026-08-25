package org.telegram.ui.Components.CameraFilters.effects;

import org.telegram.ui.Components.CameraFilters.CameraFilterType;

public class SolarEclipseFilterEffect implements ICameraFilterEffect {
    @Override
    public int getFilterType() {
        return CameraFilterType.SOLAR_ECLIPSE;
    }

    @Override
    public void process(int[] in, int[] out, int w, int h, float intensity) {
        for (int i = 0; i < in.length; i++) {
            int c = in[i];
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;

            float lum = (r * 0.299f + g * 0.587f + b * 0.114f) / 255.0f;
            // Solarized parabolic curve
            float solar = (float) Math.abs(Math.sin(lum * Math.PI));
            float outR = solar * 255f * 1.1f;
            float outG = solar * 180f;
            float outB = (1.0f - solar) * 80f;

            int finalR = Math.min(255, Math.max(0, (int) (r * (1f - intensity) + outR * intensity)));
            int finalG = Math.min(255, Math.max(0, (int) (g * (1f - intensity) + outG * intensity)));
            int finalB = Math.min(255, Math.max(0, (int) (b * (1f - intensity) + outB * intensity)));

            out[i] = 0xFF000000 | (finalR << 16) | (finalG << 8) | finalB;
        }
    }
}
