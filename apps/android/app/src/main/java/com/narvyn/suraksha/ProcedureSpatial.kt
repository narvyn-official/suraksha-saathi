package com.narvyn.suraksha

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

/** Tabletop geometry tolerances are interaction settings, never safe operating distances. */
object ProcedureSpatial {
    const val VERSION = 1
    const val HOLD_MS = 650L
    const val MAX_GAP_MS = 150L
    data class Zone(val id: String, val center: FloatArray, val radius: Float)
    data class Hit(val id: String, val error: Float)
    fun supported(step: String) = step in setOf("fire-aim", "fire-sweep-left", "fire-sweep-right", "fire-sweep-return", "gas-attendant")
    fun zones(step: String): List<Zone> {
        if (!supported(step)) return emptyList()
        if (step == "gas-attendant") return listOf(
            Zone(step, floatArrayOf(-.365f,.05f,.22f),.085f),
            Zone("$step-unsafe",floatArrayOf(.265f,.05f,-.205f),.085f))
        val x=when(step) { "fire-sweep-left", "fire-sweep-return" -> -.24f; "fire-sweep-right" -> .20f; else -> -.12f }
        return listOf(Zone(step,floatArrayOf(x,.065f,-.27f),.075f),Zone("$step-unsafe",floatArrayOf(x,.32f,-.27f),.075f))
    }
    /** Pick the nearest virtual sphere along a local-space ray, using an inverse model-view-projection matrix. */
    fun hit(x: Float, y: Float, width: Int, height: Int, inverse: FloatArray, zones: List<Zone>): Hit? {
        if(width<=0 || height<=0 || !x.isFinite() || !y.isFinite() || x !in 0f..width.toFloat() || y !in 0f..height.toFloat() || inverse.size!=16 || inverse.any { !it.isFinite() }) return null
        fun unproject(z:Float):FloatArray? {
            val v=floatArrayOf(2*x/width-1,1-2*y/height,z,1f)
            val p=FloatArray(4) { r -> (0..3).sumOf { c -> (inverse[c*4+r]*v[c]).toDouble() }.toFloat() }
            if(p.any { !it.isFinite() } || kotlin.math.abs(p[3])<1e-7f)return null
            return FloatArray(3) { p[it]/p[3] }.takeIf { it.all(Float::isFinite) }
        }
        val start=unproject(-1f)?:return null;val end=unproject(1f)?:return null
        val delta=FloatArray(3) { end[it]-start[it] };val length=sqrt(delta.sumOf { (it*it).toDouble() }).toFloat()
        if(!length.isFinite() || length<1e-7f)return null
        val direction=FloatArray(3) { delta[it]/length }
        return zones.mapNotNull { zone ->
            val offset=FloatArray(3) { zone.center[it]-start[it] }
            val along=(0..2).sumOf { (offset[it]*direction[it]).toDouble() }.toFloat()
            val distance2=(0..2).sumOf { val d=offset[it]-direction[it]*along;(d*d).toDouble() }.toFloat()
            val radius2=zone.radius*zone.radius
            if(along<=0 || along>=length || distance2>radius2) null
            else (along-sqrt((radius2-distance2).coerceAtLeast(0f))) to Hit(zone.id,sqrt(distance2.coerceAtLeast(0f))/zone.radius)
        }.minByOrNull { it.first }?.second
    }
    data class Sample(val elapsed: Long, val error: Float)
    data class Proof(val action: String, val samples: List<Sample>) {
        fun json()=JSONObject().put("version",VERSION).put("kind","target-hold").put("action",action)
            .put("samples",JSONArray().apply { samples.forEach { put(JSONObject().put("elapsedMs",it.elapsed).put("normalisedError",it.error.toDouble())) } })
    }
    fun proof(json: JSONObject, step: String, action: String, presentation: String): Proof {
        require(presentation in setOf("camera","screen") && supported(step) && zones(step).any { it.id==action })
        require(json.getInt("version")==VERSION && json.getString("kind")=="target-hold" && json.getString("action")==action)
        val raw=json.getJSONArray("samples");require(raw.length() in 6..100)
        val samples=(0 until raw.length()).map { raw.getJSONObject(it).let { s -> Sample(s.getLong("elapsedMs"),s.getDouble("normalisedError").toFloat()) } }
        require(samples.first().elapsed==0L && samples.last().elapsed in HOLD_MS..(HOLD_MS+MAX_GAP_MS))
        samples.forEachIndexed { i,s -> require(s.error.isFinite() && s.error in 0f..1f);if(i>0)require(s.elapsed-samples[i-1].elapsed in 1..MAX_GAP_MS) }
        return Proof(action,samples)
    }
}

/** Runtime-only hold; lifecycle/mode/viewport changes discard it. No timers can manufacture valid frames. */
class ProcedureSpatialHold {
    private var id: String?=null
    private var started=0L
    private var last=0L
    private var samples=mutableListOf<ProcedureSpatial.Sample>()
    @Synchronized fun reset() { id=null;samples.clear();started=0;last=0 }
    @Synchronized fun sample(hit: ProcedureSpatial.Hit?, now: Long): ProcedureSpatial.Proof? {
        if(hit==null || now<0 || !hit.error.isFinite() || hit.error !in 0f..1f) { reset();return null }
        if(id!=hit.id || now<last || now-last>ProcedureSpatial.MAX_GAP_MS) { reset();id=hit.id;started=now;last=now;samples.add(ProcedureSpatial.Sample(0,hit.error));return null }
        if(now==last)return null // repeated camera images cannot grow the journal
        last=now
        // Keep samples bounded even on high refresh-rate displays.
        if(now-started<ProcedureSpatial.HOLD_MS && now-started-samples.last().elapsed<16)return null
        samples.add(ProcedureSpatial.Sample(now-started,hit.error))
        if(now-started<ProcedureSpatial.HOLD_MS)return null
        val proof=ProcedureSpatial.Proof(hit.id,samples.toList());reset();return proof
    }
    @Synchronized fun progress(): Float = if(samples.isEmpty())0f else (samples.last().elapsed.toFloat()/ProcedureSpatial.HOLD_MS).coerceIn(0f,1f)
}
