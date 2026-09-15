package com.narvyn.suraksha

import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

/** Requested reminders for room recall practice, separate from assessment and certification evidence.
 * Times are explicit elapsed milliseconds. Cue duration is a UI choice, not a safety threshold.
 */
class RoomCoaching(val module:String,val recall:Boolean=false) {
    data class Cue(val phase:String,val at:Long)
    private val allowed=RoomMission.phases(module).filterNot { it=="COMPLETE" }.toSet()
    private val history=ArrayList<Cue>()
    private var visible:Cue?=null
    private var lastClock=-1L

    @get:Synchronized val cueCount:Int get()=history.size
    /** Elapsed clock continuation from recorded requests, independent of wall time or cue display. */
    @get:Synchronized val nextElapsedTime:Long get() {
        val last=history.lastOrNull()?.at ?: -1L
        require(last<Long.MAX_VALUE) { "Coaching clock cannot be continued." }
        return last+1L
    }

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
        /** Restore only request history. A previously visible cue is never shown automatically. */
        fun restore(snapshot:JSONObject,module:String):RoomCoaching {
            try {
                require(snapshot.keys().asSequence().toSet()==setOf("version","mode","cues"))
                fun integer(value:Any):Long {
                    require(value is Int || value is Long);return (value as Number).toLong()
                }
                require(integer(snapshot.get("version"))==1L)
                val mode=snapshot.get("mode")
                require(mode=="guided" || mode=="recall")
                val restored=RoomCoaching(module,mode=="recall")
                val cues=snapshot.getJSONArray("cues")
                require(cues.length()<=MAX_CUES && (restored.recall || cues.length()==0))
                var previous=-1L
                for(i in 0 until cues.length()) {
                    val cue=cues.getJSONObject(i)
                    require(cue.keys().asSequence().toSet()==setOf("phase","at"))
                    val phase=cue.get("phase") as String
                    val at=integer(cue.get("at"))
                    require(phase in restored.allowed && at>=0L && at>=previous && at<Long.MAX_VALUE)
                    restored.history.add(Cue(phase,at));previous=at
                }
                restored.lastClock=previous
                return restored
            } catch(error:Exception) {
                throw IllegalArgumentException("Room coaching snapshot is not safely resumable.",error)
            }
        }
        const val CUE_DURATION_MS=7_000L
        const val MAX_CUES=128
    }
}
