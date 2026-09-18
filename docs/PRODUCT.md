# F/LAB product direction

F/LAB explores how a foldable Android device can feel more coherent, intentional and fluid while
keeping the strengths and the visual identity of the host system.

## Product principles

1. **Continuity first** — opening the device should reveal more context, not restart the task.
2. **Native performance** — no permanent services, no polling, no unnecessary background work.
3. **Progressive capability** — start with standard Android APIs, isolate anything that needs more
   into experiments, and explain the trade-off rather than burying it.
4. **F/LAB identity** — learn from strong interaction design without cloning another platform.
5. **Real-device evidence** — every experiment is measured on the target Fold.

## The governing question

Every feature has to answer one question, from DoD 48:

> Does this perceptibly improve the experience?

If the answer is no, F/LAB does not intervene. That is why abstaining is a first-class outcome in
the code: `AbstainReason` records *why* nothing happened, so "F/LAB decided this was not worth it"
is always distinguishable from "F/LAB is broken".

## Milestones

- **M0 — done.** Adaptive shell and cloud APK pipeline.
- **M1 — done.** The engine layer: Core, Fold Motion, Continuity, Immersive policy, App Profiles,
  compatibility rules, Diagnostics, and the app that controls them.
- **M2 — next.** Performance baseline on a real Fold: battery across the four DoD 25 scenarios,
  thermals, 120 Hz jank, and the DoD 36 fold/unfold stress test.
- **M3.** The overlay and accessibility decision — whether F/LAB is willing to ask for what
  sections 5, 6, 7 and 46 would require to move past their current state. A product decision, taken
  deliberately rather than arrived at.
