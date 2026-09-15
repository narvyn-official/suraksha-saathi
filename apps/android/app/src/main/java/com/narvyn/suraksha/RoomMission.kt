package com.narvyn.suraksha

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.min

/**
 * Virtual mission interactions derived from ProcedureCatalog's fire and confined-space scenarios.
 * Fire assumes the scenario's authorised role, suitable extinguisher and clear retreat path.
 * Gas remains outside: the SIM meter is not a reading and rescue readiness is unconfirmed.
 * Timing and coordinate tolerances below are game interaction settings, not operating standards.
 * Call on one owner thread, using the same monotonic millisecond clock for controls and frames.
 */
class RoomMission(val module: String) {
    data class Event(val id: String, val phase: String, val at: Long, val accepted: Boolean, val reason: String)
    data class Measurement(val kind: String, val startedAt: Long, val completedAt: Long,
                           val samples: Int, val maxGapMs: Long, val bins: List<Int> = emptyList()) {
        val durationMs get() = completedAt - startedAt
    }

    private val phases = when (module) {
        "fire" -> listOf("ALARM", "PIN", "AIM", "SWEEP", "WITHDRAW", "COMPLETE")
        "gas" -> listOf("GAS_CHECK", "BARRIER", "ATTENDANT", "REFUSE", "COMPLETE")
        else -> throw IllegalArgumentException("Unsupported room mission: $module")
    }
    private var phaseIndex = 0
    private val journal = mutableListOf<Event>()
    private val measured = mutableListOf<Measurement>()
    private var latestTime = -1L
    private var latestAimTime = -1L
    private var startedAt: Long? = null
    private var sampledAt: Long? = null
    private var sampleCount = 0
    private var maxGap = 0L
    private val visitedBins = mutableListOf<Int>()
    private var lastBin = -1
    private var released = false
    var lastInterruption: String? = null
        private set

    val phase get() = phases[phaseIndex]
    val completed get() = phase == "COMPLETE"
    val events: List<Event> get() = journal.toList()
    val measurements: List<Measurement> get() = measured.map { it.copy(bins = it.bins.toList()) }
    /** Progress of the current action. Completed phase transitions never rewind on pause. */
    val progress: Float get() = when (phase) {
        "AIM" -> (duration().toFloat() / ALIGN_MS).coerceIn(0f, 1f)
        "SWEEP" -> min(duration().toFloat() / SWEEP_MS, visitedBins.size / 5f).coerceIn(0f, 1f)
        "WITHDRAW" -> if (released) .5f else 0f
        "COMPLETE" -> 1f
        else -> 0f
    }
    val overallProgress: Float get() = if (completed) 1f else (phaseIndex + progress) / (phases.size - 1)

    /** Returns acceptance, not completion. Known out-of-order/unsafe controls are journalled. */
    fun act(id: String, now: Long): Boolean {
        if (completed || id !in CONTROLS || now < 0 || now < latestTime) return false
        latestTime = now
        val before = phase
        val accepted = when (id) {
            "alarm" -> phase == "ALARM"
            "pin-drag" -> phase == "PIN"
            "release" -> phase == "SWEEP" || (phase == "WITHDRAW" && !released)
            "withdraw" -> phase == "WITHDRAW" && released
            "inspect-meter" -> phase == "GAS_CHECK"
            "close-barrier" -> phase == "BARRIER"
            "place-attendant-outside" -> phase == "ATTENDANT"
            "refuse-entry" -> phase == "REFUSE"
            else -> false
        }
        journal.add(Event(id, before, now, accepted, when {
            accepted -> "intentional-control"
            id in UNSAFE_CONTROLS -> "unsafe-control"
            id == "withdraw" && phase == "WITHDRAW" -> "release-required"
            else -> "out-of-order"
        }))
        if (!accepted) return false
        if (id == "release") {
            resetIncomplete()
            if (phase == "WITHDRAW") { released = true; lastInterruption = null }
            else lastInterruption = "released"
        } else advance()
        return true
    }

