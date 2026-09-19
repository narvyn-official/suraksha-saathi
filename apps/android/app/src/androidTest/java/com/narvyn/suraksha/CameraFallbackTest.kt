package com.narvyn.suraksha

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.`is`
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Permission recovery on an emulator is not evidence of working physical AR tracking. */
@RunWith(AndroidJUnit4::class)
class CameraFallbackTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun all(v: View): List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap { all(v.getChildAt(it)) }else emptyList())
    private fun state() = Store(context).use { ComponentSession.restore("fire",it.componentRecords()["fire"]) }
    private fun denyIfRequested() {
        repeat(100) {
            val root=instrumentation.uiAutomation.rootInActiveWindow
            val buttons=listOf("permission_deny_button","permission_deny_and_dont_ask_again_button").flatMap { id -> root?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/$id").orEmpty() }
            if(buttons.isNotEmpty()) { assertTrue(buttons.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)); Thread.sleep(300); instrumentation.waitForIdleSync(); return }
            Thread.sleep(50)
        }
        // After repeated denial Android may return the denial without showing another prompt.
        assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.CAMERA))
        assertNotEquals("Permission dialog must be dismissed", "com.android.permissioncontroller", instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString())
    }
    @Test fun deniedCameraAndRecreationPreservePracticeAndOfferScreenAndTextInBothLanguages() {
        assertEquals("Run with camera permission revoked before instrumentation",PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.CAMERA))
        val attempts=Store(context).use { it.attempts().map { a -> a.toString() } }
        val credentials=Store(context).use { it.credentials().map { c -> c.toString() } }
        try {
            for(hi in listOf(false,true)) {
                fun t(en: String,hindi: String)=if(hi)hindi else en
                Store(context).use { it.hi=hi; val fresh=ComponentSession.start("fire",null); fresh.advance(1); it.saveComponent(fresh.data) }
                ActivityScenario.launch<ComponentPracticeActivity>(Intent(context,ComponentPracticeActivity::class.java).putExtra("moduleId","fire")).use { s ->
                    onView(withText(t("Use camera AR · fire","कैमरा AR उपयोग करें · आग"))).perform(revealOnPage(),click())
                    denyIfRequested(); s.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED); s.recreate(); instrumentation.waitForIdleSync()
                    s.onActivity { a ->
                        val views=all(a.window.decorView)
                        for(text in listOf(t("Show a hint","संकेत दिखाएँ"),t("I’m not sure","मुझे निश्चित नहीं है"))) {
                            assertFalse(views.filterIsInstance<Button>().single { it.text.toString()==text }.isEnabled)
                        }
                        assertTrue(views.filterIsInstance<TextView>().any { it.text.toString().contains(t("Camera permission is off","कैमरा अनुमति बंद है")) })
                        assertTrue(views.filterIsInstance<Button>().any { it.text.toString()==t("Camera permission settings","कैमरा अनुमति सेटिंग") && it.isEnabled })
                        assertTrue(views.filter { it.tag?.toString()?.startsWith("camera-marker-")==true }.all { it.visibility!=View.VISIBLE })
                    }
                    assertEquals(1,state().stage);assertNull(state().answer);assertFalse(state().data.optBoolean("cameraSeen"));assertEquals(0,state().data.getJSONArray("events").length())
                    File(context.getExternalFilesDir(null),if(hi)"camera-hindi-fallback.png" else "camera-permission-fallback.png").outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) }
                    onView(withText(t("Continue on screen","स्क्रीन पर जारी रखें"))).perform(revealOnPage(),click())
                    onView(withTagValue(`is`("component-marker-label" as Any))).perform(revealOnPage(),click())
                    assertEquals("screen",state().data.getJSONArray("events").getJSONObject(0).getString("presentation"))
                    onView(withText(t("Try the changed view","बदले दृश्य में कोशिश करें"))).perform(revealOnPage(),click())
                    onView(withText(t("Use camera AR · fire","कैमरा AR उपयोग करें · आग"))).perform(revealOnPage(),click())
                    denyIfRequested(); s.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                    onView(withText(t("Use text descriptions","लिखित विवरण उपयोग करें"))).perform(revealOnPage(),click())
                    onView(withTagValue(`is`("component-description-label" as Any))).perform(revealOnPage(),click())
                    s.recreate()
                    assertEquals(2,state().stage);assertTrue(state().descriptions)
                    val events=state().data.getJSONArray("events")
                    assertEquals(2,events.length());assertEquals("description",events.getJSONObject(1).getString("presentation"));assertEquals(0,state().data.optInt("cameraCorrect"))
                }
            }
            Store(context).use { assertEquals(attempts,it.attempts().map { a -> a.toString() });assertEquals(credentials,it.credentials().map { c -> c.toString() }) }
        } finally { Store(context).use { it.hi=false } }
    }
}
