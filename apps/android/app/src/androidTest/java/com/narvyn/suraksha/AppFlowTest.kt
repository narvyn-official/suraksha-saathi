package com.narvyn.suraksha
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.action.ViewActions.scrollTo
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.equalTo
import android.widget.Button
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
 private fun openModule(id:String){
  onView(withTagValue(equalTo<Any>("main-module-picker"))).perform(scrollTo(),click())
  onView(allOf(isAssignableFrom(Button::class.java),isDescendantOfA(withTagValue(equalTo<Any>("main-module-$id")))))
   .inRoot(isDialog()).perform(scrollTo(),click())
 }
 private fun shot(name:String){val i=InstrumentationRegistry.getInstrumentation();val b=i.uiAutomation.takeScreenshot();File(i.targetContext.getExternalFilesDir(null),name).outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}
 @Test fun moduleAssessmentPersistsAcrossRecreation(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  Store(context).use{it.hi=false}
  ActivityScenario.launch(MainActivity::class.java).use{s->
   s.onActivity{it.setShowWhenLocked(true);it.setTurnScreenOn(true);it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);it.getSystemService(android.app.KeyguardManager::class.java).requestDismissKeyguard(it,null)}
   InstrumentationRegistry.getInstrumentation().waitForIdleSync()
   onView(withText("Learn to stay safe.")).check(matches(isDisplayed()));shot("home.png")
   openModule("fire");tap(s,"Take an assessment");onView(withText("On-screen decisions")).inRoot(isDialog()).perform(click());onView(withText("I’m in a safe area")).inRoot(isDialog()).perform(click())
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

 @Test fun machineryCriticalDecisionStopsAssessment(){
  val c=InstrumentationRegistry.getInstrumentation().targetContext;Store(c).use{it.hi=false}
  ActivityScenario.launch(MainActivity::class.java).use{s->
   openModule("machinery")
   tap(s,"Take an assessment");onView(withText("On-screen decisions")).inRoot(isDialog()).perform(click());onView(withText("I’m in a safe area")).inRoot(isDialog()).perform(click())
   val q=Curriculum(c).module("machinery").getJSONArray("questions").getJSONObject(0)
   tap(s,q.getJSONArray("options").objects().first{!it.optBoolean("correct")}.local("text",false))
   Store(c).use{val a=it.attempts().first();assertEquals("machinery",a.getString("moduleId"));assertTrue(a.getBoolean("finished"));assertFalse(a.getJSONObject("result").getBoolean("passed"));assertEquals(1,a.getJSONArray("events").length())}
   shot("machinery-critical-result.png")
  }
 }
 @Test fun originalEquipmentModelsRenderAndRotate(){
  val c=InstrumentationRegistry.getInstrumentation().targetContext;Store(c).use{it.hi=false}
  for(module in listOf("fire","gas","machinery","ppe","emergency")){
   ActivityScenario.launch<EquipmentActivity>(android.content.Intent(c,EquipmentActivity::class.java).putExtra("moduleId",module)).use{s->
    onView(withText("Rotate right")).perform(click());onView(withText("Change zoom")).perform(click())
    val done=java.util.concurrent.CountDownLatch(1);var result=-1
    s.onActivity{a->val root=a.window.decorView;fun find(v:View):android.opengl.GLSurfaceView?{if(v is android.opengl.GLSurfaceView)return v;if(v is ViewGroup)for(i in 0 until v.childCount){find(v.getChildAt(i))?.let{return it}};return null};val surface=find(root)!!;val bitmap=Bitmap.createBitmap(surface.width,surface.height,Bitmap.Config.ARGB_8888);android.view.PixelCopy.request(surface,bitmap,{code->result=code;if(code==android.view.PixelCopy.SUCCESS){val colors=HashSet<Int>();for(x in 0 until bitmap.width step 10)for(y in 0 until bitmap.height step 10)colors.add(bitmap.getPixel(x,y));if(colors.size<8)result=-2;File(c.getExternalFilesDir(null),"model-$module.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}};done.countDown()},android.os.Handler(android.os.Looper.getMainLooper()))}
    assertTrue(done.await(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals("A non-blank 3D model must render",android.view.PixelCopy.SUCCESS,result);shot("equipment-$module.png")
   }
  }
 }

 @Test fun ppeLearningPracticeAndAssessmentInBothLanguages(){
  val c=InstrumentationRegistry.getInstrumentation().targetContext
  val module=Curriculum(c).module("ppe");val questions=module.getJSONArray("questions").objects()
  try{for(hi in listOf(false,true))for(guided in listOf(true,false)){
   fun t(en:String,hindi:String)=if(hi)hindi else en
   Store(c).use{it.hi=hi}
   ActivityScenario.launch(MainActivity::class.java).use{s->
    openModule("ppe")
    shot(if(hi)"ppe-hindi-lesson.png" else "ppe-lesson.png")
    if(guided){tap(s,t("Guided practice","निर्देशित अभ्यास"))}else{tap(s,t("Take an assessment","मूल्यांकन शुरू करें"));onView(withText(t("On-screen decisions","स्क्रीन पर निर्णय"))).inRoot(isDialog()).perform(click())}
    onView(withText(t("I’m in a safe area","मैं सुरक्षित जगह पर हूँ"))).inRoot(isDialog()).perform(click())
    questions.forEachIndexed{i,q->
     if(i==3)s.recreate()
     val option=q.getJSONArray("options").objects().first{if(guided&&i==0)!it.optBoolean("correct") else it.optBoolean("correct")}
     tap(s,option.local("text",hi));tap(s,t("Continue","आगे बढ़ें"))
    }
    Store(c).use{val a=it.attempts().first();assertEquals("ppe",a.getString("moduleId"));assertEquals(if(guided)"practice" else "assessment",a.getString("kind"));assertEquals(8,a.getJSONArray("events").length());assertTrue(a.getBoolean("finished"));assertEquals(!guided,a.getJSONObject("result").getBoolean("passed"))}
    shot(if(hi)"ppe-hindi-result.png" else "ppe-result.png")
   }
  }}finally{Store(c).use{it.hi=false}}
 }

 @Test fun spacedRecallSavesLocallyWithoutChangingAssessment(){
  val c=InstrumentationRegistry.getInstrumentation().targetContext;val curriculum=Curriculum(c);val module=curriculum.module("ppe");val q=module.getJSONArray("questions").getJSONObject(0)
  val assessment=Store(c).use{it.hi=false;TrainingSession.create(module,it.workerId,curriculum.version,false,"screen").apply{answer(q.getJSONArray("options").objects().first{!it.optBoolean("correct")}.getString("id"));it.save(this)}}
  val original=assessment.data.toString()
  ActivityScenario.launch(RecallActivity::class.java).use{s->
   s.onActivity{a->val title=texts(a.window.decorView).first{it.text.toString()==q.local("prompt",false)};texts(title.parent as ViewGroup).first{it.text.toString()=="Recall this decision"}.performClick()}
   s.onActivity{a->texts(a.window.decorView).first{it.text.toString()==q.getJSONArray("options").objects().first{it.optBoolean("correct")}.local("text",false)}.performClick()}
   s.recreate();onView(withText("You recalled a safe response")).check(matches(isDisplayed()));shot("spaced-recall.png")
   Store(c).use{assertEquals(original,it.attempt(assessment.data.getString("id")).toString());val record=it.recalls().values.first{it.getString("source")==assessment.data.getString("id")};assertEquals(1,record.getInt("streak"));assertTrue(record.getLong("dueAt")>System.currentTimeMillis());assertFalse(it.export().has("recalls"))}
  }
 }
}
