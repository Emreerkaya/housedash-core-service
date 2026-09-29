package com.housedash.adapters.outbound.otp

import com.housedash.app.OtpSender
import com.housedash.domain.identity.LoginIdentifier
import org.slf4j.LoggerFactory

class LoggingOtpSender : OtpSender {
    override fun send(
        identifier: LoginIdentifier,
        code: String,
    ) {
        LOGGER.info("no delivery account is configured; the code for {} is {}", identifier, code)
    }

    private companion object {
        val LOGGER = LoggerFactory.getLogger(LoggingOtpSender::class.java)
    }
}
