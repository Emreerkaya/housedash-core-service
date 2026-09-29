package com.housedash.domain.money

class Ledger(
    val captured: Money,
    val released: Money,
    val refunded: Money,
    val feePaid: Money,
) {
    init {
        if (paysOutMoreThanCaptured()) throw CorruptHold(HoldFault.PAID_OUT_MORE_THAN_CAPTURED)
    }

    private fun paysOutMoreThanCaptured(): Boolean {
        if (released > captured) return true
        val afterRelease = captured - released
        if (refunded > afterRelease) return true
        return feePaid > afterRelease - refunded
    }

    val nothingHasLeft: Boolean
        get() = released == Money.ZERO && refunded == Money.ZERO && feePaid == Money.ZERO

    override fun equals(other: Any?): Boolean =
        other is Ledger &&
            other.captured == captured &&
            other.released == released &&
            other.refunded == refunded &&
            other.feePaid == feePaid

    override fun hashCode(): Int = listOf(captured, released, refunded, feePaid).hashCode()

    override fun toString(): String {
        val figures = "captured=$captured, released=$released, refunded=$refunded, feePaid=$feePaid"
        return "Ledger($figures)"
    }

    companion object {
        val EMPTY = Ledger(Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO)

        fun holding(captured: Money): Ledger = Ledger(captured, Money.ZERO, Money.ZERO, Money.ZERO)
    }
}
