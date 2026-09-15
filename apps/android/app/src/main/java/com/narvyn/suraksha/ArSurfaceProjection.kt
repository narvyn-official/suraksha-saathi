package com.narvyn.suraksha

import kotlin.math.abs

/** Clips an ordered convex plane boundary before projecting it. This is screen visibility, not occlusion
 * or hazard detection. Input points are XYZ; matrix is a column-major MVP, using the OpenGL clip volume.
 */
object ArSurfaceProjection {
    private data class Vertex(val x:Double,val y:Double,val z:Double,val w:Double) {
        fun distance(plane:Int):Double=when(plane) {
            0->w+x;1->w-x;2->w+y;3->w-y;4->w+z;else->w-z
        }
        fun between(other:Vertex,t:Double)=Vertex(x+(other.x-x)*t,y+(other.y-y)*t,z+(other.z-z)*t,w+(other.w-w)*t)
        fun finite()=x.isFinite() && y.isFinite() && z.isFinite() && w.isFinite()
    }

    fun project(points:List<FloatArray>,matrix:FloatArray,width:Int,height:Int):List<ComponentProjection.Point> {
        if(points.size<3 || matrix.size!=16 || width<=0 || height<=0 || matrix.any { !it.isFinite() })return emptyList()
        if(points.any { it.size!=3 || it.any { coordinate->!coordinate.isFinite() } })return emptyList()
        // Double intermediates avoid Float overflow and cancellation while intersecting large AR surfaces.
        var polygon=points.map { point ->
            fun row(index:Int)=matrix[index].toDouble()*point[0]+matrix[index+4].toDouble()*point[1]+matrix[index+8].toDouble()*point[2]+matrix[index+12]
            Vertex(row(0),row(1),row(2),row(3))
        }
        if(polygon.any { !it.finite() })return emptyList()
        for(plane in 0..5) {
            val clipped=ArrayList<Vertex>(polygon.size+2)
            var previous=polygon.last()
            var previousDistance=previous.distance(plane)
            for(current in polygon) {
                val currentDistance=current.distance(plane)
                val previousInside=previousDistance>=0.0
                val currentInside=currentDistance>=0.0
                if(previousInside!=currentInside) {
                    val denominator=previousDistance-currentDistance
                    if(denominator==0.0 || !denominator.isFinite())return emptyList()
                    val fraction=(previousDistance/denominator).coerceIn(0.0,1.0)
                    val intersection=previous.between(current,fraction)
                    if(!intersection.finite())return emptyList()
                    clipped.add(intersection)
                }
                if(currentInside)clipped.add(current)
                previous=current;previousDistance=currentDistance
            }
            if(clipped.size<3)return emptyList()
            polygon=clipped
        }
        val projected=ArrayList<ComponentProjection.Point>(polygon.size)
        for(vertex in polygon) {
            // A clip-space origin cannot be perspective-divided. Never replace zero w with a fake depth.
            if(vertex.w<=0.0)return emptyList()
            val x=vertex.x/vertex.w;val y=vertex.y/vertex.w;val z=vertex.z/vertex.w
            if(!x.isFinite() || !y.isFinite() || !z.isFinite())return emptyList()
            // Intersections can overshoot a boundary by a few rounding bits after the six clipping passes.
            val point=ComponentProjection.Point(((x.coerceIn(-1.0,1.0)+1.0)*width/2.0).toFloat(),((1.0-y.coerceIn(-1.0,1.0))*height/2.0).toFloat())
            if(!point.x.isFinite() || !point.y.isFinite())return emptyList()
            if(projected.lastOrNull()!=point)projected.add(point)
        }
        if(projected.size>1 && projected.first()==projected.last())projected.removeAt(projected.lastIndex)
        if(projected.size<3)return emptyList()
        // Normalize for a viewport-independent degeneracy test; a boundary touching only an edge is not an area.
        var twiceArea=0.0
        for(i in projected.indices) {
            val a=projected[i];val b=projected[(i+1)%projected.size]
            twiceArea+=(a.x.toDouble()/width)*(b.y.toDouble()/height)-(b.x.toDouble()/width)*(a.y.toDouble()/height)
        }
        return if(abs(twiceArea)<=1e-12)emptyList()else projected
    }
}
