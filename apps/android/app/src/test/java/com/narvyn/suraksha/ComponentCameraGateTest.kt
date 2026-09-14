package com.narvyn.suraksha

import org.junit.Assert.*
import org.junit.Test

class ComponentCameraGateTest {
    @Test fun onlyFreshActiveReadyFramesAllowInteraction() {
        val g=ComponentCameraGate(); val v=g.configure()
        g.frame(v,true,100); assertFalse(g.allows(v,100))
        g.activate(); assertFalse(g.allows(v,100))
        g.frame(v,true,200); assertTrue(g.allows(v,200)); assertTrue(g.allows(v,700))
        assertFalse(g.allows(v,701)); assertFalse(g.allows(v,199))
        g.frame(v,false,220); assertFalse(g.allows(v,220))
    }
    @Test fun pauseAndConfigurationRejectStaleFramesAndChoices() {
        val g=ComponentCameraGate(); val old=g.configure();g.activate();g.frame(old,true,100)
        val next=g.configure();assertFalse(g.allows(old,100));g.frame(old,true,101);assertFalse(g.allows(next,101))
        g.frame(next,true,102);assertTrue(g.allows(next,102))
        g.pause();g.frame(next,true,103);g.activate();assertFalse(g.allows(next,103))
        val resumed=g.configure();g.frame(resumed,true,104);assertTrue(g.allows(resumed,104))
    }
    @Test fun repeatedCameraImagesCannotRenewEligibility() {
        val f=ComponentCameraFreshness();val g=ComponentCameraGate();val v=g.configure();g.activate()
        assertNull(f.observedAt(0,100))
        assertEquals(100L,f.observedAt(10,100))
        val repeated=f.observedAt(10,590)!!;assertEquals(100L,repeated)
        g.frame(v,true,repeated);assertTrue(g.allows(v,600));assertFalse(g.allows(v,601))
        assertNull(f.observedAt(10,601));assertNull(f.observedAt(9,602))
        assertEquals(603L,f.observedAt(11,603));assertNull(f.observedAt(11,602))
        f.requireNewImage();assertNull(f.observedAt(11,604));assertEquals(605L,f.observedAt(12,605))
        f.requireNewImage();assertNull(f.observedAt(12,2000));assertEquals(2001L,f.observedAt(13,2001))
        f.clear();assertEquals(2100L,f.observedAt(1,2100))
    }
    @Test fun viewingConeAllowsBothStationaryStagesAndRejectsUnreadableAngles() {
        assertTrue(ComponentCameraGeometry.readableView(0f,.35f,1.6f,20f))
        assertTrue(ComponentCameraGeometry.readableView(0f,.35f,1.6f,-20f))
        assertTrue(ComponentCameraGeometry.readableView(1.6f,.35f,0f,90f))
        assertFalse(ComponentCameraGeometry.readableView(0f,.35f,1.6f,90f))
        assertFalse(ComponentCameraGeometry.readableView(0f,.35f,-1.6f,0f))
        assertFalse(ComponentCameraGeometry.readableView(0f,2f,1f,0f))
        assertFalse(ComponentCameraGeometry.readableView(0f,-.5f,1f,0f))
        assertFalse(ComponentCameraGeometry.readableView(0f,0f,.1f,0f))
        assertFalse(ComponentCameraGeometry.readableView(Float.NaN,0f,1f,0f))
    }
}
