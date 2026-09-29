package com.housedash.domain.identity

import com.housedash.domain.shared.IdentifierFlaw

sealed interface IdentityError {
    data class MalformedAccountId(
        val flaw: IdentifierFlaw,
    ) : IdentityError

    data class MalformedOtpId(
        val flaw: IdentifierFlaw,
    ) : IdentityError

    data class MalformedIdentifier(
        val flaw: LoginIdentifierFlaw,
    ) : IdentityError

    data class AlreadyHasProfile(
        val kind: ProfileKind,
    ) : IdentityError
}
