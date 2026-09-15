package com.narvyn.suraksha

import android.app.Activity
import android.content.Context
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
    val canvas=Color.rgb(244,248,249); val surface=Color.WHITE; val ink=Color.rgb(20,38,61)
    val muted=Color.rgb(85,101,123); val blue=Color.rgb(49,87,213); val soft=Color.rgb(233,238,255)
    val line=Color.rgb(215,223,234); val success=Color.rgb(19,102,77); val successBg=Color.rgb(230,244,238)
    val danger=Color.rgb(172,52,59); val dangerBg=Color.rgb(253,236,238); val amber=Color.rgb(128,82,11); val amberBg=Color.rgb(255,243,220)
    val teal=Color.rgb(0,105,99); val tealBg=Color.rgb(222,244,239)
    val violet=Color.rgb(101,65,168); val violetBg=Color.rgb(241,234,255)
}
/** Roles describe the action, never the correctness of an unanswered choice. */
enum class ActionRole { PRIMARY, LEARN, CAMERA, REVIEW, NEUTRAL, DANGER }
fun Context.dp(n: Int)= (n*resources.displayMetrics.density).toInt()
fun Context.shape(colour: Int, radius: Int=16, stroke: Int?=null)=GradientDrawable().apply { setColor(colour);cornerRadius=dp(radius).toFloat();stroke?.let{setStroke(dp(1),it)} }
fun Context.label(value: String,size: Float=16f,colour: Int=Palette.ink,bold: Boolean=false)=TextView(this).apply {
    text=value;textSize=size;setTextColor(colour);typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)
    setLineSpacing(dp(3).toFloat(),1f);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
}
fun Context.column(padding: Int=0)=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(padding),dp(padding),dp(padding),dp(padding)) }
fun LinearLayout.add(view: View,top: Int=0,bottom: Int=0) { addView(view,LinearLayout.LayoutParams(-1,-2).apply { topMargin=context.dp(top);bottomMargin=context.dp(bottom) }) }
fun Context.card(colour: Int=Palette.surface)=column(20).apply {background=shape(colour,20,if(colour==Palette.surface) Palette.line else null)}
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
    minHeight=dp(56);minimumHeight=dp(56);setPadding(dp(16),dp(12),dp(16),dp(12));setOnClickListener{performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);onClick()}
}
fun Activity.notice(title: String,message: String) { android.app.AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK",null).show() }

/** Explicit structure for screen-reader heading navigation; bold text alone has no heading semantics. */
fun TextView.asHeading()=apply { isAccessibilityHeading=true }
