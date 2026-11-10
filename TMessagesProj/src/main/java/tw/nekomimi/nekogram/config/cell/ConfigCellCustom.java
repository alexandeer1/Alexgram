package tw.nekomimi.nekogram.config.cell;

import static org.telegram.messenger.LocaleController.getString;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ConfigCellCustom extends AbstractConfigCell implements WithKey {
    public static final int CUSTOM_ITEM_StickerSize = 998;
    public static final int CUSTOM_ITEM_CharBlurAlpha = 997;
    public static final int CUSTOM_ITEM_EmojiSet = 996;
    public static final int CUSTOM_ITEM_Temperature = 995;
    public static final int CUSTOM_ITEM_BlurIntensity = 994;
    public static final int CUSTOM_ITEM_AvatarCorners = 993;
    public static final int CUSTOM_ITEM_AiChatTemperature = 982;

    public final int type;
    public boolean enabled;
    private final String key;
    private final int[] searchTitleResIds;

    public ConfigCellCustom(String key, int type, boolean enabled) {
        this(key, type, enabled, new int[0]);
    }

    public ConfigCellCustom(String key, int type, boolean enabled, int... searchTitleResIds) {
        this.key = key;
        this.type = type;
        this.enabled = enabled;
        this.searchTitleResIds = searchTitleResIds;
    }

    @Override
    public List<CharSequence> getSearchTitles() {
        if (searchTitleResIds == null || searchTitleResIds.length == 0) {
            return super.getSearchTitles();
        }
        ArrayList<CharSequence> titles = new ArrayList<>();
        for (int resId : searchTitleResIds) {
            titles.add(getString(resId));
        }
        return titles;
    }

    public int getType() {
        return type;
    }

    public String getKey() {
        return this.key;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void onBindViewHolder(RecyclerView.ViewHolder holder) {
        // Not Used
    }
}
