package com.narvyn.suraksha

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.*
import android.opengl.*
import android.os.Bundle
import android.view.*
import android.widget.*
import com.google.ar.core.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Camera-backed, world-anchored action stations. All hazard content is simulated. */
class ArActivity: Activity(), GLSurfaceView.Renderer {
    private lateinit var surface: GLSurfaceView
    private lateinit var overlay: TargetOverlay
    private lateinit var store: Store
    private lateinit var training: TrainingSession
    private lateinit var question: TextView
    private lateinit var status: TextView
    private var ar: Session?=null
    private var anchor: Anchor?=null
    private var installRequested=false
    private var widthPx=1;private var heightPx=1
    private var texture=0;private var program=0
    private var message=""
    @Volatile private var tapped: Pair<Float,Float>?=null
    @Volatile private var canAnswer=false
    @Volatile private var options=emptyList<Pair<String,String>>()
    private val vertices=floats(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f))
    private val uv=ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val hi get()=store.hi
    private fun t(en:String,h:String)=if(hi)h else en
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState);store=Store(this);val data=store.attempt(intent.getStringExtra("attemptId")?:"")
        if(data==null){finish();return};training=TrainingSession(data,Curriculum(this).module(data.getString("moduleId")))
        val root=FrameLayout(this)
        surface=GLSurfaceView(this).apply{setEGLContextClientVersion(2);preserveEGLContextOnPause=true;setRenderer(this@ArActivity);renderMode=GLSurfaceView.RENDERMODE_CONTINUOUSLY}
        root.addView(surface)
        overlay=TargetOverlay();root.addView(overlay)
        val top=column(16).apply{background=shape(Color.WHITE,16)}
        top.add(label(t("AR TRAINING · SIMULATED","AR प्रशिक्षण · काल्पनिक"),13f,Palette.blue,true),bottom=8)
        question=label("",20f,Palette.ink,true);top.add(question,bottom=8)
        status=label("",14f,Palette.muted);top.add(status)
        root.addView(top,FrameLayout.LayoutParams(-1,-2,Gravity.TOP).apply{setMargins(dp(12),dp(38),dp(12),0)})
        val bottom=column(12).apply{background=shape(Color.WHITE,16)}
        bottom.add(label(t("Tap the floor to place the action stations. Then tap a floating answer.","विकल्प रखने के लिए फ़र्श पर टैप करें। फिर हवा में दिख रहे उत्तर पर टैप करें।"),14f,Palette.muted),bottom=8)
        bottom.add(action(t("Pause and return","रोकें और वापस जाएँ"),false){finish()})
        root.addView(bottom,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM).apply{setMargins(dp(12),0,dp(12),dp(28))})
        setContentView(root);refreshQuestion()
    }
    private fun refreshQuestion(){
        if(training.finished){setResult(RESULT_OK);finish();return}
        // An answer persisted before process interruption is advanced exactly once.
        if(training.data.optBoolean("awaitingContinue")){training.advance();store.save(training);if(training.finished){finish();return}}
        question.text="${training.index+1}/${training.questions.size} · "+training.current.local("prompt",hi)
        options=training.options().map{it.getString("id") to it.local("text",hi)}
    }
    override fun onResume(){super.onResume();if(!::surface.isInitialized)return
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.CAMERA),1);return}
        try{
            if(ar==null){
                if(ArCoreApk.getInstance().requestInstall(this,!installRequested)==ArCoreApk.InstallStatus.INSTALL_REQUESTED){installRequested=true;return}
                ar=Session(this).apply{configure(Config(this).apply{planeFindingMode=Config.PlaneFindingMode.HORIZONTAL;updateMode=Config.UpdateMode.LATEST_CAMERA_IMAGE})}
            }
            ar?.resume();surface.onResume()
        }catch(e:Exception){android.app.AlertDialog.Builder(this).setTitle(t("AR is not ready","AR तैयार नहीं है")).setMessage(t("Use on-screen practice on this device. AR needs a supported phone and installed Google Play Services for AR.","इस उपकरण पर स्क्रीन अभ्यास करें। AR के लिए समर्थित फ़ोन और Google Play Services for AR चाहिए।")).setPositiveButton("OK"){_,_->finish()}.setOnCancelListener{finish()}.show()}
    }
    override fun onPause(){canAnswer=false;if(::surface.isInitialized)surface.onPause();ar?.pause();super.onPause()}
    override fun onDestroy(){anchor?.detach();ar?.close();if(::store.isInitialized)store.close();super.onDestroy()}
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)onResume()else finish()}
    override fun onSurfaceCreated(gl:GL10?,config:EGLConfig?){
        val textures=IntArray(1);GLES20.glGenTextures(1,textures,0);texture=textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
        val v=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 p;attribute vec2 uv;varying vec2 tex;void main(){gl_Position=vec4(p,0.0,1.0);tex=uv;}")
        val f=shader(GLES20.GL_FRAGMENT_SHADER,"#extension GL_OES_EGL_image_external : require\nprecision mediump float;uniform samplerExternalOES camera;varying vec2 tex;void main(){gl_FragColor=texture2D(camera,tex);}")
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,v);GLES20.glAttachShader(program,f);GLES20.glLinkProgram(program)
    }
    override fun onSurfaceChanged(gl:GL10?,width:Int,height:Int){widthPx=width;heightPx=height;GLES20.glViewport(0,0,width,height)}
    override fun onDrawFrame(gl:GL10?){
        GLES20.glClearColor(.06f,.09f,.14f,1f);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val session=ar?:return
        try{
            session.setCameraTextureName(texture);session.setDisplayGeometry(windowManager.defaultDisplay.rotation,widthPx,heightPx)
            val frame=session.update();frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,vertices,Coordinates2d.TEXTURE_NORMALIZED,uv)
            GLES20.glUseProgram(program);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture)
            val p=GLES20.glGetAttribLocation(program,"p");val u=GLES20.glGetAttribLocation(program,"uv")
            vertices.position(0);uv.position(0);GLES20.glEnableVertexAttribArray(p);GLES20.glEnableVertexAttribArray(u)
            GLES20.glVertexAttribPointer(p,2,GLES20.GL_FLOAT,false,0,vertices);GLES20.glVertexAttribPointer(u,2,GLES20.GL_FLOAT,false,0,uv)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(p);GLES20.glDisableVertexAttribArray(u)
            if(frame.camera.trackingState!=TrackingState.TRACKING){canAnswer=false;overlay.targets=emptyList();showStatus(t("Move the phone slowly. Tracking is paused.","फ़ोन धीरे घुमाएँ। ट्रैकिंग रुकी है।"));overlay.postInvalidate();return}
            val tap=tapped;tapped=null
            if(anchor==null&&tap!=null){frame.hitTest(tap.first,tap.second).firstOrNull{it.trackable is Plane&&(it.trackable as Plane).isPoseInPolygon(it.hitPose)}?.let{anchor=it.createAnchor()}}
            val a=anchor
            if(a==null){canAnswer=false;showStatus(t("Find a clear floor or tabletop and tap to place.","खुला फ़र्श या मेज़ खोजें और रखने के लिए टैप करें।"));return}
            if(a.trackingState!=TrackingState.TRACKING){canAnswer=false;overlay.targets=emptyList();overlay.postInvalidate();return}
            val projection=FloatArray(16);val view=FloatArray(16);val model=FloatArray(16);val vp=FloatArray(16);val mvp=FloatArray(16)
            frame.camera.getProjectionMatrix(projection,0,.1f,30f);frame.camera.getViewMatrix(view,0);a.pose.toMatrix(model,0)
            android.opengl.Matrix.multiplyMM(vp,0,projection,0,view,0);android.opengl.Matrix.multiplyMM(mvp,0,vp,0,model,0)
            val currentOptions=options
            overlay.targets=currentOptions.mapIndexedNotNull{i,option->
                val point=FloatArray(4);val x=(i-(currentOptions.size-1)/2f)*.5f
                android.opengl.Matrix.multiplyMV(point,0,mvp,0,floatArrayOf(x,.2f,0f,1f),0)
                if(point[3]<=.1f)null else {
                    val px=(point[0]/point[3]+1f)*widthPx/2;val py=(1f-point[1]/point[3])*heightPx/2
                    val scale=(1.1f/point[3]).coerceIn(.7f,1.25f);val w=dp(132)*scale;val h=dp(110)*scale
                    Target(option.first,option.second,RectF(px-w/2,py-h/2,px+w/2,py+h/2))
                }
            }
            canAnswer=!training.data.optBoolean("awaitingContinue")&&!training.finished
            showStatus(t("Stations placed · tap your answer","विकल्प रखे गए · अपना उत्तर चुनें"));overlay.postInvalidate()
        }catch(_:Exception){canAnswer=false;overlay.targets=emptyList();overlay.postInvalidate();showStatus(t("Tracking interrupted. Pause and retry.","ट्रैकिंग बाधित है। रोकें और फिर कोशिश करें।"))}
    }
    private fun showStatus(text:String){if(message!=text){message=text;runOnUiThread{status.text=text}}}
    private fun choose(id:String){
        if(!canAnswer)return;canAnswer=false
        val correct=training.answer(id);store.save(training)
        if(training.finished){finish();return}
        if(training.guided){android.app.AlertDialog.Builder(this).setTitle(if(correct)t("Safe understanding","सही समझ")else t("Let’s practise","अभ्यास करें")).setMessage(training.current.local("explanation",hi)).setCancelable(false).setPositiveButton(t("Continue","आगे बढ़ें")){_,_->training.advance();store.save(training);refreshQuestion()}.show()}
        else {training.advance();store.save(training);refreshQuestion()}
    }
    data class Target(val id:String,val label:String,val rect:RectF)
    inner class TargetOverlay:View(this@ArActivity){
        @Volatile var targets=emptyList<Target>()
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        init{contentDescription=t("World-anchored training choices. Tap a choice to answer.","दुनिया में रखे प्रशिक्षण विकल्प। उत्तर देने के लिए टैप करें।")}
        override fun onDraw(canvas:Canvas){
            targets.forEach{target->
                paint.color=Color.WHITE;canvas.drawRoundRect(target.rect,dp(14).toFloat(),dp(14).toFloat(),paint)
                paint.color=Palette.blue;paint.style=Paint.Style.STROKE;paint.strokeWidth=dp(2).toFloat();canvas.drawRoundRect(target.rect,dp(14).toFloat(),dp(14).toFloat(),paint);paint.style=Paint.Style.FILL
                val textPaint=android.text.TextPaint(paint).apply{color=Palette.ink;textSize=dp(15).toFloat()}
                val layout=android.text.StaticLayout.Builder.obtain(target.label,0,target.label.length,textPaint,(target.rect.width()-dp(16)).toInt().coerceAtLeast(1)).setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).build()
                canvas.save();canvas.translate(target.rect.left+dp(8),target.rect.centerY()-layout.height/2);layout.draw(canvas);canvas.restore()
            }
        }
        override fun onTouchEvent(event:MotionEvent):Boolean{if(event.action==MotionEvent.ACTION_UP){val target=targets.firstOrNull{it.rect.contains(event.x,event.y)};if(target!=null)choose(target.id)else tapped=event.x to event.y;performClick()};return true}
        override fun performClick():Boolean{super.performClick();return true}
    }
    companion object{
        private fun floats(values:FloatArray):FloatBuffer=ByteBuffer.allocateDirect(values.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(values);position(0)}
        private fun shader(type:Int,source:String):Int=GLES20.glCreateShader(type).also{GLES20.glShaderSource(it,source);GLES20.glCompileShader(it)}
    }
}
