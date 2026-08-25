package com.exteragram.messenger.feed.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.feed.FeedChannelActions;
import com.exteragram.messenger.feed.FeedConfig;
import com.exteragram.messenger.feed.FeedController;
import com.exteragram.messenger.preferences.BasePreferencesActivity;

import java.util.ArrayList;
import java.util.Comparator;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

public class FeedChannelsActivity extends BasePreferencesActivity implements NotificationCenter.NotificationCenterDelegate {

    private static final Comparator<TLRPC.Chat> BY_TITLE = Comparator.comparing(chat -> chat.title == null ? "" : chat.title.toLowerCase());
    private final ArrayList<TLRPC.Chat> channels = new ArrayList<>();
    private ActionBarMenuItem otherItem;
    private String query;
    private boolean searching;

    @Override
    public String getTitle() {
        return LocaleController.getString("FeedSettings", R.string.FeedSettings);
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.feedNeedReload);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.dialogDeleted);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.feedNeedReload);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.dialogDeleted);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.feedNeedReload) {
            reloadChannels();
        } else if (id == NotificationCenter.dialogDeleted) {
            removeChannel((Long) args[0]);
        }
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    setAllExcluded(false);
                } else if (id == 2) {
                    setAllExcluded(true);
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(0, R.drawable.outline_header_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                searching = true;
                if (otherItem != null) {
                    otherItem.setVisibility(View.GONE);
                }
            }

            @Override
            public void onSearchCollapse() {
                searching = false;
                query = null;
                if (otherItem != null) {
                    otherItem.setVisibility(View.VISIBLE);
                }
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            }

            @Override
            public void onTextChanged(EditText editText) {
                query = editText.getText().toString().trim().toLowerCase();
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
            }
        }).setSearchFieldHint(LocaleController.getString("Search", R.string.Search));

        otherItem = menu.addItem(3, R.drawable.ic_ab_other);
        otherItem.addSubItem(1, R.drawable.msg_select, LocaleController.getString("SelectAll", R.string.SelectAll));
        otherItem.addSubItem(2, R.drawable.msg_cancel, LocaleController.getString("DeselectAll", R.string.DeselectAll));

        reloadChannels();
        return view;
    }

    @Override
    public boolean onBackPressed(boolean check) {
        if (!searching) {
            return super.onBackPressed(check);
        }
        if (!check) {
            return false;
        }
        actionBar.closeSearchField();
        return false;
    }

    private void reloadChannels() {
        FeedController.getInstance(currentAccount).loadChannels(true, (arrayList, i, z, i2) -> {
            if (z) {
                return;
            }
            channels.clear();
            for (int k = 0; k < arrayList.size(); k++) {
                TLRPC.Chat chat = (TLRPC.Chat) arrayList.get(k);
                TLRPC.Chat chat2 = getMessagesController().getChat(chat.id);
                channels.add(chat2 != null ? chat2 : chat);
            }
            channels.sort(BY_TITLE);
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        });
    }

    private void removeChannel(long dialogId) {
        for (int i = 0; i < channels.size(); i++) {
            if ((-channels.get(i).id) == dialogId) {
                channels.remove(i);
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
                return;
            }
        }
    }

    @Override
    public void fillItems(ArrayList<UItem> arrayList, UniversalAdapter universalAdapter) {
        FeedConfig feedConfig = FeedConfig.getInstance(currentAccount);
        boolean emptyQuery = TextUtils.isEmpty(query);
        if (emptyQuery) {
            arrayList.add(UItem.asHeader(LocaleController.getString("General", R.string.General)));
            arrayList.add(UItem.asButtonCheck(1073741822, LocaleController.getString("FeedBottomTab", R.string.FeedBottomTab), LocaleController.getString("FeedBottomTabInfo", R.string.FeedBottomTabInfo)).setChecked(ExteraConfig.getShowFeedTab()));
            if (ExteraConfig.getShowFeedTab()) {
                arrayList.add(UItem.asButtonCheck(1073741821, "Replace Contacts Tab", "Hide Contacts tab from bottom navigation bar when Feed tab is enabled.").setChecked(ExteraConfig.getFeedReplaceContactsTab()));
            }
            arrayList.add(UItem.asCheck(1073741820, LocaleController.getString("FeedUnreadCounter", R.string.FeedUnreadCounter)).setChecked(ExteraConfig.getShowFeedUnreadCounter()));
            arrayList.add(UItem.asCheck(1073741823, LocaleController.getString("FeedIncludeArchived", R.string.FeedIncludeArchived)).setChecked(feedConfig.getIncludeArchived()));
            arrayList.add(UItem.asShadow(LocaleController.getString("FeedIncludeArchivedInfo", R.string.FeedIncludeArchivedInfo)));
        }
        ArrayList<UItem> shown = new ArrayList<>();
        ArrayList<UItem> hidden = new ArrayList<>();
        for (int i = 0; i < channels.size(); i++) {
            TLRPC.Chat chat = channels.get(i);
            if (emptyQuery || (chat.title != null && chat.title.toLowerCase().contains(query))) {
                boolean isExcluded = feedConfig.isExcluded(-chat.id);
                (!isExcluded ? shown : hidden).add(UItem.asUserCheckbox((int) chat.id, chat).setChecked(!isExcluded));
            }
        }
        if (!shown.isEmpty()) {
            arrayList.add(UItem.asHeader(LocaleController.getString("FeedShownChannels", R.string.FeedShownChannels)));
            arrayList.addAll(shown);
        }
        if (!hidden.isEmpty()) {
            if (!shown.isEmpty()) {
                arrayList.add(UItem.asShadow((CharSequence) null));
            }
            arrayList.add(UItem.asHeader(LocaleController.getString("FeedHiddenChannels", R.string.FeedHiddenChannels)));
            arrayList.addAll(hidden);
        }
        if (emptyQuery && (!shown.isEmpty() || !hidden.isEmpty())) {
            arrayList.add(UItem.asShadow(LocaleController.getString("FeedChannelsInfo", R.string.FeedChannelsInfo)));
        }
    }

    @Override
    public void onClick(UItem item, View view, int position, float x, float y) {
        Object obj = item.object;
        if (obj instanceof TLRPC.Chat) {
            TLRPC.Chat chat = (TLRPC.Chat) obj;
            toggleBooleanSettingAndRefresh(item, val -> FeedConfig.getInstance(currentAccount).setExcluded(-chat.id, !val));
            return;
        }
        int id = item.id;
        if (id == 1073741822) {
            ExteraConfig.setShowFeedTab(!ExteraConfig.getShowFeedTab());
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.feedTabVisibleToggled);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mainTabsLayoutChanged);
        } else if (id == 1073741821) {
            toggleBooleanSettingAndRefresh(item, val -> {
                ExteraConfig.setFeedReplaceContactsTab(val);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.feedTabVisibleToggled);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mainTabsLayoutChanged);
            });
        } else if (id == 1073741820) {
            toggleBooleanSettingAndRefresh(item, val -> {
                ExteraConfig.setShowFeedUnreadCounter(val);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, 0);
            });
        } else if (id == 1073741823) {
            FeedConfig feedConfig = FeedConfig.getInstance(currentAccount);
            feedConfig.setIncludeArchived(!feedConfig.getIncludeArchived());
            reloadChannels();
        }
    }

    @Override
    public boolean onLongClick(UItem item, View view, int position, float x, float y) {
        Object obj = item.object;
        if (!(obj instanceof TLRPC.Chat)) {
            return super.onLongClick(item, view, position, x, y);
        }
        TLRPC.Chat chat = (TLRPC.Chat) obj;
        ItemOptions.makeOptions(this, view)
                .setScrimViewBackground(listView.getClipBackground(view))
                .add(R.drawable.msg_channel, LocaleController.getString("OpenChannel2", R.string.OpenChannel2), () -> presentFragment(ChatActivity.of(-chat.id)))
                .addIf(FeedChannelActions.canLeave(chat), R.drawable.msg_leave, LocaleController.getString("LeaveChannelMenu", R.string.LeaveChannelMenu), true, () -> FeedChannelActions.leaveChannel(this, chat, null, null))
                .show();
        return false;
    }

    private void setAllExcluded(boolean excluded) {
        FeedConfig feedConfig = FeedConfig.getInstance(currentAccount);
        if (excluded) {
            ArrayList<Long> ids = new ArrayList<>(channels.size());
            for (int i = 0; i < channels.size(); i++) {
                ids.add(-channels.get(i).id);
            }
            feedConfig.excludeAll(ids);
        } else {
            feedConfig.clearExcluded();
        }
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }
}
