package com.narvyn.suraksha

/** One immutable screen-local intent. Eligibility is not proof of a plane hit or successful placement. */
class ArPlacementRequest private constructor(
    val revision: Int,
    val x: Float,
    val y: Float,
    val width: Int,
    val height: Int,
    val createdAt: Long
) {
    fun eligible(
        currentRevision: Int,
        currentWidth: Int,
        currentHeight: Int,
        now: Long,
        active: Boolean,
        cameraTracked: Boolean,
        freshImage: Boolean
    ): Boolean = active && cameraTracked && freshImage && revision == currentRevision &&
        width == currentWidth && height == currentHeight && now >= createdAt && now - createdAt <= 500L

    companion object {
        fun createAt(revision: Int, x: Float, y: Float, width: Int, height: Int, createdAt: Long): ArPlacementRequest? {
            if (width <= 0 || height <= 0 || createdAt <= 0L || !x.isFinite() || !y.isFinite() ||
                x <= 0f || x >= width.toFloat() || y <= 0f || y >= height.toFloat()) return null
            return ArPlacementRequest(revision, x, y, width, height, createdAt)
        }

        fun createCenter(revision: Int, width: Int, height: Int, createdAt: Long): ArPlacementRequest? =
            createAt(revision, width / 2f, height / 2f, width, height, createdAt)
    }
}
