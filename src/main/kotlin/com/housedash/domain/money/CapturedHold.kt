package com.housedash.domain.money

import com.housedash.domain.shared.Outcome

class CapturedHold private constructor(
    override val id: HoldId,
    override val acceptedTotal: Money,
    override val ledger: Ledger,
) : EscrowHold {
    init {
        capturedCoversAcceptedTotalOrThrow(ledger, acceptedTotal)
        if (!ledger.nothingHasLeft) throw CorruptHold(HoldFault.PAID_OUT_WHILE_STILL_HELD)
    }

    override val state: HoldState get() = HoldState.CAPTURED

    val captured: Money get() = ledger.captured

    override fun apply(command: HoldCommand): Outcome<EscrowHold, MoneyError> =
        when (command) {
            is HoldCommand.Release -> ReleasedHold.releasing(this, command.grounds)
            is HoldCommand.Refund -> Outcome.Ok(RefundedHold.refunding(this))
            is HoldCommand.RefundLessCallOutFee -> PartRefundedHold.settling(this, command.ending)
            is HoldCommand.Capture -> refusedIn(state, command)
        }

    internal companion object {
        fun capturing(
            authorized: AuthorizedHold,
            captured: Money,
        ): Outcome<CapturedHold, MoneyError> {
            if (captured < authorized.acceptedTotal) {
                return Outcome.Err(MoneyError.CapturedBelowAcceptedTotal(captured, authorized.acceptedTotal))
            }
            return Outcome.Ok(CapturedHold(authorized.id, authorized.acceptedTotal, Ledger.holding(captured)))
        }

        fun rehydrated(
            id: HoldId,
            acceptedTotal: Money,
            ledger: Ledger,
            grounds: ReleaseGrounds?,
        ): CapturedHold {
            groundsAbsentOrThrow(grounds)
            return CapturedHold(id, acceptedTotal, ledger)
        }
    }
}
