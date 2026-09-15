package com.narvyn.suraksha

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.google.ar.core.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Local simulated procedure scene. Projected callouts are not mesh manipulation or equipment detection. */
class ProcedureSceneView(private val activity: Activity) : FrameLayout(activity), GLSurfaceView.Renderer {
    data class Target(val id: String, val label: String, val point: FloatArray)
    data class Scene(val module: String, val stepId: String, val targets: List<Target>, val completedActions: Set<String>, val enabled: Boolean = true, val spatial: Boolean = false)
    private data class Snapshot(val revision: Int, val scene: Scene, val camera: Boolean,
                                val heights: List<Float>, val columns: Int, val width: Int, val height: Int)
    private data class Action(val revision: Int, val stepId: String, val id: String, val camera: Boolean, val proof: ProcedureSpatial.Proof? = null, val gesture: Long = -1)
    private data class Pointer(val revision: Int, val serial: Long, val x: Float, val y: Float)
    private data class Preview(val revision: Int, val camera: Boolean, val points: List<ComponentProjection.Point?>,
                               val boxes: List<ArChoiceLayout.Box>?, val message: String, val progress: Float = 0f, val cursor: ComponentProjection.Point? = null, val radii: List<Float> = emptyList(), val inverse: FloatArray? = null)

    private val viewport = FrameLayout(activity)
    private val surface = GLSurfaceView(activity)
    private val overlay = ProcedureOverlay()
    private val aim = activity.action("", false, role=ActionRole.CAMERA) { }
    private val hold = ProcedureSpatialHold()
    private val pointer = AtomicReference<Pointer?>(null)
    private var pointerSerial = 0L // UI thread only
    private var sampledSerial = -1L // renderer only
    private val inverseMvp = FloatArray(16)
    private val placement = activity.action("", false) { requestPlacement(surface.width / 2f, surface.height / 2f) }
    private val gate = ComponentCameraGate()
    private val freshness = ComponentCameraFreshness()
    private val placements = ArPlacementQueue()
    private val pendingAction = AtomicReference<Action?>(null)
    private val preview = AtomicReference<Preview?>(null)
    private val posted = AtomicBoolean(false)
    private val equipment = WorldEquipment()
    @Volatile private var foreground = false
    @Volatile private var cameraMode = false
    @Volatile private var cameraRunning = false
    @Volatile private var ar: Session? = null
    @Volatile private var hi = false
    @Volatile private var snapshot = Snapshot(0, Scene("fire", "", emptyList(), emptySet(), false), false, emptyList(), 1, 0, 0)
    private var rendererRunning = false
    private var installRequested = false
    private var anchor: Anchor? = null // GL owned until renderer is paused.
    private var facing = 0f
    private var missedPlacement: Int? = null
    private var onAction: (String, String, org.json.JSONObject?) -> Unit = { _, _, _ -> }
    var onStatus: ((String) -> Unit)? = null
    var onShown: ((String) -> Unit)? = null
    val cameraSelected get() = cameraMode
    private var texture = 0
    private var cameraProgram = 0
    private var widthPx = 1
    private var heightPx = 1
    private val vertices = floats(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val uv = floats(FloatArray(8))
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val vp = FloatArray(16)
    private val mvp = FloatArray(16)
    private val light = floatArrayOf(1f, 1f, 1f, 1f)
    private val estimatedLight = FloatArray(4)
    private var lastMessage = ""
    private fun t(en: String, hindi: String) = if (hi) hindi else en

    init {
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        addView(body, LayoutParams(-1, -2))
        body.addView(viewport, LinearLayout.LayoutParams(-1, activity.dp(340)))
        body.addView(placement, LinearLayout.LayoutParams(-1, -2).apply { topMargin = activity.dp(8) })
        body.addView(aim, LinearLayout.LayoutParams(-1, -2).apply { topMargin = activity.dp(8) })
        aim.tag = "procedure-hold-aim"
        aim.setOnTouchListener { control, event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { control.isPressed=true;beginPointer(surface.width/2f,surface.height/2f);true }
                MotionEvent.ACTION_MOVE -> { if(event.x !in 0f..control.width.toFloat() || event.y !in 0f..control.height.toFloat()) { control.isPressed=false;releasePointer() };true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> { control.isPressed=false;releasePointer();true }
                else -> true
            }
        }
        // A click (including accessibility activation) cannot fabricate a continuous spatial hold.
        aim.contentDescription = ""
        placement.tag = "procedure-place-center"
        placement.visibility = GONE
        surface.setEGLContextClientVersion(2)
        surface.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        surface.preserveEGLContextOnPause = true
        surface.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        surface.tag = "procedure-gl-surface"
        viewport.addView(surface, LayoutParams(-1, -1))
        viewport.addView(overlay, LayoutParams(-1, -1))
        surface.setRenderer(this)
        surface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        surface.onPause()
        viewport.addOnLayoutChangeListener { _, l, top, r, bottom, oldL, oldTop, oldR, oldBottom ->
            if (r - l != oldR - oldL || bottom - top != oldBottom - oldTop) rebind()
        }
        // Static screen scenes also need visibility re-evaluation when an ancestor scrolls.
        viewTreeObserver.addOnScrollChangedListener { if(!overlay.fullyVisible(snapshot.revision))releasePointer();reportShown() }
    }

    fun configure(scene: Scene, hi: Boolean, onAction: (String, String, org.json.JSONObject?) -> Unit) {
        this.hi = hi
        this.onAction = onAction
        equipment.prepareProcedure(scene.module)
        val copied = scene.copy(targets = scene.targets.map { it.copy(point = it.point.copyOf()) }, completedActions = scene.completedActions.toSet())
        snapshot = snapshot.copy(scene = copied)
        lastMessage = ""
        rebind()
    }

    fun useCamera(value: Boolean) {
        stopRenderer()
        cameraMode = value
        if (value) installRequested = false // Explicit user retry can reopen a declined installation.
        rebind()
        if (foreground) startRenderer()
    }

    fun resume() {
        foreground = true
        rebind()
        startRenderer()
    }

    fun pause() {
        foreground = false
        stopRenderer()
    }

    fun close() {
        pause()
        anchor?.detach(); anchor = null
        ar?.close(); ar = null
    }

    private fun rebind() {
        pendingAction.set(null); placements.clear();releasePointer()
        val scene = snapshot.scene
        val columns = if (scene.targets.size > 1 && viewport.width >= activity.dp(300)) 2 else 1
        val cardWidth = ((viewport.width - activity.dp(10) * (columns + 1)) / columns).coerceAtLeast(1)
        val revision = gate.configure()
        val heights = overlay.bind(if(scene.spatial)emptyList()else scene.targets, cardWidth, revision)
        snapshot = Snapshot(revision, scene, cameraMode, heights, columns, viewport.width, viewport.height)
        placement.text = t("Place at camera centre", "कैमरा दृश्य के बीच में रखें")
        placement.contentDescription = t("Place simulated procedure at camera centre", "काल्पनिक प्रक्रिया कैमरा दृश्य के बीच में रखें")
        placement.visibility = if (cameraMode) VISIBLE else GONE
        aim.visibility = if(cameraMode && scene.spatial) VISIBLE else GONE
        aim.text = t("Hold to aim at a target", "लक्ष्य पर निशाने के लिए दबाए रखें")
        aim.contentDescription = t("Hold while aiming. For accessible actions, use text actions above.", "निशाना रखते हुए दबाए रखें। सुलभ क्रियाओं के लिए ऊपर लिखित क्रियाएँ उपयोग करें।")
        placement.isEnabled = foreground && scene.enabled && scene.targets.isNotEmpty()
        overlay.clear()
        surface.requestRender()
    }

    private fun startRenderer() {
        if (!foreground || rendererRunning) return
        if (cameraMode) {
            if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                status(t("Camera permission is off. Enable it or continue on screen.", "कैमरा अनुमति बंद है। अनुमति दें या स्क्रीन पर जारी रखें।"))
                return
            }
            try {
                if (ar == null) {
                    if (ArCoreApk.getInstance().requestInstall(activity, !installRequested) == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                        installRequested = true
                        status(t("Complete AR installation or continue on screen.", "AR स्थापना पूरी करें या स्क्रीन पर जारी रखें।"))
                        return
                    }
                    ar = Session(activity).apply { configure(Config(this).apply {
                        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
                    }) }
                }
                freshness.requireNewImage()
                ar!!.resume()
                cameraRunning = true
            } catch (_: Exception) {
                cameraRunning = false
                status(t("Camera AR is unavailable. Continue on screen or retry.", "कैमरा AR उपलब्ध नहीं है। स्क्रीन पर जारी रखें या फिर कोशिश करें।"))
                return
            }
        }
        gate.activate()
        rendererRunning = true
        overlay.background = null
        surface.renderMode = if (cameraMode) GLSurfaceView.RENDERMODE_CONTINUOUSLY else GLSurfaceView.RENDERMODE_WHEN_DIRTY
        surface.onResume(); surface.requestRender()
    }

