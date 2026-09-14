package com.narvyn.suraksha

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
fun JSONObject.local(key: String, hi: Boolean): String = getJSONArray(key).getString(if (hi) 1 else 0)

class Curriculum(context: Context) {
    val json = JSONObject(context.assets.open("curriculum.json").bufferedReader().use { it.readText() })
    val modules = json.getJSONArray("modules").objects()
    val version = json.getString("version")
    fun module(id: String) = modules.first { it.getString("id") == id }
}

/** Pure decision grading. A critical error is never averaged away. */
object Grader {
    fun grade(questions: List<JSONObject>, events: List<JSONObject>): JSONObject {
        val answers = events.filter { it.optString("type") == "answer" }
        val seen = mutableSetOf<String>()
        var correct = 0
        val failed = JSONArray()
        var valid = true
        answers.forEach { event ->
            val q = questions.find { it.getString("id") == event.optString("questionId") }
            if (q == null || !seen.add(event.optString("questionId"))) { valid = false; return@forEach }
            val answer = q.getJSONArray("options").objects().find { it.getString("id") == event.optString("optionId") }
            if (answer == null) { valid = false; return@forEach }
            if (answer.optBoolean("correct")) correct++ else if (q.optBoolean("critical")) failed.put(q.getString("id"))
        }
        val complete = seen.size == questions.size && valid
        val score = if (questions.isEmpty()) 0 else correct * 100 / questions.size
        return JSONObject().put("score", score).put("correct", correct).put("total", questions.size)
            .put("complete", complete).put("valid", valid).put("criticalFailures", failed)
            .put("passed", complete && failed.length() == 0 && score >= 80)
    }
}

class TrainingSession(val data: JSONObject, private val module: JSONObject) {
    val events get() = data.getJSONArray("events").objects()
    val index get() = data.optInt("index")
    val guided get() = data.getString("kind") == "practice"
    val finished get() = data.optBoolean("finished")
    val questions = module.getJSONArray("questions").objects()
    val current get() = questions[index.coerceAtMost(questions.lastIndex)]
    fun options(): List<JSONObject> = current.getJSONArray("options").objects().shuffled(java.util.Random(data.getLong("seed") + index))
    fun answer(optionId: String): Boolean {
        check(!finished && !data.optBoolean("awaitingContinue"))
        val option = current.getJSONArray("options").objects().first { it.getString("id") == optionId }
        data.getJSONArray("events").put(JSONObject().put("type", "answer").put("sequence", events.size + 1)
            .put("questionId", current.getString("id")).put("optionId", optionId).put("time", System.currentTimeMillis()))
        val ok = option.optBoolean("correct")
        data.put("lastCorrect", ok).put("awaitingContinue", true)
        if (!guided && !ok && current.optBoolean("critical")) finish()
        return ok
    }
    fun advance() {
        check(data.optBoolean("awaitingContinue") && !finished)
        if (index == questions.lastIndex) finish() else data.put("index", index + 1).put("awaitingContinue", false)
    }
    fun finish() { data.put("finished", true).put("endedAt", System.currentTimeMillis()).put("result", Grader.grade(questions, events)) }
    companion object {
        fun create(module: JSONObject, workerId: String, version: String, guided: Boolean, mode: String): TrainingSession = TrainingSession(
            JSONObject().put("id", UUID.randomUUID().toString()).put("workerId", workerId).put("moduleId", module.getString("id"))
                .put("contentVersion", version).put("kind", if(guided) "practice" else "assessment").put("mode", mode)
                .put("seed", System.nanoTime()).put("startedAt", System.currentTimeMillis()).put("index", 0).put("events", JSONArray()), module)
    }
}
