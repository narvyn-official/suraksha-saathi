package com.narvyn.suraksha

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Encrypted durable queue, one owner at a time; successful uploads may safely be repeated. */
object LearningSync {
    private val executor=Executors.newSingleThreadExecutor()
    private val running=AtomicBoolean(false)
    private fun prefs(c:Context)=c.getSharedPreferences("learner-sync",Context.MODE_PRIVATE)
    private fun key():SecretKey {val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};return store.getKey("suraksha_learning_queue",null) as? SecretKey?:KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder("suraksha_learning_queue",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey()}
    private fun file(c:Context,worker:String)=java.io.File(c.filesDir,"learning-sync-$worker.enc")
    private fun saveQueue(c:Context,worker:String,value:JSONObject){val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());val encrypted=cipher.doFinal(value.toString().toByteArray());val out=android.util.AtomicFile(file(c,worker));val stream=out.startWrite();try{stream.write((Base64.encodeToString(cipher.iv,Base64.NO_WRAP)+"."+Base64.encodeToString(encrypted,Base64.NO_WRAP)).toByteArray());out.finishWrite(stream)}catch(e:Exception){out.failWrite(stream);throw e}}
    private fun queue(c:Context,worker:String):JSONObject? {val f=file(c,worker);if(!f.exists())return null;val parts=f.readText().split('.');val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));return JSONObject(String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),Charsets.UTF_8))}
    fun bind(c:Context,worker:String,user:String){check(prefs(c).edit().putString("user:$worker",user).putString("origin:$worker",AdminClient(c).base).commit())}
    fun linked(c:Context,worker:String)=prefs(c).contains("user:$worker")
    fun status(c:Context,worker:String)=prefs(c).getString("status:$worker","Not synced yet")?:"Not synced yet"
    fun schedule(context:Context,callback:(String)->Unit={}){
        val c=context.applicationContext
        if(!running.compareAndSet(false,true)){callback("Sync already in progress");return}
        executor.execute{var worker="";var message="";try{Store(c).use{identity->
            worker=identity.workerId;val pref=prefs(c);val expected=pref.getString("user:$worker",null)?:return@use
            val api=AdminClient(c);check(api.base==pref.getString("origin:$worker",null)){"Reconnect to the original training server before syncing."}
            val current=api.call("/api/learner").getJSONObject("user").getString("userId");check(current==expected){"Sign in with this learner’s linked account to sync."}
            fun hash(value:String)=java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString(""){"%02x".format(it)}
            val scope="ack:"+hash("$current:${api.base}:$worker")+":"
            fun flush(pending:JSONObject){check(pending.getString("user")==current&&pending.getString("origin")==api.base){"Queued records belong to a different account or server."};val batches=pending.getJSONArray("batches");while(batches.length()>0){check(identity.isCurrentProfile()){ "Learner changed. Sync paused." };api.call("/api/learner",batches.getJSONObject(0));batches.remove(0);saveQueue(c,worker,pending)};val acknowledged=pending.optJSONObject("acknowledged")?:JSONObject();val editor=pref.edit();acknowledged.keys().forEach{k->editor.putString(scope+k,acknowledged.getString(k))};check(editor.commit());file(c,worker).delete()}
            queue(c,worker)?.let{flush(it)}
            val device=pref.getString("device",null)?:UUID.randomUUID().toString().also{check(pref.edit().putString("device",it).commit())}
            val revision=pref.getLong("revision:$worker",0)+1;check(pref.edit().putLong("revision:$worker",revision).commit())
            val acknowledged=JSONObject()
            fun changed(kind:String,record:JSONObject):Boolean{val key=kind+":"+record.getString("id");val value=hash(record.toString());acknowledged.put(key,value);return pref.getString(scope+key,null)!=value}
            val learning=LearningJourney(c,identity,Curriculum(c)).export();val attempts=identity.attempts().filter{it.optBoolean("finished")&&changed("attempt",it)};val procedures=ProcedureStore(c,worker).use{it.records()}.filter{changed("procedure",it)};val batches=JSONArray()
            fun batch()=JSONObject().put("action","sync").put("workerId",worker).put("deviceId",device).put("revision",revision).put("learning",learning).put("procedures",JSONArray())
            var currentBatch=batch()
            fun finishBatch(){batches.put(currentBatch);currentBatch=batch()}
            // Conservative record-sized batches avoid silently truncating older evidence.
            for(attempt in attempts){val bundle=JSONObject().put("schemaVersion",1).put("worker",JSONObject().put("id",worker).put("name",identity.name.ifBlank{"Learner"}).put("sector",identity.sector)).put("attempts",JSONArray().put(attempt));currentBatch.put("bundle",bundle);check(currentBatch.toString().toByteArray().size<900_000){"One assessment is too large to sync; ask your trainer for help."};finishBatch()}
            for(record in procedures){currentBatch.put("procedures",JSONArray().put(record));check(currentBatch.toString().toByteArray().size<900_000){"One scenario is too large to sync; ask your trainer for help."};finishBatch()}
            if(batches.length()==0)finishBatch()
            val pending=JSONObject().put("user",current).put("origin",api.base).put("batches",batches).put("acknowledged",acknowledged);saveQueue(c,worker,pending);flush(pending)
            val response=api.call("/api/learner?workerId=$worker");check(identity.isCurrentProfile()){"Learner changed. Wallet update paused."}
            response.getJSONArray("credentials").objects().forEach{row->val verified=CredentialVerifier.verify(c,row.getString("token"));check(verified.optString("workerRef")==worker){"Credential belongs to another learner; wallet unchanged."};verified.put("onlineStatus",row.optString("status")).put("statusCheckedAt",System.currentTimeMillis());identity.saveCredential(verified)}
            message="Synced · "+java.text.SimpleDateFormat("dd MMM, HH:mm",java.util.Locale.getDefault()).format(java.util.Date())
        }}catch(e:Exception){message=e.message?:"Sync paused. Your records remain saved; retry when connected."}finally{if(worker.isNotBlank()&&message.isNotBlank())prefs(c).edit().putString("status:$worker",message).apply();running.set(false);callback(message)}}
    }
}
