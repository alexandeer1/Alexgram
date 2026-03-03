package tw.nekomimi.nekogram.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.net.Uri;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.radolyn.ayugram.messages.AyuSavePreferences;
import com.radolyn.ayugram.utils.AyuGhostPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import org.telegram.messenger.SendMessagesHelper;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.BasePermissionsActivity;
import org.telegram.ui.DocumentSelectActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import kotlin.text.StringsKt;

import tw.nekomimi.nekogram.DialogConfig;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.AppRestartHelper;
import tw.nekomimi.nekogram.helpers.LocalNameHelper;
import tw.nekomimi.nekogram.ui.AlexgramSplashView;
import tw.nekomimi.nekogram.utils.AlertUtil;
import tw.nekomimi.nekogram.utils.FileUtil;
import tw.nekomimi.nekogram.utils.GsonUtil;
import tw.nekomimi.nekogram.utils.ShareUtil;
import xyz.nextalone.nagram.NaConfig;
import xyz.nextalone.nagram.helper.BookmarksHelper;
import xyz.nextalone.nagram.helper.LocalPeerColorHelper;
import xyz.nextalone.nagram.helper.LocalPremiumStatusHelper;

public class NekoSettingsActivity extends BaseFragment {

    private LinearLayout contentLayout;
    private final int BG_DARK = 0xFF00050B;
    private final int CARD_BG = 0xFF0D1421;
    private final int TEXT_TITLE = 0xFFFFFFFF;
    private final int TEXT_SUB = 0xFF8899AA;
    
    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("A-Settings");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        // Search icon
        actionBar.createMenu().addItem(1, R.drawable.ic_ab_search);

        // Make actionbar transparent to blend with header
        actionBar.setBackgroundColor(Color.TRANSPARENT);
        actionBar.setItemsColor(Color.WHITE, false);
        actionBar.setTitleColor(Color.WHITE);

        FrameLayout parentFrame = new FrameLayout(context);
        parentFrame.setBackgroundColor(BG_DARK);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        scrollView.setLayoutParams(layoutParams);

        contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(contentLayout, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        
        // 1. Live animated header
        AlexgramSettingsHeaderView headerView = new AlexgramSettingsHeaderView(context);
        contentLayout.addView(headerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 260));

