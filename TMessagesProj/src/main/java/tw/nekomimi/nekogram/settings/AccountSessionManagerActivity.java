package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.CameraScanActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.SessionsActivity;

import java.util.ArrayList;
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
    private AbstractConfigCell dividerActive;

    // Backup & Restore Section
    private AbstractConfigCell headerBackup;
    private AbstractConfigCell exportSessionsRow;
    private AbstractConfigCell importSessionRow;
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
        // 1. Active Account Sessions Section
        headerActive = cellGroup.appendCell(
                new ConfigCellHeader(getString(R.string.ActiveSessionsSection)));

        List<AccountSessionInfo> sessions = AccountSessionManager.getActiveAccountSessions();
        activeSessionRows.clear();
        for (AccountSessionInfo session : sessions) {
            String valueText = "DC" + session.dcId + " • " + (session.phone != null && !session.phone.isEmpty() ? session.phone : "ID: " + session.userId);
            AbstractConfigCell cell = cellGroup.appendCell(
                    new ConfigCellText(session.getDisplayName(), valueText, true, () -> {})
            );
            activeSessionRows.add(cell);
        }
        dividerActive = cellGroup.appendCell(new ConfigCellDivider());

        // 2. Backup & Restore Section
        headerBackup = cellGroup.appendCell(
                new ConfigCellHeader(getString(R.string.SessionBackupSection)));

        exportSessionsRow = cellGroup.appendCell(
                new ConfigCellText("ExportSessionsTitle", () -> {}));

        importSessionRow = cellGroup.appendCell(
                new ConfigCellText("ImportSessionTitle", () -> {}));

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
                showAccountSessionDetails(i);
                return;
            }
        }

        if (position == cellGroup.rows.indexOf(exportSessionsRow)) {
            performExportSessionBackup();
        } else if (position == cellGroup.rows.indexOf(importSessionRow)) {
            performPickImportSessionFile();
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
                        // Handled by camera sheet handler
                    }
                });
            }
        } else if (position == cellGroup.rows.indexOf(terminateAllRow)) {
            confirmTerminateAllSessions();
        } else {
            super.handleCellClick(view, position, x, y);
        }
    }

    private void showAccountSessionDetails(int index) {
        List<AccountSessionInfo> sessions = AccountSessionManager.getActiveAccountSessions();
        if (index < 0 || index >= sessions.size()) return;
        AccountSessionInfo info = sessions.get(index);

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(info.getDisplayName());
        String message = "Account Index: " + info.accountIndex + "\n" +
                "User ID: " + info.userId + "\n" +
                "Phone: " + (info.phone != null ? info.phone : "N/A") + "\n" +
                "Username: " + (info.username != null ? "@" + info.username : "N/A") + "\n" +
                "Datacenter: DC" + info.dcId + "\n" +
                "Premium: " + (info.isPremium ? "Yes" : "No");
        builder.setMessage(message);

        builder.setPositiveButton(getString(R.string.SwitchToAccount), (dialog, which) -> {
            if (info.accountIndex != UserConfig.selectedAccount) {
                UserConfig.selectedAccount = info.accountIndex;
                UserConfig.getInstance(info.accountIndex).saveConfig(false);
                if (getParentActivity() != null) {
                    getParentActivity().recreate();
                }
            }
        });

        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private void performExportSessionBackup() {
        List<AccountSessionInfo> activeSessions = AccountSessionManager.getActiveAccountSessions();
        if (activeSessions.isEmpty()) {
            Toast.makeText(getParentActivity(), "No active sessions found to export.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (activeSessions.size() == 1) {
            executeExport(java.util.Collections.singletonList(activeSessions.get(0).accountIndex));
            return;
        }

        CharSequence[] options = new CharSequence[]{"Export All Active Accounts (" + activeSessions.size() + ")", "Select Specific Accounts..."};

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.ExportSessionsTitle));
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                List<Integer> allIndices = new ArrayList<>();
                for (AccountSessionInfo s : activeSessions) {
                    allIndices.add(s.accountIndex);
                }
                executeExport(allIndices);
            } else {
                showSelectiveAccountDialog(activeSessions);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private void showSelectiveAccountDialog(List<AccountSessionInfo> activeSessions) {
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
            executeExport(selectedIndices);
        });

        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private void executeExport(List<Integer> targetIndices) {
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
            processImportUri(uri, null);
        }
    }

    private void processImportUri(Uri uri, String password) {
        AccountSessionManager.importSessionFromUri(getParentActivity(), uri, password, new AccountSessionManager.SessionImportCallback() {
            @Override
            public void onImportSuccess(int targetAccountIndex, String summary) {
                if (getParentActivity() == null) return;
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(getString(R.string.ImportSessionTitle));
                builder.setMessage("Account session imported successfully!\n\n" + summary + "\nWould you like to switch to this account now?");
                builder.setPositiveButton(getString(R.string.SwitchToAccount), (dialog, which) -> {
                    if (targetAccountIndex >= 0) {
                        UserConfig.selectedAccount = targetAccountIndex;
                        UserConfig.getInstance(targetAccountIndex).saveConfig(false);
                        UserConfig.getInstance(0).saveConfig(false);
                        if (getParentActivity() != null) {
                            Intent intent = new Intent(getParentActivity(), org.telegram.ui.LaunchActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            getParentActivity().startActivity(intent);
                            getParentActivity().finish();
                        }
                    }
                });
                builder.setNegativeButton(getString(R.string.Cancel), null);
                builder.show();
            }

            @Override
            public void onImportFailed(String errorMessage) {
                if (errorMessage != null && errorMessage.contains("Password required")) {
                    promptPasswordDialog(false, pass -> processImportUri(uri, pass));
                } else {
                    BulletinFactory.of(AccountSessionManagerActivity.this)
                            .createErrorBulletin(errorMessage != null ? errorMessage : getString(R.string.SessionImportError))
                            .show();
                }
            }
        });
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
