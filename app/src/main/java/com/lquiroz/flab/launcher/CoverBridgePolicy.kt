package com.lquiroz.flab.launcher

import com.lquiroz.flab.motion.MotionChannels

/**
 * When F/LAB Home mirrors itself onto the *other* panel.
 *
 * Only while the hand-off is in flight. At rest closed the cover is already the main display and
 * needs no mirror; at rest open the cover faces away from the user and lighting it would be pure
 * battery cost. The window in between — the first half of the opening — is exactly when the Duo
 * shows both surfaces at once, and it is the only time a second panel is worth a presentation.
 */
object CoverBridgePolicy {
    fun shouldMirror(channels: MotionChannels): Boolean = channels.handoffAmount > 0f
}
