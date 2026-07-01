"""extera_utils — compatibility helpers (class-proxy DSL lives in extera_utils.classes)."""

from java import dynamic_proxy  # re-exported for convenience


def get_resource_id(name, kind="drawable"):
    """Resolve an app resource id by name (e.g. get_resource_id('msg_link'))."""
    from org.telegram.messenger import ApplicationLoader
    try:
        ctx = ApplicationLoader.applicationContext
        return ctx.getResources().getIdentifier(name, kind, ctx.getPackageName())
    except Exception:
        return 0
