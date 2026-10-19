package com.sadique.dailyledger.data
import org.junit.Test
import org.junit.Assert.*
class PaymentProofValidationTest {
 @Test fun realHeaderRequiredAndSizeBounded(){
  assertEquals("application/pdf",proofMime("%PDF-1.7 test".toByteArray()))
  assertTrue(runCatching{proofMime("fake.jpg".toByteArray())}.isFailure)
  assertTrue(runCatching{proofMime(byteArrayOf())}.isFailure)
 }
}
