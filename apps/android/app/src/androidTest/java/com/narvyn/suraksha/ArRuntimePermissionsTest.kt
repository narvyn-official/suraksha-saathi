package com.narvyn.suraksha

import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Check the installed/merged APK, since unit tests cannot detect lost manifest permissions. */
@RunWith(AndroidJUnit4::class)
class ArRuntimePermissionsTest {
    @Test fun nativeTrackingCanRequestHighRateMotionSamples() {
        if (Build.VERSION.SDK_INT < 31) return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("ARCore's native sensor rate request must not be denied",
            PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission("android.permission.HIGH_SAMPLING_RATE_SENSORS"))
    }
}