    private fun stopRenderer() {
        val wasCameraRunning = cameraRunning
        cameraRunning = false
        gate.pause()
        snapshot = snapshot.copy(revision = gate.configure())
        pendingAction.set(null); placements.clear(); preview.set(null);releasePointer()
        surface.onPause(); rendererRunning = false
        if (wasCameraRunning) try { ar?.pause() } catch (_: Exception) {}
        overlay.clear()
        overlay.setBackgroundColor(Palette.canvas)
    }

    private fun status(message: String) {
        if (lastMessage != message) { lastMessage = message; onStatus?.invoke(message) }
    }

    private fun requestPlacement(x: Float, y: Float) {
        if (!foreground || !cameraMode || !cameraRunning || !snapshot.scene.enabled) {
            status(t("Camera is not running. Use the screen alternative or retry.", "कैमरा चालू नहीं है। स्क्रीन विकल्प उपयोग करें या फिर कोशिश करें।")); return
        }
        val rect = Rect()
        if (!viewport.getLocalVisibleRect(rect) || !rect.contains(x.toInt(), y.toInt())) {
            status(t("Show the camera centre before placing.", "रखने से पहले कैमरा दृश्य का बीच दिखाएँ।")); return
        }
        rebind()
        placements.offer(ArPlacementRequest.createAt(snapshot.revision, x, y, viewport.width, viewport.height, SystemClock.elapsedRealtime()))
    }

