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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Camera-off native action/recovery checks; no simulated successful physical plane hit. */
@RunWith(AndroidJUnit4::class)
class PlacementFlowTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun views(v: View): List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) }else emptyList())
    private fun nodes(n: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if(n==null)emptyList()else listOf(n)+(0 until n.childCount).flatMap { nodes(n.getChild(it)) }
    private fun click(label: String) {
        var forward=true
        repeat(40) {
            val all=nodes(instrumentation.uiAutomation.rootInActiveWindow)
            val n=all.firstOrNull { it.contentDescription?.toString()==label || it.text?.toString()==label }
            if(n!=null && n.isVisibleToUser && n.isClickable && n.isEnabled) {
                n.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_CLICK));instrumentation.waitForIdleSync();Thread.sleep(150);return
            }
            if(n!=null)n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            val labels=if(forward)listOf("More","और देखें")else listOf("Earlier","पिछला भाग")
            val paging=all.firstOrNull{it.isVisibleToUser&&it.isClickable&&it.isEnabled&&it.text?.toString() in labels}
            if(paging!=null)paging.performAction(AccessibilityNodeInfo.ACTION_CLICK)else forward=!forward
            Thread.sleep(100)
        }
        fail("Unreachable native action: $label")
    }
    private fun checkControls(a: android.app.Activity) {
        val scale=InstrumentationRegistry.getArguments().getString("fontScale","1.0").toFloat()
        assertEquals(scale,a.resources.configuration.fontScale,.05f)
        views(a.window.decorView).filterIsInstance<Button>().filter { it.isShown && it.width>0 }.forEach { b ->
            assertTrue("Target too small: ${b.text}",b.width>=a.dp(48) && b.height>=a.dp(48))
            b.layout?.let { layout -> assertTrue("Clipped label: ${b.text}",layout.height<=b.height-b.compoundPaddingTop-b.compoundPaddingBottom+2);assertTrue((0 until layout.lineCount).all { layout.getEllipsisCount(it)==0 }) }
        }
    }
    private fun shot(name: String) {
        instrumentation.waitForIdleSync();Thread.sleep(200)
        val scale=InstrumentationRegistry.getArguments().getString("fontScale","1.0")
        File(context.getExternalFilesDir(null),"$name-$scale.png").outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) }
        if(InstrumentationRegistry.getArguments().getString("placementDemo")=="true")Thread.sleep(900)
    }
    private fun deny() {
        repeat(60) {
            val root=instrumentation.uiAutomation.rootInActiveWindow
            val button=listOf("permission_deny_button","permission_deny_and_dont_ask_again_button").flatMap { root?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/$it").orEmpty() }.firstOrNull()
            if(button!=null) { assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK));Thread.sleep(300);return }
            if(it>6 && root?.packageName?.toString()==context.packageName && context.checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_DENIED)return
            Thread.sleep(100)
        }
        fail("Permission dialog not dismissed")
    }
    @Test fun arPlacementControlCannotCreateAnswersWithoutCameraAndFallbackStaysReachable() {
        assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.CAMERA))
        try { for(hi in listOf(false,true))for(guided in listOf(false,true)) {
            fun t(en: String,hindi: String)=if(hi)hindi else en
            val curriculum=Curriculum(context)
            val training=Store(context).use { it.hi=hi;TrainingSession.create(curriculum.module("fire"),it.workerId,curriculum.version,guided,"arcore").apply { it.save(this) } }
            val id=training.data.getString("id");val original=training.data.toString()
            ActivityScenario.launch<ArActivity>(Intent(context,ArActivity::class.java).putExtra("attemptId",id)).use { s ->
                click(t("Place decision stations at camera centre","निर्णय विकल्प कैमरा दृश्य के बीच में रखें"))
                s.onActivity { a -> checkControls(a);assertTrue(views(a.window.decorView).filterIsInstance<TextView>().any { it.text.toString().contains(t("Camera is not running","कैमरा चालू नहीं है")) });assertTrue(views(a.window.decorView).filter { it.tag?.toString()?.startsWith("ar-answer-")==true }.all { it.visibility!=View.VISIBLE }) }
                assertEquals(original,Store(context).use { it.attempt(id).toString() })
                s.recreate();click(t("Place decision stations at camera centre","निर्णय विकल्प कैमरा दृश्य के बीच में रखें"))
                assertEquals(original,Store(context).use { it.attempt(id).toString() })
                if(guided)shot(if(hi)"placement-ar-hindi"else"placement-ar")
                click(t("Continue on screen","स्क्रीन पर जारी रखें"))
            }
        }}finally { Store(context).use { it.hi=false } }
    }
    @Test fun componentPlacementControlCannotCreateEvidenceWithoutCameraAndDescriptionsStillWork() {
        assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.CAMERA))
        fun state()=Store(context).use { ComponentSession.restore("fire",it.componentRecords()["fire"]) }
        try { for(hi in listOf(false,true)) {
            fun t(en: String,hindi: String)=if(hi)hindi else en
            Store(context).use { it.hi=hi;val fresh=ComponentSession.start("fire",null);fresh.advance(1);it.saveComponent(fresh.data) }
            ActivityScenario.launch<ComponentPracticeActivity>(Intent(context,ComponentPracticeActivity::class.java).putExtra("moduleId","fire")).use { s ->
                click(t("Use camera AR · fire","कैमरा AR उपयोग करें · आग"));deny();s.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                val original=state().data.toString()
                click(t("Place training model at camera centre","प्रशिक्षण मॉडल कैमरा दृश्य के बीच में रखें"))
                s.onActivity { a -> checkControls(a);assertTrue(views(a.window.decorView).filterIsInstance<TextView>().any { it.text.toString().contains(t("Camera is not running","कैमरा चालू नहीं है")) });assertTrue(views(a.window.decorView).filter { it.tag?.toString()?.startsWith("camera-marker-")==true }.all { it.visibility!=View.VISIBLE }) }
                assertEquals(original,state().data.toString());assertFalse(state().data.optBoolean("cameraSeen"));assertEquals(0,state().data.getJSONArray("events").length())
                shot(if(hi)"placement-component-hindi"else"placement-component")
                s.recreate();click(t("Place training model at camera centre","प्रशिक्षण मॉडल कैमरा दृश्य के बीच में रखें"));assertEquals(original,state().data.toString())
                click(t("Continue on screen","स्क्रीन पर जारी रखें"));click(t("Use text descriptions","लिखित विवरण उपयोग करें"));click(state().target.description(hi))
                assertEquals(1,state().data.getJSONArray("events").length());assertEquals("description",state().data.getJSONArray("events").getJSONObject(0).getString("presentation"));assertEquals(0,state().data.optInt("cameraCorrect"))
            }
        }}finally { Store(context).use { it.hi=false } }
    }
}
