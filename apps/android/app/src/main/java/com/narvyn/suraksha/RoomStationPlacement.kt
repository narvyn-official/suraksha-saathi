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
    enum class Reason { READY, INVALID_INPUT, ALL_STATIONS_PLACED, TOO_CLOSE, FOOTPRINTS_TOO_CLOSE, GAS_WRONG_SIDE, GAS_FOOTPRINT_OVERLAP }
    data class Decision(val allowed: Boolean, val reason: Reason)

    // Match RoomMissionGeometry's safePoint ring and confinedZone warning ring.
    private const val GREEN_RADIUS = .45
    private const val GAS_OPENING_Z = -.55
    private const val GAS_WARNING_RADIUS = .54
    const val MIN_FOOTPRINT_GAP = .20

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
        val next=footprint(module,existing.size,candidate)
        if(existing.withIndex().any { (index,station) -> !separated(footprint(module,index,station),next) })
            return reject(Reason.FOOTPRINTS_TOO_CLOSE)
        return Decision(true, Reason.READY)
    }

    private data class Point(val x:Double,val z:Double)
    private fun footprint(module:String,index:Int,station:Station):List<Point> {
        val yaw=Math.toRadians(station.yawDegrees.toDouble()%360.0)
        return RoomPlacementArea.outline(module,index).map{p->
            Point(station.x.toDouble()+cos(yaw)*p[0]+sin(yaw)*p[2],station.z.toDouble()-sin(yaw)*p[0]+cos(yaw)*p[2])
        }
    }
    // A separating axis with a 20 cm gap is conservative at diagonal corners. It never accepts
    // overlapping footprints. Double precision avoids overflow in rejected/remote coordinates.
    private fun separated(a:List<Point>,b:List<Point>):Boolean {
        for(poly in listOf(a,b))for(i in poly.indices){
            val p=poly[i];val q=poly[(i+1)%poly.size]
            val dx=q.x-p.x;val dz=q.z-p.z;val length=hypot(dx,dz)
            if(length<1e-8)continue
            val nx=-dz/length;val nz=dx/length
            fun projection(v:List<Point>)=v.map{it.x*nx+it.z*nz}
            val ap=projection(a);val bp=projection(b)
            if(maxOf(bp.min()-ap.max(),ap.min()-bp.max())>=MIN_FOOTPRINT_GAP-1e-6)return true
        }
        // At unrenderably large finite coordinates the vertices can collapse to one point.
        if(a.distinct().size==1||b.distinct().size==1)return hypot(a[0].x-b[0].x,a[0].z-b[0].z)>10
        return false
    }

    private fun Station.finite() = x.isFinite() && z.isFinite() && yawDegrees.isFinite()
    private fun reject(reason: Reason) = Decision(false, reason)
}
