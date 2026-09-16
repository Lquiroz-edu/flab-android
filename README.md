# F/LAB for Android

F/LAB is an experimental Android platform for exploring continuity, motion, and adaptive experiences on foldable devices without replacing the identity of the underlying system.

## F/LAB MVP 0.5

The current MVP provides:

- A native Kotlin and Jetpack Compose application.
- Edge-to-edge presentation with a small F/LAB visual language.
- One event-driven Core for hinge, posture, window, display, modules and power state.
- Direct physical progress when a continuous hinge sensor is exposed, with a disclosed timed fallback for coarse postures.
- An interactive Motion preview and a One UI live wallpaper with soft page perspective.
- Optional App awareness that observes only package/window changes; it cannot retrieve content, take screenshots or perform gestures.
- Conservative compatibility rules, Safe Apps, profiles, onboarding, Kill Switch and local Diagnostics.
- Continuous integration that tests, lints, and produces a downloadable debug APK.

## Build

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions also uploads it as the `F-LAB-debug-apk` artifact.

## Safety boundary

F/LAB never replaces One UI Home or requires root. App awareness is optional. Its service is marked
`isAccessibilityTool=false`, retrieves no window content and has no screenshot or gesture capability.
The Immersive experiment is off by default and can only draw non-touchable chromatic edges for apps
explicitly enabled by the user. Critical system, camera, navigation and installation surfaces are excluded.

See [Product direction](docs/PRODUCT.md), [Architecture](docs/ARCHITECTURE.md), and [Capability boundaries](docs/CAPABILITIES.md).
