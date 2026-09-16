package com.narvyn.suraksha

import org.junit.Assert.*
import org.junit.Test

class RoomPlacementAreaTest {
    @Test fun rangeHeightAndAnchorPatchCoverageAreAllRequired() {
        assertEquals(RoomPlacementArea.Reason.READY, RoomPlacementArea.evaluate(.6f, .2f, true))
        assertEquals(RoomPlacementArea.Reason.READY, RoomPlacementArea.evaluate(3f, -.2f, true))
        for(distance in listOf(.59f,3.01f,Float.NaN,Float.POSITIVE_INFINITY))
            assertEquals(RoomPlacementArea.Reason.OUT_OF_RANGE,RoomPlacementArea.evaluate(distance,0f,true))
        for(height in listOf(.201f,-.201f,Float.NaN))
            assertEquals(RoomPlacementArea.Reason.DIFFERENT_LEVEL,RoomPlacementArea.evaluate(1f,height,true))
        assertEquals(RoomPlacementArea.Reason.INCOMPLETE_SURFACE,RoomPlacementArea.evaluate(1f,0f,false))
    }
    @Test fun smallMappedPatchCanSupportAnAnchorWithoutClaimingTheFullModelIsMapped(){
        val patch=RoomPlacementArea.anchorPatch()
        assertTrue(patch.any{it[0]==0f&&it[2]==0f})
        fun mapped(p:FloatArray)=kotlin.math.abs(p[0])<=.15f&&kotlin.math.abs(p[2])<=.15f
        assertTrue(patch.all(::mapped))
        assertFalse(RoomPlacementArea.outline("fire",0).all(::mapped))
        assertEquals(RoomPlacementArea.Reason.READY,RoomPlacementArea.evaluate(1f,0f,patch.all(::mapped)))
        val nearEdge=patch.map{floatArrayOf(it[0]+.13f,it[1],it[2])}.all(::mapped)
        assertFalse(nearEdge)
        assertEquals(RoomPlacementArea.Reason.INCOMPLETE_SURFACE,RoomPlacementArea.evaluate(1f,0f,nearEdge))
    }
    @Test fun outlinesCoverTheEquipmentBaseFireAndRearGasOpening() {
        val equipment=RoomPlacementArea.outline("fire",0)
        assertTrue(equipment.minOf{it[0]}<=-.385f && equipment.maxOf{it[0]}>=.385f)
        assertTrue(equipment.minOf{it[2]}<=-.245f && equipment.maxOf{it[2]}>=.245f)
        val gas=RoomPlacementArea.outline("gas",1)
        assertTrue(gas.minOf{it[2]}< -1.09f)
        assertTrue(gas.maxOf{it[2]}>0f && gas.maxOf{it[0]}>.54f)
        val fire=RoomPlacementArea.outline("fire",1)
        assertTrue(fire.minOf{it[0]}<=-.45f && fire.maxOf{it[0]}>=.45f)
        assertEquals(32,RoomPlacementArea.outline("gas",2).size)
    }
}
