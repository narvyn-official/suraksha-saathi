package com.narvyn.suraksha

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout

/** A worked example never writes a learner attempt or earns assessment credit. */
class DemonstrationActivity:Activity(){
    private lateinit var identity:Store
    private lateinit var scene:ProcedureSceneView
    private lateinit var body:LinearLayout
    private var index=0
    private var module="fire"
    private fun t(en:String,hi:String)=if(identity.hi)hi else en
    override fun onCreate(state:Bundle?){super.onCreate(state);identity=Store(this);module=intent.getStringExtra("moduleId")?.takeIf{it in ProcedureCatalog.modules}?:"fire";index=state?.getInt("step")?:0
        val root=column(16).apply{setBackgroundColor(Palette.canvas)};root.setOnApplyWindowInsetsListener{v,i->if(android.os.Build.VERSION.SDK_INT>=30){val b=i.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(dp(16)+b.left,b.top,dp(16)+b.right,b.bottom)}else v.setPadding(dp(16)+i.systemWindowInsetLeft,i.systemWindowInsetTop,dp(16)+i.systemWindowInsetRight,i.systemWindowInsetBottom);i}
        root.add(label(t("Worked example · demonstration","पूरा उदाहरण · प्रदर्शन"),20f,bold=true).asHeading(),top=12,bottom=8)
        scene=ProcedureSceneView(this);root.addView(scene,LinearLayout.LayoutParams(-1,0,1f));body=column(12);root.addView(paged(body,identity.hi),LinearLayout.LayoutParams(-1,0,1f));root.add(action(t("Return to course","पाठ्यक्रम पर लौटें"),false){finish()},bottom=12);setContentView(root);render()
    }
    private fun render(){val definition=ProcedureCatalog.modules.getValue(module);index=index.coerceIn(0,definition.steps.lastIndex);val step=definition.steps[index];body.removeAllViews();body.add(label("${index+1} / ${definition.steps.size} · "+step.text(identity.hi),17f,bold=true),bottom=8);body.add(label(step.explain(identity.hi),15f),bottom=8)
        val action=step.actions.first{it.correct};body.add(label(t("Demonstrated action: ","दिखाई क्रिया: ")+action.text(identity.hi),15f,Palette.teal),bottom=8)
        scene.configure(ProcedureSceneView.Scene(module,step.id,listOf(ProcedureSceneView.Target(action.id,action.text(identity.hi),action.point)),definition.steps.take(index+1).map{it.id}.toSet(),enabled=false),identity.hi){_,_,_->};scene.useCamera(false);scene.resume()
        if(index>0)body.add(action(t("Previous step","पिछला चरण"),false){index--;render()},bottom=8)
        body.add(action(if(index<definition.steps.lastIndex)t("Next demonstrated step","अगला दिखाया चरण")else t("Example complete · return to learning","उदाहरण पूरा · सीखने पर लौटें")){if(index<definition.steps.lastIndex){index++;render()}else finish()})
    }
    override fun onSaveInstanceState(out:Bundle){out.putInt("step",index);super.onSaveInstanceState(out)}
    override fun onPause(){scene.pause();super.onPause()}
    override fun onResume(){super.onResume();if(::identity.isInitialized&&!identity.isCurrentProfile())finish()else if(::scene.isInitialized)scene.resume()}
    override fun onDestroy(){scene.close();identity.close();super.onDestroy()}
}
