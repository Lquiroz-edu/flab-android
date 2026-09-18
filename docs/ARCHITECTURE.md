# Architecture

One Gradle module, `:app`, with packages that are drawn where the Gradle module boundaries would
go. Early modularization would buy nothing here and would cost configuration time on every cloud
build; the package boundaries are real and enforced by dependency direction, so extracting them
later is mechanical.

## The rule everything else follows

**The Core owns the state. Modules read it. Nothing else detects anything.**

`FLabCore` holds a single `StateFlow<FLabState>` carrying posture, continuous fold progress, hinge
angle, orientation, window size, active display, presentation mode, foreground package, engine
status, per-module state, profile and power posture.

No module opens its own `WindowInfoTracker`, registers its own sensor listener, or keeps a private
copy of the posture. That is what lets a `closed → part open → open → part closed → closed` session
run without restarting anything, and it is what makes the state reproducible in a unit test.

## Packages

| Package | Depends on | Holds |
| --- | --- | --- |
| `motion` | nothing | Fold Motion. Pure Kotlin: the interpolator, tuning, channel mapping. |
| `continuity` | `core` (models only) | The cover ↔ inner bridge state machine. Pure Kotlin. |
| `immersive` | `core`, `compat`, `profiles` | Context model, decision policy, status bar contrast. Pure Kotlin. |
| `compat` | `immersive` (models), `profiles` | The rule table and config layering. Pure Kotlin. |
| `profiles` | `motion` | Global and per-app profiles, and the safe-app policy. Pure Kotlin. |
| `diagnostics` | `core` | Snapshot model and the redacted debug report. Pure Kotlin. |
| `core` | all of the above | The engine. The only package that touches Android framework classes. |
| `settings` | `core`, `profiles` | Persistence. |
| `ui` | everything | Compose. |

Everything above `core` in that table is framework-free and unit-testable without Robolectric,
which is why the DoD's falsifiable claims — motion stops when the device stops, a protected app is
never treated, an unknown rule means no treatment — are covered by ordinary JVM tests.

## Threading and cost

Every subscription is event-driven. There is no timer, no poll and no wake lock anywhere in the
codebase.

The hinge-angle listener is the only high-rate source, and it is registered only while a transition
is in flight. `FoldMotionHost` drives `while (engine.needsFrames)`, not `while (true)`, so a still
device schedules no frame callbacks at all. A frame whose channels are neutral takes a fast path
with no graphics layer, no blur pass and no scrim.

The Core lives on the `Application`, not an Activity, because the fold cycle can destroy Activities
exactly when the state matters most. Activities attach and detach around `STARTED`.

## Motion, specifically

The Fold Motion engine interpolates towards physical evidence rather than playing an animation:

- `Sensor.TYPE_HINGE_ANGLE` where the device has it, giving genuinely continuous input.
- `FoldingFeature.State` transitions otherwise, which the interpolator fills between — between
  known samples, never running ahead of the last one.

Progress travels only towards the most recent evidence, at a bounded speed, and settles once the
evidence goes stale. Stopping the device therefore stops the motion, and a `HALF_OPENED` event is a
target of 0.5, not a trigger for a 0 → 1 animation.

Visual channels split in two, and the split is load-bearing:

- **Position channels** (scale, expansion, offset) follow progress. Stable when the device is still.
- **Veil channels** (blur, dim, alpha, elevation) follow *movement*. Zero when the device is still,
  including at half-open, which is a resting posture rather than a permanent transition.

There is no seam, crease, hinge-line, mask or curtain channel, and there must never be one.

## Future module boundaries

When a second production feature lands, extract `core:fold`, `core:designsystem` and independent
`feature:*` modules along the package lines above.
