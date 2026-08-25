"""hook_utils — reflection helpers for classes and private fields."""

from org.telegram.plugins import PluginUtils


def find_class(name):
    """Load a java.lang.Class by fully-qualified name using the app class loader, or None."""
    return PluginUtils.findClass(name)


def get_private_field(obj, name):
    """Read a (possibly private) field by name, walking up the superclass chain. None on miss."""
    return PluginUtils.getPrivateField(obj, name)


def set_private_field(obj, name, value):
    """Write a (possibly private) field by name. Returns True on success."""
    return PluginUtils.setPrivateField(obj, name, value)
