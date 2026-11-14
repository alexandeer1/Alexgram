package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Cells.TextSettingsCell;

import tw.nekomimi.nekogram.DatacenterActivity;

public class NekoAboutActivity extends BaseNekoSettingsActivity {

    private int xChannelRow;
    private int desktopChannelRow;
    private int betaChannelRow;
    private int nightlyChannelRow;
    private int channelTipsRow;
    private int sourceCodeRow;
    private int datacenterStatusRow;

    @Override
    protected void updateRows() {
        super.updateRows();

        xChannelRow = addRow();
        desktopChannelRow = addRow();
        betaChannelRow = addRow();
        nightlyChannelRow = addRow();
        channelTipsRow = addRow();
        sourceCodeRow = addRow();
        datacenterStatusRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.About);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == xChannelRow) {
            MessagesController.getInstance(currentAccount).openByUserName("AlexgramApp", NekoAboutActivity.this, 1);
        } else if (position == desktopChannelRow) {
            MessagesController.getInstance(currentAccount).openByUserName("AlexgramDesktop", NekoAboutActivity.this, 1);
        } else if (position == betaChannelRow) {
            MessagesController.getInstance(currentAccount).openByUserName("Alexgram_beta", NekoAboutActivity.this, 1);
        } else if (position == nightlyChannelRow) {
            MessagesController.getInstance(currentAccount).openByUserName("Alexgram_Nightly", NekoAboutActivity.this, 1);
        } else if (position == channelTipsRow) {
            MessagesController.getInstance(currentAccount).openByUserName("Alexgramtips", NekoAboutActivity.this, 1);
        } else if (position == sourceCodeRow) {
            Browser.openUrl(getParentActivity(), "https://github.com/alexandeer1/Alexgram");
        } else if (position == datacenterStatusRow) {
            presentFragment(new DatacenterActivity(0));
        }
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            super.onBindViewHolder(holder, position, partial);
            if (holder.getItemViewType() == TYPE_SETTINGS) {
                TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                if (position == xChannelRow) {
                    textCell.setTextAndValue("Alexgram Channel", "@AlexgramApp", true);
                } else if (position == desktopChannelRow) {
                    textCell.setTextAndValue("Alexgram Desktop", "@AlexgramDesktop", true);
                } else if (position == betaChannelRow) {
                    textCell.setTextAndValue("Alexgram Beta", "@Alexgram_beta", true);
                } else if (position == nightlyChannelRow) {
                    textCell.setTextAndValue("Alexgram Nightly", "@Alexgram_Nightly", true);
                } else if (position == channelTipsRow) {
                    textCell.setTextAndValue("Features Tips Channel", "@Alexgramtips", true);
                } else if (position == sourceCodeRow) {
                    textCell.setTextAndValue(getString(R.string.SourceCode), "Github", true);
                } else if (position == datacenterStatusRow) {
                    textCell.setText(getString(R.string.DatacenterStatus), false);
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            return TYPE_SETTINGS;
        }
    }
}
