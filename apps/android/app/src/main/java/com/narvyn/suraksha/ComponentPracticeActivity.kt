package com.narvyn.suraksha

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import java.text.DateFormat
import java.util.Date

class ComponentPracticeActivity: Activity() {
    private lateinit var store: Store
    private lateinit var session: ComponentSession
    private lateinit var scene: ComponentSceneView
    private lateinit var header: LinearLayout
    private lateinit var lower: LinearLayout
    private lateinit var scroll: ScrollView
    private val hi get() = store.hi
    private fun t(en: String, hindi: String) = if (hi) hindi else en

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        store = Store(this)
        val module = intent.getStringExtra("moduleId")?.takeIf { ComponentCatalog.modules.containsKey(it) } ?: "fire"
        session = ComponentSession.restore(module,store.componentRecords()[module])
        // Consume the launch request once; recreation or later reopening must not discard an active session.
        if (state == null && intent.getBooleanExtra("startReview",false) && session.done) session = ComponentSession.start(module,session.data)
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
        scene = ComponentSceneView(this,module)
        body.add(header)
        body.addView(scene,LinearLayout.LayoutParams(-1,dp(330)))
        body.add(lower,top=12)
        scroll = ScrollView(this).apply { isFillViewport = true; addView(body) }
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        root.add(action(t("Save & return","सहेजें और लौटें"),false) { finish() },top=12)
        setContentView(root)
        render()
    }
    override fun onResume() { super.onResume(); if (::scene.isInitialized) scene.resume() }
    override fun onPause() { if (::scene.isInitialized) scene.pause(); super.onPause() }
    override fun onDestroy() { store.close(); super.onDestroy() }

    private fun render() {
        header.removeAllViews(); lower.removeAllViews()
        header.add(label(t("Find the part","पुर्ज़ा पहचानें"),26f,Palette.ink,true),bottom=8)
        header.add(label(t("Personal practice · generic equipment · no certificate score","व्यक्तिगत अभ्यास · सामान्य उपकरण · प्रमाणपत्र का अंक नहीं"),13f,Palette.muted),bottom=12)
        if (session.done) {
            scene.visibility = View.GONE
            header.add(label(t("Practice complete","अभ्यास पूरा हुआ"),23f,Palette.success,true),bottom=16)
            val summary = card(Palette.successBg)
            summary.add(label(t("${session.data.optInt("visualCorrect")} identifications without hints in the model; ${session.data.optInt("descriptionCorrect")} using descriptions.","मॉडल में ${session.data.optInt("visualCorrect")} पहचान बिना संकेत के; विवरण से ${session.data.optInt("descriptionCorrect")} पहचान।")),bottom=12)
            summary.add(label(t("These are recognition exercises. They do not establish practical competence.","ये पहचान के अभ्यास हैं। इनसे व्यावहारिक योग्यता साबित नहीं होती।"),14f,Palette.muted))
            lower.add(summary,bottom=16)
            lower.add(label(t("Return in your review queue: ","दोहराव सूची में वापसी: ")+DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(session.data.getLong("dueAt")))),bottom=16)
            lower.add(action(t("Practise again now","अभी फिर अभ्यास करें")) { session = ComponentSession.start(session.module,session.data); saveAndRender() })
            return
        }
        scene.visibility = if (session.descriptions) View.GONE else View.VISIBLE
        val stage = when(session.stage) { 0 -> t("1 · See an example","1 · उदाहरण देखें"); 1 -> t("2 · Find without labels","2 · बिना नाम के पहचानें"); else -> if (session.descriptions) t("3 · Recall with reordered choices","3 · बदले क्रम में याद करें") else t("3 · Find from another angle","3 · दूसरे कोण से पहचानें") }
        header.add(label(t("Part ${session.index+1} of ${session.parts.size} · ","पुर्ज़ा ${session.index+1} / ${session.parts.size} · ")+stage,14f,Palette.blue,true),bottom=12)
        header.add(label((if (session.stage == 0) t("Meet: ","जानें: ") else t("Find: ","पहचानें: "))+session.target.title(hi),22f,Palette.ink,true),bottom=8)
        header.add(label(if (session.stage == 0) t("Notice the identifying cue, then try without labels.","पहचान का संकेत देखें, फिर बिना नाम के कोशिश करें।") else if (session.descriptions) t("Choose the description that matches this part.","इस पुर्ज़े से मिलता विवरण चुनें।") else t("Follow a line to the part, then choose its letter.","रेखा के सिरे पर पुर्ज़ा देखें, फिर उसका अक्षर चुनें।"),15f,Palette.muted),bottom=8)
        scene.configure(session,hi) { choose(it) }
        if (session.showLabels && !session.descriptions) {
            session.order.forEachIndexed { i,part -> lower.add(label("${('A'.code+i).toChar()} · ${part.title(hi)}",15f,if(part.id==session.target.id)Palette.blue else Palette.muted,part.id==session.target.id),bottom=6) }
        }
        if (session.stage == 0) {
            lower.add(label(session.target.description(hi)),top=10,bottom=10)
            lower.add(label(session.target.explain(hi),15f,Palette.muted),bottom=16)
            lower.add(action(t("Try without labels","बिना नाम के कोशिश करें")) { session.advance(System.currentTimeMillis()); saveAndRender() },bottom=12)
        } else if (session.answer == null) {
            if (session.descriptions) session.order.forEach { part -> lower.add(action(part.description(hi),false) { choose(part.id) }.apply { tag = "component-description-${part.id}" },bottom=10) }
            if (session.helped) lower.add(label(if(session.descriptions) session.target.description(hi) else t("Labels are visible for this retry. Take your time.","इस कोशिश में नाम दिख रहे हैं। आराम से पहचानें।"),15f,Palette.blue),bottom=12)
            else lower.add(action(t("Show a hint","संकेत दिखाएँ"),false) { session.hint(); saveAndRender() },bottom=10)
            lower.add(action(t("I’m not sure","मुझे निश्चित नहीं है"),false) { choose("_unsure") },bottom=10)
        } else {
            val feedback = card(if(session.correct)Palette.successBg else Palette.amberBg)
            feedback.add(label(if(session.correct)t("You found it","आपने पहचान लिया")else t("Let’s look at the cue","पहचान का संकेत देखें"),20f,Palette.ink,true),bottom=10)
            feedback.add(label(session.target.description(hi)),bottom=10)
            feedback.add(label(session.target.explain(hi),15f,Palette.muted),bottom=12)
            feedback.add(label(t("In your own words: what helped you recognize this part?","अपने शब्दों में: इस पुर्ज़े को पहचानने में किस बात ने मदद की?"),16f,Palette.ink,true))
            lower.add(feedback,bottom=14)
            lower.add(action(if(!session.correct)t("Try with guidance","मदद के साथ कोशिश करें")else if(session.stage==1)if(session.descriptions)t("Try reordered choices","बदले क्रम में कोशिश करें")else t("Try the changed view","बदले दृश्य में कोशिश करें")else t("Continue","आगे बढ़ें")) { session.advance(System.currentTimeMillis()); saveAndRender() },bottom=12)
        }
        if (session.answer == null) lower.add(action(if(session.descriptions)t("Use the model","मॉडल उपयोग करें")else t("Use text descriptions","लिखित विवरण उपयोग करें"),false) { session.useDescriptions(!session.descriptions); saveAndRender() },top=8)
    }
    private fun choose(id: String) { session.choose(id,System.currentTimeMillis()); saveAndRender() }
    private fun saveAndRender() { store.saveComponent(session.data); render(); scroll.post { scroll.scrollTo(0,0) } }
}
