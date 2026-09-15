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
    private data class Retained(val worker:String,val id:String,val module:String,val camera:Boolean,val mission:RoomMission,val safe:Boolean,val coaching:RoomCoaching)
    private lateinit var identity:Store
    private lateinit var store:RoomMissionStore
    private lateinit var mission:RoomMission
    private lateinit var coaching:RoomCoaching
    private lateinit var cueButton:Button
    private val cueExpiry=Runnable{if(active&&!isFinishing){hideCoaching();render()}}
    private lateinit var scene:RoomMissionView
    private var id=UUID.randomUUID().toString()
    private var module="fire";private var camera=true;private var safe=false;private var active=false
    private var held=false;private var heldAt=0L;private var saveFailed=false
    private var modalCount=0
    private var placementCount=0;private var tracking=false
    private lateinit var title:TextView;private lateinit var prompt:TextView;private lateinit var status:TextView
    private lateinit var control:Button;private lateinit var progress:ProgressBar
    private var hintUntil=0L
    private var lastPhase="";private var lastPlaced=-1;private var lastTracked=false;private var lastCanPlace=false;private var lastRetry=false
    private fun t(en:String,hi:String)=if(identity.hi)hi else en
    override fun onCreate(state:Bundle?) {
        super.onCreate(state);identity=Store(this);store=RoomMissionStore(this,identity.workerId)
        module=intent.getStringExtra("moduleId")?.takeIf{it in listOf("fire","gas")}?:"fire";camera=intent.getBooleanExtra("camera",true)
        coaching=RoomCoaching(module,intent.getBooleanExtra("recall",false))
        val kept=lastNonConfigurationInstance as? Retained
        if(kept?.worker==identity.workerId && kept.module==module && kept.camera==camera && kept.coaching.recall==coaching.recall){mission=kept.mission.apply{resetIncomplete()};id=kept.id;safe=kept.safe;coaching=kept.coaching.apply{hideCue()}}else mission=RoomMission(module)
        val root=column().apply{setBackgroundColor(Palette.canvas)}
        root.setOnApplyWindowInsetsListener{v,i->if(android.os.Build.VERSION.SDK_INT>=30){val b=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom)}else{v.setPadding(i.systemWindowInsetLeft,i.systemWindowInsetTop,i.systemWindowInsetRight,i.systemWindowInsetBottom)};i}
        val landscape=resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8))}
        title=label("",18f,bold=true).apply{maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END;isAccessibilityHeading=true};header.addView(title,LinearLayout.LayoutParams(0,-2,1f))
        cueButton=action(t("Hint","संकेत"),false,ActionRole.REVIEW){requestCue()}.apply{visibility=if(coaching.recall)View.VISIBLE else View.GONE;contentDescription=t("Reveal a coaching cue for seven seconds; recorded as help","सात सेकंड के लिए संकेत दिखाएँ; सहायता दर्ज होगी")}
        header.addView(cueButton,LinearLayout.LayoutParams(-2,-2).apply{leftMargin=dp(4)})
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
        }else{root.addView(scene,LinearLayout.LayoutParams(-1,0,1f));root.addView(capped(panel,.24f,180,fixed=true),LinearLayout.LayoutParams(-1,-2))}
        control=action("",role=ActionRole.CAMERA){primary()}.apply{tag="room-mission-control"}
        root.addView(control,LinearLayout.LayoutParams(-1,-2).apply{setMargins(dp(12),dp(4),dp(12),dp(8))})
        control.setOnTouchListener{v,e->
            if(held && e.actionMasked==MotionEvent.ACTION_UP){release();return@setOnTouchListener true}
            if(e.actionMasked in listOf(MotionEvent.ACTION_CANCEL,MotionEvent.ACTION_POINTER_DOWN)){cancelGesture();return@setOnTouchListener true}
            if(saveFailed || scene.needsRetry || mission.phase!="SWEEP" || !camera || placementCount<3)return@setOnTouchListener false
            when(e.actionMasked){
                MotionEvent.ACTION_DOWN->{if(scene.ready&&!saveFailed){held=true;heldAt=SystemClock.elapsedRealtime();v.isPressed=true;syncVisual()}}
                MotionEvent.ACTION_MOVE->{if(e.x !in 0f..v.width.toFloat() || e.y !in 0f..v.height.toFloat())cancelGesture()}
                MotionEvent.ACTION_UP->release()
            };true
        }
        scene.onImage={img->
            placementCount=img.placement;tracking=img.tracked
            cueButton.isEnabled=coaching.recall&&!mission.completed&&scene.ready&&!saveFailed&&(!coaching.cueVisible(mission.phase,SystemClock.elapsedRealtime())||coaching.cueCount>=RoomCoaching.MAX_CUES)
            if(!img.tracked){held=false;mission.resetIncomplete();syncVisual()}
            if(status.text.toString()!=img.message && !saveFailed && (SystemClock.elapsedRealtime()>=hintUntil || !img.tracked))status.text=img.message
            if(lastPlaced!=placementCount || lastTracked!=tracking || lastCanPlace!=scene.canPlace || lastRetry!=scene.needsRetry)render()
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
    // Keep feedback changes from resizing the camera viewport or cancelling an active pointer.
    private fun capped(content:View,fraction:Float,maxDp:Int,fixed:Boolean=false)=object:ScrollView(this){
        init{addView(content,FrameLayout.LayoutParams(-1,-2));isFillViewport=false}
        override fun onMeasure(w:Int,h:Int){super.onMeasure(w,MeasureSpec.makeMeasureSpec(minOf(dp(maxDp),(MeasureSpec.getSize(h)*fraction).toInt()),if(fixed)MeasureSpec.EXACTLY else MeasureSpec.AT_MOST))}
    }
    private fun briefing(){
        AlertDialog.Builder(this).setTitle(t("Make your room a practice scene","अपने कमरे में अभ्यास दृश्य बनाएँ"))
            .setMessage((if(coaching.recall)t("REMEMBER, THEN DO: target rings and step-by-step instructions are hidden. Hint reveals coaching for seven seconds and records the help. This is practice, not an independent certificate assessment.\n\n","याद करके करें: लक्ष्य के घेरे और क्रमवार निर्देश छिपे हैं। संकेत सात सेकंड सहायता दिखाता है और दर्ज होता है। यह अभ्यास है, प्रमाणपत्र जाँच नहीं।\n\n")else "")+t("Use a clear, well-lit training area away from live equipment. Place three separate stations on the floor: equipment, a virtual hazard, and a green withdrawal point. Stay in a safe position and turn the phone; walking is not required.\n\n"+(if(module=="fire")"This scenario assumes an authorised role, suitable extinguisher and clear retreat path. Practise alarm → pin → aim → sweep → stop and withdraw as conditions worsen." else "The meter is simulated. Establish an exclusion barrier and outside attendant. Rescue readiness is unconfirmed, so entry must be refused.")+"\n\nPlacement spacing is for this simulation, not a real safety distance. This mission records virtual actions; it does not certify practical competence.",
                "चालू उपकरणों से दूर, खाली और रोशनी वाला प्रशिक्षण क्षेत्र चुनें। फ़र्श पर उपकरण, काल्पनिक खतरा और हरा वापसी बिंदु अलग रखें। सुरक्षित जगह पर रहकर फ़ोन घुमाएँ; चलना आवश्यक नहीं।\n\n"+(if(module=="fire")"इस दृश्य में अधिकृत भूमिका, उपयुक्त अग्निशामक और साफ वापसी मार्ग माना गया है। अलार्म → पिन → निशाना → स्वीप → स्थिति बिगड़ने पर रोकें और हटें।" else "मीटर काल्पनिक है। सीमा और बाहर परिचर रखें। बचाव तैयारी की पुष्टि नहीं है, इसलिए प्रवेश मना करें।")+"\n\nरखने की दूरी केवल सिमुलेशन के लिए है। यह वास्तविक कौशल प्रमाणपत्र नहीं देता।"))
            .setPositiveButton(t("Start in a clear area","खाली क्षेत्र में शुरू करें")){_,_->safe=true;persist(mission);if(active)startScene();render()}
            .setNegativeButton(t("Back","वापस")){_,_->finish()}.setCancelable(false).show()
    }
    private fun startScene(){if(camera&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.CAMERA),81)else scene.resume()}
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==81&&active&&safe)scene.resume()}
    override fun onResume(){super.onResume();active=true;if(!identity.isCurrentProfile()){finish();return};if(safe&&modalCount==0){scene.resume();render()}}
    override fun onWindowFocusChanged(focused:Boolean){super.onWindowFocusChanged(focused);if(!::scene.isInitialized)return;if(!focused){hideCoaching();cancelGesture();scene.pause()}else if(active&&safe&&modalCount==0){scene.resume();render()}}
    override fun onPause(){active=false;held=false;hideCoaching();mission.resetIncomplete();scene.pause();super.onPause()}
    override fun onDestroy(){scene.close();store.close();identity.close();super.onDestroy()}
    override fun onRetainNonConfigurationInstance():Any=Retained(identity.workerId,id,module,camera,mission.fork(),safe,coaching.fork())
    private fun eligible():Boolean {
        if(!active || !safe || modalCount>0 || saveFailed || !identity.isCurrentProfile())return false
        return scene.ready
    }
    private fun record(value:RoomMission,help:RoomCoaching=coaching)=JSONObject().put("id",id).put("workerId",identity.workerId).put("module",module)
        .put("mode",if(camera)"camera" else "screen").put("updatedAt",System.currentTimeMillis()).put("mission",value.toJson()).put("coaching",help.toJson())
    private fun persist(value:RoomMission,help:RoomCoaching=coaching):Boolean=try{store.save(record(value,help));true}catch(_:Exception){saveFailed=true;status.text=t("Could not save. Actions paused; use Options to retry saving.","सहेजा नहीं गया। क्रियाएँ रुकी हैं; विकल्प से फिर सहेजें।");false}
    private fun mutate(change:(RoomMission)->Boolean){
        val before=mission;val next=before.fork();change(next)
        val changed=next.events.size!=before.events.size || next.phase!=before.phase
        if(changed && !persist(next)){held=false;syncVisual();return}
        mission=next
        if(next.lastInterruption!=null && next.lastInterruption!=before.lastInterruption){
            status.text=if(coaching.recall&&!coaching.cueVisible(next.phase,SystemClock.elapsedRealtime()))t("Movement incomplete. Try again, or request a hint.","क्रिया अधूरी है। फिर प्रयास करें या संकेत माँगें।")else when(next.lastInterruption){
                "wrong-start-edge"->t("Start the sweep at either end of the highlighted base.","स्वीप चिह्नित आधार के किसी किनारे से शुरू करें।")
                "off-base"->t("Keep the aim on the base. Begin the movement again.","निशाना आधार पर रखें। फिर से शुरू करें।")
                "skipped-band"->t("Move smoothly across the whole base, without jumping over the middle.","बीच का भाग छोड़े बिना पूरे आधार पर धीरे खिसकाएँ।")
                "released"->t("Discharge released. Begin at either edge when ready.","डिस्चार्ज छूट गया। तैयार होने पर किसी किनारे से शुरू करें।")
                else->t("The view or gesture was interrupted. Settle the phone and begin again.","दृश्य या क्रिया बाधित हुई। फ़ोन स्थिर करके फिर शुरू करें।")
            };hintUntil=SystemClock.elapsedRealtime()+1400
        }
        if(before.phase!=next.phase){hideCoaching();hintUntil=0;scene.performHapticFeedback(HapticFeedbackConstants.CONFIRM);held=held&&next.phase=="WITHDRAW";render()}
        progress.progress=(next.overallProgress*100).toInt();syncVisual()
    }
    private fun cancelGesture(){held=false;control.isPressed=false;mission.resetIncomplete();syncVisual()}
    private fun release(){held=false;control.isPressed=false
        if(active&&safe&&modalCount==0&&!saveFailed&&identity.isCurrentProfile()&&mission.phase in listOf("SWEEP","WITHDRAW"))mutate{it.act("release",SystemClock.elapsedRealtime())}else mission.resetIncomplete()
        syncVisual();render()
    }
    private fun syncVisual(){scene.update(mission.phase,mission.progress,held,!coaching.recall||coaching.cueVisible(mission.phase,SystemClock.elapsedRealtime()))}
    private fun hideCoaching(){
        coaching.hideCue();cueButton.removeCallbacks(cueExpiry)
        if(coaching.recall){
            if(!camera||placementCount==3)prompt.text=recallPrompt(mission.phase)
            status.text=t("Recall practice · requested hints are recorded","याद करके अभ्यास · माँगे संकेत दर्ज होते हैं");hintUntil=0
            syncVisual()
        }
    }
    private fun requestCue(){
        if(!eligible() || mission.completed || !coaching.recall)return
        if(coaching.cueCount>=RoomCoaching.MAX_CUES){
            hideCoaching();modalCount++;cancelGesture();scene.pause()
            AlertDialog.Builder(this).setTitle(t("Continue with coaching","निर्देशों के साथ जारी रखें"))
                .setMessage(t("This attempt has reached its hint limit. Start a new guided rehearsal; your current record remains saved.","इस प्रयास में संकेत सीमा पूरी हुई। नया निर्देशित अभ्यास शुरू करें; मौजूदा रिकॉर्ड सहेजा रहेगा।"))
                .setPositiveButton(t("Guided rehearsal","निर्देशों के साथ अभ्यास")){_,_->startRehearsal(false)}
                .setNegativeButton(t("Keep practising","अभ्यास जारी रखें"),null).setOnDismissListener{dismissModal()}.show()
            return
        }
        val next=coaching.fork()
        if(!next.requestCue(mission.phase,SystemClock.elapsedRealtime()))return
        cancelGesture()
        // Persist help before exposing it. Failed storage must not create an unrecorded hint.
        if(!persist(mission,next)){render();return}
        coaching=next;render();cueButton.removeCallbacks(cueExpiry);cueButton.postDelayed(cueExpiry,7001)
    }
    private fun recallPrompt(phase:String)=when(phase){
        "ALARM"->t("Begin your fire response. Use the scene from memory.","आग पर अपनी प्रतिक्रिया शुरू करें। याद करके दृश्य में क्रिया करें।")
        "PIN"->t("Prepare the extinguisher for this scenario.","इस दृश्य के लिए अग्निशामक तैयार करें।")
        "AIM"->t("Set your aim before discharge.","डिस्चार्ज से पहले निशाना लगाएँ।")
        "SWEEP"->t("Demonstrate a controlled sweep across the base.","आधार पर नियंत्रित स्वीप करके दिखाएँ।")
        "WITHDRAW"->t("Conditions have worsened. Decide your response.","स्थिति बिगड़ गई है। अपनी प्रतिक्रिया तय करें।")
        "GAS_CHECK"->t("Begin the outside-only readiness check. The equipment is simulated.","बाहर रहकर तैयारी जाँच शुरू करें। उपकरण काल्पनिक हैं।")
        "BARRIER"->t("Protect this access point.","इस प्रवेश बिंदु को सुरक्षित करें।")
        "ATTENDANT"->t("Set up the outside support role.","बाहर सहायता की भूमिका तय करें।")
        "REFUSE"->t("Rescue readiness is still unconfirmed. Decide your response.","बचाव तैयारी की पुष्टि अभी नहीं है। अपनी प्रतिक्रिया तय करें।")
        else->t("Review what you remembered and where you needed help.","याद की गई क्रियाओं और ली गई सहायता की समीक्षा करें।")
    }
    private fun phaseName(phase:String)=when(phase){
        "ALARM"->t("Alarm","अलार्म");"PIN"->t("Pin preparation","पिन तैयारी");"AIM"->t("Base alignment","आधार पर निशाना")
        "SWEEP"->t("Controlled sweep","नियंत्रित स्वीप");"WITHDRAW"->t("Withdrawal","वापसी")
        "GAS_CHECK"->t("Simulated meter check","काल्पनिक मीटर जाँच");"BARRIER"->t("Exclusion barrier","प्रवेश अवरोध")
        "ATTENDANT"->t("Outside attendant","बाहर परिचर");else->t("Refuse unconfirmed entry","अपुष्ट प्रवेश मना करें")
    }
    private fun startRehearsal(recall:Boolean){
        if(!persist(mission))return
        startActivity(Intent(this,RoomMissionActivity::class.java).putExtra("moduleId",module).putExtra("camera",camera).putExtra("recall",recall));finish()
    }
    private fun primary(){
        when {
            saveFailed->{saveFailed=false;persist(mission);render()}
            mission.completed->debrief()
            !safe->briefing()
            camera&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED->startScene()
            camera&&scene.needsRetry->scene.retryCamera()
            placementCount<3->scene.place()
            mission.phase=="WITHDRAW"->release()
            else->if(coaching.recall)requestCue()else missionNotice(t("Do the action in the scene","दृश्य में क्रिया करें"),prompt.text.toString())
        }
    }
    private fun render(){
        lastPhase=mission.phase;lastPlaced=placementCount;lastTracked=tracking;lastCanPlace=scene.canPlace;lastRetry=scene.needsRetry
        title.text=(if(coaching.recall)t("RECALL · ","याद करके · ")else "")+t(if(camera)"ROOM AR · " else "SCREEN MISSION · ",if(camera)"कमरा AR · " else "स्क्रीन मिशन · ")+t(if(module=="fire")"Fire response" else "Confined space",if(module=="fire")"अग्नि प्रतिक्रिया" else "बंद स्थान")
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
        val cueVisible=coaching.cueVisible(phase,SystemClock.elapsedRealtime())
        if(coaching.recall && (!camera||placementCount==3) && !cueVisible)prompt.text=recallPrompt(phase)
        cueButton.text=if(coaching.cueCount>=RoomCoaching.MAX_CUES)t("Guided","निर्देशित")else if(cueVisible)t("Hint shown","संकेत चालू")else t("Hint","संकेत")
        cueButton.isEnabled=coaching.recall&&!mission.completed&&scene.ready&&!saveFailed&&(!cueVisible||coaching.cueCount>=RoomCoaching.MAX_CUES)
        cueButton.contentDescription=if(coaching.cueCount>=RoomCoaching.MAX_CUES)t("Start a new guided rehearsal","नया निर्देशित अभ्यास शुरू करें")else t("Reveal a coaching cue for seven seconds; recorded as help","सात सेकंड के लिए संकेत दिखाएँ; सहायता दर्ज होगी")
        control.text=when{
            saveFailed->t("Retry saving","फिर सहेजें")
            mission.completed->t("View mission debrief","मिशन समीक्षा देखें")
            camera&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED->t("Enable camera","कैमरा अनुमति दें")
            camera&&scene.needsRetry->t("Retry camera","कैमरा फिर चलाएँ")
            placementCount<3->if(scene.canPlace)t("Place station","स्थल रखें")else t("Scan for a surface","सतह स्कैन करें")
            phase=="SWEEP"&&camera->t("Hold to discharge","डिस्चार्ज के लिए दबाएँ")
            phase=="WITHDRAW"->t("Release discharge","डिस्चार्ज छोड़ें")
            else->if(coaching.recall)if(coaching.cueCount>=RoomCoaching.MAX_CUES)t("Guided rehearsal","निर्देशों के साथ अभ्यास")else t("Show hint · 7 seconds","संकेत दिखाएँ · 7 सेकंड")else t("How to do this action","यह क्रिया कैसे करें")
        }
        control.isEnabled=saveFailed || mission.completed || !camera || scene.needsRetry || checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED || placementCount>=3 || scene.canPlace
        control.actionRole(if(phase=="WITHDRAW")ActionRole.DANGER else if(phase=="SWEEP"&&camera)ActionRole.PRIMARY else ActionRole.CAMERA)
        progress.progress=(mission.overallProgress*100).toInt();syncVisual()
    }
    private fun debrief(){
        val details=mission.measurements.joinToString("\n"){"${it.kind}: ${it.durationMs} ms · ${it.samples} ${t("samples","नमूने")}"}
        val help=if(!coaching.recall)t("Guided rehearsal. Next, try the same actions without the target rings.","निर्देशों के साथ अभ्यास। अब चिह्नित घेरों के बिना यही क्रियाएँ करें।")
            else if(coaching.cues.isEmpty())t("No coaching hints requested in this attempt. This does not measure practical competence.","इस प्रयास में कोई संकेत नहीं माँगा गया। यह वास्तविक दक्षता का माप नहीं है।")
            else t("Rehearse these actions again:","इन क्रियाओं का फिर अभ्यास करें:")+"\n"+coaching.cues.groupingBy{it.phase}.eachCount().entries.joinToString("\n"){(phase,count)->"${phaseName(phase)} · $count ${t("hints","संकेत")}"}
        modalCount++;cancelGesture();scene.pause()
        AlertDialog.Builder(this).setTitle(t("Remember, then do · your review","याद करके करें · आपकी समीक्षा"))
            .setMessage(t("${mission.events.count{it.accepted}} accepted actions · ${if(camera)"camera AR" else "screen simulation"}\n$details\n\n$help\n\nVirtual practice saved on this device. No certificate or physical skill assessment.","${mission.events.count{it.accepted}} स्वीकृत क्रियाएँ · ${if(camera)"कैमरा AR" else "स्क्रीन सिमुलेशन"}\n$details\n\n$help\n\nकाल्पनिक अभ्यास सहेजा गया। कोई प्रमाणपत्र या वास्तविक कौशल जाँच नहीं।"))
            .setPositiveButton(t("Try without cues","संकेतों के बिना करें")){_,_->startRehearsal(true)}
            .setNeutralButton(t("Guided rehearsal","निर्देशों के साथ अभ्यास")){_,_->startRehearsal(false)}
            .setNegativeButton(t("Close","बंद करें"),null).setOnDismissListener{dismissModal()}.show()
    }
    private fun options(){
        hideCoaching()
        val items=arrayOf(t("Resume mission","मिशन जारी रखें"),t("Restart camera mission","कैमरा मिशन फिर शुरू करें"),t("Start separate screen mission","अलग स्क्रीन मिशन शुरू करें"),t("Text procedure alternative","लिखित प्रक्रिया विकल्प"),t("Saved mission records","सहेजे मिशन रिकॉर्ड"),t("Save & leave","सहेजें और लौटें"),t("Start recall challenge","याद करके अभ्यास शुरू करें"))
        // A modal must not accumulate aim or discharge progress behind it.
        modalCount++;held=false;mission.resetIncomplete();scene.pause()
        AlertDialog.Builder(this).setTitle(t("Mission options","मिशन विकल्प")).setItems(items){_,index->when(index){
            1,2->{if(persist(mission)){startActivity(Intent(this,RoomMissionActivity::class.java).putExtra("moduleId",module).putExtra("camera",index==1).putExtra("recall",coaching.recall));finish()}}
            3->{if(persist(mission)){startActivity(Intent(this,ProcedureActivity::class.java).putExtra("moduleId",module));finish()}}
            4->saved()
            5->{if(persist(mission))finish()}
            6->startRehearsal(true)
        }}.setOnDismissListener{dismissModal()}.show()
    }
    private fun dismissModal(){modalCount=(modalCount-1).coerceAtLeast(0);if(modalCount==0&&active&&!isFinishing&&safe){scene.resume();render()}}
    private fun missionNotice(title:String,message:String){modalCount++;held=false;mission.resetIncomplete();scene.pause();AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK",null).setOnDismissListener{dismissModal()}.show()}
    private fun saved(){val records=store.records();missionNotice(t("Saved missions · this learner","सहेजे मिशन · यह शिक्षार्थी"),if(records.isEmpty())t("No saved missions yet.","अभी कोई मिशन नहीं सहेजा गया।")else records.take(12).joinToString("\n\n"){r->val m=r.getJSONObject("mission");"${r.getString("module")} · ${r.getString("mode")} · ${m.getString("phase")}\n${if(r.optJSONObject("coaching")?.optString("mode")=="recall")t("Recall practice","याद करके अभ्यास")else t("Guided practice","निर्देशित अभ्यास")} · ${r.optJSONObject("coaching")?.optJSONArray("cues")?.length()?:0} ${t("hints","संकेत")}\n${t("Virtual actions only · no certificate","केवल काल्पनिक क्रियाएँ · प्रमाणपत्र नहीं")}"})}
}
