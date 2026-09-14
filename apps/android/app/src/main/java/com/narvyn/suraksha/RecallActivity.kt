package com.narvyn.suraksha

import android.app.Activity
import android.os.Bundle
import android.widget.*
import android.view.WindowInsets
import java.text.DateFormat
import java.util.Date

class RecallActivity:Activity(){
 private lateinit var store:Store
 private lateinit var curriculum:Curriculum
 private var selected:String?=null
 private var answer:String?=null
 private val hi get()=store.hi
 private fun t(en:String,hindi:String)=if(hi)hindi else en
 override fun onCreate(state:Bundle?){super.onCreate(state);store=Store(this);curriculum=Curriculum(this);selected=state?.getString("selected");answer=state?.getString("answer");render()}
 override fun onResume(){super.onResume();if(::store.isInitialized)render()}
 override fun onSaveInstanceState(out:Bundle){super.onSaveInstanceState(out);out.putString("selected",selected);out.putString("answer",answer)}
 override fun onDestroy(){store.close();super.onDestroy()}
 private fun render(){
  val items=RecallPlanner.plan(curriculum.json,curriculum.versions,store.attempts(),store.recalls());val now=System.currentTimeMillis();val item=items.firstOrNull{it.key==selected}
  val root=column(20);root.setBackgroundColor(Palette.canvas)
  root.accessibilityPaneTitle=if(item==null)t("Review queue","दोहराव सूची")else if(answer==null)t("Recall a decision","निर्णय याद करें")else t("Review feedback","दोहराव की प्रतिक्रिया")
  root.setOnApplyWindowInsetsListener{v,i->if(android.os.Build.VERSION.SDK_INT>=30){val b=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(20)+b.left,dp(20)+b.top,dp(20)+b.right,dp(20)+b.bottom)}else v.setPadding(dp(20)+i.systemWindowInsetLeft,dp(20)+i.systemWindowInsetTop,dp(20)+i.systemWindowInsetRight,dp(20)+i.systemWindowInsetBottom);i}
  val body=column();root.addView(ScrollView(this).apply{addView(body)},LinearLayout.LayoutParams(-1,0,1f))
  body.add(label(t("Remember. Decide. Reflect.","याद करें। निर्णय लें। सोचें।"),26f,Palette.ink,true).asHeading(),bottom=10)
  body.add(label(t("Personal review · your assessment results stay unchanged.","व्यक्तिगत दोहराव · आपके मूल्यांकन परिणाम नहीं बदलते।"),14f,Palette.muted),bottom=20)
  if(item==null){
   val componentRecords=store.componentRecords().filter { (module,record) -> ComponentCatalog.modules.containsKey(module) && record.optInt("catalogVersion")==ComponentCatalog.VERSION }
   if(componentRecords.isNotEmpty()) {
    body.add(label(t("Equipment recognition","उपकरण की पहचान"),20f,Palette.ink,true).asHeading(),bottom=12)
    componentRecords.forEach { (module,record) ->
     val done=record.optInt("stage")==3; val dueAt=record.optLong("dueAt"); val ready=!done||dueAt<=now
     val card=card();card.add(label(curriculum.module(module).local("title",hi),17f,Palette.ink,true),bottom=8)
     card.add(label(if(!done)t("Saved part practice","सहेजा हुआ पुर्ज़ा अभ्यास")else if(ready)t("Ready to recognize again","फिर पहचानने के लिए तैयार")else t("Next: ","अगला: ")+DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(dueAt)),14f,Palette.muted),bottom=10)
     card.add(action(if(!done)t("Resume part practice","पुर्ज़ा अभ्यास जारी रखें")else if(ready)t("Review equipment","उपकरण दोहराएँ")else t("Practise equipment early","उपकरण का अभ्यास अभी करें"),false){startActivity(android.content.Intent(this,ComponentPracticeActivity::class.java).putExtra("moduleId",module).putExtra("startReview",true))}.apply { contentDescription=text.toString()+": "+curriculum.module(module).local("title",hi) })
     body.add(card,bottom=14)
    }
   }
   val due=items.filter{it.dueAt<=now}
   body.add(label(t("${due.size} decisions ready to revisit","${due.size} निर्णय दोहराने के लिए तैयार"),20f,Palette.ink,true),bottom=16)
   if(items.isEmpty())body.add(label(t("Complete a current assessment to start reviews. If a question changed, its older answer is not reused.","दोहराव शुरू करने के लिए वर्तमान मूल्यांकन पूरा करें। सवाल बदलने पर पुराना उत्तर दोबारा उपयोग नहीं होता।")),bottom=20)
   if(due.isEmpty()&&items.isNotEmpty())body.add(label(t("Next review: ","अगला दोहराव: ")+DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(items.first().dueAt))),bottom=20)
   due.take(20).forEach{r->val c=card();c.add(label(r.module.local("title",hi),14f,Palette.blue),bottom=8);c.add(label(r.question.local("prompt",hi),18f,Palette.ink,true),bottom=12);c.add(action(t("Recall this decision","यह निर्णय याद करें")){selected=r.key;answer=null;render()}.apply { contentDescription=t("Recall this decision: ","यह निर्णय याद करें: ")+r.module.local("title",hi)+". "+r.question.local("prompt",hi) });body.add(c,bottom=14)}
   body.add(action(t("Refresh reviews","दोहराव फिर देखें"),false){render()},top=12,bottom=12)
   if(due.size>20)body.add(label(t("More decisions will appear as you finish these.","इन्हें पूरा करने पर और निर्णय दिखाई देंगे।"),14f,Palette.muted))
  }else{
   body.add(label(item.module.local("title",hi),15f,Palette.blue),bottom=12)
   body.add(label(item.question.local("prompt",hi),22f,Palette.ink,true).asHeading(),bottom=18)
   if(answer==null){
    body.add(label(t("Recall what you would do before choosing. It is fine to be unsure.","चुनने से पहले याद करें कि आप क्या करेंगे। निश्चित न होना ठीक है।"),15f,Palette.muted),bottom=16)
    item.question.getJSONArray("options").objects().shuffled(java.util.Random(RecallPlanner.optionSeed(item))).forEach{o->body.add(action(o.local("text",hi),false){respond(item,o.getString("id"))},bottom=12)}
    body.add(action(t("I’m not sure — help me learn","मुझे निश्चित नहीं है — सीखने में मदद करें"),false){respond(item,"_unsure")},bottom=12)
   }else{
    val correct=item.question.getJSONArray("options").objects().firstOrNull{it.optString("id")==answer}?.optBoolean("correct")==true
    val feedback=card(if(correct)Palette.successBg else Palette.amberBg)
    feedback.add(label(if(correct)t("You recalled a safe response","आपने सुरक्षित प्रतिक्रिया याद की")else t("A useful decision to revisit","यह निर्णय फिर दोहराना उपयोगी है"),20f,Palette.ink,true).asHeading(),bottom=12)
    feedback.add(label(item.question.local("explanation",hi)),bottom=12)
    feedback.add(label(t("In your own words: why is this safer, and when would you stop and ask for help?","अपने शब्दों में: यह अधिक सुरक्षित क्यों है, और आप कब रुककर मदद माँगेंगे?"),16f,Palette.ink,true))
    body.add(feedback,bottom=16)
    val dueAt=store.recalls()[item.key]?.optLong("dueAt")?:now
    body.add(label(t("Scheduled again: ","फिर निर्धारित: ")+DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(dueAt)),14f,Palette.muted),bottom=12)
    body.add(action(t("Back to reviews","दोहराव पर वापस जाएँ")){selected=null;answer=null;render()},bottom=16)
   }
  }
  root.add(action(t("Back to learning","सीखने पर वापस जाएँ"),false){finish()},top=12);setContentView(root)
 }
 private fun respond(item:RecallPlanner.Item,id:String){if(answer!=null)return;val correct=item.question.getJSONArray("options").objects().firstOrNull{it.optString("id")==id}?.optBoolean("correct")==true;store.saveRecall(RecallPlanner.record(item,correct,System.currentTimeMillis()).put("answer",id));answer=id;render()}
}
