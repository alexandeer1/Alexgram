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

import com.radolyn.ayugram.messages.AyuSavePreferences;
import com.radolyn.ayugram.utils.AyuGhostPreferences;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.AppRestartHelper;
import tw.nekomimi.nekogram.ui.AlexgramSplashView;
import tw.nekomimi.nekogram.utils.AlertUtil;
import xyz.nextalone.nagram.NaConfig;

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

        advCard.addView(createSettingItem(context, "Backup & Import", "Save or Restore Settings", R.drawable.msg_cloud, 0xFF0288D1, v -> {
            // Handle backup action
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
        
        FrameLayout extFrame = (FrameLayout) ((LinearLayout) expView).getChildAt(1);
        extFrame.addView(newBadge, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 40, 0));
        
        advCard.addView(expView);
        mainContent.addView(advCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 24));


        // 4. QUICK SETTINGS (New section)
        addSectionTitle(mainContent, context, "QUICK SETTINGS");
        LinearLayout qsCard = createCard(context);

        qsCard.addView(createSwitchItem(context, "Hide Contacts", "Hide contacts list", R.drawable.msg_contact, 0xFF00796B, 
                NaConfig.INSTANCE.getHideContacts().Bool(), isChecked -> {
                    NaConfig.INSTANCE.getHideContacts().setConfigBool(isChecked);
                }));
        qsCard.addView(createDivider(context));

        qsCard.addView(createSwitchItem(context, "Ghost Mode", "Read silently", R.drawable.msg_secret, 0xFF455A64, 
                NekoConfig.isGhostModeActive(), isChecked -> {
                    NekoConfig.setGhostMode(isChecked);
                }));
        qsCard.addView(createDivider(context));

        qsCard.addView(createSwitchItem(context, "Music Graph", "Visualizer in player", R.drawable.msg_music, 0xFFD32F2F, 
                NaConfig.INSTANCE.getMusicGraph().Bool(), isChecked -> {
                    NaConfig.INSTANCE.getMusicGraph().setConfigBool(isChecked);
                }));
        qsCard.addView(createDivider(context));

        qsCard.addView(createSwitchItem(context, "Save Deleted", "Save deleted messages", R.drawable.msg_delete, 0xFFC2185B, 
                NaConfig.INSTANCE.getEnableSaveDeletedMessages().Bool(), isChecked -> {
                    NaConfig.INSTANCE.getEnableSaveDeletedMessages().setConfigBool(isChecked);
                }));

        mainContent.addView(qsCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 24));


        // 5. OTHERS (4 horizontal buttons)
        addSectionTitle(mainContent, context, "OTHERS");
        LinearLayout othersRow = new LinearLayout(context);
        othersRow.setOrientation(LinearLayout.HORIZONTAL);

        othersRow.addView(createBigButton(context, "Export", R.drawable.msg_shareout, v -> {
            
        }), LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));
        othersRow.addView(createBigButton(context, "Reset", R.drawable.msg_reset, v -> {
            
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
        arrow.setImageResource(R.drawable.arrow_right);
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

}
