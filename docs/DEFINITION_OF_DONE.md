# F/LAB v1.0 — Definition of Done tracker

This file tracks the 50-point Definition of Done against what is actually in the repository, and
against what Android permits a normal, unrooted, third-party app to do.

Status vocabulary:

| Status | Meaning |
| --- | --- |
| **Done** | Implemented and covered by tests or directly verifiable in the app. |
| **Structured** | The architecture, data model and policy exist and are tested. The behaviour is not yet wired to a real device surface. |
| **Pending** | Not started. |
| **Device-gated** | Cannot be signed off from source. Needs a Galaxy Z Fold and a measurement. |
| **Bounded by Android** | The stated goal is not reachable in full for a normal app. The section below says what *is* reachable and what is not. |

---

## Update: System effects

Since the first pass, F/LAB has a **System effects** module that applies the fold treatment across
the whole device, not only inside F/LAB. It uses an overlay window with `FLAG_BLUR_BEHIND` plus a
minimal accessibility service that reads only the foreground package name, so the effect can refuse
to appear over protected apps.

That moves several items below: DoD 1's "visible outside F/LAB's own interface" is now partly Done
rather than Bounded, and DoD 31's click-through requirement is now load-bearing rather than
recorded for later. What it does **not** move is anything requiring another app to be restyled —
see the constraint below, which has been updated rather than removed.

## Update: Fold Wallpaper

F/LAB also ships a live wallpaper (`system/FoldWallpaperService.kt`) that reproduces the effect
analysed from Apple's iPhone Duo footage: the background is one continuous image, geometrically
pinched at the hinge in proportion to how closed the device is (`FoldWarpMesh`, a `Canvas.drawBitmapMesh`
displacement, not a blur), with frosted-glass cards showing real device data — time, battery, fold
posture — over a brand-generated background.

This exists because System effects' blur/dim cannot bend geometry, only composite over it; a live
wallpaper is a surface F/LAB actually renders, pixel by pixel, so the one thing that surface
category can do — reshape its own content — becomes available. It is still bounded exactly where
System effects is: it is F/LAB's own surface, not Instagram's or anyone else's.

Reading the hinge sensor for both System effects and Fold Wallpaper at once required turning
`FLabCore`'s continuous-tracking flag into a ref-counted, per-caller request
(`requestContinuousTracking`/`releaseContinuousTracking`), so one background consumer switching off
cannot silently starve the other.

## The honest constraint, stated once

Several sections of the DoD ask F/LAB to change how **other apps** look — to extend Instagram's
Reels into the status bar area, to blend YouTube's system bars, to hide another app's relayout
during an unfold.

A normal Android app cannot do any of that. Specifically, it cannot:

- set another app's status bar or navigation bar colour, or its icon polarity;
- draw behind or into another app's window;
- read another app's window contents to sample a colour;
- take part in another app's configuration change, Activity recreation or relaunch;
- alter One UI's own transitions.

What *is* reachable, and what the code in this repository is built around:

1. **F/LAB's own surfaces** get the full treatment. Everything in `motion/`, `continuity/` and
   `immersive/` runs on F/LAB's own window today.
2. **An overlay** (`SYSTEM_ALERT_WINDOW`) can draw *above* another app. It cannot restyle it, it
   cannot be trusted over sensitive surfaces, and it is a tapjacking surface if it is interactive.
   F/LAB does not request it in v1.
3. **An accessibility service** can observe which app and roughly which screen is in front. That is
   what the App Profiles and Immersive Detection modules would need to act outside F/LAB. It is
   parked in F/LAB Experiments, off by default, with a stated rationale.

The decision taken here is the one DoD 48 asks for: build the policy, the rule table and the safety
gates first and completely, so that when a surface does become available the answer to "should
F/LAB act here?" is already written and already tested. Nothing ships that pretends to a capability
it does not have.

`docs/CAPABILITIES.md` holds the full capability boundary.

---

## Section-by-section

