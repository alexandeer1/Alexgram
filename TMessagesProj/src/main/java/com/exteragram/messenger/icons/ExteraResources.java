package com.exteragram.messenger.icons;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;

public final class ExteraResources extends Resources {
    private final Resources mResources;

    public ExteraResources(Resources resources) {
        super(resources.getAssets(), resources.getDisplayMetrics(), resources.getConfiguration());
        this.mResources = resources;
        IconManager.INSTANCE.initialize(false);
    }

    @Override
    public Drawable getDrawableForDensity(int id, int density, Resources.Theme theme) {
        IconManager iconManager = IconManager.INSTANCE;
        Drawable drawable = iconManager.getDrawable(id, density, theme);
        if (drawable != null) {
            return drawable;
        }
        return this.mResources.getDrawableForDensity(id, density, theme);
    }

    public final Drawable getOriginalDrawable(int id) {
        try {
            return this.mResources.getDrawable(id, null);
        } catch (Resources.NotFoundException unused) {
            return null;
        }
    }
}