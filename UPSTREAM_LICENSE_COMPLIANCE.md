# Upstream and license compliance notes

## Bannerlator

Selected host: https://github.com/The412Banner/Bannerlator

The upstream repository declares GPL-3.0. A derivative distribution must preserve the GPL notices, provide corresponding source for covered binaries, and satisfy GPLv3 redistribution requirements. This project must not imply endorsement by the upstream maintainer.

## D2Android

Selected reference/source: https://github.com/N1v0k/D2Android-Releases

The D2Android 1.6.2 source README identifies:
- D2Android's own app code under `winlator/app/src/main/java/com/winlator/d2/`, `mod/`, and most `tools/` as MIT.
- The Winlator-derived app/runtime portions as LGPL-2.1.
- The ALSA plugin as LGPL-2.1.
- Additional third-party components with their own licenses and notices.

The MIT license does not relicense the Winlator/LGPL or third-party portions. Preserve all applicable license texts and notices. Include corresponding source and build scripts for LGPL/GPL components when distributing binaries.

## Diablo II files

No Blizzard game executable, DLL, MPQ, installer, CD key, save or game asset is included in this repository or build artifact. The user must supply a lawfully obtained game installation. Do not publish user-supplied game binaries.

## Compatibility

The published D2Android 1.6.2 source targets Diablo II: Lord of Destruction 1.13d. This project targets 1.13c; version-specific native hooks must be independently verified against the exact 1.13c binaries. No claim of 1.13c support is made until that work and runtime tests pass.
