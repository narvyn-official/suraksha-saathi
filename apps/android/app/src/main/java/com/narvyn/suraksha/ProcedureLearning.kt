package com.narvyn.suraksha

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Draft generic sequences; require competent safety and native-language review before field use.
 * References checked 2026-09-15 (general training principles, not Indian statutory authority):
 * https://www.osha.gov/etools/evacuation-plans-procedures/emergency-standards/portable-extinguishers/use
 * https://www.osha.gov/laws-regs/regulations/standardnumber/1910/1910.146
 * These scene actions cannot measure gas, authorise entry, or establish practical competence.
 */
object ProcedureCatalog {
    const val VERSION = 1
    data class Action(val id: String, val label: String, val hindi: String, val correct: Boolean,
                      val point: FloatArray = floatArrayOf(0f, .25f, .08f)) {
        fun text(hi: Boolean) = if (hi) hindi else label
    }
    data class Step(val id: String, val title: String, val hindi: String, val explanation: String,
                    val hindiExplanation: String, val actions: List<Action>) {
        fun text(hi: Boolean) = if (hi) hindi else title
        fun explain(hi: Boolean) = if (hi) hindiExplanation else explanation
    }
    data class Module(val id: String, val title: String, val hindi: String, val steps: List<Step>) {
        fun text(hi: Boolean) = if (hi) hindi else title
    }
    private fun task(id: String, title: String, hi: String, why: String, whyHi: String,
                     action: String, actionHi: String, unsafe: String, unsafeHi: String,
                     point: FloatArray = floatArrayOf(-.22f,.28f,.12f)) = Step(id,title,hi,why,whyHi,listOf(
        Action(id,action,actionHi,true,point),
        Action("$id-unsafe",unsafe,unsafeHi,false,floatArrayOf(.28f,.28f,.12f))
    ))
    val modules = mapOf(
        "fire" to Module("fire","Fire response sequence","आग पर प्रतिक्रिया का क्रम",listOf(
            task("fire-alarm","A small simulated fire has appeared.","एक छोटी काल्पनिक आग दिखाई दी है।",
                "Raise the alarm and follow the site's emergency plan. The training scene is not a live emergency guide.","अलार्म दें और कार्यस्थल की आपात योजना अपनाएँ। यह दृश्य वास्तविक आपात स्थिति का मार्गदर्शक नहीं है।",
                "Activate the alarm","अलार्म चालू करें","Approach without alerting anyone","बिना बताए पास जाएँ",floatArrayOf(-.38f,.40f,.02f)),
            task("fire-authorisation","The scene assigns a trained, authorised responder with a clear retreat path.","दृश्य में प्रशिक्षित, अधिकृत व्यक्ति और पीछे हटने का खुला रास्ता है।",
                "Continue this simulated role only under those conditions. An untrained or unauthorised worker must withdraw; the app grants no authorisation.","केवल इन शर्तों वाले काल्पनिक दृश्य में आगे बढ़ें। अप्रशिक्षित या अनधिकृत व्यक्ति पीछे हटे; ऐप कोई अधिकार नहीं देता।",
                "Check role and clear retreat path","भूमिका और खुला वापसी रास्ता जाँचें","Proceed without role or route checks","भूमिका या रास्ता जाँचे बिना बढ़ें"),
            task("fire-label","The training extinguisher has not yet been checked.","प्रशिक्षण अग्निशामक की जाँच अभी नहीं हुई है।",
                "This scene specifies a suitable serviceable extinguisher for the small initial fire. Actual labels and site instructions determine suitability.","इस दृश्य में छोटी शुरुआती आग के लिए उपयुक्त, ठीक अग्निशामक निर्धारित है। वास्तविक लेबल और कार्यस्थल के निर्देश उपयुक्तता तय करते हैं।",
                "Inspect label and condition","लेबल और स्थिति देखें","Use any available extinguisher","कोई भी अग्निशामक चलाएँ",floatArrayOf(0f,.26f,.10f)),
            task("fire-pin","The role, retreat path and extinguisher checks are complete in this scene.","इस दृश्य में भूमिका, वापसी रास्ते और अग्निशामक की जाँच पूरी है।",
                "For the illustrated equipment, remove the safety pin before the next action. Real mechanisms vary.","दिखाए उपकरण में अगले कदम से पहले सुरक्षा पिन निकालें। असली उपकरणों की बनावट अलग हो सकती है।",
                "Pull the safety pin","सुरक्षा पिन निकालें","Force the handle with pin fitted","पिन लगी होने पर हैंडल ज़ोर से दबाएँ",floatArrayOf(0f,.49f,.02f)),
            task("fire-aim","The pin is removed. The nozzle is not yet aimed.","पिन निकल गई है। नोज़ल का निशाना अभी तय नहीं है।",
                "Aim at the base in this authorised practice scene, keeping the safe retreat path available.","इस अधिकृत अभ्यास दृश्य में आधार पर निशाना लगाएँ और पीछे हटने का सुरक्षित रास्ता खुला रखें।",
                "Aim at the fire base","आग के आधार पर निशाना लगाएँ","Aim above the flames","लपटों के ऊपर निशाना लगाएँ",floatArrayOf(-.12f,.07f,-.25f)),
            task("fire-squeeze","The nozzle is aimed at the base.","नोज़ल का निशाना आधार पर है।",
                "Squeeze the illustrated handle to begin simulated discharge. This gesture does not reproduce real grip force or discharge range.","काल्पनिक निर्वहन शुरू करने के लिए दिखाया हैंडल दबाएँ। यह इशारा असली पकड़ का बल या निर्वहन दूरी नहीं दिखाता।",
                "Squeeze the handle","हैंडल दबाएँ","Turn the nozzle toward yourself","नोज़ल अपनी ओर करें",floatArrayOf(0f,.52f,0f)),
            task("fire-sweep-left","Simulated discharge is now active.","काल्पनिक निर्वहन अब चालू है।",
                "The left target begins a side-to-side sweep. These targets rehearse sequence, not extinguishing performance.","बायाँ लक्ष्य दाएँ-बाएँ चलाने की शुरुआत है। ये लक्ष्य क्रम का अभ्यास हैं, आग बुझाने की क्षमता का प्रमाण नहीं।",
                "Sweep to the left base target","आधार के बाएँ लक्ष्य तक चलाएँ","Raise the stream above the fire","धार आग के ऊपर उठाएँ",floatArrayOf(-.24f,.06f,-.27f)),
            task("fire-sweep-right","The nozzle has reached the left side of the base.","नोज़ल आधार के बाएँ हिस्से तक पहुँची है।",
                "Continue across the base to the right target. Keep watching the situation and the retreat path.","आधार पर दाएँ लक्ष्य तक चलाएँ। हालात और पीछे हटने के रास्ते पर ध्यान रखें।",
                "Sweep to the right base target","आधार के दाएँ लक्ष्य तक चलाएँ","Hold above the base","आधार के ऊपर रोकें",floatArrayOf(.20f,.06f,-.27f)),
            task("fire-sweep-return","The nozzle has reached the right side of the base.","नोज़ल आधार के दाएँ हिस्से तक पहुँची है।",
                "Return across the base in the simulation. Real response must stop when conditions become unsafe.","दृश्य में आधार पर वापस चलाएँ। हालात असुरक्षित होने पर वास्तविक कार्रवाई रोकनी चाहिए।",
                "Sweep back to the left target","बाएँ लक्ष्य की ओर वापस चलाएँ","Move closer into smoke","धुएँ में और पास जाएँ",floatArrayOf(-.24f,.06f,-.27f)),
            task("fire-withdraw","Conditions now worsen in the simulation.","दृश्य में अब हालात बिगड़ते हैं।",
                "Stop the attempt and withdraw. A worsening fire, smoke or threatened escape route is a reason to leave.","कोशिश रोकें और पीछे हटें। बढ़ती आग, धुआँ या निकास पर खतरा बाहर जाने का कारण है।",
                "Stop discharge and withdraw","निर्वहन रोकें और पीछे हटें","Continue fighting the growing fire","बढ़ती आग बुझाने की कोशिश जारी रखें",floatArrayOf(-.38f,.18f,.20f)),
            task("fire-exit","You have withdrawn. The marked left exit is clear; the other route is blocked.","आप पीछे हट गए हैं। चिह्नित बायाँ निकास खुला है; दूसरा रास्ता बंद है।",
                "Choose the designated clear route in this scene. Virtual exits are not real-site navigation.","इस दृश्य में निर्धारित खुला रास्ता चुनें। काल्पनिक निकास असली कार्यस्थल का रास्ता नहीं बताते।",
                "Use the marked clear exit","चिह्नित खुले निकास से जाएँ","Take the blocked route","बंद रास्ते से जाएँ",floatArrayOf(-.42f,.36f,-.12f)),
            task("fire-assembly","You are outside at the designated assembly point.","आप बाहर निर्धारित एकत्र होने की जगह पर हैं।",
                "Report your arrival and missing-person information to the responsible person. Do not re-enter to search or collect belongings.","ज़िम्मेदार व्यक्ति को अपने पहुँचने और लापता लोगों की जानकारी दें। खोजने या सामान लेने दोबारा अंदर न जाएँ।",
                "Report at the assembly point","एकत्र होने की जगह पर जानकारी दें","Go back for belongings","सामान लेने वापस जाएँ",floatArrayOf(-.18f,.18f,.18f))
        )),
        "gas" to Module("gas","Confined-space entry boundary","सीमित स्थान के प्रवेश की सीमा",listOf(
            task("gas-boundary","An unverified confined space is ahead.","आगे बिना जाँच वाला सीमित स्थान है।",
                "Keep people outside the exclusion boundary. A phone camera cannot detect a safe atmosphere.","लोगों को निषेध सीमा से बाहर रखें। फ़ोन कैमरा सुरक्षित वातावरण की जाँच नहीं कर सकता।",
                "Place the exclusion barrier","प्रवेश रोकने की बाधा लगाएँ","Cross the unverified boundary","बिना जाँच की सीमा पार करें",floatArrayOf(-.30f,.22f,.10f)),
            task("gas-permit","The boundary is secured. Entry controls have not yet been reviewed.","सीमा सुरक्षित है। प्रवेश के नियंत्रणों की समीक्षा अभी नहीं हुई है।",
                "Review the site permit with the responsible authorised person. A permit alone does not make entry safe.","ज़िम्मेदार अधिकृत व्यक्ति के साथ कार्यस्थल का अनुमति पत्र देखें। केवल अनुमति पत्र प्रवेश को सुरक्षित नहीं बनाता।",
                "Review the permit requirements","अनुमति पत्र की शर्तें देखें","Treat a signature as full clearance","हस्ताक्षर को पूरी अनुमति मानें"),
            task("gas-isolation","The permit calls for equipment-specific isolation.","अनुमति पत्र में उपकरण-विशिष्ट अलगाव ज़रूरी है।",
                "The authorised person must verify required isolation under the site procedure. The learner does not operate live isolators here.","अधिकृत व्यक्ति कार्यस्थल की प्रक्रिया से ज़रूरी अलगाव की पुष्टि करे। यहाँ शिक्षार्थी असली अलगाव उपकरण नहीं चलाता।",
                "Request authorised isolation verification","अधिकृत व्यक्ति से अलगाव की पुष्टि लें","Rely on a stopped machine","रुकी मशीन को पर्याप्त मानें",floatArrayOf(-.22f,.36f,.06f)),
            task("gas-atmosphere","Isolation is recorded in the scenario. Atmospheric assessment remains unconfirmed.","दृश्य में अलगाव दर्ज है। वातावरण के आकलन की पुष्टि अभी नहीं है।",
                "Request authorised atmospheric assessment and required controls, including ventilation and monitoring. SIM is not a gas or oxygen reading.","अधिकृत वातावरण जाँच और ज़रूरी नियंत्रण लें, जिनमें वेंटिलेशन और निगरानी शामिल हैं। SIM गैस या ऑक्सीजन की रीडिंग नहीं है।",
                "Obtain the authorised assessment","अधिकृत जाँच प्राप्त करें","Use the phone display as a reading","फ़ोन के डिस्प्ले को रीडिंग मानें",floatArrayOf(0f,.30f,.09f)),
            task("gas-ppe","The scenario identifies hazards and the worker's role.","दृश्य में खतरे और कर्मचारी की भूमिका तय हैं।",
                "PPE must match the assessed hazards and role. A dust mask does not resolve oxygen deficiency or an unknown toxic atmosphere.","पीपीई जाँचे खतरों और भूमिका के अनुसार हो। धूल वाला मास्क ऑक्सीजन की कमी या अज्ञात जहरीले वातावरण का समाधान नहीं है।",
                "Check the specified PPE suitability","निर्धारित पीपीई की उपयुक्तता जाँचें","Use a dust mask for any atmosphere","हर वातावरण के लिए धूल मास्क लें"),
            task("gas-attendant","The team is assembled. No attendant position is assigned.","दल इकट्ठा है। निगरानी करने वाले की जगह तय नहीं है।",
                "Assign an attendant outside with the duties in the site procedure. Sending both buddies inside removes that protection.","कार्यस्थल की प्रक्रिया के कर्तव्यों वाला व्यक्ति बाहर नियुक्त करें। दोनों साथियों को अंदर भेजने से यह सुरक्षा हटती है।",
                "Position the attendant outside","निगरानी करने वाले को बाहर रखें","Send both buddies inside","दोनों साथियों को अंदर भेजें",floatArrayOf(-.36f,.32f,.16f)),
            task("gas-communication","The attendant is outside. Team communication has not yet been checked.","निगरानी करने वाला बाहर है। दल के संचार की जाँच अभी नहीं हुई है।",
                "Check communication and the withdrawal signal with the team. Devices and procedures must suit the actual assessed hazards.","दल के साथ संचार और बाहर आने का संकेत जाँचें। साधन और प्रक्रिया असली जाँचे खतरों के अनुकूल हों।",
                "Test communication and withdrawal signal","संचार और बाहर आने का संकेत जाँचें","Assume shouting will be enough","मान लें कि चिल्लाना पर्याप्त होगा"),
            task("gas-rescue","The rescue contact now fails to respond in this scenario.","इस दृश्य में बचाव संपर्क अब जवाब नहीं देता।",
                "Rescue readiness remains unconfirmed. Mark that prerequisite as missing and alert the entry supervisor; do not invent a rescue plan.","बचाव की तैयारी की पुष्टि नहीं है। इस शर्त को अधूरा चिन्हित करें और प्रवेश पर्यवेक्षक को बताएँ; अपनी ओर से बचाव योजना न बनाएँ।",
                "Flag rescue readiness as unconfirmed","बचाव की तैयारी अपुष्ट चिन्हित करें","Treat no response as rescue readiness","जवाब न मिलने को तैयारी मानें",floatArrayOf(-.22f,.42f,.12f)),
            task("gas-refuse-entry","Rescue readiness is missing, despite the earlier checks.","पहले की जाँचों के बावजूद बचाव की तैयारी अधूरी है।",
                "Keep entry closed and escalate. This sequence ends outside; it never grants entry clearance or instructs an unplanned rescue.","प्रवेश बंद रखें और ज़िम्मेदार अधिकारी को बताएँ। यह क्रम बाहर ही समाप्त होता है; यह प्रवेश अनुमति या बिना योजना बचाव का निर्देश नहीं देता।",
                "Keep the barrier closed and refuse entry","बाधा बंद रखें और प्रवेश न करें","Enter because other checks passed","बाकी जाँचें पूरी होने पर अंदर जाएँ",floatArrayOf(-.30f,.22f,.10f))
        ))
    )
}

