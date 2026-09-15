package com.narvyn.suraksha

import org.junit.Assert.*
import org.junit.Test

class RoomMissionTest {
    private fun aiming(): RoomMission = RoomMission("fire").apply {
        assertTrue(act("alarm", 10)); assertTrue(act("select-clear-exit", 12))
        assertTrue(act("select-suitable-extinguisher", 14)); assertTrue(act("pin-drag", 20))
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
        assertEquals("EXIT", m.phase)
        assertFalse(m.act("pin-drag", -1)); assertFalse(m.act("pin-drag", 3))
        assertFalse(m.act("pin-drag", 6)); assertEquals("out-of-order", m.events.last().reason)
        assertTrue(m.act("select-clear-exit", 7)); assertTrue(m.act("select-suitable-extinguisher", 8))
        assertTrue(m.act("pin-drag", 9)); assertEquals("AIM", m.phase)
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
        assertEquals("EVACUATE", m.phase); assertFalse(m.completed)
        assertTrue(m.act("follow-clear-route", 2600)); assertEquals("ASSEMBLY", m.phase)
        assertTrue(m.act("reach-assembly-point", 2700)); assertEquals("REPORT", m.phase)
        assertTrue(m.act("report-missing-worker", 2800))
        assertTrue(m.completed); assertEquals(1f, m.overallProgress, 0f)
        assertEquals("assembled-reported-after-worsening", m.toJson().getJSONObject("result").getString("outcome"))
        val final = m.toJson().toString()
        assertFalse(m.act("alarm", 2900)); assertFalse(m.aim(0f, 0f, true, 3000, true))
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
        assertTrue(m.act("inspect-meter", 1)); assertEquals("PPE", m.phase)
        assertFalse(m.act("enter", 2)); assertEquals("PPE", m.phase)
        assertTrue(m.act("select-specified-ppe", 3)); assertEquals("BARRIER", m.phase)
        assertTrue(m.act("close-barrier", 4)); assertEquals("ATTENDANT", m.phase)
        assertFalse(m.act("place-attendant-inside", 5))
        assertTrue(m.act("place-attendant-outside", 6)); assertEquals("COMMUNICATE", m.phase)
        assertTrue(m.act("send-buddy-check", 7)); assertEquals("ACKNOWLEDGE", m.phase)
        assertTrue(m.act("confirm-buddy-ack", 8)); assertEquals("REFUSE", m.phase)
        m.resetIncomplete(); assertEquals("REFUSE", m.phase)
        assertTrue(m.act("refuse-entry", 9)); assertTrue(m.completed)
        val data = m.toJson(); val scenario = data.getJSONObject("scenario")
        assertEquals("SIM", scenario.getString("meter")); assertFalse(scenario.getBoolean("liveReading"))
        assertEquals("unconfirmed", scenario.getString("rescueReadiness")); assertFalse(scenario.getBoolean("entryAuthorised"))
        assertEquals("outside-entry-refused", data.getJSONObject("result").getString("outcome"))
    }

