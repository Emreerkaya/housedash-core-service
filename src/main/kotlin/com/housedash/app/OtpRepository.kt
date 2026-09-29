package com.housedash.app

import com.housedash.domain.identity.IssuedOtp
import com.housedash.domain.identity.LoginIdentifier
import java.time.Instant

interface OtpRepository {
    fun latestFor(identifier: LoginIdentifier): IssuedOtp?

    fun issuedCountSince(
        identifier: LoginIdentifier,
        since: Instant,
    ): Int

    fun issuedCountFromIpSince(
        ip: String,
        since: Instant,
    ): Int

    fun insert(
        otp: IssuedOtp,
        requesterIp: String,
    )

    fun update(otp: IssuedOtp)
}
