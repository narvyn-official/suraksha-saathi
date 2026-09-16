package com.narvyn.suraksha

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import com.google.ar.core.Frame
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Pose
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import java.util.concurrent.atomic.AtomicLong

/**
 * The only Session constructor/update owner in the application. Existing scene renderers are
 * adapters; callers stop their GL surface before pause/close and detach their anchors on retirement.
 * A driver may retain a paused session for a dialog, but only one driver can resume the camera.
 */
internal class NativeArDriver(host: Activity, trainingBay: Boolean = false) : AutoCloseable {
    private val session = Session(host)
    val sessionEpoch = epochs.incrementAndGet()
    private val gate = ArFrameGate()
    @Volatile private var resumed = false
    @Volatile private var closed = false
    private var updateThread: Long? = null
    private var lastTexture = 0
    private var rotation = -1; private var width = 0; private var height = 0
    private var metricAt = 0L; private var distinct = 0; private var tracked = 0
    private var previousTimestamp = 0L; private var maxUpdateMs = 0L
    private var lastReason = ""
    private var scanOrigin: Pose? = null
    private var maxScanTravel = 0f

    init {
        try {
            val database=if(trainingBay)host.assets.open("ar/training-bay/reference.imgdb").use {
                AugmentedImageDatabase.deserialize(session,it)
            }else null
            ArCameraSupport.configure(session,database)
        }
        catch (error: Exception) {
            // Configuration can fail after native resources were allocated. Retire through the
            // same process-wide barrier used by all scenes; don't leak an unassigned constructor.
            ArSessionRelease().retire(this) {}
            throw error
        }
    }

    fun resume() {
        check(!closed) { "AR session is closed" }
        check(cameraLease.acquire(this)) { "Another training camera is still active" }
        try {
            session.resume(); resumed = true; gate.resume(); updateThread = null
            lastTexture = 0; rotation = -1; width = 0; height = 0
            metricAt = 0; distinct = 0; tracked = 0; maxUpdateMs = 0
            scanOrigin = null; maxScanTravel = 0f
            Log.i("TrainingAR", "session=$sessionEpoch resumed")
        } catch (error: Exception) { cameraLease.release(this); throw error }
    }

    fun pause() {
        gate.pause()
        try { if (resumed) session.pause() }
        finally { resumed = false; cameraLease.release(this) }
    }

    fun invalidateTexture() { lastTexture = 0 }
    fun setCameraTextureName(texture: Int) {
        check(texture > 0)
        if (lastTexture != texture) { session.setCameraTextureName(texture); lastTexture = texture }
    }
    fun setDisplayGeometry(displayRotation: Int, w: Int, h: Int) {
        if (rotation != displayRotation || width != w || height != h) {
            session.setDisplayGeometry(displayRotation, w, h)
            rotation = displayRotation; width = w; height = h
        }
    }

    fun update(): Frame {
        check(resumed && !closed) { "AR update outside foreground session" }
        val thread = Thread.currentThread().id
        check(updateThread == null || updateThread == thread) { "Multiple AR frame owners" }
        updateThread = thread
        val start = SystemClock.elapsedRealtime()
        val frame = session.update()
        val now = SystemClock.elapsedRealtime()
        maxUpdateMs = maxOf(maxUpdateMs, now - start)
        if (frame.timestamp > previousTimestamp) {
            distinct++; previousTimestamp = frame.timestamp
            if (frame.camera.trackingState == TrackingState.TRACKING) {
                tracked++
                val pose = frame.camera.pose
                val origin = scanOrigin ?: pose.also { scanOrigin = it }
                val dx=pose.tx()-origin.tx(); val dy=pose.ty()-origin.ty(); val dz=pose.tz()-origin.tz()
                maxScanTravel=maxOf(maxScanTravel,kotlin.math.sqrt(dx*dx+dy*dy+dz*dz))
            }
        }
        val status = gate.observe(frame.timestamp, now,
            frame.camera.trackingState == TrackingState.TRACKING,
            frame.camera.trackingFailureReason.name.lowercase())
        if (metricAt == 0L) metricAt = now
        if (lastReason != status.reason || now - metricAt >= 5000) {
            Log.i("TrainingAR", "session=$sessionEpoch generation=${status.generation} state=${status.reason} distinct=$distinct tracked=$tracked intervalMs=${now-metricAt} maxUpdateMs=$maxUpdateMs scanTravelCm=${(maxScanTravel*100).toInt()}")
            lastReason = status.reason
            if (now - metricAt >= 5000) { metricAt=now; distinct=0; tracked=0; maxUpdateMs=0 }
        }
        return frame
    }

    fun snapshot(now: Long = SystemClock.elapsedRealtime()) = gate.current(now)
    /** GL owner only: trackables are never shared with UI callbacks. */
    fun trackedFloors(): List<Plane> = session.getAllTrackables(Plane::class.java).filter {
        it.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
            it.trackingState == TrackingState.TRACKING && it.subsumedBy == null
    }
    fun anchorAt(pose: Pose) = session.createAnchor(pose)
    override fun close() {
        if (closed) return
        gate.pause(); resumed = false; closed = true
        try { session.close() } finally { cameraLease.release(this) }
    }

    companion object {
        private val epochs = AtomicLong()
        private val cameraLease = ArCameraLease()
    }
}
