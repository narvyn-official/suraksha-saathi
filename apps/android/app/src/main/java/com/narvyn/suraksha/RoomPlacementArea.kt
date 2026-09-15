package com.narvyn.suraksha

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Bounds of our virtual meshes and comfortable UI placement limits, not site safety distances. */
object RoomPlacementArea {
    enum class Reason { READY, OUT_OF_RANGE, DIFFERENT_LEVEL, INCOMPLETE_SURFACE }
    fun evaluate(distance: Float, heightDifference: Float, covered: Boolean): Reason = when {
        !distance.isFinite() || distance !in .6f..3f -> Reason.OUT_OF_RANGE
        !heightDifference.isFinite() || abs(heightDifference) > .20f -> Reason.DIFFERENT_LEVEL
        !covered -> Reason.INCOMPLETE_SURFACE
        else -> Reason.READY
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
