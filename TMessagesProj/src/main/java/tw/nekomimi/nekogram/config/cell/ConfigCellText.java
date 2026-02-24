package tw.nekomimi.nekogram.config.cell;

import static org.telegram.messenger.LocaleController.getString;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ui.Cells.TextSettingsCell;

import tw.nekomimi.nekogram.config.CellGroup;

public class ConfigCellText extends AbstractConfigCell implements WithKey, WithOnClick {
    private final String key;
    private final String value;
    private final Runnable onClick;
    private boolean enabled = true;
    private TextSettingsCell cell;
    private boolean isRawText = false;

    public ConfigCellText(String key, String customValue, Runnable onClick) {
        this(key, customValue, onClick, false);
    }

    public ConfigCellText(String key, String customValue, Runnable onClick, boolean isRawText) {
        this.key = key;
        this.value = (customValue == null) ? "" : customValue;
        this.onClick = onClick;
        this.isRawText = isRawText;
    }

    public ConfigCellText(String key, Runnable onClick) {
        this(key, null, onClick);
    }

    public ConfigCellText(String key, Runnable onClick, boolean isRawText) {
        this(key, null, onClick, isRawText);
    }

    public int getType() {
        return CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL;
    }

    public String getKey() {
        return key;
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
        String title;
        if (isRawText) {
            title = key;
        } else {
            title = (key == null ? "" : getString(key));
        }
        cell.setTextAndValue(title, value, cellGroup.needSetDivider(this));
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
