package com.narvyn.suraksha

import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

/** Original, offline vector scenes. All equipment is illustrative training content. */
class SceneView(context:Context, private val moduleId:String, private val hi:Boolean, private val inspect:Boolean=true,private val compact:Boolean=false):View(context){
 private val painter=ScenePainter()
 private val names=when(moduleId){"fire"->listOf("Alarm call point" to "अलार्म बटन","Extinguisher" to "अग्निशामक","Emergency exit" to "आपात निकास");"gas"->listOf("Restricted boundary" to "प्रतिबंधित सीमा","Gas detector" to "गैस डिटेक्टर","Attendant" to "बाहर का सहायक");"emergency"->listOf("Reporting point" to "सूचना देने की जगह","First-aid case" to "प्राथमिक सहायता किट","Assembly point" to "एकत्र होने की जगह");"ppe"->listOf("Safety helmet" to "सुरक्षा हेलमेट","Eye protection" to "आँखों की सुरक्षा","Hearing protection" to "सुनने की सुरक्षा");else->listOf("Machine guard" to "मशीन गार्ड","Energy isolator" to "ऊर्जा अलग करने वाला उपकरण","Personal lock" to "व्यक्तिगत ताला")}
 private val details=when(moduleId){
 "fire"->listOf("Know the site's alarm and reporting procedure before work begins." to "काम शुरू करने से पहले स्थल की अलार्म और सूचना प्रक्रिया जानें।","Check the hazard and approved equipment. Only attempt a response within your training, with a safe escape route." to "खतरा और स्वीकृत उपकरण जाँचें। अपने प्रशिक्षण की सीमा में और सुरक्षित निकास के साथ ही कार्रवाई करें।","Know the designated exit and assembly point. A phone cannot determine whether a real route is safe." to "निर्धारित निकास और एकत्र होने की जगह जानें। फ़ोन वास्तविक रास्ते की सुरक्षा नहीं बता सकता।")
 "gas"->listOf("Respect barriers. Do not enter without the required controls and authorisation." to "अवरोध मानें। ज़रूरी नियंत्रण और अनुमति के बिना प्रवेश न करें।","This drawing is not a sensor. Trained personnel must use a suitable checked instrument and the site procedure." to "यह चित्र सेंसर नहीं है। प्रशिक्षित लोग उपयुक्त जाँचे उपकरण और स्थल की प्रक्रिया का उपयोग करें।","An attendant remains outside and follows the communication and emergency procedure. Unplanned entry rescue is unsafe." to "सहायक बाहर रहता है और संचार तथा आपात प्रक्रिया मानता है। बिना योजना बचाव के लिए प्रवेश असुरक्षित है।")
 "emergency"->listOf("Know the site's reporting channel and give a clear location from a safe place. This illustration cannot call for help." to "स्थल का सूचना चैनल जानें और सुरक्षित जगह से सही स्थान बताएँ। यह चित्र मदद के लिए कॉल नहीं कर सकता।","Know how to contact a trained first aider. A case illustration does not teach treatment or authorise hazardous rescue." to "प्रशिक्षित प्राथमिक सहायक से संपर्क करना जानें। किट का चित्र उपचार नहीं सिखाता और खतरनाक बचाव की अनुमति नहीं देता।","Go to the assigned safe assembly point and report for accountability. Report missing people; do not re-enter to search." to "निर्धारित सुरक्षित एकत्र होने की जगह पर जाएँ और उपस्थिति बताएँ। लापता लोगों की सूचना दें; खोजने के लिए वापस न जाएँ।")
 "ppe"->listOf("Use the specified helmet, check its condition and fit, and report damage. A helmet does not make an unsafe area safe." to "निर्धारित हेलमेट का उपयोग करें, हालत और फिट जाँचें, और नुकसान की सूचना दें। हेलमेट असुरक्षित क्षेत्र को सुरक्षित नहीं बनाता।","Use eye protection selected for the task. Check fit and condition; ordinary spectacles may not provide the required protection." to "काम के लिए चुनी गई आँखों की सुरक्षा का उपयोग करें। फिट और हालत जाँचें; साधारण चश्मा ज़रूरी सुरक्षा नहीं दे सकता।","Use the hearing protection specified for the noise exposure, with correct fit and training. Report missing or damaged equipment before exposure." to "शोर के लिए निर्धारित कान की सुरक्षा सही फिट और प्रशिक्षण के साथ उपयोग करें। संपर्क से पहले गायब या खराब उपकरण की सूचना दें।")
 else->listOf("Keep away from rotating parts. A guard must be in place and functional before exposure." to "घूमते पुर्ज़ों से दूर रहें। संपर्क से पहले गार्ड लगा और ठीक होना चाहिए।","Stopping a machine is not isolation. Only authorised personnel apply the equipment-specific energy-control procedure." to "मशीन रोकना ऊर्जा अलग करना नहीं है। अधिकृत लोग ही उपकरण-विशिष्ट ऊर्जा नियंत्रण करें।","Do not remove someone else's lock. Follow the authorised procedure and controlled handover." to "किसी और का ताला न हटाएँ। अधिकृत प्रक्रिया और नियंत्रित हस्तांतरण मानें।")}
 init{isClickable=inspect;isFocusable=inspect;contentDescription=if(hi){if(inspect)"काल्पनिक प्रशिक्षण दृश्य। उपकरण की जानकारी खोलें।" else "केवल काल्पनिक प्रशिक्षण दृश्य।"}else{if(inspect)"Illustrative training scene. Open equipment information." else "Illustrative training scene only."};background=context.shape(Palette.soft,18)}
 override fun onMeasure(w:Int,h:Int){setMeasuredDimension(MeasureSpec.getSize(w),context.dp(if(compact)135 else 210))}
 override fun onDraw(canvas:Canvas){painter.draw(canvas,width.toFloat(),height.toFloat(),moduleId,hi,inspect)}
 override fun onTouchEvent(event:MotionEvent):Boolean{if(!inspect)return false;if(event.action==MotionEvent.ACTION_UP){val index=(event.x/width*3).toInt().coerceIn(0,2);show(index)};return true}
 private fun show(index:Int){AlertDialog.Builder(context).setTitle(if(hi)names[index].second else names[index].first).setMessage(if(hi)details[index].second else details[index].first).setPositiveButton(if(hi)"समझ गया" else "Got it",null).show()}
 override fun performClick():Boolean{super.performClick();if(inspect)AlertDialog.Builder(context).setTitle(if(hi)"उपकरण देखें" else "Inspect equipment").setItems(names.map{if(hi)it.second else it.first}.toTypedArray()){_,i->show(i)}.show();return true}
}

