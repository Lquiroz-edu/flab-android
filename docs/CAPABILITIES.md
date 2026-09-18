# Capability boundaries

What a normal, unrooted, third-party Android app can and cannot do. This file is the reference the
Definition of Done tracker points at whenever an item is marked **Bounded by Android**.

## Available with standard Android APIs

- Read the hinge angle (`Sensor.TYPE_HINGE_ANGLE`) and fold posture (`FoldingFeature`).
- Read window metrics, presentation mode (split screen, pop-up, PiP) and configuration changes.
- Read the device's power-save and thermal status.
- Draw edge-to-edge inside F/LAB, and set F/LAB's **own** system bar colours and icon polarity.
- Adapt F/LAB's layout when window size or posture changes, and preserve state across it.
- Provide motion, haptics, widgets, wallpapers and notifications owned by F/LAB.
- Identify a Samsung One UI build through a public system feature.

## Requires an explicit opt-in experiment

- **Accessibility service** — observing which app is in front, and roughly which screen within it.
  This is what App Profiles and Immersive Detection would need to act outside F/LAB's own window.
  Parked in F/LAB Experiments, off by default, with a stated rationale for exactly what is read.
- **`SYSTEM_ALERT_WINDOW`** — drawing a layer *above* another app. Not requested in v1. It cannot
  restyle the app underneath, and an interactive overlay over a permission prompt or a payment
  sheet is a tapjacking surface, which the safe-app policy exists to rule out.
- **Shizuku or ADB-mediated operations** — out of scope for v1.

Each experiment must be isolated, explain its permissions, and carry a measurable battery and
performance budget.

## Not available to a normal third-party app, at all

- Setting **another app's** status bar or navigation bar colour, or its icon polarity.
- Drawing behind or into another app's window.
- Reading another app's window contents in order to sample a colour behind its status bar.
- Taking part in another app's configuration change, Activity recreation or relaunch, or covering
  the gap while it happens.
- Injecting animations into another app or into One UI's own system transitions.
- Modifying protected System UI behaviour without platform signing, root, or OEM cooperation.

## What this means for the DoD

Several DoD sections — notably 5, 6, 7 and 46 — describe changing how Instagram, YouTube and TikTok
look. The parts of those sections that are about **deciding** what should happen are implemented
and tested. The parts that are about **applying** it to another app's window are not reachable from
here, and nothing in the codebase pretends otherwise.

The order was deliberate: build the decision, the rule table and the safety gates completely, so
that if a surface does become available the question "should F/LAB act here?" is already answered
and already tested.
