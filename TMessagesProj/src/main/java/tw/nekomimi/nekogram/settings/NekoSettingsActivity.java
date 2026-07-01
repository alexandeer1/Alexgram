package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import org.telegram.messenger.LocaleController;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.AIAssistanceSettingsActivity;
import org.telegram.ui.Plugins.PluginsActivity;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.Switch;
import org.telegram.ui.LaunchActivity;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.config.ConfigItem;
import tw.nekomimi.nekogram.helpers.AppRestartHelper;
import tw.nekomimi.nekogram.helpers.CloudSettingsHelper;
import tw.nekomimi.nekogram.helpers.HiddenChatsController;
import tw.nekomimi.nekogram.helpers.PasscodeHelper;
import tw.nekomimi.nekogram.helpers.QuickSettingEntry;
import tw.nekomimi.nekogram.helpers.QuickSettingsController;
import tw.nekomimi.nekogram.helpers.SettingsBackupHelper;
import tw.nekomimi.nekogram.ui.HiddenChatsPasscodeActivity;
import tw.nekomimi.nekogram.utils.AlertUtil;
import xyz.nextalone.nagram.NaConfig;

import java.util.ArrayList;
import java.util.List;

// [Alexgram: A-Settings UI] - Start
public class NekoSettingsActivity extends BaseNekoSettingsActivity {

    private final List<String> recentSearchKeys = new ArrayList<>();
    
    private void loadRecentSearches() {
        android.content.SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("alexgram_settings_search", Context.MODE_PRIVATE);
        String data = prefs.getString("history", "");
        recentSearchKeys.clear();
        if (!data.isEmpty()) {
            String[] keys = data.split(",");
            for (String k : keys) {
                if (!k.trim().isEmpty()) recentSearchKeys.add(k.trim());
            }
        }
    }

