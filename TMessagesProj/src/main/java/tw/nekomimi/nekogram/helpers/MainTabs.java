package tw.nekomimi.nekogram.helpers;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.R;
import org.telegram.ui.DialogsActivity;

import xyz.nextalone.nagram.NaConfig;

public final class MainTabs {

    public static final int ALWAYS = 0;
    public static final int HIDE_ON_SCROLL = 1;
    public static final int HIDDEN = 2;

    public static String getLabel(int visibility) {
        return switch (visibility) {
            case HIDDEN -> getString(R.string.Hidden);
            case HIDE_ON_SCROLL -> getString(R.string.MainTabsHideOnScroll);
            default -> getString(R.string.MainTabsAlways);
        };
    }

    public static boolean isHidden() {
        return getVisibility() == HIDDEN;
    }

    public static boolean isHideOnScroll() {
        return getVisibility() == HIDE_ON_SCROLL;
    }

    public static int getVisibility() {
        return NaConfig.INSTANCE.getMainTabsVisibility().Int();
    }

    public static void setVisibility(int visibility) {
        NaConfig.INSTANCE.getMainTabsVisibility().setConfigInt(visibility);
        NaConfig.INSTANCE.getHideBottomNavigationBar().setConfigBool(visibility == HIDDEN);
        NaConfig.INSTANCE.getHideTabsOnScroll().setConfigBool(visibility == HIDE_ON_SCROLL);
    }

    public static int getHeight() {
        return NaConfig.INSTANCE.getMainTabsHideTitles().Bool() ? DialogsActivity.FILTER_TABS_HEIGHT : DialogsActivity.MAIN_TABS_HEIGHT;
    }

    public static int getOffset() {
        return isHidden() ? 0 : getHeight() + DialogsActivity.MAIN_TABS_MARGIN;
    }

    public static int getInset() {
        return isHidden() ? 0 : getHeight() + DialogsActivity.MAIN_TABS_MARGIN * 2;
    }
}

