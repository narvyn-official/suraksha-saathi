package com.narvyn.suraksha

import org.junit.Assert.*
import org.junit.Test

class ArFrameGateTest {
    private fun tracking(g: ArFrameGate, ts: Long, at: Long) = g.observe(ts, at, true, "none")
    private fun ready(g: ArFrameGate) {
        g.resume(); tracking(g, 10, 100); tracking(g, 11, 150)
        assertTrue(tracking(g, 12, 200).ready)
    }
    @Test fun duplicatesNeverBuildRecoveryOrRefreshAge() {
        val g=ArFrameGate();g.resume()
        tracking(g,10,100)
        assertFalse(tracking(g,10,150).ready)
        assertFalse(tracking(g,10,250).ready)
        assertEquals(1,g.current(250).distinctFrames)
        assertFalse(tracking(g,10,251).ready)
        assertFalse(tracking(g,11,300).ready)
        assertFalse(tracking(g,12,350).ready)
        assertTrue(tracking(g,13,400).ready)
    }
    @Test fun callbackStallInvalidatesWithoutAnotherFrame() {
        val g=ArFrameGate();ready(g)
        assertTrue(g.current(350).ready)
        assertFalse(g.current(351).ready)
        assertEquals("stale-frame",g.current(351).reason)
        assertFalse(tracking(g,13,352).ready)
    }
    @Test fun lossAndResumeRequireNewStableFrames() {
        val g=ArFrameGate();ready(g)
        assertFalse(g.observe(13,220,false,"insufficient_features").ready)
        assertFalse(tracking(g,13,230).ready)
        assertFalse(tracking(g,14,250).ready)
        assertFalse(tracking(g,15,300).ready)
        assertTrue(tracking(g,16,350).ready)
        val epoch=g.current(350).generation
        g.pause();assertFalse(g.current(350).ready);g.resume()
        assertTrue(g.current(351).generation>epoch)
        assertFalse(tracking(g,16,351).ready)
        assertFalse(tracking(g,17,400).ready)
    }
    @Test fun oldFramesAndReversedClockCannotGrantCredit() {
        val g=ArFrameGate();ready(g)
        assertFalse(tracking(g,11,210).ready)
        assertFalse(tracking(g,13,199).ready)
        assertFalse(tracking(g,0,220).ready)
    }
    @Test fun cameraLeaseCannotBeStolenOrReleasedByAnotherOwner() {
        val lease=ArCameraLease();val a=Any();val b=Any()
        assertTrue(lease.acquire(a));assertTrue(lease.acquire(a))
        assertFalse(lease.acquire(b));lease.release(b);assertFalse(lease.acquire(b))
        lease.release(a);assertTrue(lease.acquire(b));assertFalse(lease.acquire(a))
    }
}
