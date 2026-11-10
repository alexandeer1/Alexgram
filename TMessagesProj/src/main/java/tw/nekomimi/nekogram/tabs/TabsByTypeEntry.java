package tw.nekomimi.nekogram.tabs;

import android.content.Context;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;

/**
 * Mirrors iMe's DialogsSortingTabType — defines each "Tabs by Type" chat category.
 */
public enum TabsByTypeEntry {

    UNREAD(
            R.drawable.fork_filter_icon_bubble_point,
            MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS
                    | MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ
                    | MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED,
            true,   // editable FAB
            true    // enabled by default
    ),
    PERSONAL(
            R.drawable.fork_filter_icon_user,
            MessagesController.DIALOG_FILTER_FLAG_CONTACTS
                    | MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS
                    | MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED,
            true,
            true
    ),
    GROUPS(
            R.drawable.fork_filter_icon_users,
            MessagesController.DIALOG_FILTER_FLAG_GROUPS
                    | MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED,
            true,
            true
    ),
    CHANNELS(
            R.drawable.fork_filter_icon_channel,
            MessagesController.DIALOG_FILTER_FLAG_CHANNELS
                    | MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED,
            true,
            true
    ),
    BOTS(
            R.drawable.fork_filter_icon_bot,
            MessagesController.DIALOG_FILTER_FLAG_BOTS
                    | MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED,
            true,
            true
    ),
    ADMIN(
            R.drawable.fork_filter_icon_chat_admin,
            MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED | 0x40000000,
            true,
            true
    ),
    OWNER(
            R.drawable.fork_filter_icon_owner,
            MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED | 0x80000000,
            true,
            true
    ),
    PRIVATE_GROUPS(
            R.drawable.fork_filter_icon_private_groups,
            MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED | 0x10000000,
            true,
            false
    ),
    PUBLIC_GROUPS(
            R.drawable.fork_filter_icon_public_groups,
            MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED | 0x08000000,
            true,
            false
    ),
    MENTIONED_CHATS(
            R.drawable.fork_filter_icon_mentionbutton,
            MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED | 0x01000000,
            true,   // editable FAB
            false
    ),
    LIVE_CHATS(
            R.drawable.fork_filter_icon_voicechat,
            MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED | 0x00800000,
            true,   // editable FAB
            false
    ),
    DELETED_USERS(
            R.drawable.fork_ic_ghost_26,
            MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED | 0x02000000,
            true,   // editable FAB
            false
    ),
    SECRET_CHATS(
            R.drawable.fork_filter_icon_lock,
            MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED | 0x20000000,
            true,
            false
    );

    public final int iconResId;
    public final int dialogFilterFlags;
    public final boolean isEditableFab;    // true = pencil, false = eye
    public final boolean enabledByDefault;

    TabsByTypeEntry(int iconResId, int dialogFilterFlags, boolean isEditableFab, boolean enabledByDefault) {
        this.iconResId = iconResId;
        this.dialogFilterFlags = dialogFilterFlags;
        this.isEditableFab = isEditableFab;
        this.enabledByDefault = enabledByDefault;
    }

    public String getTitle() {
        return getTitle(null);
    }

    public String getTitle(Context context) {
        String key;
        int resId;
        String fallback;
        switch (this) {
            case UNREAD:
                key = "TabsByTypeUnread";
                resId = R.string.TabsByTypeUnread;
                fallback = "Unread chats";
                break;
            case PERSONAL:
                key = "TabsByTypePersonal";
                resId = R.string.TabsByTypePersonal;
                fallback = "Private chats";
                break;
            case GROUPS:
                key = "TabsByTypeGroups";
                resId = R.string.TabsByTypeGroups;
                fallback = "Groups";
                break;
            case CHANNELS:
                key = "TabsByTypeChannels";
                resId = R.string.TabsByTypeChannels;
                fallback = "Channels";
                break;
            case BOTS:
                key = "TabsByTypeBots";
                resId = R.string.TabsByTypeBots;
                fallback = "Bots";
                break;
            case ADMIN:
                key = "TabsByTypeAdmin";
                resId = R.string.TabsByTypeAdmin;
                fallback = "Admin";
                break;
            case OWNER:
                key = "TabsByTypeOwner";
                resId = R.string.TabsByTypeOwner;
                fallback = "Owner";
                break;
            case PRIVATE_GROUPS:
                key = "TabsByTypePrivateGroups";
                resId = R.string.TabsByTypePrivateGroups;
                fallback = "Private groups";
                break;
            case PUBLIC_GROUPS:
                key = "TabsByTypePublicGroups";
                resId = R.string.TabsByTypePublicGroups;
                fallback = "Public groups";
                break;
            case MENTIONED_CHATS:
                key = "TabsByTypeMentioned";
                resId = R.string.TabsByTypeMentioned;
                fallback = "Mentioned chats";
                break;
            case LIVE_CHATS:
                key = "TabsByTypeLive";
                resId = R.string.TabsByTypeLive;
                fallback = "Live chats";
                break;
            case DELETED_USERS:
                key = "TabsByTypeDeleted";
                resId = R.string.TabsByTypeDeleted;
                fallback = "Deleted accounts";
                break;
            case SECRET_CHATS:
                key = "TabsByTypeSecret";
                resId = R.string.TabsByTypeSecret;
                fallback = "Secret chats";
                break;
            default:
                return name();
        }

        try {
            String value = LocaleController.getString(key, resId);
            if (value != null && !value.startsWith("LOC_ERR:")) {
                return value;
            }
        } catch (Throwable ignored) {
        }
        if (context != null) {
            try {
                return context.getString(resId);
            } catch (Throwable ignored) {
            }
        }
        return fallback;
    }
}
