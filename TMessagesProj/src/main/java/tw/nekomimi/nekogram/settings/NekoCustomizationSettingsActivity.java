package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UndoView;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.cell.AbstractConfigCell;
import tw.nekomimi.nekogram.config.cell.ConfigCellCustom;
import tw.nekomimi.nekogram.config.cell.ConfigCellHeader;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheckIcon;
import tw.nekomimi.nekogram.ui.cells.AvatarCornersPreviewCell;
import xyz.nextalone.nagram.NaConfig;

@SuppressLint("RtlHardcoded")
@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class NekoCustomizationSettingsActivity extends BaseNekoXSettingsActivity {

    private ListAdapter listAdapter;
    private AvatarCornersPreviewCell avatarCornersPreviewCell;

    @Override
    protected RecyclerListView.SelectionAdapter getListAdapter() {
        return listAdapter;
    }

    @Override
    protected CellGroup getCellGroup() {
        return cellGroup;
    }

    @Override
    protected String getSettingsPrefix() {
        return "customization";
    }

    private final CellGroup cellGroup = new CellGroup(this);

    // Customization Settings
    private final AbstractConfigCell avatarCornersPreviewRow = cellGroup.appendCell(new ConfigCellCustom("AvatarCorners", ConfigCellCustom.CUSTOM_ITEM_AvatarCorners, false));
    private final AbstractConfigCell singleCornerRadiusRow = cellGroup.appendCell(
            new ConfigCellTextCheck(
                    NaConfig.INSTANCE.getSingleCornerRadius(),
                    null,
                    getString(R.string.SingleCornerRadius)
            )
    );
    private final AbstractConfigCell avatarCornersInfoRow = cellGroup.appendCell(new ConfigCellCustom("SingleCornerRadiusInfo", CellGroup.ITEM_TYPE_TEXT, false));
    private final AbstractConfigCell headerCustomization = cellGroup.appendCell(new ConfigCellHeader(LocaleController.getString("Customization", R.string.Customization)));
    private final AbstractConfigCell pillStackRow = cellGroup.appendCell(
            new ConfigCellTextCheckIcon(null, "PillStack", getString(R.string.PillStackPills), R.drawable.ic_ab_search, false, () ->
                    presentFragment(new com.exteragram.messenger.pillstack.ui.PillStackPreferencesActivity()))
    );
    // [Alexgram: Fonts] - Start
    private final AbstractConfigCell fontsRow = cellGroup.appendCell(
            new ConfigCellTextCheckIcon(null, "Fonts", getString(R.string.Fonts), R.drawable.msg_theme, false, () ->
                    presentFragment(new tw.nekomimi.nekogram.settings.FontsSettingsActivity()))
    );
    // [Alexgram: Fonts] - End
    private final AbstractConfigCell showQuickEditIconRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.showQuickEditIconInChatList, LocaleController.getString("ShowQuickEditIconDesc", R.string.ShowQuickEditIconDesc), LocaleController.getString("ShowQuickEditIcon", R.string.ShowQuickEditIcon)));
    private final AbstractConfigCell quickEditIconOnlyOwnRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.quickEditIconOnlyForOwnMessages, LocaleController.getString("QuickEditIconOnlyOwnDesc", R.string.QuickEditIconOnlyOwnDesc), LocaleController.getString("QuickEditIconOnlyOwn", R.string.QuickEditIconOnlyOwn)));
    private final AbstractConfigCell forceMusicSpeedControlRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.forceMusicSpeedControl, LocaleController.getString("ExperimentalMusicSpeedControlAbout", R.string.ExperimentalMusicSpeedControlAbout), LocaleController.getString("ExperimentalMusicSpeedControl", R.string.ExperimentalMusicSpeedControl)));
    private final AbstractConfigCell enableEditFileNameRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.enableEditFileName, LocaleController.getString("ExperimentalEditFileNameAbout", R.string.ExperimentalEditFileNameAbout), LocaleController.getString("ExperimentalEditFileName", R.string.ExperimentalEditFileName)));
    private final AbstractConfigCell enableChangeNameInGroupsRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.enableChangeNameInGroups, LocaleController.getString("ExperimentalChangeSenderNameAbout", R.string.ExperimentalChangeSenderNameAbout), LocaleController.getString("ExperimentalChangeSenderName", R.string.ExperimentalChangeSenderName)));
    private final AbstractConfigCell enableLocalEditorPlusRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.enableLocalEditorPlus, LocaleController.getString("LocalEditorPlusAbout", R.string.LocalEditorPlusAbout), LocaleController.getString("LocalEditorPlus", R.string.LocalEditorPlus)));
    private final AbstractConfigCell showCopyFileRefRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.showCopyFileRef, LocaleController.getString("CopyFileRefAbout", R.string.CopyFileRefAbout), LocaleController.getString("CopyFileRef", R.string.CopyFileRef)));
    private final AbstractConfigCell showAdminTagInVoiceChatRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.showAdminTagInVoiceChat, LocaleController.getString("ShowAdminTagInVoiceChatDesc", R.string.ShowAdminTagInVoiceChatDesc), LocaleController.getString("ShowAdminTagInVoiceChat", R.string.ShowAdminTagInVoiceChat)));
    private final AbstractConfigCell showSenderNameOnStickerRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.showSenderNameOnSticker, LocaleController.getString("ShowSenderNameOnStickerDesc", R.string.ShowSenderNameOnStickerDesc), LocaleController.getString("ShowSenderNameOnSticker", R.string.ShowSenderNameOnSticker)));
    private final AbstractConfigCell showSenderNameOnGifRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.showSenderNameOnGif, LocaleController.getString("ShowSenderNameOnGifDesc", R.string.ShowSenderNameOnGifDesc), LocaleController.getString("ShowSenderNameOnGif", R.string.ShowSenderNameOnGif)));
    private final AbstractConfigCell showSenderNameOnMediaRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.showSenderNameOnMedia, LocaleController.getString("ShowSenderNameOnMediaDesc", R.string.ShowSenderNameOnMediaDesc), LocaleController.getString("ShowSenderNameOnMedia", R.string.ShowSenderNameOnMedia)));
    private final AbstractConfigCell enableCustomPrivacyRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.enableCustomPrivacy, LocaleController.getString("ProfilePrivacyManagerAbout", R.string.ProfilePrivacyManagerAbout), LocaleController.getString("ProfilePrivacyManager", R.string.ProfilePrivacyManager)));
    private final AbstractConfigCell enableSelectRangeInSharedMediaRow = cellGroup.appendCell(new ConfigCellTextCheck(
            NekoConfig.enableSelectRangeInSharedMedia,
            LocaleController.getString("SelectRangeSharedMediaAbout", R.string.SelectRangeSharedMediaAbout),
            LocaleController.getString("SelectRangeSharedMedia", R.string.SelectRangeSharedMedia)
    ));

    private final AbstractConfigCell sendVideoAsRoundRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSendVideoAsRound()));
    private final AbstractConfigCell sendLockedCustomEmojiAsStickerRow = cellGroup.appendCell(
            new ConfigCellTextCheck(
                    NaConfig.INSTANCE.getSendLockedCustomEmojiAsSticker(),
                    getString(R.string.SendLockedCustomEmojiAsStickerInfo)
            )
    );

    public NekoCustomizationSettingsActivity() {
        if (!NekoConfig.showQuickEditIconInChatList.Bool()) {
            cellGroup.rows.remove(quickEditIconOnlyOwnRow);
        }
        // Only show the GIF sender name row if the sticker sender name is enabled
        if (!NekoConfig.showSenderNameOnSticker.Bool()) {
            cellGroup.rows.remove(showSenderNameOnGifRow);
        }
        addRowsToMap(cellGroup);
    }

    @Override
    public View createView(Context context) {
        View superView = super.createView(context);

        listAdapter = new ListAdapter(context);

        listView.setAdapter(listAdapter);
        listView.invalidateItemDecorations();

        setupDefaultListeners();

        cellGroup.callBackSettingsChanged = (key, newValue) -> {
            if (key.equals(NaConfig.INSTANCE.getSingleCornerRadius().getKey())) {
                reloadAvatarCorners();
            } else if (key.equals(NekoConfig.showQuickEditIconInChatList.getKey())) {
                if ((boolean) newValue) {
                    if (!cellGroup.rows.contains(quickEditIconOnlyOwnRow)) {
                        final int index = cellGroup.rows.indexOf(showQuickEditIconRow) + 1;
                        cellGroup.rows.add(index, quickEditIconOnlyOwnRow);
                        listAdapter.notifyItemInserted(index);
                    }
                } else {
                    if (cellGroup.rows.contains(quickEditIconOnlyOwnRow)) {
                        final int index = cellGroup.rows.indexOf(quickEditIconOnlyOwnRow);
                        cellGroup.rows.remove(quickEditIconOnlyOwnRow);
                        listAdapter.notifyItemRemoved(index);
                    }
                }
                addRowsToMap(cellGroup);
            } else if (key.equals(NekoConfig.forceMusicSpeedControl.getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NekoConfig.enableEditFileName.getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NekoConfig.enableChangeNameInGroups.getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NekoConfig.enableLocalEditorPlus.getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NekoConfig.showSenderNameOnSticker.getKey())) {
                // Show or hide the GIF sub-option based on sticker name toggle
                if ((boolean) newValue) {
                    if (!cellGroup.rows.contains(showSenderNameOnGifRow)) {
                        final int index = cellGroup.rows.indexOf(showSenderNameOnStickerRow) + 1;
                        cellGroup.rows.add(index, showSenderNameOnGifRow);
                        listAdapter.notifyItemInserted(index);
                    }
                } else {
                    if (cellGroup.rows.contains(showSenderNameOnGifRow)) {
                        final int index = cellGroup.rows.indexOf(showSenderNameOnGifRow);
                        cellGroup.rows.remove(showSenderNameOnGifRow);
                        listAdapter.notifyItemRemoved(index);
                    }
                }
                addRowsToMap(cellGroup);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
                if (getParentLayout() != null) {
                    getParentLayout().rebuildAllFragmentViews(false, false);
                }
            } else if (key.equals(NekoConfig.showSenderNameOnGif.getKey())) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
                if (getParentLayout() != null) {
                    getParentLayout().rebuildAllFragmentViews(false, false);
                }
            } else if (key.equals(NekoConfig.showSenderNameOnMedia.getKey())) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
                if (getParentLayout() != null) {
                    getParentLayout().rebuildAllFragmentViews(false, false);
                }
            }

        };

        return superView;
    }

    @Override
    public int getBaseGuid() {
        return 14000;
    }

    @Override
    public int getDrawable() {
        return R.drawable.msg_theme;
    }

    @Override
    public String getTitle() {
        return LocaleController.getString("Customization", R.string.Customization);
    }

    private void reloadAvatarCorners() {
        if (avatarCornersPreviewCell != null) {
            avatarCornersPreviewCell.invalidate();
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
        getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
        if (getParentLayout() != null) {
            getParentLayout().rebuildAllFragmentViews(false, false);
        }
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        protected void onBindCustomViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            AbstractConfigCell row = cellGroup.rows.get(position);
            if (row == avatarCornersInfoRow) {
                TextInfoPrivacyCell textInfoPrivacyCell = (TextInfoPrivacyCell) holder.itemView;
                textInfoPrivacyCell.setText(getString(R.string.SingleCornerRadiusInfo));
            }
        }

        @Override
        protected View onCreateCustomViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == ConfigCellCustom.CUSTOM_ITEM_AvatarCorners) {
                avatarCornersPreviewCell = new AvatarCornersPreviewCell(
                        mContext,
                        NekoCustomizationSettingsActivity.this::reloadAvatarCorners
                );
                return avatarCornersPreviewCell;
            }
            return null;
        }
    }
}
