package com.narvyn.suraksha

/** Readable screen-space callouts attached to projected AR stations; never mesh hit testing. */
object ArChoiceLayout {
    data class Box(val left: Float, val top: Float, val width: Float, val height: Float)
    fun arrange(points: List<ComponentProjection.Point?>, heights: List<Float>, columns: Int, width: Int, height: Int, gap: Float): List<Box>? {
        if(points.isEmpty() || points.size!=heights.size || columns !in 1..points.size || width<=0 || height<=0 || !gap.isFinite() || gap<0) return null
        if(points.any { it==null || !it.x.isFinite() || !it.y.isFinite() || it.x !in gap..(width-gap) || it.y !in gap..(height-gap) } || heights.any { !it.isFinite() || it<=0 }) return null
        val cardWidth=(width-gap*(columns+1))/columns
        if(cardWidth<=0) return null
        val rows=(points.size+columns-1)/columns
        val rowHeights=(0 until rows).map { row -> heights.drop(row*columns).take(columns).max() }
        val total=rowHeights.sum()+gap*(rows-1)
        if(total>height-2*gap) return null
        var top=(points.map { it!!.y }.average().toFloat()-total/2).coerceIn(gap,height-gap-total)
        val boxes=mutableListOf<Box>()
        for(row in 0 until rows) {
            for(col in 0 until columns) { val index=row*columns+col; if(index<points.size) boxes.add(Box(gap+col*(cardWidth+gap),top,cardWidth,heights[index])) }
            top+=rowHeights[row]+gap
        }
        return boxes
    }
}
