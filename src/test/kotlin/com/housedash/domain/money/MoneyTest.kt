package com.housedash.domain.money

import com.housedash.domain.shared.Outcome
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val LARGEST_CENTS_A_FIFTH_CAN_BE_TAKEN_OF = (Long.MAX_VALUE - 50) / 20

class MoneyTest {
    @Test
    fun `a negative count of cents is refused by the constructor`() {
        assertFailsWith<IllegalArgumentException> { Money(-1) }
        assertFailsWith<IllegalArgumentException> { Money(Long.MIN_VALUE) }
    }

    @Test
    fun `a negative count of cents is refused by the parse with the figure named`() {
        assertEquals(MoneyError.NegativeAmount(-1), err(Money.of(-1)))
        assertEquals(MoneyError.NegativeAmount(Long.MIN_VALUE), err(Money.of(Long.MIN_VALUE)))
    }

    @Test
    fun `zero and the largest long are both money`() {
        assertEquals(Money.ZERO, ok(Money.of(0)))
        assertEquals(0, Money.ZERO.cents)
        assertEquals(Long.MAX_VALUE, ok(Money.of(Long.MAX_VALUE)).cents)
    }

    @Test
    fun `equality and hashing follow the cents, and the string names them`() {
        assertEquals(money(1_800), money(1_800))
        assertEquals(money(1_800).hashCode(), money(1_800).hashCode())
        assertNotEquals(money(1_800), money(1_801))
        assertEquals("Money(1800)", money(1_800).toString())
    }

    @Test
    fun `ordering follows the cents`() {
        assertTrue(money(1) < money(2))
        assertTrue(money(2) > money(1))
        assertTrue(money(2) >= money(2))
        assertEquals(0, money(2).compareTo(money(2)))
    }

    @Test
    fun `subtraction never produces negative money`() {
        assertEquals(money(7_200), money(9_000) - money(1_800))
        assertEquals(Money.ZERO, money(9_000) - money(9_000))
        assertFailsWith<IllegalArgumentException> { money(1_800) - money(1_801) }
    }

    @Test
    fun `a percentage rounded half up agrees with the decimal rule on random cents and percents`() {
        val disagreements =
            Samples()
                .repeat { it.smallCents() to it.percent() }
                .filter { (cents, percent) -> disagrees(cents, percent) }
        assertEquals(emptyList(), disagreements)
    }

    @Test
    fun `the half is rounded up, below it down, and the plus fifty is what does it`() {
        assertEquals(money(13), money(25).percentRoundedHalfUp(50))
        assertEquals(money(1), money(5).percentRoundedHalfUp(10))
        assertEquals(money(1), money(3).percentRoundedHalfUp(20))
        assertEquals(money(0), money(2).percentRoundedHalfUp(20))
        assertEquals(money(1_801), money(9_003).percentRoundedHalfUp(20))
        assertEquals(money(1_800), money(9_002).percentRoundedHalfUp(20))
    }

    @Test
    fun `a percentage outside nothing to the whole is refused`() {
        assertFailsWith<IllegalArgumentException> { money(100).percentRoundedHalfUp(-1) }
        assertFailsWith<IllegalArgumentException> { money(100).percentRoundedHalfUp(101) }
        assertEquals(Money.ZERO, money(100).percentRoundedHalfUp(0))
        assertEquals(money(100), money(100).percentRoundedHalfUp(100))
    }

    @Test
    fun `the largest cents a fifth can be taken of is one below where the multiplication overflows`() {
        val atTheEdge = money(LARGEST_CENTS_A_FIFTH_CAN_BE_TAKEN_OF).percentRoundedHalfUp(20)
        assertEquals(halfUp(LARGEST_CENTS_A_FIFTH_CAN_BE_TAKEN_OF, 20), atTheEdge.cents)
        assertFailsWith<ArithmeticException> {
            money(LARGEST_CENTS_A_FIFTH_CAN_BE_TAKEN_OF + 1).percentRoundedHalfUp(20)
        }
        assertFailsWith<ArithmeticException> { money(Long.MAX_VALUE).percentRoundedHalfUp(100) }
        assertEquals(Money.ZERO, money(Long.MAX_VALUE).percentRoundedHalfUp(0))
    }

    @Test
    fun `the parse and the constructor agree on every accepted value`() {
        Samples().repeat { it.anyCents() }.forEach { cents ->
            assertEquals(Money(cents), assertIs<Outcome.Ok<Money>>(Money.of(cents)).value)
        }
    }

    private fun disagrees(
        cents: Long,
        percent: Long,
    ): Boolean = money(cents).percentRoundedHalfUp(percent) != money(halfUp(cents, percent))

    private fun halfUp(
        cents: Long,
        percent: Long,
    ): Long =
        BigDecimal(cents)
            .multiply(BigDecimal(percent))
            .divide(BigDecimal(100), 0, RoundingMode.HALF_UP)
            .longValueExact()
}
