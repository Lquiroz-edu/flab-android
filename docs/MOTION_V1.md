# F/LAB Movimiento suave 1.0

Optional timed 620 ms frost transition on a temporary screenshot. This is not
an angle-accurate replica of the Apple demo or an official Good Lock plugin.
One UI remains the launcher. Android 13+.

## Permission and operation

Explicit consent in F/LAB followed by Android Accessibility permission. Screenshot
capability only; no accessibility tree retrieval, gestures, Internet or storage permission.
Images stay in RAM. Protected capture failures and predominantly black frames are skipped.
Disabled by default. No captures on locked/noninteractive devices. Screen-off unregisters
the sensor. Disabling cancels pending work and removes overlays. Android's disabled
animation setting is respected.

Public hinge sensor: non-wake-up preferred, then wake-up. Posture boundaries and changes
in physical display dimensions trigger the effect. Intermediate readings are reported
only after five distinct noncanonical angles; animation remains timed in both cases.

Independent AGSL displacement with Android RenderEffect Gaussian blur.
No borrowed shader source. Touch-through, non-focusable, secure overlay; 900 ms watchdog.
No forced dual display, display-state override, Shizuku, root, unlock or remote action.

## Research references (no source copied)

- https://www.reddit.com/r/GalaxyFold/comments/1wd3swb/iphone_duo_animation_take_two/
- https://github.com/Atomicx7/Duo-animation
- https://github.com/marcoazeem/duo-open

## Limits and verification

The snapshot briefly freezes moving content within the fading overlay. Samsung controls
panel power and unlock. Quick folds may omit a phase or show the effect after the switch.
Secure screens cannot be animated. Three-position readings are not reconstructed as
precise angles. No physical Samsung device is attached to CI.

Automated gate: posture/debounce tests, existing tests, lint and debug build.
Physical acceptance pending: Samsung shader compilation, fold/unfold on Home, disable and
permission revocation, protected/lock screens, idle behavior, touch-through, landscape,
frame time and battery. Debug APK is for personal sideloading, not a Play Store release.
