package com.narvyn.suraksha

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Explicit local-backend integration. Requires emulator adb reverse tcp:5173 tcp:5173. */
@RunWith(AndroidJUnit4::class)
class AdminFlowTest {
    private val instrument get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrument.targetContext
    private fun all(v:View):List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap{all(v.getChildAt(it))}else emptyList())
    private fun waitFor(s:ActivityScenario<AdminActivity>,text:String){val deadline=System.currentTimeMillis()+25000;while(System.currentTimeMillis()<deadline){var found=false;s.onActivity{a->found=all(a.window.decorView).filterIsInstance<TextView>().any{it.text.toString()==text&&it.isShown&&it.isEnabled}};if(found)return;Thread.sleep(100)};error("Timed out waiting for $text")}
    private fun tap(s:ActivityScenario<AdminActivity>,text:String){s.onActivity{a->all(a.window.decorView).filterIsInstance<Button>().first{it.text.toString()==text}.performClick()};instrument.waitForIdleSync()}
    private fun field(s:ActivityScenario<AdminActivity>,name:String,value:String){s.onActivity{a->all(a.window.decorView).filterIsInstance<EditText>().first{it.contentDescription==name}.setText(value)}}
    private fun shot(name:String){instrument.waitForIdleSync();File(context.getExternalFilesDir(null),"admin-$name.png").outputStream().use{instrument.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun independentLoginNativeWorkspaceAndPersistentSession(){
        val prefs=context.getSharedPreferences("admin_session",0);val previous=prefs.all.toMap();val oldHi=Store(context).use{it.hi};Store(context).use{it.hi=false}
        try {
            val client=AdminClient(context);client.configure("http://localhost:5173");client.clear()
            val email="android-${UUID.randomUUID()}@example.test";val password="Test-${UUID.randomUUID()}"
            client.call("/api/auth/sign-up/email",JSONObject().put("email",email).put("name","Android QA centre").put("password",password));client.clear()
            ActivityScenario.launch(AdminActivity::class.java).use{s->
                waitFor(s,"Sign in");shot("login")
                field(s,"Email address",email);field(s,"Password",password);tap(s,"Sign in");waitFor(s,"Request centre approval");shot("approval-required")
                assertFalse(prefs.getString("cookies","")!!.contains("session_token"));assertFalse(prefs.all.values.any{it.toString().contains(password)})
                s.recreate();waitFor(s,"Request centre approval")
                tap(s,"Request centre approval");field(s,"Centre name","Android QA centre");field(s,"Site or organisation","Synthetic Android site");tap(s,"Submit application");waitFor(s,"Refresh access");shot("approval-pending")
                assertTrue(client.call("/api/admin/session").isNull("current"))
                assertEquals("pending",client.call("/api/admin/session").getJSONObject("application").getString("status"))
                s.recreate();waitFor(s,"Refresh access")
                tap(s,"Account & security");waitFor(s,"Sign out");tap(s,"Sign out");waitFor(s,"Sign in")
                try{client.call("/api/admin/session");fail("Signed-out cookie must not authorize")}catch(_:AdminClient.SignedOut){}
            }
        } finally {prefs.edit().clear().apply();val edit=prefs.edit();for((key,value)in previous)if(value is String)edit.putString(key,value);edit.commit();Store(context).use{it.hi=oldHi}}
    }
}
