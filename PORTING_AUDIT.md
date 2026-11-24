# Source audit — first pass

Audit date: 2026-11-24

## Bannerlator host findings

The selected upstream is a full Android/Kotlin project, not a small launcher stub. Its tree contains roughly 4,365 entries and a large runtime/assets footprint. The authenticated account currently has read-only access to `The412Banner/Bannerlator`; the current personal `yuuki1i/bannerlator` repository is a separate small repository. A normal GitHub fork/import operation is not exposed by the connected GitHub actions, so the full upstream tree has not yet been copied into the personal repository.

Relevant existing Bannerlator input code discovered:
- `app/src/main/java/com/winlator/star/widget/InputControlsView.java`
- `app/src/main/java/com/winlator/star/inputcontrols/ControlElement.java`
- `app/src/main/java/com/winlator/star/inputcontrols/ControlsProfile.java`
- `app/src/main/java/com/winlator/star/inputcontrols/CustomIconManager.java`
- `app/src/main/java/com/winlator/star/core/JoystickRegistry.java`
- `app/src/main/java/com/winlator/star/core/TouchAimSettings.java`
- `app/src/main/java/com/winlator/star/ControlsEditorActivity.java`
- `app/src/main/java/com/winlator/star/InGameControlsEditor.java`

The existing host already has a touch-control editor, profile persistence, custom icons, opacity and virtual-stick handling. The port should extend/reuse these systems rather than build a competing overlay or duplicate the editor.

## D2Android source findings

The selected D2Android 1.6.2 source archive describes:
- Android host app and D2-specific Java/Kotlin package under `source/winlator/app/src/main/java/com/winlator/d2/`.
- Native in-game module under `source/mod/`, built for the 32-bit Windows game process.
- Runtime build scripts under `source/tools/`.
- Its published target is Diablo II LoD 1.13d, not 1.13c.

Relevant D2-specific files found:
- `D2JoystickFollow.java`: joystick anchoring/follow behavior and smoothing.
- `D2HeldAttack.java`: held attack timing and target-related decision helpers.
- `D2AimAssist.java`: aim-assist settings and cone/range behavior.
- `D2CommandWatch.java`: command-progress/stall detection.
- `D2GameActivity.java`, `D2GamepadInput.java`: gameplay lifecycle and input routing.
- `source/mod/d2move.c`: native game-side item movement logic (not itself the player movement controller).
- `source/mod/d2direct.c`: direct game-launch integration with version-specific RVAs.

The app-side/native-side bridge and movement/attack implementation must be traced further before porting. The existing 1.13d RVAs and memory layouts must not be assumed valid for 1.13c.

## Immediate implementation direction

1. Reuse Bannerlator's `InputControlsView`, `ControlElement`, `ControlsProfile` and `CustomIconManager` for the editor and visual layer.
2. Add a Diablo II-specific profile and separate movement/attack/skill control roles.
3. Trace D2Android's exact command protocol and game-side handlers; identify which files contain player movement and target/attack dispatch (do not confuse inventory `d2move.c` with character movement).
4. Port only the app-specific MIT portions with their notices; preserve LGPL-2.1 and third-party boundaries for Winlator-derived code.
5. Build a 1.13c compatibility profile only after PE fingerprints and native call sites have been verified against the user's own files.

## Current limitation

This branch contains the integration specification and an isolated, fail-closed Kotlin control prototype. It does not yet contain the full Bannerlator application source, the D2Android runtime, or an installable APK. No APK build has been claimed.
