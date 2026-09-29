package com.housedash.app

import com.housedash.domain.identity.LoginIdentifier
import com.housedash.domain.identity.LoginIdentifierFlaw
import com.housedash.domain.shared.Outcome

class SubmittedIdentifier(
    private val submitted: String,
) {
    fun asLoginIdentifier(): Outcome<LoginIdentifier, LoginIdentifierFlaw> = LoginIdentifier.of(submitted)
}

class SubmittedCode(
    private val submitted: String,
) {
    fun matches(
        hash: String,
        hasher: OtpHasher,
    ): Boolean = hasher.hash(submitted) == hash
}

class SubmittedIp(
    private val submitted: String,
) {
    fun requesterIp(): RequesterIp = RequesterIp(submitted)
}

class RequesterIp(
    val text: String,
)
