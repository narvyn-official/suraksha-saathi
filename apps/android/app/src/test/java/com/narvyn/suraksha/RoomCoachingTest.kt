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
        listOf("ALARM","PIN","AIM","SWEEP","WITHDRAW").forEachIndexed { i,phase -> assertTrue(fire.requestCue(phase,i.toLong()));assertFalse(gas.requestCue(phase,i.toLong())) }
        listOf("GAS_CHECK","BARRIER","ATTENDANT","REFUSE").forEachIndexed { i,phase -> assertTrue(gas.requestCue(phase,i.toLong()));assertFalse(fire.requestCue(phase,i.toLong())) }
        for(coaching in listOf(fire,gas))for(phase in listOf("COMPLETE","","alarm","invented")) {
            assertFalse(coaching.requestCue(phase,100));assertFalse(coaching.cueVisible(phase,100))
        }
        try { RoomCoaching("ppe",true);fail("Unknown module accepted") } catch(_:IllegalArgumentException) {}
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
}
