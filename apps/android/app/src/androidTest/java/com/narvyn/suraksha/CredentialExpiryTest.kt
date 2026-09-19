package com.narvyn.suraksha

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialExpiryTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun asset(name:String)=instrumentation.context.assets.open(name).bufferedReader().use { it.readText() }
    private fun payload(raw:String):JSONObject {
        val body=raw.trim().removePrefix("SURAKSHA:CREDENTIAL:").split('.')[1]
        return JSONObject(String(Base64.decode(body,Base64.URL_SAFE or Base64.NO_WRAP),Charsets.UTF_8))
    }
    @Test fun learnerImporterAcceptsPrefixedAndLegacyNativeAdminPayloads() {
        val context=instrumentation.targetContext
        val oldHi=Store(context).use{it.hi};Store(context).use{it.hi=false}
        val token=asset("demo-credential.txt").trim().removePrefix("SURAKSHA:CREDENTIAL:")
        try { for(raw in listOf("  SURAKSHA:CREDENTIAL:$token\n",token)) {
            androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> MainActivity::class.java.getDeclaredMethod("checkRecord",String::class.java).apply{isAccessible=true}.invoke(activity,raw) }
                androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Signature verified offline"))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                    .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
            }
        }} finally {Store(context).use{it.hi=oldHi}}
    }
    @Test fun legacySignatureRemainsVerifiableWithoutAnInventedExpiry() {
        val raw=asset("legacy-no-expiry-credential.txt");val issued=payload(raw).getLong("iat")
        val result=CredentialVerifier.verify(instrumentation.targetContext,raw,issued+1_000_000)
        assertTrue(result.getBoolean("signatureValid"));assertEquals("not-recorded",result.getString("expiryStatus"))
    }
    @Test fun signedExpiryIsReevaluatedAndTamperingFailsSignatureVerification() {
        // Refreshed by the integration fixture workflow, using an explicit trainer-selected test expiry.
        val raw=asset("demo-credential.txt");val body=payload(raw);val expires=body.getLong("expiresAt")
        val current=CredentialVerifier.verify(instrumentation.targetContext,raw,expires-1)
        val expired=CredentialVerifier.verify(instrumentation.targetContext,raw,expires)
        assertTrue(current.getBoolean("signatureValid"));assertTrue(expired.getBoolean("signatureValid"))
        assertEquals("within-validity",current.getString("expiryStatus"));assertEquals("expired",expired.getString("expiryStatus"))
        val parts=raw.trim().removePrefix("SURAKSHA:CREDENTIAL:").split('.').toMutableList()
        body.put("expiresAt",expires+86_400_000L)
        parts[1]=Base64.encodeToString(body.toString().toByteArray(Charsets.UTF_8),Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        try { CredentialVerifier.verify(instrumentation.targetContext,parts.joinToString("."),expires);fail("Expiry tamper was accepted") }
        catch(e:IllegalArgumentException) { assertEquals("Invalid signature",e.message) }
    }
}
