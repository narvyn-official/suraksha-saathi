package com.narvyn.suraksha

import org.json.JSONArray
import org.json.JSONObject

/** Recognition content for the generic meshes, independently versioned from scored safety decisions. */
object ComponentCatalog {
    const val VERSION = 1
    data class Part(val id: String, val name: String, val hindi: String, val cue: String, val hindiCue: String,
                    val explanation: String, val hindiExplanation: String, val point: FloatArray) {
        fun title(hi: Boolean) = if (hi) hindi else name
        fun description(hi: Boolean) = if (hi) hindiCue else cue
        fun explain(hi: Boolean) = if (hi) hindiExplanation else explanation
    }
    val modules = mapOf(
        "fire" to listOf(
            Part("label", "Vessel label", "पात्र का लेबल", "White panel on the red vessel", "लाल पात्र पर सफ़ेद पट्टी",
                "The label belongs to the vessel. Read the actual equipment label with a trainer; this illustration does not identify a fire class.", "लेबल पात्र पर है। प्रशिक्षक के साथ असली उपकरण का लेबल पढ़ें; यह चित्र आग का वर्ग नहीं बताता।", floatArrayOf(0f,.256f,.089f)),
            Part("gauge", "Illustrative gauge", "काल्पनिक गेज", "Small round dial near the top", "ऊपर की ओर छोटी गोल डायल",
                "The round dial is an illustrative gauge. It has no valid pressure reading.", "गोल डायल काल्पनिक गेज है। यह मान्य दबाव नहीं बताता।", floatArrayOf(0f,.455f,.062f)),
            Part("nozzle", "Discharge nozzle", "निर्वहन नोज़ल", "Thicker dark end of the curved hose", "मुड़ी हुई होज़ का मोटा गहरा सिरा",
                "The nozzle is at the hose end. Equipment types differ; identifying it does not teach an operating sequence.", "नोज़ल होज़ के सिरे पर है। उपकरण के प्रकार अलग होते हैं; पहचान से संचालन के चरण नहीं सीखे जाते।", floatArrayOf(.120f,.142f,.010f))
        ),
        "gas" to listOf(
            Part("openings", "Sensor openings", "सेंसर छिद्र", "Narrow dark slots above the display", "डिस्प्ले के ऊपर पतले गहरे छिद्र",
                "The narrow slots represent openings in a generic detector. This phone and model cannot measure gas or oxygen.", "पतले छिद्र सामान्य डिटेक्टर के छिद्र दिखाते हैं। फ़ोन और मॉडल गैस या ऑक्सीजन नहीं माप सकते।", floatArrayOf(0f,.400f,.067f)),
            Part("display", "Simulated display", "काल्पनिक डिस्प्ले", "Green rectangular panel marked SIM", "SIM लिखा हरा आयताकार पैनल",
                "SIM identifies a simulated display. The markings are not operational readings.", "SIM काल्पनिक डिस्प्ले की पहचान है। निशान वास्तविक रीडिंग नहीं हैं।", floatArrayOf(0f,.300f,.088f)),
            Part("control", "Central control button", "बीच का नियंत्रण बटन", "Small blue circle below the display", "डिस्प्ले के नीचे छोटा नीला गोल बटन",
                "This is a control on the illustrative housing. Real detector controls and checks depend on the equipment instructions.", "यह चित्र के खोल पर नियंत्रण है। असली डिटेक्टर के नियंत्रण और जाँच उपकरण के निर्देश पर निर्भर हैं।", floatArrayOf(0f,.165f,.086f))
        ),
        "machinery" to listOf(
            Part("guard", "Mesh guard", "जाली गार्ड", "Metal grid inside the yellow frame", "पीले फ्रेम के अंदर धातु की जाली",
                "The guard separates people from moving parts. Report missing or damaged guards; never reach through them.", "गार्ड लोगों को चलते पुर्ज़ों से अलग करता है। गायब या खराब गार्ड की सूचना दें; अंदर हाथ न डालें।", floatArrayOf(-.05f,.258f,.218f)),
            Part("stop", "Stop control", "स्टॉप नियंत्रण", "Raised red button on a yellow surround", "पीले घेरे पर उभरा लाल बटन",
                "A stop control does not prove effective energy isolation. Equipment-specific isolation is for authorised people.", "स्टॉप नियंत्रण प्रभावी ऊर्जा अलगाव साबित नहीं करता। उपकरण-विशिष्ट अलगाव अधिकृत लोगों का काम है।", floatArrayOf(.287f,.369f,.151f)),
            Part("lock", "Illustrative lock", "काल्पनिक ताला", "Blue block with a metal loop below the controls", "नियंत्रण के नीचे धातु के छल्ले वाला नीला खंड",
                "The lock is a separate part of this illustration. A fitted lock alone does not prove effective isolation.", "ताला इस चित्र का अलग पुर्ज़ा है। केवल लगा ताला प्रभावी अलगाव साबित नहीं करता।", floatArrayOf(.287f,.237f,.140f))
        ),
        "emergency" to listOf(
            Part("radio", "Reporting radio", "सूचना रेडियो", "Yellow and dark handset with an antenna", "एंटीना वाला पीला और गहरा हैंडसेट",
                "This generic radio cannot transmit. Use the site's approved reporting method from a safe place.", "यह सामान्य रेडियो प्रसारण नहीं कर सकता। सुरक्षित जगह से स्थल का स्वीकृत सूचना तरीका उपयोग करें।", floatArrayOf(-.255f,.218f,.106f)),
            Part("aid-case", "First-aid case", "प्राथमिक सहायता किट", "Closed green case with a handle and white plus", "हैंडल और सफ़ेद जोड़ चिह्न वाली बंद हरी किट",
                "This case represents supplies for a trained first aider. Identifying it does not teach treatment or rescue.", "यह किट प्रशिक्षित प्राथमिक सहायक की सामग्री दिखाती है। इसकी पहचान उपचार या बचाव नहीं सिखाती।", floatArrayOf(0f,.156f,.156f)),
            Part("assembly", "Assembly marker", "एकत्र होने की जगह का चिह्न", "Green sign showing people on a tall metal post", "ऊँचे धातु खंभे पर लोगों वाला हरा चिह्न",
                "The marker represents an assembly point. Follow the actual site's assigned safe route and accountability procedure.", "चिह्न एकत्र होने की जगह दिखाता है। असली स्थल का निर्धारित सुरक्षित रास्ता और उपस्थिति प्रक्रिया मानें।", floatArrayOf(.26f,.401f,-.048f))
        ),
        "ppe" to listOf(
            Part("helmet", "Helmet shell", "हेलमेट का खोल", "Rounded yellow crown above the lenses", "लेंस के ऊपर गोल पीला ऊपरी हिस्सा",
                "The shell is the outer part of the helmet. Actual equipment needs damage and fit checks; this model is not an approval mark.", "खोल हेलमेट का बाहरी हिस्सा है। असली उपकरण में नुकसान और फिट जाँचें; यह मॉडल स्वीकृति चिह्न नहीं है।", floatArrayOf(.055f,.434f,.145f)),
            Part("lens", "Eye-protection lens", "आँख की सुरक्षा का लेंस", "Reflective panel in a dark frame below the helmet", "हेलमेट के नीचे गहरे फ्रेम में चमकता पैनल",
                "The lens sits in its frame. Required eye protection depends on the task assessment; this illustration is not a complete PPE kit.", "लेंस फ्रेम में लगा है। आँख की ज़रूरी सुरक्षा काम के आकलन पर निर्भर है; यह चित्र पूरा पीपीई किट नहीं है।", floatArrayOf(.080f,.263f,.196f)),
            Part("earmuff", "Earmuff cushion", "कान की सुरक्षा की गद्दी", "Padded dark cup to the side of the helmet", "हेलमेट की बगल में गद्दी वाला गहरा कप",
                "The cushion is a hearing-protection fit surface. Required protection and correct fit need the actual task and equipment guidance.", "गद्दी सुनने की सुरक्षा के फिट की जगह है। ज़रूरी सुरक्षा और सही फिट के लिए असली काम और उपकरण का मार्गदर्शन चाहिए।", floatArrayOf(-.252f,.245f,.057f))
        )
    )
}

