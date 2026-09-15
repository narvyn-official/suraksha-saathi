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
import java.util.EnumSet
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
                     val footprint:List<ComponentProjection.Point> = emptyList())
    private data class Visual(val phase:String="ALARM",val progress:Float=0f,val held:Boolean=false)
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
    private val facing=mutableListOf<Float>()
    @Volatile private var revision=0
    @Volatile private var foreground=false
    @Volatile private var visual=Visual()
    private var session:Session?=null
    private var running=false
    private val power=host.getSystemService(PowerManager::class.java)
    @Volatile private var thermalStatus=PowerManager.THERMAL_STATUS_NONE
    private var thermalReadAt=0L
    private var healthyAt=0L // GL owner; reset before resuming the surface.
    private var lastDiagnostic=""
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
        if(!camera)return
        // A failed internal session needs a new Session, but accepted learning actions remain saved.
        pause();anchors.forEach{it.detach()};anchors.clear();facing.clear();session?.close();session=null;freshness.clear();stoppedReason=null;resume()
    }
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
    fun update(phase:String,progress:Float,held:Boolean) { visual=Visual(phase,progress,held);overlay.invalidate() }
    fun place() { if(camera && canPlace)placement.set(true) }
    fun resume() {
        if(running)return
        foreground=true;revision=gate.configure();gate.activate()
        if(camera) {
            stoppedReason?.let{unavailable(it,true);return}
            thermalStatus=power.currentThermalStatus;thermalReadAt=SystemClock.elapsedRealtime()
            if(thermalStatus>=PowerManager.THERMAL_STATUS_CRITICAL){unavailable(coolingMessage(),true);return}
            if(host.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) { unavailable(t("Enable camera access to place your training stations.","प्रशिक्षण स्थल रखने के लिए कैमरा अनुमति दें।"));return }
            try {
                if(session==null) {
                    if(ArCoreApk.getInstance().requestInstall(host,!installRequested)==ArCoreApk.InstallStatus.INSTALL_REQUESTED) {installRequested=true;unavailable(t("Finish AR installation, then return.","AR स्थापना पूरी करके वापस आएँ।"));return}
                    session=Session(host)
                    session!!.apply {
                        val filter=CameraConfigFilter(this).setTargetFps(EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30)).setDepthSensorUsage(EnumSet.of(CameraConfig.DepthSensorUsage.DO_NOT_USE))
                        getSupportedCameraConfigs(filter).minByOrNull{it.textureSize.width.toLong()*it.textureSize.height}?.let{cameraConfig=it}
                        configure(Config(this).apply {planeFindingMode=Config.PlaneFindingMode.HORIZONTAL;updateMode=Config.UpdateMode.LATEST_CAMERA_IMAGE;lightEstimationMode=Config.LightEstimationMode.AMBIENT_INTENSITY;focusMode=Config.FocusMode.AUTO})
                        Log.i("RoomAR","camera fps=${cameraConfig.fpsRange} texture=${cameraConfig.textureSize} image=${cameraConfig.imageSize}")
                    }
                }
                freshness.requireNewImage();session!!.resume()
            }catch(e:Exception) {Log.e("RoomAR","Session start failed: ${e.javaClass.simpleName}");anchors.forEach{it.detach()};anchors.clear();facing.clear();session?.close();session=null;freshness.clear();unavailable(t("Camera AR could not start. Tap Retry camera. If it repeats, check Google Play Services for AR.","कैमरा AR शुरू नहीं हुआ। कैमरा फिर चलाएँ। समस्या रहे तो Google Play Services for AR जाँचें।"),true);return}
        }
        healthyAt=SystemClock.elapsedRealtime();metricsAt=0;metricFrames=0;metricTracked=0;metricTimestamp=0;maxUpdateMs=0;running=true;surface.onResume();removeCallbacks(frameTicker);post(frameTicker)
    }
    fun pause() {removeCallbacks(frameTicker);visual=visual.copy(held=false);foreground=false;gate.pause();revision=gate.configure();placement.set(false);image=null;down=null;screenCursor=null;gestureTarget=null
        if(running){surface.onPause();if(camera)try{session?.pause()}catch(_:Exception){};running=false}
    }
    fun close(){pause();anchors.forEach{it.detach()};anchors.clear();session?.close();session=null}
    private fun unavailable(message:String,retry:Boolean=false) { if(retry)stoppedReason=message;val state=Image(revision,SystemClock.elapsedRealtime(),if(camera)anchors.size else 3,false,message,retry=retry);image=state;overlay.setBackgroundColor(Palette.canvas);onImage(state);overlay.invalidate() }
    override fun onSurfaceCreated(gl:GL10?,config:EGLConfig?) {
        equipment.prepareProcedure(module);equipment.create();geometry.create()
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
        try {
            if(camera) {
                val ar=session?:return;ar.setCameraTextureName(texture);ar.setDisplayGeometry(host.windowManager.defaultDisplay.rotation,w,h)
                val updateAt=SystemClock.elapsedRealtime();val frame=ar.update();at=SystemClock.elapsedRealtime();drawCamera(frame)
                maxUpdateMs=maxOf(maxUpdateMs,at-updateAt)
                if(frame.timestamp>0 && frame.timestamp!=metricTimestamp){metricFrames++;metricTimestamp=frame.timestamp;if(frame.camera.trackingState==TrackingState.TRACKING)metricTracked++}
                if(metricsAt==0L)metricsAt=at
                if(at-metricsAt>=5000){Log.i("RoomAR","frames=$metricFrames trackedFrames=$metricTracked intervalMs=${at-metricsAt} maxUpdateMs=$maxUpdateMs");metricsAt=at;metricFrames=0;metricTracked=0;maxUpdateMs=0}
                val receipt=freshness.observedAt(frame.timestamp,at)
                val tracking=frame.camera.trackingState;val reason=frame.camera.trackingFailureReason
                diagnostic("tracking=$tracking reason=$reason fresh=${receipt!=null} anchors=${anchors.size} thermal=$thermalStatus")
                if(receipt==null || tracking!=TrackingState.TRACKING) {
                    placement.set(false)
                    val message=if(receipt==null)t("Waiting for a fresh camera frame. Keep the app open.","नए कैमरा फ़्रेम की प्रतीक्षा है। ऐप खुला रखें।")else trackingHelp(reason)
                    publish(Image(rev,at,anchors.size,false,message))
                    if(reason==TrackingFailureReason.BAD_STATE || SystemClock.elapsedRealtime()-healthyAt>=45000)
                        requestStop(rev,if(reason==TrackingFailureReason.BAD_STATE)message else t("AR could not establish stable tracking. Camera paused. Tap Retry camera after checking lighting and phone temperature.","AR स्थिर ट्रैकिंग नहीं बना सका। कैमरा रोका गया। रोशनी और फ़ोन का तापमान जाँचकर कैमरा फिर चलाएँ।"))
                    return
                };at=receipt
                if(anchors.size<3){
                    placementHit=frame.hitTest(w/2f,h/2f).firstOrNull{(it.trackable as? Plane)?.let{p->p.type==Plane.Type.HORIZONTAL_UPWARD_FACING && p.trackingState==TrackingState.TRACKING && p.isPoseInPolygon(it.hitPose)}==true}
                    val decision=placementHit?.let{hit->RoomStationPlacement.evaluate(module,
                        anchors.mapIndexed{i,a->RoomStationPlacement.Station(a.pose.tx(),a.pose.tz(),facing[i])},
                        RoomStationPlacement.Station(hit.hitPose.tx(),hit.hitPose.tz()))}
                    placementReady=decision?.allowed==true && anchors.all{it.trackingState==TrackingState.TRACKING}
                    placementMessage=when{
                        placementHit==null->t("Tracking active · searching for a surface. Move gently sideways; aim down until the ring turns green.","ट्रैकिंग चालू · सतह खोज रहे हैं। फ़ोन धीरे दाएँ-बाएँ हिलाएँ; हरा घेरा आने तक नीचे निशाना रखें।")
                        decision?.reason in listOf(RoomStationPlacement.Reason.GAS_WRONG_SIDE,RoomStationPlacement.Reason.GAS_FOOTPRINT_OVERLAP)->t("Keep the green ring entirely on the outside of the barrier, away from the simulated opening.","हरे घेरे को पूरा अवरोध के बाहर रखें, काल्पनिक खुले स्थान से दूर।")
                        !placementReady->t("Surface found · choose a separate point, farther from the other stations.","सतह मिली · दूसरे स्थलों से दूर अलग बिंदु चुनें।")
                        else->t("Surface found · green ring shows placement. Tap Place station.","सतह मिली · हरा घेरा रखने की जगह है। स्थल रखें दबाएँ।")
                    }
                }
                if(placement.getAndSet(false) && anchors.size<3) {
                    val hit=placementHit
                    if(hit!=null) {
                        if(placementReady) {
                            val next=hit.createAnchor();var accepted=false
                            gate.withCurrentRevision(rev){if(foreground && revision==rev && SystemClock.elapsedRealtime()-at in 0..150){anchors.add(next);facing.add(Math.toDegrees(kotlin.math.atan2((frame.camera.pose.tx()-next.pose.tx()).toDouble(),(frame.camera.pose.tz()-next.pose.tz()).toDouble())).toFloat());accepted=true}}
                            if(!accepted)next.detach() else {Log.i("RoomAR","anchor placed count=${anchors.size}");placementReady=false;placementHit=null}
                        }else {publish(Image(rev,at,anchors.size,false,t("Choose a separate clear training point. Keep the green point away from the simulated hazard.","अलग खाली प्रशिक्षण बिंदु चुनें। हरा बिंदु काल्पनिक खतरे से दूर रखें।")));return}
                    }
                }
                if(anchors.any{it.trackingState!=TrackingState.TRACKING}) {
                    if(anchors.any{it.trackingState==TrackingState.STOPPED} || SystemClock.elapsedRealtime()-healthyAt>=45000)requestStop(rev,t("Placed stations could not be recovered. Tap Retry camera and place the stations again.","रखे स्थल वापस नहीं मिले। कैमरा फिर चलाकर स्थल फिर रखें।"))
                    publish(Image(rev,at,anchors.size,false,t("Recovering placed stations. Keep the floor in view; restart from Options if tracking does not return.","रखे स्थल फिर खोज रहे हैं। फ़र्श दृश्य में रखें; ट्रैकिंग न लौटे तो विकल्प से फिर शुरू करें।")));return}
                healthyAt=SystemClock.elapsedRealtime()
                frame.camera.getProjectionMatrix(projection,0,.05f,30f);frame.camera.getViewMatrix(view,0);eye=frame.camera.pose.translation
                anchors.forEachIndexed{i,a->models.add(FloatArray(16).apply{Matrix.setIdentityM(this,0);Matrix.translateM(this,0,a.pose.tx(),a.pose.ty(),a.pose.tz());Matrix.rotateM(this,0,facing[i],0f,1f,0f)})}
                if(frame.lightEstimate.state==LightEstimate.State.VALID){frame.lightEstimate.getColorCorrection(light,0);for(i in light.indices)light[i]=if(light[i].isFinite())light[i].coerceIn(.6f,1.4f)else 1f}
            }else {
                eye=floatArrayOf(0f,1.5f,3.7f);Matrix.perspectiveM(projection,0,if(w>h*1.4f)if(module=="fire")22f else 32f else 55f,w.toFloat()/h.coerceAtLeast(1),.05f,30f);Matrix.setLookAtM(view,0,eye[0],eye[1],eye[2],0f,.3f,-.5f,0f,1f,0f)
                for(p in listOf(floatArrayOf(-.6f,0f,0f),floatArrayOf(.1f,0f,-1f),floatArrayOf(.85f,0f,.3f)))models.add(FloatArray(16).apply{Matrix.setIdentityM(this,0);Matrix.translateM(this,0,p[0],p[1],p[2])})
            }
            Matrix.multiplyMM(vp,0,projection,0,view,0)
            val footprint=placementHit?.let{hit->(0..32).mapNotNull{i->
                val angle=i*2.0*Math.PI/32;val radius=if(anchors.size==2).45 else .22;val point=hit.hitPose.transformPoint(floatArrayOf((kotlin.math.cos(angle)*radius).toFloat(),.005f,(kotlin.math.sin(angle)*radius).toFloat()))
                ComponentProjection.project(point,vp,w,h)
            }}.orEmpty()
            val targets=mutableMapOf<String,ComponentProjection.Point>();var inverse:FloatArray?=null
            fun target(id:String,index:Int,p:FloatArray){if(index>=models.size)return;val mvp=FloatArray(16);Matrix.multiplyMM(mvp,0,vp,0,models[index],0);ComponentProjection.project(p,mvp,w,h)?.let{targets[id]=it}}
            models.getOrNull(0)?.let { m ->
                val flags=buildSet<String>{
                    if(module=="gas")add("room-mission")
                    if(module=="fire"&&state.phase in listOf("AIM","SWEEP","WITHDRAW","COMPLETE"))add("fire-pin")
                    if(module=="fire"&&state.phase in listOf("SWEEP","WITHDRAW","COMPLETE"))add("fire-aim")
                    if(module=="fire"&&state.held&&state.phase=="SWEEP")add("fire-squeeze")
                }
                equipment.draw(vp,m,module,eye,light,flags,false)
                val alarm=m.copyOf();Matrix.translateM(alarm,0,.26f,.32f,0f);geometry.draw(vp,alarm,"alarm",at/1000f)
                target("alarm",0,floatArrayOf(.26f,.40f,0f));target("pin",0,floatArrayOf(-.055f,.478f,.015f));target("pin-out",0,floatArrayOf(-.20f,.478f,.015f));target("meter",0,floatArrayOf(0f,.30f,.085f))
            }
            models.getOrNull(1)?.let {m ->
                if(module=="gas")geometry.draw(vp,m,"confined-zone",at/1000f)
                geometry.draw(vp,m,if(module=="fire")"fire" else "barrier",at/1000f,if(state.phase=="SWEEP")state.progress*.5f else 0f,if(module=="fire")state.phase in listOf("WITHDRAW","COMPLETE") else state.phase in listOf("ATTENDANT","REFUSE","COMPLETE"))
                target("base",1,floatArrayOf(0f,.08f,0f));target("left",1,floatArrayOf(-.45f,.08f,0f));target("right",1,floatArrayOf(.45f,.08f,0f))
                target("barrier-left",1,floatArrayOf(-.36f,.73f,0f));target("barrier-right",1,floatArrayOf(.36f,.73f,0f))
                val mvp=FloatArray(16);Matrix.multiplyMM(mvp,0,vp,0,m,0);val inv=FloatArray(16);if(Matrix.invertM(inv,0,mvp,0))inverse=inv
            }
            models.getOrNull(2)?.let{m->geometry.draw(vp,m,"safe-point",at/1000f);if(module=="gas"&&state.phase in listOf("REFUSE","COMPLETE"))geometry.draw(vp,m,"attendant",at/1000f);target("safe",2,floatArrayOf(0f,.15f,0f))}
            val needed=when(state.phase){"ALARM"->"alarm";"PIN"->"pin";"GAS_CHECK"->"meter";"ATTENDANT"->if(gestureTarget=="meter")"safe" else "meter";"BARRIER"->"barrier-left";"WITHDRAW","REFUSE"->"safe";else->"base"}
            val orientationHint=if(camera&&models.size==3&&needed !in targets)t("Turn the phone slowly towards "+(if(needed=="safe")"your green withdrawal point." else if(needed in listOf("alarm","pin","meter"))"the equipment station." else "the virtual hazard."),"फ़ोन धीरे घुमाकर "+(if(needed=="safe")"हरा वापसी बिंदु खोजें।" else if(needed in listOf("alarm","pin","meter"))"उपकरण स्थल खोजें।" else "काल्पनिक खतरा खोजें।"))else null
            publish(Image(rev,at,models.size,true,orientationHint?:if(models.size<3)(placementMessage?:t("Aim at the next clear point.","अगले खाली बिंदु पर निशाना रखें।"))else t("Stations anchored · stay in your clear practice area", "स्थल जुड़े हैं · अपने खाली अभ्यास क्षेत्र में रहें"),targets,inverse,w,h,state.phase,canPlace=placementReady,footprint=footprint))
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
        private fun radius()=host.dp(28).toFloat()
        private fun near(id:String,x:Float,y:Float)=image?.targets?.get(id)?.let{kotlin.math.hypot(x-it.x,y-it.y)<=radius()*1.4f}==true
        override fun onDraw(c:Canvas){
            val state=visual;val img=image;paint.strokeWidth=host.dp(2).toFloat();paint.style=Paint.Style.STROKE
            val cursor=if(camera)width/2f to height/2f else screenCursor
            cursor?.let{(x,y)->paint.color=if(camera&&img?.canPlace==true)Palette.teal else Color.WHITE;c.drawCircle(x,y,host.dp(10).toFloat(),paint);c.drawLine(x-20,y,x+20,y,paint);c.drawLine(x,y-20,x,y+20,paint);if(visual.phase in listOf("AIM","SWEEP")){paint.color=Palette.teal;val r=host.dp(19).toFloat();c.drawArc(RectF(x-r,y-r,x+r,y+r),-90f,360f*visual.progress,false,paint)}}
            if(camera && img?.tracked==true && img.placement<3 && img.footprint.size>2){
                paint.color=if(img.canPlace)Palette.teal else Palette.violet;paint.strokeWidth=host.dp(3).toFloat()
                val path=Path();img.footprint.forEachIndexed{i,p->if(i==0)path.moveTo(p.x,p.y)else path.lineTo(p.x,p.y)};path.close();c.drawPath(path,paint)
            }
            val ids=when(state.phase){"ALARM"->listOf("alarm");"PIN"->listOf("pin");"GAS_CHECK"->listOf("meter");"BARRIER"->listOf("barrier-left","barrier-right");"ATTENDANT"->listOf("meter","safe");"WITHDRAW","REFUSE"->listOf("safe");else->listOf("left","right")}
            if(img?.tracked==true&&img.placement==3){
                paint.color=if(state.phase=="WITHDRAW")Palette.danger else Palette.teal
                for(id in ids)img.targets[id]?.let{p->c.drawCircle(p.x,p.y,radius(),paint)}
                if(state.phase in listOf("AIM","SWEEP")){val a=img.targets["left"];val b=img.targets["right"];if(a!=null&&b!=null)c.drawLine(a.x,a.y,b.x,b.y,paint)}
                if(state.held&&state.phase=="SWEEP"&&cursor!=null){paint.color=0xffc9eefa.toInt();paint.strokeWidth=host.dp(9).toFloat();c.drawLine(width*.7f,height.toFloat(),cursor.first,cursor.second,paint)}
                down?.let{start->screenCursor?.let{end->paint.color=Palette.violet;c.drawLine(start.first,start.second,end.first,end.second,paint)}}
            }
        }
        private fun outward(dx:Float,dy:Float):Boolean {
            val a=image?.targets?.get("pin")?:return false;val b=image?.targets?.get("pin-out")?:return false
            val length=kotlin.math.hypot(b.x-a.x,b.y-a.y);return length>1f && (dx*(b.x-a.x)+dy*(b.y-a.y))/length>=host.dp(56)
        }
        override fun onTouchEvent(e:MotionEvent):Boolean {
            if(!ready){val hadPointer=down!=null;down=null;screenCursor=null;if(hadPointer&&e.actionMasked==MotionEvent.ACTION_UP)onRelease();return true}
            when(e.actionMasked){
                MotionEvent.ACTION_DOWN->{down=e.x to e.y;screenCursor=down;downAt=SystemClock.elapsedRealtime();gestureSerial++;if(!camera)sampleScreen(gestureSerial);gestureTarget=when(visual.phase){"ALARM"->"alarm";"PIN"->"pin";"GAS_CHECK"->"meter";"BARRIER"->"barrier-left";"ATTENDANT"->"meter";"WITHDRAW","REFUSE"->"safe";else->null}?.takeIf{near(it,e.x,e.y)};parent.requestDisallowInterceptTouchEvent(true)}
                MotionEvent.ACTION_MOVE->{screenCursor=e.x to e.y}
                MotionEvent.ACTION_UP->{
                    val start=down;val elapsed=SystemClock.elapsedRealtime()-downAt
                    if(start!=null && (!camera||image!!.at>=downAt) && elapsed>=80){val target=gestureTarget;val dist=kotlin.math.hypot(e.x-start.first,e.y-start.second)
                        val action=when {
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
