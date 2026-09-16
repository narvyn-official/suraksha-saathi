package com.narvyn.suraksha

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import java.text.DateFormat
import java.util.Date

class ComponentPracticeActivity: Activity() {
    private lateinit var store: Store
    private lateinit var session: ComponentSession
    private lateinit var scene: ComponentSceneView
    private lateinit var sceneContainer: FrameLayout
    private var camera: ComponentCameraView? = null
    private var cameraSelected = false
    private var activeForeground = false
    private var cameraStatus: TextView? = null
    private val cameraActions = mutableListOf<Button>()
    private lateinit var header: LinearLayout
    private lateinit var lower: LinearLayout
    private lateinit var scroll: PagedPanel
    private val hi get() = store.hi
    private fun t(en: String, hindi: String) = if (hi) hindi else en

       @Deprecated("Android 10–12 compatibility") override fun onBackPressed(){if(!popContentPage())super.onBackPressed()}
 override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        store = Store(this)
        if(state?.getString("workerId")?.let { it!=store.workerId }==true) { finish();return }
        val module = intent.getStringExtra("moduleId")?.takeIf { ComponentCatalog.modules.containsKey(it) } ?: "fire"
        session = ComponentSession.restore(module,store.componentRecords()[module])
        // Consume the launch request once; recreation or later reopening must not discard an active session.
        if (state == null && intent.getBooleanExtra("startReview",false) && session.done) session = ComponentSession.start(module,session.data)
        cameraSelected = state?.getBoolean("cameraSelected",false) == true && module == "fire" && !session.descriptions
        store.saveComponent(session.data)
        val root = column(20).apply { setBackgroundColor(Palette.canvas) }
        root.setOnApplyWindowInsetsListener { v,i ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val b = i.getInsets(WindowInsets.Type.systemBars()); v.setPadding(dp(20)+b.left,dp(20)+b.top,dp(20)+b.right,dp(20)+b.bottom)
            } else v.setPadding(dp(20)+i.systemWindowInsetLeft,dp(20)+i.systemWindowInsetTop,dp(20)+i.systemWindowInsetRight,dp(20)+i.systemWindowInsetBottom)
            i
        }
        val body = column()
        header = column(); lower = column()
        scene = ComponentSceneView(this,module).apply { onVisible = { if(activeForeground && !cameraSelected && !session.descriptions) recordPresentation("screen") } }
        sceneContainer = FrameLayout(this).apply { addView(scene,FrameLayout.LayoutParams(-1,-1)) }
        body.add(header)
        body.addView(sceneContainer,LinearLayout.LayoutParams(-1,dp(330)))
        body.add(lower,top=12)
        scroll = paged(body,hi)
        scroll.onPageChanged = { if(!cameraSelected) scene.reportVisibility() }
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        root.add(action(t("Save & return","सहेजें और लौटें"),false,role=ActionRole.NEUTRAL) { finish() },top=12)
        setContentView(root)
        render()
    }
    override fun onResume() {
        super.onResume()
        if(!currentWorker() || !::session.isInitialized)return
        activeForeground=true
        if(cameraSelected && !session.done) startCamera() else if(::scene.isInitialized) scene.resume()
    }
    override fun onPause() { activeForeground=false; camera?.pauseCamera(); if (::scene.isInitialized) scene.pause(); super.onPause() }
    override fun onDestroy() { camera?.close(); if(::store.isInitialized)store.close(); super.onDestroy() }
    override fun onSaveInstanceState(out: Bundle) { out.putBoolean("cameraSelected",cameraSelected); if(::store.isInitialized)out.putString("workerId",store.workerId); super.onSaveInstanceState(out) }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code,permissions,results)
        if(code==41 && cameraSelected && activeForeground) startCamera()
    }
    // UI-thread eligibility: retire the old worker's screen before resuming a view or changing evidence.
    private fun currentWorker(): Boolean {
        if(!::store.isInitialized || isFinishing || isDestroyed)return false
        if(store.isCurrentProfile())return true
        activeForeground=false; camera?.pauseCamera(); if(::scene.isInitialized)scene.pause(); finish();return false
    }
    private fun mayChange() = activeForeground && currentWorker()
    private fun ensureCamera(): ComponentCameraView {
        return camera ?: ComponentCameraView(this).also { view ->
            camera=view; sceneContainer.addView(view,FrameLayout.LayoutParams(-1,-1))
            view.onStatus={ message -> if(currentWorker()) { if(cameraStatus?.text?.toString()!=message) cameraStatus?.text=message; updateCameraActions() } }
            view.onVisible={ if(activeForeground && cameraSelected && !session.descriptions) recordPresentation("camera") }
        }
    }
    private fun recordPresentation(mode: String) {
        if(!mayChange() || session.stage !in 1..2 || session.answer!=null || session.descriptions || session.data.optBoolean(if(mode=="camera")"cameraSeen" else "screenSeen")) return
        session.notePresentation(mode); store.saveComponent(session.data)
    }
    private fun startCamera() {
        if(!mayChange() || !::session.isInitialized || !cameraSelected || session.done) return
        if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) ensureCamera().resumeCamera()
        else { cameraStatus?.text=t("Camera permission is off. Enable it to use AR, or continue on screen.","कैमरा अनुमति बंद है। AR के लिए अनुमति दें, या स्क्रीन पर जारी रखें।"); updateCameraActions() }
    }
    private fun switchCamera(value: Boolean) {
        if(!mayChange())return
        cameraSelected=value
        if(value) { session.useDescriptions(false); scene.pause() } else { camera?.pauseCamera(); if(activeForeground) scene.resume() }
        saveAndRender()
        if(value) {
            if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.CAMERA),41)
            else startCamera()
        }
    }
    private fun updateCameraActions() { cameraActions.forEach { it.isEnabled= !cameraSelected || camera?.ready()==true } }
    private fun gated(button: Button): Button { if(cameraSelected) { cameraActions.add(button); button.isEnabled=camera?.ready()==true }; return button }
    private fun mayInteract() = mayChange() && (!cameraSelected || camera?.ready()==true)
    private fun advance() { if(mayChange()) { session.advance(System.currentTimeMillis()); saveAndRender() } }


    private fun render() {
        if(!currentWorker() || !::session.isInitialized)return
        header.removeAllViews(); lower.removeAllViews(); cameraActions.clear(); cameraStatus=null
        header.add(label(t("Find the part","पुर्ज़ा पहचानें"),26f,Palette.ink,true).asHeading(),bottom=8)
        header.add(label(t("Personal practice · generic equipment · no certificate score","व्यक्तिगत अभ्यास · सामान्य उपकरण · प्रमाणपत्र का अंक नहीं"),13f,Palette.muted),bottom=12)
        if (session.done) {
            header.accessibilityPaneTitle=t("Practice complete","अभ्यास पूरा हुआ")
            sceneContainer.visibility = View.GONE; camera?.pauseCamera()
            header.add(label(t("Practice complete","अभ्यास पूरा हुआ"),23f,Palette.success,true).asHeading(),bottom=16)
            val summary = card(Palette.successBg)
            summary.add(label(t("${session.data.optInt("visualCorrect")} identifications without hints in the model; ${session.data.optInt("descriptionCorrect")} using descriptions.","मॉडल में ${session.data.optInt("visualCorrect")} पहचान बिना संकेत के; विवरण से ${session.data.optInt("descriptionCorrect")} पहचान।")),bottom=12)
            summary.add(label(t("Camera: ${session.data.optInt("cameraCorrect")} · screen: ${session.data.optInt("screenCorrect")} · both views: ${session.data.optInt("mixedPresentationCorrect")} (new unassisted visual records).", "कैमरा: ${session.data.optInt("cameraCorrect")} · स्क्रीन: ${session.data.optInt("screenCorrect")} · दोनों दृश्य: ${session.data.optInt("mixedPresentationCorrect")} (नए बिना मदद के दृश्य रिकॉर्ड)।"),14f,Palette.muted),bottom=12)
            summary.add(label(t("These are recognition exercises. They do not establish practical competence.","ये पहचान के अभ्यास हैं। इनसे व्यावहारिक योग्यता साबित नहीं होती।"),14f,Palette.muted))
            lower.add(summary,bottom=16)
            lower.add(label(t("Return in your review queue: ","दोहराव सूची में वापसी: ")+DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(session.data.getLong("dueAt")))),bottom=16)
            lower.add(action(t("Practise again now","अभी फिर अभ्यास करें")) { if(mayChange()) { session = ComponentSession.start(session.module,session.data); saveAndRender(); if(cameraSelected) startCamera() } })
            return
        }
        sceneContainer.visibility = if(session.descriptions) View.GONE else View.VISIBLE
        scene.visibility = if(session.descriptions || cameraSelected) View.GONE else View.VISIBLE
        camera?.visibility = if(cameraSelected && !session.descriptions) View.VISIBLE else View.GONE
        if(cameraSelected) {
            val view=ensureCamera(); view.visibility=View.VISIBLE
            view.configure(session,hi) { id -> if(cameraSelected && activeForeground && view.ready()) chooseNow(id,"camera") }
            cameraStatus=label(t("Waiting for a tracked model…","मॉडल की ट्रैकिंग की प्रतीक्षा…"),14f,Palette.muted)
            header.add(label(t("CAMERA PRACTICE · SIMULATED EQUIPMENT","कैमरा अभ्यास · काल्पनिक उपकरण"),13f,Palette.blue,true),bottom=8)
            header.add(cameraStatus!!,bottom=8)
            header.add(action(t("Continue on screen","स्क्रीन पर जारी रखें"),false) { switchCamera(false) },bottom=10)
        }
        if (session.answer == null) header.add(action(if(session.descriptions)t("Use the model","मॉडल उपयोग करें")else t("Use text descriptions","लिखित विवरण उपयोग करें"),false) { if(mayChange()) { if(cameraSelected) { cameraSelected=false; camera?.pauseCamera(); scene.resume() }; session.useDescriptions(!session.descriptions); saveAndRender() } },top=8)
        val stage = when(session.stage) { 0 -> t("1 · See an example","1 · उदाहरण देखें"); 1 -> t("2 · Find without labels","2 · बिना नाम के पहचानें"); else -> if (session.descriptions) t("3 · Recall with reordered choices","3 · बदले क्रम में याद करें") else t("3 · Find from another angle","3 · दूसरे कोण से पहचानें") }
        header.accessibilityPaneTitle=if(session.answer!=null)t("Part feedback: ","पुर्ज़े की प्रतिक्रिया: ")+session.target.title(hi) else t("Part ${session.index+1}: ","पुर्ज़ा ${session.index+1}: ")+stage
        header.add(label(t("Part ${session.index+1} of ${session.parts.size} · ","पुर्ज़ा ${session.index+1} / ${session.parts.size} · ")+stage,14f,Palette.blue,true),bottom=12)
        header.add(label((if (session.stage == 0) t("Meet: ","जानें: ") else t("Find: ","पहचानें: "))+session.target.title(hi),22f,Palette.ink,true).asHeading(),bottom=8)
        header.add(label(if (session.stage == 0) t("Notice the identifying cue, then try without labels.","पहचान का संकेत देखें, फिर बिना नाम के कोशिश करें।") else if (session.descriptions) t("Choose the description that matches this part.","इस पुर्ज़े से मिलता विवरण चुनें।") else t("Follow a line to the part, then choose its letter.","रेखा के सिरे पर पुर्ज़ा देखें, फिर उसका अक्षर चुनें।"),15f,Palette.muted),bottom=8)
        if(!cameraSelected) scene.configure(session,hi) { choose(it) }
        if (session.showLabels && !session.descriptions) {
            session.order.forEachIndexed { i,part -> lower.add(label("${('A'.code+i).toChar()} · ${part.title(hi)}",15f,if(part.id==session.target.id)Palette.blue else Palette.muted,part.id==session.target.id),bottom=6) }
        }
        if (session.stage == 0) {
            lower.add(label(session.target.description(hi)),top=10,bottom=10)
            lower.add(label(session.target.explain(hi),15f,Palette.muted),bottom=16)
            lower.add(action(t("Try without labels","बिना नाम के कोशिश करें")) { advance() },bottom=12)
        } else if (session.answer == null) {
            if (session.descriptions) session.order.forEach { part -> lower.add(action(part.description(hi),false) { choose(part.id) }.apply { tag = "component-description-${part.id}" },bottom=10) }
            if (session.helped) lower.add(label(if(session.descriptions) session.target.description(hi) else t("Labels are visible for this retry. Take your time.","इस कोशिश में नाम दिख रहे हैं। आराम से पहचानें।"),15f,Palette.blue),bottom=12)
            else lower.add(gated(action(t("Show a hint","संकेत दिखाएँ"),false,role=ActionRole.REVIEW) { if(mayInteract()) { session.hint(); saveAndRender() } }),bottom=10)
            lower.add(gated(action(t("I’m not sure","मुझे निश्चित नहीं है"),false) { choose("_unsure") }),bottom=10)
        } else {
            val feedback = card(if(session.correct)Palette.successBg else Palette.amberBg)
            feedback.add(label(if(session.correct)t("You found it","आपने पहचान लिया")else t("Let’s look at the cue","पहचान का संकेत देखें"),20f,Palette.ink,true).asHeading(),bottom=10)
            feedback.add(label(session.target.description(hi)),bottom=10)
            feedback.add(label(session.target.explain(hi),15f,Palette.muted),bottom=12)
            feedback.add(label(t("In your own words: what helped you recognize this part?","अपने शब्दों में: इस पुर्ज़े को पहचानने में किस बात ने मदद की?"),16f,Palette.ink,true))
            lower.add(feedback,bottom=14)
            lower.add(action(if(!session.correct)t("Try with guidance","मदद के साथ कोशिश करें")else if(session.stage==1)if(session.descriptions)t("Try reordered choices","बदले क्रम में कोशिश करें")else t("Try the changed view","बदले दृश्य में कोशिश करें")else t("Continue","आगे बढ़ें")) { advance() },bottom=12)
        }

        if(session.module=="fire") {
            if(!cameraSelected) lower.add(action(t("Use camera AR · fire","कैमरा AR उपयोग करें · आग"),false,role=ActionRole.CAMERA) { switchCamera(true) },top=12)
            else {
                lower.add(label(t("Use a clear training surface. The model turns between stages; stay in one safe position. This does not detect real equipment.","खाली प्रशिक्षण सतह उपयोग करें। चरणों के बीच मॉडल घूमता है; एक सुरक्षित जगह पर रहें। यह असली उपकरण नहीं पहचानता।"),14f,Palette.muted),top=12,bottom=10)
                lower.add(action(t("Place model again","मॉडल फिर रखें"),false,role=ActionRole.CAMERA) { if(mayChange()) { camera?.reposition(); updateCameraActions() } },bottom=10)
                lower.add(action(t("Retry camera","कैमरा फिर आज़माएँ"),false,role=ActionRole.CAMERA) { if(mayChange()) { camera?.prepareRetry(); switchCamera(true) } },bottom=10)
                if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) lower.add(action(t("Camera permission settings","कैमरा अनुमति सेटिंग"),false,role=ActionRole.NEUTRAL) {
                    if(mayChange())startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:$packageName")))
                })
            }
        }
    }
    private fun choose(id: String) {
        if(!mayInteract())return
        if(cameraSelected) camera?.requestChoice(id) else chooseNow(id,"screen")
    }
    private fun chooseNow(id: String, presentation: String) { if(!mayChange())return; session.choose(id,System.currentTimeMillis(),presentation); saveAndRender() }
    private fun saveAndRender() { if(!mayChange())return; store.saveComponent(session.data); render(); scroll.post { scroll.firstPage() } }
}
