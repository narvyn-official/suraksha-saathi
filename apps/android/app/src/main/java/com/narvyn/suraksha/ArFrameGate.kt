package com.narvyn.suraksha

/** Pure runtime policy shared by every camera exercise. Times are monotonic receipt times. */
class ArFrameGate {
    data class Snapshot(val generation: Long, val timestamp: Long, val receivedAt: Long,
                        val ready: Boolean, val reason: String, val distinctFrames: Int)
    private var generation = 0L
    private var active = false
    private var timestamp = 0L
    private var receivedAt = 0L
    private var lastObservedAt = 0L
    private var stableSince: Long? = null
    private var count = 0
    private var reason = "not-started"

    @Synchronized fun resume() { generation++; active = true; reset("starting") }
    @Synchronized fun pause() { generation++; active = false; reset("paused") }
    private fun reset(why: String) { count = 0; stableSince = null; reason = why }

    @Synchronized fun observe(frameTimestamp: Long, now: Long, tracking: Boolean, failure: String): Snapshot {
        if (!active) return current(now)
        if (now < 0 || now < lastObservedAt) { reset("clock-invalid"); return current(now) }
        lastObservedAt = now
        if (frameTimestamp <= 0 || frameTimestamp < timestamp) {
            reset("invalid-frame"); return current(now)
        }
        val distinct = frameTimestamp > timestamp
        if (distinct) {
            if (timestamp > 0 && now - receivedAt > MAX_AGE_MS) reset("frame-gap")
            timestamp = frameTimestamp; receivedAt = now
        }
        if (!tracking) { reset(failure); return current(now) }
        if (distinct) {
            if (stableSince == null) stableSince = now
            count++
            reason = if (count >= MIN_FRAMES && now - stableSince!! >= STABLE_MS) "tracking" else "recovering"
        }
        return current(now)
    }

    @Synchronized fun current(now: Long): Snapshot {
        val fresh = now >= receivedAt && now - receivedAt <= MAX_AGE_MS
        if (active && timestamp > 0 && !fresh) reset("stale-frame")
        return Snapshot(generation, timestamp, receivedAt,
            active && fresh && reason == "tracking", reason, count)
    }

    companion object {
        const val MAX_AGE_MS = 150L
        const val STABLE_MS = 100L
        const val MIN_FRAMES = 3
    }
}

/** Prevent even transient overlapping camera ownership when activities change. */
class ArCameraLease {
    private var owner: Any? = null
    @Synchronized fun acquire(candidate: Any): Boolean {
        if (owner != null && owner !== candidate) return false
        owner = candidate; return true
    }
    @Synchronized fun release(candidate: Any) { if (owner === candidate) owner = null }
}
