package com.narvyn.suraksha

import android.app.*
import android.os.Bundle
import android.content.*
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.view.*
import android.widget.*
import com.google.ar.core.ArCoreApk
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.integration.android.IntentIntegrator
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity: Activity() {
    private lateinit var store: Store
    private lateinit var curriculum: Curriculum
    private lateinit var body: LinearLayout
    private var page="home"
    private var selected="fire"
    private var session: TrainingSession?=null
    private var exportBytes: ByteArray?
        get()=java.io.File(cacheDir,"pending-export").takeIf{it.exists()}?.readBytes()
        set(value){val file=java.io.File(cacheDir,"pending-export");if(value==null)file.delete()else file.writeBytes(value)}
    private var tts: TextToSpeech?=null
    private var speechReady=false
    private val hi get()=store.hi
    private fun t(en: String,hiText: String)=if(hi)hiText else en

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);store=Store(this);curriculum=Curriculum(this)
        page=savedInstanceState?.getString("page")?:"home";selected=savedInstanceState?.getString("selected")?:"fire"
        savedInstanceState?.getString("session")?.let{store.attempt(it)?.let{data->session=TrainingSession(data,curriculum.module(data.getString("moduleId")))}}
        if(page=="training"&&session==null)page="home"
        tts=TextToSpeech(this){status->speechReady=status==TextToSpeech.SUCCESS}
        render()
    }
    override fun onResume(){super.onResume();if(::store.isInitialized && page=="records") render()}
    override fun onSaveInstanceState(out: Bundle) {super.onSaveInstanceState(out);out.putString("page",page);out.putString("selected",selected);out.putString("session",session?.data?.getString("id"))}
    override fun onDestroy(){tts?.shutdown();store.close();super.onDestroy()}
    override fun onPause(){tts?.stop();super.onPause()}
    @Deprecated("Compatibility") override fun onBackPressed(){if(page!="home"){page=if(page=="training")"module" else "home";render()}else super.onBackPressed()}
    private fun go(to: String){tts?.stop();if(to=="training"){session?.let{if(!it.finished&&it.data.optString("mode")=="arcore"){it.data.put("mode","hybrid");store.save(it)}}};page=to;render()}

    private fun render(){
        val root=column().apply{setBackgroundColor(Palette.canvas)}
        root.setOnApplyWindowInsetsListener{v,i->if(android.os.Build.VERSION.SDK_INT>=30){val b=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom)}else{v.setPadding(i.systemWindowInsetLeft,i.systemWindowInsetTop,i.systemWindowInsetRight,i.systemWindowInsetBottom)};i}
        val header=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(20),dp(16),dp(20),dp(10))}
        header.addView(label("S",20f,Palette.blue,true).apply{gravity=Gravity.CENTER;background=shape(Palette.soft,12)},LinearLayout.LayoutParams(dp(40),dp(40)))
        header.addView(label("Suraksha Saathi",16f,Palette.ink,true).apply{setPadding(dp(10),0,0,0)},LinearLayout.LayoutParams(0,-2,1f))
        header.addView(action(if(hi)"हिन्दी ▾" else "English ▾",false){language()},LinearLayout.LayoutParams(dp(105),dp(48)))
        root.add(header)
        val scroll=ScrollView(this).apply{isFillViewport=true;clipToPadding=false}
        body=column(20);scroll.addView(body);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        when(page){"home"->home();"module"->module();"training"->training();"records"->records();"help"->help();"verify"->verifyPage();else->home()}
        val nav=LinearLayout(this).apply{setPadding(dp(12),dp(10),dp(12),dp(10));setBackgroundColor(Color.WHITE)}
        listOf(Triple("home","Learn","सीखें"),Triple("records","My record","मेरा रिकॉर्ड"),Triple("help","Help","मदद")).forEach{(dest,en,h)->
            nav.addView(action(t(en,h),false){go(dest)}.apply{background=shape(if(page==dest||(dest=="home"&&page in listOf("module","training")))Palette.soft else Color.WHITE,12)},LinearLayout.LayoutParams(0,dp(56),1f).apply{marginEnd=dp(6)})
        }
        root.add(nav);setContentView(root);root.requestApplyInsets()
    }
    private fun title(text: String,sub: String?=null){body.add(label(text,28f,Palette.ink,true),bottom=8);sub?.let{body.add(label(it,16f,Palette.muted),bottom=20)}}
    private fun chip(text: String,colour: Int=Palette.soft,ink: Int=Palette.blue)=label(text,13f,ink).apply{background=shape(colour,10);setPadding(dp(12),dp(8),dp(12),dp(8))}
    private fun language(){
        AlertDialog.Builder(this).setTitle(t("Choose language","भाषा चुनें")).setItems(arrayOf("English","हिन्दी","Santali · review pending")){_,which->
            if(which==2)notice("Santali", "Native-speaker review and recordings are required before Santali lessons can be released. Hindi and English are available.")
            else {store.hi=which==1;render()}
        }.show()
    }
    private fun profile(){
        val field=EditText(this).apply{setSingleLine();hint=t("Your name","आपका नाम");setText(store.name);setPadding(dp(20),dp(16),dp(20),dp(16))}
        val sectors=listOf("Unspecified","Mining","Steel","Mica","Other")
        val sector=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,if(hi)listOf("नहीं चुना","खनन","इस्पात","अभ्रक","अन्य")else sectors);setSelection(sectors.indexOf(store.sector).coerceAtLeast(0));minimumHeight=dp(56)}
        val form=column(16);form.add(field);form.add(label(t("Work sector","काम का क्षेत्र"),14f),top=12);form.add(sector)
        AlertDialog.Builder(this).setTitle(t("Your learning profile","आपकी सीखने की प्रोफ़ाइल")).setMessage(t("Stored on this phone. No email or account required.","इस फ़ोन पर सुरक्षित। ईमेल या खाते की ज़रूरत नहीं।")).setView(form).setPositiveButton(t("Save","सहेजें")){_,_->store.name=field.text.toString().trim().take(80);store.sector=sectors[sector.selectedItemPosition];render()}.setNegativeButton(t("Cancel","रद्द करें"),null).show()
    }
    private fun home(){
        body.add(chip(t("OFFLINE READY  ·  PILOT TRAINING","ऑफ़लाइन तैयार  ·  पायलट प्रशिक्षण")),bottom=20)
        title(t("Learn to stay safe.","सुरक्षित रहना सीखें।"),t("Choose a lesson. Go at your own pace.","पाठ चुनें। अपनी गति से सीखें।"))
        val history=store.attempts();val latest=curriculum.modules.associate{m->m.getString("id") to history.firstOrNull{it.optString("moduleId")==m.getString("id")&&it.optString("kind")=="assessment"&&it.optBoolean("finished")}}
        val passed=latest.values.count{it?.optJSONObject("result")?.optBoolean("passed")==true}
        body.add(label(t("$passed / ${curriculum.modules.size} module assessments passed","$passed / ${curriculum.modules.size} पाठ मूल्यांकन पास"),14f,Palette.muted),bottom=16)
        body.add(action(t("Review decisions over time","समय के साथ निर्णय दोहराएँ"),false){startActivity(Intent(this,RecallActivity::class.java))},bottom=18)
        val active=history.firstOrNull{!it.optBoolean("finished")}
        if(active!=null)body.add(action(t("Continue saved training","सहेजा गया प्रशिक्षण जारी रखें"),false){session=TrainingSession(active,curriculum.module(active.getString("moduleId")));selected=active.getString("moduleId");go("training")},bottom=20)
        curriculum.modules.forEachIndexed{i,m->
            val c=card(if(i==0)0xffecf1fc.toInt() else Color.WHITE)
            c.add(label(t("LESSON ${i+1}  ·  ${m.getString("duration")} MIN","पाठ ${i+1}  ·  ${m.getString("duration")} मिनट"),13f,Palette.blue),bottom=14)
            c.add(SceneView(this,m.getString("id"),hi,false,true),bottom=16)
            latest[m.getString("id")]?.let{a->val ok=a.getJSONObject("result").optBoolean("passed");c.add(chip(if(ok)t("Assessment passed","मूल्यांकन पास")else t("Practice recommended","अभ्यास सुझाया गया"),if(ok)Palette.successBg else Palette.amberBg,if(ok)Palette.success else Palette.amber),bottom=12)}
            c.add(label(m.local("title",hi),23f,Palette.ink,true),bottom=8);c.add(label(m.local("subtitle",hi),16f,Palette.muted),bottom=20)
            c.add(action(t("Start learning","सीखना शुरू करें"),i==0){selected=m.getString("id");go("module")})
            body.add(c,bottom=16)
        }
        body.add(action(if(store.name.isBlank())t("Add your name","अपना नाम जोड़ें") else t("Learning as ${store.name}","${store.name} के रूप में सीख रहे हैं"),false){profile()},top=4)
        body.add(label(t("Your progress stays on this phone. Training results are not permission to perform hazardous work.","आपकी प्रगति इस फ़ोन पर रहती है। प्रशिक्षण परिणाम खतरनाक काम करने की अनुमति नहीं है।"),14f,Palette.muted),top=16)
    }
    private fun module(){
        val m=curriculum.module(selected)
        title(m.local("title",hi),m.local("subtitle",hi))
        body.add(SceneView(this,selected,hi),bottom=12)
        body.add(action(t("Explore in 3D","3D में देखें"),false){startActivity(Intent(this,EquipmentActivity::class.java).putExtra("moduleId",selected))},bottom=20)
        val objectives=card();objectives.add(label(t("What you’ll learn","आप क्या सीखेंगे"),19f,Palette.ink,true),bottom=12)
        val obj=m.getJSONArray("objectives");for(i in 0 until obj.length())objectives.add(label("✓  "+obj.getJSONArray(i).getString(if(hi)1 else 0),16f),bottom=10)
        body.add(objectives,bottom=16)
        m.getJSONArray("lessons").objects().forEachIndexed{i,l->
            val c=card();c.add(label("${i+1}. "+l.local("title",hi),19f,Palette.ink,true),bottom=10);c.add(label(l.local("body",hi)),bottom=14)
            c.add(action(t("Listen","सुनें"),false){speak(l.local("body",hi))});body.add(c,bottom=12)
        }
        body.add(action(t("Guided practice","निर्देशित अभ्यास")){startTraining(true,false)},top=8,bottom=12)
        body.add(action(t("Practise with camera AR","कैमरा AR में अभ्यास करें"),false){startTraining(true,true)},bottom=12)
        body.add(action(t("Take an assessment","मूल्यांकन शुरू करें"),false){AlertDialog.Builder(this).setTitle(t("Assessment mode","मूल्यांकन का तरीका")).setItems(arrayOf(t("On-screen decisions","स्क्रीन पर निर्णय"),t("Camera AR","कैमरा AR"))){_,i->startTraining(false,i==1)}.show()})
    }
    private fun speak(text: String){
        val engine=tts?:return
        val locale=if(hi)Locale.forLanguageTag("hi-IN") else Locale.ENGLISH
        if(!speechReady||engine.isLanguageAvailable(locale)<TextToSpeech.LANG_AVAILABLE){notice(t("Voice unavailable","आवाज़ उपलब्ध नहीं"),t("Install an offline voice for this language in Android speech settings. You can continue reading.","Android की आवाज़ सेटिंग में इस भाषा की ऑफ़लाइन आवाज़ इंस्टॉल करें। आप पढ़कर जारी रख सकते हैं।"));return}
        engine.language=locale
        val localVoice=engine.voices?.firstOrNull{it.locale.language==locale.language&&!it.isNetworkConnectionRequired}
        if(localVoice==null){notice(t("Offline voice needed","ऑफ़लाइन आवाज़ चाहिए"),t("No offline voice is installed for this language. Text remains available.","इस भाषा की ऑफ़लाइन आवाज़ इंस्टॉल नहीं है। पाठ उपलब्ध है।"));return}
        engine.voice=localVoice;engine.setSpeechRate(.9f);engine.speak(text,TextToSpeech.QUEUE_FLUSH,null,"lesson")
    }
    private fun startTraining(guided: Boolean,ar: Boolean){
        AlertDialog.Builder(this).setTitle(t("Check your training area","प्रशिक्षण क्षेत्र जाँचें")).setMessage(t("Use a clear, safe area away from machinery. All hazards and readings are simulated. ${if(guided)"You will get feedback after every choice." else "No hints during assessment. A critical unsafe choice ends the attempt."}","मशीनों से दूर, खुली सुरक्षित जगह में अभ्यास करें। खतरे और रीडिंग काल्पनिक हैं। ${if(guided)"हर विकल्प के बाद प्रतिक्रिया मिलेगी।" else "मूल्यांकन में संकेत नहीं मिलेंगे। गंभीर असुरक्षित निर्णय पर प्रयास समाप्त होगा।"}"))
            .setPositiveButton(t("I’m in a safe area","मैं सुरक्षित जगह पर हूँ")){_,_->
                if(ar){val availability=ArCoreApk.getInstance().checkAvailability(this);if(availability.isUnsupported){notice(t("AR is unavailable","AR उपलब्ध नहीं"),t("This phone does not support ARCore. Use on-screen practice.","यह फ़ोन ARCore का समर्थन नहीं करता। स्क्रीन पर अभ्यास करें।"));return@setPositiveButton}}
                session=TrainingSession.create(curriculum.module(selected),store.workerId,curriculum.version,guided,if(ar)"arcore" else "screen");store.save(session!!)
                if(ar){startActivityForResult(Intent(this,ArActivity::class.java).putExtra("attemptId",session!!.data.getString("id")),20)}else go("training")
            }.setNegativeButton(t("Not now","अभी नहीं"),null).show()
    }
    private fun training(){
        val s=session?:return go("home");val m=curriculum.module(s.data.getString("moduleId"))
        if(s.finished){result(s);return}
        body.add(chip(if(s.guided)t("GUIDED PRACTICE","निर्देशित अभ्यास") else t("ASSESSMENT · NO HINTS","मूल्यांकन · कोई संकेत नहीं")),bottom=16)
        title(m.local("title",hi),t("Question ${s.index+1} of ${s.questions.size}","सवाल ${s.index+1} / ${s.questions.size}"))
        body.add(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=s.questions.size;progress=s.index+1;progressTintList=android.content.res.ColorStateList.valueOf(Palette.blue)},bottom=20)
        val q=s.current
        body.add(SceneView(this,s.data.getString("moduleId"),hi,s.guided),bottom=16)
        val scene=card(0xffecf1fc.toInt());scene.add(label(t("TRAINING SCENARIO","प्रशिक्षण परिस्थिति"),13f,Palette.blue),bottom=12)
        scene.add(label(q.local("prompt",hi),22f,Palette.ink,true));body.add(scene,bottom=20)
        if(s.data.optBoolean("awaitingContinue")){
            if(s.guided){val good=s.data.optBoolean("lastCorrect");val feedback=card(if(good)Palette.successBg else Palette.amberBg);feedback.add(label(if(good)t("Safe understanding","सही समझ")else t("Let’s learn from this","इससे सीखें"),20f,if(good)Palette.success else Palette.amber,true),bottom=10);feedback.add(label(q.local("explanation",hi)));body.add(feedback,bottom=16)}else body.add(chip(t("Answer saved","उत्तर सहेजा गया")),bottom=16)
            body.add(action(t("Continue","आगे बढ़ें")){s.advance();store.save(s);render()})
        }else{
            s.options().forEach{option->body.add(action(option.local("text",hi),false){s.answer(option.getString("id"));store.save(s);render()},bottom=12)}
            if(s.guided)body.add(action(t("Read the question aloud","सवाल सुनें"),false){speak(q.local("prompt",hi))},top=4)
        }
        body.add(label(t("Saved automatically on this phone.","इस फ़ोन पर अपने आप सहेजा जाता है।"),14f,Palette.muted),top=20)
    }
    private fun result(s: TrainingSession){
        val r=s.data.getJSONObject("result");val passed=r.getBoolean("passed");val m=curriculum.module(s.data.getString("moduleId"))
        title(if(s.guided)t("Practice complete","अभ्यास पूरा हुआ") else if(passed)t("Assessment passed","मूल्यांकन पास") else t("Let’s practise again","फिर से अभ्यास करें"),m.local("title",hi))
        val c=card(if(passed||s.guided)Palette.successBg else Palette.amberBg)
        c.add(label("${r.getInt("correct")} / ${r.getInt("total")}",36f,Palette.ink,true),bottom=8)
        c.add(label(t("Decisions answered correctly","सही निर्णय")),bottom=12)
        if(r.getJSONArray("criticalFailures").length()>0)c.add(label(t("A critical safety decision needs more practice.","एक महत्वपूर्ण सुरक्षा निर्णय का और अभ्यास चाहिए।"),16f,Palette.danger,true))
        body.add(c,bottom=16)
        body.add(label(t("Practical observation: not assessed. This pilot result is not a statutory safety certificate.","व्यावहारिक निरीक्षण: मूल्यांकन नहीं हुआ। यह पायलट परिणाम वैधानिक सुरक्षा प्रमाणपत्र नहीं है।"),14f,Palette.muted),bottom=16)
        body.add(action(t("Review my decisions","मेरे निर्णय देखें"),false){review(s)},bottom=12)
        if(!s.guided&&passed)body.add(action(t("Save completion receipt (PDF)","पूर्णता रसीद सहेजें (PDF)")){saveReceipt(s)},bottom=12)
        body.add(action(t("Practise again","फिर अभ्यास करें"),false){selected=m.getString("id");startTraining(true,false)},bottom=12)
        body.add(action(t("My learning record","मेरा सीखने का रिकॉर्ड"),false){go("records")})
    }
    private fun review(s: TrainingSession){
        val text=s.events.filter{it.optString("type")=="answer"}.joinToString("\n\n"){e->val q=s.questions.first{it.getString("id")==e.getString("questionId")};val o=q.getJSONArray("options").objects().first{it.getString("id")==e.getString("optionId")};"${if(o.optBoolean("correct"))"✓" else "!"} ${q.local("prompt",hi)}\n${o.local("text",hi)}\n${q.local("explanation",hi)}"}
        notice(t("Your decisions","आपके निर्णय"),text)
    }
    private fun records(){
        title(t("My learning record","मेरा सीखने का रिकॉर्ड"),store.name.ifBlank{t("Your progress, saved on this phone.","आपकी प्रगति, इस फ़ोन पर सुरक्षित।")})
        val attempts=store.attempts();if(attempts.isEmpty())body.add(card().apply{add(label(t("Your first lesson is waiting.","आपका पहला पाठ तैयार है।"),21f,Palette.ink,true),bottom=12);add(label(t("Finish a practice to see your progress here.","अपनी प्रगति यहाँ देखने के लिए अभ्यास पूरा करें।")))},bottom=20)
        attempts.forEach{a->val m=curriculum.module(a.getString("moduleId"));val c=card();c.add(label(m.local("title",hi),20f,Palette.ink,true),bottom=8)
            val finished=a.optBoolean("finished");val passed=a.optJSONObject("result")?.optBoolean("passed")==true
            val status=if(!finished)t("In progress","जारी है") else if(a.getString("kind")=="practice")t("Practised","अभ्यास किया") else if(passed)t("Simulation passed","सिमुलेशन पास") else t("More practice needed","और अभ्यास चाहिए")
            c.add(chip(status),bottom=12);c.add(label(date(a.getLong("startedAt"))+" · "+a.getString("mode"),13f,Palette.muted),bottom=12)
            c.add(action(if(finished)t("View result","परिणाम देखें")else t("Continue","जारी रखें"),false){session=TrainingSession(a,m);selected=m.getString("id");go("training")});body.add(c,bottom=14)
        }
        if(attempts.any{it.optBoolean("finished")})body.add(action(t("Export records for trainer","प्रशिक्षक के लिए रिकॉर्ड भेजें")){exportBytes=store.export().toString(2).toByteArray();createDocument("application/json","suraksha-training-record.json")},top=8,bottom=12)
        body.add(action(t("Verify a QR record","QR रिकॉर्ड जाँचें"),false){go("verify")},bottom=12)
        store.credentials().forEach{c->body.add(action(t("View signed pilot credential","हस्ताक्षरित पायलट प्रमाणपत्र देखें"),false){showCredential(c)},bottom=12)}
        body.add(action(t("Edit my profile","मेरी प्रोफ़ाइल बदलें"),false){profile()})
    }
    private fun help(){
        title(t("Here to help","आपकी मदद के लिए"))
        listOf(t("1. Choose a lesson and learn the steps.","1. पाठ चुनें और चरण सीखें।"),t("2. Practise with guidance, then try an assessment.","2. निर्देशों के साथ अभ्यास करें, फिर मूल्यांकन करें।"),t("3. Your progress is saved after every answer.","3. हर उत्तर के बाद प्रगति सहेजी जाती है।"),t("4. Export records for your trainer. Completed practice does not authorise hazardous work.","4. प्रशिक्षक के लिए रिकॉर्ड भेजें। अभ्यास पूरा करना खतरनाक काम की अनुमति नहीं है।")).forEach{body.add(card().apply{add(label(it))},bottom=12)}
        body.add(action(t("Choose language","भाषा चुनें"),false){language()},top=8,bottom=12)
        body.add(action(t("Check AR support","AR समर्थन जाँचें"),false){ArCoreApk.getInstance().checkAvailabilityAsync(this){a->notice(t("AR support","AR समर्थन"),a.name)}},bottom=12)
        body.add(label(t("Version 0.3.1 • Pilot content requires safety review. Santali lessons await native-speaker review. Audio uses installed offline Android voices.","संस्करण 0.3.1 • पायलट सामग्री की सुरक्षा समीक्षा ज़रूरी है। संताली पाठों की स्थानीय वक्ता समीक्षा बाकी है। आवाज़ Android की इंस्टॉल ऑफ़लाइन आवाज़ से आती है।"),14f,Palette.muted))
    }
    private fun verifyPage(){
        title(t("Verify a record","रिकॉर्ड जाँचें"),t("Scan a receipt or signed training credential.","रसीद या हस्ताक्षरित प्रशिक्षण प्रमाणपत्र स्कैन करें।"))
        body.add(action(t("Scan QR code","QR कोड स्कैन करें")){IntentIntegrator(this).setDesiredBarcodeFormats(IntentIntegrator.QR_CODE).setPrompt(t("Scan training QR","प्रशिक्षण QR स्कैन करें")).setBeepEnabled(false).setOrientationLocked(false).initiateScan()},bottom=16)
        body.add(action(t("Open credential text file","प्रमाणपत्र की टेक्स्ट फ़ाइल खोलें"),false){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain"),30)},bottom=16)
        val field=EditText(this).apply{hint=t("Or paste the QR text","या QR का पाठ चिपकाएँ");inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;minLines=3;setTextColor(Palette.ink);setPadding(dp(16),dp(12),dp(16),dp(12));background=shape(Color.WHITE,12,Palette.line)}
        body.add(field,bottom=12);body.add(action(t("Check record","रिकॉर्ड जाँचें"),false){checkRecord(field.text.toString())})
    }
    private fun checkRecord(value: String){
        try {
            if(value.startsWith("SURAKSHA:CREDENTIAL:")){val credential=CredentialVerifier.verify(this,value);store.saveCredential(credential);showCredential(credential)
            } else if(value.startsWith("SURAKSHA:RECEIPT:")){
                val p=JSONObject(String(android.util.Base64.decode(value.removePrefix("SURAKSHA:RECEIPT:"),android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)))
                notice(t("Unverified completion receipt","असत्यापित पूर्णता रसीद"),t("This is a device-generated receipt, not an issuer-signed certificate. Module: ${p.getString("moduleId")}\nPractical observation: not assessed.","यह फ़ोन से बनी रसीद है, जारीकर्ता का हस्ताक्षरित प्रमाणपत्र नहीं। पाठ: ${p.getString("moduleId")}\nव्यावहारिक निरीक्षण नहीं हुआ।"))
            } else notice(t("Cannot verify this record","यह रिकॉर्ड सत्यापित नहीं हो सका"),t("No trusted issuer is configured for this QR. Ask your trainer to verify it in the dashboard. Never treat an unknown QR as a valid certificate.","इस QR के लिए विश्वसनीय जारीकर्ता नहीं है। प्रशिक्षक से डैशबोर्ड में जाँच करवाएँ। अज्ञात QR को वैध प्रमाणपत्र न मानें।"))
        }catch(_:Exception){notice(t("Invalid QR","अमान्य QR"),t("This is not a readable Suraksha training record.","यह पढ़ने योग्य सुरक्षा प्रशिक्षण रिकॉर्ड नहीं है।"))}
    }
    private fun showCredential(c:JSONObject){
        val box=column(20);box.add(label(t("Signature verified offline","हस्ताक्षर ऑफ़लाइन सत्यापित"),20f,Palette.success,true),bottom=12)
        box.add(label(curriculum.module(c.getString("moduleId")).local("title",hi)),bottom=8)
        box.add(label(t("Pilot simulation • ${c.getInt("score")}%\nPractical observation: not assessed.\nRevocation status: unknown offline. Ask your trainer for current status.","पायलट सिमुलेशन • ${c.getInt("score")}%\nव्यावहारिक निरीक्षण नहीं हुआ।\nऑफ़लाइन निरस्तीकरण की स्थिति अज्ञात है। वर्तमान स्थिति प्रशिक्षक से पूछें।"),14f,Palette.muted),bottom=12)
        val matrix=MultiFormatWriter().encode("SURAKSHA:CREDENTIAL:"+c.getString("token"),BarcodeFormat.QR_CODE,500,500)
        val bitmap=Bitmap.createBitmap(500,500,Bitmap.Config.ARGB_8888);for(x in 0 until 500)for(y in 0 until 500)bitmap.setPixel(x,y,if(matrix[x,y])Color.BLACK else Color.WHITE)
        box.add(ImageView(this).apply{setImageBitmap(bitmap);adjustViewBounds=true;contentDescription="Signed pilot credential QR"})
        AlertDialog.Builder(this).setTitle(t("Pilot credential","पायलट प्रमाणपत्र")).setView(ScrollView(this).apply{addView(box)}).setPositiveButton(t("Done","ठीक है"),null).show()
    }
    private fun createDocument(mime:String,name:String){startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,name),10)}
    @Deprecated("Compatibility") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        val scan=IntentIntegrator.parseActivityResult(requestCode,resultCode,data);if(scan!=null){scan.contents?.let{checkRecord(it)};return}
        if(requestCode==30&&resultCode==RESULT_OK){try{data?.data?.let{uri->val text=contentResolver.openInputStream(uri)?.use{run{val output=java.io.ByteArrayOutputStream();val buf=ByteArray(1024);var n=it.read(buf);while(n!=-1){require(output.size()+n<=6000);output.write(buf,0,n);n=it.read(buf)};output.toString("UTF-8")}}?:error("No file");checkRecord(text)}}catch(_:Exception){notice("Cannot read credential","Choose a valid credential text file.")};return}
        if(requestCode==10&&resultCode==RESULT_OK){try{data?.data?.let{uri->contentResolver.openOutputStream(uri)?.use{it.write(exportBytes?:error("No pending export"))};Toast.makeText(this,t("Saved","सहेजा गया"),Toast.LENGTH_SHORT).show()}}catch(_:Exception){notice(t("Could not save","सहेजा नहीं जा सका"),t("Choose another destination and try again.","दूसरी जगह चुनें और फिर कोशिश करें।"))};exportBytes=null}
        if(requestCode==20){session?.data?.getString("id")?.let{store.attempt(it)?.let{a->session=TrainingSession(a,curriculum.module(a.getString("moduleId")))}};go("training")}
    }
    private fun saveReceipt(s:TrainingSession){
        val payload=JSONObject().put("attemptId",s.data.getString("id")).put("moduleId",s.data.getString("moduleId")).put("status","unverified-receipt")
        val qr="SURAKSHA:RECEIPT:"+android.util.Base64.encodeToString(payload.toString().toByteArray(),android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
        val matrix=MultiFormatWriter().encode(qr,BarcodeFormat.QR_CODE,250,250)
        val bitmap=Bitmap.createBitmap(250,250,Bitmap.Config.ARGB_8888);for(x in 0 until 250)for(y in 0 until 250)bitmap.setPixel(x,y,if(matrix[x,y])Color.BLACK else Color.WHITE)
        val pdf=PdfDocument();val p=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,1).create());val c=p.canvas;val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawColor(Color.WHITE);paint.color=Palette.blue;c.drawRect(0f,0f,595f,14f,paint)
        fun line(text:String,y:Float,size:Float=16f){paint.color=Palette.ink;paint.textSize=size;c.drawText(text,44f,y,paint)}
        line("SURAKSHA SAATHI",72f,24f);line("PILOT COMPLETION RECEIPT",110f,18f)
        line(store.name.ifBlank{"Learner"}.take(48),165f,22f);line(curriculum.module(s.data.getString("moduleId")).local("title",false),205f)
        line("Simulation score: ${s.data.getJSONObject("result").getInt("score")}%",245f)
        line("Practical observation: NOT ASSESSED",280f);line("Issuer validation: PENDING",310f)
        c.drawBitmap(bitmap,44f,350f,paint);line(date(System.currentTimeMillis()),650f)
        line("Not a statutory safety certificate or work authorisation.",690f,14f)
        line("Attempt: ${s.data.getString("id")}",725f,11f)
        pdf.finishPage(p);val output=java.io.ByteArrayOutputStream();pdf.writeTo(output);pdf.close();exportBytes=output.toByteArray();createDocument("application/pdf","suraksha-completion-receipt.pdf")
    }
    private fun date(time:Long)=SimpleDateFormat("dd MMM yyyy, HH:mm",if(hi)Locale.forLanguageTag("hi-IN")else Locale.ENGLISH).format(Date(time))
}
