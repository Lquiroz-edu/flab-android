# F/LAB for Android

F/LAB is an experimental Android platform for exploring continuity, motion, and adaptive experiences on foldable devices without replacing the identity of the underlying system.

## F/LAB Motion Lab 0.6

The current MVP provides:

- A native Kotlin and Jetpack Compose application.
- Edge-to-edge presentation with a small F/LAB visual language.
- One event-driven Core for hinge, posture, window, display, modules and power state.
- Direct physical progress when a continuous hinge sensor is exposed, with a disclosed timed fallback for coarse postures.
- One crease-free AGSL fold material shared by Preview, the One UI live wallpaper and continuity experiments.
- Cover and inner surfaces use different physical curves; velocity adds restrained motion energy without a fake hinge mask.
- Optional System Motion LAB takes one ephemeral in-memory frame per fold phase and draws a non-touchable shader overlay.
- Optional App awareness observes package/window changes but never retrieves accessibility nodes or performs gestures.
- Conservative compatibility rules, Safe Apps, profiles, onboarding, Kill Switch and local Diagnostics.
- Continuous integration that tests, lints, and produces a downloadable debug APK.

## Build

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions also uploads it as the `F-LAB-debug-apk` artifact.

## Safety boundary

F/LAB never replaces One UI Home or requires root. App awareness is optional. Its service is marked
`isAccessibilityTool=false`, retrieves no accessibility window content and has no gesture capability.
System Motion LAB can take a screenshot only after a separate in-app opt-in and Android's Accessibility
consent. The frame stays in memory for the transition and is then destroyed; secure and protected surfaces
are excluded. Preview and the live wallpaper never need this access.
The Immersive experiment is off by default and can only draw non-touchable chromatic edges for apps
explicitly enabled by the user. Critical system, camera, navigation and installation surfaces are excluded.

See [Product direction](docs/PRODUCT.md), [Architecture](docs/ARCHITECTURE.md), and [Capability boundaries](docs/CAPABILITIES.md).
