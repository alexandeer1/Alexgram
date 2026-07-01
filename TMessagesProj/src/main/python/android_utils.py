"""android_utils — UI-thread helpers, listener wrappers and logging for plugins."""

from java import dynamic_proxy
from java.lang import Runnable
from android.view import View

from org.telegram.messenger import AndroidUtilities, FileLog, LocaleController


def log(msg):
    """Write a line to the app log (visible in logcat / FileLog)."""
    try:
        if isinstance(msg, BaseException):
            import traceback
            msg = "".join(traceback.format_exception(type(msg), msg, msg.__traceback__))
        FileLog.d("[plugin] " + str(msg))
    except Exception:
        pass


def dp(value):
    """Density-independent pixels -> px (AndroidUtilities.dp)."""
    return AndroidUtilities.dp(float(value))


def get_string(key, res=0):
    """Localized UI string by key (LocaleController.getString); optional fallback resource id."""
    if res:
        return LocaleController.getString(str(key), int(res))
    return LocaleController.getString(str(key))


def copy_to_clipboard(text):
    """Copy text to the system clipboard. Returns True on success."""
    try:
        return bool(AndroidUtilities.addToClipboard(str(text)))
    except Exception as e:
        log(e)
        return False


class _Runnable(dynamic_proxy(Runnable)):
    def __init__(self, fn):
        super().__init__()
        self.fn = fn

    def run(self):
        try:
            self.fn()
        except Exception as e:
            log(e)


def run_on_ui_thread(fn, delay=0):
    """Run fn on the Android main thread, optionally after ``delay`` ms."""
    if fn is None:
        return
    if delay and delay > 0:
        AndroidUtilities.runOnUIThread(_Runnable(fn), int(delay))
    else:
        AndroidUtilities.runOnUIThread(_Runnable(fn))


class OnClickListener(dynamic_proxy(View.OnClickListener)):
    """Wrap a python callable as a View.OnClickListener: OnClickListener(lambda v: ...)."""

    def __init__(self, fn):
        super().__init__()
        self.fn = fn

    def onClick(self, v):
        try:
            if self.fn is not None:
                self.fn(v)
        except Exception as e:
            log(e)


class OnLongClickListener(dynamic_proxy(View.OnLongClickListener)):
    """Wrap a python callable as a View.OnLongClickListener. Returns True unless fn returns False."""

    def __init__(self, fn):
        super().__init__()
        self.fn = fn

    def onLongClick(self, v):
        try:
            if self.fn is not None:
                result = self.fn(v)
                return False if result is False else True
        except Exception as e:
            log(e)
        return False
