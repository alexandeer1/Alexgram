
package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.SendMessagesHelper.SendMessageParams;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import org.telegram.ui.Components.ItemOptions;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpecialForwardActivity extends BaseFragment {

    private ArrayList<MessageObject> messages;
    private ArrayList<MessageObject> originalMessages;
    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private EditTextBoldCursor commentView;
    private FrameLayout bottomView;
    private ImageView sendButton;
    private MessageObject selectedMessage;
    private int selectedPosition = -1;

    private final static int edit_item = 1;

    public SpecialForwardActivity(ArrayList<MessageObject> sourceMessages) {
        this.messages = new ArrayList<>();
        this.originalMessages = new ArrayList<>();
        
        // Deep copy messages to avoid editing the actual chat messages
        for (MessageObject msg : sourceMessages) {
            try {
                // Create copy for working list
                SerializedData data = new SerializedData(msg.messageOwner.getObjectSize());
                msg.messageOwner.serializeToStream(data);
                
                SerializedData readData = new SerializedData(data.toByteArray());
                TLRPC.Message messageClone = TLRPC.Message.TLdeserialize(readData, readData.readInt32(false), false);
                messageClone.dialog_id = msg.getDialogId();
                MessageObject newObj = new MessageObject(UserConfig.selectedAccount, messageClone, false, false);
                newObj.messageText = msg.messageText; 
                newObj.caption = msg.caption;
                this.messages.add(newObj);
                
                data.cleanup();
                readData.cleanup();
                
                // Create SEPARATE copy for restore point
                SerializedData data2 = new SerializedData(msg.messageOwner.getObjectSize());
                msg.messageOwner.serializeToStream(data2);
                
                SerializedData readData2 = new SerializedData(data2.toByteArray());
                TLRPC.Message messageClone2 = TLRPC.Message.TLdeserialize(readData2, readData2.readInt32(false), false);
                messageClone2.dialog_id = msg.getDialogId();
                MessageObject originalObj = new MessageObject(UserConfig.selectedAccount, messageClone2, false, false);
                originalObj.messageText = msg.messageText; 
                originalObj.caption = msg.caption;
                this.originalMessages.add(originalObj);
                
                data2.cleanup();
                readData2.cleanup();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Special forward");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == edit_item) {
                     showEditOptions();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        // Ensure icon exists or use standard
        menu.addItem(edit_item, R.drawable.ic_ab_other);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(listAdapter = new ListAdapter(context));
        listView.setVerticalScrollBarEnabled(false);
        listView.setPadding(0, 0, 0, AndroidUtilities.dp(48));
        listView.setClipToPadding(false);
        listView.setOnItemClickListener((view, position) -> {
            if (position >= 0 && position < messages.size()) {
                 selectedMessage = messages.get(position);
                 selectedPosition = position;
                 if (commentView != null) {
                     CharSequence text = selectedMessage.caption != null ? selectedMessage.caption : selectedMessage.messageText;
                     commentView.setText(text != null ? text.toString() : "");
                     if (commentView.getText().length() > 0) {
                        commentView.setSelection(commentView.getText().length());
                     }
                     AndroidUtilities.showKeyboard(commentView);
                 }
            }
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 48));

        // Bottom Edit/Send View
        bottomView = new FrameLayout(context);
        bottomView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        
        commentView = new EditTextBoldCursor(context);
        commentView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        commentView.setHint("Edit Message");
        commentView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        commentView.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        commentView.setBackgroundDrawable(null);
        commentView.setPadding(0, 0, 0, 0);
        commentView.setMaxLines(4);
        commentView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        commentView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        bottomView.addView(commentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 16, 0, 48, 0));

        // Save/Update Button
        ImageView saveButton = new ImageView(context);
        saveButton.setImageResource(R.drawable.baseline_check_24); 
        saveButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText), PorterDuff.Mode.MULTIPLY));
        saveButton.setScaleType(ImageView.ScaleType.CENTER);
        saveButton.setOnClickListener(v -> {
             if (selectedMessage != null && commentView != null) {
                 String newText = commentView.getText().toString();
                 if (selectedMessage.caption != null) {
                     selectedMessage.caption = newText;
                 } else {
                     selectedMessage.messageText = newText;
                 }
                 // Also update messageOwner.message/caption legacy field just in case
                 if (selectedMessage.messageOwner != null) {
                     selectedMessage.messageOwner.message = newText;
                 }

                 if (selectedPosition != -1) {
                     listAdapter.notifyItemChanged(selectedPosition);
                 }
                 commentView.setText("");
                 selectedMessage = null;
                 selectedPosition = -1;
                 AndroidUtilities.hideKeyboard(commentView);
             }
        });
        bottomView.addView(saveButton, LayoutHelper.createFrame(48, 48, Gravity.CENTER_VERTICAL | Gravity.RIGHT));

        frameLayout.addView(bottomView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT));

        // Floating Action Button
        sendButton = new ImageView(context);
        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground));
        sendButton.setBackgroundDrawable(drawable);
        sendButton.setImageResource(R.drawable.baseline_send_24);
        sendButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.MULTIPLY));
        sendButton.setScaleType(ImageView.ScaleType.CENTER);
        sendButton.setOnClickListener(v -> {
             forwardMessages();
        });
        frameLayout.addView(sendButton, LayoutHelper.createFrame(56, 56, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 16, 64)); 

        return fragmentView;
    }

    private void showEditOptions() {
        ActionBarMenuItem editItem = actionBar.createMenu().getItem(edit_item);
        if (editItem == null) return;
        
        ItemOptions itemOptions = ItemOptions.makeOptions(this, editItem);
        itemOptions.add(R.drawable.msg_repeat, "Reset all", this::resetAll);
        itemOptions.add(R.drawable.msg_edit, "Replace all texts", this::replaceAllTexts);
        itemOptions.add(R.drawable.baseline_link_24, "Replace all links", this::replaceAllLinks);
        itemOptions.add(R.drawable.msg_delete, "Delete all links", this::deleteAllLinks);
        itemOptions.add(R.drawable.msg_delete, "Delete all media captions", this::deleteAllCaptions);
        itemOptions.show();
    }

    private void resetAll() {
        if (originalMessages == null || originalMessages.isEmpty()) return;
        
        messages.clear();
        for (MessageObject msg : originalMessages) {
             try {
                SerializedData data = new SerializedData(msg.messageOwner.getObjectSize());
                msg.messageOwner.serializeToStream(data);
                
                SerializedData readData = new SerializedData(data.toByteArray());
                TLRPC.Message messageClone = TLRPC.Message.TLdeserialize(readData, readData.readInt32(false), false);
                messageClone.dialog_id = msg.getDialogId();
                MessageObject newObj = new MessageObject(UserConfig.selectedAccount, messageClone, false, false);
                newObj.messageText = msg.messageText; 
                newObj.caption = msg.caption;
                this.messages.add(newObj);
                
                data.cleanup();
                readData.cleanup();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        listAdapter.notifyDataSetChanged();
        Toast.makeText(getParentActivity(), "Messages reset", Toast.LENGTH_SHORT).show();
    }

    private void replaceAllTexts() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Replace all texts");
        final EditText input = new EditText(getParentActivity());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        builder.setPositiveButton("Replace", (dialog, which) -> {
            String newText = input.getText().toString();
            for (MessageObject msg : messages) {
                if (msg.caption != null) {
                     msg.caption = newText;
                     if (msg.messageOwner != null) msg.messageOwner.message = newText; 
                } else {
                     msg.messageText = newText;
                     if (msg.messageOwner != null) msg.messageOwner.message = newText;
                }
            }
            listAdapter.notifyDataSetChanged();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void replaceAllLinks() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Replace all links");
        final EditText input = new EditText(getParentActivity());
        input.setHint("New link");
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        builder.setView(input);
        builder.setPositiveButton("Replace", (dialog, which) -> {
            String newLink = input.getText().toString();
            Pattern urlPattern = Pattern.compile("https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)");
            for (MessageObject msg : messages) {
                CharSequence cs = msg.caption != null ? msg.caption : msg.messageText;
                String text = cs != null ? cs.toString() : "";
                if (TextUtils.isEmpty(text)) continue;
                Matcher matcher = urlPattern.matcher(text);
                String result = matcher.replaceAll(newLink);
                if (msg.caption != null) msg.caption = result;
                else msg.messageText = result;
                
                if (msg.messageOwner != null) msg.messageOwner.message = result;
            }
            listAdapter.notifyDataSetChanged();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void deleteAllLinks() {
        Pattern urlPattern = Pattern.compile("https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)");
        for (MessageObject msg : messages) {
                CharSequence cs = msg.caption != null ? msg.caption : msg.messageText;
                String text = cs != null ? cs.toString() : "";
                if (TextUtils.isEmpty(text)) continue;
                Matcher matcher = urlPattern.matcher(text);
                String result = matcher.replaceAll("");
                if (msg.caption != null) msg.caption = result;
                else msg.messageText = result;
                if (msg.messageOwner != null) msg.messageOwner.message = result;
        }
        listAdapter.notifyDataSetChanged();
    }

    private void deleteAllCaptions() {
        for (MessageObject msg : messages) {
            msg.caption = null;
            // Also might need to clear messageText if it was derived from caption? 
            if (msg.isPhoto() || msg.isVideo() || msg.isDocument()) {
                msg.messageText = "";
                if (msg.messageOwner != null) msg.messageOwner.message = "";
            }
        }
        listAdapter.notifyDataSetChanged();
    }

    private void forwardMessages() {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_DEFAULT);
        DialogsActivity dialogsActivity = new DialogsActivity(args);
        dialogsActivity.setDelegate(new DialogsActivity.DialogsActivityDelegate() {
            @Override
            public boolean didSelectDialogs(DialogsActivity fragment, ArrayList<MessagesStorage.TopicKey> dids, CharSequence message, boolean param, boolean notify, int scheduleDate, int scheduleRepeatPeriod, TopicsFragment topicsFragment) {
                 if (dids == null || dids.isEmpty()) return false;
                 
                 long peer = dids.get(0).dialogId; 
                 
                 for (int i = 0; i < messages.size(); i++) {
                     MessageObject msg = messages.get(i);
                     
                     // Create SendMessageParams manually to ensure edited content is sent
                     SendMessageParams params;
                     
                     if (msg.isPhoto()) {
                         params = SendMessageParams.of(msg.messageOwner.media.photo, null, peer, null, null, msg.caption != null ? msg.caption.toString() : "", null, null, null, notify, scheduleDate, scheduleRepeatPeriod, 0, null, false);
                     } else if (msg.isDocument()) {
                         params = SendMessageParams.of(msg.messageOwner.media.document, null, null, peer, null, null, msg.caption != null ? msg.caption.toString() : "", null, null, null, notify, scheduleDate, scheduleRepeatPeriod, 0, null, null, false);
                     } else if (msg.messageText != null && !TextUtils.isEmpty(msg.messageText)) {
                         // Text message
                         params = SendMessageParams.of(msg.messageText.toString(), peer, null, null, null, true, null, null, null, notify, scheduleDate, scheduleRepeatPeriod, null, false);
                     } else {
                         // Fallback for other types - try to re-use object but update caption in params if possible or just forward
                         // For now, let's try to reconstruct params from object but force caption
                         params = SendMessageParams.of(msg);
                         if (msg.caption != null) params.caption = msg.caption.toString();
                         else if (msg.messageText != null) params.caption = msg.messageText.toString(); // message field in params used for caption sometimes? No.
                         // But if it's text, we handled it above.
                         // If it's a sticker or something else, we might just forward it effectively by sending as copy.
                         params.peer = peer;
                     }
                     
                     SendMessagesHelper.getInstance(UserConfig.selectedAccount).sendMessage(params);
                 }
                 
                 fragment.finishFragment();
                 finishFragment(); // Close special forward
                 return true;
            }
        });
        presentFragment(dialogsActivity);
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private Context mContext;
        public ListAdapter(Context context) { mContext = context; }
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
        @Override
        public int getItemCount() { return messages.size(); }
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new TextCell(mContext));
        }
        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            TextCell cell = (TextCell) holder.itemView;
            MessageObject message = messages.get(position);
            
            String displayText = "";
            if (message.caption != null) displayText = message.caption.toString();
            else if (message.messageText != null) displayText = message.messageText.toString();
            
            if (TextUtils.isEmpty(displayText)) {
                displayText = "[Media: " + message.type + "]";
            }
            cell.setText(displayText);
            cell.setBackgroundColor(selectedPosition == position ? Theme.getColor(Theme.key_chat_messagePanelBackground) : Color.TRANSPARENT);
        }
        @Override
        public int getItemViewType(int position) { return 0; }
    }

    private static class TextCell extends FrameLayout {
        private TextView textView;
        public TextCell(Context context) {
            super(context);
            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setSingleLine(false);
            textView.setMaxLines(3);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 16, 8, 16, 8));
        }
        public void setText(String text) { textView.setText(text); }
    }
}
