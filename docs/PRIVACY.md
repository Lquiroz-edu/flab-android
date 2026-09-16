# F/LAB privacy summary

F/LAB processes device posture, window dimensions, power-saver state and—only when the optional
App awareness service is enabled—the technical package name of the foreground application.
Processing happens locally on the device. F/LAB does not include a network permission and does not
transmit this information.

F/LAB does not retrieve accessibility node content, text, messages, passwords or user input and does
not perform gestures. If the user explicitly enables System Motion LAB, F/LAB takes one screenshot
at a fold-phase boundary, holds it only in memory as a non-interactive transition frame, and destroys
it at the endpoint or after a short timeout. It is never saved, analyzed or transmitted. Secure and
locally protected applications are skipped. A selected wallpaper image is copied into F/LAB's private
storage and can be removed with Reset F/LAB or uninstall.

Diagnostics reports are generated only when requested and omit screen content, account data,
hardware identifiers and foreground-app history. Android's share sheet gives the user final control
over where a report is sent.
