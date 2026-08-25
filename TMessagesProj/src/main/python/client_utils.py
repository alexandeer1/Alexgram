"""client_utils — fragments, accounts, controllers, queues and message sending for plugins."""

from java import dynamic_proxy
from java.lang import Runnable

from org.telegram.messenger import AccountInstance, SendMessagesHelper
from org.telegram.plugins import PluginUtils
from org.telegram.tgnet import RequestDelegate


def get_last_fragment():
    """The top-most visible BaseFragment, or None."""
    return PluginUtils.getLastFragment()


def get_context():
    """Best-effort Context: visible activity if any, else the application context."""
    return PluginUtils.getContext()


def get_current_account():
    return PluginUtils.getCurrentAccount()


# ---------------------------------------------------------------- controllers
# Everything below mirrors exteraGram's client_utils: a thin facade over
# AccountInstance.getInstance(account).getXxx(). `account` defaults to the
# currently-selected account.

def get_account_instance(account=None):
    """The AccountInstance hub for the given (or current) account."""
    acc = get_current_account() if account is None else int(account)
    return AccountInstance.getInstance(acc)


def get_messages_controller(account=None):
    return get_account_instance(account).getMessagesController()


def get_messages_storage(account=None):
    return get_account_instance(account).getMessagesStorage()


def get_connections_manager(account=None):
    return get_account_instance(account).getConnectionsManager()


def get_send_messages_helper(account=None):
    return get_account_instance(account).getSendMessagesHelper()


def get_media_data_controller(account=None):
    return get_account_instance(account).getMediaDataController()


def get_contacts_controller(account=None):
    return get_account_instance(account).getContactsController()


def get_notifications_controller(account=None):
    return get_account_instance(account).getNotificationsController()


def get_notification_center(account=None):
    return get_account_instance(account).getNotificationCenter()


def get_location_controller(account=None):
    return get_account_instance(account).getLocationController()


def get_download_controller(account=None):
    return get_account_instance(account).getDownloadController()


def get_secret_chat_helper(account=None):
    return get_account_instance(account).getSecretChatHelper()


def get_file_loader(account=None):
    return get_account_instance(account).getFileLoader()


def get_file_ref_controller(account=None):
    return get_account_instance(account).getFileRefController()


def get_stats_controller(account=None):
    return get_account_instance(account).getStatsController()


def get_user_id(account=None):
    """The logged-in user's id (clientUserId) for the given/current account."""
    return get_account_instance(account).getUserConfig().getClientUserId()


class _UserConfigProxy:
    """
    Forwards everything to the real UserConfig but also answers getCurrentAccount(),
    which vanilla UserConfig does not expose (plugins rely on it).
    """

    def __init__(self, uc, account):
        self.__dict__["_uc"] = uc
        self.__dict__["_account"] = account

    def getCurrentAccount(self):
        return self.__dict__["_account"]

    def get_current_account(self):
        return self.__dict__["_account"]

    def __getattr__(self, name):
        return getattr(self.__dict__["_uc"], name)

    def __setattr__(self, name, value):
        # Forward writes to the real UserConfig too (reads already forward via __getattr__).
        setattr(self.__dict__["_uc"], name, value)


def get_user_config():
    uc = PluginUtils.getUserConfig()
    return _UserConfigProxy(uc, PluginUtils.getCurrentAccount())


# ---------------------------------------------------------------- threading

class _Runnable(dynamic_proxy(Runnable)):
    def __init__(self, fn):
        super().__init__()
        self.fn = fn

    def run(self):
        try:
            self.fn()
        except Exception as e:
            from android_utils import log
            log(e)


def run_on_queue(fn):
    """Run fn on a background worker queue (off the UI thread)."""
    if fn is not None:
        PluginUtils.runOnQueue(_Runnable(fn))


def run_on_ui_thread(fn, delay=0):
    """Run fn on the Android main thread, optionally after `delay` milliseconds."""
    if fn is not None:
        PluginUtils.runOnUiThread(_Runnable(fn), int(delay))