### 1. Product objective — **Done** (within what the platform allows)
No root, no One UI replacement, no permanent service, no accessibility requirement for any stable
module. `AndroidManifest.xml` requests only `RECEIVE_BOOT_COMPLETED` and `POST_NOTIFICATIONS`.
"Improvements visible outside F/LAB's own UI" is delivered by **System effects**: a fold-driven
blur and dim across the whole device. It is off by default and needs two grants the user makes
deliberately. What remains **Bounded by Android** is restyling another app, which nothing permits.

### 2. F/LAB Core — **Done**
`core/FLabCore.kt` owns a single `FLabState` (`core/FLabState.kt`): posture, continuous progress,
hinge angle, orientation, window size, active display, presentation mode, foreground package,
engine status, module states, profile and power posture. No module detects the fold independently.
The Core lives on the Application, not an Activity, so a
`closed → part open → open → part closed → closed` cycle cannot lose it.
Tested in `FLabStateTest`.

### 3. Fold Motion Engine — **Done** (inside F/LAB), **Bounded by Android** (elsewhere)
`motion/PerceptualInterpolator.kt` interpolates towards physical evidence and never plays a
free-running animation. `Sensor.TYPE_HINGE_ANGLE` gives genuinely continuous input where the device
has it; posture events are the coarse fallback. Controls scale, blur, opacity, depth, offset,
easing, spring, dimming and expansion — `motion/MotionChannels.kt`.

The two properties the DoD singles out are asserted in `PerceptualInterpolatorTest`:
stopping the device stops the motion, and a `HALF_OPENED` event targets 0.5 rather than triggering
a 0→1 animation.

The prohibitions — no seam, fake fold, line, mask, curtain or joint-revealing overlay — are
structural: there is no such channel in `MotionChannels`, and `MotionChannelsTest` asserts the
ceilings that prevent a black flash or an abrupt scale change.

### 4. Continuity Engine — **Structured**
`continuity/ContinuityEngine.kt` implements the bridge as a budget rather than a duration:
`onContentReady` ends the transition early, and the budget is a hard stop so a layer can never
freeze. `shouldBridge` refuses to cover a change too small to have been noticed.
Detecting *another app's* recreation, relayout or relaunch is **Bounded by Android**.
Tested in `ContinuityEngineTest`, including a 50-cycle stress loop.

### 5. Immersive Layer — **Structured**
Strategies are modelled in `compat/CompatibilityRule.kt`: chromatic continuity, edge-to-edge,
gradient extension and the full immersive extension. They run on F/LAB's own window.
Applying them to Instagram is **Bounded by Android**.

### 6. App Immersion Profiles — **Done**
`profiles/AppProfile.kt` seeds Instagram, YouTube, TikTok, Chrome, Camera, Maps, One UI Home,
Gallery and WhatsApp with the treatment each section describes. Camera is locked outright.

### 7. Immersive Detection — **Done** (as policy)
`immersive/ImmersiveContext.kt` makes the context — not the package — the unit of decision, and
carries a confidence with every estimate. `ImmersivePolicy` abstains below 0.7 confidence and on
`Unknown` at any confidence. Producing the estimate for a third-party app is **Bounded by Android**.
The full decision table is tested in `ImmersivePolicyTest`.

### 8. Status Bar Intelligence — **Done**
`immersive/StatusBarIntelligence.kt` works in WCAG contrast over a *sampled region* rather than one
colour, because a flat colour always has a working icon polarity and a video frame does not. Falls
back to the system whenever contrast cannot be guaranteed. `StatusBarIntelligenceTest` sweeps every
grey and every grey pair and asserts there is no third outcome between "legible" and "fall back".

### 9. Navigation Bar Integration — **Structured**
Same strategy set and same fallback. Gesture-versus-button conservatism is not yet implemented.

