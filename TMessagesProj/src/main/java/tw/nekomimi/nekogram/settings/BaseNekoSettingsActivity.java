package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.CreationTextCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Cells.TextCheckbox2Cell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextRadioCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BlurredRecyclerView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.URLSpanNoUnderline;

import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import tw.nekomimi.nekogram.helpers.QuickSettingEntry;
import tw.nekomimi.nekogram.helpers.QuickSettingsController;
import tw.nekomimi.nekogram.ui.cells.AccountCell;
import tw.nekomimi.nekogram.ui.cells.EmojiSetCell;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

public abstract class BaseNekoSettingsActivity extends BaseFragment {

    public static final Object PARTIAL = new Object();

    public static final int TYPE_SHADOW = 1;
    public static final int TYPE_SETTINGS = 2;
    public static final int TYPE_CHECK = 3;
    public static final int TYPE_HEADER = 4;
    public static final int TYPE_NOTIFICATION_CHECK = 5;
    public static final int TYPE_DETAIL_SETTINGS = 6;
    public static final int TYPE_INFO_PRIVACY = 7;
    public static final int TYPE_TEXT = 8;
    public static final int TYPE_CHECKBOX = 9;
    public static final int TYPE_RADIO = 10;
    public static final int TYPE_ACCOUNT = 11;
    public static final int TYPE_EMOJI = 12;
    public static final int TYPE_EMOJI_SELECTION = 13;
    public static final int TYPE_CREATION = 14;
    public static final int TYPE_FLICKER = 15;
    public static final int TYPE_CHECK2 = 16;
    public static final int TYPE_CHECKBOX2 = 17;

    protected BlurredRecyclerView listView;
    protected BaseListAdapter listAdapter;

    protected boolean isBreakType(int position) {
        if (listAdapter == null || position < 0 || position >= listAdapter.getItemCount()) {
            return true;
        }
        int type = listAdapter.getItemViewType(position);
        return type == TYPE_SHADOW || type == TYPE_INFO_PRIVACY;
    }
    protected LinearLayoutManager layoutManager;
    protected Theme.ResourcesProvider resourcesProvider;

    protected int rowCount;
    protected HashMap<String, Integer> rowMap = new HashMap<>(20);
    protected HashMap<Integer, String> rowMapReverse = new HashMap<>(20);

    private int highlightRow = -1;
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    protected boolean isDark;
    protected int cardBg;
    protected int cardBorder;
    protected int dividerColor;


    @Override
    public boolean onFragmentCreate() {
        setupBrandingColors();
        super.onFragmentCreate();
        updateRows();
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }

