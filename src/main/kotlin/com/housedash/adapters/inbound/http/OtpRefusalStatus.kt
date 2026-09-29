package com.housedash.adapters.inbound.http

import com.housedash.app.IssueOtpFailure
import com.housedash.app.VerifyOtpFailure
import org.springframework.http.HttpStatus

fun statusFor(failure: IssueOtpFailure): HttpStatus =
    when (failure) {
        IssueOtpFailure.IdentifierAbsent -> HttpStatus.BAD_REQUEST
        is IssueOtpFailure.MalformedIdentifier -> HttpStatus.BAD_REQUEST
        IssueOtpFailure.RateLimited -> HttpStatus.TOO_MANY_REQUESTS
        is IssueOtpFailure.OtpIdRefused -> HttpStatus.INTERNAL_SERVER_ERROR
    }

fun reasonFor(failure: IssueOtpFailure): String = failure.javaClass.simpleName

fun statusFor(failure: VerifyOtpFailure): HttpStatus =
    when (failure) {
        VerifyOtpFailure.Invalid -> HttpStatus.UNAUTHORIZED
    }

fun reasonFor(failure: VerifyOtpFailure): String =
    when (failure) {
        VerifyOtpFailure.Invalid -> "Invalid"
    }
