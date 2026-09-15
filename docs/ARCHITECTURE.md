# Architecture

The bootstrap intentionally uses one Android application module so the first cloud builds stay fast and simple. Packages separate responsibilities and can become Gradle modules when their APIs stabilize.

## Layers

- `fold`: converts WindowManager signals into small domain models.
- `ui/theme`: F/LAB design tokens and Material theme.
- `ui/home`: the adaptive demonstration and device status.
- `MainActivity`: edge-to-edge host and composition root.

The app uses unidirectional state flow. Window layout information is observed as a Kotlin Flow and converted into immutable UI state. Motion preview state is saveable across supported configuration changes.

## Future module boundaries

When the second production feature is added, extract `core:fold`, `core:designsystem`, and independent `feature:*` modules. Early modularization is intentionally avoided to reduce Gradle configuration and CI complexity.
