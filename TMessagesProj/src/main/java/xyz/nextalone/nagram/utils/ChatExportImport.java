package xyz.nextalone.nagram.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class ChatExportImport {

    public static void exportChat(Context context, ArrayList<MessageObject> messages, String title) {
        if (messages == null || messages.isEmpty()) {
            Toast.makeText(context, "No messages to export", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONArray jsonArray = new JSONArray();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

            for (MessageObject msg : messages) {
                if (msg == null) continue;
                JSONObject obj = new JSONObject();
                obj.put("id", msg.getId());
                obj.put("date", sdf.format(new Date(msg.messageOwner.date * 1000L)));
                
                String senderName = "Unknown";
                long senderId = msg.getSenderId();
                if (senderId > 0) {
                    org.telegram.tgnet.TLRPC.User user = org.telegram.messenger.MessagesController.getInstance(org.telegram.messenger.UserConfig.selectedAccount).getUser(senderId);
                    if (user != null) senderName = org.telegram.messenger.UserObject.getUserName(user);
                } else {
                    org.telegram.tgnet.TLRPC.Chat chat = org.telegram.messenger.MessagesController.getInstance(org.telegram.messenger.UserConfig.selectedAccount).getChat(-senderId);
                    if (chat != null) senderName = chat.title;
                }
                
                obj.put("sender", senderName);
                obj.put("message", msg.messageOwner.message);
                if (msg.isReply()) {
                    obj.put("reply_to_msg_id", msg.getReplyMsgId());
                }
                jsonArray.put(obj);
            }

            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AlexgramExports");
            if (!dir.exists()) dir.mkdirs();

            String fileName = "ChatExport_" + title.replaceAll("[^a-zA-Z0-9]", "_") + "_" + System.currentTimeMillis() + ".json";
            File file = new File(dir, fileName);

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(jsonArray.toString(4).getBytes());
            fos.close();

            Toast.makeText(context, "Exported to " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();

            // Share file
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/json");
            shareIntent.putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file));
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(shareIntent, "Share Export"));

        } catch (Exception e) {
            FileLog.e(e);
            Toast.makeText(context, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static void importChat(BaseFragment fragment, int reqCode) {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            // Some file managers might not handle application/json correctly
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            fragment.startActivityForResult(Intent.createChooser(intent, "Import Chat JSON"), reqCode);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void handleImportResult(Context context, long dialogId, Uri uri) {
        if (uri == null) return;
        try {
            InputStream is = context.getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            is.close();

            JSONArray jsonArray = new JSONArray(sb.toString());
            int count = 0;
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                String text = obj.optString("message");
                if (text != null && !text.isEmpty()) {
                    // Send as new message
                    // Note: This sends messages from YOU to the chat.
                    // "God level" might imply restoring history, but we can't forge timestamps easily without server API.
                    // Doing a "Quote" style import could work but format is tricky.
                    // For now, let's just send the text content to current chat.
                    SendMessagesHelper.getInstance(UserConfig.selectedAccount).sendMessage(text, dialogId, null, null, null, true, null, null, null, true, 0, 0, null, false);
                    count++;
                }
            }

            Toast.makeText(context, "Imported " + count + " messages.", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            FileLog.e(e);
            Toast.makeText(context, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
