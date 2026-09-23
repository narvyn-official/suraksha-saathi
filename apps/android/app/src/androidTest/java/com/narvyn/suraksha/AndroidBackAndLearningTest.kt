package com.narvyn.suraksha

import android.content.Intent
import android.os.Build
import android.view.*
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

/** Synthetic UI only, emulator guarded; actual Android system Back, not onBackPressed calls. */
@RunWith(AndroidJUnit4::class)
class AndroidBackAndLearningTest {
    private val ins get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=ins.targetContext
    private fun setup(){assumeTrue(Build.HARDWARE in listOf("ranchu","goldfish")||Build.MODEL.contains("sdk_gphone"));Store(context).use{it.hi=false}}
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList())
    private fun tap(text:String){repeat(45){
        var n=ins.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.firstOrNull{it.text?.toString().equals(text,true)}
        while(n!=null&&!n.isClickable)n=n.parent
        if(n!=null){n.performAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id);if(n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)){ins.waitForIdleSync();Thread.sleep(180);return}}
        Thread.sleep(100)
    };error("Missing button $text")}
    private fun back(){ins.uiAutomation.executeShellCommand("input keyevent 4").use{FileInputStream(it.fileDescriptor).readBytes()};Thread.sleep(600);ins.waitForIdleSync()}
    private fun visible(text:String)=ins.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.any{it.isVisibleToUser}==true
    private fun field(a:RoomMissionActivity,name:String)=RoomMissionActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.get(a)
    @Test fun systemBackPopsMainHistoryAndSurvivesRecreation(){
        setup();ActivityScenario.launch(MainActivity::class.java).use{s->
            tap("Start learning");tap("More practice options")
            s.recreate();back()
            s.onActivity{assertNotNull(views(it.window.decorView).singleOrNull{v->v.tag=="main-page-module"})}
            back();s.onActivity{assertNotNull(views(it.window.decorView).singleOrNull{v->v.tag=="main-page-home"})}
            tap("My record");tap("Help");back()
            s.onActivity{assertNotNull(views(it.window.decorView).singleOrNull{v->v.tag=="main-page-records"})}
            back();s.onActivity{assertNotNull(views(it.window.decorView).singleOrNull{v->v.tag=="main-page-home"})}
        }
    }
    @Test fun speechBackClosesHelpBeforeOfferingToSaveTheMission(){
        setup();ActivityScenario.launch<RoomMissionActivity>(Intent(context,RoomMissionActivity::class.java).putExtra("camera",false)).use{s->
            tap("Start in a clear area");tap("Speak instructions")
            assertTrue(visible("Do not:"));back()
            s.onActivity{assertFalse(it.isFinishing);assertEquals("ALARM",(field(it,"mission")as RoomMission).phase)}
            back();assertTrue(visible("Save and return?"));tap("Keep practising")
            s.onActivity{assertFalse(it.isFinishing);assertTrue((field(it,"mission")as RoomMission).events.isEmpty())}
            back();tap("Save & return")
        }
    }
    @Test fun placementHelpWorksWithoutAReadySurfaceAndNeverPlacesAnAnchor(){
        setup();ActivityScenario.launch<RoomMissionActivity>(Intent(context,RoomMissionActivity::class.java).putExtra("camera",true)).use{s->
            tap("Start in a clear area")
            // A fresh emulator may still show the first camera permission prompt.
            repeat(15){
                val root=ins.uiAutomation.rootInActiveWindow
                val deny=listOf("permission_deny_button","permission_deny_and_dont_ask_again_button").flatMap{root?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/$it").orEmpty()}.firstOrNull()
                deny?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                Thread.sleep(80)
            }
            tap("Placement help");assertTrue(visible("Scan, aim, then place"));tap("Next");tap("Continue scanning")
            s.onActivity{a->val scene=field(a,"scene")as RoomMissionView
                assertFalse(scene.ready);assertTrue((field(a,"mission")as RoomMission).events.isEmpty())
                val buttons=views(a.window.decorView).filterIsInstance<Button>()
                assertFalse(buttons.any{it.text.toString()=="Scan for a surface"})
                assertTrue(buttons.single{it.tag=="room-help"}.isEnabled)
            }
        }
    }
    @Test fun fireGuideExplainsAgentChoicesWithoutAdvancingTheMission(){
        setup();ActivityScenario.launch<RoomMissionActivity>(Intent(context,RoomMissionActivity::class.java).putExtra("camera",false)).use{s->
            tap("Start in a clear area");tap("Coach");tap("Next");tap("Fire type & agent guide")
            tap("Next");tap("Any red cylinder");assertTrue(visible("Reconsider"));tap("Close")
            tap("A-rated extinguisher");assertTrue(visible("Suitable principle"));tap("Close");tap("Next")
            tap("Throw a bucket of water");assertTrue(visible("spread burning liquid"));tap("Close")
            s.onActivity{assertTrue((field(it,"mission")as RoomMission).events.isEmpty());assertEquals(0,(field(it,"coaching")as RoomCoaching).cueCount)}
            back();assertFalse(visible("Diesel spill"))
        }
    }
    @Test fun recallSpeechDoesNotExposeUnrecordedCoaching(){
        setup();ActivityScenario.launch<RoomMissionActivity>(Intent(context,RoomMissionActivity::class.java).putExtra("camera",false).putExtra("recall",true)).use{s->
            tap("Start in a clear area");tap("Speak instructions")
            assertTrue(visible("This reads the task only"));assertFalse(visible("Do not:"));assertFalse(visible("red call point"))
            s.onActivity{assertEquals(0,(field(it,"coaching")as RoomCoaching).cueCount);assertTrue((field(it,"mission")as RoomMission).events.isEmpty())}
        }
    }
    @Test fun automaticInstructionsSelectHindiAndMuteWithoutChangingEvidence(){
        setup();val prefs=context.getSharedPreferences("room-voice",0);val had=prefs.contains("automatic");val previous=prefs.getBoolean("automatic",true)
        prefs.edit().remove("automatic").commit();Store(context).use{it.hi=true}
        try{ActivityScenario.launch<RoomMissionActivity>(Intent(context,RoomMissionActivity::class.java).putExtra("camera",false)).use{s->
            tap("खाली क्षेत्र में शुरू करें")
            repeat(40){Thread.sleep(100);var requested=false;s.onActivity{requested=field(it,"lastAutoVoiceKey")!=null};if(requested)return@repeat}
            s.onActivity{assertEquals("true:fire:false:ALARM",field(it,"lastAutoVoiceKey"));assertTrue(field(it,"autoVoice")as Boolean);assertTrue((field(it,"mission")as RoomMission).events.isEmpty())}
            tap("विकल्प");tap("अपने आप निर्देश बोलना बंद करें")
            s.recreate();Thread.sleep(800)
            s.onActivity{assertFalse(field(it,"autoVoice")as Boolean);assertNull(field(it,"lastAutoVoiceKey"));assertTrue((field(it,"mission")as RoomMission).events.isEmpty())}
        }}finally{Store(context).use{it.hi=false};prefs.edit().apply{if(had)putBoolean("automatic",previous)else remove("automatic")}.commit()}
    }
    @Test fun adminSettingsAndDetailsReturnToTheirActualParent(){
        setup();ActivityScenario.launch(AdminActivity::class.java).use{s->
            s.onActivity{a->
                val type=AdminActivity::class.java
                type.getDeclaredField("session").apply{isAccessible=true}.set(a,org.json.JSONObject().put("current",org.json.JSONObject().put("role","admin").put("name","UI fixture")))
                val show=type.getDeclaredMethod("show",String::class.java,Boolean::class.javaPrimitiveType).apply{isAccessible=true}
                show.invoke(a,"more",false);show.invoke(a,"settings",true)
            }
            back();assertTrue(visible("Workspace tools"))
            tap("Curriculum")
            s.onActivity{a->AdminActivity::class.java.getDeclaredMethod("detail",String::class.java,String::class.java).apply{isAccessible=true}.invoke(a,"UI fixture detail","Synthetic content")}
            back();assertTrue(visible("Curriculum"));assertFalse(visible("UI fixture detail"))
            back();assertTrue(visible("Workspace tools"))
        }
    }
    @Test fun adminSignupBackReturnsToSignIn(){
        setup();ActivityScenario.launch(AdminActivity::class.java).use{s->
            tap("Create an account");back();assertTrue(visible("Welcome back"));s.onActivity{assertFalse(it.isFinishing)}
        }
    }
}
