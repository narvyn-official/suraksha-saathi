package com.narvyn.suraksha

import org.junit.Assert.*
import org.junit.Test

class ArSurfaceProjectionTest {
    private fun identity()=FloatArray(16).apply { this[0]=1f;this[5]=1f;this[10]=1f;this[15]=1f }
    private fun perspective()=FloatArray(16).apply { this[0]=1f;this[5]=1f;this[10]=-11f/9f;this[11]=-1f;this[14]=-20f/9f }
    private fun p(x:Float,y:Float,z:Float=0f)=floatArrayOf(x,y,z)
    private fun square(extent:Float=2f,z:Float=0f)=listOf(p(-extent,-extent,z),p(extent,-extent,z),p(extent,extent,z),p(-extent,extent,z))
    private fun project(points:List<FloatArray>,matrix:FloatArray=identity())=ArSurfaceProjection.project(points,matrix,200,100)
    private fun finiteVisible(points:List<ComponentProjection.Point>) {
        assertTrue(points.size>=3)
        assertTrue(points.all { it.x.isFinite() && it.y.isFinite() && it.x in 0f..200f && it.y in 0f..100f })
    }
    private fun signedArea(points:List<ComponentProjection.Point>):Double=points.indices.sumOf { i ->
        val a=points[i];val b=points[(i+1)%points.size];a.x.toDouble()*b.y-b.x.toDouble()*a.y
    }
    @Test fun polygonWithEveryOriginalVertexOffscreenStillCoversTheViewport() {
        val result=project(square())
        assertEquals(setOf(ComponentProjection.Point(0f,0f),ComponentProjection.Point(200f,0f),ComponentProjection.Point(200f,100f),ComponentProjection.Point(0f,100f)),result.toSet())
        assertEquals(4,result.size)
    }
    @Test fun retainsPartialPolygonsAndTheirBoundaryIntersections() {
        val result=project(listOf(p(-2f,-.5f),p(.5f,-.5f),p(.5f,.5f),p(-2f,.5f)))
        finiteVisible(result);assertEquals(4,result.size)
        assertEquals(0f,result.minOf { it.x },0f);assertEquals(150f,result.maxOf { it.x },0f)
        assertEquals(25f,result.minOf { it.y },0f);assertEquals(75f,result.maxOf { it.y },0f)
    }
    @Test fun columnMajorScaleAndTranslationUseTheSuppliedMvp() {
        val matrix=identity().apply { this[0]=.5f;this[5]=.5f;this[12]=.5f }
        val result=project(square(.5f),matrix)
        assertEquals(125f,result.minOf { it.x },0f);assertEquals(175f,result.maxOf { it.x },0f)
        assertEquals(37.5f,result.minOf { it.y },0f);assertEquals(62.5f,result.maxOf { it.y },0f)
    }
    @Test fun nearPlaneCrossingIsClippedBeforePerspectiveDivision() {
        val result=project(listOf(p(-.5f,-.5f,-.5f),p(.5f,-.5f,-.5f),p(.5f,.5f,-2f),p(-.5f,.5f,-2f)),perspective())
        finiteVisible(result);assertEquals(4,result.size)
        assertEquals(50f,result.minOf { it.x },.001f);assertEquals(150f,result.maxOf { it.x },.001f)
        assertEquals(37.5f,result.minOf { it.y },.001f)
        assertEquals(58.33333f,result.maxOf { it.y },.001f)
    }
    @Test fun polygonCrossingBehindCameraRetainsItsVisibleFrontPortion() {
        val result=project(listOf(p(-.5f,-.5f,1f),p(.5f,-.5f,1f),p(.5f,.5f,-2f),p(-.5f,.5f,-2f)),perspective())
        finiteVisible(result);assertTrue(signedArea(result)!=0.0)
        assertTrue(project(square(.5f,2f),perspective()).isEmpty())
    }
    @Test fun clipsFarPlaneAsWellAsScreenAndNearPlanes() {
        val result=project(listOf(p(-.5f,-.5f,0f),p(.5f,-.5f,0f),p(.5f,.5f,2f),p(-.5f,.5f,2f)))
        finiteVisible(result);assertEquals(4,result.size)
        assertEquals(50f,result.minOf { it.y },0f);assertEquals(75f,result.maxOf { it.y },0f)
        assertTrue(project(square(.5f,2f)).isEmpty())
        assertTrue(project(square(.5f,-2f)).isEmpty())
        assertTrue(project(square(.5f,-11f),perspective()).isEmpty())
    }
    @Test fun fullyOffscreenAndEdgeOnlyPolygonsAreEmpty() {
        assertTrue(project(listOf(p(2f,-.5f),p(3f,-.5f),p(3f,.5f),p(2f,.5f))).isEmpty())
        assertTrue(project(listOf(p(1f,-.5f),p(2f,-.5f),p(2f,.5f),p(1f,.5f))).isEmpty())
        assertTrue(project(listOf(p(-.5f,0f),p(0f,0f),p(.5f,0f))).isEmpty())
    }
    @Test fun repeatedClosingVerticesAreRemovedAndInputIsNotMutated() {
        val original=square(.5f);val points=original+listOf(original.last(),original.first())
        val saved=points.map { it.copyOf() };val matrix=identity();val savedMatrix=matrix.copyOf()
        assertEquals(4,project(points,matrix).size)
        points.indices.forEach { assertArrayEquals(saved[it],points[it],0f) };assertArrayEquals(savedMatrix,matrix,0f)
        val forward=project(square());val backward=project(square().reversed())
        assertEquals(forward.toSet(),backward.toSet());assertEquals(-signedArea(forward),signedArea(backward),.001)
    }
    @Test fun invalidNonfiniteAndZeroWInputsAreRejected() {
        assertTrue(project(emptyList()).isEmpty());assertTrue(project(square().take(2)).isEmpty())
        assertTrue(project(listOf(floatArrayOf(0f,0f),p(1f,0f),p(0f,1f))).isEmpty())
        for(invalid in listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY)) {
            assertTrue(project(listOf(p(invalid,0f),p(1f,0f),p(0f,1f))).isEmpty())
            assertTrue(project(square(),identity().apply { this[7]=invalid }).isEmpty())
        }
        assertTrue(project(square(),FloatArray(16)).isEmpty())
        assertTrue(project(square(),identity().apply { this[15]=0f }).isEmpty())
        assertTrue(project(square(),FloatArray(15)).isEmpty())
        assertTrue(ArSurfaceProjection.project(square(),identity(),0,100).isEmpty())
        assertTrue(ArSurfaceProjection.project(square(),identity(),200,-1).isEmpty())
    }
    @Test fun homogeneousScaleDoesNotChangeTheClippedPolygonOrOverflowFloats() {
        val expected=project(square())
        for(scale in listOf(1e-25f,Float.MAX_VALUE)) {
            val matrix=identity().map { it*scale }.toFloatArray()
            val actual=project(square(),matrix);finiteVisible(actual)
            assertEquals(expected.toSet(),actual.toSet())
        }
    }
}