class ScenePainter{
 private val p=Paint(Paint.ANTI_ALIAS_FLAG)
 private val blue=Color.rgb(49,87,213);private val ink=Color.rgb(40,61,87);private val red=Color.rgb(193,62,71);private val steel=Color.rgb(174,193,215)
 private fun rect(c:Canvas,x:Float,y:Float,w:Float,h:Float,color:Int,r:Float=8f){p.color=color;p.style=Paint.Style.FILL;c.drawRoundRect(x,y,x+w,y+h,r,r,p)}
 private fun line(c:Canvas,x:Float,y:Float,a:Float,b:Float,color:Int,stroke:Float=5f){p.color=color;p.strokeWidth=stroke;p.style=Paint.Style.STROKE;c.drawLine(x,y,a,b,p);p.style=Paint.Style.FILL}
 private fun poly(c:Canvas,points:FloatArray,color:Int){p.color=color;val path=Path();path.moveTo(points[0],points[1]);for(i in 2 until points.size step 2)path.lineTo(points[i],points[i+1]);path.close();c.drawPath(path,p)}
 private fun circle(c:Canvas,x:Float,y:Float,r:Float,color:Int){p.color=color;c.drawCircle(x,y,r,p)}
 private fun text(c:Canvas,s:String,x:Float,y:Float,size:Float=24f,color:Int=ink){p.color=color;p.textSize=size;p.typeface=Typeface.create("sans-serif",Typeface.BOLD);p.textAlign=Paint.Align.CENTER;c.drawText(s,x,y,p)}
 fun draw(c:Canvas,w:Float,h:Float,module:String,hi:Boolean,inspect:Boolean){
  c.save();c.scale(w/1000f,h/520f)
  rect(c,0f,0f,1000f,520f,Color.rgb(236,242,252),0f)
  poly(c,floatArrayOf(0f,310f,1000f,260f,1000f,520f,0f,520f),Color.rgb(218,230,246))
  for(i in 0..5)line(c,0f,320f+i*39,1000f,270f+i*39,Color.rgb(204,219,239),2f)
  if(module=="fire"){
   rect(c,86f,120f,148f,174f,red,14f);rect(c,104f,145f,112f,83f,Color.WHITE);text(c,"!",160f,208f,62f,red);line(c,139f,257f,181f,257f,Color.WHITE,9f)
   // Extinguisher silhouette with handle, hose, cylinder and identification band.
   rect(c,434f,174f,124f,203f,red,42f);rect(c,451f,208f,91f,74f,Color.WHITE,5f);rect(c,467f,139f,60f,44f,ink);line(c,484f,134f,549f,134f,ink,15f);line(c,540f,163f,592f,194f,ink,13f);line(c,592f,194f,600f,314f,ink,13f);circle(c,497f,170f,18f,Color.WHITE)
   rect(c,756f,109f,138f,254f,ink,3f);rect(c,773f,128f,103f,234f,Color.rgb(234,243,253),3f);rect(c,746f,66f,157f,42f,Color.rgb(24,117,90),5f);text(c,"EXIT →",824f,96f,24f,Color.WHITE);line(c,804f,240f,852f,240f,blue,7f);poly(c,floatArrayOf(852f,240f,830f,225f,830f,255f),blue)
  }else if(module=="gas"){
   poly(c,floatArrayOf(45f,359f,270f,282f,363f,339f,152f,415f),Color.rgb(245,222,188));line(c,84f,163f,84f,343f,ink,10f);line(c,270f,146f,270f,311f,ink,10f);line(c,84f,192f,270f,175f,Color.rgb(202,131,24),19f);line(c,84f,240f,270f,223f,Color.rgb(202,131,24),19f)
   rect(c,429f,129f,145f,239f,ink,23f);rect(c,449f,159f,105f,105f,Color.rgb(203,235,224),5f);text(c,"SIM",500f,213f,34f);text(c,if(hi)"काल्पनिक" else "NO SENSOR",500f,242f,14f);circle(c,473f,302f,12f,blue);circle(c,530f,302f,12f,Color.WHITE);line(c,487f,89f,487f,129f,ink,12f)
   circle(c,824f,154f,36f,Color.rgb(186,141,108));rect(c,777f,184f,96f,118f,blue,28f);line(c,798f,296f,785f,370f,ink,23f);line(c,849f,296f,867f,370f,ink,23f);rect(c,787f,116f,76f,24f,Color.rgb(236,177,47),16f);line(c,783f,216f,743f,275f,blue,21f);line(c,866f,216f,903f,259f,blue,21f)
  }else if(module=="emergency"){
   rect(c,98f,134f,133f,211f,ink,18f);rect(c,112f,147f,105f,184f,Color.rgb(224,174,51),12f);rect(c,127f,166f,75f,59f,Color.rgb(170,219,195),5f);text(c,"SIM",166f,207f,26f);line(c,128f,85f,128f,134f,ink,9f);for(i in 0..4)line(c,130f,248f+i*13,201f,248f+i*13,ink,4f)
   val green=Color.rgb(24,117,90);rect(c,379f,194f,242f,157f,green,17f);p.color=ink;p.style=Paint.Style.STROKE;p.strokeWidth=14f;c.drawRoundRect(448f,159f,551f,210f,12f,12f,p);p.style=Paint.Style.FILL;rect(c,475f,227f,47f,93f,Color.WHITE,3f);rect(c,452f,250f,93f,47f,Color.WHITE,3f);rect(c,401f,190f,32f,18f,steel,3f);rect(c,566f,190f,32f,18f,steel,3f)
   line(c,834f,292f,834f,381f,ink,10f);rect(c,720f,120f,229f,188f,green,10f);for(x in listOf(785f,834f,883f)){circle(c,x,183f,13f,Color.WHITE);rect(c,x-14f,200f,28f,42f,Color.WHITE,6f);line(c,x-7f,240f,x-13f,268f,Color.WHITE,8f);line(c,x+7f,240f,x+13f,268f,Color.WHITE,8f)}
  }else if(module=="ppe"){
   // Helmet, goggles and ear defenders: separate illustrative equipment, not a universal PPE prescription.
   p.color=Color.rgb(222,163,40);c.drawArc(62f,135f,290f,350f,180f,180f,true,p);rect(c,54f,235f,245f,30f,Color.rgb(242,189,68),12f);rect(c,159f,133f,28f,101f,Color.rgb(242,189,68),6f);line(c,100f,273f,116f,323f,ink,9f);line(c,253f,273f,237f,323f,ink,9f)
   rect(c,382f,199f,109f,89f,ink,25f);rect(c,511f,199f,109f,89f,ink,25f);rect(c,394f,211f,85f,65f,Color.rgb(162,209,227),18f);rect(c,523f,211f,85f,65f,Color.rgb(162,209,227),18f);line(c,486f,229f,515f,229f,ink,14f);line(c,383f,221f,360f,172f,ink,10f);line(c,619f,221f,642f,172f,ink,10f)
   p.color=ink;p.style=Paint.Style.STROKE;p.strokeWidth=22f;c.drawArc(748f,117f,923f,350f,180f,180f,false,p);p.style=Paint.Style.FILL;rect(c,731f,226f,64f,105f,blue,22f);rect(c,877f,226f,64f,105f,blue,22f);rect(c,781f,237f,15f,84f,ink,5f);rect(c,876f,237f,15f,84f,ink,5f)
  }else{
   // Isometric machine, fixed guard, separate isolator and personal lock.
   poly(c,floatArrayOf(72f,161f,235f,115f,323f,158f,158f,206f),steel);poly(c,floatArrayOf(158f,206f,323f,158f,323f,337f,158f,389f),Color.rgb(118,146,181));poly(c,floatArrayOf(72f,161f,158f,206f,158f,389f,72f,346f),ink);rect(c,174f,235f,112f,81f,Color.rgb(222,238,247),8f);for(i in 0..4)line(c,181f+i*23,239f,181f+i*23,311f,blue,4f)
   rect(c,426f,129f,149f,216f,Color.WHITE,15f);rect(c,446f,150f,111f,27f,steel,3f);circle(c,501f,236f,44f,red);line(c,480f,218f,522f,256f,ink,15f);text(c,"ISOLATE",500f,317f,19f)
   p.style=Paint.Style.STROKE;p.strokeWidth=15f;p.color=ink;c.drawArc(780f,139f,873f,268f,180f,180f,false,p);p.style=Paint.Style.FILL;rect(c,754f,223f,145f,124f,blue,17f);circle(c,826f,278f,11f,Color.WHITE);line(c,826f,280f,826f,310f,Color.WHITE,7f)
  }
  val labels=when(module){"fire"->if(hi)listOf("अलार्म","अग्निशामक","निकास")else listOf("ALARM","EXTINGUISHER","EXIT");"gas"->if(hi)listOf("सीमा","डिटेक्टर","सहायक")else listOf("BOUNDARY","DETECTOR","ATTENDANT");"emergency"->if(hi)listOf("सूचना","सहायता किट","एकत्र होने की जगह")else listOf("REPORT","FIRST AID","ASSEMBLY");"ppe"->if(hi)listOf("हेलमेट","चश्मा","कान की सुरक्षा")else listOf("HELMET","EYE PROTECTION","EAR DEFENDERS");else->if(hi)listOf("गार्ड","अलगाव","ताला")else listOf("GUARD","ISOLATOR","LOCK")}
  labels.forEachIndexed{i,s->val x=166f+i*333;rect(c,x-120f,429f,240f,45f,Color.WHITE,22f);text(c,s,x,459f,if(hi)27f else 22f)}
  text(c,if(inspect){if(hi)"जानने के लिए उपकरण पर टैप करें" else "TAP EQUIPMENT TO EXPLORE"}else{if(hi)"केवल काल्पनिक दृश्य" else "ILLUSTRATIVE SCENE ONLY"},500f,506f,19f,Color.rgb(80,103,133))
  c.restore()
 }
}
