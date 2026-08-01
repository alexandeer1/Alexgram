package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.ThemePreviewMessagesCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Paint.ColorPickerBottomSheet;
import org.telegram.ui.Components.RecyclerListView;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.cell.AbstractConfigCell;
import tw.nekomimi.nekogram.config.cell.ConfigCellCustom;
import tw.nekomimi.nekogram.config.cell.ConfigCellHeader;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck;
import tw.nekomimi.nekogram.ui.cells.AltSeekbar;

@SuppressLint("RtlHardcoded")
@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class BubbleStyleSettingsActivity extends BaseNekoXSettingsActivity {

    private ListAdapter listAdapter;
    private ThemePreviewMessagesCell messagesPreviewCell;

    private final CellGroup cellGroup = new CellGroup(this);

    // Custom Cell Types
    private static final int CUSTOM_PREVIEW = 2000;
    private static final int CUSTOM_IN_COLOR = 2001;
    private static final int CUSTOM_IN_TEXT_COLOR = 2007;
    private static final int CUSTOM_IN_ALPHA = 2002;

    private static final int CUSTOM_OUT_COLOR = 2003;
    private static final int CUSTOM_OUT_TEXT_COLOR = 2008;
    private static final int CUSTOM_OUT_ALPHA = 2004;

    private static final int CUSTOM_PRESETS = 2005;
    private static final int CUSTOM_RESET = 2006;

    // Rows
    private final AbstractConfigCell previewRow = cellGroup.appendCell(new ConfigCellCustom("Preview", CUSTOM_PREVIEW, false));
    private final AbstractConfigCell enableRow = cellGroup.appendCell(new ConfigCellTextCheck(
            NekoConfig.enableCustomBubbleStyle,
            LocaleController.getString("EnableCustomBubbleStyleDesc", R.string.EnableCustomBubbleStyleDesc),
            LocaleController.getString("EnableCustomBubbleStyle", R.string.EnableCustomBubbleStyle)
    ));
    private final AbstractConfigCell headerIncoming = cellGroup.appendCell(new ConfigCellHeader(LocaleController.getString("IncomingBubbleSection", R.string.IncomingBubbleSection)));
    private final AbstractConfigCell inColorRow = cellGroup.appendCell(new ConfigCellCustom("InColor", CUSTOM_IN_COLOR, true));
    private final AbstractConfigCell inTextColorRow = cellGroup.appendCell(new ConfigCellCustom("InTextColor", CUSTOM_IN_TEXT_COLOR, true));
    private final AbstractConfigCell inAlphaRow = cellGroup.appendCell(new ConfigCellCustom("InAlpha", CUSTOM_IN_ALPHA, false));

    private final AbstractConfigCell headerOutgoing = cellGroup.appendCell(new ConfigCellHeader(LocaleController.getString("OutgoingBubbleSection", R.string.OutgoingBubbleSection)));
    private final AbstractConfigCell outColorRow = cellGroup.appendCell(new ConfigCellCustom("OutColor", CUSTOM_OUT_COLOR, true));
    private final AbstractConfigCell outTextColorRow = cellGroup.appendCell(new ConfigCellCustom("OutTextColor", CUSTOM_OUT_TEXT_COLOR, true));
    private final AbstractConfigCell outAlphaRow = cellGroup.appendCell(new ConfigCellCustom("OutAlpha", CUSTOM_OUT_ALPHA, false));

    private final AbstractConfigCell headerPresets = cellGroup.appendCell(new ConfigCellHeader(LocaleController.getString("Presets", R.string.Presets)));
    private final AbstractConfigCell presetsRow = cellGroup.appendCell(new ConfigCellCustom("Presets", CUSTOM_PRESETS, false));
    private final AbstractConfigCell resetRow = cellGroup.appendCell(new ConfigCellCustom("Reset", CUSTOM_RESET, true));

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
        return "bubblestyle";
    }

    public BubbleStyleSettingsActivity() {
        addRowsToMap(cellGroup);
    }

    @Override
    public View createView(Context context) {
        View superView = super.createView(context);
        listAdapter = new ListAdapter(context);
        listView.setAdapter(listAdapter);
        listView.invalidateItemDecorations();

        setupDefaultListeners();

        cellGroup.callBackSettingsChanged = (key, newValue) -> notifyChanges();

        return superView;
    }

    private void notifyChanges() {
        if (messagesPreviewCell != null) {
            messagesPreviewCell.invalidate();
        }
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
        getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
        if (getParentLayout() != null) {
            getParentLayout().rebuildAllFragmentViews(false, false);
        }
    }

    @Override
    public int getBaseGuid() {
        return 14050;
    }

    @Override
    public int getDrawable() {
        return R.drawable.msg_theme;
    }

    @Override
    public String getTitle() {
        return LocaleController.getString("BubbleStyleTitle", R.string.BubbleStyleTitle);
    }

    @Override
    protected void onCustomCellClick(View view, int position, float x, float y) {
        if (position < 0 || position >= cellGroup.rows.size()) return;
        AbstractConfigCell row = cellGroup.rows.get(position);
        if (row == inColorRow) {
            showColorPicker(true, false);
        } else if (row == inTextColorRow) {
            showColorPicker(true, true);
        } else if (row == outColorRow) {
            showColorPicker(false, false);
        } else if (row == outTextColorRow) {
            showColorPicker(false, true);
        } else if (row == resetRow) {
            resetToDefaults();
        }
    }

    private void showColorPicker(boolean isIncoming, boolean isText) {
        if (getParentActivity() == null) return;

        ColorPickerBottomSheet colorPicker = new ColorPickerBottomSheet(getParentActivity(), getResourceProvider());
        colorPicker.setPipetteDelegate(new ColorPickerBottomSheet.PipetteDelegate() {
            @Override public boolean isPipetteAvailable() { return false; }
            @Override public boolean isPipetteVisible() { return false; }
            @Override public ViewGroup getContainerView() { return null; }
            @Override public View getSnapshotDrawingView() { return null; }
            @Override public void onDrawImageOverCanvas(android.graphics.Bitmap bitmap, Canvas canvas) {}
            @Override public void onStartColorPipette() {}
            @Override public void onStopColorPipette() {}
            @Override public void onColorSelected(int color) {}
        });

        int currentColor;
        if (isText) {
            currentColor = isIncoming ? NekoConfig.inTextColor.Int() : NekoConfig.outTextColor.Int();
            if (currentColor == 0) {
                currentColor = Theme.getColor(isIncoming ? Theme.key_chat_messageTextIn : Theme.key_chat_messageTextOut);
            }
        } else {
            currentColor = isIncoming ? NekoConfig.inBubbleColor.Int() : NekoConfig.outBubbleColor.Int();
            if (currentColor == 0) {
                currentColor = isIncoming ? 0xFFFFFFFF : 0xFF0088FF;
            }
        }

        colorPicker.setColor((currentColor & 0x00FFFFFF) | 0xFF000000);

        colorPicker.setColorListener(color -> {
            int selectedColor = (color & 0x00FFFFFF) | 0xFF000000;
            if (isText) {
                if (isIncoming) {
                    NekoConfig.inTextColor.setConfigInt(selectedColor);
                } else {
                    NekoConfig.outTextColor.setConfigInt(selectedColor);
                }
            } else {
                if (isIncoming) {
                    NekoConfig.inBubbleColor.setConfigInt(selectedColor & 0x00FFFFFF);
                } else {
                    NekoConfig.outBubbleColor.setConfigInt(selectedColor & 0x00FFFFFF);
                }
            }
            if (!NekoConfig.enableCustomBubbleStyle.Bool()) {
                NekoConfig.enableCustomBubbleStyle.setConfigBool(true);
            }
            notifyChanges();
        });

        showDialog(colorPicker);
    }

    private void applyPreset(int inBg, int inAlpha, int inText, int outBg, int outAlpha, int outText) {
        NekoConfig.enableCustomBubbleStyle.setConfigBool(true);
        NekoConfig.inBubbleColor.setConfigInt(inBg & 0x00FFFFFF);
        NekoConfig.inBubbleAlpha.setConfigInt(inAlpha);
        NekoConfig.inTextColor.setConfigInt(inText);

        NekoConfig.outBubbleColor.setConfigInt(outBg & 0x00FFFFFF);
        NekoConfig.outBubbleAlpha.setConfigInt(outAlpha);
        NekoConfig.outTextColor.setConfigInt(outText);

        notifyChanges();
    }

    private void resetToDefaults() {
        NekoConfig.enableCustomBubbleStyle.setConfigBool(false);
        NekoConfig.inBubbleColor.setConfigInt(0x00FFFFFF);
        NekoConfig.inBubbleAlpha.setConfigInt(100);
        NekoConfig.inTextColor.setConfigInt(0);

        NekoConfig.outBubbleColor.setConfigInt(0x000088FF);
        NekoConfig.outBubbleAlpha.setConfigInt(100);
        NekoConfig.outTextColor.setConfigInt(0);

        notifyChanges();
    }

    // --- ListAdapter ---
    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        protected View onCreateCustomViewHolder(@NonNull ViewGroup parent, int viewType) {
            switch (viewType) {
                case CUSTOM_PREVIEW: {
                    FrameLayout container = new FrameLayout(mContext);
                    container.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
                    container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

                    FrameLayout card = new FrameLayout(mContext);
                    card.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), 0xFF000000));
                    card.setClipToOutline(true);

                    messagesPreviewCell = new ThemePreviewMessagesCell(mContext, parentLayout, 0);
                    card.addView(messagesPreviewCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                    container.addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    return container;
                }

                case CUSTOM_IN_COLOR:
                case CUSTOM_IN_TEXT_COLOR:
                case CUSTOM_OUT_COLOR:
                case CUSTOM_OUT_TEXT_COLOR:
                    return new BubbleColorCell(mContext);

                case CUSTOM_IN_ALPHA: {
                    AltSeekbar seekbar = new AltSeekbar(
                            mContext,
                            (value, stop) -> {
                                NekoConfig.inBubbleAlpha.setConfigInt(Math.round(value));
                                if (messagesPreviewCell != null) messagesPreviewCell.invalidate();
                                if (stop) notifyChanges();
                            },
                            0, 100,
                            LocaleController.getString("BubbleTransparency", R.string.BubbleTransparency),
                            "0%", "100%"
                    );
                    seekbar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    return seekbar;
                }

                case CUSTOM_OUT_ALPHA: {
                    AltSeekbar seekbar = new AltSeekbar(
                            mContext,
                            (value, stop) -> {
                                NekoConfig.outBubbleAlpha.setConfigInt(Math.round(value));
                                if (messagesPreviewCell != null) messagesPreviewCell.invalidate();
                                if (stop) notifyChanges();
                            },
                            0, 100,
                            LocaleController.getString("BubbleTransparency", R.string.BubbleTransparency),
                            "0%", "100%"
                    );
                    seekbar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    return seekbar;
                }

                case CUSTOM_PRESETS:
                    return new PresetsContainerCell(mContext);

                case CUSTOM_RESET: {
                    TextCell textCell = new TextCell(mContext);
                    textCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    textCell.setTextAndIcon(
                            LocaleController.getString("ResetBubbleStyle", R.string.ResetBubbleStyle),
                            R.drawable.msg_reset,
                            false
                    );
                    textCell.setColors(Theme.key_text_RedBold, Theme.key_text_RedBold);
                    textCell.setOnClickListener(v -> resetToDefaults());
                    return textCell;
                }
            }
            return null;
        }

        @Override
        protected void onBindCustomViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int viewType = getItemViewType(position);

            if (viewType == CUSTOM_IN_COLOR) {
                BubbleColorCell cell = (BubbleColorCell) holder.itemView;
                int color = NekoConfig.getEffectiveInBubbleColor(Theme.getColor(Theme.key_chat_inBubble));
                cell.setValue(
                        LocaleController.getString("BubbleColor", R.string.BubbleColor),
                        color,
                        true,
                        v -> showColorPicker(true, false)
                );
            } else if (viewType == CUSTOM_IN_TEXT_COLOR) {
                BubbleColorCell cell = (BubbleColorCell) holder.itemView;
                int color = NekoConfig.getEffectiveInTextColor(Theme.getColor(Theme.key_chat_messageTextIn));
                cell.setValue(
                        LocaleController.getString("BubbleTextColor", R.string.BubbleTextColor),
                        color,
                        true,
                        v -> showColorPicker(true, true)
                );
            } else if (viewType == CUSTOM_OUT_COLOR) {
                BubbleColorCell cell = (BubbleColorCell) holder.itemView;
                int color = NekoConfig.getEffectiveOutBubbleColor(Theme.getColor(Theme.key_chat_outBubble));
                cell.setValue(
                        LocaleController.getString("BubbleColor", R.string.BubbleColor),
                        color,
                        true,
                        v -> showColorPicker(false, false)
                );
            } else if (viewType == CUSTOM_OUT_TEXT_COLOR) {
                BubbleColorCell cell = (BubbleColorCell) holder.itemView;
                int color = NekoConfig.getEffectiveOutTextColor(Theme.getColor(Theme.key_chat_messageTextOut));
                cell.setValue(
                        LocaleController.getString("BubbleTextColor", R.string.BubbleTextColor),
                        color,
                        true,
                        v -> showColorPicker(false, true)
                );
            } else if (viewType == CUSTOM_IN_ALPHA) {
                AltSeekbar seekbar = (AltSeekbar) holder.itemView;
                seekbar.setProgress(NekoConfig.inBubbleAlpha.Int() / 100.0f);
            } else if (viewType == CUSTOM_OUT_ALPHA) {
                AltSeekbar seekbar = (AltSeekbar) holder.itemView;
                seekbar.setProgress(NekoConfig.outBubbleAlpha.Int() / 100.0f);
            }
        }
    }

    // --- Custom Color Selector Cell Class ---
    private class BubbleColorCell extends FrameLayout {
        private final TextView titleTextView;
        private final TextView hexTextView;
        private final View colorCircleView;
        private final ImageView iconImageView;

        public BubbleColorCell(Context context) {
            super(context);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            setMinimumHeight(AndroidUtilities.dp(54));

            LinearLayout mainLayout = new LinearLayout(context);
            mainLayout.setOrientation(LinearLayout.HORIZONTAL);
            mainLayout.setGravity(Gravity.CENTER_VERTICAL);
            mainLayout.setPadding(AndroidUtilities.dp(21), 0, AndroidUtilities.dp(18), 0);

            titleTextView = new TextView(context);
            titleTextView.setTextSize(1, 15f);
            titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleTextView.setSingleLine(true);
            titleTextView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            mainLayout.addView(titleTextView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

            colorCircleView = new View(context);
            mainLayout.addView(colorCircleView, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

            hexTextView = new TextView(context);
            hexTextView.setTextSize(1, 14f);
            hexTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            hexTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            mainLayout.addView(hexTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));

            iconImageView = new ImageView(context);
            iconImageView.setImageResource(R.drawable.msg_theme);
            iconImageView.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon));
            mainLayout.addView(iconImageView, LayoutHelper.createLinear(22, 22, Gravity.CENTER_VERTICAL, 0, 0, 0, 0));

            addView(mainLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        public void setValue(String title, int argbColor, boolean needDivider, OnClickListener listener) {
            titleTextView.setText(title);
            String hex = String.format("#%08X", argbColor);
            hexTextView.setText(hex);

            Drawable circleDrawable = Theme.createRoundRectDrawable(AndroidUtilities.dp(12), argbColor);
            colorCircleView.setBackground(circleDrawable);

            setOnClickListener(listener);
        }
    }

    // --- Horizontally Scrollable Quick Presets Container ---
    private class PresetsContainerCell extends HorizontalScrollView {

        public PresetsContainerCell(Context context) {
            super(context);
            setHorizontalScrollBarEnabled(false);
            setOverScrollMode(OVER_SCROLL_NEVER);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.HORIZONTAL);
            layout.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(14));

            addPresetPill(layout, LocaleController.getString("PresetGlassmorphic", R.string.PresetGlassmorphic), 0xFFFFFFFF, 40, 0xFFFFFFFF, 0xFF00E5FF, 50, 0xFF003344);
            addPresetPill(layout, LocaleController.getString("PresetCyberpunk", R.string.PresetCyberpunk), 0xFF2D1B4E, 85, 0xFFE0C3FC, 0xFFFF007F, 90, 0xFFFFFFFF);
            addPresetPill(layout, LocaleController.getString("PresetSunset", R.string.PresetSunset), 0xFFFFF3E0, 80, 0xFF3E2723, 0xFFFF6D00, 90, 0xFFFFFFFF);
            addPresetPill(layout, LocaleController.getString("PresetEmerald", R.string.PresetEmerald), 0xFF1B3B2B, 70, 0xFFE8F5E9, 0xFF00E676, 85, 0xFF003311);
            addPresetPill(layout, LocaleController.getString("PresetMidnight", R.string.PresetMidnight), 0xFF1C1C1E, 90, 0xFFF2F2F7, 0xFF0A84FF, 95, 0xFFFFFFFF);
            addPresetPill(layout, LocaleController.getString("PresetPastel", R.string.PresetPastel), 0xFFF3E5F5, 85, 0xFF4A148C, 0xFFF48FB1, 90, 0xFF880E4F);
            addPresetPill(layout, LocaleController.getString("PresetRoyal", R.string.PresetRoyal), 0xFF1A237E, 85, 0xFFE8EAF6, 0xFF7C4DFF, 90, 0xFFFFFFFF);
            addPresetPill(layout, LocaleController.getString("PresetMinimalist", R.string.PresetMinimalist), 0xFFE0E0E0, 30, 0xFF212121, 0xFF263238, 80, 0xFFFFFFFF);

            addView(layout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));
        }

        private void addPresetPill(LinearLayout layout, String name, int inBg, int inAlpha, int inText, int outBg, int outAlpha, int outText) {
            TextView textView = new TextView(getContext());
            textView.setText(name);
            textView.setTextSize(1, 13f);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
            textView.setGravity(Gravity.CENTER);
            textView.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(8));

            Drawable bg = Theme.createRoundRectDrawable(AndroidUtilities.dp(16), Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText), 0.12f));
            textView.setBackground(bg);

            textView.setOnClickListener(v -> applyPreset(inBg, inAlpha, inText, outBg, outAlpha, outText));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, AndroidUtilities.dp(8), 0);
            layout.addView(textView, params);
        }
    }
}
