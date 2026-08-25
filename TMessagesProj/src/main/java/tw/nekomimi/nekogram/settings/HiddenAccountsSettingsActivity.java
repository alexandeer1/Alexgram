// [Alexgram: Hidden Accounts] - Start
package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

import tw.nekomimi.nekogram.helpers.HiddenAccountsController;
import tw.nekomimi.nekogram.ui.HiddenAccountsPasscodeActivity;

/**
 * Settings screen for managing hidden accounts.
 * Only reachable after PIN unlock via HiddenAccountsPasscodeActivity.
 */
@SuppressLint({"RtlHardcoded", "NotifyDataSetChanged"})
public class HiddenAccountsSettingsActivity extends BaseFragment {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private final ArrayList<Integer> accounts = new ArrayList<>();

    // Row indices
    private int rowCount;
    private int accountsHeaderRow;
    private int accountsStartRow;
    private int accountsEndRow;
    private int divider1Row;
    private int pinHeaderRow;
    private int changePinRow;
    private int removePinRow;
    private int pinFooterRow;
    private int autoLockHeaderRow;
    private int autoLockRow;
    private int autoLockFooterRow;

    @Override
    public boolean onFragmentCreate() {
        buildAccountList();
        updateRows();
        return super.onFragmentCreate();
    }

