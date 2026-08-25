package org.telegram.ui.Components.CameraFilters;

import org.telegram.messenger.LocaleController;

public class CameraFilterModel {
    public final int id;
    public final int titleResId;
    public final int subtitleResId;
    public final String fallbackTitle;
    public final String fallbackSubtitle;
    public final int primaryColor;
    public final int secondaryColor;
    public final int iconResId;
    public float intensity;

    public CameraFilterModel(int id, int titleResId, int subtitleResId, int primaryColor, int secondaryColor, int iconResId, float defaultIntensity) {
        this.id = id;
        this.titleResId = titleResId;
        this.subtitleResId = subtitleResId;
        this.fallbackTitle = null;
        this.fallbackSubtitle = null;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.iconResId = iconResId;
        this.intensity = defaultIntensity;
    }

    public CameraFilterModel(int id, String title, String subtitle, int primaryColor, int secondaryColor, int iconResId, float defaultIntensity) {
        this.id = id;
        this.titleResId = 0;
        this.subtitleResId = 0;
        this.fallbackTitle = title;
        this.fallbackSubtitle = subtitle;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.iconResId = iconResId;
        this.intensity = defaultIntensity;
    }

    public String getTitle() {
        if (titleResId != 0) {
            try {
                return LocaleController.getString(titleResId);
            } catch (Exception ignored) {}
        }
        return fallbackTitle != null ? fallbackTitle : "";
    }

    public String getSubtitle() {
        if (subtitleResId != 0) {
            try {
                return LocaleController.getString(subtitleResId);
            } catch (Exception ignored) {}
        }
        return fallbackSubtitle != null ? fallbackSubtitle : "";
    }

    public boolean isOriginal() {
        return id == CameraFilterType.ORIGINAL;
    }
}
