package com.narvyn.suraksha

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID

class Store(context: Context): SQLiteOpenHelper(context,"suraksha.db",null,1) {
    val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    val workerId: String get() = prefs.getString("workerId", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("workerId",it).commit() }
    var name: String get() = prefs.getString("name", "") ?: ""; set(value) { prefs.edit().putString("name", value).commit() }
    var hi: Boolean get() = prefs.getBoolean("hi",false); set(value) { prefs.edit().putBoolean("hi",value).commit() }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE attempts(id TEXT PRIMARY KEY, payload TEXT NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE credentials(id TEXT PRIMARY KEY, payload TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("Unsupported schema migration") }
    fun save(session: TrainingSession) {
        val values = ContentValues().apply { put("id",session.data.getString("id"));put("payload",session.data.toString());put("updated_at",System.currentTimeMillis()) }
        writableDatabase.insertWithOnConflict("attempts",null,values,SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
    }
    fun attempts(): List<JSONObject> = readableDatabase.rawQuery("SELECT payload FROM attempts ORDER BY updated_at DESC",null).use { c -> buildList { while(c.moveToNext()) add(JSONObject(c.getString(0))) } }
    fun attempt(id: String): JSONObject? = readableDatabase.rawQuery("SELECT payload FROM attempts WHERE id=?",arrayOf(id)).use { c -> if(c.moveToFirst()) JSONObject(c.getString(0)) else null }
    fun export(): JSONObject = JSONObject().put("schemaVersion",1).put("exportedAt",System.currentTimeMillis()).put("worker",JSONObject().put("id",workerId).put("name",name))
        .put("attempts",JSONArray(attempts().filter { it.optBoolean("finished") }))
    fun saveCredential(credential: JSONObject) {
        writableDatabase.insertWithOnConflict("credentials",null,ContentValues().apply {put("id",credential.getString("id"));put("payload",credential.toString())},SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun credentials(): List<JSONObject> = readableDatabase.rawQuery("SELECT payload FROM credentials",null).use { c -> buildList {while(c.moveToNext()) add(JSONObject(c.getString(0))) } }
}
