package com.narvyn.suraksha

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic token signed by the web approval flow; no private key or real learner data. */
@RunWith(AndroidJUnit4::class)
class GovernanceCredentialTest {
    @Test fun independentlyApprovedWebCredentialSurvivesQrAndNativeSignatureVerification(){
        val i=InstrumentationRegistry.getInstrumentation()
        val raw=i.context.assets.open("governance-credential.txt").bufferedReader().use{it.readText()}
        val matrix=QRCodeWriter().encode(raw,BarcodeFormat.QR_CODE,768,768)
        val pixels=IntArray(matrix.width*matrix.height){n->if(matrix[n%matrix.width,n/matrix.width])android.graphics.Color.BLACK else android.graphics.Color.WHITE}
        val decoded=QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(matrix.width,matrix.height,pixels)))).text
        assertEquals(raw,decoded)
        val payload=CredentialVerifier.verify(i.targetContext,decoded)
        assertEquals(1,payload.getInt("governanceVersion"))
        assertEquals("Government demonstration centre · QA",payload.getString("centreName"))
        assertNotEquals(payload.getString("requestedBy"),payload.getString("approvedBy"))
        assertTrue(payload.getString("evidenceDigest").matches(Regex("[a-f0-9]{64}")))
        assertEquals("not-assessed",payload.getString("practical"))
        assertEquals("pilot-simulation",payload.getString("kind"))
    }
}