/** Versioned personal procedural evidence, deliberately separate from curriculum certification. */
class ProcedureSession private constructor(val data: JSONObject) {
    val module: String get() = data.getString("module")
    val index: Int get() = data.getInt("index")
    val step: ProcedureCatalog.Step get() = ProcedureCatalog.modules.getValue(module).steps[index]
    val done: Boolean get() = data.getBoolean("finished")
    val guided: Boolean get() = data.getBoolean("guided")
    val feedback: Boolean get() = data.getBoolean("feedback")
    private val events get() = data.getJSONArray("events")
    private fun mayChange(now: Long) = !done && events.length() < MAX_EVENTS && now >= data.getLong("updatedAt")

    /** True means a valid action was accepted, including an unsafe action; inspect lastCorrect only in practice feedback. */
    fun choose(actionId: String, presentation: String, now: Long): Boolean {
        if (!mayChange(now) || feedback || presentation !in setOf("screen","camera","description")) return false
        val action = step.actions.firstOrNull { it.id == actionId } ?: return false
        events.put(JSONObject().put("type","action").put("sequence",events.length()+1).put("step",step.id)
            .put("action",action.id).put("presentation",presentation).put("correct",action.correct).put("time",now))
        data.put("updatedAt",now).put("lastCorrect",action.correct).put("feedback",true)
        if (action.correct) data.getJSONObject("flags").put(action.id,true)
        else {
            val failures = data.getJSONArray("criticalFailures")
            if ((0 until failures.length()).none { failures.getString(it) == step.id }) failures.put(step.id)
            if (!guided) finish(complete=false,stopped=true)
        }
        return true
    }
    fun advance(): Boolean = advanceAt(maxOf(System.currentTimeMillis(),data.getLong("updatedAt")))
    private fun advanceAt(now: Long): Boolean {
        if (!mayChange(now) || !feedback) return false
        events.put(JSONObject().put("type","advance").put("sequence",events.length()+1).put("step",step.id).put("time",now))
        data.put("updatedAt",now).put("feedback",false)
        if (!data.getBoolean("lastCorrect")) return true // guided retry of exactly the same step
        if (index == ProcedureCatalog.modules.getValue(module).steps.lastIndex) finish(complete=true,stopped=false)
        else data.put("index",index+1)
        return true
    }
    private fun finish(complete: Boolean, stopped: Boolean) {
        data.put("finished",true).put("completedAt",data.getLong("updatedAt"))
            .put("result",JSONObject().put("complete",complete).put("stopped",stopped)
                .put("criticalFailures",JSONArray(data.getJSONArray("criticalFailures").toString()))
                .put("practical","not-assessed").put("certifiable",false))
    }
    companion object {
        private const val MAX_EVENTS = 512
        fun create(module: String, workerId: String, guided: Boolean): ProcedureSession = fresh(module,workerId,guided,UUID.randomUUID().toString(),System.currentTimeMillis())
        private fun fresh(module: String, workerId: String, guided: Boolean, id: String, now: Long): ProcedureSession {
            require(ProcedureCatalog.modules.containsKey(module)) { "Unknown procedure module." }
            require(workerId.isNotBlank() && workerId.length <= 128 && now >= 0) { "Invalid procedure identity or time." }
            return ProcedureSession(JSONObject().put("schemaVersion",1).put("catalogVersion",ProcedureCatalog.VERSION)
                .put("id",id).put("module",module).put("workerId",workerId).put("guided",guided)
                .put("createdAt",now).put("updatedAt",now).put("index",0).put("finished",false).put("feedback",false)
                .put("lastCorrect",false).put("flags",JSONObject()).put("events",JSONArray()).put("criticalFailures",JSONArray()))
        }
        /** Replay the journal, rejecting malformed/stale events and fabricated derived state. Never silently advance a saved session. */
        fun restore(record: JSONObject): ProcedureSession {
            require(record.getInt("schemaVersion") == 1 && record.getInt("catalogVersion") == ProcedureCatalog.VERSION) { "Unsupported procedure version." }
            val id = record.getString("id")
            require(UUID.fromString(id).toString().equals(id,true)) { "Invalid procedure ID." }
            val session = fresh(record.getString("module"),record.getString("workerId"),record.getBoolean("guided"),id,record.getLong("createdAt"))
            val savedEvents = record.getJSONArray("events")
            require(savedEvents.length() <= MAX_EVENTS) { "Procedure journal is too long." }
            for (i in 0 until savedEvents.length()) {
                val event = savedEvents.getJSONObject(i)
                require(event.getInt("sequence") == i+1 && event.getString("step") == session.step.id) { "Invalid procedure event order." }
                val accepted = when (event.getString("type")) {
                    "action" -> session.choose(event.getString("action"),event.getString("presentation"),event.getLong("time"))
                    "advance" -> session.advanceAt(event.getLong("time"))
                    else -> false
                }
                require(accepted) { "Invalid procedure event." }
                if (event.getString("type") == "action") require(event.getBoolean("correct") == session.data.getBoolean("lastCorrect")) { "Invalid action outcome." }
            }
            for (key in listOf("index","finished","feedback","lastCorrect","updatedAt","flags","criticalFailures","result","completedAt")) {
                require(record.opt(key)?.toString() == session.data.opt(key)?.toString()) { "Inconsistent procedure state: $key." }
            }
            return session
        }
    }
}
