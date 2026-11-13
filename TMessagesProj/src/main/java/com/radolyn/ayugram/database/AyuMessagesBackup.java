/*
 * This is the source code of AyuGram for Android.
 *
 * We do not and cannot prevent the use of our code,
 * but be respectful and credit the original author.
 *
 * Copyright @Radolyn, 2023
 */

package com.radolyn.ayugram.database;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.JsonWriter;

import com.radolyn.ayugram.database.dao.DeletedMessageDao;
import com.radolyn.ayugram.database.dao.EditedMessageDao;
import com.radolyn.ayugram.database.entities.AyuMessageBase;
import com.radolyn.ayugram.database.entities.DeletedMessage;
import com.radolyn.ayugram.database.entities.DeletedMessageFull;
import com.radolyn.ayugram.database.entities.DeletedMessageReaction;
import com.radolyn.ayugram.database.entities.EditedMessage;
import com.radolyn.ayugram.messages.AyuMessagesController;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Exports/imports saved deleted & edited messages to a JSON backup file.
 *
 * The messages database (Room) lives in app-internal storage, so it is wiped on
 * uninstall. Media files are copied to the public Downloads folder and survive
 * a reinstall on their own, so only the records need to be backed up. On import,
 * media paths that no longer exist are re-resolved against the current
 * attachments folder by file name, so backups also survive a changed save path.
 */
public class AyuMessagesBackup {
    public static final int REQUEST_EXPORT_MESSAGES = 4021;
    public static final int REQUEST_IMPORT_MESSAGES = 4022;

    private static final String BACKUP_TYPE = "alexgram_messages_backup";
    private static final int BACKUP_VERSION = 1;
    // keep in sync with the version of AyuDatabase
    private static final int SCHEMA_VERSION = 26;
    private static final int EXPORT_CHUNK_SIZE = 200;

    private static final Map<String, Field> messageFieldMap = buildFieldMap(AyuMessageBase.class);
    private static final Map<String, Field> reactionFieldMap = buildFieldMap(DeletedMessageReaction.class, "fakeReactionId", "deletedMessageId");

    private AyuMessagesBackup() {
    }

    private static DeletedMessageDao deletedMessageDao() {
        return AyuData.getDeletedMessageDao();
    }

    private static EditedMessageDao editedMessageDao() {
        return AyuData.getEditedMessageDao();
    }

    // region UI entry points

