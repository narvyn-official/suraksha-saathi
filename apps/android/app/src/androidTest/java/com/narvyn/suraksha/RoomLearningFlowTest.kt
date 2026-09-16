package com.narvyn.suraksha

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import org.json.JSONObject

/** Emulator UI tests. No real camera observations, learning results or certificates are fabricated. */
@RunWith(AndroidJUnit4::class)
class RoomLearningFlowTest {
    private val ins get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=ins.targetContext
    private fun emulator(){assumeTrue(Build.HARDWARE in listOf("ranchu","goldfish")||Build.MODEL.contains("sdk_gphone"))}
    private fun tap(text:String){
        repeat(40){
            val node=ins.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.firstOrNull{it.text?.toString().equals(text,true)&&it.isClickable&&it.isEnabled}
            if(node!=null&&node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)){ins.waitForIdleSync();Thread.sleep(160);return}
            Thread.sleep(100)
        };error("Visible action missing: $text")
    }
    private fun shot(name:String){Thread.sleep(500);File(context.getExternalFilesDir(null),name).outputStream().use{ins.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun mission(activity:RoomMissionActivity)=RoomMissionActivity::class.java.getDeclaredField("mission").apply{isAccessible=true}.get(activity)as RoomMission
    private fun scene(activity:RoomMissionActivity)=RoomMissionActivity::class.java.getDeclaredField("scene").apply{isAccessible=true}.get(activity)as RoomMissionView
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList())
    private fun shell(command:String)=ins.uiAutomation.executeShellCommand(command).use{FileInputStream(it.fileDescriptor).bufferedReader().readText().trim()}
    @Test fun hindiCoachKeepsNavigationVisibleAtLargeFontInLandscape(){
        emulator();val scale=shell("settings get system font_scale");val oldHi=Store(context).use{val old=it.hi;it.hi=true;old}
        try{
            shell("settings put system font_scale 2.0");Thread.sleep(400)
            ActivityScenario.launch<RoomMissionActivity>(Intent(context,RoomMissionActivity::class.java).putExtra("camera",false)).use{s->
                tap("खाली क्षेत्र में शुरू करें")
                s.onActivity{it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE};Thread.sleep(900)
                tap("मार्गदर्शन");tap("अगला");Thread.sleep(500)
                val root=ins.uiAutomation.rootInActiveWindow;val window=Rect();root.getBoundsInScreen(window)
                for(label in listOf("पिछला","बंद करें")){
                    val node=root.findAccessibilityNodeInfosByText(label).first{it.text?.toString()==label&&it.isClickable}
                    val bounds=Rect();node.getBoundsInScreen(bounds)
                    assertTrue("$label must remain visible",node.isVisibleToUser&&window.contains(bounds)&&bounds.height()>0)
                }
                shot("learning-coach-hi-landscape-font200.png")
                val listen=root.findAccessibilityNodeInfosByText("सुनें / रोकें").first{it.text?.toString()=="सुनें / रोकें"}
                listen.performAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id);Thread.sleep(400)
                val visible=ins.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("सुनें / रोकें").first{it.text?.toString()=="सुनें / रोकें"}
                val bounds=Rect();visible.getBoundsInScreen(bounds)
                assertTrue("Listen must be reachable without shrinking its text",visible.isVisibleToUser&&window.contains(bounds)&&bounds.height()>=context.dp(44))
                tap("बंद करें");s.onActivity{assertTrue(mission(it).events.isEmpty())}
            }
        }finally{shell(if(scale=="null")"settings delete system font_scale"else"settings put system font_scale $scale");Store(context).use{it.hi=oldHi}}
    }
    @Test fun actionReviewQueueStartsANewMatchingRecallWithoutChangingTheCompletedRecord(){
        emulator();Store(context).use{owner->
            val oldHi=owner.hi;owner.hi=false
            val id="learning-flow-${System.nanoTime()}";val m=RoomMission("gas")
            listOf("inspect-meter","select-specified-ppe","close-barrier","place-attendant-outside","send-buddy-check","confirm-buddy-ack","refuse-entry").forEachIndexed{i,action->assertTrue(m.act(action,i.toLong()))}
            val record=JSONObject().put("id",id).put("workerId",owner.workerId).put("module","gas").put("mode","camera")
                .put("updatedAt",System.currentTimeMillis()).put("mission",m.toJson()).put("coaching",RoomCoaching("gas",false).toJson())
            RoomMissionStore(context,owner.workerId).use{store->
                store.save(record)
                try{ActivityScenario.launch<RecallActivity>(Intent(context,RecallActivity::class.java)).use{s->
                    val name=Curriculum(context).module("gas").local("title",false)
                    val description="Rehearse from memory: $name. Camera AR rehearsal"
                    s.onActivity{a->val button=views(a.window.decorView).filterIsInstance<android.widget.Button>().single{it.contentDescription?.toString()==description};button.requestRectangleOnScreen(Rect(0,0,button.width,button.height),true)}
                    Thread.sleep(500);shot("learning-action-review-queue.png")
                    s.onActivity{a->assertTrue(views(a.window.decorView).filterIsInstance<android.widget.Button>().single{it.contentDescription?.toString()==description}.performClick())}
                    ins.waitForIdleSync();Thread.sleep(500)
                    ins.runOnMainSync{
                        val a=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<RoomMissionActivity>().single()
                        assertEquals("gas",a.intent.getStringExtra("moduleId"));assertTrue(a.intent.getBooleanExtra("camera",false));assertTrue(a.intent.getBooleanExtra("recall",false))
                        assertNull(a.intent.getStringExtra("resumeId"));assertTrue(mission(a).events.isEmpty());assertFalse(scene(a).ready);a.finish()
                    }
                    assertEquals(record.toString(),store.record(id).toString())
                }}finally{store.writableDatabase.delete("room_missions","id=? AND worker=?",arrayOf(id,owner.workerId));owner.hi=oldHi}
            }
        }
    }
    @Test fun setupCardsNeverStartTrainingAndCoachExplainsWithoutChangingEvidence(){
        emulator();Store(context).use{it.hi=false}
        ActivityScenario.launch<RoomMissionActivity>(Intent(context,RoomMissionActivity::class.java).putExtra("moduleId","fire").putExtra("camera",false)).use { s ->
            tap("Setup guide");tap("Next")
            assertTrue(ins.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("2 · Choose and place").isNotEmpty())
            s.onActivity{assertTrue(mission(it).events.isEmpty());assertFalse(scene(it).ready)}
            shot("learning-setup-guide.png");tap("Close");tap("Start in a clear area")
            Thread.sleep(900);tap("Coach")
            s.onActivity{assertEquals("ALARM",mission(it).phase);assertFalse(scene(it).ready)}
            tap("Next")
            assertTrue(ins.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("Why this matters").isNotEmpty())
            shot("learning-coach-why.png")
            s.onActivity{assertTrue(mission(it).events.isEmpty())}
            tap("Close")
            s.onActivity{assertEquals("ALARM",mission(it).phase);assertTrue(mission(it).events.isEmpty())}
        }
    }
    @Test fun selectingAVisiblePatchInvalidatesOldPlacementEligibilityWithoutCreatingAnAnchor(){
        emulator()
        ActivityScenario.launch<RoomMissionActivity>(Intent(context,RoomMissionActivity::class.java).putExtra("camera",false)).use{s->s.onActivity{activity->
            val fixture=RoomMissionView(activity,"fire",true,false)
            try{
                val w=720;val h=960
                fixture.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));fixture.layout(0,0,w,h)
                val imageField=RoomMissionView::class.java.getDeclaredField("image").apply{isAccessible=true}
                RoomMissionView::class.java.getDeclaredField("foreground").apply{isAccessible=true}.setBoolean(fixture,true)
                val cursor=RoomMissionView::class.java.getDeclaredField("scanCursor").apply{isAccessible=true}.get(fixture)as ArPlacementCursor
                val original=cursor.snapshot(w,h)
                fun fresh() {imageField.set(fixture,RoomMissionView.Image(0,SystemClock.elapsedRealtime(),0,true,"Synthetic placement fixture",frameWidth=w,frameHeight=h,canPlace=true,aimSerial=original.serial))}
                fresh();assertTrue(fixture.canPlace)
                val overlay=RoomMissionView::class.java.getDeclaredField("overlay").apply{isAccessible=true}.get(fixture)as View
                var actions=0;fixture.onAction={actions++}
                val start=SystemClock.uptimeMillis()
                val down=MotionEvent.obtain(start,start,MotionEvent.ACTION_DOWN,120f,700f,0)
                try{overlay.dispatchTouchEvent(down)}finally{down.recycle()}
                SystemClock.sleep(100);fresh()
                val up=MotionEvent.obtain(start,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,120f,700f,0)
                try{overlay.dispatchTouchEvent(up)}finally{up.recycle()}
                assertEquals(120f,cursor.snapshot(w,h).x,.001f);assertEquals(700f,cursor.snapshot(w,h).y,.001f)
                assertFalse(fixture.canPlace);assertFalse(fixture.ready);fixture.place();assertEquals(0,actions)
                val anchors=RoomMissionView::class.java.getDeclaredField("anchors").apply{isAccessible=true}.get(fixture)as List<*>
                assertTrue(anchors.isEmpty());fixture.pause();assertEquals(w/2f,cursor.snapshot(w,h).x,.001f)
            }finally{fixture.close()}
        }}
    }
}
