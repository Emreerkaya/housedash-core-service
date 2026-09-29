package com.housedash.domain.money

import com.housedash.domain.shared.Outcome

enum class HoldState { AUTHORIZED, CAPTURED, RELEASED, REFUNDED, PART_REFUND }

data class ReleaseGrounds(
    val bookingCompleted: Boolean,
    val nesterConfirmed: Boolean,
) {
    val sufficient: Boolean get() = bookingCompleted && nesterConfirmed
}

sealed interface HoldCommand {
    data class Capture(
        val captured: Money,
    ) : HoldCommand

    data class Release(
        val grounds: ReleaseGrounds,
    ) : HoldCommand

    data object Refund : HoldCommand

    data class RefundLessCallOutFee(
        val ending: VisitEnding,
    ) : HoldCommand
}

sealed interface EscrowHold {
    val id: HoldId
    val acceptedTotal: Money
    val state: HoldState
    val ledger: Ledger

    fun apply(command: HoldCommand): Outcome<EscrowHold, MoneyError>

    companion object {
        fun authorize(
            id: HoldId,
            acceptedTotal: Money,
        ): AuthorizedHold = AuthorizedHold.of(id, acceptedTotal)

        fun rehydrate(row: HoldRow): EscrowHold {
            val id = holdIdOrThrow(row.id)
            val acceptedTotal = figureOrThrow(row.acceptedTotalCents)
            val ledger = ledgerOf(row.ledger)
            val grounds = groundsOf(row)
            return when (stateOrThrow(row.state)) {
                HoldState.AUTHORIZED -> AuthorizedHold.rehydrated(id, acceptedTotal, ledger, grounds)
                HoldState.CAPTURED -> CapturedHold.rehydrated(id, acceptedTotal, ledger, grounds)
                HoldState.RELEASED -> ReleasedHold.rehydrated(id, acceptedTotal, ledger, grounds)
                HoldState.REFUNDED -> RefundedHold.rehydrated(id, acceptedTotal, ledger, grounds)
                HoldState.PART_REFUND -> PartRefundedHold.rehydrated(id, acceptedTotal, ledger, grounds)
            }
        }
    }
}

internal fun refusedIn(
    state: HoldState,
    command: HoldCommand,
): Outcome<EscrowHold, MoneyError> = Outcome.Err(MoneyError.NotAllowedIn(state, command))

internal fun groundsAbsentOrThrow(grounds: ReleaseGrounds?) {
    if (grounds != null) throw CorruptHold(HoldFault.RELEASE_GROUNDS_PRESENT_BEFORE_RELEASE)
}

internal fun capturedCoversAcceptedTotalOrThrow(
    ledger: Ledger,
    acceptedTotal: Money,
) {
    if (ledger.captured < acceptedTotal) throw CorruptHold(HoldFault.CAPTURED_BELOW_ACCEPTED_TOTAL)
}

private fun ledgerOf(row: LedgerRow): Ledger =
    Ledger(
        figureOrThrow(row.capturedCents),
        figureOrThrow(row.releasedCents),
        figureOrThrow(row.refundedCents),
        figureOrThrow(row.feePaidCents),
    )

private fun groundsOf(row: HoldRow): ReleaseGrounds? {
    val completed = row.bookingCompleted
    val confirmed = row.nesterConfirmed
    if (completed == null && confirmed == null) return null
    if (completed == null || confirmed == null) throw CorruptHold(HoldFault.RELEASE_GROUNDS_ABSENT)
    return ReleaseGrounds(completed, confirmed)
}

private fun stateOrThrow(raw: String): HoldState =
    HoldState.entries.firstOrNull { it.name == raw } ?: throw CorruptHold(HoldFault.UNKNOWN_STATE)

private fun holdIdOrThrow(raw: String): HoldId =
    when (val parsed = HoldId.of(raw)) {
        is Outcome.Ok -> parsed.value
        is Outcome.Err -> throw CorruptHold(HoldFault.MALFORMED_ID)
    }

private fun figureOrThrow(cents: Long): Money =
    when (val parsed = Money.of(cents)) {
        is Outcome.Ok -> parsed.value
        is Outcome.Err -> throw CorruptHold(HoldFault.NEGATIVE_FIGURE)
    }
