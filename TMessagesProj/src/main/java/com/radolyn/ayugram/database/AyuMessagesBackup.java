/*
 * This is the source code of AyuGram for Android.
 *
 * We do not and cannot prevent the use of our code,
 * but be respectful and credit the original author.
 *
 * Copyright @Radolyn, 2023
 */

package com.radolyn.ayugram.database;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.util.Base64;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.JsonWriter;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

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
 *
 * Both operations run on a dedicated background thread: the UI is never blocked,
 * progress is mirrored on the settings rows and on a status-bar notification
 * (with a cancel action), and the result is delivered as a bulletin when the
 * app is in the foreground or as a notification otherwise.
 */
public class AyuMessagesBackup {
    public static final int REQUEST_EXPORT_MESSAGES = 4021;
    public static final int REQUEST_IMPORT_MESSAGES = 4022;

    public static final int STATE_IDLE = 0;
    public static final int STATE_EXPORTING = 1;
    public static final int STATE_IMPORTING = 2;

    private static final String BACKUP_TYPE = "alexgram_messages_backup";
    private static final int BACKUP_VERSION = 1;
    // keep in sync with the version of AyuDatabase
    private static final int SCHEMA_VERSION = 26;
    private static final int EXPORT_CHUNK_SIZE = 200;

    private static final String NOTIFICATION_CHANNEL_ID = "ayu_messages_backup";
    private static final int NOTIFICATION_ID_PROGRESS = 7301;
    private static final int NOTIFICATION_ID_RESULT = 7302;
    private static final int UI_UPDATE_EVERY = 250;

    private static final Map<String, Field> messageFieldMap = buildFieldMap(AyuMessageBase.class);
    private static final Map<String, Field> reactionFieldMap = buildFieldMap(DeletedMessageReaction.class, "fakeReactionId", "deletedMessageId");

    private static volatile int state = STATE_IDLE;
    private static volatile int progressProcessed;
    private static volatile int progressTotal;
    private static volatile boolean cancelRequested;
    private static volatile UiListener uiListener;

    public interface UiListener {
        void onBackupStateChanged();
    }

    private static class CancelledException extends Exception {
    }

