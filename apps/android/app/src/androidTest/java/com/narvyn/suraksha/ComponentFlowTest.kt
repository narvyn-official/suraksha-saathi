package com.narvyn.suraksha

import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.`is`
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ComponentFlowTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun all(v: View): List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap { all(v.getChildAt(it)) }else emptyList())
    private fun state(module: String) = Store(context).use { ComponentSession.restore(module,it.componentRecords()[module]) }
    private fun shot(name: String) { File(context.getExternalFilesDir(null),name).outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) } }
    private fun waitForMarkers(s: ActivityScenario<ComponentPracticeActivity>) {
        repeat(100) {
            var ready = false
            s.onActivity { a -> val markers = all(a.window.decorView).filter { it.tag?.toString()?.startsWith("component-marker-")==true }; ready = markers.size==3 && markers.all { it.visibility==View.VISIBLE && it.width>=a.dp(48) } }
            if (ready) return
            Thread.sleep(50)
        }
        fail("Projected part markers were not ready")
    }
    private fun verifyModelAndWaitForComposition(s: ActivityScenario<ComponentPracticeActivity>) {
        var lastResult = -1
        repeat(50) {
            val done = java.util.concurrent.CountDownLatch(1)
            s.onActivity { a ->
                val surface = all(a.window.decorView).filterIsInstance<android.opengl.GLSurfaceView>().first()
                val bitmap = Bitmap.createBitmap(surface.width,surface.height,Bitmap.Config.ARGB_8888)
                android.view.PixelCopy.request(surface,bitmap,{ result ->
                    val colors = HashSet<Int>()
                    if (result==android.view.PixelCopy.SUCCESS) for(x in 0 until bitmap.width step 8) for(y in 0 until bitmap.height step 8) colors.add(bitmap.getPixel(x,y))
                    lastResult = if(result==android.view.PixelCopy.SUCCESS && colors.size>8) 0 else -1
                    bitmap.recycle(); done.countDown()
                },android.os.Handler(android.os.Looper.getMainLooper()))
            }
            assertTrue(done.await(5,java.util.concurrent.TimeUnit.SECONDS))
            if(lastResult==0) {
                val composed = java.util.concurrent.CountDownLatch(1)
                s.onActivity { a -> a.window.decorView.postOnAnimation { a.window.decorView.postOnAnimation { composed.countDown() } } }
                assertTrue(composed.await(5,java.util.concurrent.TimeUnit.SECONDS))
                return
            }
            Thread.sleep(50)
        }
        assertEquals("Equipment must render after recreation",0,lastResult)
    }
    @Test fun guidedIdentificationChangedAngleAndLocalReviewForAllModels() {
        val attempts = Store(context).use { it.attempts().map { a -> a.toString() } }
        val credentials = Store(context).use { it.credentials().map { a -> a.toString() } }
        for (module in ComponentCatalog.modules.keys) {
            Store(context).use { it.hi=false; it.saveComponent(ComponentSession.start(module,null).data) }
            ActivityScenario.launch<ComponentPracticeActivity>(Intent(context,ComponentPracticeActivity::class.java).putExtra("moduleId",module)).use { s ->
                waitForMarkers(s); verifyModelAndWaitForComposition(s); shot("parts-$module-example.png")
                while(!state(module).done) {
                    val current = state(module)
                    if (current.stage == 0) onView(withText("Try without labels")).perform(scrollTo(),click())
                    else if (current.answer == null) {
                        waitForMarkers(s)
                        if (current.index==0 && current.stage==2) {
                            s.recreate();waitForMarkers(s);verifyModelAndWaitForComposition(s);shot("parts-$module-turned.png")
                        }
                        if (InstrumentationRegistry.getArguments().getString("componentDemo")=="true") Thread.sleep(900)
                        onView(withTagValue(`is`("component-marker-${current.target.id}" as Any))).perform(scrollTo(),click())
                    } else onView(withText(if(current.stage==1)"Try the changed view" else "Continue")).perform(scrollTo(),click())
                }
                val completed = state(module)
                assertEquals(6,completed.data.getInt("visualCorrect")); assertEquals(0,completed.data.getInt("descriptionCorrect"))
                assertTrue(completed.data.getLong("dueAt")>System.currentTimeMillis())
                s.recreate();assertTrue(state(module).done);shot("parts-$module-complete.png")
            }
        }
        Store(context).use {
            assertEquals(attempts,it.attempts().map { a -> a.toString() }); assertEquals(credentials,it.credentials().map { a -> a.toString() })
            assertFalse(it.export().has("component_learning")); assertEquals(3,it.readableDatabase.version)
        }
        ActivityScenario.launch(RecallActivity::class.java).use { s ->
            s.onActivity { a -> assertTrue(all(a.window.decorView).filterIsInstance<TextView>().any { it.text.toString()=="Equipment recognition" }) }
        }
    }
    @Test fun hindiDescriptionHintAndResumeRetainAssistance() {
        val module = "ppe"
        Store(context).use { it.hi=true; it.saveComponent(ComponentSession.start(module,null).data) }
        try {
            ActivityScenario.launch<ComponentPracticeActivity>(Intent(context,ComponentPracticeActivity::class.java).putExtra("moduleId",module)).use { s ->
                onView(withText("लिखित विवरण उपयोग करें")).perform(scrollTo(),click())
                onView(withText("बिना नाम के कोशिश करें")).perform(scrollTo(),click())
                onView(withText("मुझे निश्चित नहीं है")).perform(scrollTo(),click())
                s.recreate(); assertEquals("_unsure",state(module).answer)
                onView(withText("मदद के साथ कोशिश करें")).perform(scrollTo(),click())
                assertTrue(state(module).helped)
                onView(withTagValue(`is`("component-description-helmet" as Any))).perform(scrollTo(),click())
                assertEquals(0,state(module).data.getInt("descriptionCorrect"))
                onView(withText("बदले क्रम में कोशिश करें")).perform(scrollTo(),click())
                s.recreate();assertEquals(2,state(module).stage);assertTrue(state(module).descriptions)
                shot("parts-hindi-descriptions.png")
            }
            ActivityScenario.launch<ComponentPracticeActivity>(Intent(context,ComponentPracticeActivity::class.java).putExtra("moduleId",module)).use {
                assertEquals(2,state(module).stage); assertEquals(2,state(module).data.getJSONArray("events").length())
            }
        } finally { Store(context).use { it.hi=false } }
    }
    @Test fun databaseUpgradePreservesPriorEvidence() {
        val name = "component-upgrade-test.db"
        context.deleteDatabase(name)
        try {
            android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name),null).use { db ->
                db.execSQL("CREATE TABLE attempts(id TEXT PRIMARY KEY,payload TEXT NOT NULL,updated_at INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE credentials(id TEXT PRIMARY KEY,payload TEXT NOT NULL)")
                db.execSQL("CREATE TABLE recalls(id TEXT PRIMARY KEY,payload TEXT NOT NULL)")
                db.execSQL("INSERT INTO attempts VALUES('old','{\"id\":\"old\"}',1)")
                db.execSQL("INSERT INTO credentials VALUES('old','{\"id\":\"old\"}')")
                db.execSQL("INSERT INTO recalls VALUES('old','{\"key\":\"old\"}')")
                db.version = 2
            }
            Store(context,name).use { store ->
                assertEquals("old",store.attempt("old")!!.getString("id")); assertEquals(1,store.credentials().size);assertEquals(1,store.recalls().size)
                store.saveComponent(ComponentSession.start("fire",null).data)
                assertEquals(1,store.componentRecords().size); assertEquals(3,store.readableDatabase.version)
            }
        } finally { context.deleteDatabase(name) }
    }
}
