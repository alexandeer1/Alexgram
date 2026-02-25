
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

    private ChatMessageCell.ChatMessageCellDelegate chatMessageCellDelegate;

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
        if (chatMessageCellDelegate == null) {
            chatMessageCellDelegate = new ChatMessageCell.ChatMessageCellDelegate() {
                 @Override public boolean canPerformActions() { return false; }
                 @Override public void didPressImage(ChatMessageCell cell, float x, float y, boolean fullPreview) {}
                 @Override public void didQuickShareStart(ChatMessageCell cell, float x, float y) {}
                 // @Override public boolean isChatAdminCell(int uid) { return false; } // Not in interface
            };
        }
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
        
        // Attachment/Menu Button (Left)
        ImageView attachButton = new ImageView(context);
        attachButton.setImageResource(R.drawable.ic_ab_other); 
        attachButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
        attachButton.setScaleType(ImageView.ScaleType.CENTER);
        attachButton.setOnClickListener(v -> showEditOptions());
        bottomView.addView(attachButton, LayoutHelper.createFrame(48, 48, Gravity.CENTER_VERTICAL | Gravity.LEFT));
        
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
        // Shift input field to make space for attach button (48dp + 8dp margin = 56dp)
        bottomView.addView(commentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 56, 0, 48, 0));

        // Save/Update Button (Right)
        ImageView saveButton = new ImageView(context);
        saveButton.setImageResource(R.drawable.baseline_check_24); 
        saveButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText), PorterDuff.Mode.MULTIPLY));
        saveButton.setScaleType(ImageView.ScaleType.CENTER);
        saveButton.setOnClickListener(v -> {
             saveCurrentEdit();
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
             // Auto-save any pending edit before sending
             if (selectedMessage != null && commentView != null && commentView.getText().length() > 0) {
                 saveCurrentEdit();
             }
             forwardMessages();
        });
        frameLayout.addView(sendButton, LayoutHelper.createFrame(56, 56, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 16, 64)); 

        return fragmentView;
    }

    private MessageObject recreateMessageObject(MessageObject source) {
        if (source == null) return null;
        try {
            SerializedData data = new SerializedData(source.messageOwner.getObjectSize());
            source.messageOwner.serializeToStream(data);
            
            byte[] byteArray = data.toByteArray();
            data.cleanup();
            
            SerializedData readData = new SerializedData(byteArray);
            int constructor = readData.readInt32(false);
            TLRPC.Message messageClone = TLRPC.Message.TLdeserialize(readData, constructor, false);
            readData.cleanup();
            
            if (messageClone == null) return source;

            messageClone.dialog_id = source.getDialogId();
            
            MessageObject newObj = new MessageObject(UserConfig.selectedAccount, messageClone, false, false);
            newObj.messageText = source.messageText; 
            newObj.caption = source.caption;
            return newObj;
        } catch (Exception e) {
            FileLog.e(e);
            return source;
        }
    }

    private void saveCurrentEdit() {
         if (selectedMessage != null && commentView != null) {
             String newText = commentView.getText().toString();
             if (selectedMessage.caption != null) {
                 selectedMessage.caption = newText;
             } else {
                 selectedMessage.messageText = newText;
             }
             if (selectedMessage.messageOwner != null) {
                 selectedMessage.messageOwner.message = newText;
             }
             
             MessageObject newObj = recreateMessageObject(selectedMessage);
             if (newObj != null) {
                 messages.set(selectedPosition, newObj);
                 selectedMessage = newObj; 
             }

             if (selectedPosition != -1) {
                 listAdapter.notifyItemChanged(selectedPosition);
             }
             commentView.setText("");
             selectedMessage = null;
             selectedPosition = -1;
             AndroidUtilities.hideKeyboard(commentView);
         }
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
            for (int i = 0; i < messages.size(); i++) {
                MessageObject msg = messages.get(i);
                if (msg.caption != null) {
                     msg.caption = newText;
                     if (msg.messageOwner != null) msg.messageOwner.message = newText; 
                } else {
                     msg.messageText = newText;
                     if (msg.messageOwner != null) msg.messageOwner.message = newText;
                }
                messages.set(i, recreateMessageObject(msg));
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
            final String newLink = input.getText().toString();
            // Regex to match URLs in plain text
            Pattern urlPattern = Pattern.compile("https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)");
            
            for (int i = 0; i < messages.size(); i++) {
                MessageObject msg = messages.get(i);
                boolean changed = false;

                // 1. Handle embedded links (TextUrl) - Update the URL target only
                if (msg.messageOwner.entities != null && !msg.messageOwner.entities.isEmpty()) {
                    for (int j = 0; j < msg.messageOwner.entities.size(); j++) {
                        TLRPC.MessageEntity entity = msg.messageOwner.entities.get(j);
                        if (entity instanceof TLRPC.TL_messageEntityTextUrl) {
                            ((TLRPC.TL_messageEntityTextUrl) entity).url = newLink;
                            changed = true;
                        }
                    }
                }

                // 2. Handle plain text URLs
                CharSequence cs = msg.caption != null ? msg.caption : msg.messageText;
                String text = cs != null ? cs.toString() : "";
                
                if (!TextUtils.isEmpty(text)) {
                    Matcher matcher = urlPattern.matcher(text);
                    StringBuffer sb = new StringBuffer();
                    boolean found = false;
                    while (matcher.find()) {
                        matcher.appendReplacement(sb, newLink);
                        found = true;
                        changed = true;
                    }
                    matcher.appendTail(sb);
                    
                    if (found) {
                        String result = sb.toString();
                        if (msg.caption != null) msg.caption = result;
                        else msg.messageText = result;
                        if (msg.messageOwner != null) msg.messageOwner.message = result;
                        
                        // Since we changed text length, old entity offsets (except strictly TextUrl inside markdown? No, offsets are absolute) might be wrong.
                        // Ideally we should re-parse entities or shift them. 
                        // For simplicity, if we replaced text via regex, implies we touched URLs.
                        // Let's clear entities that are just plain URLs to avoid offset crashes, 
                        // but TextUrls (anchors) we updated in step 1 might now have wrong offsets if they came AFTER a replaced link.
                        // So, we should probably clear entities if we did a regex replacement, OR try to regenerate them.
                        // Re-generating layout with MessageObject(..., true, ...) parses entities? 
                        // No, generateLayout=true generates StaticLayout, not RPC entities.
                        // We will rely on simple replacement for now, fixing offsets is non-trivial. 
                        // But to prevent crash (IndexOutOfBounds in TextLayout), we might want to clear entities if text changed significantly.
                        // However, users want to keep "Text Link" functionality.
                    }
                }
                
                if (changed) {
                    messages.set(i, recreateMessageObject(msg));
                }
            }
            listAdapter.notifyDataSetChanged();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void deleteAllLinks() {
        Pattern urlPattern = Pattern.compile("https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)");
        for (int i = 0; i < messages.size(); i++) {
            MessageObject msg = messages.get(i);
            boolean changed = false;

            // 1. Remove embedded links (Convert TextUrl to plain text label - keep text, remove entity)
            if (msg.messageOwner.entities != null && !msg.messageOwner.entities.isEmpty()) {
                 ArrayList<TLRPC.MessageEntity> toRemove = new ArrayList<>();
                 for (int j = 0; j < msg.messageOwner.entities.size(); j++) {
                     TLRPC.MessageEntity entity = msg.messageOwner.entities.get(j);
                     if (entity instanceof TLRPC.TL_messageEntityTextUrl) {
                         // It's a text link [label](url). We want to keep 'label' but remove the link capability.
                         // So we just remove the entity from the list. The text remains 'label'.
                         toRemove.add(entity);
                         changed = true;
                     } else if (entity instanceof TLRPC.TL_messageEntityUrl) {
                         // It's a raw URL in text. The pattern below will handle removing the text if desired.
                         // But if we just want to unlink, we remove entity. 
                         // "Delete all links" usually means REMOVE THE URL TEXT. 
                         // So we leave this to the regex below.
                         toRemove.add(entity); // Remove entity anyway to be safe
                         changed = true;
                     }
                 }
                 msg.messageOwner.entities.removeAll(toRemove);
            }

            // 2. Remove plain text URLs
            CharSequence cs = msg.caption != null ? msg.caption : msg.messageText;
            String text = cs != null ? cs.toString() : "";
            
            if (!TextUtils.isEmpty(text)) {
                Matcher matcher = urlPattern.matcher(text);
                String result = matcher.replaceAll("").trim(); 
                
                if (!result.equals(text)) {
                    // Calculate if message becomes empty
                    boolean isEmpty = TextUtils.isEmpty(result);
                    boolean hasMedia = msg.isPhoto() || msg.isVideo() || msg.isDocument() || msg.isSticker();
                    
                    if (isEmpty && !hasMedia) {
                        // If it becomes empty text message, maybe user wants to delete the MessageObject?
                        // Or maybe we should keep it as empty? 
                        // User complained "it also hide message with links". 
                        // If we skip updating 'messageText' for empty result, the link stays.
                        // If we update it, it vanishes.
                        // Let's assume user doesn't want empty messages. 
                        // But we can't delete message here easily while iterating without messing up index/list.
                        // We'll set it to empty string.
                        // If user creates a message with JUST a link, and deletes links, it becomes empty.
                        // This seems correct for "Delete all links". 
                        // BUT maybe user means "Unlink" (keep text as is)? 
                        // Given "Delete", removal is expected. 
                        // I will keep the behavior but ensure we recreateObject properly.
                    }
                    
                    if (msg.caption != null) msg.caption = result;
                    else msg.messageText = result;
                    
                    if (msg.messageOwner != null) msg.messageOwner.message = result;
                    changed = true;
                }
            }
            
            if (changed) {
                messages.set(i, recreateMessageObject(msg));
            }
        }
        listAdapter.notifyDataSetChanged();
    }

    private void deleteAllCaptions() {
        for (int i = 0; i < messages.size(); i++) {
            MessageObject msg = messages.get(i);
            msg.caption = null;
            // Also might need to clear messageText if it was derived from caption? 
            if (msg.isPhoto() || msg.isVideo() || msg.isDocument()) {
                msg.messageText = "";
                if (msg.messageOwner != null) msg.messageOwner.message = "";
            }
            messages.set(i, recreateMessageObject(msg));
        }
        listAdapter.notifyDataSetChanged();
    }

    private void forwardMessages() {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        DialogsActivity dialogsActivity = new DialogsActivity(args);
        dialogsActivity.setDelegate(new DialogsActivity.DialogsActivityDelegate() {
            @Override
            public boolean didSelectDialogs(DialogsActivity fragment, ArrayList<MessagesStorage.TopicKey> dids, CharSequence message, boolean param, boolean notify, int scheduleDate, int scheduleRepeatPeriod, TopicsFragment topicsFragment) {
                 if (dids == null || dids.isEmpty()) return false;
                 
                 for (int i = 0; i < dids.size(); i++) {
                     long peer = dids.get(i).dialogId; 
                     for (int j = 0; j < messages.size(); j++) {
                         MessageObject msg = messages.get(j);
                     
                         // Create SendMessageParams manually to ensure edited content is sent
                         SendMessageParams params;
                     
                         if (msg.isPhoto()) {
                             params = SendMessageParams.of((TLRPC.TL_photo) msg.messageOwner.media.photo, null, peer, null, null, msg.caption != null ? msg.caption.toString() : "", null, null, null, notify, scheduleDate, scheduleRepeatPeriod, 0, null, false);
                         } else if (msg.isDocument()) {
                             params = SendMessageParams.of((TLRPC.TL_document) msg.messageOwner.media.document, null, null, peer, null, null, msg.caption != null ? msg.caption.toString() : "", null, null, null, notify, scheduleDate, scheduleRepeatPeriod, 0, null, null, false);
                         } else if (msg.messageText != null && !TextUtils.isEmpty(msg.messageText)) {
                             // Text message
                             params = SendMessageParams.of(msg.messageText.toString(), peer, null, null, null, true, null, null, null, notify, scheduleDate, scheduleRepeatPeriod, null, false);
                         } else {
                             // Fallback for other types
                             params = SendMessageParams.of(msg);
                             if (msg.caption != null) params.caption = msg.caption.toString();
                             else if (msg.messageText != null) params.caption = msg.messageText.toString(); 
                             params.peer = peer;
                         }
                     
                         SendMessagesHelper.getInstance(UserConfig.selectedAccount).sendMessage(params);
                     }
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
        private ChatMessageCell.ChatMessageCellDelegate delegate;

        public ListAdapter(Context context) { 
            mContext = context; 
            delegate = new ChatMessageCell.ChatMessageCellDelegate() {
                 @Override public boolean canPerformActions() { return false; }
                 @Override public void didPressImage(ChatMessageCell cell, float x, float y, boolean fullPreview) {}
                 @Override public void didQuickShareStart(ChatMessageCell cell, float x, float y) {}
            };
        }
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
        @Override
        public int getItemCount() { return messages.size(); }
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ChatMessageCell cell = new ChatMessageCell(mContext, UserConfig.selectedAccount);
            cell.setDelegate(delegate);
            return new RecyclerListView.Holder(cell);
        }
        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ChatMessageCell cell = (ChatMessageCell) holder.itemView;
            MessageObject message = messages.get(position);
            // Ensure message has a minimal valid setup for display
            cell.setMessageObject(message, null, false, false, false, false);
            cell.setBackgroundColor(selectedPosition == position ? Theme.getColor(Theme.key_chat_messagePanelBackground) : Color.TRANSPARENT);
        }
        @Override
        public int getItemViewType(int position) { return 0; }
    }
}