        // Let's add padding from sides
        LinearLayout mainContent = new LinearLayout(context);
        mainContent.setOrientation(LinearLayout.VERTICAL);
        mainContent.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(32));
        contentLayout.addView(mainContent, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // 2. CORE SETTINGS Group
        addSectionTitle(mainContent, context, "CORE SETTINGS");

        LinearLayout coreCard = createCard(context);
        
        coreCard.addView(createSettingItem(context, "General", "Appearance, Language, Behavior", R.drawable.msg_theme, 0xFF1976D2, v -> {
            presentFragment(new NekoGeneralSettingsActivity());
        }));
        coreCard.addView(createDivider(context));
        
        coreCard.addView(createSettingItem(context, "Translator", "Messages, Languages, Engine", R.drawable.ic_translate, 0xFF512DA8, v -> {
            presentFragment(new NekoTranslatorSettingsActivity());
        }));
        coreCard.addView(createDivider(context));

        // Chat settings
        coreCard.addView(createSettingItem(context, "Chats", "UI, Privacy, Media", R.drawable.msg_discussion, 0xFF388E3C, v -> {
            presentFragment(new NekoChatSettingsActivity());
        }));
        coreCard.addView(createDivider(context));

        // Passcode settings
        coreCard.addView(createSettingItem(context, "Passcode", "Security & Fingerprint", R.drawable.msg_permissions, 0xFFC2185B, v -> {
            presentFragment(new NekoPasscodeSettingsActivity());
        }));

        mainContent.addView(coreCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 24));


        // 3. ADVANCED Group
        addSectionTitle(mainContent, context, "ADVANCED");
        LinearLayout advCard = createCard(context);

        advCard.addView(createSettingItem(context, "Customization", "Themes, Font, Home UI", R.drawable.msg_edit, 0xFFE64A19, v -> {
            // NekoGeneralSettings or sub-theme? Just point to General for now or NekoGeneralSettingsActivity.
            presentFragment(new NekoGeneralSettingsActivity()); 
        }));
        advCard.addView(createDivider(context));

        advCard.addView(createSettingItem(context, "Backup & Import", "Save or Restore Settings", R.drawable.msg_photo_settings_solar, 0xFF0288D1, v -> {
            if (Build.VERSION.SDK_INT >= 33) {
                openFilePicker();
            } else {
                DocumentSelectActivity activity = getDocumentSelectActivity(getParentActivity());
                if (activity != null) {
                    presentFragment(activity);
                }
            }
        }));
        advCard.addView(createDivider(context));

        // Experimental
        View expView = createSettingItem(context, "Experimental", "Beta Tools & Features", R.drawable.msg_fave, 0xFF512DA8, v -> {
            presentFragment(new NekoExperimentalSettingsActivity());
        });
        // Add new badge
        TextView newBadge = new TextView(context);
        newBadge.setText("New");
        newBadge.setTextColor(0xFFEF5350);
        newBadge.setTextSize(12);
        newBadge.setBackgroundColor(0x33EF5350);
        newBadge.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(2), AndroidUtilities.dp(6), AndroidUtilities.dp(2));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(0x33EF5350);
        badgeBg.setCornerRadius(AndroidUtilities.dp(6));
        newBadge.setBackground(badgeBg);
        
        try {
            LinearLayout expLayout = (LinearLayout) expView;
            LinearLayout textsLayout = (LinearLayout) expLayout.getChildAt(1);
            textsLayout.addView(newBadge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
        } catch (Exception e) {
            // Ignore if layout structure changes
        }
        
        advCard.addView(expView);
        mainContent.addView(advCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 24));


        // 4. QUICK SETTINGS (New section)
        addSectionTitle(mainContent, context, "QUICK SETTINGS");
        LinearLayout qsCard = createCard(context);

        qsCard.addView(createSwitchItem(context, "Hide Contacts", "Hide contacts list", R.drawable.msg_contact, 0xFF00796B, 
                NaConfig.INSTANCE.getHideContacts().Bool(), (buttonView, isChecked) -> {
                    NaConfig.INSTANCE.getHideContacts().setConfigBool(isChecked);
                }));
        qsCard.addView(createDivider(context));

        qsCard.addView(createSwitchItem(context, "Ghost Mode", "Read silently", R.drawable.msg_secret, 0xFF455A64, 
                NekoConfig.isGhostModeActive(), (buttonView, isChecked) -> {
                    NekoConfig.setGhostMode(isChecked);
                }));
        qsCard.addView(createDivider(context));

        qsCard.addView(createSwitchItem(context, "Music Graph", "Visualizer in player", R.drawable.msg_filled_data_music_solar, 0xFFD32F2F, 
                NaConfig.INSTANCE.getMusicGraph().Bool(), (buttonView, isChecked) -> {
                    NaConfig.INSTANCE.getMusicGraph().setConfigBool(isChecked);
                }));
        qsCard.addView(createDivider(context));

        qsCard.addView(createSwitchItem(context, "Save Deleted", "Save deleted messages", R.drawable.msg_delete, 0xFFC2185B, 
                NaConfig.INSTANCE.getEnableSaveDeletedMessages().Bool(), (buttonView, isChecked) -> {
                    NaConfig.INSTANCE.getEnableSaveDeletedMessages().setConfigBool(isChecked);
                }));

        mainContent.addView(qsCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 24));


        // 5. OTHERS (4 horizontal buttons)
        addSectionTitle(mainContent, context, "OTHERS");
        LinearLayout othersRow = new LinearLayout(context);
        othersRow.setOrientation(LinearLayout.HORIZONTAL);

        othersRow.addView(createBigButton(context, "Export", R.drawable.msg_shareout, v -> {
            backupSettings();
        }), LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));
        othersRow.addView(createBigButton(context, "Reset", R.drawable.msg_reset, v -> {
            AlertUtil.showConfirm(getParentActivity(),
                    LocaleController.getString(R.string.ResetSettingsAlert),
                    R.drawable.msg_reset,
                    LocaleController.getString(R.string.Reset),
                    true,
                    () -> {
                        ApplicationLoader.applicationContext.getSharedPreferences("nekocloud", Activity.MODE_PRIVATE).edit().clear().commit();
                        ApplicationLoader.applicationContext.getSharedPreferences("nekox_config", Activity.MODE_PRIVATE).edit().clear().commit();
                        NekoConfig.getPreferences().edit().clear().commit();
                        AppRestartHelper.triggerRebirth(getParentActivity(), new Intent(getParentActivity(), LaunchActivity.class));
                    });
        }), LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));
        othersRow.addView(createBigButton(context, "Restart", R.drawable.msg_retry, v -> {
            AppRestartHelper.triggerRebirth(getParentActivity(), new Intent(getParentActivity(), LaunchActivity.class));
        }), LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));
        othersRow.addView(createBigButton(context, "About", R.drawable.msg_info, v -> {
            presentFragment(new NekoAboutActivity());
        }), LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));

        mainContent.addView(othersRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 80, 0, 0, 0, 32));

        // 6. Version Text
        TextView versionText = new TextView(context);
        versionText.setText("Alexgram v1.7.3");
        versionText.setTextSize(14);
        versionText.setTextColor(0xFF556677);
        versionText.setGravity(Gravity.CENTER);
        mainContent.addView(versionText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 0, 16));


        parentFrame.addView(scrollView);
        
        fragmentView = parentFrame;
        return fragmentView;
    }

    private void addSectionTitle(LinearLayout parent, Context context, String title) {
        TextView textView = new TextView(context);
        textView.setText(title);
        textView.setTextSize(12);
        textView.setTextColor(TEXT_SUB);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setLetterSpacing(0.05f);
        parent.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 0, 0, 8));
    }

    private LinearLayout createCard(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD_BG);
        bg.setCornerRadius(AndroidUtilities.dp(20));
        bg.setStroke(AndroidUtilities.dp(1), 0x33FFFFFF);
        layout.setBackground(bg);
        return layout;
    }

    private View createDivider(Context context) {
        View v = new View(context);
        v.setBackgroundColor(0x1AFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = AndroidUtilities.dp(64);
        v.setLayoutParams(lp);
        return v;
    }

    private View createSettingItem(Context context, String title, String subtitle, int iconRes, int iconColor, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        row.setClickable(true);
        row.setBackground(Theme.getSelectorDrawable(false));
        row.setOnClickListener(onClick);

        // Icon
        ImageView iconView = new ImageView(context);
        iconView.setImageResource(iconRes);
        iconView.setColorFilter(Color.WHITE);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.RECTANGLE);
        iconBg.setCornerRadius(AndroidUtilities.dp(12));
        iconBg.setColor(iconColor);
        iconView.setBackground(iconBg);

        row.addView(iconView, LayoutHelper.createLinear(36, 36));

        // Texts
        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(TEXT_TITLE);
        titleView.setTextSize(16);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        
        TextView subView = new TextView(context);
        subView.setText(subtitle);
        subView.setTextColor(TEXT_SUB);
        subView.setTextSize(13);

        texts.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        texts.addView(subView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 16, 0, 0, 0));

        // Right arrow
        FrameLayout rightContainer = new FrameLayout(context);
        ImageView arrow = new ImageView(context);
        arrow.setImageResource(R.drawable.arrow_more_solar);
        arrow.setColorFilter(TEXT_SUB);
        rightContainer.addView(arrow, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | Gravity.RIGHT));

        row.addView(rightContainer, LayoutHelper.createLinear(40, LayoutHelper.MATCH_PARENT));

        return row;
    }

    private View createSwitchItem(Context context, String title, String subtitle, int iconRes, int iconColor, boolean checked, android.widget.CompoundButton.OnCheckedChangeListener onChange) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        row.setBackground(Theme.getSelectorDrawable(false));

        // Icon
        ImageView iconView = new ImageView(context);
        iconView.setImageResource(iconRes);
        iconView.setColorFilter(Color.WHITE);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.RECTANGLE);
        iconBg.setCornerRadius(AndroidUtilities.dp(12));
        iconBg.setColor(iconColor);
        iconView.setBackground(iconBg);

        row.addView(iconView, LayoutHelper.createLinear(36, 36));

        // Texts
        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(TEXT_TITLE);
        titleView.setTextSize(16);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        
        TextView subView = new TextView(context);
        subView.setText(subtitle);
        subView.setTextColor(TEXT_SUB);
        subView.setTextSize(13);

        texts.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        texts.addView(subView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 16, 0, 0, 0));

        // Switch
        org.telegram.ui.Components.Switch sw = new org.telegram.ui.Components.Switch(context);
        sw.setChecked(checked, false);
        sw.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);

        row.addView(sw, LayoutHelper.createLinear(44, 24));
        
        row.setOnClickListener(v -> {
            boolean isChecked = !sw.isChecked();
            sw.setChecked(isChecked, true);
            onChange.onCheckedChanged(null, isChecked);
        });

        return row;
    }

    private View createBigButton(Context context, String text, int iconRes, View.OnClickListener onClick) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD_BG);
        bg.setCornerRadius(AndroidUtilities.dp(16));
        bg.setStroke(AndroidUtilities.dp(1), 0x33FFFFFF);
        box.setBackground(bg);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LayoutHelper.MATCH_PARENT, 1f);
        lp.rightMargin = AndroidUtilities.dp(8);
        box.setLayoutParams(lp);

        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        if (text.equals("Reset")) {
            icon.setColorFilter(0xFFEF5350);
        } else if (text.equals("Restart")) {
            icon.setColorFilter(0xFF42A5F5);
        } else {
            icon.setColorFilter(TEXT_SUB);
        }

        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(TEXT_SUB);
        tv.setTextSize(12);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, AndroidUtilities.dp(6), 0, 0);

        box.addView(icon, LayoutHelper.createLinear(24, 24));
        box.addView(tv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        box.setClickable(true);
        box.setOnClickListener(onClick);

        return box;
    }

    private void backupSettings() {
        Context context = getParentActivity();
        if (context == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.BackupSettings));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        org.telegram.ui.Cells.CheckBoxCell checkBoxCell = new org.telegram.ui.Cells.CheckBoxCell(context, org.telegram.ui.Cells.CheckBoxCell.TYPE_CHECK_BOX_DEFAULT, resourceProvider);
        checkBoxCell.setBackground(Theme.getSelectorDrawable(false));
        checkBoxCell.setText(LocaleController.getString(R.string.ExportSettingsIncludeApiKeys), "", true, false);
        checkBoxCell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
        checkBoxCell.setChecked(true, false);
        checkBoxCell.setOnClickListener(v -> {
            org.telegram.ui.Cells.CheckBoxCell cell = (org.telegram.ui.Cells.CheckBoxCell) v;
            cell.setChecked(!cell.isChecked(), true);
        });
        linearLayout.addView(checkBoxCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString(R.string.ExportTheme), (dialog, which) -> {
            boolean includeApiKeys = checkBoxCell.isChecked();
            try {
                File cacheFile = new File(AndroidUtilities.getCacheDir(), new Date().toLocaleString() + ".nekox-settings.json");
                FileUtil.writeUtf8String(backupSettingsJson(false, 4, includeApiKeys), cacheFile);
                ShareUtil.shareFile(getParentActivity(), cacheFile);
            } catch (JSONException e) {
                AlertUtil.showSimpleAlert(getParentActivity(), e);
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }

    public static String backupSettingsJson(boolean isCloud, int indentSpaces) throws JSONException {
        return backupSettingsJson(isCloud, indentSpaces, true);
    }

    public static String backupSettingsJson(boolean isCloud, int indentSpaces, boolean includeApiKeys) throws JSONException {
        JSONObject configJson = new JSONObject();

        ArrayList<String> userconfig = new ArrayList<>();
        userconfig.add("saveIncomingPhotos");
        userconfig.add("passcodeHash");
        userconfig.add("passcodeType");
        userconfig.add("passcodeHash");
        userconfig.add("autoLockIn");
        userconfig.add("useFingerprint");
        spToJSON("userconfing", configJson, userconfig::contains, isCloud);

        ArrayList<String> mainconfig = new ArrayList<>();
        mainconfig.add("saveToGallery");
        mainconfig.add("autoplayGifs");
        mainconfig.add("autoplayVideo");
        mainconfig.add("mapPreviewType");
        mainconfig.add("raiseToSpeak");
        mainconfig.add("customTabs");
        mainconfig.add("directShare");
        mainconfig.add("shuffleMusic");
        mainconfig.add("playOrderReversed");
        mainconfig.add("inappCamera");
        mainconfig.add("repeatMode");
        mainconfig.add("fontSize");
        mainconfig.add("bubbleRadius");
        mainconfig.add("ivFontSize");
        mainconfig.add("allowBigEmoji");
        mainconfig.add("streamMedia");
        mainconfig.add("saveStreamMedia");
        mainconfig.add("smoothKeyboard");
        mainconfig.add("pauseMusicOnRecord");
        mainconfig.add("streamAllVideo");
        mainconfig.add("streamMkv");
        mainconfig.add("suggestStickers");
        mainconfig.add("sortContactsByName");
        mainconfig.add("sortFilesByName");
        mainconfig.add("noSoundHintShowed");
        mainconfig.add("directShareHash");
        mainconfig.add("useThreeLinesLayout");
        mainconfig.add("archiveHidden");
        mainconfig.add("distanceSystemType");
        mainconfig.add("loopStickers");
        mainconfig.add("keepMedia");
        mainconfig.add("noStatusBar");
        mainconfig.add("lastKeepMediaCheckTime");
        mainconfig.add("searchMessagesAsListHintShows");
        mainconfig.add("searchMessagesAsListUsed");
        mainconfig.add("stickersReorderingHintUsed");
        mainconfig.add("textSelectionHintShows");
        mainconfig.add("scheduledOrNoSoundHintShows");
        mainconfig.add("lockRecordAudioVideoHint");
        mainconfig.add("disableVoiceAudioEffects");
        mainconfig.add("chatSwipeAction");

        if (!isCloud) mainconfig.add("theme");
        mainconfig.add("selectedAutoNightType");
        mainconfig.add("autoNightScheduleByLocation");
        mainconfig.add("autoNightBrighnessThreshold");
        mainconfig.add("autoNightDayStartTime");
        mainconfig.add("autoNightDayEndTime");
        mainconfig.add("autoNightSunriseTime");
        mainconfig.add("autoNightCityName");
        mainconfig.add("autoNightSunsetTime");
        mainconfig.add("autoNightLocationLatitude3");
        mainconfig.add("autoNightLocationLongitude3");
        mainconfig.add("autoNightLastSunCheckDay");
        mainconfig.add("lang_code");
        mainconfig.add("web_restricted_domains2");

        spToJSON("mainconfig", configJson, mainconfig::contains);
        if (!isCloud) spToJSON("themeconfig", configJson, null);
        spToJSON("nkmrcfg", configJson, null, includeApiKeys);

        return configJson.toString(indentSpaces);
    }

    private static void spToJSON(String sp, JSONObject object, Function<String, Boolean> filter) throws JSONException {
        spToJSON(sp, object, filter, true);
    }

    private static void spToJSON(String sp, JSONObject object, Function<String, Boolean> filter, boolean includeApiKeys) throws JSONException {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(sp, Activity.MODE_PRIVATE);
        JSONObject jsonConfig = new JSONObject();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            String key = entry.getKey();
            if (!includeApiKeys && (key.endsWith("Key") || key.contains("Token") || key.contains("AccountID"))) {
                continue;
            }
            if (filter != null && !filter.apply(key)) {
                continue;
            }
            if (entry.getValue() instanceof Long) {
                key = key + "_long";
            } else if (entry.getValue() instanceof Float) {
                key = key + "_float";
            }
            jsonConfig.put(key, entry.getValue());
        }
        object.put(sp, jsonConfig);
    }

    private DocumentSelectActivity getDocumentSelectActivity(Activity parent) {
        try {
            if (parent.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                parent.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE);
                return null;
            }
        } catch (Throwable ignore) {
        }
        DocumentSelectActivity fragment = new DocumentSelectActivity(false);
        fragment.setMaxSelectedFiles(1);
        fragment.setAllowPhoto(false);
        fragment.setDelegate(new DocumentSelectActivity.DocumentSelectActivityDelegate() {
            @Override
            public void didSelectFiles(DocumentSelectActivity activity, ArrayList<String> files, String caption, boolean notify, int scheduleDate) {
                activity.finishFragment();
                importSettings(parent, new File(files.get(0)));
            }

            @Override
            public void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos, boolean notify, int scheduleDate) {
            }

            @Override
            public void startDocumentSelectActivity() {
            }
        });
        return fragment;
    }

    public static void importSettings(Context context, File settingsFile) {
        AlertUtil.showConfirm(context,
                LocaleController.getString(R.string.ImportSettingsAlert),
                R.drawable.msg_photo_settings_solar,
                LocaleController.getString(R.string.Import),
                true,
                () -> importSettingsConfirmed(context, settingsFile));
    }

    public static void importSettingsConfirmed(Context context, File settingsFile) {
        try {
            JsonObject configJson = GsonUtil.toJsonObject(FileUtil.readUtf8String(settingsFile));
            importSettings(configJson);

            AlertDialog restart = new AlertDialog(context, 0);
            restart.setTitle(LocaleController.getString(R.string.NagramX));
            restart.setMessage(LocaleController.getString(R.string.RestartAppToTakeEffect));
            restart.setPositiveButton(LocaleController.getString(R.string.OK), (__, ___) -> AppRestartHelper.triggerRebirth(context, new Intent(context, LaunchActivity.class)));
            restart.show();
        } catch (Exception e) {
            AlertUtil.showSimpleAlert(context, e);
        }
    }

    @SuppressLint("ApplySharedPref")
    public static void importSettings(JsonObject configJson) throws JSONException {
        Set<String> allowedKeys = new HashSet<>();
        try {
            allowedKeys.addAll(NekoConfig.getAllKeys());
            allowedKeys.addAll(NaConfig.INSTANCE.getAllKeys());
        } catch (Throwable ignore) {
        }
        String[] preservePrefixes = {
                AyuGhostPreferences.ghostReadExclusionPrefix,
                AyuGhostPreferences.ghostTypingExclusionPrefix,
                AyuSavePreferences.saveExclusionPrefix,
                LocalNameHelper.chatNameOverridePrefix,
                LocalNameHelper.userNameOverridePrefix,
                DialogConfig.customForumTabPrefix,
                LocalPeerColorHelper.KEY_PREFIX,
                LocalPremiumStatusHelper.KEY_PREFIX,
                BookmarksHelper.KEY_PREFIX
        };

        for (Map.Entry<String, JsonElement> element : configJson.entrySet()) {
            String spName = element.getKey();
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(spName, Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            for (Map.Entry<String, JsonElement> config : ((JsonObject) element.getValue()).entrySet()) {
                String key = config.getKey();
                JsonPrimitive value = (JsonPrimitive) config.getValue();
                if ("nkmrcfg".equals(spName)) {
                    boolean shouldSkip = true;
                    for (String prefix : preservePrefixes) {
                        if (key.startsWith(prefix)) {
                            shouldSkip = false;
                            break;
                        }
                    }
                    if (shouldSkip) {
                        String actualKey = key;
                        if (key.endsWith("_long")) {
                            actualKey = StringsKt.substringBeforeLast(key, "_long", key);
                        } else if (key.endsWith("_float")) {
                            actualKey = StringsKt.substringBeforeLast(key, "_float", key);
                        }
                        shouldSkip = !allowedKeys.contains(actualKey);
                    }
                    if (shouldSkip) {
                        continue;
                    }
                }
                if (value.isBoolean()) {
                    editor.putBoolean(key, value.getAsBoolean());
                } else if (value.isNumber()) {
                    boolean isLong = false;
                    boolean isFloat = false;
                    if (key.endsWith("_long")) {
                        key = StringsKt.substringBeforeLast(key, "_long", key);
                        isLong = true;
                    } else if (key.endsWith("_float")) {
                        key = StringsKt.substringBeforeLast(key, "_float", key);
                        isFloat = true;
                    }
                    if (isLong) {
                        editor.putLong(key, value.getAsLong());
                    } else if (isFloat) {
                        editor.putFloat(key, value.getAsFloat());
                    } else {
                        editor.putInt(key, value.getAsInt());
                    }
                } else {
                    editor.putString(key, value.getAsString());
                }
            }
            editor.commit();
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(intent, 21);
        } catch (android.content.ActivityNotFoundException ex) {
            AlertUtil.showSimpleAlert(getParentActivity(), ex);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == 21 && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                File cacheDir = AndroidUtilities.getCacheDir();
                String tempFile = UUID.randomUUID().toString().replace("-", "") + ".nekox-settings.json";
                File file = new File(cacheDir.getPath(), tempFile);
                try {
                    final InputStream inputStream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
                    if (inputStream != null) {
                        OutputStream outputStream = new FileOutputStream(file);
                        final byte[] buffer = new byte[4 * 1024];
                        int read;
                        while ((read = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, read);
                        }
                        inputStream.close();
                        outputStream.flush();
                        outputStream.close();
                        importSettings(getParentActivity(), file);
                    }
                } catch (Exception ignore) {
                }
            }
            super.onActivityResultFragment(requestCode, resultCode, data);
        }
    }
}
