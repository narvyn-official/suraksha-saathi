package com.narvyn.suraksha

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ProcedureFlowTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun views(v: View): List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) }else emptyList())
    private fun records(module:String,guided:Boolean)=Store(context).use { s -> ProcedureStore(context,s.workerId).use { ProcedureSession.restore(it.latest(module,guided)!!) } }
    private fun seed(module:String, guided:Boolean,hi:Boolean):String = Store(context).use { s -> s.hi=hi;val p=ProcedureSession.create(module,s.workerId,guided);ProcedureStore(context,s.workerId).use { it.save(p.data) };p.data.getString("id") }
    private fun click(s:ActivityScenario<ProcedureActivity>,text:String?=null,tag:String?=null) {
        androidx.test.espresso.Espresso.onView(if(tag!=null)androidx.test.espresso.matcher.ViewMatchers.withTagValue(org.hamcrest.Matchers.equalTo<Any>(tag))else androidx.test.espresso.matcher.ViewMatchers.withText(text)).perform(revealOnPage())
        repeat(70) {
            var clicked=false
            s.onActivity { a ->
                val button=views(a.window.decorView).filterIsInstance<Button>().firstOrNull { if(tag!=null)it.tag==tag else it.text.toString()==text }
                if(button!=null && button.isShown && button.width>0) {
                    val r=Rect()
                    if(button.isEnabled && button.getGlobalVisibleRect(r) && r.height()>=button.height && r.width()>=button.width) { button.performClick();clicked=true }
                }
            }
            instrumentation.waitForIdleSync();if(clicked) { Thread.sleep(150);return };Thread.sleep(100)
        }
        screenshot("unreachable")
        fail("Action not reachable: ${tag?:text}")
    }
    private fun screenshot(name:String) {
        instrumentation.waitForIdleSync();Thread.sleep(350)
        File(context.getExternalFilesDir(null),"procedure-$name.png").outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    private fun pixels(s:ActivityScenario<ProcedureActivity>) {
        var surface:SurfaceView?=null
        s.onActivity { a -> surface=views(a.window.decorView).filterIsInstance<SurfaceView>().first();surface!!.requestRectangleOnScreen(Rect(0,0,surface!!.width,surface!!.height),true) }
        Thread.sleep(700)
        val v=surface!!;val image=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);val latch=CountDownLatch(1);var result=-1
        PixelCopy.request(v,image,{ result=it;latch.countDown() },Handler(Looper.getMainLooper()))
        assertTrue(latch.await(5,TimeUnit.SECONDS));assertEquals(PixelCopy.SUCCESS,result)
        var colored=0
        for(y in 0 until image.height step 4)for(x in 0 until image.width step 4) { val c=image.getPixel(x,y);if(android.graphics.Color.red(c)<200 || android.graphics.Color.green(c)<200 || android.graphics.Color.blue(c)<200)colored++ }
        image.recycle();assertTrue("No rendered equipment pixels",colored>100)
    }
    @Test fun allFiveProceduresCompleteThroughSceneActionsAndSurviveRecreation() {
        val before=Store(context).use { it.attempts().map { a->a.toString() }.toSet() }
        for(module in listOf("fire","gas","machinery","ppe","emergency")) {
            seed(module,true,false)
            ActivityScenario.launch<ProcedureActivity>(Intent(context,ProcedureActivity::class.java).putExtra("moduleId",module)).use { s ->
                click(s,"I am in a safe training area");pixels(s)
                while(!records(module,true).done) {
                    val current=records(module,true)
                    if(InstrumentationRegistry.getArguments().getString("walkthrough")=="true")Thread.sleep(900)
                    if(current.feedback)click(s,"Continue procedure") else {
                        var spatialOn=false;s.onActivity{a->spatialOn=views(a.window.decorView).filterIsInstance<Button>().any{it.text.toString()=="Use button actions instead"}}
                        if(spatialOn)click(s,"Use button actions instead")
                        if(current.index in listOf(0,4,6,9)) { pixels(s);screenshot("$module-step-${current.index+1}") }
                        click(s,tag="procedure-target-${current.step.actions.first { it.correct }.id}")
                        assertTrue(records(module,true).feedback)
                        if(current.index==3) { val saved=records(module,true).data.toString();s.recreate();assertEquals(saved,records(module,true).data.toString()) }
                    }
                }
                assertTrue(records(module,true).data.getJSONObject("result").getBoolean("complete"))
                assertFalse(records(module,true).data.getJSONObject("result").getBoolean("certifiable"))
                screenshot("$module-complete")
            }
        }
        assertEquals(before,Store(context).use { it.attempts().map { a->a.toString() }.toSet() })
    }
    @Test fun hindiTextActionsPreserveUnsafeStopAndGuidedRetry() {
        try { for(guided in listOf(true,false)) {
            seed("gas",guided,true)
            ActivityScenario.launch<ProcedureActivity>(Intent(context,ProcedureActivity::class.java).putExtra("moduleId","gas").putExtra("guided",guided)).use { s ->
                click(s,"मैं सुरक्षित प्रशिक्षण जगह पर हूँ");click(s,"लिखित क्रियाएँ उपयोग करें")
                val unsafe=records("gas",guided).step.actions.first { !it.correct }.id
                click(s,tag="procedure-description-$unsafe")
                s.recreate();assertEquals(!guided,records("gas",guided).done)
                if(guided) { click(s,"प्रक्रिया जारी रखें");assertEquals(0,records("gas",true).index);click(s,tag="procedure-description-${records("gas",true).step.actions.first { it.correct }.id}");assertTrue(records("gas",true).feedback) }
                else { assertTrue(records("gas",false).data.getJSONObject("result").getBoolean("stopped"));assertFalse(records("gas",false).data.getJSONObject("result").getBoolean("complete")) }
                screenshot("hindi-${if(guided)"retry"else"stop"}")
            }
        }}finally { Store(context).use { it.hi=false } }
    }
    @Test fun cameraDeniedCannotRecordActionsAndScreenFallbackWorks() {
        assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.CAMERA))
        seed("fire",true,false)
        ActivityScenario.launch<ProcedureActivity>(Intent(context,ProcedureActivity::class.java).putExtra("moduleId","fire")).use { s ->
            click(s,"I am in a safe training area");click(s,"Use camera AR")
            repeat(30) {
                val root=instrumentation.uiAutomation.rootInActiveWindow
                val n=listOf("permission_deny_button","permission_deny_and_dont_ask_again_button").flatMap { root?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/$it").orEmpty() }.firstOrNull()
                n?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);Thread.sleep(100)
            }
            s.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            val before=records("fire",true).data.toString()
            s.recreate();assertEquals(before,records("fire",true).data.toString())
            s.onActivity { a -> assertTrue(views(a.window.decorView).filter { it.tag?.toString()?.startsWith("procedure-target-")==true }.all { !it.isShown || !it.isEnabled }) }
            screenshot("camera-off");click(s,"Continue on screen");click(s,"Use text actions")
            click(s,tag="procedure-description-${records("fire",true).step.actions.first { it.correct }.id}")
            assertEquals("description",records("fire",true).data.getJSONArray("events").getJSONObject(0).getString("presentation"))
        }
    }
}
