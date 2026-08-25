package org.telegram.ui.Components.CameraFilters.effects;

public interface ICameraFilterEffect {
    int getFilterType();
    void process(int[] in, int[] out, int width, int height, float intensity);
}
