package com.narvyn.suraksha

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/** Visual centre cue only. Native placement buttons supply the accessible action and label. */
object PlacementAim {
    fun draw(canvas: Canvas, paint: Paint, context: Context, width: Int, height: Int) {
        val x=width/2f;val y=height/2f;val reach=context.dp(10).toFloat()
        for(stroke in 5 downTo 2 step 3) {
            paint.color=if(stroke==5)Color.WHITE else Palette.blue;paint.strokeWidth=context.dp(stroke).toFloat()
            canvas.drawLine(x-reach,y,x+reach,y,paint);canvas.drawLine(x,y-reach,x,y+reach,paint)
        }
    }
}
