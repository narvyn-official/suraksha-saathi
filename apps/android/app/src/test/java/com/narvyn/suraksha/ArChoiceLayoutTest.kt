package com.narvyn.suraksha

import org.junit.Assert.*
import org.junit.Test

class ArChoiceLayoutTest {
    private fun p(x: Float,y: Float)=ComponentProjection.Point(x,y)
    @Test fun twoCalloutsDoNotOverlapAndKeepTheirMeasuredHeights() {
        val boxes=ArChoiceLayout.arrange(listOf(p(120f,150f),p(150f,150f)),listOf(90f,120f),2,360,300,10f)!!
        assertEquals(2,boxes.size);assertTrue(boxes[0].left+boxes[0].width<boxes[1].left)
        assertEquals(90f,boxes[0].height);assertEquals(120f,boxes[1].height)
        assertTrue(boxes.all { it.top>=10 && it.top+it.height<=290 })
    }
    @Test fun threeLongChoicesStackWithinTheViewportOrFailClosed() {
        val points=listOf(p(60f,40f),p(150f,40f),p(250f,40f))
        val boxes=ArChoiceLayout.arrange(points,listOf(90f,80f,100f),1,320,310,10f)!!
        assertTrue(boxes.zipWithNext().all { (a,b) -> a.top+a.height<b.top });assertEquals(300f,boxes.last().top+boxes.last().height)
        assertNull(ArChoiceLayout.arrange(points,listOf(140f,140f,140f),1,320,310,10f))
    }
    @Test fun missingOffscreenAndInvalidPointsCannotProduceAnswerTargets() {
        assertNull(ArChoiceLayout.arrange(listOf(null,p(150f,150f)),listOf(80f,80f),2,320,300,10f))
        assertNull(ArChoiceLayout.arrange(listOf(p(-1f,150f)),listOf(80f),1,320,300,10f))
        assertNull(ArChoiceLayout.arrange(listOf(p(100f,100f)),listOf(Float.NaN),1,320,300,10f))
        assertNull(ArChoiceLayout.arrange(listOf(p(100f,100f)),emptyList(),1,320,300,10f))
    }
}
