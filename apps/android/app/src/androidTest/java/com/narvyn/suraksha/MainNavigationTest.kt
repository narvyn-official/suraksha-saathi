package com.narvyn.suraksha

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.graphics.Rect
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/** Real native scrolling/clicks; also runnable with system font_scale=2.0. No camera is opened. */
@RunWith(AndroidJUnit4::class)
class MainNavigationTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun all(view:View):List<View> = listOf(view)+(if(view is ViewGroup)(0 until view.childCount).flatMap{all(view.getChildAt(it))}else emptyList())
    private fun shot(name:String){
        val scale=context.resources.configuration.fontScale
        java.io.File(context.getExternalFilesDir(null),"nav-060-$name-$scale.png").outputStream().use{instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun reveal()=object:androidx.test.espresso.ViewAction{
        override fun getDescription()="Open the explicit content page containing the control"
        override fun getConstraints()=isAssignableFrom(View::class.java)
        override fun perform(c:androidx.test.espresso.UiController,v:View){v.requestRectangleOnScreen(Rect(0,0,v.width,v.height),true);c.loopMainThreadUntilIdle()}
    }
    private fun tag(value:String)=withTagValue(equalTo<Any>(value))
    private fun clickTag(value:String){if(value in listOf("main-lesson-next","main-lesson-previous"))onView(tag(value)).perform(click())else onView(tag(value)).perform(reveal(),click())}
    private fun back(){onView(tag("main-back")).perform(click())}
    private fun openModule(id:String){
        clickTag("main-module-picker")
        val expected=Curriculum(context).modules.map{"main-module-${it.getString("id")}"}.toSet()
        onView(isRoot()).inRoot(isDialog()).check { view,error ->
            if(error!=null)throw error
            assertEquals("The picker must retain every module",expected,all(requireNotNull(view)).mapNotNull{it.tag as? String}.filter{it in expected}.toSet())
        }
        onView(allOf(isAssignableFrom(Button::class.java),isDescendantOfA(tag("main-module-$id"))))
            .inRoot(isDialog()).perform(reveal(),click())
    }
    private fun assertPage(s:ActivityScenario<MainActivity>,page:String){
        s.onActivity { a -> assertNotNull(all(a.window.decorView).singleOrNull{it.tag=="main-page-$page"}) }
    }
    private fun assertReadableControls(a:MainActivity){
        all(a.window.decorView).filterIsInstance<Button>().filter{it.isShown && it.width>0}.forEach { b ->
            assertTrue("Small target: ${b.text}",b.width>=a.dp(48) && b.height>=a.dp(48))
            if(b.text.isBlank()) {
                assertFalse("Icon button needs an accessible name",b.contentDescription.isNullOrBlank())
                b.compoundDrawables.filterNotNull().forEach { icon ->
                    assertTrue("Clipped icon",icon.bounds.width()<=b.width-b.paddingLeft-b.paddingRight && icon.bounds.height()<=b.height-b.paddingTop-b.paddingBottom)
                }
                return@forEach
            }
            val layout=b.layout ?: return@forEach
            assertTrue("Clipped label: ${b.text}",layout.height<=b.height-b.compoundPaddingTop-b.compoundPaddingBottom+2)
            assertTrue("Ellipsized label: ${b.text}",(0 until layout.lineCount).all{layout.getEllipsisCount(it)==0})
        }
    }

    /** No routine home swipe on a normal portrait phone; large-font runs keep overflow accessible. */
    @Test fun normalFontHomeAndPrimaryActionsFitWithoutDownwardScrolling(){
        assumeTrue("Large fonts use explicit content parts",context.resources.configuration.fontScale<=1.05f)
        assumeTrue("Viewport contract is for portrait phones",context.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT)
        val oldHi=Store(context).use{it.hi}
        try { for(hi in listOf(false,true)) {
            Store(context).use{it.hi=hi}
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val views=all(activity.window.decorView)
                    val panel=views.filterIsInstance<PagedPanel>().single()
                    assertEquals("Home must be a single screen",1,panel.pageCount)
                    assertTrue("No swipe scrolling",views.none{it is ScrollView})
                    val feature=views.single{it.tag=="main-module-fire"}
                    val start=all(feature).filterIsInstance<Button>().single()
                    val controls=listOf(start)+listOf("main-module-picker","main-review","main-nav-home","main-nav-records","main-nav-help")
                        .map{tag->views.single{it.tag==tag}}
                    for(control in controls) {
                        val visible=Rect()
                        assertTrue("Control is not visible: ${control.tag}",control.getLocalVisibleRect(visible))
                        assertTrue("Control needs scrolling or is clipped: ${control.tag}",visible.width()>=control.width-1 && visible.height()>=control.height-1)
                    }
                    assertReadableControls(activity)
                }
            }
        }} finally { Store(context).use{it.hi=oldHi} }
    }

    @Test fun lessonsArePacedAndPositionSurvivesRecreationInBothLanguages(){
        val oldHi=Store(context).use{it.hi}
        val before=Store(context).use{it.attempts().map{a->a.toString()}}
        val modules=Curriculum(context).modules
        try { for(hi in listOf(false,true)) {
            Store(context).use{it.hi=hi}
            ActivityScenario.launch(MainActivity::class.java).use { s ->
                s.onActivity { a ->
                    val moduleTags=modules.map{"main-module-${it.getString("id")}"}.toSet()
                    assertEquals("Home features one module; the picker contains the catalogue",1,all(a.window.decorView).count{it.tag in moduleTags})
                    assertNotNull(all(a.window.decorView).singleOrNull{it.tag=="main-module-picker"})
                    assertTrue("Home should not repeat equipment previews",all(a.window.decorView).none{it is SceneView})
                    assertReadableControls(a)
                }
                shot(if(hi)"home-hi"else"home-en")
                openModule("fire");assertPage(s,"module");shot(if(hi)"module-hi"else"module-en")
                val lessons=modules.first{it.getString("id")=="fire"}.getJSONArray("lessons").objects()
                s.onActivity { a ->
                    val texts=all(a.window.decorView).filterIsInstance<TextView>().map{it.text.toString()}
                    assertTrue(lessons.none{it.local("body",hi) in texts})
                    assertTrue(texts.contains(if(hi)"निर्देशित अभ्यास"else"Guided practice"))
                    assertTrue(texts.contains(if(hi)"मूल्यांकन शुरू करें"else"Take an assessment"))
                }
                clickTag("main-lessons")
                fun assertLesson(index:Int){
                    s.onActivity { a ->
                        val content=all(a.window.decorView).filterIsInstance<TextView>().single{it.tag=="main-lesson-content"}
                        assertEquals(lessons[index].local("body",hi),content.text.toString())
                        assertTrue(all(a.window.decorView).filterIsInstance<TextView>().any{it.isAccessibilityHeading && it.text.toString()==lessons[index].local("title",hi)})
                        assertReadableControls(a)
                    }
                }
                assertLesson(0);shot(if(hi)"lesson-hi"else"lesson-en");clickTag("main-lesson-next");assertLesson(1)
                s.recreate();assertPage(s,"lesson");assertLesson(1)
                clickTag("main-lesson-previous");assertLesson(0)
                lessons.indices.forEach { index -> assertLesson(index);clickTag("main-lesson-next") }
                assertPage(s,"module");back();assertPage(s,"home")
            }
        }} finally { Store(context).use{it.hi=oldHi} }
        assertEquals("Reading must not create assessment evidence",before,Store(context).use{it.attempts().map{a->a.toString()}})
    }

    @Test fun readingBookmarkSurvivesFreshLaunchAndUsesOnlyCurrentLearnerAndVersion(){
        val prefs=context.getSharedPreferences("lesson-bookmarks",0)
        val saved=prefs.all.toMap()
        val oldHi=Store(context).use{it.hi}
        val key=Store(context).use{"${it.workerId}:${Curriculum(context).version}:fire"}
        try {
            Store(context).use{it.hi=false};prefs.edit().clear().commit()
            ActivityScenario.launch(MainActivity::class.java).use { s ->
                openModule("fire");clickTag("main-lessons");clickTag("main-lesson-next")
                assertEquals(1,prefs.getInt(key,-1))
            }
            fun assertPosition(index:Int){ActivityScenario.launch(MainActivity::class.java).use{s->
                openModule("fire");clickTag("main-lessons")
                s.onActivity{a->assertEquals(Curriculum(context).module("fire").getJSONArray("lessons").getJSONObject(index).local("body",false),all(a.window.decorView).filterIsInstance<TextView>().single{it.tag=="main-lesson-content"}.text.toString())}
            }}
            assertPosition(1)
            prefs.edit().remove(key).putInt("other-learner:${Curriculum(context).version}:fire",1).putInt(key.replace(Curriculum(context).version,"old-curriculum"),1).commit()
            assertPosition(0)
        } finally {prefs.edit().clear().apply();val e=prefs.edit();saved.forEach{(k,v)->if(v is Int)e.putInt(k,v)};e.commit();Store(context).use{it.hi=oldHi}}
    }

    @Test fun primaryFireAndGasEntryLaunchesTheCorrectGuidedRoomMission(){
        val oldHi=Store(context).use{it.hi}
        val captured=AtomicReference<Intent?>()
        val monitor=object:Instrumentation.ActivityMonitor(){
            override fun onStartActivity(intent:Intent):Instrumentation.ActivityResult? {
                if(intent.component?.className==RoomMissionActivity::class.java.name){
                    captured.set(Intent(intent));return Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null)
                }
                return null
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            Store(context).use{it.hi=false}
            ActivityScenario.launch(MainActivity::class.java).use { s ->
                for(id in listOf("fire","gas")) {
                    captured.set(null);openModule(id);clickTag("main-start-training")
                    val intent=captured.get() ?: error("Primary training entry did not launch a room mission")
                    assertEquals(id,intent.getStringExtra("moduleId"))
                    assertTrue(intent.getBooleanExtra("guided",false));assertTrue(intent.getBooleanExtra("camera",false))
                    assertFalse(intent.getBooleanExtra("recall",true))
                    assertPage(s,"module");back()
                }
                captured.set(null);openModule("machinery");clickTag("main-start-training");assertPage(s,"lesson")
                assertNull("Other modules must not enter a fire/gas room mission",captured.get())
            }
        } finally { instrumentation.removeMonitor(monitor);Store(context).use{it.hi=oldHi} }
    }

    @Test fun secondaryPracticeAndRecordRoutesReturnToTheirParent(){
        val oldHi=Store(context).use{it.hi}
        try { for(hi in listOf(false,true)) {
            fun t(en:String,h:String)=if(hi)h else en
            Store(context).use{it.hi=hi}
            ActivityScenario.launch(MainActivity::class.java).use { s ->
                openModule("gas");clickTag("main-practice-options");assertPage(s,"practice")
                s.onActivity { a ->
                    val texts=all(a.window.decorView).filterIsInstance<TextView>().map{it.text.toString()}
                    assertTrue(texts.contains(t("Practise the full procedure","पूरी प्रक्रिया का अभ्यास करें")))
                    assertTrue(texts.contains(t("Remember, then do · AR challenge","याद करके करें · AR अभ्यास")))
                    assertReadableControls(a)
                }
                back();assertPage(s,"module")
                onView(withText(t("My record","मेरा रिकॉर्ड"))).perform(click());assertPage(s,"records")
                onView(withText(t("Verify a QR record","QR रिकॉर्ड जाँचें"))).perform(reveal(),click());assertPage(s,"verify")
                back();assertPage(s,"records")
                onView(withText(t("Help","मदद"))).perform(click());assertPage(s,"help")
                onView(withText(t("Learn","सीखें"))).perform(click());assertPage(s,"home")
            }
        }} finally { Store(context).use{it.hi=oldHi} }
    }
}
