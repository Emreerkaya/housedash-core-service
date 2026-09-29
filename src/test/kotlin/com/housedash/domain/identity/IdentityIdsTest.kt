package com.housedash.domain.identity

import com.housedash.domain.shared.IdentifierFlaw
import com.housedash.domain.shared.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

private val MALFORMED =
    listOf(
        "",
        " ",
        "nope",
        "AC_1",
        " ac_1",
        "ac_1 ",
        "ac_'; drop table accounts; --",
    )

class IdentityIdsTest {
    @Test
    fun `an account id accepts its own prefix and rejects another type's`() {
        assertEquals("ac_1", accountId("ac_1").value)
        assertEquals(
            IdentityError.MalformedAccountId(IdentifierFlaw.WrongPrefix),
            assertIs<Outcome.Err<IdentityError>>(AccountId.of("otp_1")).error,
        )
    }

    @Test
    fun `an account id rejects everything that is not its shape`() {
        MALFORMED.forEach { raw ->
            val error = assertIs<Outcome.Err<IdentityError>>(AccountId.of(raw)).error
            assertIs<IdentityError.MalformedAccountId>(error, raw)
        }
    }

    @Test
    fun `an otp id accepts its own prefix and rejects another type's`() {
        assertEquals("otp_1", otpId("otp_1").value)
        assertEquals(
            IdentityError.MalformedOtpId(IdentifierFlaw.WrongPrefix),
            assertIs<Outcome.Err<IdentityError>>(OtpId.of("ac_1")).error,
        )
    }

    @Test
    fun `identifiers of the same type and value are equal, different types never compare equal`() {
        assertEquals(accountId("ac_1"), accountId("ac_1"))
        assertNotEquals(accountId("ac_1"), accountId("ac_2"))
        assertNotEquals<Any>(accountId("ac_1"), otpId("otp_1"))
        assertNotEquals<Any>(accountId("ac_1"), "ac_1")
    }

    @Test
    fun `an identifier renders as the value it holds`() {
        assertEquals("ac_1", accountId("ac_1").toString())
        assertEquals("otp_1", otpId("otp_1").toString())
    }

    @Test
    fun `the published body bound is the one enforced`() {
        assertEquals(64, AccountId.MAX_BODY_LENGTH)
        assertEquals(64, OtpId.MAX_BODY_LENGTH)
    }
}
