package com.narvyn.suraksha

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WorkerProfileStoreTest {
    private fun isolated(block:(Context,String)->Unit) {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val key="worker-profile-test-${UUID.randomUUID()}"
        val context=object:ContextWrapper(base) {
            override fun getSharedPreferences(name:String,mode:Int)=base.getSharedPreferences("$key-$name",mode)
        }
        val database="$key.db"
        try { block(context,database) } finally {
            context.deleteDatabase(database)
            base.deleteSharedPreferences("$key-preferences")
        }
    }
    private fun rejected(block:()->Unit) {
        try { block();fail("Cross-profile or invalid write was accepted") } catch(_:IllegalArgumentException) {}
    }
    private fun session(context:Context,worker:String)=Curriculum(context).let { curriculum ->
        TrainingSession.create(curriculum.module("fire"),worker,curriculum.version,false,"screen")
    }
    @Test fun versionThreeMigrationPreservesOriginalPayloadsAndProfile()=isolated { context,database ->
        val worker=UUID.randomUUID().toString()
        context.getSharedPreferences("preferences",Context.MODE_PRIVATE).edit().putString("workerId",worker).putString("name","Asha").putString("sector","Mica").commit()
        val attempt=JSONObject().put("id","legacy-attempt").put("workerId",worker).put("finished",true).put("marker","preserve events").toString()
        val credential=JSONObject().put("id","legacy-credential").put("workerRef",worker).put("token","unchanged.signed.token").toString()
        val recall=JSONObject().put("key","legacy-key").put("streak",3).toString()
        val component=JSONObject().put("module","fire").put("stage",2).toString()
        context.getDatabasePath(database).parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(database),null).use { db ->
            db.execSQL("CREATE TABLE attempts(id TEXT PRIMARY KEY,payload TEXT NOT NULL,updated_at INTEGER NOT NULL)")
            for(table in listOf("credentials","recalls","component_learning"))db.execSQL("CREATE TABLE $table(id TEXT PRIMARY KEY,payload TEXT NOT NULL)")
            db.execSQL("INSERT INTO attempts VALUES(?,?,?)",arrayOf<Any>("legacy-attempt",attempt,12L))
            db.execSQL("INSERT INTO credentials VALUES(?,?)",arrayOf("legacy-credential",credential))
            db.execSQL("INSERT INTO recalls VALUES(?,?)",arrayOf("legacy-key",recall))
            db.execSQL("INSERT INTO component_learning VALUES(?,?)",arrayOf("fire",component))
            db.version=3
        }
        Store(context,database).use { original ->
            assertEquals(4,original.readableDatabase.version)
            assertEquals(worker,original.workerId);assertEquals("Asha",original.name);assertEquals("Mica",original.sector)
            assertEquals(1,original.profiles().size)
            assertEquals(attempt,original.attempt("legacy-attempt").toString())
            assertEquals(credential,original.credentials().single().toString())
            assertEquals(recall,original.recalls()["legacy-key"].toString())
            assertEquals(component,original.componentRecords()["fire"].toString())
            val second=original.createProfile("Biren","Mining");original.switchProfile(second)
            Store(context,database).use { other ->
                assertEquals(second,other.workerId);assertTrue(other.attempts().isEmpty());assertTrue(other.credentials().isEmpty())
                assertTrue(other.recalls().isEmpty());assertTrue(other.componentRecords().isEmpty());assertNull(other.attempt("legacy-attempt"))
                other.saveRecall(JSONObject().put("key","legacy-key").put("streak",0))
                other.saveComponent(JSONObject().put("module","fire").put("stage",0))
                assertEquals(3,original.recalls()["legacy-key"]!!.getInt("streak"))
                assertEquals(2,original.componentRecords()["fire"]!!.getInt("stage"))
                assertEquals(0,other.recalls()["legacy-key"]!!.getInt("streak"))
                other.switchProfile(worker)
            }
        }
        Store(context,database).use { reopened ->
            assertEquals(worker,reopened.workerId);assertEquals(attempt,reopened.attempt("legacy-attempt").toString())
            assertEquals(credential,reopened.credentials().single().toString())
        }
    }
    @Test fun switchingKeepsOldStoresBoundAndPreventsCrossWorkerReadsAndWrites()=isolated { context,database ->
        Store(context,database).use { first ->
            first.name="First learner";first.sector="Steel"
            val firstAttempt=session(context,first.workerId).apply { data.put("finished",true) };first.save(firstAttempt)
            val firstCredential=JSONObject().put("id","certificate-a").put("workerRef",first.workerId).put("token","a.b.c")
            assertTrue(first.saveCredential(firstCredential))
            val secondId=first.createProfile("Second learner","Mining")
            assertTrue(first.isCurrentProfile());first.switchProfile(secondId);assertFalse(first.isCurrentProfile())
            Store(context,database).use { second ->
                assertEquals(secondId,second.workerId);assertTrue(second.isCurrentProfile())
                val secondAttempt=session(context,second.workerId).apply { data.put("finished",true) };second.save(secondAttempt)
                // A delayed callback from an old screen must still write only the first learner's records.
                first.saveRecall(JSONObject().put("key","same-question").put("streak",7))
                first.saveComponent(JSONObject().put("module","fire").put("stage",4))
                first.name="Updated first learner"
                second.saveRecall(JSONObject().put("key","same-question").put("streak",1))
                second.saveComponent(JSONObject().put("module","fire").put("stage",1))
                assertEquals("Updated first learner",first.name);assertEquals("Second learner",second.name)
                assertEquals(7,first.recalls()["same-question"]!!.getInt("streak"));assertEquals(1,second.recalls()["same-question"]!!.getInt("streak"))
                assertEquals(4,first.componentRecords()["fire"]!!.getInt("stage"));assertEquals(1,second.componentRecords()["fire"]!!.getInt("stage"))
                assertNull(second.attempt(firstAttempt.data.getString("id")))
                assertNull(first.attempt(secondAttempt.data.getString("id")))
                rejected { second.save(firstAttempt) }
                val collision=session(context,secondId).apply { data.put("id",firstAttempt.data.getString("id")) }
                rejected { second.save(collision) }
                rejected { second.saveRecall(first.recalls()["same-question"]!!) }
                rejected { second.saveComponent(first.componentRecords()["fire"]!!) }
                assertFalse(second.saveCredential(firstCredential));assertTrue(second.credentials().isEmpty())
                rejected { second.saveCredential(JSONObject(firstCredential.toString()).put("workerRef",secondId)) }
                assertEquals("a.b.c",first.credentials().single().getString("token"))
                val exported=second.export();assertEquals(secondId,exported.getJSONObject("worker").getString("id"))
                assertEquals(1,exported.getJSONArray("attempts").length())
                assertEquals(secondAttempt.data.getString("id"),exported.getJSONArray("attempts").getJSONObject(0).getString("id"))
                assertEquals(1,first.attempts().size);assertEquals(1,second.attempts().size)
            }
        }
    }
    @Test fun invalidProfileRequestsDoNotChangeTheSelection()=isolated { context,database ->
        Store(context,database).use { store ->
            val original=store.workerId
            rejected { store.createProfile("   ","Mining") }
            rejected { store.createProfile("x".repeat(81),"Mining") }
            rejected { store.createProfile("Person","Unknown sector") }
            rejected { store.switchProfile("missing-profile") }
            assertTrue(store.isCurrentProfile());assertEquals(1,store.profiles().size)
            Store(context,database).use { assertEquals(original,it.workerId) }
        }
    }
    @Test fun procedureEvidenceRejectsCrossWorkerPayloadsAndIdCollisionsWithoutChangingJournals()=isolated { context,database ->
        val firstWorker=UUID.randomUUID().toString();val secondWorker=UUID.randomUUID().toString()
        val first=ProcedureSession.create("fire",firstWorker,true)
        assertTrue(first.choose(first.step.actions.first { it.correct }.id,"screen",first.data.getLong("updatedAt")))
        val original=first.data.toString()
        ProcedureStore(context,firstWorker,database).use { firstStore ->
            firstStore.save(first.data)
            ProcedureStore(context,secondWorker,database).use { secondStore ->
                assertTrue(secondStore.records().isEmpty());assertNull(secondStore.latest("fire",true))
                rejected { secondStore.save(JSONObject(original)) }
                // A forged payload owner must not overwrite an existing ID belonging to another learner.
                rejected { secondStore.save(JSONObject(original).put("workerId",secondWorker)) }
                assertTrue(secondStore.records().isEmpty())
                assertEquals(original,firstStore.latest("fire",true).toString())
                val second=ProcedureSession.create("fire",secondWorker,true)
                secondStore.save(second.data)
                assertEquals(second.data.toString(),secondStore.records().single().toString())
                assertEquals(original,firstStore.records().single().toString())
                assertEquals(firstWorker,ProcedureSession.restore(firstStore.records().single()).data.getString("workerId"))
            }
        }
        // Check durable state after both helpers close, not merely their in-memory session objects.
        ProcedureStore(context,firstWorker,database).use { reopened ->
            val saved=reopened.records().single()
            assertEquals(original,saved.toString())
            assertEquals(1,saved.getJSONArray("events").length())
            assertTrue(ProcedureSession.restore(saved).feedback)
        }
        ProcedureStore(context,secondWorker,database).use { reopened ->
            assertEquals(secondWorker,reopened.records().single().getString("workerId"))
            assertEquals(0,reopened.records().single().getJSONArray("events").length())
        }
    }

}
