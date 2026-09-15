package com.narvyn.suraksha
import org.junit.Assert.*
import org.junit.Test
class CameraWorkspaceLayoutTest {
    @Test fun shortAndDenseWindowsAlwaysReserveMostHeightForScene(){
        for(height in listOf(0,100,320,640,1080,2400))for(density in listOf(.75f,1f,2.75f,4f,Float.NaN,Float.POSITIVE_INFINITY)){
            val b=CameraWorkspaceLayout.budget(height,density)
            assertTrue(b.header>=0&&b.feedback>=0)
            assertTrue(b.header+b.feedback<=height*.43f)
            assertTrue(CameraWorkspaceLayout.controls(height,density) in 0..height)
        }
    }
}
