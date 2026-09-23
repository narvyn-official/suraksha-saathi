package com.narvyn.suraksha

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Completion is explicit, profile/version scoped, and never inferred from opening a screen. */
class LearningJourney(private val context:Context,private val learner:Store,private val curriculum:Curriculum) {
    data class Stage(val id:String,val en:String,val hi:String,val complete:Boolean)
    private val prefs=context.getSharedPreferences("learning-path",Context.MODE_PRIVATE)
    private fun key(module:String)="${learner.workerId}:${curriculum.version}:$module"
    fun finishReading(module:String){check(learner.isCurrentProfile());check(prefs.edit().putBoolean(key(module),true).commit())}
    fun stages(module:String):List<Stage>{
        val records=ProcedureStore(context,learner.workerId).use{it.records()}.filter{it.optString("module")==module&&it.optInt("catalogVersion")==ProcedureCatalog.VERSION}
        val independent=records.firstOrNull{!it.optBoolean("guided")}
        val assessment=learner.attempts().firstOrNull{it.optString("moduleId")==module&&it.optString("kind")=="assessment"&&it.optBoolean("finished")}
        return listOf(Stage("learn","Read or listen","पढ़ें या सुनें",prefs.getBoolean(key(module),false)),
            Stage("guided","Guided AR rehearsal","निर्देशित AR अभ्यास",records.any{it.optBoolean("guided")&&it.optJSONObject("result")?.optBoolean("complete")==true}),
            Stage("independent","Independent scenario check","स्वतंत्र दृश्य जाँच",independent?.optJSONObject("result")?.optBoolean("complete")==true),
            Stage("assessment","Knowledge assessment","ज्ञान मूल्यांकन",assessment?.optString("contentVersion")==curriculum.version&&assessment.optJSONObject("result")?.optBoolean("passed")==true))
    }
    fun export()=JSONObject().put("contentVersion",curriculum.version).put("lessons",JSONArray(curriculum.modules.filter{prefs.getBoolean(key(it.getString("id")),false)}.map{it.getString("id")}))
}
