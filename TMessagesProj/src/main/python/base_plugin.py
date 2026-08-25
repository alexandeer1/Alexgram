"""
ZaStoGram plugin SDK — base classes (exteraGram-compatible).

A plugin is a single .plugin Python file with module-level metadata (__id__, __name__, ...)
and a subclass of BasePlugin. The host instantiates it, injects a Java PluginContext as
``self._context`` and drives its lifecycle.
"""


class HookStrategy:
    """Return strategy for high-level request/response/update/message hooks."""
    DEFAULT = 0        # let the original flow through unchanged
    MODIFY = 1         # deliver the modified object (HookResult.request/response/update/updates/params)
    CANCEL = 2         # cancel/stop the operation
    MODIFY_FINAL = 3   # modify AND stop further plugin processing of this event
    REPLACE = 1        # exteraGram alias for MODIFY


class HookResult:
    def __init__(self, strategy=HookStrategy.DEFAULT, response=None, request=None,
                 update=None, updates=None, params=None):
        self.strategy = strategy
        self.response = response
        self.request = request
        self.update = update
        self.updates = updates
        self.params = params


class AppEvent:
    """Lifecycle events delivered to BasePlugin.on_app_event."""
    START = "start"
    STOP = "stop"
    PAUSE = "pause"
    RESUME = "resume"


class MethodHook:
    """Xposed-style method hook. ``param`` is a Java MethodHookParam."""

    def before_hooked_method(self, param):
        pass

    def after_hooked_method(self, param):
        pass


class XposedHook(MethodHook):
    """Convenience hook from plain callables: XposedHook(before=fn, after=fn)."""

    def __init__(self, before=None, after=None):
        self._before = before
        self._after = after

    def before_hooked_method(self, param):
        if self._before is not None:
            self._before(param)

    def after_hooked_method(self, param):
        if self._after is not None:
            self._after(param)


class MethodReplacement(MethodHook):
    """
    Xposed-style full method replacement. The return value of replace_hooked_method(param) becomes
    the hooked method's result and the original implementation does NOT run. Pass an instance to
    self.hook_method(member, MethodReplacement(...)) just like a MethodHook.
    """
    _is_replacement = True

    def replace_hooked_method(self, param):
        return None


class MenuItemType:
    """Which app menu an added item appears in (see BasePlugin.add_menu_item)."""
    MESSAGE_CONTEXT_MENU = "message_context_menu"  # long-press on a message
    DRAWER_MENU = "drawer_menu"                     # main navigation drawer
    CHAT_ACTION_MENU = "chat_action_menu"           # 3-dot menu inside a chat
    PROFILE_ACTION_MENU = "profile_action_menu"     # 3-dot menu on a profile


class MenuItemData:
    """
    A menu item added via BasePlugin.add_menu_item(). on_click(context) receives a dict with
    context-specific data (e.g. message / dialog_id / user / fragment for the relevant menu).
    """

    def __init__(self, menu_type, text, on_click, item_id=None, icon=None, subtext=None,
                 condition=None, priority=0):
        self.menu_type = menu_type
        self.text = text
        self.on_click = on_click
        self.item_id = item_id
        self.icon = icon
        self.subtext = subtext
        self.condition = condition   # MVEL visibility expression (not yet evaluated by host)
        self.priority = priority