/** Personal practice only. No methods can write assessment or credential records. */
class ComponentSession private constructor(val data: JSONObject) {
    val module: String get() = data.getString("module")
    val parts get() = ComponentCatalog.modules.getValue(module)
    val index get() = data.getInt("index")
    val stage get() = data.getInt("stage") // 0: example; 1: identify; 2: changed view; 3: complete
    val target get() = parts[index.coerceAtMost(parts.lastIndex)]
    val done get() = stage == 3
    val answer get() = data.optString("answer").takeIf { it.isNotEmpty() }
    val helped get() = data.optBoolean("helped")
    val descriptions get() = data.optBoolean("descriptions")
    val correct get() = answer == target.id
    val showLabels get() = stage == 0 || helped || answer != null
    val yaw get() = if (stage == 2) -20f else 20f
    val order: List<ComponentCatalog.Part> get() {
        val base = parts.shuffled(java.util.Random(module.hashCode().toLong()*31L + data.getInt("round")*101L))
        return if (stage == 2) base.drop(1) + base.take(1) else base
    }
    fun useDescriptions(value: Boolean) {
        if (answer == null) {
            data.put("descriptions", value)
            if (value && stage in 1..2) data.put("descriptionSeen", true)
        }
    }
    /** Record an actually displayed model, not a requested camera mode or an untracked frame. */
    fun notePresentation(presentation: String) {
        if (stage !in 1..2 || answer != null || descriptions) return
        when (presentation) {
            "screen" -> data.put("screenSeen", true)
            "camera" -> data.put("cameraSeen", true)
        }
    }
    fun hint() { if (!done && stage > 0 && answer == null) { data.put("helped", true); data.put("clean", false) } }
    fun choose(id: String, now: Long, presentation: String = "screen") {
        if (presentation !in listOf("screen", "camera") || stage !in 1..2 || answer != null || (id != "_unsure" && parts.none { it.id == id })) return
        notePresentation(presentation)
        val success = id == target.id
        val descriptionSeen = descriptions || data.optBoolean("descriptionSeen")
        val displayed = if (descriptions) "description" else if (data.optBoolean("screenSeen") && data.optBoolean("cameraSeen")) "mixed" else if (data.optBoolean("cameraSeen")) "camera" else "screen"
        val events = data.getJSONArray("events")
        if (events.length() >= 200) events.remove(0)
        events.put(JSONObject().put("part", target.id).put("stage", stage).put("answer", id).put("correct", success)
            .put("helped", helped).put("mode", if (descriptions) "description" else if (descriptionSeen) "mixed" else "visual-markers").put("descriptionExposed", descriptionSeen).put("presentation", displayed).put("at", now))
        if (!success || helped) data.put("clean", false)
        if (success && !helped) {
            val key = if (descriptionSeen) "descriptionCorrect" else "visualCorrect"
            data.put(key, data.optInt(key) + 1)
            if (!descriptionSeen) {
                val presentationKey = when (displayed) { "camera" -> "cameraCorrect"; "mixed" -> "mixedPresentationCorrect"; else -> "screenCorrect" }
                data.put(presentationKey, data.optInt(presentationKey) + 1)
            }
        }
        data.put("answer", id)
    }
    fun advance(now: Long) {
        if (done) return
        if (stage == 0) { data.put("stage", 1).put("descriptionSeen", descriptions).put("screenSeen", false).put("cameraSeen", false); return }
        if (answer == null) return
        if (!correct) { data.put("answer", ""); data.put("helped", true); return }
        data.put("answer", "").put("helped", false).put("descriptionSeen", descriptions).put("screenSeen", false).put("cameraSeen", false)
        if (stage == 1) data.put("stage", 2)
        else if (index < parts.lastIndex) data.put("index", index + 1).put("stage", if (data.getInt("round") > 0) 1 else 0)
        else {
            val clean = data.optBoolean("clean")
            val early = data.optLong("preservedDueAt") > 0L
            val streak = if (!clean) 0 else data.optInt("previousStreak") + if (early) 0 else 1
            val dueAt = if (clean && early) data.getLong("preservedDueAt") else now + RecallPlanner.delay(clean, streak)
            data.put("stage", 3).put("completedAt", now).put("streak", streak).put("dueAt", dueAt)
        }
    }
    companion object {
        fun restore(module: String, record: JSONObject?): ComponentSession {
            require(ComponentCatalog.modules.containsKey(module))
            if (record != null && record.optInt("catalogVersion") == ComponentCatalog.VERSION && record.optString("module") == module
                && record.optInt("index", -1) in ComponentCatalog.modules.getValue(module).indices && record.optInt("stage", -1) in 0..3) {
                val restored = JSONObject(record.toString())
                // Older versions displayed the screen model. Do not relabel that exposure as camera-only.
                if (!restored.has("screenSeen") || !restored.has("cameraSeen")) {
                    restored.put("screenSeen", restored.optInt("stage") in 1..2).put("cameraSeen", false)
                }
                return ComponentSession(restored)
            }
            return start(module, null)
        }
        fun start(module: String, previous: JSONObject?, now: Long = System.currentTimeMillis()): ComponentSession {
            require(ComponentCatalog.modules.containsKey(module))
            val compatible = previous?.takeIf { it.optInt("catalogVersion") == ComponentCatalog.VERSION && it.optString("module") == module && it.optInt("stage") == 3 }
            return ComponentSession(JSONObject().put("catalogVersion", ComponentCatalog.VERSION).put("module", module)
                .put("index", 0).put("stage", if (compatible == null) 0 else 1).put("answer", "").put("helped", false).put("descriptions", false).put("descriptionSeen", false)
                .put("preservedDueAt", compatible?.optLong("dueAt")?.takeIf { it > now } ?: 0L)
                .put("round", (compatible?.optInt("round") ?: -1) + 1).put("previousStreak", compatible?.optInt("streak") ?: 0)
                .put("clean", true).put("visualCorrect", 0).put("descriptionCorrect", 0)
                .put("screenSeen", false).put("cameraSeen", false).put("cameraCorrect", 0).put("screenCorrect", 0).put("mixedPresentationCorrect", 0).put("events", JSONArray()))
        }
    }
}

object ComponentProjection {
    data class Point(val x: Float, val y: Float)
    /** Column-major MVP. Reject points outside the frustum; never claim mesh visibility/occlusion. */
    fun project(point: FloatArray, mvp: FloatArray, width: Int, height: Int): Point? {
        if (point.size != 3 || mvp.size != 16 || width <= 0 || height <= 0) return null
        val clip = FloatArray(4) { r -> mvp[r]*point[0] + mvp[4+r]*point[1] + mvp[8+r]*point[2] + mvp[12+r] }
        if (clip.any { !it.isFinite() } || clip[3] <= 0f) return null
        val x = clip[0]/clip[3]; val y = clip[1]/clip[3]; val z = clip[2]/clip[3]
        if (x !in -1f..1f || y !in -1f..1f || z !in -1f..1f) return null
        return Point((x+1f)*width/2f, (1f-y)*height/2f)
    }
}
