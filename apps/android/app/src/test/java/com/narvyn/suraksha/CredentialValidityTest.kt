package com.narvyn.suraksha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CredentialValidityTest {
    private val issued=1_800_000_000_000L
    private val expiry=issued+1000
    private fun payload()=JSONObject().put("iat",issued).put("expiresAt",expiry)
    private fun rejected(block:()->Unit) { try { block();fail("Invalid validity data accepted") } catch(_:IllegalArgumentException) {} }
    @Test fun legacyHasNoRecordedExpiryRatherThanAnAssumedPeriod() {
        val result=CredentialVerifier.validity(JSONObject().put("iat",issued),issued+999999)
        assertEquals("not-recorded",result.getString("expiryStatus"))
    }
    @Test fun expiryBoundaryAndFreshEvaluationAreIndependentOfStoredAnnotations() {
        val claims=payload().put("expiryStatus","within-validity").put("signatureValid",true)
        assertEquals("within-validity",CredentialVerifier.validity(claims,expiry-1).getString("expiryStatus"))
        assertEquals("expired",CredentialVerifier.validity(claims,expiry).getString("expiryStatus"))
        assertEquals("expired",CredentialVerifier.validity(claims,expiry+1).getString("expiryStatus"))
        assertEquals(expiry+1,CredentialVerifier.validity(claims,expiry+1).getLong("validityCheckedAt"))
    }
    @Test fun futureIssuanceIsExplicitForAnIncorrectDeviceClock() {
        assertTrue(CredentialVerifier.validity(payload(),issued-1).getBoolean("issuedInFuture"))
        assertFalse(CredentialVerifier.validity(payload(),issued).getBoolean("issuedInFuture"))
    }
    @Test fun malformedOrChronologicallyInvalidDatesAreRejected() {
        for(value in listOf<Any>(JSONObject.NULL,"1800000001000",expiry+.5,-1L,8_640_000_000_000_001L,issued,issued-1))
            rejected { CredentialVerifier.validity(payload().put("expiresAt",value),issued) }
        for(value in listOf<Any>(JSONObject.NULL,"1800000000000",issued+.5,-1L,8_640_000_000_000_001L))
            rejected { CredentialVerifier.validity(payload().put("iat",value),issued) }
        rejected { CredentialVerifier.validity(payload(),-1) }
        rejected { CredentialVerifier.validity(payload(),8_640_000_000_000_001L) }
    }
}
