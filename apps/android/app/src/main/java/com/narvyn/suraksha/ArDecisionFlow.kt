package com.narvyn.suraksha

import org.json.JSONArray
import org.json.JSONObject

/** UI-thread decision mutations; renderer eligibility remains runtime-only and synchronized by the gate. */
class ArDecisionFlow(initial: TrainingSession, private val persist: (TrainingSession) -> Unit) {
    private val module = JSONObject().put("id", initial.data.getString("moduleId"))
        .put("questions", JSONArray(initial.questions.map { JSONObject(it.toString()) }))
    private val gate = ComponentCameraGate()
    @Volatile var training: TrainingSession = copy(initial)
        private set
    @Volatile var revision: Int = 0
        private set
    @Volatile private var active = false
    val finished get() = training.finished

    fun resume() { invalidate(); active = true; gate.activate() }
    fun pause() { active = false; gate.pause(); invalidate() }
    fun invalidate() { revision = gate.configure() }
    fun frame(expectedRevision: Int, ready: Boolean, observedAt: Long) {
        gate.frame(expectedRevision, ready, observedAt)
    }
    fun ready(now: Long): Boolean = active && gate.allows(revision, now)

    /** Returns acceptance, not answer correctness. Save failures propagate without changing the live attempt. */
    fun choose(optionId: String, expectedQuestionId: String, expectedRevision: Int, now: Long): Boolean {
        if (!active || expectedRevision != revision || !ready(now) || training.finished || training.data.optBoolean("awaitingContinue")) return false
        if (training.current.getString("id") != expectedQuestionId || training.current.getJSONArray("options").objects().none { it.getString("id") == optionId }) return false
        val candidate = copy(training)
        candidate.answer(optionId)
        persist(candidate)
        training = candidate
        invalidate()
        return true
    }

    /** A saved answer needs explicit acknowledgement; tracking is not required to read feedback. */
    fun continueSaved(expectedRevision: Int): Boolean {
        if (!active || expectedRevision != revision || training.finished || !training.data.optBoolean("awaitingContinue")) return false
        val candidate = copy(training)
        candidate.advance()
        persist(candidate)
        training = candidate
        invalidate()
        return true
    }

    private fun copy(source: TrainingSession) = TrainingSession(JSONObject(source.data.toString()), module)
}
