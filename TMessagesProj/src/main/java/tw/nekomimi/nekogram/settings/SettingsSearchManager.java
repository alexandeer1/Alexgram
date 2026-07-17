package tw.nekomimi.nekogram.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.AIAssistanceSettingsActivity;
import org.telegram.ui.ActionBar.BaseFragment;

import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.cell.AbstractConfigCell;
import tw.nekomimi.nekogram.config.cell.ConfigCellCheckBox;
import tw.nekomimi.nekogram.config.cell.ConfigCellSelectBox;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck2;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheckIcon;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextDetail;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextInput;

public class SettingsSearchManager {

    public static class SearchItem {
        public String title;
        public String subtitle;
        public String key;
        public Class<? extends BaseFragment> fragmentClass;
        public String category;
        public int iconRes;

        public SearchItem(String title, String subtitle, String key, Class<? extends BaseFragment> fragmentClass, String category, int iconRes) {
            this.title = title == null ? "" : title;
            this.subtitle = subtitle;
            this.key = key;
            this.fragmentClass = fragmentClass;
            this.category = category;
            this.iconRes = iconRes;
        }
    }

    private static SettingsSearchManager instance;
    private final List<SearchItem> index = java.util.Collections.synchronizedList(new ArrayList<>());
    private boolean isIndexing = false;

    public static SettingsSearchManager getInstance() {
        if (instance == null) {
            instance = new SettingsSearchManager();
        }
        return instance;
    }

    private SettingsSearchManager() {
        // Perform fast indexing synchronously so history works immediately
        fastIndex(index);
        reloadIndex();
    }

    public boolean isIndexing() {
        return isIndexing;
    }

