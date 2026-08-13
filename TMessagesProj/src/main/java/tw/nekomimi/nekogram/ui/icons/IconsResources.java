package tw.nekomimi.nekogram.ui.icons;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;

import com.exteragram.messenger.icons.IconManager;

import xyz.nextalone.nagram.NaConfig;

@SuppressLint("UseCompatLoadingForDrawables")
public class IconsResources extends Resources {

    public static final int ICON_REPLACE_SOLAR = 1;
    public static final int ICON_REPLACE_REMIX = 2;

    public IconsResources(Resources resources) {
        super(resources.getAssets(), resources.getDisplayMetrics(), resources.getConfiguration());
    }

    @Override
    public Drawable getDrawable(int id) throws NotFoundException {
        return getDrawable(id, null);
    }

    @Override
    public Drawable getDrawable(int id, @Nullable Theme theme) throws NotFoundException {
        return getDrawableForDensity(id, 0, theme);
    }

    @Nullable
    @Override
    public Drawable getDrawableForDensity(int id, int density) throws NotFoundException {
        return getDrawableForDensity(id, density, null);
    }

    @Nullable
    @Override
    public Drawable getDrawableForDensity(int id, int density, @Nullable Theme theme) {
        Drawable custom = IconManager.INSTANCE.getDrawable(id, density, theme);
        if (custom != null) {
            return custom;
        }

        int conv = getConversion(id);
        if (conv != id) {
            try {
                return super.getDrawableForDensity(conv, density, theme);
            } catch (Exception unused) {
            }
        }

        return super.getDrawableForDensity(id, density, theme);
    }

    @Nullable
    public Drawable getOriginalDrawable(int id, @Nullable Theme theme) {
        int conv = getConversion(id);
        if (conv != id) {
            try {
                return super.getDrawableForDensity(conv, 0, theme);
            } catch (Exception unused) {
            }
        }
        try {
            return super.getDrawableForDensity(id, 0, theme);
        } catch (Exception unused) {
            return null;
        }
    }

    public static int getConversion(int icon) {
        return getConversion(icon, -1);
    }

    public static int getConversion(int icon, int forcedIconsType) {
        int iconsType = NaConfig.INSTANCE.getIconReplacements().Int();
        int consideredIconsType = forcedIconsType == -1 ? iconsType : forcedIconsType;

        if (consideredIconsType == ICON_REPLACE_SOLAR) {
            return SolarIcons.Companion.getConversion(icon);
        } else if (consideredIconsType == ICON_REPLACE_REMIX) {
            return RemixIcons.Companion.getConversion(icon);
        }

        return icon;
    }
}