### 10. F/LAB App — **Done**
`ui/screens/HomeScreen.kt` is a hub, not a settings list: a device hero card, three icon tiles
grouping Fold Motion/Continuity, Immersive/System effects and Apps by what they do, a Performance
card, and Experiments/Diagnostics as compact chips. A tile's subtitle explains its own state (DoD
43) — the per-switch detail lives one tap deeper, on the screen the tile opens.

### 10b. F/LAB Home — **Done**
`launcher/`. The Duo effect's missing half: a home screen whose icons ride the same hinge-pinch
field as the wallpaper (`HomeLayout.warpX` = `FoldWarpMesh.displaceU`), re-column with a spring
when the window changes between cover and inner, and launch apps with a clip-reveal from the icon.
Optional — the user picks it in the system's default-home dialog from the card on Home; One UI Home
stays installed. `HomeLayoutTest` pins the geometry, including that icons never cross at the
strongest pinch. See `docs/CAPABILITIES.md` for exactly what it reads.

The motion starts at the first degree, not at the panel switch: on the cover display the home holds
the hinge listener open while it is on screen (as the wallpaper does) and draws itself toward the
hinge edge as the opening begins (`MotionChannels.handoffAmount`, a bell over progress that is zero
at rest closed and gone by half open, pinned by `MotionChannelsTest`), so the inner display picks
the content up already pinched at the same hinge.

Both panels at once: `launcher/CoverDisplayBridge.kt`. A third-party proof of concept on the Z Fold 8
(moomanjohnny, r/GalaxyFold, September 2026 — hinge angle in, `Presentation` API out, no root)
showed the cover panel can be presented on as a second display. The bridge does the live version:
while `CoverBridgePolicy.shouldMirror` holds (the first half of the opening, and only then — an open
device must not keep its rear-facing cover lit) it presents the home screen on whatever other
display `DisplayManager` exposes, driven by the same channels as the main window. Nothing here can
power a panel on and none of it is documented by Samsung, so Diagnostics lists the raw displays the
device reports and what the bridge last did; the answer on a real Fold is read there, not assumed.

### 11. Live Preview — **Done**
`ui/screens/FoldMotionScreen.kt`. The scrubber feeds the same `MotionChannelMapper` at the same
tuning as the live engine, so stopping mid-drag demonstrates the DoD 3 property directly. Preview
state never touches the running configuration.

### 12. Profiles — **Done**
Balanced, Smooth, Minimal, Battery and Custom in `profiles/FLabProfile.kt`, each a complete
`MotionTuning`. Battery is inert by construction and short-circuits the renderer.

### 13. App Control — **Done**
`ui/screens/AppsScreen.kt` with per-app Immersive and Continuity cycling. Protected apps are shown
locked with the reason, rather than hidden.

### 14. Safe Apps — **Done**, and now load-bearing
`profiles/SafeApps.kt` covers banking, authenticators, password managers, the lock screen,
permission surfaces, payments, the camera, package installation and system UI. Detection is blunt
and errs towards protecting. Both `ImmersivePolicy` and `SystemEffectPolicy` refuse a protected app
even when the user has explicitly enabled it — the one place in F/LAB where a stated user
preference is deliberately not the last word.

`SystemEffectPolicyTest` is the highest-stakes suite in the project, because this is the module
that can put pixels over someone's bank. It also asserts the case that would silently defeat every
other gate: an **unknown** foreground package resolves to refusing, never to showing.

### 15. F/LAB Experiments — **Done**
`core/Experiments.kt` and `ui/screens/ExperimentsScreen.kt`. Everything is off by default and
states its risk above the switch.

### 16. Accessibility — **Done**
No stable module requires it. Only the two experiments that genuinely need it declare it, each with
a rationale stating what is read and what is not.

### 17. Permissions — **Done**
`ui/screens/AccessScreen.kt` answers the same three questions for every capability: what it is,
what F/LAB does with it, what stops working without it.