    public void reloadIndex() {
        if (isIndexing) return;
        isIndexing = true;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                loadIndex();
            } finally {
                isIndexing = false;
            }
        });
    }

    private void fastIndex(List<SearchItem> target) {
        add(target, "Ghost Mode", "Read messages without sending read receipts", "ghost_mode", NekoSettingsActivity.class, "Privacy", R.drawable.msg_secret);
        add(target, "Hide Contacts", "Remove the contacts tab from the main drawer", "hide_contacts", NekoSettingsActivity.class, "Privacy", R.drawable.msg_contacts);
        add(target, "Save Deleted Messages", "Automatically keep copies of deleted messages", "save_deleted", NekoSettingsActivity.class, "Privacy", R.drawable.msg_delete_solar);
        add(target, "Hidden Chats", "Secure your private conversations with a passcode", "hidden_chats", NekoSettingsActivity.class, "Privacy", R.drawable.msg_permissions);
        add(target, "Enable AI Assistant", "Activate the floating Alexgram AI companion", "assistant_enabled", AIAssistanceSettingsActivity.class, "AI", R.drawable.settings_chat);
        add(target, "AI Character Skin", "Change the visual appearance of the AI assistant", "character_skin", AIAssistanceSettingsActivity.class, "AI", R.drawable.msg_theme);
        add(target, "Assistant Persona", "Choose between different AI personalities", "persona_preset", AIAssistanceSettingsActivity.class, "AI", R.drawable.msg_contacts);
        add(target, "AI Background Animation", "Toggle the animated background in AI settings", "background_animation", AIAssistanceSettingsActivity.class, "AI", R.drawable.msg_sticker);
        add(target, "Particle Effects", "Show dynamic particles on AI interaction", "particle_effects", AIAssistanceSettingsActivity.class, "AI", R.drawable.msg_info);
        // [Alexgram: Friends Activities] - Start
        add(target, "Friends Activities", "Spy on Friends — see contacts' recent messages in shared groups and channels", "FriendsActivities", NekoCustomizationSettingsActivity.class, "Customization", R.drawable.msg_groups);
        // [Alexgram: Friends Activities] - End
    }

    private void loadIndex() {
        List<SearchItem> nextIndex = new ArrayList<>();
        
        // --- MANUAL INDEXING ---
        fastIndex(nextIndex);

        // --- AUTOMATIC INDEXING (NAGRAM SETTINGS) ---
        registerActivity(nextIndex, NekoGeneralSettingsActivity.class, "General", R.drawable.msg_edit);
        registerActivity(nextIndex, NekoCustomizationSettingsActivity.class, "Customization", R.drawable.msg_theme);
        registerActivity(nextIndex, NekoChatSettingsActivity.class, "Chats", R.drawable.msg_discussion_solar);
        registerActivity(nextIndex, NekoTranslatorSettingsActivity.class, "Translator", R.drawable.ic_translate);
        registerActivity(nextIndex, NekoExperimentalSettingsActivity.class, "Experimental", R.drawable.msg_fave);

        synchronized (index) {
            index.clear();
            index.addAll(nextIndex);
        }
    }

    private void registerActivity(List<SearchItem> target, Class<? extends BaseNekoXSettingsActivity> clazz, String category, int defaultIcon) {
        try {
            BaseNekoXSettingsActivity activity = clazz.getConstructor().newInstance();
            activity.onFragmentCreate(); 
            
            CellGroup cellGroup = activity.getCellGroup();
            if (cellGroup != null && cellGroup.rows != null) {
                for (AbstractConfigCell cell : cellGroup.rows) {
                    processCell(target, cell, clazz, category, defaultIcon);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void processCell(List<SearchItem> target, AbstractConfigCell cell, Class<? extends BaseFragment> fragmentClass, String category, int iconRes) {
        if (cell == null) return;
        
        String title = null;
        String subtitle = null;
        String key = null;

        if (cell instanceof ConfigCellTextCheck c) {
            title = String.valueOf(c.getTitle());
            key = c.getKey();
        } else if (cell instanceof ConfigCellSelectBox c) {
            title = String.valueOf(c.getTitle());
            key = c.getKey();
        } else if (cell instanceof ConfigCellTextInput c) {
            title = c.getKey();
            key = c.getKey();
        } else if (cell instanceof ConfigCellTextDetail c) {
            title = String.valueOf(c.getTitle());
            key = c.getKey();
        } else if (cell instanceof ConfigCellTextCheckIcon c) {
            title = String.valueOf(c.getTitle());
            key = c.getKey();
            if (c.getResId() != 0) iconRes = c.getResId();
        } else if (cell instanceof ConfigCellTextCheck2 c) {
            title = c.getTitle();
            key = c.getKey();
            add(target, title, null, key, fragmentClass, category, iconRes);
            if (c.getCheckBox() != null) {
                for (ConfigCellCheckBox cb : c.getCheckBox()) {
                    processCell(target, cb, fragmentClass, category, iconRes);
                }
            }
            return;
        } else if (cell instanceof ConfigCellCheckBox c) {
            title = c.getTitle();
            key = c.getKey();
        }

        if (title != null && key != null && !title.isEmpty() && !title.equals("null") && !title.equals("Divider")) {
            add(target, title, subtitle, key, fragmentClass, category, iconRes);
        }
    }

    private void add(List<SearchItem> target, String title, String subtitle, String key, Class<? extends BaseFragment> fragmentClass, String category, int iconRes) {
        target.add(new SearchItem(title, subtitle, key, fragmentClass, category, iconRes));
    }

    public List<SearchItem> search(String query) {
        List<SearchItem> results = new ArrayList<>();
        if (query == null || query.isEmpty()) return results;
        
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        synchronized (index) {
            for (SearchItem item : index) {
                if (item.title.toLowerCase(Locale.ROOT).contains(lowerQuery) || 
                    (item.subtitle != null && item.subtitle.toLowerCase(Locale.ROOT).contains(lowerQuery)) ||
                    item.category.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                    results.add(item);
                }
            }
        }
        return results;
    }

    public SearchItem getItem(String key) {
        if (key == null) return null;
        synchronized (index) {
            for (SearchItem item : index) {
                if (key.equals(item.key)) return item;
            }
        }
        return null;
    }
}
