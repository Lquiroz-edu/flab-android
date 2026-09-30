# F/LAB 0.8.0 — Duo Global / Shizuku

The imported Duo Fold Live 3.5.2 engine is an Android library in `duo-engine`.
The original namespace and licenses are retained. F/LAB opens its setup and settings
from Fold Motion → Duo Global. This is a global accessibility overlay, with locally
captured non-protected screen layers provided by an authorized Shizuku helper.
It operates over Home and compatible applications, not only the F/LAB preview.
Secure windows, the lock screen, OEM display policy and Samsung's native black
handoff frames impose limits. This build does not claim to eliminate those frames.

## Install / setup
1. Install this APK over the 0.7.0 test (same package and CI debug signing key).
2. Start Shizuku in ADB mode through wireless debugging and authorize F/LAB.
3. Open F/LAB → Fold Motion → Configurar efecto global. Complete the wizard:
   Samsung wallpaper angle source on both panels, overlay permission, notification
   permission, and accessibility service **F/LAB · Duo Global**. Wallpaper changes
   are explicit wizard actions. Retain your original wallpaper before changing it.
4. Start with 60 FPS. Advanced renderer settings offer 30/60/120 FPS. Avoid running
   the friend's original app and F/LAB's old overlay simultaneously.
5. Close F/LAB, fold/unfold over Home and several non-secure apps. Check rotation,
   touch pass-through, brightness and both panels. Repeat 20 cycles in each direction.
6. Lock or turn off the screen: capture pauses and the angle reader stops after
   sustained sleep (three seconds). Unlock resumes it. Transient panel handoffs
   retain a short grace period.
7. Stop with the global toggle or notification; F/LAB's main OFF action also stops
   the global effect. Confirm the overlay disappears, foreground service stops and
   the previous Samsung fold policy returns. If Shizuku disconnected, restoration
   remains journaled and retries on the next F/LAB launch with Shizuku authorized.

## Implemented cost controls
- 60 FPS capture default; small captures, texture filtering, supersampling off.
- Completion-paced Binder polling: 8 ms moving / 32 ms still; power saver 16/64 ms.
  Urgent handoff completion can request an immediate poll; no concurrent queue.
- Capture follows selected rate during motion, capped at 15 FPS when still;
  power saver or moderate thermal throttling caps motion at 30 FPS and still at 8.
- Capture clients release at settled endpoints; discarded obsolete frames recycle.
- Surface refresh vote follows selected FPS and releases to 0 when inactive.
- No permanent keep-awake supervision while OFF; no periodic sleep polling after
  sustained screen-off. Support reminder scheduling disabled; attribution retained.
- Old F/LAB overlay is disabled when Duo Global starts; activating the old overlay
  stops Duo Global, keeping a single compositor in charge.

The rate defaults and caps are policy limits, not measured device FPS. CI validates
unit tests, lint and compilation. Battery use, frame pacing and firmware-specific
hidden API behavior require measurements on the physical Fold. Do not claim this
is the fastest possible implementation without those results.
