package com.exteragram.messenger.feed.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

/**
 * A simple card cell that shows:
 *  • Channel avatar + name + date
 *  • Message text (truncated to 3 lines)
 * Tapping opens the full chat.
 */
public class FeedMessageCell extends FrameLayout {

    private final BackupImageView avatarView;
    private final TextView channelName;
    private final TextView messageDate;
    private final TextView messageText;
    private final Paint dividerPaint = new Paint();

    public FeedMessageCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        setWillNotDraw(false);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(12);
        root.setPadding(pad, pad, pad, pad);
        addView(root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Header row: avatar + channel name + date
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(AndroidUtilities.dp(16));
        header.addView(avatarView, LayoutHelper.createLinear(32, 32, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

        LinearLayout nameTime = new LinearLayout(context);
        nameTime.setOrientation(LinearLayout.VERTICAL);

        channelName = new TextView(context);
        channelName.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        channelName.setTypeface(AndroidUtilities.bold());
        channelName.setMaxLines(1);
        channelName.setEllipsize(TextUtils.TruncateAt.END);
        nameTime.addView(channelName, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        messageDate = new TextView(context);
        messageDate.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        nameTime.addView(messageDate, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        header.addView(nameTime, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        root.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // Message text
        messageText = new TextView(context);
        messageText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        messageText.setMaxLines(3);
        messageText.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(messageText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        updateColors(resourcesProvider);
    }

    private void updateColors(Theme.ResourcesProvider rp) {
        channelName.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, rp));
        messageDate.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3, rp));
        messageText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, rp));
        dividerPaint.setColor(Theme.getColor(Theme.key_divider, rp));
    }

    public void setMessage(MessageObject messageObject, int account) {
        if (messageObject == null) return;

        // Channel info
        long dialogId = messageObject.getDialogId();
        TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
        String title = chat != null ? chat.title : "";

        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setInfo(chat);
        avatarView.setForUserOrChat(chat, avatarDrawable);

        channelName.setText(title);

        // Date
        String dateStr = org.telegram.messenger.LocaleController.formatDateChat(messageObject.messageOwner.date);
        messageDate.setText(dateStr);

        // Text
        String text = messageObject.messageText != null ? messageObject.messageText.toString() : "";
        if (TextUtils.isEmpty(text) && messageObject.messageOwner.media != null) {
            if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                text = "📷 " + org.telegram.messenger.LocaleController.getString(R.string.AttachPhoto);
            } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
                text = "📎 " + org.telegram.messenger.LocaleController.getString(R.string.AttachDocument);
            } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeo) {
                text = "📍 " + org.telegram.messenger.LocaleController.getString(R.string.AttachLocation);
            } else {
                text = "📎 Media";
            }
        }
        messageText.setText(text);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        dividerPaint.setStrokeWidth(1);
        canvas.drawLine(AndroidUtilities.dp(12), getHeight() - 1, getWidth() - AndroidUtilities.dp(12), getHeight() - 1, dividerPaint);
    }
}
