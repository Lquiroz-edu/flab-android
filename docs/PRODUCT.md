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

## V1 acceptance criteria

F/LAB V1 focuses only on motion continuity inside the app:

- The selected moment, playback state, and progress survive Cover ↔ Inner transitions.
- The Cover surface prioritizes one thought and one action.
- The Inner surface keeps the primary scene anchored while revealing navigation and context.
- Opening, closing, rotation, and Flex posture never require restarting the task.
- Motion runs only while F/LAB is visible and playback is active.
- No overlay, accessibility service, launcher replacement, or root permission is required.

## Duo transition model

The reference effect is not a cross-fade between unrelated layouts. F/LAB models one shared
panoramic canvas:

- Cover renders a centered crop of the virtual canvas.
- Inner reveals the missing sides without stretching the focal content.
- The software settle is intentionally short; the physical hinge supplies most of the motion.
- A live hinge-angle sensor drives crease depth when the device exposes it, with WindowManager
  as the fallback.
- The center crease uses light and shadow that diminish as the device reaches 180 degrees.
