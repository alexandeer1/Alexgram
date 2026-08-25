package tw.nekomimi.nekogram.ui.cells;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import tw.nekomimi.nekogram.settings.BaseNekoXSettingsActivity;

public class MessageMenuConfigCell extends FrameLayout {

    private final String key;
    private final boolean defaultVal;
    private final Runnable onChange;
    private final TextView btnHide;
    private final TextView btnText;
    private final TextView btnIcon;
    private int currentMode; // 0 = Hide, 1 = Text, 2 = Icon

    public MessageMenuConfigCell(Context context, String key, String title, int iconRes, boolean defaultVal, boolean supportIcon, Runnable onChange) {
        super(context);
        this.key = key;
        this.defaultVal = defaultVal;
        this.onChange = onChange;
        this.currentMode = BaseNekoXSettingsActivity.getMessageMenuMode(key, defaultVal);

        setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));

        LinearLayout rootLayout = new LinearLayout(context);
        rootLayout.setOrientation(LinearLayout.HORIZONTAL);
        rootLayout.setGravity(Gravity.CENTER_VERTICAL);
        addView(rootLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (iconRes != 0) {
            ImageView iconView = new ImageView(context);
            iconView.setImageResource(iconRes);
            iconView.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            rootLayout.addView(iconView, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 0, 0, 16, 0));
        }

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        rootLayout.addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        LinearLayout buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setGravity(Gravity.CENTER_VERTICAL);

        btnHide = createButton(context, context.getString(R.string.MessageMenuModeHide), 0);
        btnText = createButton(context, context.getString(R.string.MessageMenuModeText), 1);
        buttonsLayout.addView(btnHide, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 28, Gravity.CENTER_VERTICAL, 0, 0, 4, 0));
        buttonsLayout.addView(btnText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 28, Gravity.CENTER_VERTICAL, 0, 0, supportIcon ? 4 : 0, 0));

        if (supportIcon) {
            btnIcon = createButton(context, context.getString(R.string.MessageMenuModeIcon), 2);
            buttonsLayout.addView(btnIcon, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 28, Gravity.CENTER_VERTICAL));
        } else {
            btnIcon = null;
        }

        rootLayout.addView(buttonsLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        updateButtonStates();
    }

    private TextView createButton(Context context, String text, int mode) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        tv.setOnClickListener(v -> {
            if (currentMode != mode) {
                currentMode = mode;
                BaseNekoXSettingsActivity.setMessageMenuMode(key, currentMode);
                updateButtonStates();
                if (onChange != null) {
                    onChange.run();
                }
            }
        });
        return tv;
    }

    private void updateButtonStates() {
        setButtonStyle(btnHide, currentMode == 0);
        setButtonStyle(btnText, currentMode == 1);
        if (btnIcon != null) {
            setButtonStyle(btnIcon, currentMode == 2);
        }
    }

    private void setButtonStyle(TextView button, boolean selected) {
        if (button == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(6));
        if (selected) {
            int activeColor = Theme.getColor(Theme.key_featuredStickers_addButton);
            if (activeColor == 0) {
                activeColor = 0xff248bda;
            }
            bg.setColor(activeColor);
            button.setBackground(bg);
            button.setTextColor(Color.WHITE);
        } else {
            int inactiveBg = Theme.getColor(Theme.key_chat_inBubble);
            if (inactiveBg == 0) {
                inactiveBg = 0x14000000;
            } else {
                inactiveBg = Theme.multAlpha(inactiveBg, 0.4f);
            }
            bg.setColor(inactiveBg);
            button.setBackground(bg);
            int inactiveText = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
            if (inactiveText == 0) {
                inactiveText = 0xff888888;
            }
            button.setTextColor(inactiveText);
        }
    }
}
