package com.housedash.app

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.housedash.adapters.outbound.otp.LoggingOtpSender
import com.housedash.domain.identity.CorruptOtp
import com.housedash.domain.identity.IssuedOtp
import com.housedash.domain.identity.LoginIdentifier
import com.housedash.domain.identity.OtpFault
import com.housedash.domain.identity.OtpRow
import com.housedash.domain.identity.StoredIdentifier
import com.housedash.domain.shared.Outcome
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class SilentOtpSender : OtpSender {
    override fun send(
        identifier: LoginIdentifier,
        code: String,
    ) = Unit
}

private fun anIdentifier(): LoginIdentifier {
    val parsed = LoginIdentifier.of(AN_IDENTIFIER)
    return assertIs<Outcome.Ok<LoginIdentifier>>(parsed).value
}

class OtpLogSafetyTest {
    @Test
    fun `a full issue and verify cycle, including every kind of refusal, never once logs the code`() {
        val otps = InMemoryOtpRepository()
        val appender = attachedListAppender(Logger.ROOT_LOGGER_NAME)
        try {
            val delivery = OtpDelivery(FixedOtpCodes(), TEST_HASHER, SilentOtpSender())
            val issue = IssueOtp(otps, CountingOtpIdentifiers(), delivery, OTP_CLOCK)
            val verify = VerifyOtp(otps, TEST_HASHER, OTP_CLOCK)
            val results =
                listOf(
                    issue.handle(issueRequest()),
                    verify.handle(verifyRequest(code = "000000")),
                    verify.handle(verifyRequest()),
                    issue.handle(issueRequest(identifier = null)),
                    issue.handle(issueRequest(identifier = "not an identifier")),
                    verify.handle(verifyRequest(identifier = null)),
                    verify.handle(verifyRequest(identifier = "never-issued@example.com")),
                )
            assertEquals(7, results.size)
        } finally {
            detach(Logger.ROOT_LOGGER_NAME, appender)
        }
        val logged = appender.list.joinToString("\n") { "${it.formattedMessage} ${it.argumentArray?.joinToString()}" }
        assertFalse(logged.contains(A_CODE), "the code must never reach a log line: $logged")
    }

    @Test
    fun `no failure value or exception message this feature can produce carries the code`() {
        val otps = InMemoryOtpRepository()
        val delivery = OtpDelivery(FixedOtpCodes(), TEST_HASHER, SilentOtpSender())
        val issue = IssueOtp(otps, CountingOtpIdentifiers(), delivery, OTP_CLOCK)
        val verify = VerifyOtp(otps, TEST_HASHER, OTP_CLOCK)
        val seeded = issue.handle(issueRequest())
        assertTrue(seeded is Outcome.Ok)
        val outcomes =
            listOf(
                verify.handle(verifyRequest(code = "000000")).toString(),
                verify.handle(verifyRequest(identifier = "not an identifier")).toString(),
                issue.handle(issueRequest(identifier = null)).toString(),
            )
        outcomes.forEach { assertFalse(it.contains(A_CODE), it) }
        val identifier = StoredIdentifier(AN_IDENTIFIER, "EMAIL")
        val corrupt =
            assertFailsWith<CorruptOtp> {
                IssuedOtp.rehydrate(OtpRow("otp_1", identifier, "", OTP_ISSUED_AT, 0, false))
            }
        assertEquals(OtpFault.BLANK_CODE_HASH, corrupt.fault)
        assertFalse(corrupt.message.orEmpty().contains(A_CODE))
    }

    @Test
    fun `the development sender is the one sanctioned place the code is written, because no delivery account exists`() {
        val appender = attachedListAppender(LoggingOtpSender::class.java.name)
        try {
            LoggingOtpSender().send(anIdentifier(), A_CODE)
        } finally {
            detach(LoggingOtpSender::class.java.name, appender)
        }
        val logged = appender.list.joinToString("\n") { it.formattedMessage }
        assertTrue(logged.contains(A_CODE), "the dev sender exists precisely so a demo can read the code here: $logged")
    }
}

private fun attachedListAppender(loggerName: String): ListAppender<ILoggingEvent> {
    val appender = ListAppender<ILoggingEvent>()
    appender.start()
    (LoggerFactory.getLogger(loggerName) as Logger).addAppender(appender)
    return appender
}

private fun detach(
    loggerName: String,
    appender: ListAppender<ILoggingEvent>,
) {
    (LoggerFactory.getLogger(loggerName) as Logger).detachAppender(appender)
}
