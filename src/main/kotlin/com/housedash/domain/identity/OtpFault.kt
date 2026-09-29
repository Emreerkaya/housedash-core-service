package com.housedash.domain.identity

enum class OtpFault {
    MALFORMED_ID,
    MALFORMED_IDENTIFIER,
    UNKNOWN_IDENTIFIER_KIND,
    BLANK_CODE_HASH,
    ATTEMPTS_OUT_OF_RANGE,
}

class CorruptOtp internal constructor(
    val fault: OtpFault,
) : IllegalStateException(fault.name)
