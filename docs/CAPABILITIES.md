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

## Available by owning a rendering surface — no extra permission

A live wallpaper (`WallpaperService`) needs nothing beyond what the OS itself enforces on the
service (`BIND_WALLPAPER`, which only the system's wallpaper host holds) and the user choosing it as
their wallpaper through the normal system picker. No runtime permission dialog, no entry on
F/LAB Access.

What that ownership buys, that `SYSTEM_ALERT_WINDOW`'s blur/dim cannot: **geometry**. Compositor
blur composites *over* another window's pixels; it cannot bend them. A wallpaper is F/LAB's own
surface, rendered pixel by pixel, so F/LAB can reshape it directly. `FoldWallpaperService` spends
that on reproducing the effect analysed from Apple's iPhone Duo footage — the background pinches at
the hinge (`FoldWarpMesh`, a `Canvas.drawBitmapMesh` displacement) in proportion to how closed the
device is, with frosted-glass cards over it showing real device data.

The boundary is the same shape as everywhere else in this file: this is F/LAB's own surface. It has
nothing to say about Instagram's background, or any other app's.

## Available with `SYSTEM_ALERT_WINDOW` plus an accessibility service

These are what **System effects** uses. Both are off until the user turns them on in F/LAB Access,
and the feature refuses to start unless it has both.

- **Blur and dim the whole screen, over any app.** An overlay window with `FLAG_BLUR_BEHIND` and
  `setBlurBehindRadius` (Android 12+) asks the compositor to blur what is *behind* it — the real
  app, not a screenshot. `FLAG_DIM_BEHIND` does the same for darkening. This is the one genuinely
  system-wide visual effect a sandboxed app can produce.
- **Know which app is in front.** One string, `AccessibilityEvent.getPackageName`. F/LAB's service
  requests neither `canRetrieveWindowContent` nor key-event filtering, so the platform never hands
  it screen content at all.

Cross-window blur can be unavailable — GPU limits, battery saver, multimedia tunneling. Android's
guidance is to carry the effect with a stronger dim instead, which is what `FoldOverlayWindow`
does. `WindowManager.isCrossWindowBlurEnabled` reports the current answer and Diagnostics shows it.

### The constraints that come with them

- The overlay is created `FLAG_NOT_TOUCHABLE` and **never** takes input. Not a setting, a constant.
  An interactive layer over a permission prompt or a payment sheet is a tapjacking surface.
- It refuses to draw over protected apps, on the lock screen, in multi-window, or when the
  foreground package is unknown — because not knowing what is underneath means not knowing whether
  it is a bank. See `SystemEffectPolicy`, which is pure and tested for exactly these cases.
- Sideloaded builds hit Android 13's Restricted Settings, which blocks the accessibility toggle.
  No app can lift that on itself, by design. F/LAB explains the route and opens the one screen it
  is allowed to open.

## Requires an explicit opt-in experiment

- **Per-screen context detection** — telling a Reel from a Feed. Needs more from the accessibility
  service than the package name. Parked in F/LAB Experiments, off by default.
- **Shizuku or ADB-mediated operations** — out of scope for v1.

Each experiment must be isolated, explain its permissions, and carry a measurable battery and
performance budget.

## Not available to a normal third-party app, at all

Not even with the overlay and accessibility grants above:

- **Moving, scaling or restyling another app.** The overlay sits above; it cannot transform what is
  underneath. Blur and dim are the only two things the compositor will do on request.
- Setting **another app's** status bar or navigation bar colour, or its icon polarity.
- Reading another app's window contents, including sampling a colour from behind its status bar.
- Taking part in another app's configuration change, Activity recreation or relaunch.
- Replacing One UI's own unfold transition, or any SystemUI animation. This is the one people ask
  about most: the celebrated iPhone Duo open animation is a SystemUI transition, and the recreations
  of it on Android foldables are standalone apps animating screenshots of *their own* content. None
  of them touches the system transition either.
- Modifying protected System UI behaviour without platform signing, root, or OEM cooperation.

## What this means for the DoD

The line now falls in a different place than it did in the first v1 pass.

**Reachable, and implemented:** a fold-driven blur and dim across the whole system, gated per app.
That is System effects, and it is what DoD 1's "improvements visible outside F/LAB's own interface"
actually amounts to for a sandboxed app.

**Still not reachable:** the parts of DoD 5, 6, 7 and 46 that would *restyle* Instagram — extending
its content into the status bar area, recolouring its system bars. Those need to modify another
app's window, which nothing above permits. The decision layer for them is built and tested, so if a
surface ever appears the question "should F/LAB act here?" is already answered.

The order was deliberate throughout: build the decision, the rule table and the safety gates
completely first. That is why turning on a capability that can draw over other apps was a matter of
wiring an existing, tested policy to a new window — rather than inventing the safety rules at the
same time as the feature that needs them.
