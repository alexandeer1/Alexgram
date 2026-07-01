"""
ui.settings — declarative rows for a plugin's settings screen (exteraGram-compatible).

A plugin returns a list of these from create_settings(). Each item exposes to_model()
producing a flat dict the native renderer consumes. Interaction is routed back to
on_change(value) / on_click(view) by the loader. to_model() may return None to be skipped.
"""


class _Item:
    type = "item"
    key = None
    on_change = None
    on_click = None
    on_long_click = None
    create_sub_fragment = None
    link_alias = None

    def to_model(self, plugin, index):
        return {"type": self.type, "index": index}


class Header(_Item):
    type = "header"

    def __init__(self, text=""):
        self.text = text

    def to_model(self, plugin, index):
        return {"type": "header", "index": index, "text": str(self.text)}


class Divider(_Item):
    """A section separator; optional text becomes a footer/info line."""
    type = "divider"

    def __init__(self, text=""):
        self.text = text

    def to_model(self, plugin, index):
        return {"type": "divider", "index": index, "text": str(self.text or "")}


class Switch(_Item):
    type = "switch"

    def __init__(self, key, text, default=False, subtext="", icon=None,
                 on_change=None, on_long_click=None, link_alias=None):
        self.key = key
        self.text = text
        self.default = default
        self.subtext = subtext
        self.icon = icon
        self.on_change = on_change
        self.on_long_click = on_long_click
        self.link_alias = link_alias

    def to_model(self, plugin, index):
        value = plugin.get_setting(self.key, self.default) if plugin is not None else self.default
        return {"type": "switch", "index": index, "key": self.key,
                "text": str(self.text), "subtext": str(self.subtext or ""),
                "icon": self.icon, "value": bool(value)}


class Selector(_Item):
    """Single-choice row; persisted value is the selected option index (int)."""
    type = "selector"

    def __init__(self, key, text, items=None, default=0, subtext="", icon=None,
                 on_change=None, on_long_click=None, link_alias=None, options=None):
        self.key = key
        self.text = text
        # 'items' is the exteraGram field name; 'options' kept as a legacy alias.
        self.items = list(items if items is not None else (options or []))
        self.default = default
        self.subtext = subtext
        self.icon = icon
        self.on_change = on_change
        self.on_long_click = on_long_click
        self.link_alias = link_alias

    def to_model(self, plugin, index):
        from java.util import ArrayList
        value = plugin.get_setting(self.key, self.default) if plugin is not None else self.default
        opts = ArrayList()
        for o in self.items:
            opts.add(str(o))
        try:
            ivalue = int(value)
        except Exception:
            ivalue = 0
        return {"type": "selector", "index": index, "key": self.key,
                "text": str(self.text), "subtext": str(self.subtext or ""),
                "icon": self.icon, "options": opts, "value": ivalue}


class Input(_Item):
    type = "input"

    def __init__(self, key, text, default="", subtext="", icon=None,
                 on_change=None, on_long_click=None, link_alias=None):
        self.key = key
        self.text = text
        self.default = default
        self.subtext = subtext
        self.icon = icon
        self.on_change = on_change
        self.on_long_click = on_long_click
        self.link_alias = link_alias

    def to_model(self, plugin, index):
        value = plugin.get_setting(self.key, self.default) if plugin is not None else self.default
        return {"type": "input", "index": index, "key": self.key,
                "text": str(self.text), "subtext": str(self.subtext or ""),
                "icon": self.icon, "value": "" if value is None else str(value)}


class EditText(_Item):
    """Free-form text input (optionally multiline). Rendered with the input row for now."""
    type = "edittext"

    def __init__(self, key, hint="", default="", multiline=False, max_length=0, mask=None,
                 on_change=None, text=None, subtext="", icon=None):
        self.key = key
        self.hint = hint
        self.default = default
        self.multiline = multiline
        self.max_length = max_length
        self.mask = mask
        self.on_change = on_change
        self.text = text
        self.subtext = subtext
        self.icon = icon

    def to_model(self, plugin, index):
        value = plugin.get_setting(self.key, self.default) if plugin is not None else self.default
        title = self.text if self.text is not None else (self.hint or self.key)
        return {"type": "input", "index": index, "key": self.key,
                "text": str(title), "subtext": str(self.subtext or ""),
                "icon": self.icon, "value": "" if value is None else str(value),
                "multiline": bool(self.multiline)}


class Text(_Item):
    """A clickable text row. on_click(view) is called with the row's View."""
    type = "text"

    def __init__(self, text="", icon=None, subtext="", on_click=None, on_long_click=None,
                 accent=False, red=False, create_sub_fragment=None, link_alias=None):
        self.text = text
        self.icon = icon
        self.subtext = subtext
        self.on_click = on_click
        self.on_long_click = on_long_click
        self.accent = accent
        self.red = red
        self.create_sub_fragment = create_sub_fragment
        self.link_alias = link_alias

    def to_model(self, plugin, index):
        return {"type": "text", "index": index, "text": str(self.text),
                "subtext": str(self.subtext or ""), "icon": self.icon,
                "accent": bool(self.accent), "red": bool(self.red)}


class Custom(_Item):
    """
    Advanced custom row backed by a Java view/factory/UItem. Host-side rendering of arbitrary
    Java views is not implemented yet, so it is skipped gracefully (to_model returns None).
    """
    type = "custom"

    def __init__(self, item=None, view=None, factory=None, factory_args=None,
                 on_click=None, on_long_click=None, create_sub_fragment=None, link_alias=None):
        self.item = item
        self.view = view
        self.factory = factory
        self.factory_args = factory_args
        self.on_click = on_click
        self.on_long_click = on_long_click
        self.create_sub_fragment = create_sub_fragment
        self.link_alias = link_alias

    def to_model(self, plugin, index):
        return None  # not renderable by the host yet → skipped by the loader


class SimpleSettingFactory:
    """
    Stub for exteraGram's custom-view factory. Building Java CustomSetting.Factory from Python
    needs the class-proxy/DexMaker layer, which is not implemented; using it yields an inert
    Custom row rather than crashing the plugin.
    """

    def __init__(self, *args, **kwargs):
        self.instance = self
        self.java = None

    def __call__(self, *args, **kwargs):
        return Custom(link_alias=kwargs.get("link_alias"))
