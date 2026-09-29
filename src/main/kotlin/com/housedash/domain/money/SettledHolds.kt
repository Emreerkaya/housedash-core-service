package com.housedash.domain.money

import com.housedash.domain.shared.Outcome

sealed interface SettledHold : EscrowHold {
    override fun apply(command: HoldCommand): Outcome<EscrowHold, MoneyError> = refusedIn(state, command)
}

class ReleasedHold private constructor(
    override val id: HoldId,
    override val acceptedTotal: Money,
    override val ledger: Ledger,
    val grounds: ReleaseGrounds,
) : SettledHold {
    init {
        capturedCoversAcceptedTotalOrThrow(ledger, acceptedTotal)
        if (ledger.released != ledger.captured) throw CorruptHold(HoldFault.RELEASED_SHORT_OF_CAPTURED)
        if (!grounds.bookingCompleted) throw CorruptHold(HoldFault.RELEASED_WITHOUT_COMPLETION)
        if (!grounds.nesterConfirmed) throw CorruptHold(HoldFault.RELEASED_WITHOUT_CONFIRMATION)
    }

    override val state: HoldState get() = HoldState.RELEASED

    internal companion object {
        fun releasing(
            held: CapturedHold,
            grounds: ReleaseGrounds,
        ): Outcome<ReleasedHold, MoneyError> {
            if (!grounds.sufficient) return Outcome.Err(MoneyError.ReleaseRefused(grounds))
            val ledger = Ledger(held.captured, held.captured, Money.ZERO, Money.ZERO)
            return Outcome.Ok(ReleasedHold(held.id, held.acceptedTotal, ledger, grounds))
        }

        fun rehydrated(
            id: HoldId,
            acceptedTotal: Money,
            ledger: Ledger,
            grounds: ReleaseGrounds?,
        ): ReleasedHold {
            if (grounds == null) throw CorruptHold(HoldFault.RELEASE_GROUNDS_ABSENT)
            return ReleasedHold(id, acceptedTotal, ledger, grounds)
        }
    }
}

class RefundedHold private constructor(
    override val id: HoldId,
    override val acceptedTotal: Money,
    override val ledger: Ledger,
) : SettledHold {
    init {
        capturedCoversAcceptedTotalOrThrow(ledger, acceptedTotal)
        if (ledger.refunded != ledger.captured) throw CorruptHold(HoldFault.REFUNDED_SHORT_OF_CAPTURED)
    }

    override val state: HoldState get() = HoldState.REFUNDED

    internal companion object {
        fun refunding(held: CapturedHold): RefundedHold {
            val ledger = Ledger(held.captured, Money.ZERO, held.captured, Money.ZERO)
            return RefundedHold(held.id, held.acceptedTotal, ledger)
        }

        fun rehydrated(
            id: HoldId,
            acceptedTotal: Money,
            ledger: Ledger,
            grounds: ReleaseGrounds?,
        ): RefundedHold {
            groundsAbsentOrThrow(grounds)
            return RefundedHold(id, acceptedTotal, ledger)
        }
    }
}

class PartRefundedHold private constructor(
    override val id: HoldId,
    override val acceptedTotal: Money,
    override val ledger: Ledger,
) : SettledHold {
    init {
        capturedCoversAcceptedTotalOrThrow(ledger, acceptedTotal)
        if (ledger.captured - ledger.feePaid != ledger.refunded) {
            throw CorruptHold(HoldFault.PART_REFUND_DOES_NOT_ADD_UP_TO_CAPTURED)
        }
    }

    override val state: HoldState get() = HoldState.PART_REFUND

    val feePaid: Money get() = ledger.feePaid

    val refunded: Money get() = ledger.refunded

    internal companion object {
        fun settling(
            held: CapturedHold,
            ending: VisitEnding,
        ): Outcome<PartRefundedHold, MoneyError> {
            if (!CallOutFee.payableOn(ending)) return Outcome.Err(MoneyError.CallOutFeeNotOwed(ending))
            val fee = CallOutFee.of(held.acceptedTotal)
            val ledger = Ledger(held.captured, Money.ZERO, held.captured - fee, fee)
            return Outcome.Ok(PartRefundedHold(held.id, held.acceptedTotal, ledger))
        }

        fun rehydrated(
            id: HoldId,
            acceptedTotal: Money,
            ledger: Ledger,
            grounds: ReleaseGrounds?,
        ): PartRefundedHold {
            groundsAbsentOrThrow(grounds)
            return PartRefundedHold(id, acceptedTotal, ledger)
        }
    }
}
