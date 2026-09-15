package com.narvyn.suraksha
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
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
class RoomMissionWorkspaceTest {
    private val ins get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=ins.targetContext
    private fun all(v:View):List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap{all(v.getChildAt(it))}else emptyList())
    private fun dialog(text:String){var clicked=false;repeat(50){if(!clicked){val n=ins.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.firstOrNull();if(n!=null){clicked=n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)}else Thread.sleep(80)}};assertTrue(text,clicked);Thread.sleep(250)}
    @Test fun permissionOffCannotCompleteCameraActions(){
        assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.CAMERA));Store(context).use{it.hi=false}
        ActivityScenario.launch<RoomMissionActivity>(Intent(context,RoomMissionActivity::class.java).putExtra("camera",true)).use{s->
            dialog("Start in a clear area")
            repeat(20){val root=ins.uiAutomation.rootInActiveWindow;listOf("permission_deny_button","permission_deny_and_dont_ask_again_button").flatMap{root?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/$it").orEmpty()}.firstOrNull()?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);Thread.sleep(60)}
            s.recreate();Thread.sleep(300)
            s.onActivity{a->val views=all(a.window.decorView);val scene=views.filterIsInstance<RoomMissionView>().single();assertFalse(scene.ready)
                val f=RoomMissionActivity::class.java.getDeclaredField("mission").apply{isAccessible=true};val mission=f.get(a)as RoomMission;assertEquals("ALARM",mission.phase);assertTrue(mission.events.isEmpty())
                assertTrue(views.filterIsInstance<Button>().any{it.text.toString()=="Enable camera"&&it.isShown})
            }
            File(context.getExternalFilesDir(null),"room-camera-off.png").outputStream().use{ins.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}
        }
    }
    /** Run only on emulator with: adb shell cmd thermalservice override-status 4. */
    @Test fun criticalHeatPausesCameraAndOffersRecovery(){
        assertEquals(android.os.PowerManager.THERMAL_STATUS_CRITICAL,context.getSystemService(android.os.PowerManager::class.java).currentThermalStatus)
        Store(context).use{it.hi=false}
        ActivityScenario.launch<RoomMissionActivity>(Intent(context,RoomMissionActivity::class.java).putExtra("camera",true)).use{s->
            dialog("Start in a clear area")
            repeat(2){
                s.onActivity{a->
                    val views=all(a.window.decorView);val scene=views.filterIsInstance<RoomMissionView>().single()
                    assertFalse(scene.ready);assertFalse(scene.canPlace);assertTrue(scene.needsRetry)
                    assertTrue(views.filterIsInstance<android.widget.TextView>().any{it.text.contains("Phone too hot")})
                    val retry=views.filterIsInstance<Button>().single{it.text.toString()=="Retry camera"};assertTrue(retry.isEnabled);retry.performClick()
                    assertFalse(scene.ready);assertTrue(scene.needsRetry)
                    val f=RoomMissionActivity::class.java.getDeclaredField("mission").apply{isAccessible=true};val m=f.get(a)as RoomMission
                    assertEquals("ALARM",m.phase);assertTrue(m.events.isEmpty())
                }
                s.recreate();Thread.sleep(250)
            }
            File(context.getExternalFilesDir(null),"room-thermal-recovery.png").outputStream().use{ins.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}
        }
    }
    /** Synthetic SWEEP fixture on emulator only: verifies recovery UI, never camera evidence. */
    @Test fun interruptedDischargeDoesNotSwallowRetryTouch(){
        assertEquals(4,context.getSystemService(android.os.PowerManager::class.java).currentThermalStatus)
        Store(context).use{it.hi=false}
        ActivityScenario.launch<RoomMissionActivity>(Intent(context,RoomMissionActivity::class.java).putExtra("camera",true)).use{s->
            dialog("Start in a clear area")
            var expectedEvents=0
            s.onActivity{a->
                val f=RoomMissionActivity::class.java.getDeclaredField("mission").apply{isAccessible=true};val m=f.get(a)as RoomMission
                m.act("alarm",10);m.act("pin-drag",20);for(now in listOf(100L,200L,300L,400L))m.aim(0f,0f,false,now,true)
                assertEquals("SWEEP",m.phase);expectedEvents=m.events.size
                RoomMissionActivity::class.java.getDeclaredField("placementCount").apply{isAccessible=true}.setInt(a,3)
                RoomMissionActivity::class.java.getDeclaredMethod("render").apply{isAccessible=true}.invoke(a)
                val control=all(a.window.decorView).filterIsInstance<Button>().single{it.text.toString()=="Retry camera"}
                val now=android.os.SystemClock.uptimeMillis()
                for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)){
                    val e=android.view.MotionEvent.obtain(now,now+100,action,control.width/2f,control.height/2f,0)
                    control.dispatchTouchEvent(e);e.recycle()
                }
            }
            ins.waitForIdleSync()
            s.onActivity{a->
                val f=RoomMissionActivity::class.java.getDeclaredField("mission").apply{isAccessible=true};val m=f.get(a)as RoomMission
                assertEquals("Retry must not fabricate a discharge release",expectedEvents,m.events.size)
                assertEquals(0,RoomMissionActivity::class.java.getDeclaredField("placementCount").apply{isAccessible=true}.getInt(a))
                val scene=all(a.window.decorView).filterIsInstance<RoomMissionView>().single();assertTrue(scene.needsRetry);assertFalse(scene.ready)
                scene.pause();assertTrue("Recovery must survive modal/focus pause",scene.needsRetry);scene.resume();assertTrue(scene.needsRetry)
            }
        }
    }
    @Test fun hindiWorkspaceKeepsSceneAndControlsInPortraitAndLandscape(){
        Store(context).use{it.hi=true}
        try {ActivityScenario.launch<RoomMissionActivity>(Intent(context,RoomMissionActivity::class.java).putExtra("camera",false)).use{s->
            dialog("खाली क्षेत्र में शुरू करें");Thread.sleep(700)
            for(orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)){
                s.onActivity{it.requestedOrientation=orientation};Thread.sleep(850)
                s.onActivity{a->val views=all(a.window.decorView);val scene=views.filterIsInstance<RoomMissionView>().single();assertTrue("Scene collapsed",scene.height>a.window.decorView.height*.25f)
                    val control=views.filterIsInstance<Button>().single{it.tag=="room-mission-control"};val r=Rect();assertTrue(control.getGlobalVisibleRect(r));assertEquals(control.height,r.height());assertEquals(Palette.violet,control.currentTextColor)
                    views.filterIsInstance<Button>().first{it.text.toString()=="विकल्प"}.performClick()
                    assertFalse("Options must pause all input",scene.ready)
                }
                dialog("मिशन जारी रखें");Thread.sleep(500)
                File(context.getExternalFilesDir(null),"room-hi-$orientation.png").outputStream().use{ins.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}
            }
        }}finally{Store(context).use{it.hi=false}}
    }
}
