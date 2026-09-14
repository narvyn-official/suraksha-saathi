package com.narvyn.suraksha

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import com.google.ar.core.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2

/** Fire-only stationary recognition, with real ARCore tracking and a screen alternative in its host. */
class ComponentCameraView(private val host: Activity): FrameLayout(host), GLSurfaceView.Renderer {
    private data class Scene(val revision: Int, val yaw: Float, val order: List<ComponentCatalog.Part>, val canChoose: Boolean)
    private data class Choice(val revision: Int, val id: String)
    private data class Preview(val revision: Int, val points: List<ComponentProjection.Point?>, val message: String, val ready: Boolean)
    private val gate = ComponentCameraGate()
    private val freshness = ComponentCameraFreshness()
    private val surface = GLSurfaceView(host)
    private val lines = Lines()
    private val placeholder = host.label("",17f,Palette.muted,true)
    private val markers = mutableListOf<Button>()
    private val equipment = WorldEquipment()
    @Volatile private var ar: Session? = null
    private var anchor: Anchor? = null // GL thread only while rendering
    private var facing = 0f
    private var installRequested = false
    @Volatile private var running = false
    @Volatile private var hi = false
    @Volatile private var scene = Scene(0,20f,ComponentCatalog.modules.getValue("fire"),false)
    private var select: (String) -> Unit = {}
    private var displayedPoints = emptyList<ComponentProjection.Point?>()
    var onStatus: (String) -> Unit = {}
    var onVisible: () -> Unit = {}
    private val tap = AtomicReference<Pair<Float,Float>?>(null)
    private val choice = AtomicReference<Choice?>(null)
    private val reset = AtomicBoolean(false)
    private val posted = AtomicBoolean(false)
    private val preview = AtomicReference<Preview?>(null)
    private var widthPx = 1; private var heightPx = 1
    private var texture = 0; private var program = 0
    private val vertices = buffer(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f))
    private val uv = buffer(FloatArray(8))
    private val projection = FloatArray(16); private val view = FloatArray(16)
    private val vp = FloatArray(16); private val model = FloatArray(16); private val mvp = FloatArray(16)
    private val light = floatArrayOf(1f,1f,1f,1f); private val estimated = FloatArray(4)
    private fun t(en: String, hindi: String) = if(hi) hindi else en

    init {
        surface.setEGLContextClientVersion(2); surface.setEGLConfigChooser(8,8,8,8,16,0)
        surface.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(surface,LayoutParams(-1,-1)); addView(lines,LayoutParams(-1,-1))
        placeholder.gravity=android.view.Gravity.CENTER
        placeholder.setPadding(host.dp(20),host.dp(20),host.dp(20),host.dp(20))
        placeholder.background=host.shape(Palette.surface,16,Palette.line)
        addView(placeholder,LayoutParams(-1,-2,android.view.Gravity.CENTER).apply { setMargins(host.dp(16),0,host.dp(16),0) })
        repeat(3) {
            val button = host.action("",false) {}.apply {
                textSize=16f; minWidth=0; minimumWidth=0; minHeight=0; minimumHeight=0
                setPadding(0,0,0,0); visibility=INVISIBLE
            }
            markers.add(button); addView(button,LayoutParams(host.dp(48),host.dp(48)))
        }
        surface.setOnTouchListener { _, event ->
            if(event.action == MotionEvent.ACTION_UP) { tap.set(event.x to event.y); surface.performClick() }; true
        }
        surface.setRenderer(this); surface.renderMode=GLSurfaceView.RENDERMODE_CONTINUOUSLY; surface.onPause()
    }
    fun configure(session: ComponentSession, hindi: Boolean, callback: (String) -> Unit) {
        hi=hindi; select=callback; choice.set(null); tap.set(null)
        placeholder.text=t("Camera view paused\nScreen practice is always available.","कैमरा दृश्य रुका है\nस्क्रीन अभ्यास हमेशा उपलब्ध है।")
        scene=Scene(gate.configure(),session.yaw,session.order,session.stage in 1..2 && session.answer==null)
        markers.forEachIndexed { i,b ->
            val part=session.order[i]; val letter=('A'.code+i).toChar().toString()
            b.text=letter; b.tag="camera-marker-${part.id}"
            b.contentDescription=t("Marker $letter","चिह्न $letter") + if(session.showLabels) ": ${part.title(hi)}" else ""
            val named=session.showLabels && part.id==session.target.id
            b.background=host.shape(if(named)Palette.blue else Palette.surface,24,Palette.blue)
            b.setTextColor(if(named)android.graphics.Color.WHITE else Palette.ink)
            b.setOnClickListener { requestChoice(part.id) }
        }
        hideMarkers()
    }
    private fun partsVisible(): Boolean {
        val visible=android.graphics.Rect()
        return isShown && getLocalVisibleRect(visible) && displayedPoints.size==3 && displayedPoints.all { it!=null && visible.contains(it.x.toInt(),it.y.toInt()) }
    }
    fun ready(): Boolean = gate.allows(scene.revision,SystemClock.elapsedRealtime()) && partsVisible()
    fun requestChoice(id: String) {
        val current=scene
        if(current.canChoose && ready()) choice.compareAndSet(null,Choice(current.revision,id))
    }
    fun reposition() {
        scene=scene.copy(revision=gate.configure()); choice.set(null); tap.set(null); reset.set(true); hideMarkers()
    }
    /** Caller owns permission prompts. Errors leave the screen alternative available. */
    fun resumeCamera() {
        if(running) return
        try {
            if(ar==null) {
                if(ArCoreApk.getInstance().requestInstall(host,!installRequested)==ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                    installRequested=true; onStatus(t("Finish installing AR services, or use screen practice.","AR सेवाओं की स्थापना पूरी करें, या स्क्रीन अभ्यास करें।")); return
                }
                ar=Session(host).apply { configure(Config(this).apply {
                    planeFindingMode=Config.PlaneFindingMode.HORIZONTAL
                    updateMode=Config.UpdateMode.LATEST_CAMERA_IMAGE
                    lightEstimationMode=Config.LightEstimationMode.AMBIENT_INTENSITY
                }) }
            }
            freshness.requireNewImage(); scene=scene.copy(revision=gate.configure()); gate.activate()
            ar!!.resume(); running=true; surface.onResume()
            onStatus(t("Find a clear tabletop, then tap the camera view to place.","खाली मेज़ खोजें, फिर रखने के लिए कैमरा दृश्य पर टैप करें।"))
        } catch(_: Exception) {
            gate.pause(); running=false; hideMarkers()
            onStatus(t("Camera AR is unavailable. Use screen practice, or retry the camera.","कैमरा AR उपलब्ध नहीं है। स्क्रीन अभ्यास करें, या कैमरा फिर आज़माएँ।"))
        }
    }
    fun pauseCamera() {
        gate.pause(); choice.set(null); tap.set(null); preview.set(null); hideMarkers()
        surface.onPause()
        if(running) { try { ar?.pause() } catch(_: Exception) {} }
        running=false
    }
    fun prepareRetry() { pauseCamera(); installRequested=false }
    fun close() { pauseCamera(); anchor?.detach(); anchor=null; ar?.close(); ar=null }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        equipment.create()
        val ids=IntArray(1); GLES20.glGenTextures(1,ids,0); texture=ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
        val vertex=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 p;attribute vec2 uv;varying vec2 tex;void main(){gl_Position=vec4(p,0.0,1.0);tex=uv;}")
        val fragment=shader(GLES20.GL_FRAGMENT_SHADER,"#extension GL_OES_EGL_image_external : require\nprecision mediump float;uniform samplerExternalOES camera;varying vec2 tex;void main(){gl_FragColor=texture2D(camera,tex);}")
        program=GLES20.glCreateProgram(); GLES20.glAttachShader(program,vertex); GLES20.glAttachShader(program,fragment); GLES20.glLinkProgram(program)
        GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment)
    }
    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) { widthPx=w; heightPx=h; GLES20.glViewport(0,0,w,h) }
    override fun onDrawFrame(gl: GL10?) {
        val current=scene
        GLES20.glClearColor(.965f,.973f,.99f,1f); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if(!running) return
        val session=ar ?: return
        try {
            session.setCameraTextureName(texture); session.setDisplayGeometry(host.windowManager.defaultDisplay.rotation,widthPx,heightPx)
            val frame=session.update()
            drawCamera(frame)
            val observedAt=freshness.observedAt(frame.timestamp,SystemClock.elapsedRealtime())
            if(observedAt==null) { tap.set(null); unavailable(current,t("Waiting for a fresh camera image. Hold still, or retry.","नए कैमरा दृश्य की प्रतीक्षा है। स्थिर रहें, या फिर कोशिश करें।")); return }
            if(reset.getAndSet(false)) { anchor?.detach(); anchor=null }
            if(frame.camera.trackingState!=TrackingState.TRACKING) {
                tap.set(null); unavailable(current,t("Tracking paused. Hold still and look at the training surface.","ट्रैकिंग रुकी है। स्थिर रहें और प्रशिक्षण सतह देखें।")); return
            }
            val placed=tap.getAndSet(null)
            if(anchor==null && placed!=null) {
                val hit=frame.hitTest(placed.first,placed.second).firstOrNull {
                    val plane=it.trackable as? Plane
                    plane!=null && plane.type==Plane.Type.HORIZONTAL_UPWARD_FACING && plane.trackingState==TrackingState.TRACKING && plane.isPoseInPolygon(it.hitPose)
                }
                if(hit!=null) {
                    anchor=hit.createAnchor()
                    val origin=anchor!!.pose; val eye=frame.camera.pose
                    facing=Math.toDegrees(atan2((eye.tx()-origin.tx()).toDouble(),(eye.tz()-origin.tz()).toDouble())).toFloat()
                }
            }
            val a=anchor
            if(a==null) { unavailable(current,t("Tap a clear tabletop to place the training model.","प्रशिक्षण मॉडल रखने के लिए खाली मेज़ पर टैप करें।")); return }
            if(a.trackingState!=TrackingState.TRACKING) {
                if(a.trackingState==TrackingState.STOPPED) { a.detach(); anchor=null }
                unavailable(current,t("Placement is not tracked. Wait, or place the model again.","रखी जगह की ट्रैकिंग नहीं हो रही। प्रतीक्षा करें, या मॉडल फिर रखें।")); return
            }
            frame.camera.getProjectionMatrix(projection,0,.05f,20f); frame.camera.getViewMatrix(view,0)
            Matrix.multiplyMM(vp,0,projection,0,view,0)
            // Only translation is inherited: a horizontal training model faces the learner on placement.
            Matrix.setIdentityM(model,0); Matrix.translateM(model,0,a.pose.tx(),a.pose.ty(),a.pose.tz())
            Matrix.rotateM(model,0,facing+current.yaw,0f,1f,0f)
            if(frame.lightEstimate.state==LightEstimate.State.VALID) {
                frame.lightEstimate.getColorCorrection(estimated,0)
                for(i in 0..3) if(estimated[i].isFinite()) light[i]=light[i]*.9f+estimated[i].coerceIn(.65f,1.35f)*.1f
            }
            equipment.draw(vp,model,"fire",frame.camera.pose.translation,light)
            Matrix.multiplyMM(mvp,0,vp,0,model,0)
            val points=current.order.map { ComponentProjection.project(it.point,mvp,widthPx,heightPx) }
            val dx=frame.camera.pose.tx()-a.pose.tx(); val dz=frame.camera.pose.tz()-a.pose.tz()
            val readable=ComponentCameraGeometry.readableView(dx,frame.camera.pose.ty()-a.pose.ty()-.27f,dz,facing+current.yaw)
            val visible=points.all { it!=null && it.x in host.dp(8).toFloat()..(widthPx-host.dp(64)).toFloat() && it.y in host.dp(12).toFloat()..(heightPx-host.dp(12)).toFloat() }
            val ready=readable && visible
            gate.frame(current.revision,ready,observedAt)
            val pending=choice.getAndSet(null)
            if(pending!=null && pending.revision==current.revision && current.canChoose && ready && gate.allows(current.revision,SystemClock.elapsedRealtime())) {
                post { if(scene.revision==current.revision && ready()) { onVisible(); select(pending.id) } }
            }
            publish(Preview(current.revision,if(ready)points else emptyList(),if(ready)t("Model placed · follow the lines to identify a part.","मॉडल रखा है · पुर्ज़ा पहचानने के लिए रेखाएँ देखें।") else t("Keep the whole model in view from the front. No walking is needed.","पूरा मॉडल सामने से दृश्य में रखें। चलने की ज़रूरत नहीं है।"),ready))
        } catch(_: Exception) { tap.set(null); unavailable(current,t("Camera interrupted. Retry, or continue on screen.","कैमरा बाधित है। फिर कोशिश करें, या स्क्रीन पर जारी रखें।")) }
    }
    private fun drawCamera(frame: Frame) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        vertices.position(0); uv.position(0)
        frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,vertices,Coordinates2d.TEXTURE_NORMALIZED,uv)
        GLES20.glUseProgram(program); GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture)
        val p=GLES20.glGetAttribLocation(program,"p"); val u=GLES20.glGetAttribLocation(program,"uv")
        vertices.position(0); uv.position(0); GLES20.glEnableVertexAttribArray(p); GLES20.glEnableVertexAttribArray(u)
        GLES20.glVertexAttribPointer(p,2,GLES20.GL_FLOAT,false,0,vertices); GLES20.glVertexAttribPointer(u,2,GLES20.GL_FLOAT,false,0,uv)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4); GLES20.glDisableVertexAttribArray(p); GLES20.glDisableVertexAttribArray(u)
    }
    private fun unavailable(current: Scene, message: String) {
        gate.frame(current.revision,false,SystemClock.elapsedRealtime()); choice.set(null)
        publish(Preview(current.revision,emptyList(),message,false))
    }
    private fun publish(value: Preview) {
        preview.set(value)
        if(posted.compareAndSet(false,true)) post {
            posted.set(false)
            val latest=preview.getAndSet(null)
            if(latest!=null && latest.revision==scene.revision) {
                placeholder.visibility=if(latest.ready)GONE else VISIBLE
                if(latest.ready && gate.allows(scene.revision,SystemClock.elapsedRealtime())) { position(latest.points); if(partsVisible()) onVisible() } else hideMarkers()
                onStatus(if(latest.ready && !partsVisible()) t("Scroll until all three parts are visible in the camera view.","स्क्रॉल करें जब तक कैमरा दृश्य में तीनों पुर्ज़े दिखें।") else latest.message)
            }
        }
    }
    private fun hideMarkers() { placeholder.visibility=VISIBLE; displayedPoints=emptyList(); markers.forEach { it.visibility=INVISIBLE; it.isEnabled=false }; lines.points=emptyList(); lines.invalidate() }
    private fun position(points: List<ComponentProjection.Point?>) {
        displayedPoints=points
        val size=host.dp(48).toFloat(); val gap=host.dp(10).toFloat(); val margin=host.dp(4).toFloat()
        val ordered=points.indices.sortedBy { points[it]?.y ?: 0f }; val ys=mutableMapOf<Int,Float>(); var bottom=margin-size-gap
        for(i in ordered) { val p=points[i] ?: continue; ys[i]=maxOf((p.y-size/2).coerceIn(margin,(height-size-margin).coerceAtLeast(margin)),bottom+size+gap); bottom=ys.getValue(i) }
        val overflow=(bottom+size+margin-height).coerceAtLeast(0f); val x=(width-size-margin).coerceAtLeast(0f)
        val segments=mutableListOf<FloatArray>()
        markers.forEachIndexed { i,b ->
            val p=points.getOrNull(i); val y=ys[i]?.minus(overflow)
            if(p==null || y==null) b.visibility=INVISIBLE else {
                b.x=x;b.y=y;b.visibility=VISIBLE;b.isEnabled=scene.canChoose && ready()
                segments.add(floatArrayOf(p.x,p.y,x,y+size/2))
            }
        }
        lines.points=segments; lines.invalidate()
    }
    private inner class Lines: View(host) {
        var points=emptyList<FloatArray>(); private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
        override fun onDraw(canvas: Canvas) {
            points.forEach { p ->
                paint.color=android.graphics.Color.WHITE; paint.strokeWidth=host.dp(5).toFloat(); canvas.drawLine(p[0],p[1],p[2],p[3],paint)
                paint.color=Palette.blue; paint.strokeWidth=host.dp(2).toFloat(); canvas.drawLine(p[0],p[1],p[2],p[3],paint); canvas.drawCircle(p[0],p[1],host.dp(4).toFloat(),paint)
            }
        }
    }
    companion object {
        private fun buffer(v: FloatArray)=ByteBuffer.allocateDirect(v.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(v);position(0) }
        private fun shader(type: Int, source: String)=GLES20.glCreateShader(type).also { GLES20.glShaderSource(it,source);GLES20.glCompileShader(it) }
    }
}
