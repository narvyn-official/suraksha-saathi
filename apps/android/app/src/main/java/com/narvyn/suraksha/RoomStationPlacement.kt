package com.narvyn.suraksha

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Placement constraints for the virtual room scene, in metres. These are simulation layout rules,
 * not operational separation distances, a real-floor clearance check, or a declaration of safety.
 * Callers must independently require a fresh, tracked plane hit and tracked existing anchors.
 * Station order is equipment, hazard, then the green outside/withdrawal marker.
 */
object RoomStationPlacement {
    data class Station(val x: Float, val z: Float, val yawDegrees: Float = 0f)
    enum class Reason { READY, INVALID_INPUT, ALL_STATIONS_PLACED, TOO_CLOSE, GAS_WRONG_SIDE, GAS_FOOTPRINT_OVERLAP }
    data class Decision(val allowed: Boolean, val reason: Reason)

    // Match RoomMissionGeometry's safePoint ring and confinedZone warning ring.
    private const val GREEN_RADIUS = .45
    private const val GAS_OPENING_Z = -.55
    private const val GAS_WARNING_RADIUS = .54

    fun evaluate(module: String, existing: List<Station>, candidate: Station): Decision {
        if (module !in setOf("fire", "gas") || !candidate.finite() || existing.any { !it.finite() })
            return reject(Reason.INVALID_INPUT)
        if (existing.size >= 3) return reject(Reason.ALL_STATIONS_PLACED)

        val green = existing.size == 2
        for ((index, station) in existing.withIndex()) {
            val minimum = if (green && index == 1) 1.0 else .5
            if (hypot(candidate.x.toDouble() - station.x, candidate.z.toDouble() - station.z) <= minimum)
                return reject(Reason.TOO_CLOSE)
        }

        if (module == "gas" && green) {
            val hazard = existing[1]
            val yaw = Math.toRadians(hazard.yawDegrees.toDouble() % 360.0)
            val dx = candidate.x.toDouble() - hazard.x
            val dz = candidate.z.toDouble() - hazard.z
            // Inverse of the renderer's positive-Y rotation: world = R(yaw) * local.
            val localX = cos(yaw) * dx - sin(yaw) * dz
            val localZ = sin(yaw) * dx + cos(yaw) * dz
            if (localZ <= 0.0) return reject(Reason.GAS_WRONG_SIDE)
            // Keep the full green ring outside the barrier plane, and away from the opening rim.
            if (localZ <= GREEN_RADIUS || hypot(localX, localZ - GAS_OPENING_Z) <= GREEN_RADIUS + GAS_WARNING_RADIUS)
                return reject(Reason.GAS_FOOTPRINT_OVERLAP)
        }
        return Decision(true, Reason.READY)
    }

    private fun Station.finite() = x.isFinite() && z.isFinite() && yawDegrees.isFinite()
    private fun reject(reason: Reason) = Decision(false, reason)
}
