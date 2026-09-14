package com.narvyn.suraksha

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

object Palette {
    val canvas=Color.rgb(246,247,251); val surface=Color.WHITE; val ink=Color.rgb(20,38,61)
    val muted=Color.rgb(85,101,123); val blue=Color.rgb(49,87,213); val soft=Color.rgb(233,238,255)
    val line=Color.rgb(215,223,234); val success=Color.rgb(19,102,77); val successBg=Color.rgb(230,244,238)
    val danger=Color.rgb(172,52,59); val dangerBg=Color.rgb(253,236,238); val amber=Color.rgb(128,82,11); val amberBg=Color.rgb(255,243,220)
}
fun Context.dp(n: Int)= (n*resources.displayMetrics.density).toInt()
fun Context.shape(colour: Int, radius: Int=16, stroke: Int?=null)=GradientDrawable().apply { setColor(colour);cornerRadius=dp(radius).toFloat();stroke?.let{setStroke(dp(1),it)} }
fun Context.label(value: String,size: Float=16f,colour: Int=Palette.ink,bold: Boolean=false)=TextView(this).apply {
    text=value;textSize=size;setTextColor(colour);typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)
    setLineSpacing(dp(3).toFloat(),1f);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
}
fun Context.column(padding: Int=0)=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(dp(padding),dp(padding),dp(padding),dp(padding)) }
fun LinearLayout.add(view: View,top: Int=0,bottom: Int=0) { addView(view,LinearLayout.LayoutParams(-1,-2).apply { topMargin=context.dp(top);bottomMargin=context.dp(bottom) }) }
fun Context.card(colour: Int=Palette.surface)=column(20).apply {background=shape(colour,20,if(colour==Palette.surface) Palette.line else null)}
fun Context.action(text: String,primary: Boolean=true,onClick: ()->Unit)=Button(this).apply {
    stateListAnimator=null;elevation=0f;this.text=text;isAllCaps=false;textSize=16f;setTextColor(if(primary)Color.WHITE else Palette.ink)
    background=shape(if(primary)Palette.blue else Palette.surface,14,if(primary)null else Palette.line)
    minHeight=dp(56);minimumHeight=dp(56);setPadding(dp(16),dp(12),dp(16),dp(12));setOnClickListener{onClick()}
}
fun Activity.notice(title: String,message: String) { android.app.AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK",null).show() }

/** Explicit structure for screen-reader heading navigation; bold text alone has no heading semantics. */
fun TextView.asHeading()=apply { isAccessibilityHeading=true }
