package com.narvyn.suraksha

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.opengl.*
import android.opengl.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.view.*
import android.widget.*
import com.google.ar.core.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Camera-backed decision stations. Rendering never reads or mutates an assessment JSONObject. */
class ArActivity: Activity(), GLSurfaceView.Renderer {
    private data class Option(val id: String, val text: String)
    private data class Scene(val revision: Int, val questionId: String, val module: String, val options: List<Option>, val heights: List<Float>, val columns: Int, val width: Int, val height: Int, val canAnswer: Boolean)
    private data class Choice(val revision: Int, val questionId: String, val optionId: String)
    private data class Preview(val revision: Int, val points: List<ComponentProjection.Point?>, val boxes: List<ArChoiceLayout.Box>?, val message: String)
    private lateinit var store: Store
    private lateinit var flow: ArDecisionFlow
    private lateinit var surface: GLSurfaceView
    private lateinit var overlay: AnswerOverlay
    private lateinit var viewport: FrameLayout
    private lateinit var feedback: LinearLayout
    private lateinit var scroll: PagedPanel
    private lateinit var feedbackBody: LinearLayout
    private lateinit var controls: LinearLayout
    private lateinit var question: TextView
    private lateinit var status: TextView
    private lateinit var mode: TextView
    private var hi=false
    @Volatile private var active=false
    @Volatile private var running=false
    @Volatile private var ar: NativeArDriver?=null
    @Volatile private var scene=Scene(0,"","fire",emptyList(),emptyList(),1,0,0,false)
    private var missedPlacement: Int?=null
    private var anchor: Anchor?=null // Only accessed on GL thread while running; stopped before UI cleanup.
    private var installRequested=false
    private var blocked: String?=null
    private val release=ArSessionRelease()
    @Volatile private var textureRegistered=false
    private val cameraPump by lazy { ArCameraFramePump(surface) { stopWithMessage(ArCameraSupport.coolingMessage(hi)) } }
    private var widthPx=1;private var heightPx=1
    private var texture=0;private var program=0
    private val equipment=WorldEquipment()
    private val freshness=ComponentCameraFreshness()
    private val lightCorrection=floatArrayOf(1f,1f,1f,1f);private val estimatedLight=FloatArray(4)
    private val projection=FloatArray(16);private val view=FloatArray(16);private val model=FloatArray(16);private val vp=FloatArray(16);private val mvp=FloatArray(16)
    private val pendingChoice=AtomicReference<Choice?>(null)
    private val pendingPlacement=ArPlacementQueue()
    private val pendingPreview=AtomicReference<Preview?>(null)
    private val previewPosted=AtomicBoolean(false)
    private val profileRetirementPosted=AtomicBoolean(false)
    private val vertices=floats(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f))
    private val uv=floats(FloatArray(8))
    private fun t(en: String,hindi: String)=if(hi)hindi else en

       // API 33+ uses AppBackNavigation and PagedPanel callbacks; retain this fallback for API 29–32.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Deprecated("Android 10–12 compatibility") override fun onBackPressed(){if(!popContentPage())super.onBackPressed()}
 override fun onCreate(state: Bundle?) {
        super.onCreate(state);store=Store(this);hi=store.hi
        if(state?.getString("workerId")?.let { it!=store.workerId }==true) { finish();return }
        val data=store.attempt(intent.getStringExtra("attemptId") ?: "")
        if(data==null) { finish();return }
        val curriculum=Curriculum(this)
        val module=curriculum.versions[data.optString("contentVersion")]?.getJSONArray("modules")?.objects()?.firstOrNull { it.getString("id")==data.optString("moduleId") }
        if(module==null) { Toast.makeText(this,t("This training version cannot be resumed.","प्रशिक्षण का यह संस्करण जारी नहीं किया जा सकता।"),Toast.LENGTH_LONG).show();finish();return }
        flow=ArDecisionFlow(TrainingSession(data,module)) { check(active && currentWorker()) { "Worker profile changed" };store.save(it) }
        installRequested=state?.getBoolean("installRequested") ?: false
        val root=column(16).apply { setBackgroundColor(Palette.canvas) }
        root.setOnApplyWindowInsetsListener { v,i ->
            if(android.os.Build.VERSION.SDK_INT>=30) { val b=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(16)+b.left,dp(12)+b.top,dp(16)+b.right,dp(12)+b.bottom) }
            else v.setPadding(dp(16)+i.systemWindowInsetLeft,dp(12)+i.systemWindowInsetTop,dp(16)+i.systemWindowInsetRight,dp(12)+i.systemWindowInsetBottom)
            i
        }
        val body=column()
        scroll=paged(body,hi)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        mode=label("",13f,Palette.blue,true);body.add(mode,bottom=8)
        question=label("",20f,Palette.ink,true).asHeading();body.add(question,bottom=8)
        status=label("",14f,Palette.muted);body.add(status,bottom=12)
        viewport=FrameLayout(this)
        surface=GLSurfaceView(this).apply {
            setEGLContextClientVersion(2);setEGLConfigChooser(8,8,8,8,16,0);preserveEGLContextOnPause=true
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setRenderer(this@ArActivity);renderMode=GLSurfaceView.RENDERMODE_WHEN_DIRTY;onPause()
        }
        viewport.addView(surface,FrameLayout.LayoutParams(-1,-1))
        overlay=AnswerOverlay();viewport.addView(overlay,FrameLayout.LayoutParams(-1,-1))
        body.addView(viewport,LinearLayout.LayoutParams(-1,dp(320)))
        feedbackBody=column(12)
        feedback=feedbackBody
        body.add(feedback)
        controls=column();body.add(controls,top=12)
        root.add(action(t("Continue on screen","स्क्रीन पर जारी रखें"),false) { setResult(RESULT_OK);finish() },top=10)
        viewport.addOnLayoutChangeListener { _,l,top,r,b,ol,ot,or,ob ->
            if((r-l!=or-ol || b-top!=ob-ot) && ::flow.isInitialized && currentWorker() && !flow.finished && !flow.training.data.optBoolean("awaitingContinue")) {
                flow.invalidate();refreshScene()
            }
        }
        setContentView(root);renderState()
    }
    private fun renderState() {
        if(!currentWorker() || !::flow.isInitialized)return
        if(flow.finished) { stopCamera();setResult(RESULT_OK);finish();return }
        val training=flow.training
        mode.text=if(training.guided)t("AR PRACTICE · SIMULATED","AR अभ्यास · काल्पनिक")else t("AR ASSESSMENT · NO HINTS","AR मूल्यांकन · कोई संकेत नहीं")
        question.text="${training.index+1}/${training.questions.size} · "+training.current.local("prompt",hi)
        val saved=training.data.optBoolean("awaitingContinue")
        feedback.visibility=if(saved)View.VISIBLE else View.GONE
        viewport.visibility=if(saved)View.GONE else View.VISIBLE
        controls.removeAllViews();feedbackBody.removeAllViews()
        if(saved) {
            stopCamera()
            status.text=t("Your answer is saved. Continue when you are ready.","आपका उत्तर सहेजा गया है। तैयार होने पर आगे बढ़ें।")
            val card=card(if(training.guided && !training.data.optBoolean("lastCorrect"))Palette.amberBg else Palette.soft)
            card.add(label(if(training.guided)if(training.data.optBoolean("lastCorrect"))t("Safe understanding","सही समझ")else t("Let’s learn from this","इससे सीखें") else t("Answer saved","उत्तर सहेजा गया"),22f,Palette.ink,true),bottom=12)
            if(training.guided) card.add(label(training.current.local("explanation",hi),18f))
            else card.add(label(t("Assessment feedback is available after the attempt ends.","मूल्यांकन समाप्त होने के बाद प्रतिक्रिया उपलब्ध होगी।"),16f,Palette.muted))
            feedbackBody.add(card,bottom=16)
            val revision=flow.revision
            feedbackBody.add(action(t("Continue","आगे बढ़ें")) {
                if(active && currentWorker())try { if(flow.continueSaved(revision)) { renderState();if(!flow.finished)startCamera(false) } } catch(_: Exception) { saveFailed() }
            })
        } else {
            status.text=t("Place the simulated stations on a clear training surface.","काल्पनिक विकल्प खाली प्रशिक्षण सतह पर रखें।")
            val row=LinearLayout(this)
            row.addView(action(t("Place at centre","बीच में रखें"),false,role=ActionRole.CAMERA) { requestPlacementAt(viewport.width/2f,viewport.height/2f) }.apply { tag="ar-place-center";contentDescription=t("Place decision stations at camera centre","निर्णय विकल्प कैमरा दृश्य के बीच में रखें") },LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=dp(8) })
            row.addView(action(if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)t("Retry camera","कैमरा फिर आज़माएँ")else t("Enable camera","कैमरा चालू करें"),false) { retryCamera() },LinearLayout.LayoutParams(0,-2,1f))
            controls.add(row)
            if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) controls.add(action(t("Camera permission settings","कैमरा अनुमति सेटिंग"),false,role=ActionRole.NEUTRAL) { if(active && currentWorker())startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:$packageName"))) },top=8)
        }
        refreshScene();scroll.post { scroll.firstPage() }
    }
    private fun refreshScene() {
        if(!currentWorker() || !::overlay.isInitialized || !::flow.isInitialized || flow.finished) return
        pendingChoice.set(null);pendingPlacement.clear()
        val training=flow.training
        val options=training.options().map { Option(it.getString("id"),it.local("text",hi)) }
        val columns=if(options.size==2 && viewport.width>=dp(280))2 else 1
        val widths=((viewport.width-dp(10)*(columns+1))/columns).coerceAtLeast(1)
        val revision=flow.revision
        val heights=overlay.bind(options,widths,revision,training.current.getString("id"))
        scene=Scene(revision,training.current.getString("id"),training.data.getString("moduleId"),options,heights,columns,viewport.width,viewport.height,!training.data.optBoolean("awaitingContinue"))
    }
    override fun onResume() {
        super.onResume()
        if(!currentWorker() || !::flow.isInitialized)return
        active=true
        flow.resume();renderState();if(!flow.finished && !flow.training.data.optBoolean("awaitingContinue"))startCamera(false)
    }
    override fun onPause() { active=false;if(::flow.isInitialized)flow.pause();stopCamera();super.onPause() }
    override fun onDestroy() { active=false;stopCamera();retireSession();if(::store.isInitialized)store.close();super.onDestroy() }
    override fun onSaveInstanceState(out: Bundle) { out.putBoolean("installRequested",installRequested);if(::store.isInitialized)out.putString("workerId",store.workerId);super.onSaveInstanceState(out) }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code,permissions,results)
        if(code==51 && active && ::flow.isInitialized) { renderState();startCamera(false) }
    }
    // Called on the UI thread, including before applying a renderer's delayed choice.
    private fun currentWorker(): Boolean {
        if(!::store.isInitialized || isFinishing || isDestroyed)return false
        if(store.isCurrentProfile())return true
        active=false;if(::flow.isInitialized)flow.pause();stopCamera();finish();return false
    }
    private fun startCamera(requestPermission: Boolean) {
        if(!currentWorker() || !::flow.isInitialized || !active || flow.finished || flow.training.data.optBoolean("awaitingContinue") || running)return
        blocked?.let { status.text=it; return }
        if(release.failed) { status.text=ArCameraSupport.releaseMessage(hi,true); return }
        if(release.busy) {
            status.text=ArCameraSupport.releaseMessage(hi,false)
            release.awaitAvailable { if(active && !isDestroyed && !isFinishing && blocked==null)startCamera(false) }
            return
        }
        if(cameraPump.tooHot()) { stopWithMessage(ArCameraSupport.coolingMessage(hi)); return }
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) {
            status.text=t("Camera permission is off. Enable it, or continue on screen.","कैमरा अनुमति बंद है। अनुमति दें, या स्क्रीन पर जारी रखें।")
            if(requestPermission)requestPermissions(arrayOf(Manifest.permission.CAMERA),51)
            return
        }
        try {
            if(ar==null) {
                if(ArCoreApk.getInstance().requestInstall(this,!installRequested)==ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                    installRequested=true;status.text=t("Complete AR installation, or continue on screen.","AR स्थापना पूरी करें, या स्क्रीन पर जारी रखें।");return
                }
                ar=NativeArDriver(this)

            }
            freshness.requireNewImage();textureRegistered=false;ar!!.resume();running=true;overlay.cameraVisible(true);surface.onResume();cameraPump.start()
        } catch(error: Exception) {
            android.util.Log.e("TrainingAR", "Assessment camera start failed: ${error.javaClass.simpleName}")
            stopWithMessage(ArCameraSupport.startupMessage(error,hi))
        }
    }
    private fun stopCamera() {
        release.cancelPendingResume()
        if(::surface.isInitialized)cameraPump.stop()
        textureRegistered=false
        val wasRunning=running;running=false
        pendingChoice.set(null);pendingPlacement.clear();pendingPreview.set(null)
        if(::overlay.isInitialized) { overlay.clear();overlay.cameraVisible(false) }
        if(::surface.isInitialized)surface.onPause()
        if(wasRunning) { try { ar?.pause() } catch(_: Exception) {} };running=false
    }
    private fun retireSession() {
        val retired=ar ?: return
        try { retired.pause() } catch(_: Exception) {}
        anchor?.detach();anchor=null;ar=null;freshness.clear();textureRegistered=false
        release.retire(retired) { if(active && !isDestroyed && !isFinishing && blocked==null)startCamera(false) }
    }
    private fun stopWithMessage(message:String) {
        blocked=message
        if(::flow.isInitialized)flow.invalidate()
        stopCamera();retireSession()
        if(::status.isInitialized)status.text=message
    }
    private fun retryCamera() {
        if(!active || !currentWorker())return
        stopCamera();blocked=null;retireSession();installRequested=false
        flow.invalidate();refreshScene();startCamera(true)
    }
    private fun saveFailed() {
        if(!currentWorker())return
        flow.invalidate();stopCamera();renderState()
        status.text=t("Could not save. Your previously saved progress is preserved.","सहेजा नहीं जा सका। पहले सहेजी गई प्रगति सुरक्षित है।")
        notice(t("Could not save","सहेजा नहीं जा सका"),t("Check available storage, then retry from your saved progress.","उपलब्ध स्टोरेज जाँचें, फिर सहेजी गई प्रगति से कोशिश करें।"))
    }
    private fun requestPlacementAt(x: Float,y: Float) {
        if(!currentWorker())return
        if(!active || !running || !scene.canAnswer) {
            status.text=t("Camera is not running. Enable it, or continue on screen.","कैमरा चालू नहीं है। चालू करें, या स्क्रीन पर जारी रखें।");return
        }
        val visible=Rect()
        if(!viewport.getLocalVisibleRect(visible) || !visible.contains(x.toInt(),y.toInt())) {
            status.text=t("Show the camera centre before placing, or continue on screen.","रखने से पहले कैमरा दृश्य का बीच दिखाएँ, या स्क्रीन पर जारी रखें।");return
        }
        flow.invalidate();refreshScene()
        pendingPlacement.offer(ArPlacementRequest.createAt(flow.revision,x,y,viewport.width,viewport.height,SystemClock.elapsedRealtime()))
    }
    private fun queueChoice(optionId: String, revision: Int, questionId: String) {
        if(active && currentWorker() && flow.revision==revision && flow.ready(SystemClock.elapsedRealtime()) && overlay.targetsVisible()) pendingChoice.compareAndSet(null,Choice(revision,questionId,optionId))
    }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        ar?.invalidateTexture()
        textureRegistered=false;equipment.create()
        val textures=IntArray(1);GLES20.glGenTextures(1,textures,0);texture=textures[0];GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
        val v=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 p;attribute vec2 uv;varying vec2 tex;void main(){gl_Position=vec4(p,0.0,1.0);tex=uv;}")
        val f=shader(GLES20.GL_FRAGMENT_SHADER,"#extension GL_OES_EGL_image_external : require\nprecision mediump float;uniform samplerExternalOES camera;varying vec2 tex;void main(){gl_FragColor=texture2D(camera,tex);}")
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,v);GLES20.glAttachShader(program,f);GLES20.glLinkProgram(program);GLES20.glDeleteShader(v);GLES20.glDeleteShader(f)
    }
    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) { widthPx=w;heightPx=h;GLES20.glViewport(0,0,w,h) }
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClearColor(.965f,.973f,.99f,1f);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if(!running)return
        if(!store.isCurrentProfile()) {
            // Never stop GLSurfaceView from its renderer thread; clear eligibility and retire on the UI thread.
            if(profileRetirementPosted.compareAndSet(false,true))runOnUiThread { profileRetirementPosted.set(false);currentWorker() }
            return
        }
        val current=scene;val session=ar ?: return
        try {
            if(!textureRegistered) { session.setCameraTextureName(texture);textureRegistered=true };session.setDisplayGeometry(windowManager.defaultDisplay.rotation,widthPx,heightPx)
            val frame=session.update()
            val observedAt=freshness.observedAt(frame.timestamp,SystemClock.elapsedRealtime())
            if(frame.timestamp>0L && observedAt!=null)drawCamera(frame)
            if(observedAt==null || !session.snapshot().ready) { unavailable(current,t("Tracking paused. Wait for a fresh, tracked camera image.","ट्रैकिंग रुकी है। नए, ट्रैक किए गए कैमरा दृश्य की प्रतीक्षा करें।"));return }
            val tap=pendingPlacement.consumeFor(current.revision)
            if(tap?.eligible(flow.revision,widthPx,heightPx,SystemClock.elapsedRealtime(),active && running && current.canAnswer,session.snapshot().ready,observedAt!=null)==true && current.revision==flow.revision) {
                val hit=frame.hitTest(tap.x,tap.y).firstOrNull { val plane=it.trackable as? Plane;plane!=null && plane.type==Plane.Type.HORIZONTAL_UPWARD_FACING && plane.trackingState==TrackingState.TRACKING && plane.isPoseInPolygon(it.hitPose) }
                if(hit!=null) { val replacement=hit.createAnchor();anchor?.detach();anchor=replacement;missedPlacement=null } else missedPlacement=current.revision
            }
            val a=anchor
            if(a==null) { unavailable(current,if(missedPlacement==current.revision)placementMissMessage()else t("Aim the camera centre at a clear surface, then select Place at centre.","खाली सतह पर कैमरा दृश्य का बीच रखें, फिर बीच में रखें चुनें।"));return }
            if(a.trackingState!=TrackingState.TRACKING) {
                if(a.trackingState==TrackingState.STOPPED) { a.detach();anchor=null }
                unavailable(current,t("Placement tracking stopped. Wait or place again.","रखी जगह की ट्रैकिंग रुकी है। प्रतीक्षा करें या फिर रखें।"));return
            }
            frame.camera.getProjectionMatrix(projection,0,.1f,30f);frame.camera.getViewMatrix(view,0);a.pose.toMatrix(model,0)
            Matrix.multiplyMM(vp,0,projection,0,view,0);Matrix.multiplyMM(mvp,0,vp,0,model,0)
            if(frame.lightEstimate.state==LightEstimate.State.VALID) { frame.lightEstimate.getColorCorrection(estimatedLight,0);for(i in 0..3)if(estimatedLight[i].isFinite())lightCorrection[i]=lightCorrection[i]*.9f+estimatedLight[i].coerceIn(.65f,1.35f)*.1f }
            equipment.draw(vp,model,current.module,frame.camera.pose.translation,lightCorrection)
            val points=current.options.indices.map { i -> ComponentProjection.project(floatArrayOf((i-(current.options.size-1)/2f)*.5f,.75f,0f),mvp,widthPx,heightPx) }
            val boxes=if(current.canAnswer && current.width==widthPx && current.height==heightPx)ArChoiceLayout.arrange(points,current.heights,current.columns,widthPx,heightPx,dp(10).toFloat())else null
            flow.frame(current.revision,boxes!=null,observedAt)
            val choice=pendingChoice.getAndSet(null)
            if(boxes!=null && choice!=null && choice.revision==current.revision && choice.questionId==current.questionId && flow.ready(SystemClock.elapsedRealtime())) {
                runOnUiThread {
                    if(active && currentWorker() && flow.revision==choice.revision && overlay.targetsVisible()) {
                        try { if(flow.choose(choice.optionId,choice.questionId,choice.revision,SystemClock.elapsedRealtime()))renderState() } catch(_: Exception) { saveFailed() }
                    }
                }
            }
            publish(Preview(current.revision,points,boxes,if(missedPlacement==current.revision)placementMissMessage()else if(boxes==null)t("Keep every station in view. If choices do not fit, continue on screen.","सभी विकल्प दृश्य में रखें। विकल्प न समाएँ तो स्क्रीन पर जारी रखें।")else t("Stations placed · choose one response.","विकल्प रखे गए · एक उत्तर चुनें।")))
        } catch(error: Exception) {
            val message=t("Camera interrupted. Retry, or continue on screen.","कैमरा बाधित है। फिर कोशिश करें, या स्क्रीन पर जारी रखें।")
            unavailable(current,message)
            runOnUiThread { if(active && running && ar===session && flow.revision==current.revision)stopWithMessage(message) }
        }
    }
    private fun drawCamera(frame: Frame) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);vertices.position(0);uv.position(0)
        frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,vertices,Coordinates2d.TEXTURE_NORMALIZED,uv)
        GLES20.glUseProgram(program);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture)
        val p=GLES20.glGetAttribLocation(program,"p");val u=GLES20.glGetAttribLocation(program,"uv");vertices.position(0);uv.position(0)
        GLES20.glEnableVertexAttribArray(p);GLES20.glEnableVertexAttribArray(u);GLES20.glVertexAttribPointer(p,2,GLES20.GL_FLOAT,false,0,vertices);GLES20.glVertexAttribPointer(u,2,GLES20.GL_FLOAT,false,0,uv)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(p);GLES20.glDisableVertexAttribArray(u)
    }
    private fun placementMissMessage()=t("No tracked surface at this point. Aim at a clear surface and try again.","इस बिंदु पर ट्रैक की गई सतह नहीं मिली। खाली सतह पर निशाना रखकर फिर कोशिश करें।")
    private fun unavailable(current: Scene, message: String) {
        flow.frame(current.revision,false,SystemClock.elapsedRealtime());pendingChoice.set(null)
        pendingPlacement.discardFor(current.revision)
        publish(Preview(current.revision,emptyList(),null,message))
    }
    private fun publish(value: Preview) {
        pendingPreview.set(value)
        if(previewPosted.compareAndSet(false,true))runOnUiThread {
            previewPosted.set(false);val latest=pendingPreview.getAndSet(null)
            if(active && currentWorker() && latest!=null && flow.revision==latest.revision && scene.canAnswer) {
                if(latest.boxes!=null && flow.ready(SystemClock.elapsedRealtime()))overlay.position(latest.points,latest.boxes)else overlay.clear()
                if(status.text.toString()!=latest.message)status.text=latest.message
            }
        }
    }
    private inner class AnswerOverlay: FrameLayout(this@ArActivity) {
        private val buttons=mutableListOf<Button>();private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        private var lines=emptyList<FloatArray>()
        private var cameraIsVisible=false
        private val placeholder=label(t("Camera view paused\nYou can continue on screen.","कैमरा दृश्य रुका है\nआप स्क्रीन पर जारी रख सकते हैं।"),17f,Palette.muted,true).apply { gravity=Gravity.CENTER;setPadding(dp(16),dp(16),dp(16),dp(16));background=shape(Palette.surface,16,Palette.line) }
        init { setWillNotDraw(false);cameraVisible(false);addView(placeholder,LayoutParams(-1,-2,Gravity.CENTER).apply { setMargins(dp(8),0,dp(8),0) }) }
        fun cameraVisible(visible: Boolean) { cameraIsVisible=visible;setBackgroundColor(if(visible)Color.TRANSPARENT else Palette.canvas);placeholder.visibility=if(visible)GONE else VISIBLE;invalidate() }
        fun bind(options: List<Option>, width: Int, revision: Int, questionId: String): List<Float> {
            buttons.forEach { removeView(it) };buttons.clear();clear()
            return options.map { option ->
                val button=action(option.text,false) { queueChoice(option.id,revision,questionId) }.apply { tag="ar-answer-${option.id}";textSize=16f;setPadding(dp(12),dp(10),dp(12),dp(10));visibility=INVISIBLE;isEnabled=false }
                button.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED))
                val h=button.measuredHeight.coerceAtLeast(dp(48));buttons.add(button);addView(button,LayoutParams(width,h));h.toFloat()
            }
        }
        fun clear() { buttons.forEach { it.visibility=INVISIBLE;it.isEnabled=false };lines=emptyList();placeholder.visibility=if(cameraIsVisible)GONE else VISIBLE;invalidate() }
        fun targetsVisible(): Boolean {
            val visible=Rect()
            return isShown && buttons.isNotEmpty() && getLocalVisibleRect(visible) && buttons.all { b -> b.visibility==VISIBLE && b.isEnabled && visible.contains(kotlin.math.floor(b.x).toInt(),kotlin.math.floor(b.y).toInt(),kotlin.math.ceil(b.x+b.width).toInt(),kotlin.math.ceil(b.y+b.height).toInt()) }
        }
        fun position(points: List<ComponentProjection.Point?>, boxes: List<ArChoiceLayout.Box>) {
            if(boxes.size!=buttons.size) { clear();return }
            placeholder.visibility=GONE
            buttons.forEachIndexed { i,b -> val box=boxes[i];b.x=box.left;b.y=box.top;b.visibility=VISIBLE;b.isEnabled=true }
            lines=points.mapIndexedNotNull { i,p -> p?.let { floatArrayOf(it.x,it.y,boxes[i].left+boxes[i].width/2,boxes[i].top+boxes[i].height/2) } };invalidate()
        }
        override fun onDraw(canvas: Canvas) { super.onDraw(canvas);if(cameraIsVisible)PlacementAim.draw(canvas,paint,this@ArActivity,width,height);lines.forEach { p -> paint.color=Color.WHITE;paint.strokeWidth=dp(5).toFloat();canvas.drawLine(p[0],p[1],p[2],p[3],paint);paint.color=Palette.blue;paint.strokeWidth=dp(2).toFloat();canvas.drawLine(p[0],p[1],p[2],p[3],paint) } }
        override fun onTouchEvent(event: MotionEvent): Boolean { if(event.action==MotionEvent.ACTION_UP) { requestPlacementAt(event.x,event.y);performClick() };return true }
        override fun performClick(): Boolean { super.performClick();return true }
    }
    companion object {
        private fun floats(values: FloatArray): FloatBuffer=ByteBuffer.allocateDirect(values.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values);position(0) }
        private fun shader(type: Int,source: String): Int=GLES20.glCreateShader(type).also { GLES20.glShaderSource(it,source);GLES20.glCompileShader(it) }
    }
}
