#!/usr/bin/env python3
"""Fail-closed local fingerprint checker for the owner-supplied D2 1.13c binaries.

This intentionally does not patch, load, or redistribute game files. It only verifies
that local files match the compatibility manifest before native hooks may be enabled.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import struct
import sys
from pathlib import Path


def inspect_pe(path: Path) -> dict:
    data = path.read_bytes()
    result = {
        "size": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
    }
    if len(data) < 0x40 or data[:2] != b"MZ":
        result["peError"] = "missing DOS header"
        return result
    pe_offset = struct.unpack_from("<I", data, 0x3C)[0]
    if pe_offset + 24 > len(data) or data[pe_offset:pe_offset + 4] != b"PE\0\0":
        result["peError"] = "invalid PE signature/offset"
        return result
    machine, _sections, timestamp = struct.unpack_from("<HHI", data, pe_offset + 4)
    result["peMachine"] = f"0x{machine:04x}"
    result["peTimestamp"] = timestamp
    return result


def compare(name: str, actual: dict, expected: dict) -> list[str]:
    errors = []
    for key in ("size", "sha256", "peMachine"):
        want = expected.get(key)
        got = actual.get(key)
        if want is None:
            errors.append(f"{name}: manifest lacks {key}; verification incomplete")
        elif got != want:
            errors.append(f"{name}: {key} mismatch (expected {want}, got {got})")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("game_dir", type=Path, help="Directory containing Game.exe and DLLs")
    parser.add_argument("--manifest", type=Path, default=Path(__file__).parents[1] / "compatibility" / "1.13c-build.json")
    args = parser.parse_args()

    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    expected_files = manifest.get("files", {})
    failures = []
    results = {}
    for filename, expected in expected_files.items():
        path = args.game_dir / filename
        if not path.is_file():
            failures.append(f"{filename}: missing local file")
            continue
        actual = inspect_pe(path)
        results[filename] = actual
        failures.extend(compare(filename, actual, expected))

    print(json.dumps({"target": manifest.get("target"), "results": results, "failures": failures,
                      "nativeHooksAllowed": not failures and manifest.get("nativeCompatibility", {}).get("status") == "verified"},
                     indent=2))
    return 0 if not failures else 2


if __name__ == "__main__":
    sys.exit(main())
