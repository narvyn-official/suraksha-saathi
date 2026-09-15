package com.narvyn.suraksha

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/** Worker-bound audit snapshots. These records never enter the assessment or credential stores. */
class RoomMissionStore(context: Context, private val worker: String, name: String = "room-missions.db") :
    SQLiteOpenHelper(context, name, null, 1) {
    init { require(worker.isNotBlank() && worker.length <= 128) { "Invalid mission owner." } }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE room_missions(id TEXT PRIMARY KEY,worker TEXT NOT NULL,module TEXT NOT NULL,mode TEXT NOT NULL,updated INTEGER NOT NULL,payload TEXT NOT NULL)")
        db.execSQL("CREATE INDEX room_mission_worker ON room_missions(worker,updated)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun save(record: JSONObject) {
        // Validate and persist the same detached snapshot even if the caller later mutates its object.
        val payload = record.toString()
        val snapshot = JSONObject(payload)
        val id = snapshot.opt("id")
        val module = snapshot.opt("module")
        val mode = snapshot.opt("mode")
        val updated = snapshot.opt("updatedAt")
        require(id is String && id.isNotBlank() && id.length <= 128) { "Invalid mission ID." }
        require(snapshot.opt("workerId") == worker) { "Mission owner does not match this store." }
        require(module is String && module in setOf("fire", "gas")) { "Unsupported room mission." }
        require(mode is String && mode in setOf("camera", "screen")) { "Unsupported mission presentation." }
        require((updated is Long || updated is Int) && (updated as Number).toLong() >= 0) { "Invalid mission timestamp." }
        val mission = snapshot.optJSONObject("mission")
        require(mission != null && mission.opt("module") == module) { "Mission module does not match its record." }
        val result = mission.optJSONObject("result")
        require(result != null && result.opt("certifiable") == false) { "Room missions cannot certify competence." }
        val incomingCoaching = coaching(snapshot, module)

        val db = writableDatabase
        db.beginTransaction()
        try {
            db.rawQuery("SELECT worker,module,mode,payload FROM room_missions WHERE id=?", arrayOf(id)).use { cursor ->
                if (cursor.moveToFirst()) {
                    require(cursor.getString(0) == worker) { "Mission ID belongs to another worker." }
                    require(cursor.getString(1) == module) { "A mission ID cannot change module." }
                    require(cursor.getString(2) == mode) { "A mission ID cannot change presentation." }
                    val previousCoaching = coaching(JSONObject(cursor.getString(3)), module)
                    require(previousCoaching.mode == incomingCoaching.mode) { "A mission ID cannot change coaching mode." }
                    require(incomingCoaching.cues.size >= previousCoaching.cues.size &&
                        previousCoaching.cues.indices.all { incomingCoaching.cues[it] == previousCoaching.cues[it] }) {
                        "Previously saved coaching cues cannot be removed or changed."
                    }
                }
            }
            val values = ContentValues().apply {
                put("id", id); put("worker", worker); put("module", module); put("mode", mode)
                put("updated", (updated as Number).toLong()); put("payload", payload)
            }
            check(db.insertWithOnConflict("room_missions", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private data class Cue(val phase: String, val at: Long)
    private data class Coaching(val mode: String, val cues: List<Cue>)

    /** Legacy payloads retain their bytes and mean guided practice without recalled cues. */
    private fun coaching(record: JSONObject, module: String): Coaching {
        if (!record.has("coaching")) return Coaching("guided", emptyList())
        val value = record.opt("coaching")
        require(value is JSONObject && value.keys().asSequence().toSet() == setOf("version", "mode", "cues")) {
            "Invalid coaching metadata."
        }
        val version = value.opt("version")
        require((version is Int || version is Long) && (version as Number).toLong() == 1L) { "Unsupported coaching version." }
        val mode = value.opt("mode")
        require(mode is String && mode in setOf("guided", "recall")) { "Invalid coaching mode." }
        val cues = value.optJSONArray("cues")
        require(cues != null && cues.length() <= 128) { "Invalid coaching cue list." }
        require(mode != "guided" || cues.length() == 0) { "Guided missions cannot contain recalled cues." }
        // The v2 journey retains every v1 phase, so legacy cue histories remain readable without migration.
        val phases = RoomMission.phases(module).filterNot { it == "COMPLETE" }.toSet()
        val parsed = mutableListOf<Cue>()
        for (index in 0 until cues.length()) {
            val cue = cues.opt(index)
            require(cue is JSONObject && cue.keys().asSequence().toSet() == setOf("phase", "at")) { "Invalid coaching cue." }
            val phase = cue.opt("phase")
            val at = cue.opt("at")
            require(phase is String && phase in phases) { "Coaching cue phase does not match its module." }
            require((at is Int || at is Long) && (at as Number).toLong() >= 0L) { "Invalid coaching cue timestamp." }
            val time = (at as Number).toLong()
            require(parsed.lastOrNull()?.let { time >= it.at } != false) { "Coaching cue timestamps cannot decrease." }
            parsed.add(Cue(phase, time))
        }
        return Coaching(mode, parsed)
    }

    fun record(id:String):JSONObject? = readableDatabase.rawQuery(
        "SELECT payload FROM room_missions WHERE id=? AND worker=?",arrayOf(id,worker)
    ).use{cursor->if(cursor.moveToFirst())JSONObject(cursor.getString(0))else null}

    fun records(): List<JSONObject> = readableDatabase.rawQuery(
        "SELECT payload FROM room_missions WHERE worker=? ORDER BY updated DESC,id DESC", arrayOf(worker)
    ).use { cursor ->
        buildList { while (cursor.moveToNext()) add(JSONObject(cursor.getString(0))) }
    }
}
