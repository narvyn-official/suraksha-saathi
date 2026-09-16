package com.narvyn.suraksha

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.*

object Palette {
    val canvas=Color.rgb(246,248,249); val surface=Color.WHITE; val ink=Color.rgb(24,44,56)
    val muted=Color.rgb(87,105,115); val blue=Color.rgb(47,83,164); val soft=Color.rgb(237,241,251)
    val line=Color.rgb(224,231,234); val success=Color.rgb(19,102,77); val successBg=Color.rgb(230,244,238)
    val danger=Color.rgb(172,52,59); val dangerBg=Color.rgb(253,236,238); val amber=Color.rgb(128,82,11); val amberBg=Color.rgb(255,243,220)
    val teal=Color.rgb(8,111,98); val tealBg=Color.rgb(228,244,239)
    val violet=Color.rgb(101,65,168); val violetBg=Color.rgb(241,234,255)
}
/** Roles describe the action, never the correctness of an unanswered choice. */
enum class ActionRole { PRIMARY, LEARN, CAMERA, REVIEW, NEUTRAL, DANGER }
fun Context.dp(n: Int)= (n*resources.displayMetrics.density).toInt()
fun Context.shape(colour: Int, radius: Int=16, stroke: Int?=null)=GradientDrawable().apply { setColor(colour);cornerRadius=dp(radius).toFloat();stroke?.let{setStroke(dp(1),it)} }
fun Context.label(value: String,size: Float=16f,colour: Int=Palette.ink,bold: Boolean=false)=TextView(this).apply {
    text=value;textSize=size;setTextColor(colour);typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)
    setLineSpacing(dp(2).toFloat(),1f);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
}
fun Context.column(padding: Int=0)=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(padding),dp(padding),dp(padding),dp(padding)) }
fun LinearLayout.add(view: View,top: Int=0,bottom: Int=0) { addView(view,LinearLayout.LayoutParams(-1,-2).apply { topMargin=context.dp(top);bottomMargin=context.dp(bottom) }) }
fun Context.card(colour: Int=Palette.surface)=column(18).apply {background=shape(colour,18,if(colour==Palette.surface) Palette.line else null)}
fun Button.actionRole(role: ActionRole)=apply {
    val (fill,ink)=when(role) {
        ActionRole.PRIMARY -> Palette.teal to Color.WHITE
        ActionRole.LEARN -> Palette.soft to Palette.blue
        ActionRole.CAMERA -> Palette.violetBg to Palette.violet
        ActionRole.REVIEW -> Palette.amberBg to Palette.amber
        ActionRole.NEUTRAL -> 0xffeaf0f3.toInt() to Palette.ink
        ActionRole.DANGER -> Palette.dangerBg to Palette.danger
    }
    setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled),intArrayOf()),intArrayOf(Palette.muted,ink)))
    val states=StateListDrawable().apply {
        addState(intArrayOf(-android.R.attr.state_enabled),context.shape(0xffe4eaed.toInt(),14,Palette.line))
        addState(intArrayOf(android.R.attr.state_focused),context.shape(fill,14,ink))
        addState(intArrayOf(),context.shape(fill,14))
    }
    states.state=drawableState
    backgroundTintList=null
    background=RippleDrawable(ColorStateList.valueOf(if(role==ActionRole.PRIMARY)0x40ffffff else 0x200e3551),states,context.shape(Color.WHITE,14))
    background.state=drawableState
    refreshDrawableState()
}
fun Context.action(text: String,primary: Boolean=true,role: ActionRole=if(primary)ActionRole.PRIMARY else ActionRole.LEARN,onClick: ()->Unit)=Button(this).apply {
    stateListAnimator=null;elevation=0f;this.text=text;isAllCaps=false;textSize=16f;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
    actionRole(role)
    minHeight=dp(52);minimumHeight=dp(52);setPadding(dp(16),dp(12),dp(16),dp(12));setOnClickListener{performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);onClick()}
}
fun Activity.notice(title: String,message: String) { android.app.AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK",null).show() }

/** Explicit structure for screen-reader heading navigation; bold text alone has no heading semantics. */
fun TextView.asHeading()=apply { isAccessibilityHeading=true }

/** Small original vector symbols: no font glyph dependencies, network images or missing icon fonts. */
class AppIcon(private val kind:String,private val ink:Int):Drawable() {
    private val pen=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=ink;style=Paint.Style.STROKE;strokeWidth=1.7f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND }
    override fun draw(canvas:Canvas) {
        val checkpoint=canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/24f,bounds.height()/24f)
        fun line(x:Float,y:Float,x2:Float,y2:Float)=canvas.drawLine(x,y,x2,y2,pen)
        fun path(vararg points:Float) { val p=Path();p.moveTo(points[0],points[1]);for(i in 2 until points.size step 2)p.lineTo(points[i],points[i+1]);canvas.drawPath(p,pen) }
        when(kind) {
            "learn" -> { canvas.drawRoundRect(RectF(3f,4f,21f,20f),2f,2f,pen);line(12f,4f,12f,20f);line(6f,8f,9f,8f);line(15f,8f,18f,8f);line(6f,12f,9f,12f);line(15f,12f,18f,12f) }
            "records","shield" -> { path(12f,2.5f,20f,5.5f,19f,14f,16f,18f,12f,21f,8f,18f,5f,14f,4f,5.5f,12f,2.5f);path(8f,11.5f,11f,14.5f,16f,9.5f) }
            "help" -> { canvas.drawCircle(12f,12f,9f,pen);canvas.drawCircle(12f,12f,4f,pen);line(6f,6f,9f,9f);line(15f,15f,18f,18f);line(6f,18f,9f,15f);line(15f,9f,18f,6f) }
            "user" -> { canvas.drawCircle(12f,8f,4f,pen);canvas.drawArc(RectF(4f,13f,20f,26f),180f,180f,false,pen) }
            "fire" -> { val p=Path();p.moveTo(13f,2f);p.cubicTo(5f,7f,8f,10f,5f,11f);p.cubicTo(1f,22f,20f,25f,20f,14f);p.cubicTo(20f,10f,17f,8f,17f,8f);p.lineTo(15f,13f);p.cubicTo(10f,10f,14f,6f,13f,2f);canvas.drawPath(p,pen) }
            "gas" -> { canvas.drawRoundRect(RectF(5f,3f,19f,22f),3f,3f,pen);canvas.drawRoundRect(RectF(8f,6f,16f,12f),1f,1f,pen);canvas.drawCircle(9f,16f,1f,pen);canvas.drawCircle(15f,16f,1f,pen);line(9f,20f,15f,20f) }
            "machinery" -> { canvas.drawRoundRect(RectF(3f,8f,21f,21f),2f,2f,pen);canvas.drawCircle(10f,14f,3f,pen);path(6f,8f,6f,3f,15f,3f,15f,8f);line(17f,12f,18f,12f);line(17f,16f,18f,16f) }
            "ppe" -> { canvas.drawArc(RectF(4f,5f,20f,21f),180f,180f,false,pen);path(3f,13f,3f,17f,21f,17f,21f,13f);line(12f,4f,12f,11f) }
            "emergency" -> { canvas.drawRoundRect(RectF(4f,5f,20f,20f),3f,3f,pen);line(9f,2f,15f,2f);line(12f,9f,12f,16f);line(8.5f,12.5f,15.5f,12.5f) }
            "camera" -> { canvas.drawRoundRect(RectF(2f,6f,22f,21f),3f,3f,pen);canvas.drawCircle(12f,13f,4f,pen);path(7f,6f,9f,3f,15f,3f,17f,6f) }
            "admin" -> { canvas.drawRoundRect(RectF(3f,3f,21f,21f),3f,3f,pen);line(3f,9f,21f,9f);line(10f,9f,10f,21f);line(14f,13f,17f,13f);line(14f,17f,17f,17f) }
            "down" -> path(6f,9f,12f,15f,18f,9f)
            else -> path(9f,5f,16f,12f,9f,19f)
        }
        canvas.restoreToCount(checkpoint)
    }
    override fun setAlpha(alpha:Int) { pen.alpha=alpha;invalidateSelf() }
    override fun setColorFilter(filter:android.graphics.ColorFilter?) { pen.colorFilter=filter;invalidateSelf() }
    @Suppress("DEPRECATION") override fun getOpacity()=android.graphics.PixelFormat.TRANSLUCENT
}
fun Context.appIcon(kind:String,ink:Int=Palette.teal,size:Int=24)=AppIcon(kind,ink).apply { setBounds(0,0,dp(size),dp(size)) }