    /**
     * Target-local coordinates: base y=0, horizontal range [-1,1]. Returns true only when
     * a measured phase completes. Repeated timestamps, stale/invalid frames and release
     * discard partial evidence; callers must also invoke resetIncomplete on pause.
     */
    fun aim(x: Float, y: Float, held: Boolean, now: Long, fresh: Boolean): Boolean {
        if (completed || module != "fire" || phase !in setOf("AIM", "SWEEP")) return false
        val clockInvalid = now < 0 || now < latestTime || now < latestAimTime
        val repeatedFrame = now == latestAimTime
        if (now >= 0) { latestTime = maxOf(latestTime, now); latestAimTime = maxOf(latestAimTime, now) }
        val interruption = when {
            clockInvalid -> "clock-invalid"
            !fresh || repeatedFrame -> "stale-frame"
            !x.isFinite() || !y.isFinite() || x !in -1f..1f || abs(y) > BASE_TOLERANCE ||
                (phase == "AIM" && abs(x) > CENTRE_TOLERANCE) -> "off-base"
            phase == "SWEEP" && !held -> "released"
            else -> null
        }
        if (interruption != null) {
            resetIncomplete(); lastInterruption = interruption; return false
        }
        val previous = sampledAt
        val frameGap = previous != null && now - previous > MAX_GAP_MS
        if (frameGap) { resetIncomplete(); lastInterruption = "frame-gap" }
        if (phase == "AIM") {
            if (!frameGap) lastInterruption = null
            sample(now)
            if (duration() < ALIGN_MS) return false
            finishMeasurement("base-alignment", now)
            return true
        }

        val bin = (((x + 1f) * 2.5f).toInt()).coerceIn(0, 4)
        // Start at either side. A jump across unobserved bins cannot manufacture coverage.
        if (startedAt == null) {
            if (bin != 0 && bin != 4) {
                if (!frameGap) lastInterruption = "wrong-start-edge"
                return false
            }
            visitedBins.add(bin)
        } else if (abs(bin - lastBin) > 1) {
            resetIncomplete(); lastInterruption = "skipped-band"; return false
        } else if (bin !in visitedBins) visitedBins.add(bin)
        // A gap already restarted this sample; expose its reason until the next valid sample.
        if (!frameGap) lastInterruption = null
        lastBin = bin
        sample(now)
        if (visitedBins.size != 5 || duration() < SWEEP_MS || bin != 4 - visitedBins.first()) return false
        finishMeasurement("base-sweep", now)
        // The next phase deliberately introduces worsening conditions, never a fire-out claim.
        return true
    }

    /** Clears only an incomplete dwell/trajectory; accepted controls and completed phases survive. */
    fun resetIncomplete() {
        startedAt = null; sampledAt = null; sampleCount = 0; maxGap = 0
        visitedBins.clear(); lastBin = -1
    }

    /** Owner-thread transaction candidate; no mutable runtime journal or trajectory is shared. */
    fun fork(): RoomMission = RoomMission(module).also { copy ->
        copy.phaseIndex = phaseIndex
        copy.journal.addAll(journal)
        copy.measured.addAll(measured.map { it.copy(bins = it.bins.toList()) })
        copy.latestTime = latestTime; copy.latestAimTime = latestAimTime
        copy.startedAt = startedAt; copy.sampledAt = sampledAt
        copy.sampleCount = sampleCount; copy.maxGap = maxGap
        copy.visitedBins.addAll(visitedBins); copy.lastBin = lastBin
        copy.released = released
        copy.lastInterruption = lastInterruption
    }

    private fun duration() = if (startedAt == null || sampledAt == null) 0L else sampledAt!! - startedAt!!
    private fun sample(now: Long) {
        if (startedAt == null) startedAt = now
        sampledAt?.let { maxGap = maxOf(maxGap, now - it) }
        sampledAt = now; sampleCount++
    }
    private fun advance() { resetIncomplete(); lastInterruption = null; phaseIndex++ }
    private fun finishMeasurement(kind: String, now: Long) {
        measured.add(Measurement(kind, startedAt!!, now, sampleCount, maxGap, visitedBins.toList()))
        journal.add(Event(kind, phase, now, true, "virtual-measurement"))
        advance()
    }

    /** Audit snapshot only; presentation/worker binding and atomic persistence belong to the host. */
    fun toJson(): JSONObject = JSONObject().put("version", VERSION).put("module", module)
        .put("phase", phase).put("completed", completed).put("progress", progress.toDouble())
        .put("overallProgress", overallProgress.toDouble()).put("released", released)
        .put("lastInterruption", lastInterruption ?: JSONObject.NULL)
        .put("events", JSONArray().apply { journal.forEach { event ->
            put(JSONObject().put("id", event.id).put("phase", event.phase).put("at", event.at)
                .put("accepted", event.accepted).put("reason", event.reason))
        } })
        .put("measurements", JSONArray().apply { measured.forEach { value ->
            put(JSONObject().put("kind", value.kind).put("startedAt", value.startedAt).put("completedAt", value.completedAt)
                .put("durationMs", value.durationMs).put("samples", value.samples).put("maxGapMs", value.maxGapMs)
                .put("bins", JSONArray(value.bins)))
        } })
        .put("scenario", JSONObject().put("simulated", true).put("catalogVersion", ProcedureCatalog.VERSION)
            .apply { if (module == "gas") put("meter", "SIM").put("liveReading", false).put("rescueReadiness", "unconfirmed").put("entryAuthorised", false)
                else put("role", "scenario-authorised").put("equipment", "scenario-suitable").put("retreatPath", "scenario-clear") })
        .put("result", JSONObject().put("complete", completed).put("certifiable", false).put("practical", "not-assessed")
            .put("outcome", if (!completed) "in-progress" else if (module == "gas") "outside-entry-refused" else "withdrawn-after-worsening"))

    companion object {
        const val VERSION = 1
        const val ALIGN_MS = 300L
        const val SWEEP_MS = 1500L
        const val MAX_GAP_MS = 150L
        const val BASE_TOLERANCE = .25f
        const val CENTRE_TOLERANCE = .2f
        private val UNSAFE_CONTROLS = setOf("enter", "place-attendant-inside", "continue-discharge")
        private val CONTROLS = setOf("alarm", "pin-drag", "release", "withdraw", "inspect-meter", "close-barrier",
            "place-attendant-outside", "refuse-entry") + UNSAFE_CONTROLS
    }
}
