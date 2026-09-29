package com.housedash.domain.money

import com.housedash.domain.shared.IdentifierFlaw

sealed interface MoneyError {
    data class NegativeAmount(
        val cents: Long,
    ) : MoneyError

    data class MalformedHoldId(
        val flaw: IdentifierFlaw,
    ) : MoneyError

    data class MalformedSubscriptionId(
        val flaw: IdentifierFlaw,
    ) : MoneyError

    data class CapturedBelowAcceptedTotal(
        val captured: Money,
        val acceptedTotal: Money,
    ) : MoneyError

    data class NotAllowedIn(
        val state: HoldState,
        val command: HoldCommand,
    ) : MoneyError

    data class ReleaseRefused(
        val grounds: ReleaseGrounds,
    ) : MoneyError

    data class CallOutFeeNotOwed(
        val ending: VisitEnding,
    ) : MoneyError
}
