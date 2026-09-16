# F/LAB for Android

F/LAB is an experimental Android platform for exploring continuity, motion, and adaptive experiences on foldable devices without replacing the identity of the underlying system.

## Motion Continuity V1

The current prototype provides:

- A native Kotlin and Jetpack Compose application.
- Edge-to-edge presentation with a small F/LAB visual language.
- Responsive compact, medium, and expanded layouts.
- Fold posture reporting through Jetpack WindowManager.
- A shared panoramic canvas: Cover crops the center and Inner reveals the missing sides.
- Live hinge-angle response when available, with WindowManager fallback.
- A short physical settle and center-crease treatment inspired by the supplied Duo reference.
- Continuous integration that tests, lints, and produces a downloadable debug APK.

## Build

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions also uploads it as the `F-LAB-debug-apk` artifact.

## Safety boundary

This milestone only changes UI inside F/LAB. It does not alter other apps, replace One UI Home, use an accessibility service, draw overlays, or require root access.

See [Product direction](docs/PRODUCT.md), [Architecture](docs/ARCHITECTURE.md), and [Capability boundaries](docs/CAPABILITIES.md).
