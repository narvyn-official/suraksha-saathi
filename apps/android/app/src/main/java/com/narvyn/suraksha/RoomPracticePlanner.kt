package com.narvyn.suraksha

import org.json.JSONObject

/** Suggested action rehearsal dates, derived from worker-scoped journals; never certification. */
object RoomPracticePlanner {
    const val DAY=86_400_000L
    data class Item(val id:String,val module:String,val camera:Boolean,val explosionRisk:Boolean,
                    val dueAt:Long,val independentRehearsals:Int,val focus:List<String>)
    private data class Entry(val id:String,val updated:Long,val camera:Boolean,val mission:RoomMission,val coaching:RoomCoaching)
    fun plan(records:List<JSONObject>,worker:String):List<Item> {
        val valid=records.mapNotNull { record -> try {
            require(record.getString("workerId")==worker)
            val id=record.getString("id");require(id.isNotBlank())
            val updated=record.getLong("updatedAt");require(updated>=0&&updated<Long.MAX_VALUE-7*DAY)
            val mode=record.getString("mode");require(mode in setOf("camera","screen"))
            val mission=RoomMission.restore(record.getJSONObject("mission"))
            require(mission.completed&&mission.module==record.getString("module"))
            val coaching=RoomCoaching.restore(record.getJSONObject("coaching"),mission.module)
            Entry(id,updated,mode=="camera",mission,coaching)
        } catch(_:Exception){null} }.sortedByDescending{it.updated}.distinctBy{it.id}
        return valid.groupBy{Triple(it.mission.module,it.mission.explosionRisk,it.camera)}.values.map { attempts ->
            val latest=attempts.first()
            val independent=attempts.takeWhile { it.coaching.recall && it.coaching.cueCount==0 && it.mission.events.none{e->!e.accepted} }.size
            val days=when { independent==0->1;independent==1->3;else->7 }
            Item(latest.id,latest.mission.module,latest.camera,latest.mission.explosionRisk,
                latest.updated+days*DAY,independent,
                RoomLearning.priorities(RoomLearning.review(latest.mission,latest.coaching)).map{it.phase})
        }.sortedWith(compareBy<Item>{it.dueAt}.thenBy{it.id})
    }
}
