package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.CameraScanActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.SessionsActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.cell.AbstractConfigCell;
import tw.nekomimi.nekogram.config.cell.ConfigCellDivider;
import tw.nekomimi.nekogram.config.cell.ConfigCellHeader;
import tw.nekomimi.nekogram.config.cell.ConfigCellText;
import tw.nekomimi.nekogram.helpers.AccountSessionManager;
import tw.nekomimi.nekogram.helpers.AccountSessionManager.AccountSessionInfo;

@SuppressLint("RtlHardcoded")
@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class AccountSessionManagerActivity extends BaseNekoXSettingsActivity {

    private static final int REQUEST_PICK_SESSION_FILE = 2105;

    private ListAdapter listAdapter;
    private final CellGroup cellGroup = new CellGroup(this);

    private boolean encryptBackup = false;

    // Active Sessions Section
    private AbstractConfigCell headerActive;
    private final List<AbstractConfigCell> activeSessionRows = new ArrayList<>();
    private final List<AccountSessionInfo> activeSessionData = new ArrayList<>();
    private AbstractConfigCell dividerActive;

    // Backup & Restore Section
    private AbstractConfigCell headerBackup;
    private AbstractConfigCell importStringRow;
    private AbstractConfigCell importFileRow;
    private AbstractConfigCell exportSessionsRow;
    private AbstractConfigCell encryptBackupRow;
    private AbstractConfigCell dividerBackup;

    // Session Tools & Devices Section
    private AbstractConfigCell headerTools;
    private AbstractConfigCell telegramDevicesRow;
    private AbstractConfigCell webSessionsRow;
    private AbstractConfigCell scanQrRow;
    private AbstractConfigCell terminateAllRow;
    private AbstractConfigCell dividerTools;

    @Override
    public boolean onFragmentCreate() {
        buildCells();
        return super.onFragmentCreate();
    }

    private void buildCells() {
        cellGroup.rows.clear();

        // 1. Active Account Sessions Section
        headerActive = cellGroup.appendCell(
                new ConfigCellHeader(getString(R.string.ActiveSessionsSection)));

        activeSessionData.clear();
        activeSessionData.addAll(AccountSessionManager.getActiveAccountSessions());
        activeSessionRows.clear();

        if (activeSessionData.isEmpty()) {
            AbstractConfigCell emptyCell = cellGroup.appendCell(
                    new ConfigCellText("No active accounts", "Please log in first", false, () -> {}));
            activeSessionRows.add(emptyCell);
        } else {
            for (AccountSessionInfo session : activeSessionData) {
                String sub = "DC" + session.dcId + " • " + (session.phone != null && !session.phone.isEmpty() ? session.phone : "ID: " + session.userId);
                if (session.accountIndex == UserConfig.selectedAccount) {
                    sub = "[Current] " + sub;
                }
                AbstractConfigCell cell = cellGroup.appendCell(
                        new ConfigCellText(session.getDisplayName(), sub, true, () -> {})
                );
                activeSessionRows.add(cell);
            }
        }
        dividerActive = cellGroup.appendCell(new ConfigCellDivider());

        // 2. Backup & Restore Section
        headerBackup = cellGroup.appendCell(
                new ConfigCellHeader(getString(R.string.SessionBackupSection)));

        importStringRow = cellGroup.appendCell(
                new ConfigCellText("ImportSessionStringTitle", () -> {}));

        importFileRow = cellGroup.appendCell(
                new ConfigCellText("ImportSessionTitle", () -> {}));

        exportSessionsRow = cellGroup.appendCell(
                new ConfigCellText("ExportSessionsTitle", () -> {}));

        encryptBackupRow = cellGroup.appendCell(
                new ConfigCellText("SessionEncryptExport", encryptBackup ? "ON" : "OFF", () -> {}));

        dividerBackup = cellGroup.appendCell(new ConfigCellDivider());

        // 3. Session Tools & Devices Section
        headerTools = cellGroup.appendCell(
                new ConfigCellHeader(getString(R.string.SessionToolsSection)));

        telegramDevicesRow = cellGroup.appendCell(
                new ConfigCellText("TelegramActiveDevices", () -> {}));

        webSessionsRow = cellGroup.appendCell(
                new ConfigCellText("WebSessionsTitleNa", () -> {}));

        scanQrRow = cellGroup.appendCell(
                new ConfigCellText("ScanSessionQRCode", () -> {}));

        terminateAllRow = cellGroup.appendCell(
                new ConfigCellText("TerminateAllOtherSessions", () -> {}));

        dividerTools = cellGroup.appendCell(new ConfigCellDivider());
    }

    @Override
    protected RecyclerListView.SelectionAdapter getListAdapter() {
        return listAdapter;
    }

    @Override
    protected CellGroup getCellGroup() {
        return cellGroup;
    }

    @Override
    protected String getSettingsPrefix() {
        return "session_management";
    }

    @Override
    public String getTitle() {
        return getString(R.string.SessionManagementTitle);
    }

    @Override
    public View createView(Context context) {
        View superView = super.createView(context);
        listAdapter = new ListAdapter(context);
        listView.setAdapter(listAdapter);
        listView.invalidateItemDecorations();

        setupDefaultListeners();
        addRowsToMap(cellGroup);

        return superView;
    }

    @Override
    protected void handleCellClick(View view, int position, float x, float y) {
        // Active Session item click
        for (int i = 0; i < activeSessionRows.size(); i++) {
            if (position == cellGroup.rows.indexOf(activeSessionRows.get(i))) {
                if (i < activeSessionData.size()) {
                    showAccountSessionActionMenu(activeSessionData.get(i));
                }
                return;
            }
        }

        if (position == cellGroup.rows.indexOf(importStringRow)) {
            promptPasteSessionString();
        } else if (position == cellGroup.rows.indexOf(importFileRow)) {
            performPickImportSessionFile();
        } else if (position == cellGroup.rows.indexOf(exportSessionsRow)) {
            performExportSessionsFlow();
        } else if (position == cellGroup.rows.indexOf(encryptBackupRow)) {
            encryptBackup = !encryptBackup;
            if (encryptBackupRow instanceof ConfigCellText) {
                ((ConfigCellText) encryptBackupRow).setValue(encryptBackup ? "ON" : "OFF");
            }
        } else if (position == cellGroup.rows.indexOf(telegramDevicesRow)) {
            presentFragment(new SessionsActivity(SessionsActivity.TYPE_DEVICES));
        } else if (position == cellGroup.rows.indexOf(webSessionsRow)) {
            presentFragment(new SessionsActivity(SessionsActivity.TYPE_WEB_SESSIONS));
        } else if (position == cellGroup.rows.indexOf(scanQrRow)) {
            if (getParentActivity() != null) {
                CameraScanActivity.showAsSheet(getParentActivity(), false, CameraScanActivity.TYPE_QR_LOGIN, new CameraScanActivity.CameraScanActivityDelegate() {
                    @Override
                    public void didFindQr(String text) {
                        // Handled by camera scan sheet
                    }
                });
            }
        } else if (position == cellGroup.rows.indexOf(terminateAllRow)) {
            confirmTerminateAllSessions();
        } else {
            super.handleCellClick(view, position, x, y);
        }
    }

    private void showAccountSessionActionMenu(AccountSessionInfo info) {
        if (getParentActivity() == null) return;

        CharSequence[] options = new CharSequence[]{
                getString(R.string.SwitchToAccount),
                getString(R.string.ExportPyrogramString),
                getString(R.string.ExportPyrogramFile),
                getString(R.string.ExportTelethonString),
                getString(R.string.SessionDetailsTitle)
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(info.getDisplayName());
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    switchToAccount(info.accountIndex);
                    break;
                case 1:
                    copyPyrogramString(info.accountIndex);
                    break;
                case 2:
                    exportPyrogramSqlite(info.accountIndex);
                    break;
                case 3:
                    copyTelethonString(info.accountIndex);
                    break;
                case 4:
                    showAccountDetailsDialog(info);
                    break;
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private void switchToAccount(int accountIndex) {
        if (accountIndex != UserConfig.selectedAccount) {
            UserConfig.selectedAccount = accountIndex;
            UserConfig.getInstance(accountIndex).saveConfig(false);
            UserConfig.getInstance(0).saveConfig(false);
            if (LaunchActivity.instance != null) {
                LaunchActivity.instance.switchToAccount(accountIndex, true);
            } else if (getParentActivity() instanceof LaunchActivity) {
                ((LaunchActivity) getParentActivity()).switchToAccount(accountIndex, true);
            }
        } else {
            BulletinFactory.of(this).createSimpleBulletin(R.drawable.msg_check, getString(R.string.SessionAlreadyActive)).show();
        }
    }

    private void copyPyrogramString(int accountIndex) {
        String sessionString = AccountSessionManager.exportPyrogramString(accountIndex);
        if (TextUtils.isEmpty(sessionString)) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.FailedExtractAuthKey)).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Pyrogram Session", sessionString);
        clipboard.setPrimaryClip(clip);

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.PyrogramSessionStringV2));
        builder.setMessage(sessionString);
        builder.setPositiveButton(getString(R.string.CopyAgain), (dialog, which) -> {
            clipboard.setPrimaryClip(clip);
            BulletinFactory.of(this).createSimpleBulletin(R.drawable.msg_check, getString(R.string.SessionCopied)).show();
        });
        builder.setNeutralButton(getString(R.string.ShareFile), (dialog, which) -> {
            if (getParentActivity() == null) return;
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, sessionString);
            getParentActivity().startActivity(Intent.createChooser(shareIntent, getString(R.string.ShareSessionString)));
        });
        builder.setNegativeButton(getString(R.string.Close), null);
        builder.show();

        BulletinFactory.of(this).createSimpleBulletin(R.drawable.msg_check, getString(R.string.SessionCopied)).show();
    }

    private void copyTelethonString(int accountIndex) {
        String sessionString = AccountSessionManager.exportTelethonString(accountIndex);
        if (TextUtils.isEmpty(sessionString)) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.FailedExtractAuthKey)).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Telethon Session", sessionString);
        clipboard.setPrimaryClip(clip);

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.TelethonSessionStringTitle));
        builder.setMessage(sessionString);
        builder.setPositiveButton(getString(R.string.CopyAgain), (dialog, which) -> {
            clipboard.setPrimaryClip(clip);
            BulletinFactory.of(this).createSimpleBulletin(R.drawable.msg_check, getString(R.string.TelethonSessionCopied)).show();
        });
        builder.setNeutralButton(getString(R.string.ShareFile), (dialog, which) -> {
            if (getParentActivity() == null) return;
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, sessionString);
            getParentActivity().startActivity(Intent.createChooser(shareIntent, getString(R.string.ShareSessionString)));
        });
        builder.setNegativeButton(getString(R.string.Close), null);
        builder.show();

        BulletinFactory.of(this).createSimpleBulletin(R.drawable.msg_check, getString(R.string.TelethonSessionCopied)).show();
    }

    private void exportPyrogramSqlite(int accountIndex) {
        File file = AccountSessionManager.exportPyrogramSqliteFile(getParentActivity(), accountIndex);
        if (file != null && file.exists()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle("Pyrogram .session Exported");
            builder.setMessage("Saved to:\n" + file.getAbsolutePath());
            builder.setPositiveButton("Share File", (dialog, which) -> {
                if (getParentActivity() == null) return;
                try {
                    Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                            getParentActivity(),
                            ApplicationLoader.getApplicationId() + ".provider",
                            file
                    );
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("application/octet-stream");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    getParentActivity().startActivity(Intent.createChooser(shareIntent, "Share .session file"));
                } catch (Exception e) {
                    Toast.makeText(getParentActivity(), "File saved at: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
                }
            });
            builder.setNegativeButton(getString(R.string.OK), null);
            builder.show();
        } else {
            BulletinFactory.of(this).createErrorBulletin("Failed to create .session SQLite file.").show();
        }
    }

    private void showAccountDetailsDialog(AccountSessionInfo info) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(info.getDisplayName());
        String message = "Account Slot: " + (info.accountIndex + 1) + " (Index: " + info.accountIndex + ")\n" +
                "User ID: " + info.userId + "\n" +
                "Phone: " + (info.phone != null && !info.phone.isEmpty() ? info.phone : "N/A") + "\n" +
                "Username: " + (info.username != null && !info.username.isEmpty() ? "@" + info.username : "N/A") + "\n" +
                "Datacenter: DC" + info.dcId + "\n" +
                "Premium: " + (info.isPremium ? "Yes" : "No") + "\n" +
                "Bot Account: " + (info.isBot ? "Yes" : "No");
        builder.setMessage(message);
        builder.setPositiveButton(getString(R.string.OK), null);
        builder.show();
    }

    private void promptPasteSessionString() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.ImportSessionStringTitle));

        final EditText editText = new EditText(getParentActivity());
        editText.setHint(getString(R.string.PasteSessionStringHint));
        editText.setTextSize(13);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editText.setMinLines(3);
        editText.setMaxLines(6);

        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        int dp20 = AndroidUtilities.dp(20);
        container.setPadding(dp20, dp20 / 2, dp20, dp20 / 2);
        container.addView(editText);
        builder.setView(container);

        builder.setPositiveButton("Import", (dialog, which) -> {
            String raw = editText.getText().toString();
            if (TextUtils.isEmpty(raw.trim())) {
                BulletinFactory.of(this).createErrorBulletin("Session string cannot be empty.").show();
                return;
            }
            AccountSessionManager.importSessionFromString(getParentActivity(), raw.trim(), new AccountSessionManager.SessionImportCallback() {
                @Override
                public void onImportSuccess(int targetAccountIndex, String summary) {
                    onSessionImportSuccess(targetAccountIndex, summary);
                }

                @Override
                public void onImportFailed(String errorMessage) {
                    BulletinFactory.of(AccountSessionManagerActivity.this)
                            .createErrorBulletin(errorMessage != null ? errorMessage : getString(R.string.SessionImportError))
                            .show();
                }
            });
        });

        builder.setNeutralButton(getString(R.string.PasteClipboard), (dialog, which) -> {
            ClipboardManager clipboard = (ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = clipboard.getPrimaryClip().getItemAt(0).getText();
                if (text != null) {
                    editText.setText(text.toString().trim());
                }
            }
        });

        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private void performExportSessionsFlow() {
        List<AccountSessionInfo> activeSessions = AccountSessionManager.getActiveAccountSessions();
        if (activeSessions.isEmpty()) {
            Toast.makeText(getParentActivity(), LocaleController.getString("NoAccounts", R.string.NoAccounts), Toast.LENGTH_SHORT).show();
            return;
        }

        CharSequence[] formatOptions = new CharSequence[]{
                getString(R.string.ExportPyrogramStringOpt),
                getString(R.string.ExportPyrogramFileOpt),
                getString(R.string.ExportTelethonStringOpt),
                getString(R.string.ExportAlexgramBackupOpt)
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.ExportTypeTitle));
        builder.setItems(formatOptions, (dialog, which) -> {
            switch (which) {
                case 0:
                    selectAccountForExport(activeSessions, this::copyPyrogramString);
                    break;
                case 1:
                    selectAccountForExport(activeSessions, this::exportPyrogramSqlite);
                    break;
                case 2:
                    selectAccountForExport(activeSessions, this::copyTelethonString);
                    break;
                case 3:
                    exportAlexgramBackupFlow(activeSessions);
                    break;
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private interface OnAccountSelectedListener {
        void onAccountSelected(int accountIndex);
    }

    private void selectAccountForExport(List<AccountSessionInfo> sessions, OnAccountSelectedListener listener) {
        if (sessions.size() == 1) {
            listener.onAccountSelected(sessions.get(0).accountIndex);
            return;
        }
        CharSequence[] items = new CharSequence[sessions.size()];
        for (int i = 0; i < sessions.size(); i++) {
            items[i] = sessions.get(i).getDisplayName() + " (DC" + sessions.get(i).dcId + ")";
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Select Account to Export");
        builder.setItems(items, (dialog, which) -> {
            listener.onAccountSelected(sessions.get(which).accountIndex);
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private void exportAlexgramBackupFlow(List<AccountSessionInfo> activeSessions) {
        if (activeSessions.size() == 1) {
            executeAlexgramBackup(Collections.singletonList(activeSessions.get(0).accountIndex));
            return;
        }

        CharSequence[] options = new CharSequence[]{
                "Export All Active Accounts (" + activeSessions.size() + ")",
                "Select Specific Accounts..."
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.ExportSessionsTitle));
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                List<Integer> allIndices = new ArrayList<>();
                for (AccountSessionInfo s : activeSessions) {
                    allIndices.add(s.accountIndex);
                }
                executeAlexgramBackup(allIndices);
            } else {
                showSelectiveAccountsDialog(activeSessions);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private void showSelectiveAccountsDialog(List<AccountSessionInfo> activeSessions) {
        if (getParentActivity() == null) return;
        CharSequence[] items = new CharSequence[activeSessions.size()];
        boolean[] checked = new boolean[activeSessions.size()];
        for (int i = 0; i < activeSessions.size(); i++) {
            AccountSessionInfo s = activeSessions.get(i);
            items[i] = s.getDisplayName() + " (ID: " + s.userId + ", DC" + s.dcId + ")";
            checked[i] = true;
        }

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getParentActivity());
        builder.setTitle("Select Accounts to Export");
        builder.setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> {
            checked[which] = isChecked;
        });

        builder.setPositiveButton("Export Selected", (dialog, which) -> {
            List<Integer> selectedIndices = new ArrayList<>();
            for (int i = 0; i < activeSessions.size(); i++) {
                if (checked[i]) {
                    selectedIndices.add(activeSessions.get(i).accountIndex);
                }
            }
            if (selectedIndices.isEmpty()) {
                Toast.makeText(getParentActivity(), "Please select at least one account.", Toast.LENGTH_SHORT).show();
                return;
            }
            executeAlexgramBackup(selectedIndices);
        });

        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private void executeAlexgramBackup(List<Integer> targetIndices) {
        if (encryptBackup) {
            promptPasswordDialog(true, password -> {
                AccountSessionManager.exportSessions(getParentActivity(), targetIndices, true, password, () -> {
                    BulletinFactory.of(AccountSessionManagerActivity.this)
                            .createSimpleBulletin(R.drawable.msg_check, getString(R.string.SessionExportSuccess))
                            .show();
                });
            });
        } else {
            AccountSessionManager.exportSessions(getParentActivity(), targetIndices, false, null, () -> {
                BulletinFactory.of(AccountSessionManagerActivity.this)
                        .createSimpleBulletin(R.drawable.msg_check, getString(R.string.SessionExportSuccess))
                        .show();
            });
        }
    }

    private void performPickImportSessionFile() {
        if (getParentActivity() == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            getParentActivity().startActivityForResult(
                    Intent.createChooser(intent, getString(R.string.ImportSessionTitle)),
                    REQUEST_PICK_SESSION_FILE
            );
        } catch (Exception e) {
            Toast.makeText(getParentActivity(), getString(R.string.SessionImportError), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICK_SESSION_FILE && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            processImportFileUri(uri, null);
        }
    }

    private void processImportFileUri(Uri uri, String password) {
        AccountSessionManager.importSessionFromFile(getParentActivity(), uri, password, new AccountSessionManager.SessionImportCallback() {
            @Override
            public void onImportSuccess(int targetAccountIndex, String summary) {
                onSessionImportSuccess(targetAccountIndex, summary);
            }

            @Override
            public void onImportFailed(String errorMessage) {
                if (errorMessage != null && errorMessage.contains("Password required")) {
                    promptPasswordDialog(false, pass -> processImportFileUri(uri, pass));
                } else {
                    BulletinFactory.of(AccountSessionManagerActivity.this)
                            .createErrorBulletin(errorMessage != null ? errorMessage : getString(R.string.SessionImportError))
                            .show();
                }
            }
        });
    }

    private void onSessionImportSuccess(int targetAccountIndex, String summary) {
        if (getParentActivity() == null) return;

        // Rebuild cells so UI shows the new account
        buildCells();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.ImportSessionTitle));
        builder.setMessage("Account session imported successfully!\n\n" + summary + "\n\nWould you like to switch to this account now?");
        builder.setPositiveButton(getString(R.string.SwitchToAccount), (dialog, which) -> {
            if (targetAccountIndex >= 0) {
                switchToAccount(targetAccountIndex);
            }
        });
        builder.setNegativeButton(getString(R.string.Close), null);
        builder.show();
    }

    private void promptPasswordDialog(boolean isExport, OnPasswordEntered listener) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(isExport ? getString(R.string.SessionEncryptExport) : getString(R.string.ImportSessionTitle));

        final EditText editText = new EditText(getParentActivity());
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        editText.setHint("Enter Password");
        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        int dp20 = AndroidUtilities.dp(20);
        container.setPadding(dp20, dp20 / 2, dp20, dp20 / 2);
        container.addView(editText);
        builder.setView(container);

        builder.setPositiveButton(getString(R.string.OK), (dialog, which) -> {
            String text = editText.getText().toString();
            if (listener != null) {
                listener.onPasswordEntered(text);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private interface OnPasswordEntered {
        void onPasswordEntered(String password);
    }

    private void confirmTerminateAllSessions() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.TerminateAllOtherSessions));
        builder.setMessage(getString(R.string.TerminateAllOtherSessionsConfirm));
        builder.setPositiveButton(getString(R.string.OK), (dialog, which) -> {
            presentFragment(new SessionsActivity(SessionsActivity.TYPE_DEVICES));
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private class ListAdapter extends BaseListAdapter {
        public ListAdapter(Context context) {
            super(context);
        }
    }
}
