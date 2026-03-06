package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.BulletinFactory;
import android.text.InputType;
import tw.nekomimi.nekogram.helpers.HiddenChatsController;
import tw.nekomimi.nekogram.ui.HiddenChatsActivity;

public class HiddenChatsSettingsActivity extends BaseFragment {

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Hidden Chats Settings");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ScrollView scrollView = new ScrollView(context);
        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(contentLayout);
        fragmentView = scrollView;

        contentLayout.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));

        // Options
        contentLayout.addView(createSettingItem(context, "Change Passcode", "Update your 4-digit PIN", R.drawable.msg_permissions_solar, 0xFFE91E63, v -> {
             // For now, re-use setup dialog from NekoSettingsActivity via static method or duplication
             // Or better, just clear passcode and ask to set new one
             showChangePasscodeDialog(context);
        }));

        contentLayout.addView(createSettingItem(context, "Open Hidden Chats", "Access your hidden chats now", R.drawable.msg_folders_private_solar, 0xFFE91E63, v -> {
             presentFragment(new HiddenChatsActivity(null));
        }));

        // Reset
        contentLayout.addView(createSettingItem(context, "Reset Hidden Chats", "Clear all hidden chats and reset passcode", R.drawable.msg_delete, 0xFFE91E63, v -> {
             AlertDialog.Builder builder = new AlertDialog.Builder(context);
             builder.setTitle("Reset Hidden Chats");
             builder.setMessage("This will unhide all chats and remove the passcode. Are you sure?");
             builder.setPositiveButton("Reset", (d, w) -> {
                 HiddenChatsController.getInstance().reset();
                 finishFragment();
             });
             builder.setNegativeButton("Cancel", null);
             builder.show();
        }));

        // How to Use
        contentLayout.addView(createSettingItem(context, "How to Use", "Learn how to manage hidden chats", R.drawable.msg_info, 0xFFE91E63, v -> {
             AlertDialog.Builder builder = new AlertDialog.Builder(context);
             builder.setTitle("How to Use Hidden Chats");
             builder.setMessage("• Hide Chats:\nLong-press any chat in the list and select 'Hide', or use the Plus icon in the Hidden Chats screen to add multiple chats at once.\n\n• Access Hidden Chats:\nLong-press the 'Search' or 'New Message' button on the main screen, or open them directly from these settings.\n\n• Privacy:\nChats added to Hidden Chats are automatically muted. You can manually unmute them if you prefer.\n\n• Passcode:\nYour hidden chats are protected by a 4-digit passcode.");
             builder.setPositiveButton("Got It", null);
             builder.show();
        }));

        return fragmentView;
    }

    private View createSettingItem(Context context, String title, String subtitle, int iconRes, int iconColor, View.OnClickListener onClick) {
        // Simple implementation mimicking NekoSettingsActivity style
        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setGravity(Gravity.CENTER_VERTICAL);
        linearLayout.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(10), AndroidUtilities.dp(20), AndroidUtilities.dp(10));
        linearLayout.setBackground(Theme.getSelectorDrawable(false));
        linearLayout.setOnClickListener(onClick);

        ImageView imageView = new ImageView(context);
        imageView.setImageResource(iconRes);
        imageView.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
        
        // Background for icon
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(Color.argb(30, Color.red(iconColor), Color.green(iconColor), Color.blue(iconColor)));
        imageView.setBackground(shape);
        imageView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));

        LinearLayout textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        textLayout.setGravity(Gravity.CENTER_VERTICAL);
        
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        TextView subtitleView = new TextView(context);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(13);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));

        textLayout.addView(titleView);
        textLayout.addView(subtitleView);

        linearLayout.addView(imageView, LayoutHelper.createLinear(40, 40, 0, 0, 16, 0));
        linearLayout.addView(textLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        frameLayout.addView(linearLayout);
        return frameLayout;
    }
    
    private void showChangePasscodeDialog(Context context) {
        // Reuse setup logic logic, potentially duplicate code due to time constraints (user waiting)
        // Ideally should be shared.
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Set New Passcode");
        
        final org.telegram.ui.Components.EditTextBoldCursor editText = new org.telegram.ui.Components.EditTextBoldCursor(context);
        editText.setTextSize(18);
        editText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        editText.setFilters(new android.text.InputFilter[] { new android.text.InputFilter.LengthFilter(4) });
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setGravity(Gravity.CENTER);
        
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(12), AndroidUtilities.dp(24), 0);
        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        builder.setView(container);

        builder.setPositiveButton("Set", null);
        builder.setNegativeButton("Cancel", null);
        
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(di -> {
            View b = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
            b.setOnClickListener(view -> {
                String code = editText.getText().toString();
                if (code.length() == 4) {
                    HiddenChatsController.getInstance().setPasscode(code);
                    AndroidUtilities.runOnUIThread(() -> {
                         org.telegram.ui.Components.BulletinFactory.of(HiddenChatsSettingsActivity.this).createSimpleBulletin(R.raw.done, "Passcode Updated").show();
                    });
                    dialog.dismiss();
                } else {
                    AndroidUtilities.shakeView(editText);
                }
            });
        });
        dialog.show();
    }
}