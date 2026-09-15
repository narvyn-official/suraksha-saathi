package com.narvyn.suraksha

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/** Separate, worker-scoped procedural evidence. Never silently converted into a credential. */
class ProcedureStore(context: Context, private val worker: String, name: String = "procedures.db") : SQLiteOpenHelper(context,name,null,1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE procedures(id TEXT PRIMARY KEY,worker TEXT NOT NULL,module TEXT NOT NULL,guided INTEGER NOT NULL,updated INTEGER NOT NULL,payload TEXT NOT NULL)")
        db.execSQL("CREATE INDEX procedure_worker ON procedures(worker,updated)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun save(record: JSONObject) {
        require(record.getString("workerId")==worker)
        val id=record.getString("id")
        writableDatabase.beginTransaction()
        try {
            writableDatabase.rawQuery("SELECT worker FROM procedures WHERE id=?",arrayOf(id)).use { if(it.moveToFirst())require(it.getString(0)==worker) }
            check(writableDatabase.insertWithOnConflict("procedures",null,ContentValues().apply {
                put("id",id);put("worker",worker);put("module",record.getString("module"));put("guided",if(record.getBoolean("guided"))1 else 0)
                put("updated",System.currentTimeMillis());put("payload",record.toString())
            },SQLiteDatabase.CONFLICT_REPLACE)!=-1L)
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }
    fun records(): List<JSONObject> = readableDatabase.rawQuery("SELECT payload FROM procedures WHERE worker=? ORDER BY updated DESC,rowid DESC",arrayOf(worker)).use { c -> buildList { while(c.moveToNext())add(JSONObject(c.getString(0))) } }
    fun latest(module: String, guided: Boolean): JSONObject? = records().firstOrNull { it.optString("module")==module && it.optBoolean("guided")==guided }
}