class BasePlugin:

    def __init__(self):
        self._context = None      # Java org.telegram.plugins.PluginContext
        self.id = None
        self.name = None
        self._request_hooks = []  # (name, match_substring) filters from add_hook()
        self._menu_items = []     # MenuItemData registered via add_menu_item()
        self._send_message_hook = False  # set by add_on_send_message_hook()

    # ------------------------------------------------------------------ lifecycle

    def on_plugin_load(self):
        pass

    def on_plugin_unload(self):
        pass

    def on_app_event(self, event_type):
        """Called on AppEvent.START/STOP/PAUSE/RESUME."""
        pass

    def create_settings(self):
        return []

    # ------------------------------------------------------------------ high-level hooks

    def pre_request_hook(self, request_name, account, request):
        """Before an outgoing request is sent. Return HookResult (CANCEL / MODIFY w/ .request)."""
        return HookResult(strategy=HookStrategy.DEFAULT)

    def post_request_hook(self, request_name, account, response, error):
        """Just before a response is delivered. Return HookResult (MODIFY w/ .response / CANCEL)."""
        return HookResult(strategy=HookStrategy.DEFAULT)

    def on_update_hook(self, update_name, account, update):
        """A single incoming update. Return HookResult (MODIFY w/ .update)."""
        return HookResult(strategy=HookStrategy.DEFAULT)

    def on_updates_hook(self, container_name, account, updates):
        """An updates container. Return HookResult (MODIFY w/ .updates)."""
        return HookResult(strategy=HookStrategy.DEFAULT)

    def on_send_message_hook(self, account, params):
        """Outgoing message params just before send. Return HookResult (MODIFY w/ .params / CANCEL)."""
        return HookResult(strategy=HookStrategy.DEFAULT)

    # ------------------------------------------------------------------ method hooking

    def hook_method(self, method, hook):
        return self._context.hookMethod(method, hook)

    def hook_all_constructors(self, clazz, hook):
        return self._context.hookAllConstructors(clazz, hook)

    def hook_all_methods(self, clazz, method_name=None, hook=None):
        """
        Hook all overloads of method_name on clazz (Xposed-style hookAllMethods). clazz may be a
        java.lang.Class or a fully-qualified class-name string. The shorter form
        hook_all_methods(clazz, hook) hooks every declared method. Returns a list of Unhooks.
        """
        if hook is None and method_name is not None and not isinstance(method_name, str):
            hook = method_name      # called as hook_all_methods(clazz, hook)
            method_name = None
        return self._context.hookAllMethods(clazz, method_name, hook)

    def unhook_method(self, unhook):
        if unhook is not None:
            self._context.unhook(unhook)

    def add_hook(self, request_name, match_substring=False, priority=0):
        """Register interest in a (TL) request name so pre/post_request_hook fires for it."""
        self._request_hooks.append((str(request_name), bool(match_substring)))
        return (request_name, match_substring)

    def add_on_send_message_hook(self, priority=0):
        """Enable on_send_message_hook for this plugin."""
        self._send_message_hook = True

    def _matches_request(self, request_name):
        if not self._request_hooks:
            return None  # no filters → caller decides
        for name, substring in self._request_hooks:
            if substring:
                if name in str(request_name):
                    return True
            elif name == str(request_name):
                return True
        return False

    # ------------------------------------------------------------------ menu items

    def add_menu_item(self, item):
        """Register a MenuItemData; it appears in the menu named by item.menu_type."""
        self._menu_items.append(item)
        return item.item_id if getattr(item, "item_id", None) is not None else item

    def remove_menu_item(self, item_id):
        """Remove a previously added menu item by its item_id (or the object returned by add)."""
        self._menu_items = [m for m in self._menu_items
                            if m is not item_id and getattr(m, "item_id", None) != item_id]

    # ------------------------------------------------------------------ settings storage

    def get_setting(self, key, default=None):
        value = self._context.getSetting(key, default)
        if value is None:
            return default
        # getSetting is declared to return Object, so Chaquopy hands back a Java-typed proxy
        # (e.g. java.lang.Boolean) that does NOT unbox via bool()/int(). Coerce to the native
        # Python type implied by `default`. NB: check bool before int — bool subclasses int.
        if isinstance(default, bool):
            return str(value).strip().lower() == "true"
        if isinstance(default, int):
            try:
                return int(value)
            except Exception:
                return default
        if isinstance(default, float):
            try:
                return float(value)
            except Exception:
                return default
        return str(value)

    def set_setting(self, key, value, reload_settings=False):
        self._context.setSetting(key, value)
        if reload_settings:
            try:
                self._context.reloadSettings()
            except Exception:
                pass

    def export_settings(self):
        """Return a dict of all persisted settings for this plugin."""
        out = {}
        try:
            m = self._context.getAllSettings()
            if m is not None:
                for k in m.keySet():
                    out[str(k)] = m.get(k)
        except Exception:
            pass
        return out

    def import_settings(self, settings, reload_settings=True):
        if not settings:
            return
        try:
            for k, v in settings.items():
                self._context.setSetting(str(k), v)
        except Exception:
            pass
        if reload_settings:
            try:
                self._context.reloadSettings()
            except Exception:
                pass

    # ------------------------------------------------------------------ misc

    def getName(self):
        return self.name or self.id

    def log(self, msg):
        try:
            self._context.log(str(msg))
        except Exception:
            pass
