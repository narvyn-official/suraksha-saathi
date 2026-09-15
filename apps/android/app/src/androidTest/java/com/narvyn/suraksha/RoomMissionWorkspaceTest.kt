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
