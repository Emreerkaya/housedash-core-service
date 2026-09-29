package com.housedash.domain.money

import java.lang.reflect.Modifier
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val STARTED_IN: YearMonth = YearMonth.of(2026, 10)

class SubscriptionTest {
    private val subscription = Subscription.flat(subscriptionId(), money(4_900), STARTED_IN)

    @Test
    fun `a subscription carries its id, one monthly price and the month it started`() {
        assertEquals(subscriptionId(), subscription.id)
        assertEquals(money(4_900), subscription.monthly)
        assertEquals(STARTED_IN, subscription.startedIn)
    }

    @Test
    fun `the charge for the starting month and every month after it is the monthly price and nothing else`() {
        assertEquals(money(4_900), subscription.chargeFor(STARTED_IN))
        Samples().repeat { it.cents(1_200) }.forEach { monthsOn ->
            assertEquals(money(4_900), subscription.chargeFor(STARTED_IN.plusMonths(monthsOn)), "$monthsOn")
        }
    }

    @Test
    fun `there is no charge for a month before the subscription started`() {
        assertEquals(Money.ZERO, subscription.chargeFor(STARTED_IN.minusMonths(1)))
        assertEquals(Money.ZERO, subscription.chargeFor(STARTED_IN.minusYears(10)))
    }

    @Test
    fun `nothing on a subscription takes a count, a case or a visit, so nothing on it can be metered`() {
        val parameterTypes =
            Subscription::class.java.declaredMethods
                .filterNot { it.isSynthetic }
                .flatMap { method -> method.parameterTypes.map { it.name } }
                .toSet()
        assertEquals(setOf(YearMonth::class.java.name), parameterTypes)
        val fields = Subscription::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
        val fieldTypes = fields.map { it.type.name }.toSet()
        val expected = listOf(SubscriptionId::class.java, Money::class.java, YearMonth::class.java).map { it.name }
        assertEquals(expected.toSet(), fieldTypes)
        assertTrue(fields.none { it.type.isPrimitive })
    }
}