        if (getArguments() != null) {
            String scrollToKey = getArguments().getString("scrollToKey");
            if (scrollToKey != null) {
                getArguments().remove("scrollToKey");
                scrollToRow(scrollToKey, null);
            }
        }
    }

    protected ItemOptions makeLongClickOptions(View view) {
        ItemOptions options = ItemOptions.makeOptions(this, view);
        Drawable background = null;
        if (listView != null) {
            background = listView.getClipBackground(view);
        }
        return options.setScrimViewBackground(background);
    }

    protected int addRow() {
        return rowCount++;
    }

    protected int addRow(String... keys) {
        var row = rowCount++;
        for (var key : keys) {
            rowMap.put(key, row);
        }
        rowMapReverse.put(row, keys[0]);
        return row;
    }

    public View createView(Context context) {
        setupBrandingColors();
        fragmentView = new BlurContentView(context);
        fragmentView.setBackgroundColor(isAlexgramTheme() ? android.graphics.Color.TRANSPARENT : getThemedColor(Theme.key_windowBackgroundGray));
        SizeNotifierFrameLayout frameLayout = (SizeNotifierFrameLayout) fragmentView;

        if (isAlexgramTheme()) {
            AlexgramSettingsHeaderView backgroundView = new AlexgramSettingsHeaderView(context);
            frameLayout.addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        if (!isAlexgramTheme()) {
            actionBar.setDrawBlurBackground(frameLayout);
        }

        if (useSimpleActionBar()) {
            // Telegram glass header: floating back circle + title, menu items in glass bubbles
            simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
            simpleActionBar.setTitle(getActionBarTitle());
            simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    }
                }
            });
            if (AndroidUtilities.isTablet()) {
                simpleActionBar.setOccupyStatusBar(false);
            }
        }

        listView = new BlurredRecyclerView(context);
        listView.disableBlurTopPadding = true;
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                frameLayout.invalidateBlur();
            }
        });
        listView.additionalClipBottom = dp(200);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);

        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setChangeDuration(350);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayAnimations(false);
        listView.setItemAnimator(itemAnimator);
        if (isAlexgramTheme()) {
            listView.setPadding(0, AndroidUtilities.statusBarHeight + dp(64), 0, dp(40));
            listView.setClipToPadding(false);
        }
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listAdapter = createAdapter(context);

        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener(this::onItemClick);
        listView.setClipToPadding(false);

        listView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.State state) {
                int position = recyclerView.getChildAdapterPosition(view);
                if (position == RecyclerView.NO_POSITION || BaseNekoSettingsActivity.this.isBreakType(position)) {
                    outRect.set(0, 0, 0, 0);
                    return;
                }
                boolean isFirst = position == 0 || BaseNekoSettingsActivity.this.isBreakType(position - 1);
                boolean isLast = position == listAdapter.getItemCount() - 1 || BaseNekoSettingsActivity.this.isBreakType(position + 1);
                outRect.set(dp(16), isFirst ? dp(4) : 0, dp(16), isLast ? dp(12) : 0);
            }
        });
        listView.setOnItemLongClickListener((view, position, x, y) -> {
            if (onItemLongClick(view, position, x, y)) {
                return true;
            }
            var holder = listView.findViewHolderForAdapterPosition(position);
            var key = getKey();
            if (key != null && holder != null && listAdapter.isEnabled(holder) && rowMapReverse.containsKey(position)) {
                String rowKey = rowMapReverse.get(position);
                boolean isOnOff = false;
                if (view != null) {
                    String simpleName = view.getClass().getSimpleName();
                    if (simpleName.contains("Check") || simpleName.contains("Switch")) {
                        isOnOff = true;
                    }
                }

                ArrayList<CharSequence> items = new ArrayList<>();
                items.add(getString(R.string.CopyLink));
                if (isOnOff) {
                    if (!QuickSettingsController.getInstance().isAdded(rowKey)) {
                        items.add("Add to Quick Settings");
                    } else {
                        items.add("Remove from Quick Settings");
                    }
                }
                showDialog(new AlertDialog.Builder(context).setItems(items.toArray(new CharSequence[0]), (dialogInterface, i) -> {
                    CharSequence clickedItem = items.get(i);
                    if (getString(R.string.CopyLink).equals(clickedItem)) {
                        AndroidUtilities.addToClipboard(String.format(Locale.getDefault(), "https://%s/alexsettings/%s?r=%s", getMessagesController().linkPrefix, getKey(), rowKey));
                        BulletinFactory.of(BaseNekoSettingsActivity.this).createCopyLinkBulletin().show();
                    } else if ("Add to Quick Settings".equals(clickedItem)) {
                        String title = "";
                        String subtitle = null;
                        int type = QuickSettingEntry.TYPE_SWITCH;
                        if (view instanceof TextCheckCell) {
                            title = ((TextCheckCell) view).getTextView().getText().toString();
                        } else if (view instanceof NotificationsCheckCell) {
                            title = ((NotificationsCheckCell) view).getTextView().getText().toString();
                        } else if (view instanceof CheckBoxCell) {
                            title = ((CheckBoxCell) view).getTextView().getText().toString();
                        } else if (view instanceof android.view.ViewGroup) {
                            java.util.List<android.widget.TextView> textViews = new java.util.ArrayList<>();
                            findTextViews(view, textViews);
                            if (textViews.size() >= 1) {
                                title = textViews.get(0).getText().toString();
                            }
                            if (textViews.size() >= 2) {
                                subtitle = textViews.get(1).getText().toString();
                            }
                        }

                        if (title != null && !title.isEmpty()) {
                            QuickSettingsController.getInstance().addQuickSetting(new QuickSettingEntry(rowKey, title, subtitle, "msg_settings", 0xFF2196F3, type, getClass().getName()));
                            BulletinFactory.of(BaseNekoSettingsActivity.this).createSimpleBulletin(R.drawable.msg_settings, "Added to Quick Settings").show();
                        }
                    } else if ("Remove from Quick Settings".equals(clickedItem)) {
                        QuickSettingsController.getInstance().removeQuickSetting(rowKey);
                        BulletinFactory.of(BaseNekoSettingsActivity.this).createSimpleBulletin(R.drawable.msg_delete, "Removed from Quick Settings").show();
                    }
                }).create());
                return true;
            }
            return false;
        });

        listView.setSections(true);
        if (useSimpleActionBar()) {
            // the glass header (SimpleActionBar) is added on top by the framework
        } else {
            actionBar.setAdaptiveBackground(listView);
            if (isAlexgramTheme()) {
                frameLayout.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
        }
        return fragmentView;
    }

    private void findTextViews(View view, java.util.List<android.widget.TextView> out) {
        if (view instanceof android.widget.TextView) {
            out.add((android.widget.TextView) view);
        } else if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                findTextViews(group.getChildAt(i), out);
            }
        }
    }

    protected boolean isAlexgramTheme() {
        return true;
    }

    // [Alexgram: Glass Header] - Start
    // All A-Settings pages use Telegram's simple glass header (back arrow in a glass circle)
    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    @Override
    protected void onViewCreated(View view) {
        super.onViewCreated(view);
        // the glass setup swaps in the bitmap sections decoration, which draws nothing
        // on screen – restore the legacy decorator that paints the rounded cards
        // (it is a valid IBlur3Capture, so the glass capture keeps working)
        if (listView != null) {
            listView.setLegacySections(true);
        }
    }
    // [Alexgram: Glass Header] - End

    private void setupBrandingColors() {
        isDark = Theme.getActiveTheme().isDark();
        if (isDark) {
            cardBg = 0x221E2732;
            cardBorder = 0x15FFFFFF;
            dividerColor = 0x10FFFFFF;
        } else {
            cardBg = 0x40FFFFFF;
            cardBorder = 0x20000000;
            dividerColor = 0x15000000;
        }
    }

    // @Override
    protected void setParentLayout(ActionBarLayout layout) {
        if (layout != null && !hasWhiteActionBar()) {
            resourcesProvider = layout.getLastFragment().getResourceProvider();
        }
        super.setParentLayout(layout);
    }

    @Override
    public ActionBar createActionBar(Context context) {
        ActionBar actionBar = super.createActionBar(context);
        if (isAlexgramTheme()) {
            actionBar.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            actionBar.setCastShadows(false);
            actionBar.setAddToContainer(false);
            int color = isDark ? android.graphics.Color.WHITE : 0xFF1A1A2E;
            actionBar.setItemsColor(color, false);
            actionBar.setTitleColor(color);
            actionBar.setItemsBackgroundColor(isDark ? 0x0FFFFFFF : 0x0F000000, false);
        } else if (hasWhiteActionBar()) {
            actionBar.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            actionBar.setItemsColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), false);
            actionBar.setTitleColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            actionBar.setCastShadows(false);
        }
        actionBar.setTitle(getActionBarTitle());
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
        return actionBar;
    }

    protected String getKey() {
        return null;
    }

    protected abstract void onItemClick(View view, int position, float x, float y);

    protected boolean onItemLongClick(View view, int position, float x, float y) {
        return false;
    }

    protected abstract BaseListAdapter createAdapter(Context context);

    protected abstract String getActionBarTitle();

    protected boolean hasWhiteActionBar() {
        return true;
    }

    protected CharSequence getSpannedString(int id) {
        var text = getString(id);
        var builder = new SpannableStringBuilder(text);
        int index1 = text.indexOf("**");
        int index2 = text.lastIndexOf("**");
        if (index1 >= 0 && index2 >= 0 && index1 != index2) {
            builder.replace(index2, index2 + 2, "");
            builder.replace(index1, index1 + 2, "");
            builder.setSpan(new URLSpanNoUnderline("https://core.telegram.org/api/datacenter"), index1, index2 - 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return builder;
    }

    @Override
    public boolean isLightStatusBar() {
        if (!hasWhiteActionBar()) return super.isLightStatusBar();
        int color = getThemedColor(Theme.key_windowBackgroundWhite);
        return ColorUtils.calculateLuminance(color) > 0.7f;
    }

    // existing addRow(String... keys) and addRow() at the bottom will be kept as the source of truth if needed, 
    // but the ones I added are now gone or consolidated.

    public void scrollToRow(String key, Runnable unknown) {
        if (rowMap.containsKey(key)) {
            int position = rowMap.get(key);
            highlightRow = position;
            layoutManager.scrollToPositionWithOffset(position, dp(60));
            listAdapter.notifyItemChanged(position);
            AndroidUtilities.runOnUIThread(() -> {
                highlightRow = -1;
                listAdapter.notifyItemChanged(position);
            }, 1500);
        } else if (unknown != null) {
            unknown.run();
        }
    }

    protected void updateRows() {
        rowCount = 0;
        rowMap.clear();
        rowMapReverse.clear();
    }

    @Override
    public Theme.ResourcesProvider getResourceProvider() {
        return resourcesProvider;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        listView.setPadding(0, listView.getPaddingTop(), 0, bottom);
        listView.setClipToPadding(false);
    }

    private class BlurContentView extends SizeNotifierFrameLayout {

        public Paint blurScrimPaint = new Paint();
        Rect rectTmp = new Rect();

        public BlurContentView(Context context) {
            super(context);
            needBlur = hasWhiteActionBar() && !isAlexgramTheme();
            blurBehindViews.add(this);
        }

        @Override
        protected void drawList(Canvas blurCanvas, boolean top, ArrayList<IViewWithInvalidateCallback> views) {
            for (int j = 0; j < listView.getChildCount(); j++) {
                View child = listView.getChildAt(j);
                if (child.getY() < listView.blurTopPadding + dp(100)) {
                    int restore = blurCanvas.save();
                    blurCanvas.translate(getX() + child.getX(), getY() + listView.getY() + child.getY());
                    child.draw(blurCanvas);
                    blurCanvas.restoreToCount(restore);
                }
            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (hasWhiteActionBar() && listView.canScrollVertically(-1)) {
                rectTmp.set(0, 0, getMeasuredWidth(), 1);
                blurScrimPaint.setColor(getThemedColor(Theme.key_divider));
                drawBlurRect(canvas, getY(), rectTmp, blurScrimPaint, true);
            }
        }
    }

    protected abstract class BaseListAdapter extends RecyclerListView.SelectionAdapter {

        protected final Context mContext;

        public BaseListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == TYPE_SETTINGS || type == TYPE_CHECK || type == TYPE_NOTIFICATION_CHECK || type == TYPE_DETAIL_SETTINGS || type == TYPE_TEXT | type == TYPE_CHECKBOX || type == TYPE_RADIO || type == TYPE_ACCOUNT || type == TYPE_EMOJI || type == TYPE_EMOJI_SELECTION || type == TYPE_CREATION || type == TYPE_CHECK2 || type == TYPE_CHECKBOX2;
        }

        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            if (isAlexgramTheme()) {
                modernizeCell(holder.itemView, position);
            }
        }

        private void modernizeCell(View view, int position) {
            int type = getItemViewType(position);
            
            boolean isHighlighted = position == highlightRow;
            if (type == TYPE_HEADER) {
                if (view instanceof HeaderCell headerCell) {
                    headerCell.getTextView().setTextColor(isDark ? 0xFF33A1FF : 0xFF007AFF);
                    headerCell.getTextView().setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
                    headerCell.getTextView().setTypeface(AndroidUtilities.bold());
                    headerCell.setPadding(dp(21), dp(12), dp(21), 0);
                    headerCell.getTextView().setPadding(0, dp(4), 0, dp(4));
                }
            } else if (type == TYPE_SHADOW || type == TYPE_INFO_PRIVACY) {
                view.setBackground(null);
                view.getLayoutParams().height = dp(12);
                return;
            }

            boolean isFirst = position == 0 || isBreakType(position - 1);
            boolean isLast = position == getItemCount() - 1 || isBreakType(position + 1);

            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setColor(cardBg);
            float r = dp(16);
            gd.setCornerRadii(new float[]{
                    isFirst ? r : 0, isFirst ? r : 0,
                    isFirst ? r : 0, isFirst ? r : 0,
                    isLast ? r : 0, isLast ? r : 0,
                    isLast ? r : 0, isLast ? r : 0
            });
            if (isFirst && isLast) {
                gd.setStroke(dp(1), cardBorder);
            }
            if (isHighlighted) {
                gd.setColor(isDark ? 0x442196F3 : 0x222196F3);
            }
            view.setBackground(gd);

            if (view instanceof TextSettingsCell cell) {
                cell.getTextView().setTextColor(isDark ? android.graphics.Color.WHITE : 0xFF1A1A2E);
                cell.getValueTextView().setTextColor(isDark ? 0xFF33A1FF : 0xFF007AFF);
            } else if (view instanceof TextCheckCell cell) {
                cell.getTextView().setTextColor(isDark ? android.graphics.Color.WHITE : 0xFF1A1A2E);
                cell.getValueTextView().setTextColor(isDark ? 0xFF33A1FF : 0xFF007AFF);
            } else if (view instanceof TextCell cell) {
                cell.getTextView().setTextColor(isDark ? android.graphics.Color.WHITE : 0xFF1A1A2E);
                cell.getValueTextView().setTextColor(isDark ? 0xFF33A1FF : 0xFF007AFF);
            } else if (view instanceof TextDetailSettingsCell cell) {
                cell.getTextView().setTextColor(isDark ? android.graphics.Color.WHITE : 0xFF1A1A2E);
                cell.getValueTextView().setTextColor(isDark ? 0xFF33A1FF : 0xFF007AFF);
            }
        }

        protected boolean isBreakType(int position) {
            int type = getItemViewType(position);
            return type == TYPE_SHADOW || type == TYPE_INFO_PRIVACY;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            var payload = holder.getPayload();
            onBindViewHolder(holder, position, PARTIAL.equals(payload));
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case TYPE_SHADOW:
                    view = new ShadowSectionCell(mContext, resourcesProvider);
                    break;
                case TYPE_SETTINGS:
                    view = new TextSettingsCell(mContext, resourcesProvider);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_CHECK:
                    view = new TextCheckCell(mContext, resourcesProvider);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_HEADER:
                    view = new HeaderCell(mContext, resourcesProvider);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_NOTIFICATION_CHECK:
                    view = new NotificationsCheckCell(mContext, resourcesProvider);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_DETAIL_SETTINGS:
                    view = new TextDetailSettingsCell(mContext);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_INFO_PRIVACY:
                    view = new TextInfoPrivacyCell(mContext, resourcesProvider);
                    view.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, getThemedColor(Theme.key_windowBackgroundGrayShadow)));
                    break;
                case TYPE_TEXT:
                    view = new TextCell(mContext, resourcesProvider);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_CHECKBOX:
                    view = new TextCheckbox2Cell(mContext);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_RADIO:
                    view = new TextRadioCell(mContext);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_ACCOUNT:
                    view = new AccountCell(mContext);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_EMOJI:
                case TYPE_EMOJI_SELECTION:
                    view = new EmojiSetCell(mContext, viewType == TYPE_EMOJI_SELECTION);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_CREATION:
                    CreationTextCell creationTextCell = new CreationTextCell(mContext, 70, resourcesProvider);
                    creationTextCell.startPadding = 61;
                    view = creationTextCell;
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_FLICKER:
                    view = new FlickerLoadingView(mContext, resourcesProvider);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_CHECK2:
                    view = new TextCheckCell2(mContext);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_CHECKBOX2:
                    CheckBoxCell checkBoxCell = new CheckBoxCell(mContext, CheckBoxCell.TYPE_CHECK_BOX_ROUND, 21, getResourceProvider());
                    checkBoxCell.getCheckBoxRound().setDrawBackgroundAsArc(14);
                    checkBoxCell.getCheckBoxRound().setColor(Theme.key_switch2TrackChecked, Theme.key_radioBackground, Theme.key_checkboxCheck);
                    checkBoxCell.setEnabled(true);
                    view = checkBoxCell;
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            // noinspection ConstantConditions
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }
    }


}
