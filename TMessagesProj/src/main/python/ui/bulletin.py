"""ui.bulletin — BulletinHelper for Telegram-style top toasts."""

from org.telegram.ui.Components import BulletinFactory

from android_utils import run_on_ui_thread, log


def _show(kind, text):
    def go():
        try:
            factory = BulletinFactory.global_()  # 'global' is a Python keyword; Chaquopy escapes it as global_
            if factory is None:
                return
            if kind == "error":
                factory.createErrorBulletin(str(text)).show()
            else:
                factory.createSuccessBulletin(str(text)).show()
        except Exception as e:
            log(e)
    run_on_ui_thread(go)


class BulletinHelper:

    @staticmethod
    def show_error(text):
        _show("error", text)

    @staticmethod
    def show_success(text):
        _show("success", text)

    @staticmethod
    def show_info(text):
        _show("success", text)

    # exteraGram aliases / convenience
    @staticmethod
    def show(text):
        _show("success", text)
