# Plugin SDK Python source

This is the source of truth for the Python plugin SDK that ships in
`TMessagesProj/src/main/assets/plugins_pysdk/sdk-<abi>.zip`.

The zip is byte-reproducible and contains precompiled `.pyc` files. The
script `Tools/build-plugin-sdk-zip.py` rebuilds both ABI archives plus the
outer `v.txt`.

To verify the committed archives match this tree without writing anything:
```sh
py -3.11 Tools/build-plugin-sdk-zip.py --check
```

## Requirements

Two requirements come from `PythonPluginsEngine`:

- `_sdk_version`, `base_plugin` and `plugin_settings` must ship as `.pyc`.
- `_sdk_version.__start__()` must return a truthy value, and its
  `__version__` becomes `SDK_VERSION`, which gates plugins declaring
  `__sdk_version__`. The build derives `v.txt` from that same string so the
  two cannot drift.

`elyx` is not a directory here. The host publishes it at runtime as an alias of
`elyxcore` (`PythonPluginsEngine.initSdk`), so plugins can `import elyx` even
though the archive only ships `elyxcore/`.

## Notes

- Both ABI archives are byte-identical: the SDK is pure Python with no native
  code. The split exists only because the host looks up the archive by ABI.
- Archive entries use a fixed timestamp so rebuilds are reproducible, and the
  `.pyc` files record bare module filenames rather than absolute build paths.


---
## Alexgram layout note

Alexgram ships these sources as prebuilt archives at
TMessagesProj/src/main/assets/plugins_pysdk/ (sdk-<abi>.zip + v.txt),
extracted at runtime by the plugin engine. TMessagesProj/src/main/python
is intentionally empty so in-app SDK updates are never shadowed by
Chaquopy-bundled app modules. Rebuild the archives from this directory
with NagramXF Tools/build-plugin-sdk-zip.py (host Python 3.11).
