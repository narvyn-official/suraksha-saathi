package com.narvyn.suraksha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ArDecisionFlowTest {
    private fun initial(guided: Boolean = false): TrainingSession {
        val module = JSONObject("""{"id":"fire","questions":[
          {"id":"alarm","critical":true,"options":[{"id":"safe","correct":true},{"id":"unsafe","correct":false}]},
          {"id":"exit","critical":false,"options":[{"id":"safe","correct":true},{"id":"unsafe","correct":false}]}
        ]}""")
        return TrainingSession.create(module,"demo-worker","test",guided,"ar")
    }
    private fun tracked(flow: ArDecisionFlow, now: Long = 100L): Int {
        val revision = flow.revision
        flow.frame(revision,true,now)
        return revision
    }
    @Test fun staleQueuedChoicesCannotCrossQuestionsOrLifecycleRevisions() {
        val flow = ArDecisionFlow(initial()) {}
        flow.resume(); val first = tracked(flow)
        assertTrue(flow.choose("safe","alarm",first,100))
        assertTrue(flow.continueSaved(flow.revision))
        val second = tracked(flow)
        assertFalse(flow.choose("safe","alarm",first,100))
        assertFalse(flow.choose("safe","alarm",second,100))
        flow.pause(); flow.resume()
        flow.frame(second,true,101)
        assertFalse(flow.ready(101)); assertFalse(flow.choose("safe","exit",second,101))
        val resumed = tracked(flow,102)
        assertTrue(flow.choose("safe","exit",resumed,102)); assertEquals(2,flow.training.events.size)
    }
    @Test fun duplicateAnswerAndContinueCannotWriteTwice() {
        var saves = 0
        val flow = ArDecisionFlow(initial()) { saves++ }
        flow.resume(); val first = tracked(flow)
        assertTrue(flow.choose("safe","alarm",first,100))
        assertFalse(flow.choose("safe","alarm",first,100))
        tracked(flow)
        assertFalse(flow.choose("safe","alarm",flow.revision,100))
        val acknowledged = flow.revision
        assertTrue(flow.continueSaved(acknowledged)); assertFalse(flow.continueSaved(acknowledged))
        assertEquals(2,saves); assertEquals(1,flow.training.events.size); assertEquals(1,flow.training.index)
    }
    @Test fun restoredGuidedMistakeWaitsForExplicitFeedbackAcknowledgement() {
        val flow = ArDecisionFlow(initial(true)) {}
        flow.resume(); assertTrue(flow.choose("unsafe","alarm",tracked(flow),100))
        assertFalse(flow.finished)
        val restored = ArDecisionFlow(flow.training) {}
        assertEquals(0,restored.training.index); assertTrue(restored.training.data.getBoolean("awaitingContinue"))
        assertFalse(restored.training.data.getBoolean("lastCorrect")); assertEquals(1,restored.training.events.size)
        restored.resume()
        assertFalse(restored.ready(100))
        assertTrue(restored.continueSaved(restored.revision)); assertEquals(1,restored.training.index)
    }
    @Test fun assessmentRestoreDoesNotSkipSavedAnswerOrFinalAcknowledgement() {
        val flow = ArDecisionFlow(initial()) {}
        flow.resume(); assertTrue(flow.choose("safe","alarm",tracked(flow),100))
        val restored = ArDecisionFlow(flow.training) {}
        assertFalse(restored.finished); assertEquals(0,restored.training.index)
        restored.resume(); assertTrue(restored.continueSaved(restored.revision))
        assertTrue(restored.choose("safe","exit",tracked(restored),100)); assertFalse(restored.finished)
        assertTrue(restored.continueSaved(restored.revision)); assertTrue(restored.finished)
        assertTrue(restored.training.data.getJSONObject("result").getBoolean("passed"))
    }
    @Test fun criticalAssessmentFailureRemainsFinishedAcrossRestore() {
        var saves = 0
        val flow = ArDecisionFlow(initial()) { saves++ }
        flow.resume(); assertTrue(flow.choose("unsafe","alarm",tracked(flow),100))
        assertTrue(flow.finished); assertFalse(flow.training.data.getJSONObject("result").getBoolean("passed"))
        val restored = ArDecisionFlow(flow.training) { saves++ }
        restored.resume(); tracked(restored)
        assertFalse(restored.continueSaved(restored.revision)); assertFalse(restored.choose("safe","alarm",restored.revision,100))
        assertEquals(1,saves); assertEquals(1,restored.training.events.size)
    }
    @Test fun failedPersistenceRollsBackAnswerAndContinueBeforeRetry() {
        var failSave = true
        val original = initial()
        val flow = ArDecisionFlow(original) { if(failSave) throw IllegalStateException("storage unavailable") }
        flow.resume(); val first = tracked(flow)
        try { flow.choose("safe","alarm",first,100); fail("Expected save error") } catch(_: IllegalStateException) {}
        assertEquals(first,flow.revision); assertEquals(0,flow.training.events.size); assertEquals(0,original.events.size)
        failSave = false
        assertTrue(flow.choose("safe","alarm",first,100))
        val saved = flow.training.data.toString(); val beforeContinue = flow.revision
        failSave = true
        try { flow.continueSaved(beforeContinue); fail("Expected save error") } catch(_: IllegalStateException) {}
        assertEquals(saved,flow.training.data.toString()); assertEquals(beforeContinue,flow.revision)
        failSave = false
        assertTrue(flow.continueSaved(beforeContinue)); assertEquals(1,flow.training.events.size)
    }
    @Test fun invalidUntrackedExpiredAndInactiveActionsDoNotPersist() {
        var saves = 0
        val flow = ArDecisionFlow(initial()) { saves++ }
        flow.frame(flow.revision,true,100)
        assertFalse(flow.choose("safe","alarm",flow.revision,100))
        flow.resume()
        assertFalse(flow.choose("safe","alarm",flow.revision,100))
        val revision = tracked(flow)
        assertFalse(flow.choose("unknown","alarm",revision,100)); assertFalse(flow.choose("safe","alarm",revision,601))
        assertFalse(flow.continueSaved(revision)); assertEquals(0,saves)
        assertTrue(flow.choose("safe","alarm",revision,100))
        val saved = flow.revision
        flow.pause(); assertFalse(flow.continueSaved(saved)); assertFalse(flow.continueSaved(flow.revision))
        assertEquals(1,saves)
    }
}
