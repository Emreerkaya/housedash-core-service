package com.housedash.domain.money

import com.housedash.domain.shared.Outcome

class AuthorizedHold private constructor(
    override val id: HoldId,
    override val acceptedTotal: Money,
    override val ledger: Ledger,
) : EscrowHold {
    init {
        if (ledger != Ledger.EMPTY) throw CorruptHold(HoldFault.FIGURE_PRESENT_BEFORE_CAPTURE)
    }

    override val state: HoldState get() = HoldState.AUTHORIZED

    override fun apply(command: HoldCommand): Outcome<EscrowHold, MoneyError> =
        when (command) {
            is HoldCommand.Capture -> CapturedHold.capturing(this, command.captured)
            else -> refusedIn(state, command)
        }

    internal companion object {
        fun of(
            id: HoldId,
            acceptedTotal: Money,
        ): AuthorizedHold = AuthorizedHold(id, acceptedTotal, Ledger.EMPTY)

        fun rehydrated(
            id: HoldId,
            acceptedTotal: Money,
            ledger: Ledger,
            grounds: ReleaseGrounds?,
        ): AuthorizedHold {
            groundsAbsentOrThrow(grounds)
            return AuthorizedHold(id, acceptedTotal, ledger)
        }
    }
}
