package com.housedash.domain.identity

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val NOT_EXPIRED_NOW: Instant = issuedAt.plus(IssuedOtp.TTL).minusSeconds(1)

private val EXPIRED_NOW: Instant = issuedAt.plus(IssuedOtp.TTL)

private data class VerifyCase(
    val attempts: Int,
    val consumed: Boolean,
    val expired: Boolean,
    val codeMatches: Boolean,
) {
    val now: Instant get() = if (expired) EXPIRED_NOW else NOT_EXPIRED_NOW

    override fun toString() = "attempts=$attempts consumed=$consumed expired=$expired matches=$codeMatches"
}

private fun everyVerifyCase(): List<VerifyCase> =
    (0..IssuedOtp.ATTEMPT_CAP).flatMap { attempts ->
        listOf(true, false).flatMap { consumed ->
            listOf(true, false).flatMap { expired ->
                listOf(true, false).map { codeMatches -> VerifyCase(attempts, consumed, expired, codeMatches) }
            }
        }
    }

private fun otpWith(case: VerifyCase): IssuedOtp {
    val identifier = StoredIdentifier(emailIdentifier().value, "EMAIL")
    val row = OtpRow(otpId().value, identifier, A_HASH, issuedAt, case.attempts, case.consumed)
    return IssuedOtp.rehydrate(row)
}

private fun expectedReason(case: VerifyCase): OtpRejection? =
    when {
        case.consumed -> OtpRejection.ALREADY_CONSUMED
        case.attempts >= IssuedOtp.ATTEMPT_CAP -> OtpRejection.ATTEMPTS_EXHAUSTED
        case.expired -> OtpRejection.EXPIRED
        case.codeMatches -> null
        else -> OtpRejection.WRONG_CODE
    }

private fun assertAccepted(
    case: VerifyCase,
    result: OtpVerification,
) {
    val accepted = assertIs<OtpVerification.Accepted>(result, case.toString())
    assertEquals(true, accepted.consumed.consumed, case.toString())
    assertEquals(case.attempts, accepted.consumed.attempts, case.toString())
}

private fun assertRejected(
    case: VerifyCase,
    reason: OtpRejection,
    result: OtpVerification,
) {
    val rejected = assertIs<OtpVerification.Rejected>(result, case.toString())
    assertEquals(reason, rejected.reason, case.toString())
    val wrongCode = reason == OtpRejection.WRONG_CODE
    val expectedAttempts = if (wrongCode) case.attempts + 1 else case.attempts
    assertEquals(expectedAttempts, rejected.updated.attempts, case.toString())
    assertEquals(if (wrongCode) false else case.consumed, rejected.updated.consumed, case.toString())
}

class IssuedOtpTest {
    @Test
    fun `every command in every state lands where the domain model draws it and nowhere else`() {
        val cases = everyVerifyCase()
        cases.forEach { case ->
            val result = otpWith(case).verify(case.codeMatches, case.now)
            val reason = expectedReason(case)
            if (reason == null) assertAccepted(case, result) else assertRejected(case, reason, result)
        }
        assertEquals((IssuedOtp.ATTEMPT_CAP + 1) * 2 * 2 * 2, cases.size)
    }

    @Test
    fun `a freshly issued code is unconsumed with no attempts and expires ten minutes after issue`() {
        val otp = anIssuedOtp()
        assertEquals(0, otp.attempts)
        assertEquals(false, otp.consumed)
        assertEquals(issuedAt.plusSeconds(600), otp.expiresAt)
    }

    @Test
    fun `five consecutive wrong attempts exhaust the cap, and the sixth is exhausted rather than wrong`() {
        var otp = anIssuedOtp()
        repeat(IssuedOtp.ATTEMPT_CAP) {
            val rejected = assertIs<OtpVerification.Rejected>(otp.verify(false, NOT_EXPIRED_NOW))
            assertEquals(OtpRejection.WRONG_CODE, rejected.reason)
            otp = rejected.updated
        }
        assertEquals(IssuedOtp.ATTEMPT_CAP, otp.attempts)
        val sixth = assertIs<OtpVerification.Rejected>(otp.verify(true, NOT_EXPIRED_NOW))
        assertEquals(OtpRejection.ATTEMPTS_EXHAUSTED, sixth.reason, "even the correct code is refused once spent")
    }

    @Test
    fun `verifying is single use, a second correct attempt after acceptance is already consumed`() {
        val accepted = assertIs<OtpVerification.Accepted>(anIssuedOtp().verify(true, NOT_EXPIRED_NOW)).consumed
        val second = assertIs<OtpVerification.Rejected>(accepted.verify(true, NOT_EXPIRED_NOW))
        assertEquals(OtpRejection.ALREADY_CONSUMED, second.reason)
    }
}
