package tw.nekomimi.nekogram.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Paint.PaintTypeface;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import tw.nekomimi.nekogram.NekoConfig;

@SuppressLint("RtlHardcoded")
public class FontsSettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int ROW_TYPE_APP_FONT = 0;
    private static final int ROW_TYPE_APP_FONT_DESC = 1;
    private static final int ROW_TYPE_HEADER = 2;
    private static final int ROW_TYPE_INCLUDE_SYSTEM = 3;
    private static final int ROW_TYPE_FONT = 4;
    private static final int ROW_TYPE_SHADOW = 5;

    private RecyclerListView listView;
    private ListAdapter listAdapter;

    private final ArrayList<RowItem> rows = new ArrayList<>();

    private static class RowItem {
        int type;
        PaintTypeface font;
        String headerText;

        static RowItem appFont() { RowItem r = new RowItem(); r.type = ROW_TYPE_APP_FONT; return r; }
        static RowItem appFontDesc() { RowItem r = new RowItem(); r.type = ROW_TYPE_APP_FONT_DESC; return r; }
        static RowItem header(String t) { RowItem r = new RowItem(); r.type = ROW_TYPE_HEADER; r.headerText = t; return r; }
        static RowItem includeSystem() { RowItem r = new RowItem(); r.type = ROW_TYPE_INCLUDE_SYSTEM; return r; }
        static RowItem font(PaintTypeface f) { RowItem r = new RowItem(); r.type = ROW_TYPE_FONT; r.font = f; return r; }
        static RowItem shadow() { RowItem r = new RowItem(); r.type = ROW_TYPE_SHADOW; return r; }
    }

    private void buildRows() {
        rows.clear();
        rows.add(RowItem.appFont());
        rows.add(RowItem.appFontDesc());
        rows.add(RowItem.header(LocaleController.getString("AvailableFonts", R.string.AvailableFonts)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            rows.add(RowItem.includeSystem());
        }
        List<PaintTypeface> fonts = getVisibleFonts();
        for (PaintTypeface pf : fonts) {
            rows.add(RowItem.font(pf));
        }
        rows.add(RowItem.shadow());
    }

    private List<PaintTypeface> getVisibleFonts() {
        List<PaintTypeface> all = PaintTypeface.get();
        if (!NekoConfig.appFontIncludeSystem.Bool()) {
            List<PaintTypeface> result = new ArrayList<>();
            for (PaintTypeface pf : all) {
                if (PaintTypeface.BUILT_IN_FONTS.contains(pf)) {
                    result.add(pf);
                }
            }
            return result;
        }
        return all;
    }

    private String getCurrentFontName() {
        String key = NekoConfig.appFontKey.String();
        if (TextUtils.isEmpty(key)) {
            return LocaleController.getString("AppFontDefault", R.string.AppFontDefault);
        }
        PaintTypeface pf = PaintTypeface.find(key);
        if (pf != null) {
            return pf.getName();
        }
        return LocaleController.getString("AppFontDefault", R.string.AppFontDefault);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        buildRows();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.customTypefacesLoaded);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.customTypefacesLoaded) {
            buildRows();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString("Fonts", R.string.Fonts));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setItemAnimator(null);

        listAdapter = new ListAdapter(context);
        listView.setAdapter(listAdapter);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= rows.size()) return;
            RowItem item = rows.get(position);
            if (item.type == ROW_TYPE_INCLUDE_SYSTEM) {
                boolean newVal = !NekoConfig.appFontIncludeSystem.Bool();
                NekoConfig.appFontIncludeSystem.setConfigBool(newVal);
                if (newVal && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    PaintTypeface.get(); // trigger loading
                }
                buildRows();
                listAdapter.notifyDataSetChanged();
            } else if (item.type == ROW_TYPE_FONT && item.font != null) {
                selectFont(item.font);
            } else if (item.type == ROW_TYPE_APP_FONT) {
                showResetDialog();
            }
        });

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.customTypefacesLoaded);
        return fragmentView;
    }

    private void selectFont(PaintTypeface pf) {
        NekoConfig.appFontKey.setConfigString(pf.getKey());
        AndroidUtilities.mediumTypeface = null;
        buildRows();
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
        if (getParentLayout() != null) {
            getParentLayout().rebuildAllFragmentViews(false, false);
        }
    }

    private void showResetDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("AppFont", R.string.AppFont));
        builder.setMessage(LocaleController.getString("AppFontResetDesc", R.string.AppFontResetDesc));
        builder.setPositiveButton(LocaleController.getString("Reset", R.string.Reset), (dialog, which) -> {
            NekoConfig.appFontKey.setConfigString("");
            AndroidUtilities.mediumTypeface = null;
            buildRows();
            if (listAdapter != null) listAdapter.notifyDataSetChanged();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
            if (getParentLayout() != null) {
                getParentLayout().rebuildAllFragmentViews(false, false);
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(builder.create());
    }

    @Override
    public int getBaseGuid() {
        return 15500;
    }

    // ============ Adapter ============

    private class ListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final Context mContext;

        ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case ROW_TYPE_APP_FONT:
                    view = new AppFontCell(mContext);
                    break;
                case ROW_TYPE_APP_FONT_DESC:
                case ROW_TYPE_SHADOW:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case ROW_TYPE_HEADER:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case ROW_TYPE_INCLUDE_SYSTEM:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                default: // ROW_TYPE_FONT
                    view = new FontItemCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            return new RecyclerView.ViewHolder(view) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            RowItem item = rows.get(position);
            boolean isLast = (position == rows.size() - 1) ||
                    (position + 1 < rows.size() && rows.get(position + 1).type == ROW_TYPE_SHADOW);

            switch (item.type) {
                case ROW_TYPE_APP_FONT:
                    ((AppFontCell) holder.itemView).bind(
                            LocaleController.getString("AppFont", R.string.AppFont),
                            getCurrentFontName());
                    break;
                case ROW_TYPE_APP_FONT_DESC:
                    ((TextInfoPrivacyCell) holder.itemView).setText(
                            LocaleController.getString("AppFontDesc", R.string.AppFontDesc));
                    break;
                case ROW_TYPE_HEADER:
                    ((HeaderCell) holder.itemView).setText(item.headerText);
                    break;
                case ROW_TYPE_INCLUDE_SYSTEM:
                    ((TextCheckCell) holder.itemView).setTextAndValueAndCheck(
                            LocaleController.getString("IncludeSystemFonts", R.string.IncludeSystemFonts),
                            LocaleController.getString("IncludeSystemFontsDesc", R.string.IncludeSystemFontsDesc),
                            NekoConfig.appFontIncludeSystem.Bool(),
                            true, !isLast);
                    break;
                case ROW_TYPE_FONT:
                    if (item.font != null) {
                        boolean isBuiltIn = PaintTypeface.BUILT_IN_FONTS.contains(item.font);
                        boolean isSelected = item.font.getKey().equals(NekoConfig.appFontKey.String());
                        ((FontItemCell) holder.itemView).bind(item.font, isBuiltIn, isSelected, !isLast);
                    }
                    break;
                case ROW_TYPE_SHADOW:
                default:
                    break;
            }
        }
    }

    // ============ AppFontCell ============
    private static class AppFontCell extends FrameLayout {
        private final TextView titleView;
        private final TextView valueView;

        AppFontCell(Context context) {
            super(context);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            setMinimumHeight(AndroidUtilities.dp(50));

            titleView = new TextView(context);
            titleView.setTextSize(16);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);

            valueView = new TextView(context);
            valueView.setTextSize(16);
            valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
            valueView.setSingleLine(true);

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(AndroidUtilities.dp(17), AndroidUtilities.dp(13),
                    AndroidUtilities.dp(17), AndroidUtilities.dp(13));
            row.addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            row.addView(valueView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            addView(row, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            View divider = new View(context);
            divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
            addView(divider, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT,
                    1f / context.getResources().getDisplayMetrics().density,
                    Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 17, 0, 0, 0));
        }

        void bind(String title, String value) {
            titleView.setText(title);
            valueView.setText(value);
        }
    }

    // ============ FontItemCell ============
    @SuppressLint("ViewConstructor")
    private static class FontItemCell extends FrameLayout {
        private final TextView nameView;
        private final TextView tagView;
        private final ImageView eyeView;
        private final View divider;

        FontItemCell(Context context) {
            super(context);
            setMinimumHeight(AndroidUtilities.dp(58));

            nameView = new TextView(context);
            nameView.setTextSize(16);
            nameView.setSingleLine(true);
            nameView.setEllipsize(TextUtils.TruncateAt.END);

            tagView = new TextView(context);
            tagView.setTypeface(AndroidUtilities.bold());
            tagView.setTextSize(10);
            tagView.setIncludeFontPadding(false);
            tagView.setGravity(Gravity.CENTER);
            int tagPadH = AndroidUtilities.dp(5);
            int tagPadV = AndroidUtilities.dp(1);
            tagView.setPadding(tagPadH, tagPadV, tagPadH, tagPadV);

            LinearLayout nameRow = new LinearLayout(context);
            nameRow.setOrientation(LinearLayout.HORIZONTAL);
            nameRow.setGravity(Gravity.CENTER_VERTICAL);
            nameRow.addView(nameView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            nameRow.addView(tagView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    0, 0, 8, 0, 0, 0));

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(AndroidUtilities.dp(17), AndroidUtilities.dp(10),
                    AndroidUtilities.dp(17), AndroidUtilities.dp(10));
            row.addView(nameRow, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

            eyeView = new ImageView(context);
            eyeView.setImageResource(R.drawable.msg_message);
            eyeView.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN);
            eyeView.setScaleType(ImageView.ScaleType.CENTER);
            row.addView(eyeView, LayoutHelper.createLinear(40, 40));

            addView(row, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL));

            divider = new View(context);
            divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
            addView(divider, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT,
                    1f / context.getResources().getDisplayMetrics().density,
                    Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 17, 0, 0, 0));
        }

        void bind(PaintTypeface font, boolean isBuiltIn, boolean isSelected, boolean showDivider) {
            nameView.setText(font.getName());

            Typeface tf = font.getTypeface();
            nameView.setTypeface(tf != null ? tf : Typeface.DEFAULT);

            nameView.setTextColor(isSelected
                    ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)
                    : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));

            // STOCK = blue-ish, SYSTEM = green/teal
            String tagLabel = isBuiltIn ? "STOCK" : "SYSTEM";
            int tagColor = isBuiltIn
                    ? 0xFF4A90D9  // blue
                    : 0xFF8E44AD; // purple
            try {
                tagColor = isBuiltIn
                        ? Theme.getColor(Theme.key_featuredStickers_buttonText)
                        : Theme.getColor(Theme.key_featuredStickers_addButton);
            } catch (Exception ignored) {}

            tagView.setText(tagLabel.toUpperCase(Locale.ROOT));
            tagView.setTextColor(tagColor);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(AndroidUtilities.dp(4));
            int alpha = Theme.isCurrentThemeDark() ? 51 : 26;
            bg.setColor(Color.argb(alpha,
                    Color.red(tagColor), Color.green(tagColor), Color.blue(tagColor)));
            tagView.setBackground(bg);

            // Eye icon tint — highlighted if selected
            eyeView.setColorFilter(isSelected
                    ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)
                    : Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN);

            divider.setVisibility(showDivider ? VISIBLE : GONE);
        }
    }
}
