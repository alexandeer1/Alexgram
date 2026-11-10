package tw.nekomimi.nekogram.ui.cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.BlurredBackgroundWithFadeDrawable;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.utils.glass.source.GlassSourceColor;
import org.telegram.ui.Components.glass.GlassTabView;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.MainTabsLayout;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

import tw.nekomimi.nekogram.helpers.MainTabs;
import xyz.nextalone.nagram.NaConfig;

@SuppressLint("ViewConstructor")
public final class MainTabsPreviewCell extends FrameLayout implements FactorAnimator.Target {

    private static final int HEIGHT = 168;
    private static final int SCROLL_DISTANCE = 72;
    private static final int TABS_BOTTOM_MARGIN = 8;

    private static final int ANIMATOR_ID_TABS_VISIBLE = 0;
    private static final int ANIMATOR_ID_COMPACT = 1;

    private final BoolAnimator tabsVisible = new BoolAnimator(ANIMATOR_ID_TABS_VISIBLE, this, CubicBezierInterpolator.EASE_OUT_QUINT, 380, !MainTabs.isHidden());
    private final BoolAnimator compact = new BoolAnimator(ANIMATOR_ID_COMPACT, this, CubicBezierInterpolator.EASE_OUT_QUINT, 380, NaConfig.INSTANCE.getMainTabsHideTitles().Bool());

    private final BaseFragment fragment;
    private final FlickerLoadingView listView;
    private final View fadeView;
    private final MainTabsLayout tabsView;
    private final GlassTabView[] tabs;
    private final GlassSourceColor backgroundSource = new GlassSourceColor();
    private final BlurredBackgroundDrawable tabsBackground;
    private final Runnable scrollDown = () -> {
        setScrolled(true, true);
        postDelayed(this.scrollUp, 1600);
    };
    private final Runnable scrollUp = () -> {
        setScrolled(false, true);
        postDelayed(scrollDown, 3000);
    };

