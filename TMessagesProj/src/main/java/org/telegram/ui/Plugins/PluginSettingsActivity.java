package org.telegram.ui.Plugins;

import android.content.Context;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.plugins.PluginInfo;
import org.telegram.plugins.PluginsController;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One plugin's screen: an enable toggle, its metadata, the rows it declares via
 * create_settings(), and a destructive "delete" action. Settings rows are a flat model
 * (List&lt;Map&gt;) produced by the Python side, so this class stays free of Python types.
 */
public class PluginSettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_CHECK = 1;
    private static final int VIEW_TYPE_TEXT = 2;
    private static final int VIEW_TYPE_INFO = 3;
    private static final int VIEW_TYPE_SHADOW = 4;

    private final String pluginId;

    private RecyclerListView listView;
    private ListAdapter adapter;
    private List<Map<String, Object>> settingsModel = new ArrayList<>();

    private int enabledRow;
    private int metaRow;
    private int settingsStartRow;
    private int settingsEndRow;
    private int deleteRow;
    private int deleteInfoRow;
    private int rowCount;

    public PluginSettingsActivity(String pluginId) {
        this.pluginId = pluginId;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.pluginsDidLoad);
        updateRows();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.pluginsDidLoad);
    }

    private PluginInfo info() {
        return PluginsController.getInstance().findById(pluginId);
    }

    private void updateRows() {
        PluginInfo info = info();
        boolean enabled = info != null && info.enabled;
        settingsModel = enabled ? PluginsController.getInstance().getSettingsModel(pluginId) : new ArrayList<>();
        rowCount = 0;
        enabledRow = rowCount++;
        metaRow = rowCount++;
        if (!settingsModel.isEmpty()) {
            settingsStartRow = rowCount;
            rowCount += settingsModel.size();
            settingsEndRow = rowCount;
        } else {
            settingsStartRow = settingsEndRow = -1;
        }
        deleteRow = rowCount++;
        deleteInfoRow = rowCount++;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        PluginInfo info = info();
        actionBar.setTitle(info != null ? info.displayName() : LocaleController.getString("Plugin", R.string.Plugin));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(adapter = new ListAdapter(context));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener(this::onRowClick);
        return fragmentView;
    }

    private void onRowClick(View view, int position) {
        if (position == enabledRow) {
            PluginInfo info = info();
            if (info == null) {
                return;
            }
            boolean newValue = !info.enabled;
            PluginsController.getInstance().setEnabled(pluginId, newValue);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(newValue);
            }
            return;
        }
        if (position == deleteRow) {
            confirmDelete();
            return;
        }
        if (position >= settingsStartRow && position < settingsEndRow) {
            Map<String, Object> m = settingsModel.get(position - settingsStartRow);
            handleSettingClick(view, m);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleSettingClick(View view, Map<String, Object> m) {
        String type = String.valueOf(m.get("type"));
        String key = m.get("key") != null ? m.get("key").toString() : null;
        switch (type) {
            case "switch": {
                boolean newValue = !asBool(m.get("value"));
                m.put("value", newValue);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(newValue);
                }
                PluginsController.getInstance().onSettingChange(pluginId, key, newValue);
                break;
            }
            case "input":
                showInputDialog(m);
                break;
            case "selector":
                showSelectorDialog(m);
                break;
            case "text":
                int index = asInt(m.get("index"), 0);
                PluginsController.getInstance().onSettingClick(pluginId, index, view);
                break;
            default:
                break;
        }
    }

    private void showInputDialog(Map<String, Object> m) {
        Context ctx = getParentActivity();
        if (ctx == null) {
            return;
        }
        final String key = m.get("key") != null ? m.get("key").toString() : null;
        String current = m.get("value") == null ? "" : m.get("value").toString();

        EditTextBoldCursor edit = new EditTextBoldCursor(ctx);
        edit.setText(current);
        try {
            edit.setSelection(current.length());
        } catch (Throwable ignore) {
        }
        edit.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        edit.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        edit.setBackground(Theme.createEditTextDrawable(ctx, true));
        edit.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6));
        edit.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout container = new FrameLayout(ctx);
        container.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL, 24, 4, 24, 4));

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(String.valueOf(m.get("text")));
        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
            String value = edit.getText() != null ? edit.getText().toString() : "";
            m.put("value", value);
            PluginsController.getInstance().onSettingChange(pluginId, key, value);
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            dialog.dismiss();
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), (dialog, which) -> dialog.dismiss());
        showDialog(builder.create());
        edit.requestFocus();
    }

    @SuppressWarnings("unchecked")
    private void showSelectorDialog(Map<String, Object> m) {
        Context ctx = getParentActivity();
        if (ctx == null) {
            return;
        }
        final String key = m.get("key") != null ? m.get("key").toString() : null;
        List<Object> options = m.get("options") instanceof List ? (List<Object>) m.get("options") : new ArrayList<>();
        CharSequence[] arr = new CharSequence[options.size()];
        for (int i = 0; i < options.size(); i++) {
            arr[i] = String.valueOf(options.get(i));
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(String.valueOf(m.get("text")));
        builder.setItems(arr, (DialogInterface.OnClickListener) (dialog, which) -> {
            m.put("value", which);
            PluginsController.getInstance().onSettingChange(pluginId, key, which);
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        });
        showDialog(builder.create());
    }

    private void confirmDelete() {
        Context ctx = getParentActivity();
        if (ctx == null) {
            return;
        }
        PluginInfo info = info();
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(LocaleController.getString("DeletePluginTitle", R.string.DeletePluginTitle));
        builder.setMessage(LocaleController.formatString("DeletePluginAlert", R.string.DeletePluginAlert, info != null ? info.displayName() : pluginId));
        builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialog, which) -> {
            PluginsController.getInstance().deletePlugin(pluginId);
            dialog.dismiss();
            finishFragment();
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), (dialog, which) -> dialog.dismiss());
        showDialog(builder.create());
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.pluginsDidLoad) {
            updateRows();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
    }

    private int resolveIcon(Object name) {
        if (!(name instanceof String) || getParentActivity() == null) {
            return 0;
        }
        try {
            return getParentActivity().getResources().getIdentifier(
                    (String) name, "drawable", getParentActivity().getPackageName());
        } catch (Throwable t) {
            return 0;
        }
    }

    private static boolean asBool(Object o) {
        return o instanceof Boolean ? (Boolean) o : Boolean.parseBoolean(String.valueOf(o));
    }

    private static int asInt(Object o, int def) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Throwable t) {
            return def;
        }
    }

    private static String asStr(Object o) {
        return o == null ? "" : o.toString();
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == VIEW_TYPE_CHECK || type == VIEW_TYPE_TEXT;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == enabledRow) {
                return VIEW_TYPE_CHECK;
            }
            if (position == metaRow || position == deleteInfoRow) {
                return VIEW_TYPE_INFO;
            }
            if (position == deleteRow) {
                return VIEW_TYPE_TEXT;
            }
            if (position >= settingsStartRow && position < settingsEndRow) {
                Map<String, Object> m = settingsModel.get(position - settingsStartRow);
                String type = String.valueOf(m.get("type"));
                switch (type) {
                    case "header":
                        return VIEW_TYPE_HEADER;
                    case "switch":
                        return VIEW_TYPE_CHECK;
                    case "divider":
                        return asStr(m.get("text")).isEmpty() ? VIEW_TYPE_SHADOW : VIEW_TYPE_INFO;
                    default:
                        return VIEW_TYPE_TEXT;
                }
            }
            return VIEW_TYPE_SHADOW;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_HEADER:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_CHECK:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_TEXT:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_SHADOW:
                    view = new ShadowSectionCell(mContext);
                    break;
                default:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            int viewType = holder.getItemViewType();
            if (position == enabledRow) {
                PluginInfo info = info();
                ((TextCheckCell) holder.itemView).setTextAndCheck(
                        LocaleController.getString("EnabledToggle", R.string.EnabledToggle), info != null && info.enabled, true);
                return;
            }
            if (position == metaRow) {
                ((TextInfoPrivacyCell) holder.itemView).setText(metaText());
                return;
            }
            if (position == deleteRow) {
                TextCell cell = (TextCell) holder.itemView;
                cell.setTextAndIcon(LocaleController.getString("DeletePlugin", R.string.DeletePlugin), R.drawable.msg_delete, false);
                cell.setColors(Theme.key_text_RedRegular, Theme.key_text_RedRegular);
                cell.setSubtitle(""); // clear any subtitle left over from a recycled TextCell
                return;
            }
            if (position == deleteInfoRow) {
                ((TextInfoPrivacyCell) holder.itemView).setText(
                        LocaleController.getString("DeletePluginInfo", R.string.DeletePluginInfo));
                return;
            }
            // settings model rows
            Map<String, Object> m = settingsModel.get(position - settingsStartRow);
            String type = String.valueOf(m.get("type"));
            boolean dividerBelow = (position + 1) < settingsEndRow;
            switch (type) {
                case "header":
                    ((HeaderCell) holder.itemView).setText(asStr(m.get("text")));
                    break;
                case "switch": {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    String subtext = asStr(m.get("subtext"));
                    if (subtext.isEmpty()) {
                        cell.setTextAndCheck(asStr(m.get("text")), asBool(m.get("value")), dividerBelow);
                    } else {
                        cell.setTextAndValueAndCheck(asStr(m.get("text")), subtext, asBool(m.get("value")), true, dividerBelow);
                    }
                    break;
                }
                case "divider":
                    if (viewType == VIEW_TYPE_INFO) {
                        ((TextInfoPrivacyCell) holder.itemView).setText(asStr(m.get("text")));
                    }
                    break;
                case "input": {
                    TextCell cell = (TextCell) holder.itemView;
                    cell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                    int inputIcon = resolveIcon(m.get("icon"));
                    if (inputIcon != 0) {
                        cell.setTextAndValueAndIcon(asStr(m.get("text")), asStr(m.get("value")), inputIcon, dividerBelow);
                    } else {
                        cell.setTextAndValue(asStr(m.get("text")), asStr(m.get("value")), dividerBelow);
                    }
                    cell.setSubtitle(asStr(m.get("subtext"))); // always set so a recycled cell can't keep a stale subtitle
                    break;
                }
                case "selector": {
                    TextCell cell = (TextCell) holder.itemView;
                    cell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                    List<?> options = m.get("options") instanceof List ? (List<?>) m.get("options") : null;
                    int value = asInt(m.get("value"), 0);
                    String shown = options != null && value >= 0 && value < options.size()
                            ? String.valueOf(options.get(value)) : "";
                    cell.setTextAndValue(asStr(m.get("text")), shown, dividerBelow);
                    cell.setSubtitle(asStr(m.get("subtext"))); // show subtext and clear on recycle
                    break;
                }
                default: { // "text"
                    TextCell cell = (TextCell) holder.itemView;
                    boolean red = asBool(m.get("red"));
                    boolean accent = asBool(m.get("accent"));
                    int colorKey = red ? Theme.key_text_RedRegular
                            : (accent ? Theme.key_windowBackgroundWhiteBlueText : Theme.key_windowBackgroundWhiteBlackText);
                    cell.setColors(red ? Theme.key_text_RedRegular : Theme.key_windowBackgroundWhiteGrayIcon, colorKey);
                    int icon = resolveIcon(m.get("icon"));
                    if (icon != 0) {
                        cell.setTextAndIcon(asStr(m.get("text")), icon, dividerBelow);
                    } else {
                        cell.setText(asStr(m.get("text")), dividerBelow);
                    }
                    cell.setSubtitle(asStr(m.get("subtext"))); // unconditional: avoid a stale subtitle on recycle
                    break;
                }
            }
        }
    }

    private String metaText() {
        PluginInfo info = info();
        if (info == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (info.description != null && info.description.length() > 0) {
            sb.append(info.description);
        }
        if (info.author != null && info.author.length() > 0) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(LocaleController.formatString("Author", R.string.Author, info.author));
        }
        if (info.version != null && info.version.length() > 0) {
            sb.append(sb.length() > 0 ? "\n" : "").append(LocaleController.formatString("Version", R.string.Version, info.version));
        }
        if (info.error != null) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("⚠ ").append(info.error);
        }
        return sb.toString();
    }
}
