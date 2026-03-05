package tw.nekomimi.nekogram.ui;

import org.telegram.ui.DialogsActivity;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import android.os.Bundle;

public class HiddenChatsActivity extends DialogsActivity {

    public HiddenChatsActivity(Bundle args) {
        super(args);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        if (actionBar != null) {
            actionBar.setTitle("Hidden Chats");
        }
    }

    @Override
    public java.util.ArrayList<org.telegram.tgnet.TLRPC.Dialog> getDialogsArray(int currentAccount, int dialogsType, int folderId, boolean frozen) {
        if (frozen) {
            return super.getDialogsArray(currentAccount, dialogsType, folderId, frozen);
        }
        java.util.ArrayList<org.telegram.tgnet.TLRPC.Dialog> all = getMessagesController().getAllDialogs();
        java.util.ArrayList<org.telegram.tgnet.TLRPC.Dialog> hidden = new java.util.ArrayList<>();
        tw.nekomimi.nekogram.helpers.HiddenChatsController controller = tw.nekomimi.nekogram.helpers.HiddenChatsController.getInstance();
        for (int i = 0; i < all.size(); i++) {
             org.telegram.tgnet.TLRPC.Dialog d = all.get(i);
             if (controller.isHidden(d.id)) {
                 hidden.add(d);
             }
        }
        return hidden;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        tw.nekomimi.nekogram.helpers.HiddenChatsController.getInstance().lock();
    }
}
