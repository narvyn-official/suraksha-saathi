package com.narvyn.suraksha

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class LearningJourneyFlowTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun demonstrationDoesNotCreateCreditAndReadingIsProfileScoped(){
        val original=Store(context).use{it.workerId}
        val worker=Store(context).use{val id=it.createProfile("Synthetic journey learner","Mining");it.switchProfile(id);id}
        try {
            Store(context).use { s ->
                val path=LearningJourney(context,s,Curriculum(context));assertFalse(path.stages("machinery").any{it.complete})
                ActivityScenario.launch<DemonstrationActivity>(Intent(context,DemonstrationActivity::class.java).putExtra("moduleId","machinery")).use{it.recreate()}
                assertTrue(ProcedureStore(context,worker).use{it.records().isEmpty()});assertTrue(s.attempts().isEmpty());assertFalse(path.stages("machinery").any{it.complete})
                path.finishReading("machinery");assertTrue(path.stages("machinery")[0].complete);assertFalse(path.stages("ppe")[0].complete)
                s.switchProfile(original)
            }
            Store(context).use{assertFalse(LearningJourney(context,it,Curriculum(context)).export().toString().contains(worker))}
        } finally {cleanup(worker,original)}
    }
    @Test fun failedUploadRetainsEncryptedQueueAndRetriesOnlyChangedEvidence(){
        val original=Store(context).use{it.workerId};val api=AdminClient(context)
        // Test process must not take over a user's authenticated session.
        assertFalse("Run this synthetic transport test signed out",api.hasSession())
        val sessionPrefs=context.getSharedPreferences("admin_session",0);val saved=sessionPrefs.all.toMap()
        val worker=Store(context).use{val id=it.createProfile("Synthetic sync learner","Mining");it.switchProfile(id);id}
        val posts=CopyOnWriteArrayList<JSONObject>();var fail=true;var account="synthetic-account"
        val server=ServerSocket(0,10,java.net.InetAddress.getByName("127.0.0.1"))
        val serving=Thread {
            try {while(!server.isClosed)server.accept().use { socket ->
                val reader=socket.getInputStream().bufferedReader();val request=reader.readLine()?:return@use;val headers=mutableMapOf<String,String>()
                while(true){val line=reader.readLine()?:break;if(line.isEmpty())break;val i=line.indexOf(':');if(i>0)headers[line.take(i).lowercase()]=line.substring(i+1).trim()}
                val size=headers["content-length"]?.toInt()?:0;val chars=CharArray(size);var got=0;while(got<size){val n=reader.read(chars,got,size-got);if(n<0)break;got+=n}
                var status=200;val response=when {
                    request.startsWith("POST /api/auth/")->"{}"
                    request.startsWith("POST ")->{posts.add(JSONObject(String(chars)));if(fail){fail=false;status=503};"{}"}
                    request.contains("workerId=")->"{\"credentials\":[]}"
                    else->JSONObject().put("user",JSONObject().put("userId",account)).toString()
                }.toByteArray()
                socket.getOutputStream().apply{write(("HTTP/1.1 $status OK\r\nContent-Type: application/json\r\nContent-Length: ${response.size}\r\nConnection: close\r\n"+(if(request.startsWith("POST /api/auth/"))"Set-Cookie: better-auth.session_token=synthetic; Path=/; HttpOnly\r\n"else "")+"\r\n").toByteArray());write(response);flush()}
            }}catch(_:java.net.SocketException){}
        }.apply{isDaemon=true;start()}
        try {
            api.configure("http://127.0.0.1:${server.localPort}");api.remember(false);api.call("/api/auth/sign-in/email",JSONObject())
            assertTrue(AdminClient(context).hasSession());assertFalse(sessionPrefs.contains("cookies"))
            LearningSync.bind(context,worker,account)
            val record=ProcedureSession.create("ppe",worker,true)
            ProcedureStore(context,worker).use{it.save(record.data)}
            Store(context).use{LearningJourney(context,it,Curriculum(context)).finishReading("ppe")}
            fun sync():String {val done=CountDownLatch(1);var message="";LearningSync.schedule(context){message=it;done.countDown()};assertTrue("Sync timed out",done.await(25,TimeUnit.SECONDS));return message}
            assertFalse(sync().startsWith("Synced"));val pending=java.io.File(context.filesDir,"learning-sync-$worker.enc");assertTrue(pending.exists());assertFalse(pending.readText().contains("procedures"))
            assertTrue(sync().startsWith("Synced"));assertFalse(pending.exists());assertEquals(posts[0].toString(),posts[1].toString())
            posts.clear();assertTrue(sync().startsWith("Synced"));assertEquals(1,posts.size);assertEquals(0,posts[0].getJSONArray("procedures").length())
            record.choose(record.step.actions.first{it.correct}.id,"description",System.currentTimeMillis());ProcedureStore(context,worker).use{it.save(record.data)}
            posts.clear();assertTrue(sync().startsWith("Synced"));assertEquals(1,posts[0].getJSONArray("procedures").length())
            account="another-account";posts.clear();assertFalse(sync().startsWith("Synced"));assertTrue(posts.isEmpty())
            api.clear();assertFalse(AdminClient(context).hasSession())
        } finally {
            server.close();serving.join(1500);api.clear();val edit=sessionPrefs.edit().clear();saved.forEach{(k,v)->when(v){is String->edit.putString(k,v);is Boolean->edit.putBoolean(k,v)}};edit.commit();cleanup(worker,original)
        }
    }
    private fun cleanup(worker:String,original:String){
        Store(context).use{it.switchProfile(original);it.writableDatabase.delete("worker_profiles","id=?",arrayOf(worker))}
        ProcedureStore(context,worker).use{it.writableDatabase.delete("procedures","worker=?",arrayOf(worker))}
        for(name in listOf("learning-path","learner-sync")){val pref=context.getSharedPreferences(name,0);val edit=pref.edit();pref.all.keys.filter{it.contains(worker)}.forEach{edit.remove(it)};edit.commit()}
        java.io.File(context.filesDir,"learning-sync-$worker.enc").delete()
    }
}
