# F/LAB product direction

F/LAB explores how foldable Android devices can feel more coherent, intentional, and fluid while keeping the strengths and visual identity of the host system.

## Product principles

1. **Continuity first** — opening the device should reveal more context, not restart the task.
2. **Native performance** — avoid permanent services and unnecessary work in the background.
3. **Progressive capability** — start with standard Android APIs, isolate experimental permissions, and explain trade-offs.
4. **F/LAB identity** — learn from strong interaction design without cloning another platform.
5. **Real-device evidence** — every experiment is measured and tested on the target Fold.

## Milestones

- M0: adaptive shell and cloud APK pipeline.
- M1: continuity and opening/closing motion prototype.
- M2: performance baseline and device telemetry.
- M3: opt-in experiments that interact with broader system surfaces.

## Reference informing the next experiments

The [Duo Fold Live adaptation plan](DUO_FOLD_REFERENCE.md) adds concrete evidence for the system-wide fold-animation direction. Start with a continuous-angle capability probe on the target device, then a single session model, local rendering and opt-in system overlays. Preserve F/LAB identity and measure battery/frame-time costs. A visual fade is not proof of zero-blackout handoff or universal third-party app continuity. The source import itself does not deliver these runtime features.
