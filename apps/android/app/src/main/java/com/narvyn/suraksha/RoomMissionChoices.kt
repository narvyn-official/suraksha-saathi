package com.narvyn.suraksha

/** Labels describe simulated scene objects/options; they are not detected site signs or live PPE advice. */
object RoomMissionChoices {
    data class Choice(val action:String,val station:Int,val x:Float,val y:Float,val z:Float,val en:String,val hi:String)
    fun forPhase(phase:String):List<Choice> = when(phase) {
        "EXIT" -> listOf(
            Choice("select-clear-exit",2,0f,.75f,0f,"Clear exit","खुला निकास"),
            Choice("select-blocked-exit",1,0f,.85f,0f,"Blocked exit","बंद निकास"))
        "EQUIPMENT" -> listOf(
            Choice("select-suitable-extinguisher",0,0f,.70f,0f,"Scenario-approved unit","दृश्य का स्वीकृत उपकरण"),
            Choice("select-unsuitable-extinguisher",1,0f,.85f,0f,"Unconfirmed suitability","उपयुक्तता अपुष्ट"),
            Choice("choose-evacuation",2,0f,.75f,0f,"Evacuate only","केवल निकासी"))
        "EVACUATE" -> listOf(
            Choice("follow-clear-route",2,0f,.75f,0f,"Clear exit route","खुला निकास मार्ग"),
            Choice("follow-blocked-route",1,0f,.85f,0f,"Blocked route","बंद मार्ग"))
        "ASSEMBLY" -> listOf(
            Choice("reach-assembly-point",2,0f,.75f,0f,"Assembly / roll call","एकत्र स्थल / उपस्थिति"),
            Choice("leave-without-rollcall",0,0f,.70f,0f,"Leave the site","स्थल छोड़ें"))
        "REPORT" -> listOf(
            Choice("report-missing-worker",2,0f,.75f,0f,"Report missing colleague","लापता साथी की सूचना"),
            Choice("reenter-search",1,0f,.85f,0f,"Re-enter to search","खोजने के लिए लौटें"))
        "PPE" -> listOf(
            Choice("select-specified-ppe",0,0f,.22f,0f,"Outside-role kit","बाहरी भूमिका की किट"),
            Choice("select-dust-mask",1,0f,.94f,0f,"Dust mask → enter","धूल मास्क → प्रवेश"))
        "COMMUNICATE" -> listOf(
            Choice("send-buddy-check",2,.115f,1.12f,.13f,"Send radio check","रेडियो जाँच भेजें"),
            Choice("skip-buddy-check",0,0f,.72f,0f,"Skip contact check","संपर्क जाँच छोड़ें"))
        "ACKNOWLEDGE" -> listOf(
            Choice("confirm-buddy-ack",2,.115f,1.12f,.13f,"Confirm reply + stop signal","उत्तर व रुकने का संकेत"),
            Choice("proceed-without-ack",0,0f,.72f,0f,"Proceed without reply","उत्तर बिना आगे बढ़ें"))
        else -> emptyList()
    }
    fun instruction(phase:String,hi:Boolean):String? {
        val text=when(phase) {
            "EXIT" -> "Identify the clear simulated exit. The other route is blocked. These signs do not identify real exits." to "खुला काल्पनिक निकास चुनें। दूसरा मार्ग बंद है। ये वास्तविक निकास के संकेत नहीं हैं।"
            "EQUIPMENT" -> "This drill assumes a trained, authorised responder and an approved unit. Select that unit, or choose evacuation only. Never use equipment of unconfirmed suitability." to "इस अभ्यास में प्रशिक्षित, अधिकृत व्यक्ति और स्वीकृत उपकरण माना गया है। वह चुनें या केवल निकासी करें। अपुष्ट उपकरण न चुनें।"
            "EVACUATE" -> "Conditions require evacuation. Select the clear simulated exit route; do not enter the blocked route. Stay in your training spot." to "अब निकासी करें। खुला काल्पनिक मार्ग चुनें; बंद मार्ग में न जाएँ। प्रशिक्षण की जगह पर रहें।"
            "ASSEMBLY" -> "At the virtual assembly point, join roll call before leaving. Tap the assembly marker." to "काल्पनिक एकत्र स्थल पर जाने से पहले उपस्थिति दर्ज करें। एकत्र स्थल का चिह्न छुएँ।"
            "REPORT" -> "Roll call finds a colleague missing. Report to the responsible person from assembly; do not re-enter to search." to "उपस्थिति में एक साथी लापता है। एकत्र स्थल से प्रभारी को बताएँ; खोजने के लिए वापस न जाएँ।"
            "PPE" -> "Outside-only role: select the scenario kit (helmet, eye protection, gloves and boots). It does not permit entry. A dust mask cannot make an unknown atmosphere safe." to "केवल बाहरी भूमिका: दृश्य की किट चुनें (हेलमेट, आँख सुरक्षा, दस्ताने, जूते)। इससे प्रवेश अनुमति नहीं मिलती। धूल मास्क अज्ञात वातावरण सुरक्षित नहीं बनाता।"
            "COMMUNICATE" -> "The attendant stays outside. Tap their radio to send a communication check before continuing." to "परिचर बाहर रहेगा। आगे बढ़ने से पहले संपर्क जाँच के लिए उसका रेडियो छुएँ।"
            "ACKNOWLEDGE" -> "Simulated reply: ‘Received. STOP means withdraw and report.’ Confirm the reply and shared stop signal. Rescue readiness remains unconfirmed." to "काल्पनिक उत्तर: ‘संदेश मिला। रुकें का अर्थ हटें और सूचना दें।’ उत्तर व संकेत की पुष्टि करें। बचाव तैयारी अभी अपुष्ट है।"
            else -> return null
        };return if(hi)text.second else text.first
    }
    fun feedback(action:String,hi:Boolean):String {
        val text=when(action){
            "select-blocked-exit","follow-blocked-route","enter-smoke" -> "That route is blocked. Find the clear simulated exit." to "वह मार्ग बंद है। खुला काल्पनिक निकास खोजें।"
            "select-unsuitable-extinguisher","operate-without-training" -> "Unconfirmed suitability or authority means withdraw; do not attempt discharge." to "उपयुक्तता या अधिकार अपुष्ट हो तो हटें; डिस्चार्ज न करें।"
            "select-dust-mask" -> "A dust mask does not protect against an unknown gas atmosphere. Stay outside; select the outside-role kit." to "धूल मास्क अज्ञात गैस वातावरण से सुरक्षा नहीं देता। बाहर रहें; बाहरी भूमिका की किट चुनें।"
            "skip-buddy-check","proceed-without-ack" -> "Confirm contact and the shared stop signal. Missing communication keeps the procedure on hold." to "संपर्क और रुकने का संकेत पक्का करें। संपर्क न होने पर प्रक्रिया रुकी रहेगी।"
            "leave-without-rollcall" -> "Join accountability at assembly before leaving." to "जाने से पहले एकत्र स्थल पर उपस्थिति दर्ज करें।"
            "reenter-search" -> "Report the missing colleague to the responsible person. Do not re-enter for an unplanned rescue." to "प्रभारी को लापता साथी की सूचना दें। बिना बचाव योजना के वापस न जाएँ।"
            else -> "This action is not available in the current step. Review the scene and try again." to "यह क्रिया अभी उपलब्ध नहीं है। दृश्य देखें और फिर प्रयास करें।"
        };return if(hi)text.second else text.first
    }
}
