package com.housedash.domain.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class HoldRehydrationTest {
    @Test
    fun `every state round-trips through its row with the same id, figures and grounds`() {
        everyState().forEach { hold ->
            val back = EscrowHold.rehydrate(rowOf(hold))
            assertEquals(hold.state, back.state)
            assertEquals(hold.id, back.id)
            assertEquals(hold.acceptedTotal, back.acceptedTotal)
            assertEquals(hold.ledger, back.ledger)
            assertEquals(hold::class, back::class)
        }
        assertEquals(released().grounds, assertIs<ReleasedHold>(EscrowHold.rehydrate(rowOf(released()))).grounds)
    }

    @Test
    fun `a malformed id is refused`() {
        assertEquals(HoldFault.MALFORMED_ID, corruptOnLoad(holdRowWithId("cs_1")))
        assertEquals(HoldFault.MALFORMED_ID, corruptOnLoad(holdRowWithId("eh_")))
    }

    @Test
    fun `an unknown state is refused`() {
        assertEquals(HoldFault.UNKNOWN_STATE, corruptOnLoad(holdRow(state = "HELD")))
        assertEquals(HoldFault.UNKNOWN_STATE, corruptOnLoad(holdRow(state = "captured")))
    }

    @Test
    fun `a negative figure anywhere on the row is refused`() {
        assertEquals(HoldFault.NEGATIVE_FIGURE, corruptOnLoad(holdRow(acceptedTotal = -1)))
        assertEquals(HoldFault.NEGATIVE_FIGURE, corruptOnLoad(holdRow(ledger = ledgerRow(captured = -1))))
        assertEquals(HoldFault.NEGATIVE_FIGURE, corruptOnLoad(holdRow(ledger = ledgerRow(released = -1))))
        assertEquals(HoldFault.NEGATIVE_FIGURE, corruptOnLoad(holdRow(ledger = ledgerRow(refunded = -1))))
        assertEquals(HoldFault.NEGATIVE_FIGURE, corruptOnLoad(holdRow(ledger = ledgerRow(feePaid = -1))))
    }

    @Test
    fun `the standing invariant is refused directly, one cent over, on each figure that can leave`() {
        val overReleased = releasedRow(ledgerRow(released = 9_001))
        assertEquals(HoldFault.PAID_OUT_MORE_THAN_CAPTURED, corruptOnLoad(overReleased))
        val overRefunded = holdRow(state = "REFUNDED", ledger = ledgerRow(refunded = 9_001))
        assertEquals(HoldFault.PAID_OUT_MORE_THAN_CAPTURED, corruptOnLoad(overRefunded))
        val overPaid = holdRow(state = "PART_REFUND", ledger = ledgerRow(refunded = 7_200, feePaid = 1_801))
        assertEquals(HoldFault.PAID_OUT_MORE_THAN_CAPTURED, corruptOnLoad(overPaid))
    }

    @Test
    fun `the standing invariant is checked without overflowing at the top of a long`() {
        val over = ledgerRow(captured = Long.MAX_VALUE, released = Long.MAX_VALUE, refunded = 1)
        assertEquals(HoldFault.PAID_OUT_MORE_THAN_CAPTURED, corruptOnLoad(releasedRow(over)))
        val whole = ledgerRow(captured = Long.MAX_VALUE, released = Long.MAX_VALUE)
        assertEquals(HoldState.RELEASED, EscrowHold.rehydrate(releasedRow(whole)).state)
    }

    @Test
    fun `the ledger refuses the same figures on its own, which is the one place the invariant is written`() {
        val fault = assertFailsWith<CorruptHold> { Ledger(money(10), money(5), money(5), money(1)) }.fault
        assertEquals(HoldFault.PAID_OUT_MORE_THAN_CAPTURED, fault)
        assertEquals(money(10), Ledger(money(10), money(5), money(5), Money.ZERO).captured)
        val spelledOut = Ledger(money(10), Money.ZERO, Money.ZERO, Money.ZERO)
        assertEquals(Ledger.holding(money(10)), spelledOut)
        assertEquals(Ledger.holding(money(10)).hashCode(), spelledOut.hashCode())
        assertNotEquals(Ledger.holding(money(10)), Ledger.holding(money(11)))
    }

    @Test
    fun `a part refund row is not re-priced on load, so a fee the rule no longer produces still loads`() {
        val row = holdRow(state = "PART_REFUND", ledger = ledgerRow(refunded = 8_999, feePaid = 1))
        val hold = assertIs<PartRefundedHold>(EscrowHold.rehydrate(row))
        assertEquals(money(1), hold.feePaid)
    }
}
