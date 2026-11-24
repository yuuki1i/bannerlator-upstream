# D2 1.13c porting audit

## Host integration

D2-specific controls live under `app/src/main/java/com/winlator/star/d2android/`; the runtime attaches the overlay to the selected game session. The profile store persists control positions and presentation settings independently of generic Winlator profiles.

## D2 source compatibility

The reference D2Android source targets 1.13d. Its native RVAs are not reused. The 1.13c module uses the separately verified 1.13c address table and refuses to activate unless the owner-supplied Game.exe, D2Common.dll and D2Client.dll fingerprints match.

## Implemented in this branch

- Continuous eight-direction joystick pathing through the 1.13c native ClickMap entrypoint.
- Separate held basic attack, right-click skill hold/drag, target lock and target cycling.
- Direct game-native tap/drag input for menus, inventory and item interaction.
- Nearby item hint/pickup, nearest stash discovery using 16-bit object class IDs, and nearest town NPC interaction.
- Character/skill/mercenary/inventory/stash quick menu and persistent layout editor.
- Offline single-player town inventory packing and item-move packet state machine; charms fixed by default.
- 32-bit launcher wrapper for the supplied game.exe/-3dfx/-direct entry, including .exe/.bat shortcut interception.
- Unit tests for joystick math, command packet encoding, profile persistence/validation and state protocol parsing.

## Remaining release blockers

- Real-device/game end-to-end smoke testing is still required; compile success does not prove in-game behavior.
- Inventory auto-sort is restricted to offline single-player town state and has not been tested against the user's live save.
- Validate screen scaling, hotkey bindings, inventory grid behavior and touch layout on the target Android device.
