package com.narvyn.suraksha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RoomLearningTest {
    private fun gas(recall:Boolean=true,wrong:Boolean=false,hint:Boolean=false):Pair<RoomMission,RoomCoaching> {
        val mission=RoomMission("gas");val help=RoomCoaching("gas",recall)
        if(wrong)mission.act("enter",0)
        if(hint)help.requestCue("GAS_CHECK",0)
        listOf("inspect-meter","select-specified-ppe","close-barrier","place-attendant-outside",
            "send-buddy-check","confirm-buddy-ack","refuse-entry").forEachIndexed{i,id->assertTrue(mission.act(id,10L+i))}
        return mission to help
    }
    private fun record(id:String,time:Long,worker:String="learner",recall:Boolean=true,wrong:Boolean=false,hint:Boolean=false,camera:Boolean=true):JSONObject {
        val(m,c)=gas(recall,wrong,hint)
        return JSONObject().put("id",id).put("workerId",worker).put("updatedAt",time).put("module","gas")
            .put("mode",if(camera)"camera"else"screen").put("mission",m.toJson()).put("coaching",c.toJson())
    }
    @Test fun bothLanguagesCoverEveryActionAndExplosionVariantChangesExplanation(){
        for(module in listOf("fire","gas"))for(phase in RoomMission.phases(module).filter{it!="COMPLETE"}){
            val l=RoomLearning.forPhase(phase)
            for(hi in listOf(false,true))assertTrue(listOf(l.title,l.why,l.reflect).all{it.local(hi).isNotBlank()})
        }
        assertNotEquals(RoomLearning.forPhase("EQUIPMENT").why,RoomLearning.forPhase("EQUIPMENT",true).why)
        assertTrue(RoomLearning.forPhase("EQUIPMENT",true).why.en.contains("evacuation only"))
    }
    @Test fun reviewUsesActualEventsAndDoesNotMarkSkippedExtinguisherPhasesAsFailures(){
        val m=RoomMission("fire");val c=RoomCoaching("fire",true)
        m.act("alarm",1);m.act("select-blocked-exit",2);c.requestCue("EXIT",2)
        m.act("select-clear-exit",3);m.act("choose-evacuation",4)
        val before=m.toJson().toString();val help=c.toJson().toString()
        val reviews=RoomLearning.review(m,c)
        assertEquals(listOf("ALARM","EXIT","EQUIPMENT"),reviews.map{it.phase})
        assertEquals("EXIT",RoomLearning.priorities(reviews).single().phase)
        assertEquals(1,reviews[1].corrections);assertEquals(1,reviews[1].hints)
        assertEquals(before,m.toJson().toString());assertEquals(help,c.toJson().toString())
    }
    @Test fun scheduleSeparatesWorkersAndPresentationModesAndIgnoresCorruptOrIncompleteRecords(){
        val own=record("ours",100)
        val malformed=record("forged",200).apply{getJSONObject("mission").put("phase","ALARM")}
        val partial=record("partial",300).put("mission",RoomMission("gas").toJson())
        val result=RoomPracticePlanner.plan(listOf(own,record("other",400,worker="other"),malformed,partial,record("screen",500,camera=false)),"learner")
        assertEquals(2,result.size);assertEquals(setOf("ours","screen"),result.map{it.id}.toSet())
        assertEquals(setOf(true,false),result.map{it.camera}.toSet())
    }
    @Test fun scheduleAdvancesOnlyAfterDistinctCompletedRecallAttemptsWithoutHintsOrCorrections(){
        val first=record("first",100)
        assertEquals(100+3*RoomPracticePlanner.DAY,RoomPracticePlanner.plan(listOf(first,first),"learner").single().dueAt)
        val second=record("second",200)
        assertEquals(200+7*RoomPracticePlanner.DAY,RoomPracticePlanner.plan(listOf(first,second),"learner").single().dueAt)
        for(latest in listOf(record("guided",300,recall=false),record("hint",300,hint=true),record("wrong",300,wrong=true))){
            val item=RoomPracticePlanner.plan(listOf(first,second,latest),"learner").single()
            assertEquals(300+RoomPracticePlanner.DAY,item.dueAt);assertEquals(0,item.independentRehearsals)
        }
        assertEquals(listOf("GAS_CHECK"),RoomPracticePlanner.plan(listOf(record("wrong",300,wrong=true)),"learner").single().focus)
    }
}
