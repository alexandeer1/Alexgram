"""
extera_utils.classes — stub for the exteraGram class-proxy DSL (DexMaker-backed Java subclassing).

Runtime Java class generation (DexMaker/MVEL) is NOT implemented in ZaStoGram yet. These names
exist so plugins importing the DSL still LOAD and define their classes; actually instantiating a
generated proxy (new_instance / new_java_instance / java_class / MVEL bodies) raises a clear
NotImplementedError instead of a confusing ImportError.
"""

_MSG = "extera_utils.classes (DexMaker class-proxy) is not yet implemented in ZaStoGram"


class _Unsupported:
    def __init__(self, *args, **kwargs):
        pass

    def __call__(self, *args, **kwargs):
        raise NotImplementedError(_MSG)

    def __getattr__(self, name):
        raise NotImplementedError(_MSG)


class Base:
    """Base for managed Java subclasses. Definition works; instantiation is unsupported (stub)."""

    @classmethod
    def bind(cls, *args, **kwargs):
        return cls

    @classmethod
    def java_class(cls):
        raise NotImplementedError(_MSG)

    @classmethod
    def new_instance(cls, *args, **kwargs):
        raise NotImplementedError(_MSG)

    @classmethod
    def new_java_instance(cls, *args, **kwargs):
        raise NotImplementedError(_MSG)

    @classmethod
    def from_java(cls, obj):
        raise NotImplementedError(_MSG)

    @property
    def java(self):
        raise NotImplementedError(_MSG)

    @property
    def this(self):
        raise NotImplementedError(_MSG)


def java_subclass(*classes, **kwargs):
    """No-op binder: keeps the Python class usable for definition; instantiation raises via Base."""
    def deco(cls):
        return cls
    return deco


def _passthrough(*args, **kwargs):
    # Usable as @x, @x(), or @x("name", [...]) — returns the decorated function unchanged.
    if len(args) == 1 and callable(args[0]) and not kwargs:
        return args[0]

    def deco(fn):
        return fn
    return deco


joverride = _passthrough
joverload = _passthrough
jmethod = _passthrough
jconstructor = _passthrough
jpreconstructor = _passthrough
jclassbuilder = _passthrough


def jfield(jtype="java.lang.Object", default=None, methods=None):
    """Acts as a plain Python attribute holding the default (no real Java field is generated)."""
    return default


def jgetmethod(*args, **kwargs):
    return None


def jsetmethod(*args, **kwargs):
    return None


def jMVELmethod(*args, **kwargs):
    return _Unsupported()


def jMVELoverride(*args, **kwargs):
    return _Unsupported()


# lowercase aliases (per docs)
jmvelmethod = jMVELmethod
jmveloverride = jMVELoverride


class PyObj:
    """Carries an arbitrary Python object through Java-facing APIs (stub: identity)."""

    @staticmethod
    def create(value):
        return value
