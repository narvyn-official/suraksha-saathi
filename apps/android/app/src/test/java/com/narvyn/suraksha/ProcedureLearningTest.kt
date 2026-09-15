package com.narvyn.suraksha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProcedureLearningTest {
    private fun nextTime(s: ProcedureSession) = s.data.getLong("updatedAt") + 1
    private fun safe(s: ProcedureSession, mode: String = "screen") {
        assertTrue(s.choose(s.step.actions.single { it.correct }.id,mode,nextTime(s)))
        assertTrue(s.advance())
    }
    private fun finish(s: ProcedureSession) { while (!s.done) safe(s) }
    private fun expectInvalid(block: () -> Unit) {
        try { block(); fail("Expected malformed procedure to be rejected") } catch (e: IllegalArgumentException) {
            // Includes failed invariants and malformed UUIDs.
        } catch (e: org.json.JSONException) {
            // Missing required journal fields are invalid too.
        }
    }

    @Test fun completeSequencesRequireEveryActionAndRemainNonCertifying() {
        for (module in ProcedureCatalog.modules.keys) {
            val s = ProcedureSession.create(module,"worker-1",false)
            assertFalse(s.advance())
            assertEquals(0,s.index)
            finish(s)
            val result = s.data.getJSONObject("result")
            assertTrue(result.getBoolean("complete")); assertFalse(result.getBoolean("stopped"))
            assertEquals(0,result.getJSONArray("criticalFailures").length())
            assertFalse(result.getBoolean("certifiable")); assertEquals("not-assessed",result.getString("practical"))
            assertEquals(ProcedureCatalog.modules.getValue(module).steps.size,s.data.getJSONObject("flags").length())
            assertEquals(ProcedureCatalog.modules.getValue(module).steps.last().id,s.step.id)
            assertFalse(s.advance()); assertFalse(s.choose(s.step.actions.first().id,"screen",nextTime(s)))
            assertTrue(ProcedureSession.restore(s.data).done)
        }
    }

    @Test fun unsafePracticeIsRecordedAndMustRetrySameStepBeforeProgression() {
        val s = ProcedureSession.create("fire","worker-1",true)
        val first = s.step.id
        assertTrue(s.choose(s.step.actions.single { !it.correct }.id,"camera",nextTime(s)))
        assertFalse(s.done); assertTrue(s.feedback); assertFalse(s.data.getBoolean("lastCorrect"))
        assertEquals(0,s.data.getJSONObject("flags").length())
        assertFalse(s.choose(s.step.actions.single { it.correct }.id,"camera",nextTime(s)))
        val restored = ProcedureSession.restore(s.data)
        assertTrue(restored.advance()); assertEquals(first,restored.step.id); assertFalse(restored.feedback)
        safe(restored,"description"); assertEquals(1,restored.index)
        finish(restored)
        assertTrue(restored.data.getJSONObject("result").getBoolean("complete"))
        assertEquals(first,restored.data.getJSONObject("result").getJSONArray("criticalFailures").getString(0))
        assertEquals(0,s.index) // restore cloned the journal and state
    }

    @Test fun anyUnsafeIndependentActionStopsWithoutApplyingItsStateFlag() {
        val s = ProcedureSession.create("gas","worker-1",false)
        safe(s)
        val unsafe = s.step.actions.single { !it.correct }
        assertTrue(s.choose(unsafe.id,"screen",nextTime(s)))
        assertTrue(s.done); assertFalse(s.data.getJSONObject("result").getBoolean("complete"))
        assertTrue(s.data.getJSONObject("result").getBoolean("stopped"))
        assertFalse(s.data.getJSONObject("flags").has(unsafe.id))
        assertEquals(1,s.data.getJSONObject("flags").length())
        assertFalse(s.advance())
        assertFalse(s.choose(s.step.actions.single { it.correct }.id,"screen",nextTime(s)))
        assertTrue(ProcedureSession.restore(s.data).done)
    }

    @Test fun staleDuplicateUnknownAndInvalidPresentationActionsCannotMutateState() {
        val s = ProcedureSession.create("fire","worker-1",true)
        val first = s.step.actions.single { it.correct }.id
        var before = s.data.toString()
        assertFalse(s.choose("fire-pin","camera",nextTime(s))) // action from a future step
        assertFalse(s.choose(first,"arcore",nextTime(s))) // contract distinguishes camera/screen/description
        assertFalse(s.choose(first,"screen",s.data.getLong("createdAt")-1))
        assertEquals(before,s.data.toString())
        assertTrue(s.choose(first,"camera",nextTime(s))); before = s.data.toString()
        assertFalse(s.choose(first,"camera",nextTime(s))); assertEquals(before,s.data.toString())
        assertTrue(s.advance()); before = s.data.toString()
        assertFalse(s.choose(first,"screen",nextTime(s))); assertEquals(before,s.data.toString())
    }

    @Test fun restoredFeedbackDoesNotAllowDuplicateSubmissionAndTracksEachPresentation() {
        val s = ProcedureSession.create("gas","worker-1",true)
        assertTrue(s.choose(s.step.actions.single { it.correct }.id,"camera",nextTime(s)))
        val copy = ProcedureSession.restore(s.data)
        assertTrue(copy.feedback)
        assertFalse(copy.choose(copy.step.actions.single { it.correct }.id,"screen",nextTime(copy)))
        assertTrue(copy.advance()); safe(copy,"description"); safe(copy,"screen")
        val presentations = copy.data.getJSONArray("events").objects().filter { it.getString("type") == "action" }.map { it.getString("presentation") }
        assertEquals(listOf("camera","description","screen"),presentations)
        assertTrue(copy.data.getLong("updatedAt") >= copy.data.getLong("createdAt"))
        assertEquals(copy.data.toString(),ProcedureSession.restore(copy.data).data.toString())
    }

    @Test fun malformedJournalAndFabricatedProgressAreRejectedOnRestore() {
        val s = ProcedureSession.create("fire","worker-1",true)
        safe(s)
        expectInvalid { ProcedureSession.restore(JSONObject(s.data.toString()).put("catalogVersion",999)) }
        expectInvalid { ProcedureSession.restore(JSONObject(s.data.toString()).put("index",5)) }
        expectInvalid { ProcedureSession.restore(JSONObject(s.data.toString()).put("finished",true)) }
        expectInvalid { ProcedureSession.restore(JSONObject(s.data.toString()).put("id","not-a-uuid")) }
        val wrongFlag = JSONObject(s.data.toString()); wrongFlag.getJSONObject("flags").put("fire-pin",true)
        expectInvalid { ProcedureSession.restore(wrongFlag) }
        val changedOutcome = JSONObject(s.data.toString()); changedOutcome.getJSONArray("events").getJSONObject(0).put("correct",false)
        expectInvalid { ProcedureSession.restore(changedOutcome) }
        val wrongStep = JSONObject(s.data.toString()); wrongStep.getJSONArray("events").getJSONObject(0).put("step","fire-pin")
        expectInvalid { ProcedureSession.restore(wrongStep) }
        val wrongSequence = JSONObject(s.data.toString()); wrongSequence.getJSONArray("events").getJSONObject(1).put("sequence",7)
        expectInvalid { ProcedureSession.restore(wrongSequence) }
    }

    @Test fun fireSweepHasSequentialDirectionalTargetsAndWorseningConditionsRequireWithdrawal() {
        val s = ProcedureSession.create("fire","worker-1",false)
        while (s.step.id != "fire-sweep-left") safe(s)
        assertTrue(s.data.getJSONObject("flags").getBoolean("fire-pin"))
        assertTrue(s.data.getJSONObject("flags").getBoolean("fire-aim"))
        assertTrue(s.data.getJSONObject("flags").getBoolean("fire-squeeze"))
        assertFalse(s.choose("fire-sweep-right","screen",nextTime(s)))
        val left = s.step.actions.single { it.correct }.point[0]; safe(s)
        val right = s.step.actions.single { it.correct }.point[0]; assertTrue(left < right); safe(s)
        assertEquals("fire-sweep-return",s.step.id); safe(s)
        assertEquals("fire-withdraw",s.step.id)
        assertTrue(s.choose("fire-withdraw-unsafe","screen",nextTime(s)))
        assertTrue(s.done); assertTrue(s.data.getJSONObject("result").getBoolean("stopped"))
        assertFalse(s.data.getJSONObject("flags").has("fire-exit"))
    }

    @Test fun gasSequenceEndsWithRefusalRatherThanAutomaticEntryClearance() {
        val s = ProcedureSession.create("gas","worker-1",false)
        while (s.step.id != "gas-refuse-entry") safe(s)
        assertTrue(s.data.getJSONObject("flags").getBoolean("gas-rescue")) // missing rescue readiness was flagged
        assertTrue(s.data.getJSONObject("flags").getBoolean("gas-attendant"))
        assertFalse(s.done)
        safe(s)
        assertTrue(s.data.getJSONObject("flags").getBoolean("gas-refuse-entry"))
        assertFalse(s.data.getJSONObject("flags").has("entry-authorised"))
        assertTrue(s.done)
    }
}
