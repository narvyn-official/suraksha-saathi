package com.narvyn.suraksha

import java.util.concurrent.atomic.AtomicReference

/** Latest valid placement intent. An older rendering frame cannot remove a newer scene's request. */
class ArPlacementQueue {
    private val pending = AtomicReference<ArPlacementRequest?>(null)

    fun offer(request: ArPlacementRequest?) {
        if (request != null) pending.set(request)
    }

    fun consumeFor(revision: Int): ArPlacementRequest? {
        while (true) {
            val request = pending.get() ?: return null
            if (request.revision != revision) return null
            if (pending.compareAndSet(request, null)) return request
        }
    }

    fun discardFor(revision: Int) { consumeFor(revision) }
    fun clear() { pending.set(null) }
}
