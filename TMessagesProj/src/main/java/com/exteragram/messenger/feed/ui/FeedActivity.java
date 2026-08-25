package com.exteragram.messenger.feed.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;


import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.feed.FeedConfig;
import com.exteragram.messenger.feed.FeedController;

import java.util.ArrayList;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ChatActivityContainer;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MainTabsActivity;

public class FeedActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, MainTabsActivity.TabFragmentDelegate {
    private ChatActivityContainer chatContainer;
    private boolean embeddedChatCreated;
    private boolean hasMainTabs;
    private int lastConfigGeneration;
    private WindowInsetsCompat lastWindowInsets;
    private final Runnable loadNewPosts;
    private Runnable parentTabsGlassInvalidationCallback;
    private boolean resumedOnce;
    private boolean uiActiveHeld;
    private boolean uiResumedHeld;
    private boolean viewportFullyVisible;

    @Override
    public boolean drawEdgeNavigationBar() {
        return false;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    public FeedActivity() {
        this(null);
    }

    public FeedActivity(Bundle bundle) {
        super(bundle);
        this.loadNewPosts = () -> {
            ChatActivity chatActivity;
            if (chatContainer != null && (chatActivity = chatContainer.chatActivity) != null && uiResumedHeld) {
                chatActivity.loadNewerFeed(true);
            }
        };
    }

    public static void presentFeed(BaseFragment baseFragment) {
        LaunchActivity launchActivity;
        if (!AndroidUtilities.isTablet() || (launchActivity = LaunchActivity.instance) == null || launchActivity.getRightActionBarLayout() == null) {
            if (baseFragment != null) {
                baseFragment.presentFragment(new FeedActivity());
                return;
            }
            return;
        }
        INavigationLayout rightActionBarLayout = LaunchActivity.instance.getRightActionBarLayout();
        if (rightActionBarLayout.getLastFragment() instanceof FeedActivity) {
            return;
        }
        if (!rightActionBarLayout.getFragmentStack().isEmpty()) {
            while (rightActionBarLayout.getFragmentStack().size() - 1 > 0) {
                rightActionBarLayout.removeFragmentFromStack(rightActionBarLayout.getFragmentStack().get(0));
            }
            rightActionBarLayout.closeLastFragment(false);
        }
        rightActionBarLayout.presentFragment(new INavigationLayout.NavigationParams(new FeedActivity()).setNoAnimation(true).forceRightLayout());
    }

    @Override
    public boolean onFragmentCreate() {
        if (this.currentAccount < 0 || this.currentAccount >= 16) {
            this.currentAccount = org.telegram.messenger.UserConfig.selectedAccount;
        }
        Bundle bundle = this.arguments;
        boolean z = bundle != null && bundle.getBoolean("hasMainTabs", false);
        this.hasMainTabs = z;
        this.viewportFullyVisible = !z;
        NotificationCenter.getInstance(this.currentAccount).addObserver(this, NotificationCenter.didReceiveNewMessages);
        NotificationCenter.getInstance(this.currentAccount).addObserver(this, NotificationCenter.feedNeedReload);
        this.lastConfigGeneration = FeedConfig.getInstance(this.currentAccount).getGeneration();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        AndroidUtilities.cancelRunOnUIThread(this.loadNewPosts);
        destroyEmbeddedChat();
        if (this.uiResumedHeld) {
            this.uiResumedHeld = false;
            FeedController.getInstance(this.currentAccount).setUiResumed(false);
        }
        if (this.uiActiveHeld) {
            this.uiActiveHeld = false;
            FeedController.getInstance(this.currentAccount).setUiActive(false);
        }
        Bulletin.removeDelegate(this);
        NotificationCenter.getInstance(this.currentAccount).removeObserver(this, NotificationCenter.didReceiveNewMessages);
        NotificationCenter.getInstance(this.currentAccount).removeObserver(this, NotificationCenter.feedNeedReload);
        super.onFragmentDestroy();
    }

    private void destroyEmbeddedChat() {
        ChatActivity chatActivity;
        ChatActivityContainer chatActivityContainer = this.chatContainer;
        if (chatActivityContainer != null && (chatActivity = chatActivityContainer.chatActivity) != null) {
            if (!this.hasMainTabs && this.embeddedChatCreated) {
                chatActivity.saveFeedScrollPosition();
            }
            this.chatContainer.chatActivity.setFeedChannelsChangedCallback(null);
            this.chatContainer.chatActivity.setGlassSourceInvalidationCallback(null);
            if (this.embeddedChatCreated) {
                this.chatContainer.chatActivity.onFragmentDestroy();
            }
        }
        this.embeddedChatCreated = false;
        this.chatContainer = null;
    }

    @Override
    public boolean onBackPressed(boolean z) {
        ChatActivity chatActivity;
        ChatActivityContainer chatActivityContainer = this.chatContainer;
        if (chatActivityContainer == null || (chatActivity = chatActivityContainer.chatActivity) == null || chatActivity.getActionBar() == null || !this.chatContainer.chatActivity.getActionBar().isActionModeShowed()) {
            return super.onBackPressed(z);
        }
        if (!z) {
            return false;
        }
        this.chatContainer.chatActivity.clearSelectionMode();
        return false;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        boolean z = false;
        if (id == NotificationCenter.didReceiveNewMessages) {
            if (((Boolean) args[2]).booleanValue() || this.chatContainer == null || !FeedController.getInstance(this.currentAccount).isIncludedChannelPost(((Long) args[0]).longValue())) {
                return;
            }
            AndroidUtilities.cancelRunOnUIThread(this.loadNewPosts);
            AndroidUtilities.runOnUIThread(this.loadNewPosts, 1000L);
            return;
        }
        if (id == NotificationCenter.feedNeedReload) {
            ChatActivityContainer chatActivityContainer = this.chatContainer;
            if (chatActivityContainer != null && chatActivityContainer.chatActivity != null) {
                if (args.length > 0 && Boolean.TRUE.equals(args[0])) {
                    z = true;
                }
                this.chatContainer.chatActivity.onFeedChannelsChanged(z);
            }
            updateFeedSubtitle();
        }
    }

    @Override
    public View createView(Context context) {
        destroyEmbeddedChat();
        this.lastWindowInsets = null;
        this.actionBar.setAddToContainer(false);
        this.actionBar.setVisibility(View.GONE);
        FrameLayout frameLayout = new FrameLayout(context);
        this.fragmentView = frameLayout;
        frameLayout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        if (this.hasMainTabs) {
            ViewCompat.setOnApplyWindowInsetsListener(frameLayout, (view, windowInsetsCompat) -> {
                this.lastWindowInsets = windowInsetsCompat;
                // Do not inflate system bar insets here — ChatActivity.checkUi_chatListViewPaddings()
                // already adds dp(getMainTabsHeightWithMargins()) when isFeedSearch() is true.
                // Adding extra dp here causes double bottom-padding on devices/emulators with
                // a visible software navigation bar.
                return windowInsetsCompat;
            });
            frameLayout.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewDetachedFromWindow(View view) {}

                @Override
                public void onViewAttachedToWindow(View view) {
                    if (FeedActivity.this.lastWindowInsets != null) {
                        ViewCompat.dispatchApplyWindowInsets(view, FeedActivity.this.lastWindowInsets);
                    } else {
                        view.requestApplyInsets();
                    }
                }
            });
        }
        FrameLayout frameLayout2 = new FrameLayout(context);
        frameLayout.addView(frameLayout2, LayoutHelper.createFrame(-1, -1, 119));
        Bundle bundle = new Bundle();
        bundle.putInt("chatMode", 7);
        bundle.putInt("searchType", 4);
        bundle.putBoolean("hasMainTabs", this.hasMainTabs);
        ChatActivityContainer chatActivityContainer = new ChatActivityContainer(context, getParentLayout(), bundle) {
            boolean activityCreated = false;

            @Override
            public void initChatActivity() {
                FeedActivity feedActivity;
                View view;
                if (this.activityCreated) {
                    return;
                }
                this.activityCreated = true;
                FeedActivity.this.embeddedChatCreated = true;
                super.initChatActivity();
                FeedActivity.this.applyFloatingWindowLayout();
                FeedActivity.this.setupChatActionBar();
                FeedActivity.this.setupChatTitle();
                if (FeedActivity.this.lastWindowInsets != null && (view = (feedActivity = FeedActivity.this).fragmentView) != null) {
                    ViewCompat.dispatchApplyWindowInsets(view, feedActivity.lastWindowInsets);
                }
                FeedActivity.this.invalidateParentTabsGlass();
            }
        };
        this.chatContainer = chatActivityContainer;
        ChatActivity chatActivity = chatActivityContainer.chatActivity;
        chatActivity.isInsideContainer = false;
        chatActivity.setFeedChannelsChangedCallback(this::updateFeedSubtitle);
        this.chatContainer.chatActivity.setGlassSourceInvalidationCallback(this::invalidateParentTabsGlass);
        updateFeedViewportActive(this.viewportFullyVisible);
        if (!this.uiResumedHeld) {
            this.chatContainer.onPause();
        }
        frameLayout2.addView(this.chatContainer, LayoutHelper.createFrame(-1, -1, 119));
        if (!this.uiActiveHeld) {
            this.uiActiveHeld = true;
            FeedController.getInstance(this.currentAccount).setUiActive(true);
        }
        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public int getTopOffset(int tag) {
                return AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight();
            }

