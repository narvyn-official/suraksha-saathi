package com.narvyn.suraksha

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.*

/** Low-poly original training objects. Coordinates are metres in the AR anchor frame. */
class WorldEquipment{
 private var program=0
 private val cube=meshCube()
 private val cylinder=meshCylinder()
 private val matrix=FloatArray(16);private val transform=FloatArray(16);private val mvp=FloatArray(16)
 fun create(){
  fun compile(type:Int,source:String):Int {val shader=GLES20.glCreateShader(type);GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader);val ok=IntArray(1);GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,ok,0);check(ok[0]!=0){GLES20.glGetShaderInfoLog(shader)};return shader}
  val v=compile(GLES20.GL_VERTEX_SHADER,"uniform mat4 mvp;attribute vec3 position;attribute float shade;varying float light;void main(){gl_Position=mvp*vec4(position,1.0);light=shade;}")
  val f=compile(GLES20.GL_FRAGMENT_SHADER,"precision mediump float;uniform vec3 color;varying float light;void main(){gl_FragColor=vec4(color*light,1.0);}")
  program=GLES20.glCreateProgram();GLES20.glAttachShader(program,v);GLES20.glAttachShader(program,f);GLES20.glLinkProgram(program);GLES20.glDeleteShader(v);GLES20.glDeleteShader(f)
  val ok=IntArray(1);GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);check(ok[0]!=0){GLES20.glGetProgramInfoLog(program)}
 }
 fun draw(vp:FloatArray,anchor:FloatArray,module:String){
  if(program==0)return
  GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);GLES20.glUseProgram(program)
  fun item(x:Float,y:Float,z:Float,w:Float,h:Float,d:Float,r:Float,g:Float,b:Float,round:Boolean=false){
   Matrix.setIdentityM(transform,0);Matrix.translateM(transform,0,x,y,z);Matrix.scaleM(transform,0,w,h,d);Matrix.multiplyMM(matrix,0,anchor,0,transform,0);Matrix.multiplyMM(mvp,0,vp,0,matrix,0)
   GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"mvp"),1,false,mvp,0);GLES20.glUniform3f(GLES20.glGetUniformLocation(program,"color"),r,g,b)
   val mesh=if(round)cylinder else cube;val p=GLES20.glGetAttribLocation(program,"position");val light=GLES20.glGetAttribLocation(program,"shade")
   mesh.position(0);GLES20.glVertexAttribPointer(p,3,GLES20.GL_FLOAT,false,16,mesh);mesh.position(3);GLES20.glVertexAttribPointer(light,1,GLES20.GL_FLOAT,false,16,mesh);GLES20.glEnableVertexAttribArray(p);GLES20.glEnableVertexAttribArray(light);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,mesh.capacity()/4);GLES20.glDisableVertexAttribArray(p);GLES20.glDisableVertexAttribArray(light)
  }
  // Common training plinth visually distinguishes simulated equipment from surroundings.
  item(0f,.015f,0f,.8f,.03f,.5f,.75f,.84f,.96f)
  when(module){
   "fire"->{item(0f,.23f,0f,.16f,.40f,.16f,.8f,.16f,.22f,true);item(0f,.28f,.083f,.12f,.12f,.012f,.97f,.97f,.98f);item(0f,.46f,0f,.08f,.05f,.07f,.17f,.22f,.29f);item(.03f,.505f,0f,.16f,.025f,.03f,.17f,.22f,.29f);item(.13f,.32f,0f,.025f,.31f,.025f,.17f,.22f,.29f);item(.07f,.46f,0f,.12f,.025f,.025f,.17f,.22f,.29f)}
   "gas"->{item(0f,.24f,0f,.25f,.42f,.12f,.15f,.21f,.29f);item(0f,.30f,.067f,.195f,.19f,.016f,.63f,.87f,.75f);item(-.058f,.14f,.071f,.043f,.043f,.018f,.21f,.38f,.87f);item(.058f,.14f,.071f,.043f,.043f,.018f,.94f,.95f,.96f);item(-.03f,.48f,0f,.025f,.07f,.025f,.15f,.21f,.29f);item(-.30f,.23f,0f,.025f,.4f,.025f,.9f,.59f,.13f);item(.30f,.23f,0f,.025f,.4f,.025f,.9f,.59f,.13f);item(0f,.40f,-.12f,.63f,.035f,.025f,.9f,.59f,.13f)}
   else->{item(0f,.20f,0f,.54f,.34f,.30f,.29f,.42f,.60f);item(0f,.44f,-.08f,.54f,.14f,.15f,.58f,.69f,.82f);item(0f,.22f,.164f,.38f,.20f,.022f,.71f,.86f,.91f);for(i in -2..2)item(i*.07f,.22f,.18f,.012f,.20f,.012f,.20f,.34f,.65f);item(.31f,.31f,0f,.075f,.28f,.14f,.9f,.93f,.97f);item(.35f,.35f,.08f,.054f,.065f,.022f,.80f,.18f,.23f);item(.35f,.24f,.08f,.054f,.065f,.022f,.2f,.39f,.84f)}
  }
  GLES20.glDisable(GLES20.GL_DEPTH_TEST)
 }
 private fun buffer(values:List<Float>):FloatBuffer=ByteBuffer.allocateDirect(values.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(values.toFloatArray());position(0)}
 private fun meshCube():FloatBuffer{
  val faces=listOf(floatArrayOf(-.5f,-.5f,.5f,.5f,-.5f,.5f,.5f,.5f,.5f,-.5f,.5f,.5f),floatArrayOf(.5f,-.5f,-.5f,-.5f,-.5f,-.5f,-.5f,.5f,-.5f,.5f,.5f,-.5f),floatArrayOf(-.5f,-.5f,-.5f,-.5f,-.5f,.5f,-.5f,.5f,.5f,-.5f,.5f,-.5f),floatArrayOf(.5f,-.5f,.5f,.5f,-.5f,-.5f,.5f,.5f,-.5f,.5f,.5f,.5f),floatArrayOf(-.5f,.5f,.5f,.5f,.5f,.5f,.5f,.5f,-.5f,-.5f,.5f,-.5f),floatArrayOf(-.5f,-.5f,-.5f,.5f,-.5f,-.5f,.5f,-.5f,.5f,-.5f,-.5f,.5f))
  return buffer(buildList{faces.forEachIndexed{i,f->for(j in listOf(0,1,2,0,2,3)){add(f[j*3]);add(f[j*3+1]);add(f[j*3+2]);add(listOf(.92f,.7f,.78f,.84f,1f,.65f)[i])}}})
 }
 private fun meshCylinder():FloatBuffer=buffer(buildList{
  fun vertex(x:Float,y:Float,z:Float,l:Float){add(x);add(y);add(z);add(l)}
  for(i in 0 until 16){val a=i*2*PI/16;val b=(i+1)*2*PI/16;val x1=cos(a).toFloat()*.5f;val z1=sin(a).toFloat()*.5f;val x2=cos(b).toFloat()*.5f;val z2=sin(b).toFloat()*.5f;val light=.78f+.18f*sin(a).toFloat()
   vertex(x1,-.5f,z1,light);vertex(x2,-.5f,z2,light);vertex(x2,.5f,z2,light);vertex(x1,-.5f,z1,light);vertex(x2,.5f,z2,light);vertex(x1,.5f,z1,light)
   vertex(0f,.5f,0f,1f);vertex(x1,.5f,z1,1f);vertex(x2,.5f,z2,1f);vertex(0f,-.5f,0f,.65f);vertex(x2,-.5f,z2,.65f);vertex(x1,-.5f,z1,.65f)
  }
 })
}
