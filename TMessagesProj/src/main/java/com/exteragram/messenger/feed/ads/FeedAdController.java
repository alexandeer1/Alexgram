package com.exteragram.messenger.feed.ads;

import android.os.SystemClock;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

public final class FeedAdController {
    private static final FeedAdController[] instances = new FeedAdController[16];
    private static final Object[] locks = new Object[16];

    static {
        for (int i = 0; i < 16; i++) {
            locks[i] = new Object();
        }
    }

    public final int currentAccount;
    private long lastLoadTime;
    private boolean loading;
    private int rotationIndex;
    private final ArrayList<FeedAd> allAds = new ArrayList<>();
    private ArrayList<FeedAd> eligibleAds = new ArrayList<>();
    private final ArrayList<FeedAd> rotation = new ArrayList<>();
    private final ArrayList<Runnable> pendingLoadCallbacks = new ArrayList<>();

    public static FeedAdController getInstance(int account) {
        if (account < 0 || account >= 16) {
            account = UserConfig.selectedAccount;
            if (account < 0 || account >= 16) account = 0;
        }
        FeedAdController controller = instances[account];
        if (controller != null) {
            return controller;
        }
        synchronized (locks[account]) {
            controller = instances[account];
            if (controller == null) {
                controller = new FeedAdController(account);
                instances[account] = controller;
            }
        }
        return controller;
    }

    private FeedAdController(int account) {
        this.currentAccount = account;
    }

    public boolean isEnabled() {
        return false; // ads disabled
    }

    public int getFirstAfter() {
        return 30;
    }

    public int getMinTrailing() {
        return 2;
    }

    public int getBaseEvery() {
        return 30;
    }

    public int getEffectiveEvery() {
        int baseEvery = getBaseEvery();
        int size = eligibleAds.size();
        if (size <= 1) {
            return baseEvery * 3;
        } else if (size == 2) {
            return baseEvery * 2;
        }
        return baseEvery;
    }

    public FeedAd nextAd() {
        if (eligibleAds.isEmpty()) {
            return null;
        }
        if (rotation.isEmpty() || rotationIndex >= rotation.size()) {
            reshuffleRotation();
        }
        if (rotation.isEmpty()) {
            return null;
        }
        return rotation.get(rotationIndex++);
    }

    private void reshuffleRotation() {
        rotation.clear();
        for (FeedAd feedAd : eligibleAds) {
            for (int i = 0; i < Math.max(1, feedAd.weight); i++) {
                rotation.add(feedAd);
            }
        }
        Collections.shuffle(rotation);
        rotationIndex = 0;
    }

    public void ensureLoaded(Runnable runnable) {
        if (!loading && lastLoadTime != 0 && SystemClock.elapsedRealtime() - lastLoadTime < 1800000) {
            recomputeEligible();
            if (runnable != null) {
                runnable.run();
            }
            return;
        }
        if (runnable != null) {
            pendingLoadCallbacks.add(runnable);
        }
        if (loading) {
            return;
        }
        loading = true;
        fetchHistory((messages, error) -> {
            loading = false;
            lastLoadTime = SystemClock.elapsedRealtime();
            if (error == null && messages != null) {
                ArrayList<FeedAd> parsed = FeedAdParser.parse(messages);
                allAds.clear();
                allAds.addAll(parsed);
                recomputeEligible();
            }
            ArrayList<Runnable> callbacks = new ArrayList<>(pendingLoadCallbacks);
            pendingLoadCallbacks.clear();
            for (Runnable cb : callbacks) {
                cb.run();
            }
        });
    }

    public void recomputeEligible() {
        ArrayList<FeedAd> list = new ArrayList<>(allAds.size());
        for (FeedAd ad : allAds) {
            if (isEligible(ad)) {
                list.add(ad);
            }
        }
        boolean matches = list.size() == eligibleAds.size();
        for (int i = 0; matches && i < list.size(); i++) {
            matches = TextUtils.equals(list.get(i).id, eligibleAds.get(i).id) && list.get(i).weight == eligibleAds.get(i).weight;
        }
        eligibleAds = list;
        if (!matches) {
            rotation.clear();
            rotationIndex = 0;
            return;
        }
        if (rotation.isEmpty()) {
            return;
        }
        HashMap<String, FeedAd> map = new HashMap<>();
        for (FeedAd ad : list) {
            map.put(ad.id, ad);
        }
        for (int i = 0; i < rotation.size(); i++) {
            FeedAd ad = map.get(rotation.get(i).id);
            if (ad != null) {
                rotation.set(i, ad);
            }
        }
    }

    private boolean isEligible(FeedAd feedAd) {
        if (!matchesLocale(feedAd.locales)) {
            return false;
        }
        boolean isPremium = UserConfig.getInstance(currentAccount).isPremium();
        int prem = feedAd.premium;
        if ((prem == 1 && !isPremium) || (prem == 2 && isPremium)) {
            return false;
        }
        return true;
    }

    private boolean matchesLocale(Set<String> set) {
        LocaleController.LocaleInfo info;
        return set == null || set.isEmpty() || (info = LocaleController.getInstance().getCurrentLocaleInfo()) == null || contains(set, info.getLangCode()) || contains(set, info.shortName) || contains(set, info.baseLangCode);
    }

    private static boolean contains(Set<String> set, String str) {
        return str != null && set.contains(str.toLowerCase());
    }

    private void fetchHistory(Utilities.Callback2<TLRPC.messages_Messages, TLRPC.TL_error> callback) {
        AccountInstance accountInstance = AccountInstance.getInstance(currentAccount);
        TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.peer = accountInstance.getMessagesController().getInputPeer(-3514621311L);
        req.offset_id = 0;
        req.limit = 75;
        if (req.peer != null && req.peer.access_hash != 0) {
            accountInstance.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error != null || !(response instanceof TLRPC.messages_Messages)) {
                    callback.run(null, error);
                } else {
                    callback.run((TLRPC.messages_Messages) response, null);
                }
            }));
        } else {
            callback.run(null, null);
        }
    }
}
