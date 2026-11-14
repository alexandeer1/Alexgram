package com.exteragram.messenger.plugins.ui.preferences;

import android.content.Context;
import android.view.View;

import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UniversalFragment;

/**
 * Alexgram adaptation: NagramXF's plugin screens are built on its own preferences framework
 * (BasePreferencesActivity extends UniversalFragment). Alexgram already has a different
 * {@code com.exteragram.messenger.preferences.BasePreferencesActivity} used by its own screens,
 * so the plugin version lives in this plugin-scoped package instead.
 */
public abstract class BasePreferencesActivity extends UniversalFragment {

    /**
     * Screens with the simple glass header return true; the base keeps the classic action bar for
     * every page that has not opted in, so no page ever touches a null {@code simpleActionBar}.
     */
    @Override
    protected boolean useSimpleActionBar() {
        return false;
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        listView.adapter.setApplyBackground(false);
        if (useSimpleActionBar()) {
            // the glass header floats over the list: the sections decoration draws the cards
            setHasOwnBackground(true);
            listView.setSections();
            listView.setSectionsDrawBackground(true);
        } else {
            actionBar.setAllowOverlayTitle(false);
            actionBar.setAdaptiveBackground(listView);
            listView.setSections();
        }
        listView.setClipToPadding(false);
        return view;
    }

    /** The list the glass header samples; null while the page keeps the classic action bar. */
    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return useSimpleActionBar() ? listView : null;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return useSimpleActionBar();
    }
}
