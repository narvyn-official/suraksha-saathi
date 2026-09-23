package com.narvyn.suraksha

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.min

/**
 * Virtual mission interactions derived from ProcedureCatalog's fire and confined-space scenarios.
 * Fire requires a clear-exit choice, then a suitable scenario extinguisher or an evacuation-only choice.
 * Extinguisher actions are solely a simulated authorised role, never authorisation for the learner.
 * Both fire paths end with a designated route, assembly and a missing-person report without re-entry.
 * An announced explosion-risk variant permits evacuation only; the app does not detect explosion risk.
 * Gas remains outside: PPE is a draft scenario kit for that role, the SIM meter is not a reading,
 * buddy acknowledgement does not establish rescue readiness, and entry is never authorised.
 * General training references checked 2026-09-16, not Indian statutory authority:
 * https://www.osha.gov/etools/evacuation-plans-procedures/eap/elements
 * https://www.osha.gov/laws-regs/regulations/standardnumber/1910/1910.146
 * https://www.osha.gov/sites/default/files/publications/OSHA3527.pdf
 * Timing and coordinate tolerances below are game interaction settings, not operating standards.
 * Call on one owner thread, using the same monotonic millisecond clock for controls and frames.
 */
class RoomMission(val module: String, val explosionRisk: Boolean = false) {
    init { require(!explosionRisk || module == "fire") { "Explosion-risk variant is a fire scenario." } }
    data class Event(val id: String, val phase: String, val at: Long, val accepted: Boolean, val reason: String)
    data class Measurement(val kind: String, val startedAt: Long, val completedAt: Long,
                           val samples: Int, val maxGapMs: Long, val bins: List<Int> = emptyList()) {
        val durationMs get() = completedAt - startedAt
    }

    private val phaseSequence = phases(module)
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
    var evacuationOnly = false
        private set
    var lastInterruption: String? = null
        private set

    /** First elapsed millisecond after persisted evidence; transient frame clocks are not journalled. */
    val nextElapsedTime: Long get() {
        val last = journal.maxOfOrNull { it.at } ?: -1L
        require(last < Long.MAX_VALUE) { "Mission clock cannot be continued." }
        return last + 1L
    }

    val phase get() = phaseSequence[phaseIndex]
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
    val overallProgress: Float get() = if (completed) 1f else (phaseIndex + progress) / (phaseSequence.size - 1)

