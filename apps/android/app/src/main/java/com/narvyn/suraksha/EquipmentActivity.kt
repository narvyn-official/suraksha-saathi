package com.narvyn.suraksha

import android.app.Activity
import android.os.Bundle
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import android.widget.LinearLayout
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Offline orbit view using the same original meshes that are placed in AR. */
class EquipmentActivity:Activity(){
 private lateinit var surface:GLSurfaceView
 @Volatile private var yaw=25f
 @Volatile private var distance=2.5f
 override fun onCreate(state:Bundle?){super.onCreate(state)
  val module=intent.getStringExtra("moduleId")?.takeIf{it in listOf("fire","gas","machinery")}?:"fire"
  val hi=Store(this).use{it.hi};fun t(en:String,hindi:String)=if(hi)hindi else en
  yaw=state?.getFloat("yaw",25f)?:25f;distance=state?.getFloat("distance",2.5f)?:2.5f
  val root=column(20);root.setBackgroundColor(Palette.canvas)
  root.setOnApplyWindowInsetsListener{v,i->if(android.os.Build.VERSION.SDK_INT>=30){val b=i.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(dp(20)+b.left,dp(20)+b.top,dp(20)+b.right,dp(20)+b.bottom)}else{v.setPadding(dp(20)+i.systemWindowInsetLeft,dp(20)+i.systemWindowInsetTop,dp(20)+i.systemWindowInsetRight,dp(20)+i.systemWindowInsetBottom)};i}
  root.add(label(t("Explore the equipment","उपकरण को देखें"),25f,Palette.ink,true),bottom=8)
  root.add(label(t("3D illustration · drag to rotate. This is not a live camera or a gas sensor.","3D चित्र · घुमाने के लिए खींचें। यह लाइव कैमरा या गैस सेंसर नहीं है।"),14f,Palette.muted),bottom=12)
  surface=GLSurfaceView(this);surface.setEGLContextClientVersion(2);surface.setEGLConfigChooser(8,8,8,8,16,0)
  surface.contentDescription=t("Rotatable training equipment model","घुमाने योग्य प्रशिक्षण उपकरण का मॉडल")
  val equipment=WorldEquipment();val projection=FloatArray(16);val view=FloatArray(16);val vp=FloatArray(16);val model=FloatArray(16)
  surface.setRenderer(object:GLSurfaceView.Renderer{
   override fun onSurfaceCreated(gl:GL10?,config:EGLConfig?){GLES20.glClearColor(.965f,.973f,.99f,1f);equipment.create()}
   override fun onSurfaceChanged(gl:GL10?,w:Int,h:Int){GLES20.glViewport(0,0,w,h);Matrix.perspectiveM(projection,0,38f,w.toFloat()/h.coerceAtLeast(1),.05f,10f)}
   override fun onDrawFrame(gl:GL10?){GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT);Matrix.setLookAtM(view,0,0f,.8f,distance,0f,.25f,0f,0f,1f,0f);Matrix.multiplyMM(vp,0,projection,0,view,0);Matrix.setIdentityM(model,0);Matrix.rotateM(model,0,yaw,0f,1f,0f);equipment.draw(vp,model,module)}
  });surface.renderMode=GLSurfaceView.RENDERMODE_WHEN_DIRTY
  var last=0f
  surface.setOnTouchListener{_,e->when(e.actionMasked){MotionEvent.ACTION_DOWN->{last=e.x;true};MotionEvent.ACTION_MOVE->{yaw+=(e.x-last)*.4f;last=e.x;surface.requestRender();true};MotionEvent.ACTION_UP->{surface.performClick();true};else->false}}
  root.addView(surface,LinearLayout.LayoutParams(-1,0,1f))
  val controls=LinearLayout(this)
  listOf(t("Rotate left","बाएँ घुमाएँ") to -30f,t("Rotate right","दाएँ घुमाएँ") to 30f).forEach{(label,delta)->controls.addView(action(label,false){yaw+=delta;surface.requestRender()},LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(dp(3),dp(4),dp(3),dp(4))})}
  root.add(controls);root.add(action(t("Change zoom","ज़ूम बदलें"),false){distance=if(distance>2.3f)2.1f else 3.0f;surface.requestRender()},bottom=8)
  root.add(action(t("Back to lesson","पाठ पर वापस जाएँ")){finish()})
  setContentView(root)
 }
 override fun onResume(){super.onResume();surface.onResume()}
 override fun onPause(){surface.onPause();super.onPause()}
 override fun onSaveInstanceState(out:Bundle){out.putFloat("yaw",yaw);out.putFloat("distance",distance);super.onSaveInstanceState(out)}
}
