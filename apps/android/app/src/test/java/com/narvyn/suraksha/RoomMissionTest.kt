package com.narvyn.suraksha

import org.junit.Assert.*
import org.junit.Test

class RoomMissionTest {
    private fun aiming(): RoomMission = RoomMission("fire").apply {
        assertTrue(act("alarm", 10)); assertTrue(act("pin-drag", 20))
    }
    private fun sweeping(): RoomMission = aiming().apply {
        for (t in 100L..400L step 100) aim(0f, 0f, false, t, true)
        assertEquals("SWEEP", phase)
    }
    private fun sweep(mission: RoomMission, start: Long = 500, direction: Int = 1): Boolean {
        var completed = false
        for (i in 0..15) completed = mission.aim(direction * (-1f + 2f * i / 15f), 0f, true, start + i * 100, true) || completed
        return completed
    }

    @Test fun intentionalControlsAreOrderedAndUnsafeAttemptsDoNotAdvance() {
        val m = RoomMission("fire")
        assertFalse(m.act("pin-drag", 1)); assertFalse(m.act("continue-discharge", 2))
        assertEquals("ALARM", m.phase); assertEquals(2, m.events.size)
        assertFalse(m.events[0].accepted); assertEquals("unsafe-control", m.events[1].reason)
        assertFalse(m.act("invented", 3)); assertEquals(2, m.events.size)
        assertTrue(m.act("alarm", 4)); assertFalse(m.act("alarm", 5))
        assertEquals("PIN", m.phase)
        assertFalse(m.act("pin-drag", -1)); assertFalse(m.act("pin-drag", 3))
        assertTrue(m.act("pin-drag", 6)); assertEquals("AIM", m.phase)
    }

    @Test fun alignmentNeedsContinuousFreshBaseCentredFramesForThreeHundredMs() {
        val m = aiming()
        assertFalse(m.aim(0f, 0f, false, 100, true))
        assertFalse(m.aim(.2f, .25f, false, 250, true))
        assertEquals(.5f, m.progress, .0001f)
        assertTrue(m.aim(0f, 0f, false, 400, true))
        assertEquals("SWEEP", m.phase)
        val proof = m.measurements.single()
        assertEquals(300L, proof.durationMs); assertEquals(150L, proof.maxGapMs)
        assertEquals("base-alignment", proof.kind)
    }

    @Test fun invalidCachedDuplicateOrGappedAlignmentCannotAccumulateCredit() {
        for (failure in 0..7) {
            val m = aiming()
            m.aim(0f, 0f, false, 100, true); m.aim(0f, 0f, false, 200, true)
            when (failure) {
                0 -> m.aim(0f, 0f, false, 250, false)
                1 -> m.aim(Float.NaN, 0f, false, 250, true)
                2 -> m.aim(0f, Float.POSITIVE_INFINITY, false, 250, true)
                3 -> m.aim(.21f, 0f, false, 250, true)
                4 -> m.aim(0f, -.251f, false, 250, true)
                5 -> m.aim(0f, 0f, false, 200, true)
                6 -> m.aim(0f, 0f, false, 199, true)
                else -> m.resetIncomplete()
            }
            assertEquals(0f, m.progress, 0f)
            assertFalse(m.aim(0f, 0f, false, 300, true))
            assertFalse(m.aim(0f, 0f, false, 400, true))
            assertFalse(m.aim(0f, 0f, false, 551, true)) // gap restarts dwell
            assertEquals("AIM", m.phase); assertTrue(m.measurements.isEmpty())
        }
    }

    @Test fun sweepNeedsFiveObservedBinsAndEnoughTimeInEitherDirection() {
        for (direction in listOf(-1, 1)) {
            val m = sweeping()
            assertTrue(sweep(m, direction = direction))
            assertEquals("WITHDRAW", m.phase)
            val measured = m.measurements.last()
            assertEquals("base-sweep", measured.kind); assertEquals(1500L, measured.durationMs)
            assertEquals(if (direction == 1) listOf(0, 1, 2, 3, 4) else listOf(4, 3, 2, 1, 0), measured.bins)
            assertEquals(100L, measured.maxGapMs)
        }
        val m = sweeping()
        for (i in 0..15) assertFalse(m.aim(-1f, 0f, true, 500L + i * 100, true))
        assertEquals("SWEEP", m.phase); assertTrue(m.progress < 1f)
    }

