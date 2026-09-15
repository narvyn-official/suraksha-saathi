package com.narvyn.suraksha

import org.junit.Assert.*
import org.junit.Test

class ArPlacementRequestTest {
    private fun accepts(request: ArPlacementRequest, revision: Int = request.revision, width: Int = request.width,
                        height: Int = request.height, now: Long = request.createdAt, active: Boolean = true,
                        tracked: Boolean = true, fresh: Boolean = true) =
        request.eligible(revision, width, height, now, active, tracked, fresh)

    @Test fun nativeCenterAndTouchRequestUseTheSameViewportCoordinates() {
        val center = ArPlacementRequest.createCenter(7, 641, 479, 100L)!!
        val touch = ArPlacementRequest.createAt(7, 320.5f, 239.5f, 641, 479, 100L)!!
        assertEquals(touch.revision, center.revision)
        assertEquals(touch.x, center.x, 0f); assertEquals(touch.y, center.y, 0f)
        assertEquals(touch.width, center.width); assertEquals(touch.height, center.height)
        assertEquals(touch.createdAt, center.createdAt)
        assertTrue(accepts(center)); assertTrue(accepts(touch))
        val smallest = ArPlacementRequest.createCenter(0, 1, 1, 1L)!!
        assertEquals(.5f, smallest.x, 0f); assertEquals(.5f, smallest.y, 0f)
        assertTrue(accepts(smallest))
    }

    @Test fun edgesOutsideAndNonFiniteCoordinatesCannotCreateRequests() {
        for (x in listOf(-1f, 0f, 640f, 641f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertNull(ArPlacementRequest.createAt(1, x, 100f, 640, 480, 100L))
        }
        for (y in listOf(-1f, 0f, 480f, 481f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertNull(ArPlacementRequest.createAt(1, 100f, y, 640, 480, 100L))
        }
        assertNotNull(ArPlacementRequest.createAt(1, .01f, .01f, 640, 480, 100L))
        assertNotNull(ArPlacementRequest.createAt(1, 639.99f, 479.99f, 640, 480, 100L))
    }

    @Test fun invalidViewportAndNonPositiveCreationTimeAreRejectedByBothFactories() {
        for ((width, height) in listOf(0 to 480, -1 to 480, 640 to 0, 640 to -1)) {
            assertNull(ArPlacementRequest.createAt(1, 1f, 1f, width, height, 100L))
            assertNull(ArPlacementRequest.createCenter(1, width, height, 100L))
        }
        for (time in listOf(0L, -1L, Long.MIN_VALUE)) {
            assertNull(ArPlacementRequest.createAt(1, 1f, 1f, 640, 480, time))
            assertNull(ArPlacementRequest.createCenter(1, 640, 480, time))
        }
    }

    @Test fun ageIsBoundedAndFutureOrOverflowLikeTimesCannotBeAccepted() {
        val request = ArPlacementRequest.createCenter(1, 640, 480, 100L)!!
        assertTrue(accepts(request, now = 100L)); assertTrue(accepts(request, now = 600L))
        assertFalse(accepts(request, now = 601L)); assertFalse(accepts(request, now = 99L))
        assertFalse(accepts(request, now = Long.MIN_VALUE)); assertFalse(accepts(request, now = Long.MAX_VALUE))
        val nearLimit = ArPlacementRequest.createCenter(1, 640, 480, Long.MAX_VALUE - 500L)!!
        assertTrue(accepts(nearLimit, now = Long.MAX_VALUE))
        assertFalse(accepts(nearLimit, now = Long.MIN_VALUE))
    }

    @Test fun revisionViewportAndEachRuntimePrerequisiteIndependentlyInvalidateTheIntent() {
        val request = ArPlacementRequest.createAt(-3, 140f, 200f, 640, 480, 100L)!!
        assertTrue(accepts(request)) // Revisions are opaque IDs, not positive counters.
        assertFalse(accepts(request, revision = -2))
        assertFalse(accepts(request, width = 641)); assertFalse(accepts(request, height = 481))
        assertFalse(accepts(request, width = 480, height = 640))
        assertFalse(accepts(request, active = false))
        assertFalse(accepts(request, tracked = false))
        assertFalse(accepts(request, fresh = false))
        assertTrue(accepts(request)) // Pure eligibility checks do not consume or mutate the request.
    }
}
