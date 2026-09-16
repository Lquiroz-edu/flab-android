# F/LAB Fold Motion Lab 0.6

An installable Android 13+ live wallpaper that gives the Fold home screen a physical
page transition. It follows F/LAB's ivory, black and cobalt editorial direction and
supports a user-selected landscape.

## Permission and operation

Fold Motion needs no runtime, Accessibility, storage, overlay or network permission.
Android binds the wallpaper engine with `BIND_WALLPAPER`; the user applies it through the
system live-wallpaper picker. The separate System Motion LAB experiment declares an Accessibility
service and, only after explicit opt-in, can take one ephemeral screenshot at a fold-phase boundary.
It cannot retrieve accessibility nodes or perform gestures.

The renderer uses a GPU AGSL ray/glass model. Cover and inner panels receive separate progress
curves; the moving glass refracts a continuous source image and applies velocity-aware frost and
attenuation. It never draws a center line, artificial crease, curtain or hinge mask.
The central Core consumes the public hinge sensor. Continuous samples map directly to visual
progress so stopping the physical hinge also stops visual progress. Devices that expose only
coarse postures receive a disclosed, profile-dependent perceptual interpolation.
The engine runs only while the wallpaper is visible and unregisters the sensor otherwise.

Controls include manual preview, motion softness, corner radius, light/dark stage,
Fold response and a private image copied into app storage.

## Research references

- https://www.reddit.com/r/GalaxyFold/comments/1wd3swb/iphone_duo_animation_take_two/
- https://github.com/Atomicx7/Duo-animation
- https://github.com/marcoazeem/duo-open

The shader model is independently adapted from the MIT-licensed `duo-open` implementation. Full
attribution is in `THIRD_PARTY_NOTICES.md`. No MoomanJohnny source code is included.

## Limits and verification

The store-safe effect belongs to the wallpaper; One UI retains and draws icons, widgets and other
apps. System Motion LAB can briefly cover a compatible surface with a touch-transparent in-memory
frame, but it cannot replace Samsung's panel-power or unlock transition. Devices exposing only coarse posture
events receive a timed transition; F/LAB does not invent intermediate hinge angles.
No physical Samsung device is attached to CI.

Automated gate: geometry and posture tests, existing tests, lint and debug build.
Physical acceptance pending: wallpaper selection, fold/unfold on Home, Samsung renderer,
surface resize, idle behavior, frame time and battery. The debug APK is for personal
sideloading, not a Play Store release.
