package com.narvyn.suraksha

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.*

/** Original procedural training illustrations, in metres. No operational readings or manufacturer claims. */
class WorldEquipment {
 private data class Material(val color:FloatArray,val roughness:Float=.5f,val metal:Float=0f,val emission:Float=0f)
 private val red=Material(floatArrayOf(.72f,.025f,.045f),.27f)
 private val blue=Material(floatArrayOf(.075f,.22f,.52f),.34f)
 private val yellow=Material(floatArrayOf(.98f,.63f,.035f),.31f)
 private val rubber=Material(floatArrayOf(.026f,.039f,.05f),.86f)
 private val metal=Material(floatArrayOf(.64f,.70f,.75f),.24f,.85f)
 private val brass=Material(floatArrayOf(.66f,.45f,.17f),.28f,.8f)
 private val white=Material(floatArrayOf(.88f,.91f,.91f),.67f)
 private val glass=Material(floatArrayOf(.14f,.39f,.48f),.12f,.25f)
 private val dark=Material(floatArrayOf(.10f,.15f,.20f),.49f)
 private val screen=Material(floatArrayOf(.43f,.75f,.57f),.28f,0f,.23f)
 private val scenes=mapOf("fire" to fire(),"gas" to gas(),"machinery" to machinery(),"ppe" to ppe(),"emergency" to emergency())
 // Only the procedure view requests these meshes. Existing viewers keep their original buffers.
 private val procedureFire by lazy {
  buildMap<Int,FloatBuffer>{
   put(0,scenes.getValue("fire"));put(1,fire(pinRemoved=true))
   for(direction in -1..1)for(pressed in listOf(false,true)){
    val target=when(direction){-1->-.24f;1->.20f;else->-.12f}
    put(2+(direction+1)*2+if(pressed)1 else 0,fire(pinRemoved=true,aimX=target,pressed=pressed))
   }
  }
 }
 private val procedureGas by lazy { gas(includeBarrier=false) }
 private val fireProcedureProps by lazy { listOf(fireProps(false),fireProps(true)) }
 private val gasProcedureProps by lazy { (0..3).map { gasProps((it and 1) != 0,(it and 2) != 0) } }
 private var program=0
 private var position=0;private var normal=0;private var color=0;private var surface=0
 private var mvpLocation=0;private var modelLocation=0;private var eyeLocation=0;private var lightingLocation=0
 private val mvp=FloatArray(16)
 private val defaultEye=floatArrayOf(0f,.8f,2.5f)
 private val defaultLighting=floatArrayOf(1f,1f,1f,1f)
 fun create(){
  fun compile(type:Int,source:String):Int{val s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);val ok=IntArray(1);GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);check(ok[0]!=0){GLES20.glGetShaderInfoLog(s)};return s}
  val vertex=compile(GLES20.GL_VERTEX_SHADER,"""
   uniform mat4 mvp; uniform mat4 model;
   attribute vec3 position; attribute vec3 normal; attribute vec3 color; attribute vec3 surface;
   varying mediump vec3 worldPosition; varying mediump vec3 worldNormal; varying mediump vec3 base; varying mediump vec3 material;
   void main(){worldPosition=(model*vec4(position,1.0)).xyz;worldNormal=mat3(model)*normal;base=color;material=surface;gl_Position=mvp*vec4(position,1.0);}
  """.trimIndent())
  val fragment=compile(GLES20.GL_FRAGMENT_SHADER,"""
   precision mediump float;
   uniform vec3 eye; uniform vec4 lighting;
   varying mediump vec3 worldPosition; varying mediump vec3 worldNormal; varying mediump vec3 base; varying mediump vec3 material;
   void main(){
    vec3 n=normalize(worldNormal);vec3 v=normalize(eye-worldPosition);
    vec3 l=normalize(vec3(-0.45,0.82,0.65));vec3 h=normalize(l+v);
    float rough=material.x;float metallic=material.y;
    float diffuse=max(dot(n,l),0.0);float power=mix(100.0,9.0,rough);
    float spec=pow(max(dot(n,h),0.0),power)*(1.0-rough*.65);
    vec3 f0=mix(vec3(.045),base,metallic);
    vec3 fresnel=f0+(1.0-f0)*pow(1.0-max(dot(n,v),0.0),5.0);
    vec3 hemi=mix(vec3(.22,.23,.25),vec3(.54,.59,.68),n.y*.5+.5);
    vec3 result=base*(hemi*.62+diffuse*.72)*(1.0-metallic*.35);
    result+=fresnel*spec*1.65;
    // Broad studio fill makes rounded surfaces legible without a costly environment map.
    result+=base*max(dot(n,normalize(vec3(.7,.35,-.5))),0.0)*.15;
    result+=fresnel*pow(1.0-max(dot(n,v),0.0),3.0)*.12;
    result*=lighting.rgb*lighting.a;result+=base*material.z;
    gl_FragColor=vec4(clamp(result,0.0,1.0),1.0);
   }
  """.trimIndent())
  program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment);GLES20.glLinkProgram(program)
  GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment)
  val ok=IntArray(1);GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);check(ok[0]!=0){GLES20.glGetProgramInfoLog(program)}
  position=GLES20.glGetAttribLocation(program,"position");normal=GLES20.glGetAttribLocation(program,"normal");color=GLES20.glGetAttribLocation(program,"color");surface=GLES20.glGetAttribLocation(program,"surface")
  mvpLocation=GLES20.glGetUniformLocation(program,"mvp");modelLocation=GLES20.glGetUniformLocation(program,"model");eyeLocation=GLES20.glGetUniformLocation(program,"eye");lightingLocation=GLES20.glGetUniformLocation(program,"lighting")
 }
 /** Prepare bounded variants before starting a procedure renderer; no mesh construction is needed per frame. */
 fun prepareProcedure(module:String){when(module){"fire"->{procedureFire;fireProcedureProps};"gas"->{procedureGas;gasProcedureProps}}}
 /** Anchor is rigid. Default calls retain one original mesh; procedures add a separate pre-baked scene mesh. */
 fun draw(vp:FloatArray,anchor:FloatArray,module:String,cameraPosition:FloatArray=defaultEye,lightCorrection:FloatArray=defaultLighting,completedActions:Set<String> = emptySet(),procedureMode:Boolean=false){
  if(program==0)return
  val original=scenes[module]?:return
  val meshes=if(module=="fire" && (procedureMode || completedActions.isNotEmpty())){
   val pin="fire-pin" in completedActions;val aimed=pin && "fire-aim" in completedActions
   val pressed=aimed && "fire-squeeze" in completedActions && "fire-withdraw" !in completedActions
   val direction=if("fire-sweep-return" in completedActions)-1 else if("fire-sweep-right" in completedActions)1 else if("fire-sweep-left" in completedActions)-1 else 0
   val key=if(!pin)0 else if(!aimed)1 else 2+(direction+1)*2+if(pressed)1 else 0
   listOf(procedureFire.getValue(key)) + if(procedureMode)listOf(fireProcedureProps[if("fire-sweep-return" in completedActions)1 else 0])else emptyList()
  }else if(module=="gas" && (procedureMode || completedActions.isNotEmpty())){
   val key=(if("gas-boundary" in completedActions)1 else 0)+(if("gas-attendant" in completedActions)2 else 0)
   listOf(procedureGas) + if(procedureMode)listOf(gasProcedureProps[key])else emptyList()
  }else listOf(original)
  GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);GLES20.glUseProgram(program)
  Matrix.multiplyMM(mvp,0,vp,0,anchor,0)
  GLES20.glUniformMatrix4fv(mvpLocation,1,false,mvp,0);GLES20.glUniformMatrix4fv(modelLocation,1,false,anchor,0)
  GLES20.glUniform3fv(eyeLocation,1,cameraPosition,0);GLES20.glUniform4fv(lightingLocation,1,lightCorrection,0)
  for(mesh in meshes){
   for((location,offset) in listOf(position to 0,normal to 3,color to 6,surface to 9)){mesh.position(offset);GLES20.glVertexAttribPointer(location,3,GLES20.GL_FLOAT,false,48,mesh);GLES20.glEnableVertexAttribArray(location)}
   GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,mesh.capacity()/12)
  }
  for(location in intArrayOf(position,normal,color,surface))GLES20.glDisableVertexAttribArray(location)
  GLES20.glDisable(GLES20.GL_DEPTH_TEST)
 }
 private fun base(b:Builder){
  b.box(0f,.017f,0f,.77f,.034f,.49f,.012f,Material(floatArrayOf(.77f,.83f,.89f),.65f))
  b.box(0f,.035f,0f,.72f,.008f,.44f,.003f,Material(floatArrayOf(.89f,.92f,.94f),.8f))
  for(x in listOf(-.32f,.32f))for(z in listOf(-.17f,.17f))b.sphere(x,.008f,z,.038f,.012f,.038f,rubber)
 }
 private fun fire(pinRemoved:Boolean=false,aimX:Float?=null,pressed:Boolean=false):FloatBuffer=Builder().apply{
  base(this)
  // Turned vessel: rounded foot, cylindrical wall, shoulder and neck, not stacked flat cylinders.
  lathe(0f,0f,0f,listOf(.039f to .051f,.044f to .069f,.061f to .082f,.095f to .085f,.365f to .085f,.39f to .080f,.410f to .061f,.424f to .029f,.439f to .026f),red)
  lathe(0f,0f,0f,listOf(.040f to .058f,.046f to .075f,.059f to .077f),rubber)
  cylinder(0f,.439f,0f,.038f,.023f,brass)
  box(0f,.463f,0f,.069f,.038f,.040f,.007f,brass)
  box(.033f,if(pressed).493f else .504f,0f,.138f,.018f,.030f,.005f,red,rz=if(pressed)4f else -8f)
  box(.042f,.480f,0f,.13f,.015f,.032f,.005f,rubber,rz=4f)
  // Pull pin and retaining ring, plus a flexible curved discharge hose.
  if(!pinRemoved){
   rod(floatArrayOf(-.044f,.478f,.015f),floatArrayOf(.03f,.478f,.015f),.003f,metal)
   torus(-.055f,.478f,.015f,.019f,.0028f,metal)
  }
  if(aimX==null){
   tube(listOf(floatArrayOf(.03f,.458f,-.006f),floatArrayOf(.092f,.466f,-.006f),floatArrayOf(.133f,.430f,-.006f),floatArrayOf(.142f,.346f,-.006f),floatArrayOf(.138f,.240f,-.006f),floatArrayOf(.12f,.157f,-.006f)),.008f,rubber)
   rod(floatArrayOf(.12f,.17f,-.006f),floatArrayOf(.12f,.115f,-.006f),.014f,dark)
  }else{
   val nozzle=floatArrayOf(.14f,.23f,.15f);val target=floatArrayOf(aimX,.065f,-.27f)
   val delta=FloatArray(3){target[it]-nozzle[it]};val length=sqrt(delta.sumOf{(it*it).toDouble()}).toFloat()
   val tip=FloatArray(3){nozzle[it]+delta[it]*.075f/length}
   tube(listOf(floatArrayOf(.03f,.458f,-.006f),floatArrayOf(.115f,.456f,.015f),floatArrayOf(.18f,.385f,.10f),floatArrayOf(.18f,.30f,.16f),nozzle),.008f,rubber)
   rod(nozzle,tip,.014f,dark)
   if(pressed){
    // Segmented pale rods are a symbolic discharge path, not particle/fluid physics or range guidance.
    val stream=Material(floatArrayOf(.70f,.88f,.96f),.68f,0f,.12f)
    for(segment in 0..5){
     val a=.10f+segment*.14f;val b=a+.08f
     rod(FloatArray(3){tip[it]+(target[it]-tip[it])*a},FloatArray(3){tip[it]+(target[it]-tip[it])*b},.0025f,stream)
    }
   }
  }
  cylinder(0f,.455f,.044f,.041f,.018f,metal,rx=90f)
  cylinder(0f,.455f,.055f,.034f,.003f,white,rx=90f)
  // Gauge markings are illustrative only; no pressure value is shown.
  for(i in 0..8){val a=(i*25+(-10))*PI/180;val x=cos(a).toFloat()*.013f;val y=sin(a).toFloat()*.013f;box(x,.455f+y,.058f,.0017f,.004f,.001f,0f,dark,rz=(i*25-100).toFloat())}
  rod(floatArrayOf(0f,.455f,.059f),floatArrayOf(-.008f,.464f,.059f),.0012f,red)
  box(0f,.256f,.084f,.112f,.139f,.003f,.004f,white)
  box(0f,.301f,.087f,.094f,.014f,.001f,0f,red)
  for(i in 0..3)box(-.008f,.273f-i*.016f,.087f,.075f-i*.009f,.003f,.001f,0f,dark)
  for(i in -1..1)box(i*.025f,.213f,.087f,.016f,.014f,.001f,.002f,blue)
 }.finish()
 private fun gas(includeBarrier:Boolean=true):FloatBuffer=Builder().apply{
  base(this)
  box(0f,.242f,0f,.254f,.397f,.117f,.035f,rubber)
  box(0f,.242f,.009f,.230f,.370f,.111f,.028f,yellow)
  box(0f,.247f,.064f,.199f,.317f,.009f,.022f,dark)
  box(0f,.301f,.072f,.172f,.146f,.014f,.009f,rubber)
  box(0f,.300f,.081f,.153f,.127f,.006f,.004f,screen)
  // Abstract bars are not live gas values; SIM is explicitly formed on the display.
  simLabel(this,-.054f,.316f,.085f,.015f)
  for(i in 0..2){box(-.018f,.29f-i*.017f,.086f,.081f,.003f,.001f,0f,dark);box(.052f,.29f-i*.017f,.086f,.01f,.006f,.001f,.001f,dark)}
  for(i in -1..1){cylinder(i*.055f,.165f,.074f,.037f,.010f,rubber,rx=90f);cylinder(i*.055f,.165f,.081f,.026f,.004f,if(i==0)blue else white,rx=90f)}
  for(i in -3..3)box(i*.020f,.400f,.061f,.006f,.027f,.005f,.002f,rubber)
  for(x in listOf(-.08f,.08f))for(y in listOf(.10f,.377f))bolt(this,x,y,.076f)
  box(0f,.251f,-.073f,.095f,.239f,.021f,.008f,metal)
  box(0f,.349f,-.082f,.118f,.039f,.012f,.005f,rubber)
  cylinder(-.055f,.455f,0f,.026f,.038f,metal);cylinder(-.055f,.474f,0f,.038f,.009f,rubber)
  if(includeBarrier){
   for(x in listOf(-.295f,.295f)){cylinder(x,.058f,-.04f,.105f,.027f,rubber);cylinder(x,.239f,-.04f,.022f,.345f,yellow);cylinder(x,.406f,-.04f,.029f,.021f,dark)}
   box(0f,.386f,-.064f,.61f,.021f,.012f,.004f,yellow)
  }
 }.finish()
 private fun fireProps(worsening:Boolean):FloatBuffer=Builder().apply{
  box(0f,.012f,-.025f,1.12f,.018f,.72f,.009f,Material(floatArrayOf(.83f,.86f,.88f),.88f))
  // Persistent tray and sculpted flames align with the catalogue's base/sweep targets.
  box(-.02f,.052f,-.27f,.57f,.018f,.15f,.004f,dark)
  val orange=Material(floatArrayOf(.99f,.24f,.025f),.7f,0f,.22f)
  val amber=Material(floatArrayOf(1f,.66f,.045f),.68f,0f,.28f)
  for((index,x) in listOf(-.22f,-.12f,-.01f,.10f,.20f).withIndex()){
   val h=(if(index%2==0).135f else .19f)*(if(worsening)1.45f else 1f)
   lathe(x,.064f,-.27f,listOf(0f to .028f,h*.24f to .037f,h*.67f to .020f,h to .001f),orange,scaleZ=.72f)
   lathe(x,.066f,-.239f,listOf(0f to .013f,h*.32f to .018f,h*.70f to .001f),amber,scaleZ=.5f)
  }
  if(worsening){
   val smoke=Material(floatArrayOf(.24f,.27f,.30f),.96f)
   for(i in 0..2)sphere(-.04f+i*.09f,.35f+i*.065f,-.29f,.14f+i*.018f,.11f,.095f,smoke)
  }
  // Left doorway stays clear; the other doorway is visibly crossed by an obstruction.
  for(x in listOf(-.48f,.46f)){
   for(side in listOf(-1f,1f))box(x+side*.055f,.226f,-.115f,.014f,.35f,.025f,.003f,metal)
   box(x,.40f,-.115f,.124f,.018f,.025f,.003f,metal)
   box(x,.435f,-.115f,.144f,.048f,.017f,.004f,blue)
  }
  rod(floatArrayOf(-.515f,.435f,-.102f),floatArrayOf(-.45f,.435f,-.102f),.003f,white)
  rod(floatArrayOf(-.515f,.435f,-.102f),floatArrayOf(-.494f,.449f,-.102f),.003f,white)
  rod(floatArrayOf(-.515f,.435f,-.102f),floatArrayOf(-.494f,.421f,-.102f),.003f,white)
  rod(floatArrayOf(.405f,.11f,-.087f),floatArrayOf(.515f,.34f,-.087f),.009f,yellow)
  rod(floatArrayOf(.405f,.34f,-.083f),floatArrayOf(.515f,.11f,-.083f),.009f,yellow)
  // Assembly marker remains outside the tray and the illustrative retreat path.
  cylinder(-.23f,.07f,.23f,.14f,.007f,blue)
  for(x in listOf(-.252f,-.23f,-.208f)){sphere(x,.083f,.23f,.011f,.011f,.011f,white);box(x,.075f,.23f,.01f,.01f,.024f,.002f,white)}
 }.finish()
 private fun gasProps(boundary:Boolean,attendant:Boolean):FloatBuffer=Builder().apply{
  box(0f,.012f,-.025f,1.12f,.018f,.72f,.009f,Material(floatArrayOf(.83f,.86f,.88f),.88f))
  // Recessed opening is a generic excluded area, never a measured safe atmosphere.
  box(.265f,.033f,-.205f,.32f,.018f,.22f,.012f,metal)
  box(.265f,.044f,-.205f,.274f,.008f,.172f,.009f,rubber)
  for(x in listOf(-.36f,.46f)){
   cylinder(x,.044f,.105f,.079f,.015f,dark)
   cylinder(x,.18f,.105f,.015f,.265f,metal)
  }
  if(boundary){
   box(.05f,.252f,.105f,.82f,.034f,.015f,.003f,yellow)
   for(i in 0..9)box(-.30f+i*.075f,.252f,.114f,.027f,.031f,.002f,0f,dark,rz=-24f)
  }
  cylinder(-.365f,.036f,.22f,.13f,.009f,blue)
  if(attendant){
   // An original miniature figure marks the outside position; it is not a tracked real worker.
   val x=-.365f;val z=.22f
   rod(floatArrayOf(x-.023f,.045f,z),floatArrayOf(x-.018f,.16f,z),.012f,dark)
   rod(floatArrayOf(x+.023f,.045f,z),floatArrayOf(x+.018f,.16f,z),.012f,dark)
   box(x,.213f,z,.080f,.119f,.052f,.014f,blue)
   sphere(x,.306f,z,.057f,.063f,.053f,Material(floatArrayOf(.67f,.47f,.33f),.9f))
   dome(x,.321f,z,.071f,.040f,.066f,yellow)
   cylinder(x,.32f,z,.079f,.009f,yellow)
   rod(floatArrayOf(x-.045f,.25f,z),floatArrayOf(x-.062f,.17f,z+.02f),.010f,blue)
   rod(floatArrayOf(x+.045f,.25f,z),floatArrayOf(x+.055f,.19f,z+.033f),.010f,blue)
  }
 }.finish()
 private fun machinery():FloatBuffer=Builder().apply{
  base(this)
  for(x in listOf(-.225f,.225f))for(z in listOf(-.12f,.12f)){box(x,.072f,z,.057f,.068f,.058f,.006f,dark);cylinder(x,.043f,z,.065f,.014f,rubber)}
  box(-.025f,.255f,0f,.51f,.315f,.295f,.018f,blue)
  box(-.025f,.433f,-.059f,.51f,.086f,.175f,.018f,Material(floatArrayOf(.37f,.47f,.58f),.31f,.2f))
  // Recessed motor/rollers behind a physically separate guard grid.
  box(-.05f,.258f,.149f,.393f,.205f,.012f,.004f,rubber)
  for(x in listOf(-.155f,.05f)){cylinder(x,.255f,.175f,.134f,.03f,metal,rx=90f);cylinder(x,.255f,.195f,.074f,.013f,dark,rx=90f)}
  for(x in listOf(-.26f,.16f))box(x,.258f,.210f,.023f,.249f,.019f,.003f,yellow)
  for(y in listOf(.141f,.375f))box(-.05f,y,.210f,.443f,.021f,.019f,.003f,yellow)
  for(i in -5..5)rod(floatArrayOf(-.05f+i*.035f,.151f,.212f),floatArrayOf(-.05f+i*.035f,.365f,.212f),.0022f,metal)
  for(i in 0..5)rod(floatArrayOf(-.247f,.166f+i*.035f,.214f),floatArrayOf(.147f,.166f+i*.035f,.214f),.0022f,metal)
  for(x in listOf(-.26f,.16f))for(y in listOf(.145f,.372f))bolt(this,x,y,.224f)
  box(.287f,.308f,.033f,.102f,.266f,.135f,.010f,white)
  box(.287f,.311f,.105f,.08f,.233f,.009f,.004f,dark)
  cylinder(.287f,.369f,.117f,.059f,.018f,yellow,rx=90f);cylinder(.287f,.369f,.134f,.037f,.026f,red,rx=90f)
  cylinder(.287f,.307f,.117f,.026f,.012f,blue,rx=90f)
  box(.287f,.237f,.123f,.046f,.058f,.028f,.005f,blue)
  tube(listOf(floatArrayOf(.273f,.267f,.124f),floatArrayOf(.273f,.286f,.124f),floatArrayOf(.287f,.296f,.124f),floatArrayOf(.301f,.286f,.124f),floatArrayOf(.301f,.267f,.124f)),.003f,metal)
  for(i in 0..7)box(-.284f,.21f+i*.018f,-.027f,.004f,.006f,.14f,.002f,rubber)
  box(-.018f,.409f,.035f,.21f,.003f,.033f,.002f,yellow)
 }.finish()
 private fun ppe():FloatBuffer=Builder().apply{
  base(this)
  box(0f,.052f,-.075f,.23f,.028f,.21f,.011f,dark)
  rod(floatArrayOf(0f,.06f,-.075f),floatArrayOf(0f,.36f,-.075f),.016f,metal)
  // Ellipsoid crown, shaped brim, raised reinforcement ribs, adjustable rear suspension.
  dome(0f,.365f,0f,.422f,.271f,.34f,yellow)
  cylinder(0f,.365f,.017f,.51f,.017f,yellow,scaleZ=.79f)
  tube(listOf(floatArrayOf(0f,.373f,.176f),floatArrayOf(0f,.456f,.119f),floatArrayOf(0f,.493f,0f),floatArrayOf(0f,.456f,-.118f),floatArrayOf(0f,.373f,-.169f)),.009f,yellow)
  for(x in listOf(-.12f,.12f))box(x,.389f,-.125f,.038f,.014f,.015f,.005f,rubber)
  box(0f,.341f,-.162f,.096f,.041f,.025f,.009f,rubber)
  cylinder(0f,.34f,-.18f,.032f,.016f,blue,rx=90f)
  // Separate framed lenses, nose bridge, hinges and side arms.
  for(x in listOf(-.08f,.08f)){box(x,.263f,.163f,.155f,.094f,.034f,.018f,rubber);box(x,.263f,.184f,.131f,.070f,.015f,.015f,glass);box(x-.025f,.279f,.193f,.047f,.006f,.002f,.002f,white,rz=12f)}
  tube(listOf(floatArrayOf(-.016f,.281f,.177f),floatArrayOf(0f,.285f,.181f),floatArrayOf(.016f,.281f,.177f)),.005f,dark)
  for(x in listOf(-.16f,.16f)){rod(floatArrayOf(x,.28f,.162f),floatArrayOf(x,.28f,-.073f),.0045f,dark);bolt(this,x,.28f,.175f)}
  for(x in listOf(-.252f,.252f)){box(x,.245f,-.004f,.063f,.143f,.116f,.026f,rubber);box(x*1.07f,.245f,-.004f,.035f,.119f,.091f,.020f,blue);rod(floatArrayOf(x,.31f,-.004f),floatArrayOf(x*.91f,.405f,-.004f),.005f,metal)}
  tube(listOf(floatArrayOf(-.23f,.395f,-.015f),floatArrayOf(-.18f,.468f,-.015f),floatArrayOf(-.09f,.51f,-.015f),floatArrayOf(.09f,.51f,-.015f),floatArrayOf(.18f,.468f,-.015f),floatArrayOf(.23f,.395f,-.015f)),.011f,rubber)
 }.finish()
 private fun emergency():FloatBuffer=Builder().apply{
  base(this)
  // Generic reporting radio: no transmitted signal or operational display.
  box(-.255f,.178f,.045f,.135f,.257f,.084f,.019f,rubber)
  box(-.255f,.18f,.091f,.113f,.226f,.012f,.011f,yellow)
  box(-.255f,.218f,.100f,.088f,.068f,.008f,.005f,screen)
  simLabel(this,-.287f,.223f,.105f,.009f)
  for(i in 0..4)box(-.255f,.158f-i*.013f,.098f,.087f,.004f,.004f,.002f,dark)
  cylinder(-.28f,.355f,.035f,.013f,.123f,rubber)
  cylinder(-.224f,.316f,.035f,.028f,.035f,dark)
  cylinder(-.255f,.092f,.100f,.018f,.009f,blue,rx=90f)
  for(x in listOf(-.3f,-.21f))for(y in listOf(.072f,.271f))bolt(this,x,y,.099f)
  // Closed first-aid case; contents and treatment skills are not represented.
  val green=Material(floatArrayOf(.045f,.38f,.24f),.46f)
  box(.0f,.149f,.074f,.25f,.197f,.13f,.013f,green)
  box(.0f,.149f,.143f,.235f,.18f,.010f,.008f,green)
  box(0f,.156f,.151f,.075f,.022f,.004f,.002f,white)
  box(0f,.156f,.152f,.023f,.074f,.004f,.002f,white)
  tube(listOf(floatArrayOf(-.051f,.249f,.075f),floatArrayOf(-.051f,.278f,.075f),floatArrayOf(.051f,.278f,.075f),floatArrayOf(.051f,.249f,.075f)),.008f,rubber)
  for(x in listOf(-.086f,.086f)){box(x,.249f,.113f,.034f,.016f,.028f,.003f,metal);box(x,.076f,.01f,.035f,.012f,.016f,.002f,metal)}
  // Muster marker: a generic training symbol, never a real route or validated site location.
  cylinder(.26f,.264f,-.074f,.018f,.45f,metal)
  box(.26f,.401f,-.07f,.201f,.158f,.016f,.005f,white)
  box(.26f,.401f,-.058f,.184f,.141f,.007f,.003f,green)
  for(x in listOf(.232f,.26f,.288f)){sphere(x,.421f,-.05f,.014f,.014f,.005f,white);box(x,.4f,-.05f,.015f,.021f,.004f,.004f,white);rod(floatArrayOf(x-.004f,.39f,-.05f),floatArrayOf(x-.007f,.375f,-.05f),.002f,white);rod(floatArrayOf(x+.004f,.39f,-.05f),floatArrayOf(x+.007f,.375f,-.05f),.002f,white)}
  for(sign in listOf(-1f,1f)){
   val start=.26f+sign*.077f;val end=.26f+sign*.048f
   rod(floatArrayOf(start,.449f,-.05f),floatArrayOf(end,.432f,-.05f),.0027f,white)
   rod(floatArrayOf(end,.432f,-.05f),floatArrayOf(end+sign*.002f,.445f,-.05f),.0027f,white)
   rod(floatArrayOf(end,.432f,-.05f),floatArrayOf(end+sign*.014f,.434f,-.05f),.0027f,white)
  }
 }.finish()
 private fun bolt(b:Builder,x:Float,y:Float,z:Float){b.cylinder(x,y,z,.012f,.004f,metal,rx=90f);b.box(x,y,z+.003f,.007f,.0018f,.001f,0f,dark,rz=25f)}
 private fun simLabel(b:Builder,x:Float,y:Float,z:Float,s:Float){
  // Geometric lettering avoids fonts, texture downloads and misleading measurement digits.
  fun line(x1:Float,y1:Float,x2:Float,y2:Float)=b.rod(floatArrayOf(x+x1*s,y+y1*s,z),floatArrayOf(x+x2*s,y+y2*s,z),s*.08f,dark)
  line(0f,1f,1f,1f);line(0f,1f,0f,.5f);line(0f,.5f,1f,.5f);line(1f,.5f,1f,0f);line(1f,0f,0f,0f)
  line(1.6f,0f,1.6f,1f);line(2.3f,0f,2.3f,1f);line(2.3f,1f,2.9f,.4f);line(2.9f,.4f,3.5f,1f);line(3.5f,1f,3.5f,0f)
 }
 private class Builder{
  private val vertices=ArrayList<Float>()
  private fun emit(p:FloatArray,n:FloatArray,m:Material){vertices.addAll(p.toList());vertices.addAll(n.toList());vertices.addAll(m.color.toList());vertices.add(m.roughness);vertices.add(m.metal);vertices.add(m.emission)}
  private fun norm(a:FloatArray):FloatArray{val l=sqrt(a.sumOf{(it*it).toDouble()}).toFloat().coerceAtLeast(.000001f);return FloatArray(3){a[it]/l}}
  private fun transformed(p:FloatArray,n:FloatArray,x:Float,y:Float,z:Float,rx:Float,rz:Float,m:Material){
   fun rotate(a:FloatArray):FloatArray{val ax=rx*PI/180;val az=rz*PI/180;val py=(a[1]*cos(ax)-a[2]*sin(ax)).toFloat();val pz=(a[1]*sin(ax)+a[2]*cos(ax)).toFloat();return floatArrayOf((a[0]*cos(az)-py*sin(az)).toFloat(),(a[0]*sin(az)+py*cos(az)).toFloat(),pz)}
   val point=rotate(p);point[0]+=x;point[1]+=y;point[2]+=z;emit(point,rotate(n),m)
  }
  fun box(x:Float,y:Float,z:Float,w:Float,h:Float,d:Float,r:Float,m:Material,rx:Float=0f,rz:Float=0f){
   val half=floatArrayOf(w/2,h/2,d/2);val radius=min(r,half.minOrNull()?:0f)*.99f
   for(axis in 0..2)for(sign in listOf(-1f,1f)){
    val u=(axis+1)%3;val v=(axis+2)%3
    fun samples(extent:Float)=if(radius<.00001f)listOf(-extent,extent)else listOf(-extent,-extent+radius*.293f,-extent+radius,extent-radius,extent-radius*.293f,extent)
    val us=samples(half[u]);val vs=samples(half[v])
    fun vertex(i:Int,j:Int){val raw=FloatArray(3);raw[axis]=half[axis]*sign;raw[u]=us[i];raw[v]=vs[j]
     val core=FloatArray(3){raw[it].coerceIn(-half[it]+radius,half[it]-radius)};val n=if(radius==0f)FloatArray(3){if(it==axis)sign else 0f}else norm(FloatArray(3){raw[it]-core[it]});val p=FloatArray(3){if(radius==0f)raw[it]else core[it]+n[it]*radius};transformed(p,n,x,y,z,rx,rz,m)}
    for(i in 0 until us.lastIndex)for(j in 0 until vs.lastIndex){vertex(i,j);vertex(i+1,j);vertex(i+1,j+1);vertex(i,j);vertex(i+1,j+1);vertex(i,j+1)}
   }
  }
  fun lathe(x:Float,y:Float,z:Float,profile:List<Pair<Float,Float>>,m:Material,rx:Float=0f,scaleZ:Float=1f){
   val steps=40
   fun vertex(r:Int,s:Int){val a=s*2*PI/steps;val before=profile[(r-1).coerceAtLeast(0)];val after=profile[(r+1).coerceAtMost(profile.lastIndex)];val dy=after.first-before.first;val dr=after.second-before.second;val p=profile[r]
    transformed(floatArrayOf(cos(a).toFloat()*p.second,p.first,sin(a).toFloat()*p.second*scaleZ),norm(floatArrayOf(cos(a).toFloat()*dy,-dr,sin(a).toFloat()*dy/scaleZ)),x,y,z,rx,0f,m)}
   for(r in 0 until profile.lastIndex)for(s in 0 until steps){vertex(r,s);vertex(r,s+1);vertex(r+1,s+1);vertex(r,s);vertex(r+1,s+1);vertex(r+1,s)}
   for(end in listOf(0,profile.lastIndex))for(s in 0 until steps){val a=s*2*PI/steps;val b=(s+1)*2*PI/steps;val p=profile[end];val n=floatArrayOf(0f,if(end==0)-1f else 1f,0f);for(v in listOf(floatArrayOf(0f,p.first,0f),floatArrayOf(cos(a).toFloat()*p.second,p.first,sin(a).toFloat()*p.second*scaleZ),floatArrayOf(cos(b).toFloat()*p.second,p.first,sin(b).toFloat()*p.second*scaleZ)))transformed(v,n,x,y,z,rx,0f,m)}
  }
  fun cylinder(x:Float,y:Float,z:Float,d:Float,h:Float,m:Material,rx:Float=0f,scaleZ:Float=1f)=lathe(x,y,z,listOf(-h/2 to d*.47f,-h*.35f to d*.5f,h*.35f to d*.5f,h/2 to d*.47f),m,rx,scaleZ)
  fun sphere(x:Float,y:Float,z:Float,w:Float,h:Float,d:Float,m:Material)=ellipsoid(x,y,z,w,h,d,m,false)
  fun dome(x:Float,y:Float,z:Float,w:Float,h:Float,d:Float,m:Material)=ellipsoid(x,y,z,w,h,d,m,true)
  private fun ellipsoid(x:Float,y:Float,z:Float,w:Float,h:Float,d:Float,m:Material,dome:Boolean){
   fun vertex(r:Int,s:Int){val lat=(if(dome)0.0 else -PI/2)+r*(if(dome)PI/2 else PI)/12;val lon=s*2*PI/32;val a=cos(lat).toFloat()*cos(lon).toFloat();val b=sin(lat).toFloat();val c=cos(lat).toFloat()*sin(lon).toFloat();emit(floatArrayOf(x+a*w/2,y+b*h/2,z+c*d/2),norm(floatArrayOf(a/w,b/h,c/d)),m)}
   for(r in 0 until 12)for(s in 0 until 32){vertex(r,s);vertex(r,s+1);vertex(r+1,s+1);vertex(r,s);vertex(r+1,s+1);vertex(r+1,s)}
  }
  fun rod(a:FloatArray,b:FloatArray,r:Float,m:Material)=tube(listOf(a,b),r,m)
  fun tube(path:List<FloatArray>,r:Float,m:Material){
   val n=12
   fun cross(a:FloatArray,b:FloatArray)=floatArrayOf(a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])
   fun vertex(i:Int,s:Int){val before=path[(i-1).coerceAtLeast(0)];val after=path[(i+1).coerceAtMost(path.lastIndex)];val tangent=norm(FloatArray(3){after[it]-before[it]});val reference=if(abs(tangent[2])<.8f)floatArrayOf(0f,0f,1f)else floatArrayOf(0f,1f,0f);val u=norm(cross(tangent,reference));val v=cross(tangent,u);val angle=s*2*PI/n;val normal=FloatArray(3){u[it]*cos(angle).toFloat()+v[it]*sin(angle).toFloat()};emit(FloatArray(3){path[i][it]+normal[it]*r},normal,m)}
   for(i in 0 until path.lastIndex)for(s in 0 until n){vertex(i,s);vertex(i,s+1);vertex(i+1,s+1);vertex(i,s);vertex(i+1,s+1);vertex(i+1,s)}
  }
  fun torus(x:Float,y:Float,z:Float,r:Float,t:Float,m:Material){tube((0..40).map{val a=it*2*PI/40;floatArrayOf(x+cos(a).toFloat()*r,y+sin(a).toFloat()*r,z)},t,m)}
  fun finish():FloatBuffer=ByteBuffer.allocateDirect(vertices.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(vertices.toFloatArray());position(0)}
 }
}
