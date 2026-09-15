package com.narvyn.suraksha

import com.google.ar.core.Pose
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.cos
import kotlin.math.sin

/** Synthetic pose mathematics only. These tests cannot establish physical tracking quality. */
@RunWith(AndroidJUnit4::class)
class RoomAnchorPoseTest {
    private fun yaw(degrees:Float):Pose {
        val half=Math.toRadians(degrees.toDouble())/2
        return Pose.makeRotation(0f,sin(half).toFloat(),0f,cos(half).toFloat())
    }
    @Test fun savedFacingFollowsWorldRebasesInsteadOfTurningAwayFromTheCamera() {
        val anchor=Pose.makeTranslation(1f,0f,-2f).compose(yaw(37f))
        val camera=Pose.makeTranslation(2f,1.5f,0f)
        val offset=RoomAnchorPose.facingOffset(anchor,camera)
        val model=RoomAnchorPose.model(anchor,offset)
        for(degrees in listOf(-90f,45f,180f)){
            val rebase=Pose.makeTranslation(-3f,.1f,4f).compose(yaw(degrees))
            val updated=RoomAnchorPose.model(rebase.compose(anchor),offset)
            for(point in listOf(floatArrayOf(0f,0f,0f),floatArrayOf(.36f,.73f,0f),floatArrayOf(0f,0f,-.55f))){
                assertArrayEquals(rebase.transformPoint(model.transformPoint(point)),updated.transformPoint(point),.0001f)
            }
            val before=model.inverse().transformPoint(camera.translation)
            val after=updated.inverse().transformPoint(rebase.compose(camera).translation)
            assertArrayEquals(before,after,.0001f)
            assertEquals(0f,before[0],.0001f);assertTrue(before[2]>0f)
        }
    }
    @Test fun outsidePlacementClassificationSurvivesCommonWorldRotation() {
        val model=Pose.makeTranslation(2f,0f,-1f).compose(yaw(25f))
        for(degrees in listOf(0f,90f,180f,-37f)){
            val world=Pose.makeTranslation(-2f,0f,3f).compose(yaw(degrees))
            val hazard=world.compose(model)
            fun station(x:Float,z:Float)=RoomAnchorPose.station(hazard.compose(Pose.makeTranslation(x,0f,z)))
            val existing=listOf(station(-3f,0f),RoomAnchorPose.station(hazard))
            assertTrue(RoomStationPlacement.evaluate("gas",existing,station(0f,1.1f)).allowed)
            assertFalse(RoomStationPlacement.evaluate("gas",existing,station(0f,-1.1f)).allowed)
        }
    }
}
