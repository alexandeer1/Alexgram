package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.content.Intent;
import android.text.TextUtils;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.SeekBar;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.radolyn.ayugram.database.AyuData;
import com.radolyn.ayugram.messages.AyuMessagesController;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
// [Alexgram: AI Reply] - Start
import org.telegram.ui.AIAssistanceSettingsActivity;
import org.telegram.ui.Components.EditTextBoldCursor;
import tw.nekomimi.nekogram.config.ConfigItem;
// [Alexgram: AI Reply] - End
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckBoxCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BlurredRecyclerView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UndoView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import kotlin.Unit;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.cell.AbstractConfigCell;
import tw.nekomimi.nekogram.config.cell.ConfigCellCustom;
import tw.nekomimi.nekogram.config.cell.ConfigCellDivider;
import tw.nekomimi.nekogram.config.cell.ConfigCellHeader;
import tw.nekomimi.nekogram.config.cell.ConfigCellSelectBox;
import tw.nekomimi.nekogram.config.cell.ConfigCellText;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheckIcon;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextInput;
import tw.nekomimi.nekogram.filters.RegexFiltersSettingActivity;
import tw.nekomimi.nekogram.helpers.TimeStringHelper;
import tw.nekomimi.nekogram.ui.PopupBuilder;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import xyz.nextalone.nagram.NaConfig;

@SuppressLint("RtlHardcoded")
@SuppressWarnings("unused")
public class NekoExperimentalSettingsActivity extends BaseNekoXSettingsActivity {

