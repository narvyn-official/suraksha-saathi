package com.narvyn.suraksha

import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.*
import java.util.EnumSet
import java.util.concurrent.Executors

/** Common camera workload policy; these controls do not establish tracking or learning evidence. */
internal object ArCameraSupport {
    fun configure(session: Session) {
        val filter = CameraConfigFilter(session)
            .setTargetFps(EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30))
            .setDepthSensorUsage(EnumSet.of(CameraConfig.DepthSensorUsage.DO_NOT_USE))
        val camera = session.getSupportedCameraConfigs(filter)
            .minByOrNull { it.textureSize.width.toLong() * it.textureSize.height }
            ?: error("No supported 30 fps camera configuration")
        session.cameraConfig = camera
        session.configure(Config(session).apply {
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
            focusMode = Config.FocusMode.FIXED
        })
        Log.i("TrainingAR", "camera fps=${camera.fpsRange} texture=${camera.textureSize}")
    }

    fun startupMessage(error: Exception, hi: Boolean): String {
        fun t(en: String, hindi: String) = if (hi) hindi else en
        return when (error) {
            is UnavailableDeviceNotCompatibleException -> t("This phone does not support ARCore camera tracking. Continue on screen.", "यह फ़ोन ARCore कैमरा ट्रैकिंग समर्थित नहीं करता। स्क्रीन पर जारी रखें।")
            is UnavailableArcoreNotInstalledException, is UnavailableApkTooOldException -> t("Install or update Google Play Services for AR, then retry the camera.", "Google Play Services for AR स्थापित या अपडेट करें, फिर कैमरा आज़माएँ।")
            is UnavailableSdkTooOldException -> t("This app needs an update to use the installed AR services. Continue on screen.", "स्थापित AR सेवाओं के लिए ऐप अपडेट चाहिए। स्क्रीन पर जारी रखें।")
            is UnavailableUserDeclinedInstallationException -> t("AR installation was declined. Retry to install it, or continue on screen.", "AR स्थापना अस्वीकार की गई। स्थापना के लिए फिर कोशिश करें, या स्क्रीन पर जारी रखें।")
            is CameraNotAvailableException -> t("Camera is unavailable or in use. Close other camera apps, then retry.", "कैमरा उपलब्ध नहीं या उपयोग में है। दूसरे कैमरा ऐप बंद करें, फिर कोशिश करें।")
            is SecurityException -> t("Camera permission is off. Enable it, then retry the camera.", "कैमरा अनुमति बंद है। अनुमति देकर कैमरा फिर आज़माएँ।")
            else -> t("Camera AR could not start. Retry the camera, or continue on screen.", "कैमरा AR शुरू नहीं हुआ। कैमरा फिर आज़माएँ, या स्क्रीन पर जारी रखें।")
        }
    }

    fun coolingMessage(hi: Boolean) = if (hi)
        "फ़ोन बहुत गर्म है। कैमरा रोका गया। ठंडा होने पर कैमरा फिर आज़माएँ।"
        else "Phone is too hot. Camera paused. Let it cool, then retry the camera."
    fun releaseMessage(hi: Boolean, failed: Boolean) = if (failed) {
        if (hi) "कैमरा बंद नहीं हुआ। यह प्रशिक्षण बंद करके फिर खोलें।"
        else "Camera release failed. Close this training and open it again."
    } else {
        if (hi) "पिछला कैमरा सत्र बंद हो रहा है…" else "Releasing the previous camera session…"
    }
}

/** Main-thread pump. A stopped/hidden camera cannot retain callbacks or keep the display awake. */
internal class ArCameraFramePump(private val surface: GLSurfaceView, private val onCritical: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val power = surface.context.getSystemService(PowerManager::class.java)
    private var active = false
    private var thermal = PowerManager.THERMAL_STATUS_NONE
    private var sampledAt = Long.MIN_VALUE
    fun tooHot(): Boolean = power.currentThermalStatus >= PowerManager.THERMAL_STATUS_CRITICAL
    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            val now = SystemClock.elapsedRealtime()
            if (sampledAt == Long.MIN_VALUE || now - sampledAt >= 1000L) {
                thermal = power.currentThermalStatus; sampledAt = now
            }
            if (thermal >= PowerManager.THERMAL_STATUS_CRITICAL) {
                stop(); onCritical(); return
            }
            surface.requestRender()
            main.postDelayed(this, if (thermal >= PowerManager.THERMAL_STATUS_SEVERE) 50L else 34L)
        }
    }
    fun start() {
        stop(); active = true; sampledAt = Long.MIN_VALUE
        surface.keepScreenOn = true
        main.post(tick)
    }
    fun stop() {
        active = false; main.removeCallbacks(tick); surface.keepScreenOn = false
    }
}

/** Caller must stop its GL owner, pause the session and detach anchors before handing ownership here. */
internal class ArSessionRelease {
    var closing = false
        private set
    var failed = false
        private set
    /** All AR owners share this barrier, including releases queued on the worker. UI-thread access only. */
    val busy get() = pendingReleases > 0
    private var available: (() -> Unit)? = null
    fun awaitAvailable(callback: () -> Unit) {
        cancelPendingResume()
        if (!busy) { callback(); return }
        available = callback; waiters.add(this)
    }
    fun cancelPendingResume() { available = null; waiters.remove(this) }
    fun retire(session: Session, afterClose: () -> Unit) {
        check(!closing)
        closing = true; pendingReleases++
        closer.execute {
            var error = false
            try { session.close() } catch (failure: Exception) {
                error = true; Log.e("TrainingAR", "Session release failed: ${failure.javaClass.simpleName}")
            }
            main.post {
                closing = false; failed = error; pendingReleases--
                afterClose()
                if (pendingReleases == 0) {
                    val ready = waiters.toList(); waiters.clear()
                    ready.forEach { owner ->
                        val callback = owner.available; owner.available = null
                        callback?.invoke()
                    }
                }
            }
        }
    }
    companion object {
        private val main = Handler(Looper.getMainLooper())
        private var pendingReleases = 0
        private val waiters = linkedSetOf<ArSessionRelease>()
        private val closer = Executors.newSingleThreadExecutor { task -> Thread(task, "training-ar-close") }
    }
}
