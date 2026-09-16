package com.narvyn.suraksha

import org.junit.Assert.*
import org.junit.Test

class ArPlacementCursorTest {
    @Test fun newTapInvalidatesAnOlderReadyFrameAndKeepsNormalizedPosition(){
        val c=ArPlacementCursor();val initial=c.snapshot(400,800)
        assertEquals(200f,initial.x,0f)
        assertTrue(c.select(100f,600f,400,800))
        assertFalse(c.isCurrent(initial.serial));var committed=false
        assertFalse(c.withCurrent(initial.serial){committed=true});assertFalse(committed)
        val next=c.snapshot(800,400);assertEquals(200f,next.x,0f);assertEquals(300f,next.y,0f)
        assertTrue(c.withCurrent(next.serial){committed=true});assertTrue(committed)
    }
    @Test fun invalidTouchesDoNotCreateTargetsAndPauseResetsToCentre(){
        val c=ArPlacementCursor();val original=c.snapshot(100,200)
        for(p in listOf(-1f to 0f,100f to 0f,1f to 200f,Float.NaN to 2f,2f to Float.POSITIVE_INFINITY))
            assertFalse(c.select(p.first,p.second,100,200))
        assertFalse(c.select(0f,0f,0,0));assertTrue(c.isCurrent(original.serial))
        c.select(20f,40f,100,200);val selected=c.snapshot(100,200);c.reset()
        assertFalse(c.isCurrent(selected.serial));assertEquals(50f,c.snapshot(100,200).x,0f)
    }
}
