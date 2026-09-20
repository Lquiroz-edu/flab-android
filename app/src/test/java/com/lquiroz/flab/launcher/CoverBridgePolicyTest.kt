package com.lquiroz.flab.launcher

import com.lquiroz.flab.motion.EvidenceSource
import com.lquiroz.flab.motion.FoldMotionFrame
import com.lquiroz.flab.motion.MotionChannelMapper
import com.lquiroz.flab.motion.MotionChannels
import com.lquiroz.flab.motion.MotionTuning
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverBridgePolicyTest {

    private fun at(progress: Float) = MotionChannelMapper.map(
        FoldMotionFrame(progress, 0f, 0f, settled = true, source = EvidenceSource.HingeAngle),
        MotionTuning.Balanced,
    )

    @Test
    fun `mirrors only while the hand-off is in flight`() {
        assertFalse("closed at rest: the cover is already the main display", CoverBridgePolicy.shouldMirror(at(0f)))
        assertTrue(CoverBridgePolicy.shouldMirror(at(0.1f)))
        assertTrue(CoverBridgePolicy.shouldMirror(at(MotionChannels.HANDOFF_PEAK_PROGRESS)))
        assertTrue(CoverBridgePolicy.shouldMirror(at(0.4f)))
        assertFalse("open: the cover faces away", CoverBridgePolicy.shouldMirror(at(0.6f)))
        assertFalse(CoverBridgePolicy.shouldMirror(at(1f)))
        assertFalse("nothing to mirror when the engine is idle", CoverBridgePolicy.shouldMirror(MotionChannels.Neutral))
    }
}
