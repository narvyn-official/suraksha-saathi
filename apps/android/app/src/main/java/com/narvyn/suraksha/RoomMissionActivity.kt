package com.narvyn.suraksha

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.*
import android.widget.*
import org.json.JSONObject
import java.util.UUID

/** Action-led missions have a distinct local journal and never award an assessment certificate. */
class RoomMissionActivity:Activity() {
    private data class Retained(val worker:String,val id:String,val module:String,val camera:Boolean,val mission:RoomMission,val safe:Boolean)
    private lateinit var identity:Store
    private lateinit var store:RoomMissionStore
    private lateinit var mission:RoomMission
    private lateinit var scene:RoomMissionView
    private var id=UUID.randomUUID().toString()
    private var module="fire";private var camera=true;private var safe=false;private var active=false
    private var held=false;private var heldAt=0L;private var saveFailed=false
    private var modalCount=0
    private var placementCount=0;private var tracking=false
    private lateinit var title:TextView;private lateinit var prompt:TextView;private lateinit var status:TextView
    private lateinit var control:Button;private lateinit var progress:ProgressBar
    private var hintUntil=0L
    private var lastPhase="";private var lastPlaced=-1;private var lastTracked=false
    private fun t(en:String,hi:String)=if(identity.hi)hi else en
    override fun onCreate(state:Bundle?) {
        super.onCreate(state);identity=Store(this);store=RoomMissionStore(this,identity.workerId)
        module=intent.getStringExtra("moduleId")?.takeIf{it in listOf("fire","gas")}?:"fire";camera=intent.getBooleanExtra("camera",true)
        val kept=lastNonConfigurationInstance as? Retained
        if(kept?.worker==identity.workerId && kept.module==module && kept.camera==camera){mission=kept.mission.apply{resetIncomplete()};id=kept.id;safe=kept.safe}else mission=RoomMission(module)
        val root=column().apply{setBackgroundColor(Palette.canvas)}
        root.setOnApplyWindowInsetsListener{v,i->if(android.os.Build.VERSION.SDK_INT>=30){val b=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom)}else{v.setPadding(i.systemWindowInsetLeft,i.systemWindowInsetTop,i.systemWindowInsetRight,i.systemWindowInsetBottom)};i}
        val landscape=resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8))}
        title=label("",18f,bold=true).apply{maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END;isAccessibilityHeading=true};header.addView(title,LinearLayout.LayoutParams(0,-2,1f))
        val menu=action(t("Options","विकल्प"),false,ActionRole.NEUTRAL){options()}.apply{contentDescription=t("Options and saved missions","विकल्प और सहेजे मिशन");minHeight=dp(44);minimumHeight=dp(44)};header.addView(menu,LinearLayout.LayoutParams(-2,-2).apply{leftMargin=dp(8)})
        root.addView(capped(header,.22f,160),LinearLayout.LayoutParams(-1,-2))
        scene=RoomMissionView(this,module,camera,identity.hi)
        val panel=column(12);prompt=label("",17f,bold=true);panel.add(prompt)
        status=label("",13f,Palette.muted);panel.add(status,top=4)
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;progressTintList=android.content.res.ColorStateList.valueOf(Palette.teal)};panel.add(progress,top=6)
        if(landscape){
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            row.addView(scene,LinearLayout.LayoutParams(0,-1,.64f))
            row.addView(ScrollView(this).apply{addView(panel,FrameLayout.LayoutParams(-1,-2))},LinearLayout.LayoutParams(0,-1,.36f))
            root.addView(row,LinearLayout.LayoutParams(-1,0,1f))
        }else{root.addView(scene,LinearLayout.LayoutParams(-1,0,1f));root.addView(capped(panel,.24f,180),LinearLayout.LayoutParams(-1,-2))}
        control=action("",role=ActionRole.CAMERA){primary()}.apply{tag="room-mission-control"}
        root.addView(control,LinearLayout.LayoutParams(-1,-2).apply{setMargins(dp(12),dp(4),dp(12),dp(8))})
        control.setOnTouchListener{v,e->
            if(held && e.actionMasked==MotionEvent.ACTION_UP){release();return@setOnTouchListener true}
            if(e.actionMasked in listOf(MotionEvent.ACTION_CANCEL,MotionEvent.ACTION_POINTER_DOWN)){cancelGesture();return@setOnTouchListener true}
            if(saveFailed || mission.phase!="SWEEP" || !camera || placementCount<3)return@setOnTouchListener false
            when(e.actionMasked){
                MotionEvent.ACTION_DOWN->{if(scene.ready&&!saveFailed){held=true;heldAt=SystemClock.elapsedRealtime();v.isPressed=true;syncVisual()}}
                MotionEvent.ACTION_MOVE->{if(e.x !in 0f..v.width.toFloat() || e.y !in 0f..v.height.toFloat())cancelGesture()}
                MotionEvent.ACTION_UP->release()
            };true
        }
        scene.onImage={img->
            placementCount=img.placement;tracking=img.tracked
            if(!img.tracked){held=false;mission.resetIncomplete();syncVisual()}
            if(status.text.toString()!=img.message && !saveFailed && (SystemClock.elapsedRealtime()>=hintUntil || !img.tracked))status.text=img.message
            if(lastPlaced!=placementCount || lastTracked!=tracking)render()
        }
        scene.onAction={action->if(eligible())mutate{it.act(action,SystemClock.elapsedRealtime())}}
        scene.onRelease={if(!camera)release()}
        scene.onCancel={cancelGesture()}
        scene.onAim={x,y,pressed,at,fresh->
            if(eligible() && mission.phase in listOf("AIM","SWEEP")) {
                val valid=fresh && (!camera || !pressed || at>=heldAt)
                mutate{it.aim(x,y,pressed,at,valid)}
            }else mission.resetIncomplete()
        }
        setContentView(root);render()
        if(!safe)briefing()
    }
    private fun capped(content:View,fraction:Float,maxDp:Int)=object:ScrollView(this){
        init{addView(content,FrameLayout.LayoutParams(-1,-2));isFillViewport=false}
        override fun onMeasure(w:Int,h:Int){super.onMeasure(w,MeasureSpec.makeMeasureSpec(minOf(dp(maxDp),(MeasureSpec.getSize(h)*fraction).toInt()),MeasureSpec.AT_MOST))}
    }
    private fun briefing(){
        AlertDialog.Builder(this).setTitle(t("Make your room a practice scene","अपने कमरे में अभ्यास दृश्य बनाएँ"))
            .setMessage(t("Use a clear, well-lit training area away from live equipment. Place three separate stations on the floor: equipment, a virtual hazard, and a green withdrawal point. Stay in a safe position and turn the phone; walking is not required.\n\n"+(if(module=="fire")"This scenario assumes an authorised role, suitable extinguisher and clear retreat path. Practise alarm → pin → aim → sweep → stop and withdraw as conditions worsen." else "The meter is simulated. Establish an exclusion barrier and outside attendant. Rescue readiness is unconfirmed, so entry must be refused.")+"\n\nPlacement spacing is for this simulation, not a real safety distance. This mission records virtual actions; it does not certify practical competence.",
                "चालू उपकरणों से दूर, खाली और रोशनी वाला प्रशिक्षण क्षेत्र चुनें। फ़र्श पर उपकरण, काल्पनिक खतरा और हरा वापसी बिंदु अलग रखें। सुरक्षित जगह पर रहकर फ़ोन घुमाएँ; चलना आवश्यक नहीं।\n\n"+(if(module=="fire")"इस दृश्य में अधिकृत भूमिका, उपयुक्त अग्निशामक और साफ वापसी मार्ग माना गया है। अलार्म → पिन → निशाना → स्वीप → स्थिति बिगड़ने पर रोकें और हटें।" else "मीटर काल्पनिक है। सीमा और बाहर परिचर रखें। बचाव तैयारी की पुष्टि नहीं है, इसलिए प्रवेश मना करें।")+"\n\nरखने की दूरी केवल सिमुलेशन के लिए है। यह वास्तविक कौशल प्रमाणपत्र नहीं देता।"))
            .setPositiveButton(t("Start in a clear area","खाली क्षेत्र में शुरू करें")){_,_->safe=true;persist(mission);if(active)startScene();render()}
            .setNegativeButton(t("Back","वापस")){_,_->finish()}.setCancelable(false).show()
    }
    private fun startScene(){if(camera&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.CAMERA),81)else scene.resume()}
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==81&&active&&safe)scene.resume()}
    override fun onResume(){super.onResume();active=true;if(!identity.isCurrentProfile()){finish();return};if(safe&&modalCount==0)scene.resume()}
    override fun onWindowFocusChanged(focused:Boolean){super.onWindowFocusChanged(focused);if(!::scene.isInitialized)return;if(!focused){cancelGesture();scene.pause()}else if(active&&safe&&modalCount==0)scene.resume()}
    override fun onPause(){active=false;held=false;mission.resetIncomplete();scene.pause();super.onPause()}
    override fun onDestroy(){scene.close();store.close();identity.close();super.onDestroy()}
    override fun onRetainNonConfigurationInstance():Any=Retained(identity.workerId,id,module,camera,mission.fork(),safe)
    private fun eligible():Boolean {
        if(!active || !safe || modalCount>0 || saveFailed || !identity.isCurrentProfile())return false
        return scene.ready
    }
    private fun record(value:RoomMission)=JSONObject().put("id",id).put("workerId",identity.workerId).put("module",module)
        .put("mode",if(camera)"camera" else "screen").put("updatedAt",System.currentTimeMillis()).put("mission",value.toJson())
    private fun persist(value:RoomMission):Boolean=try{store.save(record(value));true}catch(_:Exception){saveFailed=true;status.text=t("Could not save. Actions paused; use Options to retry saving.","सहेजा नहीं गया। क्रियाएँ रुकी हैं; विकल्प से फिर सहेजें।");false}
    private fun mutate(change:(RoomMission)->Boolean){
        val before=mission;val next=before.fork();change(next)
        val changed=next.events.size!=before.events.size || next.phase!=before.phase
        if(changed && !persist(next)){held=false;syncVisual();return}
        mission=next
        if(next.lastInterruption!=null && next.lastInterruption!=before.lastInterruption){
            status.text=when(next.lastInterruption){
                "wrong-start-edge"->t("Start the sweep at either end of the highlighted base.","स्वीप चिह्नित आधार के किसी किनारे से शुरू करें।")
                "off-base"->t("Keep the aim on the base. Begin the movement again.","निशाना आधार पर रखें। फिर से शुरू करें।")
                "skipped-band"->t("Move smoothly across the whole base, without jumping over the middle.","बीच का भाग छोड़े बिना पूरे आधार पर धीरे खिसकाएँ।")
                "released"->t("Discharge released. Begin at either edge when ready.","डिस्चार्ज छूट गया। तैयार होने पर किसी किनारे से शुरू करें।")
                else->t("The view or gesture was interrupted. Settle the phone and begin again.","दृश्य या क्रिया बाधित हुई। फ़ोन स्थिर करके फिर शुरू करें।")
            };hintUntil=SystemClock.elapsedRealtime()+1400
        }
        if(before.phase!=next.phase){hintUntil=0;scene.performHapticFeedback(HapticFeedbackConstants.CONFIRM);held=held&&next.phase=="WITHDRAW";render()}
        progress.progress=(next.overallProgress*100).toInt();syncVisual()
    }
    private fun cancelGesture(){held=false;control.isPressed=false;mission.resetIncomplete();syncVisual()}
    private fun release(){held=false;control.isPressed=false
        if(active&&safe&&modalCount==0&&!saveFailed&&identity.isCurrentProfile()&&mission.phase in listOf("SWEEP","WITHDRAW"))mutate{it.act("release",SystemClock.elapsedRealtime())}else mission.resetIncomplete()
        syncVisual();render()
    }
    private fun syncVisual(){scene.update(mission.phase,mission.progress,held)}
    private fun primary(){
        when {
            saveFailed->{saveFailed=false;persist(mission);render()}
            mission.completed->debrief()
            !safe->briefing()
            camera&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED->startScene()
            placementCount<3->scene.place()
            mission.phase=="WITHDRAW"->release()
            else->missionNotice(t("Do the action in the scene","दृश्य में क्रिया करें"),prompt.text.toString())
        }
    }
    private fun render(){
        lastPhase=mission.phase;lastPlaced=placementCount;lastTracked=tracking
        title.text=t(if(camera)"ROOM AR · " else "SCREEN MISSION · ",if(camera)"कमरा AR · " else "स्क्रीन मिशन · ")+t(if(module=="fire")"Fire response" else "Confined space",if(module=="fire")"अग्नि प्रतिक्रिया" else "बंद स्थान")
        val phase=mission.phase
        prompt.text=if(camera&&placementCount<3)t(listOf("1 / 3 · Place the equipment station","2 / 3 · Place the virtual hazard","3 / 3 · Mark a clear withdrawal point")[placementCount],listOf("1 / 3 · उपकरण स्थल रखें","2 / 3 · काल्पनिक खतरा रखें","3 / 3 · खाली वापसी बिंदु रखें")[placementCount])else when(phase){
            "ALARM"->t("Raise the alarm. Tap the red call point beside the extinguisher.","अलार्म दें। अग्निशामक के पास लाल बिंदु छुएँ।")
            "PIN"->t("Pull the pin. Drag outwards from the highlighted retaining ring.","पिन निकालें। चिह्नित पिन के छल्ले से बाहर की ओर खींचें।")
            "AIM"->t(if(camera)"Aim the centre cross at the fire’s base. Hold it steady." else "Touch the centre of the fire’s base and hold steady.",if(camera)"बीच का निशाना आग के आधार पर स्थिर रखें।" else "आग के आधार के बीच को छूकर स्थिर रखें।")
            "SWEEP"->t(if(camera)"Hold discharge. Start at either edge and slowly sweep across the entire base by turning the phone." else "Drag slowly from one edge of the base to the other, keeping your finger down.",if(camera)"डिस्चार्ज दबाएँ। किसी किनारे से शुरू करके फ़ोन घुमाते हुए पूरे आधार पर धीरे स्वीप करें।" else "उँगली दबाकर आधार के एक किनारे से दूसरे किनारे तक धीरे खींचें।")
            "WITHDRAW"->t("Conditions are worsening. Release discharge, then select your green withdrawal marker. Do not approach the fire.","स्थिति बिगड़ रही है। डिस्चार्ज छोड़ें, फिर हरा वापसी बिंदु चुनें। आग के पास न जाएँ।")
            "GAS_CHECK"->t("Inspect the SIM meter. Hold the highlighted display, then release. These are not live readings.","SIM मीटर जाँचें। चिह्नित डिस्प्ले दबाकर छोड़ें। यह वास्तविक माप नहीं है।")
            "BARRIER"->t("Establish exclusion. Drag the tape from the left post to the right post.","प्रवेश सीमा बनाएँ। बाएँ खंभे से दाएँ तक टेप खींचें।")
            "ATTENDANT"->t("Keep an attendant outside. Drag from the equipment marker to the green outside point.","परिचर बाहर रखें। उपकरण चिह्न से हरे बाहरी बिंदु तक खींचें।")
            "REFUSE"->t("Rescue readiness is unconfirmed. Select the green outside marker to refuse entry and request support in this simulation.","बचाव तैयारी की पुष्टि नहीं है। प्रवेश मना करने और काल्पनिक सहायता माँगने के लिए हरा बाहरी बिंदु चुनें।")
            else->t("Mission complete. Review the actions you performed.","मिशन पूरा हुआ। अपनी की गई क्रियाएँ देखें।")
        }
        control.text=when{
            saveFailed->t("Retry saving","फिर सहेजें")
            mission.completed->t("View mission debrief","मिशन समीक्षा देखें")
            camera&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED->t("Enable camera","कैमरा अनुमति दें")
            placementCount<3->t("Place at centre cross","बीच के निशाने पर रखें")
            phase=="SWEEP"&&camera->t("Hold to discharge","डिस्चार्ज के लिए दबाएँ")
            phase=="WITHDRAW"->t("Release discharge","डिस्चार्ज छोड़ें")
            else->t("How to do this action","यह क्रिया कैसे करें")
        }
        control.actionRole(if(phase=="WITHDRAW")ActionRole.DANGER else if(phase=="SWEEP"&&camera)ActionRole.PRIMARY else ActionRole.CAMERA)
        progress.progress=(mission.overallProgress*100).toInt();syncVisual()
    }
    private fun debrief(){
        val details=mission.measurements.joinToString("\n"){"${it.kind}: ${it.durationMs} ms · ${it.samples} ${t("samples","नमूने")}"}
        missionNotice(t("Your mission evidence","आपका मिशन रिकॉर्ड"),t("${mission.events.count{it.accepted}} accepted actions · ${if(camera)"camera AR" else "screen simulation"}\n$details\n\n"+(if(module=="fire")"You stopped and withdrew when conditions worsened." else "You kept the attendant outside and refused unconfirmed entry.")+"\n\nVirtual actions saved on this device. Physical equipment handling, real hazard recognition and practical competence were not assessed.","${mission.events.count{it.accepted}} स्वीकृत क्रियाएँ · ${if(camera)"कैमरा AR" else "स्क्रीन सिमुलेशन"}\n$details\n\nकाल्पनिक क्रियाएँ इस उपकरण पर सहेजी गई हैं। वास्तविक उपकरण संचालन, खतरा पहचान और व्यावहारिक दक्षता की जाँच नहीं हुई।"))
    }
    private fun options(){
        val items=arrayOf(t("Resume mission","मिशन जारी रखें"),t("Restart camera mission","कैमरा मिशन फिर शुरू करें"),t("Start separate screen mission","अलग स्क्रीन मिशन शुरू करें"),t("Text procedure alternative","लिखित प्रक्रिया विकल्प"),t("Saved mission records","सहेजे मिशन रिकॉर्ड"),t("Save & leave","सहेजें और लौटें"))
        // A modal must not accumulate aim or discharge progress behind it.
        modalCount++;held=false;mission.resetIncomplete();scene.pause()
        AlertDialog.Builder(this).setTitle(t("Mission options","मिशन विकल्प")).setItems(items){_,index->when(index){
            1,2->{if(persist(mission)){startActivity(Intent(this,RoomMissionActivity::class.java).putExtra("moduleId",module).putExtra("camera",index==1));finish()}}
            3->{if(persist(mission)){startActivity(Intent(this,ProcedureActivity::class.java).putExtra("moduleId",module));finish()}}
            4->saved()
            5->{if(persist(mission))finish()}
        }}.setOnDismissListener{dismissModal()}.show()
    }
    private fun dismissModal(){modalCount=(modalCount-1).coerceAtLeast(0);if(modalCount==0&&active&&!isFinishing&&safe)scene.resume()}
    private fun missionNotice(title:String,message:String){modalCount++;held=false;mission.resetIncomplete();scene.pause();AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK",null).setOnDismissListener{dismissModal()}.show()}
    private fun saved(){val records=store.records();missionNotice(t("Saved missions · this learner","सहेजे मिशन · यह शिक्षार्थी"),if(records.isEmpty())t("No saved missions yet.","अभी कोई मिशन नहीं सहेजा गया।")else records.take(12).joinToString("\n\n"){r->val m=r.getJSONObject("mission");"${r.getString("module")} · ${r.getString("mode")} · ${m.getString("phase")}\n${t("Virtual actions only · no certificate","केवल काल्पनिक क्रियाएँ · प्रमाणपत्र नहीं")}"})}
}
