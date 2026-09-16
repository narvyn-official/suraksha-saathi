package com.narvyn.suraksha

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
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
class CameraWorkspaceTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) }else emptyList())
    private fun record()=Store(context).use { owner -> ProcedureStore(context,owner.workerId).use { ProcedureSession.restore(it.latest("fire",true)!!) } }
    private fun seed(feedback:Boolean,hi:Boolean) {
        Store(context).use { owner ->
            owner.hi=hi
            val session=ProcedureSession.create("fire",owner.workerId,true)
            while(session.step.id!="fire-aim") { session.choose(session.step.id,"description",session.data.getLong("updatedAt")+1);session.advance() }
            if(feedback)session.choose(session.step.id,"description",session.data.getLong("updatedAt")+1)
            ProcedureStore(context,owner.workerId).use { it.save(session.data) }
        }
    }
    private fun click(s:ActivityScenario<ProcedureActivity>,text:String) {
        repeat(50) {
            var done=false
            s.onActivity { a -> views(a.window.decorView).filterIsInstance<Button>().firstOrNull { it.text.toString()==text && it.isShown }?.let {
                it.requestRectangleOnScreen(Rect(0,0,it.width,it.height),true)
                val r=Rect();if(it.isEnabled && it.getGlobalVisibleRect(r) && r.width()>=it.width && r.height()>=it.height) { it.performClick();done=true }
            } }
            instrumentation.waitForIdleSync();if(done)return;Thread.sleep(80)
        };fail("Unreachable workspace action: $text")
    }
    private fun denyPermission() {
        repeat(20) {
            val root=instrumentation.uiAutomation.rootInActiveWindow
            listOf("permission_deny_button","permission_deny_and_dont_ask_again_button").flatMap { root?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/$it").orEmpty() }
                .firstOrNull()?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            Thread.sleep(60)
        }
    }
    private fun workspace(hi:Boolean) {
        assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.CAMERA))
        for(feedback in listOf(false,true)) {
            seed(feedback,hi)
            ActivityScenario.launch<ProcedureActivity>(Intent(context,ProcedureActivity::class.java).putExtra("moduleId","fire").putExtra("camera",true)).use { s ->
                click(s,if(hi)"मैं सुरक्षित प्रशिक्षण जगह पर हूँ"else"I am in a safe training area");denyPermission()
                s.recreate();instrumentation.waitForIdleSync()
                s.onActivity { a ->
                    val all=views(a.window.decorView);val scene=all.filterIsInstance<ProcedureSceneView>().single()
                    assertTrue(scene.cameraSelected)
                    val surface=all.single { it.tag=="procedure-gl-surface" }
                    assertTrue("Camera workspace collapsed",surface.height>=a.window.decorView.height*.25f)
                    val expected=if(feedback)if(hi)"प्रक्रिया जारी रखें"else"Continue procedure"else if(hi)"स्क्रीन पर जारी रखें"else"Continue on screen"
                    val control=all.filterIsInstance<Button>().single { it.text.toString()==expected }
                    val rect=Rect();assertTrue(control.getGlobalVisibleRect(rect));assertEquals(control.height,rect.height())
                }
                File(context.getExternalFilesDir(null),"workspace-${if(hi)"hi"else"en"}-${if(feedback)"feedback"else"camera-off"}.png").outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) }
                val before=record().data.toString()
                if(feedback) { click(s,if(hi)"प्रक्रिया जारी रखें"else"Continue procedure");assertEquals("fire-squeeze",record().step.id) }
                click(s,if(hi)"स्क्रीन पर जारी रखें"else"Continue on screen")
                s.onActivity { a -> assertFalse(views(a.window.decorView).filterIsInstance<ProcedureSceneView>().single().cameraSelected) }
                if(!feedback)assertEquals(before,record().data.toString())
            }
        }
    }
    @Test fun systemBackLeavesTheCameraWithoutAdvancingSavedFeedback(){
        org.junit.Assume.assumeTrue(android.os.Build.HARDWARE in listOf("ranchu","goldfish"))
        seed(true,false)
        ActivityScenario.launch<ProcedureActivity>(Intent(context,ProcedureActivity::class.java).putExtra("moduleId","fire").putExtra("camera",true)).use{s->
            click(s,"I am in a safe training area");denyPermission();val before=record().data.toString()
            instrumentation.uiAutomation.executeShellCommand("input keyevent 4").use{java.io.FileInputStream(it.fileDescriptor).readBytes()};Thread.sleep(600)
            s.onActivity{a->assertFalse(a.isFinishing);assertFalse(views(a.window.decorView).filterIsInstance<ProcedureSceneView>().single().cameraSelected)}
            assertEquals(before,record().data.toString())
        }
    }
    @Test fun cameraEntryAndSavedFeedbackKeepSceneAndFooterAvailable()=workspace(false)
    @Test fun hindiCameraWorkspaceKeepsFallbackAndContinuationReachable() {
        try { workspace(true) }finally { Store(context).use { it.hi=false } }
    }
}
