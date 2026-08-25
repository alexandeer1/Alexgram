// [Alexgram: Special Forward] - Start
package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.text.style.URLSpan;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SendMessagesHelper.SendMessageParams;
import org.telegram.messenger.MessagesStorage;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;
import org.telegram.ui.Components.ItemOptions;
import android.text.SpannableStringBuilder;
import java.util.ArrayList;

public class SpecialForwardActivity extends ChatActivity {

    public static class CategoryTab {
        public int id;
        public String title;
        public int iconRes;
        public String emoticon;
        public org.telegram.messenger.MessagesController.DialogFilter filter;
    }

    private final SparseArray<MessageObject> originalMessagesMap = new SparseArray<>();
    private MessageObject selectedMessage = null;
    private int nextUniqueId = -100000;
    private boolean forwardAsFile = false;
    private FrameLayout tapToEditPanel = null;

    private final static int MENU_EDIT_OPTIONS = 1009;
    private final static int MENU_PREVIEW = 1010;

    private ImageView inlineResetView;
    private ImageView inlineReplaceLinkView;
    private ImageView inlineDeleteLinkView;

    public SpecialForwardActivity(ArrayList<MessageObject> sourceMessages) {
        super(new Bundle());
        this.chatMode = 102; // Custom multi-chat / special forward mode
        
        // Sort original messages so the newest (highest ID) comes first, matching Telegram's reversed messages layout
        java.util.Collections.sort(sourceMessages, (o1, o2) -> Integer.compare(o2.getId(), o1.getId()));
        
        // Deep copy messages and assign sequential negative unique IDs
        for (MessageObject msg : sourceMessages) {
            try {
                int id = nextUniqueId--;
                originalMessagesMap.put(id, msg);
                
                TLRPC.Message msgClone = cloneMessage(msg.messageOwner);
                if (msgClone != null) {
                    msgClone.id = id;
                    msgClone.fwd_msg_id = msg.getId();
                    
                    // Strip replies, reactions, and forwards metadata
                    msgClone.flags &= ~0x8D40;
                    msgClone.reply_to = null;
                    msgClone.reactions = null;
                    msgClone.reply_markup = null;
                    msgClone.fwd_from = null;
                    msgClone.edit_date = 0;
                    
                    MessageObject clonedObj = createPreviewMessageObject(msgClone, msg);
                    clonedObj.stableId = ChatActivity.lastStableId++;
                    clonedObj.forceUpdate = true;
                    clonedObj.checkLayout();
                    
                    this.messages.add(clonedObj);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    private TLRPC.Message cloneMessage(TLRPC.Message source) {
        if (source == null) return null;
        try {
            SerializedData data = new SerializedData(source.getObjectSize());
            source.serializeToStream(data);
            
            SerializedData readData = new SerializedData(data.toByteArray());
            int constructor = readData.readInt32(false);
            TLRPC.Message messageClone = TLRPC.Message.TLdeserialize(readData, constructor, false);
            
            data.cleanup();
            readData.cleanup();
            
            if (messageClone != null) {
                messageClone.dialog_id = source.dialog_id;
                messageClone.attachPath = source.attachPath;
                messageClone.params = source.params;
            }
            return messageClone;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private MessageObject createPreviewMessageObject(TLRPC.Message messageOwner, MessageObject originalObject) {
        if (originalObject != null && originalObject.localMediaOverridden && originalObject.messageOwner != null) {
            messageOwner.media = originalObject.messageOwner.media;
        }
        MessageObject messageObject = new MessageObject(currentAccount, messageOwner, false, true);
        messageObject.previewForward = true;
        if (originalObject != null) {
            messageObject.localGroupId = originalObject.localGroupId;
            messageObject.localSentGroupId = originalObject.localSentGroupId;
            messageObject.localMediaOverridden = originalObject.localMediaOverridden;
            messageObject.localMediaOverriddenPath = originalObject.localMediaOverriddenPath;
            messageObject.localMediaIsVideo = originalObject.localMediaIsVideo;
            messageObject.videoEditedInfo = originalObject.videoEditedInfo;
        }
        return messageObject;
    }

    private void setupTapToEditPanel() {
        if (chatActivityEnterView == null || chatActivityEnterView.getParent() == null) return;
        ViewGroup parent = (ViewGroup) chatActivityEnterView.getParent();
        if (tapToEditPanel == null) {
            Context context = getParentActivity();
            tapToEditPanel = new FrameLayout(context);
            
            TextView textView = new TextView(context);
            textView.setText("Tap on message to edit it");
            textView.setTextSize(15);
            textView.setGravity(Gravity.CENTER);
            textView.setTag("text");
            
            tapToEditPanel.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            tapToEditPanel.setOnClickListener(v -> {});
            
            parent.addView(tapToEditPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 7, 0, 7, 0));
        }
        
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(AndroidUtilities.dp(24));
        bg.setColor(getThemedColor(Theme.key_chat_messagePanelBackground));
        tapToEditPanel.setBackground(bg);
        
        TextView textView = tapToEditPanel.findViewWithTag("text");
        if (textView != null) {
            textView.setTextColor(getThemedColor(Theme.key_chat_messagePanelHint));
        }
    }

    private void updateEditModeUI() {
        setupTapToEditPanel();
        boolean isEditing = (selectedMessage != null);
        if (tapToEditPanel != null) {
            tapToEditPanel.setVisibility(isEditing ? View.GONE : View.VISIBLE);
        }
        if (chatActivityEnterView != null) {
            chatActivityEnterView.setVisibility(isEditing ? View.VISIBLE : View.INVISIBLE);
        }
        if (actionBar != null && actionBar.createMenu() != null) {
            ActionBarMenuItem previewItem = actionBar.createMenu().getItem(MENU_PREVIEW);
            if (previewItem != null) {
                previewItem.setIcon(isEditing ? R.drawable.msg_views_solar : R.drawable.ic_send);
            }
        }
    }

    @Override
    public void updateBottomOverlay() {
        super.updateBottomOverlay();
        updateEditModeUI();
    }

    @Override
    public void updateBottomOverlay(boolean animated) {
        super.updateBottomOverlay(animated);
        updateEditModeUI();
    }

    @Override
    public boolean isMultiChat() {
        return true;
    }

    @Override
    public boolean isMultiChatWithInput() {
        return true;
    }

    @Override
    protected boolean multiChatOnFragmentCreate() {
        return !messages.isEmpty();
    }

    @Override
    protected void multiChatMessagesFirstLoad(int lastLoadIndex) {
        setLoaded();
        updateVisibleRows();
    }

    @Override
    protected void multiChatOnCreateView() {
        setLoaded();
        
        if (bottomChannelButtonsLayout != null) {
            bottomChannelButtonsLayout.setVisibility(View.GONE);
        }
        
        if (chatActivityEnterView != null) {
            chatActivityEnterView.forceShowSendButton = true;
            chatActivityEnterView.checkSendButton(false);
            chatActivityEnterView.setVisibility(View.VISIBLE);
            chatActivityEnterView.setAllowStickersAndGifs(true, false, false, true);
            chatActivityEnterView.showEditDoneProgress(false, true);
            
            View doneBtn = chatActivityEnterView.getDoneButton();
            if (doneBtn != null) {
                doneBtn.setOnClickListener(v -> onDoneEditingMessage(chatActivityEnterView.getFieldText()));
            }
            if (chatActivityEnterView.sendButtonContainer != null) {
                for (int i = 0; i < chatActivityEnterView.sendButtonContainer.getChildCount(); i++) {
                    View child = chatActivityEnterView.sendButtonContainer.getChildAt(i);
                    if (child instanceof org.telegram.ui.Components.ChatActivityEnterView.SendButton) {
                        child.setOnClickListener(v -> forwardMessages());
                    }
                }
            }
        }
        
        if (replyCloseImageView != null) {
            replyCloseImageView.setOnClickListener(v -> {
                if (chatActivityEnterView != null) {
                    chatActivityEnterView.setEditingMessageObject(null, null, false);
                }
                hideFieldPanel(true);
                selectedMessage = null;
                editingMessageObject = null;
                updateBottomOverlay();
                updateVisibleRows();
                updateSendBadge();
            });
        }
        
        if (avatarContainer != null) {
            avatarContainer.setTitle(LocaleController.getString("SpecialForwardTitle", R.string.SpecialForwardTitle));
            avatarContainer.setSubtitle(messages.size() + " messages");
            
            // Customize avatar to be circular blue forward button
            if (avatarContainer.avatarImageView != null) {
                avatarContainer.avatarImageView.setVisibility(View.GONE);
            }
            
            ImageView customAvatar = new ImageView(getParentActivity());
            customAvatar.setImageResource(R.drawable.baseline_forward_24);
            customAvatar.setColorFilter(new android.graphics.PorterDuffColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN));
            
            android.graphics.drawable.GradientDrawable circleBg = new android.graphics.drawable.GradientDrawable();
            circleBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            circleBg.setColor(0xff2196f3); // Telegraph blue
            customAvatar.setBackground(circleBg);
            
            customAvatar.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
            customAvatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
            customAvatar.setOnClickListener(v -> forwardMessages());
            
            avatarContainer.addView(customAvatar, 0, LayoutHelper.createFrame(42, 42, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
        }
        
        setupCustomEditPanel();
        updateSendBadge();
        
        rebuildGroupedMessages();
        
        if (messages.size() == 1) {
            startEditingMessage(messages.get(0));
        } else {
            updateBottomOverlay();
        }
    }

    private void setupCustomEditPanel() {
        if (chatActivityEnterTopView == null || getParentActivity() == null) return;
        
        Context context = getParentActivity();
        
        View replyView = chatActivityEnterTopView.getReplyView();
        if (replyView != null && replyView.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) replyView.getLayoutParams();
            lp.rightMargin = AndroidUtilities.dp(196);
            replyView.setLayoutParams(lp);
        }
        View editView = chatActivityEnterTopView.getEditView();
        if (editView != null && editView.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) editView.getLayoutParams();
            lp.rightMargin = AndroidUtilities.dp(196);
            editView.setLayoutParams(lp);
        }

        if (inlineResetView != null) chatActivityEnterTopView.removeView(inlineResetView);
        if (inlineReplaceLinkView != null) chatActivityEnterTopView.removeView(inlineReplaceLinkView);
        if (inlineDeleteLinkView != null) chatActivityEnterTopView.removeView(inlineDeleteLinkView);
        
        inlineDeleteLinkView = new ImageView(context);
        inlineDeleteLinkView.setImageResource(R.drawable.msg_delete);
        inlineDeleteLinkView.setColorFilter(new android.graphics.PorterDuffColorFilter(getThemedColor(Theme.key_chat_replyPanelClose), android.graphics.PorterDuff.Mode.SRC_IN));
        inlineDeleteLinkView.setScaleType(ImageView.ScaleType.CENTER);
        inlineDeleteLinkView.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), 1, AndroidUtilities.dp(19)));
        inlineDeleteLinkView.setOnClickListener(v -> {
            if (selectedMessage != null) {
                deleteLinks(selectedMessage, 15);
                MessageObject updated = updateClonedMessage(selectedMessage);
                startEditingMessage(updated);
                Toast.makeText(context, "Deleted all links in message", Toast.LENGTH_SHORT).show();
            }
        });
        
        inlineReplaceLinkView = new ImageView(context);
        inlineReplaceLinkView.setImageResource(R.drawable.baseline_link_24);
        inlineReplaceLinkView.setColorFilter(new android.graphics.PorterDuffColorFilter(getThemedColor(Theme.key_chat_replyPanelClose), android.graphics.PorterDuff.Mode.SRC_IN));
        inlineReplaceLinkView.setScaleType(ImageView.ScaleType.CENTER);
        inlineReplaceLinkView.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), 1, AndroidUtilities.dp(19)));
        inlineReplaceLinkView.setOnClickListener(v -> {
            if (selectedMessage != null) {
                replaceLinkForSelectedMessage();
            }
        });
        
        inlineResetView = new ImageView(context);
        inlineResetView.setImageResource(R.drawable.msg_reset_all);
        inlineResetView.setColorFilter(new android.graphics.PorterDuffColorFilter(getThemedColor(Theme.key_chat_replyPanelClose), android.graphics.PorterDuff.Mode.SRC_IN));
        inlineResetView.setScaleType(ImageView.ScaleType.CENTER);
        inlineResetView.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), 1, AndroidUtilities.dp(19)));
        inlineResetView.setOnClickListener(v -> {
            resetSelectedMessage();
        });
        
        chatActivityEnterTopView.addView(inlineDeleteLinkView, LayoutHelper.createFrame(48, 46, Gravity.RIGHT | Gravity.TOP, 0, 0.5f, 52, 0));
        chatActivityEnterTopView.addView(inlineReplaceLinkView, LayoutHelper.createFrame(48, 46, Gravity.RIGHT | Gravity.TOP, 0, 0.5f, 100, 0));
        chatActivityEnterTopView.addView(inlineResetView, LayoutHelper.createFrame(48, 46, Gravity.RIGHT | Gravity.TOP, 0, 0.5f, 148, 0));
    }

    private void updateSendBadge() {
        if (chatActivityEnterView == null || getParentActivity() == null) return;
        
        View enterSendBtn = chatActivityEnterView.getSendButtonInternal();
        if (enterSendBtn instanceof org.telegram.ui.Components.ChatActivityEnterView.SendButton) {
            ((org.telegram.ui.Components.ChatActivityEnterView.SendButton) enterSendBtn).setCount(messages.size(), true);
        }
    }

    @Override
    protected boolean multiChatListOnItemClick(View view, int position) {
        if (position >= 0 && position < messages.size()) {
            startEditingMessage(messages.get(position));
            return true;
        }
        return false;
    }

    private void startEditingMessage(MessageObject messageObject) {
        if (messageObject == null) return;
        if (messageObject.getGroupId() != 0) {
            MessageObject.GroupedMessages groupedMessages = groupedMessagesMap.get(messageObject.getGroupId());
            if (groupedMessages != null) {
                if (groupedMessages.captionMessage != null) {
                    messageObject = groupedMessages.captionMessage;
                } else if (!groupedMessages.messages.isEmpty()) {
                    messageObject = groupedMessages.messages.get(0);
                }
            }
        }
        this.selectedMessage = messageObject;
        this.editingMessageObject = messageObject;
        
        showFieldPanelForEdit(true, messageObject);
        setupCustomEditPanel();
        
        if (chatActivityEnterView != null) {
            chatActivityEnterView.setEditingMessageObject(messageObject, null, !messageObject.isMediaEmpty());
            
            CharSequence editingText = messageObject.isMediaEmpty() ? messageObject.messageText : messageObject.caption;
            if (editingText == null) editingText = "";
            CharSequence formattedText = org.telegram.ui.Components.ChatActivityEnterView.applyMessageEntities(
                messageObject.messageOwner.entities,
                editingText,
                Theme.chat_msgTextPaint.getFontMetricsInt()
            );
            chatActivityEnterView.setFieldText(formattedText);

            View doneBtn = chatActivityEnterView.getDoneButton();
            if (doneBtn != null) {
                doneBtn.setOnClickListener(v -> onDoneEditingMessage(chatActivityEnterView.getFieldText()));
            }
            chatActivityEnterView.setVisibility(View.VISIBLE);
            chatActivityEnterView.setFieldFocused();
        }
        
        updateBottomOverlay();
        updateVisibleRows();
    }

    public void onDoneEditingMessage(CharSequence text) {
        if (selectedMessage != null) {
            updateMessageText(selectedMessage, text);
            int idx = messages.indexOf(selectedMessage);
            if (idx >= 0) {
                try {
                    TLRPC.Message messageClone = cloneMessage(selectedMessage.messageOwner);
                    if (messageClone != null) {
                        messageClone.id = selectedMessage.getId();
                        MessageObject newCloned = createPreviewMessageObject(messageClone, selectedMessage);
                        newCloned.stableId = selectedMessage.stableId;
                        newCloned.forceUpdate = true;
                        newCloned.checkLayout();
                        messages.set(idx, newCloned);
                        rebuildGroupedMessages();
                        if (chatAdapter != null) {
                            chatAdapter.notifyDataSetChanged();
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        if (chatActivityEnterView != null) {
            chatActivityEnterView.setEditingMessageObject(null, null, false);
        }
        hideFieldPanel(true);
        selectedMessage = null;
        editingMessageObject = null;
        updateBottomOverlay();
        updateVisibleRows();
        updateSendBadge();
    }

    @Override
    public void sendMedia(org.telegram.messenger.MediaController.PhotoEntry photoEntry, org.telegram.messenger.VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate, int scheduleRepeatPeriod, boolean forceDocument, long stars) {
        if (photoEntry == null || editingMessageObject == null) {
            return;
        }

        String newPath = photoEntry.imagePath != null ? photoEntry.imagePath : photoEntry.path;
        if (newPath == null) {
            return;
        }

        // Apply media edits/replacements locally
        if (photoEntry.isVideo) {
            TLRPC.TL_messageMediaDocument mediaDoc = new TLRPC.TL_messageMediaDocument();
            mediaDoc.document = new TLRPC.TL_document();
            mediaDoc.document.id = 0;
            mediaDoc.document.mime_type = "video/mp4";
            mediaDoc.document.size = (int) new java.io.File(newPath).length();
            
            TLRPC.TL_documentAttributeVideo attributeVideo = new TLRPC.TL_documentAttributeVideo();
            attributeVideo.duration = videoEditedInfo != null ? (int) (videoEditedInfo.estimatedDuration / 1000) : photoEntry.duration;
            
            int w = 800;
            int h = 600;
            if (videoEditedInfo != null) {
                w = videoEditedInfo.originalWidth;
                h = videoEditedInfo.originalHeight;
            } else {
                android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                if (photoEntry.coverPath != null) {
                    android.graphics.BitmapFactory.decodeFile(photoEntry.coverPath, opts);
                } else if (photoEntry.thumbPath != null) {
                    android.graphics.BitmapFactory.decodeFile(photoEntry.thumbPath, opts);
                }
                if (opts.outWidth > 0) {
                    w = opts.outWidth;
                    h = opts.outHeight;
                }
            }
            attributeVideo.w = w;
            attributeVideo.h = h;
            mediaDoc.document.attributes.add(attributeVideo);
            mediaDoc.document.localPath = newPath;
            
            String coverPath = photoEntry.coverPath != null ? photoEntry.coverPath : photoEntry.thumbPath;
            if (coverPath != null) {
                TLRPC.TL_photoSize thumbSize = new TLRPC.TL_photoSize();
                thumbSize.type = "x";
                thumbSize.location = new com.radolyn.ayugram.utils.AyuFileLocation(coverPath);
                thumbSize.w = w;
                thumbSize.h = h;
                thumbSize.size = (int) new java.io.File(coverPath).length();
                mediaDoc.document.thumbs.add(thumbSize);
            }
            editingMessageObject.messageOwner.media = mediaDoc;
        } else {
            TLRPC.TL_messageMediaPhoto mediaPhoto = new TLRPC.TL_messageMediaPhoto();
            mediaPhoto.photo = new TLRPC.TL_photo();
            mediaPhoto.photo.id = 0;
            
            TLRPC.TL_photoSize size = new TLRPC.TL_photoSize();
            size.type = "x";
            size.location = new com.radolyn.ayugram.utils.AyuFileLocation(newPath);
            
            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(newPath, opts);
            int w = opts.outWidth > 0 ? opts.outWidth : 800;
            int h = opts.outHeight > 0 ? opts.outHeight : 600;
            
            size.w = w;
            size.h = h;
            size.size = (int) new java.io.File(newPath).length();
            mediaPhoto.photo.sizes.add(size);
            editingMessageObject.messageOwner.media = mediaPhoto;
        }

        editingMessageObject.messageOwner.attachPath = newPath;
        editingMessageObject.videoEditedInfo = videoEditedInfo;
        editingMessageObject.localMediaOverridden = true;
        editingMessageObject.localMediaOverriddenPath = newPath;
        editingMessageObject.localMediaIsVideo = photoEntry.isVideo;

        if (photoEntry.caption != null) {
            updateMessageText(editingMessageObject, photoEntry.caption);
        }

        int idx = messages.indexOf(editingMessageObject);
        if (idx >= 0) {
            try {
                TLRPC.Message messageClone = cloneMessage(editingMessageObject.messageOwner);
                if (messageClone != null) {
                    messageClone.id = editingMessageObject.getId();
                    MessageObject newCloned = createPreviewMessageObject(messageClone, editingMessageObject);
                    newCloned.stableId = editingMessageObject.stableId;
                    newCloned.forceUpdate = true;
                    newCloned.checkLayout();
                    messages.set(idx, newCloned);
                    rebuildGroupedMessages();
                    if (chatAdapter != null) {
                        chatAdapter.notifyDataSetChanged();
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        if (chatActivityEnterView != null) {
            chatActivityEnterView.setEditingMessageObject(null, null, false);
        }
        hideFieldPanel(true);
        selectedMessage = null;
        editingMessageObject = null;
        updateBottomOverlay();
        updateVisibleRows();
        updateSendBadge();
    }

    @Override
    protected void multiChatOnMessageSend(CharSequence charSequence, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long topicId) {
        if (selectedMessage != null) {
            updateMessageText(selectedMessage, charSequence);
            
            int idx = messages.indexOf(selectedMessage);
            if (idx >= 0) {
                try {
                    TLRPC.Message messageClone = cloneMessage(selectedMessage.messageOwner);
                    if (messageClone != null) {
                        messageClone.id = selectedMessage.getId();
                        MessageObject newCloned = createPreviewMessageObject(messageClone, selectedMessage);
                        newCloned.stableId = selectedMessage.stableId;
                        newCloned.forceUpdate = true;
                        newCloned.checkLayout();
                        
                        messages.set(idx, newCloned);
                        rebuildGroupedMessages();
                        if (chatAdapter != null) {
                            chatAdapter.notifyItemChanged(idx);
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        
        if (chatActivityEnterView != null) {
            chatActivityEnterView.setEditingMessageObject(null, null, false);
        }
        hideFieldPanel(true);
        selectedMessage = null;
        editingMessageObject = null;
        
        updateBottomOverlay();
        updateVisibleRows();
        updateSendBadge();
    }

    @Override
    protected void multiChatCreateMenuItems(org.telegram.ui.ActionBar.ActionBarMenu menu) {
        menu.addItem(MENU_EDIT_OPTIONS, R.drawable.msg_edit);
        menu.addItem(MENU_PREVIEW, R.drawable.msg_views_solar);
    }

    @Override
    protected boolean multiChatOnMenuItemClicked(int itemId) {
        if (itemId == MENU_EDIT_OPTIONS) {
            showEditOptionsMenu();
            return true;
        } else if (itemId == MENU_PREVIEW) {
            if (selectedMessage == null) {
                forwardMessages();
            } else {
                showPreviewDialog();
            }
            return true;
        }
        return false;
    }

    private void showEditOptionsMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("SpecialForwardTitle", R.string.SpecialForwardTitle) + " Options");
        
        CharSequence[] items = new CharSequence[]{
            LocaleController.getString("SpecialForwardResetAll", R.string.SpecialForwardResetAll),
            LocaleController.getString("SpecialForwardReplaceAllTexts", R.string.SpecialForwardReplaceAllTexts),
            LocaleController.getString("SpecialForwardReplaceAllLinks", R.string.SpecialForwardReplaceAllLinks),
            LocaleController.getString("SpecialForwardDeleteAllLinks", R.string.SpecialForwardDeleteAllLinks),
            LocaleController.getString("SpecialForwardDeleteAllCaptions", R.string.SpecialForwardDeleteAllCaptions),
            LocaleController.getString("SpecialForwardAsFile", R.string.SpecialForwardAsFile) + ": " + (forwardAsFile ? "ON" : "OFF")
        };
        
        int[] icons = new int[]{
            R.drawable.msg_reset_all,
            R.drawable.msg_replace_text,
            R.drawable.msg_replace_link,
            R.drawable.msg_delete_link,
            R.drawable.msg_delete_caption,
            R.drawable.msg_forward_as_file
        };
        
        builder.setItems(items, icons, (dialog, which) -> {
            if (which == 0) {
                resetAll();
            } else if (which == 1) {
                replaceAllTexts();
            } else if (which == 2) {
                replaceAllLinks();
            } else if (which == 3) {
                deleteAllLinks();
            } else if (which == 4) {
                deleteAllCaptions();
            } else if (which == 5) {
                forwardAsFile = !forwardAsFile;
                Toast.makeText(getParentActivity(), LocaleController.getString("SpecialForwardAsFile", R.string.SpecialForwardAsFile) + ": " + (forwardAsFile ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
            }
        });
        builder.show();
    }

    private void showPreviewDialog() {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            MessageObject msg = messages.get(i);
            CharSequence text = getMessageText(msg);
            if (text != null && text.length() > 0) {
                if (builder.length() > 0) {
                    builder.append("\n\n---\n\n");
                }
                builder.append("Message #").append(String.valueOf(i + 1)).append(":\n").append(text);
            }
        }
        
        if (builder.length() == 0) {
            Toast.makeText(getParentActivity(), "All messages are empty", Toast.LENGTH_SHORT).show();
            return;
        }
        
        AlertDialog.Builder alert = new AlertDialog.Builder(getParentActivity());
        alert.setTitle("Forward Messages Preview");
        
        LinearLayout layout = new LinearLayout(getParentActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(16), AndroidUtilities.dp(24), AndroidUtilities.dp(16));
        
        TextView textView = new TextView(getParentActivity());
        textView.setText(builder);
        textView.setTextSize(16);
        textView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        
        alert.setView(layout);
        alert.setPositiveButton("OK", null);
        alert.show();
    }

    private void updateMessageText(MessageObject msg, CharSequence charSequence) {
        if (msg == null) return;
        ArrayList<TLRPC.MessageEntity> entities = charSequence == null ? null : MediaDataController.getInstance(currentAccount).getEntities(new CharSequence[]{charSequence}, true);
        msg.messageOwner.message = charSequence == null ? "" : charSequence.toString();
        TLRPC.Message message = msg.messageOwner;
        if (entities == null) {
            entities = new ArrayList<>();
        }
        message.entities = entities;
        msg.messageOwner.send_state = 3;
        msg.forceUpdate = true;
        if (!msg.isMediaEmpty()) {
            msg.caption = null;
            msg.generateCaption();
        } else if (TextUtils.isEmpty(msg.messageOwner.message)) {
            msg.messageOwner.message = "Empty message";
        }
        msg.applyNewText();
        msg.checkLayout();
        msg.messageOwner.send_state = 0;
    }

    private void updateMessageText(MessageObject msg, CharSequence charSequence, ArrayList<TLRPC.MessageEntity> entities) {
        if (msg == null) return;
        msg.messageOwner.message = charSequence == null ? "" : charSequence.toString();
        TLRPC.Message message = msg.messageOwner;
        if (entities == null) {
            entities = new ArrayList<>();
        }
        message.entities = entities;
        msg.messageOwner.send_state = 3;
        msg.forceUpdate = true;
        if (!msg.isMediaEmpty()) {
            msg.caption = null;
            msg.generateCaption();
        } else if (TextUtils.isEmpty(msg.messageOwner.message)) {
            msg.messageOwner.message = "Empty message";
        }
        msg.applyNewText();
        msg.checkLayout();
        msg.messageOwner.send_state = 0;
    }

    private MessageObject updateClonedMessage(MessageObject msg) {
        if (msg == null) return null;
        int idx = messages.indexOf(msg);
        if (idx >= 0) {
            try {
                TLRPC.Message messageClone = cloneMessage(msg.messageOwner);
                if (messageClone != null) {
                    messageClone.id = msg.getId();
                    MessageObject newCloned = createPreviewMessageObject(messageClone, msg);
                    newCloned.stableId = msg.stableId;
                    newCloned.forceUpdate = true;
                    newCloned.checkLayout();
                    messages.set(idx, newCloned);
                    rebuildGroupedMessages();
                    if (chatAdapter != null) {
                        chatAdapter.notifyDataSetChanged();
                    }
                    if (selectedMessage == msg) {
                        selectedMessage = newCloned;
                    }
                    if (editingMessageObject == msg) {
                        editingMessageObject = newCloned;
                    }
                    return newCloned;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return msg;
    }

    private CharSequence getMessageText(MessageObject msg) {
        if (msg == null) return "";
        return msg.isMediaEmpty() ? msg.messageText : msg.caption;
    }

    private void resetSelectedMessage() {
        if (selectedMessage == null) {
            Toast.makeText(getParentActivity(), "Reset error: selectedMessage is null", Toast.LENGTH_SHORT).show();
            return;
        }
        if (originalMessagesMap == null) {
            Toast.makeText(getParentActivity(), "Reset error: originalMessagesMap is null", Toast.LENGTH_SHORT).show();
            return;
        }
        int id = selectedMessage.getId();
        MessageObject originalObj = originalMessagesMap.get(id);
        if (originalObj == null) {
            Toast.makeText(getParentActivity(), "Reset error: original message not found for ID " + id, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            TLRPC.Message messageClone = cloneMessage(originalObj.messageOwner);
            if (messageClone != null) {
                messageClone.id = id;
                messageClone.fwd_msg_id = originalObj.getId();
                messageClone.flags &= ~0x8D40;
                messageClone.reply_to = null;
                messageClone.reactions = null;
                messageClone.reply_markup = null;
                messageClone.fwd_from = null;
                messageClone.edit_date = 0;
                
                MessageObject newCloned = createPreviewMessageObject(messageClone, originalObj);
                newCloned.stableId = selectedMessage.stableId;
                newCloned.forceUpdate = true;
                newCloned.checkLayout();
                
                int idx = messages.indexOf(selectedMessage);
                if (idx >= 0) {
                    messages.set(idx, newCloned);
                    rebuildGroupedMessages();
                    if (chatAdapter != null) {
                        chatAdapter.notifyDataSetChanged();
                    }
                    startEditingMessage(newCloned);
                    Toast.makeText(getParentActivity(), "Message reset to original", Toast.LENGTH_SHORT).show();
                } else {
                    int foundIdx = -1;
                    for (int i = 0; i < messages.size(); i++) {
                        if (messages.get(i).getId() == id) {
                            foundIdx = i;
                            break;
                        }
                    }
                    if (foundIdx >= 0) {
                        messages.set(foundIdx, newCloned);
                        rebuildGroupedMessages();
                        if (chatAdapter != null) {
                            chatAdapter.notifyDataSetChanged();
                        }
                        startEditingMessage(newCloned);
                        Toast.makeText(getParentActivity(), "Message reset to original (ID search)", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getParentActivity(), "Reset error: message not found in list, index: " + idx, Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Toast.makeText(getParentActivity(), "Reset error: failed to clone original", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            FileLog.e(e);
            Toast.makeText(getParentActivity(), "Reset error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void resetAll() {
        if (originalMessagesMap == null || originalMessagesMap.size() == 0) return;
        
        for (int i = 0; i < messages.size(); i++) {
            MessageObject workingObj = messages.get(i);
            MessageObject originalObj = originalMessagesMap.get(workingObj.getId());
            if (originalObj != null) {
                try {
                    TLRPC.Message messageClone = cloneMessage(originalObj.messageOwner);
                    if (messageClone != null) {
                        messageClone.id = workingObj.getId();
                        messageClone.fwd_msg_id = originalObj.getId();
                        messageClone.flags &= ~0x8D40;
                        messageClone.reply_to = null;
                        messageClone.reactions = null;
                        messageClone.reply_markup = null;
                        messageClone.fwd_from = null;
                        messageClone.edit_date = 0;
                        
                        MessageObject newCloned = createPreviewMessageObject(messageClone, originalObj);
                        newCloned.stableId = workingObj.stableId;
                        newCloned.forceUpdate = true;
                        newCloned.checkLayout();
                        
                        messages.set(i, newCloned);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        
        rebuildGroupedMessages();
        
        if (chatAdapter != null) {
            chatAdapter.notifyDataSetChanged();
        }
        
        if (selectedMessage != null) {
            startEditingMessage(selectedMessage);
        }
        updateSendBadge();
        Toast.makeText(getParentActivity(), LocaleController.getString("SpecialForwardResetSuccess", R.string.SpecialForwardResetSuccess), Toast.LENGTH_SHORT).show();
    }

    private void replaceAllTexts() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("SpecialForwardReplaceAllTexts", R.string.SpecialForwardReplaceAllTexts));
        
        LinearLayout layout = new LinearLayout(getParentActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));
        
        final org.telegram.ui.Cells.TextCheckCell checkPre = new org.telegram.ui.Cells.TextCheckCell(getParentActivity());
        checkPre.setTextAndCheck("Prepend (add at beginning)", false, true);
        
        final org.telegram.ui.Cells.TextCheckCell checkPost = new org.telegram.ui.Cells.TextCheckCell(getParentActivity());
        checkPost.setTextAndCheck("Append (add at end)", false, true);
        
        final EditText searchPhrase = new EditText(getParentActivity());
        searchPhrase.setHint("Phrase to search and replace (leave empty to replace all)");
        searchPhrase.setInputType(InputType.TYPE_CLASS_TEXT);
        searchPhrase.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        searchPhrase.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
        searchPhrase.setBackground(Theme.createEditTextDrawable(getParentActivity(), true));
        
        final EditText input = new EditText(getParentActivity());
        input.setHint("Replacement / New text");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
        input.setBackground(Theme.createEditTextDrawable(getParentActivity(), true));
        
        final boolean[] prep = new boolean[]{false};
        final boolean[] app = new boolean[]{false};
        checkPre.setOnClickListener(v -> {
            prep[0] = !prep[0];
            checkPre.setChecked(prep[0]);
            if (prep[0]) {
                app[0] = false;
                checkPost.setChecked(false);
                searchPhrase.setVisibility(View.GONE);
            } else {
                searchPhrase.setVisibility(View.VISIBLE);
            }
        });
        checkPost.setOnClickListener(v -> {
            app[0] = !app[0];
            checkPost.setChecked(app[0]);
            if (app[0]) {
                prep[0] = false;
                checkPre.setChecked(false);
                searchPhrase.setVisibility(View.GONE);
            } else {
                searchPhrase.setVisibility(View.VISIBLE);
            }
        });
        
        layout.addView(checkPre, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        layout.addView(checkPost, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        layout.addView(searchPhrase, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 12));
        layout.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        
        builder.setView(layout);
        builder.setPositiveButton("Replace", (dialog, which) -> {
            String newText = input.getText().toString();
            String searchVal = searchPhrase.getText().toString();
            
            for (int i = 0; i < messages.size(); i++) {
                MessageObject msg = messages.get(i);
                CharSequence oldText = getMessageText(msg);
                if (oldText == null) oldText = "";
                
                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(oldText);
                boolean isTextEmpty = oldText.length() == 0;
                
                if (prep[0]) {
                    spannableStringBuilder.replace(0, 0, isTextEmpty ? newText : newText + "\n");
                } else if (app[0]) {
                    if (isTextEmpty) {
                        spannableStringBuilder.append(newText);
                    } else {
                        spannableStringBuilder.append("\n").append(newText);
                    }
                } else {
                    if (TextUtils.isEmpty(searchVal)) {
                        spannableStringBuilder = new SpannableStringBuilder(newText);
                    } else {
                        String searchStr = searchVal;
                        String fullStr = spannableStringBuilder.toString();
                        int index = fullStr.indexOf(searchStr);
                        while (index != -1) {
                            spannableStringBuilder.replace(index, index + searchStr.length(), newText);
                            fullStr = spannableStringBuilder.toString();
                            index = fullStr.indexOf(searchStr, index + newText.length());
                        }
                    }
                }
                
                updateMessageText(msg, spannableStringBuilder);
                
                try {
                    TLRPC.Message messageClone = cloneMessage(msg.messageOwner);
                    if (messageClone != null) {
                        messageClone.id = msg.getId();
                        MessageObject newCloned = createPreviewMessageObject(messageClone, msg);
                        newCloned.stableId = msg.stableId;
                        newCloned.forceUpdate = true;
                        newCloned.checkLayout();
                        messages.set(i, newCloned);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            rebuildGroupedMessages();
            if (chatAdapter != null) {
                chatAdapter.notifyDataSetChanged();
            }
            if (selectedMessage != null) {
                startEditingMessage(selectedMessage);
            }
            updateSendBadge();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void replaceAllLinks() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("SpecialForwardReplaceAllLinks", R.string.SpecialForwardReplaceAllLinks));
        
        LinearLayout layout = new LinearLayout(getParentActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));
        
        final boolean[] checked = new boolean[]{true, true, true, true};
        String[] titles = new String[]{
            "Internal links (tg://, t.me)",
            "Mentions (@username)",
            "Hashtags (#tag)",
            "External links (http://, https://)"
        };
        
        for (int i = 0; i < titles.length; i++) {
            final int idx = i;
            final org.telegram.ui.Cells.TextCheckCell cell = new org.telegram.ui.Cells.TextCheckCell(getParentActivity());
            cell.setTextAndCheck(titles[i], checked[i], i < titles.length - 1);
            cell.setOnClickListener(v -> {
                checked[idx] = !checked[idx];
                cell.setChecked(checked[idx]);
            });
            layout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        
        final EditText input = new EditText(getParentActivity());
        input.setHint("New link (e.g. https://t.me/...)");
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
        input.setBackground(Theme.createEditTextDrawable(getParentActivity(), true));
        layout.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 0, 0));
        
        builder.setView(layout);
        builder.setPositiveButton("Replace", (dialog, which) -> {
            String newLink = input.getText().toString();
            int typeMask = 0;
            if (checked[0]) typeMask |= 1;
            if (checked[1]) typeMask |= 2;
            if (checked[2]) typeMask |= 4;
            if (checked[3]) typeMask |= 8;
            
            for (int i = 0; i < messages.size(); i++) {
                MessageObject msg = messages.get(i);
                replaceLinks(msg, typeMask, newLink);
                try {
                    TLRPC.Message messageClone = cloneMessage(msg.messageOwner);
                    if (messageClone != null) {
                        messageClone.id = msg.getId();
                        MessageObject newCloned = createPreviewMessageObject(messageClone, msg);
                        newCloned.stableId = msg.stableId;
                        newCloned.forceUpdate = true;
                        newCloned.checkLayout();
                        messages.set(i, newCloned);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            rebuildGroupedMessages();
            if (chatAdapter != null) {
                chatAdapter.notifyDataSetChanged();
            }
            if (selectedMessage != null) {
                for (MessageObject msg : messages) {
                    if (msg.stableId == selectedMessage.stableId) {
                        selectedMessage = msg;
                        break;
                    }
                }
                startEditingMessage(selectedMessage);
            }
            updateSendBadge();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void replaceLinkForSelectedMessage() {
        if (selectedMessage == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Replace Links in Selected Message");
        
        LinearLayout layout = new LinearLayout(getParentActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));
        
        final boolean[] checked = new boolean[]{true, true, true, true};
        String[] titles = new String[]{
            "Internal links (tg://, t.me)",
            "Mentions (@username)",
            "Hashtags (#tag)",
            "External links (http://, https://)"
        };
        
        for (int i = 0; i < titles.length; i++) {
            final int idx = i;
            final org.telegram.ui.Cells.TextCheckCell cell = new org.telegram.ui.Cells.TextCheckCell(getParentActivity());
            cell.setTextAndCheck(titles[i], checked[i], i < titles.length - 1);
            cell.setOnClickListener(v -> {
                checked[idx] = !checked[idx];
                cell.setChecked(checked[idx]);
            });
            layout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        
        final EditText input = new EditText(getParentActivity());
        input.setHint("New link (e.g. https://t.me/...)");
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
        input.setBackground(Theme.createEditTextDrawable(getParentActivity(), true));
        layout.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 0, 0));
        
        builder.setView(layout);
        builder.setPositiveButton("Replace", (dialog, which) -> {
            String newLink = input.getText().toString();
            int typeMask = 0;
            if (checked[0]) typeMask |= 1;
            if (checked[1]) typeMask |= 2;
            if (checked[2]) typeMask |= 4;
            if (checked[3]) typeMask |= 8;
            
            replaceLinks(selectedMessage, typeMask, newLink);
            MessageObject updated = updateClonedMessage(selectedMessage);
            startEditingMessage(updated);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void replaceLinks(MessageObject msg, int typeMask, CharSequence newLink) {
        if (msg == null || msg.messageOwner == null) return;
        String rawText = msg.messageOwner.message;
        if (rawText == null) rawText = "";
        
        ArrayList<TLRPC.MessageEntity> entities = msg.messageOwner.entities;
        if (entities == null || entities.isEmpty()) {
            entities = MediaDataController.getInstance(currentAccount).getEntities(new CharSequence[]{rawText}, true);
        }
        if (entities == null) {
            entities = new ArrayList<>();
        }
        
        StringBuilder textBuilder = new StringBuilder(rawText);
        String replacementStr = newLink.toString();
        
        ArrayList<TLRPC.MessageEntity> workingEntities = new ArrayList<>();
        for (TLRPC.MessageEntity ent : entities) {
            workingEntities.add(ent);
        }

        // Parse missing mentions from text
        try {
            java.util.regex.Matcher mentionMatcher = java.util.regex.Pattern.compile("@[a-zA-Z0-9_]{1,32}").matcher(rawText);
            while (mentionMatcher.find()) {
                int start = mentionMatcher.start();
                if (start > 0) {
                    char prev = rawText.charAt(start - 1);
                    if (!Character.isWhitespace(prev) && prev != '\n') {
                        continue;
                    }
                }
                int end = mentionMatcher.end();
                boolean overlap = false;
                for (TLRPC.MessageEntity existing : workingEntities) {
                    if (!(existing.offset >= end || existing.offset + existing.length <= start)) {
                        overlap = true;
                        break;
                    }
                }
                if (!overlap) {
                    TLRPC.TL_messageEntityMention mention = new TLRPC.TL_messageEntityMention();
                    mention.offset = start;
                    mention.length = end - start;
                    workingEntities.add(mention);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        // Parse missing hashtags from text
        try {
            java.util.regex.Matcher hashtagMatcher = java.util.regex.Pattern.compile("#[a-zA-Z0-9_]{1,32}").matcher(rawText);
            while (hashtagMatcher.find()) {
                int start = hashtagMatcher.start();
                if (start > 0) {
                    char prev = rawText.charAt(start - 1);
                    if (!Character.isWhitespace(prev) && prev != '\n') {
                        continue;
                    }
                }
                int end = hashtagMatcher.end();
                boolean overlap = false;
                for (TLRPC.MessageEntity existing : workingEntities) {
                    if (!(existing.offset >= end || existing.offset + existing.length <= start)) {
                        overlap = true;
                        break;
                    }
                }
                if (!overlap) {
                    TLRPC.TL_messageEntityHashtag hashtag = new TLRPC.TL_messageEntityHashtag();
                    hashtag.offset = start;
                    hashtag.length = end - start;
                    workingEntities.add(hashtag);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        
        ArrayList<TLRPC.MessageEntity> toReplace = new ArrayList<>();
        for (TLRPC.MessageEntity ent : workingEntities) {
            if (ent.offset < 0 || ent.offset >= rawText.length() || ent.offset + ent.length > rawText.length()) {
                continue;
            }
            
            String entText = rawText.substring(ent.offset, ent.offset + ent.length);
            boolean shouldReplace = false;

            if (ent instanceof TLRPC.TL_messageEntityMention || ent instanceof TLRPC.TL_messageEntityMentionName || ent instanceof TLRPC.TL_inputMessageEntityMentionName) {
                shouldReplace = (typeMask & 2) != 0;
            } else if (ent instanceof TLRPC.TL_messageEntityHashtag) {
                shouldReplace = (typeMask & 4) != 0;
            } else {
                String url = null;
                if (ent instanceof TLRPC.TL_messageEntityUrl) {
                    url = entText;
                } else if (ent instanceof TLRPC.TL_messageEntityTextUrl) {
                    url = ((TLRPC.TL_messageEntityTextUrl) ent).url;
                }
                
                if (url != null) {
                    boolean isInternal = org.telegram.messenger.browser.Browser.isInternalUrl(url, null);
                    if (isInternal) {
                        shouldReplace = (typeMask & 1) != 0;
                    } else {
                        shouldReplace = (typeMask & 8) != 0;
                    }
                }
            }
            
            if (shouldReplace) {
                toReplace.add(ent);
            }
        }
        
        java.util.Collections.sort(toReplace, (e1, e2) -> Integer.compare(e2.offset, e1.offset));
        
        for (TLRPC.MessageEntity ent : toReplace) {
            int oldOffset = ent.offset;
            int oldLength = ent.length;
            
            if (ent instanceof TLRPC.TL_messageEntityTextUrl) {
                ((TLRPC.TL_messageEntityTextUrl) ent).url = replacementStr;
                String entText = textBuilder.substring(ent.offset, ent.offset + ent.length);
                if (entText.startsWith("http://") || entText.startsWith("https://") || entText.startsWith("tg://") || entText.contains("t.me")) {
                    textBuilder.replace(ent.offset, ent.offset + ent.length, replacementStr);
                    int lengthDiff = replacementStr.length() - oldLength;
                    ent.length = replacementStr.length();
                    
                    for (TLRPC.MessageEntity other : workingEntities) {
                        if (other != ent && other.offset > oldOffset) {
                            other.offset += lengthDiff;
                        }
                    }
                }
            } else {
                textBuilder.replace(ent.offset, ent.offset + ent.length, replacementStr);
                int lengthDiff = replacementStr.length() - oldLength;
                
                TLRPC.MessageEntity newUrlEnt;
                if (replacementStr.startsWith("@")) {
                    newUrlEnt = new TLRPC.TL_messageEntityMention();
                } else if (replacementStr.startsWith("#")) {
                    newUrlEnt = new TLRPC.TL_messageEntityHashtag();
                } else {
                    newUrlEnt = new TLRPC.TL_messageEntityUrl();
                }
                newUrlEnt.offset = ent.offset;
                newUrlEnt.length = replacementStr.length();
                
                int index = workingEntities.indexOf(ent);
                if (index >= 0) {
                    workingEntities.set(index, newUrlEnt);
                }
                
                for (TLRPC.MessageEntity other : workingEntities) {
                    if (other != ent && other != newUrlEnt) {
                        if (other.offset > oldOffset) {
                            other.offset += lengthDiff;
                        }
                    }
                }
            }
        }
        
        updateMessageText(msg, textBuilder.toString(), workingEntities);
    }

    private void deleteAllLinks() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("SpecialForwardDeleteAllLinks", R.string.SpecialForwardDeleteAllLinks));
        
        LinearLayout layout = new LinearLayout(getParentActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));
        
        final boolean[] checked = new boolean[]{true, true, true, true};
        String[] titles = new String[]{
            "Internal links (tg://, t.me)",
            "Mentions (@username)",
            "Hashtags (#tag)",
            "External links (http://, https://)"
        };
        
        for (int i = 0; i < titles.length; i++) {
            final int idx = i;
            final org.telegram.ui.Cells.TextCheckCell cell = new org.telegram.ui.Cells.TextCheckCell(getParentActivity());
            cell.setTextAndCheck(titles[i], checked[i], i < titles.length - 1);
            cell.setOnClickListener(v -> {
                checked[idx] = !checked[idx];
                cell.setChecked(checked[idx]);
            });
            layout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        
        builder.setView(layout);
        builder.setPositiveButton("Delete", (dialog, which) -> {
            int typeMask = 0;
            if (checked[0]) typeMask |= 1;
            if (checked[1]) typeMask |= 2;
            if (checked[2]) typeMask |= 4;
            if (checked[3]) typeMask |= 8;
            
            for (int i = 0; i < messages.size(); i++) {
                MessageObject msg = messages.get(i);
                deleteLinks(msg, typeMask);
                try {
                    TLRPC.Message messageClone = cloneMessage(msg.messageOwner);
                    if (messageClone != null) {
                        messageClone.id = msg.getId();
                        MessageObject newCloned = createPreviewMessageObject(messageClone, msg);
                        newCloned.stableId = msg.stableId;
                        newCloned.forceUpdate = true;
                        newCloned.checkLayout();
                        messages.set(i, newCloned);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            rebuildGroupedMessages();
            if (chatAdapter != null) {
                chatAdapter.notifyDataSetChanged();
            }
            if (selectedMessage != null) {
                for (MessageObject msg : messages) {
                    if (msg.stableId == selectedMessage.stableId) {
                        selectedMessage = msg;
                        break;
                    }
                }
                startEditingMessage(selectedMessage);
            }
            updateSendBadge();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void deleteLinks(MessageObject msg, int typeMask) {
        if (msg == null || msg.messageOwner == null) return;
        String rawText = msg.messageOwner.message;
        if (rawText == null) rawText = "";
        
        ArrayList<TLRPC.MessageEntity> entities = msg.messageOwner.entities;
        if (entities == null || entities.isEmpty()) {
            entities = MediaDataController.getInstance(currentAccount).getEntities(new CharSequence[]{rawText}, true);
        }
        if (entities == null) {
            entities = new ArrayList<>();
        }
        
        StringBuilder textBuilder = new StringBuilder(rawText);
        
        ArrayList<TLRPC.MessageEntity> workingEntities = new ArrayList<>();
        for (TLRPC.MessageEntity ent : entities) {
            workingEntities.add(ent);
        }

        // Parse missing mentions from text
        try {
            java.util.regex.Matcher mentionMatcher = java.util.regex.Pattern.compile("@[a-zA-Z0-9_]{1,32}").matcher(rawText);
            while (mentionMatcher.find()) {
                int start = mentionMatcher.start();
                if (start > 0) {
                    char prev = rawText.charAt(start - 1);
                    if (!Character.isWhitespace(prev) && prev != '\n') {
                        continue;
                    }
                }
                int end = mentionMatcher.end();
                boolean overlap = false;
                for (TLRPC.MessageEntity existing : workingEntities) {
                    if (!(existing.offset >= end || existing.offset + existing.length <= start)) {
                        overlap = true;
                        break;
                    }
                }
                if (!overlap) {
                    TLRPC.TL_messageEntityMention mention = new TLRPC.TL_messageEntityMention();
                    mention.offset = start;
                    mention.length = end - start;
                    workingEntities.add(mention);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        // Parse missing hashtags from text
        try {
            java.util.regex.Matcher hashtagMatcher = java.util.regex.Pattern.compile("#[a-zA-Z0-9_]{1,32}").matcher(rawText);
            while (hashtagMatcher.find()) {
                int start = hashtagMatcher.start();
                if (start > 0) {
                    char prev = rawText.charAt(start - 1);
                    if (!Character.isWhitespace(prev) && prev != '\n') {
                        continue;
                    }
                }
                int end = hashtagMatcher.end();
                boolean overlap = false;
                for (TLRPC.MessageEntity existing : workingEntities) {
                    if (!(existing.offset >= end || existing.offset + existing.length <= start)) {
                        overlap = true;
                        break;
                    }
                }
                if (!overlap) {
                    TLRPC.TL_messageEntityHashtag hashtag = new TLRPC.TL_messageEntityHashtag();
                    hashtag.offset = start;
                    hashtag.length = end - start;
                    workingEntities.add(hashtag);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        
        ArrayList<TLRPC.MessageEntity> toDeleteText = new ArrayList<>();
        ArrayList<TLRPC.MessageEntity> toRemoveEntityOnly = new ArrayList<>();
        
        for (TLRPC.MessageEntity ent : workingEntities) {
            if (ent.offset < 0 || ent.offset >= rawText.length() || ent.offset + ent.length > rawText.length()) {
                continue;
            }
            
            String entText = rawText.substring(ent.offset, ent.offset + ent.length);
            boolean isTextUrl = false;
            boolean shouldDelete = false;
            
            if (ent instanceof TLRPC.TL_messageEntityMention || ent instanceof TLRPC.TL_messageEntityMentionName || ent instanceof TLRPC.TL_inputMessageEntityMentionName) {
                shouldDelete = (typeMask & 2) != 0;
            } else if (ent instanceof TLRPC.TL_messageEntityHashtag) {
                shouldDelete = (typeMask & 4) != 0;
            } else {
                String url = null;
                if (ent instanceof TLRPC.TL_messageEntityUrl) {
                    url = entText;
                } else if (ent instanceof TLRPC.TL_messageEntityTextUrl) {
                    url = ((TLRPC.TL_messageEntityTextUrl) ent).url;
                    isTextUrl = true;
                }
                
                if (url != null) {
                    boolean isInternal = org.telegram.messenger.browser.Browser.isInternalUrl(url, null);
                    if (isInternal) {
                        shouldDelete = (typeMask & 1) != 0;
                    } else {
                        shouldDelete = (typeMask & 8) != 0;
                    }
                }
            }
            
            if (shouldDelete) {
                if (isTextUrl) {
                    if (entText.startsWith("http://") || entText.startsWith("https://") || entText.startsWith("tg://") || entText.contains("t.me")) {
                        toDeleteText.add(ent);
                    } else {
                        toRemoveEntityOnly.add(ent);
                    }
                } else {
                    toDeleteText.add(ent);
                }
            }
        }
        
        java.util.Collections.sort(toDeleteText, (e1, e2) -> Integer.compare(e2.offset, e1.offset));
        
        for (TLRPC.MessageEntity ent : toDeleteText) {
            int oldOffset = ent.offset;
            int oldLength = ent.length;
            
            textBuilder.delete(ent.offset, ent.offset + ent.length);
            workingEntities.remove(ent);
            
            int lengthDiff = -oldLength;
            for (TLRPC.MessageEntity other : workingEntities) {
                if (other.offset > oldOffset) {
                    other.offset += lengthDiff;
                }
            }
        }
        
        for (TLRPC.MessageEntity ent : toRemoveEntityOnly) {
            workingEntities.remove(ent);
        }
        
        updateMessageText(msg, textBuilder.toString(), workingEntities);
    }

    private void deleteAllCaptions() {
        for (int i = 0; i < messages.size(); i++) {
            MessageObject msg = messages.get(i);
            if (msg.isMediaEmpty()) {
                continue;
            }
            msg.caption = null;
            if (msg.isPhoto() || msg.isVideo() || msg.isDocument()) {
                msg.messageText = "";
                if (msg.messageOwner != null) msg.messageOwner.message = "";
            }
            updateMessageText(msg, "");
            try {
                TLRPC.Message messageClone = cloneMessage(msg.messageOwner);
                if (messageClone != null) {
                    messageClone.id = msg.getId();
                    MessageObject newCloned = createPreviewMessageObject(messageClone, msg);
                    newCloned.stableId = msg.stableId;
                    newCloned.forceUpdate = true;
                    newCloned.checkLayout();
                    messages.set(i, newCloned);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        
        rebuildGroupedMessages();
        
        if (chatAdapter != null) {
            chatAdapter.notifyDataSetChanged();
        }
        if (selectedMessage != null) {
            startEditingMessage(selectedMessage);
        }
        updateSendBadge();
    }

    private void rebuildGroupedMessages() {
        if (groupedMessagesMap == null) return;
        groupedMessagesMap.clear();
        for (MessageObject msgObj : messages) {
            if (msgObj.getGroupId() != 0) {
                MessageObject.GroupedMessages groupedMessages = groupedMessagesMap.get(msgObj.getGroupId());
                if (groupedMessages == null) {
                    groupedMessages = new MessageObject.GroupedMessages();
                    groupedMessages.groupId = msgObj.getGroupId();
                    groupedMessagesMap.put(groupedMessages.groupId, groupedMessages);
                }
                groupedMessages.messages.add(msgObj);
                groupedMessages.reversed = reversed;
            }
        }
        for (int i = 0; i < groupedMessagesMap.size(); i++) {
            MessageObject.GroupedMessages groupedMessages = groupedMessagesMap.valueAt(i);
            java.util.Collections.sort(groupedMessages.messages, (a, b) -> Integer.compare(a.getId(), b.getId()));
            groupedMessages.calculate();
        }
    }

    private void forwardMessages() {
        if (messages.isEmpty() || getParentActivity() == null) return;
        
        SpecialForwardShareAlert shareAlert = new SpecialForwardShareAlert(
            getParentActivity(), 
            currentAccount, 
            messages, 
            forwardAsFile, 
            (dids, showQuoteState, keepCaptionState, sendAsAlbumState, removeLinkPreviewState) -> {
                performForwardToSelectedChats(dids, showQuoteState, keepCaptionState, removeLinkPreviewState);
            }
        );
        showDialog(shareAlert);
    }

    private void sendMessagesToPeer(ArrayList<MessageObject> forwardList, long peer, boolean showQuote, boolean keepCaption) {
        boolean sendAsCopy = !showQuote;
        if (!sendAsCopy) {
            for (MessageObject msg : forwardList) {
                if (msg != null && (getMessagesController().isPeerNoForwards(msg.getDialogId(), true) 
                        || (msg.messageOwner != null && msg.messageOwner.noforwards)
                        || msg.localMediaOverridden)) {
                    sendAsCopy = true;
                    break;
                }
            }
        }
        
        if (sendAsCopy) {
            for (int a = 0; a < forwardList.size(); a++) {
                MessageObject message = forwardList.get(a);
                if (message == null) continue;
                long groupId = message.messageOwner.grouped_id;
                if (groupId != 0) {
                    ArrayList<MessageObject> group = new ArrayList<>();
                    group.add(message);
                    while (a + 1 < forwardList.size() && forwardList.get(a + 1).messageOwner.grouped_id != 0 && forwardList.get(a + 1).messageOwner.grouped_id == groupId) {
                        group.add(forwardList.get(++a));
                    }
                    
                    boolean hasOverridden = false;
                    for (MessageObject m : group) {
                        if (m.localMediaOverridden) {
                            hasOverridden = true;
                            break;
                        }
                    }
                    
                    if (hasOverridden) {
                        ArrayList<SendMessagesHelper.SendingMediaInfo> infos = new ArrayList<>();
                        boolean forceDocument = false;
                        for (MessageObject m : group) {
                            String path = m.localMediaOverridden ? m.localMediaOverriddenPath : null;
                            if (path == null) {
                                java.io.File f = getFileLoader().getPathToMessage(m.messageOwner);
                                if (f != null && f.exists()) {
                                    path = f.getAbsolutePath();
                                }
                            }
                            if (path == null) {
                                path = m.messageOwner.attachPath;
                            }
                            
                            boolean isVideo = false;
                            if (m.localMediaOverridden) {
                                isVideo = m.localMediaIsVideo;
                            } else if (m.messageOwner.media.document instanceof TLRPC.TL_document) {
                                TLRPC.TL_document document = (TLRPC.TL_document) m.messageOwner.media.document;
                                isVideo = MessageObject.isVideoDocument(document) || m.videoEditedInfo != null;
                                if (!isVideo) forceDocument = true;
                            }
                            
                            if (path != null) {
                                SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
                                info.path = path;
                                info.isVideo = isVideo;
                                info.caption = keepCaption && m.messageOwner.message != null ? m.messageOwner.message : "";
                                info.entities = keepCaption ? m.messageOwner.entities : null;
                                if (m.messageOwner.media != null) {
                                    info.ttl = m.messageOwner.media.ttl_seconds;
                                    info.hasMediaSpoilers = m.messageOwner.media.spoiler;
                                }
                                info.videoEditedInfo = m.videoEditedInfo;
                                infos.add(info);
                            }
                        }
                        
                        if (!infos.isEmpty()) {
                            final boolean finalForceDocument = forceDocument;
                            final ArrayList<SendMessagesHelper.SendingMediaInfo> finalInfos = infos;
                            AndroidUtilities.runOnUIThread(() -> {
                                SendMessagesHelper.prepareSendingMedia(getAccountInstance(), finalInfos, peer, null, null, null, null, finalForceDocument, true, null, true, 0, 0, 0, false, null, null, 0L, false, 0L, 0L, null);
                            });
                        }
                    } else {
                        if (group.size() > 1) {
                            SendMessagesHelper.getInstance(currentAccount).processForwardFromMyName(group, peer, 0, 0, null);
                        } else {
                            SendMessagesHelper.getInstance(currentAccount).processForwardFromMyName(message, peer, 0, 0, null);
                        }
                    }
                } else {
                    if (message.localMediaOverridden) {
                        String path = message.localMediaOverriddenPath;
                        String caption = keepCaption && message.messageOwner.message != null ? message.messageOwner.message : "";
                        ArrayList<TLRPC.MessageEntity> entities = keepCaption ? message.messageOwner.entities : null;
                        if (message.localMediaIsVideo) {
                            SendMessagesHelper.prepareSendingVideo(getAccountInstance(), path, message.videoEditedInfo, null, null, peer, null, null, null, null, entities, 0, null, true, 0, 0, false, false, caption, null, 0L, 0L);
                        } else {
                            SendMessagesHelper.prepareSendingPhoto(getAccountInstance(), path, null, null, peer, null, null, null, null, entities, null, null, 0, null, message.videoEditedInfo, true, 0, 0, false, caption, null, 0L, 0L);
                        }
                    } else {
                        SendMessagesHelper.getInstance(currentAccount).processForwardFromMyName(message, peer, 0, 0, null);
                    }
                }
            }
        } else {
            SendMessagesHelper.getInstance(currentAccount).sendMessage(forwardList, peer, false, !keepCaption, true, 0, 0);
        }
    }

    private void performForwardToSelectedChats(ArrayList<Long> dids, boolean showQuote, boolean keepCaption, boolean removeLinkPreview) {
        ArrayList<MessageObject> forwardList = new ArrayList<>(messages);
        java.util.Collections.sort(forwardList, (o1, o2) -> Integer.compare(o1.getId(), o2.getId()));

        // Strip web page preview if requested
        if (removeLinkPreview) {
            for (MessageObject msg : forwardList) {
                if (msg != null && msg.messageOwner != null
                        && msg.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
                    msg.messageOwner.media = new TLRPC.TL_messageMediaEmpty();
                }
            }
        }

        for (long peer : dids) {
            if (forwardAsFile) {
                for (int j = 0; j < forwardList.size(); j++) {
                    MessageObject msg = forwardList.get(j);
                    if (msg == null || msg.messageOwner == null) continue;
                    
                    String caption = keepCaption && msg.caption != null ? msg.caption.toString() : "";
                    ArrayList<TLRPC.MessageEntity> entities = keepCaption ? msg.messageOwner.entities : new ArrayList<>();
                    String attachPath = msg.messageOwner.attachPath;
                    
                    if (!TextUtils.isEmpty(attachPath) && !attachPath.startsWith("http")) {
                        ArrayList<String> paths = new ArrayList<>();
                        paths.add(attachPath);
                        SendMessagesHelper.prepareSendingDocuments(getAccountInstance(), paths, paths, null, caption, entities, null, peer, null, null, null, null, null, false, 0, 0, null, null, 0L, false, 0L, 0L, null);
                    } else {
                        ArrayList<MessageObject> singleList = new ArrayList<>();
                        singleList.add(msg);
                        sendMessagesToPeer(singleList, peer, showQuote, keepCaption);
                    }
                }
            } else {
                sendMessagesToPeer(forwardList, peer, showQuote, keepCaption);
            }
        }
        finishFragment();
    }

    public static class SpecialForwardShareAlert extends BottomSheet implements org.telegram.messenger.NotificationCenter.NotificationCenterDelegate {
        private final int currentAccount;
        private final ArrayList<MessageObject> messages;
        private boolean forwardAsFile;
        private final ShareAlertCallback callback;
        
        private ArrayList<TLRPC.Dialog> allDialogs;
        private final ArrayList<TLRPC.Dialog> filteredDialogs = new ArrayList<>();
        private final LongSparseArray<TLRPC.Dialog> selectedDialogs = new LongSparseArray<>();
        private org.telegram.ui.Adapters.SearchAdapterHelper searchAdapterHelper;
        private int lastSearchId = 0;
        
        private final ArrayList<CategoryTab> tabs = new ArrayList<>();
        private int activeCategoryIndex = 0;
        private String searchQuery = "";
        
        private boolean showQuote = false;
        private boolean keepCaption = true;
        private boolean sendAsAlbum = false;
        private boolean removeLinkPreview = false;
        private boolean showAsList = false;
        
        private RecyclerView gridView;
        private ShareAdapter adapter;
        private EditText searchEditText;
        private ImageView sendButton;
        private FrameLayout sendButtonContainer;
        private TextView sendBadge;
        private GridLayoutManager gridLayoutManager;
        private androidx.recyclerview.widget.LinearLayoutManager listLayoutManager;
        
        public interface ShareAlertCallback {
            void onShareSelected(ArrayList<Long> dids, boolean showQuote, boolean keepCaption, boolean sendAsAlbum, boolean removeLinkPreview);
        }
        
        public SpecialForwardShareAlert(Context context, int account, ArrayList<MessageObject> messages, boolean asFile, ShareAlertCallback callback) {
            super(context, true);
            this.currentAccount = account;
            this.messages = messages;
            this.forwardAsFile = asFile;
            this.callback = callback;

            org.telegram.ui.DialogsActivity.loadDialogs(org.telegram.messenger.AccountInstance.getInstance(currentAccount));
            org.telegram.messenger.MessagesController.getInstance(currentAccount).loadDialogs(0, 0, 200, true);
            org.telegram.messenger.MessagesController.getInstance(currentAccount).loadDialogs(1, 0, 200, true);
            org.telegram.messenger.NotificationCenter.getInstance(currentAccount).addObserver(this, org.telegram.messenger.NotificationCenter.dialogsNeedReload);

            // Initialize standard tabs
            CategoryTab tabAll = new CategoryTab();
            tabAll.id = 0;
            tabAll.title = LocaleController.getString("FilterAllChatsShort", R.string.FilterAllChatsShort);
            if (TextUtils.isEmpty(tabAll.title)) tabAll.title = "All";
            tabAll.iconRes = R.drawable.filter_all_solar;
            tabs.add(tabAll);

            CategoryTab tabFav = new CategoryTab();
            tabFav.id = -1;
            tabFav.title = LocaleController.getString("ArchiveHintHeader3", R.string.ArchiveHintHeader3);
            if (TextUtils.isEmpty(tabFav.title)) tabFav.title = "Pinned";
            tabFav.iconRes = R.drawable.filter_favorite_solar;
            tabs.add(tabFav);

            CategoryTab tabPm = new CategoryTab();
            tabPm.id = -2;
            tabPm.title = LocaleController.getString("TabsByTypePersonal", R.string.TabsByTypePersonal);
            if (TextUtils.isEmpty(tabPm.title)) tabPm.title = "Personal";
            tabPm.iconRes = R.drawable.filter_private_solar;
            tabs.add(tabPm);

            CategoryTab tabContacts = new CategoryTab();
            tabContacts.id = -3;
            tabContacts.title = LocaleController.getString("FilterContacts", R.string.FilterContacts);
            if (TextUtils.isEmpty(tabContacts.title)) tabContacts.title = "Contacts";
            tabContacts.iconRes = R.drawable.msg_contacts_solar;
            tabs.add(tabContacts);

            CategoryTab tabGroup = new CategoryTab();
            tabGroup.id = -4;
            tabGroup.title = LocaleController.getString("FilterGroups", R.string.FilterGroups);
            if (TextUtils.isEmpty(tabGroup.title)) tabGroup.title = "Groups";
            tabGroup.iconRes = R.drawable.filter_groups_solar;
            tabs.add(tabGroup);

            CategoryTab tabChannel = new CategoryTab();
            tabChannel.id = -5;
            tabChannel.title = LocaleController.getString("FilterChannels", R.string.FilterChannels);
            if (TextUtils.isEmpty(tabChannel.title)) tabChannel.title = "Channels";
            tabChannel.iconRes = R.drawable.filter_channel_solar;
            tabs.add(tabChannel);

            CategoryTab tabBot = new CategoryTab();
            tabBot.id = -6;
            tabBot.title = LocaleController.getString("FilterBots", R.string.FilterBots);
            if (TextUtils.isEmpty(tabBot.title)) tabBot.title = "Bots";
            tabBot.iconRes = R.drawable.filter_bots_solar;
            tabs.add(tabBot);

            // Add custom folders
            ArrayList<org.telegram.messenger.MessagesController.DialogFilter> dialogFilters = org.telegram.messenger.MessagesController.getInstance(currentAccount).getDialogFilters();
            if (dialogFilters != null) {
                for (int i = 0; i < dialogFilters.size(); i++) {
                    org.telegram.messenger.MessagesController.DialogFilter filter = dialogFilters.get(i);
                    if (filter == null || filter.id == 0 || filter.isDefault()) {
                        continue;
                    }
                    CategoryTab tabFilter = new CategoryTab();
                    tabFilter.id = filter.id;
                    tabFilter.title = filter.name;
                    tabFilter.emoticon = filter.emoticon;
                    tabFilter.filter = filter;
                    if (tw.nekomimi.nekogram.tabs.TabsByTypeManager.isVirtualFilter(filter)) {
                        tw.nekomimi.nekogram.tabs.TabsByTypeEntry entry = tw.nekomimi.nekogram.tabs.TabsByTypeManager.getTabFromFilter(filter);
                        tabFilter.iconRes = entry != null ? entry.iconResId : R.drawable.msg_folders;
                    } else {
                        tabFilter.iconRes = R.drawable.msg_folders;
                    }
                    tabs.add(tabFilter);
                }
            }
            
            searchAdapterHelper = new org.telegram.ui.Adapters.SearchAdapterHelper(false);
            searchAdapterHelper.setDelegate(new org.telegram.ui.Adapters.SearchAdapterHelper.SearchAdapterHelperDelegate() {
                @Override
                public void onDataSetChanged(int searchId) {
                    if (searchId == lastSearchId) {
                        filterDialogs();
                    }
                }

                @Override
                public boolean canApplySearchResults(int searchId) {
                    return searchId == lastSearchId;
                }
            });

            allDialogs = new ArrayList<>(org.telegram.messenger.MessagesController.getInstance(currentAccount).getAllDialogs());
            filterDialogs();
            
            LinearLayout rootLayout = new LinearLayout(context);
            rootLayout.setOrientation(LinearLayout.VERTICAL);
            rootLayout.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(12));
            
            // 1. Bottom Sheet Top Slide Handle
            View handleView = new View(context);
            android.graphics.drawable.GradientDrawable handleBg = new android.graphics.drawable.GradientDrawable();
            int headerColor = Theme.getColor(Theme.key_divider);
            handleBg.setColor(headerColor != 0 ? headerColor : 0x22888888);
            handleBg.setCornerRadius(AndroidUtilities.dp(2));
            handleView.setBackground(handleBg);
            LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(AndroidUtilities.dp(36), AndroidUtilities.dp(4));
            handleLp.gravity = Gravity.CENTER_HORIZONTAL;
            handleLp.bottomMargin = AndroidUtilities.dp(8);
            rootLayout.addView(handleView, handleLp);
            
            // 2. Search Box and Switches Top Row
            LinearLayout topRow = new LinearLayout(context);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            
            LinearLayout searchContainer = new LinearLayout(context);
            searchContainer.setOrientation(LinearLayout.HORIZONTAL);
            searchContainer.setGravity(Gravity.CENTER_VERTICAL);
            searchContainer.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), Theme.getColor(Theme.key_dialogBackgroundGray)));
            searchContainer.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
            
            ImageView searchIcon = new ImageView(context);
            searchIcon.setImageResource(R.drawable.baseline_search_24);
            searchIcon.setColorFilter(new android.graphics.PorterDuffColorFilter(0xff888888, android.graphics.PorterDuff.Mode.SRC_IN));
            searchContainer.addView(searchIcon, LayoutHelper.createLinear(18, 18, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));
            
            searchEditText = new EditText(context);
            searchEditText.setHint("Send to...");
            searchEditText.setHintTextColor(0x88888888);
            searchEditText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            searchEditText.setTextSize(14);
            searchEditText.setBackground(null);
            searchEditText.setSingleLine(true);
            searchEditText.setInputType(InputType.TYPE_CLASS_TEXT);
            searchEditText.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                public void afterTextChanged(android.text.Editable s) {
                    searchQuery = s.toString().trim().toLowerCase();
                    if (!TextUtils.isEmpty(searchQuery)) {
                        lastSearchId++;
                        searchAdapterHelper.queryServerSearch(searchQuery, true, true, true, true, false, 0, false, 0, lastSearchId);
                    } else {
                        searchAdapterHelper.queryServerSearch(null, true, true, true, true, false, 0, false, 0, 0);
                    }
                    filterDialogs();
                }
            });
            searchContainer.addView(searchEditText, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f));
            
            topRow.addView(searchContainer, LayoutHelper.createLinear(0, 36, 1.0f, Gravity.CENTER_VERTICAL, 4, 0, 4, 0));
            
            // QUOTE Switch
            LinearLayout quoteLayout = new LinearLayout(context);
            quoteLayout.setOrientation(LinearLayout.VERTICAL);
            quoteLayout.setGravity(Gravity.CENTER);
            
            TextView quoteText = new TextView(context);
            quoteText.setText("QUOTE");
            quoteText.setTextSize(9);
            quoteText.setTextColor(Theme.getColor(Theme.key_dialogTextGray));
            quoteText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            
            Switch quoteSwitch = new Switch(context);
            quoteSwitch.setChecked(showQuote, false);
            quoteSwitch.setOnCheckedChangeListener((view, checked) -> showQuote = checked);
            
            quoteLayout.addView(quoteText);
            quoteLayout.addView(quoteSwitch, LayoutHelper.createLinear(37, 20, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));
            topRow.addView(quoteLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 4, 0, 4, 0));
            
            // CAP Switch
            LinearLayout capLayout = new LinearLayout(context);
            capLayout.setOrientation(LinearLayout.VERTICAL);
            capLayout.setGravity(Gravity.CENTER);
            
            TextView capText = new TextView(context);
            capText.setText("CAP");
            capText.setTextSize(9);
            capText.setTextColor(Theme.getColor(Theme.key_dialogTextGray));
            capText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            
            Switch capSwitch = new Switch(context);
            capSwitch.setChecked(keepCaption, false);
            capSwitch.setOnCheckedChangeListener((view, checked) -> keepCaption = checked);
            
            capLayout.addView(capText);
            capLayout.addView(capSwitch, LayoutHelper.createLinear(37, 20, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));
            topRow.addView(capLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 4, 0, 4, 0));
            
            ImageView threeDots = new ImageView(context);
            threeDots.setImageResource(R.drawable.ic_ab_other);
            threeDots.setColorFilter(new android.graphics.PorterDuffColorFilter(Theme.getColor(Theme.key_dialogIcon), android.graphics.PorterDuff.Mode.SRC_IN));
            threeDots.setBackground(Theme.getSelectorDrawable(false));
            threeDots.setOnClickListener(v -> showOptionsMenu(threeDots));
            topRow.addView(threeDots, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL, 4, 0, 4, 0));
            
            rootLayout.addView(topRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
            
            // 3. Category tabs
            HorizontalScrollView scrollCategories = new HorizontalScrollView(context);
            scrollCategories.setHorizontalScrollBarEnabled(false);
            
            LinearLayout categoriesRow = new LinearLayout(context);
            categoriesRow.setOrientation(LinearLayout.HORIZONTAL);
            categoriesRow.setGravity(Gravity.CENTER_VERTICAL);
            
            int selectedColor = Theme.getColor(Theme.key_dialogTextBlue);
            int unselectedColor = Theme.getColor(Theme.key_dialogIcon);
            int selectedBgColor = (selectedColor & 0x00ffffff) | 0x1a000000;
            
            final View[] categoryTabs = new View[tabs.size()];
            for (int i = 0; i < tabs.size(); i++) {
                final int idx = i;
                final CategoryTab tab = tabs.get(idx);
                
                LinearLayout pillLayout = new LinearLayout(context);
                pillLayout.setOrientation(LinearLayout.HORIZONTAL);
                pillLayout.setGravity(Gravity.CENTER_VERTICAL);
                pillLayout.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
                
                // Emoticon or Icon
                int tabTitleType = tw.nekomimi.nekogram.NekoConfig.tabsTitleType.Int();
                boolean showIcon = tabTitleType != tw.nekomimi.nekogram.NekoXConfig.TITLE_TYPE_TEXT;
                boolean showText = tabTitleType != tw.nekomimi.nekogram.NekoXConfig.TITLE_TYPE_ICON;

                if (showIcon) {
                    if (!TextUtils.isEmpty(tab.emoticon)) {
                        TextView emojiView = new TextView(context);
                        emojiView.setText(tab.emoticon);
                        emojiView.setTextSize(14);
                        pillLayout.addView(emojiView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, showText ? 6 : 0, 0));
                    } else if (tab.iconRes != 0) {
                        ImageView tabIcon = new ImageView(context);
                        tabIcon.setImageResource(tab.iconRes);
                        tabIcon.setColorFilter(new android.graphics.PorterDuffColorFilter(idx == activeCategoryIndex ? selectedColor : unselectedColor, android.graphics.PorterDuff.Mode.SRC_IN));
                        pillLayout.addView(tabIcon, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL, 0, 0, showText ? 6 : 0, 0));
                    }
                }
                
                if (showText) {
                    TextView titleView = new TextView(context);
                    titleView.setText(tab.title);
                    titleView.setTextSize(13);
                    titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    titleView.setTextColor(idx == activeCategoryIndex ? selectedColor : unselectedColor);
                    pillLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
                }
                
                android.graphics.drawable.GradientDrawable tabBg = new android.graphics.drawable.GradientDrawable();
                tabBg.setCornerRadius(AndroidUtilities.dp(16));
                tabBg.setColor(idx == activeCategoryIndex ? selectedBgColor : Color.TRANSPARENT);
                pillLayout.setBackground(tabBg);
                
                pillLayout.setOnClickListener(v -> {
                    activeCategoryIndex = idx;
                    for (int j = 0; j < categoryTabs.length; j++) {
                        LinearLayout sibling = (LinearLayout) categoryTabs[j];
                        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                        bg.setCornerRadius(AndroidUtilities.dp(16));
                        bg.setColor(j == activeCategoryIndex ? selectedBgColor : Color.TRANSPARENT);
                        sibling.setBackground(bg);
                        
                        int siblingColor = (j == activeCategoryIndex ? selectedColor : unselectedColor);
                        for (int k = 0; k < sibling.getChildCount(); k++) {
                            View child = sibling.getChildAt(k);
                            if (child instanceof ImageView) {
                                ((ImageView) child).setColorFilter(new android.graphics.PorterDuffColorFilter(siblingColor, android.graphics.PorterDuff.Mode.SRC_IN));
                            } else if (child instanceof TextView) {
                                TextView tv = (TextView) child;
                                if (tv.getText().toString().equals(tabs.get(j).emoticon)) {
                                    continue;
                                }
                                tv.setTextColor(siblingColor);
                            }
                        }
                    }
                    filterDialogs();
                });
                
                categoryTabs[i] = pillLayout;
                categoriesRow.addView(pillLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32, 0, 4, 0, 4, 0));
            }
            scrollCategories.addView(categoriesRow);
            rootLayout.addView(scrollCategories, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
            
            // 4. Grid view and floating submit send button
            gridLayoutManager = new GridLayoutManager(context, 4);
            listLayoutManager = new androidx.recyclerview.widget.LinearLayoutManager(context);
            gridView = new RecyclerView(context);
            gridView.setLayoutManager(gridLayoutManager);
            adapter = new ShareAdapter(context);
            gridView.setAdapter(adapter);
            rootLayout.addView(gridView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 260));
            
            FrameLayout frameLayout = new FrameLayout(context);
            containerView = frameLayout;
            // Solid opaque background so the sheet isn't transparent
            android.graphics.drawable.GradientDrawable sheetBg = new android.graphics.drawable.GradientDrawable();
            sheetBg.setColor(Theme.getColor(Theme.key_dialogBackground));
            sheetBg.setCornerRadii(new float[]{AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), 0, 0, 0, 0});
            frameLayout.setBackground(sheetBg);
            
            frameLayout.addView(rootLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            
            sendButtonContainer = new FrameLayout(context);
            sendButtonContainer.setVisibility(View.GONE);
            
            sendButton = new ImageView(context);
            sendButton.setImageResource(R.drawable.baseline_forward_24);
            sendButton.setColorFilter(new android.graphics.PorterDuffColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN));
            
            android.graphics.drawable.GradientDrawable btnBg = new android.graphics.drawable.GradientDrawable();
            btnBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            btnBg.setColor(0xff2196f3);
            sendButton.setBackground(btnBg);
            sendButton.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
            sendButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
            sendButton.setOnClickListener(v -> {
                if (selectedDialogs.size() > 0) {
                    dismiss();
                    ArrayList<Long> dids = new ArrayList<>();
                    for (int i = 0; i < selectedDialogs.size(); i++) {
                        dids.add(selectedDialogs.keyAt(i));
                    }
                    callback.onShareSelected(dids, showQuote, keepCaption, sendAsAlbum, removeLinkPreview);
                }
            });
            sendButtonContainer.addView(sendButton, LayoutHelper.createFrame(48, 48));
            
            sendBadge = new TextView(context);
            sendBadge.setTextColor(Color.WHITE);
            sendBadge.setTextSize(9);
            sendBadge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            sendBadge.setGravity(Gravity.CENTER);
            
            android.graphics.drawable.GradientDrawable badgeBg = new android.graphics.drawable.GradientDrawable();
            badgeBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            badgeBg.setColor(0xff2196f3);
            badgeBg.setStroke(AndroidUtilities.dp(1.2f), 0xffffffff);
            sendBadge.setBackground(badgeBg);
            
            FrameLayout.LayoutParams lpBadge = new FrameLayout.LayoutParams(
                AndroidUtilities.dp(16), 
                AndroidUtilities.dp(16), 
                Gravity.RIGHT | Gravity.TOP
            );
            sendButtonContainer.addView(sendBadge, lpBadge);
            
            frameLayout.addView(sendButtonContainer, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 16, 16));
        }
        
        private void showOptionsMenu(View anchorView) {
            boolean selectAllOn = selectedDialogs.size() == filteredDialogs.size() && !filteredDialogs.isEmpty();
            ItemOptions.makeOptions(container, anchorView)
                .add(selectAllOn ? R.drawable.baseline_check_box_24 : R.drawable.baseline_check_box_outline_blank_24, "Select All", () -> {
                    if (!selectAllOn) {
                        for (TLRPC.Dialog d : filteredDialogs) selectedDialogs.put(d.id, d);
                    } else {
                        selectedDialogs.clear();
                    }
                    if (adapter != null) adapter.notifyDataSetChanged();
                    sendButtonContainer.setVisibility(selectedDialogs.size() > 0 ? View.VISIBLE : View.GONE);
                    if (selectedDialogs.size() > 0) sendBadge.setText(String.valueOf(selectedDialogs.size()));
                })
                .add(sendAsAlbum ? R.drawable.baseline_check_box_24 : R.drawable.baseline_check_box_outline_blank_24, "Send as album", () -> {
                    sendAsAlbum = !sendAsAlbum;
                })
                .add(removeLinkPreview ? R.drawable.baseline_check_box_24 : R.drawable.baseline_check_box_outline_blank_24, "Remove link preview", () -> {
                    removeLinkPreview = !removeLinkPreview;
                })
                .add(R.drawable.ic_filter_list, "Show as list", () -> {
                    showAsList = !showAsList;
                    gridView.setLayoutManager(showAsList ? listLayoutManager : gridLayoutManager);
                    if (adapter != null) adapter.notifyDataSetChanged();
                })
                .show();
        }

        private long getDialogDate(TLRPC.Dialog dialog) {
            if (dialog == null) return 0;
            TLRPC.DraftMessage draftMessage = MediaDataController.getInstance(currentAccount).getDraft(dialog.id, 0);
            return DialogObject.getLastMessageOrDraftDate(dialog, draftMessage);
        }

        private void filterDialogs() {
            filteredDialogs.clear();
            if (tabs.isEmpty()) {
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
                return;
            }
            CategoryTab activeTab = tabs.get(activeCategoryIndex);
            
            // If Contacts category is active and search query is empty, show all contacts
            if (activeTab.id == -3 && TextUtils.isEmpty(searchQuery)) {
                ArrayList<TLRPC.TL_contact> contactsList = org.telegram.messenger.ContactsController.getInstance(currentAccount).contacts;
                for (TLRPC.TL_contact contact : contactsList) {
                    TLRPC.Dialog fakeDialog = new TLRPC.TL_dialog();
                    fakeDialog.id = contact.user_id;
                    filteredDialogs.add(fakeDialog);
                }
                java.util.Collections.sort(filteredDialogs, (dialog1, dialog2) -> {
                    TLRPC.User u1 = org.telegram.messenger.MessagesController.getInstance(currentAccount).getUser(dialog1.id);
                    TLRPC.User u2 = org.telegram.messenger.MessagesController.getInstance(currentAccount).getUser(dialog2.id);
                    String name1 = u1 != null ? org.telegram.messenger.ContactsController.formatName(u1.first_name, u1.last_name) : "";
                    String name2 = u2 != null ? org.telegram.messenger.ContactsController.formatName(u2.first_name, u2.last_name) : "";
                    return name1.compareToIgnoreCase(name2);
                });
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
                return;
            }
            
            ArrayList<TLRPC.Dialog> dialogList = new ArrayList<>();
            org.telegram.messenger.MessagesController messagesController = org.telegram.messenger.MessagesController.getInstance(currentAccount);
            for (int a = 0; a < messagesController.dialogs_dict.size(); a++) {
                TLRPC.Dialog d = messagesController.dialogs_dict.valueAt(a);
                if (d != null) {
                    dialogList.add(d);
                }
            }

            for (TLRPC.Dialog dialog : dialogList) {
                long dialogId = dialog.id;
                
                if (!TextUtils.isEmpty(searchQuery)) {
                    String title = "";
                    if (DialogObject.isUserDialog(dialogId)) {
                        TLRPC.User user = org.telegram.messenger.MessagesController.getInstance(currentAccount).getUser(dialogId);
                        if (user != null) {
                            title = (user.first_name + " " + user.last_name).toLowerCase();
                        }
                    } else {
                        TLRPC.Chat chat = org.telegram.messenger.MessagesController.getInstance(currentAccount).getChat(-dialogId);
                        if (chat != null) {
                            title = chat.title.toLowerCase();
                        }
                    }
                    if (!title.contains(searchQuery)) {
                        continue;
                    }
                }
                
                if (activeTab.id == -1) { // Pinned
                    if (!dialog.pinned) continue;
                } else if (activeTab.id == -2) { // Personal (PMs)
                    if (!DialogObject.isUserDialog(dialogId)) continue;
                    TLRPC.User user = org.telegram.messenger.MessagesController.getInstance(currentAccount).getUser(dialogId);
                    if (user == null || user.bot) continue;
                } else if (activeTab.id == -3) { // Contacts (with search query)
                    if (!DialogObject.isUserDialog(dialogId)) continue;
                    if (!org.telegram.messenger.ContactsController.getInstance(currentAccount).isContact(dialogId)) continue;
                } else if (activeTab.id == -4) { // Groups (combined: chat & megagroup)
                    if (!DialogObject.isChatDialog(dialogId)) continue;
                    TLRPC.Chat chat = org.telegram.messenger.MessagesController.getInstance(currentAccount).getChat(-dialogId);
                    if (chat == null || (ChatObject.isChannel(chat) && !chat.megagroup)) continue;
                } else if (activeTab.id == -5) { // Channels
                    if (!DialogObject.isChatDialog(dialogId)) continue;
                    TLRPC.Chat chat = org.telegram.messenger.MessagesController.getInstance(currentAccount).getChat(-dialogId);
                    if (chat == null || !ChatObject.isChannel(chat) || chat.megagroup) continue;
                } else if (activeTab.id == -6) { // Bots
                    if (!DialogObject.isUserDialog(dialogId)) continue;
                    TLRPC.User user = org.telegram.messenger.MessagesController.getInstance(currentAccount).getUser(dialogId);
                    if (user == null || !user.bot) continue;
                } else if ((activeTab.id > 0 || tw.nekomimi.nekogram.tabs.TabsByTypeManager.isVirtualFilter(activeTab.filter)) && activeTab.filter != null) { // Custom folder & Tabs by type
                    if (!activeTab.filter.includesDialog(org.telegram.messenger.AccountInstance.getInstance(currentAccount), dialogId, dialog)) continue;
                }
                
                filteredDialogs.add(dialog);
            }

            // Add explicitly folder-included chats (alwaysShow & pinnedDialogs) that might not be in dialogs_dict/allDialogs
            if ((activeTab.id > 0 || tw.nekomimi.nekogram.tabs.TabsByTypeManager.isVirtualFilter(activeTab.filter)) && activeTab.filter != null) {
                ArrayList<Long> folderIds = new ArrayList<>(activeTab.filter.alwaysShow);
                for (int a = 0; a < activeTab.filter.pinnedDialogs.size(); a++) {
                    long did = activeTab.filter.pinnedDialogs.keyAt(a);
                    if (!folderIds.contains(did)) {
                        folderIds.add(did);
                    }
                }
                
                for (int a = 0; a < folderIds.size(); a++) {
                    long did = folderIds.get(a);
                    
                    if (!TextUtils.isEmpty(searchQuery)) {
                        String title = "";
                        if (DialogObject.isUserDialog(did)) {
                            TLRPC.User user = messagesController.getUser(did);
                            if (user != null) {
                                title = (user.first_name + " " + user.last_name).toLowerCase();
                            }
                        } else {
                            TLRPC.Chat chat = messagesController.getChat(-did);
                            if (chat != null) {
                                title = chat.title.toLowerCase();
                            }
                        }
                        if (!title.contains(searchQuery)) {
                            continue;
                        }
                    }
                    
                    boolean exists = false;
                    for (TLRPC.Dialog d : filteredDialogs) {
                        if (d.id == did) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        TLRPC.Dialog fakeDialog = messagesController.dialogs_dict.get(did);
                        if (fakeDialog == null) {
                            fakeDialog = new TLRPC.TL_dialog();
                            fakeDialog.id = did;
                        }
                        filteredDialogs.add(fakeDialog);
                    }
                }
            }
            
            // Also search in contacts if searching
            if (!TextUtils.isEmpty(searchQuery)) {
                ArrayList<TLRPC.TL_contact> contactsList = org.telegram.messenger.ContactsController.getInstance(currentAccount).contacts;
                for (TLRPC.TL_contact contact : contactsList) {
                    TLRPC.User user = org.telegram.messenger.MessagesController.getInstance(currentAccount).getUser(contact.user_id);
                    if (user != null) {
                        String title = (user.first_name + " " + user.last_name).toLowerCase();
                        if (title.contains(searchQuery)) {
                            // Check if already in filteredDialogs
                            boolean exists = false;
                            for (TLRPC.Dialog d : filteredDialogs) {
                                    if (d.id == user.id) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                TLRPC.Dialog fakeDialog = new TLRPC.TL_dialog();
                                fakeDialog.id = user.id;
                                filteredDialogs.add(fakeDialog);
                            }
                        }
                    }
                }

                ArrayList<org.telegram.tgnet.TLObject> localServerResults = searchAdapterHelper.getLocalServerSearch();
                for (int a = 0; a < localServerResults.size(); a++) {
                    org.telegram.tgnet.TLObject obj = localServerResults.get(a);
                    long id = 0;
                    if (obj instanceof TLRPC.User) {
                        id = ((TLRPC.User) obj).id;
                    } else if (obj instanceof TLRPC.Chat) {
                        id = -((TLRPC.Chat) obj).id;
                    }
                    if (id != 0) {
                        if (activeTab.id == -1) {
                            continue;
                        } else if (activeTab.id == -2) {
                            if (!DialogObject.isUserDialog(id)) continue;
                            TLRPC.User user = org.telegram.messenger.MessagesController.getInstance(currentAccount).getUser(id);
                            if (user == null || user.bot) continue;
                        } else if (activeTab.id == -3) {
                            if (!DialogObject.isUserDialog(id)) continue;
                            if (!org.telegram.messenger.ContactsController.getInstance(currentAccount).isContact(id)) continue;
                        } else if (activeTab.id == -4) {
                            if (!DialogObject.isChatDialog(id)) continue;
                            TLRPC.Chat chat = org.telegram.messenger.MessagesController.getInstance(currentAccount).getChat(-id);
                            if (chat == null || (ChatObject.isChannel(chat) && !chat.megagroup)) continue;
                        } else if (activeTab.id == -5) {
                            if (!DialogObject.isChatDialog(id)) continue;
                            TLRPC.Chat chat = org.telegram.messenger.MessagesController.getInstance(currentAccount).getChat(-id);
                            if (chat == null || !ChatObject.isChannel(chat) || chat.megagroup) continue;
                        } else if (activeTab.id == -6) {
                            if (!DialogObject.isUserDialog(id)) continue;
                            TLRPC.User user = org.telegram.messenger.MessagesController.getInstance(currentAccount).getUser(id);
                            if (user == null || !user.bot) continue;
                        } else if ((activeTab.id > 0 || tw.nekomimi.nekogram.tabs.TabsByTypeManager.isVirtualFilter(activeTab.filter)) && activeTab.filter != null) {
                            TLRPC.Dialog tempDialog = new TLRPC.TL_dialog();
                            tempDialog.id = id;
                            if (!activeTab.filter.includesDialog(org.telegram.messenger.AccountInstance.getInstance(currentAccount), id, tempDialog)) continue;
                        }

                        boolean exists = false;
                        for (int b = 0; b < filteredDialogs.size(); b++) {
                            if (filteredDialogs.get(b).id == id) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            TLRPC.Dialog fakeDialog = new TLRPC.TL_dialog();
                            fakeDialog.id = id;
                            filteredDialogs.add(fakeDialog);
                        }
                    }
                }

                ArrayList<org.telegram.tgnet.TLObject> globalResults = searchAdapterHelper.getGlobalSearch();
                for (int a = 0; a < globalResults.size(); a++) {
                    org.telegram.tgnet.TLObject obj = globalResults.get(a);
                    long id = 0;
                    if (obj instanceof TLRPC.User) {
                        id = ((TLRPC.User) obj).id;
                    } else if (obj instanceof TLRPC.Chat) {
                        id = -((TLRPC.Chat) obj).id;
                    }
                    if (id != 0) {
                        if (activeTab.id == -1) {
                            continue;
                        } else if (activeTab.id == -2) {
                            if (!DialogObject.isUserDialog(id)) continue;
                            TLRPC.User user = org.telegram.messenger.MessagesController.getInstance(currentAccount).getUser(id);
                            if (user == null || user.bot) continue;
                        } else if (activeTab.id == -3) {
                            if (!DialogObject.isUserDialog(id)) continue;
                            if (!org.telegram.messenger.ContactsController.getInstance(currentAccount).isContact(id)) continue;
                        } else if (activeTab.id == -4) {
                            if (!DialogObject.isChatDialog(id)) continue;
                            TLRPC.Chat chat = org.telegram.messenger.MessagesController.getInstance(currentAccount).getChat(-id);
                            if (chat == null || (ChatObject.isChannel(chat) && !chat.megagroup)) continue;
                        } else if (activeTab.id == -5) {
                            if (!DialogObject.isChatDialog(id)) continue;
                            TLRPC.Chat chat = org.telegram.messenger.MessagesController.getInstance(currentAccount).getChat(-id);
                            if (chat == null || !ChatObject.isChannel(chat) || chat.megagroup) continue;
                        } else if (activeTab.id == -6) {
                            if (!DialogObject.isUserDialog(id)) continue;
                            TLRPC.User user = org.telegram.messenger.MessagesController.getInstance(currentAccount).getUser(id);
                            if (user == null || !user.bot) continue;
                        } else if ((activeTab.id > 0 || tw.nekomimi.nekogram.tabs.TabsByTypeManager.isVirtualFilter(activeTab.filter)) && activeTab.filter != null) {
                            TLRPC.Dialog tempDialog = new TLRPC.TL_dialog();
                            tempDialog.id = id;
                            if (!activeTab.filter.includesDialog(org.telegram.messenger.AccountInstance.getInstance(currentAccount), id, tempDialog)) continue;
                        }

                        boolean exists = false;
                        for (int b = 0; b < filteredDialogs.size(); b++) {
                            if (filteredDialogs.get(b).id == id) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            TLRPC.Dialog fakeDialog = new TLRPC.TL_dialog();
                            fakeDialog.id = id;
                            filteredDialogs.add(fakeDialog);
                        }
                    }
                }
            }

            // Auto sort dialogs: pinned first, then by last message/draft date descending
            if (activeTab.id != -3) { // skip sorting contacts which are already sorted alphabetically
                java.util.Collections.sort(filteredDialogs, (dialog1, dialog2) -> {
                    boolean pinned1 = dialog1.pinned;
                    boolean pinned2 = dialog2.pinned;
                    
                    if (activeTab.filter != null) {
                        pinned1 = activeTab.filter.pinnedDialogs.indexOfKey(dialog1.id) >= 0;
                        pinned2 = activeTab.filter.pinnedDialogs.indexOfKey(dialog2.id) >= 0;
                    }
                    
                    if (pinned1 != pinned2) {
                        return pinned1 ? -1 : 1;
                    } else if (pinned1) {
                        if (activeTab.filter != null) {
                            int index1 = activeTab.filter.pinnedDialogs.get(dialog1.id);
                            int index2 = activeTab.filter.pinnedDialogs.get(dialog2.id);
                            return Integer.compare(index1, index2);
                        }
                        return Integer.compare(dialog2.pinnedNum, dialog1.pinnedNum);
                    }
                    
                    long date1 = getDialogDate(dialog1);
                    long date2 = getDialogDate(dialog2);
                    return Long.compare(date2, date1);
                });
            }
            
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
        
        private class ShareAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
            private final Context context;
            
            public ShareAdapter(Context context) {
                this.context = context;
            }
            
            @Override
            public int getItemCount() {
                return filteredDialogs.size();
            }
            
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                org.telegram.ui.Cells.ShareDialogCell cell = new org.telegram.ui.Cells.ShareDialogCell(context, org.telegram.ui.Cells.ShareDialogCell.TYPE_SHARE, null);
                cell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(92)));
                return new Holder(cell);
            }
            
            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                org.telegram.ui.Cells.ShareDialogCell cell = (org.telegram.ui.Cells.ShareDialogCell) holder.itemView;
                TLRPC.Dialog dialog = filteredDialogs.get(position);
                boolean isSelected = selectedDialogs.indexOfKey(dialog.id) >= 0;
                
                cell.setDialog(dialog.id, isSelected, null);
                
                // Reposition the CheckBox2 view to the top-left of the avatar
                for (int i = 0; i < cell.getChildCount(); i++) {
                    View child = cell.getChildAt(i);
                    if (child instanceof org.telegram.ui.Components.CheckBox2) {
                        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) child.getLayoutParams();
                        lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
                        lp.leftMargin = -AndroidUtilities.dp(20);
                        lp.rightMargin = 0;
                        lp.topMargin = AndroidUtilities.dp(2);
                        child.setLayoutParams(lp);
                        break;
                    }
                }
                
                cell.setOnClickListener(v -> {
                    if (selectedDialogs.indexOfKey(dialog.id) >= 0) {
                        selectedDialogs.remove(dialog.id);
                    } else {
                        selectedDialogs.put(dialog.id, dialog);
                    }
                    cell.setChecked(selectedDialogs.indexOfKey(dialog.id) >= 0, true);
                    
                    if (selectedDialogs.size() > 0) {
                        sendButtonContainer.setVisibility(View.VISIBLE);
                        sendBadge.setText(String.valueOf(selectedDialogs.size()));
                    } else {
                        sendButtonContainer.setVisibility(View.GONE);
                    }
                });
            }
        }
        
        private static class Holder extends RecyclerView.ViewHolder {
            public Holder(View itemView) {
                super(itemView);
            }
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == org.telegram.messenger.NotificationCenter.dialogsNeedReload) {
                allDialogs = new ArrayList<>(org.telegram.messenger.MessagesController.getInstance(currentAccount).getAllDialogs());
                filterDialogs();
            }
        }

        @Override
        public void dismiss() {
            super.dismiss();
            org.telegram.messenger.NotificationCenter.getInstance(currentAccount).removeObserver(this, org.telegram.messenger.NotificationCenter.dialogsNeedReload);
            if (searchAdapterHelper != null) {
                searchAdapterHelper.queryServerSearch(null, true, true, true, true, false, 0, false, 0, 0);
            }
        }
    }
}
// [Alexgram: Special Forward] - End