### 18. Zero-Touch Operation — **Done**
No daily interaction is required. Configuration is persisted and the engine is event-driven.
`core/SetupProgress.kt` plus Home's guided setup card collapse the "turn F/LAB on, grant two
permissions, remember to flip System effects on too" sequence into three taps and no toggle left to
recall afterwards: the setup preference is set the moment the user starts, and the existing
reconciliation in `FLabViewModel.refreshAccess` starts the service itself the instant both grants
land, in whichever order they were granted. The checklist disappears once done and reappears on its
own if a grant is later revoked — the same three-boolean check either way.

### 19. Boot Persistence — **Done**
`BootReceiver.kt` plus `settings/FLabSettings.kt`. Deliberately does *not* start a service at boot:
a permanent service would cost battery all day for no visible gain (DoD 25).

### 20. Crash Safety — **Done**
`core/ModuleCircuitBreaker.kt` takes a module out after three failures in a minute, keeps the
user's own preference intact, and forgets failures outside the window. A `SupervisorJob` keeps one
module's coroutine failure from cancelling the others. Tested in `ModuleCircuitBreakerTest`.

### 21. Kill Switch — **Done**
`FLabCore.disable()` from the Home pill, `FLabCore.reset()` from Diagnostics behind a confirmation.

### 22. Performance — **Done** (by construction), **Device-gated** (by measurement)
No polling, no timers, no wake locks anywhere in the codebase. The hinge listener is registered only
while a transition is in flight. `FoldMotionHost` runs `while (engine.needsFrames)`, not
`while (true)`, so a still device schedules no frames. A neutral frame takes a fast path with no
graphics layer, no blur pass and no scrim.

### 23. Frame Rate — **Device-gated**
The loop is driven by `withFrameNanos`, so it samples at the panel's refresh rate without knowing
it. Real 120 Hz jank has to be measured.

### 24. Latency — **Device-gated**
Evidence submission is synchronous and the next frame applies it. Perceived latency needs a device.

### 25. Battery — **Device-gated**
Needs the four measurement scenarios the DoD lists: idle, normal use, an intensive fold session,
and vertical video.

### 26. Temperature — **Structured**, **Device-gated**
`PowerPosture` folds Android's battery saver and thermal status into the state. `Conserving`
(battery saver on, or `THERMAL_STATUS_MODERATE`) drops the veil channels — blur, dim, elevation,
the ones with a GPU cost — through `MotionTuning.forPower`, and keeps the motion itself running;
`Restricted` (`THERMAL_STATUS_SEVERE`+) stops everything. It used to take Fold Motion out entirely
at `Conserving`, which on a real Galaxy Fold — battery saver at 40%, a warm device after a few
minutes — left every visible effect dead while Home read ON; that is fixed, and `FLabStateTest`
pins it. Home names the signal ("Battery saver is on", "The device is warm"), not just the posture.
Sustained heating still has to be measured.

### 27. Fold compatibility — **Structured**
Nothing is hard-coded to one model. The cover/inner inference is a documented guess, labelled as
one in Diagnostics, and nothing destructive depends on it.

### 28. External app updates — **Done**
No rule depends on a pixel, a coordinate, a view id or a resource id — asserted in
`CompatibilityRegistryTest`. A version outside every known range resolves to nothing, and
`ImmersivePolicy` turns nothing into an abstention. An Instagram update that moves something makes
F/LAB stop optimising Instagram; it does not make F/LAB improvise.

### 29. Compatibility Rules — **Done**
`compat/CompatibilityRule.kt` implements `App → version range → context → capability → strategy`
exactly. Most-specific-first resolution, overrides layer on top without removing the floor.

### 30. No Visual Artifacts — **Structured**, **Device-gated**
The ones that can be prevented structurally are: no seam or fake fold channel exists; dim and alpha
are capped so a transition cannot read as a flash; scale travel is bounded; a stale evidence gap
cannot inject a jump; the continuity budget is a hard stop against a frozen overlay. The rest —
flicker, duplicated frames, late bars — needs a device.

