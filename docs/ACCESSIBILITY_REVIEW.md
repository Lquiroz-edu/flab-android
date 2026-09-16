# Accessibility review declaration

## Core purpose

F/LAB improves motion and visual continuity on foldable Android devices without replacing the
launcher. Accessibility is optional and is not required for Fold Motion, the live wallpaper,
previews, settings or diagnostics.

## Data accessed

The optional `FLabAccessibilityService` receives window-state/window-change events and the technical
package name supplied with those events. Its metadata explicitly sets:

- `isAccessibilityTool=false`
- `canRetrieveWindowContent=false`
- `canTakeScreenshot=true` for the separately enabled System Motion LAB experiment
- `canPerformGestures=false`

It does not retrieve nodes, text, passwords, messages or user input. It does not perform taps,
gestures, navigation or automated actions. If System Motion LAB is enabled, it captures one screen
frame when a fold phase begins, keeps it only in memory while the hinge-driven overlay is visible,
and destroys it at the endpoint or after 720 ms without movement. It does not save, analyze, log or
transmit the frame. Android blocks secure windows, and F/LAB adds exclusions for sensitive packages.

## User benefit and operation

The current package name selects a conservative local compatibility rule. If the user separately
enables the Immersive experiment and explicitly opts an eligible app in, F/LAB may draw a short
non-focusable and non-touchable chromatic edge. Safe and critical apps never receive it.

System Motion LAB is a distinct opt-in. Its overlay is non-focusable, non-touchable, marked secure
and removed automatically. Preview and live wallpaper remain usable without Accessibility.

Before Android Settings is opened, F/LAB presents an in-app prominent disclosure describing the
data, purpose, optional status, non-use and revocation path. The user must affirmatively choose
`ACEPTO · ABRIR AJUSTES`. Android then retains the final system-controlled consent toggle.

## Distribution checklist

- Complete Google Play's AccessibilityService Permissions Declaration Form.
- Provide a review video showing disclosure, consent, profiles, Kill Switch and revocation.
- Repeat the disclosure in the store listing and privacy policy.
- Publish a signed AAB through Internal Testing before production review.
- Do not claim F/LAB is an accessibility tool for disabilities.
- Treat the LAB build as an internal test artifact until Google Play confirms that the declared use
  is eligible. A future public flavor must be able to exclude screenshot capability without changing Core.
