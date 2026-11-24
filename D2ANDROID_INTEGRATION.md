# Bannerlator D2Android integration — engineering specification

Target branch: `bannerlator-d2android-1.13c`

## Product goal

Create a Bannerlator-based Android build for owner-supplied Diablo II: Lord of Destruction 1.13c installations. The original Windows game remains the game; the Android app supplies the Wine/Box64 runtime and a native touch interface.

## Required controls

1. **Native movement stick** — an independent analog virtual stick. Direction and magnitude are translated to the game's movement command, not to a screen-position mouse drag. Support fixed and thumb-follow modes, dead-zone and sensitivity settings.
2. **Independent target/attack** — a separate attack control and target-selection path. It must not be coupled to the movement stick. The game-side module should select/retain a valid target and issue the correct native game command; behavior must be verified for 1.13c before enabling it.
3. **Skill/action buttons** — independently bindable skills/actions, with per-character profiles and clear feedback for unavailable skills.
4. **Full control editor** — drag positioning, independent width/height/scale, opacity, custom image/texture, shape (circle/square/custom image), rotation, z-order, and reset/restore. Persist profiles per game/version and screen orientation.
5. **Game-native command bridge** — use a version-gated in-process module and a narrow local IPC channel. Do not fall back silently to fake mouse clicks for movement or attack.
6. **Chinese UI** — localize app screens, control editor, setup/diagnostics, and error messages. Do not modify proprietary game text/assets without the owner's lawful files and explicit scope.

## Architecture

- Bannerlator remains the Android host/runtime: Android lifecycle, Wine/Box64 environment, graphics/audio stack, file access and existing input editor.
- D2Android-derived code is isolated into clearly named packages/modules. Preserve original copyright and license notices at file and module boundaries.
- Android input layer: touch gesture -> normalized control state -> command bridge.
- Native game module: validates the game build, reads only version-matched structures, translates commands into game-native movement/attack/skill operations.
- Compatibility profile: exact executable/DLL fingerprints, architecture, PE timestamp/version, expected module hashes, and feature capability flags.
- Fail closed: unknown executable/DLL versions disable native injection and show a diagnostic; never guess offsets or patch an unverified binary.

## 1.13c compatibility gate

D2Android 1.6.2 source describes 1.13d. 1.13c is a different target and must not reuse 1.13d offsets, signatures or patches without verification. Owner-provided EXE/DLL files will be inspected for PE metadata, imports/exports and hashes. A 1.13c-specific profile and tests are required before native control is marked supported.

## Milestones

- [x] Select Bannerlator as host project and establish a dedicated development branch.
- [x] Record control and native-command requirements.
- [ ] Import/establish a complete Bannerlator source base in this repository.
- [ ] Audit Bannerlator input editor, overlay routing, JNI and runtime launch paths.
- [ ] Audit D2Android source and identify independently licensable modules.
- [ ] Port touch editor and profile persistence.
- [ ] Port game command bridge and native module with version gate.
- [ ] Add Diablo II 1.13c profile after binary verification.
- [ ] Add Chinese localization and device diagnostics.
- [ ] Configure hosted CI build and publish first test APK.
- [ ] Validate on-device with owner-supplied game files.

## Distribution and account safety

- Use normal GitHub API/Actions usage with small reviewable commits and no repeated polling.
- Do not automate stars, forks, issues, releases, or other engagement.
- Do not upload proprietary game executables, DLLs, MPQs, CD keys, saves, signing keys or personal tokens to a public repository.
- Keep upstream attribution, license files, source offers and third-party notices.
- Publish only artifacts built from source that this project is permitted to redistribute.
