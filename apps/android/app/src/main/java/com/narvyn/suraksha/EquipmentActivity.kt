package com.narvyn.suraksha

import android.app.Activity
import android.os.Bundle
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.app.AlertDialog
import android.widget.LinearLayout
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Offline orbit view using the same original meshes that are placed in AR. */
class EquipmentActivity:Activity(){
 private lateinit var surface:GLSurfaceView
 @Volatile private var yaw=25f
 @Volatile private var distance=1.75f
 @Volatile private var pitch=0f
 override fun onCreate(state:Bundle?){super.onCreate(state)
  val module=intent.getStringExtra("moduleId")?.takeIf{it in listOf("fire","gas","machinery","ppe","emergency")}?:"fire"
  val hi=Store(this).use{it.hi};fun t(en:String,hindi:String)=if(hi)hindi else en
  yaw=state?.getFloat("yaw",25f)?:25f;distance=state?.getFloat("distance",1.75f)?:1.75f;pitch=state?.getFloat("pitch",0f)?:0f
  val root=column(20);root.setBackgroundColor(Palette.canvas)
  root.setOnApplyWindowInsetsListener{v,i->if(android.os.Build.VERSION.SDK_INT>=30){val b=i.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(dp(20)+b.left,dp(20)+b.top,dp(20)+b.right,dp(20)+b.bottom)}else{v.setPadding(dp(20)+i.systemWindowInsetLeft,dp(20)+i.systemWindowInsetTop,dp(20)+i.systemWindowInsetRight,dp(20)+i.systemWindowInsetBottom)};i}
  root.add(label(t("Explore the equipment","उपकरण को देखें"),25f,Palette.ink,true),bottom=8)
  root.add(label(t("Illustrative equipment · drag to turn and tilt, pinch to zoom. No live sensor readings.","उपकरण का चित्र · घुमाने और झुकाने के लिए खींचें, दो उँगलियों से ज़ूम करें। लाइव सेंसर रीडिंग नहीं।"),14f,Palette.muted),bottom=12)
  surface=GLSurfaceView(this);surface.setEGLContextClientVersion(2);surface.setEGLConfigChooser(8,8,8,8,16,0)
  surface.contentDescription=t("Rotatable training equipment model","घुमाने योग्य प्रशिक्षण उपकरण का मॉडल")
  val equipment=WorldEquipment();val projection=FloatArray(16);val view=FloatArray(16);val vp=FloatArray(16);val model=FloatArray(16);val camera=floatArrayOf(0f,.8f,distance)
  surface.setRenderer(object:GLSurfaceView.Renderer{
   override fun onSurfaceCreated(gl:GL10?,config:EGLConfig?){GLES20.glClearColor(.965f,.973f,.99f,1f);equipment.create()}
   override fun onSurfaceChanged(gl:GL10?,w:Int,h:Int){GLES20.glViewport(0,0,w,h);Matrix.perspectiveM(projection,0,38f,w.toFloat()/h.coerceAtLeast(1),.05f,10f)}
   override fun onDrawFrame(gl:GL10?){GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT);camera[2]=distance;Matrix.setLookAtM(view,0,camera[0],camera[1],camera[2],0f,.26f,0f,0f,1f,0f);Matrix.multiplyMM(vp,0,projection,0,view,0);Matrix.setIdentityM(model,0);Matrix.rotateM(model,0,yaw,0f,1f,0f);Matrix.translateM(model,0,0f,.26f,0f);Matrix.rotateM(model,0,pitch,1f,0f,0f);Matrix.translateM(model,0,0f,-.26f,0f);equipment.draw(vp,model,module,camera)}
  });surface.renderMode=GLSurfaceView.RENDERMODE_WHEN_DIRTY
  var last=0f;var lastY=0f
  val scale=ScaleGestureDetector(this,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){override fun onScale(detector:ScaleGestureDetector):Boolean{distance=(distance/detector.scaleFactor).coerceIn(1.2f,3f);surface.requestRender();return true}})
  surface.setOnTouchListener{_,e->scale.onTouchEvent(e);when(e.actionMasked){MotionEvent.ACTION_DOWN->{last=e.x;lastY=e.y;true};MotionEvent.ACTION_MOVE->{if(!scale.isInProgress&&e.pointerCount==1){yaw+=(e.x-last)*.4f;pitch=(pitch+(e.y-lastY)*.2f).coerceIn(-35f,35f);surface.requestRender()};last=e.x;lastY=e.y;true};MotionEvent.ACTION_UP->{surface.performClick();true};else->true}}
  root.addView(surface,LinearLayout.LayoutParams(-1,0,1f))
  val controls=LinearLayout(this)
  listOf(t("Rotate left","बाएँ घुमाएँ") to -30f,t("Rotate right","दाएँ घुमाएँ") to 30f).forEach{(label,delta)->controls.addView(action(label,false){yaw+=delta;surface.requestRender()},LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(dp(3),dp(4),dp(3),dp(4))})}
  root.add(controls)
  val detailControls=LinearLayout(this)
  detailControls.addView(action(t("Change zoom","ज़ूम बदलें"),false){distance=if(distance>1.6f)1.35f else 1.95f;surface.requestRender()},LinearLayout.LayoutParams(0,-2,1f))
  detailControls.addView(action(t("Inspect parts","पुर्ज़े जानें"),false){
   val parts=when(module){
    "fire"->listOf(t("Vessel & label","पात्र और लेबल") to t("Confirm the actual extinguisher type and label with a competent trainer. This generic model does not identify a fire class or authorise use.","योग्य प्रशिक्षक से असली अग्निशामक का प्रकार और लेबल समझें। यह सामान्य मॉडल आग का वर्ग नहीं बताता और उपयोग की अनुमति नहीं देता।"),t("Valve, lever & pin","वाल्व, लीवर और पिन") to t("Parts and operation vary by extinguisher. The shaped lever, retaining pin and hose support recognition, not an operating sequence.","अग्निशामक के अनुसार पुर्ज़े और उपयोग बदलते हैं। लीवर, पिन और होज़ पहचान के लिए हैं, संचालन के चरण नहीं हैं।"),t("Illustrative gauge","काल्पनिक गेज") to t("The gauge has no valid pressure reading. Inspection must follow the actual equipment instructions.","गेज कोई मान्य दबाव नहीं बताता। असली उपकरण के निर्देश से जाँच करें।"))
    "gas"->listOf(t("Display & sensor ports","डिस्प्ले और सेंसर छिद्र") to t("SIM marks a simulated display. This model and your phone do not measure gas or oxygen. Use only suitable, checked instruments as trained.","SIM काल्पनिक डिस्प्ले बताता है। मॉडल और फ़ोन गैस या ऑक्सीजन नहीं मापते। प्रशिक्षण के अनुसार केवल उपयुक्त, जाँचे हुए उपकरण उपयोग करें।"),t("Housing & controls","खोल और नियंत्रण") to t("Housing, buttons and clip help identify parts. This is not a real detector’s calibration or bump-test procedure.","खोल, बटन और क्लिप पुर्ज़ों की पहचान के लिए हैं। यह असली डिटेक्टर की कैलिब्रेशन या बम्प टेस्ट प्रक्रिया नहीं है।"))
    "machinery"->listOf(t("Guard & fasteners","गार्ड और कसने वाले पुर्ज़े") to t("The mesh guard separates people from moving parts. Report missing or damaged guards; never reach through them.","जाली गार्ड लोगों को चलते पुर्ज़ों से अलग करता है। गायब या खराब गार्ड की सूचना दें; अंदर हाथ न डालें।"),t("Stop & lock","स्टॉप और ताला") to t("A stop control or fitted lock does not prove effective isolation. Only authorised people follow the equipment-specific procedure.","स्टॉप नियंत्रण या लगा ताला प्रभावी अलगाव साबित नहीं करता। केवल अधिकृत लोग उपकरण-विशिष्ट प्रक्रिया मानें।"))
    "emergency"->listOf(t("Reporting radio","सूचना रेडियो") to t("This generic radio cannot transmit. Report from a safe place using the site's approved communication procedure and equipment.","यह सामान्य रेडियो प्रसारण नहीं कर सकता। सुरक्षित जगह से स्थल की स्वीकृत संचार प्रक्रिया और उपकरण से सूचना दें।"),t("First-aid case","प्राथमिक सहायता किट") to t("Locate the trained first aider and approved supplies before work. This closed case is an illustration; it does not teach treatment.","काम से पहले प्रशिक्षित प्राथमिक सहायक और स्वीकृत सामग्री की जगह जानें। यह बंद किट चित्र है; इससे उपचार नहीं सिखाया जाता।"),t("Assembly marker","एकत्र होने की जगह का चिह्न") to t("Follow the assigned safe route and report at the designated assembly point. This model cannot determine a real safe route or locate missing people.","निर्धारित सुरक्षित रास्ते से एकत्र होने की जगह जाएँ और उपस्थिति बताएँ। यह मॉडल असली सुरक्षित रास्ता नहीं बता सकता और लापता लोगों को नहीं ढूँढ सकता।"))
    else->listOf(t("Helmet & suspension","हेलमेट और अंदर की पट्टियाँ") to t("Shell, brim and suspension are separate parts. Check actual equipment for damage and correct fit; this model is not an approval mark.","खोल, किनारा और अंदर की पट्टियाँ अलग पुर्ज़े हैं। असली उपकरण में नुकसान और सही फिट जाँचें; यह स्वीकृति चिह्न नहीं है।"),t("Eye & hearing protection","आँख और सुनने की क्षमता की सुरक्षा") to t("Lenses, frame, side arms and ear cushions show fit surfaces. Required protection depends on the task assessment; this is not a complete PPE kit.","लेंस, फ्रेम, किनारे की डंडियाँ और कान की गद्दियाँ फिट की जगह दिखाते हैं। ज़रूरी सुरक्षा काम के आकलन पर निर्भर है; यह पूरा पीपीई किट नहीं है।"))
   }
   AlertDialog.Builder(this).setTitle(t("Inspect the illustration","चित्र के पुर्ज़े जानें")).setItems(parts.map{it.first}.toTypedArray()){_,which->AlertDialog.Builder(this).setTitle(parts[which].first).setMessage(parts[which].second).setPositiveButton(t("Close","बंद करें"),null).show()}.setNegativeButton(t("Close","बंद करें"),null).show()
  },LinearLayout.LayoutParams(0,-2,1f));root.add(detailControls,bottom=8)
  root.add(action(t("Practice finding parts","पुर्ज़े पहचानने का अभ्यास"),false){startActivity(android.content.Intent(this,ComponentPracticeActivity::class.java).putExtra("moduleId",module))},bottom=8)
  root.add(action(t("Back to lesson","पाठ पर वापस जाएँ")){finish()})
  setContentView(root)
 }
 override fun onResume(){super.onResume();surface.onResume()}
 override fun onPause(){surface.onPause();super.onPause()}
 override fun onSaveInstanceState(out:Bundle){out.putFloat("yaw",yaw);out.putFloat("distance",distance);out.putFloat("pitch",pitch);super.onSaveInstanceState(out)}
}
