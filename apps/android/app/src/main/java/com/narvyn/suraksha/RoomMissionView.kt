package com.narvyn.suraksha

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.*
import android.opengl.*
import android.opengl.Matrix
import android.os.SystemClock
import android.os.PowerManager
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import com.google.ar.core.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Three independently placed stations. Touch gestures never relocate an existing station. */
class RoomMissionView(private val host:Activity, val module:String, val camera:Boolean, private val hindi:Boolean):FrameLayout(host),GLSurfaceView.Renderer {
    data class Image(val revision:Int,val at:Long,val placement:Int,val tracked:Boolean,val message:String,
                     val targets:Map<String,ComponentProjection.Point> = emptyMap(),val inverse:FloatArray?=null,val frameWidth:Int=0,val frameHeight:Int=0,val phase:String="",val canPlace:Boolean=false,val retry:Boolean=false,
                     val footprint:List<ComponentProjection.Point> = emptyList(),
                     val surfaceBoundary:List<ComponentProjection.Point> = emptyList(),val scanProgress:Float=0f,val preview:Boolean=false)
    private data class Visual(val phase:String="ALARM",val progress:Float=0f,val held:Boolean=false,val showCues:Boolean=true,val explosionRisk:Boolean=false)
    private val surface=GLSurfaceView(host)
    private val overlay=Overlay()
    private val equipment=WorldEquipment()
    private val geometry=RoomMissionGeometry()
    private val gate=ComponentCameraGate()
    private val freshness=ComponentCameraFreshness()
    private val placement=AtomicBoolean(false)
    private val mailbox=AtomicReference<Image?>(null)
    private val posted=AtomicBoolean(false)
    private val anchors=mutableListOf<Anchor>() // GL owner; detach only after surface pause.
    private val facing=mutableListOf<Pose>() // Anchor-local rotation, never a saved world yaw.
    private val surfaceReadiness=ArSurfaceReadiness()
    private var cameraTextureRegistered=false // GL owner; reset only while renderer is stopped.
    private var closed=false
    private val sessionRelease=ArSessionRelease()
    @Volatile private var revision=0
    @Volatile private var foreground=false
    @Volatile private var visual=Visual()
    private var session:Session?=null
    private var running=false
    private val power=host.getSystemService(PowerManager::class.java)
    @Volatile private var thermalStatus=PowerManager.THERMAL_STATUS_NONE
    private var thermalReadAt=0L
    private var healthyAt=0L // GL owner; reset before resuming the surface.
    @Volatile private var lastDiagnostic=""
    private var diagnosticAt=0L
    private var metricsAt=0L;private var metricFrames=0;private var metricTracked=0;private var metricTimestamp=0L;private var maxUpdateMs=0L
    private val stopRequested=AtomicBoolean(false)
    private val frameTicker=object:Runnable{override fun run(){if(foreground&&running){
        val now=SystemClock.elapsedRealtime()
        if(camera && now-thermalReadAt>=1000){thermalReadAt=now;thermalStatus=power.currentThermalStatus
            if(thermalStatus>=PowerManager.THERMAL_STATUS_CRITICAL){stopCamera(coolingMessage());return}}
        surface.requestRender();postDelayed(this,if(camera&&thermalStatus>=PowerManager.THERMAL_STATUS_SEVERE)50 else 33)
    }}}
    val canPlace get()=foreground && image?.let{it.canPlace && it.revision==revision && it.frameWidth==width && it.frameHeight==height && SystemClock.elapsedRealtime()-it.at in 0..150}==true
    private var stoppedReason:String?=null // UI owner; survives focus changes and modal pauses.
    val needsRetry get()=stoppedReason!=null
    fun retryCamera(){
        if(!camera || closed || sessionRelease.closing || sessionRelease.failed)return
        // A failed internal session needs a new Session, but accepted learning actions remain saved.
        pause();stoppedReason=null;foreground=true
        if(!retireSession())resume()
    }
    fun diagnosticSummary():String = listOf(
        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}",
        if(camera)t("Camera AR requested","कैमरा AR अनुरोधित")else t("Screen simulation; camera not used","स्क्रीन सिमुलेशन; कैमरा उपयोग नहीं हुआ"),
        t("Last tracking status: ","अंतिम ट्रैकिंग स्थिति: ")+(lastDiagnostic.ifBlank{t("No camera frame received yet","कैमरा फ़्रेम अभी नहीं मिला")}),
        t("Thermal status: ","ताप स्थिति: ")+thermalStatus,
        stoppedReason?:t("No stopped-session error recorded","रुके सत्र की त्रुटि दर्ज नहीं"),
        t("This is runtime status, not proof of a completed AR exercise.","यह रनटाइम स्थिति है, AR अभ्यास पूरा होने का प्रमाण नहीं।")
    ).joinToString("\n\n")
    private fun coolingMessage()=t("Phone too hot for reliable AR. Camera paused. Let it cool, then tap Retry camera.","फ़ोन बहुत गर्म है। कैमरा रोका गया। ठंडा होने पर कैमरा फिर चलाएँ।")
    private fun stopCamera(message:String){pause();unavailable(message,true)}
    private fun requestStop(rev:Int,message:String){if(stopRequested.compareAndSet(false,true))post{if(foreground&&revision==rev)stopCamera(message);stopRequested.set(false)}}
    private fun diagnostic(value:String){
        val now=SystemClock.elapsedRealtime()
        if(value!=lastDiagnostic || now-diagnosticAt>=5000){Log.i("RoomAR",value);lastDiagnostic=value;diagnosticAt=now}
    }
    private fun trackingHelp(reason:TrackingFailureReason)=when(reason){
        TrackingFailureReason.INSUFFICIENT_LIGHT->t("Too dark to track. Use a brighter area and keep the camera uncovered.","ट्रैकिंग के लिए रोशनी कम है। उजली जगह चुनें और कैमरा खुला रखें।")
        TrackingFailureReason.INSUFFICIENT_FEATURES->t("Not enough surface detail. Aim at floor edges or a patterned floor; avoid glare and blank surfaces.","सतह पर विवरण कम है। फ़र्श के किनारे या पैटर्न देखें; चमक और सादी सतह से बचें।")
        TrackingFailureReason.EXCESSIVE_MOTION->t("Movement is too fast. Move the phone gently sideways with the floor in view.","फ़ोन तेज़ हिल रहा है। फ़र्श देखते हुए फ़ोन धीरे दाएँ-बाएँ हिलाएँ।")
        TrackingFailureReason.CAMERA_UNAVAILABLE->t("Camera access interrupted by another app. Return when the camera is free.","दूसरे ऐप से कैमरा बाधित हुआ। कैमरा खाली होने पर वापस आएँ।")
        TrackingFailureReason.BAD_STATE->t("AR motion tracking failed internally. Tap Retry camera to reset the session.","AR गति ट्रैकिंग में अंदरूनी त्रुटि है। कैमरा फिर चलाएँ।")
        else->t("Starting motion tracking. Move the phone gently sideways while looking at the floor.","गति ट्रैकिंग शुरू हो रही है। फ़र्श देखते हुए फ़ोन धीरे दाएँ-बाएँ हिलाएँ।")
    }
    private var installRequested=false
    private var texture=0;private var program=0;private var w=1;private var h=1
    private val vertices=floats(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f));private val uv=floats(FloatArray(8))
    private val projection=FloatArray(16);private val view=FloatArray(16);private val vp=FloatArray(16)
    private var image:Image?=null // UI owner
    private var down:Pair<Float,Float>?=null
    private var downAt=0L
    private var gestureSerial=0L
    @Volatile private var gestureTarget:String?=null
    private var screenCursor:Pair<Float,Float>?=null
    var onImage:(Image)->Unit={}
    var onAim:(Float,Float,Boolean,Long,Boolean)->Unit={_,_,_,_,_->}
    var onAction:(String)->Unit={}
    var onRelease:()->Unit={}
    var onCancel:()->Unit={}
    val ready get()=foreground && image?.let { it.revision==revision && it.frameWidth==width && it.frameHeight==height && it.phase==visual.phase && it.placement==3 && it.tracked && (!camera || SystemClock.elapsedRealtime()-it.at in 0..150) }==true
    private fun t(en:String,hi:String)=if(hindi)hi else en
    init {
        surface.setEGLContextClientVersion(2);surface.setEGLConfigChooser(8,8,8,8,16,0)
        surface.preserveEGLContextOnPause=true;surface.tag="room-mission-surface"
        surface.importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(surface,LayoutParams(-1,-1));addView(overlay,LayoutParams(-1,-1));overlay.setBackgroundColor(Palette.canvas)
        surface.setRenderer(this);surface.renderMode=GLSurfaceView.RENDERMODE_WHEN_DIRTY;surface.onPause()
        overlay.contentDescription=t("Interactive mission scene. Use screen procedure for text controls.","इंटरैक्टिव मिशन दृश्य। लिखित नियंत्रणों के लिए स्क्रीन प्रक्रिया उपयोग करें।")
    }
    fun update(phase:String,progress:Float,held:Boolean,showCues:Boolean=true,explosionRisk:Boolean=false) { visual=Visual(phase,progress,held,showCues,explosionRisk);overlay.invalidate() }
    fun place() { if(camera && canPlace)placement.set(true) }
    fun resume() {
        if(running || closed)return
        foreground=true;revision=gate.configure();gate.activate()
        if(camera && sessionRelease.failed){unavailable(ArCameraSupport.releaseMessage(hindi,true),true);return}
        if(camera && sessionRelease.busy){
            unavailable(ArCameraSupport.releaseMessage(hindi,false))
            sessionRelease.awaitAvailable{if(!closed&&foreground)resume()};return
        }
        if(camera) {
            stoppedReason?.let{unavailable(it,true);return}
            thermalStatus=power.currentThermalStatus;thermalReadAt=SystemClock.elapsedRealtime()
            if(thermalStatus>=PowerManager.THERMAL_STATUS_CRITICAL){unavailable(coolingMessage(),true);return}
            if(host.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) { unavailable(t("Enable camera access to place your training stations.","प्रशिक्षण स्थल रखने के लिए कैमरा अनुमति दें।"));return }
            try {
                if(session==null) {
                    if(ArCoreApk.getInstance().requestInstall(host,!installRequested)==ArCoreApk.InstallStatus.INSTALL_REQUESTED) {installRequested=true;unavailable(t("Finish AR installation, then return.","AR स्थापना पूरी करके वापस आएँ।"));return}
                    session=Session(host)
                    ArCameraSupport.configure(session!!)
                }
                freshness.requireNewImage();cameraTextureRegistered=false;session!!.resume()
            }catch(e:Exception) {Log.e("RoomAR","Session start failed: ${e.javaClass.simpleName}");retireSession();unavailable(ArCameraSupport.startupMessage(e,hindi),true);return}
        }
        healthyAt=SystemClock.elapsedRealtime();metricsAt=0;metricFrames=0;metricTracked=0;metricTimestamp=0;maxUpdateMs=0;running=true;keepScreenOn=camera;surface.onResume();removeCallbacks(frameTicker);post(frameTicker)
    }
    fun pause() {sessionRelease.cancelPendingResume();keepScreenOn=false;removeCallbacks(frameTicker);visual=visual.copy(held=false);foreground=false;gate.pause();revision=gate.configure();placement.set(false);image=null;down=null;screenCursor=null;gestureTarget=null
        if(running){surface.onPause();if(camera)try{session?.pause()}catch(_:Exception){};running=false}
        surfaceReadiness.reset();cameraTextureRegistered=false
    }
    // UI thread after surface.onPause() has stopped the GL owner. No retired AR object is reused.
    private fun retireSession():Boolean {
        val retired=session?:return false
        try{retired.pause()}catch(_:Exception){}
        anchors.forEach{it.detach()};anchors.clear();facing.clear();session=null
        freshness.clear();surfaceReadiness.reset();cameraTextureRegistered=false
        if(!closed)unavailable(t("Releasing the previous camera session…","पिछला कैमरा सत्र बंद हो रहा है…"))
        sessionRelease.retire(retired){
            if(sessionRelease.failed)stoppedReason=ArCameraSupport.releaseMessage(hindi,true)
            if(!closed && foreground)resume()
        }
        return true
    }
    fun close(){pause();closed=true;retireSession()}
    private fun unavailable(message:String,retry:Boolean=false) { if(retry)stoppedReason=message;val state=Image(revision,SystemClock.elapsedRealtime(),if(camera)anchors.size else 3,false,message,retry=retry);image=state;overlay.setBackgroundColor(Palette.canvas);onImage(state);overlay.invalidate() }
    override fun onSurfaceCreated(gl:GL10?,config:EGLConfig?) {
        cameraTextureRegistered=false;equipment.prepareProcedure(module);equipment.create();geometry.create()
        val textures=IntArray(1);GLES20.glGenTextures(1,textures,0);texture=textures[0];GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture)
        for(p in listOf(GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_TEXTURE_MAG_FILTER))GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,p,GLES20.GL_LINEAR)
        for(p in listOf(GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_TEXTURE_WRAP_T))GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,p,GLES20.GL_CLAMP_TO_EDGE)
        val vs=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 p;attribute vec2 uv;varying vec2 tex;void main(){gl_Position=vec4(p,0.,1.);tex=uv;}")
        val fs=shader(GLES20.GL_FRAGMENT_SHADER,"#extension GL_OES_EGL_image_external : require\nprecision mediump float;uniform samplerExternalOES camera;varying vec2 tex;void main(){gl_FragColor=texture2D(camera,tex);}")
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);GLES20.glLinkProgram(program);GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs)
    }
    override fun onSurfaceChanged(gl:GL10?,width:Int,height:Int){w=width;h=height;GLES20.glViewport(0,0,w,h);placement.set(false);revision=gate.configure();val resizedAtRevision=revision;post{if(revision==resizedAtRevision){down=null;gestureTarget=null;screenCursor=null;onCancel()}}}
    override fun onDrawFrame(gl:GL10?) {
        GLES20.glClearColor(.94f,.97f,.98f,1f);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if(!foreground)return
        val rev=revision;val state=visual;val models=mutableListOf<FloatArray>();var at=SystemClock.elapsedRealtime();val eye:FloatArray
        val light=floatArrayOf(1f,1f,1f,1f)
        var placementHit:HitResult?=null;var placementReady=false;var placementMessage:String?=null
        var previewPose:Pose?=null;var showPreview=false;var scanProgress=0f
        var footprintWorld=emptyList<FloatArray>();var surfaceWorld=emptyList<FloatArray>()
        try {
            if(camera) {
                val ar=session?:return
                if(!cameraTextureRegistered){ar.setCameraTextureName(texture);cameraTextureRegistered=true}
                ar.setDisplayGeometry(host.windowManager.defaultDisplay.rotation,w,h)
                val updateAt=SystemClock.elapsedRealtime();val frame=ar.update();at=SystemClock.elapsedRealtime()
                maxUpdateMs=maxOf(maxUpdateMs,at-updateAt)
                if(frame.timestamp>0 && frame.timestamp!=metricTimestamp){metricFrames++;metricTimestamp=frame.timestamp;if(frame.camera.trackingState==TrackingState.TRACKING)metricTracked++}
                if(metricsAt==0L)metricsAt=at
                if(at-metricsAt>=5000){Log.i("RoomAR","frames=$metricFrames trackedFrames=$metricTracked intervalMs=${at-metricsAt} maxUpdateMs=$maxUpdateMs");metricsAt=at;metricFrames=0;metricTracked=0;maxUpdateMs=0}
                val receipt=freshness.observedAt(frame.timestamp,at)
                if(frame.timestamp>0 && receipt!=null)drawCamera(frame)
                val tracking=frame.camera.trackingState;val reason=frame.camera.trackingFailureReason
                diagnostic("tracking=$tracking reason=$reason fresh=${receipt!=null} anchors=${anchors.size} thermal=$thermalStatus")
                if(receipt==null || tracking!=TrackingState.TRACKING) {
                    placement.set(false);surfaceReadiness.reset()
                    val message=if(receipt==null)t("Waiting for a fresh camera frame. Keep the app open.","नए कैमरा फ़्रेम की प्रतीक्षा है। ऐप खुला रखें।")else trackingHelp(reason)
                    publish(Image(rev,at,anchors.size,false,message))
                    if(reason==TrackingFailureReason.BAD_STATE || SystemClock.elapsedRealtime()-healthyAt>=45000)
                        requestStop(rev,if(reason==TrackingFailureReason.BAD_STATE)message else t("AR could not establish stable tracking. Camera paused. Tap Retry camera after checking lighting and phone temperature.","AR स्थिर ट्रैकिंग नहीं बना सका। कैमरा रोका गया। रोशनी और फ़ोन का तापमान जाँचकर कैमरा फिर चलाएँ।"))
                    return
                };at=receipt
                if(anchors.size<3){
                    placementHit=frame.hitTest(w/2f,h/2f).firstOrNull{(it.trackable as? Plane)?.let{p->p.type==Plane.Type.HORIZONTAL_UPWARD_FACING && p.trackingState==TrackingState.TRACKING && p.isPoseInPolygon(it.hitPose)}==true}
                    val hit=placementHit
                    val plane=hit?.trackable as? Plane
                    val decision=hit?.let{RoomStationPlacement.evaluate(module,
                        anchors.mapIndexed{i,a->RoomAnchorPose.station(RoomAnchorPose.model(a.pose,facing[i]))},
                        RoomStationPlacement.Station(it.hitPose.tx(),it.hitPose.tz()))}
                    if(hit!=null && plane!=null){
                        previewPose=RoomAnchorPose.model(hit.hitPose,RoomAnchorPose.facingOffset(hit.hitPose,frame.camera.pose))
                        footprintWorld=RoomPlacementArea.outline(module,anchors.size).map{previewPose!!.transformPoint(it)}
                        val polygon=plane.polygon.duplicate();val count=polygon.remaining()/2
                        // Bound preview work on large planes while keeping all footprint containment checks.
                        surfaceWorld=(0 until count step maxOf(1,count/64)).map{i->
                            plane.centerPose.transformPoint(floatArrayOf(polygon.get(i*2),.003f,polygon.get(i*2+1)))}
                    }
                    val area=if(hit!=null && plane!=null)RoomPlacementArea.evaluate(hit.distance,
                        anchors.firstOrNull()?.let{hit.hitPose.ty()-it.pose.ty()}?:0f,
                        footprintWorld.all{plane.isPoseInPolygon(Pose.makeTranslation(it))})else null
                    val eligible=decision?.allowed==true && area==RoomPlacementArea.Reason.READY && anchors.all{it.trackingState==TrackingState.TRACKING}
                    if(eligible && hit!=null && plane!=null){
                        val local=plane.centerPose.inverse().transformPoint(hit.hitPose.translation)
                        val stability=surfaceReadiness.observe(plane,local[0],local[2],frame.timestamp,SystemClock.elapsedRealtime(),rev)
                        placementReady=stability.ready;scanProgress=stability.progress;showPreview=true
                    }else surfaceReadiness.reset()
                    placementMessage=when{
                        hit==null->t("Scan the floor gently sideways. Use a bright, patterned area; the outline shows a detected surface.","फ़र्श धीरे दाएँ-बाएँ स्कैन करें। उजला, पैटर्न वाला क्षेत्र चुनें; रेखा मिली सतह दिखाती है।")
                        area==RoomPlacementArea.Reason.OUT_OF_RANGE->t("Aim at floor 0.6–3 m away. Turn or tilt the phone; do not walk backwards.","0.6–3 मीटर दूर फ़र्श पर निशाना रखें। फ़ोन घुमाएँ या झुकाएँ; पीछे न चलें।")
                        area==RoomPlacementArea.Reason.DIFFERENT_LEVEL->t("Use the same floor level for all three stations.","तीनों स्थल फ़र्श के एक ही स्तर पर रखें।")
                        area==RoomPlacementArea.Reason.INCOMPLETE_SURFACE->t("Scan a wider patch of floor until the full preview footprint fits the detected surface.","फ़र्श का बड़ा हिस्सा स्कैन करें, ताकि पूर्वावलोकन पूरा मिली सतह पर आए।")
                        decision?.reason in listOf(RoomStationPlacement.Reason.GAS_WRONG_SIDE,RoomStationPlacement.Reason.GAS_FOOTPRINT_OVERLAP)->t("Keep the green ring entirely on the outside of the barrier, away from the simulated opening.","हरे घेरे को पूरा अवरोध के बाहर रखें, काल्पनिक खुले स्थान से दूर।")
                        !eligible->t("Choose a separate point, farther from the other stations.","दूसरे स्थलों से दूर अलग बिंदु चुनें।")
                        !placementReady->t("Preview only · hold the phone steady briefly to confirm this point.","केवल पूर्वावलोकन · बिंदु की पुष्टि के लिए फ़ोन थोड़ा स्थिर रखें।")
                        else->t("Preview ready · tap Place station to anchor it here.","पूर्वावलोकन तैयार · यहाँ जोड़ने के लिए स्थल रखें दबाएँ।")
                    }
                }

                if(placement.getAndSet(false) && anchors.size<3) {
                    val hit=placementHit
                    if(hit!=null) {
                        if(placementReady) {
                            val next=hit.createAnchor();var accepted=false
                            gate.withCurrentRevision(rev){if(foreground && revision==rev && SystemClock.elapsedRealtime()-at in 0..150){anchors.add(next);facing.add(RoomAnchorPose.facingOffset(next.pose,frame.camera.pose));accepted=true}}
                            if(!accepted)next.detach() else {Log.i("RoomAR","anchor placed count=${anchors.size}");placementReady=false;placementHit=null;previewPose=null;showPreview=false;footprintWorld=emptyList();surfaceReadiness.reset()}
                        }else {publish(Image(rev,at,anchors.size,false,t("Choose a separate clear training point. Keep the green point away from the simulated hazard.","अलग खाली प्रशिक्षण बिंदु चुनें। हरा बिंदु काल्पनिक खतरे से दूर रखें।")));return}
                    }
                }
                if(anchors.any{it.trackingState!=TrackingState.TRACKING}) {
                    if(anchors.any{it.trackingState==TrackingState.STOPPED} || SystemClock.elapsedRealtime()-healthyAt>=45000)requestStop(rev,t("Placed stations could not be recovered. Tap Retry camera and place the stations again.","रखे स्थल वापस नहीं मिले। कैमरा फिर चलाकर स्थल फिर रखें।"))
                    publish(Image(rev,at,anchors.size,false,t("Recovering placed stations. Keep the floor in view; restart from Options if tracking does not return.","रखे स्थल फिर खोज रहे हैं। फ़र्श दृश्य में रखें; ट्रैकिंग न लौटे तो विकल्प से फिर शुरू करें।")));return}
                healthyAt=SystemClock.elapsedRealtime()
                frame.camera.getProjectionMatrix(projection,0,.05f,30f);frame.camera.getViewMatrix(view,0);eye=frame.camera.pose.translation
                anchors.forEachIndexed{i,a->models.add(FloatArray(16).apply{RoomAnchorPose.model(a.pose,facing[i]).toMatrix(this,0)})}
                if(frame.lightEstimate.state==LightEstimate.State.VALID){frame.lightEstimate.getColorCorrection(light,0);for(i in light.indices)light[i]=if(light[i].isFinite())light[i].coerceIn(.6f,1.4f)else 1f}
            }else {
                eye=floatArrayOf(0f,1.5f,3.7f);Matrix.perspectiveM(projection,0,if(w>h*1.4f)if(module=="fire")22f else 32f else 55f,w.toFloat()/h.coerceAtLeast(1),.05f,30f);Matrix.setLookAtM(view,0,eye[0],eye[1],eye[2],0f,.3f,-.5f,0f,1f,0f)
                for(p in listOf(floatArrayOf(-.6f,0f,0f),floatArrayOf(.1f,0f,-1f),floatArrayOf(.85f,0f,.3f)))models.add(FloatArray(16).apply{Matrix.setIdentityM(this,0);Matrix.translateM(this,0,p[0],p[1],p[2])})
            }
            Matrix.multiplyMM(vp,0,projection,0,view,0)
            val footprint=ArSurfaceProjection.project(footprintWorld,vp,w,h)
            val surfaceBoundary=ArSurfaceProjection.project(surfaceWorld,vp,w,h)
            val targets=mutableMapOf<String,ComponentProjection.Point>();var inverse:FloatArray?=null
            fun target(id:String,index:Int,p:FloatArray){if(index>=models.size)return;val mvp=FloatArray(16);Matrix.multiplyMM(mvp,0,vp,0,models[index],0);ComponentProjection.project(p,mvp,w,h)?.let{targets[id]=it}}
            models.getOrNull(0)?.let { m ->
                val flags=buildSet<String>{
                    if(module=="gas")add("room-mission")
                    if(module=="fire"&&state.phase in listOf("AIM","SWEEP","WITHDRAW","EVACUATE","ASSEMBLY","REPORT","COMPLETE"))add("fire-pin")
                    if(module=="fire"&&state.phase in listOf("SWEEP","WITHDRAW","EVACUATE","ASSEMBLY","REPORT","COMPLETE"))add("fire-aim")
                    if(module=="fire"&&state.held&&state.phase=="SWEEP")add("fire-squeeze")
                }
                if(state.phase=="PPE")geometry.draw(vp,m,"ppe-kit",at/1000f)else equipment.draw(vp,m,module,eye,light,flags,false,clearDepth=false)
                val alarm=m.copyOf();Matrix.translateM(alarm,0,.26f,.32f,0f);geometry.draw(vp,alarm,"alarm",at/1000f)
                target("alarm",0,floatArrayOf(.26f,.40f,0f));target("pin",0,floatArrayOf(-.055f,.478f,.015f));target("pin-out",0,floatArrayOf(-.20f,.478f,.015f));target("meter",0,floatArrayOf(0f,.30f,.085f))
            }
            models.getOrNull(1)?.let {m ->
                if(module=="gas")geometry.draw(vp,m,"confined-zone",at/1000f)
                geometry.draw(vp,m,if(module=="fire")"fire" else "barrier",at/1000f,if(state.phase=="SWEEP")state.progress*.5f else 0f,if(module=="fire")state.explosionRisk || state.phase in listOf("WITHDRAW","EVACUATE","ASSEMBLY","REPORT","COMPLETE") else state.phase in listOf("ATTENDANT","COMMUNICATE","ACKNOWLEDGE","REFUSE","COMPLETE"))
                target("base",1,floatArrayOf(0f,.08f,0f));target("left",1,floatArrayOf(-.45f,.08f,0f));target("right",1,floatArrayOf(.45f,.08f,0f))
                target("barrier-left",1,floatArrayOf(-.36f,.73f,0f));target("barrier-right",1,floatArrayOf(.36f,.73f,0f))
                val mvp=FloatArray(16);Matrix.multiplyMM(mvp,0,vp,0,m,0);val inv=FloatArray(16);if(Matrix.invertM(inv,0,mvp,0))inverse=inv
            }
            models.getOrNull(2)?.let{m->geometry.draw(vp,m,if(module=="fire" && state.phase in listOf("ASSEMBLY","REPORT","COMPLETE"))"assembly"else if(module=="fire"&&state.phase in listOf("EXIT","EQUIPMENT","EVACUATE"))"exit"else"safe-point",at/1000f);if(module=="gas"&&state.phase in listOf("COMMUNICATE","ACKNOWLEDGE","REFUSE","COMPLETE"))geometry.draw(vp,m,"attendant",at/1000f);target("safe",2,floatArrayOf(0f,.15f,0f))}
            if(module=="fire"&&state.phase in listOf("EXIT","EQUIPMENT","EVACUATE"))models.getOrNull(1)?.let{geometry.draw(vp,it,"blocked-exit",at/1000f)}
            if(state.phase=="PPE")models.getOrNull(1)?.let{geometry.draw(vp,it,"dust-mask",at/1000f)}
            RoomMissionChoices.forPhase(state.phase).forEach{target(it.action,it.station,floatArrayOf(it.x,it.y,it.z))}
            if(camera && showPreview && previewPose!=null){
                val model=FloatArray(16);previewPose!!.toMatrix(model,0)
                when(anchors.size){
                    0->equipment.draw(vp,model,module,eye,light,if(module=="gas")setOf("room-mission")else emptySet(),false,clearDepth=false)
                    1->{if(module=="gas")geometry.draw(vp,model,"confined-zone",at/1000f)
                        geometry.draw(vp,model,if(module=="fire")"fire" else "barrier",at/1000f,active=false)}
                    2->geometry.draw(vp,model,"safe-point",at/1000f)
                }
            }
            val needed=RoomMissionChoices.forPhase(state.phase).firstOrNull()?.action?:when(state.phase){"ALARM"->"alarm";"PIN"->"pin";"GAS_CHECK"->"meter";"ATTENDANT"->if(gestureTarget=="meter")"safe" else "meter";"BARRIER"->"barrier-left";"WITHDRAW","REFUSE"->"safe";else->"base"}
            val neededStation=RoomMissionChoices.forPhase(state.phase).firstOrNull{it.action==needed}?.station?:if(needed=="safe")2 else if(needed in listOf("alarm","pin","meter"))0 else 1
            val orientationHint=if(camera&&state.showCues&&models.size==3&&needed !in targets)t("Turn the phone slowly towards "+(if(neededStation==2)"the exit / outside station." else if(neededStation==0)"the equipment station." else "the virtual hazard."),"फ़ोन धीरे घुमाकर "+(if(neededStation==2)"निकास / बाहरी स्थल खोजें।" else if(neededStation==0)"उपकरण स्थल खोजें।" else "काल्पनिक खतरा खोजें।"))else null
            publish(Image(rev,at,models.size,true,orientationHint?:if(models.size<3)(placementMessage?:t("Aim at the next clear point.","अगले खाली बिंदु पर निशाना रखें।"))else if(camera)t("Stations anchored · stay in your clear practice area", "स्थल जुड़े हैं · अपने खाली अभ्यास क्षेत्र में रहें")else t("Screen simulation · virtual actions only","स्क्रीन सिमुलेशन · केवल काल्पनिक क्रियाएँ"),targets,inverse,w,h,state.phase,canPlace=placementReady,footprint=footprint,surfaceBoundary=surfaceBoundary,scanProgress=scanProgress,preview=showPreview))
        }catch(e:Exception){placement.set(false);Log.e("RoomAR","Frame failed: ${e.javaClass.simpleName}");requestStop(rev,t("AR session interrupted. Tap Retry camera to recover.","AR सत्र बाधित हुआ। कैमरा फिर चलाएँ।"))}
    }
    private fun publish(value:Image){mailbox.set(value);if(posted.compareAndSet(false,true))post{posted.set(false);val next=mailbox.getAndSet(null)?:return@post;if(!foreground||next.revision!=revision)return@post
        val previous=image;image=next;overlay.background=null
        if(!next.tracked || (camera&&SystemClock.elapsedRealtime()-next.at>150)){down=null;gestureTarget=null;screenCursor=null}
        onImage(next);overlay.invalidate()
        if(camera && next.tracked && next.placement==3 && next.at!=previous?.at && SystemClock.elapsedRealtime()-next.at in 0..150) {
            val cursor=if(camera)w/2f to h/2f else if(down!=null)screenCursor else null
            val point=cursor?.let{ray(it.first,it.second,next.inverse)}
            onAim(point?.first?:Float.NaN,point?.second?:Float.NaN,if(camera)visual.held else down!=null,next.at,true)
        }else if(camera && (!next.tracked || SystemClock.elapsedRealtime()-next.at>150))onAim(Float.NaN,Float.NaN,false,SystemClock.elapsedRealtime(),false)
    }}
    /** Offline pointer practice samples the displayed static geometry, independently of GPU frame rate.
     * Camera mode never uses this path or receives screen-mode evidence. */
    private fun sampleScreen(serial:Long){
        if(camera || serial!=gestureSerial || down==null || !ready)return
        val cursor=screenCursor;val point=cursor?.let{ray(it.first,it.second,image?.inverse)}
        onAim(point?.first?:Float.NaN,point?.second?:Float.NaN,true,SystemClock.elapsedRealtime(),true)
        postDelayed({sampleScreen(serial)},50)
    }
    private fun ray(x:Float,y:Float,inverse:FloatArray?):Pair<Float,Float>? {
        if(inverse==null)return null
        fun unproject(z:Float):FloatArray {val p=FloatArray(4);Matrix.multiplyMV(p,0,inverse,0,floatArrayOf(2*x/w-1,1-2*y/h,z,1f),0);return FloatArray(3){p[it]/p[3]}}
        val a=unproject(-1f);val b=unproject(1f);val dz=b[2]-a[2];if(kotlin.math.abs(dz)<.00001f)return null
        val fraction=-a[2]/dz;if(fraction !in 0f..1f)return null
        return (a[0]+(b[0]-a[0])*fraction)/.45f to (a[1]+(b[1]-a[1])*fraction-.08f)/.4f
    }
    private fun drawCamera(frame:Frame){
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);vertices.position(0);uv.position(0);frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,vertices,Coordinates2d.TEXTURE_NORMALIZED,uv)
        GLES20.glUseProgram(program);val p=GLES20.glGetAttribLocation(program,"p");val u=GLES20.glGetAttribLocation(program,"uv")
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"camera"),0)
        GLES20.glVertexAttribPointer(p,2,GLES20.GL_FLOAT,false,0,vertices);GLES20.glVertexAttribPointer(u,2,GLES20.GL_FLOAT,false,0,uv);GLES20.glEnableVertexAttribArray(p);GLES20.glEnableVertexAttribArray(u);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(p);GLES20.glDisableVertexAttribArray(u)
    }
    private inner class Overlay:View(host){
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        private val choiceBounds=mutableMapOf<String,RectF>()
        private fun radius()=host.dp(28).toFloat()
        private fun near(id:String,x:Float,y:Float)=image?.targets?.get(id)?.let{kotlin.math.hypot(x-it.x,y-it.y)<=radius()*1.4f}==true
        override fun onDraw(c:Canvas){
            choiceBounds.clear();val state=visual;val img=image;paint.strokeWidth=host.dp(2).toFloat();paint.style=Paint.Style.STROKE
            val cursor=if(camera)width/2f to height/2f else screenCursor
            cursor?.let{(x,y)->paint.color=if(camera&&img?.canPlace==true)Palette.teal else Color.WHITE;c.drawCircle(x,y,host.dp(10).toFloat(),paint);c.drawLine(x-20,y,x+20,y,paint);c.drawLine(x,y-20,x,y+20,paint);if(visual.phase in listOf("AIM","SWEEP")){paint.color=Palette.teal;val r=host.dp(19).toFloat();c.drawArc(RectF(x-r,y-r,x+r,y+r),-90f,360f*visual.progress,false,paint)}}
            if(camera && img?.tracked==true && img.placement<3){
                if(img.surfaceBoundary.size>2){
                    val area=Path();img.surfaceBoundary.forEachIndexed{i,p->if(i==0)area.moveTo(p.x,p.y)else area.lineTo(p.x,p.y)};area.close()
                    paint.color=0x180b8b83;paint.style=Paint.Style.FILL;c.drawPath(area,paint)
                    paint.color=Palette.teal;paint.style=Paint.Style.STROKE;paint.strokeWidth=host.dp(1).toFloat();c.drawPath(area,paint)
                }
                if(img.scanProgress>0f){paint.color=Palette.teal;paint.strokeWidth=host.dp(4).toFloat();val r=host.dp(25).toFloat()
                    c.drawArc(RectF(width/2f-r,height/2f-r,width/2f+r,height/2f+r),-90f,360f*img.scanProgress,false,paint)}
                if(img.preview){
                    val text=t("PREVIEW · NOT PLACED","पूर्वावलोकन · अभी नहीं रखा")
                    paint.textSize=12f*resources.displayMetrics.scaledDensity;paint.typeface=Typeface.DEFAULT_BOLD;paint.style=Paint.Style.FILL
                    val pad=host.dp(9).toFloat();val top=host.dp(12).toFloat()
                    val available=(width-pad*4).coerceAtLeast(1f)
                    if(paint.measureText(text)>available)paint.textSize*=available/paint.measureText(text)
                    val tw=paint.measureText(text);val metrics=paint.fontMetrics;val bottom=top+pad*2+metrics.descent-metrics.ascent
                    paint.color=0xeefff3db.toInt();c.drawRoundRect(RectF((width-tw)/2-pad,top,(width+tw)/2+pad,bottom),pad,pad,paint)
                    paint.color=0xff855300.toInt();c.drawText(text,(width-tw)/2,top+pad-metrics.ascent,paint);paint.style=Paint.Style.STROKE
                }
            }
            if(camera && img?.tracked==true && img.placement<3 && img.footprint.size>2){
                paint.color=if(img.canPlace)Palette.teal else Palette.amber;paint.strokeWidth=host.dp(3).toFloat()
                val path=Path();img.footprint.forEachIndexed{i,p->if(i==0)path.moveTo(p.x,p.y)else path.lineTo(p.x,p.y)};path.close();c.drawPath(path,paint)
            }
            val ids=if(RoomMissionChoices.forPhase(state.phase).isNotEmpty())emptyList()else when(state.phase){"ALARM"->listOf("alarm");"PIN"->listOf("pin");"GAS_CHECK"->listOf("meter");"BARRIER"->listOf("barrier-left","barrier-right");"ATTENDANT"->listOf("meter","safe");"WITHDRAW","REFUSE"->listOf("safe");else->listOf("left","right")}
            if(img?.tracked==true&&img.placement==3){
                paint.color=if(state.phase=="WITHDRAW")Palette.danger else Palette.teal
                if(state.showCues)for(id in ids)img.targets[id]?.let{p->c.drawCircle(p.x,p.y,radius(),paint)}
                if(state.showCues && state.phase in listOf("AIM","SWEEP")){val a=img.targets["left"];val b=img.targets["right"];if(a!=null&&b!=null)c.drawLine(a.x,a.y,b.x,b.y,paint)}
                if(state.held&&state.phase=="SWEEP"&&cursor!=null){paint.color=0xffc9eefa.toInt();paint.strokeWidth=host.dp(9).toFloat();c.drawLine(width*.7f,height.toFloat(),cursor.first,cursor.second,paint)}
                down?.let{start->screenCursor?.let{end->paint.color=Palette.violet;c.drawLine(start.first,start.second,end.first,end.second,paint)}}
                // Captions identify virtual objects even during recall; they do not reveal scoring.
                val choices=RoomMissionChoices.forPhase(state.phase)
                choices.forEach{choice->img.targets[choice.action]?.let{point->
                    paint.style=Paint.Style.STROKE;paint.strokeWidth=host.dp(2).toFloat();paint.color=Palette.blue
                    if(state.showCues)c.drawCircle(point.x,point.y,radius(),paint)
                    paint.style=Paint.Style.FILL;paint.textSize=13f*resources.displayMetrics.scaledDensity;paint.typeface=Typeface.DEFAULT_BOLD
                    val text=if(hindi)choice.hi else choice.en
                    val maxWidth=(width*.44f).coerceAtLeast(host.dp(70).toFloat())
                    // Two lines with ellipsis at large text; full instruction remains in the native prompt.
                    val parts=mutableListOf<String>();var rest=text
                    repeat(2){if(rest.isNotEmpty()){val count=paint.breakText(rest,true,maxWidth,null).coerceAtLeast(1);val cut=if(count<rest.length)rest.lastIndexOf(' ',count).takeIf{it>0}?:count else count;parts.add(rest.take(cut));rest=rest.drop(cut).trimStart()}}
                    if(rest.isNotEmpty()&&parts.isNotEmpty())parts[parts.lastIndex]=parts.last().dropLast(1)+"…"
                    val line=paint.fontSpacing;val boxW=(parts.maxOfOrNull{paint.measureText(it)}?:0f)+host.dp(16)
                    val left=(point.x-boxW/2).coerceIn(0f,(width-boxW).coerceAtLeast(0f));val top=(point.y-radius()-line*parts.size-host.dp(12)).coerceAtLeast(0f)
                    val bounds=RectF(left,top,left+boxW,top+line*parts.size+host.dp(8));choiceBounds[choice.action]=bounds
                    paint.color=0xf2ffffff.toInt();c.drawRoundRect(bounds,host.dp(8).toFloat(),host.dp(8).toFloat(),paint)
                    paint.color=Palette.ink;parts.forEachIndexed{i,part->c.drawText(part,left+host.dp(8),top+host.dp(4)-paint.fontMetrics.ascent+i*line,paint)}
                    paint.style=Paint.Style.STROKE
                }}
            }
        }
        private fun choiceAt(x:Float,y:Float):String? = RoomMissionChoices.forPhase(visual.phase)
            .mapNotNull{choice->image?.targets?.get(choice.action)?.let{p->choice.action to kotlin.math.hypot(x-p.x,y-p.y)}}
            .filter{it.second<=radius()*1.4f||choiceBounds[it.first]?.contains(x,y)==true}.minByOrNull{it.second}?.first
        private fun outward(dx:Float,dy:Float):Boolean {
            val a=image?.targets?.get("pin")?:return false;val b=image?.targets?.get("pin-out")?:return false
            val length=kotlin.math.hypot(b.x-a.x,b.y-a.y);return length>1f && (dx*(b.x-a.x)+dy*(b.y-a.y))/length>=host.dp(56)
        }
        override fun onTouchEvent(e:MotionEvent):Boolean {
            if(!ready){val hadPointer=down!=null;down=null;screenCursor=null;if(hadPointer&&e.actionMasked==MotionEvent.ACTION_UP)onRelease();return true}
            when(e.actionMasked){
                MotionEvent.ACTION_DOWN->{down=e.x to e.y;screenCursor=down;downAt=SystemClock.elapsedRealtime();gestureSerial++;if(!camera)sampleScreen(gestureSerial);gestureTarget=choiceAt(e.x,e.y)?:when(visual.phase){"ALARM"->"alarm";"PIN"->"pin";"GAS_CHECK"->"meter";"BARRIER"->"barrier-left";"ATTENDANT"->"meter";"WITHDRAW","REFUSE"->"safe";else->null}?.takeIf{near(it,e.x,e.y)};parent.requestDisallowInterceptTouchEvent(true)}
                MotionEvent.ACTION_MOVE->{screenCursor=e.x to e.y}
                MotionEvent.ACTION_UP->{
                    val start=down;val elapsed=SystemClock.elapsedRealtime()-downAt
                    if(start!=null && (!camera||image!!.at>=downAt) && elapsed>=80){val target=gestureTarget;val dist=kotlin.math.hypot(e.x-start.first,e.y-start.second)
                        val action=when {
                            target!=null&&RoomMissionChoices.forPhase(visual.phase).any{it.action==target}&&choiceAt(e.x,e.y)==target->target
                            target=="alarm"&&near(target,e.x,e.y)->"alarm"
                            target=="pin"&&dist>=host.dp(64)&&elapsed>=180&&outward(e.x-start.first,e.y-start.second)->"pin-drag"
                            target=="meter"&&visual.phase=="GAS_CHECK"&&near(target,e.x,e.y)&&elapsed>=450->"inspect-meter"
                            target=="barrier-left"&&near("barrier-right",e.x,e.y)&&elapsed>=300->"close-barrier"
                            target=="meter"&&visual.phase=="ATTENDANT"&&near("safe",e.x,e.y)&&elapsed>=300->"place-attendant-outside"
                            target=="safe"&&near(target,e.x,e.y)&&visual.phase=="WITHDRAW"->"withdraw"
                            target=="safe"&&near(target,e.x,e.y)&&visual.phase=="REFUSE"->"refuse-entry"
                            else->null
                        };if(action!=null)onAction(action)
                    };down=null;gestureTarget=null;screenCursor=null;onRelease();performClick();parent.requestDisallowInterceptTouchEvent(false)
                }
                MotionEvent.ACTION_CANCEL,MotionEvent.ACTION_POINTER_DOWN->{down=null;gestureTarget=null;screenCursor=null;onCancel()}
            };invalidate();return true
        }
        override fun performClick():Boolean{super.performClick();return true}
    }
    companion object {
        private fun floats(v:FloatArray)=ByteBuffer.allocateDirect(v.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(v);position(0)}
        private fun shader(type:Int,s:String)=GLES20.glCreateShader(type).also{GLES20.glShaderSource(it,s);GLES20.glCompileShader(it)}
    }
}
