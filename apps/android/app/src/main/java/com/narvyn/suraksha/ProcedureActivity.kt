package com.narvyn.suraksha

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.*
import org.json.JSONObject

/** Saved, ordered practice/check attempts. The renderer never owns a learning record. */
class ProcedureActivity: Activity() {
    private lateinit var identity: Store
    private lateinit var evidence: ProcedureStore
    private lateinit var session: ProcedureSession
    private lateinit var scene: ProcedureSceneView
    private lateinit var header: LinearLayout
    private lateinit var lower: LinearLayout
    private lateinit var scroll: PagedPanel
    private lateinit var screenBody: LinearLayout
    private lateinit var cameraBody: LinearLayout
    private lateinit var footer: Button
    private var cameraBudget=CameraWorkspaceLayout.budget(0,1f)
    private var immersive=false
    private val backNavigation by lazy { AppBackNavigation(this){camera=false;scene.useCamera(false);render()} }
    private var centreAim=false
    private var status: TextView?=null
    private var active=false
    private var camera=false
    private var safeArea=false
    private var descriptions=false
    private var spatial=true
    private var revision=0
    private val hi get()=identity.hi
    private fun t(en:String,hindi:String)=if(hi)hindi else en

    override fun onCreate(state: Bundle?) {
        super.onCreate(state);identity=Store(this);evidence=ProcedureStore(this,identity.workerId)
        val module=intent.getStringExtra("moduleId")?.takeIf { it in ProcedureCatalog.modules } ?: "fire"
        val guided=intent.getBooleanExtra("guided",true)
        try {
            val saved=state?.takeIf{it.getString("workerId")==identity.workerId}?.getString("procedureId")?.let{id->evidence.records().firstOrNull{it.optString("id")==id}}
            session=saved?.let{ProcedureSession.restore(it)} ?: evidence.latest(module,guided)?.takeIf{it.optInt("catalogVersion")==ProcedureCatalog.VERSION && !it.optBoolean("finished")}?.let { ProcedureSession.restore(it) } ?: ProcedureSession.create(module,identity.workerId,guided).also { evidence.save(it.data) }
        } catch(_:Exception) { notice(t("Saved procedure unavailable","सहेजी प्रक्रिया उपलब्ध नहीं"),t("This record could not be resumed. It has been preserved.","यह रिकॉर्ड जारी नहीं हो सका। इसे सुरक्षित रखा गया है।"));finish();return }
        val sameLearner=state?.getString("workerId")==identity.workerId
        safeArea=sameLearner && state?.getBoolean("safeArea")==true;camera=if(sameLearner)state?.getBoolean("camera")==true else intent.getBooleanExtra("camera",false);descriptions=sameLearner && state?.getBoolean("descriptions")==true
        spatial=if(sameLearner)state?.getBoolean("spatial",true)!=false else true
        centreAim=sameLearner && state?.getBoolean("centreAim")==true
        val root=column(16).apply { setBackgroundColor(Palette.canvas) }
        root.setOnApplyWindowInsetsListener { v,i ->
            if(android.os.Build.VERSION.SDK_INT>=30) { val b=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(16)+b.left,dp(12)+b.top,dp(16)+b.right,dp(12)+b.bottom) }
            else v.setPadding(dp(16)+i.systemWindowInsetLeft,dp(12)+i.systemWindowInsetTop,dp(16)+i.systemWindowInsetRight,dp(12)+i.systemWindowInsetBottom)
            i
        }
        screenBody=column();header=column();lower=column();scene=ProcedureSceneView(this)
        scene.onStatus={ message -> status?.text=message }
        screenBody.add(header);screenBody.addView(scene,LinearLayout.LayoutParams(-1,-2));screenBody.add(lower,top=12)
        scroll=paged(screenBody,hi)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        cameraBody=object:LinearLayout(this) {
            override fun onMeasure(w:Int,h:Int) { cameraBudget=CameraWorkspaceLayout.budget(View.MeasureSpec.getSize(h),resources.displayMetrics.density);super.onMeasure(w,h) }
        }.apply { orientation=LinearLayout.VERTICAL;visibility=View.GONE };root.addView(cameraBody,LinearLayout.LayoutParams(-1,0,1f))
        footer=action(t("Save & return","सहेजें और लौटें"),false,role=ActionRole.NEUTRAL) {
            if(immersive && session.feedback)change { it.advance() }
            else if(immersive) { camera=false;render() }else finish()
        }
        root.add(footer,top=12)
        setContentView(root);render()
    }
    override fun onResume() {
        super.onResume();active=true
        if(!::session.isInitialized)return
        if(Store(this).use { it.workerId }!=session.data.getString("workerId")) { finish();return }
        render()
    }
    override fun onPause() { if(::identity.isInitialized&&LearningSync.linked(this,identity.workerId))LearningSync.schedule(this);active=false;revision++;if(::scene.isInitialized)scene.pause();super.onPause() }
    // API 33+ uses AppBackNavigation and PagedPanel callbacks; retain this fallback for API 29–32.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Deprecated("Android 10–12 compatibility") override fun onBackPressed(){if(popContentPage())return;if(immersive){camera=false;scene.useCamera(false);render()}else super.onBackPressed()}
    override fun onDestroy() { backNavigation.close(); if(::scene.isInitialized)scene.close();if(::evidence.isInitialized)evidence.close();if(::identity.isInitialized)identity.close();super.onDestroy() }
    override fun onSaveInstanceState(out: Bundle) { out.putString("workerId",identity.workerId);out.putString("procedureId",session.data.getString("id"));out.putBoolean("safeArea",safeArea);out.putBoolean("camera",camera);out.putBoolean("descriptions",descriptions);out.putBoolean("spatial",spatial);out.putBoolean("centreAim",centreAim);super.onSaveInstanceState(out) }
    override fun onRequestPermissionsResult(code:Int, permissions:Array<out String>,results:IntArray) {
        super.onRequestPermissionsResult(code,permissions,results)
        if(code==61 && active && camera) { scene.useCamera(true);render();if(safeArea && !descriptions && !session.done && !session.feedback)scene.resume() }
    }
    private fun change(mutation:(ProcedureSession)->Boolean) {
        if(!active)return
        if(Store(this).use { it.workerId }!=session.data.getString("workerId")) { finish();return }
        try {
            val candidate=ProcedureSession.restore(JSONObject(session.data.toString()))
            if(!mutation(candidate))return
            evidence.save(candidate.data);session=candidate;render()
        } catch(_:Exception) { render();notice(t("Could not save","सहेजा नहीं जा सका"),t("Your last saved step is preserved. Check storage and try again.","आपका पिछला सहेजा चरण सुरक्षित है। स्टोरेज जाँचकर फिर कोशिश करें।")) }
    }
    private fun requestCameraPermission() {
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.CAMERA),61)
    }
    private fun arrangeCamera(value:Boolean) {
        footer.text=if(value)if(session.feedback)t("Continue procedure","प्रक्रिया जारी रखें")else t("Continue on screen","स्क्रीन पर जारी रखें")else t("Save & return","सहेजें और लौटें")
        footer.actionRole(if(value && session.feedback)ActionRole.PRIMARY else if(value)ActionRole.LEARN else ActionRole.NEUTRAL)
        if(immersive==value)return
        immersive=value
        for(v in listOf(header,scene,lower))(v.parent as? android.view.ViewGroup)?.removeView(v)
        cameraBody.removeAllViews();screenBody.removeAllViews()
        if(value) {
            val top=object:PagedPanel(this,hi) { override fun onMeasure(w:Int,h:Int) { super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(cameraBudget.header,View.MeasureSpec.AT_MOST)) } }.apply { setContent(header) }
            val bottom=object:PagedPanel(this,hi) { override fun onMeasure(w:Int,h:Int) { super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(cameraBudget.feedback,View.MeasureSpec.AT_MOST)) } }.apply { setContent(lower) }
            cameraBody.add(top);cameraBody.addView(scene,LinearLayout.LayoutParams(-1,0,1f));cameraBody.add(bottom,top=6)
        }else { screenBody.add(header);screenBody.addView(scene,LinearLayout.LayoutParams(-1,-2));screenBody.add(lower,top=12) }
        scroll.visibility=if(value)View.GONE else View.VISIBLE;cameraBody.visibility=if(value)View.VISIBLE else View.GONE
        scene.useImmersive(value)
    }
    private fun cameraOptions() {
        val options=mutableListOf(t("Continue on screen","स्क्रीन पर जारी रखें"),t("Use text actions","लिखित क्रियाएँ उपयोग करें"),t("Reposition the scene","दृश्य की जगह बदलें"),t("Camera permission settings","कैमरा अनुमति सेटिंग"),if(centreAim)t("Use direct target touch","लक्ष्य सीधे छूकर चुनें")else t("Use centre aiming","बीच का निशाना उपयोग करें"),t("Save & return","सहेजें और लौटें"))
        if(!session.feedback && ProcedureSpatial.supported(session.step.id))options.add(if(spatial)t("Use button actions instead","बटन वाली क्रियाएँ उपयोग करें")else t("Use spatial target practice","स्थानिक लक्ष्य अभ्यास उपयोग करें"))
        PageDialogBuilder(this).setTitle(t("AR practice options","AR अभ्यास विकल्प")).setItems(options.toTypedArray()) { _,which ->
            if(!active)return@setItems
            when(which) {
                0->{camera=false;descriptions=false;render()}
                1->{camera=false;descriptions=true;render()}
                2->scene.placeAtCameraCentre()
                3->startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:$packageName")))
                4->{centreAim=!centreAim;scene.useCentreAim(centreAim)}
                5->finish()
                6->{spatial=!spatial;render()}
            }
        }.setNegativeButton(t("Close","बंद करें"),null).show()
    }
    /** Camera training occupies the remaining window; options and feedback no longer push it below a long lesson. */
    private fun renderCamera(currentRevision:Int) {
        val step=session.step
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        row.addView(label(t("CAMERA AR · ${session.index+1}/${session.definition.steps.size}","कैमरा AR · ${session.index+1}/${session.definition.steps.size}"),15f,Palette.blue,true),LinearLayout.LayoutParams(0,-2,1f))
        row.addView(action(t("Options","विकल्प"),false,role=ActionRole.NEUTRAL) { cameraOptions() },LinearLayout.LayoutParams(-2,-2))
        header.add(row,bottom=6);header.add(label(step.text(hi),18f,Palette.ink,true).asHeading(),bottom=6)
        status=label(t("Find a clear training surface, then place the scene.","खाली प्रशिक्षण सतह खोजें, फिर दृश्य रखें।"),13f,Palette.muted)
        if(!session.feedback)header.add(status!!,bottom=6)
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)lower.add(action(t("Enable camera","कैमरा चालू करें"),false,role=ActionRole.CAMERA) { requestCameraPermission() },bottom=8)
        val completed=session.data.getJSONObject("flags").keys().asSequence().filter { session.data.getJSONObject("flags").optBoolean(it) }.toSet()
        val choices=step.actions.shuffled(java.util.Random(session.data.getString("id").hashCode().toLong() xor session.index.toLong()))
        val useSpatial=spatial && ProcedureSpatial.supported(step.id) && !session.feedback
        scene.pause();scene.visibility=View.VISIBLE
        scene.configure(ProcedureSceneView.Scene(session.module,step.id,if(session.feedback)emptyList()else choices.map { ProcedureSceneView.Target(it.id,it.text(hi),it.point) },completed,enabled=!session.feedback,spatial=useSpatial),hi) { id,presentation,proof ->
            if(active && revision==currentRevision && !session.feedback && !session.done && session.step.id==step.id)change { it.choose(id,presentation,System.currentTimeMillis(),proof) }
        }
        scene.useCentreAim(centreAim);scene.useCamera(true);if(active)scene.resume()
        if(session.feedback) {
            lower.add(label(if(session.guided)if(session.data.optBoolean("lastCorrect"))t("Action completed","क्रिया पूरी हुई")else t("Review and retry","समझें और फिर कोशिश करें")else t("Action saved","क्रिया सहेजी गई"),18f,Palette.ink,true).asHeading(),bottom=6)
            if(session.guided)lower.add(label(step.explain(hi),15f),bottom=6)
        }else if(useSpatial) {
            lower.add(label(t("Hold a target for 0.65 s · virtual targeting only","लक्ष्य 0.65 सेकंड दबाएँ · केवल काल्पनिक निशाना"),13f,Palette.muted),bottom=4)
            choices.forEachIndexed { index,target -> lower.add(label("${index+1} · ${target.text(hi)}",14f,Palette.ink),bottom=4) }
        }
    }
    private fun render() {
        revision++;val currentRevision=revision
        header.removeAllViews();lower.removeAllViews();status=null
        arrangeCamera(camera && safeArea && !session.done && !descriptions)
        backNavigation.enabled(immersive)
        if(immersive) { renderCamera(currentRevision);return }
        header.add(label(ProcedureCatalog.modules.getValue(session.module).text(hi),25f,Palette.ink,true).asHeading(),bottom=8)
        header.add(label(if(session.guided)t("GUIDED PROCEDURE · DRAFT SIMULATION","निर्देशित प्रक्रिया · प्रारूप सिमुलेशन")else t("INDEPENDENT PROCEDURE · NO HINTS","स्वतंत्र प्रक्रिया · कोई संकेत नहीं"),13f,Palette.blue,true),bottom=8)
        header.add(label(t("Learner: ${identity.name.ifBlank { "Unnamed learner" }} · Practical competence is not assessed.","शिक्षार्थी: ${identity.name.ifBlank { "बिना नाम" }} · व्यावहारिक योग्यता का मूल्यांकन नहीं है।"),14f,Palette.muted),bottom=12)
        scene.visibility=View.GONE;scene.pause()
        if(!safeArea && !session.done) {
            header.add(label(t("Prepare a safe training space","सुरक्षित प्रशिक्षण जगह तैयार करें"),22f,Palette.ink,true).asHeading(),bottom=12)
            header.add(label(t("Stay away from operating machinery and real hazards. Use a cleared tabletop or a stationary screen exercise. All flames, readings, routes and actions are simulated; they never assess the real surroundings.","चलती मशीनों और असली खतरों से दूर रहें। खाली मेज़ या स्थिर स्क्रीन अभ्यास उपयोग करें। आग, रीडिंग, रास्ते और क्रियाएँ काल्पनिक हैं; ये असली आसपास का आकलन नहीं करते।")),bottom=16)
            lower.add(action(t("I am in a safe training area","मैं सुरक्षित प्रशिक्षण जगह पर हूँ")) { safeArea=true;render();if(camera)requestCameraPermission() })
        } else if(session.done) {
            header.accessibilityPaneTitle=t("Procedure result","प्रक्रिया परिणाम")
            val stopped=session.data.optJSONObject("result")?.optBoolean("stopped")==true
            val card=card(if(stopped)Palette.amberBg else Palette.successBg)
            card.add(label(if(stopped)t("Procedure stopped","प्रक्रिया रोकी गई")else t("Sequence completed","क्रम पूरा हुआ"),23f,Palette.ink,true).asHeading(),bottom=12)
            card.add(label(if(stopped)t("An unsafe action ended this independent attempt. Review the explanation and practise the sequence.","असुरक्षित क्रिया से स्वतंत्र प्रयास समाप्त हुआ। विवरण पढ़ें और क्रम का अभ्यास करें।")else t("Your ordered actions are saved. Completion does not prove safe physical operation or issue a certificate.","आपकी क्रमबद्ध क्रियाएँ सहेजी गईं। पूरा होना सुरक्षित वास्तविक संचालन साबित नहीं करता और प्रमाणपत्र जारी नहीं करता।")),bottom=12)
            if(stopped)card.add(label(session.step.explain(hi)),bottom=12)
            val events=session.data.getJSONArray("events").objects().filter { it.optString("type")=="action" }
            val cameraEvents=events.count { it.optString("presentation")=="camera" };val descriptionEvents=events.count { it.optString("presentation")=="description" }
            card.add(label(t("Recorded actions: ${events.size} · camera: $cameraEvents · screen: ${events.size-cameraEvents-descriptionEvents} · text: $descriptionEvents","दर्ज क्रियाएँ: ${events.size} · कैमरा: $cameraEvents · स्क्रीन: ${events.size-cameraEvents-descriptionEvents} · लिखित: $descriptionEvents"),14f,Palette.muted))
            val spatialEvents=events.filter { it.has("spatial") }
            card.add(label(t("Spatial target holds: ${spatialEvents.size} · camera: ${spatialEvents.count { it.optString("presentation")=="camera" }}. These measure virtual targeting, not real equipment handling.","स्थानिक लक्ष्य पर पकड़: ${spatialEvents.size} · कैमरा: ${spatialEvents.count { it.optString("presentation")=="camera" }}। यह काल्पनिक लक्ष्य मापता है, असली उपकरण चलाना नहीं।"),14f,Palette.muted),top=10)
            lower.add(card,bottom=16)
            lower.add(action(t("Review actions and explanations","क्रियाएँ और व्याख्या देखें"),false,ActionRole.REVIEW){
                val text=events.joinToString("\n\n"){e->val st=session.definition.steps.first{it.id==e.getString("step")};(if(e.optBoolean("correct"))"✓ " else "! ")+st.text(hi)+"\n"+st.explain(hi)}
                notice(t("Your decision timeline","आपके निर्णयों का क्रम"),text)
            },bottom=12)
            lower.add(action(t("Continue learning path","सीखने का क्रम जारी रखें"),false){finish()},bottom=12)
            lower.add(action(t("Start a new attempt","नया प्रयास शुरू करें")) {
                try { val next=ProcedureSession.create(session.module,identity.workerId,session.guided);evidence.save(next.data);session=next;safeArea=false;render() }
                catch(_:Exception) { notice(t("Could not save","सहेजा नहीं जा सका"),t("The existing record is preserved.","मौजूदा रिकॉर्ड सुरक्षित है।")) }
            })
        } else {
            val step=session.step
            val choices=step.actions.shuffled(java.util.Random(session.data.getString("id").hashCode().toLong() xor session.index.toLong()))
            header.accessibilityPaneTitle=step.text(hi)
            header.add(label(t("Step ${session.index+1} of ${session.definition.steps.size}","चरण ${session.index+1} / ${session.definition.steps.size}"),14f,Palette.blue),bottom=8)
            header.add(label(step.text(hi),22f,Palette.ink,true).asHeading(),bottom=12)
            if(session.feedback) {
                val card=card(Palette.soft)
                card.add(label(if(session.guided)if(session.data.optBoolean("lastCorrect"))t("Action completed","क्रिया पूरी हुई")else t("Unsafe action · review and retry","असुरक्षित क्रिया · समझें और फिर कोशिश करें")else t("Action saved","क्रिया सहेजी गई"),20f,Palette.ink,true).asHeading(),bottom=12)
                card.add(label(if(session.guided)step.explain(hi)else t("Continue when ready. Feedback appears after the attempt.","तैयार होने पर जारी रखें। प्रतिक्रिया प्रयास के बाद दिखेगी।")))
                lower.add(card,bottom=16)
                lower.add(action(t("Continue procedure","प्रक्रिया जारी रखें")) { if(revision==currentRevision)change { it.advance() } })
            } else {
                status=label(t("Choose the next action on the training scene.","प्रशिक्षण दृश्य पर अगली क्रिया चुनें।"),14f,Palette.muted)
                header.add(status!!,bottom=10)
                header.add(action(if(camera)t("Continue on screen","स्क्रीन पर जारी रखें")else t("Use camera AR","कैमरा AR उपयोग करें"),false,role=if(camera)ActionRole.LEARN else ActionRole.CAMERA) {
                    descriptions=false;camera=!camera;scene.useCamera(camera);render()
                    if(camera && checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.CAMERA),61)
                },bottom=10)
                if(camera)header.add(action(t("Camera permission settings","कैमरा अनुमति सेटिंग"),false,role=ActionRole.NEUTRAL) { startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:$packageName"))) },bottom=10)
                header.add(action(if(descriptions)t("Use the training scene","प्रशिक्षण दृश्य उपयोग करें")else t("Use text actions","लिखित क्रियाएँ उपयोग करें"),false) { descriptions=!descriptions;camera=false;scene.useCamera(false);render() },bottom=10)
                val useSpatial=spatial && ProcedureSpatial.supported(step.id)
                if(ProcedureSpatial.supported(step.id) && !descriptions) {
                    header.add(action(if(spatial)t("Use button actions instead","बटन वाली क्रियाएँ उपयोग करें")else t("Use spatial target practice","स्थानिक लक्ष्य अभ्यास उपयोग करें"),false,role=ActionRole.LEARN) { spatial=!spatial;render() },bottom=10)
                    if(useSpatial) {
                        header.add(label(t("SPATIAL PRACTICE · hold a target for 0.65 seconds","स्थानिक अभ्यास · लक्ष्य पर 0.65 सेकंड पकड़ रखें"),13f,Palette.blue,true),bottom=8)
                        header.add(label(t("Hold on the labelled virtual target to carry out your decision. This records a simulated interaction, not real equipment handling.","निर्णय की क्रिया के लिए नाम वाले काल्पनिक लक्ष्य पर पकड़ें। यह सिमुलेशन है, असली उपकरण संचालन नहीं।"),14f,Palette.muted),bottom=10)
                    }
                }
                val flags=session.data.optJSONObject("flags") ?: JSONObject()
                val completed=flags.keys().asSequence().filter { flags.optBoolean(it) }.toSet()
                scene.visibility=if(descriptions)View.GONE else View.VISIBLE
                scene.configure(ProcedureSceneView.Scene(session.module,step.id,choices.map { ProcedureSceneView.Target(it.id,it.text(hi),it.point) },completed,spatial=useSpatial),hi) { id,presentation,proof ->
                    if(active && revision==currentRevision && !session.feedback && !session.done && session.step.id==step.id)change { it.choose(id,presentation,System.currentTimeMillis(),proof) }
                }
                scene.useCamera(camera);if(active && !descriptions)scene.resume()
                if(useSpatial && !descriptions) choices.forEachIndexed { index,target -> lower.add(label("${index+1} · ${target.text(hi)}",16f,Palette.ink).apply { tag="procedure-spatial-legend-${target.id}" },bottom=10) }
                if(descriptions)choices.forEach { target -> lower.add(action(target.text(hi),false) { if(revision==currentRevision)change { it.choose(target.id,"description",System.currentTimeMillis()) } }.apply { tag="procedure-description-${target.id}" },bottom=12) }
                lower.add(label(t("Use the scene controls to carry out the sequence. You may pause or switch to screen mode without losing your saved actions.","क्रम पूरा करने के लिए दृश्य के नियंत्रण उपयोग करें। बिना सहेजी क्रियाएँ खोए आप रुक सकते हैं या स्क्रीन तरीका चुन सकते हैं।"),14f,Palette.muted))
            }
        }
        scroll.post { scroll.firstPage() }
    }
}