    public MainTabsPreviewCell(Context context, BaseFragment fragment) {
        super(context);
        this.fragment = fragment;
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        Theme.ResourcesProvider resourcesProvider = fragment.getResourceProvider();
        int account = fragment.getCurrentAccount();

        listView = new FlickerLoadingView(context, resourcesProvider);
        listView.setViewType(FlickerLoadingView.DIALOG_CELL_TYPE);
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, HEIGHT + SCROLL_DISTANCE));

        BlurredBackgroundDrawableViewFactory backgroundFactory = new BlurredBackgroundDrawableViewFactory(backgroundSource);
        fadeView = new View(context);
        BlurredBackgroundWithFadeDrawable fadeDrawable = new BlurredBackgroundWithFadeDrawable(backgroundFactory.create(fadeView, null));
        fadeDrawable.setFadeHeight(dp(60), true);
        fadeView.setBackground(fadeDrawable);
        addView(fadeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 0, Gravity.BOTTOM));

        tabsView = new MainTabsLayout(context, resourcesProvider);
        tabsView.setClipChildren(false);
        tabsView.setPadding(dp(DialogsActivity.MAIN_TABS_MARGIN + 4), dp(DialogsActivity.MAIN_TABS_MARGIN + 4), dp(DialogsActivity.MAIN_TABS_MARGIN + 4), dp(DialogsActivity.MAIN_TABS_MARGIN + 4));
        tabsView.setMaxWidth(dp(328 + DialogsActivity.MAIN_TABS_MARGIN * 2));
        tabs = new GlassTabView[]{
                GlassTabView.createMainTab(context, resourcesProvider, GlassTabView.TabAnimation.CHATS, R.string.MainTabsChats),
                GlassTabView.createMainTab(context, resourcesProvider, GlassTabView.TabAnimation.CONTACTS, R.string.MainTabsContacts),
                UserConfig.getInstance(account).showCallsTab
                        ? GlassTabView.createMainTab(context, resourcesProvider, GlassTabView.TabAnimation.CALLS, R.string.MainTabsCalls)
                        : GlassTabView.createMainTab(context, resourcesProvider, GlassTabView.TabAnimation.SETTINGS, R.string.Settings),
                GlassTabView.createAvatar(context, resourcesProvider, account, R.string.MainTabsProfile)
        };
        for (GlassTabView tab : tabs) {
            tabsView.addView(tab);
            tabsView.setViewVisible(tab, true, false);
        }
        tabsView.setViewVisible(tabs[1], !NaConfig.INSTANCE.getHideContacts().Bool(), false);
        tabs[0].setSelected(true, false);
        tabsBackground = backgroundFactory.create(tabsView, BlurredBackgroundProviderImpl.mainTabs(resourcesProvider));
        tabsBackground.setPadding(dp(DialogsActivity.MAIN_TABS_MARGIN - 0.334f));
        tabsView.setBackground(tabsBackground);
        addView(tabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, TABS_BOTTOM_MARGIN));

        applyCompact();
        applyTabsVisible();
        updateColors();
    }

    public void updateVisibility() {
        removeCallbacks(scrollDown);
        removeCallbacks(scrollUp);
        setScrolled(false, true);
        playHideOnScroll();
    }

    public void updateTitles() {
        compact.setValue(NaConfig.INSTANCE.getMainTabsHideTitles().Bool(), true);
    }

    public void updateContactsTab() {
        if (!compact.isAnimating()) {
            tabsView.setViewVisible(tabs[1], !NaConfig.INSTANCE.getHideContacts().Bool(), true);
        }
    }

    public void updateColors() {
        backgroundSource.setColor(fragment.getThemedColor(Theme.key_windowBackgroundWhite));
        listView.setColors(Theme.isCurrentThemeDark() ? Theme.key_actionBarDefaultSubmenuBackground : Theme.key_listSelector, Theme.key_listSelector, -1);
        tabsBackground.updateColors();
        fadeView.invalidate();
        for (GlassTabView tab : tabs) {
            tab.updateColorsLottie();
        }
        tabsView.invalidate();
        invalidate();
    }

    private void playHideOnScroll() {
        if (MainTabs.isHideOnScroll()) {
            postDelayed(scrollDown, 400);
        }
    }

    private void setScrolled(boolean scrolled, boolean animated) {
        float translationY = scrolled ? -dp(SCROLL_DISTANCE) : 0;
        listView.animate().cancel();
        if (animated) {
            listView.animate().translationY(translationY).setDuration(600).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        } else {
            listView.setTranslationY(translationY);
        }
        tabsVisible.setValue(!scrolled && !MainTabs.isHidden(), animated);
    }

    private void applyCompact() {
        float height = lerp((float) DialogsActivity.MAIN_TABS_HEIGHT, DialogsActivity.FILTER_TABS_HEIGHT, compact.getFloatValue());
        tabsView.setCompact(compact.getFloatValue());
        if (tabsView.getLayoutParams() != null) {
            tabsView.getLayoutParams().height = dp(height + DialogsActivity.MAIN_TABS_MARGIN * 2);
        }
        if (fadeView.getLayoutParams() != null) {
            fadeView.getLayoutParams().height = dp(height + DialogsActivity.MAIN_TABS_MARGIN * 2 + TABS_BOTTOM_MARGIN);
        }
        tabsBackground.setRadius(dp(height / 2f));
    }

    private void applyTabsVisible() {
        float factor = tabsVisible.getFloatValue();
        tabsView.setTranslationY(lerp(dp(40), 0, factor));
        tabsView.setAlpha(factor);
        tabsView.setVisibility(factor > 0 ? VISIBLE : GONE);
        fadeView.setAlpha(factor);
        fadeView.setVisibility(factor > 0 ? VISIBLE : GONE);
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_TABS_VISIBLE) {
            applyTabsVisible();
        } else {
            applyCompact();
        }
    }

    @Override
    public void onFactorChangeFinished(int id, float finalFactor, FactorAnimator callee) {
        if (id == ANIMATOR_ID_COMPACT) {
            post(this::updateContactsTab);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        playHideOnScroll();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(scrollDown);
        removeCallbacks(scrollUp);
        setScrolled(false, false);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(HEIGHT), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        canvas.drawColor(fragment.getThemedColor(Theme.key_windowBackgroundWhite));
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
    }
}
