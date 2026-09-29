package com.housedash.app

import com.housedash.domain.identity.IdentityError
import com.housedash.domain.identity.OtpId
import com.housedash.domain.shared.Outcome
import java.util.UUID

fun interface OtpIdentifiers {
    fun next(): Outcome<OtpId, IdentityError>
}

private const val OTP_ID_PREFIX = "otp_"

private const val MARK_UUID_GROUPS_ARE_SPLIT_BY = "-"

class RandomOtpIdentifiers : OtpIdentifiers {
    override fun next(): Outcome<OtpId, IdentityError> =
        OtpId.of(OTP_ID_PREFIX + UUID.randomUUID().toString().replace(MARK_UUID_GROUPS_ARE_SPLIT_BY, ""))
}
