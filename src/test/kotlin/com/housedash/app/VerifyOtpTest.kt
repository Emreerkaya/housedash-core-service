package com.housedash.app

import com.housedash.domain.shared.Outcome
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val AFTER_EXPIRY: Clock = Clock.fixed(OTP_ISSUED_AT.plus(Duration.ofMinutes(11)), ZoneOffset.UTC)

class VerifyOtpTest {
    private val otps = InMemoryOtpRepository()

    private val sender = RecordingOtpSender()

    private fun issued(clock: Clock = OTP_CLOCK): IssueOtp {
        val delivery = OtpDelivery(FixedOtpCodes(), TEST_HASHER, sender)
        return IssueOtp(otps, CountingOtpIdentifiers(), delivery, clock)
    }

    private fun verifyOtp(clock: Clock = OTP_CLOCK): VerifyOtp = VerifyOtp(otps, TEST_HASHER, clock)

    private fun seed(
        identifier: String = AN_IDENTIFIER,
        clock: Clock = OTP_CLOCK,
    ) {
        check(issued(clock).handle(issueRequest(identifier = identifier)) is Outcome.Ok) { "setup must succeed" }
    }

    private fun verifyIgnoringResult(
        code: String,
        clock: Clock = OTP_CLOCK,
    ): Outcome<Unit, VerifyOtpFailure> = verifyOtp(clock).handle(verifyRequest(code = code))

    @Test
    fun `the correct code inside the window is accepted and consumes the code`() {
        seed()
        assertEquals(Outcome.Ok(Unit), verifyOtp().handle(verifyRequest()))
        assertEquals(
            true,
            otps.stored
                .single()
                .first.consumed,
        )
    }

    @Test
    fun `a wrong code is refused generically and the code lives to be tried again`() {
        seed()
        val failure = verifyOtp().handle(verifyRequest(code = "000000"))
        assertEquals(Outcome.Err(VerifyOtpFailure.Invalid), failure)
        assertEquals(
            1,
            otps.stored
                .single()
                .first.attempts,
        )
        assertEquals(
            false,
            otps.stored
                .single()
                .first.consumed,
        )
    }

    @Test
    fun `an identifier for which no code was ever issued answers exactly like a wrong code would`() {
        val failure = verifyOtp().handle(verifyRequest(identifier = "never-issued@example.com"))
        assertEquals(Outcome.Err(VerifyOtpFailure.Invalid), failure)
    }

    @Test
    fun `an absent identifier or an absent code both answer the one generic refusal`() {
        assertEquals(Outcome.Err(VerifyOtpFailure.Invalid), verifyOtp().handle(verifyRequest(identifier = null)))
        assertEquals(Outcome.Err(VerifyOtpFailure.Invalid), verifyOtp().handle(verifyRequest(code = null)))
    }

    @Test
    fun `an identifier that is not shaped like an email or a phone answers the one generic refusal`() {
        assertEquals(
            Outcome.Err(VerifyOtpFailure.Invalid),
            verifyOtp().handle(verifyRequest(identifier = "not an identifier")),
        )
    }

    @Test
    fun `a code submitted after the ten minute window answers the one generic refusal`() {
        seed()
        assertEquals(Outcome.Err(VerifyOtpFailure.Invalid), verifyOtp(AFTER_EXPIRY).handle(verifyRequest()))
    }

    @Test
    fun `five wrong attempts exhaust the code, and the sixth, even correct, answers the same refusal`() {
        seed()
        repeat(5) { assertEquals(Outcome.Err(VerifyOtpFailure.Invalid), verifyIgnoringResult("000000")) }
        val sixth = verifyOtp().handle(verifyRequest())
        assertEquals(Outcome.Err(VerifyOtpFailure.Invalid), sixth)
    }

    @Test
    fun `verifying twice with the correct code, the second answers the same generic refusal a wrong one would`() {
        seed()
        val first = verifyOtp().handle(verifyRequest())
        assertEquals(Outcome.Ok(Unit), first)
        val second = verifyOtp().handle(verifyRequest())
        assertEquals(Outcome.Err(VerifyOtpFailure.Invalid), second)
    }

    @Test
    fun `wrong code, no such identifier, expired and attempts exhausted are the same value, not merely equal types`() {
        val unknown = verifyOtp().handle(verifyRequest(identifier = "no-such-identifier@example.com"))
        seed()
        val wrong = verifyOtp().handle(verifyRequest(code = "000000"))
        repeat(4) { assertEquals(Outcome.Err(VerifyOtpFailure.Invalid), verifyIgnoringResult("000000")) }
        val exhausted = verifyOtp().handle(verifyRequest())
        seed(identifier = "expires@example.com")
        val expired = verifyOtp(AFTER_EXPIRY).handle(verifyRequest(identifier = "expires@example.com"))
        val all = listOf(unknown, wrong, exhausted, expired)
        assertTrue(all.all { it == Outcome.Err(VerifyOtpFailure.Invalid) }, all.toString())
    }
}
