package com.narvyn.suraksha

/** Allocate from the actual window height, including short windows; never reserve two fixed tall panels. */
object CameraWorkspaceLayout {
    data class Budget(val header: Int, val feedback: Int)
    fun budget(height:Int,density:Float):Budget {
        val h=height.coerceAtLeast(0)
        val d=if(density.isFinite() && density>0)density else 1f
        return Budget(minOf((150*d).toInt(),(h*.20f).toInt()),minOf((175*d).toInt(),(h*.22f).toInt()))
    }
    fun controls(height:Int,density:Float):Int = minOf((80*(if(density.isFinite() && density>0)density else 1f)).toInt(),(height.coerceAtLeast(0)*.32f).toInt())
}