    /** Returns acceptance, not completion. Known out-of-order/unsafe controls are journalled. */
    fun act(id: String, now: Long): Boolean {
        if (completed || id !in CONTROLS || now < 0 || now < latestTime) return false
        latestTime = now
        val before = phase
        val accepted = when (id) {
            "alarm" -> phase == "ALARM"
            "select-clear-exit" -> phase == "EXIT"
            "select-suitable-extinguisher" -> phase == "EQUIPMENT" && !explosionRisk
            "choose-evacuation" -> phase == "EQUIPMENT"
            "pin-drag" -> phase == "PIN"
            "release" -> phase == "SWEEP" || (phase == "WITHDRAW" && !released)
            "withdraw" -> phase == "WITHDRAW" && released
            "follow-clear-route" -> phase == "EVACUATE"
            "reach-assembly-point" -> phase == "ASSEMBLY"
            "report-missing-worker" -> phase == "REPORT"
            "inspect-meter" -> phase == "GAS_CHECK"
            "select-specified-ppe" -> phase == "PPE"
            "close-barrier" -> phase == "BARRIER"
            "place-attendant-outside" -> phase == "ATTENDANT"
            "send-buddy-check" -> phase == "COMMUNICATE"
            "confirm-buddy-ack" -> phase == "ACKNOWLEDGE"
            "refuse-entry" -> phase == "REFUSE"
            else -> false
        }
        journal.add(Event(id, before, now, accepted, when {
            accepted -> "intentional-control"
            id in UNSAFE_CONTROLS || (id == "select-suitable-extinguisher" && phase == "EQUIPMENT" && explosionRisk) -> "unsafe-control"
            id == "withdraw" && phase == "WITHDRAW" -> "release-required"
            else -> "out-of-order"
        }))
        if (!accepted) return false
        if (id == "choose-evacuation") {
            evacuationOnly = true
            resetIncomplete(); lastInterruption = null
            phaseIndex = phaseSequence.indexOf("EVACUATE")
        } else if (id == "release") {
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
    fun fork(): RoomMission = RoomMission(module, explosionRisk).also { copy ->
        copy.phaseIndex = phaseIndex
        copy.journal.addAll(journal)
        copy.measured.addAll(measured.map { it.copy(bins = it.bins.toList()) })
        copy.latestTime = latestTime; copy.latestAimTime = latestAimTime
        copy.startedAt = startedAt; copy.sampledAt = sampledAt
        copy.sampleCount = sampleCount; copy.maxGap = maxGap
        copy.visitedBins.addAll(visitedBins); copy.lastBin = lastBin
        copy.released = released; copy.evacuationOnly = evacuationOnly
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

    /** Restorable v2 audit snapshot; presentation/worker binding and atomic persistence belong to the host. */
    fun toJson(): JSONObject = JSONObject().put("version", VERSION).put("module", module)
        .put("phase", phase).put("completed", completed).put("progress", progress.toDouble())
        .put("overallProgress", overallProgress.toDouble()).put("released", released).put("evacuationOnly", evacuationOnly)
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
        .put("scenario", JSONObject().put("simulated", true).put("catalogVersion", 1)
            .apply { if (module == "gas") {
                put("meter", "SIM").put("liveReading", false).put("rescueReadiness", "unconfirmed").put("entryAuthorised", false)
                put("ppe", "draft-scenario-kit-for-outside-role").put("ppeAuthorisesEntry", false)
                put("buddyCommunication", if (accepted("confirm-buddy-ack")) "scenario-acknowledged" else "unconfirmed")
            } else {
                put("role", "scenario-authorised").put("realAuthorisationGranted", false)
                put("explosionRisk", explosionRisk)
                if (explosionRisk) put("explosionRiskSource", "scenario-announced")
                put("equipment", if (evacuationOnly) "not-used" else if (accepted("select-suitable-extinguisher")) "scenario-suitable-selected" else "selection-pending")
                put("retreatPath", if (accepted("select-clear-exit")) "scenario-clear-selected" else "selection-pending")
                put("missingPerson", "scenario-colleague-unaccounted-for").put("reentryAuthorised", false)
            } })
        .put("result", JSONObject().put("complete", completed).put("certifiable", false).put("practical", "not-assessed")
            .put("outcome", if (!completed) "in-progress" else if (module == "gas") "outside-entry-refused" else if (evacuationOnly) "assembled-reported-without-discharge" else "assembled-reported-after-worsening"))

    private fun accepted(id: String) = journal.any { it.id == id && it.accepted }

    companion object {
        const val VERSION = 2
        const val MAX_RESTORE_EVENTS = 4096

        /**
         * Restores accepted stages from a bounded v2 journal, never camera anchors or partial gestures.
         * Control events are replayed through act(); measured transitions use validated original summaries
         * without inventing frame samples. Summaries establish internal consistency, not proof of real work.
         * The host must bind the worker, presentation and scenario, and require a fresh safe setup.
         * Unsupported/invalid snapshots throw IllegalArgumentException; v1 remains read-only history.
         */
        fun restore(snapshot: JSONObject): RoomMission {
            try {
                keys(snapshot, setOf("version", "module", "phase", "completed", "progress", "overallProgress",
                    "released", "evacuationOnly", "lastInterruption", "events", "measurements", "scenario", "result"))
                require(integer(snapshot.get("version")) == VERSION.toLong())
                val module = snapshot.get("module") as? String ?: throw IllegalArgumentException("Missing module.")
                val scenario = snapshot.getJSONObject("scenario")
                val explosion = if (module == "fire") boolean(scenario.get("explosionRisk")) else false
                val restored = RoomMission(module, explosion)
                val events = snapshot.getJSONArray("events")
                val measurements = snapshot.getJSONArray("measurements")
                require(events.length() <= MAX_RESTORE_EVENTS && measurements.length() <= 2)
                var measurementIndex = 0
                var gestureEligibleAt = 0L
                var lastMeasuredFrame = -1L
                for (i in 0 until events.length()) {
                    val value = events.getJSONObject(i)
                    keys(value, setOf("id", "phase", "at", "accepted", "reason"))
                    val event = Event(value.get("id") as String, value.get("phase") as String,
                        integer(value.get("at")), boolean(value.get("accepted")), value.get("reason") as String)
                    require(event.at >= 0L && event.at < Long.MAX_VALUE && event.at >= restored.latestTime)
                    require(!restored.completed && event.phase == restored.phase)
                    if (event.id == "base-alignment" || event.id == "base-sweep") {
                        val expectedKind = when (restored.phase) { "AIM" -> "base-alignment"; "SWEEP" -> "base-sweep"; else -> "" }
                        require(event.id == expectedKind && event.accepted && event.reason == "virtual-measurement")
                        require(measurementIndex < measurements.length())
                        val evidence = readMeasurement(measurements.getJSONObject(measurementIndex++))
                        require(evidence.kind == event.id && evidence.completedAt == event.at)
                        require(evidence.startedAt >= gestureEligibleAt && evidence.startedAt > lastMeasuredFrame)
                        restored.measured.add(evidence)
                        restored.journal.add(event)
                        restored.latestTime = event.at; restored.latestAimTime = event.at
                        lastMeasuredFrame = event.at
                        restored.advance()
                        gestureEligibleAt = event.at
                    } else {
                        val previousPhase = restored.phase
                        val previousCount = restored.journal.size
                        restored.act(event.id, event.at)
                        require(restored.journal.size == previousCount + 1 && restored.journal.last() == event)
                        if (restored.phase != previousPhase || (event.id == "release" && event.accepted)) {
                            gestureEligibleAt = event.at
                        }
                    }
                }
                require(measurementIndex == measurements.length())
                require(snapshot.get("phase") == restored.phase)
                require(boolean(snapshot.get("completed")) == restored.completed)
                require(boolean(snapshot.get("released")) == restored.released)
                require(boolean(snapshot.get("evacuationOnly")) == restored.evacuationOnly)
                val progress = finiteNumber(snapshot.get("progress"))
                val overall = finiteNumber(snapshot.get("overallProgress"))
                require(progress in 0.0..1.0)
                when (restored.phase) {
                    "AIM" -> require(progress < 1.0)
                    "SWEEP" -> Unit // Full coverage can precede arrival back at the opposite edge.
                    else -> require(abs(progress - restored.progress.toDouble()) < 0.000001)
                }
                val expectedOverall = if (restored.completed) 1.0 else
                    (restored.phaseIndex + progress) / (restored.phaseSequence.size - 1)
                require(abs(overall - expectedOverall) < 0.000001)
                val interruption = snapshot.get("lastInterruption")
                require(interruption == JSONObject.NULL || (restored.phase in setOf("AIM", "SWEEP") &&
                    interruption in setOf("clock-invalid", "stale-frame", "off-base", "released", "frame-gap", "wrong-start-edge", "skipped-band")))
                val expected = restored.toJson()
                require(sameObject(scenario, expected.getJSONObject("scenario")))
                require(sameObject(snapshot.getJSONObject("result"), expected.getJSONObject("result")))
                restored.resetIncomplete(); restored.lastInterruption = null
                restored.nextElapsedTime // Reject a clock that cannot safely be incremented.
                return restored
            } catch (error: Exception) {
                throw IllegalArgumentException("Room mission snapshot is not safely resumable.", error)
            }
        }

        private fun readMeasurement(value: JSONObject): Measurement {
            keys(value, setOf("kind", "startedAt", "completedAt", "durationMs", "samples", "maxGapMs", "bins"))
            val kind = value.get("kind") as String
            val start = integer(value.get("startedAt")); val end = integer(value.get("completedAt"))
            val samples = integer(value.get("samples")); val gap = integer(value.get("maxGapMs"))
            require(start >= 0L && end >= start && end < Long.MAX_VALUE)
            val duration = end - start
            require(integer(value.get("durationMs")) == duration)
            require(samples in 2L..Int.MAX_VALUE.toLong() && gap in 1L..MAX_GAP_MS)
            val intervals = samples - 1L
            // Distinct millisecond frames, an actually observed maximum gap, and no hidden long gap.
            require(duration >= gap + intervals - 1L && duration <= intervals * gap)
            val rawBins = value.getJSONArray("bins")
            require(rawBins.length() <= 5)
            val bins = (0 until rawBins.length()).map { index ->
                val bin = integer(rawBins.get(index)); require(bin in 0L..4L); bin.toInt()
            }
            when (kind) {
                "base-alignment" -> require(bins.isEmpty() && duration >= ALIGN_MS && duration - gap < ALIGN_MS)
                "base-sweep" -> require(duration >= SWEEP_MS && samples >= 5 &&
                    (bins == listOf(0, 1, 2, 3, 4) || bins == listOf(4, 3, 2, 1, 0)))
                else -> throw IllegalArgumentException("Unknown measurement.")
            }
            return Measurement(kind, start, end, samples.toInt(), gap, bins)
        }
        private fun integer(value: Any): Long {
            require(value is Int || value is Long) { "Expected integer JSON value." }
            return (value as Number).toLong()
        }
        private fun boolean(value: Any): Boolean {
            require(value is Boolean) { "Expected boolean JSON value." }; return value
        }
        private fun finiteNumber(value: Any): Double {
            require(value is Number); return value.toDouble().also { require(it.isFinite()) }
        }
        private fun keys(value: JSONObject, expected: Set<String>) {
            require(value.keys().asSequence().toSet() == expected) { "Unexpected snapshot fields." }
        }
        private fun sameObject(actual: JSONObject, expected: JSONObject): Boolean {
            if (actual.keys().asSequence().toSet() != expected.keys().asSequence().toSet()) return false
            return expected.keys().asSequence().all { key ->
                val a = actual.get(key); val e = expected.get(key)
                if (a is Number && e is Number) {
                    (a is Int || a is Long) && a.toLong() == e.toLong()
                } else a == e
            }
        }
        /** Full ordered journey, including COMPLETE. The fire evacuation branch skips manipulation phases. */
        fun phases(module: String): List<String> = when (module) {
            "fire" -> listOf("ALARM", "EXIT", "EQUIPMENT", "PIN", "AIM", "SWEEP", "WITHDRAW", "EVACUATE", "ASSEMBLY", "REPORT", "COMPLETE")
            "gas" -> listOf("GAS_CHECK", "PPE", "BARRIER", "ATTENDANT", "COMMUNICATE", "ACKNOWLEDGE", "REFUSE", "COMPLETE")
            else -> throw IllegalArgumentException("Unsupported room mission: $module")
        }
        const val ALIGN_MS = 300L
        const val SWEEP_MS = 1500L
        const val MAX_GAP_MS = 150L
        const val BASE_TOLERANCE = .25f
        const val CENTRE_TOLERANCE = .2f
        // select-dust-mask means claiming a dust mask permits entry into an unknown atmosphere, not ordinary dust PPE use.
        private val UNSAFE_CONTROLS = setOf("enter", "place-attendant-inside", "continue-discharge",
            "select-blocked-exit", "select-unsuitable-extinguisher", "operate-without-training", "follow-blocked-route",
            "enter-smoke", "leave-without-rollcall", "reenter-search", "select-dust-mask", "skip-buddy-check", "proceed-without-ack")
        private val CONTROLS = setOf("alarm", "select-clear-exit", "select-suitable-extinguisher", "choose-evacuation",
            "pin-drag", "release", "withdraw", "follow-clear-route", "reach-assembly-point", "report-missing-worker",
            "inspect-meter", "select-specified-ppe", "close-barrier", "place-attendant-outside", "send-buddy-check",
            "confirm-buddy-ack", "refuse-entry") + UNSAFE_CONTROLS
    }
}
