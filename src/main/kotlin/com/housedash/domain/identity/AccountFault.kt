package com.housedash.domain.identity

enum class AccountFault {
    MALFORMED_ID,
    MALFORMED_IDENTIFIER,
    UNKNOWN_IDENTIFIER_KIND,
    UNKNOWN_PROFILE_KIND,
    NO_PROFILES,
    DUPLICATE_PROFILE,
}

class CorruptAccount internal constructor(
    val fault: AccountFault,
) : IllegalStateException(fault.name)