    private void saveRecentSearches() {
        android.content.SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("alexgram_settings_search", Context.MODE_PRIVATE);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < recentSearchKeys.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(recentSearchKeys.get(i));
        }
        prefs.edit().putString("history", sb.toString()).apply();
    }

    private void addToRecentSearches(String key) {
        recentSearchKeys.remove(key);
        recentSearchKeys.add(0, key);
        while (recentSearchKeys.size() > 10) {
            recentSearchKeys.remove(recentSearchKeys.size() - 1);
        }
        saveRecentSearches();
    }

    private boolean isDark;
    private int cardBg;
    private int cardBorder;
    private int dividerColor;

    private int headerRow;
    private int quickSettingsHeaderRow;
    private int quickSettingsStartRow;
    private int quickSettingsEndRow;
    private int hideContactsRow;
    private int ghostModeRow;
    private int musicGraphRow;
    private int saveDeletedRow;
    private int privacyHeaderRow;
    private int hiddenChatsRow;
    private int analyticsRow;
    private int coreHeaderRow;
    private int coreSettingsRow;
    private int advancedHeaderRow;
    private int advancedSettingsRow;
    private int actionsRow;
    private int footerRow;

    @Override
    protected void updateRows() {
        rowCount = 0;
        rowMap.clear();
        rowMapReverse.clear();

        headerRow = addRow();
        
        quickSettingsHeaderRow = addRow();
        
        List<QuickSettingEntry> quickSettings = QuickSettingsController.getInstance().getQuickSettings();
        if (quickSettings.isEmpty()) {
            quickSettingsStartRow = -1;
            quickSettingsEndRow = -1;
        } else {
            quickSettingsStartRow = rowCount;
            for (QuickSettingEntry entry : quickSettings) {
                addRow(entry.key);
            }
            quickSettingsEndRow = rowCount;
        }

        hideContactsRow = addRow("hide_contacts");
        ghostModeRow = addRow("ghost_mode");
        musicGraphRow = addRow("music_graph");
        saveDeletedRow = addRow("save_deleted");
        
        privacyHeaderRow = addRow();
        hiddenChatsRow = addRow("hidden_chats");
        analyticsRow = addRow("analytics");
        
        coreHeaderRow = addRow();
        coreSettingsRow = addRow("core_settings");
        
        advancedHeaderRow = addRow();
        advancedSettingsRow = addRow("advanced_settings");
        
        actionsRow = addRow("actions");
        footerRow = addRow();
    }

    @Override
    public View createView(Context context) {
        setupColors();
        
        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Color.TRANSPARENT);

        // Add animated background
        AlexgramSettingsHeaderView backgroundView = new AlexgramSettingsHeaderView(context);
        frameLayout.addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString("ASettings", R.string.ASettings));
        actionBar.setBackgroundColor(Color.TRANSPARENT);
        actionBar.setCastShadows(false);
        actionBar.setAddToContainer(false);
        
        int color = isDark ? Color.WHITE : 0xFF1A1A2E;
        actionBar.setItemsColor(color, false);
        actionBar.setTitleColor(color);
        
        final FrameLayout searchContainer = new FrameLayout(context);
        searchContainer.setVisibility(View.GONE);
        searchContainer.setAlpha(0.0f);
        
        org.telegram.ui.Components.RecyclerListView searchListView = new org.telegram.ui.Components.RecyclerListView(context);
        searchListView.setLayoutManager(new LinearLayoutManager(context));
        searchListView.setPadding(0, AndroidUtilities.dp(64) + (AndroidUtilities.isTablet() ? 0 : AndroidUtilities.statusBarHeight), 0, AndroidUtilities.dp(16));
        searchListView.setClipToPadding(true);
        
        final SearchAdapter searchAdapter = new SearchAdapter(context);
        searchListView.setAdapter(searchAdapter);
        searchContainer.addView(searchListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        
        final int search_id = 1;
        final int cloud_id = 2;
        org.telegram.ui.ActionBar.ActionBarMenu menu = actionBar.createMenu();
        org.telegram.ui.ActionBar.ActionBarMenuItem searchItem = menu.addItem(search_id, R.drawable.ic_ab_search_solar);
        searchItem.setIsSearchField(true).setActionBarMenuItemSearchListener(new org.telegram.ui.ActionBar.ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                searchContainer.setVisibility(View.VISIBLE);
                searchContainer.animate().alpha(1.0f).setDuration(200).start();
                listView.animate().alpha(0.0f).setDuration(200).start();
                loadRecentSearches();
                searchAdapter.search("");
            }
            @Override
            public void onSearchCollapse() {
                searchContainer.animate().alpha(0.0f).setDuration(200).withEndAction(() -> searchContainer.setVisibility(View.GONE)).start();
                listView.animate().alpha(1.0f).setDuration(200).start();
                searchAdapter.clear();
            }
            @Override
            public void onTextChanged(android.widget.EditText editText) {
                String query = editText.getText().toString();
                searchAdapter.search(query);
            }
        });
        searchItem.setSearchFieldHint("Search anything...");
        
        org.telegram.ui.ActionBar.ActionBarMenuItem cloudItem = menu.addItem(cloud_id, R.drawable.cloud_sync);
        if (searchItem != null) searchItem.setIconColor(color);
        if (cloudItem != null) cloudItem.setIconColor(color);
        
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == cloud_id) {
                    CloudSettingsHelper.getInstance().showDialog(NekoSettingsActivity.this);
                }
            }
        });

        actionBar.getTitleTextView().setAlpha(0.0f);
        
        listView = new org.telegram.ui.Components.BlurredRecyclerView(context);
        listView.disableBlurTopPadding = true;
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setPadding(0, AndroidUtilities.dp(40) + (AndroidUtilities.isTablet() ? 0 : AndroidUtilities.statusBarHeight), 0, AndroidUtilities.dp(40));
        listView.setClipToPadding(false);
        listView.setAdapter(listAdapter = createAdapter(context));
        listView.setOnItemClickListener(this::onItemClick);
        listView.setOnItemLongClickListener(this::onItemLongClick);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                int scrollY = recyclerView.computeVerticalScrollOffset();
                float alpha = Math.max(0, Math.min(1.0f, (scrollY - AndroidUtilities.dp(100)) / (float) AndroidUtilities.dp(60)));
                actionBar.getTitleTextView().setAlpha(alpha);
                actionBar.setBackgroundColor(ColorUtils.setAlphaComponent(isDark ? 0xFF1E2732 : Color.WHITE, (int) (alpha * 255)));
            }
        });
        
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        frameLayout.addView(searchContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));  frameLayout.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void setupColors() {
        isDark = Theme.getActiveTheme().isDark();
        if (isDark) {
            cardBg = 0x221E2732;
            cardBorder = 0x15FFFFFF;
            dividerColor = 0x10FFFFFF;
        } else {
            cardBg = 0x40FFFFFF;
            cardBorder = 0x20000000;
            dividerColor = 0x15000000;
        }
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position >= quickSettingsStartRow && position < quickSettingsEndRow) {
            QuickSettingEntry entry = QuickSettingsController.getInstance().getQuickSettings().get(position - quickSettingsStartRow);
            if (entry.type == QuickSettingEntry.TYPE_SWITCH) {
                ConfigItem config = findConfig(entry.key);
                if (config != null) {
                    config.toggleConfigBool();
                    if (view instanceof CardSwitchCell) {
                        ((CardSwitchCell) view).setChecked(config.Bool());
                    }
                }
            } else {
                try {
                    Class<?> clazz = Class.forName(entry.activityClass);
                    BaseFragment fragment = (BaseFragment) clazz.getConstructor().newInstance();
                    android.os.Bundle args = new android.os.Bundle();
                    args.putString("scrollToKey", entry.key);
                    fragment.setArguments(args);
                    presentFragment(fragment);
                } catch (Exception e) {
                    org.telegram.messenger.FileLog.e(e);
                }
            }
        }
    }

    @Override
    protected boolean onItemLongClick(View view, int position, float x, float y) {
        if (position >= quickSettingsStartRow && position < quickSettingsEndRow) {
            String key = rowMapReverse.get(position);
            if (key != null) {
                ItemOptions options = makeLongClickOptions(view);
                options.add(R.drawable.msg_delete, "Remove from Quick Settings", () -> {
                    QuickSettingsController.getInstance().removeQuickSetting(key);
                    updateRows();
                    listAdapter.notifyDataSetChanged();
                    BulletinFactory.of(this).createSimpleBulletin(R.drawable.msg_delete, "Removed from Quick Settings").show();
                });
                options.show();
                return true;
            }
        }
        return super.onItemLongClick(view, position, x, y);
    }

    private ConfigItem findConfig(String key) {
        for (ConfigItem config : NekoConfig.getAllConfigs()) {
            if (config.key.equals(key)) return config;
        }
        for (ConfigItem config : NaConfig.INSTANCE.getAllConfigs()) {
            if (config.key.equals(key)) return config;
        }
        return null;
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString("ASettings", R.string.ASettings);
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0: view = new BrandingHeaderView(mContext); break;
                case 1: view = new SectionHeaderCell(mContext); break;
                case 2: view = new CardSwitchCell(mContext); break;
                case 3: view = new CardItemCell(mContext); break;
                case 4: view = new ActionsCell(mContext); break;
                case 5: view = new FooterCell(mContext); break;
                case 6: view = new CardSwitchCell(mContext); break; // Quick Setting Switch
                case 7: view = new CardItemCell(mContext); break; // Quick Setting Dialog/Nav
                default: view = new View(mContext); break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            int viewType = holder.getItemViewType();
            if (viewType == 0) {
                ((BrandingHeaderView) holder.itemView).update();
            } else if (viewType == 1) {
                SectionHeaderCell cell = (SectionHeaderCell) holder.itemView;
                if (position == quickSettingsHeaderRow) cell.setText("QUICK SETTINGS");
                else if (position == privacyHeaderRow) cell.setText("PRIVACY");
                else if (position == coreHeaderRow) cell.setText("CORE SETTINGS");
                else if (position == advancedHeaderRow) cell.setText("ADVANCED");
            } else if (viewType == 6) {
                CardSwitchCell cell = (CardSwitchCell) holder.itemView;
                QuickSettingEntry entry = QuickSettingsController.getInstance().getQuickSettings().get(position - quickSettingsStartRow);
                ConfigItem config = findConfig(entry.key);
                int resId = mContext.getResources().getIdentifier(entry.iconResName, "drawable", mContext.getPackageName());
                if (resId == 0) resId = R.drawable.msg_settings;
                
                boolean isLast = position == quickSettingsEndRow - 1;
                cell.setData(entry.title, null, resId, config != null && config.Bool(), isChecked -> {
                    if ("music_graph".equals(entry.key) && isChecked && android.os.Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        android.content.SharedPreferences prefs = org.telegram.messenger.MessagesController.getGlobalMainSettings();
                        if (prefs.getBoolean("asked_mic_music_graph", false) || !getParentActivity().shouldShowRequestPermissionRationale(android.Manifest.permission.RECORD_AUDIO)) {
                            tw.nekomimi.nekogram.utils.AlertUtil.showMicPermissionDialog(getParentActivity());
                        } else {
                            prefs.edit().putBoolean("asked_mic_music_graph", true).apply();
                            getParentActivity().requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 101);
                        }
                        cell.setChecked(false);
                        return;
                    }
                    if (config != null) {
                        config.setConfigBool(isChecked);
                        if ("HideContacts".equals(config.key)) {
                            AppRestartHelper.triggerRebirth(mContext, new Intent(mContext, LaunchActivity.class));
                        }
                    }
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
                }, position == quickSettingsStartRow, isLast);
            } else if (viewType == 7) {
                CardItemCell cell = (CardItemCell) holder.itemView;
                QuickSettingEntry entry = QuickSettingsController.getInstance().getQuickSettings().get(position - quickSettingsStartRow);
                ConfigItem config = findConfig(entry.key);
                int resId = mContext.getResources().getIdentifier(entry.iconResName, "drawable", mContext.getPackageName());
                if (resId == 0) resId = R.drawable.msg_settings;
                
                String subtitle = entry.subtitle;
                if (config != null) {
                    if (config.type == ConfigItem.configTypeString || config.type == ConfigItem.configTypeInt) {
                        subtitle = config.String();
                    }
                }
                
                boolean isLast = position == quickSettingsEndRow - 1;
                cell.setData(entry.title, subtitle, resId, 0xFF2196F3, v -> onItemClick(cell, position, 0, 0), position == quickSettingsStartRow, isLast);
            } else if (viewType == 2) {
                CardSwitchCell cell = (CardSwitchCell) holder.itemView;
                if (position == hideContactsRow) {
                    cell.setData("Hide Contacts", "Hide contacts tab", R.drawable.msg_contacts, NaConfig.INSTANCE.getHideContacts().Bool(), isChecked -> {
                        NaConfig.INSTANCE.getHideContacts().setConfigBool(isChecked);
                        AppRestartHelper.triggerRebirth(mContext, new Intent(mContext, LaunchActivity.class));
                    }, true, false);
                } else if (position == ghostModeRow) {
                    cell.setData("Ghost Mode", "Read silently", R.drawable.msg_secret, NekoConfig.isGhostModeActive(), isChecked -> NekoConfig.setGhostMode(isChecked), false, false);
                } else if (position == musicGraphRow) {
                    cell.setData("Music Graph", "Visualizer in player", R.drawable.baseline_music_note_24, NaConfig.INSTANCE.getMusicGraph().Bool(), isChecked -> {
                        if (isChecked && android.os.Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            android.content.SharedPreferences prefs = org.telegram.messenger.MessagesController.getGlobalMainSettings();
                            if (prefs.getBoolean("asked_mic_music_graph", false) || !getParentActivity().shouldShowRequestPermissionRationale(android.Manifest.permission.RECORD_AUDIO)) {
                                tw.nekomimi.nekogram.utils.AlertUtil.showMicPermissionDialog(getParentActivity());
                            } else {
                                prefs.edit().putBoolean("asked_mic_music_graph", true).apply();
                                getParentActivity().requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 101);
                            }
                            cell.setChecked(false);
                            return;
                        }
                        NaConfig.INSTANCE.getMusicGraph().setConfigBool(isChecked);
                    }, false, false);
                } else if (position == saveDeletedRow) {
                    cell.setData("Save Deleted", "Save deleted messages", R.drawable.msg_delete_solar, NaConfig.INSTANCE.getEnableSaveDeletedMessages().Bool(), isChecked -> NaConfig.INSTANCE.getEnableSaveDeletedMessages().setConfigBool(isChecked), false, true);
                }
            } else if (viewType == 3) {
                CardItemCell cell = (CardItemCell) holder.itemView;
                if (position == hiddenChatsRow) {
                    cell.setData("Hidden Chats", "Private conversations", R.drawable.msg_secret, 0xFF2196F3, v -> {
                        tw.nekomimi.nekogram.helpers.HiddenChatsController controller = tw.nekomimi.nekogram.helpers.HiddenChatsController.getInstance();
                        if (controller.hasPasscode()) {
                            presentFragment(new tw.nekomimi.nekogram.ui.HiddenChatsPasscodeActivity(tw.nekomimi.nekogram.ui.HiddenChatsPasscodeActivity.MODE_UNLOCK_SETTINGS));
                        } else {
                            presentFragment(new tw.nekomimi.nekogram.ui.HiddenChatsPasscodeActivity(tw.nekomimi.nekogram.ui.HiddenChatsPasscodeActivity.MODE_SETUP_PASSCODE));
                        }
                    }, true, false);
                } else if (position == analyticsRow) {
                    cell.setData("Analytics & Control", "Usage stats, chat lock & focus mode", R.drawable.msg_stats_solar, 0xFF2196F3, v -> {
                        Intent intent = new Intent(getParentActivity(), xyz.nextalone.nagram.analytics.ui.AnalyticsDashboardActivity.class);
                        getParentActivity().startActivity(intent);
                    }, false, true);

                } else if (position == coreSettingsRow) {
                    List<CoreItem> items = new ArrayList<>();
                    items.add(new CoreItem("General", "Appearance, Language, Behavior", R.drawable.msg_settings, 0xFF2196F3, v -> presentFragment(new NekoGeneralSettingsActivity())));
                    items.add(new CoreItem(LocaleController.getString("Customization", R.string.Customization), "Icons, Styles, Theme & Decoration", R.drawable.msg_theme, 0xFFFF9C00, v -> presentFragment(new NekoCustomizationSettingsActivity())));
                    items.add(new CoreItem("Translator", "Messages, Languages, Engine", R.drawable.ic_translate, 0xFF9C27B0, v -> presentFragment(new NekoTranslatorSettingsActivity())));
                    items.add(new CoreItem("AI Assistance", "Alexgram assistant behavior & animations", R.drawable.settings_chat, 0xFF8E44AD, v -> presentFragment(new AIAssistanceSettingsActivity())));
                    items.add(new CoreItem("Chats", "UI, Privacy, Media", R.drawable.msg_discussion, 0xFF4CAF50, v -> presentFragment(new NekoChatSettingsActivity())));
                    items.add(new CoreItem("Plugins", "Manage Python Plugins", R.drawable.msg_fave, 0xFF009688, v -> presentFragment(new PluginsActivity())));
                    if (!PasscodeHelper.isSettingsHidden()) {
                        items.add(new CoreItem("Passcode", "Security & Fingerprint", R.drawable.msg_permissions, 0xFFF44336, v -> presentFragment(new NekoPasscodeSettingsActivity())));
                    }
                    cell.setMultiData(items.toArray(new CoreItem[0]));
                } else if (position == advancedSettingsRow) {
                    cell.setMultiData(new CoreItem[]{
                            new CoreItem("Cloud Settings", "Sync, backup, and restore", R.drawable.cloud_sync, 0xFF00BCD4, v -> CloudSettingsHelper.getInstance().showDialog(NekoSettingsActivity.this)),
                            new CoreItem("Experimental", "Beta Tools & Features", R.drawable.msg_fave, 0xFF673AB7, v -> presentFragment(new NekoExperimentalSettingsActivity()))
                    });
                }
            } else if (viewType == 4) {
                ((ActionsCell) holder.itemView).update();
            } else if (viewType == 5) {
                ((FooterCell) holder.itemView).update();
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow) return 0;
            if (position == quickSettingsHeaderRow || position == privacyHeaderRow || position == coreHeaderRow || position == advancedHeaderRow) return 1;
            if (position == hideContactsRow || position == ghostModeRow || position == musicGraphRow || position == saveDeletedRow) return 2;
            if (position == hiddenChatsRow || position == analyticsRow || position == coreSettingsRow || position == advancedSettingsRow) return 3;
            if (position >= quickSettingsStartRow && position < quickSettingsEndRow) {
                QuickSettingEntry entry = QuickSettingsController.getInstance().getQuickSettings().get(position - quickSettingsStartRow);
                return entry.type == QuickSettingEntry.TYPE_SWITCH ? 6 : 7;
            }
            if (position == actionsRow) return 4;
            if (position == footerRow) return 5;
            return -1;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int viewType = holder.getItemViewType();
            return viewType == 2 || viewType == 3 || viewType == 4 || viewType == 6 || viewType == 7;
        }
    }

    private class BrandingHeaderView extends FrameLayout {
        private final ImageView logoView;
        private final TextView nameView;
        private final TextView versionView;

        public BrandingHeaderView(Context context) {
            super(context);
            setPadding(0, dp(32), 0, dp(4));
            setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));

            FrameLayout logoContainer = new FrameLayout(context);
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                logoContainer.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, android.graphics.Outline outline) {
                        outline.setOval(0, 0, view.getWidth(), view.getHeight());
                    }
                });
                logoContainer.setClipToOutline(true);
            }
            GradientDrawable logoBg = new GradientDrawable();
            logoBg.setShape(GradientDrawable.OVAL);
            logoBg.setColor(Color.TRANSPARENT);
            logoContainer.setBackground(logoBg);
            addView(logoContainer, LayoutHelper.createFrame(60, 60, Gravity.CENTER_HORIZONTAL));

            logoView = new ImageView(context);
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                logoView.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, android.graphics.Outline outline) {
                        outline.setOval(0, 0, view.getWidth(), view.getHeight());
                    }
                });
                logoView.setClipToOutline(true);
            }
            logoView.setImageResource(isDark ? R.drawable.ic_launcher_alexgram_dark : R.drawable.ic_launcher_alexgram_blue);
            logoView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            logoContainer.addView(logoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            nameView = new TextView(context);
            nameView.setText("Alexgram");
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            nameView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            nameView.setTextColor(isDark ? Color.WHITE : 0xFF1A1A2E);
            nameView.setGravity(Gravity.CENTER);
            addView(nameView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 72, 0, 0));

            versionView = new TextView(context);
            versionView.setText("v" + BuildVars.BUILD_VERSION_STRING);
            versionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            versionView.setAlpha(0.6f);
            versionView.setTextColor(isDark ? Color.WHITE : 0xFF5C6B7F);
            versionView.setGravity(Gravity.CENTER);
            addView(versionView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 96, 0, 0));
        }
        public void update() {
            nameView.setTextColor(isDark ? Color.WHITE : 0xFF1A1A2E);
            versionView.setTextColor(isDark ? 0xAAFFFFFF : 0xAA5C6B7F);
            logoView.setImageResource(isDark ? R.drawable.ic_launcher_alexgram_dark : R.drawable.ic_launcher_alexgram_blue);
        }
    }

    private class SectionHeaderCell extends FrameLayout {
        private final TextView textView;
        public SectionHeaderCell(Context context) {
            super(context);
            setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            setPadding(dp(22), dp(16), dp(16), dp(8));
            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textView.setLetterSpacing(0.05f);
            textView.setTextColor(isDark ? 0x88FFFFFF : 0x885C6B7F);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        }
        public void setText(String text) { textView.setText(text); }
    }

    private class CardSwitchCell extends FrameLayout {
        private final LinearLayout container;
        private final ImageView iconView;
        private final TextView titleView;
        private final TextView subtitleView;
        private final Switch switchView;
        private final View divider;

        public CardSwitchCell(Context context) {
            super(context);
            setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            setPadding(dp(16), 0, dp(16), 0);
            container = new LinearLayout(context);
            container.setOrientation(LinearLayout.HORIZONTAL);
            container.setGravity(Gravity.CENTER_VERTICAL);
            container.setPadding(dp(14), dp(12), dp(14), dp(12));
            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER);
            container.addView(iconView, LayoutHelper.createLinear(32, 32));
            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(dp(12), 0, 0, 0);
            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            texts.addView(titleView);
            subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            texts.addView(subtitleView);
            container.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));
            switchView = new Switch(context);
            container.addView(switchView, LayoutHelper.createLinear(44, 30, Gravity.CENTER_VERTICAL, 0, 0, 0, 0));
            addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            divider = new View(context);
            addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.BOTTOM, 54, 0, 14, 0));
        }

        public void setChecked(boolean checked) {
            switchView.setChecked(checked, true);
        }

        public void setData(String title, String subtitle, int iconRes, boolean checked, OnSwitchListener listener, boolean first, boolean last) {
            titleView.setText(title);
            subtitleView.setText(subtitle);
            iconView.setImageResource(iconRes);
            switchView.setChecked(checked, false);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(cardBg);
            float r = dp(16);
            bg.setCornerRadii(new float[]{first ? r : 0, first ? r : 0, first ? r : 0, first ? r : 0, last ? r : 0, last ? r : 0, last ? r : 0, last ? r : 0});
            container.setBackground(bg);
            titleView.setTextColor(isDark ? Color.WHITE : 0xFF1A1A2E);
            subtitleView.setTextColor(isDark ? 0xAAFFFFFF : 0xAA5C6B7F);
            iconView.setColorFilter(new PorterDuffColorFilter(checked ? 0xFF2196F3 : (isDark ? 0xFFAAAAAA : 0xFF777777), PorterDuff.Mode.SRC_IN));
            
            if (iconRes == R.drawable.msg_delete_solar) {
                iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iconView.setPadding(dp(2), dp(2), dp(2), dp(2));
            } else {
                iconView.setScaleType(ImageView.ScaleType.CENTER);
                iconView.setPadding(0, 0, 0, 0);
            }
            
            divider.setBackgroundColor(dividerColor);
            divider.setVisibility(last ? GONE : VISIBLE);
            setOnClickListener(v -> {
                boolean target = !switchView.isChecked();
                switchView.setChecked(target, true);
                listener.onSwitch(target);
                iconView.setColorFilter(new PorterDuffColorFilter(target ? 0xFF2196F3 : (isDark ? 0xFFAAAAAA : 0xFF777777), PorterDuff.Mode.SRC_IN));
            });
            setOnLongClickListener(v -> {
                int position = -1;
                if (getParent() instanceof RecyclerListView) {
                    position = ((RecyclerListView) getParent()).getChildAdapterPosition(this);
                }
                return onItemLongClick(this, position, 0, 0);
            });
        }
    }

    private interface OnSwitchListener { void onSwitch(boolean isChecked); }

    private class CardItemCell extends LinearLayout {
        public CardItemCell(Context context) {
            super(context);
            setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            setOrientation(VERTICAL);
            setPadding(dp(16), 0, dp(16), 8);
        }
        public void setData(String title, String subtitle, int iconRes, int iconColor, View.OnClickListener listener, boolean first, boolean last) {
            removeAllViews();
            addView(createItem(getContext(), title, subtitle, iconRes, iconColor, listener, first, last), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        public void setMultiData(CoreItem[] items) {
            removeAllViews();
            for (int i = 0; i < items.length; i++) {
                CoreItem item = items[i];
                addView(createItem(getContext(), item.title, item.subtitle, item.iconRes, item.iconColor, item.listener, i == 0, i == items.length - 1), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                if (i < items.length - 1) {
                    View d = new View(getContext());
                    d.setBackgroundColor(dividerColor);
                    addView(d, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 56, 0, 16, 0));
                }
            }
        }
        private View createItem(Context ctx, String title, String subtitle, int iconRes, int iconColor, View.OnClickListener listener, boolean first, boolean last) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setClickable(true);
            row.setOnClickListener(listener);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(cardBg);
            float r = dp(16);
            bg.setCornerRadii(new float[]{first ? r : 0, first ? r : 0, first ? r : 0, first ? r : 0, last ? r : 0, last ? r : 0, last ? r : 0, last ? r : 0});
            row.setBackground(bg);
            ImageView iconView = new ImageView(ctx);
            iconView.setImageResource(iconRes);
            iconView.setColorFilter(Color.WHITE);
            iconView.setScaleType(ImageView.ScaleType.CENTER);
            GradientDrawable iconBg = new GradientDrawable();
            iconBg.setCornerRadius(dp(10));
            iconBg.setColor(iconColor);
            iconView.setBackground(iconBg);
            row.addView(iconView, LayoutHelper.createLinear(32, 32));
            LinearLayout texts = new LinearLayout(ctx);
            texts.setOrientation(VERTICAL);
            texts.setPadding(dp(12), 0, 0, 0);
            TextView titleView = new TextView(ctx);
            titleView.setText(title);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleView.setTextColor(isDark ? Color.WHITE : 0xFF1A1A2E);
            texts.addView(titleView);
            TextView subView = new TextView(ctx);
            subView.setText(subtitle);
            subView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            subView.setTextColor(isDark ? 0xAAFFFFFF : 0xAA5C6B7F);
            texts.addView(subView);
            row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));
            ImageView arrow = new ImageView(ctx);
            arrow.setImageResource(R.drawable.msg_arrowright);
            arrow.setColorFilter(new PorterDuffColorFilter(isDark ? 0x44FFFFFF : 0x22000000, PorterDuff.Mode.SRC_IN));
            row.addView(arrow, LayoutHelper.createLinear(20, 20));
            row.setOnLongClickListener(v -> {
                int position = -1;
                if (getParent() instanceof RecyclerListView) {
                    position = ((RecyclerListView) getParent()).getChildAdapterPosition(this);
                }
                return onItemLongClick(this, position, 0, 0);
            });
            return row;
        }
    }

    private static class CoreItem {
        String title, subtitle;
        int iconRes, iconColor;
        View.OnClickListener listener;
        CoreItem(String t, String s, int i, int c, View.OnClickListener l) { title = t; subtitle = s; iconRes = i; iconColor = c; listener = l; }
    }

    private class ActionsCell extends LinearLayout {
        public ActionsCell(Context context) {
            super(context);
            setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            setOrientation(HORIZONTAL);
            setPadding(dp(16), dp(8), dp(16), dp(8));
        }
        public void update() {
            removeAllViews();
            int color = isDark ? Color.WHITE : 0xFF1A1A2E;
            addView(createAction(getContext(), "Export", R.drawable.msg_share, color, v -> SettingsBackupHelper.backupSettings(getParentActivity(), resourcesProvider)), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));
            addView(new View(getContext()), LayoutHelper.createLinear(dp(8), 1));
            addView(createAction(getContext(), "Reset", R.drawable.msg_reset, color, v -> resetSettings()), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));
            addView(new View(getContext()), LayoutHelper.createLinear(dp(8), 1));
            addView(createAction(getContext(), "Restart", R.drawable.msg_retry, color, v -> AppRestartHelper.triggerRebirth(getParentActivity(), new Intent(getParentActivity(), LaunchActivity.class))), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));
            addView(new View(getContext()), LayoutHelper.createLinear(dp(8), 1));
            addView(createAction(getContext(), "About", R.drawable.msg_info, color, v -> presentFragment(new NekoAboutActivity())), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));
        }
        private View createAction(Context ctx, String text, int iconRes, int textColor, View.OnClickListener listener) {
            LinearLayout btn = new LinearLayout(ctx);
            btn.setOrientation(VERTICAL);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(0, dp(16), 0, dp(16));
            btn.setClickable(true);
            btn.setOnClickListener(listener);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(cardBg);
            bg.setCornerRadius(dp(20));
            bg.setStroke(dp(1), cardBorder);
            btn.setBackground(bg);
            ImageView icon = new ImageView(ctx);
            icon.setImageResource(iconRes);
            icon.setColorFilter(new PorterDuffColorFilter(0xFF2196F3, PorterDuff.Mode.SRC_IN));
            btn.addView(icon, LayoutHelper.createLinear(28, 28));
            TextView tv = new TextView(ctx);
            tv.setText(text);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            tv.setGravity(Gravity.CENTER);
            tv.setTextColor(textColor);
            btn.addView(tv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));
            return btn;
        }
    }

    private class FooterCell extends FrameLayout {
        private final TextView textView;
        public FooterCell(Context context) {
            super(context);
            setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            setPadding(0, dp(32), 0, dp(32));
            textView = new TextView(context);
            textView.setTextSize(11);
            textView.setGravity(Gravity.CENTER);
            textView.setTextColor(isDark ? 0x80FFFFFF : 0x805C6B7F);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        }
        public void update() { 
            textView.setText("Alexgram v" + BuildVars.BUILD_VERSION_STRING); 
            textView.setGravity(Gravity.CENTER);
        }
    }

    private class ClearRecentSearchCell extends FrameLayout {
        public ClearRecentSearchCell(Context context) {
            super(context);
            TextView textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTextColor(0xFFF44336);
            textView.setGravity(Gravity.CENTER);
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textView.setText("Clear All Recent Searches");
            textView.setPadding(0, dp(16), 0, dp(16));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            
            View divider = new View(context);
            divider.setBackgroundColor(isDark ? 0x10FFFFFF : 0x10000000);
            addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.TOP, 16, 0, 16, 0));
            
            setBackground(Theme.getSelectorDrawable(false));
        }
    }

    private class SearchAdapter extends RecyclerListView.SelectionAdapter {
        private final Context mContext;
        private final List<SettingsSearchManager.SearchItem> results = new ArrayList<>();
        private boolean isSearching = false;

        private static final int VIEW_TYPE_HEADER = 0;
        private static final int VIEW_TYPE_ITEM = 1;
        private static final int VIEW_TYPE_CLEAR = 2;

        public SearchAdapter(Context context) {
            mContext = context;
        }

        public void search(String query) {
            isSearching = !query.isEmpty();
            results.clear();
            if (isSearching) {
                results.addAll(SettingsSearchManager.getInstance().search(query));
            } else {
                for (String key : recentSearchKeys) {
                    SettingsSearchManager.SearchItem item = SettingsSearchManager.getInstance().getItem(key);
                    if (item != null) results.add(item);
                }
            }
            notifyDataSetChanged();
        }

        public void clear() {
            results.clear();
            isSearching = false;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            if (isSearching) return results.size();
            return results.isEmpty() ? 0 : results.size() + 2;
        }

        @Override
        public int getItemViewType(int position) {
            if (isSearching) return VIEW_TYPE_ITEM;
            if (position == 0) return VIEW_TYPE_HEADER;
            if (position == results.size() + 1) return VIEW_TYPE_CLEAR;
            return VIEW_TYPE_ITEM;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == VIEW_TYPE_ITEM || holder.getItemViewType() == VIEW_TYPE_CLEAR;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == VIEW_TYPE_HEADER) {
                view = new SectionHeaderCell(mContext);
            } else if (viewType == VIEW_TYPE_CLEAR) {
                view = new ClearRecentSearchCell(mContext);
            } else {
                view = new SettingsSearchResultCell(mContext);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == VIEW_TYPE_HEADER) {
                ((SectionHeaderCell) holder.itemView).setText("RECENT SEARCHES");
            } else if (holder.getItemViewType() == VIEW_TYPE_CLEAR) {
                holder.itemView.setOnClickListener(v -> {
                    recentSearchKeys.clear();
                    saveRecentSearches();
                    search("");
                });
            } else {
                SettingsSearchResultCell cell = (SettingsSearchResultCell) holder.itemView;
                int idx = isSearching ? position : position - 1;
                SettingsSearchManager.SearchItem item = results.get(idx);
                cell.setData(item, idx == results.size() - 1);
                
                if (!isSearching) {
                    cell.setCanDelete(() -> {
                        recentSearchKeys.remove(item.key);
                        saveRecentSearches();
                        search("");
                    });
                } else {
                    cell.setCanDelete(null);
                }
                
                cell.setOnClickListener(v -> {
                    addToRecentSearches(item.key);
                    try {
                        if (item.fragmentClass == NekoSettingsActivity.class) {
                            actionBar.closeSearchField();
                            scrollToRow(item.key, null);
                        } else {
                            BaseFragment fragment = item.fragmentClass.getConstructor().newInstance();
                            android.os.Bundle args = new android.os.Bundle();
                            args.putString("scrollToKey", item.key);
                            fragment.setArguments(args);
                            presentFragment(fragment);
                        }
                    } catch (Exception e) {
                        org.telegram.messenger.FileLog.e(e);
                    }
                });
            }
        }
    }

    private void resetSettings() {
        AlertUtil.showConfirm(getParentActivity(),
                getString(R.string.ResetSettingsAlert),
                R.drawable.msg_reset,
                getString(R.string.Reset),
                true,
                () -> {
                    ApplicationLoader.applicationContext.getSharedPreferences("nekocloud", Activity.MODE_PRIVATE).edit().clear().commit();
                    ApplicationLoader.applicationContext.getSharedPreferences("nekox_config", Activity.MODE_PRIVATE).edit().clear().commit();
                    NekoConfig.getPreferences().edit().clear().commit();
                    AppRestartHelper.triggerRebirth(getParentActivity(), new Intent(getParentActivity(), LaunchActivity.class));
                });
    }
}
// [Alexgram: A-Settings UI] - End
