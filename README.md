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

## Installing without fighting Android

Android 13 blocks accessibility services for apps installed outside an app store — the
"Restricted setting" refusal. Installers that use the session-based `PackageInstaller` API are
exempt, so the path of least resistance is a release rather than a raw APK:

1. Tag a commit: `git tag v0.2.0 && git push origin v0.2.0`. The `Release` workflow builds, signs
   and publishes the APK.
2. Point [Obtainium](https://github.com/ImranR98/Obtainium) at this repository. It installs through
   the session API, so the block never appears.

Samsung's Auto Blocker must be off for any sideload at all: **Settings › Security and privacy ›
Auto Blocker**. On One UI 9 its Maximum restrictions mode also blocks USB entirely, so `adb` will
not reach the device either.

If you did install a raw APK and hit the refusal, F/LAB Access has the manual route under
"Android blocked the switch?". The step people miss is that the option stays hidden until Android
has refused you at least once, and it lives in the **⋮ menu** of Settings › Apps › F/LAB, not on
the page itself.

### Release signing

The workflow signs with a key supplied through repository secrets. Generate one and add it:

```bash
keytool -genkeypair -v -keystore flab-release.jks -keyalg RSA -keysize 4096 \
  -validity 10000 -alias flab

base64 -w0 flab-release.jks    # paste as FLAB_KEYSTORE_BASE64
```

Then add four secrets under Settings › Secrets and variables › Actions:
`FLAB_KEYSTORE_BASE64`, `FLAB_KEYSTORE_PASSWORD`, `FLAB_KEY_ALIAS`, `FLAB_KEY_PASSWORD`.

Keep `flab-release.jks` somewhere safe and out of the repository — `.gitignore` already excludes
`*.jks`. Losing it means every future build is a different app as far as Android is concerned, and
updates stop working without an uninstall. Without the secrets the workflow still produces an
installable APK signed with the debug key, and warns that it did.

## Safety boundary

F/LAB does not replace One UI Home and does not require root.

Everything works inside F/LAB's own window with no special permission. **System effects** — the
fold treatment applied across the whole device — is the one part that reaches outside, it is off
until you turn it on, and it needs two grants you make yourself:

- **Display over other apps**, for a layer created `FLAG_NOT_TOUCHABLE` that never takes a touch.
  Every tap, swipe and gesture passes through to the app underneath.
- **An accessibility service** that reads one string, the foreground package name, so the layer can
  refuse to appear over banking apps, authenticators, payment sheets and the camera. It declares no
  window-content access, so Android never hands it screen contents at all.

While it runs, a notification stays in your shade with a one-tap stop. Uninstalling F/LAB leaves
nothing behind.

See [Product direction](docs/PRODUCT.md), [Architecture](docs/ARCHITECTURE.md),
[Capability boundaries](docs/CAPABILITIES.md) and
[Definition of Done](docs/DEFINITION_OF_DONE.md).