    private fun beginPointer(x:Float,y:Float) {
        val current=snapshot
        if(!current.scene.spatial || !ready(current))return
        hold.reset()
        val input=Pointer(current.revision,++pointerSerial,x,y);pointer.set(input)
        if(!current.camera)sampleScreen(input.serial)
    }
    /** Screen practice samples the last displayed static geometry; it never receives camera attribution. */
    private fun sampleScreen(serial:Long) {
        val input=pointer.get()?:return
        val current=snapshot
        if(input.serial!=serial || input.revision!=current.revision || current.camera || !ready(current)) { releasePointer();return }
        val hit=overlay.screenHit(input.x,input.y,current)
        val proof=hold.sample(hit,SystemClock.elapsedRealtime())
        overlay.showHold(hold.progress(),ComponentProjection.Point(input.x,input.y))
        if(proof!=null)deliver(Action(current.revision,current.scene.stepId,proof.action,false,proof,input.serial))
        else postDelayed({sampleScreen(serial)},50)
    }
    private fun releasePointer() { pointer.set(null);hold.reset();aim.isPressed=false }

    private fun requestAction(id: String, revision: Int) {
        val current = snapshot
        if (current.scene.spatial || revision != current.revision || !current.scene.enabled || !ready(current)) return
        val request = Action(revision, current.scene.stepId, id, current.camera)
        if (current.camera) pendingAction.compareAndSet(null, request) else deliver(request)
    }

    private fun ready(current: Snapshot): Boolean = foreground && current.revision == snapshot.revision &&
        current.camera == cameraMode && overlay.fullyVisible(current.revision) &&
        (!current.camera || (cameraRunning && gate.allows(current.revision, SystemClock.elapsedRealtime())))