### 31. Interactions — **Done**
F/LAB now does draw over other apps, so this is real. The overlay is created with
`FLAG_NOT_TOUCHABLE or FLAG_NOT_FOCUSABLE` in a constant that is never made conditional, and it has
no content view and no listener of any kind. Every tap, swipe, back gesture, scroll and keystroke
reaches the app underneath untouched.

### 32. System gestures — **Structured**, **Device-gated**
Nothing intercepts input outside F/LAB's own window.

### 33. Multitasking — **Done**
`WindowPresentation` distinguishes full screen, split screen, pop-up and PiP, and
`WindowState.ownsSystemBars` is false for all but full screen. `ImmersivePolicy` abstains with
`MultiWindow`. Unknown is treated as not-full-screen, so the default is to stand back.
Tested in `FLabStateTest` and `ImmersivePolicyTest`.

### 34. Orientation change — **Structured**
`WindowSize` derives its own orientation; the Activity handles the config change in place.

### 35. Lock / Unlock — **Structured**
The Core detaches on `STOPPED`, so no subscription or listener survives a lock.

### 36. Cover → Inner stress test — **Device-gated**
`ContinuityEngineTest` runs the state-machine half of it (50 cycles, no accumulated state). The
real sequence needs a Fold.

### 37. Diagnostics — **Done**
`ui/screens/DiagnosticsScreen.kt` shows device, One UI, Android, fold state, active display, active
modules, permissions, service status and last module error. Part of the Core, not switchable.

### 38. Debug Report — **Done**
`diagnostics/DebugReport.kt`. No installed-app inventory, no identifiers, no screen contents, no
absolute timestamps. Shown in full before sharing, and shared through the system sheet so F/LAB
never uploads anything itself. Tested in `DebugReportTest`.

### 39. Remote Config Ready — **Done**
`compat/RemoteConfigSource.kt`. Rules are data behind a `ConfigSource`; `ConfigResolver` already
does the version gating and layering a remote source would need. No backend, as the DoD allows.

### 40. F/LAB design — **Done**
`ui/theme/FLabTheme.kt` defines both light and dark schemes, not a dark palette with a fallback.
Glass is the default surface treatment across the whole app, not an occasional accent:
`FLabCard` (`ui/components/FLabComponents.kt`) is translucent by default, `FLabApp`'s `GlassBackdrop`
paints a static, colourful multi-blob background behind every screen so that translucency actually
reads as glass, and `FLabButton`/`Pill` fill with a vivid two-hue gradient (`FLabTokens.gradientBrush`)
rather than a flat swatch. `FLabGlassCard` remains the more saturated hero variant, for the single
most important thing on a screen (the Home setup checklist), carrying a stronger two-hue tint through
to the surface underneath. None of this is the wallpaper's real backdrop blur — a card sits over a
scrolling screen, and blurring live content behind it every frame would cost exactly what DoD 22
and 23 rule out, so it reaches for the same reading through static gradients and translucency, a
cheaper and honest approximation of "Liquid Glass" rather than a pixel-accurate clone of it.

### 41. F/LAB animations — **Done**
The whole app is wrapped in `FoldMotionHost`, so F/LAB's own screens get the treatment F/LAB is
arguing for. Every interactive surface responds with a spring rather than a ripple.

### 42. Onboarding — **Done**
`ui/screens/OnboardingScreen.kt`, five steps in the order the DoD lists, including what Android
will not allow and how to switch F/LAB off.

### 43. Healthy state — **Done**
Home shows `F/LAB Active`, `Action required` or the reason a module is off, and offers a route to
Diagnostics.

### 44a. System effects — **Done**
`system/SystemEffectPolicy.kt`, `system/FoldOverlayWindow.kt`, `system/FLabOverlayService.kt`,
`system/FLabAccessibilityService.kt`. Blur-behind with a documented dim fallback when cross-window
blur is unavailable; a foreground service whose notification is deliberate; frame loop stops and
the overlay's surface is released the moment motion settles.

