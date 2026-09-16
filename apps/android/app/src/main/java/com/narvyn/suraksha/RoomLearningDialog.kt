package com.narvyn.suraksha

import android.app.Activity
import android.app.Dialog
import android.view.*
import android.widget.*

/** One topic at a time. A single fixed footer advances content parts and then the next topic. */
internal object RoomLearningDialog {
    data class Choice(val label:String,val feedback:String,val suitable:Boolean)
    data class Page(val title:String,val body:String,val question:String="",val tint:Int=Palette.tealBg,val choices:List<Choice> = emptyList())
    data class Next(val label:String,val run:()->Unit)
    fun show(host:Activity,title:String,pages:List<Page>,hi:Boolean,onDismiss:()->Unit,
             listen:((String)->String?)?=null,next:List<Next> = emptyList(),stopVoice:()->Unit={},autoSpeak:Boolean=false):Dialog {
        require(pages.isNotEmpty())
        fun t(en:String,hindi:String)=if(hi)hindi else en
        val dialog=Dialog(host,android.R.style.Theme_Material_Light_NoActionBar)
        val root=host.column(12).apply{setBackgroundColor(Palette.canvas)}
        root.setOnApplyWindowInsetsListener{v,i->if(android.os.Build.VERSION.SDK_INT>=30){val b=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(host.dp(12)+b.left,host.dp(8)+b.top,host.dp(12)+b.right,host.dp(8)+b.bottom)};i}
        root.add(host.label(title,16f,Palette.ink,true).apply{maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END},bottom=4)
        val counter=host.label("",12f,Palette.muted).apply{tag="learning-page-counter"};root.add(counter,bottom=8)
        val content=host.column(14)
        val heading=host.label("",21f,Palette.ink,true).asHeading();content.add(heading,bottom=12)
        val body=host.label("",16f);content.add(body,bottom=14)
        val reflection=host.label("",16f,Palette.ink,true);content.add(reflection,bottom=10)
        val choices=host.column();content.add(choices,top=4)
        val voiceStatus=host.label("",13f,Palette.muted).apply{visibility=View.GONE};content.add(voiceStatus,top=6)
        val panel=PagedPanel(host,hi,false).apply{setContent(content)}
        root.addView(panel,LinearLayout.LayoutParams(-1,0,1f))
        var index=0;var speaking=false
        val answers=mutableMapOf<Int,Int>()
        fun narration(p:Page)=p.title+". "+p.body+". "+p.question+". "+p.choices.joinToString(". "){it.label}
        fun read(text:String){if(listen==null)return;val error=listen(text);speaking=error==null;voiceStatus.text=error?:t("Narration requested. Listen / stop pauses speech.","आवाज़ का अनुरोध भेजा। सुनें / रोकें से आवाज़ रोकें।");voiceStatus.visibility=View.VISIBLE}
        if(listen!=null)content.add(host.action(t("Listen / stop","सुनें / रोकें"),false,ActionRole.REVIEW){
            if(speaking){stopVoice();speaking=false;voiceStatus.text=t("Speech stopped.","आवाज़ रोकी गई।")}
            else read(narration(pages[index]))
        },top=8)
        val nextBox=host.column();content.add(nextBox,top=8)
        val footer=LinearLayout(host).apply{isBaselineAligned=false;setPadding(0,host.dp(8),0,0)}
        val previous=host.action(t("Previous","पिछला"),false,ActionRole.NEUTRAL){}.apply{textSize=14f}
        val close=host.action(t("Close","बंद करें"),false,ActionRole.NEUTRAL){dialog.dismiss()}.apply{textSize=14f}
        val forward=host.action(t("Next","अगला"),false,ActionRole.PRIMARY){}.apply{textSize=14f}
        listOf(previous,close,forward).forEachIndexed{i,v->footer.addView(v,LinearLayout.LayoutParams(0,-2,1f).apply{if(i<2)rightMargin=host.dp(8)})}
        root.add(footer)
        fun chrome(){
            val text=t("Topic ${index+1} of ${pages.size}","विषय ${index+1} / ${pages.size}")+if(panel.pageCount>1)t(" · part ${panel.pageIndex+1}/${panel.pageCount}"," · भाग ${panel.pageIndex+1}/${panel.pageCount}")else ""
            if(counter.text.toString()!=text)counter.text=text
            previous.isEnabled=index>0||panel.hasPrevious
            forward.isEnabled=index<pages.lastIndex||panel.pageIndex<panel.pageCount-1
        }
        fun render(){
            stopVoice();speaking=false
            val page=pages[index];heading.text=page.title;body.text=page.body;reflection.text=page.question
            reflection.visibility=if(page.question.isBlank())View.GONE else View.VISIBLE
            content.setBackgroundColor(page.tint);voiceStatus.visibility=View.GONE;choices.removeAllViews();nextBox.removeAllViews()
            page.choices.forEachIndexed{answer,choice->choices.add(host.action(choice.label,false,ActionRole.LEARN){
                stopVoice();speaking=false;answers[index]=answer
                show(host,t(if(choice.suitable)"Suitable principle"else"Reconsider",if(choice.suitable)"उपयुक्त सिद्धांत"else"फिर सोचें"),
                    listOf(Page(choice.label,choice.feedback,t("Close to return to the example.","उदाहरण पर लौटने के लिए बंद करें।"),if(choice.suitable)Palette.tealBg else Palette.amberBg)),hi,{},listen,stopVoice=stopVoice,autoSpeak=autoSpeak)
            }.apply{isSelected=answers[index]==answer},top=6)}
            if(index==pages.lastIndex)next.forEach{choice->nextBox.add(host.action(choice.label,false,ActionRole.REVIEW){choice.run();dialog.dismiss()},top=6)}
            panel.firstPage();content.requestLayout();chrome()
            if(autoSpeak)read(narration(page))
        }
        panel.onPageChanged={chrome()}
        previous.setOnClickListener{if(panel.hasPrevious)panel.previousPage()else if(index>0){index--;render()}}
        forward.setOnClickListener{if(panel.pageIndex<panel.pageCount-1)panel.nextPage()else if(index<pages.lastIndex){index++;render()}}
        dialog.setContentView(root)
        dialog.setOnShowListener{dialog.window?.setLayout(-1,-1);render();root.requestApplyInsets()}
        dialog.setOnDismissListener{stopVoice();onDismiss()};dialog.show();return dialog
    }
}
