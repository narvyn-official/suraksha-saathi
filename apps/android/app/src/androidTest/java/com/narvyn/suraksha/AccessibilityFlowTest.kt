package com.narvyn.suraksha

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.view.View
import android.view.MotionEvent
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

/** Run at system font_scale=2.0 with camera permission revoked. No TalkBack speech is claimed. */
@RunWith(AndroidJUnit4::class)
class AccessibilityFlowTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun all(v: View): List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap { all(v.getChildAt(it)) }else emptyList())
    private fun nodes(n: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if(n==null)emptyList()else listOf(n)+(0 until n.childCount).flatMap { nodes(n.getChild(it)) }
    private fun visibleNodes()=nodes(instrumentation.uiAutomation.rootInActiveWindow).filter { it.isVisibleToUser }
    private fun shot(name: String) {
        instrumentation.waitForIdleSync();Thread.sleep(250)
        File(context.getExternalFilesDir(null),name).outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) }
        if(InstrumentationRegistry.getArguments().getString("accessibilityDemo")=="true")Thread.sleep(1000)
    }
    private fun equipmentPixels(s: ActivityScenario<EquipmentActivity>) {
        repeat(30) {
            val done=java.util.concurrent.CountDownLatch(1);var rendered=false
            s.onActivity { a ->
                val surface=all(a.window.decorView).filterIsInstance<GLSurfaceView>().single()
                val bitmap=Bitmap.createBitmap(surface.width,surface.height,Bitmap.Config.ARGB_8888)
                android.view.PixelCopy.request(surface,bitmap,{ result ->
                    val colors=HashSet<Int>()
                    if(result==android.view.PixelCopy.SUCCESS)for(x in 0 until bitmap.width step 10)for(y in 0 until bitmap.height step 10)colors.add(bitmap.getPixel(x,y))
                    rendered=colors.size>8;bitmap.recycle();done.countDown()
                },android.os.Handler(android.os.Looper.getMainLooper()))
            }
            assertTrue(done.await(5,java.util.concurrent.TimeUnit.SECONDS))
            if(rendered)return
            Thread.sleep(100)
        }
        fail("Equipment pixels missing at large font/recreation")
    }
    private fun largeFont(a: android.app.Activity) { assertTrue("Run with 200% system font",a.resources.configuration.fontScale>=1.9f) }
    private fun buttonsFit(a: android.app.Activity) {
        all(a.window.decorView).filterIsInstance<Button>().filter { it.isShown && it.width>0 }.forEach { b ->
            val layout=b.layout ?: return@forEach
            assertTrue("Small target: ${b.text}",b.width>=a.dp(48) && b.height>=a.dp(48))
            assertTrue("Clipped button text: ${b.text}",layout.height<=b.height-b.compoundPaddingTop-b.compoundPaddingBottom+2)
            assertTrue("Ellipsized button: ${b.text}",(0 until layout.lineCount).all { layout.getEllipsisCount(it)==0 })
        }
    }
    /** Uses actual service-facing scroll/show/click actions, not View.performClick(). */
    private fun accessibleClick(text: String) {
        repeat(45) {
            instrumentation.waitForIdleSync()
            val all=nodes(instrumentation.uiAutomation.rootInActiveWindow)
            val node=all.firstOrNull { it.text?.toString()?.equals(text,ignoreCase=true)==true || it.contentDescription?.toString()==text }
            if(node!=null) {
                if(!node.isVisibleToUser)node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                else {
                    var actionNode=node
                    repeat(3) { if(actionNode!=null && !actionNode!!.isClickable)actionNode=actionNode!!.parent }
                    if(actionNode?.isClickable==true && actionNode!!.isEnabled) {
                        actionNode!!.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                        assertTrue("Accessibility click: $text",actionNode!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                        instrumentation.waitForIdleSync();Thread.sleep(150);return
                    }
                }
            }
            all.firstOrNull { it.isScrollable && it.isVisibleToUser }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            Thread.sleep(100)
        }
        fail("No reachable, enabled accessibility action: $text")
    }
    private fun state(module: String)=Store(context).use { ComponentSession.restore(module,it.componentRecords()[module]) }
    private fun denyPermission() {
        repeat(60) {
            val root=instrumentation.uiAutomation.rootInActiveWindow
            val deny=listOf("permission_deny_button","permission_deny_and_dont_ask_again_button").flatMap { root?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/$it").orEmpty() }.firstOrNull()
            if(deny!=null) { assertTrue(deny.performAction(AccessibilityNodeInfo.ACTION_CLICK));Thread.sleep(300);return }
            if(root?.packageName?.toString()==context.packageName && context.checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_DENIED && it>5)return
            Thread.sleep(100)
        }
        fail("Permission dialog not dismissed")
    }
    @Test fun equipmentViewportAndNativeControlsSurviveLargeFontAndRotation() {
        try { for(hi in listOf(false,true)) {
            fun t(en: String,hindi: String)=if(hi)hindi else en
            Store(context).use { it.hi=hi }
            ActivityScenario.launch<EquipmentActivity>(Intent(context,EquipmentActivity::class.java).putExtra("moduleId","fire")).use { s ->
                s.onActivity { it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT };Thread.sleep(400)
                s.onActivity { a -> largeFont(a);buttonsFit(a);assertTrue(all(a.window.decorView).filterIsInstance<GLSurfaceView>().single().height>=a.dp(240)) }
                assertTrue(visibleNodes().any { it.text?.toString()==t("Explore the equipment","उपकरण को देखें") && it.isHeading })
                equipmentPixels(s)
                shot(if(hi)"a11y-equipment-hindi-large.png"else"a11y-equipment-large.png")
                accessibleClick(t("Rotate right","दाएँ घुमाएँ"));accessibleClick(t("Tilt up","ऊपर झुकाएँ"));accessibleClick(t("Change zoom","ज़ूम बदलें"))
                var description=""
                s.onActivity { a -> description=(all(a.window.decorView).single { it.tag=="equipment-view-state" } as TextView).text.toString();assertTrue(description.contains("55°") && description.contains("15°")) }
                s.recreate();s.onActivity { a -> assertEquals(description,(all(a.window.decorView).single { it.tag=="equipment-view-state" } as TextView).text.toString()) }
                equipmentPixels(s)
                s.onActivity { a ->
                    val surface=all(a.window.decorView).filterIsInstance<GLSurfaceView>().single()
                    val now=android.os.SystemClock.uptimeMillis()
                    listOf(MotionEvent.ACTION_DOWN to 40f,MotionEvent.ACTION_MOVE to 100f,MotionEvent.ACTION_CANCEL to 100f).forEachIndexed { i,(action,x) ->
                        val event=MotionEvent.obtain(now,now+i*16L,action,x,40f,0);surface.dispatchTouchEvent(event);event.recycle()
                    }
                    val updated=(all(a.window.decorView).single { it.tag=="equipment-view-state" } as TextView).text.toString()
                    assertNotEquals("Interrupted gesture must update its accessible state",description,updated)
                }
                accessibleClick(t("Inspect parts","पुर्ज़े जानें"));accessibleClick(t("Valve, lever & pin","वाल्व, लीवर और पिन"));shot(if(hi)"a11y-inspect-hindi.png"else"a11y-inspect.png");accessibleClick(t("Close","बंद करें"))
                s.onActivity { it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE };Thread.sleep(600)
                s.onActivity { a -> assertTrue("Landscape viewport collapsed",all(a.window.decorView).filterIsInstance<GLSurfaceView>().single().height>=a.dp(240));buttonsFit(a) }
                accessibleClick(t("Rotate left","बाएँ घुमाएँ"));shot(if(hi)"a11y-equipment-hindi-landscape.png"else"a11y-equipment-landscape.png")
                accessibleClick(t("Back to lesson","पाठ पर वापस जाएँ"))
            }
        }}finally { Store(context).use { it.hi=false } }
    }
    @Test fun descriptionPracticeWorksThroughAccessibilityActionsInBothLanguages() {
        val assessments=Store(context).use { it.attempts().map { a->a.toString() } }
        try { for(hi in listOf(false,true)) {
            fun t(en: String,hindi: String)=if(hi)hindi else en
            Store(context).use { it.hi=hi;it.saveComponent(ComponentSession.start("ppe",null).data) }
            ActivityScenario.launch<ComponentPracticeActivity>(Intent(context,ComponentPracticeActivity::class.java).putExtra("moduleId","ppe")).use { s ->
                s.onActivity { it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT };Thread.sleep(400);s.onActivity { largeFont(it) }
                accessibleClick(t("Use text descriptions","लिखित विवरण उपयोग करें"));assertTrue(state("ppe").descriptions)
                while(!state("ppe").done) {
                    val current=state("ppe")
                    if(current.stage==0)accessibleClick(t("Try without labels","बिना नाम के कोशिश करें"))
                    else if(current.answer==null) { s.onActivity { buttonsFit(it) };accessibleClick(current.target.description(hi)) }
                    else {
                        if(current.index==0 && current.stage==1) { val saved=current.data.toString();s.recreate();assertEquals(saved,state("ppe").data.toString());shot(if(hi)"a11y-component-hindi-feedback.png"else"a11y-component-feedback.png") }
                        accessibleClick(if(current.stage==1)t("Try reordered choices","बदले क्रम में कोशिश करें")else t("Continue","आगे बढ़ें"))
                    }
                }
                assertEquals(6,state("ppe").data.getInt("descriptionCorrect"));assertEquals(0,state("ppe").data.getInt("visualCorrect"));assertEquals(6,state("ppe").data.getJSONArray("events").length())
                accessibleClick(t("Save & return","सहेजें और लौटें"))
            }
        }}finally { Store(context).use { it.hi=false } }
        assertEquals(assessments,Store(context).use { it.attempts().map { a->a.toString() } })
    }
    @Test fun savedCameraFeedbackAdvancesWithoutTrackingAtLargeFont() {
        assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.CAMERA))
        try { for(hi in listOf(false,true)) {
            fun t(en: String,hindi: String)=if(hi)hindi else en
            Store(context).use { it.hi=hi;val fresh=ComponentSession.start("fire",null);fresh.advance(1);fresh.choose(fresh.target.id,2,"screen");it.saveComponent(fresh.data) }
            ActivityScenario.launch<ComponentPracticeActivity>(Intent(context,ComponentPracticeActivity::class.java).putExtra("moduleId","fire")).use { s ->
                s.onActivity { largeFont(it) }
                accessibleClick(t("Use camera AR · fire","कैमरा AR उपयोग करें · आग"));denyPermission();s.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);s.recreate()
                val events=state("fire").data.getJSONArray("events").toString()
                accessibleClick(t("Try the changed view","बदले दृश्य में कोशिश करें"))
                assertEquals(2,state("fire").stage);assertNull(state("fire").answer);assertEquals(events,state("fire").data.getJSONArray("events").toString());assertEquals(0,state("fire").data.optInt("cameraCorrect"))
                s.onActivity { a -> assertFalse(all(a.window.decorView).filterIsInstance<Button>().single { it.text.toString()==t("I’m not sure","मुझे निश्चित नहीं है") }.isEnabled) }
                accessibleClick(t("Continue on screen","स्क्रीन पर जारी रखें"));accessibleClick(t("Use text descriptions","लिखित विवरण उपयोग करें"));assertTrue(state("fire").descriptions)
            }
        }}finally { Store(context).use { it.hi=false } }
    }
    @Test fun reviewChoicesHaveDistinctLabelsAndPreserveAssessmentAtLargeFont() {
        val curriculum=Curriculum(context)
        try { for(hi in listOf(false,true)) {
            fun t(en: String,hindi: String)=if(hi)hindi else en
            val module=curriculum.module("ppe");val q=module.getJSONArray("questions").getJSONObject(0)
            val attempt=Store(context).use { it.hi=hi;TrainingSession.create(module,it.workerId,curriculum.version,false,"screen").apply { answer(q.getJSONArray("options").objects().first { !it.optBoolean("correct") }.getString("id"));it.save(this) } }
            val original=attempt.data.toString()
            ActivityScenario.launch(RecallActivity::class.java).use { s ->
                s.onActivity { a -> largeFont(a);buttonsFit(a);val buttons=all(a.window.decorView).filterIsInstance<Button>().filter { it.text.toString()==t("Recall this decision","यह निर्णय याद करें") };assertTrue(buttons.isNotEmpty());assertTrue(buttons.all { !it.contentDescription.isNullOrBlank() && it.contentDescription.toString()!=it.text.toString() });assertEquals(buttons.size,buttons.map { it.contentDescription.toString() }.distinct().size) }
                accessibleClick(t("Recall this decision: ","यह निर्णय याद करें: ")+module.local("title",hi)+". "+q.local("prompt",hi))
                accessibleClick(q.getJSONArray("options").objects().first { it.optBoolean("correct") }.local("text",hi));s.recreate()
                assertTrue(visibleNodes().any { it.text?.toString()==t("Remember. Decide. Reflect.","याद करें। निर्णय लें। सोचें।") && it.isHeading })
                shot(if(hi)"a11y-recall-hindi-feedback.png"else"a11y-recall-feedback.png")
                s.onActivity { buttonsFit(it) };accessibleClick(t("Back to reviews","दोहराव पर वापस जाएँ"));accessibleClick(t("Back to learning","सीखने पर वापस जाएँ"))
            }
            assertEquals(original,Store(context).use { it.attempt(attempt.data.getString("id")).toString() })
        }}finally { Store(context).use { it.hi=false } }
    }
}
