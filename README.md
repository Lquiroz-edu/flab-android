# F/LAB for Android

F/LAB is an experimental Android platform for exploring continuity, motion, and adaptive experiences on foldable devices without replacing the identity of the underlying system.

## First milestone

The initial milestone provides:

- A native Kotlin and Jetpack Compose application.
- Edge-to-edge presentation with a small F/LAB visual language.
- Responsive compact, medium, and expanded layouts.
- Fold posture reporting through Jetpack WindowManager.
- A local motion prototype that does not require accessibility, overlay, or root permissions.
- Continuous integration that tests, lints, and produces a downloadable debug APK.

## Build

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions also uploads it as the `F-LAB-debug-apk` artifact.

## Safety boundary

This milestone only changes UI inside F/LAB. It does not alter other apps, replace One UI Home, use an accessibility service, draw overlays, or require root access.

See [Product direction](docs/PRODUCT.md), [Architecture](docs/ARCHITECTURE.md), and [Capability boundaries](docs/CAPABILITIES.md).

## Duo Fold Live reference

The supplied Duo Fold Live 3.5.2 source archive is preserved under [references/duo-fold-live](references/duo-fold-live/README.md), with its original licenses and a file hash inventory. See the [technical review and F/LAB integration plan](docs/DUO_FOLD_REFERENCE.md) for continuous-angle acquisition, live capture, projection, handoff and firmware limits. This reference is not part of the F/LAB application build; its system-wide animation remains a future opt-in integration.
