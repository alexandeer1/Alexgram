// [Alexgram: Accounts Settings] - Start
package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UndoView;

import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.cell.AbstractConfigCell;
import tw.nekomimi.nekogram.config.cell.ConfigCellDivider;
import tw.nekomimi.nekogram.config.cell.ConfigCellHeader;
import tw.nekomimi.nekogram.config.cell.ConfigCellNumberPicker;
import tw.nekomimi.nekogram.config.cell.ConfigCellText;
import tw.nekomimi.nekogram.helpers.HiddenAccountsController;
import tw.nekomimi.nekogram.ui.HiddenAccountsPasscodeActivity;
import xyz.nextalone.nagram.NaConfig;

@SuppressLint("RtlHardcoded")
@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class AccountsSettingsActivity extends BaseNekoXSettingsActivity {

    private ListAdapter listAdapter;

    private final CellGroup cellGroup = new CellGroup(this);

    // Account Limits section
    private final AbstractConfigCell headerLimits = cellGroup.appendCell(
            new ConfigCellHeader(getString(R.string.AccountLimitsHeader)));

    private final AbstractConfigCell maxAccountCountRow = cellGroup.appendCell(
            new ConfigCellNumberPicker("MaxAccountCount",
                    NaConfig.INSTANCE.getMaxAccountCount(), 1, 100));

    private final AbstractConfigCell maxActiveAccountsRow = cellGroup.appendCell(
            new ConfigCellNumberPicker("MaxActiveAccounts",
                    NaConfig.INSTANCE.getMaxActiveAccounts(), 1, 100));

    private final AbstractConfigCell dividerLimits = cellGroup.appendCell(new ConfigCellDivider());

    // Startup Performance section
    private final AbstractConfigCell headerStartup = cellGroup.appendCell(
            new ConfigCellHeader(getString(R.string.StartupPerformanceHeader)));

    private final AbstractConfigCell startupActiveAccountsRow = cellGroup.appendCell(
            new ConfigCellNumberPicker("StartupActiveAccounts",
                    NaConfig.INSTANCE.getStartupActiveAccounts(), 1, 100));

    private final AbstractConfigCell dividerStartup = cellGroup.appendCell(new ConfigCellDivider());

    // [Alexgram: Hidden Accounts] - Start
    // Hidden Accounts section
    private final AbstractConfigCell headerHidden = cellGroup.appendCell(
            new ConfigCellHeader(getString(R.string.HiddenAccountsHeader)));

    private final AbstractConfigCell hiddenAccountsRow = cellGroup.appendCell(
            new ConfigCellText("HiddenAccountsTitle", () -> {
                // Resolved at click time inside onClick, but we need the fragment reference
            }));

    private final AbstractConfigCell dividerHidden = cellGroup.appendCell(new ConfigCellDivider());
    // [Alexgram: Hidden Accounts] - End

    // [Alexgram: Session Management] - Start
    // Session Management section (placed at the last position)
    private final AbstractConfigCell headerSession = cellGroup.appendCell(
            new ConfigCellHeader(getString(R.string.SessionManagementHeader)));

    private final AbstractConfigCell sessionManagementRow = cellGroup.appendCell(
            new ConfigCellText("SessionManagementTitle", () -> {}));

    private final AbstractConfigCell dividerSession = cellGroup.appendCell(new ConfigCellDivider());
    // [Alexgram: Session Management] - End

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
        return "accounts";
    }

    @Override
    public String getTitle() {
        return getString(R.string.AccountsSettings);
    }

    @Override
    public View createView(Context context) {
        View superView = super.createView(context);

        listAdapter = new ListAdapter(context);
        listView.setAdapter(listAdapter);
        listView.invalidateItemDecorations();

        setupDefaultListeners();
        addRowsToMap(cellGroup);

        cellGroup.callBackSettingsChanged = (key, newValue) -> {
            if (key.equals(NaConfig.INSTANCE.getMaxAccountCount().getKey())
                    || key.equals(NaConfig.INSTANCE.getMaxActiveAccounts().getKey())
                    || key.equals(NaConfig.INSTANCE.getStartupActiveAccounts().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            }
        };

        return superView;
    }

    @Override
    protected void handleCellClick(View view, int position, float x, float y) {
        // [Alexgram: Hidden Accounts] - Start
        // Intercept Hidden Accounts row click before base class handles it
        if (position == cellGroup.rows.indexOf(hiddenAccountsRow)) {
            HiddenAccountsController ctrl = HiddenAccountsController.getInstance();
            if (ctrl.hasPin()) {
                presentFragment(new HiddenAccountsPasscodeActivity(
                        HiddenAccountsPasscodeActivity.MODE_UNLOCK_SETTINGS));
            } else {
                presentFragment(new HiddenAccountsPasscodeActivity(
                        HiddenAccountsPasscodeActivity.MODE_SETUP_PIN));
            }
            return;
        }
        // [Alexgram: Hidden Accounts] - End

        // [Alexgram: Session Management] - Start
        if (position == cellGroup.rows.indexOf(sessionManagementRow)) {
            presentFragment(new AccountSessionManagerActivity());
            return;
        }
        // [Alexgram: Session Management] - End

        super.handleCellClick(view, position, x, y);
    }

    private class ListAdapter extends BaseListAdapter {
        public ListAdapter(Context context) {
            super(context);
        }
    }
}
// [Alexgram: Accounts Settings] - End