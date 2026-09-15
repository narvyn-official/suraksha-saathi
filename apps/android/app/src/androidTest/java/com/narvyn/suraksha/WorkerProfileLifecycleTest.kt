package com.narvyn.suraksha

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real lifecycle transitions on throwaway local profiles. No camera grant or tracking is required. */
@RunWith(AndroidJUnit4::class)
class WorkerProfileLifecycleTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val children=listOf(RecallActivity::class.java,ComponentPracticeActivity::class.java,ArActivity::class.java)
    private fun views(view:View):List<View> = listOf(view)+(if(view is ViewGroup)(0 until view.childCount).flatMap { views(view.getChildAt(it)) }else emptyList())
    private fun button(activity:Activity,text:String)=views(activity.window.decorView).filterIsInstance<Button>().first { it.text.toString()==text }
    private fun await(latch:CountDownLatch,message:String) { assertTrue(message,latch.await(10,TimeUnit.SECONDS));instrumentation.waitForIdleSync() }
    private fun observing(callback:ActivityLifecycleCallback,block:()->Unit) {
        val monitor=ActivityLifecycleMonitorRegistry.getInstance()
        instrumentation.runOnMainSync { monitor.addLifecycleCallback(callback) }
        try { block() } finally { instrumentation.runOnMainSync { monitor.removeLifecycleCallback(callback) } }
    }
    private fun workers(block:(Store,Store)->Unit) {
        Store(context).use { original ->
            val originalId=original.workerId;val originalHi=original.hi
            val created=mutableListOf<String>()
            try {
                original.hi=false
                val firstId=original.createProfile("Lifecycle test A","Mining").also { created.add(it) }
                val secondId=original.createProfile("Lifecycle test B","Mica").also { created.add(it) }
                original.switchProfile(firstId)
                Store(context).use { first ->
                    original.switchProfile(secondId)
                    Store(context).use { second -> original.switchProfile(firstId);block(first,second) }
                }
            } finally {
                // Scenarios close before returning here. Only this test's fresh UUID owners are removed.
                original.switchProfile(originalId);original.hi=originalHi
                val db=original.writableDatabase;db.beginTransaction()
                try {
                    for(id in created) {
                        for(table in listOf("attempts","credentials","recalls","component_learning"))db.delete(table,"worker_id=?",arrayOf(id))
                        db.delete("worker_profiles","id=?",arrayOf(id))
                    }
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            }
        }
    }
    private fun records(store:Store):List<String> = listOf("attempts","credentials","recalls","component_learning").flatMap { table ->
        val columns=if(table=="attempts")"id,payload,updated_at"else"id,payload"
        store.readableDatabase.rawQuery("SELECT $columns FROM $table WHERE worker_id=? ORDER BY id",arrayOf(store.workerId)).use { cursor ->
            buildList { while(cursor.moveToNext())add(table+":"+(0 until cursor.columnCount).joinToString("|") { cursor.getString(it) }) }
        }
    }
    private fun intent(type:Class<out Activity>,owner:Store):Intent {
        val intent=Intent(context,type)
        val curriculum=Curriculum(context)
        if(type==ArActivity::class.java) {
            // Saved acknowledgement avoids opening or asking to open a camera.
            val session=TrainingSession.create(curriculum.module("fire"),owner.workerId,curriculum.version,true,"arcore")
            session.answer(session.current.getJSONArray("options").objects().first { it.optBoolean("correct") }.getString("id"))
            owner.save(session);intent.putExtra("attemptId",session.data.getString("id"))
        } else if(type==RecallActivity::class.java) {
            val session=TrainingSession.create(curriculum.module("fire"),owner.workerId,curriculum.version,false,"screen")
            // An actual critical miss produces a finished assessment and an immediately due review.
            while(!session.finished) {
                session.answer(session.current.getJSONArray("options").objects().first { it.optBoolean("correct") != session.current.optBoolean("critical") }.getString("id"))
                if(!session.finished)session.advance()
            }
            owner.save(session)
        } else intent.putExtra("moduleId","fire")
        return intent
    }
    private fun prepare(activity:Activity):Button = when(activity) {
        is RecallActivity -> { button(activity,"Recall this decision").performClick();button(activity,"I’m not sure — help me learn") }
        is ComponentPracticeActivity -> button(activity,"Try without labels")
        else -> button(activity,"Continue")
    }

    @Test fun pausedChildrenFinishWhenAnotherWorkerBecomesActiveWithoutChangingRecords() {
        for(type in children)workers { first,second ->
            ActivityScenario.launch<Activity>(intent(type,first)).use { scenario ->
                val child=AtomicReference<Activity>();scenario.onActivity { child.set(it);prepare(it) }
                val picker=AtomicReference<Activity>();val stopped=CountDownLatch(1);val pickerReady=CountDownLatch(1);val destroyed=CountDownLatch(1)
                val callback=ActivityLifecycleCallback { activity,stage ->
                    if(activity===child.get() && stage==Stage.STOPPED)stopped.countDown()
                    if(activity===child.get() && stage==Stage.DESTROYED)destroyed.countDown()
                    if(activity is WorkerProfilesActivity && stage==Stage.RESUMED) { picker.set(activity);pickerReady.countDown() }
                }
                observing(callback) {
                    // Use a real covering activity: Scenario.moveToState(RESUMED) would expect the child to stay resumed.
                    scenario.onActivity { it.startActivity(Intent(it,WorkerProfilesActivity::class.java)) }
                    await(stopped,"${type.simpleName} did not pause/stop");await(pickerReady,"Picker did not resume")
                    val beforeFirst=records(first);val beforeSecond=records(second)
                    instrumentation.runOnMainSync { first.switchProfile(second.workerId);picker.get().finish() }
                    await(destroyed,"${type.simpleName} remained open for the previous worker")
                    assertEquals(beforeFirst,records(first));assertEquals(beforeSecond,records(second))
                    assertTrue(second.isCurrentProfile())
                }
            }
        }
    }

    @Test fun recreatedChildrenRejectPreviousWorkersSavedUiAndLeaveBothJournalsUntouched() {
        for(type in children)workers { first,second ->
            ActivityScenario.launch<Activity>(intent(type,first)).use { scenario ->
                val original=AtomicReference<Activity>()
                scenario.onActivity { activity ->
                    original.set(activity);val answer=prepare(activity)
                    if(activity is RecallActivity) { answer.performClick();assertNotNull(button(activity,"Back to reviews")) }
                }
                val beforeFirst=records(first);val beforeSecond=records(second)
                val replacement=AtomicReference<Activity>();val destroyed=CountDownLatch(2)
                val callback=ActivityLifecycleCallback { activity,stage ->
                    if(activity.javaClass==type) {
                        if(activity!==original.get())replacement.set(activity)
                        if(stage==Stage.DESTROYED) {
                            // Switch between saving A's state and constructing the replacement Store.
                            // This avoids a queued renderer callback closing A before recreation starts.
                            if(activity===original.get())first.switchProfile(second.workerId)
                            destroyed.countDown()
                        }
                    }
                }
                observing(callback) {
                    instrumentation.runOnMainSync { original.get().recreate() }
                    await(destroyed,"${type.simpleName} restored another worker's saved UI instead of closing")
                    assertNotNull("A replacement activity must exercise onCreate(savedState)",replacement.get())
                    assertTrue(replacement.get().isFinishing)
                    assertEquals(beforeFirst,records(first));assertEquals(beforeSecond,records(second))
                    assertTrue(second.isCurrentProfile())
                }
            }
        }
    }

    @Test fun oldAnswerCallbacksCannotWriteAfterAProfileSwitchEvenBeforePause() {
        for(type in children)workers { first,second ->
            ActivityScenario.launch<Activity>(intent(type,first)).use { scenario ->
                val child=AtomicReference<Activity>();val oldButton=AtomicReference<Button>()
                scenario.onActivity { child.set(it);oldButton.set(prepare(it)) }
                val beforeFirst=records(first);val beforeSecond=records(second);val destroyed=CountDownLatch(1)
                observing(ActivityLifecycleCallback { activity,stage -> if(activity===child.get() && stage==Stage.DESTROYED)destroyed.countDown() }) {
                    instrumentation.runOnMainSync { first.switchProfile(second.workerId);oldButton.get().performClick() }
                    await(destroyed,"${type.simpleName} accepted an old worker's callback")
                    assertEquals(beforeFirst,records(first));assertEquals(beforeSecond,records(second))
                }
            }
        }
    }
}