    private fun deliver(request: Action) {
        val current = snapshot
        if (request.revision != current.revision || request.stepId != current.scene.stepId || request.camera != current.camera ||
            !current.scene.enabled || current.scene.targets.none { it.id == request.id } || !ready(current)) return
        if(current.scene.spatial != (request.proof!=null))return
        if(request.proof!=null && pointer.get()?.let { it.serial==request.gesture && it.revision==request.revision }!=true)return
        val mode = if (request.camera) "camera" else "screen"
        onShown?.invoke(mode)
        if (snapshot.revision != current.revision || !ready(current)) return
        // Invalidate before the host callback, even if the host cannot save and does not reconfigure.
        snapshot = current.copy(revision = gate.configure())
        pendingAction.set(null); placements.clear(); overlay.clear();releasePointer()
        onAction(request.id, mode, request.proof?.json())
    }

    private fun reportShown() {
        val current = snapshot
        if (current.scene.enabled && current.scene.targets.isNotEmpty() && ready(current)) onShown?.invoke(if (current.camera) "camera" else "screen")
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        equipment.create()
        val textures = IntArray(1); GLES20.glGenTextures(1, textures, 0); texture = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        for (parameter in listOf(GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_TEXTURE_MAG_FILTER)) GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, parameter, GLES20.GL_LINEAR)
        for (parameter in listOf(GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_TEXTURE_WRAP_T)) GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, parameter, GLES20.GL_CLAMP_TO_EDGE)
        val vertex = shader(GLES20.GL_VERTEX_SHADER, "attribute vec2 p;attribute vec2 uv;varying vec2 tex;void main(){gl_Position=vec4(p,0.,1.);tex=uv;}")
        val fragment = shader(GLES20.GL_FRAGMENT_SHADER, "#extension GL_OES_EGL_image_external : require\nprecision mediump float;uniform samplerExternalOES camera;varying vec2 tex;void main(){gl_FragColor=texture2D(camera,tex);}")
        cameraProgram = GLES20.glCreateProgram(); GLES20.glAttachShader(cameraProgram, vertex); GLES20.glAttachShader(cameraProgram, fragment); GLES20.glLinkProgram(cameraProgram)
        GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        widthPx = width; heightPx = height; GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val current = snapshot
        GLES20.glClearColor(.965f, .973f, .99f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (!foreground) return
        try {
            var observedAt = SystemClock.elapsedRealtime()
            val eye: FloatArray
            if (current.camera) {
                if (!cameraRunning) return
                val session = ar ?: return
                session.setCameraTextureName(texture)
                session.setDisplayGeometry(activity.windowManager.defaultDisplay.rotation, widthPx, heightPx)
                val frame = session.update(); drawCamera(frame)
                val receipt = freshness.observedAt(frame.timestamp, SystemClock.elapsedRealtime())
                if (receipt == null || frame.camera.trackingState != TrackingState.TRACKING) {
                    unavailable(current, t("Tracking paused. Wait or continue on screen.", "ट्रैकिंग रुकी है। प्रतीक्षा करें या स्क्रीन पर जारी रखें।")); return
                }
                observedAt = receipt
                val request = placements.consumeFor(current.revision)
                if (request?.eligible(snapshot.revision, widthPx, heightPx, SystemClock.elapsedRealtime(), foreground && cameraRunning, true, true) == true) {
                    val hit = frame.hitTest(request.x, request.y).firstOrNull {
                        val plane = it.trackable as? Plane
                        plane != null && plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING && plane.trackingState == TrackingState.TRACKING && plane.isPoseInPolygon(it.hitPose)
                    }
                    if (hit != null) {
                        val next = hit.createAnchor(); anchor?.detach(); anchor = next; missedPlacement = null
                        facing = Math.toDegrees(kotlin.math.atan2((frame.camera.pose.tx() - next.pose.tx()).toDouble(), (frame.camera.pose.tz() - next.pose.tz()).toDouble())).toFloat()
                    } else missedPlacement = current.revision
                }
                val placed = anchor
                if (placed == null || placed.trackingState != TrackingState.TRACKING) {
                    if (placed?.trackingState == TrackingState.STOPPED) { placed.detach(); anchor = null }
                    unavailable(current, if (missedPlacement == current.revision) t("No tracked surface here. Aim at a clear tabletop and try again.", "यहाँ ट्रैक की गई सतह नहीं मिली। खाली मेज़ पर निशाना रखकर फिर कोशिश करें।") else t("Aim at a clear tabletop, then select Place at camera centre.", "खाली मेज़ पर निशाना रखें, फिर कैमरा दृश्य के बीच में रखें चुनें।")); return
                }
                frame.camera.getProjectionMatrix(projection, 0, .05f, 20f); frame.camera.getViewMatrix(view, 0)
                Matrix.setIdentityM(model, 0); Matrix.translateM(model, 0, placed.pose.tx(), placed.pose.ty(), placed.pose.tz()); Matrix.rotateM(model, 0, facing, 0f, 1f, 0f)
                eye = frame.camera.pose.translation
                if (frame.lightEstimate.state == LightEstimate.State.VALID) {
                    frame.lightEstimate.getColorCorrection(estimatedLight, 0)
                    for (i in 0..3) if (estimatedLight[i].isFinite()) light[i] = light[i] * .9f + estimatedLight[i].coerceIn(.65f, 1.35f) * .1f
                }
            } else {
                val aspect = widthPx.coerceAtLeast(1).toFloat() / heightPx.coerceAtLeast(1)
                // Fit the 1.12 m tabletop, including its nearest edge, on narrow screens.
                // This changes only the offline camera framing, never AR equipment scale.
                val distance = maxOf(1.8f, .34f + .60f / (kotlin.math.tan(Math.toRadians(22.0)).toFloat() * aspect))
                eye = floatArrayOf(0f, .3f + distance * .32f, distance)
                Matrix.perspectiveM(projection, 0, 44f, aspect, .05f, 20f)
                // Reserve space beneath the equipment for the native action labels.
                projection[9] = -.16f
                Matrix.setLookAtM(view, 0, eye[0], eye[1], eye[2], 0f, .3f, 0f, 0f, 1f, 0f)
                Matrix.setIdentityM(model, 0)
            }
            Matrix.multiplyMM(vp, 0, projection, 0, view, 0)
            equipment.draw(vp, model, current.scene.module, eye, if (current.camera) light else floatArrayOf(1f, 1f, 1f, 1f), completedActions = current.scene.completedActions, procedureMode = true)
            Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)
            if(current.scene.spatial) {
                drawSpatial(current,observedAt)
                return
            }
            val points = current.scene.targets.map { ComponentProjection.project(it.point, mvp, widthPx, heightPx) }
            val boxes = if (current.width == widthPx && current.height == heightPx) {
                val bottomPoints = points.map { it?.let { p -> ComponentProjection.Point(p.x, heightPx - activity.dp(10).toFloat()) } }
                // Validate actual anchor visibility separately; action cards stay below the illustration.
                if (points.all { it != null }) ArChoiceLayout.arrange(bottomPoints, current.heights, current.columns, widthPx, heightPx, activity.dp(10).toFloat()) else null
            } else null
            gate.frame(current.revision, boxes != null && current.scene.enabled, observedAt)
            val action = pendingAction.get()
            if (action?.revision == current.revision && pendingAction.compareAndSet(action, null) && boxes != null && current.scene.enabled && gate.allows(current.revision, SystemClock.elapsedRealtime())) post { deliver(action) }
            publish(Preview(current.revision, current.camera, points, boxes, if (boxes == null) t("Keep every action in view. Use the text alternative if labels do not fit.", "सभी क्रियाएँ दृश्य में रखें। नाम न समाएँ तो लिखित विकल्प उपयोग करें।") else if (missedPlacement == current.revision && current.camera) t("No new surface found; the previous placement is kept.", "नई सतह नहीं मिली; पिछली रखी जगह बनी हुई है।") else t("Simulated procedure · select an action.", "काल्पनिक प्रक्रिया · एक क्रिया चुनें।")))
        } catch (_: Exception) {
            unavailable(current, t("Scene interrupted. Retry or continue with the text alternative.", "दृश्य बाधित है। फिर कोशिश करें या लिखित विकल्प से जारी रखें।"))
        }
    }

    private fun drawSpatial(current: Snapshot, observedAt: Long) {
        val zones=ProcedureSpatial.zones(current.scene.stepId)
        val ordered=current.scene.targets.mapNotNull { target -> zones.firstOrNull { it.id==target.id } }
        val points=ordered.map { ComponentProjection.project(it.center,mvp,widthPx,heightPx) }
        val radii=ordered.mapIndexed { i,zone ->
            val edge=ComponentProjection.project(floatArrayOf(zone.center[0]+zone.radius,zone.center[1],zone.center[2]),mvp,widthPx,heightPx)
            val point=points[i]
            if(edge==null || point==null)0f else kotlin.math.hypot(edge.x-point.x,edge.y-point.y)
        }
        val visible=current.scene.enabled && current.revision==snapshot.revision && current.width==widthPx && current.height==heightPx &&
            ordered.size==current.scene.targets.size && points.isNotEmpty() && points.all { it!=null } && radii.all { it>0f } && Matrix.invertM(inverseMvp,0,mvp,0)
        gate.frame(current.revision,visible,observedAt)
        val input=pointer.get()
        var cursor:ComponentProjection.Point?=null
        if(current.camera && visible && input!=null && input.revision==current.revision && gate.allows(current.revision,SystemClock.elapsedRealtime())) {
            if(sampledSerial!=input.serial) { hold.reset();sampledSerial=input.serial }
            val x=if(current.camera)widthPx/2f else input.x;val y=if(current.camera)heightPx/2f else input.y
            cursor=ComponentProjection.Point(x,y)
            val hit=ProcedureSpatial.hit(x,y,widthPx,heightPx,inverseMvp,ordered)
            val proof=hold.sample(hit,observedAt)
            if(proof!=null) {
                val action=Action(current.revision,current.scene.stepId,proof.action,current.camera,proof,input.serial)
                if(pendingAction.compareAndSet(null,action))post {
                    if(pendingAction.compareAndSet(action,null))deliver(action)
                }
            }
        }else if(current.camera)hold.reset()
        val message=if(!visible)t("Keep both target zones in view, or use text actions.","दोनों लक्ष्य दृश्य में रखें या लिखित क्रियाएँ उपयोग करें।")
            else if(current.camera)t("Aim the centre cross, then hold the aim control steadily. Targets are simulated.","बीच का निशाना रखें, फिर निशाने का नियंत्रण स्थिर दबाएँ। लक्ष्य काल्पनिक हैं।")
            else t("Touch and hold a target ring. Slide to adjust; lifting resets the hold.","लक्ष्य का घेरा छूकर दबाए रखें। खिसकाकर ठीक करें; उठाने से पकड़ रीसेट होगी।")
        publish(Preview(current.revision,current.camera,points,if(visible)emptyList()else null,message,hold.progress(),cursor,radii,if(visible)inverseMvp.copyOf()else null))
    }

    private fun unavailable(current: Snapshot, message: String) {
        gate.frame(current.revision, false, SystemClock.elapsedRealtime());hold.reset()
        placements.discardFor(current.revision)
        val action = pendingAction.get()
        if (action?.revision == current.revision) pendingAction.compareAndSet(action, null)
        publish(Preview(current.revision, current.camera, emptyList(), null, message))
    }

    private fun publish(value: Preview) {
        preview.set(value)
        if (posted.compareAndSet(false, true)) post {
            posted.set(false)
            val latest = preview.getAndSet(null)
            val current = snapshot
            if (foreground && latest != null && latest.revision == current.revision && latest.camera == cameraMode) {
                if (latest.boxes != null && (!latest.camera || gate.allows(current.revision, SystemClock.elapsedRealtime()))) overlay.position(current, latest.points, latest.boxes, latest.progress, latest.cursor, latest.radii, latest.inverse) else overlay.clear()
                status(latest.message)
                reportShown()
            }
        }
    }

    private fun drawCamera(frame: Frame) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST); vertices.position(0); uv.position(0)
        frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, vertices, Coordinates2d.TEXTURE_NORMALIZED, uv)
        GLES20.glUseProgram(cameraProgram); GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        val p = GLES20.glGetAttribLocation(cameraProgram, "p"); val u = GLES20.glGetAttribLocation(cameraProgram, "uv")
        vertices.position(0); uv.position(0); GLES20.glEnableVertexAttribArray(p); GLES20.glEnableVertexAttribArray(u)
        GLES20.glVertexAttribPointer(p, 2, GLES20.GL_FLOAT, false, 0, vertices); GLES20.glVertexAttribPointer(u, 2, GLES20.GL_FLOAT, false, 0, uv)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4); GLES20.glDisableVertexAttribArray(p); GLES20.glDisableVertexAttribArray(u)
    }

    private inner class ProcedureOverlay : FrameLayout(activity) {
        private val buttons = mutableListOf<Button>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var shownRevision = -1
        private var points = emptyList<ComponentProjection.Point?>()
        private var boxes = emptyList<ArChoiceLayout.Box>()
        private var progress = 0f
        private var cursor: ComponentProjection.Point?=null
        private var radii = emptyList<Float>()
        private var inverse:FloatArray?=null
        init { setWillNotDraw(false); setBackgroundColor(Palette.canvas) }
        fun bind(targets: List<Target>, width: Int, revision: Int): List<Float> {
            buttons.forEach { removeView(it) }; buttons.clear()
            return targets.mapIndexed { index, target ->
                val button = activity.action("${index + 1} · ${target.label}", false) { requestAction(target.id, revision) }.apply {
                    tag = "procedure-target-${target.id}"; textSize = 15f
                    setPadding(activity.dp(10), activity.dp(8), activity.dp(10), activity.dp(8))
                    visibility = INVISIBLE; isEnabled = false
                }
                button.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                val height = button.measuredHeight.coerceAtLeast(activity.dp(48))
                buttons.add(button); addView(button, LayoutParams(width, height)); height.toFloat()
            }
        }
        fun clear() {
            shownRevision = -1; points = emptyList(); boxes = emptyList();progress=0f;cursor=null;radii=emptyList();inverse=null
            buttons.forEach { it.visibility = INVISIBLE; it.isEnabled = false }; invalidate()
        }
        fun position(current: Snapshot, projected: List<ComponentProjection.Point?>, placements: List<ArChoiceLayout.Box>, holdProgress:Float, aimPoint:ComponentProjection.Point?, zoneRadii:List<Float>, inverseMatrix:FloatArray?) {
            if (placements.size != buttons.size) { clear(); return }
            shownRevision = current.revision; points = projected; boxes = placements;progress=holdProgress;cursor=aimPoint;radii=zoneRadii;inverse=inverseMatrix
            buttons.forEachIndexed { i, button ->
                // Real layout positions let native scrolling/focus reveal the entire action.
                // Translation-only placement left requestRectangleOnScreen targeting the old origin.
                val params=button.layoutParams as LayoutParams
                val left=placements[i].left.toInt();val top=placements[i].top.toInt()
                if(params.leftMargin!=left || params.topMargin!=top) {
                    params.leftMargin=left;params.topMargin=top;button.layoutParams=params
                }
                button.visibility = VISIBLE; button.isEnabled = current.scene.enabled
            }
            invalidate()
        }
        fun screenHit(x:Float,y:Float,current:Snapshot):ProcedureSpatial.Hit? {
            if(current.camera || current.revision!=shownRevision)return null
            return inverse?.let { ProcedureSpatial.hit(x,y,width,height,it,ProcedureSpatial.zones(current.scene.stepId)) }
        }
        fun showHold(value:Float,point:ComponentProjection.Point) { progress=value;cursor=point;invalidate() }
        fun fullyVisible(revision: Int): Boolean {
            val rect = Rect()
            return shownRevision == revision && isShown && points.isNotEmpty() && getLocalVisibleRect(rect) &&
                points.all { it != null && rect.contains(it.x.toInt(), it.y.toInt()) } && buttons.all {
                    it.visibility == VISIBLE && it.isEnabled && rect.contains(kotlin.math.floor(it.x).toInt(), kotlin.math.floor(it.y).toInt(), kotlin.math.ceil(it.x + it.width).toInt(), kotlin.math.ceil(it.y + it.height).toInt())
                }
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if(snapshot.scene.spatial) {
                points.forEachIndexed { index,p -> if(p!=null && index<radii.size) {
                    paint.style=Paint.Style.STROKE;paint.strokeWidth=activity.dp(3).toFloat();paint.color=Color.WHITE
                    canvas.drawCircle(p.x,p.y,radii[index],paint)
                    paint.strokeWidth=activity.dp(1).toFloat();paint.color=Palette.blue
                    canvas.drawCircle(p.x,p.y,radii[index],paint)
                    drawMarker(canvas,p,index+1)
                } }
                cursor?.let { p ->
                    val r=activity.dp(22).toFloat();paint.style=Paint.Style.STROKE;paint.strokeWidth=activity.dp(4).toFloat();paint.color=Palette.blue
                    canvas.drawArc(p.x-r,p.y-r,p.x+r,p.y+r,-90f,360*progress,false,paint)
                }
            }
            if (this@ProcedureSceneView.foreground && cameraMode && cameraRunning) PlacementAim.draw(canvas, paint, activity, width, height)
            // Draw every connector before the markers so crossings cannot obscure numbers.
            points.forEachIndexed { i, point ->
                if (point == null || i >= boxes.size) return@forEachIndexed
                val box = boxes[i]
                paint.style = Paint.Style.STROKE; paint.strokeWidth = activity.dp(3).toFloat(); paint.color = Color.WHITE
                canvas.drawLine(point.x, point.y, box.left + box.width / 2, box.top, paint)
                paint.strokeWidth = activity.dp(1).toFloat(); paint.color = Palette.muted
                canvas.drawLine(point.x, point.y, box.left + box.width / 2, box.top, paint)
            }
            points.forEachIndexed { i, point ->
                if (point != null && i < boxes.size) drawMarker(canvas, point, i + 1)
            }
            paint.style = Paint.Style.FILL
        }
        private fun drawMarker(canvas: Canvas, point: ComponentProjection.Point, number: Int) {
            // Numbers describe the action-to-location link; all choices use identical styling.
            // Compact badges leave the procedural equipment state visible underneath.
            val radius = activity.dp(10).toFloat()
            paint.style = Paint.Style.FILL; paint.color = Color.WHITE
            canvas.drawCircle(point.x, point.y, radius, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = activity.dp(1).toFloat(); paint.color = Palette.muted
            canvas.drawCircle(point.x, point.y, radius, paint)
            paint.style = Paint.Style.FILL; paint.color = Palette.ink
            paint.textSize = activity.dp(12).toFloat(); paint.textAlign = Paint.Align.CENTER; paint.isFakeBoldText = true
            canvas.drawText(number.toString(), point.x, point.y - (paint.ascent() + paint.descent()) / 2, paint)
            paint.textAlign = Paint.Align.LEFT; paint.isFakeBoldText = false
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if(snapshot.scene.spatial && !cameraMode) {
                when(event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { parent.requestDisallowInterceptTouchEvent(true);beginPointer(event.x,event.y) }
                    MotionEvent.ACTION_MOVE -> pointer.get()?.let { pointer.set(it.copy(x=event.x,y=event.y)) }
                    MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL,MotionEvent.ACTION_POINTER_DOWN -> { releasePointer();parent.requestDisallowInterceptTouchEvent(false);if(event.actionMasked==MotionEvent.ACTION_UP)performClick() }
                }
            } else if(event.actionMasked==MotionEvent.ACTION_UP) { if(cameraMode)requestPlacement(event.x,event.y);performClick() }
            return true
        }
        override fun performClick(): Boolean { super.performClick(); return true }
    }

    companion object {
        private fun floats(values: FloatArray) = ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values); position(0) }
        private fun shader(type: Int, source: String) = GLES20.glCreateShader(type).also { GLES20.glShaderSource(it, source); GLES20.glCompileShader(it) }
    }
}
