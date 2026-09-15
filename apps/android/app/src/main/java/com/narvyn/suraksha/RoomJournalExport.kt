package com.narvyn.suraksha

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Separate, worker-bound practice export. It never creates an assessment or credential. */
object RoomJournalExport {
    fun create(context: Context, identity: Store): JSONObject {
        check(identity.isCurrentProfile()) { "The active learner changed." }
        val records=RoomMissionStore(context,identity.workerId).use { it.records() }
        require(records.isNotEmpty()) { "No room practice journals are saved." }
        val chosen=records.take(100).toMutableList()
        fun envelope()=JSONObject().put("schemaVersion",1).put("kind","room-mission-journal")
            .put("exportedAt",System.currentTimeMillis())
            .put("worker",JSONObject().put("id",identity.workerId).put("name",identity.name).put("sector",identity.sector))
            .put("omittedCount",records.size-chosen.size).put("rooms",JSONArray(chosen))
        var result=envelope()
        // Reserve space for the readable indentation used by the Android document exporter.
        while(chosen.size>1 && result.toString(2).toByteArray(Charsets.UTF_8).size>950_000) {
            chosen.removeAt(chosen.lastIndex);result=envelope()
        }
        require(result.toString(2).toByteArray(Charsets.UTF_8).size<=950_000) { "Room journal is too large to export." }
        check(identity.isCurrentProfile()) { "The active learner changed." }
        return result
    }
}
