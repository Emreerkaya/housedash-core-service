package com.housedash.domain.money

import java.time.YearMonth

class Subscription private constructor(
    val id: SubscriptionId,
    val monthly: Money,
    val startedIn: YearMonth,
) {
    fun chargeFor(month: YearMonth): Money = if (month.isBefore(startedIn)) Money.ZERO else monthly

    companion object {
        fun flat(
            id: SubscriptionId,
            monthly: Money,
            startedIn: YearMonth,
        ): Subscription = Subscription(id, monthly, startedIn)
    }
}
