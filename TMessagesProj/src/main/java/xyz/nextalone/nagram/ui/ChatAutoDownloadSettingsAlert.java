package xyz.nextalone.nagram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;
import xyz.nextalone.nagram.utils.ChatAutoDownloadHelper;

public class ChatAutoDownloadSettingsAlert extends BottomSheet {

    private final long dialogId;
    private final TextCheckCell enableCell;
    private final TextCheckCell photosCell;
    private final TextCheckCell videosCell;
    private final TextCheckCell filesCell;
    private final TextCheckCell voiceCell;
    private final TextCheckCell audioCell;

    public ChatAutoDownloadSettingsAlert(Context context, long dialogId) {
        super(context, true);
        this.dialogId = dialogId;

        FrameLayout container = new FrameLayout(context);

        TextView titleView = new TextView(context);
        titleView.setText(LocaleController.getString("ChatAutoDownloadTitle", R.string.ChatAutoDownloadTitle));
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        titleView.setTextSize(20);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setGravity(Gravity.CENTER);
        container.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP, 0, 12, 0, 0));

        ScrollView scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);
        container.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 60, 0, 12));

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(contentLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        boolean customEnabled = ChatAutoDownloadHelper.isCustomEnabled(dialogId);

        enableCell = new TextCheckCell(context);
        enableCell.setTextAndCheck(LocaleController.getString("ChatAutoDownloadEnable", R.string.ChatAutoDownloadEnable), customEnabled, true);
        enableCell.setOnClickListener(v -> {
            boolean checked = !enableCell.checkBox.isChecked();
            enableCell.checkBox.setChecked(checked, true);
            ChatAutoDownloadHelper.setCustomEnabled(dialogId, checked);
            updateOptions(checked);
        });
        contentLayout.addView(enableCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        photosCell = new TextCheckCell(context);
        photosCell.setTextAndCheck(LocaleController.getString("ChatAutoDownloadPhotos", R.string.ChatAutoDownloadPhotos), ChatAutoDownloadHelper.isMediaAutoDownloadEnabled(dialogId, ChatAutoDownloadHelper.TYPE_PHOTO), true);
        photosCell.setOnClickListener(v -> {
            if (!photosCell.isEnabled()) return;
            boolean checked = !photosCell.checkBox.isChecked();
            photosCell.checkBox.setChecked(checked, true);
            ChatAutoDownloadHelper.setMediaAutoDownloadEnabled(dialogId, ChatAutoDownloadHelper.TYPE_PHOTO, checked);
        });
        contentLayout.addView(photosCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        videosCell = new TextCheckCell(context);
        videosCell.setTextAndCheck(LocaleController.getString("ChatAutoDownloadVideos", R.string.ChatAutoDownloadVideos), ChatAutoDownloadHelper.isMediaAutoDownloadEnabled(dialogId, ChatAutoDownloadHelper.TYPE_VIDEO), true);
        videosCell.setOnClickListener(v -> {
            if (!videosCell.isEnabled()) return;
            boolean checked = !videosCell.checkBox.isChecked();
            videosCell.checkBox.setChecked(checked, true);
            ChatAutoDownloadHelper.setMediaAutoDownloadEnabled(dialogId, ChatAutoDownloadHelper.TYPE_VIDEO, checked);
        });
        contentLayout.addView(videosCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        filesCell = new TextCheckCell(context);
        filesCell.setTextAndCheck(LocaleController.getString("ChatAutoDownloadFiles", R.string.ChatAutoDownloadFiles), ChatAutoDownloadHelper.isMediaAutoDownloadEnabled(dialogId, ChatAutoDownloadHelper.TYPE_FILE), true);
        filesCell.setOnClickListener(v -> {
            if (!filesCell.isEnabled()) return;
            boolean checked = !filesCell.checkBox.isChecked();
            filesCell.checkBox.setChecked(checked, true);
            ChatAutoDownloadHelper.setMediaAutoDownloadEnabled(dialogId, ChatAutoDownloadHelper.TYPE_FILE, checked);
        });
        contentLayout.addView(filesCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        voiceCell = new TextCheckCell(context);
        voiceCell.setTextAndCheck(LocaleController.getString("ChatAutoDownloadVoice", R.string.ChatAutoDownloadVoice), ChatAutoDownloadHelper.isMediaAutoDownloadEnabled(dialogId, ChatAutoDownloadHelper.TYPE_VOICE), true);
        voiceCell.setOnClickListener(v -> {
            if (!voiceCell.isEnabled()) return;
            boolean checked = !voiceCell.checkBox.isChecked();
            voiceCell.checkBox.setChecked(checked, true);
            ChatAutoDownloadHelper.setMediaAutoDownloadEnabled(dialogId, ChatAutoDownloadHelper.TYPE_VOICE, checked);
        });
        contentLayout.addView(voiceCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        audioCell = new TextCheckCell(context);
        audioCell.setTextAndCheck(LocaleController.getString("ChatAutoDownloadAudio", R.string.ChatAutoDownloadAudio), ChatAutoDownloadHelper.isMediaAutoDownloadEnabled(dialogId, ChatAutoDownloadHelper.TYPE_AUDIO), false);
        audioCell.setOnClickListener(v -> {
            if (!audioCell.isEnabled()) return;
            boolean checked = !audioCell.checkBox.isChecked();
            audioCell.checkBox.setChecked(checked, true);
            ChatAutoDownloadHelper.setMediaAutoDownloadEnabled(dialogId, ChatAutoDownloadHelper.TYPE_AUDIO, checked);
        });
        contentLayout.addView(audioCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        updateOptions(customEnabled);
        setCustomView(container);
    }

    private void updateOptions(boolean customEnabled) {
        photosCell.setEnabled(customEnabled);
        videosCell.setEnabled(customEnabled);
        filesCell.setEnabled(customEnabled);
        voiceCell.setEnabled(customEnabled);
        audioCell.setEnabled(customEnabled);

        float alpha = customEnabled ? 1.0f : 0.5f;
        photosCell.setAlpha(alpha);
        videosCell.setAlpha(alpha);
        filesCell.setAlpha(alpha);
        voiceCell.setAlpha(alpha);
        audioCell.setAlpha(alpha);
    }
}
