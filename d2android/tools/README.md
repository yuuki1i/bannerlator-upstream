# 1.13c binary verification

Before native D2Android hooks can be enabled, verify the user's own game files
against `compatibility/1.13c-build.json`.

Run from a checkout:

```sh
python3 d2android/tools/verify_113c_binaries.py /path/to/Diablo-II
```

The checker reports file size, SHA-256 and PE machine/timestamp metadata. It
fails closed when the manifest has incomplete fingerprints, files are missing,
or any value differs. It does not modify the game files.

**Current blocker:** the manifest's `D2Client.dll` SHA-256 and PE metadata are
not yet verified. Native hooks must remain disabled until all required binaries
are fingerprinted and hook addresses/structures are validated against this
exact build. A version string alone is not sufficient proof.
