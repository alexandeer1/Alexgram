package tw.nekomimi.nekogram.helpers;

import org.telegram.ui.MainTabsActivity;

import com.exteragram.messenger.ExteraConfig;
import xyz.nextalone.nagram.NaConfig;

public final class MainTabsHelper {
    public static final int MAIN_TABS_HEIGHT = 56;
    public static final int MAIN_TABS_MARGIN = 8;
    public static final int MAIN_TABS_MARGIN_COMPACT = 4;
    public static final int FILTER_TABS_HEIGHT = 36;
    public static final int TAB_WIDTH = 80;
    public static final int TAB_PADDING = 4;

    private MainTabsHelper() {
    }

    public static boolean isMainTabsHideTitleStyle() {
        return NaConfig.INSTANCE.getMainTabsHideTitles().Bool();
    }

    public static int getMainTabsHeight() {
        return isMainTabsHideTitleStyle() ? FILTER_TABS_HEIGHT : MAIN_TABS_HEIGHT;
    }

    public static int getMainTabsMargin() {
        return isMainTabsHideTitleStyle() ? MAIN_TABS_MARGIN_COMPACT : MAIN_TABS_MARGIN;
    }

    public static int getMainTabsHeightWithMargins() {
        return getMainTabsHeight() + getMainTabsMargin() * 2;
    }

    public static boolean isFeedTabShown() {
        return ExteraConfig.getShowFeedTab();
    }

    public static boolean isContactsTabHidden() {
        return NaConfig.INSTANCE.getHideContacts().Bool() || (isFeedTabShown() && ExteraConfig.getFeedReplaceContactsTab());
    }

    public static int getChatsPosition() {
        return 0;
    }

    public static int getFeedPosition() {
        return isFeedTabShown() ? 1 : -1;
    }

    public static int getContactsPosition() {
        if (isContactsTabHidden()) return -1;
        return isFeedTabShown() ? 2 : 1;
    }

    public static int getCallsOrSettingsPosition() {
        int pos = 1;
        if (isFeedTabShown()) pos++;
        if (!isContactsTabHidden()) pos++;
        return pos;
    }

    public static int getProfilePosition() {
        int pos = 2;
        if (isFeedTabShown()) pos++;
        if (!isContactsTabHidden()) pos++;
        return pos;
    }

    public static int getFragmentsCount() {
        int count = isContactsTabHidden() ? 3 : 4;
        if (isFeedTabShown()) count++;
        return count;
    }

    public static int getTabsViewWidth() {
        return TAB_WIDTH * 4 + (getMainTabsMargin() + TAB_PADDING) * 2;
    }
}
