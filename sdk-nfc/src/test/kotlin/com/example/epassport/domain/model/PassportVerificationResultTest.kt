package com.example.epassport.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassportVerificationResultTest {

    @Test
    fun success_withPaAndAa_returnsSuccessful() {
        val pa = AuthenticationStepResult.success("PA")
        val aa = AuthenticationStepResult.success("AA")

        val result = PassportVerificationResult.success(pa, aa)

        assertTrue(result.isSuccessful)
        assertTrue(result.passiveAuthentication.success)
        assertTrue(result.activeAuthentication!!.success)
        assertNull(result.failureReason)
    }

    @Test
    fun success_withPaOnly_returnsSuccessful() {
        val pa = AuthenticationStepResult.success("PA")

        val result = PassportVerificationResult.success(pa, null)

        assertTrue(result.isSuccessful)
        assertNull(result.activeAuthentication)
    }

    @Test
    fun success_withFailedAa_returnsUnsuccessful() {
        val pa = AuthenticationStepResult.success("PA")
        val aa = AuthenticationStepResult.failure("AA", "signature invalid")

        val result = PassportVerificationResult.success(pa, aa)

        assertFalse(result.isSuccessful)
        assertTrue(result.failureReason!!.contains("AA failed"))
    }

    @Test
    fun failure_returnsUnsuccessfulWithReason() {
        val pa = AuthenticationStepResult.success("PA")
        val reason = "AA requested but DG15 missing"

        val result = PassportVerificationResult.failure(pa, null, reason)

        assertFalse(result.isSuccessful)
        assertEquals(reason, result.failureReason)
    }
}