    public static void showExportDialog(BaseFragment fragment) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        new AlertDialog.Builder(fragment.getContext(), fragment.getResourceProvider())
                .setTitle(LocaleController.getString(R.string.ExportMessagesHistory))
                .setMessage(LocaleController.getString(R.string.MessagesBackupExportText))
                .setPositiveButton(LocaleController.getString(R.string.MessagesBackupExport), (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("application/json");
                        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
                        intent.putExtra(Intent.EXTRA_TITLE, "Alexgram-Messages-Backup-" + stamp + ".json");
                        fragment.startActivityForResult(intent, REQUEST_EXPORT_MESSAGES);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), (dialog, which) -> dialog.dismiss())
                .show();
    }

    public static void showImportDialog(BaseFragment fragment) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
            fragment.startActivityForResult(intent, REQUEST_IMPORT_MESSAGES);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static boolean handleActivityResult(BaseFragment fragment, int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_EXPORT_MESSAGES && requestCode != REQUEST_IMPORT_MESSAGES) {
            return false;
        }
        if (resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) {
            return true;
        }
        Uri uri = data.getData();
        try {
            fragment.getParentActivity().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignore) {
        }

        if (requestCode == REQUEST_EXPORT_MESSAGES) {
            export(fragment, uri);
        } else {
            importBackup(fragment, uri);
        }
        return true;
    }

    private static AlertDialog showProgress(BaseFragment fragment) {
        AlertDialog progressDialog = new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progressDialog.setCanCancel(false);
        progressDialog.show();
        return progressDialog;
    }

    private static void showDone(BaseFragment fragment, String message) {
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.done, message).show();
    }

    private static void showError(BaseFragment fragment, String message) {
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, message).show();
    }

    // endregion

    // region export

    private static void export(BaseFragment fragment, Uri uri) {
        AlertDialog progressDialog = showProgress(fragment);
        Utilities.globalQueue.postRunnable(() -> {
            int[] counts;
            try {
                counts = exportTo(uri);
            } catch (Exception e) {
                FileLog.e("messages backup export failed", e);
                AndroidUtilities.runOnUIThread(() -> {
                    progressDialog.dismiss();
                    showError(fragment, LocaleController.getString(R.string.MessagesBackupExportFailed));
                });
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                progressDialog.dismiss();
                showDone(fragment, LocaleController.formatString(R.string.MessagesBackupExportDone, counts[0], counts[1]));
            });
        });
    }

    public static int[] exportTo(Uri uri) throws Exception {
        Context context = ApplicationLoader.applicationContext;
        int deleted = 0;
        int edited = 0;
        try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(new BufferedOutputStream(context.getContentResolver().openOutputStream(uri)), StandardCharsets.UTF_8))) {
            writer.beginObject();
            writer.name("type").value(BACKUP_TYPE);
            writer.name("backupVersion").value(BACKUP_VERSION);
            writer.name("schemaVersion").value(SCHEMA_VERSION);
            writer.name("exportDate").value((int) (System.currentTimeMillis() / 1000));

            writer.name("deletedMessages");
            writer.beginArray();
            long afterId = 0;
            while (true) {
                List<DeletedMessageFull> batch = deletedMessageDao().getFullMessagesAfter(afterId, EXPORT_CHUNK_SIZE);
                if (batch == null || batch.isEmpty()) {
                    break;
                }
                for (DeletedMessageFull full : batch) {
                    // re-point stale paths (old cache refs) at the durable attachments copy before writing
                    resolveMediaPaths(full.message);
                    writeDeletedMessage(writer, full);
                    deleted++;
                }
                afterId = batch.get(batch.size() - 1).message.fakeId;
                if (batch.size() < EXPORT_CHUNK_SIZE) {
                    break;
                }
            }
            writer.endArray();

            writer.name("editedMessages");
            writer.beginArray();
            afterId = 0;
            while (true) {
                List<EditedMessage> batch = editedMessageDao().getMessagesAfter(afterId, EXPORT_CHUNK_SIZE);
                if (batch == null || batch.isEmpty()) {
                    break;
                }
                for (EditedMessage revision : batch) {
                    resolveMediaPaths(revision);
                    writeFields(writer, revision, messageFieldMap);
                    edited++;
                }
                afterId = batch.get(batch.size() - 1).fakeId;
                if (batch.size() < EXPORT_CHUNK_SIZE) {
                    break;
                }
            }
            writer.endArray();

            writer.endObject();
        }
        return new int[]{deleted, edited};
    }

    private static void writeDeletedMessage(JsonWriter writer, DeletedMessageFull full) throws Exception {
        writer.beginObject();
        writeFields(writer, full.message, messageFieldMap);
        writer.name("reactions");
        writer.beginArray();
        if (full.reactions != null) {
            for (DeletedMessageReaction reaction : full.reactions) {
                writer.beginObject();
                writeFields(writer, reaction, reactionFieldMap);
                writer.endObject();
            }
        }
        writer.endArray();
        writer.endObject();
    }

    private static void writeFields(JsonWriter writer, Object target, Map<String, Field> fields) throws Exception {
        for (Field field : fields.values()) {
            writer.name(field.getName());
            Class<?> type = field.getType();
            if (type == int.class) {
                writer.value(field.getInt(target));
            } else if (type == long.class) {
                writer.value(field.getLong(target));
            } else if (type == boolean.class) {
                writer.value(field.getBoolean(target));
            } else if (type == String.class) {
                writer.value((String) field.get(target));
            } else if (type == byte[].class) {
                byte[] value = (byte[]) field.get(target);
                writer.value(value == null ? null : Base64.encodeToString(value, Base64.NO_WRAP));
            } else {
                writer.nullValue();
            }
        }
    }

    // endregion

    // region import

    private static void importBackup(BaseFragment fragment, Uri uri) {
        AlertDialog progressDialog = showProgress(fragment);
        Utilities.globalQueue.postRunnable(() -> {
            long firstUserId;
            try {
                firstUserId = scanForAccount(uri);
            } catch (Exception e) {
                FileLog.e("messages backup scan failed", e);
                AndroidUtilities.runOnUIThread(() -> {
                    progressDialog.dismiss();
                    showError(fragment, e.getMessage());
                });
                return;
            }

            long currentUserId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
            if (firstUserId != 0 && firstUserId != currentUserId) {
                AndroidUtilities.runOnUIThread(() -> {
                    progressDialog.dismiss();
                    showRemapDialog(fragment, uri);
                });
                return;
            }
            // scan dialog must go away before runImport shows its own progress
            AndroidUtilities.runOnUIThread(progressDialog::dismiss);
            runImport(fragment, uri, false);
        });
    }

    private static void showRemapDialog(BaseFragment fragment, Uri uri) {
        new AlertDialog.Builder(fragment.getContext(), fragment.getResourceProvider())
                .setTitle(LocaleController.getString(R.string.MessagesBackupRemapTitle))
                .setMessage(LocaleController.getString(R.string.MessagesBackupRemapText))
                .setPositiveButton(LocaleController.getString(R.string.MessagesBackupRemapToCurrent), (dialog, which) -> runImport(fragment, uri, true))
                .setNeutralButton(LocaleController.getString(R.string.MessagesBackupRemapKeep), (dialog, which) -> runImport(fragment, uri, false))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), (dialog, which) -> dialog.dismiss())
                .show();
    }

    private static void runImport(BaseFragment fragment, Uri uri, boolean remapToCurrentAccount) {
        // may be entered from the scan worker on globalQueue: dialogs must be shown from the main thread
        AndroidUtilities.runOnUIThread(() -> {
            AlertDialog progressDialog = showProgress(fragment);
            Utilities.globalQueue.postRunnable(() -> {
                int[] counts;
                try {
                    counts = importFrom(uri, remapToCurrentAccount);
                } catch (Exception e) {
                    FileLog.e("messages backup import failed", e);
                    AndroidUtilities.runOnUIThread(() -> {
                        progressDialog.dismiss();
                        showError(fragment, TextUtils.isEmpty(e.getMessage()) ? LocaleController.getString(R.string.MessagesBackupImportFailed) : e.getMessage());
                    });
                    return;
                }
                AndroidUtilities.runOnUIThread(() -> {
                    progressDialog.dismiss();
                    showDone(fragment, LocaleController.formatString(R.string.MessagesBackupImportDone, counts[0], counts[1], counts[2] + counts[3]));
                });
            });
        });
    }

    public static int[] importFrom(Uri uri, boolean remapToCurrentAccount) throws Exception {
        Context context = ApplicationLoader.applicationContext;
        long currentUserId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        int[] counters = new int[4];
        try (JsonReader reader = new JsonReader(new InputStreamReader(new BufferedInputStream(context.getContentResolver().openInputStream(uri)), StandardCharsets.UTF_8))) {
            // atomic: any failure rolls the whole import back, so a truncated or
            // corrupt file can never leave half-imported records behind
            AyuData.getDatabase().runInTransaction(() -> {
                try {
                    importRecords(reader, remapToCurrentAccount, currentUserId, counters);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
        return counters;
    }

    private static void importRecords(JsonReader reader, boolean remapToCurrentAccount, long currentUserId, int[] counters) throws Exception {
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if ("deletedMessages".equals(name)) {
                reader.beginArray();
                while (reader.hasNext()) {
                    DeletedMessageFull full = readDeletedMessage(reader);
                    if (remapToCurrentAccount) {
                        full.message.userId = currentUserId;
                    }
                    resolveMediaPaths(full.message);
                    // strict: ignore topicId drift between app versions so the same
                    // message can never be imported twice
                    if (deletedMessageDao().existsByMessageId(full.message.userId, full.message.dialogId, full.message.messageId)) {
                        counters[1]++;
                        continue;
                    }
                    long fakeId = deletedMessageDao().insert(full.message);
                    if (fakeId != 0 && full.reactions != null) {
                        for (DeletedMessageReaction reaction : full.reactions) {
                            reaction.deletedMessageId = fakeId;
                            deletedMessageDao().insertReaction(reaction);
                        }
                    }
                    counters[0]++;
                }
                reader.endArray();
            } else if ("editedMessages".equals(name)) {
                reader.beginArray();
                while (reader.hasNext()) {
                    EditedMessage revision = new EditedMessage();
                    reader.beginObject();
                    while (reader.hasNext()) {
                        applyField(revision, reader, reader.nextName(), messageFieldMap);
                    }
                    reader.endObject();
                    if (remapToCurrentAccount) {
                        revision.userId = currentUserId;
                    }
                    resolveMediaPaths(revision);
                    if (editedMessageDao().existsRevision(revision.userId, revision.dialogId, revision.messageId, revision.entityCreateDate, revision.text)) {
                        counters[3]++;
                        continue;
                    }
                    editedMessageDao().insert(revision);
                    counters[2]++;
                }
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
    }

    private static DeletedMessageFull readDeletedMessage(JsonReader reader) throws Exception {
        DeletedMessage message = new DeletedMessage();
        List<DeletedMessageReaction> reactions = new ArrayList<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if ("reactions".equals(name)) {
                reader.beginArray();
                while (reader.hasNext()) {
                    DeletedMessageReaction reaction = new DeletedMessageReaction();
                    reader.beginObject();
                    while (reader.hasNext()) {
                        applyField(reaction, reader, reader.nextName(), reactionFieldMap);
                    }
                    reader.endObject();
                    reactions.add(reaction);
                }
                reader.endArray();
            } else {
                applyField(message, reader, name, messageFieldMap);
            }
        }
        reader.endObject();

        DeletedMessageFull full = new DeletedMessageFull();
        full.message = message;
        full.reactions = reactions;
        return full;
    }

    private static void applyField(Object target, JsonReader reader, String name, Map<String, Field> fields) throws Exception {
        Field field = fields.get(name);
        if (field == null) {
            reader.skipValue();
            return;
        }
        Class<?> type = field.getType();
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            if (type == String.class || type == byte[].class) {
                field.set(target, null);
            }
            return;
        }
        if (type == int.class) {
            field.setInt(target, reader.nextInt());
        } else if (type == long.class) {
            field.setLong(target, reader.nextLong());
        } else if (type == boolean.class) {
            field.setBoolean(target, reader.nextBoolean());
        } else if (type == String.class) {
            field.set(target, reader.nextString());
        } else if (type == byte[].class) {
            String value = reader.nextString();
            field.set(target, TextUtils.isEmpty(value) ? null : Base64.decode(value, Base64.DEFAULT));
        } else {
            reader.skipValue();
        }
    }

    /**
     * First pass over the file: validates the header and grabs the userId of the
     * first record of either array to detect a cross-account restore. Everything
     * else is skipped by streaming, so it is fast even on huge backups.
     */
    private static long scanForAccount(Uri uri) throws Exception {
        Context context = ApplicationLoader.applicationContext;
        long firstUserId = 0;
        String type = null;
        int schemaVersion = 0;
        try (JsonReader reader = new JsonReader(new InputStreamReader(new BufferedInputStream(context.getContentResolver().openInputStream(uri)), StandardCharsets.UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if ("type".equals(name)) {
                    type = reader.nextString();
                } else if ("schemaVersion".equals(name)) {
                    schemaVersion = reader.nextInt();
                } else if ("deletedMessages".equals(name) || "editedMessages".equals(name)) {
                    if (firstUserId == 0) {
                        firstUserId = peekFirstUserId(reader);
                    } else {
                        reader.skipValue();
                    }
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        }
        validate(type, schemaVersion, firstUserId);
        return firstUserId;
    }

    private static long peekFirstUserId(JsonReader reader) throws Exception {
        long userId = 0;
        reader.beginArray();
        if (reader.hasNext()) {
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if ("userId".equals(key)) {
                    userId = reader.nextLong();
                    break;
                }
                reader.skipValue();
            }
            while (reader.hasNext()) {
                reader.skipValue();
            }
            reader.endObject();
        }
        while (reader.hasNext()) {
            reader.skipValue();
        }
        reader.endArray();
        return userId;
    }

    private static long validate(String type, int schemaVersion, long firstUserId) throws IOException {
        if (!BACKUP_TYPE.equals(type)) {
            throw new IOException(LocaleController.getString(R.string.MessagesBackupInvalidFile));
        }
        if (schemaVersion > SCHEMA_VERSION) {
            throw new IOException(LocaleController.getString(R.string.MessagesBackupNewerVersion));
        }
        return firstUserId;
    }

    // endregion

    // region helpers

    private static void resolveMediaPaths(AyuMessageBase message) {
        message.mediaPath = resolveMediaPath(message.mediaPath);
        message.hqThumbPath = resolveMediaPath(message.hqThumbPath);
    }

    /**
     * The attachments folder may have moved (custom save path changed, reinstall):
     * if the stored path is gone, look the file up by name in the current folder.
     */
    private static String resolveMediaPath(String path) {
        if (TextUtils.isEmpty(path)) {
            return path;
        }
        File file = new File(path);
        if (file.exists()) {
            return path;
        }
        File candidate = new File(AyuMessagesController.attachmentsPath, file.getName());
        if (candidate.exists()) {
            return candidate.getAbsolutePath();
        }
        return path;
    }

    private static Map<String, Field> buildFieldMap(Class<?> clazz, String... excluded) {
        Map<String, Field> map = new HashMap<>();
        for (Field field : clazz.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            boolean skip = false;
            for (String name : excluded) {
                if (field.getName().equals(name)) {
                    skip = true;
                    break;
                }
            }
            if (skip) {
                continue;
            }
            field.setAccessible(true);
            map.put(field.getName(), field);
        }
        return map;
    }

    // endregion
}
