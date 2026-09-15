package com.narvyn.suraksha

import kotlin.math.min

/** Runtime-only placement dwell in local surface coordinates. Constants are UI choices, not safety limits.
 * No Android, ARCore, wall clock or world-coordinate dependency. A new helper may be used for a new camera session.
 */
class ArSurfaceReadiness {
    data class Result(val progress:Float,val ready:Boolean)
    private var surface:Any?=null
    private var revision=0
    private var originX=0f
    private var originZ=0f
    private var startedAt=0L
    private var frames=0
    private var result=EMPTY
    private var timestamp:Long?=null
    private var lastFreshAt:Long?=null
    private var lastObservedAt:Long?=null

    /** Clear a candidate on no hit, tracking loss or lifecycle changes. Retain the image watermark so an
     * already-observed frozen image cannot become fresh merely because a candidate was reset.
     */
    @Synchronized fun reset() { surface=null;frames=0;result=EMPTY }

    /** frameTimestamp is an opaque positive, increasing image timestamp (ARCore nanoseconds work as-is).
     * now is monotonic elapsed milliseconds. Call for every valid hit; call reset() when there is no valid hit.
     * Keys compare by equality: ARCore may return a new wrapper for the same native trackable each frame.
     * x/z must remain within the radius of the FIRST candidate point.
     * A duplicate image returns frozen progress while fresh, and never extends the last distinct image's age.
     */
    @Synchronized fun observe(surfaceKey:Any,x:Float,z:Float,frameTimestamp:Long,now:Long,sceneRevision:Int):Result {
        if(now<0L || frameTimestamp<=0L) { reset();return EMPTY }
        val previousTimestamp=timestamp
        val previousFreshAt=lastFreshAt
        val clockRegressed=lastObservedAt?.let { now<it }==true
        val imageRegressed=previousTimestamp?.let { frameTimestamp<it }==true
        val distinct=previousTimestamp!=frameTimestamp
        val brokenGap=previousFreshAt?.let { now<it || now-it>MAX_GAP_MS }==true
        lastObservedAt=now
        if(distinct) { timestamp=frameTimestamp;lastFreshAt=now }
        // Consume the regressed/invalid sample without seeding a candidate. The next new image starts afresh.
        if(clockRegressed || imageRegressed || !x.isFinite() || !z.isFinite()) { reset();return EMPTY }
        val dx=x.toDouble()-originX.toDouble();val dz=z.toDouble()-originZ.toDouble()
        val moved=dx*dx+dz*dz>RADIUS_METRES.toDouble()*RADIUS_METRES.toDouble()
        val changed=surface!=surfaceKey || revision!=sceneRevision || moved
        if(!distinct) {
            if(brokenGap || changed)reset()
            return result
        }
        if(frames==0 || brokenGap || changed) {
            surface=surfaceKey;revision=sceneRevision;originX=x;originZ=z;startedAt=now;frames=1;result=EMPTY
            return result
        }
        frames=(frames+1).coerceAtMost(MIN_FRAMES)
        val duration=(now-startedAt).coerceAtLeast(0L)
        val timeProgress=(duration.toDouble()/DWELL_MS).coerceIn(0.0,1.0).toFloat()
        val frameProgress=(frames-1).toFloat()/(MIN_FRAMES-1)
        result=Result(min(timeProgress,frameProgress),duration>=DWELL_MS && frames>=MIN_FRAMES)
        return result
    }
    companion object {
        const val DWELL_MS=450L
        const val MAX_GAP_MS=150L
        const val MIN_FRAMES=5
        const val RADIUS_METRES=.12f
        private val EMPTY=Result(0f,false)
    }
}
