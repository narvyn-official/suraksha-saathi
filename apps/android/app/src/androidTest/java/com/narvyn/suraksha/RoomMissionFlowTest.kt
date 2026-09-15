package com.narvyn.suraksha

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.*
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RoomMissionFlowTest {
    private val ins get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=ins.targetContext
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList())
    private fun phase(s:ActivityScenario<RoomMissionActivity>):String {var p="";s.onActivity{a->val f=RoomMissionActivity::class.java.getDeclaredField("mission").apply{isAccessible=true};p=(f.get(a)as RoomMission).phase};return p}
    private fun button(s:ActivityScenario<RoomMissionActivity>,text:String){s.onActivity{a->views(a.window.decorView).filterIsInstance<Button>().firstOrNull{it.text.toString()==text}?.performClick()?:run{val root=ins.uiAutomation.rootInActiveWindow;val nodes=root?.findAccessibilityNodeInfosByText(text).orEmpty();check(nodes.isNotEmpty()){text};nodes.first().performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)}};ins.waitForIdleSync()}
    private fun point(s:ActivityScenario<RoomMissionActivity>,id:String):Pair<Float,Float>{var result:Pair<Float,Float>?=null
        repeat(50){s.onActivity{a->val view=views(a.window.decorView).filterIsInstance<RoomMissionView>().single();val f=RoomMissionView::class.java.getDeclaredField("image").apply{isAccessible=true};val img=f.get(view)as?RoomMissionView.Image;val p=img?.targets?.get(id);if(view.ready&&p!=null){val at=IntArray(2);view.getLocationOnScreen(at);result=p.x+at[0] to p.y+at[1]}};if(result!=null)return result!!;Thread.sleep(60)};error("Target not displayed: $id")}
    private fun gesture(a:Pair<Float,Float>,b:Pair<Float,Float>,duration:Long,beforeUp:()->Unit={}){val start=SystemClock.uptimeMillis()
        fun event(action:Int,x:Float,y:Float){val e=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,x,y,0);e.source=InputDevice.SOURCE_TOUCHSCREEN;check(ins.uiAutomation.injectInputEvent(e,true));e.recycle()}
        event(MotionEvent.ACTION_DOWN,a.first,a.second)
        val steps=(duration/45).toInt().coerceAtLeast(1)
        for(i in 1..steps){Thread.sleep(duration/steps);val f=i.toFloat()/steps;event(MotionEvent.ACTION_MOVE,a.first+(b.first-a.first)*f,a.second+(b.second-a.second)*f)}
        try{beforeUp()}finally{event(MotionEvent.ACTION_UP,b.first,b.second)};ins.waitForIdleSync();Thread.sleep(120)
    }
    private fun shot(name:String){File(context.getExternalFilesDir(null),name).outputStream().use{ins.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)};Thread.sleep(500)}
    private fun start(module:String):ActivityScenario<RoomMissionActivity>{val now=SystemClock.uptimeMillis();val cancel=MotionEvent.obtain(now,now,MotionEvent.ACTION_CANCEL,0f,0f,0);cancel.source=InputDevice.SOURCE_TOUCHSCREEN;ins.uiAutomation.injectInputEvent(cancel,true);cancel.recycle();Store(context).use{it.hi=false};val s=ActivityScenario.launch<RoomMissionActivity>(Intent(context,RoomMissionActivity::class.java).putExtra("moduleId",module).putExtra("camera",false));Thread.sleep(250)
        var clicked=false
        repeat(40){if(!clicked){val node=ins.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Start in a clear area")?.firstOrNull();if(node!=null){node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);clicked=true}else Thread.sleep(100)}}
        assertTrue("Briefing must be acknowledged",clicked);Thread.sleep(900);return s}
    @Test fun fireMissionRequiresSceneGesturesAndPersistsMeasuredSweep(){start("fire").use{s->
        point(s,"alarm").let{gesture(it,it,180)};assertEquals("PIN",phase(s))
        val pin=point(s,"pin");val out=point(s,"pin-out");val dx=out.first-pin.first;val dy=out.second-pin.second;val length=kotlin.math.hypot(dx,dy);gesture(pin,pin.first+dx/length*220 to pin.second+dy/length*220,600);assertEquals("AIM",phase(s))
        point(s,"base").let{gesture(it,it,90)};Thread.sleep(450);assertEquals("AIM",phase(s)) // A released tap cannot accrue alignment.
        s.moveToState(Lifecycle.State.CREATED);s.moveToState(Lifecycle.State.RESUMED);Thread.sleep(500);assertEquals("AIM",phase(s))
        point(s,"base").let{gesture(it,it,650)};assertEquals("SWEEP",phase(s));shot("room-fire-sweep.png")
        val left=point(s,"left");val right=point(s,"right");val a=left.first+3 to left.second;val b=right.first-3 to right.second
        gesture(a,b,2300){var detail="";var current="";ins.runOnMainSync{val activity=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<RoomMissionActivity>().single();val f=RoomMissionActivity::class.java.getDeclaredField("mission").apply{isAccessible=true};val m=f.get(activity)as RoomMission;detail=m.toJson().toString();current=m.phase};assertEquals(detail,"WITHDRAW",current)};assertEquals("WITHDRAW",phase(s));shot("room-fire-withdraw.png")
        point(s,"safe").let{gesture(it,it,180)};assertEquals("COMPLETE",phase(s))
        Store(context).use{owner->RoomMissionStore(context,owner.workerId).use{store->val r=store.records().first{it.getString("module")=="fire"};assertEquals("screen",r.getString("mode"));val m=r.getJSONObject("mission");assertTrue(m.getBoolean("completed"));assertFalse(m.getJSONObject("result").getBoolean("certifiable"));assertEquals(2,m.getJSONArray("measurements").length())}}
        s.recreate();assertEquals("COMPLETE",phase(s))
    }}
    @Test fun gasMissionBuildsBoundaryAndKeepsAttendantOutside(){start("gas").use{s->
        point(s,"meter").let{gesture(it,it,620)};assertEquals("BARRIER",phase(s));shot("room-gas-open.png")
        gesture(point(s,"barrier-left"),point(s,"barrier-right"),750);assertEquals("ATTENDANT",phase(s))
        gesture(point(s,"meter"),point(s,"safe"),750);assertEquals("REFUSE",phase(s));shot("room-gas-attendant.png")
        point(s,"safe").let{gesture(it,it,180)};assertEquals("COMPLETE",phase(s))
        Store(context).use{owner->RoomMissionStore(context,owner.workerId).use{store->val m=store.records().first{it.getString("module")=="gas"}.getJSONObject("mission");assertEquals("outside-entry-refused",m.getJSONObject("result").getString("outcome"))}}
    }}
}
