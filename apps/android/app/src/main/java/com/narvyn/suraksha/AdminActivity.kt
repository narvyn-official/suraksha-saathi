package com.narvyn.suraksha

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Calendar
import java.util.concurrent.Executors

/** Native centre workspace: fixed chrome, dedicated record lists, and authenticated server actions. */
class AdminActivity:Activity() {
    private lateinit var api:AdminClient
    private lateinit var root:LinearLayout
    private lateinit var body:LinearLayout
    private lateinit var status:TextView
    private lateinit var heading:TextView
    private lateinit var nav:LinearLayout
    private val jobs=Executors.newSingleThreadExecutor()
    private var session:JSONObject?=null
    private var management=JSONObject()
    private var records=JSONObject()
    private var page="overview"
    private var busy=false
    private var signup=false
    private var detailOpen=false
    private val pageHistory=mutableListOf<String>()
    private val backNavigation by lazy { AppBackNavigation(this){navigateBack()} }
    private var generation=0
    private val hi by lazy { Store(this).use { it.hi } }
    private fun t(en:String,hin:String)=if(hi)hin else en
    private val role get()=session?.optJSONObject("current")?.optString("role")?:"viewer"
    private val writable get()=role!="viewer"
    override fun onCreate(saved:Bundle?){super.onCreate(saved);api=AdminClient(this);login();if(api.base.isNotBlank()&&api.hasSession())refresh()}
    override fun onDestroy(){backNavigation.close();generation++;jobs.shutdownNow();super.onDestroy()}
    // API 33+ uses AppBackNavigation and PagedPanel callbacks; retain this fallback for API 29–32.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Deprecated("Android 10–12 compatibility") override fun onBackPressed(){navigateBack()}
    private fun navigateBack(){
        if(popContentPage())return
        if(busy){info(t("Please wait for the current request to finish.","मौजूदा अनुरोध पूरा होने दें।"));return}
        when{
            detailOpen->{detailOpen=false;show(page,false)}
            session==null&&signup->{signup=false;login()}
            session==null||page=="overview"->finish()
            else->show(if(pageHistory.isNotEmpty())pageHistory.removeAt(pageHistory.lastIndex)else "overview",false)
        }
    }
    private fun screen(title:String,withNav:Boolean=session!=null) {
        backNavigation.enabled(true)
        root=column();root.setBackgroundColor(Palette.canvas);root.fitsSystemWindows=true
        val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(16),dp(8),dp(16),dp(8));setBackgroundColor(Palette.surface)}
        top.addView(action("‹",role=ActionRole.NEUTRAL){navigateBack()}.apply{contentDescription=t("Back to learning","सीखने पर वापस जाएँ")},LinearLayout.LayoutParams(dp(48),dp(48)))
        heading=label(title,21f,bold=true).asHeading().apply{setPadding(dp(12),0,0,0)};top.addView(heading,LinearLayout.LayoutParams(0,-2,1f));root.add(top)
        status=label("",14f,Palette.muted).apply{visibility=View.GONE;setPadding(dp(20),dp(8),dp(20),dp(8));accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE};root.add(status)
        body=column(20);root.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        nav=LinearLayout(this).apply{setPadding(dp(8),dp(8),dp(8),dp(8));setBackgroundColor(Palette.surface)}
        if(withNav)for((id,en,hin) in listOf(Triple("overview","Overview","सारांश"),Triple("workers","Workers","कर्मचारी"),Triple("records","Records","रिकॉर्ड"),Triple("more","More","अधिक"))) {
            nav.addView(action(t(en,hin),role=if(page==id)ActionRole.PRIMARY else ActionRole.NEUTRAL){show(id)}.apply{textSize=13f;tag="admin-nav-$id";isSelected=page==id;setPadding(dp(4),dp(10),dp(4),dp(10))},LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=dp(3);marginEnd=dp(3)})
        }
        if(withNav)root.add(nav)
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root){v,insets->val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);insets}
    }
    private fun info(message:String,error:Boolean=false){status.text=message;status.setTextColor(if(error)Palette.danger else Palette.muted);status.visibility=View.VISIBLE}
    private fun task(work:()->JSONObject,done:(JSONObject)->Unit){if(busy)return;busy=true;val token=++generation;info(t("Connecting…","कनेक्ट हो रहा है…"));enable(root,false)
        jobs.execute{try{val result=work();runOnUiThread{if(!isDestroyed&&token==generation){busy=false;enable(root,true);status.visibility=View.GONE;try{done(result)}catch(e:Exception){info(e.message?:"Could not display the result.",true)}}}}catch(e:Exception){runOnUiThread{if(!isDestroyed&&token==generation){busy=false;enable(root,true);if(e is AdminClient.SignedOut){session=null;login()};info(when(e){is java.io.IOException->t("Cannot reach your centre. Check your connection and server address.","केंद्र से संपर्क नहीं हुआ। कनेक्शन और सर्वर पता जाँचें।");else->e.message?:"Could not complete this action."},true)}}}}
    }
    private fun enable(view:View,value:Boolean){view.isEnabled=value;if(view is android.view.ViewGroup)for(i in 0 until view.childCount)enable(view.getChildAt(i),value)}
    private fun field(parent:LinearLayout,title:String,value:String="",type:Int=InputType.TYPE_CLASS_TEXT):EditText {
        val group=column();parent.add(group,top=12)
        val caption=label(title,14f,Palette.muted,true);group.add(caption)
        val input=EditText(this).apply{ id=View.generateViewId();setText(value);textSize=16f;inputType=type;isSingleLine=true;minHeight=dp(48);setPadding(dp(12),dp(8),dp(12),dp(8));background=shape(Palette.surface,12,Palette.line);contentDescription=title;tag="admin-field-$title" }
        caption.labelFor=input.id;group.add(input,top=6)
        if(type and InputType.TYPE_MASK_VARIATION==InputType.TYPE_TEXT_VARIATION_PASSWORD){
            input.setAutofillHints(View.AUTOFILL_HINT_PASSWORD)
            group.add(action(t("Show password","पासवर्ड दिखाएँ"),role=ActionRole.NEUTRAL){
                val start=input.selectionStart;val end=input.selectionEnd
                val hidden=input.transformationMethod is android.text.method.PasswordTransformationMethod
                input.transformationMethod=if(hidden)null else android.text.method.PasswordTransformationMethod.getInstance()
                val toggle=group.getChildAt(group.childCount-1) as Button
                toggle.text=t(if(hidden)"Hide password" else "Show password",if(hidden)"पासवर्ड छिपाएँ" else "पासवर्ड दिखाएँ")
                input.requestFocus();if(start>=0&&end>=0)input.setSelection(start,end)
            },top=4)
        }else if(type and InputType.TYPE_MASK_VARIATION==InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)input.setAutofillHints(View.AUTOFILL_HINT_EMAIL_ADDRESS)
        return input
    }
    private fun button(title:String,role:ActionRole=ActionRole.PRIMARY,click:()->Unit){body.add(action(title,role=role, onClick=click),top=12)}
    private fun form(title:String,detail:Boolean=true,setup:(LinearLayout)->Unit){detailOpen=detail;screen(title);val content=column();val scroll=paged(content,hi);body.addView(scroll,LinearLayout.LayoutParams(-1,-1));setup(content)}
    private fun login(){session=null;detailOpen=false;pageHistory.clear();page="login";screen(t("Training centre","प्रशिक्षण केंद्र"),false)
        val content=column();val scroll=paged(content,hi);body.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        content.add(label(t(if(signup)"Create your account" else "Welcome back",if(signup)"खाता बनाएँ" else "फिर से स्वागत है"),28f,bold=true),top=12)
        content.add(label(t("Your SurakshaAr account","आपका SurakshaAr खाता"),15f,Palette.muted),top=6)
        val name=if(signup)field(content,t("Full name","पूरा नाम"))else null
        val email=field(content,t("Email address","ईमेल"),type=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val password=field(content,t("Password","पासवर्ड"),type=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        content.add(label(t(if(signup)"Use at least 15 characters." else "Sign in to manage workers and training.",if(signup)"कम से कम 15 अक्षर रखें।" else "कर्मचारी और प्रशिक्षण प्रबंधन के लिए प्रवेश करें।"),14f,Palette.muted),top=8)
        content.add(action(t(if(signup)"Create account" else "Sign in",if(signup)"खाता बनाएँ" else "साइन इन")){
            if(api.base.isBlank()){server();return@action}
            if(!android.util.Patterns.EMAIL_ADDRESS.matcher(email.text.toString().trim()).matches()||password.length()<(if(signup)15 else 1)||password.length()>128||signup&&name!!.text.isBlank()){info(t("Check your name, email and password.","नाम, ईमेल और पासवर्ड जाँचें।"),true);return@action}
            val payload=JSONObject().put("email",email.text.toString().trim()).put("password",password.text.toString()).put("rememberMe",true);if(signup)payload.put("name",name!!.text.toString().trim())
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(password.windowToken,0)
            task({api.call("/api/auth/${if(signup)"sign-up" else "sign-in"}/email",payload)}){password.text.clear();refresh()}
        }.apply{tag="admin-login-submit"},top=24)
        content.add(action(t(if(signup)"Already registered? Sign in" else "Create an account",if(signup)"खाता है? साइन इन करें" else "नया खाता बनाएँ"),role=ActionRole.LEARN){signup=!signup;login()},top=10)
        if(!signup)content.add(action(t("Forgot password?","पासवर्ड भूल गए?"),role=ActionRole.LEARN){
            if(api.base.isBlank()){server();return@action}
            val address=email.text.toString().trim()
            if(!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()){email.error=t("Enter your account email first.","पहले खाते का ईमेल दर्ज करें।");return@action}
            task({api.call("/api/auth/request-password-reset",JSONObject().put("email",address))}){
                notice(t("Password recovery","पासवर्ड पुनर्प्राप्ति"),t("If this address has an account, check your inbox for a reset link. If it does not arrive, contact your training centre.","यदि इस ईमेल पर खाता है, तो इनबॉक्स में रीसेट लिंक देखें। लिंक न मिलने पर प्रशिक्षण केंद्र से संपर्क करें।"))
            }
        },top=10)
        body.add(action(t("Connection settings","कनेक्शन सेटिंग"),role=ActionRole.NEUTRAL){server()},top=8)
        body.add(label(t("Offline lessons remain available without signing in.","बिना साइन इन के ऑफलाइन पाठ उपलब्ध हैं।"),13f,Palette.muted),top=10)
    }
    private fun server(){val input=EditText(this).apply{setText(api.base);hint="https://training.example.org";inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI;isSingleLine=true};val dialog=PageDialogBuilder(this).setTitle(t("Training centre server","प्रशिक्षण केंद्र सर्वर")).setMessage(t("Enter the service address provided by your administrator. Changing it signs out this device.","व्यवस्थापक द्वारा दिया गया सर्वर पता दर्ज करें। बदलने पर साइन आउट होगा।")).setView(input).setNegativeButton(t("Cancel","रद्द"),null).setPositiveButton(t("Save","सहेजें"),null).create();dialog.setOnShowListener{dialog.getButton(-1).setOnClickListener{try{api.configure(input.text.toString());session=null;dialog.dismiss();login()}catch(e:Exception){input.error=e.message}}};dialog.show()}
    private fun refresh(){if(!::root.isInitialized)screen(t("Training centre","प्रशिक्षण केंद्र"),false);task({val s=api.call("/api/admin/session");if(s.isNull("current"))api.call("/api/admin/session",JSONObject().put("owner",s.getJSONObject("user").getString("userId")));val fresh=api.call("/api/admin/session");val m=api.call("/api/admin/manage");val r=api.call("/api/records");JSONObject().put("session",fresh).put("manage",m).put("records",r)}){session=it.getJSONObject("session");management=it.getJSONObject("manage");records=it.getJSONObject("records");show(if(page=="login")"overview" else page)}}
    private fun rows(key:String)=management.optJSONArray(key)?.objects().orEmpty()
    private fun recordRows(key:String)=records.optJSONArray(key)?.objects().orEmpty()
    private fun module(id:String)=Curriculum(this).modules.find{it.optString("id")==id}?.local("title",hi)?:id
    private fun date(value:Long)=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(java.util.Date(value))
    private fun show(id:String,remember:Boolean=true){if(busy)return;if(remember&&id!=page&&page!="login")pageHistory.add(page);detailOpen=false;page=id;when(id){
        "overview"->overview();"workers"->workers();"records"->assessments();"more"->more();"assignments"->assignments();"credentials"->credentials();"team"->team();"settings"->settings();"audit"->activityLog();"rooms"->rooms();"verify"->verify();"curriculum"->curriculum();else->overview()
    }}
    private fun overview(){screen(t("Training overview","प्रशिक्षण सारांश"));
        val parent=body.parent as LinearLayout;val position=parent.indexOfChild(body);parent.removeView(body)
        parent.addView(paged(body,hi),position,LinearLayout.LayoutParams(-1,0,1f));val centre=session!!.getJSONObject("current");body.add(label(centre.optString("name"),24f,bold=true));body.add(label(listOf(centre.optString("site"),role).filter{it.isNotBlank()}.joinToString(" · "),14f,Palette.muted),top=6)
        val stats=card(Palette.tealBg);stats.add(label(t("Your centre at a glance","आपके केंद्र का सारांश"),16f,bold=true))
        stats.add(label("${management.optJSONObject("counts")?.optInt("workers")?:0}  ${t("workers","कर्मचारी")}    ·    ${recordRows("attempts").size}  ${t("recent records","हाल के रिकॉर्ड")}",18f,bold=true),top=16)
        stats.add(label("${rows("assignments").count{it.optString("status")=="overdue"}}  ${t("assignments need follow-up","प्रशिक्षण लंबित हैं")}",16f,Palette.amber),top=12);body.add(stats,top=22)
        button(t("Plan training","प्रशिक्षण योजना")){show("assignments")}
        if(writable)button(t("Sync this worker’s records","इस कर्मचारी के रिकॉर्ड भेजें"),ActionRole.LEARN){sync()}
        button(t("Switch / join a centre","केंद्र बदलें / जुड़ें"),ActionRole.NEUTRAL){switchCentre()}
        button(t("Refresh workspace","कार्यस्थल ताज़ा करें"),ActionRole.NEUTRAL){refresh()}
        body.add(label(t("Pilot learning records · practical competence requires separate assessment.","पायलट सीखने के रिकॉर्ड · व्यावहारिक योग्यता का अलग मूल्यांकन आवश्यक है।"),13f,Palette.muted),top=16)
    }
    private fun listing(title:String,items:List<Pair<String,String>>,click:(Int)->Unit){
        screen(title)
        if(items.isEmpty()){body.add(label(t("Nothing here yet","अभी कोई रिकॉर्ड नहीं"),22f,bold=true),top=24);body.add(label(t("Use the actions below to get started.","नीचे दिए कार्यों से शुरू करें।"),15f,Palette.muted),top=12);body.addView(View(this),LinearLayout.LayoutParams(1,0,1f))}
        else{val entries=column();items.forEachIndexed{index,item->entries.add(card().apply{
            add(label(item.first,16f,bold=true));if(item.second.isNotBlank())add(label(item.second,14f,Palette.muted),top=5)
            minimumHeight=dp(68);isClickable=true;isFocusable=true;setOnClickListener{click(index)}
            background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Palette.tealBg),shape(Palette.surface,16,Palette.line),null)
        },bottom=10)};body.addView(paged(entries,hi),LinearLayout.LayoutParams(-1,0,1f))}
    }

    private fun workers(){val values=rows("workers");listing(t("Workers","कर्मचारी"),values.map{it.optString("name") to it.optString("sector")}){val worker=values[it];val choices=if(writable)arrayOf(t("Learning history","सीखने का इतिहास"),t("Edit profile","प्रोफाइल बदलें"))else arrayOf(t("Learning history","सीखने का इतिहास"));PageDialogBuilder(this).setTitle(worker.optString("name")).setItems(choices){_,index->if(index==1)workerForm(worker)else workerHistory(worker)}.show()};if(writable)button(t("Add worker","कर्मचारी जोड़ें")){workerForm(null)}}
    private fun workerForm(worker:JSONObject?){if(!writable){notice(worker!!.optString("name"),"${worker.optString("sector")}\n${worker.optString("id")}");return};form(t("Worker profile","कर्मचारी प्रोफाइल")){f->val name=field(f,t("Name","नाम"),worker?.optString("name")?:"");val sectors=Store.SECTORS.toList();val sector=Spinner(this).apply{adapter=ArrayAdapter(this@AdminActivity,android.R.layout.simple_spinner_dropdown_item,sectors);setSelection(sectors.indexOf(worker?.optString("sector")?:"Unspecified").coerceAtLeast(0))};f.add(label(t("Sector","क्षेत्र"),14f),top=12);f.add(sector);val androidId=if(worker==null)field(f,t("Android worker ID (optional)","Android कर्मचारी ID (वैकल्पिक)"))else null;f.add(action(t("Save worker","सहेजें")){val value=JSONObject().put("action","worker").put("name",name.text.toString()).put("sector",sector.selectedItem);if(worker!=null)value.put("id",worker.getString("id"))else if(!androidId!!.text.isBlank())value.put("androidId",androidId.text.toString().trim());save(value)},top=24)}}
    private fun save(value:JSONObject){task({api.call("/api/admin/manage",value)}){val code=it.optString("invitation");refresh();if(code.isNotBlank())PageDialogBuilder(this).setTitle(t("Private invitation code","निजी आमंत्रण कोड")).setMessage(t("Share privately with the invited person. Expires in seven days.","आमंत्रित व्यक्ति को निजी रूप से दें। सात दिन तक मान्य।")).setPositiveButton(t("Share code","कोड साझा करें")){_,_->share(code,"Suraksha centre invitation")}.setNegativeButton("Close",null).show()}}
    private fun assignments(){val values=rows("assignments");listing(t("Assignments","प्रशिक्षण कार्य"),values.map{"${it.optString("worker_name")} · ${module(it.optString("module_id"))}" to "${it.optString("status")} · ${date(it.optLong("due_at"))}"}){val a=values[it];val options=if(writable&&a.optString("status")!="cancelled")arrayOf(t("View details","विवरण"),t("Cancel assignment","प्रशिक्षण रद्द करें"))else arrayOf(t("View details","विवरण"));PageDialogBuilder(this).setTitle(a.optString("worker_name")).setItems(options){_,i->if(i==0)notice(module(a.optString("module_id")),"${a.optString("note")}\n${a.optString("status")}\n${date(a.optLong("due_at"))}")else reason(t("Reason for cancellation","रद्द करने का कारण")){save(JSONObject().put("action","cancel").put("id",a.getString("id")).put("reason",it))}}.show()};if(writable)button(t("Assign training","प्रशिक्षण दें")){assign()};if(values.isNotEmpty())button(t("Share assignment report","प्रशिक्षण रिपोर्ट साझा करें"),ActionRole.NEUTRAL){assignmentReport(values)}}
    private fun assign(){val workers=rows("workers");if(workers.isEmpty()){notice(t("Add a worker first","पहले कर्मचारी जोड़ें"),t("Import a phone profile or register a worker.","फोन प्रोफाइल भेजें या कर्मचारी जोड़ें।"));return};form(t("Assign training","प्रशिक्षण दें")){f->val chosen=linkedSetOf<Int>();val chooseWorkers=action(t("Select workers","कर्मचारी चुनें"),role=ActionRole.NEUTRAL){};chooseWorkers.setOnClickListener{val selected=BooleanArray(workers.size){it in chosen};PageDialogBuilder(this).setTitle(t("Select up to 100 workers","100 कर्मचारी तक चुनें")).setMultiChoiceItems(workers.map{it.optString("name")}.toTypedArray(),selected){_,index,checked->selected[index]=checked}.setNegativeButton("Cancel",null).setPositiveButton("Done"){_,_->chosen.clear();selected.indices.filter{selected[it]}.take(100).forEach{chosen.add(it)};chooseWorkers.text="${chosen.size} ${t("workers selected","कर्मचारी चयनित")}"}.show()};f.add(label(t("Workers","कर्मचारी"),14f));f.add(chooseWorkers);val modules=Curriculum(this).modules;val m=Spinner(this).apply{adapter=ArrayAdapter(this@AdminActivity,android.R.layout.simple_spinner_dropdown_item,modules.map{it.local("title",hi)})};f.add(label(t("Training module","प्रशिक्षण मॉड्यूल"),14f),top=16);f.add(m);val due=Calendar.getInstance().apply{add(Calendar.DAY_OF_YEAR,7);set(Calendar.HOUR_OF_DAY,18);set(Calendar.MINUTE,0)};val choose=action(date(due.timeInMillis),role=ActionRole.NEUTRAL){};choose.setOnClickListener{DatePickerDialog(this,{_,y,mo,d->due.set(y,mo,d,18,0);choose.text=date(due.timeInMillis)},due.get(Calendar.YEAR),due.get(Calendar.MONTH),due.get(Calendar.DAY_OF_MONTH)).show()};f.add(label(t("Due date · 18:00 local time","अंतिम तारीख · स्थानीय समय 18:00"),14f),top=16);f.add(choose);val note=field(f,t("Instructions (optional)","निर्देश (वैकल्पिक)"));f.add(action(t("Assign training","प्रशिक्षण दें")){if(chosen.isEmpty()){info(t("Select at least one worker.","कम से कम एक कर्मचारी चुनें।"),true);return@action};save(JSONObject().put("action","assign").put("workerIds",JSONArray(chosen.map{workers[it].getString("id")} )).put("moduleId",modules[m.selectedItemPosition].getString("id")).put("dueAt",due.timeInMillis).put("note",note.text.toString()))},top=24)}}
    private fun assessments(){val values=recordRows("attempts");listing(t("Assessment records","मूल्यांकन रिकॉर्ड"),values.map{val p=it.getJSONObject("payload");"${it.optString("worker_name")} · ${module(p.optString("moduleId"))}" to "${p.optString("kind")} · ${p.optJSONObject("result")?.optInt("score")?:0}%"}){assessment(values[it])};if(writable)button(t("Sync this worker","इस कर्मचारी के रिकॉर्ड भेजें")){sync()}}
    private fun assessment(row:JSONObject){val p=row.getJSONObject("payload");val result=p.optJSONObject("result")?:JSONObject();val actions=mutableListOf(t("View decisions","निर्णय देखें"));if(writable&&p.optString("kind")=="assessment"&&result.optBoolean("passed"))actions.add(t("Issue pilot credential","पायलट प्रमाणपत्र जारी करें"));PageDialogBuilder(this).setTitle(row.optString("worker_name")).setMessage("${module(p.optString("moduleId"))}\n${result.optInt("score")}% · ${if(result.optBoolean("passed"))"Passed" else "Needs review"}").setItems(actions.toTypedArray()){_,i->if(i==0)decisionEvidence(p)else PageDialogBuilder(this).setTitle(t("Issue pilot credential?","पायलट प्रमाणपत्र जारी करें? ")).setMessage(t("This confirms a passed simulation assessment. It does not certify practical competence.","यह सफल सिमुलेशन मूल्यांकन दर्शाता है। यह व्यावहारिक योग्यता प्रमाणित नहीं करता।")).setNegativeButton("Cancel",null).setPositiveButton("Issue"){_,_->issue(row.getString("id"))}.show()}.show()}
    private fun issue(attemptId:String) {
        val expiry=Calendar.getInstance().apply{add(Calendar.DAY_OF_YEAR,30)}
        DatePickerDialog(this,{_,y,m,d->expiry.set(y,m,d,23,59,59);val expiresAt=expiry.timeInMillis
            PageDialogBuilder(this).setTitle(t("Credential expiry","प्रमाणपत्र की वैधता")).setMessage(date(expiresAt)).setNegativeButton("Cancel",null).setPositiveButton("Issue"){_,_->task({api.call("/api/credentials",JSONObject().put("attemptId",attemptId).put("expiresAt",expiresAt))}){credential(it)}}.show()
        },expiry.get(Calendar.YEAR),expiry.get(Calendar.MONTH),expiry.get(Calendar.DAY_OF_MONTH)).apply{datePicker.minDate=System.currentTimeMillis()}.show()
    }
    private fun credentials(){val values=recordRows("credentials");listing(t("Pilot credentials","पायलट प्रमाणपत्र"),values.map{module(it.optString("moduleId")) to "${it.optString("status")} · ${it.optString("id").take(8)}"}){credential(values[it])}}
    private fun credential(value:JSONObject){form(t("Pilot credential","पायलट प्रमाणपत्र")){f->f.add(label(t("Simulation learning","सिमुलेशन प्रशिक्षण"),22f,bold=true));f.add(label("${value.optString("status")} · ${module(value.optString("moduleId"))}",16f),top=12);val token=value.optString("token");if(token.isNotBlank()){val matrix=QRCodeWriter().encode("SURAKSHA:CREDENTIAL:"+token.removePrefix("SURAKSHA:CREDENTIAL:"),BarcodeFormat.QR_CODE,640,640);val bitmap=Bitmap.createBitmap(640,640,Bitmap.Config.ARGB_8888);for(y in 0 until 640)for(x in 0 until 640)bitmap.setPixel(x,y,if(matrix[x,y])android.graphics.Color.BLACK else android.graphics.Color.WHITE);f.add(ImageView(this).apply{setImageBitmap(bitmap);adjustViewBounds=true;maxHeight=dp(250);contentDescription="Signed credential QR code"},top=16);f.add(action(t("Share credential","प्रमाणपत्र साझा करें")){share("SURAKSHA:CREDENTIAL:"+token.removePrefix("SURAKSHA:CREDENTIAL:"),"Pilot credential")},top=16)};if(writable&&value.isNull("revoked_at"))f.add(action(t("Revoke credential","प्रमाणपत्र रद्द करें"),role=ActionRole.DANGER){reason(t("Revocation reason","रद्द करने का कारण")){task({api.call("/api/credentials",JSONObject().put("id",value.getString("id")).put("reason",it),"PATCH")}){refresh()}}},top=12);f.add(label(t("Practical competence: not assessed","व्यावहारिक योग्यता: मूल्यांकन नहीं हुआ"),14f,Palette.muted),top=12)}}
    private fun more(){val items=mutableListOf("assignments" to t("Assignments","प्रशिक्षण कार्य"),"rooms" to t("AR practice journals","AR अभ्यास रिकॉर्ड"),"curriculum" to t("Curriculum","पाठ्यक्रम"),"credentials" to t("Pilot credentials","पायलट प्रमाणपत्र"),"verify" to t("Verify QR credential","QR प्रमाणपत्र जाँचें"),"audit" to t("Activity log","कार्य इतिहास"));if(role=="admin"){items.add("team" to t("Team access","टीम प्रवेश"));items.add("settings" to t("Centre settings","केंद्र सेटिंग"))};listing(t("Workspace tools","केंद्र के उपकरण"),items.map{it.second to ""}){show(items[it].first)};button(t("Account & security","खाता और सुरक्षा"),ActionRole.NEUTRAL){account()}}
    private fun team(){val values=rows("team");listing(t("Team access","टीम प्रवेश"),values.map{it.optString("email") to "${it.optString("role")} · ${if(it.optInt("active")==0)"Revoked" else if(it.isNull("user_id"))"Invited" else "Active"}"}){val member=values[it];PageDialogBuilder(this).setTitle(member.optString("email")).setItems(arrayOf("Change role / resend invitation",if(member.optInt("active")==1)"Revoke access" else "Restore access")){_,i->if(i==0)invite(member)else save(JSONObject().put("action","member").put("email",member.optString("email")).put("role",member.optString("role")).put("active",member.optInt("active")==0))}.show()};button(t("Invite colleague","सहकर्मी को आमंत्रित करें")){invite(null)}}
    private fun invite(member:JSONObject?){form(t("Invite colleague","सहकर्मी को आमंत्रित करें")){f->val email=field(f,t("Email address","ईमेल"),member?.optString("email")?:"",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);val roles=listOf("trainer","viewer","admin");val selected=Spinner(this).apply{adapter=ArrayAdapter(this@AdminActivity,android.R.layout.simple_spinner_dropdown_item,roles);setSelection(roles.indexOf(member?.optString("role")?:"trainer").coerceAtLeast(0))};f.add(label(t("Role","भूमिका"),14f),top=16);f.add(selected);f.add(label(t("You’ll receive a private invitation code to share. No email is sent.","साझा करने के लिए निजी कोड मिलेगा। ईमेल नहीं भेजा जाता।"),14f,Palette.muted),top=16);f.add(action(t("Save invitation","आमंत्रण सहेजें")){save(JSONObject().put("action","member").put("email",email.text.toString()).put("role",selected.selectedItem).put("active",true))},top=24)}}
    private fun settings(){form(t("Centre settings","केंद्र सेटिंग"),false){f->val c=session!!.getJSONObject("current");val name=field(f,t("Centre name","केंद्र का नाम"),c.optString("name"));val site=field(f,t("Site / location","स्थान"),c.optString("site"));f.add(action(t("Save settings","सेटिंग सहेजें")){save(JSONObject().put("action","settings").put("name",name.text.toString()).put("site",site.text.toString()))},top=24)}}
    private fun account(){form(t("Account security","खाता सुरक्षा")){f->f.add(label(session!!.getJSONObject("user").optString("email"),16f,bold=true));val current=field(f,t("Current password","वर्तमान पासवर्ड"),type=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD);val next=field(f,t("New password · 12+ characters","नया पासवर्ड · 12+ अक्षर"),type=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD);f.add(action(t("Update password","पासवर्ड बदलें")){task({api.call("/api/auth/change-password",JSONObject().put("currentPassword",current.text.toString()).put("newPassword",next.text.toString()).put("revokeOtherSessions",true))}){current.text.clear();next.text.clear();info(t("Password updated. Other sessions signed out.","पासवर्ड बदला गया। अन्य सत्र बंद हुए।"))}},top=20);f.add(action(t("Sign out","साइन आउट"),role=ActionRole.DANGER){task({api.call("/api/auth/sign-out",JSONObject())}){api.clear();login()}},top=16);f.add(action(t("Connection settings","कनेक्शन सेटिंग"),role=ActionRole.NEUTRAL){server()},top=12)}}
    private fun switchCentre(){val choices=session!!.getJSONArray("workspaces").objects();PageDialogBuilder(this).setTitle(t("Choose centre","केंद्र चुनें")).setItems((choices.map{it.optString("name")}+t("Join with invitation code","आमंत्रण कोड से जुड़ें")).toTypedArray()){_,i->if(i<choices.size)task({api.call("/api/admin/session",JSONObject().put("owner",choices[i].getString("owner")))}){refresh()}else{val input=EditText(this).apply{hint="Invitation code";isSingleLine=true};PageDialogBuilder(this).setTitle(t("Join a centre","केंद्र से जुड़ें")).setView(input).setNegativeButton("Cancel",null).setPositiveButton("Join"){_,_->task({api.call("/api/admin/session",JSONObject().put("invitation",input.text.toString().trim()))}){refresh()}}.show()}}.show()}
    private fun activityLog(){val values=rows("audit");listing(t("Activity log","कार्य इतिहास"),values.map{it.optString("action") to "${it.optString("actor_email")} · ${date(it.optLong("at"))}"}){detail(t("Activity details","कार्य विवरण"),values[it].let{entry->"${entry.optString("action")}\n${entry.optString("actor_email")}\n${date(entry.optLong("at"))}\n\n${entry.optJSONObject("detail")?.let{d->d.keys().asSequence().joinToString("\n"){key->"$key: ${d.opt(key)}"}}?:""}"})}}
    private fun rooms(){screen(t("AR practice","AR अभ्यास"));task({api.call("/api/room-journals")}){response->val values=response.getJSONArray("rooms").objects();listing(t("AR practice","AR अभ्यास"),values.map{it.optString("worker_name") to "${module(it.getJSONObject("payload").optString("module"))} · ${it.optInt("snapshots")} snapshots"}){val row=values[it];task({api.call("/api/room-journals?attempt="+row.getString("id"))}){roomEvidence(it)}};if(writable)button(t("Sync room practice","कमरे का अभ्यास भेजें")){syncRooms()}}}
    private fun curriculum(){val modules=Curriculum(this).modules;listing(t("Curriculum","पाठ्यक्रम"),modules.map{it.local("title",hi) to "${it.optString("duration")} min"}){val m=modules[it];detail(m.local("title",hi),m.optJSONArray("objectives")?.let{a->(0 until a.length()).joinToString("\n\n"){i->val o=a.get(i);if(o is JSONArray)o.getString(if(hi)1 else 0)else o.toString()}}?:m.local("subtitle",hi))}}
    private fun verify(){form(t("Verify credential","प्रमाणपत्र जाँचें"),false){f->val value=field(f,t("QR payload","QR डेटा"));f.add(action(t("Verify","जाँचें")){verifyToken(value.text.toString())},top=20);f.add(action(t("Scan QR code","QR स्कैन करें"),role=ActionRole.CAMERA){IntentIntegrator(this).setDesiredBarcodeFormats(IntentIntegrator.QR_CODE).setPrompt(t("Scan credential QR","प्रमाणपत्र QR स्कैन करें")).setBeepEnabled(false).initiateScan()},top=12)}}
    private fun verifyToken(value:String){task({api.call("/api/verify",JSONObject().put("token",value))}){result->detail(t("Verification result","जाँच परिणाम"),"${result.optString("status")}\n${result.optString("message")}\n${module(result.optString("moduleId"))}\n${result.optString("revocationStatus")}\n${result.optString("expiryStatus")}")}}
    @Deprecated("Activity result") override fun onActivityResult(request:Int,result:Int,data:Intent?){val scan=IntentIntegrator.parseActivityResult(request,result,data);if(scan!=null){scan.contents?.let{verifyToken(it)}}else super.onActivityResult(request,result,data)}
    private fun sync(){Store(this).use{store->PageDialogBuilder(this).setTitle(t("Sync worker records?","कर्मचारी रिकॉर्ड भेजें? ")).setMessage("${store.name.ifBlank{"Current worker"}} → ${session!!.getJSONObject("current").optString("name")}\n"+t("Only this phone profile’s completed records will be uploaded.","केवल इस फोन प्रोफाइल के पूर्ण रिकॉर्ड भेजे जाएंगे।")).setNegativeButton("Cancel",null).setPositiveButton("Sync"){_,_->val data=Store(this).use{it.export()};task({uploadAssessments(data)}){refresh()}}.show()}}
    private fun uploadAssessments(data:JSONObject):JSONObject {
        val pending=data.getJSONArray("attempts").objects();require(pending.isNotEmpty()){t("No completed records to upload.","भेजने के लिए कोई पूर्ण रिकॉर्ड नहीं है।")}
        var imported=0;var unchanged=0
        for(chunk in pending.chunked(100)) {
            val response=api.call("/api/import",JSONObject(data.toString()).put("attempts",JSONArray(chunk)))
            imported+=response.optInt("imported");unchanged+=response.optInt("unchanged")
        }
        return JSONObject().put("imported",imported).put("unchanged",unchanged)
    }
    private fun syncRooms(){Store(this).use{store->val data=try{RoomJournalExport.create(this,store)}catch(e:Exception){info(e.message?:"No practice records",true);return};PageDialogBuilder(this).setTitle(t("Sync room practice?","कमरे का अभ्यास भेजें? ")).setMessage("${store.name} → ${session!!.getJSONObject("current").optString("name")}").setNegativeButton("Cancel",null).setPositiveButton("Sync"){_,_->task({api.call("/api/room-journals",data)}){info(t("Room practice uploaded.","कमरे का अभ्यास भेजा गया।"))}}.show()}}
    private fun reason(title:String,done:(String)->Unit){val input=EditText(this);val dialog=PageDialogBuilder(this).setTitle(title).setView(input).setNegativeButton("Cancel",null).setPositiveButton("Confirm",null).create();dialog.setOnShowListener{dialog.getButton(-1).setOnClickListener{val text=input.text.toString().trim();if(text.length !in 5..500)input.error="Use 5–500 characters" else{dialog.dismiss();done(text)}}};dialog.show()}
    private fun workerHistory(worker:JSONObject) {
        val attempts=recordRows("attempts").filter{it.optString("worker_id")==worker.optString("id")}
        listing(worker.optString("name"),attempts.map{row->val p=row.getJSONObject("payload");module(p.optString("moduleId")) to "${p.optString("kind")} · ${p.optJSONObject("result")?.optInt("score")?:0}%"}){assessment(attempts[it])}
    }
    private fun assignmentReport(rows:List<JSONObject>) {
        fun cell(value:String):String {val safe=if(value.firstOrNull() in listOf('=','+','-','@','\t','\r'))"'"+value else value;return "\""+safe.replace("\"","\"\"")+"\""}
        val lines=mutableListOf(listOf("Worker","Worker ID","Training","Due","Status","Completed").joinToString(",",transform=::cell))
        rows.forEach{a->lines.add(listOf(a.optString("worker_name"),a.optString("worker_id"),module(a.optString("module_id")),date(a.optLong("due_at")),a.optString("status"),if(a.isNull("completed_at"))""else date(a.optLong("completed_at"))).joinToString(",",transform=::cell))}
        share(lines.joinToString("\r\n"),"Suraksha assignment report · ${rows.size} loaded records")
    }
    private fun decisionEvidence(payload:JSONObject) {
        val source=Curriculum(this).versions[payload.optString("contentVersion")]
        val questions=source?.optJSONArray("modules")?.objects()?.find{it.optString("id")==payload.optString("moduleId")}?.optJSONArray("questions")?.objects().orEmpty()
        val events=payload.optJSONArray("events")?.objects().orEmpty().filter{it.optString("type")=="answer"}
        val items=events.mapIndexed{index,event->val q=questions.find{it.optString("id")==event.optString("questionId")};val answer=q?.optJSONArray("options")?.objects()?.find{it.optString("id")==event.optString("optionId")}
            "${index+1}. ${q?.let{runCatching{it.local("prompt",hi)}.getOrElse{q.optString("id")}}?:event.optString("questionId")}" to "${answer?.local("text",hi)?:event.optString("optionId")} · ${if(answer?.optBoolean("correct")==true)t("Correct decision","सही निर्णय")else t("Needs review","दोहराना आवश्यक")}"}
        listing(t("Decision evidence","निर्णय प्रमाण"),items){index->notice(items[index].first,items[index].second)}
    }
    private fun roomEvidence(response:JSONObject) {
        val snapshots=response.optJSONArray("snapshots")?.objects().orEmpty()
        val items=snapshots.map{row->val p=row.getJSONObject("payload");val mission=p.optJSONObject("mission")?:JSONObject()
            date(row.optLong("captured_at")) to "${if(mission.optBoolean("completed"))t("Completed","पूर्ण")else t("In progress","जारी")} · ${mission.optJSONArray("events")?.length()?:0} ${t("actions","कार्य")}"}
        listing(t("Practice history","अभ्यास इतिहास"),items){index->val mission=snapshots[index].getJSONObject("payload").getJSONObject("mission");val actions=mission.optJSONArray("events")?.objects().orEmpty().map{e->e.optString("id").replace('-',' ') to "${if(e.optBoolean("accepted"))t("Accepted","स्वीकार")else t("Retry needed","फिर करें")} · ${e.optString("reason").replace('-',' ')}"};listing(t("Practice actions","अभ्यास के कार्य"),actions){i->notice(actions[i].first,actions[i].second)}}
    }
    private fun detail(title:String,value:String){form(title){it.add(label(value,16f));it.add(action(t("Back","वापस"),role=ActionRole.NEUTRAL){navigateBack()},top=20)}}
    private fun share(value:String,title:String){startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,value);putExtra(Intent.EXTRA_SUBJECT,title)},title))}
}
