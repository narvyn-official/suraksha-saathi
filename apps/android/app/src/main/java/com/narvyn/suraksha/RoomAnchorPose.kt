package com.narvyn.suraksha

import com.google.ar.core.Pose
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Persist only anchor-relative orientation. World coordinates are valid for one ARCore frame. */
object RoomAnchorPose {
    fun facingOffset(anchor: Pose, camera: Pose): Pose {
        val localCamera = anchor.inverse().transformPoint(camera.translation)
        val halfYaw = atan2(localCamera[0], localCamera[2]) / 2f
        return Pose.makeRotation(0f, sin(halfYaw), 0f, cos(halfYaw))
    }

    fun model(anchor: Pose, offset: Pose): Pose = anchor.compose(offset)

    fun station(model: Pose): RoomStationPlacement.Station {
        val forward = model.zAxis
        return RoomStationPlacement.Station(model.tx(), model.tz(),
            Math.toDegrees(atan2(forward[0], forward[2]).toDouble()).toFloat())
    }
}
