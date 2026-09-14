package com.narvyn.suraksha

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Fixed, reviewed viewing angles keep the selected recognition anchors in front of the model. */
class ComponentSceneView(context: Context, private val module: String): FrameLayout(context) {
    private data class Scene(val revision: Int, val yaw: Float, val order: List<ComponentCatalog.Part>)
    private val surface = GLSurfaceView(context)
    private val markers = mutableListOf<Button>()
    private val leaders = LeaderLines(context)
    private var revision = 0
    @Volatile private var scene = Scene(0, 20f, ComponentCatalog.modules.getValue(module))
    private var viewportWidth = 1
    private var viewportHeight = 1

    init {
        surface.setEGLContextClientVersion(2)
        surface.setEGLConfigChooser(8,8,8,8,16,0)
        surface.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(surface, LayoutParams(-1,-1))
        addView(leaders, LayoutParams(-1,-1))
        repeat(3) {
            val marker = context.action("", false) {}.apply { textSize = 16f; minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0; setPadding(0,0,0,0); visibility = INVISIBLE }
            markers.add(marker)
            addView(marker, LayoutParams(context.dp(48),context.dp(48)))
        }
        val equipment = WorldEquipment()
        val projection = FloatArray(16); val view = FloatArray(16); val vp = FloatArray(16); val model = FloatArray(16); val mvp = FloatArray(16)
        val eye = floatArrayOf(0f,.62f,1.6f)
        surface.setRenderer(object: GLSurfaceView.Renderer {
            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) { GLES20.glClearColor(.965f,.973f,.99f,1f); equipment.create() }
            override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
                viewportWidth = w; viewportHeight = h
                GLES20.glViewport(0,0,w,h)
                Matrix.perspectiveM(projection,0,34f,w.toFloat()/h.coerceAtLeast(1),.05f,10f)
            }
            override fun onDrawFrame(gl: GL10?) {
                val snapshot = scene
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
                Matrix.setLookAtM(view,0,eye[0],eye[1],eye[2],0f,.27f,0f,0f,1f,0f)
                Matrix.multiplyMM(vp,0,projection,0,view,0)
                Matrix.setIdentityM(model,0); Matrix.rotateM(model,0,snapshot.yaw,0f,1f,0f)
                equipment.draw(vp,model,module,eye)
                Matrix.multiplyMM(mvp,0,vp,0,model,0)
                val points = snapshot.order.map { ComponentProjection.project(it.point,mvp,viewportWidth,viewportHeight) }
                post { if (scene.revision == snapshot.revision) positionMarkers(points) }
            }
        })
        surface.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    fun configure(session: ComponentSession, hi: Boolean, select: (String) -> Unit) {
        revision++
        scene = Scene(revision,session.yaw,session.order)
        markers.forEachIndexed { i, button ->
            val part = session.order[i]; val letter = ('A'.code+i).toChar().toString()
            button.text = letter
            button.tag = "component-marker-${part.id}"
            button.contentDescription = (if (hi) "चिह्न $letter" else "Marker $letter") + if (session.showLabels) ": ${part.title(hi)}" else ""
            val target = session.showLabels && part.id == session.target.id
            button.background = context.shape(if (target) Palette.blue else Palette.surface,24,Palette.blue)
            button.setTextColor(if (target) android.graphics.Color.WHITE else Palette.ink)
            button.isEnabled = session.stage in 1..2 && session.answer == null
            button.visibility = INVISIBLE
            button.setOnClickListener { select(part.id) }
        }
        leaders.lines = emptyList(); leaders.invalidate()
        surface.requestRender()
    }

    private fun positionMarkers(points: List<ComponentProjection.Point?>) {
        val size = context.dp(48).toFloat()
        val gap = context.dp(10).toFloat()
        val margin = context.dp(4).toFloat()
        // Callouts sit in one separate column with leader lines, so nearby parts never share a hit area.
        val ordered = points.indices.filter { points[it] != null }.sortedBy { points[it]!!.y }
        val positions = mutableMapOf<Int, Float>()
        var bottom = margin-size-gap
        for (index in ordered) {
            val y = (points[index]!!.y-size/2).coerceIn(margin,(height-size-margin).coerceAtLeast(margin))
            positions[index] = maxOf(y,bottom+size+gap); bottom = positions.getValue(index)
        }
        if (ordered.isNotEmpty()) {
            val overflow = (bottom+size+margin-height).coerceAtLeast(0f)
            ordered.forEach { positions[it] = positions.getValue(it)-overflow }
        }
        val x = (width-size-margin).coerceAtLeast(0f)
        val lines = mutableListOf<FloatArray>()
        markers.forEachIndexed { i, button ->
            val p = points.getOrNull(i)
            if (p == null) button.visibility = INVISIBLE
            else {
                val y = positions.getValue(i)
                button.x = x; button.y = y; button.visibility = VISIBLE
                lines.add(floatArrayOf(p.x,p.y,x,y+size/2))
            }
        }
        leaders.lines = lines; leaders.invalidate()
    }
    fun resume() { surface.onResume(); surface.requestRender() }
    fun pause() { surface.onPause() }
    private class LeaderLines(context: Context): View(context) {
        var lines: List<FloatArray> = emptyList()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        override fun onDraw(canvas: Canvas) {
            paint.color = Palette.blue; paint.strokeWidth = context.dp(2).toFloat()
            lines.forEach { line -> canvas.drawLine(line[0],line[1],line[2],line[3],paint); canvas.drawCircle(line[0],line[1],context.dp(4).toFloat(),paint) }
        }
    }
}
