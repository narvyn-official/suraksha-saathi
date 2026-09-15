package com.narvyn.suraksha

import android.app.Activity
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.InvocationTargetException

@RunWith(AndroidJUnit4::class)
class PendingExportTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun set(a:MainActivity,value:ByteArray?)=MainActivity::class.java.getDeclaredMethod("setExportBytes",ByteArray::class.java).apply { isAccessible=true }.invoke(a,value)
    private fun get(a:MainActivity)=MainActivity::class.java.getDeclaredMethod("getExportBytes").apply { isAccessible=true }.invoke(a) as ByteArray?
    @Test fun activityInstancesKeepSeparateExportsAndRecreationPreservesTheRequest() {
        ActivityScenario.launch(MainActivity::class.java).use { first ->
            lateinit var a:MainActivity
            first.onActivity { a=it;set(it,"first request".toByteArray()) }
            first.recreate();first.onActivity { a=it;assertArrayEquals("first request".toByteArray(),get(it)) }
            ActivityScenario.launch(MainActivity::class.java).use { second ->
                second.onActivity { b ->
                    set(b,"second request".toByteArray())
                    assertArrayEquals("first request".toByteArray(),get(a))
                    assertArrayEquals("second request".toByteArray(),get(b))
                    set(b,null)
                    assertArrayEquals("first request".toByteArray(),get(a))
                    set(a,null)
                }
            }
        }
    }
    @Test fun changedProfileRejectsThePendingBytesBeforeDestinationAccess() {
        val original=Store(context).use { it.workerId }
        val other=Store(context).use { it.createProfile("Export isolation test","Mining") }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { a ->
                    set(a,"original learner only".toByteArray())
                    Store(context).use { it.switchProfile(other) }
                    try { get(a);fail("Foreign active profile could retrieve the export") }
                    catch(e:InvocationTargetException) { assertTrue(e.cause is IllegalStateException) }
                    // An invalid destination must never be opened after the profile guard rejects the result.
                    MainActivity::class.java.getDeclaredMethod("onActivityResult",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Intent::class.java).apply { isAccessible=true }
                        .invoke(a,10,Activity.RESULT_OK,Intent().setData(android.net.Uri.parse("content://invalid-export-destination/not-opened")))
                    val id=MainActivity::class.java.getDeclaredField("pendingExportId").apply { isAccessible=true }.get(a)
                    assertNull(id)
                }
            }
        } finally { Store(context).use { it.switchProfile(original);it.writableDatabase.delete("worker_profiles","id=?",arrayOf(other)) } }
    }
}
