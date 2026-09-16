package com.narvyn.suraksha

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Anchor support and virtual mesh bounds. Neither measures real obstacle clearance or site safety. */
object RoomPlacementArea {
    enum class Reason { READY, OUT_OF_RANGE, DIFFERENT_LEVEL, INCOMPLETE_SURFACE }
    fun evaluate(distance: Float, heightDifference: Float, anchorPatchCovered: Boolean): Reason = when {
        !distance.isFinite() || distance !in .6f..3f -> Reason.OUT_OF_RANGE
        !heightDifference.isFinite() || abs(heightDifference) > .20f -> Reason.DIFFERENT_LEVEL
        !anchorPatchCovered -> Reason.INCOMPLETE_SURFACE
        else -> Reason.READY
    }
    /** Sample a 15 cm patch around the anchor, including its centre. A large virtual mesh may
     * extend beyond the currently mapped polygon; that remains a visible layout warning.
     * Radius is a placement-quality heuristic, not a physical support or safety claim.
     */
    fun anchorPatch():List<FloatArray> = listOf(floatArrayOf(0f,0f,0f))+(0 until 8).map {
        val a=it*Math.PI/4
        floatArrayOf((cos(a)*.075).toFloat(),0f,(sin(a)*.075).toFloat())
    }
    fun outline(module: String, index: Int): List<FloatArray> = when (index) {
        0 -> rectangle(-.42f, .42f, -.28f, .28f)
        1 -> if (module == "gas") rectangle(-.60f, .60f, -1.12f, .14f)
             else rectangle(-.60f, .60f, -.40f, .40f)
        else -> (0 until 32).map { val a = it * Math.PI * 2 / 32
            floatArrayOf((cos(a) * .47).toFloat(), .005f, (sin(a) * .47).toFloat()) }
    }
    private fun rectangle(left: Float, right: Float, back: Float, front: Float) = listOf(
        floatArrayOf(left,.005f,back), floatArrayOf(right,.005f,back),
        floatArrayOf(right,.005f,front), floatArrayOf(left,.005f,front))
}
