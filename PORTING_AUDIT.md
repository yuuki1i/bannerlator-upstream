# D2 1.13c porting audit

## Host integration

The application already has a mature on-screen control editor and profile persistence. D2-specific controls are isolated under `app/src/main/java/com/winlator/star/d2android/` rather than duplicating the existing host editor.

## D2 source compatibility

The reference D2Android source targets Diablo II LoD 1.13d. Its native RVAs and memory layouts are not accepted for 1.13c. No 1.13c offsets are assumed.

## Implemented in this branch

- Normalized independent joystick math with dead-zone handling.
- Independent movement, attack and skill control profile elements.
- Multitouch overlay view with separate pointer ownership for movement and attack.
- Semantic game adapter interface; no silent mouse-click fallback.
- Fail-closed compatibility gate; no approved 1.13c hashes are configured yet.
- Unit tests for joystick math and profile serialization.

## Remaining release blockers

- Verify owner-supplied 1.13c executable and DLL fingerprints.
- Implement the matching native in-game command handler for movement, target acquisition and attack.
- Attach the D2 overlay to the correct game session and persist/edit its profile through the host editor.
- Run Gradle tests/build on hosted CI and validate on-device.
