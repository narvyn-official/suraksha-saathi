package com.narvyn.suraksha
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.graphics.Bitmap
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
@RunWith(AndroidJUnit4::class)
class AppFlowTest {
 private fun texts(v:View):List<TextView> = (if(v is TextView)listOf(v)else emptyList())+(if(v is ViewGroup)(0 until v.childCount).flatMap{texts(v.getChildAt(it))}else emptyList())
 private fun tap(s:ActivityScenario<MainActivity>,text:String){s.onActivity{a->val view=texts(a.window.decorView).firstOrNull{it.text.toString()==text}?:error("Missing control: $text");view.performClick()};InstrumentationRegistry.getInstrumentation().waitForIdleSync()}
 private fun shot(name:String){val i=InstrumentationRegistry.getInstrumentation();val b=i.uiAutomation.takeScreenshot();File(i.targetContext.getExternalFilesDir(null),name).outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}
 @Test fun moduleAssessmentPersistsAcrossRecreation(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  Store(context).use{it.hi=false}
  ActivityScenario.launch(MainActivity::class.java).use{s->
   s.onActivity{it.setShowWhenLocked(true);it.setTurnScreenOn(true);it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);it.getSystemService(android.app.KeyguardManager::class.java).requestDismissKeyguard(it,null)}
   InstrumentationRegistry.getInstrumentation().waitForIdleSync()
   onView(withText("Learn to stay safe.")).check(matches(isDisplayed()));shot("home.png")
   tap(s,"Start learning");tap(s,"Take an assessment");onView(withText("On-screen decisions")).inRoot(isDialog()).perform(click());onView(withText("I’m in a safe area")).inRoot(isDialog()).perform(click())
   val curriculum=Curriculum(context);val questions=curriculum.module("fire").getJSONArray("questions").objects()
   questions.forEachIndexed{i,q->
    if(i==2)s.recreate()
    if(InstrumentationRegistry.getArguments().getString("demo")=="true")Thread.sleep(1400)
    tap(s,q.getJSONArray("options").objects().first{it.optBoolean("correct")}.local("text",false))
    if(i<questions.lastIndex)tap(s,"Continue") else tap(s,"Continue")
   }
   shot("result.png");Store(context).use{val result=it.attempts().first();assertEquals("assessment",result.getString("kind"));assertTrue(result.getBoolean("finished"));assertTrue(result.getJSONObject("result").getBoolean("passed"));assertEquals(8,result.getJSONArray("events").length())}
  }
 }
 @Test fun offlineCredentialSignatureRejectsTampering(){
  val i=InstrumentationRegistry.getInstrumentation();val raw=i.context.assets.open("demo-credential.txt").bufferedReader().use{it.readText()}
  val verified=CredentialVerifier.verify(i.targetContext,raw);assertEquals("pilot-simulation",verified.getString("kind"))
  val p=raw.removePrefix("SURAKSHA:CREDENTIAL:").split('.').toMutableList();val payload=org.json.JSONObject(String(android.util.Base64.decode(p[1],android.util.Base64.URL_SAFE)));payload.put("score",12);p[1]=android.util.Base64.encodeToString(payload.toString().toByteArray(),android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
  try{CredentialVerifier.verify(i.targetContext,"SURAKSHA:CREDENTIAL:"+p.joinToString("."));fail("Tampered credential accepted")}catch(expected:IllegalArgumentException){}
 }
 @Test fun hindiHomeRenders(){val c=InstrumentationRegistry.getInstrumentation().targetContext;Store(c).use{it.hi=true};ActivityScenario.launch(MainActivity::class.java).use{onView(withText("सुरक्षित रहना सीखें।")).check(matches(isDisplayed()));shot("hindi-home.png")};Store(c).use{it.hi=false}}
}
