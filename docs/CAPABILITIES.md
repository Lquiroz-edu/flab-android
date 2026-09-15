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
