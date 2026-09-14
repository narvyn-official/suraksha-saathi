package com.narvyn.suraksha
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
class RecallPlannerTest{
 private fun curriculum(version:String="v1",word:String="Choose")=JSONObject("""{"version":"$version","modules":[{"id":"fire","questions":[{"id":"a","prompt":["$word","चुनें"],"critical":true,"options":[{"id":"yes","correct":true},{"id":"no","correct":false}]}]}]}""")
 private fun attempt(id:String="attempt",correct:Boolean=false,kind:String="assessment",time:Long=100)=JSONObject("""{"id":"$id","contentVersion":"v1","moduleId":"fire","kind":"$kind","finished":true,"endedAt":$time,"events":[{"questionId":"a","optionId":"${if(correct)"yes" else "no"}"}]}""")
 private fun plan(a:JSONObject,reviews:Map<String,JSONObject> = emptyMap())=RecallPlanner.plan(curriculum(),mapOf("v1" to curriculum()),listOf(a),reviews)
 @Test fun errorsAreDueNowAndCorrectDecisionsAreSpaced(){assertEquals(100L,plan(attempt()).single().dueAt);assertEquals(100L+RecallPlanner.DAY,plan(attempt(correct=true)).single().dueAt)}
 @Test fun practiceDoesNotCreateAssessmentEvidence(){assertTrue(plan(attempt(kind="practice")).isEmpty())}
 @Test fun completedReviewPersistsItsScheduleWithoutChangingAttempt(){val a=attempt();val before=a.toString();val item=plan(a).single();val saved=RecallPlanner.record(item,true,500);val after=plan(a,mapOf(item.key to saved)).single();assertEquals(1,after.streak);assertEquals(500+RecallPlanner.DAY,after.dueAt);assertEquals(before,a.toString())}
 @Test fun newerAssessmentResetsPriorReview(){val item=plan(attempt()).single();val saved=RecallPlanner.record(item,true,500);val newer=plan(attempt(id="new",time=700),mapOf(item.key to saved)).single();assertEquals(700L,newer.dueAt);assertEquals(0,newer.streak)}
 @Test fun changedContentDoesNotReuseOldAnswer(){assertTrue(RecallPlanner.plan(curriculum("v2","Changed"),mapOf("v1" to curriculum()),listOf(attempt()),emptyMap()).isEmpty());assertEquals(1,RecallPlanner.plan(curriculum("v2"),mapOf("v1" to curriculum()),listOf(attempt()),emptyMap()).size)}
 @Test fun spacingGrowsWithSuccessfulRecallAndResetsOnError(){assertEquals(RecallPlanner.DAY,RecallPlanner.delay(true,1));assertEquals(3*RecallPlanner.DAY,RecallPlanner.delay(true,2));assertEquals(14*RecallPlanner.DAY,RecallPlanner.delay(true,9));assertEquals(600_000L,RecallPlanner.delay(false,9))}
 @Test fun optionSeedChangesAfterReviewButIsStableDuringRound(){val first=plan(attempt()).single();assertEquals(RecallPlanner.optionSeed(first),RecallPlanner.optionSeed(plan(attempt()).single()));val record=RecallPlanner.record(first,false,500);val next=plan(attempt(),mapOf(first.key to record)).single();assertEquals(1,next.round);assertNotEquals(RecallPlanner.optionSeed(first),RecallPlanner.optionSeed(next))}
 @Test fun unchangedQuestionsKeepTheirReviewScheduleAcrossContentVersions(){
  val a=attempt();val item=plan(a).single();val saved=RecallPlanner.record(item,true,500)
  val next=RecallPlanner.plan(curriculum("v2"),mapOf("v1" to curriculum(),"v2" to curriculum("v2")),listOf(a),mapOf(item.key to saved)).single()
  assertEquals("v2:fire:a",next.key);assertEquals(saved.getLong("dueAt"),next.dueAt);assertEquals(1,next.streak);assertEquals(1,next.round)
  val changedArchive=curriculum("v1","Changed")
  assertTrue(RecallPlanner.plan(curriculum("v2"),mapOf("v1" to changedArchive),listOf(a),mapOf(item.key to saved)).isEmpty())
 }
 @Test fun mostRecentCompatibleReviewWinsEvenIfWrittenByAnOlderApp(){
  val a=attempt();val initial=plan(a).single()
  val current=RecallPlanner.plan(curriculum("v2"),mapOf("v1" to curriculum()),listOf(a),emptyMap()).single()
  val currentSuccess=RecallPlanner.record(current,true,500)
  val newerOldVersionError=RecallPlanner.record(initial,false,700)
  val result=RecallPlanner.plan(curriculum("v2"),mapOf("v1" to curriculum()),listOf(a),mapOf(current.key to currentSuccess,initial.key to newerOldVersionError)).single()
  assertEquals(700L+600_000L,result.dueAt);assertEquals(0,result.streak)
 }
}
