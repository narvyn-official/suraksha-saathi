package com.narvyn.suraksha

/** A screen target, not an anchor. Changing it invalidates eligibility from older camera frames. */
class ArPlacementCursor {
    data class Snapshot(val serial:Long,val x:Float,val y:Float)
    private var serial=0L
    private var x=.5f;private var y=.5f
    @Synchronized fun select(px:Float,py:Float,width:Int,height:Int):Boolean {
        if(width<=0||height<=0||!px.isFinite()||!py.isFinite()||px<0||py<0||px>=width||py>=height)return false
        x=px/width;y=py/height;serial++;return true
    }
    @Synchronized fun reset(){x=.5f;y=.5f;serial++}
    @Synchronized fun snapshot(width:Int,height:Int)=Snapshot(serial,x*width,y*height)
    @Synchronized fun isCurrent(value:Long)=value==serial
    @Synchronized fun withCurrent(value:Long,action:()->Unit):Boolean {
        if(value!=serial)return false
        action();return true
    }
}
