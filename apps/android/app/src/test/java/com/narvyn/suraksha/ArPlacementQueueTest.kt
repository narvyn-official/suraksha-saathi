package com.narvyn.suraksha

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class ArPlacementQueueTest {
    private fun request(revision: Int) = ArPlacementRequest.createCenter(revision, 640, 480, 100L)!!

    @Test fun olderFrameCannotConsumeOrDiscardTheNewerSceneRequest() {
        val queue = ArPlacementQueue()
        queue.offer(request(1))
        val newer = request(2)
        queue.offer(newer)
        assertNull(queue.consumeFor(1))
        queue.discardFor(1)
        assertSame(newer, queue.consumeFor(2))
    }

    @Test fun matchingFrameConsumesAtMostOnceAndCanDiscardItsOwnIntent() {
        val queue = ArPlacementQueue()
        val first = request(3)
        queue.offer(first)
        assertSame(first, queue.consumeFor(3))
        assertNull(queue.consumeFor(3))
        queue.offer(request(3))
        queue.discardFor(3)
        assertNull(queue.consumeFor(3))
    }

    @Test fun latestValidOfferReplacesPendingWhileNullDoesNotAndClearRemovesIt() {
        val queue = ArPlacementQueue()
        queue.offer(request(4))
        val replacement = request(4)
        queue.offer(replacement)
        queue.offer(null)
        assertSame(replacement, queue.consumeFor(4))
        queue.offer(request(5))
        queue.clear()
        assertNull(queue.consumeFor(5))
        val afterClear = request(6)
        queue.offer(afterClear)
        assertSame(afterClear, queue.consumeFor(6))
    }

    @Test fun concurrentConsumersCannotBothReceiveTheSameRequest() {
        val queue = ArPlacementQueue()
        val offered = request(7)
        queue.offer(offered)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = (1..2).map {
                pool.submit<ArPlacementRequest?> {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    queue.consumeFor(7)
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            val received = results.map { it.get(5, TimeUnit.SECONDS) }.filterNotNull()
            assertEquals(1, received.size)
            assertSame(offered, received.single())
            assertNull(queue.consumeFor(7))
        } finally {
            start.countDown()
            pool.shutdownNow()
        }
    }
}
