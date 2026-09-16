package com.narvyn.suraksha

/** Explanations for authored simulations. No inference about the real room or worker competence. */
object RoomLearning {
    data class Text(val en:String,val hi:String) { fun local(hindi:Boolean)=if(hindi)hi else en }
    data class Lesson(val title:Text,val why:Text,val reflect:Text)
    private fun lesson(en:String,hi:String,why:String,whyHi:String,ask:String,askHi:String)=
        Lesson(Text(en,hi),Text(why,whyHi),Text(ask,askHi))
    private val lessons=mapOf(
        "ALARM" to lesson("Raise the alarm","अलार्म दें",
            "An early alarm lets other people respond and follow the site's emergency plan.","जल्दी अलार्म देने से दूसरे लोग प्रतिक्रिया देकर स्थल की आपात योजना का पालन कर सकते हैं।",
            "Who needs to know before you focus on equipment?","उपकरण पर ध्यान देने से पहले किसे सूचना चाहिए?"),
        "EXIT" to lesson("Exit recognition","निकास पहचान",
            "Keep a usable way out. A visible exit sign does not make a blocked route usable. The signs here belong to the simulation.","बाहर जाने का खुला रास्ता रखें। निकास चिह्न होने से बंद रास्ता खुला नहीं हो जाता। यहाँ के चिह्न सिमुलेशन के हैं।",
            "What would make you reject an exit route?","आप किस स्थिति में निकास मार्ग नहीं चुनेंगे?"),
        "EQUIPMENT" to lesson("Response selection","प्रतिक्रिया चयन",
            "Equipment use depends on training, authority, suitability and conditions. Evacuation is a valid response; an unconfirmed unit is not a reason to experiment.","उपकरण उपयोग प्रशिक्षण, अधिकार, उपयुक्तता और स्थिति पर निर्भर है। निकासी सही प्रतिक्रिया हो सकती है; अपुष्ट उपकरण आज़माने का कारण नहीं है।",
            "What would change your decision from equipment use to evacuation?","किस बात पर आप उपकरण के बजाय निकासी चुनेंगे?"),
        "PIN" to lesson("Pin preparation","पिन तैयारी",
            "The retaining pin prevents accidental operation. This gesture practises the sequence, not the force or weight of real equipment.","सुरक्षा पिन अनजाने संचालन को रोकती है। यह क्रिया क्रम का अभ्यास है, वास्तविक उपकरण के बल या वज़न का नहीं।",
            "Why is preparation a separate step from discharge?","तैयारी और डिस्चार्ज अलग क्रियाएँ क्यों हैं?"),
        "AIM" to lesson("Base alignment","आधार पर निशाना",
            "In this approved-unit scenario, the target is the base of the fire. A steady aim prepares a controlled sweep.","इस स्वीकृत उपकरण वाले दृश्य में लक्ष्य आग का आधार है। स्थिर निशाना नियंत्रित स्वीप की तैयारी करता है।",
            "What are you aiming at, and why there?","निशाना कहाँ है और वहीं क्यों?"),
        "SWEEP" to lesson("Controlled sweep","नियंत्रित स्वीप",
            "A smooth sweep covers the whole highlighted base. Jumping straight from one end to the other leaves the middle unpractised.","धीरे स्वीप करने से पूरा चिह्नित आधार शामिल होता है। एक सिरे से सीधे दूसरे तक जाने पर बीच का अभ्यास छूट जाता है।",
            "How would you notice that part of the base was missed?","आधार का कोई भाग छूटने का पता कैसे चलेगा?"),
        "WITHDRAW" to lesson("Withdrawal","वापसी",
            "Worsening conditions change the response. Release the virtual discharge and choose withdrawal rather than approaching the hazard.","स्थिति बिगड़ने पर प्रतिक्रिया बदलती है। काल्पनिक डिस्चार्ज छोड़कर खतरे के पास जाने के बजाय वापसी चुनें।",
            "Which change tells you to stop the attempt?","कौन-सा बदलाव प्रयास रोकने का संकेत है?"),
        "EVACUATE" to lesson("Evacuation route","निकासी मार्ग",
            "A clear route keeps the response directed toward escape. Follow the actual site's emergency plan in a real incident; these markers are virtual.","खुला मार्ग प्रतिक्रिया को बाहर निकलने की दिशा देता है। वास्तविक घटना में स्थल की आपात योजना का पालन करें; यहाँ के चिह्न काल्पनिक हैं।",
            "Why is the blocked route unsuitable even if it looks shorter?","छोटा दिखने पर भी बंद मार्ग सही क्यों नहीं है?"),
        "ASSEMBLY" to lesson("Assembly accountability","एकत्र उपस्थिति",
            "Roll call helps identify who may still be missing. Leaving without reporting makes accountability harder.","उपस्थिति से लापता लोगों का पता लगाने में मदद मिलती है। बिना बताए जाने से यह जाँच कठिन हो जाती है।",
            "What information does roll call give the response team?","उपस्थिति से प्रतिक्रिया दल को क्या जानकारी मिलती है?"),
        "REPORT" to lesson("Missing-person reporting","लापता व्यक्ति की सूचना",
            "Reporting a missing colleague lets the responsible response team act. Re-entering for an unplanned rescue can create another casualty.","लापता साथी की सूचना से जिम्मेदार दल कार्रवाई कर सकता है। बिना योजना बचाव के लिए लौटने से एक और व्यक्ति हताहत हो सकता है।",
            "How can you help a missing colleague while staying at assembly?","एकत्र स्थल पर रहकर लापता साथी की मदद कैसे करेंगे?"),
        "GAS_CHECK" to lesson("Simulated meter check","काल्पनिक मीटर जाँच",
            "Atmosphere information must be checked before an entry decision. This SIM display is a teaching prop and cannot measure gas or establish real entry readiness.","प्रवेश के निर्णय से पहले वातावरण की जानकारी जाँचना जरूरी है। यह SIM डिस्प्ले सीखने का उपकरण है; गैस माप या वास्तविक प्रवेश तैयारी की पुष्टि नहीं करता।",
            "Why would one apparent reading be insufficient to authorise entry?","केवल एक दिखती रीडिंग प्रवेश अनुमति के लिए पर्याप्त क्यों नहीं है?"),
        "PPE" to lesson("Outside-role PPE","बाहरी भूमिका की सुरक्षा",
            "The kit in this drill is for an outside role. A dust mask does not make an unknown gas atmosphere safe, and wearing a kit does not grant entry permission.","इस अभ्यास की किट बाहर की भूमिका के लिए है। धूल मास्क अज्ञात गैस वातावरण सुरक्षित नहीं बनाता और किट पहनना प्रवेश अनुमति नहीं है।",
            "What is the difference between wearing PPE and being ready to enter?","PPE पहनने और प्रवेश के लिए तैयार होने में क्या अंतर है?"),
        "BARRIER" to lesson("Exclusion barrier","प्रवेश अवरोध",
            "The barrier communicates that access is restricted. It helps prevent an unprepared person from following someone into the space.","अवरोध बताता है कि प्रवेश प्रतिबंधित है। यह बिना तैयारी किसी के पीछे अंदर जाने से रोकने में मदद करता है।",
            "Who is the barrier protecting besides you?","अवरोध आपके अलावा किसकी सुरक्षा करता है?"),
        "ATTENDANT" to lesson("Outside attendant","बाहर परिचर",
            "The attendant's outside position supports communication and escalation. This role is not permission to enter for an unplanned rescue.","परिचर का बाहर रहना संपर्क और सहायता बुलाने में सहायक है। यह भूमिका बिना योजना बचाव के लिए अंदर जाने की अनुमति नहीं है।",
            "Why must the attendant remain outside?","परिचर को बाहर क्यों रहना चाहिए?"),
        "COMMUNICATE" to lesson("Buddy contact","साथी संपर्क",
            "A working contact check establishes that the message can be exchanged. Sending without checking a response does not confirm communication.","संपर्क जाँच से संदेशों के आदान-प्रदान की पुष्टि होती है। उत्तर जाँचे बिना संदेश भेजना संपर्क की पुष्टि नहीं है।",
            "How would you know your buddy actually received the message?","साथी को संदेश मिलने का पता कैसे चलेगा?"),
        "ACKNOWLEDGE" to lesson("Reply and stop signal","उत्तर व रुकने का संकेत",
            "Both people need the same meaning for the stop signal. Acknowledgement confirms the message, but does not establish rescue readiness.","दोनों लोगों को रुकने के संकेत का एक ही अर्थ समझना चाहिए। उत्तर से संदेश की पुष्टि होती है, बचाव तैयारी की नहीं।",
            "What must both people understand about STOP?","रुकें के संकेत के बारे में दोनों को क्या समझना चाहिए?"),
        "REFUSE" to lesson("Refuse unconfirmed entry","अपुष्ट प्रवेश मना करें",
            "In this scenario rescue readiness is unconfirmed. Staying outside and requesting support is the required response, even after the earlier checks.","इस दृश्य में बचाव तैयारी अपुष्ट है। पिछली जाँचों के बाद भी बाहर रहना और सहायता माँगना जरूरी प्रतिक्रिया है।",
            "Which missing condition keeps entry closed?","कौन-सी अधूरी शर्त के कारण प्रवेश बंद है?")
    )
    fun forPhase(phase:String,explosionRisk:Boolean=false):Lesson {
        val base=lessons[phase]?:error("Unknown learning phase: $phase")
        return if(explosionRisk&&phase=="EQUIPMENT")base.copy(why=Text(
            "Explosion risk has been announced in this scenario. Choose evacuation only; the earlier equipment-use path no longer applies.",
            "इस दृश्य में विस्फोट का खतरा बताया गया है। केवल निकासी चुनें; उपकरण उपयोग वाला पिछला विकल्प अब लागू नहीं है।"))else base
    }
    fun doNot(phase:String):Text = when(phase){
        "ALARM"->Text("Delay the alarm to inspect equipment.","उपकरण देखने के लिए अलार्म में देर न करें।")
        "EXIT","EVACUATE"->Text("Choose a blocked or smoke-filled route because it looks shorter.","छोटा दिखने पर भी बंद या धुएँ वाला मार्ग न चुनें।")
        "EQUIPMENT"->Text("Guess an agent from cylinder colour. Use water, sand or an unconfirmed extinguisher on an unknown fire.","सिलेंडर के रंग से माध्यम न चुनें। अज्ञात आग पर पानी, रेत या अपुष्ट अग्निशामक न लें।")
        "PIN"->Text("Pull the pin before confirming suitability, authorisation and a usable escape route.","उपयुक्तता, अनुमति और खुले निकास की पुष्टि से पहले पिन न निकालें।")
        "AIM"->Text("Aim at the tops of flames or move toward the hazard to line up a target.","लौ के ऊपरी भाग पर निशाना न लगाएँ; निशाने के लिए खतरे की ओर न चलें।")
        "SWEEP"->Text("Skip the middle, keep discharging as conditions worsen, or treat the AR spacing as a real firefighting distance.","बीच का भाग न छोड़ें, बिगड़ती स्थिति में डिस्चार्ज न करें, AR अंतर को आग बुझाने की वास्तविक दूरी न मानें।")
        "WITHDRAW"->Text("Continue discharge or approach the fire after the worsening-conditions warning.","स्थिति बिगड़ने की चेतावनी के बाद डिस्चार्ज या आग की ओर बढ़ना जारी न रखें।")
        "ASSEMBLY"->Text("Leave the assembly point without reporting.","सूचना दिए बिना एकत्र स्थल न छोड़ें।")
        "REPORT"->Text("Re-enter for an unplanned rescue.","बिना योजना बचाव के लिए फिर प्रवेश न करें।")
        "GAS_CHECK"->Text("Treat this SIM meter as live gas detection or entry permission.","SIM मीटर को वास्तविक गैस माप या प्रवेश अनुमति न मानें।")
        "PPE"->Text("Assume a dust mask protects against unknown gas or oxygen deficiency.","धूल मास्क को अज्ञात गैस या ऑक्सीजन की कमी से सुरक्षा न मानें।")
        "BARRIER"->Text("Cross or leave a gap in the access barrier.","प्रवेश अवरोध पार न करें और उसमें अंतर न छोड़ें।")
        "ATTENDANT"->Text("Move the attendant inside for an improvised rescue.","बिना योजना बचाव के लिए परिचर को अंदर न भेजें।")
        "COMMUNICATE","ACKNOWLEDGE"->Text("Assume a sent message was received. Continue after losing communication or a stop signal.","भेजे संदेश को मिला हुआ न मानें। संपर्क टूटने या रुकने के संकेत के बाद जारी न रखें।")
        "REFUSE"->Text("Enter while rescue readiness remains unconfirmed.","बचाव तैयारी अपुष्ट होने पर प्रवेश न करें।")
        else->error("Unknown learning phase: $phase")
    }
    data class Review(val phase:String,val events:List<RoomMission.Event>,val hints:Int) {
        val corrections get()=events.count{!it.accepted}
        val needsPractice get()=corrections>0||hints>0
    }
    fun review(mission:RoomMission,coaching:RoomCoaching):List<Review> {
        require(mission.module==coaching.module)
        return RoomMission.phases(mission.module).filter{it!="COMPLETE"}.mapNotNull { phase ->
            val events=mission.events.filter{it.phase==phase};val hints=coaching.cues.count{it.phase==phase}
            if(events.isEmpty()&&hints==0)null else Review(phase,events,hints)
        }
    }
    fun priorities(reviews:List<Review>)=reviews.filter{it.needsPractice}
        .sortedWith(compareByDescending<Review>{it.corrections}.thenByDescending{it.hints}).take(2)
}
