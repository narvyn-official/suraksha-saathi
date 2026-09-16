package com.narvyn.suraksha

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.view.*
import android.widget.*
import org.json.JSONObject
import java.util.UUID
import java.util.Locale

/** Action-led missions have a distinct local journal and never award an assessment certificate. */
class RoomMissionActivity:Activity() {
    private data class Retained(val worker:String,val id:String,val module:String,val camera:Boolean,val mission:RoomMission,val safe:Boolean,val coaching:RoomCoaching,val clockOffset:Long)
    private lateinit var identity:Store
    private lateinit var store:RoomMissionStore
    private lateinit var mission:RoomMission
    private lateinit var coaching:RoomCoaching
    private lateinit var cueButton:Button
    private lateinit var speakButton:Button
    private val backNavigation by lazy { AppBackNavigation(this){leavePractice()} }
    private val cueExpiry=Runnable{if(active&&!isFinishing){hideCoaching();render()}}
    private lateinit var scene:RoomMissionView
    private var id=UUID.randomUUID().toString()
    private var clockOffset=0L
    private var resumeNotice:String?=null
    private fun missionClock(raw:Long=SystemClock.elapsedRealtime())=Math.addExact(raw,clockOffset)
    private var module="fire";private var camera=true;private var explosionRisk=false;private var safe=false;private var active=false
    private var held=false;private var heldAt=0L;private var saveFailed=false
    private var modalCount=0
    private var learningVoice:TextToSpeech?=null
    private var voiceReady=false
    private var voiceInitialised=false
    private var autoVoice=true
    private var lastAutoVoiceKey:String?=null
    private var voiceErrorLanguage:String?=null
    private val autoVoiceNext=Runnable{narrateCurrentStep()}
    private var placementCount=0;private var tracking=false
    private lateinit var title:TextView;private lateinit var prompt:TextView;private lateinit var status:TextView
    private lateinit var control:Button;private lateinit var progress:ProgressBar
    private var hintUntil=0L
    private var lastPhase="";private var lastPlaced=-1;private var lastTracked=false;private var lastCanPlace=false;private var lastRetry=false
    private var lastPlacementLabel=""
    private fun t(en:String,hi:String)=if(identity.hi)hi else en
    override fun onCreate(state:Bundle?) {
        super.onCreate(state);identity=Store(this);store=RoomMissionStore(this,identity.workerId)
        autoVoice=getSharedPreferences("room-voice",MODE_PRIVATE).getBoolean("automatic",true)
        learningVoice=TextToSpeech(this){voiceReady=it==TextToSpeech.SUCCESS;voiceInitialised=true;runOnUiThread{if(::speakButton.isInitialized)scheduleNarration()}}
        learningVoice?.setOnUtteranceProgressListener(object:android.speech.tts.UtteranceProgressListener(){
            override fun onStart(id:String?){android.util.Log.i("RoomVoice","Offline instruction playback started")}
            override fun onDone(id:String?){android.util.Log.i("RoomVoice","Offline instruction playback completed")}
            @Deprecated("Legacy engine callback") override fun onError(id:String?){voiceError()}
            override fun onError(id:String?,errorCode:Int){voiceError()}
            private fun voiceError(){runOnUiThread{if(active&&!isFinishing)Toast.makeText(this@RoomMissionActivity,t("Speech could not play. Read the instruction or try again.","आवाज़ नहीं चली। निर्देश पढ़ें या फिर प्रयास करें।"),Toast.LENGTH_LONG).show()}}
        })
        module=intent.getStringExtra("moduleId")?.takeIf{it in listOf("fire","gas")}?:"fire";camera=intent.getBooleanExtra("camera",true);explosionRisk=module=="fire"&&intent.getBooleanExtra("explosionRisk",false)
        coaching=RoomCoaching(module,intent.getBooleanExtra("recall",false))
        val kept=lastNonConfigurationInstance as? Retained
        if(kept?.worker==identity.workerId && kept.module==module && kept.camera==camera && kept.coaching.recall==coaching.recall && kept.mission.explosionRisk==explosionRisk){mission=kept.mission.apply{resetIncomplete()};id=kept.id;safe=kept.safe;coaching=kept.coaching.apply{hideCue()};clockOffset=kept.clockOffset}else {
            mission=RoomMission(module,explosionRisk)
            val restoreId=state?.getString("roomAttemptId")?:intent.getStringExtra("resumeId")
            if(restoreId!=null)try{
                check(state==null||state.getString("roomWorker")==identity.workerId)
                val record=store.record(restoreId)?:error("Practice record is missing")
                check(record.getString("module")==module&&record.getString("mode")==if(camera)"camera"else"screen")
                val restored=RoomMission.restore(record.getJSONObject("mission"))
                val restoredCoaching=RoomCoaching.restore(record.getJSONObject("coaching"),module)
                check(restored.explosionRisk==explosionRisk&&restoredCoaching.recall==coaching.recall)
                val elapsed=SystemClock.elapsedRealtime();val floor=maxOf(elapsed,restored.nextElapsedTime,restoredCoaching.nextElapsedTime)
                check(floor<Long.MAX_VALUE-86_400_000L)
                clockOffset=floor-elapsed;mission=restored;coaching=restoredCoaching;id=restoreId
                resumeNotice=t("Saved actions restored. Confirm your training area and place the camera stations again. Incomplete gestures restart.","सहेजी क्रियाएँ वापस मिलीं। क्षेत्र की पुष्टि करके कैमरा स्थल फिर रखें। अधूरी क्रियाएँ फिर शुरू होंगी।")
            }catch(_:Exception){resumeNotice=t("This saved practice cannot be resumed. A separate new attempt will start; the earlier record is retained.","यह सहेजा अभ्यास जारी नहीं हो सकता। अलग नया प्रयास शुरू होगा; पुराना रिकॉर्ड रहेगा।")}
        }
        val root=column().apply{setBackgroundColor(Palette.canvas)}
        root.setOnApplyWindowInsetsListener{v,i->if(android.os.Build.VERSION.SDK_INT>=30){val b=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom)}else{v.setPadding(i.systemWindowInsetLeft,i.systemWindowInsetTop,i.systemWindowInsetRight,i.systemWindowInsetBottom)};i}
        val landscape=resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8))}
        header.addView(action("‹",false,ActionRole.NEUTRAL){leavePractice()}.apply{contentDescription=t("Save and return to learning","सहेजकर सीखने पर लौटें");textSize=26f;setPadding(0,0,0,0)},LinearLayout.LayoutParams(dp(48),dp(48)).apply{rightMargin=dp(8)})
        title=label("",18f,bold=true).apply{maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END;isAccessibilityHeading=true};header.addView(title,LinearLayout.LayoutParams(0,-2,1f))
        cueButton=action(t("Coach","मार्गदर्शन"),false,ActionRole.REVIEW){if(camera&&placementCount<3)placementHelp()else if(coaching.recall)requestCue()else showCoach()}
        val menu=action(t("Options","विकल्प"),false,ActionRole.NEUTRAL){options()}.apply{contentDescription=t("Options and saved missions","विकल्प और सहेजे मिशन");minHeight=dp(44);minimumHeight=dp(44)};header.addView(menu,LinearLayout.LayoutParams(-2,-2).apply{leftMargin=dp(8)})
        root.addView(header,LinearLayout.LayoutParams(-1,-2))
        scene=RoomMissionView(this,module,camera,identity.hi)
        val panel=column(12);prompt=label("",17f,bold=true);panel.add(prompt)
        status=label("",13f,Palette.muted);panel.add(status,top=4)
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;progressTintList=android.content.res.ColorStateList.valueOf(Palette.teal)};panel.add(progress,top=6)
        if(landscape){
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            row.addView(scene,LinearLayout.LayoutParams(0,-1,.64f))
            row.addView(paged(panel,identity.hi),LinearLayout.LayoutParams(0,-1,.36f))
            root.addView(row,LinearLayout.LayoutParams(-1,0,1f))
        }else{root.addView(scene,LinearLayout.LayoutParams(-1,0,1f));root.addView(capped(panel,.24f,180,fixed=true),LinearLayout.LayoutParams(-1,-2))}
        val toolsRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;isBaselineAligned=false;setPadding(dp(12),dp(4),dp(12),0)}
        speakButton=action(t("Speak instructions","निर्देश सुनें"),false,ActionRole.LEARN){speakInstructions()}.apply{tag="room-speak";textSize=14f}
        toolsRow.addView(speakButton,LinearLayout.LayoutParams(0,-2,1.15f).apply{rightMargin=dp(8)})
        cueButton.textSize=14f;cueButton.tag="room-help"
        toolsRow.addView(cueButton,LinearLayout.LayoutParams(0,-2,1f))
        root.addView(toolsRow,LinearLayout.LayoutParams(-1,-2))
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
            cueButton.isEnabled=!saveFailed&&!mission.completed&&if(camera&&placementCount<3)safe else if(coaching.recall)scene.ready&&(!coaching.cueVisible(mission.phase,missionClock())||coaching.cueCount>=RoomCoaching.MAX_CUES)else safe
            if(!img.tracked){held=false;mission.resetIncomplete();syncVisual()}
            if(status.text.toString()!=img.message && !saveFailed && (SystemClock.elapsedRealtime()>=hintUntil || !img.tracked))status.text=img.message
            if(lastPlaced!=placementCount || lastTracked!=tracking || lastCanPlace!=scene.canPlace || lastRetry!=scene.needsRetry || (placementCount<3&&lastPlacementLabel!=scene.placementControlLabel))render()
        }
        scene.onAction={action->if(eligible()){
            mutate{it.act(action,missionClock())}
            if(!saveFailed && mission.events.lastOrNull()?.accepted==false){
                status.text=if(explosionRisk&&action=="select-suitable-extinguisher")t("Explosion risk is announced. Evacuate without attempting discharge.","विस्फोट का खतरा बताया गया है। डिस्चार्ज किए बिना निकासी करें।")else RoomMissionChoices.feedback(action,identity.hi);hintUntil=SystemClock.elapsedRealtime()+6500
                if(!camera)scene.performHapticFeedback(HapticFeedbackConstants.REJECT)
            }
        }}
        scene.onRelease={if(!camera)release()}
        scene.onCancel={cancelGesture()}
        scene.onAim={x,y,pressed,at,fresh->
            if(eligible() && mission.phase in listOf("AIM","SWEEP")) {
                val valid=fresh && (!camera || !pressed || at>=heldAt)
                mutate{it.aim(x,y,pressed,missionClock(at),valid)}
            }else mission.resetIncomplete()
        }
        setContentView(root);backNavigation.enabled(true);render()
        if(!safe)briefing()
    }
    // Keep feedback changes from resizing the camera viewport or cancelling an active pointer.
    private fun capped(content:View,fraction:Float,maxDp:Int,fixed:Boolean=false)=object:PagedPanel(this,identity.hi){
        init{setContent(content)}
        override fun onMeasure(w:Int,h:Int){super.onMeasure(w,MeasureSpec.makeMeasureSpec(minOf(dp(maxDp),(MeasureSpec.getSize(h)*fraction).toInt()),if(fixed)MeasureSpec.EXACTLY else MeasureSpec.AT_MOST))}
    }
    private fun briefing(){
        val mode=if(coaching.recall)t("Recall practice: try the actions from memory. Requested hints are recorded.","याद करके अभ्यास करें। माँगे गए संकेत दर्ज होंगे।")else t("Guided practice: do each action in the scene. Coach explains the reason.","निर्देशित अभ्यास: दृश्य में क्रिया करें। मार्गदर्शन उसका कारण समझाता है।")
        val scenario=if(module=="fire")t("Virtual fire response: alarm, clear exit, an approved response, then assembly and reporting. Extinguisher use assumes a trained, authorised role.","काल्पनिक आग: अलार्म, खुला निकास, स्वीकृत प्रतिक्रिया, फिर एकत्र स्थल और सूचना। उपकरण उपयोग में प्रशिक्षित, अधिकृत भूमिका मानी गई है।")else t("Outside-only confined-space practice. The SIM meter cannot measure gas. Entry stays closed; rescue readiness is unconfirmed.","बाहर रहकर बंद स्थान का अभ्यास। SIM मीटर गैस नहीं माप सकता। प्रवेश बंद है; बचाव तैयारी अपुष्ट है।")
        PageDialogBuilder(this).setTitle(t("Ready for your practice?","अभ्यास के लिए तैयार?"))
            .setMessage((resumeNotice?.plus("\n\n")?:"")+t("Use a clear, well-lit area away from live machinery. Stay in one safe position; no walking toward hazards.\n\n","चालू मशीनों से दूर खाली, उजले क्षेत्र में रहें। सुरक्षित जगह पर रहकर अभ्यास करें; खतरे की ओर न चलें।\n\n")+mode+"\n\n"+scenario+"\n\n"+t("Virtual actions only. Markers are not real exits or safety distances; this practice does not issue a certificate.","केवल काल्पनिक क्रियाएँ। चिह्न वास्तविक निकास या सुरक्षा दूरी नहीं हैं; यह अभ्यास प्रमाणपत्र नहीं देता।"))
            .setPositiveButton(t("Start in a clear area","खाली क्षेत्र में शुरू करें")){_,_->safe=true;persist(mission);if(active)startScene();render()}
            .setNeutralButton(t("Setup guide","तैयारी सीखें")){_,_->setupGuide()}
            .setNegativeButton(t("Back","वापस")){_,_->finish()}.setCancelable(false).show()
    }
    private fun speakLearning(text:String):String? {
        if(!active||!identity.isCurrentProfile())return t("Return to this practice to listen.","सुनने के लिए इस अभ्यास पर लौटें।")
        val engine=learningVoice
        if(engine==null||!voiceReady)return if(!voiceInitialised)t("Voice is loading. Tap Listen again.","आवाज़ तैयार हो रही है। फिर सुनें दबाएँ।")else t("Android speech is unavailable. Continue reading; the text works offline.","Android आवाज़ उपलब्ध नहीं है। पढ़कर जारी रखें; पाठ ऑफ़लाइन चलता है।")
        val locale=if(identity.hi)Locale.forLanguageTag("hi-IN")else Locale.ENGLISH
        val voice=engine.voices?.firstOrNull{it.locale.language==locale.language&&!it.isNetworkConnectionRequired&&TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()}
            ?:return t("Install an offline voice for this language in Android speech settings. The text is available offline.","Android आवाज़ सेटिंग में इस भाषा की ऑफ़लाइन आवाज़ स्थापित करें। पाठ ऑफ़लाइन उपलब्ध है।")
        if(engine.setVoice(voice)!=TextToSpeech.SUCCESS)return t("The offline voice could not load. Continue reading.","ऑफ़लाइन आवाज़ लोड नहीं हुई। पढ़कर जारी रखें।")
        engine.setSpeechRate(.9f)
        android.util.Log.i("RoomVoice","Selected offline voice language=${voice.locale.language}")
        return if(engine.speak(text,TextToSpeech.QUEUE_FLUSH,null,"room-coach")==TextToSpeech.ERROR)t("Voice could not start. Continue reading.","आवाज़ शुरू नहीं हुई। पढ़कर जारी रखें।")else null
    }
    private fun learningPages(title:String,pages:List<RoomLearningDialog.Page>,next:List<RoomLearningDialog.Next> = emptyList(),autoSpeak:Boolean=autoVoice) {
        if(!active||isFinishing||!identity.isCurrentProfile())return
        modalCount++;speakButton.removeCallbacks(autoVoiceNext);learningVoice?.stop();cancelGesture();scene.pause()
        RoomLearningDialog.show(this,title,pages,identity.hi,{
            learningVoice?.stop();dismissModal()
            if(!safe&&active&&!isFinishing&&!isDestroyed)briefing()
        },::speakLearning,next,{learningVoice?.stop()},autoSpeak)
    }
    @Deprecated("Android 10–12 compatibility") override fun onBackPressed(){leavePractice()}
    private fun leavePractice(){
        if(popContentPage())return
        if(!active||isFinishing||!identity.isCurrentProfile())return
        if(!safe||mission.completed){if(!safe||persist(mission))finish();return}
        modalCount++;learningVoice?.stop();hideCoaching();cancelGesture();scene.pause()
        val dialog=PageDialogBuilder(this).setTitle(t("Save and return?","सहेजकर लौटें?"))
            .setMessage(t("Completed actions stay on this phone. Resume this attempt from My record; camera stations will need placing again.","पूरी क्रियाएँ इस फ़ोन पर रहेंगी। मेरा रिकॉर्ड से यह प्रयास जारी करें; कैमरा स्थल फिर रखने होंगे।"))
            .setNegativeButton(t("Keep practising","अभ्यास जारी रखें"),null)
            .setPositiveButton(t("Save & return","सहेजें और लौटें"),null).create()
        dialog.setOnShowListener{dialog.getButton(-1).setOnClickListener{if(persist(mission)){dialog.dismiss();finish()}else dialog.window?.decorView?.findViewWithTag<TextView>("paged-dialog-message")?.setText(status.text)}}
        dialog.setOnDismissListener{dismissModal()};dialog.show()
    }
    private fun placementHelp(){
        if(!active||!safe||saveFailed)return
        val lastStatus=status.text.toString()
        learningPages(t("Placement help","स्थल रखने में मदद"),listOf(
            RoomLearningDialog.Page(t("Scan, aim, then place","स्कैन, निशाना, फिर रखें"),t("Scanning runs automatically while the camera is open. Gently move the phone sideways over a well-lit, textured floor or mat. Point 0.6–3 m ahead. Tap inside the teal outline, hold steady, then use Place station. Grey Place station means the point is not ready yet.","कैमरा खुला होने पर स्कैन अपने आप चलता है। उजले, पैटर्न वाले फ़र्श या चटाई पर फ़ोन धीरे दाएँ-बाएँ हिलाएँ। 0.6–3 मीटर आगे निशाना रखें। हरी रूपरेखा में छुएँ, स्थिर रखें, फिर स्थल रखें दबाएँ। धूसर बटन का अर्थ बिंदु अभी तैयार नहीं है।"),t("Last camera feedback: ","कैमरे की पिछली सूचना: ")+lastStatus),
            RoomLearningDialog.Page(t("Give every station room","हर स्थल को जगह दें"),t("Keep at least 20 cm between virtual footprints, on the same floor level. Aim farther from a placed model if the preview is rejected. An amber footprint can extend beyond the mapped patch; it is not a real obstacle or clearance check. Continue scanning recentres the aim without deleting placed stations.","एक ही फ़र्श स्तर पर काल्पनिक रूपरेखाओं के बीच कम से कम 20 सेमी रखें। पूर्वावलोकन अस्वीकृत हो तो रखे मॉडल से दूर निशाना करें। पीली रूपरेखा मिली सतह से बाहर हो सकती है; यह वास्तविक बाधा या खाली जगह की जाँच नहीं। स्कैन जारी रखें से निशाना बीच में आएगा, रखे स्थल नहीं मिटेंगे।"))
        ),listOf(RoomLearningDialog.Next(t("Continue scanning","स्कैन जारी रखें")){scene.recenterPlacement()}))
    }
    private fun currentInstruction():String {
        val placing=camera&&placementCount<3
        return when{
            placing->prompt.text.toString()+". "+t("Move the phone sideways slowly over a textured surface. Tap inside the teal outline, hold steady, then tap Place station. Keep virtual footprints twenty centimetres apart. Do not walk backwards or approach real hazards.","पैटर्न वाली सतह पर फ़ोन धीरे दाएँ-बाएँ हिलाएँ। हरी रूपरेखा के अंदर छुएँ, स्थिर रखें, फिर स्थल रखें दबाएँ। काल्पनिक रूपरेखाओं में बीस सेंटीमीटर अंतर रखें। पीछे न चलें और वास्तविक खतरे के पास न जाएँ।")
            mission.completed->prompt.text.toString()
            coaching.recall->recallPrompt(mission.phase)+". "+t("This reads the task only. Use Hint if you need recorded help.","यह केवल कार्य पढ़ता है। दर्ज सहायता के लिए संकेत दबाएँ।")
            else->prompt.text.toString()+"\n\n"+t("Do not: ","यह न करें: ")+RoomLearning.doNot(mission.phase).local(identity.hi)+"\n\n"+RoomLearning.forPhase(mission.phase,explosionRisk).why.local(identity.hi)
        }
    }
    private fun speakInstructions(){
        learningPages(t("Speak instructions","निर्देश सुनें"),listOf(RoomLearningDialog.Page(t("Your current step","आपकी मौजूदा क्रिया"),currentInstruction())),autoSpeak=true)
    }
    private fun scheduleNarration(){
        speakButton.removeCallbacks(autoVoiceNext)
        if(autoVoice&&active&&safe&&modalCount==0&&!saveFailed&&!isFinishing)speakButton.postDelayed(autoVoiceNext,300)
    }
    private fun narrateCurrentStep(){
        if(!autoVoice||!active||!safe||modalCount>0||saveFailed||isFinishing||!identity.isCurrentProfile()||!voiceInitialised)return
        val key="${identity.hi}:$module:${coaching.recall}:"+if(camera&&placementCount<3)"place-$placementCount"else mission.phase
        if(key==lastAutoVoiceKey)return
        lastAutoVoiceKey=key
        val error=speakLearning(currentInstruction())
        if(error!=null&&voiceErrorLanguage!=identity.hi.toString()){
            voiceErrorLanguage=identity.hi.toString();Toast.makeText(this,error,Toast.LENGTH_LONG).show()
        }
    }
    private fun toggleAutomaticVoice(){
        autoVoice=!autoVoice;getSharedPreferences("room-voice",MODE_PRIVATE).edit().putBoolean("automatic",autoVoice).apply()
        if(!autoVoice){learningVoice?.stop();speakButton.removeCallbacks(autoVoiceNext)}else{lastAutoVoiceKey=null;voiceErrorLanguage=null;scheduleNarration()}
        Toast.makeText(this,if(autoVoice)t("Automatic voice on","अपने आप आवाज़ चालू")else t("Automatic voice muted","अपने आप आवाज़ बंद"),Toast.LENGTH_SHORT).show()
    }
    private fun fireGuide(){if(!coaching.recall&&!saveFailed)learningPages(t("Fire type & agent guide","आग का प्रकार और माध्यम"),FireResponseLearning.pages(identity.hi))}
    private fun setupGuide(){
        learningPages(t("Set up your AR space","AR क्षेत्र तैयार करें"),listOf(
            RoomLearningDialog.Page(t("1 · Find a surface","1 · सतह खोजें"),t("Point down at a textured floor 1–2 metres ahead. Move the phone sideways 20–30 cm without walking. A patterned mat or newspaper helps on glossy floors. The teal outline marks a detected surface.","1–2 मीटर आगे पैटर्न वाले फ़र्श पर नीचे देखें। बिना चले फ़ोन 20–30 सेमी दाएँ-बाएँ हिलाएँ। चमकीले फ़र्श पर चटाई या अखबार मदद करता है। हरी रेखा मिली सतह दिखाती है।")),
            RoomLearningDialog.Page(t("2 · Choose and place","2 · चुनें और रखें"),t("Tap well inside the teal surface outline, or aim the centre crosshair there. Hold steady, then press Place station. An amber footprint means the virtual scene extends beyond the mapped patch. It does not measure real clearance.","हरी सतह रेखा के अंदर छुएँ या बीच का निशाना वहाँ रखें। स्थिर रखें, फिर स्थल रखें दबाएँ। पीली रूपरेखा का अर्थ है कि काल्पनिक दृश्य मिली सतह से बाहर है। यह वास्तविक खाली जगह नहीं मापता।")),
            RoomLearningDialog.Page(t("3 · Make three stations","3 · तीन स्थल बनाएँ"),t("Place equipment, then the virtual hazard, then a separate green withdrawal point on the same floor level. Keep the green point away from the hazard. Turn the phone to interact; do not walk into the scene. These distances are layout rules for the simulation.","एक ही फ़र्श पर उपकरण, फिर काल्पनिक खतरा, फिर अलग हरा वापसी बिंदु रखें। हरा बिंदु खतरे से दूर रखें। फ़ोन घुमाकर क्रिया करें; दृश्य में चलकर न जाएँ। दूरियाँ केवल सिमुलेशन की व्यवस्था हैं।"),t("Keep 20 cm between virtual footprints. All three are virtual stations, not detection of actual equipment or exits.","काल्पनिक रूपरेखाओं के बीच 20 सेमी रखें। तीनों काल्पनिक स्थल हैं; वास्तविक उपकरण या निकास की पहचान नहीं।"),Palette.amberBg)
        ))
    }
    private fun showCoach(){
        if(coaching.recall||mission.completed||saveFailed)return
        if(camera&&placementCount<3){placementHelp();return}
        val lesson=RoomLearning.forPhase(mission.phase,explosionRisk)
        learningPages(t("Do it. Understand it.","करें। समझें।"),listOf(
            RoomLearningDialog.Page(lesson.title.local(identity.hi),prompt.text.toString()+"\n\n"+t("Do not: ","यह न करें: ")+RoomLearning.doNot(mission.phase).local(identity.hi)),
            RoomLearningDialog.Page(t("Why this matters","यह क्यों जरूरी है"),lesson.why.local(identity.hi),t("Explain in your own words: ","अपने शब्दों में समझाएँ: ")+lesson.reflect.local(identity.hi),Palette.soft)
        ),if(module=="fire")listOf(RoomLearningDialog.Next(t("Fire type & agent guide","आग का प्रकार और माध्यम")){fireGuide()})else emptyList())
    }
    private fun startScene(){if(camera&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.CAMERA),81)else scene.resume()}
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==81&&active&&safe)scene.resume()}
    override fun onResume(){super.onResume();active=true;if(!identity.isCurrentProfile()){finish();return};if(safe&&modalCount==0){scene.resume();render()}}
    override fun onWindowFocusChanged(focused:Boolean){super.onWindowFocusChanged(focused);if(!::scene.isInitialized)return;if(!focused){hideCoaching();cancelGesture();scene.pause()}else if(active&&safe&&modalCount==0){scene.resume();render()}}
    override fun onPause(){active=false;speakButton.removeCallbacks(autoVoiceNext);held=false;learningVoice?.stop();hideCoaching();mission.resetIncomplete();scene.pause();super.onPause()}
    override fun onDestroy(){backNavigation.close();learningVoice?.shutdown();learningVoice=null;scene.close();store.close();identity.close();super.onDestroy()}
    override fun onSaveInstanceState(out:Bundle){out.putString("roomAttemptId",id);out.putString("roomWorker",identity.workerId);super.onSaveInstanceState(out)}
    override fun onRetainNonConfigurationInstance():Any=Retained(identity.workerId,id,module,camera,mission.fork(),safe,coaching.fork(),clockOffset)
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
            status.text=if(coaching.recall&&!coaching.cueVisible(next.phase,missionClock()))t("Movement incomplete. Try again, or request a hint.","क्रिया अधूरी है। फिर प्रयास करें या संकेत माँगें।")else when(next.lastInterruption){
                "wrong-start-edge"->t("Start the sweep at either end of the highlighted base.","स्वीप चिह्नित आधार के किसी किनारे से शुरू करें।")
                "off-base"->t("Keep the aim on the base. Begin the movement again.","निशाना आधार पर रखें। फिर से शुरू करें।")
                "skipped-band"->t("Move smoothly across the whole base, without jumping over the middle.","बीच का भाग छोड़े बिना पूरे आधार पर धीरे खिसकाएँ।")
                "released"->t("Discharge released. Begin at either edge when ready.","डिस्चार्ज छूट गया। तैयार होने पर किसी किनारे से शुरू करें।")
                else->t("The view or gesture was interrupted. Settle the phone and begin again.","दृश्य या क्रिया बाधित हुई। फ़ोन स्थिर करके फिर शुरू करें।")
            };hintUntil=SystemClock.elapsedRealtime()+1400
        }
        if(before.phase!=next.phase){learningVoice?.stop();hideCoaching();hintUntil=0;if(!camera)scene.performHapticFeedback(HapticFeedbackConstants.CONFIRM);held=held&&next.phase=="WITHDRAW";render()}
        progress.progress=(next.overallProgress*100).toInt();syncVisual()
    }
    private fun cancelGesture(){held=false;control.isPressed=false;mission.resetIncomplete();syncVisual()}
    private fun release(){held=false;control.isPressed=false
        if(active&&safe&&modalCount==0&&!saveFailed&&identity.isCurrentProfile()&&mission.phase in listOf("SWEEP","WITHDRAW"))mutate{it.act("release",missionClock())}else mission.resetIncomplete()
        syncVisual();render()
    }
    private fun syncVisual(){scene.update(mission.phase,mission.progress,held,!coaching.recall||coaching.cueVisible(mission.phase,missionClock()),explosionRisk)}
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
            PageDialogBuilder(this).setTitle(t("Continue with coaching","निर्देशों के साथ जारी रखें"))
                .setMessage(t("This attempt has reached its hint limit. Start a new guided rehearsal; your current record remains saved.","इस प्रयास में संकेत सीमा पूरी हुई। नया निर्देशित अभ्यास शुरू करें; मौजूदा रिकॉर्ड सहेजा रहेगा।"))
                .setPositiveButton(t("Guided rehearsal","निर्देशों के साथ अभ्यास")){_,_->startRehearsal(false)}
                .setNegativeButton(t("Keep practising","अभ्यास जारी रखें"),null).setOnDismissListener{dismissModal()}.show()
            return
        }
        val next=coaching.fork()
        if(!next.requestCue(mission.phase,missionClock()))return
        cancelGesture()
        // Persist help before exposing it. Failed storage must not create an unrecorded hint.
        if(!persist(mission,next)){render();return}
        coaching=next;render();cueButton.removeCallbacks(cueExpiry);cueButton.postDelayed(cueExpiry,7001)
    }
    private fun recallPrompt(phase:String)=when(phase){
        "EXIT"->t("Find the usable exit in this simulated scene.","काल्पनिक दृश्य में उपयोग योग्य निकास खोजें।")
        "EQUIPMENT"->t(if(explosionRisk)"Announced explosion risk. Choose your response."else"Choose your response for the authorised-role scenario.",if(explosionRisk)"विस्फोट का खतरा बताया गया है। प्रतिक्रिया चुनें।"else"अधिकृत भूमिका के दृश्य में प्रतिक्रिया चुनें।")
        "EVACUATE"->t("Select your evacuation route from the scene.","दृश्य से अपना निकासी मार्ग चुनें।")
        "ASSEMBLY"->t("Choose what happens after evacuation.","निकासी के बाद की क्रिया चुनें।")
        "REPORT"->t("A colleague is missing at roll call. Respond.","उपस्थिति में एक साथी लापता है। प्रतिक्रिया दें।")
        "PPE"->t("Choose protection for your outside-only role. Entry remains closed.","केवल बाहरी भूमिका की सुरक्षा चुनें। प्रवेश बंद है।")
        "COMMUNICATE"->t("Prepare communication with the outside attendant.","बाहर परिचर से संपर्क तैयार करें।")
        "ACKNOWLEDGE"->t("The radio reply includes a shared stop signal. Decide what to do next.","रेडियो उत्तर में रुकने का संकेत है। अगली क्रिया चुनें।")
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
        "EXIT"->t("Exit recognition","निकास पहचान");"EQUIPMENT"->t("Response selection","प्रतिक्रिया चयन")
        "EVACUATE"->t("Evacuation route","निकासी मार्ग");"ASSEMBLY"->t("Assembly accountability","एकत्र उपस्थिति")
        "REPORT"->t("Missing-person reporting","लापता व्यक्ति की सूचना");"PPE"->t("Outside-role PPE","बाहरी भूमिका की सुरक्षा")
        "COMMUNICATE"->t("Buddy contact","साथी संपर्क");"ACKNOWLEDGE"->t("Reply and stop signal","उत्तर व रुकने का संकेत")
        "ALARM"->t("Alarm","अलार्म");"PIN"->t("Pin preparation","पिन तैयारी");"AIM"->t("Base alignment","आधार पर निशाना")
        "SWEEP"->t("Controlled sweep","नियंत्रित स्वीप");"WITHDRAW"->t("Withdrawal","वापसी")
        "GAS_CHECK"->t("Simulated meter check","काल्पनिक मीटर जाँच");"BARRIER"->t("Exclusion barrier","प्रवेश अवरोध")
        "ATTENDANT"->t("Outside attendant","बाहर परिचर");else->t("Refuse unconfirmed entry","अपुष्ट प्रवेश मना करें")
    }
    private fun startRehearsal(recall:Boolean){
        if(!active||!identity.isCurrentProfile()||!persist(mission))return
        startActivity(Intent(this,RoomMissionActivity::class.java).putExtra("moduleId",module).putExtra("camera",camera).putExtra("recall",recall).putExtra("explosionRisk",explosionRisk));finish()
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
            else->if(coaching.recall)requestCue()else showCoach()
        }
    }
    private fun render(){
        lastPhase=mission.phase;lastPlaced=placementCount;lastTracked=tracking;lastCanPlace=scene.canPlace;lastRetry=scene.needsRetry
        lastPlacementLabel=scene.placementControlLabel
        title.text=(if(coaching.recall)t("RECALL · ","याद करके · ")else "")+t(if(camera)"ROOM AR · " else "SCREEN MISSION · ",if(camera)"कमरा AR · " else "स्क्रीन मिशन · ")+t(if(module=="fire")"Fire response" else "Confined space",if(module=="fire")"अग्नि प्रतिक्रिया" else "बंद स्थान")
        val phase=mission.phase
        prompt.text=if(camera&&placementCount<3)t(listOf("1 / 3 · Place the equipment station","2 / 3 · Place the virtual hazard","3 / 3 · Mark a clear withdrawal point")[placementCount],listOf("1 / 3 · उपकरण स्थल रखें","2 / 3 · काल्पनिक खतरा रखें","3 / 3 · खाली वापसी बिंदु रखें")[placementCount])else RoomMissionChoices.instruction(phase,identity.hi)?:when(phase){
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
        if(explosionRisk && phase=="EQUIPMENT")prompt.text=t("Scenario warning: explosion risk has been announced. Select evacuation only. Do not attempt extinguisher use.","दृश्य चेतावनी: विस्फोट का खतरा बताया गया है। केवल निकासी चुनें। अग्निशामक उपयोग न करें।")
        val cueVisible=coaching.cueVisible(phase,missionClock())
        if(coaching.recall && (!camera||placementCount==3) && !cueVisible && !(explosionRisk&&phase=="EQUIPMENT"))prompt.text=recallPrompt(phase)
        cueButton.text=if(camera&&placementCount<3)t("Placement help","स्थल रखने में मदद")else if(!coaching.recall)t("Coach","मार्गदर्शन")else if(coaching.cueCount>=RoomCoaching.MAX_CUES)t("Guided","निर्देशित")else if(cueVisible)t("Hint shown","संकेत चालू")else t("Hint","संकेत")
        cueButton.isEnabled=!mission.completed&&!saveFailed&&if(camera&&placementCount<3)safe else if(coaching.recall)scene.ready&&(!cueVisible||coaching.cueCount>=RoomCoaching.MAX_CUES)else safe
        speakButton.isEnabled=safe&&!saveFailed
        cueButton.contentDescription=if(camera&&placementCount<3)t("Placement help: scanning and station spacing","स्थल रखने में मदद: स्कैन और अंतर")else if(!coaching.recall)t("Step guidance and why it matters","क्रिया का मार्गदर्शन और उसका कारण")else if(coaching.cueCount>=RoomCoaching.MAX_CUES)t("Start a new guided rehearsal","नया निर्देशित अभ्यास शुरू करें")else t("Reveal a coaching cue for seven seconds; recorded as help","सात सेकंड के लिए संकेत दिखाएँ; सहायता दर्ज होगी")
        control.text=when{
            saveFailed->t("Retry saving","फिर सहेजें")
            mission.completed->t("View mission debrief","मिशन समीक्षा देखें")
            camera&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED->t("Enable camera","कैमरा अनुमति दें")
            camera&&scene.needsRetry->t("Retry camera","कैमरा फिर चलाएँ")
            placementCount<3->t("Place station","स्थल रखें")
            phase=="SWEEP"&&camera->t("Hold to discharge","डिस्चार्ज के लिए दबाएँ")
            phase=="WITHDRAW"->t("Release discharge","डिस्चार्ज छोड़ें")
            else->if(coaching.recall)if(coaching.cueCount>=RoomCoaching.MAX_CUES)t("Guided rehearsal","निर्देशों के साथ अभ्यास")else t("Show hint · 7 seconds","संकेत दिखाएँ · 7 सेकंड")else t("How to do this action","यह क्रिया कैसे करें")
        }
        control.isEnabled=saveFailed || mission.completed || !camera || scene.needsRetry || checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED || placementCount>=3 || scene.canPlace
        control.actionRole(if(phase=="WITHDRAW")ActionRole.DANGER else if(phase=="SWEEP"&&camera)ActionRole.PRIMARY else ActionRole.CAMERA)
        progress.progress=(mission.overallProgress*100).toInt();syncVisual();scheduleNarration()
    }
    private fun debrief(){
        val reviews=RoomLearning.review(mission,coaching)
        val priorities=RoomLearning.priorities(reviews)
        val focus=if(priorities.isEmpty())t("Try the sequence again without cues, then explain why you stopped or continued.","क्रम को बिना संकेत फिर करें, फिर समझाएँ कि आप रुके या आगे क्यों बढ़े।")else t("Rehearse these actions again:","इन क्रियाओं का फिर अभ्यास करें:")+"\n"+priorities.joinToString("\n"){RoomLearning.forPhase(it.phase,explosionRisk).title.local(identity.hi)}
        val pages=mutableListOf(RoomLearningDialog.Page(t("Your practice, replayed","आपके अभ्यास की समीक्षा"),
            t("${mission.events.count{it.accepted}} recorded accepted actions · ${mission.events.count{!it.accepted}} choices to revisit · ${coaching.cueCount} requested hints.\n\n$focus","${mission.events.count{it.accepted}} दर्ज स्वीकृत क्रियाएँ · ${mission.events.count{!it.accepted}} दोहराने योग्य विकल्प · ${coaching.cueCount} माँगे संकेत।\n\n$focus"),
            t("${if(camera)"Camera-mode practice" else "Screen simulation"}. This record does not certify physical skill.","${if(camera)"कैमरा-मोड अभ्यास" else "स्क्रीन सिमुलेशन"}। यह रिकॉर्ड वास्तविक कौशल प्रमाणित नहीं करता।"),if(priorities.isEmpty())Palette.tealBg else Palette.amberBg))
        reviews.forEach { review ->
            val lesson=RoomLearning.forPhase(review.phase,explosionRisk)
            val mistakes=review.events.filter{!it.accepted}.distinctBy{it.id}.take(2).joinToString("\n") { event ->
                if(event.id=="select-suitable-extinguisher"&&explosionRisk)lesson.why.local(identity.hi)
                else RoomMissionChoices.feedback(event.id,identity.hi)
            }
            val recorded=t("${review.events.count{it.accepted}} accepted actions · ${review.corrections} corrections · ${review.hints} hints","${review.events.count{it.accepted}} स्वीकृत क्रियाएँ · ${review.corrections} सुधार · ${review.hints} संकेत")
            pages.add(RoomLearningDialog.Page(lesson.title.local(identity.hi),recorded+"\n\n"+(if(mistakes.isBlank())lesson.why.local(identity.hi)else mistakes),lesson.reflect.local(identity.hi),if(review.needsPractice)Palette.amberBg else Palette.tealBg))
        }
        pages.add(RoomLearningDialog.Page(t("Bring it back from memory","याद करके फिर करें"),t("Leave a gap before repeating the actions. Your offline review queue suggests when to return and which decisions deserve more practice. These are learning suggestions, not certification deadlines.","क्रियाओं को दोहराने से पहले अंतर रखें। ऑफ़लाइन सूची लौटने का समय और अभ्यास योग्य निर्णय सुझाती है। ये सीखने के सुझाव हैं, प्रमाणपत्र की समय-सीमा नहीं।"),t("What would change your response in a different situation?","अलग स्थिति में आपकी प्रतिक्रिया कैसे बदलेगी?"),Palette.soft))
        val next=mutableListOf(RoomLearningDialog.Next(t("Try without cues","संकेतों के बिना करें")){startRehearsal(true)},RoomLearningDialog.Next(t("Guided rehearsal","निर्देशों के साथ अभ्यास")){startRehearsal(false)})
        if(module=="fire")next.add(RoomLearningDialog.Next(t("Try changed conditions","बदली स्थिति आज़माएँ")){
            if(active&&identity.isCurrentProfile()&&persist(mission)){
                startActivity(Intent(this,RoomMissionActivity::class.java).putExtra("moduleId",module).putExtra("camera",camera).putExtra("recall",true).putExtra("explosionRisk",!explosionRisk));finish()
            }
        })
        learningPages(t("Your decision replay","आपके निर्णयों की समीक्षा"),pages,next)
    }
    private fun options(){
        learningVoice?.stop();speakButton.removeCallbacks(autoVoiceNext);hideCoaching()
        val items=arrayOf(t("Resume mission","मिशन जारी रखें"),t("Restart camera mission","कैमरा मिशन फिर शुरू करें"),t("Start separate screen mission","अलग स्क्रीन मिशन शुरू करें"),t("Text procedure alternative","लिखित प्रक्रिया विकल्प"),t("Saved mission records","सहेजे मिशन रिकॉर्ड"),t("Save & leave","सहेजें और लौटें"),t("Start recall challenge","याद करके अभ्यास शुरू करें"),t("Camera diagnostics","कैमरा स्थिति")) + if(module=="fire"&&!coaching.recall)arrayOf(t("Fire type & agent guide","आग का प्रकार और माध्यम"))else emptyArray()
        val choices=items+if(autoVoice)t("Mute automatic instructions","अपने आप निर्देश बोलना बंद करें")else t("Enable automatic instructions","अपने आप निर्देश बोलना चालू करें")
        // A modal must not accumulate aim or discharge progress behind it.
        modalCount++;held=false;mission.resetIncomplete();scene.pause()
        PageDialogBuilder(this).setTitle(t("Mission options","मिशन विकल्प")).setItems(choices){_,index->if(index==items.size)toggleAutomaticVoice()else when(index){
            1,2->{if(persist(mission)){startActivity(Intent(this,RoomMissionActivity::class.java).putExtra("moduleId",module).putExtra("camera",index==1).putExtra("recall",coaching.recall).putExtra("explosionRisk",explosionRisk));finish()}}
            3->{if(persist(mission)){startActivity(Intent(this,ProcedureActivity::class.java).putExtra("moduleId",module));finish()}}
            4->saved()
            5->{if(persist(mission))finish()}
            6->startRehearsal(true)
            7->missionNotice(t("Camera diagnostics","कैमरा स्थिति"),scene.diagnosticSummary())
            8->fireGuide()
        }}.setOnDismissListener{dismissModal()}.show()
    }
    private fun dismissModal(){modalCount=(modalCount-1).coerceAtLeast(0);if(modalCount==0&&active&&!isFinishing&&safe){scene.resume();render()}}
    private fun missionNotice(title:String,message:String){learningVoice?.stop();speakButton.removeCallbacks(autoVoiceNext);modalCount++;held=false;mission.resetIncomplete();scene.pause();PageDialogBuilder(this).setTitle(title).setMessage(message).setPositiveButton("OK",null).setOnDismissListener{dismissModal()}.show()}
    private fun saved(){val records=store.records();missionNotice(t("Saved missions · this learner","सहेजे मिशन · यह शिक्षार्थी"),if(records.isEmpty())t("No saved missions yet.","अभी कोई मिशन नहीं सहेजा गया।")else records.take(12).joinToString("\n\n"){r->val m=r.getJSONObject("mission");"${r.getString("module")} · ${r.getString("mode")} · ${m.getString("phase")}\n${if(r.optJSONObject("coaching")?.optString("mode")=="recall")t("Recall practice","याद करके अभ्यास")else t("Guided practice","निर्देशित अभ्यास")} · ${r.optJSONObject("coaching")?.optJSONArray("cues")?.length()?:0} ${t("hints","संकेत")}\n${t("Virtual actions only · no certificate","केवल काल्पनिक क्रियाएँ · प्रमाणपत्र नहीं")}"})}
}