    private void buildAccountList() {
        accounts.clear();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (AccountInstance.getInstance(a).getUserConfig().isClientActivated()) {
                accounts.add(a);
            }
        }
    }

    private void updateRows() {
        rowCount = 0;
        accountsHeaderRow = rowCount++;
        accountsStartRow = rowCount;
        rowCount += accounts.size();
        accountsEndRow = rowCount++;
        divider1Row = rowCount++;
        pinHeaderRow = rowCount++;
        changePinRow = rowCount++;
        removePinRow = rowCount++;
        pinFooterRow = rowCount++;
        autoLockHeaderRow = rowCount++;
        autoLockRow = rowCount++;
        autoLockFooterRow = rowCount++;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getString(R.string.HiddenAccountsTitle));
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        FrameLayout frame = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(context, androidx.recyclerview.widget.LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listAdapter = new ListAdapter(context);
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position, x, y) -> onRowClick(view, position));

        return fragmentView;
    }

    private void onRowClick(View view, int position) {
        if (position >= accountsStartRow && position < accountsStartRow + accounts.size()) {
            int account = accounts.get(position - accountsStartRow);
            HiddenAccountsController ctrl = HiddenAccountsController.getInstance();
            boolean isHidden = ctrl.isAccountHidden(account);
            ctrl.setAccountHidden(account, !isHidden);
            if (view instanceof AccountToggleCell) {
                ((AccountToggleCell) view).setChecked(!isHidden);
            }
            return;
        }

        if (position == changePinRow) {
            presentFragment(new HiddenAccountsPasscodeActivity(HiddenAccountsPasscodeActivity.MODE_CHANGE_PIN));
            return;
        }

        if (position == removePinRow) {
            AlertDialog dlg = new AlertDialog.Builder(getParentActivity())
                    .setTitle(getString(R.string.HiddenAccountsRemovePIN))
                    .setMessage(getString(R.string.HiddenAccountsRemovePINConfirm))
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .setPositiveButton(getString(R.string.OK), (d, w) -> {
                        HiddenAccountsController.getInstance().removePin();
                        BulletinFactory.of(this)
                                .createSimpleBulletin(R.raw.done, "PIN removed, all accounts visible")
                                .show();
                        finishFragment();
                    }).create();
            showDialog(dlg);
            android.widget.TextView btn = (android.widget.TextView) dlg.getButton(Dialog.BUTTON_POSITIVE);
            if (btn != null) btn.setTextColor(Theme.getColor(Theme.key_dialogTextRed));
            return;
        }

        if (position == autoLockRow) {
            HiddenAccountsController ctrl = HiddenAccountsController.getInstance();
            boolean newVal = !ctrl.isAutoLockEnabled();
            ctrl.setAutoLockEnabled(newVal);
            if (view instanceof TextCheckCell) ((TextCheckCell) view).setChecked(newVal);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context ctx;
        ListAdapter(Context c) { this.ctx = c; }

        @Override public int getItemCount() { return rowCount; }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int pos = holder.getAdapterPosition();
            return pos >= accountsStartRow && pos < accountsStartRow + accounts.size()
                    || pos == changePinRow || pos == removePinRow || pos == autoLockRow;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == accountsHeaderRow || position == pinHeaderRow || position == autoLockHeaderRow) return 0; // header
            if (position == accountsEndRow || position == pinFooterRow || position == autoLockFooterRow) return 1;    // info
            if (position == divider1Row) return 2;                                                                     // shadow
            if (position == changePinRow || position == removePinRow) return 3;                                        // text settings
            if (position == autoLockRow) return 4;                                                                     // check
            if (position >= accountsStartRow && position < accountsStartRow + accounts.size()) return 5;               // account toggle
            return 3;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0: view = new HeaderCell(ctx); break;
                case 1: view = new TextInfoPrivacyCell(ctx); break;
                case 2: view = new ShadowSectionCell(ctx); break;
                case 3: view = new TextSettingsCell(ctx); break;
                case 4: view = new TextCheckCell(ctx); break;
                case 5: view = new AccountToggleCell(ctx); break;
                default: view = new TextSettingsCell(ctx); break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerView.ViewHolder(view) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == accountsHeaderRow)   cell.setText(getString(R.string.HiddenAccountsSection));
                    else if (position == pinHeaderRow)   cell.setText(getString(R.string.HiddenAccountsPINManagement));
                    else if (position == autoLockHeaderRow) cell.setText("Auto-lock");
                    break;
                }
                case 1: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setBackground(Theme.getThemedDrawable(ctx,
                            position == autoLockFooterRow ? R.drawable.greydivider_bottom : R.drawable.greydivider,
                            Theme.key_windowBackgroundGrayShadow));
                    if (position == accountsEndRow)
                        cell.setText(getString(R.string.HiddenAccountsDesc));
                    else if (position == pinFooterRow)
                        cell.setText("Your PIN is stored as a salted hash and cannot be recovered. Keep it safe.");
                    else if (position == autoLockFooterRow)
                        cell.setText(getString(R.string.HiddenAccountsAutoLockDesc));
                    break;
                }
                case 2: {
                    holder.itemView.setBackground(Theme.getThemedDrawable(ctx, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
                case 3: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (position == changePinRow) {
                        cell.setText(getString(R.string.HiddenAccountsChangePIN), true);
                        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    } else if (position == removePinRow) {
                        cell.setText(getString(R.string.HiddenAccountsRemovePIN), false);
                        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText3));
                    }
                    break;
                }
                case 4: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    cell.setTextAndCheck(getString(R.string.HiddenAccountsAutoLock),
                            HiddenAccountsController.getInstance().isAutoLockEnabled(), false);
                    break;
                }
                case 5: {
                    AccountToggleCell cell = (AccountToggleCell) holder.itemView;
                    int account = accounts.get(position - accountsStartRow);
                    boolean isLast = (position == accountsStartRow + accounts.size() - 1);
                    cell.bind(account, !isLast);
                    break;
                }
            }
        }
    }

    // ── AccountToggleCell ─────────────────────────────────────────────────────

    private class AccountToggleCell extends FrameLayout {
        private final BackupImageView avatarView;
        private final android.widget.TextView nameView;
        private final android.widget.TextView phoneView;
        private final android.widget.ImageView lockIcon;
        private final android.widget.ImageView checkView;
        private final AvatarDrawable avatarDrawable;
        private final android.graphics.Paint dividerPaint = new android.graphics.Paint();
        private boolean showDivider;
        private boolean checked;

        AccountToggleCell(Context context) {
            super(context);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            setWillNotDraw(false);
            dividerPaint.setColor(Theme.getColor(Theme.key_divider));
            dividerPaint.setStrokeWidth(1);

            avatarDrawable = new AvatarDrawable();
            avatarDrawable.setTextSize(AndroidUtilities.dp(14));

            avatarView = new BackupImageView(context);
            avatarView.setRoundRadius(org.telegram.messenger.AvatarCornerHelper.getAvatarRoundRadius(42f));
            addView(avatarView, LayoutHelper.createFrame(42, 42, Gravity.LEFT | Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

            nameView = new android.widget.TextView(context);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            nameView.setTypeface(AndroidUtilities.bold());
            nameView.setLines(1); nameView.setMaxLines(1); nameView.setSingleLine(true);
            nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(nameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 74, 11, 60, 0));

            phoneView = new android.widget.TextView(context);
            phoneView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            phoneView.setLines(1); phoneView.setMaxLines(1); phoneView.setSingleLine(true);
            phoneView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            phoneView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            addView(phoneView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 74, 33, 60, 0));

            lockIcon = new android.widget.ImageView(context);
            lockIcon.setImageResource(R.drawable.msg_secret);
            lockIcon.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
            lockIcon.setColorFilter(new PorterDuffColorFilter(0xFFFFAB00, PorterDuff.Mode.SRC_IN));
            lockIcon.setVisibility(View.GONE);
            addView(lockIcon, LayoutHelper.createFrame(20, 20, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 48, 0));

            checkView = new android.widget.ImageView(context);
            checkView.setImageResource(R.drawable.account_check);
            checkView.setScaleType(android.widget.ImageView.ScaleType.CENTER);
            checkView.setColorFilter(new PorterDuffColorFilter(0xFFFFAB00, PorterDuff.Mode.MULTIPLY));
            checkView.setVisibility(View.GONE);
            addView(checkView, LayoutHelper.createFrame(40, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.TOP, 0, 0, 6, 0));
        }

        void bind(int account, boolean divider) {
            showDivider = divider;
            checked = HiddenAccountsController.getInstance().isAccountHidden(account);
            TLRPC.User user = AccountInstance.getInstance(account).getUserConfig().getCurrentUser();
            if (user != null) {
                avatarDrawable.setInfo(account, user);
                avatarView.setForUserOrChat(user, avatarDrawable);
                String name = ContactsController.formatName(user.first_name, user.last_name);
                nameView.setText(name.isEmpty() ? "Account " + account : name);
                phoneView.setText(user.phone != null ? "+" + user.phone : "");
            } else {
                nameView.setText("Account " + account);
                phoneView.setText("");
            }
            lockIcon.setVisibility(checked ? View.VISIBLE : View.GONE);
            checkView.setVisibility(checked ? View.GONE : View.VISIBLE);
            invalidate();
        }

        void setChecked(boolean c) {
            checked = c;
            lockIcon.setVisibility(checked ? View.VISIBLE : View.GONE);
            checkView.setVisibility(checked ? View.GONE : View.VISIBLE);
        }

        @Override
        protected void onMeasure(int w, int h) {
            super.onMeasure(w, android.view.View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64), android.view.View.MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (showDivider) {
                canvas.drawLine(AndroidUtilities.dp(74), getHeight() - 1, getWidth(), getHeight() - 1, dividerPaint);
            }
        }
    }

    // ── Helper import ─────────────────────────────────────────────────────────
    private static int TypedValue_COMPLEX_UNIT_DIP = TypedValue.COMPLEX_UNIT_DIP;
    private static class TypedValue extends android.util.TypedValue {}
}
// [Alexgram: Hidden Accounts] - End