            @Override
            public int getBottomOffset(int tag) {
                return 0;
            }
        });
        return this.fragmentView;
    }

    @Override
    public void onResume() {
        ChatActivityContainer chatActivityContainer;
        ChatActivity chatActivity;
        ChatActivity chatActivity2;
        View view;
        WindowInsetsCompat windowInsetsCompat;
        super.onResume();
        ChatActivityContainer chatActivityContainer2 = this.chatContainer;
        if (chatActivityContainer2 != null) {
            chatActivityContainer2.onResume();
            updateFeedViewportActive(this.viewportFullyVisible);
        }
        if (!this.uiResumedHeld) {
            this.uiResumedHeld = true;
            FeedController.getInstance(this.currentAccount).setUiResumed(true);
        }
        if (this.hasMainTabs && (view = this.fragmentView) != null && (windowInsetsCompat = this.lastWindowInsets) != null) {
            ViewCompat.dispatchApplyWindowInsets(view, windowInsetsCompat);
        }
        reattachCurrentFeedVideoTexture();
        int generation = FeedConfig.getInstance(this.currentAccount).getGeneration();
        if (generation != this.lastConfigGeneration) {
            this.lastConfigGeneration = generation;
            ChatActivityContainer chatActivityContainer3 = this.chatContainer;
            if (chatActivityContainer3 != null && (chatActivity2 = chatActivityContainer3.chatActivity) != null) {
                chatActivity2.applyFeedConfigChange();
            }
        } else if (this.resumedOnce && (chatActivityContainer = this.chatContainer) != null && (chatActivity = chatActivityContainer.chatActivity) != null) {
            chatActivity.reconcileFeedList();
            this.chatContainer.chatActivity.refreshFeedUnreadDivider();
            if (!FeedController.getInstance(this.currentAccount).getMessages().isEmpty()) {
                this.chatContainer.chatActivity.loadNewerFeed(true);
            }
        }
        this.resumedOnce = true;
        updateFeedSubtitle();
    }

    @Override
    public void onBecomeFullyVisible() {
        super.onBecomeFullyVisible();
        this.viewportFullyVisible = true;
        updateFeedViewportActive(true);
        reattachCurrentFeedVideoTexture();
    }

    @Override
    public void onBecomeFullyHidden() {
        this.viewportFullyVisible = false;
        updateFeedViewportActive(false);
        super.onBecomeFullyHidden();
    }

    @Override
    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        if (this.hasMainTabs) {
            this.viewportFullyVisible = false;
            updateFeedViewportActive(false);
        }
        super.onTransitionAnimationStart(isOpen, backward);
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (this.hasMainTabs) {
            this.viewportFullyVisible = isOpen;
            updateFeedViewportActive(isOpen);
        }
    }

    public void onParentBecomeFullyVisible() {
        reattachCurrentFeedVideoTexture();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (this.chatContainer != null) {
            updateFeedViewportActive(false);
            this.chatContainer.onPause();
        }
        if (this.uiResumedHeld) {
            this.uiResumedHeld = false;
            FeedController.getInstance(this.currentAccount).setUiResumed(false);
        }
    }

    private void updateFeedViewportActive(boolean z) {
        ChatActivity chatActivity;
        ChatActivityContainer chatActivityContainer = this.chatContainer;
        if (chatActivityContainer == null || (chatActivity = chatActivityContainer.chatActivity) == null) {
            return;
        }
        chatActivity.setFeedViewportActive(z);
    }

    @Override
    public boolean canParentTabsSlide(MotionEvent motionEvent, boolean z) {
        ChatActivity chatActivity;
        ChatActivityContainer chatActivityContainer = this.chatContainer;
        return chatActivityContainer == null || (chatActivity = chatActivityContainer.chatActivity) == null || chatActivity.getActionBar() == null || !this.chatContainer.chatActivity.getActionBar().isActionModeShowed();
    }

    @Override
    public boolean isLightStatusBar() {
        ChatActivity chatActivity;
        ChatActivityContainer chatActivityContainer = this.chatContainer;
        if (chatActivityContainer != null && (chatActivity = chatActivityContainer.chatActivity) != null) {
            return chatActivity.isLightStatusBar();
        }
        return !Theme.isCurrentThemeDark();
    }

    private void reattachCurrentFeedVideoTexture() {
        ChatActivity chatActivity;
        ChatActivityContainer chatActivityContainer = this.chatContainer;
        if (chatActivityContainer == null || (chatActivity = chatActivityContainer.chatActivity) == null) {
            return;
        }
        chatActivity.reattachCurrentFeedVideoTexture();
    }

    private void setupChatActionBar() {
        ChatActivity chatActivity;
        final ActionBar actionBar;
        ChatActivityContainer chatActivityContainer = this.chatContainer;
        if (chatActivityContainer == null || (chatActivity = chatActivityContainer.chatActivity) == null || (actionBar = chatActivity.getActionBar()) == null) {
            return;
        }
        ActionBarMenu actionBarMenuCreateMenu = actionBar.createMenu();
        if (actionBarMenuCreateMenu.getItem(76) == null) {
            actionBarMenuCreateMenu.addItem(76, R.drawable.msg_markread, this.chatContainer.chatActivity.themeDelegate).setContentDescription(LocaleController.getString("FeedMarkAllRead", R.string.FeedMarkAllRead));
        }
        if (actionBarMenuCreateMenu.getItem(75) == null) {
            actionBarMenuCreateMenu.addItem(75, R.drawable.msg_settings, this.chatContainer.chatActivity.themeDelegate).setContentDescription(LocaleController.getString("FeedSettings", R.string.FeedSettings));
        }
        if (this.hasMainTabs) {
            applyMainTabsHeaderLayout();
        }
        final ActionBar.ActionBarMenuOnItemClick actionBarMenuOnItemClick = actionBar.getActionBarMenuOnItemClick();
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (FeedActivity.this.chatContainer != null && FeedActivity.this.chatContainer.chatActivity != null && FeedActivity.this.chatContainer.chatActivity.getActionBar() != null && FeedActivity.this.chatContainer.chatActivity.getActionBar().isActionModeShowed()) {
                        FeedActivity.this.chatContainer.chatActivity.clearSelectionMode();
                        return;
                    }
                    if (FeedActivity.this.hasMainTabs) {
                        if (FeedActivity.this.getParentLayout() != null && FeedActivity.this.getParentLayout().getLastFragment() instanceof MainTabsActivity) {
                            ((MainTabsActivity) FeedActivity.this.getParentLayout().getLastFragment()).onBackPressed(true);
                            return;
                        }
                        if (LaunchActivity.instance != null && LaunchActivity.instance.getActionBarLayout() != null && LaunchActivity.instance.getActionBarLayout().getLastFragment() instanceof MainTabsActivity) {
                            ((MainTabsActivity) LaunchActivity.instance.getActionBarLayout().getLastFragment()).onBackPressed(true);
                            return;
                        }
                    }
                    FeedActivity.this.finishFragment();
                    return;
                }
                if (id == 76) {
                    FeedActivity.this.showMarkAllReadDialog();
                    return;
                }
                if (id == 75) {
                    FeedActivity.this.presentFragment(new FeedChannelsActivity());
                    return;
                }
                if (actionBarMenuOnItemClick != null) {
                    actionBarMenuOnItemClick.onItemClick(id);
                }
            }

            @Override
            public boolean canOpenMenu() {
                return actionBarMenuOnItemClick == null || actionBarMenuOnItemClick.canOpenMenu();
            }
        });
    }

    private void applyFloatingWindowLayout() {
        ChatActivityContainer chatActivityContainer;
        ChatActivity chatActivity;
        if (getParentLayout() == null || !getParentLayout().isLayersLayout() || (chatActivityContainer = this.chatContainer) == null || (chatActivity = chatActivityContainer.chatActivity) == null) {
            return;
        }
        if (chatActivity.getActionBar() != null) {
            chatActivity.getActionBar().setOccupyStatusBar(false);
        }
        ChatAvatarContainer chatAvatarContainer = chatActivity.avatarContainer;
        if (chatAvatarContainer != null) {
            chatAvatarContainer.setOccupyStatusBar(false);
        }
        ChatActivity.ChatActivityFragmentView chatActivityFragmentView = chatActivity.contentView;
        if (chatActivityFragmentView != null) {
            chatActivityFragmentView.setOccupyStatusBar(false);
        }
    }

    private void applyMainTabsHeaderLayout() {
        ChatActivity chatActivity;
        ChatAvatarContainer chatAvatarContainer;
        ChatActivityContainer chatActivityContainer = this.chatContainer;
        if (chatActivityContainer == null || (chatActivity = chatActivityContainer.chatActivity) == null || (chatAvatarContainer = chatActivity.avatarContainer) == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = chatAvatarContainer.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;
            int iDp = AndroidUtilities.dp(ExteraConfig.getNewChatHeaderStyle() ? 12.0f : 56.0f);
            if (marginLayoutParams.leftMargin != iDp) {
                marginLayoutParams.leftMargin = iDp;
                this.chatContainer.chatActivity.avatarContainer.setLayoutParams(marginLayoutParams);
            }
        }
    }

    private void showMarkAllReadDialog() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(LocaleController.getString("FeedMarkAllRead", R.string.FeedMarkAllRead));
        builder.setMessage(LocaleController.getString("FeedMarkAllReadConfirm", R.string.FeedMarkAllReadConfirm));
        builder.setPositiveButton(LocaleController.getString("MarkAsRead", R.string.MarkAsRead), (dialog, which) -> {
            markAllRead();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, LocaleController.getString("FeedMarkAllReadDone", R.string.FeedMarkAllReadDone)).show();
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(builder.create());
    }

    public void markAllRead() {
        ChatActivity chatActivity;
        ChatActivityContainer chatActivityContainer = this.chatContainer;
        if (chatActivityContainer != null && (chatActivity = chatActivityContainer.chatActivity) != null) {
            chatActivity.markFeedAsRead();
        } else {
            FeedController.getInstance(this.currentAccount).markAllRead();
        }
    }

    @Override
    public void onParentScrollToTop() {
        ChatActivity chatActivity;
        ChatActivityContainer chatActivityContainer = this.chatContainer;
        if (chatActivityContainer == null || (chatActivity = chatActivityContainer.chatActivity) == null) {
            return;
        }
        chatActivity.onPageDownClicked();
    }

    private void setupChatTitle() {
        ChatActivity chatActivity;
        ChatAvatarContainer chatAvatarContainer;
        ChatActivityContainer chatActivityContainer = this.chatContainer;
        if (chatActivityContainer == null || (chatActivity = chatActivityContainer.chatActivity) == null || (chatAvatarContainer = chatActivity.avatarContainer) == null) {
            return;
        }
        chatAvatarContainer.setTitle(LocaleController.getString("Feed", R.string.Feed));
        this.chatContainer.chatActivity.avatarContainer.setFeedAvatar();
        updateFeedSubtitle();
    }

    private void updateFeedSubtitle() {
        FeedController feedController = FeedController.getInstance(this.currentAccount);
        setFeedSubtitle(feedController.getIncludedChannelCount());
        feedController.loadChannels((channels, includedCount, failed, generation) -> {
            if (!failed) {
                setFeedSubtitle(includedCount);
            }
        });
    }

    private void setFeedSubtitle(int i) {
        ChatActivity chatActivity;
        ChatAvatarContainer chatAvatarContainer;
        ChatActivityContainer chatActivityContainer = this.chatContainer;
        if (chatActivityContainer == null || (chatActivity = chatActivityContainer.chatActivity) == null || (chatAvatarContainer = chatActivity.avatarContainer) == null) {
            return;
        }
        chatAvatarContainer.setSubtitle(LocaleController.formatPluralString("Channels", i));
        View subtitleTextView = this.chatContainer.chatActivity.avatarContainer.getSubtitleTextView();
        if (subtitleTextView != null) {
            subtitleTextView.setVisibility(View.VISIBLE);
        }
    }

    public BlurredBackgroundSourceRenderNode getGlassSource() {
        return null;
    }

    public void setParentTabsGlassInvalidationCallback(Runnable runnable) {
        this.parentTabsGlassInvalidationCallback = runnable;
    }

    private void invalidateParentTabsGlass() {
        if (parentTabsGlassInvalidationCallback != null) {
            parentTabsGlassInvalidationCallback.run();
        }
    }
}
