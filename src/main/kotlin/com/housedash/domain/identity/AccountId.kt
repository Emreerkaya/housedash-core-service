package com.housedash.domain.identity

import com.housedash.domain.shared.Identifier
import com.housedash.domain.shared.IdentifierShape
import com.housedash.domain.shared.Outcome

class AccountId private constructor(
    value: String,
) : Identifier(value, SHAPE) {
    override fun toString(): String = value

    companion object {
        const val MAX_BODY_LENGTH = 64

        private val SHAPE = IdentifierShape("ac_", MAX_BODY_LENGTH)

        fun of(raw: String): Outcome<AccountId, IdentityError> =
            SHAPE
                .check(raw)
                .mapError(IdentityError::MalformedAccountId)
                .map { AccountId(it) }
    }
}