    @Test fun sweepRejectsShortcutsAndRestartsAfterLossOfInputOrFreshness() {
        for (failure in 0..7) {
            val m = sweeping()
            for (i in 0..8) m.aim(-1f + i * .1f, 0f, true, 500L + i * 100, true)
            when (failure) {
                0 -> m.aim(.8f, 0f, true, 1400, true) // skips an unobserved bin
                1 -> m.aim(0f, 0f, false, 1400, true)
                2 -> m.aim(0f, 0f, true, 1400, false)
                3 -> m.aim(0f, .26f, true, 1400, true)
                4 -> m.aim(2f, 0f, true, 1400, true)
                5 -> m.aim(0f, 0f, true, 1451, true)
                6 -> m.act("release", 1400)
                else -> m.resetIncomplete()
            }
            assertEquals("SWEEP", m.phase); assertEquals(0f, m.progress, 0f)
            assertFalse(m.aim(0f, 0f, true, 1500, true)) // cannot start in the middle
            assertTrue(sweep(m, start = 1600)); assertEquals("WITHDRAW", m.phase)
        }
        val fast = sweeping()
        for (i in 0..4) assertFalse(fast.aim(-1f + i * .5f, 0f, true, 500L + i * 100, true))
        assertEquals("SWEEP", fast.phase)
    }

    @Test fun worseningConditionsRequireExplicitReleaseThenWithdrawal() {
        val m = sweeping(); sweep(m)
        assertFalse(m.act("withdraw", 2100)); assertEquals("release-required", m.events.last().reason)
        assertFalse(m.aim(0f, 0f, false, 2200, true)) // releasing a frame alone is not the intentional control
        assertTrue(m.act("release", 2300)); assertEquals(.5f, m.progress, 0f)
        m.resetIncomplete(); assertEquals("WITHDRAW", m.phase); assertEquals(.5f, m.progress, 0f)
        assertFalse(m.act("release", 2400)); assertTrue(m.act("withdraw", 2500))
        assertTrue(m.completed); assertEquals(1f, m.overallProgress, 0f)
        val final = m.toJson().toString()
        assertFalse(m.act("alarm", 2600)); assertFalse(m.aim(0f, 0f, true, 2700, true))
        m.resetIncomplete(); assertEquals(final, m.toJson().toString())
    }

    @Test fun staleTimesCannotReleaseOrCompleteAndRepeatedFramesResetASweep() {
        val m = sweeping()
        val initialEvents = m.events.size
        assertFalse(m.act("release", 399)); assertEquals(initialEvents, m.events.size)
        assertFalse(m.act("unknown", Long.MAX_VALUE)) // ignored input cannot poison the clock
        for (i in 0..4) m.aim(-1f + i * .1f, 0f, true, 500L + i * 100, true)
        assertTrue(m.progress > 0f)
        assertFalse(m.aim(-.6f, 0f, true, 900, true))
        assertEquals(0f, m.progress, 0f)
        assertFalse(m.act("release", 899)); assertEquals(initialEvents, m.events.size)
        assertFalse(m.aim(-1f, 0f, true, 800, true))
        assertEquals(0f, m.progress, 0f)
        assertTrue(sweep(m, start = 1000))
        assertEquals(2, m.measurements.size)
        assertFalse(m.act("release", 2499))
        assertFalse(m.act("withdraw", 2501)); assertEquals("WITHDRAW", m.phase)
        assertTrue(m.act("release", 2502)); assertTrue(m.act("withdraw", 2503))
        assertEquals(1, m.events.count { it.id == "withdraw" && it.accepted })
        assertFalse(m.act("withdraw", 2504))
        assertEquals(1, m.events.count { it.id == "withdraw" && it.accepted })
    }

    @Test fun screenDwellWhilePausedCannotBecomeAccumulatedMissionProgress() {
        val m = aiming()
        m.aim(0f, 0f, false, 100, true); m.aim(0f, 0f, false, 250, true)
        m.resetIncomplete()
        assertEquals("AIM", m.phase)
        for (t in 1000L..1200L step 100) assertFalse(m.aim(0f, 0f, false, t, true))
        assertTrue(m.aim(0f, 0f, false, 1300, true))
        assertEquals(1000L, m.measurements.single().startedAt)
        for (i in 0..8) m.aim(-1f + i * .1f, 0f, true, 1400L + i * 100, true)
        m.resetIncomplete()
        assertEquals("SWEEP", m.phase); assertEquals(1, m.measurements.size)
        assertTrue(sweep(m, start = 3000))
        assertEquals(3000L, m.measurements.last().startedAt)
    }

    @Test fun gasMissionStaysOutsideAndNeverInterpretsSimAsAReading() {
        val m = RoomMission("gas")
        assertFalse(m.aim(0f, 0f, true, 0, true))
        assertTrue(m.act("inspect-meter", 1)); assertEquals("BARRIER", m.phase)
        assertFalse(m.act("enter", 2)); assertEquals("BARRIER", m.phase)
        assertTrue(m.act("close-barrier", 3)); assertEquals("ATTENDANT", m.phase)
        assertFalse(m.act("place-attendant-inside", 4))
        assertTrue(m.act("place-attendant-outside", 5)); assertEquals("REFUSE", m.phase)
        m.resetIncomplete(); assertEquals("REFUSE", m.phase)
        assertTrue(m.act("refuse-entry", 6)); assertTrue(m.completed)
        val data = m.toJson(); val scenario = data.getJSONObject("scenario")
        assertEquals("SIM", scenario.getString("meter")); assertFalse(scenario.getBoolean("liveReading"))
        assertEquals("unconfirmed", scenario.getString("rescueReadiness")); assertFalse(scenario.getBoolean("entryAuthorised"))
        assertEquals("outside-entry-refused", data.getJSONObject("result").getString("outcome"))
    }

