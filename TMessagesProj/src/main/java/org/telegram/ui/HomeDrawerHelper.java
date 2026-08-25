package org.telegram.ui;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.DrawerContainer;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.DrawerLayoutAdapter;
import org.telegram.ui.Cells.DrawerActionCell;
import org.telegram.ui.Cells.DrawerActionCheckCell;
import org.telegram.ui.Cells.DrawerAddCell;
import org.telegram.ui.Cells.DrawerProfileCell;
import org.telegram.ui.Cells.DrawerUserCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MediaActivity;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SharedMediaLayout;
import org.telegram.ui.Components.SideMenultItemAnimator;
import org.telegram.ui.Stars.StarGiftSheet;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.bots.BotWebViewSheet;
import org.telegram.ui.web.WebBrowserSettings;

import tw.nekomimi.nekogram.ChatHistoryActivity;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.AppRestartHelper;
import tw.nekomimi.nekogram.settings.GhostModeActivity;
import tw.nekomimi.nekogram.settings.NekoSettingsActivity;
import tw.nekomimi.nekogram.ui.BookmarkManagerActivity;
import tw.nekomimi.nekogram.utils.BrowserUtils;
import xyz.nextalone.nagram.NaConfig;

public class HomeDrawerHelper {
    private final LaunchActivity activity;

    private DrawerLayoutAdapter drawerLayoutAdapter;
    private RecyclerListView sideMenu;
    private DrawerProfileCell sideMenuHeaderView;
    private SideMenultItemAnimator itemAnimator;
    private FrameLayout sideMenuContainer;
    private SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow selectAnimatedEmojiDialog;
    private final RectF accountPanelRect = new RectF();
    private final Paint accountPanelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accountPanelStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public HomeDrawerHelper(LaunchActivity activity) {
        this.activity = activity;
    }

    public void onCreate() {
        setupSideMenu();
        syncHomeDrawer();
        NotificationCenter.getGlobalInstance().addObserver(drawerLayoutAdapter, NotificationCenter.proxySettingsChanged);
    }

    public void onDestroy() {
        if (drawerLayoutAdapter != null) {
            NotificationCenter.getGlobalInstance().removeObserver(drawerLayoutAdapter, NotificationCenter.proxySettingsChanged);
        }
        dismissSelectAnimatedEmojiDialog();
    }

    public void onFragmentStackChanged() {
        bindHomeDrawerToDialogs();
        BaseFragment lastFragment = activity.getLastFragment();
        if (lastFragment instanceof MainTabsActivity || lastFragment instanceof DialogsActivity dialogsActivity && dialogsActivity.isMainDialogList()) {
            DialogsActivity homeDrawerDialogsActivity = getHomeDrawerDialogsActivity();
            if (homeDrawerDialogsActivity != null) {
                homeDrawerDialogsActivity.postUpdateHomeDrawerAvailability();
            }
        }
    }

    public void dismissSelectAnimatedEmojiDialog() {
        if (selectAnimatedEmojiDialog != null) {
            selectAnimatedEmojiDialog.dismiss();
            selectAnimatedEmojiDialog = null;
        }
    }