    static {
        try {
            IntentFilter filter = new IntentFilter(getCancelAction());
            ContextCompat.registerReceiver(ApplicationLoader.applicationContext, new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    cancelRequested = true;
                }
            }, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        } catch (Throwable e) {
            FileLog.e(e);
        }
        // a killed process leaves its progress notification behind
        cancelNotifications();
    }

    private AyuMessagesBackup() {
    }

    private static String getCancelAction() {
        return ApplicationLoader.applicationContext.getPackageName() + ".MESSAGES_BACKUP_CANCEL";
    }

    private static DeletedMessageDao deletedMessageDao() {
        return AyuData.getDeletedMessageDao();
    }

    private static EditedMessageDao editedMessageDao() {
        return AyuData.getEditedMessageDao();
    }

    // region background state

    public static boolean isBusy() {
        return state != STATE_IDLE;
    }

    public static int getState() {
        return state;
    }

    public static int getProgressPercent() {
        return progressTotal <= 0 ? 0 : Math.min(99, (int) (progressProcessed * 100L / progressTotal));
    }

    public static void setUiListener(UiListener listener) {
        uiListener = listener;
        if (listener != null && isBusy()) {
            AndroidUtilities.runOnUIThread(listener::onBackupStateChanged);
        }
    }

    private static void notifyUiChanged() {
        UiListener listener = uiListener;
        if (listener != null) {
            AndroidUtilities.runOnUIThread(listener::onBackupStateChanged);
        }
    }

    // endregion

    // region UI entry points

    public static void showExportDialog(BaseFragment fragment) {
        if (isBusy()) {
            bulletinAlreadyRunning(fragment);
            return;
        }
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
        if (isBusy()) {
            bulletinAlreadyRunning(fragment);
            return;
        }
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
            startExport(uri);
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

    private static void bulletinAlreadyRunning(BaseFragment fragment) {
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.MessagesBackupAlreadyRunning)).show();
    }

    private static void showError(BaseFragment fragment, String message) {
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, message).show();
    }

    // endregion

    // region export

    private static void startExport(Uri uri) {
        state = STATE_EXPORTING;
        cancelRequested = false;
        progressProcessed = 0;
        progressTotal = 0;
        notifyUiChanged();
        showProgressNotification(R.string.MessagesBackupExportingNotification);
        Thread thread = new Thread(() -> {
            int[] counts;
            try {
                progressTotal = deletedMessageDao().countAll() + editedMessageDao().countAll();
                counts = exportTo(uri);
            } catch (CancelledException e) {
                deleteDocument(uri);
                finishOperation(LocaleController.getString(R.string.MessagesBackupExportCancelled), true);
                return;
            } catch (Exception e) {
                FileLog.e("messages backup export failed", e);
                finishOperation(TextUtils.isEmpty(e.getMessage()) ? LocaleController.getString(R.string.MessagesBackupExportFailed) : e.getMessage(), true);
                return;
            }
            String text = LocaleController.formatString(R.string.MessagesBackupExportDone, counts[0], counts[1]);
            if (counts[2] > 0) {
                text += "\n" + LocaleController.formatString(R.string.MessagesBackupExportSkipped, counts[2]);
            }
            finishOperation(text, counts[2] > 0);
        }, "AyuMessagesBackupExport");
        thread.start();
    }

    /**
     * Returns {deleted, edited, skipped}. Rows are fetched one by one via id
     * chunks: a single oversized row (bigger than the SQLite cursor window)
     * would otherwise fail the entire batch query, so unreadable rows are
     * skipped and reported instead of failing the whole export.
     */
    public static int[] exportTo(Uri uri) throws Exception {
        Context context = ApplicationLoader.applicationContext;
        int deleted = 0;
        int edited = 0;
        int skipped = 0;
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
                if (cancelRequested) {
                    throw new CancelledException();
                }
                List<Long> ids = deletedMessageDao().getIdsAfter(afterId, EXPORT_CHUNK_SIZE);
                if (ids == null || ids.isEmpty()) {
                    break;
                }
                for (long id : ids) {
                    try {
                        DeletedMessageFull full = deletedMessageDao().getMessageFullById(id);
                        if (full == null || full.message == null) {
                            continue;
                        }
                        // re-point stale paths (old cache refs) at the durable attachments copy before writing
                        resolveMediaPaths(full.message);
                        writeDeletedMessage(writer, full);
                        deleted++;
                    } catch (IOException e) {
                        throw e; // the output stream itself is broken: the whole export must abort
                    } catch (Exception e) {
                        recoverWriter(writer);
                        FileLog.e("messages backup: skipping unreadable deleted row " + id, e);
                        skipped++;
                    }
                    progressProcessed++;
                }
                afterId = ids.get(ids.size() - 1);
                notifyUiChanged();
                updateProgressNotification();
                if (ids.size() < EXPORT_CHUNK_SIZE) {
                    break;
                }
            }
            writer.endArray();

            writer.name("editedMessages");
            writer.beginArray();
            afterId = 0;
            while (true) {
                if (cancelRequested) {
                    throw new CancelledException();
                }
                List<Long> ids = editedMessageDao().getIdsAfter(afterId, EXPORT_CHUNK_SIZE);
                if (ids == null || ids.isEmpty()) {
                    break;
                }
                for (long id : ids) {
                    try {
                        EditedMessage revision = editedMessageDao().getMessageById(id);
                        if (revision == null) {
                            continue;
                        }
                        resolveMediaPaths(revision);
                        writer.beginObject();
                        writeFields(writer, revision, messageFieldMap);
                        writer.endObject();
                        edited++;
                    } catch (IOException e) {
                        throw e; // the output stream itself is broken: the whole export must abort
                    } catch (Exception e) {
                        recoverWriter(writer);
                        FileLog.e("messages backup: skipping unreadable edited row " + id, e);
                        skipped++;
                    }
                    progressProcessed++;
                }
                afterId = ids.get(ids.size() - 1);
                notifyUiChanged();
                updateProgressNotification();
                if (ids.size() < EXPORT_CHUNK_SIZE) {
                    break;
                }
            }
            writer.endArray();

            writer.endObject();
        }
        return new int[]{deleted, edited, skipped};
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
            ScanResult result;
            try {
                result = scanForAccount(uri);
            } catch (Exception e) {
                FileLog.e("messages backup scan failed", e);
                AndroidUtilities.runOnUIThread(() -> {
                    progressDialog.dismiss();
                    showError(fragment, TextUtils.isEmpty(e.getMessage()) ? LocaleController.getString(R.string.MessagesBackupImportFailed) : e.getMessage());
                });
                return;
            }

            long currentUserId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
            if (result.firstUserId != 0 && result.firstUserId != currentUserId) {
                AndroidUtilities.runOnUIThread(() -> {
                    progressDialog.dismiss();
                    showRemapDialog(fragment, uri, result.totalRecords);
                });
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                progressDialog.dismiss();
                startImport(uri, result.totalRecords, false);
            });
        });
    }

    private static void showRemapDialog(BaseFragment fragment, Uri uri, int totalRecords) {
        new AlertDialog.Builder(fragment.getContext(), fragment.getResourceProvider())
                .setTitle(LocaleController.getString(R.string.MessagesBackupRemapTitle))
                .setMessage(LocaleController.getString(R.string.MessagesBackupRemapText))
                .setPositiveButton(LocaleController.getString(R.string.MessagesBackupRemapToCurrent), (dialog, which) -> startImport(uri, totalRecords, true))
                .setNeutralButton(LocaleController.getString(R.string.MessagesBackupRemapKeep), (dialog, which) -> startImport(uri, totalRecords, false))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), (dialog, which) -> dialog.dismiss())
                .show();
    }

    private static void startImport(Uri uri, int totalRecords, boolean remapToCurrentAccount) {
        state = STATE_IMPORTING;
        cancelRequested = false;
        progressProcessed = 0;
        progressTotal = totalRecords;
        notifyUiChanged();
        showProgressNotification(R.string.MessagesBackupImportingNotification);
        Thread thread = new Thread(() -> {
            int[] counts;
            try {
                counts = importFrom(uri, remapToCurrentAccount);
            } catch (CancelledException e) {
                // the transaction rolled back, so nothing was restored
                finishOperation(LocaleController.getString(R.string.MessagesBackupImportCancelled), true);
                return;
            } catch (Exception e) {
                FileLog.e("messages backup import failed", e);
                finishOperation(TextUtils.isEmpty(e.getMessage()) ? LocaleController.getString(R.string.MessagesBackupImportFailed) : e.getMessage(), true);
                return;
            }
            finishOperation(LocaleController.formatString(R.string.MessagesBackupImportDone, counts[0], counts[2], counts[1] + counts[3]), false);
        }, "AyuMessagesBackupImport");
        thread.start();
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
                    if (cancelRequested) {
                        throw new CancelledException();
                    }
                    DeletedMessageFull full = readDeletedMessage(reader);
                    if (remapToCurrentAccount) {
                        full.message.userId = currentUserId;
                    }
                    resolveMediaPaths(full.message);
                    // strict: ignore topicId drift between app versions so the same
                    // message can never be imported twice
                    if (deletedMessageDao().existsByMessageId(full.message.userId, full.message.dialogId, full.message.messageId)) {
                        counters[1]++;
                    } else {
                        long fakeId = deletedMessageDao().insert(full.message);
                        if (fakeId != 0 && full.reactions != null) {
                            for (DeletedMessageReaction reaction : full.reactions) {
                                reaction.deletedMessageId = fakeId;
                                deletedMessageDao().insertReaction(reaction);
                            }
                        }
                        counters[0]++;
                    }
                    onRecordProcessed();
                }
                reader.endArray();
            } else if ("editedMessages".equals(name)) {
                reader.beginArray();
                while (reader.hasNext()) {
                    if (cancelRequested) {
                        throw new CancelledException();
                    }
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
                    } else {
                        editedMessageDao().insert(revision);
                        counters[2]++;
                    }
                    onRecordProcessed();
                }
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
    }

    private static void onRecordProcessed() {
        progressProcessed++;
        if (progressProcessed % UI_UPDATE_EVERY == 0) {
            notifyUiChanged();
            updateProgressNotification();
        }
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
     * First pass over the file: validates the header, counts the records (used
     * as the progress total) and grabs the userId of the first record of either
     * array to detect a cross-account restore. Everything else is skipped by
     * streaming, so it is fast even on huge backups.
     */
    private static ScanResult scanForAccount(Uri uri) throws Exception {
        Context context = ApplicationLoader.applicationContext;
        ScanResult result = new ScanResult();
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
                    long[] userIdAndCount = readFirstUserIdAndCount(reader);
                    if (result.firstUserId == 0 && userIdAndCount[0] != 0) {
                        result.firstUserId = userIdAndCount[0];
                    }
                    result.totalRecords += (int) userIdAndCount[1];
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        }
        validate(type, schemaVersion, result.firstUserId);
        return result;
    }

    private static long[] readFirstUserIdAndCount(JsonReader reader) throws Exception {
        long userId = 0;
        int count = 0;
        reader.beginArray();
        while (reader.hasNext()) {
            if (count == 0) {
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
            } else {
                reader.skipValue();
            }
            count++;
        }
        reader.endArray();
        return new long[]{userId, count};
    }

    private static void validate(String type, int schemaVersion, long firstUserId) throws IOException {
        if (!BACKUP_TYPE.equals(type)) {
            throw new IOException(LocaleController.getString(R.string.MessagesBackupInvalidFile));
        }
        if (schemaVersion > SCHEMA_VERSION) {
            throw new IOException(LocaleController.getString(R.string.MessagesBackupNewerVersion));
        }
    }

    private static class ScanResult {
        long firstUserId;
        int totalRecords;
    }

    // endregion

    // region finish + result delivery

    private static void finishOperation(String text, boolean isError) {
        state = STATE_IDLE;
        notifyUiChanged();
        cancelProgressNotification();
        deliverResult(text, isError);
    }

    private static void deliverResult(String text, boolean isError) {
        boolean notified = false;
        // while the app is in the background, or for failures, the status bar is
        // the only place the user is guaranteed to see the outcome
        if (ApplicationLoader.mainInterfacePaused || isError) {
            notified = postResultNotification(text, isError);
        }
        if (!notified) {
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    BulletinFactory.global().createSimpleBulletin(isError ? R.raw.error : R.raw.done, text).show();
                } catch (Throwable ignored) {
                }
            });
        }
    }

    // endregion

    // region notifications

    private static void ensureChannel() {
        try {
            NotificationManager manager = (NotificationManager) ApplicationLoader.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) {
                return;
            }
            NotificationChannel channel = manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID);
            if (channel == null) {
                channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, LocaleController.getString(R.string.MessagesBackupNotificationChannel), NotificationManager.IMPORTANCE_LOW);
                manager.createNotificationChannel(channel);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void showProgressNotification(int titleRes) {
        try {
            ensureChannel();
            NotificationCompat.Builder builder = progressNotificationBuilder(LocaleController.getString(titleRes));
            builder.setProgress(100, 0, true);
            NotificationManagerCompat.from(ApplicationLoader.applicationContext).notify(NOTIFICATION_ID_PROGRESS, builder.build());
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void updateProgressNotification() {
        try {
            ensureChannel();
            NotificationCompat.Builder builder = progressNotificationBuilder(LocaleController.getString(state == STATE_IMPORTING ? R.string.MessagesBackupImportingNotification : R.string.MessagesBackupExportingNotification));
            builder.setProgress(100, getProgressPercent(), false);
            NotificationManagerCompat.from(ApplicationLoader.applicationContext).notify(NOTIFICATION_ID_PROGRESS, builder.build());
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static NotificationCompat.Builder progressNotificationBuilder(String title) {
        return new NotificationCompat.Builder(ApplicationLoader.applicationContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.notification)
                .setContentTitle(title)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentPendingIntent())
                .addAction(0, LocaleController.getString(R.string.Cancel), cancelPendingIntent());
    }

    private static boolean postResultNotification(String text, boolean isError) {
        try {
            ensureChannel();
            NotificationCompat.Builder builder = new NotificationCompat.Builder(ApplicationLoader.applicationContext, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.notification)
                    .setContentTitle(LocaleController.getString(R.string.MessagesBackupNotificationChannel))
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                    .setAutoCancel(true)
                    .setContentIntent(contentPendingIntent());
            NotificationManagerCompat.from(ApplicationLoader.applicationContext).notify(NOTIFICATION_ID_RESULT, builder.build());
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    private static void cancelProgressNotification() {
        try {
            NotificationManagerCompat.from(ApplicationLoader.applicationContext).cancel(NOTIFICATION_ID_PROGRESS);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void cancelNotifications() {
        try {
            NotificationManagerCompat.from(ApplicationLoader.applicationContext).cancel(NOTIFICATION_ID_PROGRESS);
            NotificationManagerCompat.from(ApplicationLoader.applicationContext).cancel(NOTIFICATION_ID_RESULT);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static PendingIntent contentPendingIntent() {
        Intent launch = ApplicationLoader.applicationContext.getPackageManager().getLaunchIntentForPackage(ApplicationLoader.applicationContext.getPackageName());
        if (launch == null) {
            launch = new Intent();
        }
        return PendingIntent.getActivity(ApplicationLoader.applicationContext, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent cancelPendingIntent() {
        Intent intent = new Intent(getCancelAction());
        return PendingIntent.getBroadcast(ApplicationLoader.applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void deleteDocument(Uri uri) {
        try {
            DocumentsContract.deleteDocument(ApplicationLoader.applicationContext.getContentResolver(), uri);
        } catch (Throwable e) {
            FileLog.e(e);
        }
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

    /**
     * If a row failed mid-write the JsonWriter is left with unclosed nesting;
     * close whatever level we are stuck at so the remaining rows stay valid.
     */
    private static void recoverWriter(JsonWriter writer) {
        try {
            writer.endArray();
        } catch (Throwable ignored) {
        }
        try {
            writer.endObject();
        } catch (Throwable ignored) {
        }
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
