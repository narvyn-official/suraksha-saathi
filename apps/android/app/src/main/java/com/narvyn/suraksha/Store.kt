package com.narvyn.suraksha

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID

/** Device-local record separation, not authentication or proof of worker identity. */
class Store(context: Context, databaseName: String = "suraksha.db"): SQLiteOpenHelper(context,databaseName,null,4) {
    val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    private val originalWorkerId: String = synchronized(profileLock) {
        prefs.getString("workerId",null) ?: UUID.randomUUID().toString().also {
            check(prefs.edit().putString("workerId",it).commit()) { "Could not save worker identity" }
        }
    }
    /** Immutable for this helper's lifetime: an old activity must never adopt a newly selected identity. */
    val workerId: String
    init {
        val db=writableDatabase
        workerId=synchronized(profileLock) {
            val selected=prefs.getString("activeWorkerId",originalWorkerId) ?: originalWorkerId
            val resolved=if(profileExists(db,selected))selected else originalWorkerId
            check(prefs.edit().putString("activeWorkerId",resolved).commit()) { "Could not select profile" }
            resolved
        }
    }
    var name: String
        get()=profileValue("name")
        set(value) { val clean=value.trim();require(clean.length<=80){"Name is too long"};updateProfile("name",clean) }
    var sector:String
        get()=profileValue("sector").ifBlank { "Unspecified" }
        set(value) { require(value in SECTORS){"Unknown sector"};updateProfile("sector",value) }
    var hi: Boolean get() = prefs.getBoolean("hi",false); set(value) { check(prefs.edit().putBoolean("hi",value).commit()) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE attempts(id TEXT PRIMARY KEY, payload TEXT NOT NULL, updated_at INTEGER NOT NULL,worker_id TEXT NOT NULL)")
        db.execSQL("CREATE TABLE credentials(id TEXT PRIMARY KEY, payload TEXT NOT NULL,worker_id TEXT NOT NULL)")
        db.execSQL("CREATE TABLE recalls(id TEXT PRIMARY KEY, payload TEXT NOT NULL,worker_id TEXT NOT NULL)")
        db.execSQL("CREATE TABLE component_learning(id TEXT PRIMARY KEY, payload TEXT NOT NULL,worker_id TEXT NOT NULL)")
        createProfiles(db)
        createOwnerIndexes(db)
    }
    private fun createProfiles(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE worker_profiles(id TEXT PRIMARY KEY,name TEXT NOT NULL,sector TEXT NOT NULL,created_at INTEGER NOT NULL)")
        check(db.insertOrThrow("worker_profiles",null,ContentValues().apply {
            put("id",originalWorkerId);put("name",prefs.getString("name","")?:"")
            put("sector",(prefs.getString("sector","Unspecified")?:"Unspecified").takeIf { it in SECTORS }?:"Unspecified")
            put("created_at",System.currentTimeMillis())
        })!=-1L)
    }
    private fun createOwnerIndexes(db:SQLiteDatabase) {
        for(table in listOf("attempts","credentials","recalls","component_learning"))db.execSQL("CREATE INDEX ${table}_worker_idx ON $table(worker_id)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if(oldVersion<2)db.execSQL("CREATE TABLE recalls(id TEXT PRIMARY KEY,payload TEXT NOT NULL)")
        if(oldVersion<3)db.execSQL("CREATE TABLE component_learning(id TEXT PRIMARY KEY,payload TEXT NOT NULL)")
        if(oldVersion<4) {
            createProfiles(db)
            for(table in listOf("attempts","credentials","recalls","component_learning")) {
                db.execSQL("ALTER TABLE $table ADD COLUMN worker_id TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE $table SET worker_id=?",arrayOf(originalWorkerId))
            }
            // Only SQL keys change. Historical JSON, including signatures and event histories, stays byte-for-byte intact.
            for(table in listOf("recalls","component_learning")) {
                val ids=db.rawQuery("SELECT id FROM $table",null).use { c -> buildList { while(c.moveToNext())add(c.getString(0)) } }
                for(id in ids)db.execSQL("UPDATE $table SET id=? WHERE id=?",arrayOf(scopedKey(originalWorkerId,id),id))
            }
            createOwnerIndexes(db)
        }
    }
    private fun profileExists(db:SQLiteDatabase,id:String)=db.rawQuery("SELECT 1 FROM worker_profiles WHERE id=?",arrayOf(id)).use { it.moveToFirst() }
    private fun profileValue(column:String):String=readableDatabase.rawQuery("SELECT $column FROM worker_profiles WHERE id=?",arrayOf(workerId)).use { if(it.moveToFirst())it.getString(0)else "" }
    private fun updateProfile(column:String,value:String) { check(writableDatabase.update("worker_profiles",ContentValues().apply { put(column,value) },"id=?",arrayOf(workerId))==1) }
    fun profiles():List<JSONObject> = readableDatabase.rawQuery("SELECT id,name,sector,created_at FROM worker_profiles ORDER BY created_at,id",null).use { c -> buildList {
        while(c.moveToNext())add(JSONObject().put("id",c.getString(0)).put("name",c.getString(1)).put("sector",c.getString(2)).put("createdAt",c.getLong(3)))
    } }
    /** Creates an empty local profile. Caller explicitly switches after creation. */
    fun createProfile(name:String,sector:String):String {
        val clean=name.trim();require(clean.isNotEmpty() && clean.length<=80){"Enter a name of 1–80 characters"};require(sector in SECTORS){"Unknown sector"}
        val id=UUID.randomUUID().toString()
        check(writableDatabase.insertOrThrow("worker_profiles",null,ContentValues().apply {
            put("id",id);put("name",clean);put("sector",sector);put("created_at",System.currentTimeMillis())
        })!=-1L)
        return id
    }
    fun switchProfile(id:String) { synchronized(profileLock) {
        require(profileExists(readableDatabase,id)){"Unknown worker profile"}
        check(prefs.edit().putString("activeWorkerId",id).commit()){ "Could not select worker profile" }
    } }
    fun isCurrentProfile():Boolean=prefs.getString("activeWorkerId",originalWorkerId)==workerId

    fun componentRecords():Map<String,JSONObject> = recordsByKey("component_learning","module")
    fun recalls():Map<String,JSONObject> = recordsByKey("recalls","key")
    private fun recordsByKey(table:String,key:String):Map<String,JSONObject> = readableDatabase.rawQuery("SELECT payload FROM $table WHERE worker_id=?",arrayOf(workerId)).use { c -> buildMap {
        while(c.moveToNext()){val record=JSONObject(c.getString(0));put(record.getString(key),record)}
    } }
    fun saveComponent(record:JSONObject)=saveLearning("component_learning",record.getString("module"),record)
    fun saveRecall(record:JSONObject)=saveLearning("recalls",record.getString("key"),record)
    private fun saveLearning(table:String,key:String,record:JSONObject) {
        require(!record.has("workerId")||record.optString("workerId")==workerId){"Record belongs to another worker"}
        val owned=JSONObject(record.toString()).put("workerId",workerId)
        check(writableDatabase.insertWithOnConflict(table,null,ContentValues().apply {
            put("id",scopedKey(workerId,key));put("payload",owned.toString());put("worker_id",workerId)
        },SQLiteDatabase.CONFLICT_REPLACE)!=-1L)
    }
    fun save(session: TrainingSession) {
        require(session.data.optString("workerId")==workerId){"Attempt belongs to another worker"}
        saveOwned("attempts",session.data.getString("id"),session.data,System.currentTimeMillis())
    }
    private fun saveOwned(table:String,id:String,payload:JSONObject,updatedAt:Long?=null) {
        val db=writableDatabase;db.beginTransaction()
        try {
            val existing=db.rawQuery("SELECT worker_id FROM $table WHERE id=?",arrayOf(id)).use { if(it.moveToFirst())it.getString(0)else null }
            require(existing==null||existing==workerId){"Record ID belongs to another worker"}
            val values=ContentValues().apply { put("id",id);put("payload",payload.toString());put("worker_id",workerId);if(updatedAt!=null)put("updated_at",updatedAt) }
            check(db.insertWithOnConflict(table,null,values,SQLiteDatabase.CONFLICT_REPLACE)!=-1L)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun attempts():List<JSONObject> = readableDatabase.rawQuery("SELECT payload FROM attempts WHERE worker_id=? ORDER BY updated_at DESC,id DESC",arrayOf(workerId)).use { c -> buildList { while(c.moveToNext())add(JSONObject(c.getString(0))) } }
    fun attempt(id:String):JSONObject? = readableDatabase.rawQuery("SELECT payload FROM attempts WHERE id=? AND worker_id=?",arrayOf(id,workerId)).use { if(it.moveToFirst())JSONObject(it.getString(0))else null }
    fun export():JSONObject=JSONObject().put("schemaVersion",1).put("exportedAt",System.currentTimeMillis()).put("worker",JSONObject().put("id",workerId).put("name",name).put("sector",sector))
        .put("attempts",JSONArray(attempts().filter { it.optBoolean("finished") }))
    /** A verified foreign credential may be displayed by the caller, but is never added to this learner's wallet. */
    fun saveCredential(credential:JSONObject):Boolean {
        if(credential.optString("workerRef")!=workerId)return false
        saveOwned("credentials",credential.getString("id"),credential)
        return true
    }
    fun credentials():List<JSONObject> = readableDatabase.rawQuery("SELECT payload FROM credentials WHERE worker_id=?",arrayOf(workerId)).use { c -> buildList { while(c.moveToNext())add(JSONObject(c.getString(0))) } }
    companion object {
        val SECTORS=listOf("Unspecified","Mining","Steel","Mica","Other")
        private val profileLock=Any()
        private fun scopedKey(worker:String,key:String)=JSONArray().put(worker).put(key).toString()
    }
}
