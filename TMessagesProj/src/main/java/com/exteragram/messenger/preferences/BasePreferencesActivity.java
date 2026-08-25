package com.exteragram.messenger.preferences;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;

import org.telegram.messenger.AndroidUtilities;

import androidx.core.util.Consumer;

import java.util.ArrayList;

import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.messenger.R;

public abstract class BasePreferencesActivity extends BaseFragment {

    protected LinearLayoutManager layoutManager;
    protected UniversalRecyclerView listView;

    public abstract void fillItems(ArrayList<UItem> arrayList, UniversalAdapter universalAdapter);

    public abstract String getTitle();

    public boolean hasHeaderCell() {
        return false;
    }

    public boolean hasWhiteActionBar() {
        return false;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    public boolean needHideTitle() {
        return false;
    }

    public abstract void onClick(UItem uItem, View view, int i, float f, float f2);

    public boolean onLongClick(UItem uItem, View view, int i, float f, float f2) {
        return false;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(getTitle());
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        if (actionBar.menu == null) {
            actionBar.createMenu();
        }

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        listView.setSections();
        if (!hasHeaderCell()) {
            actionBar.setAdaptiveBackground(listView, needHideTitle());
        }
        listView.adapter.setApplyBackground(false);
        listView.setClipToPadding(false);

        layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        listView.setLayoutManager(layoutManager);

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = frameLayout;
        return frameLayout;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false);
        }
        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public int getTopOffset(int tag) {
                return hasHeaderCell() ? AndroidUtilities.statusBarHeight : 0;
            }

            @Override
            public int getBottomOffset(int tag) {
                return getBottomInset();
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        Bulletin.removeDelegate(this);
    }

    public void toggleBooleanSettingAndRefresh(UItem uItem, Consumer<Boolean> consumer) {
        boolean checked = !uItem.checked;
        if (consumer != null) {
            consumer.accept(checked);
        }
        uItem.setChecked(checked);
        View view = listView.findViewByItemId(uItem.id);
        if (view instanceof CheckBoxCell) {
            ((CheckBoxCell) view).setChecked(checked, true);
        } else if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(checked);
        }
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        if (listView != null) {
            listView.setPadding(0, 0, 0, bottom);
            listView.setClipToPadding(false);
        }
    }
}
