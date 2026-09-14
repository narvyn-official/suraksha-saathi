package com.narvyn.suraksha

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Stored-record recovery tests. No positive physical camera tracking is simulated or claimed. */
@RunWith(AndroidJUnit4::class)
class ArRecoveryTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun all(v: View): List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap { all(v.getChildAt(it)) }else emptyList())
    private fun seed(guided: Boolean, hi: Boolean, last: Boolean=false): TrainingSession {
        val curriculum=Curriculum(context);val module=curriculum.module("fire")
        return Store(context).use { store ->
            store.hi=hi
            TrainingSession.create(module,store.workerId,curriculum.version,guided,"arcore").apply {
                if(last) for(i in 0 until questions.lastIndex) { answer(current.getJSONArray("options").objects().first { it.optBoolean("correct") }.getString("id"));advance() }
                answer(current.getJSONArray("options").objects().first { if(guided && !last)!it.optBoolean("correct")else it.optBoolean("correct") }.getString("id"))
                store.save(this)
            }
        }
    }
    private fun saved(id: String)=Store(context).use { it.attempt(id)!! }
    private fun launch(training: TrainingSession)=ActivityScenario.launch<ArActivity>(Intent(context,ArActivity::class.java).putExtra("attemptId",training.data.getString("id")))
    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync();Thread.sleep(150)
        File(context.getExternalFilesDir(null),name).outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) }
        if(InstrumentationRegistry.getArguments().getString("arRecoveryDemo")=="true")Thread.sleep(1200)
    }
    @Test fun guidedFeedbackSurvivesRecreationAndStaleContinueInBothLanguages() {
        assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.CAMERA))
        try { for(hi in listOf(false,true)) {
            fun t(en: String,hindi: String)=if(hi)hindi else en
            val initial=seed(true,hi);val id=initial.data.getString("id");val original=saved(id).toString()
            launch(initial).use { scenario ->
                scenario.recreate();scenario.recreate()
                scenario.onActivity { activity ->
                    val texts=all(activity.window.decorView).filterIsInstance<TextView>()
                    assertTrue(texts.any { it.text.toString()==initial.current.local("explanation",hi) })
                    assertTrue(texts.any { it.text.toString()==t("Let’s learn from this","इससे सीखें") })
                }
                assertEquals(original,saved(id).toString())
                screenshot(if(hi)"ar-guided-hindi-resume.png"else"ar-guided-resume.png")
                scenario.onActivity { activity ->
                    val old=all(activity.window.decorView).filterIsInstance<Button>().single { it.text.toString()==t("Continue","आगे बढ़ें") }
                    old.performClick();old.performClick() // same pre-transition callback cannot skip another question
                }
                scenario.recreate()
                assertEquals(1,saved(id).getInt("index"));assertEquals(1,saved(id).getJSONArray("events").length());assertFalse(saved(id).optBoolean("awaitingContinue"))
                scenario.onActivity { activity ->
                    val views=all(activity.window.decorView)
                    assertTrue(views.filterIsInstance<TextView>().any { it.text.toString().contains(t("Camera permission is off","कैमरा अनुमति बंद है")) })
                    val answers=views.filter { it.tag?.toString()?.startsWith("ar-answer-")==true }
                    assertEquals(initial.questions[1].getJSONArray("options").length(),answers.size)
                    assertTrue(answers.all { it.visibility!=View.VISIBLE })
                }
                screenshot(if(hi)"ar-hindi-camera-unavailable.png"else"ar-camera-unavailable.png")
            }
        } } finally { Store(context).use { it.hi=false } }
    }
    @Test fun assessmentRestoresOnlySavedAcknowledgementUntilExplicitContinue() {
        val initial=seed(false,false);val id=initial.data.getString("id");val original=saved(id).toString()
        launch(initial).use { scenario ->
            scenario.recreate()
            scenario.onActivity { activity ->
                val texts=all(activity.window.decorView).filterIsInstance<TextView>()
                assertTrue(texts.any { it.text.toString()=="Answer saved" })
                assertFalse(texts.any { it.text.toString()==initial.current.local("explanation",false) })
            }
            assertEquals(original,saved(id).toString());screenshot("ar-assessment-resume.png")
            onView(withText("Continue")).perform(click())
            assertEquals(1,saved(id).getInt("index"));assertEquals(1,saved(id).getJSONArray("events").length())
        }
    }
    @Test fun finalGuidedFeedbackIsNotSkippedBeforeCompletion() {
        val initial=seed(true,false,true);val id=initial.data.getString("id")
        launch(initial).use { scenario ->
            scenario.recreate();assertFalse(saved(id).optBoolean("finished"));assertEquals(8,saved(id).getJSONArray("events").length())
            onView(withText("Continue")).perform(click())
            assertTrue(saved(id).getBoolean("finished"));assertEquals(8,saved(id).getJSONArray("events").length());assertEquals("practice",saved(id).getString("kind"))
        }
    }
    @Test fun parentReloadsSavedArFeedbackBeforeSwitchingToScreen() {
        Store(context).use { it.hi=false }
        ActivityScenario.launch(MainActivity::class.java).use { parent ->
            fun tap(text: String) { parent.onActivity { activity -> all(activity.window.decorView).filterIsInstance<TextView>().first { it.text.toString()==text }.performClick() };instrumentation.waitForIdleSync() }
            tap("Start learning");tap("Guided practice");onView(withText("I’m in a safe area")).inRoot(isDialog()).perform(click())
            val latest=Store(context).use { it.attempts().first() };val id=latest.getString("id")
            val training=TrainingSession(latest.put("mode","arcore"),Curriculum(context).module("fire"))
            training.answer(training.current.getJSONArray("options").objects().first { !it.optBoolean("correct") }.getString("id"));Store(context).use { it.save(training) }
            parent.onActivity { it.startActivityForResult(Intent(it,ArActivity::class.java).putExtra("attemptId",id),20) }
            onView(withText("Continue on screen")).perform(click())
            assertEquals("hybrid",saved(id).getString("mode"));assertEquals(1,saved(id).getJSONArray("events").length());assertTrue(saved(id).getBoolean("awaitingContinue"))
            parent.onActivity { activity -> assertTrue(all(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString()==training.current.local("explanation",false) }) }
            tap("Continue");assertEquals(1,saved(id).getInt("index"));assertEquals(1,saved(id).getJSONArray("events").length())
        }
    }
}
