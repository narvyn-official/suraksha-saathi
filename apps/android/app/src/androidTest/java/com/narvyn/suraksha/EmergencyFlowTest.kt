package com.narvyn.suraksha

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.action.ViewActions.scrollTo
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.equalTo
import android.widget.Button
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class EmergencyFlowTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun texts(v: View): List<TextView> = (if(v is TextView)listOf(v)else emptyList())+(if(v is ViewGroup)(0 until v.childCount).flatMap { texts(v.getChildAt(it)) }else emptyList())
    private fun tap(s: ActivityScenario<MainActivity>, text: String) {
        s.onActivity { a -> texts(a.window.decorView).first { it.text.toString()==text }.performClick() }
        instrumentation.waitForIdleSync()
    }
    private fun shot(name: String) {
        instrumentation.waitForIdleSync(); Thread.sleep(150)
        File(context.getExternalFilesDir(null),name).outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    private fun open(s: ActivityScenario<MainActivity>, hi: Boolean, practice: Boolean) {
        fun t(en: String, hindi: String) = if(hi)hindi else en
        onView(withTagValue(equalTo<Any>("main-module-picker"))).perform(scrollTo(),click())
        onView(allOf(isAssignableFrom(Button::class.java),isDescendantOfA(withTagValue(equalTo<Any>("main-module-emergency")))))
            .inRoot(isDialog()).perform(scrollTo(),click())
        instrumentation.waitForIdleSync()
        if(InstrumentationRegistry.getArguments().getString("emergencyDemo")=="true")Thread.sleep(700)
        shot(if(hi)"emergency-hindi-lesson.png" else "emergency-lesson.png")
        if(practice)tap(s,t("Guided practice","निर्देशित अभ्यास"))
        else {
            tap(s,t("Take an assessment","मूल्यांकन शुरू करें"))
            onView(withText(t("On-screen decisions","स्क्रीन पर निर्णय"))).inRoot(isDialog()).perform(click())
        }
        onView(withText(t("I’m in a safe area","मैं सुरक्षित जगह पर हूँ"))).inRoot(isDialog()).perform(click())
    }
    @Test fun emergencyPracticeAndAssessmentCompleteInBothLanguages() {
        val questions = Curriculum(context).module("emergency").getJSONArray("questions").objects()
        try {
            for(hi in listOf(false,true))for(practice in listOf(true,false)) {
                Store(context).use { it.hi=hi }
                ActivityScenario.launch(MainActivity::class.java).use { s ->
                    open(s,hi,practice)
                    questions.forEachIndexed { i,q ->
                        if(i==3)s.recreate()
                        val option=q.getJSONArray("options").objects().first { if(practice&&i==0)!it.optBoolean("correct") else it.optBoolean("correct") }
                        if(InstrumentationRegistry.getArguments().getString("emergencyDemo")=="true")Thread.sleep(650)
                        tap(s,option.local("text",hi));tap(s,if(hi)"आगे बढ़ें" else "Continue")
                    }
                    Store(context).use { store ->
                        val a=store.attempts().first()
                        assertEquals("emergency",a.getString("moduleId")); assertEquals(if(practice)"practice" else "assessment",a.getString("kind"))
                        assertEquals(8,a.getJSONArray("events").length());assertTrue(a.getBoolean("finished"));assertEquals(!practice,a.getJSONObject("result").getBoolean("passed"))
                    }
                    shot(if(hi)"emergency-hindi-result.png" else "emergency-result.png")
                }
            }
        } finally { Store(context).use { it.hi=false } }
    }
    @Test fun unsafeEmergencyEntryStopsAssessmentAndPersists() {
        Store(context).use { it.hi=false }
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            open(s,false,false)
            val q=Curriculum(context).module("emergency").getJSONArray("questions").getJSONObject(0)
            tap(s,q.getJSONArray("options").objects().first { !it.optBoolean("correct") }.local("text",false))
            s.recreate()
            Store(context).use {
                val a=it.attempts().first();assertEquals("emergency",a.getString("moduleId"));assertTrue(a.getBoolean("finished"))
                assertEquals(1,a.getJSONArray("events").length());assertFalse(a.getJSONObject("result").getBoolean("passed"));assertEquals(q.getString("id"),a.getJSONObject("result").getJSONArray("criticalFailures").getString(0))
            }
        }
    }
    @Test fun trainerSignedEmergencyCredentialVerifiesOnAndroid() {
        val raw=instrumentation.context.assets.open("demo-emergency-credential.txt").bufferedReader().use { it.readText() }
        val record=CredentialVerifier.verify(context,raw)
        assertEquals("emergency",record.getString("moduleId"));assertEquals("pilot-simulation",record.getString("kind"));assertEquals("not-assessed",record.getString("practical"))
    }
}