    public void onNotificationReceived(int id, int account, Object[] args) {
        if (id == NotificationCenter.mainUserInfoChanged || id == NotificationCenter.attachMenuBotsDidLoad || id == NotificationCenter.currentUserPremiumStatusChanged) {
            refreshDrawer(true);
        } else if (id == NotificationCenter.didSetNewWallpapper) {
            refreshDrawer(false);
        } else if (id == NotificationCenter.themeAccentListUpdated) {
            AndroidUtilities.runOnUIThread(() -> {
                if (!DialogsActivity.switchingTheme) {
                    refreshDrawer(false);
                }
            });
        } else if (id == NotificationCenter.notificationsCountUpdated) {
            if (sideMenu != null) {
                Integer accountNum = (Integer) args[0];
                int count = sideMenu.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = sideMenu.getChildAt(a);
                    if (child instanceof DrawerUserCell && ((DrawerUserCell) child).getAccountNumber() == accountNum) {
                        child.invalidate();
                        break;
                    }
                }
            }
        } else if (id == NotificationCenter.didSetNewTheme) {
            Boolean nightTheme = (Boolean) args[0];
            if (!nightTheme) {
                if (!DialogsActivity.switchingTheme) {
                    refreshDrawer(true);
                } else {
                    bindHomeDrawerToDialogs();
                    if (sideMenuContainer != null) {
                        sideMenuContainer.setBackgroundColor(Theme.getColor(Theme.key_chats_menuBackground));
                    }
                    if (sideMenu != null) {
                        sideMenu.setBackgroundColor(Theme.getColor(Theme.key_chats_menuBackground));
                        sideMenu.setGlowColor(Theme.getColor(Theme.key_chats_menuBackground));
                        sideMenu.setListSelectorColor(Theme.getColor(Theme.key_listSelector));
                        sideMenu.invalidate();
                    }
                }
            }
        }
    }

    public DialogsActivity getHomeDrawerDialogsActivity() {
        BaseFragment fragment = null;
        if (activity.actionBarLayout != null && activity.actionBarLayout.getFragmentStack() != null && !activity.actionBarLayout.getFragmentStack().isEmpty()) {
            fragment = activity.actionBarLayout.getFragmentStack().get(0);
        } else {
            INavigationLayout layersActionBarLayout = activity.getLayersActionBarLayout();
            if (layersActionBarLayout != null && layersActionBarLayout.getFragmentStack() != null && !layersActionBarLayout.getFragmentStack().isEmpty()) {
                fragment = layersActionBarLayout.getFragmentStack().get(0);
            }
        }
        if (fragment instanceof MainTabsActivity mainTabsActivity) {
            DialogsActivity dialogsActivity = mainTabsActivity.getDialogsActivity();
            if (dialogsActivity == null) {
                dialogsActivity = mainTabsActivity.prepareDialogsActivity(null);
            }
            return dialogsActivity;
        } else if (fragment instanceof DialogsActivity dialogsActivity && dialogsActivity.isMainDialogList()) {
            return dialogsActivity;
        }
        return null;
    }

    public void bindHomeDrawerToDialogs() {
        if (sideMenu == null) {
            return;
        }
        DialogsActivity dialogsActivity = getHomeDrawerDialogsActivity();
        if (dialogsActivity != null) {
            dialogsActivity.setSideMenu(sideMenu);
        }
    }

    private void refreshDrawerVisibleViews() {
        if (sideMenu == null) {
            return;
        }
        TLRPC.User user = UserConfig.getInstance(activity.currentAccount).getCurrentUser();
        boolean accountsShown = drawerLayoutAdapter != null && drawerLayoutAdapter.isAccountsShown();
        if (sideMenuHeaderView != null) {
            sideMenuHeaderView.setUser(user, accountsShown);
            sideMenuHeaderView.updateColors();
        }
        int childCount = sideMenu.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = sideMenu.getChildAt(i);
            if (child instanceof DrawerUserCell drawerUserCell) {
                drawerUserCell.setAccount(drawerUserCell.getAccountNumber());
            } else if (child instanceof DrawerActionCell drawerActionCell && drawerLayoutAdapter != null) {
                int position = sideMenu.getChildAdapterPosition(child);
                if (position != RecyclerView.NO_POSITION && drawerLayoutAdapter.getId(position) == 15) {
                    boolean hasStatus = user != null && DialogObject.getEmojiStatusDocumentId(user.emoji_status) != 0;
                    drawerActionCell.updateTextAndIcon(
                            activity.getString(hasStatus ? R.string.ChangeEmojiStatus : R.string.SetEmojiStatus),
                            hasStatus ? R.drawable.msg_status_edit_solar : R.drawable.msg_status_set_solar
                    );
                }
            }
        }
    }

    public void refreshDrawer(boolean notifyAdapter) {
        bindHomeDrawerToDialogs();
        if (sideMenuContainer != null) {
            sideMenuContainer.setBackgroundColor(Theme.getColor(Theme.key_chats_menuBackground));
        }
        if (sideMenu != null) {
            sideMenu.setBackgroundColor(Theme.getColor(Theme.key_chats_menuBackground));
            sideMenu.setGlowColor(Theme.getColor(Theme.key_chats_menuBackground));
            sideMenu.setListSelectorColor(Theme.getColor(Theme.key_listSelector));
        }
        if (notifyAdapter && drawerLayoutAdapter != null) {
            drawerLayoutAdapter.notifyDataSetChanged();
        }
        refreshDrawerVisibleViews();
    }

    public void setupSideMenu() {
        if (activity.drawerLayoutContainer == null || sideMenu != null) {
            return;
        }

        sideMenuContainer = new DrawerContainer(activity);
        sideMenu = new RecyclerListView(activity) {
            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                int restore = -1;
                if (itemAnimator != null && itemAnimator.isRunning() && itemAnimator.isAnimatingChild(child)) {
                    restore = canvas.save();
                    canvas.clipRect(0, itemAnimator.getAnimationClipTop(), getMeasuredWidth(), getMeasuredHeight());
                }
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (restore >= 0) {
                    canvas.restoreToCount(restore);
                }
                return result;
            }
        };
        itemAnimator = new SideMenultItemAnimator(sideMenu);
        sideMenu.setItemAnimator(itemAnimator);
        sideMenu.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                drawAccountsPanel(canvas, parent);
            }
        });
        sideMenu.setClipToPadding(false);
        sideMenu.setBackgroundColor(Theme.getColor(Theme.key_chats_menuBackground));
        sideMenuContainer.setBackgroundColor(Theme.getColor(Theme.key_chats_menuBackground));
        sideMenu.setLayoutManager(new LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false));
        sideMenu.setAllowItemsInteractionDuringAnimation(false);
        sideMenu.setGlowColor(Theme.getColor(Theme.key_chats_menuBackground));
        sideMenu.setListSelectorColor(Theme.getColor(Theme.key_listSelector));
        sideMenu.setAdapter(drawerLayoutAdapter = new DrawerLayoutAdapter(activity, itemAnimator, activity.drawerLayoutContainer));
        sideMenuContainer.addView(sideMenu, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        sideMenuHeaderView = new DrawerProfileCell(activity, activity.drawerLayoutContainer) {
            @Override
            protected void onPremiumClick() {
                showCurrentUserStatusDialog();
            }
        };
        drawerLayoutAdapter.setProfileCell(sideMenuHeaderView);
        sideMenuHeaderView.setAccountsShown(drawerLayoutAdapter.isAccountsShown(), false);
        TLRPC.User currentUser = UserConfig.getInstance(activity.currentAccount).getCurrentUser();
        if (currentUser != null) {
            sideMenuHeaderView.setUser(currentUser, drawerLayoutAdapter.isAccountsShown());
        } else {
            sideMenuHeaderView.updateColors();
        }
        final float[] drawerHeaderTouch = new float[2];
        sideMenuHeaderView.setOnTouchListener((v, event) -> {
            drawerHeaderTouch[0] = event.getX();
            drawerHeaderTouch[1] = event.getY();
            return false;
        });
        sideMenuHeaderView.setOnClickListener(v -> {
            if (sideMenuHeaderView.isInAvatar(drawerHeaderTouch[0], drawerHeaderTouch[1])) {
                openSettings(sideMenuHeaderView.hasAvatar());
            } else if (drawerLayoutAdapter != null) {
                drawerLayoutAdapter.setAccountsShown(!drawerLayoutAdapter.isAccountsShown(), true);
            }
        });
        sideMenuContainer.addView(sideMenuHeaderView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        activity.drawerLayoutContainer.setDrawerLayout(sideMenuContainer, sideMenu);

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) sideMenuContainer.getLayoutParams();
        Point screenSize = AndroidUtilities.getRealScreenSize();
        layoutParams.width = AndroidUtilities.isTablet()
                ? AndroidUtilities.dp(320)
                : Math.min(AndroidUtilities.dp(320), Math.min(screenSize.x, screenSize.y) - AndroidUtilities.dp(56));
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        sideMenuContainer.setLayoutParams(layoutParams);

        sideMenu.setOnItemClickListener((view, position, x, y) -> {
            if (drawerLayoutAdapter.click(view, position)) {
                activity.drawerLayoutContainer.closeDrawer(false);
                return;
            }
            if (view instanceof DrawerUserCell drawerUserCell) {
                activity.switchToAccount(drawerUserCell.getAccountNumber(), true);
                activity.drawerLayoutContainer.closeDrawer(false);
                return;
            }
            if (view instanceof DrawerAddCell) {
                openAddAccountFromDrawer();
                return;
            }
            if (view instanceof DrawerActionCheckCell) {
                int id = drawerLayoutAdapter.getId(position);
                if (id == 13) {
                    activity.presentFragment(new ProxyListActivity());
                    activity.drawerLayoutContainer.closeDrawer(false);
                }
                return;
            }

            int id = drawerLayoutAdapter.getId(position);
            TLRPC.TL_attachMenuBot attachMenuBot = drawerLayoutAdapter.getAttachMenuBot(position);
            if (attachMenuBot != null) {
                openAttachMenuBotFromDrawer(attachMenuBot);
                return;
            }
            handleDrawerItemClick(id);
        });

        final ItemTouchHelper sideMenuTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            private RecyclerView.ViewHolder selectedViewHolder;

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                if (viewHolder.getItemViewType() != target.getItemViewType()) {
                    return false;
                }
                drawerLayoutAdapter.swapElements(viewHolder.getAdapterPosition(), target.getAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                clearSelectedViewHolder();
                if (actionState != ItemTouchHelper.ACTION_STATE_IDLE && viewHolder != null) {
                    selectedViewHolder = viewHolder;
                    final View view = viewHolder.itemView;
                    sideMenu.cancelClickRunnables(false);
                    view.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
                    view.setElevation(AndroidUtilities.dp(1));
                }
                super.onSelectedChanged(viewHolder, actionState);
            }

            @Override
            public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                clearSelectedViewHolder();
            }

            private void clearSelectedViewHolder() {
                if (selectedViewHolder == null) {
                    return;
                }
                selectedViewHolder.itemView.setBackgroundColor(0);
                selectedViewHolder.itemView.setElevation(0f);
                selectedViewHolder = null;
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                final View view = viewHolder.itemView;
                if (drawerLayoutAdapter.isAccountsShown()) {
                    RecyclerView.ViewHolder topViewHolder = recyclerView.findViewHolderForAdapterPosition(drawerLayoutAdapter.getFirstAccountPosition() - 1);
                    RecyclerView.ViewHolder bottomViewHolder = recyclerView.findViewHolderForAdapterPosition(drawerLayoutAdapter.getLastAccountPosition() + 1);
                    if (topViewHolder != null && topViewHolder.itemView != null && topViewHolder.itemView.getBottom() == view.getTop() && dY < 0f) {
                        dY = 0f;
                    } else if (bottomViewHolder != null && bottomViewHolder.itemView != null && bottomViewHolder.itemView.getTop() == view.getBottom() && dY > 0f) {
                        dY = 0f;
                    }
                }
                view.setTranslationX(dX);
                view.setTranslationY(dY);
            }
        });
        sideMenuTouchHelper.attachToRecyclerView(sideMenu);
        sideMenu.setOnItemLongClickListener((view, position) -> {
            if (view instanceof DrawerUserCell drawerUserCell) {
                if (drawerUserCell.getAccountNumber() == activity.currentAccount || AndroidUtilities.isTablet()) {
                    sideMenuTouchHelper.startDrag(sideMenu.getChildViewHolder(view));
                    return true;
                }
            }
            if (view instanceof DrawerActionCell) {
                int id = drawerLayoutAdapter.getId(position);
                TLRPC.TL_attachMenuBot attachMenuBot = drawerLayoutAdapter.getAttachMenuBot(position);
                if (attachMenuBot != null) {
                    BotWebViewSheet.deleteBot(activity.currentAccount, attachMenuBot.bot_id, null);
                    return true;
                } else if (id == DrawerLayoutAdapter.nkbtnGhostMode) {
                    activity.presentFragment(new GhostModeActivity());
                    activity.drawerLayoutContainer.closeDrawer(false);
                    return true;
                } else if (id == DrawerLayoutAdapter.nkbtnBrowser) {
                    activity.presentFragment(new WebBrowserSettings(null));
                    activity.drawerLayoutContainer.closeDrawer(false);
                    return true;
                }
            }
            return false;
        });
    }

    private void drawAccountsPanel(Canvas canvas, RecyclerView parent) {
        if (drawerLayoutAdapter == null || !drawerLayoutAdapter.isAccountsShown()) {
            return;
        }
        int firstPosition = drawerLayoutAdapter.getFirstAccountPosition();
        int addAccountPosition = drawerLayoutAdapter.getLastAccountPosition() + 1;
        if (firstPosition == RecyclerView.NO_POSITION || addAccountPosition <= firstPosition) {
            return;
        }

        float top = Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);
            if (position >= firstPosition && position <= addAccountPosition) {
                top = Math.min(top, child.getTop() + child.getTranslationY());
                bottom = Math.max(bottom, child.getBottom() + child.getTranslationY());
            }
        }
        if (top == Float.MAX_VALUE || bottom == -Float.MAX_VALUE) {
            return;
        }

        int menuBackground = Theme.getColor(Theme.key_chats_menuBackground);
        int panelOverlay = Theme.isCurrentThemeDark()
                ? Theme.multAlpha(0xff000000, 0.18f)
                : Theme.multAlpha(Theme.getColor(Theme.key_chats_menuItemIcon), 0.065f);
        accountPanelPaint.setColor(Theme.blendOver(menuBackground, panelOverlay));
        accountPanelStrokePaint.setStyle(Paint.Style.STROKE);
        accountPanelStrokePaint.setStrokeWidth(AndroidUtilities.dpf2(1));
        accountPanelStrokePaint.setColor(Theme.multAlpha(
                Theme.getColor(Theme.key_chats_menuItemIcon),
                Theme.isCurrentThemeDark() ? 0.22f : 0.14f
        ));

        accountPanelRect.set(
                AndroidUtilities.dp(20),
                top - AndroidUtilities.dp(1),
                parent.getWidth() - AndroidUtilities.dp(20),
                bottom + AndroidUtilities.dp(1)
        );
        float radius = AndroidUtilities.dp(20);
        canvas.drawRoundRect(accountPanelRect, radius, radius, accountPanelPaint);
        canvas.drawRoundRect(accountPanelRect, radius, radius, accountPanelStrokePaint);
    }

    public void syncHomeDrawer() {
        if (activity.drawerLayoutContainer == null || sideMenuContainer == null) {
            return;
        }
        boolean enabled = NekoConfig.navigationDrawerEnabled.Bool();
        sideMenuContainer.setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (!enabled) {
            activity.drawerLayoutContainer.closeDrawer(false);
            activity.drawerLayoutContainer.setAllowOpenDrawer(false, false);
        }
        refreshDrawer(true);
    }

    private void showCurrentUserStatusDialog() {
        showDrawerStatusDialog();
    }

    private void showDrawerStatusDialog() {
        if (selectAnimatedEmojiDialog != null || SharedConfig.appLocked) {
            return;
        }
        BaseFragment fragment = getHomeDrawerDialogsActivity();
        if (fragment == null) {
            fragment = activity.getLastFragment();
        }
        if (fragment == null) {
            return;
        }
        final View profileCell = sideMenuHeaderView;
        if (profileCell == null) {
            if (fragment instanceof DialogsActivity dialogsActivity) {
                dialogsActivity.showSelectStatusDialog();
            }
            return;
        }
        final SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[] popup = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[1];
        TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId());
        int xoff = 0;
        int yoff = 0;
        AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable scrimDrawable = null;
        View scrimDrawableParent = null;
        DrawerProfileCell profileCellCasted = null;
        if (profileCell instanceof DrawerProfileCell drawerProfileCell) {
            profileCellCasted = drawerProfileCell;
            scrimDrawable = drawerProfileCell.getEmojiStatusDrawable();
            if (scrimDrawable != null) {
                scrimDrawable.play();
            }
            scrimDrawableParent = drawerProfileCell.getEmojiStatusDrawableParent();
            drawerProfileCell.getEmojiStatusLocation(AndroidUtilities.rectTmp2);
            yoff = -(profileCell.getHeight() - AndroidUtilities.rectTmp2.centerY()) - AndroidUtilities.dp(16);
            xoff = AndroidUtilities.rectTmp2.centerX();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && activity.getWindow() != null
                    && activity.getWindow().getDecorView() != null
                    && activity.getWindow().getDecorView().getRootWindowInsets() != null) {
                xoff -= activity.getWindow().getDecorView().getRootWindowInsets().getStableInsetLeft();
            }
        } else {
            xoff = profileCell.getWidth() / 2;
            yoff = -(profileCell.getHeight() - AndroidUtilities.dp(48)) - AndroidUtilities.dp(16);
        }
        SelectAnimatedEmojiDialog popupLayout = new SelectAnimatedEmojiDialog(fragment, activity, true, xoff, SelectAnimatedEmojiDialog.TYPE_EMOJI_STATUS, null) {
            @Override
            public void onSettings() {
                if (activity.drawerLayoutContainer != null) {
                    activity.drawerLayoutContainer.closeDrawer(false);
                }
            }

            @Override
            protected boolean willApplyEmoji(View view, Long documentId, TLRPC.Document document, TL_stars.TL_starGiftUnique gift, Integer until) {
                if (gift != null) {
                    final TL_stars.SavedStarGift savedStarGift = StarsController.getInstance(activity.currentAccount).findUserStarGift(gift.id);
                    return savedStarGift == null || MessagesController.getGlobalMainSettings().getInt("statusgiftpage", 0) >= 2;
                }
                return true;
            }

            @Override
            protected void onEmojiSelected(View emojiView, Long documentId, TLRPC.Document document, TL_stars.TL_starGiftUnique gift, Integer until) {
                final TLRPC.EmojiStatus emojiStatus;
                if (documentId == null) {
                    emojiStatus = new TLRPC.TL_emojiStatusEmpty();
                } else if (gift != null) {
                    final TL_stars.SavedStarGift savedStarGift = StarsController.getInstance(activity.currentAccount).findUserStarGift(gift.id);
                    if (savedStarGift != null && MessagesController.getGlobalMainSettings().getInt("statusgiftpage", 0) < 2) {
                        MessagesController.getGlobalMainSettings().edit().putInt("statusgiftpage", MessagesController.getGlobalMainSettings().getInt("statusgiftpage", 0) + 1).apply();
                        new StarGiftSheet(activity, activity.currentAccount, UserConfig.getInstance(activity.currentAccount).getClientUserId(), null)
                                .set(savedStarGift, null)
                                .setupWearPage()
                                .show();
                        if (popup[0] != null) {
                            selectAnimatedEmojiDialog = null;
                            popup[0].dismiss();
                        }
                        return;
                    }
                    final TLRPC.TL_inputEmojiStatusCollectible status = new TLRPC.TL_inputEmojiStatusCollectible();
                    status.collectible_id = gift.id;
                    if (until != null) {
                        status.flags |= 1;
                        status.until = until;
                    }
                    emojiStatus = status;
                } else {
                    final TLRPC.TL_emojiStatus status = new TLRPC.TL_emojiStatus();
                    status.document_id = documentId;
                    if (until != null) {
                        status.flags |= 1;
                        status.until = until;
                    }
                    emojiStatus = status;
                }
                MessagesController.getInstance(activity.currentAccount).updateEmojiStatus(emojiStatus, gift);
                TLRPC.User currentUser = UserConfig.getInstance(activity.currentAccount).getCurrentUser();
                if (currentUser != null && sideMenuHeaderView != null) {
                    if (documentId != null) {
                        sideMenuHeaderView.animateStateChange(documentId);
                    }
                    sideMenuHeaderView.setUser(currentUser, drawerLayoutAdapter != null && drawerLayoutAdapter.isAccountsShown());
                }
                if (currentUser != null && sideMenu != null) {
                    for (int i = 0; i < sideMenu.getChildCount(); i++) {
                        View child = sideMenu.getChildAt(i);
                        if (child instanceof DrawerUserCell drawerUserCell) {
                            drawerUserCell.setAccount(drawerUserCell.getAccountNumber());
                        } else if (child instanceof DrawerActionCell drawerActionCell && drawerLayoutAdapter != null) {
                            int position = sideMenu.getChildAdapterPosition(child);
                            if (position != RecyclerView.NO_POSITION && drawerLayoutAdapter.getId(position) == 15) {
                                boolean hasStatus = currentUser.emoji_status != null && DialogObject.getEmojiStatusDocumentId(currentUser.emoji_status) != 0;
                                drawerActionCell.updateTextAndIcon(
                                        activity.getString(hasStatus ? R.string.ChangeEmojiStatus : R.string.SetEmojiStatus),
                                        hasStatus ? R.drawable.msg_status_edit_solar : R.drawable.msg_status_set_solar
                                );
                            }
                        }
                    }
                } else {
                    refreshDrawer(true);
                }
                if (popup[0] != null) {
                    selectAnimatedEmojiDialog = null;
                    popup[0].dismiss();
                }
            }
        };
        if (user != null && DialogObject.getEmojiStatusUntil(user.emoji_status) > 0) {
            popupLayout.setExpireDateHint(DialogObject.getEmojiStatusUntil(user.emoji_status));
        }
        if (profileCellCasted != null && profileCellCasted.getEmojiStatusGiftId() != null) {
            popupLayout.setSelected(profileCellCasted.getEmojiStatusGiftId());
        } else if (scrimDrawable != null && scrimDrawable.getDrawable() instanceof AnimatedEmojiDrawable drawable) {
            popupLayout.setSelected(drawable.getDocumentId());
        } else {
            long selectedDocumentId = user == null ? 0 : DialogObject.getEmojiStatusDocumentId(user.emoji_status);
            popupLayout.setSelected(selectedDocumentId != 0 ? selectedDocumentId : null);
        }
        popupLayout.setSaveState(2);
        popupLayout.setScrimDrawable(scrimDrawable, scrimDrawableParent);
        popup[0] = selectAnimatedEmojiDialog = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                selectAnimatedEmojiDialog = null;
            }
        };
        popup[0].showAsDropDown(profileCell, 0, yoff, Gravity.TOP);
        popup[0].dimBehind();
    }

    private void openAddAccountFromDrawer() {
        int freeAccounts = 0;
        Integer availableAccount = null;
        for (int a = UserConfig.MAX_ACCOUNT_COUNT - 1; a >= 0; a--) {
            if (!UserConfig.getInstance(a).isClientActivated()) {
                freeAccounts++;
                if (availableAccount == null) {
                    availableAccount = a;
                }
            }
        }
        if (!UserConfig.hasPremiumOnAccounts()) {
            freeAccounts -= (UserConfig.MAX_ACCOUNT_COUNT - UserConfig.MAX_ACCOUNT_DEFAULT_COUNT);
        }
        if (freeAccounts > 0 && availableAccount != null) {
            activity.presentFragment(new LoginActivity(availableAccount));
            activity.drawerLayoutContainer.closeDrawer(false);
        } else if (!UserConfig.hasPremiumOnAccounts() && activity.actionBarLayout.getFragmentStack().size() > 0) {
            BaseFragment fragment = activity.actionBarLayout.getFragmentStack().get(0);
            LimitReachedBottomSheet limitReachedBottomSheet = new LimitReachedBottomSheet(fragment, activity, LimitReachedBottomSheet.TYPE_ACCOUNTS, activity.currentAccount, null);
            fragment.showDialog(limitReachedBottomSheet);
            limitReachedBottomSheet.onShowPremiumScreenRunnable = () -> activity.drawerLayoutContainer.closeDrawer(false);
        }
    }

    private void openAttachMenuBotFromDrawer(TLRPC.TL_attachMenuBot attachMenuBot) {
        if (attachMenuBot.inactive || attachMenuBot.side_menu_disclaimer_needed) {
            WebAppDisclaimerAlert.show(activity, allowSendMessage -> {
                TLRPC.TL_messages_toggleBotInAttachMenu botRequest = new TLRPC.TL_messages_toggleBotInAttachMenu();
                botRequest.bot = MessagesController.getInstance(activity.currentAccount).getInputUser(attachMenuBot.bot_id);
                botRequest.enabled = true;
                botRequest.write_allowed = true;
                ConnectionsManager.getInstance(activity.currentAccount).sendRequest(botRequest, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    attachMenuBot.inactive = false;
                    attachMenuBot.side_menu_disclaimer_needed = false;
                    LaunchActivity.showAttachMenuBot(activity, activity.currentAccount, attachMenuBot, null, true);
                    MediaDataController.getInstance(activity.currentAccount).updateAttachMenuBotsInCache();
                }), ConnectionsManager.RequestFlagInvokeAfter | ConnectionsManager.RequestFlagFailOnServerErrors);
            }, null, null);
        } else {
            LaunchActivity.showAttachMenuBot(activity, activity.currentAccount, attachMenuBot, null, true);
        }
    }

    private void handleDrawerItemClick(int id) {
        if (id == 2) {
            activity.presentFragment(new GroupCreateActivity(new Bundle()));
            activity.drawerLayoutContainer.closeDrawer(false);
        } else if (id == 4) {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            if (!BuildVars.DEBUG_VERSION && preferences.getBoolean("channel_intro", false)) {
                Bundle args = new Bundle();
                args.putInt("step", 0);
                activity.presentFragment(new ChannelCreateActivity(args));
            } else {
                activity.presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANNEL_CREATE));
                preferences.edit().putBoolean("channel_intro", true).apply();
            }
            activity.drawerLayoutContainer.closeDrawer(false);
        } else if (id == 6) {
            Bundle args = new Bundle();
            args.putBoolean("needFinishFragment", false);
            activity.presentFragment(new ContactsActivity(args));
            activity.drawerLayoutContainer.closeDrawer(false);
        } else if (id == 8) {
            activity.presentFragment(new SettingsActivity());
            activity.drawerLayoutContainer.closeDrawer(false);
        } else if (id == 10) {
            activity.presentFragment(new CallLogActivity());
            activity.drawerLayoutContainer.closeDrawer(false);
        } else if (id == 11) {
            if (MessagesController.getInstance(UserConfig.selectedAccount).savedViewAsChats) {
                Bundle args = new Bundle();
                args.putLong("dialog_id", UserConfig.getInstance(activity.currentAccount).getClientUserId());
                args.putInt("type", MediaActivity.TYPE_MEDIA);
                args.putInt("start_from", SharedMediaLayout.TAB_SAVED_DIALOGS);
                activity.presentFragment(new MediaActivity(args, null));
            } else {
                Bundle args = new Bundle();
                args.putLong("user_id", UserConfig.getInstance(activity.currentAccount).getClientUserId());
                activity.presentFragment(new ChatActivity(args));
            }
            activity.drawerLayoutContainer.closeDrawer(false);
        } else if (id == 15) {
            showCurrentUserStatusDialog();
        } else if (id == 16) {
            activity.drawerLayoutContainer.closeDrawer(true);
            Bundle args = new Bundle();
            args.putLong("user_id", UserConfig.getInstance(activity.currentAccount).getClientUserId());
            args.putBoolean("my_profile", true);
            activity.presentFragment(new ProfileActivity(args, null));
        } else if (id == DrawerLayoutAdapter.nkbtnBookmarks) {
            activity.presentFragment(new BookmarkManagerActivity());
            activity.drawerLayoutContainer.closeDrawer(false);
        } else if (id == DrawerLayoutAdapter.nkbtnFeed) {
            activity.presentFragment(new com.exteragram.messenger.feed.ui.FeedActivity());
            activity.drawerLayoutContainer.closeDrawer(false);
        } else if (id == DrawerLayoutAdapter.nkbtnRecentChats) {
            activity.presentFragment(new ChatHistoryActivity());
            activity.drawerLayoutContainer.closeDrawer(false);
        } else if (id == DrawerLayoutAdapter.nkbtnSettings) {
            activity.presentFragment(new NekoSettingsActivity());
            activity.drawerLayoutContainer.closeDrawer(false);
        } else if (id == DrawerLayoutAdapter.nkbtnBrowser) {
            BrowserUtils.openBrowserHome(() -> activity.drawerLayoutContainer.closeDrawer(true), true);
        } else if (id == DrawerLayoutAdapter.nkbtnQrLogin) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{Manifest.permission.CAMERA}, ActionIntroActivity.CAMERA_PERMISSION_REQUEST_CODE);
                return;
            }
            CameraScanActivity.showAsSheet(activity, false, CameraScanActivity.TYPE_QR_LOGIN, new CameraScanActivity.CameraScanActivityDelegate() {
                @Override
                public boolean processQr(String link, Runnable onLoadEnd) {
                    AndroidUtilities.runOnUIThread(() -> {
                        try {
                            String code = link.substring("tg://login?token=".length());
                            code = code.replaceAll("/", "_");
                            code = code.replaceAll("\\+", "-");
                            byte[] token = Base64.decode(code, Base64.URL_SAFE);
                            TLRPC.TL_auth_acceptLoginToken req = new TLRPC.TL_auth_acceptLoginToken();
                            req.token = token;
                            ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(onLoadEnd::run));
                        } catch (Exception e) {
                            FileLog.e("Failed to pass qr code auth", e);
                            if (!activity.actionBarLayout.getFragmentStack().isEmpty()) {
                                BaseFragment fragment = activity.actionBarLayout.getFragmentStack().get(0);
                                AndroidUtilities.runOnUIThread(() -> AlertsCreator.showSimpleAlert(fragment, activity.getString(R.string.AuthAnotherClient), activity.getString(R.string.ErrorOccurred)));
                            }
                            onLoadEnd.run();
                        }
                    }, 750);
                    return true;
                }
            });
            activity.drawerLayoutContainer.closeDrawer(false);
        } else if (id == DrawerLayoutAdapter.nkbtnArchivedChats) {
            Bundle args = new Bundle();
            args.putInt("folderId", 1);
            activity.presentFragment(new DialogsActivity(args));
            activity.drawerLayoutContainer.closeDrawer(false);
        } else if (id == DrawerLayoutAdapter.nkbtnGhostMode) {
            String message = NekoConfig.isGhostModeActive()
                    ? LocaleController.getString(R.string.GhostModeDisabled)
                    : LocaleController.getString(R.string.GhostModeEnabled);
            NekoConfig.toggleGhostMode();
            BaseFragment lastFragment = activity.getLastFragment();
            if (lastFragment != null) {
                BulletinFactory.of(lastFragment).createSuccessBulletin(message).show();
            }
            NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.mainUserInfoChanged);
            if (drawerLayoutAdapter != null) {
                drawerLayoutAdapter.notifyDataSetChanged();
            }
            activity.drawerLayoutContainer.closeDrawer(false);
        } else if (id == DrawerLayoutAdapter.nkbtnSessions) {
            activity.presentFragment(new SessionsActivity(SessionsActivity.TYPE_DEVICES));
            activity.drawerLayoutContainer.closeDrawer(false);
        } else if (id == DrawerLayoutAdapter.nkbtnRestartApp) {
            AppRestartHelper.triggerRebirth(ApplicationLoader.applicationContext, new Intent(ApplicationLoader.applicationContext, LaunchActivity.class));
        }
    }

    private void openSettings(boolean expanded) {
        Bundle args = new Bundle();
        args.putLong("user_id", UserConfig.getInstance(activity.currentAccount).clientUserId);
        if (expanded) {
            args.putBoolean("expandPhoto", true);
        }
        activity.presentFragment(new ProfileActivity(args));
        activity.drawerLayoutContainer.closeDrawer(false);
    }
}
