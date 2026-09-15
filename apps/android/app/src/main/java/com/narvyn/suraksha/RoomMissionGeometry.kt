package com.narvyn.suraksha

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.IdentityHashMap
import kotlin.math.*

/** Original room-scale training illustrations, in metres; +Y up, +Z front, origin at the base.
 * These meshes are simulated scene cues, not equipment detection or operational specifications.
 * Call create() after each EGL context creation and draw() on that context's renderer thread.
 */
class RoomMissionGeometry {
    private data class Material(val r:Float,val g:Float,val b:Float,val rough:Float=.65f,val glow:Float=0f)
    private data class Mesh(val buffer:FloatBuffer,val count:Int)
    private data class Tongue(val x:Float,val z:Float,val width:Float,val height:Float,val phase:Float)
    private val charcoal=Material(.085f,.095f,.11f,.94f)
    private val steel=Material(.51f,.58f,.64f,.25f)
    private val dark=Material(.10f,.15f,.19f,.76f)
    private val red=Material(.79f,.035f,.045f,.28f)
    private val yellow=Material(.98f,.68f,.035f,.42f)
    private val green=Material(.035f,.58f,.34f,.44f,.08f)
    private val white=Material(.93f,.96f,.93f,.52f)
    private val vest=Material(.79f,.91f,.095f,.77f)
    private val glove=Material(.69f,.73f,.70f,.9f)
    private val skin=Material(.55f,.37f,.25f,.83f)
    private val orange=Material(.99f,.25f,.015f,.8f,.85f)
    private val amber=Material(1f,.72f,.055f,.8f,1f)
    private val bed=fireBed()
    private val flame=flame(orange)
    private val core=flame(amber)
    private val markers=mapOf("safe-point" to safePoint(),"barrier" to barrier(closed=true),"attendant" to attendant(),"alarm" to alarm(),"confined-zone" to confinedZone(),"exit" to exitSign(false),"blocked-exit" to exitSign(true),"assembly" to assembly(),"ppe-kit" to ppeKit(),"dust-mask" to dustMask())
    private val openBarrier=barrier(closed=false)
    private val tongues=arrayOf(
        Tongue(-.30f,-.08f,.15f,.43f,.3f),Tongue(-.15f,.045f,.20f,.58f,1.6f),
        Tongue(.015f,-.07f,.23f,.66f,3.2f),Tongue(.20f,.035f,.19f,.52f,4.5f),
        Tongue(.33f,-.075f,.12f,.39f,5.7f),Tongue(-.07f,.17f,.13f,.33f,2.4f),
        Tongue(.15f,-.20f,.12f,.36f,4f)
    )
    private var program=0
    // All flame instances share the same immutable mesh buffer and therefore one VBO.
    private val vertexBuffers=IdentityHashMap<FloatBuffer,Int>()
    private var positionLocation=0;private var normalLocation=0;private var colorLocation=0;private var materialLocation=0
    private var mvpLocation=0;private var normalMatrixLocation=0;private var viewDirectionLocation=0;private var pulseLocation=0
    private val transform=FloatArray(16);private val mvp=FloatArray(16)
    private val inverse=FloatArray(16);private val normalMatrix=FloatArray(16)
    private val inverseVp=FloatArray(16);private val near=FloatArray(4);private val far=FloatArray(4)
    private val clipNear=floatArrayOf(0f,0f,-1f,1f);private val clipFar=floatArrayOf(0f,0f,1f,1f)
    private val viewDirection=floatArrayOf(0f,0f,1f)

