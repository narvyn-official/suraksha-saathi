package com.narvyn.suraksha

import org.junit.Assert.*
import org.junit.Test

class RoomCoachingTest {
    @Test fun guidedModeNeverRecordsRecallCueRequests() {
        val coaching=RoomCoaching("fire")
        assertFalse(coaching.recall);assertFalse(coaching.requestCue("ALARM",0));assertFalse(coaching.cueVisible("ALARM",0))
        assertTrue(coaching.cues.isEmpty());assertEquals("guided",coaching.toJson().getString("mode"))
        assertEquals(0,coaching.toJson().getJSONArray("cues").length())
    }
    @Test fun onlyTheModulesActualPhasesCanRequestCues() {
        val fire=RoomCoaching("fire",true);val gas=RoomCoaching("gas",true)
        listOf("ALARM","EXIT","EQUIPMENT","PIN","AIM","SWEEP","WITHDRAW","EVACUATE","ASSEMBLY","REPORT").forEachIndexed { i,phase -> assertTrue(fire.requestCue(phase,i.toLong()));assertFalse(gas.requestCue(phase,i.toLong())) }
        listOf("GAS_CHECK","PPE","BARRIER","ATTENDANT","COMMUNICATE","ACKNOWLEDGE","REFUSE").forEachIndexed { i,phase -> assertTrue(gas.requestCue(phase,i.toLong()));assertFalse(fire.requestCue(phase,i.toLong())) }
        for(coaching in listOf(fire,gas))for(phase in listOf("COMPLETE","","alarm","invented")) {
            assertFalse(coaching.requestCue(phase,100));assertFalse(coaching.cueVisible(phase,100))
        }
        try { RoomCoaching("ppe",true);fail("Unknown module accepted") } catch(_:IllegalArgumentException) {}
    }
    @Test fun expandedJourneyCuesRemainVersionOneMetadataAndForkWithoutLoss() {
        for(module in listOf("fire","gas")) {
            val coaching=RoomCoaching(module,true)
            val phases=RoomMission.phases(module).filterNot { it=="COMPLETE" }
            phases.forEachIndexed { i,phase ->
                assertTrue(coaching.requestCue(phase,i*100L));assertTrue(coaching.cueVisible(phase,i*100L))
            }
            val copy=coaching.fork();coaching.hideCue()
            assertEquals(phases,copy.cues.map { it.phase });assertEquals(phases.size,copy.cueCount)
            assertEquals(1,copy.toJson().getInt("version"));assertEquals("recall",copy.toJson().getString("mode"))
            assertEquals(coaching.toJson().toString(),copy.toJson().toString())
            assertFalse(copy.requestCue("COMPLETE",10_000));assertFalse(copy.cueVisible("COMPLETE",10_000))
        }
    }
    @Test fun cueLastsSevenSecondsWithoutExtendingOnQueriesOrRepeatedRequests() {
        val coaching=RoomCoaching("fire",true)
        assertTrue(coaching.requestCue("PIN",100));assertTrue(coaching.cueVisible("PIN",100))
        assertFalse(coaching.requestCue("PIN",1_000));assertTrue(coaching.cueVisible("PIN",7_099))
        assertFalse(coaching.cueVisible("PIN",7_100));assertEquals(1,coaching.cues.size)
        assertTrue(coaching.requestCue("PIN",7_100));assertEquals(listOf(100L,7_100L),coaching.cues.map { it.at })
    }
    @Test fun movingToAnotherPhaseShowsOnlyTheNewCueAndPreservesBothRequests() {
        val coaching=RoomCoaching("gas",true)
        assertTrue(coaching.requestCue("GAS_CHECK",0));assertTrue(coaching.requestCue("BARRIER",10))
        assertFalse(coaching.cueVisible("GAS_CHECK",10));assertTrue(coaching.cueVisible("BARRIER",10))
        assertEquals(listOf("GAS_CHECK","BARRIER"),coaching.cues.map { it.phase })
    }
    @Test fun hidingCancelsDisplayWithoutErasingTheHistory() {
        val coaching=RoomCoaching("fire",true);assertTrue(coaching.requestCue("AIM",50))
        val recorded=coaching.toJson().toString();coaching.hideCue()
        assertFalse(coaching.cueVisible("AIM",60));assertEquals(recorded,coaching.toJson().toString())
        assertTrue(coaching.requestCue("AIM",60));assertEquals(2,coaching.cues.size)
    }
    @Test fun negativeAndRegressingClocksCannotRecordOrResurrectCues() {
        val coaching=RoomCoaching("fire",true)
        assertFalse(coaching.requestCue("ALARM",-1));assertTrue(coaching.requestCue("ALARM",100))
        assertTrue(coaching.cueVisible("ALARM",200));assertFalse(coaching.requestCue("PIN",199))
        assertFalse(coaching.cueVisible("ALARM",199));assertFalse(coaching.cueVisible("ALARM",-1))
        assertTrue(coaching.requestCue("PIN",200)) // Equal timestamps are nondecreasing and allowed across phases.
        assertFalse(coaching.cueVisible("PIN",7_200));assertFalse(coaching.cueVisible("PIN",201))
        assertEquals(listOf(100L,200L),coaching.cues.map { it.at })
        assertFalse(coaching.requestCue("AIM",201));assertTrue(coaching.requestCue("AIM",7_200))
    }
    @Test fun historyCapCannotBeBypassedByHidingOrExpiration() {
        val coaching=RoomCoaching("fire",true)
        repeat(RoomCoaching.MAX_CUES) { i -> assertTrue(coaching.requestCue(if(i%2==0)"ALARM"else"PIN",i.toLong())) }
        assertEquals(128,coaching.cues.size)
        assertFalse(coaching.requestCue("AIM",10_000));coaching.hideCue();assertFalse(coaching.requestCue("AIM",20_000))
        assertEquals(128,coaching.toJson().getJSONArray("cues").length())
    }
    @Test fun forkDetachesClockDisplayAndHistory() {
        val original=RoomCoaching("gas",true);assertTrue(original.requestCue("GAS_CHECK",100))
        val copy=original.fork();assertEquals(original.toJson().toString(),copy.toJson().toString())
        original.hideCue();assertFalse(original.cueVisible("GAS_CHECK",8_000))
        assertTrue(copy.cueVisible("GAS_CHECK",101));assertTrue(copy.requestCue("BARRIER",102))
        assertEquals(1,original.cues.size);assertEquals(2,copy.cues.size)
        assertTrue(original.requestCue("REFUSE",8_001));assertEquals("BARRIER",copy.cues.last().phase)
    }
    @Test fun publishedSnapshotsCannotMutateHistoryOrExposeDisplayState() {
        val coaching=RoomCoaching("fire",true);assertTrue(coaching.requestCue("ALARM",10))
        val snapshot=coaching.cues
        try { (snapshot as MutableList<RoomCoaching.Cue>).clear();fail("History snapshot was mutable") } catch(_:UnsupportedOperationException) {}
        val json=coaching.toJson();assertEquals(setOf("version","mode","cues"),json.keys().asSequence().toSet())
        assertEquals(1,json.getInt("version"));assertEquals("recall",json.getString("mode"))
        json.getJSONArray("cues").getJSONObject(0).put("phase","COMPLETE").put("at",-1)
        assertEquals(RoomCoaching.Cue("ALARM",10),coaching.cues.single())
        assertTrue(coaching.requestCue("PIN",20));assertEquals(1,snapshot.size)
        assertEquals(2,coaching.toJson().getJSONArray("cues").length())
    }
    @Test fun durationArithmeticDoesNotOverflowNearLongLimit() {
        val coaching=RoomCoaching("fire",true)
        assertTrue(coaching.requestCue("WITHDRAW",Long.MAX_VALUE-6_999))
        assertTrue(coaching.cueVisible("WITHDRAW",Long.MAX_VALUE))
        val expired=RoomCoaching("fire",true)
        assertTrue(expired.requestCue("WITHDRAW",Long.MAX_VALUE-7_000));assertFalse(expired.cueVisible("WITHDRAW",Long.MAX_VALUE))
    }
    @Test fun restoreRetainsDetachedHistoryWithoutRevivingDisplayOrRegressingClock() {
        val original=RoomCoaching("gas",true)
        original.requestCue("PPE",100);original.requestCue("ACKNOWLEDGE",200)
        val snapshot=original.toJson()
        val restored=RoomCoaching.restore(snapshot,"gas")
        assertEquals(original.cues,restored.cues);assertEquals(201L,restored.nextElapsedTime)
        assertFalse(restored.cueVisible("ACKNOWLEDGE",200))
        assertFalse(restored.requestCue("PPE",199))
        assertTrue(restored.requestCue("ACKNOWLEDGE",201))
        assertEquals(3,restored.cueCount)
        snapshot.getJSONArray("cues").getJSONObject(0).put("at",999)
        assertEquals(100L,restored.cues.first().at)
        val guided=RoomCoaching.restore(RoomCoaching("fire").toJson(),"fire")
        assertFalse(guided.recall);assertEquals(0L,guided.nextElapsedTime);assertEquals(0,guided.cueCount)
    }
    @Test fun restoreRejectsMalformedCrossModuleOrUncontinuableCoaching() {
        val original=RoomCoaching("fire",true).apply { requestCue("EXIT",100);requestCue("AIM",200) }
        val edits:List<(org.json.JSONObject)->Unit> = listOf(
            { it.put("version",2) }, { it.put("version",1.0) }, { it.put("mode","guided") },
            { it.put("certifiable",true) }, { it.put("mode","assessment") },
            { it.getJSONArray("cues").getJSONObject(0).put("phase","COMPLETE") },
            { it.getJSONArray("cues").getJSONObject(0).put("phase","PPE") },
            { it.getJSONArray("cues").getJSONObject(0).put("at",-1) },
            { it.getJSONArray("cues").getJSONObject(1).put("at",99) },
            { it.getJSONArray("cues").getJSONObject(1).put("at",Long.MAX_VALUE) },
            { it.getJSONArray("cues").getJSONObject(1).put("at",200.0) },
            { it.getJSONArray("cues").getJSONObject(1).put("unexpected",true) },
            { value -> val cues=value.getJSONArray("cues");repeat(RoomCoaching.MAX_CUES) { cues.put(cues.getJSONObject(1)) } }
        )
        for(edit in edits) {
            try { RoomCoaching.restore(original.toJson().also(edit),"fire");fail("Corrupt coaching resumed") }
            catch(_:IllegalArgumentException) {}
        }
        try { RoomCoaching.restore(original.toJson(),"gas");fail("Wrong module resumed") }
        catch(_:IllegalArgumentException) {}
    }

}
