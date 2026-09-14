package com.narvyn.suraksha

/** Runtime-only eligibility. Never persisted as learning or competence evidence. */
class ComponentCameraGate {
    private var revision = 0
    private var active = false
    private var lastReadyAt: Long? = null
    @Synchronized fun configure(): Int { revision++; lastReadyAt = null; return revision }
    @Synchronized fun activate() { active = true; lastReadyAt = null }
    @Synchronized fun pause() { active = false; revision++; lastReadyAt = null }
    @Synchronized fun frame(version: Int, ready: Boolean, now: Long) {
        if (version == revision) lastReadyAt = if (active && ready) now else null
    }
    @Synchronized fun allows(version: Int, now: Long): Boolean {
        val at = lastReadyAt ?: return false
        return active && version == revision && now >= at && now - at <= 500L
    }
}

/** ARCore LATEST_CAMERA_IMAGE may repeat a Frame. Repeated renders must not renew its age. */
class ComponentCameraFreshness {
    private var timestamp = 0L
    private var observedAt = 0L
    private var needsNewImage = true
    @Synchronized fun clear() { timestamp=0L; observedAt=0L; needsNewImage=true }
    @Synchronized fun requireNewImage() { needsNewImage=true }
    @Synchronized fun observedAt(imageTimestamp: Long, now: Long): Long? {
        if(imageTimestamp<=0L || now<observedAt || (needsNewImage && imageTimestamp<=timestamp)) return null
        if(imageTimestamp>timestamp) { timestamp=imageTimestamp; observedAt=now; needsNewImage=false }
        if(imageTimestamp!=timestamp || now-observedAt>500L) return null
        return observedAt
    }
}

/** A limited frontal viewing cone for these generic fire-part targets, not an occlusion test. */
object ComponentCameraGeometry {
    fun readableView(dx: Float, dyFromModelCenter: Float, dz: Float, yaw: Float): Boolean {
        if(listOf(dx,dyFromModelCenter,dz,yaw).any { !it.isFinite() }) return false
        val distance=kotlin.math.sqrt(dx*dx+dz*dz)
        if(!distance.isFinite() || distance<=.25f) return false
        val radians=Math.toRadians(yaw.toDouble())
        val frontal=(dx*kotlin.math.sin(radians)+dz*kotlin.math.cos(radians))/distance
        return frontal>.82 && dyFromModelCenter/distance in -.2f..1f
    }
}
