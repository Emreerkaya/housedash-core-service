package com.housedash.adapters.inbound.http

import com.housedash.app.IssueOtp
import com.housedash.app.IssueOtpRequest
import com.housedash.app.RequesterIp
import com.housedash.app.SubmittedIp
import com.housedash.app.VerifyOtp
import com.housedash.app.VerifyOtpRequest
import com.housedash.domain.shared.Outcome
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

const val OTP_ISSUE_PATH = "/otp/issue"

const val OTP_VERIFY_PATH = "/otp/verify"

const val CLIENT_IP_HEADER = "X-Forwarded-For"

private const val IP_WHEN_THE_HEADER_IS_ABSENT = "unknown"

@RestController
class OtpController(
    private val issueOtp: IssueOtp,
    private val verifyOtp: VerifyOtp,
) {
    @PostMapping(OTP_ISSUE_PATH)
    fun issue(
        @RequestHeader(name = CLIENT_IP_HEADER, required = false) ip: SubmittedIp?,
        @RequestBody(required = false) request: IssueOtpRequestBody?,
    ): ResponseEntity<OtpReply> {
        val requesterIp = (ip?.requesterIp() ?: RequesterIp(IP_WHEN_THE_HEADER_IS_ABSENT)).text
        return when (val outcome = issueOtp.handle(IssueOtpRequest(request?.identifier, requesterIp))) {
            is Outcome.Ok -> ResponseEntity.ok(OtpAccepted)
            is Outcome.Err -> ResponseEntity.status(statusFor(outcome.error)).body(OtpRefusal(reasonFor(outcome.error)))
        }
    }

    @PostMapping(OTP_VERIFY_PATH)
    fun verify(
        @RequestBody(required = false) request: VerifyOtpRequestBody?,
    ): ResponseEntity<OtpReply> =
        when (val outcome = verifyOtp.handle(VerifyOtpRequest(request?.identifier, request?.code))) {
            is Outcome.Ok -> ResponseEntity.ok(OtpAccepted)
            is Outcome.Err -> ResponseEntity.status(statusFor(outcome.error)).body(OtpRefusal(reasonFor(outcome.error)))
        }
}