# ---------------------------------------------------------------- sending

def send_document(dialog_id, file_path, caption="", replyToMsg=None, replyToTopMsg=None, replyQuote=None):
    """Send a local file as a document to a dialog. Reply/quote args are optional."""
    PluginUtils.sendDocument(
        int(dialog_id), str(file_path), caption if caption is not None else "",
        replyToMsg, replyToTopMsg, replyQuote)


# dict-key -> public SendMessageParams field name (only where they differ)
_SEND_ALIASES = {
    "reply_to_msg": "replyToMsg",
    "reply_to_top_msg": "replyToTopMsg",
    "reply_markup": "replyMarkup",
    "schedule_date": "scheduleDate",
    "schedule_repeat_period": "scheduleRepeatPeriod",
    "web_page": "webPage",
    "reply_quote": "replyQuote",
    "update_stickers_order": "updateStickersOrder",
    "has_media_spoilers": "hasMediaSpoilers",
}

# dict keys consumed for routing (peer/text/account) — never set as fields
_SEND_SKIP_KEYS = {
    "peer", "dialog_id", "dialogId", "chat_id", "chatId",
    "user_id", "userId", "to", "message", "text", "account",
}


def _set_param_field(obj, name, value):
    """Best-effort public-field set on a Java object; unknown/incompatible fields are ignored."""
    try:
        setattr(obj, name, value)
    except Exception:
        from android_utils import log
        log("send_message: cannot set field '%s'" % name)


def send_message(params, account=None):
    """
    Send a message (exteraGram-compatible). `params` is a dict; common keys:
      peer / dialog_id  : target dialog id (int, REQUIRED)
      message / text    : message text
      reply_to_msg      : MessageObject to reply to
      entities          : ArrayList<TLRPC.MessageEntity>
      reply_markup      : TLRPC.ReplyMarkup
      silent            : bool  -> notify = not silent
      no_webpage        : bool  -> searchLinks = not no_webpage
      schedule_date     : int
    Any other key matching a public SendMessageParams field is applied verbatim.
    Returns the dispatched SendMessageParams.
    """
    if not isinstance(params, dict):
        raise TypeError("send_message expects a dict of parameters")

    peer = None
    for key in ("peer", "dialog_id", "dialogId", "chat_id", "chatId", "user_id", "userId", "to"):
        if params.get(key) is not None:
            peer = params.get(key)
            break
    if peer is None:
        raise ValueError("send_message: missing target ('peer'/'dialog_id')")
    peer = int(peer)

    text = params.get("message", params.get("text"))
    spm = SendMessagesHelper.SendMessageParams.of("" if text is None else str(text), peer)

    for k, v in params.items():
        if k in _SEND_SKIP_KEYS:
            continue
        if k == "silent":
            _set_param_field(spm, "notify", not bool(v))
        elif k in ("no_webpage", "no_web_page", "noWebpage"):
            _set_param_field(spm, "searchLinks", not bool(v))
        else:
            _set_param_field(spm, _SEND_ALIASES.get(k, k), v)

    helper = get_send_messages_helper(account)
    run_on_ui_thread(lambda: helper.sendMessage(spm))
    return spm


class _RequestDelegate(dynamic_proxy(RequestDelegate)):
    def __init__(self, fn):
        super().__init__()
        self.fn = fn

    def run(self, response, error):
        try:
            self.fn(response, error)
        except Exception as e:
            from android_utils import log
            log(e)


def send_request(request, on_complete=None, flags=None, connection_type=None):
    """
    Send a raw TL request via ConnectionsManager. `on_complete(response, error)` is invoked
    on the network thread (wrap UI work in run_on_ui_thread). Returns the request token (int).
    """
    cm = get_connections_manager()
    delegate = _RequestDelegate(on_complete) if on_complete is not None else None
    if flags is None:
        return cm.sendRequest(request, delegate)
    if connection_type is None:
        return cm.sendRequest(request, delegate, int(flags))
    return cm.sendRequest(request, delegate, int(flags), int(connection_type))
