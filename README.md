# F/LAB for Android

F/LAB is a foldable-first Android app that tries to make a Galaxy Z Fold feel more coherent when it
opens, closes and changes screen — without replacing One UI, without root, and without asking for
anything beyond the normal app sandbox.

## What is in this build

**F/LAB Core.** One engine holding one state: posture, continuous fold progress, hinge angle,
orientation, window size, active display, presentation mode, profile, module health and power
posture. No module detects the fold for itself.

**Fold Motion.** Motion that follows the hinge rather than replaying an animation. Where the device
reports a continuous hinge angle, F/LAB uses it; where it only reports posture changes, F/LAB
interpolates between them. Either way, progress only travels towards the most recent physical
evidence — stop opening the device halfway and the motion stops with you.

**Continuity.** A cover ↔ inner bridge built against a budget rather than a duration, so it hides
latency instead of adding any, and can never leave a layer up.

**Immersive and App Profiles.** A per-*context* decision — Reels is not Feed, Shorts is not the
YouTube home — with a confidence threshold, a version-scoped compatibility rule table, and a
safe-app policy that refuses banking, authenticators, password managers, payments, the camera and
system UI whatever the settings say.

**Diagnostics.** Everything the engine believes, on one screen, plus a debug report that contains
no identifiers, no screen contents, no installed-app inventory and no wall-clock times.

## What it does not do

A normal Android app cannot restyle another app's status bar, draw into another app's window, or
take part in another app's relayout. F/LAB does not claim to. The decision layer for those cases is
built and tested; the application layer is bounded by the platform, and
[`docs/CAPABILITIES.md`](docs/CAPABILITIES.md) sets out exactly where the line falls.

Progress against the 50-point Definition of Done is tracked in
[`docs/DEFINITION_OF_DONE.md`](docs/DEFINITION_OF_DONE.md), including which items can only be
signed off with a device in hand.

## Build

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions also uploads it
as the `F-LAB-debug-apk` artifact.

## Safety boundary

F/LAB changes nothing outside its own process. It does not replace One UI Home, run an accessibility
service, draw over other apps, or require root. It requests two permissions —
`RECEIVE_BOOT_COMPLETED` so it survives a restart, and `POST_NOTIFICATIONS` so it can tell you when
a module switched itself off. Uninstalling it leaves nothing behind.

See [Product direction](docs/PRODUCT.md), [Architecture](docs/ARCHITECTURE.md),
[Capability boundaries](docs/CAPABILITIES.md) and
[Definition of Done](docs/DEFINITION_OF_DONE.md).
