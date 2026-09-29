# Capability boundaries

## Available with standard Android APIs

- Draw edge-to-edge inside F/LAB.
- Adapt layouts when window size or fold posture changes.
- Preserve F/LAB state while opening or closing the device.
- Provide custom motion, haptics, widgets, wallpapers, and notifications owned by F/LAB.

## Requires an explicit opt-in experiment

- Drawing an overlay above other apps.
- Accessibility-powered observation or interaction.
- Becoming the default launcher.
- Shizuku or ADB-mediated privileged operations.

Each experiment must be isolated, explain its permissions, and include a measurable battery and performance budget.

## Not available to a normal third-party app

- Replacing another app's status bar treatment.
- Injecting animations into another app or One UI system transitions.
- Modifying protected System UI behavior without platform signing, root, or OEM cooperation.

## Evidence from the supplied Duo Fold Live source

The [reference review](DUO_FOLD_REFERENCE.md) documents a Shizuku-assisted Samsung wallpaper angle feed, privileged capture, overlays and mirrored previews over ordinary apps. This demonstrates a concrete implementation to investigate, rather than supported platform APIs or verified F/LAB capability. Compositing above an app does not inject code into it, control its status bar or guarantee its Activity continuity. The native panel handoff blackout remains a documented limitation. Device/firmware validation, opt-in setup, restoration and performance measurements are required before integration.
