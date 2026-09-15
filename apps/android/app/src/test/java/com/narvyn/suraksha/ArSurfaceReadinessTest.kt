package com.narvyn.suraksha

import org.junit.Assert.*
import org.junit.Test

class ArSurfaceReadinessTest {
    private val key=Any()
    private fun sample(gate:ArSurfaceReadiness,frame:Long,now:Long,x:Float=0f,z:Float=0f,surface:Any=key,revision:Int=1)=gate.observe(surface,x,z,frame,now,revision)
    private fun ready(gate:ArSurfaceReadiness):ArSurfaceReadiness.Result {
        for(i in 0..3)assertFalse(sample(gate,i+1L,i*100L).ready)
        return sample(gate,5,450)
    }
    private fun empty(result:ArSurfaceReadiness.Result) { assertFalse(result.ready);assertEquals(0f,result.progress,0f) }

    @Test fun requiresBothDwellDurationAndDistinctImages() {
        val gate=ArSurfaceReadiness()
        empty(sample(gate,1,0))
        for(i in 1..3)assertFalse(sample(gate,i+1L,i*100L).ready)
        val early=sample(gate,5,449);assertFalse(early.ready);assertTrue(early.progress<1f)
        val complete=sample(gate,6,450);assertTrue(complete.ready);assertEquals(1f,complete.progress,0f)
    }
    @Test fun fourFramesOverEnoughTimeStillAreNotReady() {
        val gate=ArSurfaceReadiness()
        for(i in 0..3)assertFalse(sample(gate,i+1L,i*150L).ready)
        assertTrue(sample(gate,5,500).ready)
    }
    @Test fun rapidFramesCannotReplaceTheDwell() {
        val gate=ArSurfaceReadiness()
        for(i in 0..20) {
            val result=sample(gate,i+1L,i*5L)
            assertFalse(result.ready);assertTrue(result.progress<=100f/450f)
        }
    }
    @Test fun duplicatesNeitherAdvanceProgressNorRefreshAge() {
        val gate=ArSurfaceReadiness()
        empty(sample(gate,1,0));val progress=sample(gate,2,100)
        assertEquals(progress,sample(gate,2,150));assertEquals(progress,sample(gate,2,250))
        empty(sample(gate,2,251));empty(sample(gate,2,300))
        empty(sample(gate,3,310)) // Fresh image starts a new dwell, not a continuation of the old one.
        assertFalse(sample(gate,4,410).ready)
    }
    @Test fun readyCandidateExpiresEvenIfTheSameImageKeepsRendering() {
        val gate=ArSurfaceReadiness();assertTrue(ready(gate).ready)
        assertTrue(sample(gate,5,600).ready)
        empty(sample(gate,5,601));empty(sample(gate,5,650))
    }
    @Test fun freshImageAfterALargeGapStartsOver() {
        val gate=ArSurfaceReadiness()
        for(i in 0..3)sample(gate,i+1L,i*100L)
        empty(sample(gate,5,451))
        assertFalse(sample(gate,6,550).ready)
        val exact=ArSurfaceReadiness()
        for(i in 0..3)assertFalse(sample(exact,i+1L,i*150L).ready)
        assertTrue(sample(exact,5,600).ready)
    }
    @Test fun cumulativeDriftIsMeasuredFromFirstPoint() {
        val gate=ArSurfaceReadiness()
        sample(gate,1,0);sample(gate,2,100,x=.06f);sample(gate,3,200,x=.11f)
        empty(sample(gate,4,300,x=.16f)) // Each small step alone is below the radius, but total drift is not.
        assertFalse(sample(gate,5,450,x=.16f).ready)
    }
    @Test fun radiusUsesBothAxesAndIncludesBoundary() {
        val diagonal=ArSurfaceReadiness();sample(diagonal,1,0)
        empty(sample(diagonal,2,100,x=.10f,z=.10f))
        val boundary=ArSurfaceReadiness();sample(boundary,1,0)
        for(i in 1..3)sample(boundary,i+1L,i*100L,x=ArSurfaceReadiness.RADIUS_METRES)
        assertTrue(sample(boundary,5,450,x=ArSurfaceReadiness.RADIUS_METRES).ready)
    }
    @Test fun equalTrackableWrappersRetainDwellButAnotherTrackableOrSceneResets() {
        // Like ARCore Plane wrappers, different objects can identify one underlying native trackable.
        data class Plane(val nativeId:Int)
        val first=Plane(1);val replacement=Plane(1);assertEquals(first,replacement);assertNotSame(first,replacement)
        val gate=ArSurfaceReadiness()
        empty(sample(gate,1,0,surface=first))
        for(i in 1..3)assertFalse(sample(gate,i+1L,i*100L,surface=Plane(1)).ready)
        assertTrue(sample(gate,5,450,surface=replacement).ready)
        empty(sample(gate,6,550,surface=Plane(2)))
        for(i in 0..2)assertFalse(sample(gate,i+7L,650L+i*100L,surface=Plane(2)).ready)
        assertTrue(sample(gate,10,1000,surface=Plane(2)).ready)
        empty(sample(gate,11,1100,surface=Plane(2),revision=2))
        assertFalse(sample(gate,12,1200,surface=Plane(2),revision=2).ready)
    }
    @Test fun changedCandidateOnDuplicateImageCannotSeedANewWindow() {
        val gate=ArSurfaceReadiness();sample(gate,1,0);sample(gate,2,100)
        empty(sample(gate,2,110,x=.2f));empty(sample(gate,2,120,x=.2f))
        empty(sample(gate,3,130,x=.2f))
    }
    @Test fun invalidCoordinatesAndTimestampsClearEligibility() {
        for(invalid in listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY)) {
            val gate=ArSurfaceReadiness();assertTrue(ready(gate).ready)
            empty(sample(gate,6,500,x=invalid));empty(sample(gate,6,510))
            empty(sample(gate,7,520,z=invalid));empty(sample(gate,8,530))
        }
        val gate=ArSurfaceReadiness();assertTrue(ready(gate).ready)
        empty(sample(gate,0,500));empty(sample(gate,5,510));empty(sample(gate,6,520))
        empty(sample(gate,7,-1));empty(sample(gate,8,530))
    }
    @Test fun regressingImageOrElapsedClockCannotRetainReadiness() {
        val image=ArSurfaceReadiness();assertTrue(ready(image).ready)
        empty(sample(image,4,500));empty(sample(image,4,510));empty(sample(image,5,520))
        assertFalse(sample(image,6,620).ready)
        val clock=ArSurfaceReadiness();assertTrue(ready(clock).ready)
        empty(sample(clock,6,449));empty(sample(clock,7,460))
    }
    @Test fun explicitResetOnNoHitDoesNotMakeAFrozenImageFreshAgain() {
        val gate=ArSurfaceReadiness();assertTrue(ready(gate).ready)
        gate.reset();empty(sample(gate,5,460));empty(sample(gate,6,470))
        for(i in 0..2)assertFalse(sample(gate,7L+i,570L+i*100L).ready)
        assertTrue(sample(gate,10,920).ready)
    }
}
