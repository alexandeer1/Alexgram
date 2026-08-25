package tw.nekomimi.nekogram.config.cell;

import static org.telegram.messenger.LocaleController.getString;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ui.Cells.TextSettingsCell;

import tw.nekomimi.nekogram.config.CellGroup;

public class ConfigCellText extends AbstractConfigCell implements WithKey, WithOnClick {
    private String key;
    private String customTitle;
    private String value;
    private Runnable onClick;
    private boolean enabled = true;
    private TextSettingsCell cell;

    public ConfigCellText(String key, String customValue, Runnable onClick) {
        this.key = key;
        this.value = (customValue == null) ? "" : customValue;
        this.onClick = onClick;
    }

    public ConfigCellText(String key, Runnable onClick) {
        this(key, null, onClick);
    }

    public ConfigCellText(String customTitle, String customValue, boolean isLiteralTitle, Runnable onClick) {
        this.key = null;
        this.customTitle = customTitle;
        this.value = (customValue == null) ? "" : customValue;
        this.onClick = onClick;
    }

    public void setValue(String value) {
        this.value = (value == null) ? "" : value;
        if (this.cell != null) {
            String titleStr = customTitle != null ? customTitle : (key != null ? getString(key) : "");
            this.cell.setTextAndValue(titleStr, this.value, false, cellGroup != null && cellGroup.needSetDivider(this), true);
        }
    }

    public int getType() {
        return CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL;
    }

    public String getKey() {
        return key != null ? key : (customTitle != null ? customTitle : "");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (this.cell != null) this.cell.setEnabled(this.enabled);
    }

    public void onBindViewHolder(RecyclerView.ViewHolder holder) {
        TextSettingsCell cell = (TextSettingsCell) holder.itemView;
        this.cell = cell;
        String titleStr = customTitle != null ? customTitle : (key != null ? getString(key) : "");
        cell.setTextAndValue(titleStr, value, false, cellGroup.needSetDivider(this), true);
        cell.setEnabled(enabled);
    }

    public void onClick() {
        if (!enabled) return;
        if (onClick != null) {
            try {
                onClick.run();
            } catch (Exception ignored) {}
        }
    }
}
