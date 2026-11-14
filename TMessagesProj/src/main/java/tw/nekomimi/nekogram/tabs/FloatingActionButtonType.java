package tw.nekomimi.nekogram.tabs;

import android.content.Context;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.RLottieImageView;

public enum FloatingActionButtonType {
    CREATE_CHAT(0),
    BOOKMARKS(1),
    ARCHIVE(2),
    CLOUD(3),       // renamed to "Saved Messages" in getTitle / bindBig
    MARK_ALL_READ(4),
    WALLET(5),
    CONTACTS(6),
    // MUSIC(7)     — removed (useless)
    // ALBUMS(8)    — removed (useless)
    // CREATE_ALBUM(9) — removed (useless)
    CREATE_STORY(10),
    MINI_APPS(11);
    // AI_CHAT(12)  — removed (useless)
    // TODO(13)     — removed (useless)

    private final int id;

    FloatingActionButtonType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static FloatingActionButtonType getDefault() {
        return CREATE_CHAT;
    }

    public String getTitle(Context context) {
        switch (this) {
            case CREATE_CHAT:
                return LocaleController.getString("folder_fab_settings_fab_create_chat", R.string.folder_fab_settings_fab_create_chat);
            case BOOKMARKS:
                return LocaleController.getString("bookmarks_title", R.string.bookmarks_title);
            case ARCHIVE:
                return LocaleController.getString("folder_fab_settings_fab_archive", R.string.folder_fab_settings_fab_archive);
            case CLOUD:
                // [Alexgram: Tabs by Type] - renamed Cloud → Saved Messages
                return LocaleController.getString("SavedMessages", R.string.SavedMessages);
            case MARK_ALL_READ:
                return LocaleController.getString("folder_fab_settings_fab_mark_all_read", R.string.folder_fab_settings_fab_mark_all_read);
            case WALLET:
                return LocaleController.getString("folder_fab_settings_fab_wallet", R.string.folder_fab_settings_fab_wallet);
            case CONTACTS:
                return LocaleController.getString("folder_fab_settings_fab_contacts", R.string.folder_fab_settings_fab_contacts);
            case CREATE_STORY:
                return LocaleController.getString("Story", R.string.Story);
            case MINI_APPS:
                return LocaleController.getString("drawer_mini_apps", R.string.drawer_mini_apps);
            default:
                return name();
        }
    }

    public void bindBig(RLottieImageView view) {
        if (view == null) return;
        view.clearAnimation();
        switch (this) {
            case CREATE_CHAT:
                try {
                    view.setAnimation(R.raw.write_contacts_fab_icon, 52, 52);
                    view.playAnimation();
                } catch (Throwable e) {
                    view.setImageResource(R.drawable.filled_fab_compose_32);
                }
                break;
            case BOOKMARKS:
                // [Alexgram: Tabs by Type] - use white star (fork_fab_star) to match FAB button style
                view.setImageResource(R.drawable.fork_fab_star);
                break;
            case ARCHIVE:
                view.setImageResource(R.drawable.fork_fab_archive);
                break;
            case CLOUD:
                // [Alexgram: Tabs by Type] - changed from cloud icon to saved messages icon
                view.setImageResource(R.drawable.msg_saved_solar);
                break;
            case MARK_ALL_READ:
                view.setImageResource(R.drawable.fork_fab_mark_all_read);
                break;
            case WALLET:
                view.setImageResource(R.drawable.fork_fab_wallet);
                break;
            case CONTACTS:
                view.setImageResource(R.drawable.fork_fab_contacts);
                break;
            case CREATE_STORY:
                view.setImageResource(R.drawable.camera);
                break;
            case MINI_APPS:
                view.setImageResource(R.drawable.fork_fab_miniapps_icon);
                break;
        }
    }
}