    fun create() {
        // Old buffer names are invalid after context loss; never delete them in the new context.
        vertexBuffers.clear()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,0)
        fun compile(type:Int,source:String):Int {
            val shader=GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader)
            val result=IntArray(1);GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,result,0)
            if(result[0]==0) { val message=GLES20.glGetShaderInfoLog(shader);GLES20.glDeleteShader(shader);error(message) }
            return shader
        }
        val vertex=compile(GLES20.GL_VERTEX_SHADER,"""
            uniform mat4 mvp; uniform mat4 normalMatrix;
            attribute vec3 position; attribute vec3 normal; attribute vec3 color; attribute vec2 material;
            varying mediump vec3 n; varying mediump vec3 base; varying mediump vec2 surface;
            void main(){gl_Position=mvp*vec4(position,1.0);n=(normalMatrix*vec4(normal,0.0)).xyz;base=color;surface=material;}
        """.trimIndent())
        val fragment=compile(GLES20.GL_FRAGMENT_SHADER,"""
            precision mediump float;
            uniform vec3 viewDirection; uniform float pulse;
            varying mediump vec3 n; varying mediump vec3 base; varying mediump vec2 surface;
            void main(){
                vec3 normal=normalize(n);vec3 light=normalize(vec3(-.45,.85,.60));
                vec3 halfVector=normalize(light+viewDirection);
                float diffuse=max(dot(normal,light),0.0);
                float specular=pow(max(dot(normal,halfVector),0.0),mix(80.0,9.0,surface.x));
                vec3 hemi=mix(vec3(.25,.27,.30),vec3(.57,.62,.69),normal.y*.5+.5);
                vec3 shaded=base*(hemi*.70+diffuse*.72)+vec3(specular*(1.0-surface.x)*.40);
                shaded+=base*max(dot(normal,normalize(vec3(.7,.25,-.5))),0.0)*.15;
                shaded=mix(shaded,base*mix(.89,1.0,pulse),clamp(surface.y,0.0,1.0));
                gl_FragColor=vec4(clamp(shaded,0.0,1.0),1.0);
            }
        """.trimIndent())
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment);GLES20.glLinkProgram(program)
        GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment)
        val result=IntArray(1);GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,result,0)
        check(result[0]!=0) { GLES20.glGetProgramInfoLog(program) }
        positionLocation=GLES20.glGetAttribLocation(program,"position");normalLocation=GLES20.glGetAttribLocation(program,"normal")
        colorLocation=GLES20.glGetAttribLocation(program,"color");materialLocation=GLES20.glGetAttribLocation(program,"material")
        mvpLocation=GLES20.glGetUniformLocation(program,"mvp");normalMatrixLocation=GLES20.glGetUniformLocation(program,"normalMatrix")
        viewDirectionLocation=GLES20.glGetUniformLocation(program,"viewDirection");pulseLocation=GLES20.glGetUniformLocation(program,"pulse")
    }

    /** progress suppresses fire (1 = no flames); active exaggerates worsening, without modelling fire physics.
     * For barrier, active selects deployed tape; false leaves two posts with a short loose tail at the left.
     * Other kinds are static. Each supplied model is an independent anchor; this never clears depth.
     */
    fun draw(vp:FloatArray,model:FloatArray,kind:String,timeSeconds:Float,progress:Float=0f,active:Boolean=false) {
        if(program==0 || (kind!="fire" && kind !in markers))return
        require(vp.size>=16 && model.size>=16)
        val time=if(timeSeconds.isFinite())timeSeconds%3600f else 0f
        val suppression=if(progress.isFinite())progress.coerceIn(0f,1f)else 0f
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glDepthMask(true)
        GLES20.glUseProgram(program)
        try {
        // The centre view ray supplies a stable highlight direction using only the public VP contract.
        if(Matrix.invertM(inverseVp,0,vp,0)) {
            Matrix.multiplyMV(near,0,inverseVp,0,clipNear,0);Matrix.multiplyMV(far,0,inverseVp,0,clipFar,0)
            if(abs(near[3])>1e-6f && abs(far[3])>1e-6f) {
                var length=0f
                for(i in 0..2) { viewDirection[i]=near[i]/near[3]-far[i]/far[3];length+=viewDirection[i]*viewDirection[i] }
                if(length>1e-8f)for(i in 0..2)viewDirection[i]/=sqrt(length)
            }
        }
        GLES20.glUniform3fv(viewDirectionLocation,1,viewDirection,0)
        GLES20.glUniform1f(pulseLocation,.5f+.5f*sin(time*7.2f))
        if(kind=="fire") {
            render(bed,vp,model)
            if(suppression<1f)for((index,tongue) in tongues.withIndex()) {
                val remaining=1f-suppression
                val flicker=1f+.055f*sin(time*(if(active)10.5f else 6.2f)+tongue.phase)
                val height=tongue.height*remaining*(if(active)1.32f else 1f)*flicker
                model.copyInto(transform,0,0,16)
                Matrix.translateM(transform,0,tongue.x,.035f,tongue.z)
                Matrix.rotateM(transform,0,sin(time*3.1f+tongue.phase)*(if(active)8f else 4f),0f,0f,1f)
                Matrix.rotateM(transform,0,tongue.phase*31f,0f,1f,0f)
                Matrix.scaleM(transform,0,tongue.width*(.45f+.55f*remaining),height,tongue.width*.70f)
                render(flame,vp,transform)
                // Short brighter tongues sit around the visible edge, avoiding a flat flame billboard.
                model.copyInto(transform,0,0,16)
                Matrix.translateM(transform,0,tongue.x+.022f*sin(tongue.phase),.035f,tongue.z+if(index%2==0).074f else -.074f)
                Matrix.rotateM(transform,0,-tongue.phase*25f,0f,1f,0f)
                Matrix.scaleM(transform,0,tongue.width*.48f*remaining,height*.59f,tongue.width*.42f*remaining)
                render(core,vp,transform)
            }
        } else render(if(kind=="barrier" && !active)openBarrier else markers.getValue(kind),vp,model)
        } finally {
            GLES20.glDisableVertexAttribArray(positionLocation);GLES20.glDisableVertexAttribArray(normalLocation)
            GLES20.glDisableVertexAttribArray(colorLocation);GLES20.glDisableVertexAttribArray(materialLocation)
            // The camera pass in the next frame supplies client FloatBuffers.
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,0)
        }
    }
    private fun render(mesh:Mesh,vp:FloatArray,model:FloatArray) {
        Matrix.multiplyMM(mvp,0,vp,0,model,0)
        if(!Matrix.invertM(inverse,0,model,0))return
        Matrix.transposeM(normalMatrix,0,inverse,0)
        GLES20.glUniformMatrix4fv(mvpLocation,1,false,mvp,0);GLES20.glUniformMatrix4fv(normalMatrixLocation,1,false,normalMatrix,0)
        bindVertices(mesh.buffer)
        fun bind(location:Int,offset:Int,size:Int) { GLES20.glVertexAttribPointer(location,size,GLES20.GL_FLOAT,false,44,offset*4);GLES20.glEnableVertexAttribArray(location) }
        bind(positionLocation,0,3);bind(normalLocation,3,3);bind(colorLocation,6,3);bind(materialLocation,9,2)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,mesh.count)
    }
    private fun bindVertices(buffer:FloatBuffer) {
        val cached=vertexBuffers[buffer]
        if(cached!=null) { GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,cached);return }
        val names=IntArray(1);GLES20.glGenBuffers(1,names,0)
        check(names[0]!=0) { "Could not allocate room vertex buffer" }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,names[0])
        val data=buffer.duplicate().apply { clear() }
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,buffer.capacity()*4,data,GLES20.GL_STATIC_DRAW)
        vertexBuffers[buffer]=names[0]
    }
    private fun fireBed()=Builder().apply {
        // Uneven fuel and ember bed directly on the room floor, with no plinth, table or miniature room.
        ellipsoid(0f,.015f,0f,.45f,.015f,.28f,charcoal)
        for(i in 0..7) {
            val x=-.34f+i*.094f;val z=if(i%2==0)-.045f else .065f
            rod(floatArrayOf(x-.055f,.028f,z-.09f),floatArrayOf(x+.065f,.037f,z+.075f),.026f,dark)
            ellipsoid(x,.043f,z,.032f,.008f,.018f,Material(.80f,.12f,.018f,.9f,.7f))
        }
    }.finish()
    private fun flame(material:Material)=Builder().apply {
        val heights=floatArrayOf(0f,.08f,.24f,.44f,.66f,.84f,1f)
        val radii=floatArrayOf(.27f,.48f,.43f,.32f,.21f,.10f,0f)
        val centres=floatArrayOf(0f,-.035f,.025f,.12f,.18f,.16f,.29f)
        for(row in 0 until heights.lastIndex)for(i in 0 until 18) {
            fun point(r:Int,j:Int):FloatArray { val angle=j*2f*PI.toFloat()/18;return floatArrayOf(centres[r]+cos(angle)*radii[r],heights[r],sin(angle)*radii[r]) }
            fun normal(r:Int,j:Int):FloatArray { val angle=j*2f*PI.toFloat()/18;return unit(floatArrayOf(cos(angle),.32f,sin(angle))) }
            fun shade(r:Int)=material.copy(g=(material.g+sin(heights[r]*PI.toFloat())*.18f).coerceAtMost(1f))
            triangle(point(row,i),point(row+1,i),point(row,i+1),shade(row),normal(row,i),normal(row+1,i),normal(row,i+1))
            if(row<heights.lastIndex-1)triangle(point(row,i+1),point(row+1,i),point(row+1,i+1),shade(row+1),normal(row,i+1),normal(row+1,i),normal(row+1,i+1))
        }
    }.finish()
    private fun safePoint()=Builder().apply {
        ring(0f,.009f,0f,.45f,.38f,.018f,green)
        for(i in 0..7) { val a=i*PI.toFloat()/4;ellipsoid(cos(a)*.415f,.023f,sin(a)*.415f,.020f,.009f,.020f,white) }
        rod(floatArrayOf(0f,.015f,-.37f),floatArrayOf(0f,.72f,-.37f),.020f,steel)
        disc(0f,.68f,-.37f,.135f,.035f,green)
        // A raised check marker remains legible from both sides; UI supplies the assigned-safe-point label.
        for(z in floatArrayOf(-.347f,-.393f)) {
            rod(floatArrayOf(-.07f,.68f,z),floatArrayOf(-.015f,.625f,z),.015f,white)
            rod(floatArrayOf(-.015f,.625f,z),floatArrayOf(.078f,.735f,z),.015f,white)
        }
    }.finish()
    private fun confinedZone()=Builder().apply {
        // Simulated access opening behind the barrier, not a detected hole or a live hazard boundary.
        // The dark floor and taller inner wall suggest a recess without cutting or masking the camera image.
        val centreZ=-.55f
        val interior=Material(.014f,.020f,.025f,.98f)
        val innerWall=Material(.12f,.16f,.19f,.78f)
        val warning=Material(.98f,.60f,.035f,.78f,.06f)
        rod(floatArrayOf(0f,0f,centreZ),floatArrayOf(0f,.008f,centreZ),.40f,interior,64)
        ring(0f,.009f,centreZ,.402f,.355f,.006f,Material(.030f,.041f,.049f,.95f))
        ring(0f,.033f,centreZ,.423f,.400f,.066f,innerWall)
        ring(0f,.017f,centreZ,.47f,.420f,.034f,dark)
        ring(0f,.060f,centreZ,.455f,.400f,.020f,steel)
        for(i in 0 until 12) {
            val angle=i*PI.toFloat()/6;val x=cos(angle)*.432f;val z=centreZ+sin(angle)*.432f
            cylinder(x,.073f,z,.018f,.006f,dark)
            rod(floatArrayOf(x,.076f,z),floatArrayOf(x,.086f,z),.0115f,steel,6)
            box(x,.0865f,z,.012f,.001f,.002f,charcoal)
        }
        ring(0f,.006f,centreZ,.54f,.50f,.012f,warning)
        for(i in 0 until 12) {
            val angle=i*PI.toFloat()/6
            fun p(radius:Float,offset:Float)=floatArrayOf(cos(angle+offset)*radius,.0125f,centreZ+sin(angle+offset)*radius)
            quad(p(.50f,0f),p(.50f,.105f),p(.54f,.16f),p(.54f,.055f),charcoal)
        }
    }.finish()
    private fun barrier(closed:Boolean)=Builder().apply {
        for(x in floatArrayOf(-.36f,.36f)) {
            cylinder(x,.025f,0f,.09f,.05f,charcoal)
            cylinder(x,.46f,0f,.022f,.87f,yellow)
            cylinder(x,.90f,0f,.031f,.028f,dark)
            cylinder(x,.28f,0f,.023f,.10f,dark)
        }
        // Post centres x=±.36; attachment centres y=.47/.73, z=0. Closed tape reaches both posts.
        for(y in floatArrayOf(.47f,.73f)) {
            if(!closed) {
                // A folded, hanging tail stays entirely beside the left post; the central opening is clear.
                box(-.315f,y,0f,.10f,.095f,.014f,yellow)
                box(-.285f,y-.09f,.01f,.055f,.135f,.014f,yellow)
                for(offset in floatArrayOf(-.045f,-.09f,-.135f))for(z in floatArrayOf(.0175f,.0025f)) {
                    val a=floatArrayOf(-.3125f,y+offset-.010f,z);val b=floatArrayOf(-.2575f,y+offset+.006f,z)
                    val c=floatArrayOf(-.2575f,y+offset+.021f,z);val d=floatArrayOf(-.3125f,y+offset+.005f,z)
                    if(z>.01f)quad(a,b,c,d,charcoal)else quad(d,c,b,a,charcoal)
                }
                continue
            }
            box(0f,y,0f,.72f,.095f,.014f,yellow)
            for(i in 0..5) {
                val x=-.35f+i*.12f
                for(z in floatArrayOf(.0075f,-.0075f)) {
                    val a=floatArrayOf(x,y-.0475f,z);val b=floatArrayOf(x+.055f,y-.0475f,z)
                    val c=floatArrayOf(x+.105f,y+.0475f,z);val d=floatArrayOf(x+.05f,y+.0475f,z)
                    if(z>0)quad(a,b,c,d,charcoal)else quad(d,c,b,a,charcoal)
                }
            }
        }
    }.finish()
    private fun attendant()=Builder().apply {
        // Full-height worker silhouette, not a miniature figurine. High-visibility clothing identifies the role.
        for(x in floatArrayOf(-.095f,.095f)) {
            ellipsoid(x,.07f,.035f,.080f,.070f,.135f,charcoal)
            rod(floatArrayOf(x,.14f,0f),floatArrayOf(x,.65f,0f),.066f,dark)
        }
        ellipsoid(0f,.68f,0f,.16f,.11f,.095f,dark)
        torso(.70f,1.16f,.145f,.195f,.105f,vest)
        box(0f,.81f,.111f,.29f,.047f,.008f,white);box(0f,.81f,-.111f,.29f,.047f,.008f,white)
        for(x in floatArrayOf(-.095f,.095f))for(z in floatArrayOf(-.112f,.112f))box(x,1.01f,z,.038f,.265f,.009f,white)
        box(0f,.94f,.118f,.012f,.43f,.005f,dark)
        rod(floatArrayOf(0f,1.13f,0f),floatArrayOf(0f,1.26f,0f),.052f,skin)
        ellipsoid(0f,1.315f,0f,.088f,.112f,.085f,skin)
        ellipsoid(0f,1.40f,-.008f,.115f,.091f,.112f,yellow)
        ellipsoid(0f,1.395f,.021f,.139f,.015f,.144f,yellow)
        box(0f,1.462f,-.008f,.023f,.041f,.16f,yellow)
        // One open gloved hand is held up; the other arm rests beside a chest-mounted radio.
        rod(floatArrayOf(-.175f,1.12f,0f),floatArrayOf(-.285f,.995f,0f),.059f,dark)
        rod(floatArrayOf(-.285f,.995f,0f),floatArrayOf(-.35f,1.20f,.025f),.047f,dark)
        ellipsoid(-.35f,1.245f,.027f,.052f,.075f,.024f,glove)
        for(i in 0..3)rod(floatArrayOf(-.386f+i*.021f,1.285f,.027f),floatArrayOf(-.386f+i*.021f,1.34f-abs(i-1.5f)*.010f,.027f),.008f,glove)
        rod(floatArrayOf(.175f,1.12f,0f),floatArrayOf(.235f,.91f,0f),.057f,dark)
        rod(floatArrayOf(.235f,.91f,0f),floatArrayOf(.225f,.74f,.055f),.045f,dark)
        ellipsoid(.225f,.71f,.055f,.045f,.060f,.027f,glove)
        box(.115f,1.055f,.132f,.058f,.10f,.035f,charcoal)
        rod(floatArrayOf(.132f,1.09f,.132f),floatArrayOf(.132f,1.20f,.132f),.005f,charcoal)
        for(i in 0..3)box(.114f,1.028f+i*.012f,.151f,.037f,.004f,.002f,steel)
    }.finish()
    private fun exitSign(blocked:Boolean)=Builder().apply {
        val ink=if(blocked)red else green
        for(x in floatArrayOf(-.27f,.27f))box(x,.48f,-.10f,.035f,.96f,.045f,steel)
        box(0f,.94f,-.10f,.57f,.045f,.045f,steel)
        box(0f,.80f,-.075f,.46f,.18f,.035f,ink)
        if(blocked){
            for(y in floatArrayOf(.34f,.52f))box(0f,y,-.045f,.56f,.08f,.035f,yellow)
            rod(floatArrayOf(-.11f,.75f,-.045f),floatArrayOf(.11f,.85f,-.045f),.015f,white)
            rod(floatArrayOf(-.11f,.85f,-.045f),floatArrayOf(.11f,.75f,-.045f),.015f,white)
        }else{
            rod(floatArrayOf(-.14f,.80f,-.045f),floatArrayOf(.14f,.80f,-.045f),.015f,white)
            rod(floatArrayOf(.07f,.86f,-.045f),floatArrayOf(.14f,.80f,-.045f),.015f,white)
            rod(floatArrayOf(.07f,.74f,-.045f),floatArrayOf(.14f,.80f,-.045f),.015f,white)
        }
    }.finish()
    private fun assembly()=Builder().apply {
        rod(floatArrayOf(0f,0f,0f),floatArrayOf(0f,.82f,0f),.022f,steel)
        box(0f,.74f,.01f,.43f,.32f,.04f,green)
        // Group pictogram on a virtual accountability marker.
        for(x in floatArrayOf(-.10f,0f,.10f)){
            ellipsoid(x,.79f,.04f,.025f,.025f,.010f,white)
            box(x,.715f,.04f,.046f,.09f,.015f,white)
        }
    }.finish()
    private fun ppeKit()=Builder().apply {
        box(0f,.025f,0f,.64f,.05f,.42f,dark)
        ellipsoid(-.14f,.18f,0f,.14f,.13f,.13f,yellow)
        ellipsoid(-.14f,.14f,.02f,.17f,.014f,.16f,yellow)
        for(x in floatArrayOf(.065f,.185f)){
            ellipsoid(x,.10f,.035f,.044f,.08f,.075f,charcoal)
            ellipsoid(x,.045f,.09f,.05f,.04f,.10f,charcoal)
        }
        box(-.14f,.075f,.17f,.19f,.055f,.015f,steel)
        for(x in floatArrayOf(-.19f,-.09f))box(x,.075f,.18f,.063f,.034f,.012f,Material(.20f,.56f,.66f,.16f))
        for(x in floatArrayOf(.085f,.19f)){
            ellipsoid(x,.09f,-.13f,.043f,.016f,.055f,glove)
            for(i in 0..3)rod(floatArrayOf(x-.03f+i*.02f,.09f,-.16f),floatArrayOf(x-.03f+i*.02f,.09f,-.205f),.007f,glove)
        }
    }.finish()
    private fun dustMask()=Builder().apply {
        ellipsoid(0f,.94f,0f,.105f,.075f,.055f,white)
        for(x in floatArrayOf(-.12f,.12f)){
            rod(floatArrayOf(x*.7f,.99f,0f),floatArrayOf(x,.95f,-.04f),.006f,steel)
            rod(floatArrayOf(x,.95f,-.04f),floatArrayOf(x*.7f,.89f,0f),.006f,steel)
        }
        for(y in floatArrayOf(.92f,.95f,.98f))box(0f,y,.05f,.13f,.004f,.008f,steel)
    }.finish()
    private fun alarm()=Builder().apply {
        // Generic manual alarm cue with recessed activation face and corner fixings; no live controls.
        box(0f,.075f,0f,.15f,.15f,.05f,dark)
        box(0f,.075f,.011f,.143f,.143f,.05f,red)
        box(0f,.067f,.038f,.108f,.086f,.010f,white)
        box(0f,.067f,.044f,.084f,.065f,.006f,red)
        box(0f,.12f,.039f,.080f,.016f,.004f,white)
        for(x in floatArrayOf(-.059f,.059f))for(y in floatArrayOf(.016f,.134f)) {
            ellipsoid(x,y,.038f,.004f,.004f,.002f,steel)
            box(x,y,.0405f,.0045f,.0009f,.001f,dark)
        }
        // Simple raised bell pictogram formed as geometry instead of an unreadable texture.
        ellipsoid(0f,.070f,.049f,.017f,.019f,.003f,white)
        box(0f,.054f,.049f,.041f,.005f,.006f,white)
        ellipsoid(0f,.048f,.049f,.004f,.004f,.003f,white)
    }.finish()

    private class Builder {
        private val vertices=ArrayList<Float>()
        private fun vertex(p:FloatArray,n:FloatArray,m:Material) { for(v in p)vertices.add(v);for(v in n)vertices.add(v);vertices.add(m.r);vertices.add(m.g);vertices.add(m.b);vertices.add(m.rough);vertices.add(m.glow) }
        fun triangle(a:FloatArray,b:FloatArray,c:FloatArray,m:Material,na:FloatArray?=null,nb:FloatArray?=null,nc:FloatArray?=null) {
            val n=na ?: unit(cross(sub(b,a),sub(c,a)))
            vertex(a,n,m);vertex(b,nb?:n,m);vertex(c,nc?:n,m)
        }
        fun quad(a:FloatArray,b:FloatArray,c:FloatArray,d:FloatArray,m:Material) { triangle(a,b,c,m);triangle(a,c,d,m) }
        fun box(x:Float,y:Float,z:Float,w:Float,h:Float,d:Float,m:Material) {
            fun p(i:Int)=floatArrayOf(x+if(i and 1==0)-w/2 else w/2,y+if(i and 2==0)-h/2 else h/2,z+if(i and 4==0)-d/2 else d/2)
            for(face in arrayOf(intArrayOf(4,5,7,6),intArrayOf(1,0,2,3),intArrayOf(0,4,6,2),intArrayOf(5,1,3,7),intArrayOf(6,7,3,2),intArrayOf(0,1,5,4)))quad(p(face[0]),p(face[1]),p(face[2]),p(face[3]),m)
        }
        fun torso(low:Float,high:Float,waist:Float,shoulder:Float,depth:Float,m:Material) {
            val p=arrayOf(floatArrayOf(-waist,low,depth),floatArrayOf(waist,low,depth),floatArrayOf(shoulder,high,depth),floatArrayOf(-shoulder,high,depth),floatArrayOf(-waist,low,-depth),floatArrayOf(waist,low,-depth),floatArrayOf(shoulder,high,-depth),floatArrayOf(-shoulder,high,-depth))
            for(face in arrayOf(intArrayOf(0,1,2,3),intArrayOf(5,4,7,6),intArrayOf(4,0,3,7),intArrayOf(1,5,6,2),intArrayOf(3,2,6,7),intArrayOf(4,5,1,0)))quad(p[face[0]],p[face[1]],p[face[2]],p[face[3]],m)
        }
        fun ellipsoid(x:Float,y:Float,z:Float,rx:Float,ry:Float,rz:Float,m:Material) {
            fun n(row:Int,col:Int):FloatArray { val a=PI.toFloat()*row/10;val b=2*PI.toFloat()*col/18;return floatArrayOf(sin(a)*cos(b),cos(a),sin(a)*sin(b)) }
            fun p(n:FloatArray)=floatArrayOf(x+n[0]*rx,y+n[1]*ry,z+n[2]*rz)
            fun normal(n:FloatArray)=unit(floatArrayOf(n[0]/rx,n[1]/ry,n[2]/rz))
            for(row in 0 until 10)for(col in 0 until 18) {
                val a=n(row,col);val b=n(row+1,col);val c=n(row+1,col+1);val d=n(row,col+1)
                if(row>0)triangle(p(a),p(d),p(b),m,normal(a),normal(d),normal(b))
                if(row<9)triangle(p(b),p(d),p(c),m,normal(b),normal(d),normal(c))
            }
        }
        fun cylinder(x:Float,y:Float,z:Float,radius:Float,height:Float,m:Material)=rod(floatArrayOf(x,y-height/2,z),floatArrayOf(x,y+height/2,z),radius,m)
        fun disc(x:Float,y:Float,z:Float,radius:Float,depth:Float,m:Material)=rod(floatArrayOf(x,y,z-depth/2),floatArrayOf(x,y,z+depth/2),radius,m,32)
        fun rod(a:FloatArray,b:FloatArray,radius:Float,m:Material,segments:Int=18) {
            val axis=unit(sub(b,a));val u=unit(cross(axis,if(abs(axis[1])<.9f)floatArrayOf(0f,1f,0f)else floatArrayOf(1f,0f,0f)));val v=cross(axis,u)
            fun normal(i:Int):FloatArray { val t=i*2f*PI.toFloat()/segments;return FloatArray(3) { u[it]*cos(t)+v[it]*sin(t) } }
            fun p(centre:FloatArray,n:FloatArray)=FloatArray(3) { centre[it]+n[it]*radius }
            for(i in 0 until segments) {
                val n=normal(i);val next=normal(i+1);val pa=p(a,n);val pb=p(a,next);val pc=p(b,next);val pd=p(b,n)
                triangle(pa,pb,pd,m,n,next,n);triangle(pb,pc,pd,m,next,next,n)
                triangle(a,pb,pa,m,FloatArray(3) { -axis[it] });triangle(b,pd,pc,m,axis)
            }
        }
        fun ring(x:Float,y:Float,z:Float,outer:Float,inner:Float,height:Float,m:Material) {
            for(i in 0 until 64) {
                fun p(radius:Float,j:Int,dy:Float):FloatArray { val a=j*2f*PI.toFloat()/64;return floatArrayOf(x+radius*cos(a),y+dy,z+radius*sin(a)) }
                quad(p(inner,i,height/2),p(inner,i+1,height/2),p(outer,i+1,height/2),p(outer,i,height/2),m)
                quad(p(outer,i,-height/2),p(outer,i,height/2),p(outer,i+1,height/2),p(outer,i+1,-height/2),m)
                quad(p(inner,i+1,-height/2),p(inner,i+1,height/2),p(inner,i,height/2),p(inner,i,-height/2),m)
            }
        }
        fun finish():Mesh {
            check(vertices.size%33==0 && vertices.all { it.isFinite() })
            val buffer=ByteBuffer.allocateDirect(vertices.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            for(value in vertices)buffer.put(value)
            buffer.position(0);return Mesh(buffer,vertices.size/11)
        }
    }
    private companion object {
        fun sub(a:FloatArray,b:FloatArray)=FloatArray(3) { a[it]-b[it] }
        fun cross(a:FloatArray,b:FloatArray)=floatArrayOf(a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])
        fun unit(a:FloatArray):FloatArray { val length=sqrt(a[0]*a[0]+a[1]*a[1]+a[2]*a[2]);return if(length>1e-8f)FloatArray(3) { a[it]/length }else floatArrayOf(0f,1f,0f) }
    }
}
