package com.sadique.dailyledger.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.security.MessageDigest

class KametiOperationsPolicyTest {
    @Test fun unverifiedAndRejectedNeverCountAsVerified() {
        val s = kametiVerificationPreview(listOf(
            KametiPaymentCandidate("a", 500000, KametiPaymentVerification.VERIFIED),
            KametiPaymentCandidate("b", 200000, KametiPaymentVerification.PENDING),
            KametiPaymentCandidate("c", 100000, KametiPaymentVerification.REVERSED)))
        assertEquals(500000L, s.verifiedMinor)
        assertEquals(200000L, s.pendingMinor)
        assertEquals(100000L, s.rejectedOrReversedMinor)
    }
    @Test(expected = IllegalArgumentException::class)
    fun duplicatePaymentIdRejected() {
        kametiVerificationPreview(listOf(
            KametiPaymentCandidate("same", 100, KametiPaymentVerification.VERIFIED),
            KametiPaymentCandidate("same", 100, KametiPaymentVerification.PENDING)))
    }
    @Test fun roleMatrixPreventsCollectorPayoutAndViewerEdits() {
        assertFalse(kametiMay(KametiRole.COLLECTOR, KametiPermission.APPROVE_PAYOUT))
        assertFalse(kametiMay(KametiRole.VIEWER, KametiPermission.CORRECT))
        assertTrue(kametiMay(KametiRole.APPROVER, KametiPermission.VERIFY))
    }
    @Test fun familyAllocationNeverExceedsAnyMember() {
        assertEquals(500L, validateFamilyAllocations(
            listOf(KametiFamilyAllocation("father", "ali", 200), KametiFamilyAllocation("sister", "ali", 300)),
            mapOf("ali" to 500L)))
    }
    @Test(expected = IllegalArgumentException::class)
    fun familyAllocationOverpayRejected() {
        validateFamilyAllocations(listOf(KametiFamilyAllocation("father", "ali", 501)), mapOf("ali" to 500))
    }
    @Test fun promiseIsNotACollectionAndExitCanFlagReview() {
        assertTrue(kametiPromiseOverdue(KametiPaymentPromise("ali", 500, LocalDate.of(2027, 1, 10)), LocalDate.of(2027, 1, 11)))
        assertTrue(KametiExitPreview(30000, 0, 5000, 1).needsReview)
    }
    @Test fun reportUsesVerifiedOnly() {
        val s = KametiHealthSnapshot(100000, 40000, 20000, 5000, 100000, 60000)
        assertEquals(40, s.collectionPercent)
        assertEquals(40000L, s.payoutShortfallMinor)
    }
    @Test fun recoveryChecksumOwnerBinding() {
        val bytes = "offline fixture".toByteArray()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        val manifest = KametiRecoveryManifest("owner", 7, 4, hash)
        assertTrue(verifyKametiRecoveryManifest(manifest, "owner", bytes))
        assertFalse(verifyKametiRecoveryManifest(manifest, "different", bytes))
        assertFalse(verifyKametiRecoveryManifest(manifest, "owner", "altered".toByteArray()))
    }
    @Test fun templateCannotWriteMoney() {
        assertEquals(20, kametiDefaultTemplate("friends").slots)
        assertEquals(12, kametiDefaultTemplate("family").slots)
    }
}