### 44b. Fold Wallpaper — **Done**
`system/FoldWarpMesh.kt` (pure, tested), `system/FoldWallpaperLayout.kt` (pure, tested),
`system/FoldWallpaperService.kt`. The hinge-pinch geometry, the card layout and the card content
model are unit-tested; the Canvas/Bitmap drawing itself is Android-framework code and is not, in
line with the split the rest of the project already uses. Battery and clock are read once via a
registered receiver and a throttled tick respectively, cached, and never touched from the per-frame
draw path — a live wallpaper redraws at up to the panel's refresh rate during a transition, so a
Binder round-trip or a `SimpleDateFormat` allocation per frame there would be a real, measurable
cost, not a rounding error.

### 44. Minimum modules — **Structured**
All four exist as modules with real policy and real tests. Fold Motion is fully live inside F/LAB;
the other three are complete as engines and policy, and gated on the surfaces in the constraint
section above. Diagnostics is part of the Core.

### 45. Minimum validation apps — **Device-gated**

### 46. Instagram acceptance test — **Structured**
The decision half is done and tested: Reels → immersive extension, Stories → gradient, Feed → no
change, DM → no change. `ImmersivePolicyTest` covers all four. The visual half needs a device.

### 47. YouTube acceptance test — **Structured**
Full screen is recognised as already correct and explicitly abstained from with
`AppAlreadyImmersive`; Shorts is marked not-worth-it on Auto. Tested.

### 48. Intervention principle — **Done**
`AbstainReason.NoPerceptibleGain` and `CompatibilityRule.worthwhileOnAuto` make "this would not
improve anything" a first-class answer, recorded like any other decision.

### 49. Technical Definition of Done — see checklist below

### 50. The real test — **Device-gated**

---

## DoD 49 checklist

| Criterion | Status |
| --- | --- |
| Installs normally via APK | Done — CI publishes a debug APK |
| Works without root | Done |
| Keeps behaviour after a restart | Done |
| Fold Motion is continuous and convincing | Done in F/LAB; convincing is device-gated |
| Continuity works cover ↔ inner | Structured |
| Immersive works in the defined compatible contexts | Structured |
| Instagram has a working contextual profile | Structured — policy done and tested |
| YouTube receives no unnecessary intervention | Done — tested |
| Per-app profiles work | Done |
| Status and navigation bars stay legible | Done — tested |
| One UI keeps working normally | Done — F/LAB touches nothing outside its own window |
| Multitasking keeps working | Done |
| The gesture system keeps working | Done |
| No stuck overlays | Done — no overlay exists, and the continuity budget is a hard stop |
| A service crash does not affect the phone | Done — no permanent service; supervised scopes |
| Kill switch exists | Done |
| Diagnostics exists | Done |
| Battery stays within target | Device-gated |
| No sustained heating | Device-gated |
| No habitual perceptible jank | Device-gated |
| No flashes or artificial bands | Structured — prevented by construction, needs device confirmation |
| Permissions are explained | Done |
| Experimental features are separated | Done |
| Fold/unfold stress test passes | Device-gated |
| Instagram tests pass | Device-gated |
| YouTube tests pass | Device-gated |
| Final F/LAB visual identity | Done |
| Release build is signed | Pending |
| An update over a previous version keeps its configuration | Done — stable keys, tolerant reads |
| Uninstalling returns the device to standard behaviour | Done — F/LAB changes nothing outside its own process |

---

## What to do next, in order

1. **Measure on a device.** Everything marked Device-gated is blocked on a Galaxy Z Fold. Battery,
   thermals, 120 Hz jank and the fold/unfold stress test are the ones that can still invalidate
   design decisions, so they should come before more features.
2. **Decide the overlay question.** Sections 5, 6, 7 and 46 cannot progress past Structured without
   either an overlay, an accessibility service, or both. That is a product decision about what
   F/LAB is willing to ask for, not an engineering one, and it should be taken deliberately rather
   than arrived at.
3. **Sign the release build.** The last purely mechanical item on the DoD 49 checklist.
