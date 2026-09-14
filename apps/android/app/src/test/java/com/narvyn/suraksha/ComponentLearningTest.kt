package com.narvyn.suraksha

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class ComponentLearningTest {
    private fun finish(session: ComponentSession, now: Long) {
        while (!session.done) {
            if (session.stage > 0 && session.answer == null) session.choose(session.target.id,now)
            session.advance(now)
        }
    }
    @Test fun unaidedRecognitionRequiresBothViewsOfEveryPart() {
        for (module in ComponentCatalog.modules.keys) {
            val session = ComponentSession.start(module,null)
            session.choose(session.target.id,1) // example is not an answer
            assertEquals(0,session.data.getJSONArray("events").length())
            session.advance(1)
            val originalOrder = session.order.map { it.id }
            val originalYaw = session.yaw
            session.choose(session.target.id,2); session.advance(2)
            assertNotEquals(originalOrder,session.order.map { it.id })
            assertNotEquals(originalYaw,session.yaw)
            finish(session,10)
            assertEquals(6,session.data.getJSONArray("events").length())
            assertEquals(6,session.data.getInt("visualCorrect"))
            assertEquals(0,session.data.getInt("descriptionCorrect"))
            assertEquals(10+RecallPlanner.DAY,session.data.getLong("dueAt"))
        }
    }
    @Test fun mistakeNeedsGuidedRetryAndCannotBecomeUnaidedEvidence() {
        val session = ComponentSession.start("fire",null)
        session.advance(1); val wrong = session.parts.first { it.id != session.target.id }.id
        session.choose(wrong,2); session.choose(session.target.id,3)
        assertEquals(wrong,session.answer)
        val restored = ComponentSession.restore("fire",session.data)
        restored.advance(3)
        assertTrue(restored.helped); assertNull(restored.answer); assertEquals(1,restored.stage)
        restored.choose(restored.target.id,4); restored.advance(4)
        assertEquals(0,restored.data.getInt("visualCorrect"))
        finish(restored,20)
        assertEquals(20+600_000L,restored.data.getLong("dueAt")); assertEquals(0,restored.data.getInt("streak"))
    }
    @Test fun requestedHintIsRecordedEvenIfAnswerIsCorrect() {
        val session = ComponentSession.start("gas",null)
        session.advance(1); session.hint(); session.choose(session.target.id,2)
        assertTrue(session.data.getJSONArray("events").getJSONObject(0).getBoolean("helped"))
        finish(session,20)
        assertEquals(5,session.data.getInt("visualCorrect")); assertEquals(20+600_000L,session.data.getLong("dueAt"))
    }
    @Test fun descriptionsHaveSeparateEvidenceAndUnknownAnswersCannotAdvance() {
        val session = ComponentSession.start("ppe",null)
        session.useDescriptions(true); session.advance(1); session.choose("unknown",2)
        assertNull(session.answer)
        session.advance(2); assertEquals(1,session.stage)
        finish(session,20)
        assertEquals(0,session.data.getInt("visualCorrect")); assertEquals(6,session.data.getInt("descriptionCorrect"))
        assertTrue(session.data.getJSONArray("events").objects().all { it.getString("mode") == "description" })
    }
    @Test fun currentSavedRoundResumesWhileChangedCatalogStartsFresh() {
        val session = ComponentSession.start("machinery",null)
        session.advance(1); session.choose("_unsure",2)
        val copy = ComponentSession.restore("machinery",session.data)
        assertEquals("_unsure",copy.answer)
        copy.advance(3); assertEquals("_unsure",session.answer) // restoration is not aliased
        val changed = JSONObject(session.data.toString()).put("catalogVersion",0)
        assertEquals(0,ComponentSession.restore("machinery",changed).stage)
        assertEquals(0,ComponentSession.restore("fire",session.data).stage)
    }
    @Test fun newReviewStartsWithFreshAnswersAndLongerScheduleOnlyAfterSuccess() {
        val session = ComponentSession.start("fire",null); finish(session,10)
        val next = ComponentSession.start("fire",session.data,10+RecallPlanner.DAY)
        assertEquals(1,next.stage); assertEquals(0,next.data.getJSONArray("events").length()); assertEquals(1,next.data.getInt("round"))
        finish(next,20+RecallPlanner.DAY)
        assertEquals(20+4*RecallPlanner.DAY,next.data.getLong("dueAt"))
    }
    @Test fun answerPositionsVaryAndEarlyPracticeDoesNotPostponeDueReview() {
        val session = ComponentSession.start("fire",null)
        val positions = mutableSetOf<Int>()
        while (!session.done) {
            if (session.stage == 1 && session.answer == null) positions.add(session.order.indexOf(session.target))
            if (session.stage > 0 && session.answer == null) session.choose(session.target.id,10)
            session.advance(10)
        }
        assertEquals(3,positions.size)
        val early = ComponentSession.start("fire",session.data,20)
        assertEquals(1,early.stage) // reviews start without an answer-revealing example
        finish(early,30)
        assertEquals(session.data.getLong("dueAt"),early.data.getLong("dueAt"))
        assertEquals(1,early.data.getInt("streak"))
    }
    @Test fun descriptionExposureCannotBeHiddenBySwitchingBackToModel() {
        val session = ComponentSession.start("ppe",null)
        session.advance(1); session.useDescriptions(true); session.useDescriptions(false)
        session.choose(session.target.id,2)
        val event = session.data.getJSONArray("events").getJSONObject(0)
        assertEquals("mixed",event.getString("mode")); assertTrue(event.getBoolean("descriptionExposed"))
        assertEquals(0,session.data.getInt("visualCorrect")); assertEquals(1,session.data.getInt("descriptionCorrect"))
    }
    @Test fun cameraAndScreenExposureStayMixedAcrossRecreationAndResetForNextDecision() {
        val session = ComponentSession.start("fire",null)
        session.notePresentation("screen") // instructional example is not retrieval exposure
        session.advance(1)
        session.notePresentation("camera")
        val restored = ComponentSession.restore("fire",session.data)
        restored.notePresentation("screen")
        restored.choose(restored.target.id,2,"camera")
        val event = restored.data.getJSONArray("events").getJSONObject(0)
        assertEquals("visual-markers",event.getString("mode")); assertEquals("mixed",event.getString("presentation"))
        assertEquals(1,restored.data.getInt("mixedPresentationCorrect")); assertEquals(0,restored.data.getInt("cameraCorrect"))
        restored.advance(3)
        restored.choose(restored.target.id,4,"camera")
        assertEquals("camera",restored.data.getJSONArray("events").getJSONObject(1).getString("presentation"))
        assertEquals(1,restored.data.getInt("cameraCorrect")); assertEquals(2,restored.data.getInt("visualCorrect"))
    }
    @Test fun descriptionUseNeverCreatesCameraRecognitionCredit() {
        val session = ComponentSession.start("fire",null)
        session.advance(1); session.notePresentation("camera"); session.useDescriptions(true)
        session.choose(session.target.id,2,"camera")
        assertEquals("description",session.data.getJSONArray("events").getJSONObject(0).getString("presentation"))
        assertEquals(0,session.data.getInt("cameraCorrect")); assertEquals(1,session.data.getInt("descriptionCorrect"))
        session.advance(3); session.useDescriptions(false); session.choose(session.target.id,4,"camera")
        val event = session.data.getJSONArray("events").getJSONObject(1)
        assertEquals("mixed",event.getString("mode")); assertEquals("camera",event.getString("presentation"))
        assertEquals(0,session.data.getInt("cameraCorrect")); assertEquals(2,session.data.getInt("descriptionCorrect"))
    }
    @Test fun invalidPresentationAndAnswersCannotCreateExposureOrEvents() {
        val session = ComponentSession.start("fire",null)
        session.advance(1); session.notePresentation("untracked")
        session.choose(session.target.id,2,"untracked"); session.choose("unknown",3,"camera")
        assertNull(session.answer); assertFalse(session.data.getBoolean("cameraSeen")); assertFalse(session.data.getBoolean("screenSeen"))
        assertEquals(0,session.data.getJSONArray("events").length())
        session.choose(session.target.id,4)
        assertEquals("screen",session.data.getJSONArray("events").getJSONObject(0).getString("presentation"))
        assertEquals(1,session.data.getInt("screenCorrect"))
        session.notePresentation("camera"); session.choose(session.target.id,5,"camera")
        assertFalse(session.data.getBoolean("cameraSeen")); assertEquals(1,session.data.getJSONArray("events").length())
    }
    @Test fun legacyActiveDecisionIsConservativelyScreenExposedWithoutRewritingHistory() {
        val session = ComponentSession.start("fire",null)
        session.advance(1); session.choose(session.target.id,2); session.advance(3)
        session.data.remove("screenSeen"); session.data.remove("cameraSeen")
        val historical = session.data.getJSONArray("events").getJSONObject(0).toString()
        val restored = ComponentSession.restore("fire",session.data)
        restored.choose(restored.target.id,4,"camera")
        assertEquals(historical,restored.data.getJSONArray("events").getJSONObject(0).toString())
        assertEquals("mixed",restored.data.getJSONArray("events").getJSONObject(1).getString("presentation"))
        assertEquals(0,restored.data.getInt("cameraCorrect"))
    }
    @Test fun wrongAnswerRetryRetainsPresentationExposureAndCannotGainUnaidedCredit() {
        val session = ComponentSession.start("fire",null)
        session.advance(1); session.choose("_unsure",2,"camera"); session.advance(3)
        session.choose(session.target.id,4,"screen")
        val retry = session.data.getJSONArray("events").getJSONObject(1)
        assertEquals("mixed",retry.getString("presentation")); assertTrue(retry.getBoolean("helped"))
        assertEquals(0,session.data.getInt("cameraCorrect")); assertEquals(0,session.data.getInt("screenCorrect"))
        assertEquals(0,session.data.getInt("mixedPresentationCorrect"))
    }
    @Test fun projectionRejectsBehindCameraOffscreenAndInvalidCoordinates() {
        val identity = FloatArray(16) { if (it%5==0) 1f else 0f }
        assertEquals(ComponentProjection.Point(100f,50f),ComponentProjection.project(floatArrayOf(0f,0f,0f),identity,200,100))
        assertEquals(ComponentProjection.Point(150f,25f),ComponentProjection.project(floatArrayOf(.5f,.5f,0f),identity,200,100))
        assertNull(ComponentProjection.project(floatArrayOf(2f,0f,0f),identity,200,100))
        assertNull(ComponentProjection.project(floatArrayOf(Float.NaN,0f,0f),identity,200,100))
        identity[15] = -1f
        assertNull(ComponentProjection.project(floatArrayOf(0f,0f,0f),identity,200,100))
    }
}
