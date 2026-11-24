# D2 touch-control prototype

This is the first isolated control-model layer for the Bannerlator port.

- Analog joystick math produces a normalized direction and strength; it does not synthesize mouse movement.
- Attack and target-selection are separate commands from movement.
- Button configuration models position, independent dimensions, opacity, shape, custom texture, rotation and z-order.
- The native bridge is an interface only. No 1.13c offsets or executable hooks are enabled until verified against owner-provided binaries.
- The compatibility gate defaults to deny-all.

This directory is not yet wired into Bannerlator's Android UI or native runtime and is not an APK-ready application by itself.
