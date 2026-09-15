package com.narvyn.suraksha

import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

/** Requested reminders for room recall practice, separate from assessment and certification evidence.
 * Times are explicit elapsed milliseconds. Cue duration is a UI choice, not a safety threshold.
 */
class RoomCoaching(val module:String,val recall:Boolean=false) {
    data class Cue(val phase:String,val at:Long)
    private val allowed=PHASES[module] ?: throw IllegalArgumentException("Unknown room coaching module")
    private val history=ArrayList<Cue>()
    private var visible:Cue?=null
    private var lastClock=-1L

    @get:Synchronized val cueCount:Int get()=history.size

    /** Detached, unmodifiable snapshot; neither list mutation nor JSON mutation can change this history. */
    @get:Synchronized val cues:List<Cue> get()=Collections.unmodifiableList(history.map { it.copy() })

    @Synchronized fun requestCue(phase:String,now:Long):Boolean {
        if(!recall || phase !in allowed || history.size>=MAX_CUES || !observeClock(now))return false
        if(isVisible(phase,now))return false
        val cue=Cue(phase,now)
        history.add(cue);visible=cue
        return true
    }
    @Synchronized fun cueVisible(phase:String,now:Long):Boolean {
        if(!recall || phase !in allowed || !observeClock(now))return false
        return isVisible(phase,now)
    }
    /** Cancel only the current display, for example on pause or phase transition. */
    @Synchronized fun hideCue() { visible=null }

    @Synchronized fun fork():RoomCoaching=RoomCoaching(module,recall).also { copy ->
        history.forEach { copy.history.add(it.copy()) }
        copy.visible=visible?.copy();copy.lastClock=lastClock
    }
    /** Display state is deliberately transient; only requested-cue history is persisted as coaching metadata. */
    @Synchronized fun toJson():JSONObject=JSONObject().put("version",1).put("mode",if(recall)"recall"else"guided")
        .put("cues",JSONArray().apply { history.forEach { put(JSONObject().put("phase",it.phase).put("at",it.at)) } })

    private fun observeClock(now:Long):Boolean {
        if(now<0L || now<lastClock)return false
        lastClock=now;return true
    }
    private fun isVisible(phase:String,now:Long):Boolean {
        val cue=visible ?: return false
        return cue.phase==phase && now>=cue.at && now-cue.at<CUE_DURATION_MS
    }
    companion object {
        const val CUE_DURATION_MS=7_000L
        const val MAX_CUES=128
        private val PHASES=mapOf(
            "fire" to setOf("ALARM","PIN","AIM","SWEEP","WITHDRAW"),
            "gas" to setOf("GAS_CHECK","BARRIER","ATTENDANT","REFUSE")
        )
    }
}