    @Test fun auditSnapshotDoesNotExposeMutableStateOrCertificationCredit() {
        val m = sweeping(); sweep(m)
        val snapshot = m.toJson()
        assertEquals(2, snapshot.getInt("version")); assertEquals("fire", snapshot.getString("module"))
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

    @Test fun exitAndEquipmentChoicesRejectUnsafeOptionsBeforeAnyManipulation() {
        val m = RoomMission("fire")
        assertTrue(m.act("alarm", 1))
        assertFalse(m.act("select-blocked-exit", 2)); assertEquals("EXIT", m.phase)
        assertFalse(m.act("select-suitable-extinguisher", 3)); assertFalse(m.act("choose-evacuation", 4))
        assertTrue(m.act("select-clear-exit", 5)); assertEquals("EQUIPMENT", m.phase)
        assertFalse(m.act("select-unsuitable-extinguisher", 6)); assertFalse(m.act("operate-without-training", 7))
        assertFalse(m.act("pin-drag", 8)); assertEquals("EQUIPMENT", m.phase)
        assertEquals(listOf("select-blocked-exit", "select-unsuitable-extinguisher", "operate-without-training"), m.events.filter { it.reason == "unsafe-control" }.map { it.id })
        assertTrue(m.act("select-suitable-extinguisher", 9)); assertTrue(m.act("pin-drag", 10))
        assertEquals("AIM", m.phase); assertFalse(m.evacuationOnly); assertTrue(m.measurements.isEmpty())
        assertFalse(m.toJson().getJSONObject("scenario").getBoolean("realAuthorisationGranted"))
    }

    @Test fun evacuationOnlyChoiceCompletesRouteAssemblyAndReportWithoutDischargeCredit() {
        val m = RoomMission("fire")
        assertTrue(m.act("alarm", 1)); assertTrue(m.act("select-clear-exit", 2))
        assertTrue(m.act("choose-evacuation", 3)); assertEquals("EVACUATE", m.phase); assertTrue(m.evacuationOnly)
        assertFalse(m.act("pin-drag", 4)); assertFalse(m.aim(0f, 0f, true, 5, true))
        assertFalse(m.act("report-missing-worker", 6)); assertEquals("EVACUATE", m.phase)
        val fork = m.fork(); assertTrue(fork.evacuationOnly); assertEquals(m.toJson().toString(), fork.toJson().toString())
        assertTrue(fork.act("follow-clear-route", 7)); assertEquals("EVACUATE", m.phase)
        assertTrue(m.act("follow-clear-route", 7)); assertEquals("ASSEMBLY", m.phase); assertFalse(m.completed)
        assertTrue(m.act("reach-assembly-point", 8)); assertEquals("REPORT", m.phase); assertFalse(m.completed)
        assertTrue(m.act("report-missing-worker", 9)); assertTrue(m.completed); assertTrue(m.measurements.isEmpty())
        assertFalse(m.events.any { it.accepted && it.id in setOf("pin-drag", "base-alignment", "base-sweep", "release", "withdraw") })
        val data = m.toJson(); assertTrue(data.getBoolean("evacuationOnly"))
        assertEquals("not-used", data.getJSONObject("scenario").getString("equipment"))
        assertEquals("assembled-reported-without-discharge", data.getJSONObject("result").getString("outcome"))
        assertFalse(data.getJSONObject("result").getBoolean("certifiable")); assertEquals("not-assessed", data.getJSONObject("result").getString("practical"))
    }

    @Test fun evacuationMustUseClearRouteAndEndWithResponsiblePersonReportWithoutReentry() {
        val m = sweeping(); assertTrue(sweep(m)); assertTrue(m.act("release", 2100)); assertTrue(m.act("withdraw", 2200))
        assertFalse(m.act("follow-blocked-route", 2300)); assertFalse(m.act("enter-smoke", 2400)); assertEquals("EVACUATE", m.phase)
        assertFalse(m.act("reach-assembly-point", 2500)); assertTrue(m.act("follow-clear-route", 2600))
        assertFalse(m.act("leave-without-rollcall", 2700)); assertFalse(m.act("report-missing-worker", 2800)); assertEquals("ASSEMBLY", m.phase)
        assertTrue(m.act("reach-assembly-point", 2900)); assertFalse(m.act("reenter-search", 3000)); assertEquals("REPORT", m.phase)
        val checkpoint=m.fork(); m.resetIncomplete(); assertEquals(checkpoint.toJson().toString(),m.toJson().toString())
        assertTrue(m.act("report-missing-worker", 3100)); assertEquals("REPORT", checkpoint.phase)
        assertEquals(listOf("follow-blocked-route", "enter-smoke", "leave-without-rollcall", "reenter-search"), m.events.filter { it.reason == "unsafe-control" }.map { it.id })
        assertEquals(listOf("withdraw", "follow-clear-route", "reach-assembly-point", "report-missing-worker"), m.events.filter { it.accepted }.takeLast(4).map { it.id })
        assertFalse(m.toJson().getJSONObject("scenario").getBoolean("reentryAuthorised"))
    }

    @Test fun gasPpeAndAcknowledgementAreExplicitButNeverAuthoriseUnknownAtmosphereEntry() {
        val m = RoomMission("gas")
        assertTrue(m.act("inspect-meter", 1)); assertFalse(m.act("select-dust-mask", 2)); assertEquals("PPE", m.phase)
        assertFalse(m.act("close-barrier", 3)); assertTrue(m.act("select-specified-ppe", 4))
        assertTrue(m.act("close-barrier", 5)); assertTrue(m.act("place-attendant-outside", 6))
        assertFalse(m.act("skip-buddy-check", 7)); assertFalse(m.act("confirm-buddy-ack", 8)); assertFalse(m.act("refuse-entry", 9))
        assertEquals("COMMUNICATE", m.phase); assertTrue(m.act("send-buddy-check", 10))
        assertEquals("ACKNOWLEDGE", m.phase); assertEquals("unconfirmed", m.toJson().getJSONObject("scenario").getString("buddyCommunication"))
        assertFalse(m.act("proceed-without-ack", 11)); assertFalse(m.act("refuse-entry", 12)); assertFalse(m.act("enter", 13))
        val waiting=m.fork(); m.resetIncomplete(); assertEquals("ACKNOWLEDGE", m.phase)
        assertTrue(m.act("confirm-buddy-ack", 14)); assertEquals("ACKNOWLEDGE", waiting.phase)
        val scenario=m.toJson().getJSONObject("scenario")
        assertEquals("scenario-acknowledged", scenario.getString("buddyCommunication")); assertEquals("unconfirmed", scenario.getString("rescueReadiness"))
        assertEquals("draft-scenario-kit-for-outside-role", scenario.getString("ppe")); assertFalse(scenario.getBoolean("ppeAuthorisesEntry"))
        assertFalse(scenario.getBoolean("entryAuthorised")); assertFalse(m.act("enter", 15))
        assertTrue(m.act("refuse-entry", 16)); assertTrue(m.completed)
        assertFalse(m.toJson().getJSONObject("result").getBoolean("certifiable")); assertTrue(m.measurements.isEmpty())
        assertEquals(listOf("select-dust-mask", "skip-buddy-check", "proceed-without-ack", "enter", "enter"), m.events.filter { it.reason == "unsafe-control" }.map { it.id })
    }

    @Test fun announcedExplosionRiskAllowsOnlyEvacuationWithoutExtinguisherActions() {
        val m=RoomMission("fire",explosionRisk=true)
        assertTrue(m.act("alarm",1));assertTrue(m.act("select-clear-exit",2))
        val before=m.fork();assertTrue(before.explosionRisk);assertEquals(m.toJson().toString(),before.toJson().toString())
        assertFalse(m.act("select-suitable-extinguisher",3));assertEquals("unsafe-control",m.events.last().reason)
        assertEquals("EQUIPMENT",m.phase);assertFalse(m.act("pin-drag",4));assertFalse(m.aim(0f,0f,true,5,true))
        assertEquals(2,before.events.size)
        assertTrue(m.act("choose-evacuation",6));assertEquals("EVACUATE",m.phase)
        assertTrue(m.act("follow-clear-route",7));assertTrue(m.act("reach-assembly-point",8));assertTrue(m.act("report-missing-worker",9))
        assertTrue(m.completed);assertTrue(m.evacuationOnly);assertTrue(m.measurements.isEmpty())
        assertFalse(m.events.any { it.accepted && it.id in setOf("select-suitable-extinguisher","pin-drag","base-alignment","base-sweep","release","withdraw") })
        val json=m.toJson();val scenario=json.getJSONObject("scenario")
        assertTrue(scenario.getBoolean("explosionRisk"));assertEquals("scenario-announced",scenario.getString("explosionRiskSource"))
        assertFalse(scenario.getBoolean("realAuthorisationGranted"));assertEquals("not-used",scenario.getString("equipment"))
        assertEquals("assembled-reported-without-discharge",json.getJSONObject("result").getString("outcome"))
        assertFalse(json.getJSONObject("result").getBoolean("certifiable"))
        assertFalse(RoomMission("fire").toJson().getJSONObject("scenario").getBoolean("explosionRisk"))
        try { RoomMission("gas",explosionRisk=true);fail("Fire-only risk variant accepted for gas") } catch(_:IllegalArgumentException) {}
    }

    @Test fun publicPhaseContractsCoverNewJourneysAndSnapshotsAreVersioned() {
        assertEquals(listOf("ALARM", "EXIT", "EQUIPMENT", "PIN", "AIM", "SWEEP", "WITHDRAW", "EVACUATE", "ASSEMBLY", "REPORT", "COMPLETE"), RoomMission.phases("fire"))
        assertEquals(listOf("GAS_CHECK", "PPE", "BARRIER", "ATTENDANT", "COMMUNICATE", "ACKNOWLEDGE", "REFUSE", "COMPLETE"), RoomMission.phases("gas"))
        assertEquals(2, RoomMission.VERSION); assertEquals(2, RoomMission("gas").toJson().getInt("version"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownModuleIsRejected() { RoomMission("unknown") }
    private fun rejectedSnapshot(value: org.json.JSONObject) {
        try { RoomMission.restore(value); fail("Corrupt snapshot resumed") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun restoreRetainsOriginalMeasuredEvidenceAndReleaseStateWithoutInventingEvents() {
        for (release in listOf(false, true)) {
            val original = sweeping(); assertTrue(sweep(original, direction = -1))
            assertFalse(original.act("withdraw", 2100))
            if (release) assertTrue(original.act("release", 2200))
            val snapshot = original.toJson()
            val restored = RoomMission.restore(snapshot)
            assertEquals(original.events, restored.events)
            assertEquals(original.measurements, restored.measurements)
            assertEquals("WITHDRAW", restored.phase)
            assertEquals(if (release) .5f else 0f, restored.progress, 0f)
            assertEquals(if (release) 2201L else 2101L, restored.nextElapsedTime)
            if (!release) {
                assertFalse(restored.act("withdraw", 2300))
                assertTrue(restored.act("release", 2400))
            }
            assertTrue(restored.act("withdraw", 2500))
            assertTrue(restored.act("follow-clear-route", 2600))
            assertTrue(restored.act("reach-assembly-point", 2700))
            assertTrue(restored.act("report-missing-worker", 2800))
            val complete = RoomMission.restore(restored.toJson())
            assertTrue(complete.completed); assertEquals(restored.events, complete.events)
            assertEquals(original.measurements, complete.measurements)
            snapshot.getJSONArray("events").getJSONObject(0).put("accepted", false)
            assertTrue(complete.events.first().accepted) // Detached from the caller's mutable JSON.
        }
    }

    @Test fun restoreClearsPartialAlignmentAndSweepButKeepsAcceptedStages() {
        val alignment = aiming()
        alignment.aim(0f, 0f, false, 100, true); alignment.aim(0f, 0f, false, 200, true)
        assertTrue(alignment.progress > 0f)
        val alignCopy = RoomMission.restore(alignment.toJson())
        assertEquals("AIM", alignCopy.phase); assertEquals(0f, alignCopy.progress, 0f)
        assertEquals(alignment.events, alignCopy.events); assertTrue(alignCopy.measurements.isEmpty())
        assertFalse(alignCopy.aim(0f, 0f, false, 300, true))
        assertFalse(alignCopy.aim(0f, 0f, false, 400, true))
        assertFalse(alignCopy.aim(0f, 0f, false, 500, true))
        assertTrue(alignCopy.aim(0f, 0f, false, 600, true))
        val partial = sweeping()
        partial.aim(-1f, 0f, true, 500, true); partial.aim(-.5f, 0f, true, 600, true)
        val sweepCopy = RoomMission.restore(partial.toJson())
        assertEquals("SWEEP", sweepCopy.phase); assertEquals(0f, sweepCopy.progress, 0f)
        assertEquals(partial.measurements, sweepCopy.measurements)
        assertFalse(sweepCopy.aim(0f, 0f, true, 700, true))
        assertEquals("wrong-start-edge", sweepCopy.lastInterruption)
        assertTrue(sweep(sweepCopy, 800))
    }

    @Test fun restoreSupportsEvacuationOnlyAndExplosionBranchesAndGasAcknowledgement() {
        for (explosion in listOf(false, true)) {
            val m = RoomMission("fire", explosion)
            m.act("alarm", 1); m.act("select-clear-exit", 2)
            if (explosion) assertFalse(m.act("select-suitable-extinguisher", 3))
            m.act("choose-evacuation", 4)
            var copy = RoomMission.restore(m.toJson())
            assertTrue(copy.evacuationOnly); assertEquals(explosion, copy.explosionRisk)
            assertEquals(m.events, copy.events); assertTrue(copy.measurements.isEmpty())
            copy.act("follow-clear-route", 5); copy.act("reach-assembly-point", 6); copy.act("report-missing-worker", 7)
            copy = RoomMission.restore(copy.toJson())
            assertTrue(copy.completed)
            assertEquals("assembled-reported-without-discharge", copy.toJson().getJSONObject("result").getString("outcome"))
        }
        val gas = RoomMission("gas")
        listOf("inspect-meter", "select-specified-ppe", "close-barrier", "place-attendant-outside", "send-buddy-check")
            .forEachIndexed { i, id -> gas.act(id, i.toLong()) }
        assertFalse(gas.act("proceed-without-ack", 5))
        val copy = RoomMission.restore(gas.toJson())
        assertEquals("ACKNOWLEDGE", copy.phase); assertEquals(gas.events, copy.events)
        assertEquals(6L, copy.nextElapsedTime)
        assertTrue(copy.act("confirm-buddy-ack", 6)); assertTrue(copy.act("refuse-entry", 7))
        assertTrue(RoomMission.restore(copy.toJson()).completed)
        assertEquals(0L, RoomMission.restore(RoomMission("gas").toJson()).nextElapsedTime)
    }

    @Test fun restoreRejectsForgedCompletionCertificationBranchAndControlOrdering() {
        val m = aiming()
        val edits: List<(org.json.JSONObject) -> Unit> = listOf(
            { it.put("version", 1) }, { it.put("version", 2.0) },
            { it.put("completed", true) }, { it.put("phase", "COMPLETE") },
            { it.put("released", true) }, { it.put("evacuationOnly", true) },
            { it.put("progress", 1.0) }, { it.put("overallProgress", .99) },
            { it.getJSONObject("result").put("certifiable", true) },
            { it.getJSONObject("result").put("practical", "passed") },
            { it.getJSONObject("scenario").put("realAuthorisationGranted", true) },
            { it.getJSONObject("scenario").put("explosionRisk", true).put("explosionRiskSource", "scenario-announced") },
            { it.getJSONArray("events").getJSONObject(0).put("id", "pin-drag") },
            { it.getJSONArray("events").getJSONObject(0).put("accepted", false) },
            { it.getJSONArray("events").getJSONObject(0).put("reason", "unsafe-control") },
            { it.getJSONArray("events").getJSONObject(0).put("at", -1) },
            { it.getJSONArray("events").getJSONObject(1).put("at", 0) },
            { it.getJSONArray("events").getJSONObject(0).put("at", "10") },
            { it.put("certifiable", true) }
        )
        for (edit in edits) rejectedSnapshot(m.toJson().also(edit))
        val maxClock = RoomMission("gas").apply { act("inspect-meter", Long.MAX_VALUE) }
        rejectedSnapshot(maxClock.toJson())
        val complete = RoomMission("gas")
        listOf("inspect-meter", "select-specified-ppe", "close-barrier", "place-attendant-outside", "send-buddy-check", "confirm-buddy-ack", "refuse-entry")
            .forEachIndexed { i, id -> complete.act(id, i.toLong()) }
        val extra = complete.toJson()
        extra.getJSONArray("events").put(org.json.JSONObject().put("id", "enter").put("phase", "COMPLETE")
            .put("at", 9).put("accepted", false).put("reason", "unsafe-control"))
        rejectedSnapshot(extra)
    }

    @Test fun restoreRejectsImpossibleOrUnpairedMeasurementSummariesAndBoundedJournals() {
        val m = sweeping(); sweep(m)
        val edits: List<(org.json.JSONObject) -> Unit> = listOf(
            { it.put("durationMs", 301) }, { it.put("startedAt", -1) },
            { it.put("samples", 2) }, { it.put("samples", 3.5) },
            { it.put("maxGapMs", 151) }, { it.put("maxGapMs", 0) },
            { it.put("completedAt", 401) }, { it.put("kind", "base-sweep") },
            { it.put("bins", org.json.JSONArray(listOf(0))) }
        )
        for (edit in edits) {
            val snapshot = m.toJson(); edit(snapshot.getJSONArray("measurements").getJSONObject(0)); rejectedSnapshot(snapshot)
        }
        val skippedBins = m.toJson()
        skippedBins.getJSONArray("measurements").getJSONObject(1).put("bins", org.json.JSONArray(listOf(0, 2, 1, 3, 4)))
        rejectedSnapshot(skippedBins)
        rejectedSnapshot(m.toJson().put("measurements", org.json.JSONArray()))
        val duplicated = m.toJson()
        duplicated.getJSONArray("measurements").put(duplicated.getJSONArray("measurements").getJSONObject(0))
        rejectedSnapshot(duplicated)
        val tooMany = RoomMission("gas")
        repeat(RoomMission.MAX_RESTORE_EVENTS + 1) { tooMany.act("enter", it.toLong()) }
        rejectedSnapshot(tooMany.toJson())
    }

    @Test fun restoreAllowsRejectedControlsDuringMeasuredGestureAndHonoursReleaseReset() {
        val m = aiming()
        m.aim(0f, 0f, false, 100, true); m.act("continue-discharge", 150)
        m.aim(0f, 0f, false, 200, true); m.aim(0f, 0f, false, 300, true); m.aim(0f, 0f, false, 400, true)
        m.aim(-1f, 0f, true, 500, true); m.act("release", 550)
        assertTrue(sweep(m, 600))
        val restored = RoomMission.restore(m.toJson())
        assertEquals(m.events, restored.events); assertEquals(m.measurements, restored.measurements)
        val overlap = m.toJson()
        val evidence = overlap.getJSONArray("measurements").getJSONObject(1)
        // A summary claiming samples before the release reset cannot be carried forward.
        evidence.put("startedAt", 500).put("durationMs", 1600).put("samples", 17)
        rejectedSnapshot(overlap)
    }

}
