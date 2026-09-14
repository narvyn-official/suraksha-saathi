package com.narvyn.suraksha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GraderTest {
    private fun q(id: String, critical: Boolean) = JSONObject("""{"id":"$id","critical":$critical,"options":[{"id":"safe","correct":true},{"id":"unsafe"}]}""")
    private fun e(id: String, answer: String) = JSONObject("""{"type":"answer","questionId":"$id","optionId":"$answer"}""")
    @Test fun criticalMistakeOverridesHighScore() {
        val qs=(1..10).map { q("q$it",it==10) }
        val result=Grader.grade(qs,(1..10).map { e("q$it",if(it==10) "unsafe" else "safe") })
        assertEquals(90,result.getInt("score"));assertFalse(result.getBoolean("passed"))
    }
    @Test fun duplicateAnswersCannotCompleteAnAttempt() {
        val result=Grader.grade(listOf(q("a",true),q("b",true)),listOf(e("a","safe"),e("a","safe")))
        assertFalse(result.getBoolean("valid"));assertFalse(result.getBoolean("passed"))
    }
    @Test fun missingAnswerPreventsPassing() { assertFalse(Grader.grade(listOf(q("a",true),q("b",false)),listOf(e("a","safe"))).getBoolean("passed")) }
    @Test fun unknownOptionIsInvalid() { assertFalse(Grader.grade(listOf(q("a",true)),listOf(e("a","other"))).getBoolean("valid")) }
    @Test fun allCorrectPasses() { assertTrue(Grader.grade(listOf(q("a",true),q("b",true)),listOf(e("a","safe"),e("b","safe"))).getBoolean("passed")) }
}
