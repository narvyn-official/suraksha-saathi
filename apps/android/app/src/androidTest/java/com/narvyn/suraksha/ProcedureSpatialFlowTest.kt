package com.narvyn.suraksha

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProcedureSpatialFlowTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) }else emptyList())
    private fun saved(module:String,guided:Boolean=true)=Store(context).use { s -> ProcedureStore(context,s.workerId).use { ProcedureSession.restore(it.latest(module,guided)!!) } }
    private fun seed(step:String,guided:Boolean=true,hi:Boolean=false) {
        Store(context).use { s ->
            s.hi=hi
            val p=ProcedureSession.create(if(step.startsWith("fire"))"fire"else"gas",s.workerId,guided)
            while(p.step.id!=step) { p.choose(p.step.id,"description",p.data.getLong("updatedAt")+1);p.advance() }
            ProcedureStore(context,s.workerId).use { it.save(p.data) }
        }
    }
    private fun click(s:ActivityScenario<ProcedureActivity>,text:String) {
        repeat(60) {
            var done=false
            s.onActivity { a -> views(a.window.decorView).filterIsInstance<Button>().firstOrNull { it.text.toString()==text && it.isShown }?.let {
                it.requestRectangleOnScreen(Rect(0,0,it.width,it.height),true)
                val r=Rect();if(it.isEnabled && it.getGlobalVisibleRect(r) && r.height()>=it.height) { it.performClick();done=true }
            } }
            instrumentation.waitForIdleSync();if(done) { Thread.sleep(120);return };Thread.sleep(80)
        };s.onActivity { a ->
            var v:View?=views(a.window.decorView).filterIsInstance<Button>().firstOrNull { it.text.toString()==text }
            while(v!=null) { val r=Rect();v.getGlobalVisibleRect(r);android.util.Log.i("SpatialLayout", "${v.javaClass.simpleName} top=${v.top} h=${v.height} measured=${v.measuredHeight} scroll=${v.scrollY} visible=$r");v=v.parent as? View }
        };screenshot("workspace-unreachable");fail("Missing action: $text")
    }
    /** Read rendered target positions, then exercise real touch delivery; never invoke the learning callback. */
    private fun target(s:ActivityScenario<ProcedureActivity>,index:Int):Pair<View,ComponentProjection.Point> {
        repeat(60) {
            var result:Pair<View,ComponentProjection.Point>?=null
            s.onActivity { a ->
                val scene=views(a.window.decorView).filterIsInstance<ProcedureSceneView>().single()
                val overlay=scene.javaClass.getDeclaredField("overlay").apply { isAccessible=true }.get(scene) as View
                overlay.requestRectangleOnScreen(Rect(0,0,overlay.width,overlay.height),true)
                @Suppress("UNCHECKED_CAST") val points=overlay.javaClass.getDeclaredField("points").apply { isAccessible=true }.get(overlay) as List<ComponentProjection.Point?>
                val r=Rect()
                if(overlay.getGlobalVisibleRect(r) && r.height()>=overlay.height && points.size>index)points[index]?.let { result=overlay to it }
            }
            instrumentation.waitForIdleSync();if(result!=null)return result!!;Thread.sleep(100)
        };error("Spatial targets never rendered")
    }
    private fun touch(s:ActivityScenario<ProcedureActivity>,target:Pair<View,ComponentProjection.Point>,duration:Long,cancel:Boolean=false) {
        val start=SystemClock.uptimeMillis()
        s.onActivity { val event=MotionEvent.obtain(start,start,MotionEvent.ACTION_DOWN,target.second.x,target.second.y,0);target.first.dispatchTouchEvent(event);event.recycle() }
        Thread.sleep(duration)
        s.onActivity { val event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),if(cancel)MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP,target.second.x,target.second.y,0);target.first.dispatchTouchEvent(event);event.recycle() }
        instrumentation.waitForIdleSync()
    }
    private fun screenshot(name:String) {
        instrumentation.waitForIdleSync();Thread.sleep(250)
        File(context.getExternalFilesDir(null),"spatial-$name.png").outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    private fun choiceIndex(s:ProcedureSession,correct:Boolean):Int {
        val choices=s.step.actions.shuffled(java.util.Random(s.data.getString("id").hashCode().toLong() xor s.index.toLong()))
        return choices.indexOfFirst { it.correct==correct }
    }
    @Test fun fireAndGasSpatialTouchesSaveReplayableEvidenceAndRecreate() {
        for(step in listOf("fire-aim","fire-sweep-left","fire-sweep-right","fire-sweep-return","gas-attendant")) {
            seed(step);val module=if(step.startsWith("fire"))"fire"else"gas"
            ActivityScenario.launch<ProcedureActivity>(Intent(context,ProcedureActivity::class.java).putExtra("moduleId",module)).use { s ->
                click(s,"I am in a safe training area")
                val p=target(s,choiceIndex(saved(module),true))
                if(InstrumentationRegistry.getArguments().getString("walkthrough")=="true")Thread.sleep(1400)
                screenshot(step)
                touch(s,p,1100)
                val record=saved(module);assertTrue("No completed spatial action: $step",record.feedback)
                val event=record.data.getJSONArray("events").getJSONObject(record.data.getJSONArray("events").length()-1)
                assertEquals("screen",event.getString("presentation"));assertTrue(event.has("spatial"))
                val proof=ProcedureSpatial.proof(event.getJSONObject("spatial"),step,step,"screen")
                assertTrue(proof.samples.last().elapsed>=ProcedureSpatial.HOLD_MS)
                s.recreate();assertEquals(record.data.toString(),saved(module).data.toString())
                if(InstrumentationRegistry.getArguments().getString("walkthrough")=="true")Thread.sleep(1200)
            }
        }
    }
    @Test fun partialHoldCancellationAndPauseCannotCompleteAnAction() {
        seed("fire-aim")
        ActivityScenario.launch<ProcedureActivity>(Intent(context,ProcedureActivity::class.java).putExtra("moduleId","fire")).use { s ->
            click(s,"I am in a safe training area");val before=saved("fire").data.toString()
            var p=target(s,choiceIndex(saved("fire"),true));touch(s,p,200,true);Thread.sleep(800)
            assertEquals(before,saved("fire").data.toString())
            touch(s,p,200);s.recreate();Thread.sleep(800)
            assertEquals(before,saved("fire").data.toString())
            p=target(s,choiceIndex(saved("fire"),true));touch(s,p,1100)
            assertTrue(saved("fire").feedback)
        }
    }
    @Test fun unsafeSpatialGasPositionStopsIndependentAttempt() {
        seed("gas-attendant",false)
        ActivityScenario.launch<ProcedureActivity>(Intent(context,ProcedureActivity::class.java).putExtra("moduleId","gas").putExtra("guided",false)).use { s ->
            click(s,"I am in a safe training area");touch(s,target(s,choiceIndex(saved("gas",false),false)),1100)
            val record=saved("gas",false)
            assertTrue(record.done);assertTrue(record.data.getJSONObject("result").getBoolean("stopped"))
            assertFalse(record.data.getJSONObject("result").getBoolean("certifiable"));screenshot("unsafe-stop")
        }
    }
    @Test fun unavailableCameraHoldCannotCreateEvidenceAndScreenStillWorks() {
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,context.checkSelfPermission(android.Manifest.permission.CAMERA))
        seed("fire-aim")
        ActivityScenario.launch<ProcedureActivity>(Intent(context,ProcedureActivity::class.java).putExtra("moduleId","fire")).use { s ->
            click(s,"I am in a safe training area");click(s,"Use camera AR")
            repeat(25) {
                val root=instrumentation.uiAutomation.rootInActiveWindow
                listOf("permission_deny_button","permission_deny_and_dont_ask_again_button").flatMap {
                    root?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/$it").orEmpty()
                }.firstOrNull()?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                Thread.sleep(80)
            }
            val before=saved("fire").data.toString()
            click(s,"Options")
            val option=instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("Use centre aiming").first()
            assertTrue(option.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            instrumentation.waitForIdleSync()
            var control:View?=null
            s.onActivity { a -> control=views(a.window.decorView).first { it.tag=="procedure-hold-aim" };assertTrue(control!!.isShown);control!!.requestRectangleOnScreen(Rect(0,0,control!!.width,control!!.height),true) }
            touch(s,control!! to ComponentProjection.Point(control!!.width/2f,control!!.height/2f),900)
            assertEquals(before,saved("fire").data.toString())
            s.recreate();assertEquals(before,saved("fire").data.toString())
            click(s,"Continue on screen");touch(s,target(s,choiceIndex(saved("fire"),true)),1100)
            val events=saved("fire").data.getJSONArray("events");val event=events.getJSONObject(events.length()-1)
            assertEquals("screen",event.getString("presentation"));assertTrue(event.has("spatial"))
        }
    }
    @Test fun hindiTextAlternativeRemainsReachableAndDoesNotReceiveSpatialCredit() {
        seed("gas-attendant",hi=true)
        try {
            ActivityScenario.launch<ProcedureActivity>(Intent(context,ProcedureActivity::class.java).putExtra("moduleId","gas")).use { s ->
                click(s,"मैं सुरक्षित प्रशिक्षण जगह पर हूँ");target(s,0);screenshot("hindi-targets")
                click(s,"लिखित क्रियाएँ उपयोग करें");click(s,saved("gas").step.actions.single { it.correct }.text(true))
                val record=saved("gas");val event=record.data.getJSONArray("events").getJSONObject(record.data.getJSONArray("events").length()-1)
                assertEquals("description",event.getString("presentation"));assertFalse(event.has("spatial"))
            }
        }finally { Store(context).use { it.hi=false } }
    }
}
