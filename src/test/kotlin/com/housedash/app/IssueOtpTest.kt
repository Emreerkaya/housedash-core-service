package com.housedash.app

import com.housedash.domain.identity.LoginIdentifierFlaw
import com.housedash.domain.shared.Outcome
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IssueOtpTest {
    private val otps = InMemoryOtpRepository()

    private val sender = RecordingOtpSender()

    private fun issueOtp(
        identifiers: OtpIdentifiers = CountingOtpIdentifiers(),
        codes: OtpCodeGenerator = FixedOtpCodes(),
    ): IssueOtp = IssueOtp(otps, identifiers, OtpDelivery(codes, TEST_HASHER, sender), OTP_CLOCK)

    @Test
    fun `issuing stores the code hashed, never in the clear, and delivers the plaintext only to the sender`() {
        assertEquals(Outcome.Ok(Unit), issueOtp().handle(issueRequest()))
        assertEquals(1, otps.stored.size)
        val (stored, ip) = otps.stored.single()
        assertNotEquals(A_CODE, stored.codeHash)
        assertEquals(TEST_HASHER.hash(A_CODE), stored.codeHash)
        assertEquals(AN_IP, ip)
        assertEquals(OTP_ISSUED_AT, stored.issuedAt)
        assertEquals(1, sender.sent.size)
        assertEquals(A_CODE, sender.sent.single().second)
    }

    @Test
    fun `an absent identifier is refused before anything is stored`() {
        val failure = failureOf(issueOtp().handle(issueRequest(identifier = null)))
        assertEquals(IssueOtpFailure.IdentifierAbsent, failure)
        assertEquals(0, otps.stored.size)
        assertEquals(0, sender.sent.size)
    }

    @Test
    fun `an identifier that is neither an email nor a phone is refused by its own shape`() {
        val failure = failureOf(issueOtp().handle(issueRequest(identifier = "not an identifier")))
        assertEquals(
            IssueOtpFailure.MalformedIdentifier(LoginIdentifierFlaw.UnrecognizedShape),
            failure,
        )
        assertEquals(0, otps.stored.size)
    }

    @Test
    fun `an identifier that has never been seen before is issued a code exactly like one that has`() {
        val neverSeen = issueOtp().handle(issueRequest(identifier = "brand-new@example.com"))
        assertEquals(Outcome.Ok(Unit), neverSeen)
        assertEquals(1, otps.stored.size)
    }

    @Test
    fun `a fourth issuance for the same identifier inside the window is rate limited`() {
        val issue = issueOtp()
        repeat(3) { assertEquals(Outcome.Ok(Unit), issue.handle(issueRequest())) }
        assertEquals(IssueOtpFailure.RateLimited, failureOf(issue.handle(issueRequest())))
        assertEquals(3, otps.stored.size)
    }

    @Test
    fun `an eleventh issuance from the same ip for different identifiers is rate limited`() {
        val issue = issueOtp()
        (1..10).forEach { n ->
            assertEquals(Outcome.Ok(Unit), issue.handle(issueRequest(identifier = "n$n@example.com")))
        }
        val failure = failureOf(issue.handle(issueRequest(identifier = "n11@example.com")))
        assertEquals(IssueOtpFailure.RateLimited, failure)
        assertEquals(10, otps.stored.size)
    }

    @Test
    fun `rate limiting one identifier does not rate limit another on the same ip below the ip cap`() {
        val issue = issueOtp()
        repeat(3) { issue.handle(issueRequest(identifier = AN_IDENTIFIER)) }
        val other = issue.handle(issueRequest(identifier = "someone-else@example.com"))
        assertEquals(Outcome.Ok(Unit), other)
    }

    @Test
    fun `an identifier source that cannot mint an otp id is its own failure and stores nothing`() {
        val failure = failureOf(issueOtp(identifiers = RefusingOtpIdentifiers()).handle(issueRequest()))
        assertIs<IssueOtpFailure.OtpIdRefused>(failure)
        assertEquals(0, otps.stored.size)
        assertEquals(0, sender.sent.size)
    }

    @Test
    fun `every issued code is exactly six numeric digits from the real generator`() {
        val delivery = OtpDelivery(SecureRandomOtpCodes(), TEST_HASHER, sender)
        val real = IssueOtp(otps, CountingOtpIdentifiers(), delivery, OTP_CLOCK)
        assertEquals(Outcome.Ok(Unit), real.handle(issueRequest()))
        val code = sender.sent.single().second
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    private fun <T> failureOf(outcome: Outcome<T, IssueOtpFailure>): IssueOtpFailure =
        when (outcome) {
            is Outcome.Ok -> error("expected a refusal and the use case succeeded")
            is Outcome.Err -> outcome.error
        }
}
