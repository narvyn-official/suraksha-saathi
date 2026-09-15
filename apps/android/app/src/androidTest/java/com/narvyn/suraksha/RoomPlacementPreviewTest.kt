package com.narvyn.suraksha

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Emulator-only rendering fixtures. No camera permission request, Session, AR evidence or certificate.
 * The camera view stays unattached and never resumes. Only its Canvas overlay is rendered to a bitmap.
 */
@RunWith(AndroidJUnit4::class)
class RoomPlacementPreviewTest {
    @Test fun placementOverlayFitsBothLanguagesAndOrientationsAtDoubleFontScaleWithoutAuthorizingActions() {
        assumeTrue("Synthetic preview fixtures must run on an emulator", Build.HARDWARE in listOf("ranchu", "goldfish") ||
            Build.FINGERPRINT.startsWith("generic") || Build.MODEL.contains("sdk_gphone"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Leave the normal screen mission's briefing unacknowledged: its renderer never starts either.
        ActivityScenario.launch<RoomMissionActivity>(Intent(context, RoomMissionActivity::class.java)
            .putExtra("moduleId", "fire").putExtra("camera", false)).use { scenario ->
            scenario.onActivity { activity ->
                val metrics = activity.resources.displayMetrics
                val originalScaledDensity = metrics.scaledDensity
                try {
                    // Overlay text uses scaledDensity directly. Exercise precisely 200% Canvas text,
                    // without changing system font settings or the learner's saved language.
                    metrics.scaledDensity = metrics.density * 2f
                    for (hindi in listOf(false, true)) {
                        for ((orientation, widthDp, heightDp) in listOf(
                            Triple("portrait", 360, 620), Triple("landscape", 400, 240))) {
                            val fixture = RoomMissionView(activity, "fire", camera = true, hindi = hindi)
                            var actions = 0
                            var samples = 0
                            fixture.onAction = { actions++ }
                            fixture.onAim = { _, _, _, _, _ -> samples++ }
                            val width = activity.dp(widthDp)
                            val height = activity.dp(heightDp)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try {
                                fixture.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                                fixture.layout(0, 0, width, height)
                                fun point(x: Float, y: Float) = ComponentProjection.Point(x * width, y * height)
                                val synthetic = RoomMissionView.Image(revision = 0, at = SystemClock.elapsedRealtime(),
                                    placement = 0, tracked = true, message = "Synthetic overlay fixture only",
                                    frameWidth = width, frameHeight = height, phase = "ALARM",
                                    canPlace = true, preview = true, scanProgress = .6f,
                                    surfaceBoundary = listOf(point(.08f, .28f), point(.92f, .28f), point(.92f, .94f), point(.08f, .94f)),
                                    footprint = listOf(point(.30f, .58f), point(.70f, .58f), point(.70f, .80f), point(.30f, .80f)))
                                RoomMissionView::class.java.getDeclaredField("image").apply { isAccessible = true }.set(fixture, synthetic)
                                val overlay = RoomMissionView::class.java.getDeclaredField("overlay").apply { isAccessible = true }.get(fixture) as View
                                overlay.background = null
                                bitmap.eraseColor(Color.WHITE)
                                overlay.draw(Canvas(bitmap))

                                val name = "fixture-room-placement-preview-${if (hindi) "hi" else "en"}-$orientation-font200.png"
                                File(activity.getExternalFilesDir(null), name).outputStream().use {
                                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                                }
                                assertPreviewLabelInsideBitmap(bitmap, activity.dp(9))
                                val surfacePixel = bitmap.getPixel((width * .15f).toInt(), (height * .60f).toInt())
                                assertNotEquals("Detected surface tint must be drawn: $name", Color.WHITE, surfacePixel)
                                assertTrue("Surface tint must be teal: $name", Color.green(surfacePixel) > Color.red(surfacePixel))
                                assertTrue("Footprint outline must be drawn: $name",
                                    countColor(bitmap, Palette.teal, (width * .29f).toInt(), (height * .57f).toInt(),
                                        (width * .71f).toInt(), (height * .81f).toInt()) > activity.dp(20))

                                // A synthetic ready-looking image cannot bypass the foreground/lifecycle gate.
                                assertFalse(fixture.canPlace)
                                assertFalse(fixture.ready)
                                fixture.place()
                                val now = SystemClock.uptimeMillis()
                                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                                    val event = MotionEvent.obtain(now, now + 100, action, width / 2f, height / 2f, 0)
                                    try { overlay.dispatchTouchEvent(event) } finally { event.recycle() }
                                }
                                assertFalse(fixture.canPlace)
                                assertFalse(fixture.ready)
                                assertEquals("Fixture must emit no training actions", 0, actions)
                                assertEquals("Fixture must emit no camera measurements", 0, samples)
                            } finally {
                                fixture.close()
                                bitmap.recycle()
                            }
                        }
                    }
                } finally { metrics.scaledDensity = originalScaledDensity }
            }
        }
    }

    private fun assertPreviewLabelInsideBitmap(bitmap: Bitmap, padding: Int) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val glyphColor = 0xff855300.toInt()
        var count = 0
        var left = bitmap.width
        var right = -1
        var top = bitmap.height
        var bottom = -1
        for (y in 0 until bitmap.height / 3) for (x in 0 until bitmap.width) {
            if (pixels[y * bitmap.width + x] == glyphColor) {
                count++;left = minOf(left, x);right = maxOf(right, x);top = minOf(top, y);bottom = maxOf(bottom, y)
            }
        }
        assertTrue("Preview warning must contain visible text", count > 30)
        assertTrue("Preview warning clipped at left", left >= padding)
        assertTrue("Preview warning clipped at right", right < bitmap.width - padding)
        assertTrue("Preview warning clipped vertically", top >= padding && bottom < bitmap.height / 3 - padding)
    }

    private fun countColor(bitmap: Bitmap, color: Int, left: Int, top: Int, right: Int, bottom: Int): Int {
        val width = right - left
        val height = bottom - top
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, left, top, width, height)
        return pixels.count { it == color }
    }
}
