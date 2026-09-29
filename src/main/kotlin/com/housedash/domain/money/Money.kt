package com.housedash.domain.money

import com.housedash.domain.shared.Outcome

private const val PERCENT_DENOMINATOR = 100L

private const val HALF_OF_THE_DENOMINATOR = 50L

class Money(
    val cents: Long,
) : Comparable<Money> {
    init {
        require(cents >= 0) { "money is a count of minor units and never negative" }
    }

    operator fun minus(other: Money): Money {
        require(other <= this) { "money is never negative, so the subtrahend may not exceed the minuend" }
        return Money(cents - other.cents)
    }

    fun percentRoundedHalfUp(percent: Long): Money {
        require(percent in 0..PERCENT_DENOMINATOR) { "a percentage of a sum lies between nothing and the whole" }
        val scaled = Math.addExact(Math.multiplyExact(cents, percent), HALF_OF_THE_DENOMINATOR)
        return Money(scaled / PERCENT_DENOMINATOR)
    }

    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    override fun equals(other: Any?): Boolean = other is Money && other.cents == cents

    override fun hashCode(): Int = cents.hashCode()

    override fun toString(): String = "Money($cents)"

    companion object {
        val ZERO = Money(0)

        fun of(cents: Long): Outcome<Money, MoneyError> =
            if (cents < 0) Outcome.Err(MoneyError.NegativeAmount(cents)) else Outcome.Ok(Money(cents))
    }
}
