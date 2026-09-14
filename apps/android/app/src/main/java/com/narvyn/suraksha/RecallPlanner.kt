package com.narvyn.suraksha

import org.json.JSONObject

/** Local learning schedule only. This never changes assessment evidence or credentials. */
object RecallPlanner {
 const val DAY=86_400_000L
 data class Item(val key:String,val module:JSONObject,val question:JSONObject,val source:String,val dueAt:Long,val streak:Int,val round:Int=0)
 fun delay(correct:Boolean,streak:Int):Long=if(!correct)10*60_000L else when(streak){0,1->DAY;2->3*DAY;3->7*DAY;else->14*DAY}
 fun plan(current:JSONObject,versions:Map<String,JSONObject>,attempts:List<JSONObject>,reviews:Map<String,JSONObject>):List<Item>{
  val latest=attempts.filter{it.optBoolean("finished")&&it.optString("kind")=="assessment"}.sortedByDescending{it.optLong("endedAt")}.distinctBy{it.optString("moduleId")}
  return current.getJSONArray("modules").objects().flatMap{module->
   val a=latest.firstOrNull{it.optString("moduleId")==module.getString("id")}?:return@flatMap emptyList()
   val oldModule=versions[a.optString("contentVersion")]?.getJSONArray("modules")?.objects()?.firstOrNull{it.optString("id")==module.getString("id")}?:return@flatMap emptyList()
   module.getJSONArray("questions").objects().mapNotNull{q->
    val oldQuestion=oldModule.getJSONArray("questions").objects().firstOrNull{it.optString("id")==q.getString("id")}
    // A changed question requires fresh assessment evidence before deriving its review schedule.
    if(oldQuestion?.toString()!=q.toString())return@mapNotNull null
    val e=a.getJSONArray("events").objects().firstOrNull{it.optString("questionId")==q.getString("id")}?:return@mapNotNull null
    val option=q.getJSONArray("options").objects().firstOrNull{it.optString("id")==e.optString("optionId")}?:return@mapNotNull null
    val key="${current.getString("version")}:${module.getString("id")}:${q.getString("id")}";val source=a.getString("id")
    val review=(versions + (current.getString("version") to current)).entries.mapNotNull { (version,archive) ->
     val previousQuestion=archive.getJSONArray("modules").objects().firstOrNull{it.optString("id")==module.getString("id")}?.getJSONArray("questions")?.objects()?.firstOrNull{it.optString("id")==q.getString("id")}
     if(previousQuestion?.toString()!=q.toString()) null else reviews["$version:${module.getString("id")}:${q.getString("id")}"]?.takeIf{it.optString("source")==source}
    }.maxByOrNull{it.optLong("reviewedAt")}
    Item(key,module,q,a.getString("id"),review?.optLong("dueAt")?:a.getLong("endedAt")+(if(option.optBoolean("correct"))DAY else 0),review?.optInt("streak")?:0,review?.optInt("round")?:0)
   }
  }.sortedWith(compareBy<Item>{it.dueAt}.thenBy{it.key})
 }
 fun optionSeed(item:Item):Long = item.key.hashCode().toLong()*31L + item.source.hashCode().toLong()*17L + item.round.toLong()*1_000_003L
 fun record(item:Item,correct:Boolean,now:Long):JSONObject{
  val streak=if(correct)item.streak+1 else 0
  return JSONObject().put("key",item.key).put("source",item.source).put("correct",correct).put("reviewedAt",now).put("dueAt",now+delay(correct,streak)).put("streak",streak).put("round",item.round+1)
 }
}