    private ListAdapter listAdapter;

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
        return "experimental";
    }

    private AnimatorSet animatorSet;
    private boolean sensitiveCanChange = false;
    private boolean sensitiveEnabled = false;

    private final CellGroup cellGroup = new CellGroup(this);

    // General
    private final AbstractConfigCell headerGeneral = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.General)));
    private final AbstractConfigCell backAnimationStyleRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getBackAnimationStyle(),
            Build.VERSION.SDK_INT >= 34 ? new String[]{
                    getString(R.string.BackAnimationClassic),
                    getString(R.string.BackAnimationSpring),
                    getString(R.string.BackAnimationPredictive),
            } : new String[]{
                    getString(R.string.BackAnimationClassic),
                    getString(R.string.BackAnimationSpring),
            }, null));
    private final AbstractConfigCell springAnimationCrossfadeRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSpringAnimationCrossfade()));
    private final AbstractConfigCell localPremiumRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.localPremium));
    private final AbstractConfigCell hidePremiumIconRow = cellGroup.appendCell(new ConfigCellTextCheck(
            NekoConfig.hidePremiumIcon,
            LocaleController.getString("HidePremiumIconDesc", R.string.HidePremiumIconDesc),
            LocaleController.getString("HidePremiumIcon", R.string.HidePremiumIcon)
    ));
    private final AbstractConfigCell unlimitedPinnedDialogsRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.unlimitedPinnedDialogs, getString(R.string.UnlimitedPinnedDialogsAbout)));
    private final AbstractConfigCell unlimitedFavedStickersRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.unlimitedFavedStickers, getString(R.string.UnlimitedFavoredStickersAbout)));

    private final AbstractConfigCell dividerGeneral = cellGroup.appendCell(new ConfigCellDivider());
    private final AbstractConfigCell runInBackgroundRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getRunInBackground(), getString(R.string.RunInBackgroundInfo)));

    // Connections
    private final AbstractConfigCell headerConnection = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Connection)));
    private final AbstractConfigCell boostUploadRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.uploadBoost));
    private final AbstractConfigCell enhancedFileLoaderRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.enhancedFileLoader));
    private final AbstractConfigCell dividerConnection = cellGroup.appendCell(new ConfigCellDivider());

    // Media
    private final AbstractConfigCell headerMedia = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.MediaSettings)));
    private final AbstractConfigCell audioEnhanceRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getNoiseSuppressAndVoiceEnhance()));
    private final AbstractConfigCell v8dAudioRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getV8dAudio(), getString(R.string.V8DAudio), getString(R.string.V8DAudioAbout)));
    private final AbstractConfigCell sendMp4DocumentAsVideoRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSendMp4DocumentAsVideo()));
    private final AbstractConfigCell enhancedVideoBitrateRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getEnhancedVideoBitrate()));
    private final AbstractConfigCell musicGraphRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getMusicGraph(), getString(R.string.MusicGraphInfo)));
    // [Alexgram: Special Forward] - Start
    private final AbstractConfigCell specialForwardRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSpecialForward(), getString(R.string.SpecialForwardAbout), getString(R.string.SpecialForward)));
    // [Alexgram: Special Forward] - End
    private final AbstractConfigCell customAudioBitrateRow = cellGroup.appendCell(new ConfigCellCustom("customGroupVoipAudioBitrate", CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL, true));
    private final AbstractConfigCell playerDecoderRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getPlayerDecoder(), new String[]{
            getString(R.string.VideoPlayerDecoderHardware),
            getString(R.string.VideoPlayerDecoderPreferHW),
            getString(R.string.VideoPlayerDecoderPreferSW),
    }, null));
    private final AbstractConfigCell dividerMedia = cellGroup.appendCell(new ConfigCellDivider());

    // Ayu
    private final AbstractConfigCell headerAyuMoments = cellGroup.appendCell(new ConfigCellHeader("AyuMoments"));
    private final AbstractConfigCell ghostModeRow = cellGroup.appendCell(new ConfigCellText("GhostMode", () -> presentFragment(new GhostModeActivity())));
    private final AbstractConfigCell regexFiltersEnabledRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getRegexFiltersEnabled(), getString(R.string.RegexFiltersNotice)));
    private final AbstractConfigCell saveLastSeenRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSaveLocalLastSeen()));
    private final AbstractConfigCell enableSaveDeletedMessagesRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getEnableSaveDeletedMessages()));
    private final AbstractConfigCell enableSaveEditsHistoryRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getEnableSaveEditsHistory()));
    private final AbstractConfigCell messageSavingSaveMediaRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getMessageSavingSaveMedia(), getString(R.string.MessageSavingSaveMediaHint)));
    private final AbstractConfigCell saveDeletedMessageForBotsUserRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSaveDeletedMessageForBotUser()));
    private final AbstractConfigCell saveDeletedMessageInBotChatRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getSaveDeletedMessageForBot()));
    private final AbstractConfigCell translucentDeletedMessagesRow = cellGroup.appendCell(new ConfigCellText("TranslucentDeletedMessages", null));
    private final AbstractConfigCell useDeletedIconRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getUseDeletedIcon()));
    private final AbstractConfigCell customDeletedMarkRow = cellGroup.appendCell(new ConfigCellTextInput(null, NaConfig.INSTANCE.getCustomDeletedMark(), "", null));
    // [Alexgram: Deleted Icon Color Row] - Start
    private final AbstractConfigCell deletedIconColorRow = cellGroup.appendCell(new ConfigCellText("DeletedIconColor", null));
    // [Alexgram: Deleted Icon Color Row] - End
    private final AbstractConfigCell clearMessageDatabaseRow = cellGroup.appendCell(new ConfigCellTextCheckIcon(null, "ClearMessageDatabase", null, AyuData.totalSize > 0 ? AndroidUtilities.formatFileSize(AyuData.totalSize) : "...", R.drawable.msg_clear_solar, false, () -> new AlertDialog.Builder(getContext(), getResourceProvider())
            .setTitle(getString(R.string.ClearMessageDatabase))
            .setMessage(getString(R.string.AreYouSure))
            .setPositiveButton(getString(R.string.Clear), (dialog, which) -> {
                AlertDialog progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
                progressDialog.setCanCancel(false);
                progressDialog.show();
                Utilities.globalQueue.postRunnable(() -> {
                    AyuMessagesController.getInstance().clean();
                    AndroidUtilities.runOnUIThread(() -> {
                        progressDialog.dismiss();
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.ClearMessageDatabaseNotification)).show();
                    });
                    AyuData.loadSizes(this);
                });
            })
            .setNegativeButton(getString(R.string.Cancel), (d, w) -> d.dismiss())
            .makeRed(AlertDialog.BUTTON_POSITIVE)
            .show()));
    // [Alexgram: Home Drawer] - Start
    private final AbstractConfigCell navigationDrawerRow = cellGroup.appendCell(
            new ConfigCellTextCheck(NekoConfig.navigationDrawerEnabled, null, getString(R.string.HomeDrawer))
    );
    private final AbstractConfigCell drawerElementsRow = cellGroup.appendCell(new ConfigCellTextCheckIcon(null, "DrawerElements", getString(R.string.DrawerElements), R.drawable.menu_newfilter, false, () ->
            showDialog(showConfigMenuWithIconAlert(this, R.string.DrawerElements, new java.util.ArrayList<>() {{
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemMyProfile(), getString(R.string.MyProfile), R.drawable.left_status_profile));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemSetEmojiStatus(), getString(R.string.SetEmojiStatus), R.drawable.msg_status_set_solar, true));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemArchivedChats(), getString(R.string.ArchivedChats), R.drawable.msg_archive, true));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemNewGroup(), getString(R.string.NewGroup), R.drawable.msg_groups));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemNewChannel(), getString(R.string.NewChannel), R.drawable.msg_channel));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemContacts(), getString(R.string.Contacts), R.drawable.msg_contacts));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemCalls(), getString(R.string.Calls), R.drawable.msg_calls));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemRecentChats(), getString(R.string.RecentChats), R.drawable.msg_recent_solar));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemSaved(), getString(R.string.SavedMessages), R.drawable.msg_saved));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemFeed(), LocaleController.getString("Feed", R.string.Feed), R.drawable.ic_feed, true));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemSettings(), getString(R.string.Settings), R.drawable.msg_settings_old, true));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemNSettings(), getString(R.string.NekoSettings), R.drawable.nagramx_outline));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemBrowser(), getString(R.string.InappBrowser), R.drawable.web_browser));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemQrLogin(), getString(R.string.ImportLogin), R.drawable.msg_qrcode));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemSessions(), getString(R.string.Devices), R.drawable.msg2_devices, true));
                add(new ConfigCellTextCheckIcon(NaConfig.INSTANCE.getDrawerItemRestartApp(), getString(R.string.RestartApp), R.drawable.msg_retry));
            }}))
    ));
    // [Alexgram: Home Drawer] - End
    private final AbstractConfigCell dividerAyuMoments = cellGroup.appendCell(new ConfigCellDivider());

    // N-Config
    private final AbstractConfigCell headerNConfig = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.N_Config)));
    private final AbstractConfigCell showRPCErrorRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getShowRPCError()));
    private final AbstractConfigCell disableChoosingStickerRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.disableChoosingSticker));
    private final AbstractConfigCell disableFilteringRow = cellGroup.appendCell(new ConfigCellCustom("SensitiveDisableFiltering", CellGroup.ITEM_TYPE_TEXT_CHECK, true));
    private final AbstractConfigCell devicePerformanceClassRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getPerformanceClass(), new String[]{
            getString(R.string.QualityAuto) + " [" + SharedConfig.getPerformanceClassName(SharedConfig.measureDevicePerformanceClass()) + "]",
            getString(R.string.PerformanceClassHigh),
            getString(R.string.PerformanceClassAverage),
            getString(R.string.PerformanceClassLow),
    }, null));
    // [Alexgram: Force Max FPS] - Start
    private final AbstractConfigCell forceMaxRefreshRateRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getForceMaxRefreshRate(), getString(R.string.ExperimentalForceMaxFPSInfo), getString(R.string.ExperimentalForceMaxFPS)));
    private final AbstractConfigCell dividerForceMaxFPS = cellGroup.appendCell(new ConfigCellDivider());
    // [Alexgram: Force Max FPS] - End

    // Story
    private final AbstractConfigCell headerStory = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Story)));
    private final AbstractConfigCell disableStoriesRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getDisableStories()));
    private final AbstractConfigCell hideFromHeaderRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getHideStoriesFromHeader()));
    private final AbstractConfigCell dividerStory = cellGroup.appendCell(new ConfigCellDivider());

    // Pangu
    private final AbstractConfigCell headerPangu = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.Pangu)));
    private final AbstractConfigCell enablePanguOnSendingRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getEnablePanguOnSending(), getString(R.string.PanguInfo)));
    private final AbstractConfigCell dividerPangu = cellGroup.appendCell(new ConfigCellDivider());

    // [Alexgram: AI Reply] - Start
    // AI Reply
    private final AbstractConfigCell headerAiReply = cellGroup.appendCell(new ConfigCellHeader("AI Reply"));
    private final AbstractConfigCell aiAssistanceSettingsRow = cellGroup.appendCell(new ConfigCellText("AiAssistanceSettings", () -> presentFragment(new AIAssistanceSettingsActivity())));
    private final AbstractConfigCell enableAIReplyRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getEnableAIReply()));
    private final AbstractConfigCell enableSummarizeChatRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getEnableSummarizeChat()));
    private final AbstractConfigCell usePollinationsAiRow = cellGroup.appendCell(new ConfigCellTextCheck(NaConfig.INSTANCE.getUsePollinationsAi(), getString(R.string.usePollinationsAiAbout)));
    private final AbstractConfigCell aiApiKeyRow = cellGroup.appendCell(new ConfigCellText("AI API Key", null, () -> showAiSettingsBottomSheet(false)) {
        private org.telegram.ui.Cells.TextSettingsCell customCell;

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            if (this.customCell != null) {
                this.customCell.setEnabled(enabled);
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder) {
            org.telegram.ui.Cells.TextSettingsCell cell = (org.telegram.ui.Cells.TextSettingsCell) holder.itemView;
            this.customCell = cell;
            String keyStr = NaConfig.INSTANCE.getAiApiKey().String();
            String displayVal = TextUtils.isEmpty(keyStr) ? "Not Configured" : maskKey(keyStr);
            cell.setTextAndValue("AI API Key", displayVal, false, cellGroup.needSetDivider(this), true);
            cell.setEnabled(isEnabled());
        }
    });
    private final AbstractConfigCell aiApiKey2Row = cellGroup.appendCell(new ConfigCellText("API Key (Failover)", null, () -> showAiSettingsBottomSheet(true)) {
        private org.telegram.ui.Cells.TextSettingsCell customCell;

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            if (this.customCell != null) {
                this.customCell.setEnabled(enabled);
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder) {
            org.telegram.ui.Cells.TextSettingsCell cell = (org.telegram.ui.Cells.TextSettingsCell) holder.itemView;
            this.customCell = cell;
            String keyStr = NaConfig.INSTANCE.getAiApiKey2().String();
            String displayVal = TextUtils.isEmpty(keyStr) ? "Not Configured" : maskKey(keyStr);
            cell.setTextAndValue("API Key (Failover)", displayVal, false, cellGroup.needSetDivider(this), true);
            cell.setEnabled(isEnabled());
        }
    });
    private final AbstractConfigCell dividerAiReply = cellGroup.appendCell(new ConfigCellDivider());
    // [Alexgram: AI Reply] - End

    public NekoExperimentalSettingsActivity() {
        if (NaConfig.INSTANCE.getUseDeletedIcon().Bool()) {
            cellGroup.rows.remove(customDeletedMarkRow);
        } else {
            // [Alexgram: Deleted Icon Color Init] - Start
            cellGroup.rows.remove(deletedIconColorRow);
            // [Alexgram: Deleted Icon Color Init] - End
        }
        if (!NaConfig.INSTANCE.getSaveDeletedMessageForBotUser().Bool()) {
            cellGroup.rows.remove(saveDeletedMessageInBotChatRow);
        }
        if (NaConfig.INSTANCE.getBackAnimationStyle().Int() != ActionBarLayout.BACK_ANIMATION_SPRING) {
            cellGroup.rows.remove(springAnimationCrossfadeRow);
        }
        // [Alexgram: Home Drawer] - Start
        if (!NekoConfig.navigationDrawerEnabled.Bool()) {
            cellGroup.rows.remove(drawerElementsRow);
        }
        // [Alexgram: Home Drawer] - End
        if (!NekoConfig.localPremium.Bool()) {
            cellGroup.rows.remove(hidePremiumIconRow);
        }
        checkStoriesRows();
        checkUseDeletedIconRows();
        checkSaveBotMsgRows();
        checkSaveDeletedRows();
        checkAiApiRows();
        addRowsToMap(cellGroup);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        AyuData.loadSizes(this);
        return true;
    }

    @Override
    protected BlurredRecyclerView createListView(Context context) {
        BlurredRecyclerView rv = new BlurredRecyclerView(context) {
            @Override
            public Integer getSelectorColor(int position) {
                if (position == cellGroup.rows.indexOf(clearMessageDatabaseRow)) {
                    return Theme.multAlpha(getThemedColor(Theme.key_text_RedRegular), .1f);
                }
                return getThemedColor(Theme.key_listSelector);
            }
        };
        rv.disableBlurTopPadding = true;
        return rv;
    }

    @SuppressLint("NewApi")
    @Override
    public View createView(Context context) {
        View superView = super.createView(context);

        listAdapter = new ListAdapter(context);

        listView.setAdapter(listAdapter);
        listView.invalidateItemDecorations();

        setupDefaultListeners();

        // Cells: Set OnSettingChanged Callbacks
        cellGroup.callBackSettingsChanged = (key, newValue) -> {
            if (key.equals(NaConfig.INSTANCE.getEnableSaveDeletedMessages().getKey())) {
                checkSaveDeletedRows();
            } else if (key.equals(NaConfig.INSTANCE.getDisableStories().getKey())) {
                checkStoriesRows();
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NekoConfig.localPremium.getKey())) {
                boolean localPremiumEnabled = (boolean) newValue;
                if (localPremiumEnabled) {
                    if (!cellGroup.rows.contains(hidePremiumIconRow)) {
                        final int index = cellGroup.rows.indexOf(localPremiumRow) + 1;
                        cellGroup.rows.add(index, hidePremiumIconRow);
                        listAdapter.notifyItemInserted(index);
                    }
                } else {
                    if (cellGroup.rows.contains(hidePremiumIconRow)) {
                        final int index = cellGroup.rows.indexOf(hidePremiumIconRow);
                        cellGroup.rows.remove(hidePremiumIconRow);
                        listAdapter.notifyItemRemoved(index);
                    }
                }
                addRowsToMap(cellGroup);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mainUserInfoChanged);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
            } else if (key.equals(NekoConfig.hidePremiumIcon.getKey())) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mainUserInfoChanged);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
            } else if (key.equals(NaConfig.INSTANCE.getUseDeletedIcon().getKey())) {
                checkUseDeletedIconRows();
            } else if (key.equals(NaConfig.INSTANCE.getSaveDeletedMessageForBotUser().getKey())) {
                checkSaveBotMsgRows();
            } else if (key.equals(NaConfig.INSTANCE.getBackAnimationStyle().getKey())) {
                final int style = (int) newValue;
                if (style != ActionBarLayout.BACK_ANIMATION_SPRING) {
                    if (cellGroup.rows.contains(springAnimationCrossfadeRow)) {
                        final int index = cellGroup.rows.indexOf(springAnimationCrossfadeRow);
                        cellGroup.rows.remove(springAnimationCrossfadeRow);
                        listAdapter.notifyItemRemoved(index);
                    }
                } else {
                    if (!cellGroup.rows.contains(springAnimationCrossfadeRow)) {
                        final int index = cellGroup.rows.indexOf(backAnimationStyleRow) + 1;
                        cellGroup.rows.add(index, springAnimationCrossfadeRow);
                        listAdapter.notifyItemInserted(index);
                    }
                }
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getSpringAnimationCrossfade().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getPerformanceClass().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getPlayerDecoder().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getHideStoriesFromHeader().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            } else if (key.equals(NaConfig.INSTANCE.getRunInBackground().getKey())) {
                updateRunInBackground((Boolean) newValue);
            // [Alexgram: Force Max FPS] - Start
            } else if (key.equals(NaConfig.INSTANCE.getForceMaxRefreshRate().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
                if (getParentActivity() != null) {
                    AndroidUtilities.setPreferredMaxRefreshRate(getParentActivity().getWindow());
                }
            // [Alexgram: Force Max FPS] - End
            } else if (key.equals(NaConfig.INSTANCE.getV8dAudio().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            // [Alexgram: Home Drawer] - Start
            } else if (key.equals(NekoConfig.navigationDrawerEnabled.getKey())) {
                checkDrawerElementsRow();
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            // [Alexgram: Home Drawer] - End
            } else if (key.equals(NaConfig.INSTANCE.getUsePollinationsAi().getKey())) {
                checkAiApiRows();
            }
        };

        return superView;
    }

    @Override
    public void onResume() {
        super.onResume();
        checkSensitive();
    }

    @Override
    protected void handleCellClick(View view, int position, float x, float y) {
        if (position < 0 || position >= cellGroup.rows.size()) {
            return;
        }
        AbstractConfigCell a = cellGroup.rows.get(position);
        // [Alexgram: Translucent Click] - Start
        if (a == translucentDeletedMessagesRow) {
            showTranslucentOptions();
            return;
        }
        // [Alexgram: Translucent Click] - End
        // [Alexgram: Deleted Icon Color Click] - Start
        if (a == deletedIconColorRow) {
            showDeletedColorOptions(view);
            return;
        }
        // [Alexgram: Deleted Icon Color Click] - End
        if (a instanceof ConfigCellTextCheck) {
            if (position == cellGroup.rows.indexOf(musicGraphRow)) {
                if (!NaConfig.INSTANCE.getMusicGraph().Bool()) {
                    if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        android.content.SharedPreferences prefs = org.telegram.messenger.MessagesController.getGlobalMainSettings();
                        if (prefs.getBoolean("asked_mic_music_graph", false) || !getParentActivity().shouldShowRequestPermissionRationale(android.Manifest.permission.RECORD_AUDIO)) {
                            tw.nekomimi.nekogram.utils.AlertUtil.showMicPermissionDialog(getParentActivity());
                        } else {
                            prefs.edit().putBoolean("asked_mic_music_graph", true).apply();
                            getParentActivity().requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 101);
                        }
                        return;
                    }
                }
            }
            if (position == cellGroup.rows.indexOf(regexFiltersEnabledRow) && (LocaleController.isRTL && x > AndroidUtilities.dp(76) || !LocaleController.isRTL && x < (view.getMeasuredWidth() - AndroidUtilities.dp(76)))) {
                presentFragment(new RegexFiltersSettingActivity());
                return;
            }
            if (position == cellGroup.rows.indexOf(messageSavingSaveMediaRow) && (LocaleController.isRTL && x > AndroidUtilities.dp(76) || !LocaleController.isRTL && x < (view.getMeasuredWidth() - AndroidUtilities.dp(76)))) {
                showBottomSheet();
                return;
            }
        }
        super.handleCellClick(view, position, x, y);
    }

    @Override
    protected void onCustomCellClick(View view, int position, float x, float y) {
        if (position == cellGroup.rows.indexOf(disableFilteringRow)) {
            sensitiveEnabled = !sensitiveEnabled;
            TL_account.setContentSettings req = new TL_account.setContentSettings();
            req.sensitive_enabled = sensitiveEnabled;
            AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
            progressDialog.show();
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                progressDialog.dismiss();
                if (error == null) {
                    if (response instanceof TLRPC.TL_boolTrue && view instanceof TextCheckCell) {
                        ((TextCheckCell) view).setChecked(sensitiveEnabled);
                    }
                } else {
                    AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(currentAccount, error, this, req));
                }
            }));
        } else if (position == cellGroup.rows.indexOf(customAudioBitrateRow)) {
            PopupBuilder builder = new PopupBuilder(view);
            builder.setItems(new String[]{
                    "32 (" + getString(R.string.Default) + ")",
                    "64",
                    "128",
                    "192",
                    "256",
                    "320"
            }, (i, __) -> {
                switch (i) {
                    case 0:
                        NekoConfig.customAudioBitrate.setConfigInt(32);
                        break;
                    case 1:
                        NekoConfig.customAudioBitrate.setConfigInt(64);
                        break;
                    case 2:
                        NekoConfig.customAudioBitrate.setConfigInt(128);
                        break;
                    case 3:
                        NekoConfig.customAudioBitrate.setConfigInt(192);
                        break;
                    case 4:
                        NekoConfig.customAudioBitrate.setConfigInt(256);
                        break;
                    case 5:
                        NekoConfig.customAudioBitrate.setConfigInt(320);
                        break;
                }
                listAdapter.notifyItemChanged(position);
                return Unit.INSTANCE;
            });
            builder.show();
        }
    }

    @Override
    public int getBaseGuid() {
        return 11000;
    }

    @Override
    public int getDrawable() {
        return R.drawable.msg_fave;
    }

    @Override
    public String getTitle() {
        return getString(R.string.Experimental);
    }

    private void checkSensitive() {
        TL_account.getContentSettings req = new TL_account.getContentSettings();
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TL_account.contentSettings settings = (TL_account.contentSettings) response;
                sensitiveEnabled = settings.sensitive_enabled;
                sensitiveCanChange = settings.sensitive_can_change;
                int count = listView.getChildCount();
                ArrayList<Animator> animators = new ArrayList<>();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.getChildViewHolder(child);
                    int position = holder.getAdapterPosition();
                    if (position == cellGroup.rows.indexOf(disableFilteringRow)) {
                        TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                        checkCell.setChecked(sensitiveEnabled);
                        checkCell.setEnabled(sensitiveCanChange, animators);
                        if (sensitiveCanChange) {
                            if (!animators.isEmpty()) {
                                if (animatorSet != null) {
                                    animatorSet.cancel();
                                }
                                animatorSet = new AnimatorSet();
                                animatorSet.playTogether(animators);
                                animatorSet.addListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animator) {
                                        if (animator.equals(animatorSet)) {
                                            animatorSet = null;
                                        }
                                    }
                                });
                                animatorSet.setDuration(150);
                                animatorSet.start();
                            }
                        }
                    }
                }
            } else {
                AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(currentAccount, error, this, req));
            }
        }));
    }

    //impl ListAdapter
    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        protected void onBindCustomViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.itemView instanceof TextCheckCell textCheckCell) {
                textCheckCell.setEnabled(true, null);
                if (position == cellGroup.rows.indexOf(disableFilteringRow)) {
                    textCheckCell.setTextAndValueAndCheck(getString(R.string.SensitiveDisableFiltering), getString(R.string.SensitiveAbout), sensitiveEnabled, true, true);
                    textCheckCell.setEnabled(sensitiveCanChange, null);
                }
            } else if (holder.itemView instanceof TextSettingsCell textSettingsCell) {
                textSettingsCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                if (position == cellGroup.rows.indexOf(customAudioBitrateRow)) {
                    String value = NekoConfig.customAudioBitrate.Int() + "kbps";
                    if (NekoConfig.customAudioBitrate.Int() == 32)
                        value += " (" + getString(R.string.Default) + ")";
                    textSettingsCell.setTextAndValue(getString(R.string.customGroupVoipAudioBitrate), value, true);
                }
            }
        }

        @Override
        protected void onBindDefaultViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            CellGroup cellGroup = getCellGroup();
            if (cellGroup == null) return;
            AbstractConfigCell a = cellGroup.rows.get(position);
            // [Alexgram: Translucent Deleted Messages Bind] - Start
            if (a == translucentDeletedMessagesRow) {
                if (holder.itemView instanceof TextSettingsCell textCell) {
                    boolean enabled = NaConfig.INSTANCE.getTranslucentDeletedMessages().Bool();
                    if (enabled) {
                        int alpha = NaConfig.INSTANCE.getTranslucentDeletedMessagesAlpha().Int();
                        textCell.setTextAndValue("Translucent Deleted Messages", alpha + "% Opacity", false, cellGroup.needSetDivider(a), true);
                    } else {
                        textCell.setTextAndValue("Translucent Deleted Messages", "Off", false, cellGroup.needSetDivider(a), true);
                    }
                }
            }
            // [Alexgram: Translucent Deleted Messages Bind] - End
            // [Alexgram: Deleted Icon Color Bind] - Start
            else if (a == deletedIconColorRow) {
                if (holder.itemView instanceof TextSettingsCell textCell) {
                    int currentColor = NaConfig.INSTANCE.getDeletedIconColor().Int();
                    if (currentColor == 0) {
                        textCell.setTextAndValue("Deleted Icon Color", getString(R.string.Default), false, cellGroup.needSetDivider(a), true);
                        textCell.setTextValueColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
                    } else {
                        String hex = String.format("#%08X", currentColor);
                        textCell.setTextAndValue("Deleted Icon Color", "⬤ " + hex, false, cellGroup.needSetDivider(a), true);
                        textCell.setTextValueColor(currentColor);
                    }
                }
            }
            // [Alexgram: Deleted Icon Color Bind] - End
            else if (a instanceof ConfigCellTextCheckIcon) {
                if (holder.itemView instanceof TextCell textCell) {
                    if (position == cellGroup.rows.indexOf(clearMessageDatabaseRow)) {
                        textCell.setColors(Theme.key_text_RedRegular, Theme.key_text_RedRegular);
                    }
                }
            }
        }

        // [Alexgram: Deleted Icon Color Adapter Bind] - Start
        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            super.onBindViewHolder(holder, position);
            CellGroup cellGroup = getCellGroup();
            if (cellGroup != null && position >= 0 && position < cellGroup.rows.size()) {
                AbstractConfigCell a = cellGroup.rows.get(position);
                if (a == deletedIconColorRow) {
                    if (holder.itemView instanceof TextSettingsCell textCell) {
                        int currentColor = NaConfig.INSTANCE.getDeletedIconColor().Int();
                        if (currentColor != 0) {
                            textCell.setTextValueColor(currentColor);
                        }
                    }
                }
            }
        }
        // [Alexgram: Deleted Icon Color Adapter Bind] - End
    }

    // [Alexgram: AI Reply] - Start
    private void testAiApi(String url, String key) {
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(key)) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error, "URL and API Key cannot be empty").show();
            return;
        }

        // Detect Gemini Native API vs OpenAI Compatible API
        final boolean isGeminiNative = (url.contains("generativelanguage.googleapis.com") || url.contains("googleapis.com")) && !url.contains("openai");
        String finalUrl = url;

        if (isGeminiNative && !url.contains(":generateContent")) {
            if (url.endsWith("/")) {
                finalUrl = url.substring(0, url.length() - 1) + ":generateContent";
            } else {
                finalUrl = url + ":generateContent";
            }
        }

        AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
        progressDialog.setCanCancel(false);
        progressDialog.show();

        final String requestUrl = finalUrl;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .build();

                JSONObject jsonBody = new JSONObject();

                if (isGeminiNative) {
                    JSONArray contents = new JSONArray();
                    JSONObject contentObj = new JSONObject();
                    JSONArray parts = new JSONArray();
                    JSONObject textPart = new JSONObject();
                    textPart.put("text", "Say ping");
                    parts.put(textPart);
                    contentObj.put("parts", parts);
                    contents.put(contentObj);
                    jsonBody.put("contents", contents);
                } else {
                    jsonBody.put("model", "gpt-3.5-turbo");
                    JSONArray messages = new JSONArray();
                    JSONObject userMsg = new JSONObject();
                    userMsg.put("role", "user");
                    userMsg.put("content", "Say ping");
                    messages.put(userMsg);
                    jsonBody.put("messages", messages);
                    jsonBody.put("max_tokens", 5);
                }

                Request.Builder requestBuilder = new Request.Builder()
                        .url(requestUrl)
                        .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonBody.toString()));

                if (isGeminiNative) {
                    requestBuilder.addHeader("x-goog-api-key", key);
                } else {
                    requestBuilder.addHeader("Authorization", "Bearer " + key);
                }

                client.newCall(requestBuilder.build()).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, java.io.IOException e) {
                        AndroidUtilities.runOnUIThread(() -> {
                            progressDialog.dismiss();
                            BulletinFactory.of(NekoExperimentalSettingsActivity.this).createSimpleBulletin(R.raw.error, "API Test Error: " + e.getMessage()).show();
                        });
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws java.io.IOException {
                        String responseBody = response.body().string();
                        int code = response.code();
                        AndroidUtilities.runOnUIThread(() -> {
                            progressDialog.dismiss();
                            showTestResult(response.isSuccessful(), code, responseBody, isGeminiNative);
                        });
                    }
                });
            } catch (Exception e) {
                AndroidUtilities.runOnUIThread(() -> {
                    progressDialog.dismiss();
                    showTestResult(false, 0, e.getMessage(), isGeminiNative);
                });
            }
        });
    }

    private void showTestResult(boolean success, int code, String responseBody, boolean isGeminiNative) {
        if (getParentActivity() == null) return;

        String aiReply = null;
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);
            if (isGeminiNative) {
                if (jsonResponse.has("candidates")) {
                    JSONArray candidates = jsonResponse.getJSONArray("candidates");
                    if (candidates.length() > 0) {
                        JSONObject candidate = candidates.getJSONObject(0);
                        if (candidate.has("content")) {
                            JSONObject content = candidate.getJSONObject("content");
                            JSONArray parts = content.getJSONArray("parts");
                            if (parts.length() > 0) {
                                aiReply = parts.getJSONObject(0).getString("text");
                            }
                        }
                    }
                }
            } else {
                if (jsonResponse.has("choices")) {
                    JSONArray choices = jsonResponse.getJSONArray("choices");
                    if (choices.length() > 0) {
                        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                        aiReply = message.getString("content");
                    }
                }
            }
        } catch (Exception e) {
            // ignore parsing error
        }

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, AndroidUtilities.dp(16), 0, 0);

        // Lottie Animation
        RLottieImageView animationView = new RLottieImageView(getContext());
        animationView.setAnimation(success ? R.raw.done : R.raw.error, 80, 80);
        animationView.setAutoRepeat(false);
        root.addView(animationView, LayoutHelper.createLinear(80, 80, Gravity.CENTER, 0, 0, 0, 8));
        AndroidUtilities.runOnUIThread(animationView::playAnimation, 200);

        // Status Title
        TextView titleView = new TextView(getContext());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setText(success ? "API Connect Successful" : "API Connect Failed");
        titleView.setTextColor(Theme.getColor(success ? Theme.key_windowBackgroundWhiteGreenText : Theme.key_text_RedRegular));
        titleView.setGravity(Gravity.CENTER);
        root.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 0, 24, 12));

        android.text.SpannableStringBuilder infoText = new android.text.SpannableStringBuilder();

        // Details
        int start = infoText.length();
        infoText.append("Status: ");
        infoText.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, infoText.length(), 0);
        infoText.append(code == 200 ? "200 OK" : (code == 0 ? "Local Error" : "HTTP " + code)).append("\n");

        start = infoText.length();
        infoText.append("API Type: ");
        infoText.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, infoText.length(), 0);
        infoText.append(isGeminiNative ? "Gemini Native" : "OpenAI Compatible").append("\n\n");

        if (aiReply != null) {
            start = infoText.length();
            infoText.append("AI Response:").append("\n");
            infoText.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, infoText.length(), 0);
            infoText.append(tw.nekomimi.nekogram.helpers.EntitiesHelper.parseMarkdown(aiReply)).append("\n\n");
        }

        start = infoText.length();
        infoText.append("Raw Response Body:").append("\n");
        infoText.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, infoText.length(), 0);
        infoText.append(responseBody);

        android.widget.TextView textView = new android.widget.TextView(getContext());
        textView.setText(infoText);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        textView.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), AndroidUtilities.dp(8));
        textView.setTextIsSelectable(true);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(getContext());
        scrollView.addView(textView);
        root.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 1.0f));

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setView(root);
        builder.setPositiveButton("OK", null);
        final String finalAiReply = aiReply != null ? aiReply : responseBody;
        builder.setNeutralButton("Copy Result", (dialog, which) -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getParentActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("AI Test Result", finalAiReply);
            clipboard.setPrimaryClip(clip);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.done, "Copied to clipboard").show();
        });

        AlertDialog dialog = builder.create();
        showDialog(dialog);

        // Haptic Feedback
        try {
            root.performHapticFeedback(success ? android.view.HapticFeedbackConstants.VIRTUAL_KEY : android.view.HapticFeedbackConstants.LONG_PRESS);
        } catch (Exception ignore) {}
    }

    private String maskKey(String key) {
        if (TextUtils.isEmpty(key)) return "";
        if (key.length() > 8) {
            return key.substring(0, 4) + "••••••••" + key.substring(key.length() - 4);
        } else {
            return "••••••••";
        }
    }

    private void checkAiApiRows() {
        boolean usePollinations = NaConfig.INSTANCE.getUsePollinationsAi().Bool();
        if (listAdapter == null) {
            if (usePollinations) {
                cellGroup.rows.remove(aiApiKeyRow);
                cellGroup.rows.remove(aiApiKey2Row);
            }
            return;
        }
        if (!usePollinations) {
            final int index = cellGroup.rows.indexOf(usePollinationsAiRow);
            int insertIndex = index + 1;
            boolean inserted = false;
            if (!cellGroup.rows.contains(aiApiKeyRow)) {
                cellGroup.rows.add(insertIndex, aiApiKeyRow);
                listAdapter.notifyItemInserted(insertIndex);
                insertIndex++;
                inserted = true;
            }
            if (!cellGroup.rows.contains(aiApiKey2Row)) {
                cellGroup.rows.add(insertIndex, aiApiKey2Row);
                listAdapter.notifyItemInserted(insertIndex);
                inserted = true;
            }
            if (inserted) {
                addRowsToMap(cellGroup);
            }
        } else {
            boolean removed = false;
            final int index1 = cellGroup.rows.indexOf(aiApiKeyRow);
            if (index1 != -1) {
                cellGroup.rows.remove(aiApiKeyRow);
                listAdapter.notifyItemRemoved(index1);
                removed = true;
            }
            final int index2 = cellGroup.rows.indexOf(aiApiKey2Row);
            if (index2 != -1) {
                cellGroup.rows.remove(aiApiKey2Row);
                listAdapter.notifyItemRemoved(index2);
                removed = true;
            }
            if (removed) {
                addRowsToMap(cellGroup);
            }
        }
    }

    private void showAiSettingsBottomSheet(final boolean isFailover) {
        if (getParentActivity() == null) {
            return;
        }

        final ConfigItem modelUrlConfig = isFailover ? NaConfig.INSTANCE.getAiModelUrl2() : NaConfig.INSTANCE.getAiModelUrl();
        final ConfigItem apiKeyConfig = isFailover ? NaConfig.INSTANCE.getAiApiKey2() : NaConfig.INSTANCE.getAiApiKey();
        final String defaultUrl = isFailover ? "https://api.openai.com/v1/chat/completions" : "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash";
        final String defaultHint = isFailover ? "sk-..." : "Api Key";
        final String titleText = isFailover ? "API Key (Failover)" : "AI API Key Settings";

        BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity(), true);
        builder.setApplyTopPadding(true);
        builder.setApplyBottomPadding(true);

        LinearLayout contentLayout = new LinearLayout(getParentActivity());
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(16), AndroidUtilities.dp(24), AndroidUtilities.dp(20));

        // Header: Title and description
        TextView titleView = new TextView(getParentActivity());
        titleView.setText(titleText);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        titleView.setGravity(Gravity.LEFT);
        contentLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        TextView descView = new TextView(getParentActivity());
        descView.setText(isFailover ? "Configure the secondary backup AI model URL and API Key. It will be used if the primary key fails." : "Configure your custom AI API Key and model URL. Leave URL empty to use default.");
        descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        descView.setTextColor(Theme.getColor(Theme.key_dialogTextGray));
        contentLayout.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 20));

        // Input 1: API Key
        TextView apiKeyLabel = new TextView(getParentActivity());
        apiKeyLabel.setText("API KEY");
        apiKeyLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        apiKeyLabel.setTypeface(AndroidUtilities.bold());
        apiKeyLabel.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        contentLayout.addView(apiKeyLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        FrameLayout apiKeyContainer = new FrameLayout(getParentActivity());
        android.graphics.drawable.GradientDrawable inputBg = new android.graphics.drawable.GradientDrawable();
        inputBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        inputBg.setCornerRadius(AndroidUtilities.dp(10));
        inputBg.setColor(isDark ? 0x15FFFFFF : 0x0F000000);
        inputBg.setStroke(AndroidUtilities.dp(1), isDark ? 0x20FFFFFF : 0x1A000000);
        apiKeyContainer.setBackground(inputBg);

        EditTextBoldCursor apiKeyEdit = new EditTextBoldCursor(getParentActivity());
        apiKeyEdit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        apiKeyEdit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        apiKeyEdit.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray));
        apiKeyEdit.setHandlesColor(Theme.getColor(Theme.key_chat_TextSelectionCursor));
        apiKeyEdit.setFocusable(true);
        apiKeyEdit.setBackground(null);
        apiKeyEdit.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        apiKeyEdit.setText(apiKeyConfig.String());
        apiKeyEdit.setHint(defaultHint);
        apiKeyContainer.addView(apiKeyEdit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        contentLayout.addView(apiKeyContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // Input 2: Model URL
        TextView urlLabel = new TextView(getParentActivity());
        urlLabel.setText("AI MODEL URL");
        urlLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        urlLabel.setTypeface(AndroidUtilities.bold());
        urlLabel.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        contentLayout.addView(urlLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        FrameLayout urlContainer = new FrameLayout(getParentActivity());
        android.graphics.drawable.GradientDrawable inputBg2 = new android.graphics.drawable.GradientDrawable();
        inputBg2.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        inputBg2.setCornerRadius(AndroidUtilities.dp(10));
        inputBg2.setColor(isDark ? 0x15FFFFFF : 0x0F000000);
        inputBg2.setStroke(AndroidUtilities.dp(1), isDark ? 0x20FFFFFF : 0x1A000000);
        urlContainer.setBackground(inputBg2);

        EditTextBoldCursor urlEdit = new EditTextBoldCursor(getParentActivity());
        urlEdit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        urlEdit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        urlEdit.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray));
        urlEdit.setHandlesColor(Theme.getColor(Theme.key_chat_TextSelectionCursor));
        urlEdit.setFocusable(true);
        urlEdit.setBackground(null);
        urlEdit.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        urlEdit.setText(modelUrlConfig.String());
        urlEdit.setHint(defaultUrl);
        urlContainer.addView(urlEdit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        contentLayout.addView(urlContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 20));

        // Testing indicator / output area
        LinearLayout testOutputContainer = new LinearLayout(getParentActivity());
        testOutputContainer.setOrientation(LinearLayout.VERTICAL);
        testOutputContainer.setVisibility(View.GONE);
        testOutputContainer.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        android.graphics.drawable.GradientDrawable testBg = new android.graphics.drawable.GradientDrawable();
        testBg.setCornerRadius(AndroidUtilities.dp(8));
        testBg.setColor(isDark ? 0x10FFFFFF : 0x08000000);
        testOutputContainer.setBackground(testBg);
        
        TextView testStatusText = new TextView(getParentActivity());
        testStatusText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        testStatusText.setTypeface(AndroidUtilities.bold());
        testOutputContainer.addView(testStatusText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        TextView testDetailsText = new TextView(getParentActivity());
        testDetailsText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        testDetailsText.setTextColor(Theme.getColor(Theme.key_dialogTextGray));
        testOutputContainer.addView(testDetailsText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        contentLayout.addView(testOutputContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 20));

        // Buttons Layout
        LinearLayout buttonsLayout = new LinearLayout(getParentActivity());
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setGravity(Gravity.RIGHT);

        // Test button
        TextView testButton = new TextView(getParentActivity());
        testButton.setText("TEST");
        testButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        testButton.setTypeface(AndroidUtilities.bold());
        testButton.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        testButton.setGravity(Gravity.CENTER);
        testButton.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        testButton.setBackground(Theme.getSelectorDrawable(true));
        buttonsLayout.addView(testButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        // Spacer
        View spacer = new View(getParentActivity());
        buttonsLayout.addView(spacer, LayoutHelper.createLinear(0, 0, 1f));

        // Cancel button
        TextView cancelButton = new TextView(getParentActivity());
        cancelButton.setText("CANCEL");
        cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        cancelButton.setTypeface(AndroidUtilities.bold());
        cancelButton.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        cancelButton.setGravity(Gravity.CENTER);
        cancelButton.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        cancelButton.setBackground(Theme.getSelectorDrawable(true));
        buttonsLayout.addView(cancelButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        // Save button
        TextView saveButton = new TextView(getParentActivity());
        saveButton.setText("SAVE");
        saveButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        saveButton.setTypeface(AndroidUtilities.bold());
        saveButton.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        saveButton.setGravity(Gravity.CENTER);
        saveButton.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        saveButton.setBackground(Theme.getSelectorDrawable(true));
        buttonsLayout.addView(saveButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        contentLayout.addView(buttonsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        builder.setCustomView(contentLayout);
        BottomSheet bottomSheet = builder.create();

        cancelButton.setOnClickListener(v -> bottomSheet.dismiss());

        saveButton.setOnClickListener(v -> {
            String newKey = apiKeyEdit.getText().toString().trim();
            String newUrl = urlEdit.getText().toString().trim();
            apiKeyConfig.setConfigString(newKey);
            modelUrlConfig.setConfigString(newUrl);

            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
            if (getParentLayout() != null) {
                getParentLayout().rebuildAllFragmentViews(false, false);
            }
            bottomSheet.dismiss();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.done, "Settings Saved").show();
        });

        testButton.setOnClickListener(v -> {
            String testKey = apiKeyEdit.getText().toString().trim();
            String testUrl = urlEdit.getText().toString().trim();
            if (TextUtils.isEmpty(testUrl)) {
                testUrl = defaultUrl;
            }

            testStatusText.setText("Testing Connection...");
            testStatusText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            testDetailsText.setText("Please wait while we reach the server...");
            testOutputContainer.setVisibility(View.VISIBLE);

            final String finalTestUrl = testUrl;
            final boolean isGeminiNative = (finalTestUrl.contains("generativelanguage.googleapis.com") || finalTestUrl.contains("googleapis.com")) && !finalTestUrl.contains("openai");
            
            String reqUrl = finalTestUrl;
            if (isGeminiNative && !reqUrl.contains(":generateContent")) {
                if (reqUrl.endsWith("/")) {
                    reqUrl = reqUrl.substring(0, reqUrl.length() - 1) + ":generateContent";
                } else {
                    reqUrl = reqUrl + ":generateContent";
                }
            }
            final String requestUrl = reqUrl;

            Utilities.globalQueue.postRunnable(() -> {
                try {
                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(10, TimeUnit.SECONDS)
                            .build();

                    JSONObject jsonBody = new JSONObject();
                    if (isGeminiNative) {
                        JSONArray contents = new JSONArray();
                        JSONObject contentObj = new JSONObject();
                        JSONArray parts = new JSONArray();
                        JSONObject textPart = new JSONObject();
                        textPart.put("text", "Say ping");
                        parts.put(textPart);
                        contentObj.put("parts", parts);
                        contents.put(contentObj);
                        jsonBody.put("contents", contents);
                    } else {
                        jsonBody.put("model", "gpt-3.5-turbo");
                        JSONArray messages = new JSONArray();
                        JSONObject userMsg = new JSONObject();
                        userMsg.put("role", "user");
                        userMsg.put("content", "Say ping");
                        messages.put(userMsg);
                        jsonBody.put("messages", messages);
                        jsonBody.put("max_tokens", 5);
                    }

                    Request.Builder requestBuilder = new Request.Builder()
                            .url(requestUrl)
                            .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonBody.toString()));

                    if (isGeminiNative) {
                        requestBuilder.addHeader("x-goog-api-key", testKey);
                    } else {
                        requestBuilder.addHeader("Authorization", "Bearer " + testKey);
                    }

                    client.newCall(requestBuilder.build()).enqueue(new Callback() {
                        @Override
                        public void onFailure(Call call, java.io.IOException e) {
                            AndroidUtilities.runOnUIThread(() -> {
                                testStatusText.setText("Connection Failed");
                                testStatusText.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                                testDetailsText.setText("Error: " + e.getMessage());
                            });
                        }

                        @Override
                        public void onResponse(Call call, Response response) throws java.io.IOException {
                            String body = response.body().string();
                            int code = response.code();
                            AndroidUtilities.runOnUIThread(() -> {
                                if (response.isSuccessful()) {
                                    testStatusText.setText("Connection Successful!");
                                    testStatusText.setTextColor(isDark ? 0xFF4CAF50 : 0xFF2E7D32);
                                    testDetailsText.setText("HTTP Code: " + code + "\nAPI Type: " + (isGeminiNative ? "Gemini Native" : "OpenAI Compatible"));
                                } else {
                                    testStatusText.setText("Connection Failed (HTTP " + code + ")");
                                    testStatusText.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                                    testDetailsText.setText("Response: " + body);
                                }
                            });
                        }
                    });
                } catch (Exception e) {
                    AndroidUtilities.runOnUIThread(() -> {
                        testStatusText.setText("Local Error");
                        testStatusText.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                        testDetailsText.setText("Error: " + e.getMessage());
                    });
                }
            });
        });

        showDialog(bottomSheet);
    }
    // [Alexgram: AI Reply] - End


    private void showBottomSheet() {
        if (getParentActivity() == null) {
            return;
        }
        BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity());
        builder.setApplyTopPadding(false);
        builder.setApplyBottomPadding(false);
        LinearLayout linearLayout = new LinearLayout(getParentActivity());
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        builder.setCustomView(linearLayout);

        HeaderCell headerCell = new HeaderCell(getParentActivity(), Theme.key_dialogTextBlue2, 21, 15, false);
        headerCell.setText(getString(R.string.MessageSavingSaveMedia).toUpperCase());
        linearLayout.addView(headerCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextCheckBoxCell[] cells = new TextCheckBoxCell[5];
        for (int a = 0; a < cells.length; a++) {
            TextCheckBoxCell checkBoxCell = cells[a] = new TextCheckBoxCell(getParentActivity(), true, false);
            if (a == 0) {
                cells[a].setTextAndCheck(getString(R.string.MessageSavingSaveMediaInPrivateChats), NaConfig.INSTANCE.getSaveMediaInPrivateChats().Bool(), true);
            } else if (a == 1) {
                cells[a].setTextAndCheck(getString(R.string.MessageSavingSaveMediaInPublicChannels), NaConfig.INSTANCE.getSaveMediaInPublicChannels().Bool(), true);
            } else if (a == 2) {
                cells[a].setTextAndCheck(getString(R.string.MessageSavingSaveMediaInPrivateChannels), NaConfig.INSTANCE.getSaveMediaInPrivateChannels().Bool(), true);
            } else if (a == 3) {
                cells[a].setTextAndCheck(getString(R.string.MessageSavingSaveMediaInPublicGroups), NaConfig.INSTANCE.getSaveMediaInPublicGroups().Bool(), true);
            } else { // a == 4
                cells[a].setTextAndCheck(getString(R.string.MessageSavingSaveMediaInPrivateGroups), NaConfig.INSTANCE.getSaveMediaInPrivateGroups().Bool(), true);
            }
            cells[a].setBackground(Theme.getSelectorDrawable(false));
            cells[a].setOnClickListener(v -> {
                if (!v.isEnabled()) {
                    return;
                }
                checkBoxCell.setChecked(!checkBoxCell.isChecked());
            });
            linearLayout.addView(cells[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50));
        }

        FrameLayout buttonsLayout = new FrameLayout(getParentActivity());
        buttonsLayout.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        linearLayout.addView(buttonsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52));

        TextView textView = new TextView(getParentActivity());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        textView.setGravity(Gravity.CENTER);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setText(getString(R.string.Cancel).toUpperCase());
        textView.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        buttonsLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.TOP | Gravity.LEFT));
        textView.setOnClickListener(v14 -> builder.getDismissRunnable().run());

        textView = new TextView(getParentActivity());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        textView.setGravity(Gravity.CENTER);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setText(getString(R.string.Save).toUpperCase());
        textView.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        buttonsLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.TOP | Gravity.RIGHT));
        textView.setOnClickListener(v1 -> {
            NaConfig.INSTANCE.getSaveMediaInPrivateChats().setConfigBool(cells[0].isChecked());
            NaConfig.INSTANCE.getSaveMediaInPublicChannels().setConfigBool(cells[1].isChecked());
            NaConfig.INSTANCE.getSaveMediaInPrivateChannels().setConfigBool(cells[2].isChecked());
            NaConfig.INSTANCE.getSaveMediaInPublicGroups().setConfigBool(cells[3].isChecked());
            NaConfig.INSTANCE.getSaveMediaInPrivateGroups().setConfigBool(cells[4].isChecked());

            builder.getDismissRunnable().run();
        });
        showDialog(builder.create());
    }

    public void refreshAyuDataSize() {
        if (listAdapter != null) {
            ((ConfigCellTextCheckIcon) clearMessageDatabaseRow).setValue(AyuData.totalSize > 0 ? AndroidUtilities.formatFileSize(AyuData.totalSize) : "...");
            listAdapter.notifyItemChanged(cellGroup.rows.indexOf(clearMessageDatabaseRow));
        }
    }

    // [Alexgram: Home Drawer] - Start
    private void checkDrawerElementsRow() {
        boolean enabled = NekoConfig.navigationDrawerEnabled.Bool();
        if (listAdapter == null) {
            if (!enabled) {
                cellGroup.rows.remove(drawerElementsRow);
            }
            return;
        }
        if (enabled) {
            final int index = cellGroup.rows.indexOf(navigationDrawerRow);
            if (!cellGroup.rows.contains(drawerElementsRow)) {
                cellGroup.rows.add(index + 1, drawerElementsRow);
                listAdapter.notifyItemInserted(index + 1);
            }
        } else {
            final int index = cellGroup.rows.indexOf(drawerElementsRow);
            if (index != -1) {
                cellGroup.rows.remove(drawerElementsRow);
                listAdapter.notifyItemRemoved(index);
            }
        }
        addRowsToMap(cellGroup);
    }
    // [Alexgram: Home Drawer] - End

    private void checkStoriesRows() {
        boolean disabled = NaConfig.INSTANCE.getDisableStories().Bool();
        if (listAdapter == null) {
            if (disabled) {
                cellGroup.rows.remove(hideFromHeaderRow);
            }
            return;
        }
        if (!disabled) {
            final int index = cellGroup.rows.indexOf(disableStoriesRow);
            if (!cellGroup.rows.contains(hideFromHeaderRow)) {
                cellGroup.rows.add(index + 1, hideFromHeaderRow);
                listAdapter.notifyItemInserted(index + 1);
            }
        } else {
            final int index = cellGroup.rows.indexOf(hideFromHeaderRow);
            if (index != -1) {
                cellGroup.rows.remove(hideFromHeaderRow);
                listAdapter.notifyItemRemoved(index);
            }
        }
    }

    private void checkSaveDeletedRows() {
        final boolean isSaveEnabled = NaConfig.INSTANCE.getEnableSaveDeletedMessages().Bool();
        final List<AbstractConfigCell> allManagedRows = Arrays.asList(
                messageSavingSaveMediaRow,
                saveDeletedMessageForBotsUserRow,
                saveDeletedMessageInBotChatRow,
                translucentDeletedMessagesRow,
                useDeletedIconRow,
                customDeletedMarkRow,
                // [Alexgram: Deleted Icon Color Save Managed] - Start
                deletedIconColorRow
                // [Alexgram: Deleted Icon Color Save Managed] - End
        );
        if (listAdapter == null) {
            if (!isSaveEnabled) {
                cellGroup.rows.removeAll(allManagedRows);
            }
            return;
        }
        final int anchorIndex = cellGroup.rows.indexOf(enableSaveEditsHistoryRow);
        int firstManagedRowIndex = -1;
        int lastManagedRowIndex = -1;
        for (int i = anchorIndex + 1; i < cellGroup.rows.size(); i++) {
            if (allManagedRows.contains(cellGroup.rows.get(i))) {
                if (firstManagedRowIndex == -1) {
                    firstManagedRowIndex = i;
                }
                lastManagedRowIndex = i;
            }
        }
        if (firstManagedRowIndex != -1) {
            int count = lastManagedRowIndex - firstManagedRowIndex + 1;
            cellGroup.rows.subList(firstManagedRowIndex, lastManagedRowIndex + 1).clear();
            listAdapter.notifyItemRangeRemoved(firstManagedRowIndex, count);
        }
        if (isSaveEnabled) {
            final List<AbstractConfigCell> rowsToAdd = new ArrayList<>();
            rowsToAdd.add(messageSavingSaveMediaRow);
            rowsToAdd.add(saveDeletedMessageForBotsUserRow);
            if (NaConfig.INSTANCE.getSaveDeletedMessageForBotUser().Bool()) {
                rowsToAdd.add(saveDeletedMessageInBotChatRow);
            }
            rowsToAdd.add(translucentDeletedMessagesRow);
            rowsToAdd.add(useDeletedIconRow);
            if (!NaConfig.INSTANCE.getUseDeletedIcon().Bool()) {
                rowsToAdd.add(customDeletedMarkRow);
            } else {
                // [Alexgram: Deleted Icon Color Save Add] - Start
                rowsToAdd.add(deletedIconColorRow);
                // [Alexgram: Deleted Icon Color Save Add] - End
            }
            cellGroup.rows.addAll(anchorIndex + 1, rowsToAdd);
            listAdapter.notifyItemRangeInserted(anchorIndex + 1, rowsToAdd.size());
        }
        addRowsToMap(cellGroup);
    }

    private void checkSaveBotMsgRows() {
        boolean enabled = NaConfig.INSTANCE.getSaveDeletedMessageForBotUser().Bool();
        if (listAdapter == null) {
            if (!enabled) {
                cellGroup.rows.remove(saveDeletedMessageInBotChatRow);
            }
            return;
        }
        if (enabled) {
            final int index = cellGroup.rows.indexOf(saveDeletedMessageForBotsUserRow);
            if (!cellGroup.rows.contains(saveDeletedMessageInBotChatRow)) {
                cellGroup.rows.add(index + 1, saveDeletedMessageInBotChatRow);
                listAdapter.notifyItemInserted(index + 1);
            }
        } else {
            final int index = cellGroup.rows.indexOf(saveDeletedMessageInBotChatRow);
            if (index != -1) {
                cellGroup.rows.remove(saveDeletedMessageInBotChatRow);
                listAdapter.notifyItemRemoved(index);
            }
        }
        addRowsToMap(cellGroup);
    }

    // [Alexgram: Deleted Icon Color Visibility] - Start
    private void checkUseDeletedIconRows() {
        boolean enabled = NaConfig.INSTANCE.getUseDeletedIcon().Bool();
        if (listAdapter == null) {
            if (enabled) {
                cellGroup.rows.remove(customDeletedMarkRow);
            } else {
                cellGroup.rows.remove(deletedIconColorRow);
            }
            return;
        }
        if (!enabled) {
            final int colorIndex = cellGroup.rows.indexOf(deletedIconColorRow);
            if (colorIndex != -1) {
                cellGroup.rows.remove(deletedIconColorRow);
                listAdapter.notifyItemRemoved(colorIndex);
            }
            final int index = cellGroup.rows.indexOf(useDeletedIconRow);
            if (!cellGroup.rows.contains(customDeletedMarkRow)) {
                cellGroup.rows.add(index + 1, customDeletedMarkRow);
                listAdapter.notifyItemInserted(index + 1);
            }
        } else {
            final int index = cellGroup.rows.indexOf(customDeletedMarkRow);
            if (index != -1) {
                cellGroup.rows.remove(customDeletedMarkRow);
                listAdapter.notifyItemRemoved(index);
            }
            final int useIconIndex = cellGroup.rows.indexOf(useDeletedIconRow);
            if (!cellGroup.rows.contains(deletedIconColorRow)) {
                cellGroup.rows.add(useIconIndex + 1, deletedIconColorRow);
                listAdapter.notifyItemInserted(useIconIndex + 1);
            }
        }
        addRowsToMap(cellGroup);
    }
    // [Alexgram: Deleted Icon Color Visibility] - End

    private void updateRunInBackground(boolean enabled) {
        MessagesController.getGlobalNotificationsSettings().edit()
                .putBoolean("pushService", enabled)
                .putBoolean("pushConnection", enabled)
                .apply();

        MessagesController.getGlobalMainSettings().edit()
                .putBoolean("keepAliveService", enabled)
                .putBoolean("backgroundConnection", enabled)
                .apply();

        for (int i = 0; i < org.telegram.messenger.UserConfig.MAX_ACCOUNT_COUNT; i++) {
            SharedPreferences notificationsSettings = MessagesController.getNotificationsSettings(i);
            notificationsSettings.edit()
                    .putBoolean("pushService", enabled)
                    .putBoolean("pushConnection", enabled)
                    .apply();

            SharedPreferences mainSettings = MessagesController.getMainSettings(i);
            mainSettings.edit()
                    .putBoolean("keepAliveService", enabled)
                    .putBoolean("backgroundConnection", enabled)
                    .apply();

            MessagesController.getInstance(i).keepAliveService = enabled;
            MessagesController.getInstance(i).backgroundConnection = enabled;
            ConnectionsManager.getInstance(i).setPushConnectionEnabled(enabled);
        }

        Intent serviceIntent = new Intent(ApplicationLoader.applicationContext, org.telegram.messenger.NotificationsService.class);
        if (!enabled) {
            ApplicationLoader.applicationContext.stopService(serviceIntent);
        }
        ApplicationLoader.startPushService();

        // Request all necessary permissions and settings when enabling background run
        if (enabled && getParentActivity() != null) {
            SharedPreferences prefs = MessagesController.getGlobalMainSettings();
            if (!prefs.getBoolean("asked_background_permissions", false)) {
                AndroidUtilities.requestIgnoreBatteryOptimizations(getParentActivity());
                AndroidUtilities.requestNotificationPermission(getParentActivity());

                Intent autoStart = AndroidUtilities.getAutoStartIntent();
                if (autoStart != null) {
                    new AlertDialog.Builder(getParentActivity())
                            .setTitle("Background Reliability")
                            .setMessage("To ensure the background service works perfectly, please enable Auto-Start or Background Activity in your device settings. Allow battery optimizations first if prompted.")
                            .setPositiveButton("Open Settings", (dialog, which) -> {
                                try {
                                    getParentActivity().startActivity(autoStart);
                                } catch (Exception ignored) {
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                } else {
                    new AlertDialog.Builder(getParentActivity())
                            .setTitle("Background Reliability")
                            .setMessage("To ensure background features work reliably, please enable auto-start or background run for this app in your device settings.")
                            .setPositiveButton("OK", null)
                            .show();
                }
                prefs.edit().putBoolean("asked_background_permissions", true).apply();
            }
        }
    }

    // [Alexgram: Deleted Icon Color Helper] - Start
    private void showDeletedColorOptions(View view) {
        PopupBuilder builder = new PopupBuilder(view);
        builder.setItems(new String[]{
                getString(R.string.Default),
                "Choose Custom Color..."
        }, (i, __) -> {
            if (i == 0) {
                NaConfig.INSTANCE.getDeletedIconColor().setConfigInt(0);
                if (listAdapter != null) {
                    listAdapter.notifyItemChanged(cellGroup.rows.indexOf(deletedIconColorRow));
                }
                TimeStringHelper.deletedSpan = null;
                org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
            } else if (i == 1) {
                showDeletedColorPicker();
            }
            return kotlin.Unit.INSTANCE;
        });
        builder.show();
    }

    private void showDeletedColorPicker() {
        if (getParentActivity() == null) return;

        org.telegram.ui.Components.Paint.ColorPickerBottomSheet colorPicker =
                new org.telegram.ui.Components.Paint.ColorPickerBottomSheet(getParentActivity(), getResourceProvider());

        colorPicker.setPipetteDelegate(new org.telegram.ui.Components.Paint.ColorPickerBottomSheet.PipetteDelegate() {
            @Override
            public boolean isPipetteAvailable() { return false; }
            @Override
            public boolean isPipetteVisible() { return false; }
            @Override
            public android.view.ViewGroup getContainerView() { return null; }
            @Override
            public android.view.View getSnapshotDrawingView() { return null; }
            @Override
            public void onDrawImageOverCanvas(android.graphics.Bitmap bitmap, android.graphics.Canvas canvas) {}
            @Override
            public void onStartColorPipette() {}
            @Override
            public void onStopColorPipette() {}
            @Override
            public void onColorSelected(int color) {}
        });

        colorPicker.setColorListener(color -> {
            NaConfig.INSTANCE.getDeletedIconColor().setConfigInt(color);
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(cellGroup.rows.indexOf(deletedIconColorRow));
            }
            TimeStringHelper.deletedSpan = null;
            org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
        });

        int currentColor = NaConfig.INSTANCE.getDeletedIconColor().Int();
        colorPicker.setColor(currentColor != 0 ? currentColor : 0xFFFF0000);
        showDialog(colorPicker);
    }
    // [Alexgram: Deleted Icon Color Helper] - End

    // [Alexgram: Translucent Helper] - Start
    private void showTranslucentOptions() {
        if (getParentActivity() == null) return;

        LinearLayout contentLayout = new LinearLayout(getParentActivity());
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(16), AndroidUtilities.dp(24), AndroidUtilities.dp(16));

        // Switch to enable/disable Translucent Deleted Messages (Custom layout to prevent truncation)
        LinearLayout checkboxLayout = new LinearLayout(getParentActivity());
        checkboxLayout.setOrientation(LinearLayout.HORIZONTAL);
        checkboxLayout.setGravity(Gravity.CENTER_VERTICAL);
        checkboxLayout.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        checkboxLayout.setBackground(Theme.getSelectorDrawable(false));

        TextView checkboxText = new TextView(getParentActivity());
        checkboxText.setText("Enable Translucent Deleted Messages");
        checkboxText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        checkboxText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        checkboxLayout.addView(checkboxText, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

        org.telegram.ui.Components.CheckBoxSquare checkBoxSquare = new org.telegram.ui.Components.CheckBoxSquare(getParentActivity(), true);
        checkBoxSquare.setChecked(NaConfig.INSTANCE.getTranslucentDeletedMessages().Bool(), false);
        checkboxLayout.addView(checkBoxSquare, LayoutHelper.createLinear(18, 18, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 4, 0));
        
        contentLayout.addView(checkboxLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Translucency percentage label
        TextView valueLabel = new TextView(getParentActivity());
        valueLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        valueLabel.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        contentLayout.addView(valueLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 4));

        // SeekBar (Slider)
        SeekBar seekBar = new SeekBar(getParentActivity());
        seekBar.setMax(100);
        int currentAlpha = NaConfig.INSTANCE.getTranslucentDeletedMessagesAlpha().Int();
        seekBar.setProgress(currentAlpha);
        contentLayout.addView(seekBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 16));

        // Live Preview Title
        TextView previewTitle = new TextView(getParentActivity());
        previewTitle.setText("Live Preview");
        previewTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        previewTitle.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        previewTitle.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        contentLayout.addView(previewTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 8));

        // Live Preview Container (Chat Background)
        FrameLayout previewContainer = new FrameLayout(getParentActivity());
        previewContainer.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16));
        
        android.graphics.drawable.Drawable wallpaperDrawable = Theme.getCachedWallpaper();
        if (wallpaperDrawable != null) {
            previewContainer.setBackground(wallpaperDrawable);
        } else {
            int chatWallpaperColor = Theme.getColor(Theme.key_chat_wallpaper);
            if (chatWallpaperColor == 0) {
                chatWallpaperColor = 0xFFECE5DD;
            }
            android.graphics.drawable.GradientDrawable containerBg = new android.graphics.drawable.GradientDrawable();
            containerBg.setColor(chatWallpaperColor);
            previewContainer.setBackground(containerBg);
        }

        previewContainer.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(android.view.View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), AndroidUtilities.dp(12));
            }
        });
        previewContainer.setClipToOutline(true);
        
        contentLayout.addView(previewContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // Outgoing Deleted Message bubble
        LinearLayout messageBubble = new LinearLayout(getParentActivity());
        messageBubble.setOrientation(LinearLayout.VERTICAL);
        messageBubble.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
        
        int outBubbleColor = Theme.getColor(Theme.key_chat_outBubble);
        if (outBubbleColor == 0) {
            outBubbleColor = 0xFFE2F9C3;
        }
        android.graphics.drawable.GradientDrawable bubbleBg = new android.graphics.drawable.GradientDrawable();
        bubbleBg.setColor(outBubbleColor);
        bubbleBg.setCornerRadii(new float[] {
            AndroidUtilities.dp(16), AndroidUtilities.dp(16),
            AndroidUtilities.dp(16), AndroidUtilities.dp(16),
            AndroidUtilities.dp(2), AndroidUtilities.dp(2),
            AndroidUtilities.dp(16), AndroidUtilities.dp(16)
        });
        messageBubble.setBackground(bubbleBg);

        // Preview text inside message bubble
        TextView messageText = new TextView(getParentActivity());
        messageText.setText("This is a deleted message.\nDrag the slider to adjust translucent level!");
        int outTextColor = Theme.getColor(Theme.key_chat_messageTextOut);
        if (outTextColor == 0) {
            outTextColor = 0xFF000000;
        }
        messageText.setTextColor(outTextColor);
        messageText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        messageBubble.addView(messageText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        // Time and Deleted Icon
        LinearLayout infoLayout = new LinearLayout(getParentActivity());
        infoLayout.setOrientation(LinearLayout.HORIZONTAL);
        infoLayout.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        
        TextView timeView = new TextView(getParentActivity());
        timeView.setText("12:00 PM ");
        timeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        int outTimeColor = Theme.getColor(Theme.key_chat_outTimeText);
        if (outTimeColor == 0) {
            outTimeColor = 0xFF70B15C;
        }
        timeView.setTextColor(outTimeColor);
        infoLayout.addView(timeView);

        boolean useDeletedIcon = NaConfig.INSTANCE.getUseDeletedIcon().Bool();
        int deletedColor = NaConfig.INSTANCE.getDeletedIconColor().Int();
        if (deletedColor == 0) {
            deletedColor = outTimeColor;
        }

        if (useDeletedIcon) {
            android.widget.ImageView deletedIconView = new android.widget.ImageView(getParentActivity());
            deletedIconView.setImageResource(R.drawable.msg_delete_solar);
            deletedIconView.setColorFilter(deletedColor, android.graphics.PorterDuff.Mode.SRC_IN);
            infoLayout.addView(deletedIconView, LayoutHelper.createLinear(14, 14, Gravity.CENTER_VERTICAL, 0, 0, 0, 0));
        } else {
            TextView deletedTextView = new TextView(getParentActivity());
            String customMark = NaConfig.INSTANCE.getCustomDeletedMark().String();
            deletedTextView.setText(!TextUtils.isEmpty(customMark) ? customMark : "Deleted");
            deletedTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            deletedTextView.setTextColor(outTimeColor); // Watermark text inherits standard theme time text color, ignoring custom icon color
            deletedTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            infoLayout.addView(deletedTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 0, 0));
        }

        messageBubble.addView(infoLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT, 0, 4, 0, 0));

        FrameLayout.LayoutParams bubbleParams = new FrameLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        bubbleParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        bubbleParams.rightMargin = AndroidUtilities.dp(8);
        bubbleParams.leftMargin = AndroidUtilities.dp(48);
        previewContainer.addView(messageBubble, bubbleParams);

        // Update preview dynamically
        Runnable updatePreview = () -> {
            boolean enabled = checkBoxSquare.isChecked();
            int alphaPercent = seekBar.getProgress();
            
            seekBar.setEnabled(enabled);
            valueLabel.setText("Opacity: " + alphaPercent + "%");
            
            if (enabled) {
                messageBubble.setAlpha(alphaPercent / 100.0f);
            } else {
                messageBubble.setAlpha(1.0f);
            }
        };

        checkboxLayout.setOnClickListener(v -> {
            checkBoxSquare.setChecked(!checkBoxSquare.isChecked(), true);
            updatePreview.run();
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updatePreview.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        updatePreview.run();

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Translucent Deleted Messages");
        builder.setView(contentLayout);
        builder.setPositiveButton(getString(R.string.Save), (dialog, which) -> {
            NaConfig.INSTANCE.getTranslucentDeletedMessages().setConfigBool(checkBoxSquare.isChecked());
            NaConfig.INSTANCE.getTranslucentDeletedMessagesAlpha().setConfigInt(seekBar.getProgress());
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(cellGroup.rows.indexOf(translucentDeletedMessagesRow));
            }
            org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }
    // [Alexgram: Translucent Helper] - End

}
