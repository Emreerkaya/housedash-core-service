package com.housedash.domain.identity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private fun otpRow(
    id: String = "otp_1",
    identifier: StoredIdentifier = StoredIdentifier(AN_EMAIL, "EMAIL"),
    codeHash: String = A_HASH,
    attempts: Int = 0,
    consumed: Boolean = false,
): OtpRow = OtpRow(id, identifier, codeHash, issuedAt, attempts, consumed)

private fun corruptOnLoad(row: OtpRow): OtpFault = assertFailsWith<CorruptOtp> { IssuedOtp.rehydrate(row) }.fault

class OtpRehydrationTest {
    @Test
    fun `an issued otp round trips with the same id, identifier, hash, attempts and consumed flag`() {
        val back = IssuedOtp.rehydrate(otpRow(attempts = 2, consumed = false))
        assertEquals(otpId(), back.id)
        assertEquals(emailIdentifier(), back.identifier)
        assertEquals(A_HASH, back.codeHash)
        assertEquals(issuedAt, back.issuedAt)
        assertEquals(2, back.attempts)
        assertEquals(false, back.consumed)
    }

    @Test
    fun `a consumed row at the attempt cap round trips exactly as stored`() {
        val back = IssuedOtp.rehydrate(otpRow(attempts = IssuedOtp.ATTEMPT_CAP, consumed = true))
        assertEquals(IssuedOtp.ATTEMPT_CAP, back.attempts)
        assertEquals(true, back.consumed)
    }

    @Test
    fun `a malformed otp id is refused, constructed directly and never merely believed`() {
        assertEquals(OtpFault.MALFORMED_ID, corruptOnLoad(otpRow(id = "ac_1")))
        assertEquals(OtpFault.MALFORMED_ID, corruptOnLoad(otpRow(id = "otp_")))
    }

    @Test
    fun `an unknown identifier kind is refused`() {
        val row = otpRow(identifier = StoredIdentifier(AN_EMAIL, "FAX"))
        assertEquals(OtpFault.UNKNOWN_IDENTIFIER_KIND, corruptOnLoad(row))
    }

    @Test
    fun `an identifier value that no longer matches its stored kind is refused`() {
        val row = otpRow(identifier = StoredIdentifier("not an email", "EMAIL"))
        assertEquals(OtpFault.MALFORMED_IDENTIFIER, corruptOnLoad(row))
    }

    @Test
    fun `a blank code hash is refused, a code is never stored in the clear nor empty`() {
        assertEquals(OtpFault.BLANK_CODE_HASH, corruptOnLoad(otpRow(codeHash = "")))
        assertEquals(OtpFault.BLANK_CODE_HASH, corruptOnLoad(otpRow(codeHash = "   ")))
    }

    @Test
    fun `attempts outside zero to the cap are refused, one below and one above the bound`() {
        assertEquals(OtpFault.ATTEMPTS_OUT_OF_RANGE, corruptOnLoad(otpRow(attempts = -1)))
        assertEquals(OtpFault.ATTEMPTS_OUT_OF_RANGE, corruptOnLoad(otpRow(attempts = IssuedOtp.ATTEMPT_CAP + 1)))
    }
}
