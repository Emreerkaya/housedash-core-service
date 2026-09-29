package com.housedash.domain.money

import kotlin.test.Test
import kotlin.test.assertEquals

private val NOT_YET_RELEASED = listOf("AUTHORIZED", "CAPTURED", "REFUNDED", "PART_REFUND")

private fun ledgerFor(state: String): LedgerRow =
    when (state) {
        "AUTHORIZED" -> ledgerRow(captured = 0)
        "CAPTURED" -> ledgerRow()
        "REFUNDED" -> ledgerRow(refunded = 9_000)
        else -> ledgerRow(refunded = 7_200, feePaid = 1_800)
    }

class HoldShapeFaultTest {
    @Test
    fun `an authorized hold with any figure on its ledger is refused`() {
        val captured = holdRow(state = "AUTHORIZED")
        assertEquals(HoldFault.FIGURE_PRESENT_BEFORE_CAPTURE, corruptOnLoad(captured))
        val refunded = holdRow(state = "AUTHORIZED", ledger = ledgerRow(captured = 5, refunded = 5))
        assertEquals(HoldFault.FIGURE_PRESENT_BEFORE_CAPTURE, corruptOnLoad(refunded))
        val empty = holdRow(state = "AUTHORIZED", ledger = ledgerRow(captured = 0))
        assertEquals(HoldState.AUTHORIZED, EscrowHold.rehydrate(empty).state)
    }

    @Test
    fun `a captured figure below the accepted total is refused in every state past authorization`() {
        val held = holdRow(state = "CAPTURED", ledger = ledgerRow(captured = 8_999))
        assertEquals(HoldFault.CAPTURED_BELOW_ACCEPTED_TOTAL, corruptOnLoad(held))
        val released = releasedRow(ledgerRow(captured = 8_999, released = 8_999))
        assertEquals(HoldFault.CAPTURED_BELOW_ACCEPTED_TOTAL, corruptOnLoad(released))
        val refunded = holdRow(state = "REFUNDED", ledger = ledgerRow(captured = 8_999, refunded = 8_999))
        assertEquals(HoldFault.CAPTURED_BELOW_ACCEPTED_TOTAL, corruptOnLoad(refunded))
        val partShort = ledgerRow(captured = 8_999, refunded = 7_199, feePaid = 1_800)
        val part = holdRow(state = "PART_REFUND", ledger = partShort)
        assertEquals(HoldFault.CAPTURED_BELOW_ACCEPTED_TOTAL, corruptOnLoad(part))
    }

    @Test
    fun `a captured hold from which anything has already left is refused`() {
        listOf(ledgerRow(released = 1), ledgerRow(refunded = 1), ledgerRow(feePaid = 1)).forEach { ledger ->
            val row = holdRow(state = "CAPTURED", ledger = ledger)
            assertEquals(HoldFault.PAID_OUT_WHILE_STILL_HELD, corruptOnLoad(row))
        }
    }

    @Test
    fun `a released hold that released less than it captured is refused`() {
        assertEquals(HoldFault.RELEASED_SHORT_OF_CAPTURED, corruptOnLoad(releasedRow(ledgerRow(released = 8_999))))
    }

    @Test
    fun `a released hold without both grounds is refused, completion first, the guard no command reaches`() {
        val unconfirmed = releasedRow(completed = true, confirmed = false)
        assertEquals(HoldFault.RELEASED_WITHOUT_CONFIRMATION, corruptOnLoad(unconfirmed))
        val incomplete = releasedRow(completed = false, confirmed = true)
        assertEquals(HoldFault.RELEASED_WITHOUT_COMPLETION, corruptOnLoad(incomplete))
        val neither = releasedRow(completed = false, confirmed = false)
        assertEquals(HoldFault.RELEASED_WITHOUT_COMPLETION, corruptOnLoad(neither))
    }

    @Test
    fun `a released hold whose grounds are missing, wholly or by half, is refused`() {
        assertEquals(HoldFault.RELEASE_GROUNDS_ABSENT, corruptOnLoad(releasedRow(completed = null, confirmed = null)))
        assertEquals(HoldFault.RELEASE_GROUNDS_ABSENT, corruptOnLoad(releasedRow(completed = true, confirmed = null)))
        assertEquals(HoldFault.RELEASE_GROUNDS_ABSENT, corruptOnLoad(releasedRow(completed = null, confirmed = true)))
    }

    @Test
    fun `grounds on a hold that has not been released are refused in each of the four states`() {
        NOT_YET_RELEASED.forEach { state ->
            val ledger = ledgerFor(state)
            val both = holdRow(state = state, ledger = ledger, bookingCompleted = true, nesterConfirmed = true)
            assertEquals(HoldFault.RELEASE_GROUNDS_PRESENT_BEFORE_RELEASE, corruptOnLoad(both), state)
            val half = holdRow(state = state, ledger = ledgerFor(state), bookingCompleted = true)
            assertEquals(HoldFault.RELEASE_GROUNDS_ABSENT, corruptOnLoad(half), state)
        }
        assertEquals(4, NOT_YET_RELEASED.size)
    }

    @Test
    fun `a refunded hold that refunded less than it captured is refused`() {
        val short = holdRow(state = "REFUNDED", ledger = ledgerRow(refunded = 8_999))
        assertEquals(HoldFault.REFUNDED_SHORT_OF_CAPTURED, corruptOnLoad(short))
    }

    @Test
    fun `a part refund whose fee and refund do not add up to what was captured is refused`() {
        val short = holdRow(state = "PART_REFUND", ledger = ledgerRow(refunded = 7_199, feePaid = 1_800))
        assertEquals(HoldFault.PART_REFUND_DOES_NOT_ADD_UP_TO_CAPTURED, corruptOnLoad(short))
        val whole = holdRow(state = "PART_REFUND", ledger = ledgerRow(refunded = 7_200, feePaid = 1_800))
        assertEquals(HoldState.PART_REFUND, EscrowHold.rehydrate(whole).state)
    }
}