    @Test fun auditSnapshotDoesNotExposeMutableStateOrCertificationCredit() {
        val m = sweeping(); sweep(m)
        val snapshot = m.toJson()
        assertEquals(1, snapshot.getInt("version")); assertEquals("fire", snapshot.getString("module"))
        assertEquals(2, snapshot.getJSONArray("measurements").length())
        assertFalse(snapshot.getJSONObject("result").getBoolean("certifiable"))
        assertEquals("not-assessed", snapshot.getJSONObject("result").getString("practical"))
        snapshot.put("phase", "COMPLETE"); snapshot.getJSONArray("events").put("fabricated")
        assertEquals("WITHDRAW", m.phase); assertFalse(m.completed)
        val phaseProgress = m.overallProgress
        m.resetIncomplete(); assertEquals(phaseProgress, m.overallProgress, 0f)
    }

    @Test fun transactionalForkCopiesPartialTimingBinsHistoryAndReleaseWithoutSharingState() {
        val m = aiming()
        m.aim(0f, 0f, false, 100, true); m.aim(0f, 0f, false, 250, true)
        val aligned = m.fork()
        assertEquals(m.toJson().toString(), aligned.toJson().toString())
        assertTrue(aligned.aim(0f, 0f, false, 400, true))
        assertEquals("AIM", m.phase); assertTrue(m.measurements.isEmpty())
        assertTrue(m.aim(0f, 0f, false, 400, true))
        assertEquals(m.toJson().toString(), aligned.toJson().toString())
        for (i in 0..8) m.aim(-1f + 2f * i / 15f, 0f, true, 500L + i * 100, true)
        val same = m.fork(); val reset = m.fork()
        reset.resetIncomplete()
        assertEquals(0f, reset.progress, 0f); assertTrue(m.progress > 0f)
        assertFalse(same.act("release", 1299)) // copied clock rejects stale control
        for (i in 9..15) {
            val now = 500L + i * 100
            assertEquals(m.aim(-1f + 2f * i / 15f, 0f, true, now, true), same.aim(-1f + 2f * i / 15f, 0f, true, now, true))
            assertFalse(reset.aim(-1f + 2f * i / 15f, 0f, true, now, true))
        }
        assertEquals(m.toJson().toString(), same.toJson().toString())
        assertEquals("WITHDRAW", m.phase); assertEquals("SWEEP", reset.phase)
        assertTrue(same.act("release", 2100)); assertEquals(0f, m.progress, 0f)
        val released = same.fork()
        assertTrue(released.act("withdraw", 2200))
        assertEquals("WITHDRAW", same.phase); assertFalse(same.completed)
        assertTrue(same.act("withdraw", 2200))
        assertEquals(same.toJson().toString(), released.toJson().toString())
        assertEquals(1, reset.measurements.size); assertEquals(2, released.measurements.size)
    }

    @Test fun interruptionReasonExplainsRetryWithoutAddingFrameEventsAndForksIndependently() {
        for (reason in listOf("stale-frame", "frame-gap", "off-base", "wrong-start-edge", "skipped-band", "released", "clock-invalid")) {
            val m = sweeping(); val eventsBefore = m.events.size
            val retryAt = when (reason) {
                "stale-frame" -> { m.aim(-1f, 0f, true, 500, false); 600L }
                "frame-gap" -> { m.aim(-1f, 0f, true, 500, true); m.aim(-1f, 0f, true, 651, true); 751L }
                "off-base" -> { m.aim(-1f, .26f, true, 500, true); 600L }
                "wrong-start-edge" -> { m.aim(0f, 0f, true, 500, true); 600L }
                "skipped-band" -> { m.aim(-1f, 0f, true, 500, true); m.aim(0f, 0f, true, 600, true); 700L }
                "released" -> { m.aim(-1f, 0f, false, 500, true); 600L }
                else -> { m.aim(-1f, 0f, true, 399, true); 500L }
            }
            assertEquals(reason, m.lastInterruption)
            assertEquals(reason, m.toJson().getString("lastInterruption"))
            assertEquals(eventsBefore, m.events.size)
            val retry = m.fork(); assertEquals(reason, retry.lastInterruption)
            assertFalse(retry.aim(-1f, 0f, true, retryAt, true))
            assertNull(retry.lastInterruption); assertTrue(retry.toJson().isNull("lastInterruption"))
            assertEquals(reason, m.lastInterruption); assertEquals(eventsBefore, retry.events.size)
        }
        val released = sweeping()
        assertTrue(released.act("release", 500)); assertEquals("released", released.lastInterruption)
        assertTrue(sweep(released, start = 600)); assertNull(released.lastInterruption)
        assertTrue(released.act("release", 2200)); assertNull(released.lastInterruption)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownModuleIsRejected() { RoomMission("unknown") }
}
