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
class RoomRecallWorkspaceTest {
    private val ins get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=ins.targetContext
    private fun all(v:View):List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap{all(v.getChildAt(it))}else emptyList())
    private fun dialog(text:String){var clicked=false;repeat(50){if(!clicked){val n=ins.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.firstOrNull();if(n!=null){clicked=n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)}else Thread.sleep(80)}};assertTrue(text,clicked);Thread.sleep(250)}
    @Test fun hindiWorkspaceKeepsSceneAndControlsInPortraitAndLandscape(){
        Store(context).use{it.hi=true}
        try {ActivityScenario.launch<RoomMissionActivity>(Intent(context,RoomMissionActivity::class.java).putExtra("camera",false).putExtra("recall",true)).use{s->
            dialog("खाली क्षेत्र में शुरू करें");Thread.sleep(700)
            for(orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)){
                s.onActivity{it.requestedOrientation=orientation};Thread.sleep(850)
                s.onActivity{a->val views=all(a.window.decorView);val scene=views.filterIsInstance<RoomMissionView>().single();assertTrue("Scene collapsed",scene.height>a.window.decorView.height*.25f)
                    val control=views.filterIsInstance<Button>().single{it.tag=="room-mission-control"};val r=Rect();assertTrue(control.getGlobalVisibleRect(r));assertEquals(control.height,r.height());assertEquals(Palette.violet,control.currentTextColor)
                    views.filterIsInstance<Button>().first{it.text.toString()=="विकल्प"}.performClick()
                    assertFalse("Options must pause all input",scene.ready)
                }
                dialog("मिशन जारी रखें");Thread.sleep(500)
                File(context.getExternalFilesDir(null),"room-recall-hi-$orientation.png").outputStream().use{ins.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}
            }
        }}finally{Store(context).use{it.hi=false}}
    }
}
