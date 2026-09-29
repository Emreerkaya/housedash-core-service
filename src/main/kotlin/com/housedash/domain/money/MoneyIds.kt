package com.housedash.domain.money

import com.housedash.domain.shared.Identifier
import com.housedash.domain.shared.IdentifierShape
import com.housedash.domain.shared.Outcome

class HoldId private constructor(
    value: String,
) : Identifier(value, SHAPE) {
    override fun toString(): String = value

    companion object {
        const val MAX_BODY_LENGTH = 64

        private val SHAPE = IdentifierShape("eh_", MAX_BODY_LENGTH)

        fun of(raw: String): Outcome<HoldId, MoneyError> =
            SHAPE
                .check(raw)
                .mapError(MoneyError::MalformedHoldId)
                .map { HoldId(it) }
    }
}

class SubscriptionId private constructor(
    value: String,
) : Identifier(value, SHAPE) {
    override fun toString(): String = value

    companion object {
        const val MAX_BODY_LENGTH = 64

        private val SHAPE = IdentifierShape("sb_", MAX_BODY_LENGTH)

        fun of(raw: String): Outcome<SubscriptionId, MoneyError> =
            SHAPE
                .check(raw)
                .mapError(MoneyError::MalformedSubscriptionId)
                .map { SubscriptionId(it) }
    }
}
