package com.housedash.domain.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EscrowHoldTest {
    @Test
    fun `an authorized hold carries its id and the accepted total and has captured nothing`() {
        val hold = authorized(money(9_000))
        assertEquals(holdId(), hold.id)
        assertEquals(money(9_000), hold.acceptedTotal)
        assertEquals(HoldState.AUTHORIZED, hold.state)
        assertEquals(Ledger.EMPTY, hold.ledger)
    }

    @Test
    fun `capturing the accepted total moves the hold to captured with that figure and nothing gone`() {
        val hold = captured(money(9_000))
        assertEquals(HoldState.CAPTURED, hold.state)
        assertEquals(money(9_000), hold.captured)
        assertEquals(Ledger.holding(money(9_000)), hold.ledger)
        assertTrue(hold.ledger.nothingHasLeft)
    }

    @Test
    fun `capturing more than the accepted total is allowed, which is where a protection fee would ride`() {
        val hold = captured(money(9_000), captured = money(9_400))
        assertEquals(money(9_400), hold.captured)
        assertEquals(money(9_000), hold.acceptedTotal)
    }

    @Test
    fun `capturing less than the accepted total is refused with both figures`() {
        val outcome = authorized(money(9_000)).apply(HoldCommand.Capture(money(8_999)))
        assertEquals(MoneyError.CapturedBelowAcceptedTotal(money(8_999), money(9_000)), err(outcome))
    }

    @Test
    fun `release on both grounds moves everything captured to the tasker and records the grounds`() {
        val hold = released(money(9_000))
        assertEquals(HoldState.RELEASED, hold.state)
        assertEquals(Ledger(money(9_000), money(9_000), Money.ZERO, Money.ZERO), hold.ledger)
        assertEquals(ReleaseGrounds(bookingCompleted = true, nesterConfirmed = true), hold.grounds)
    }

    @Test
    fun `a full refund returns everything captured to the nester, minus nothing`() {
        val hold = refunded(money(9_000))
        assertEquals(HoldState.REFUNDED, hold.state)
        assertEquals(Ledger(money(9_000), Money.ZERO, money(9_000), Money.ZERO), hold.ledger)
    }

    @Test
    fun `a part refund pays the call-out fee to the tasker and the remainder to the nester`() {
        val hold = partRefunded(money(9_000))
        assertEquals(HoldState.PART_REFUND, hold.state)
        assertEquals(money(1_800), hold.feePaid)
        assertEquals(money(7_200), hold.refunded)
        assertEquals(Ledger(money(9_000), Money.ZERO, money(7_200), money(1_800)), hold.ledger)
    }

    @Test
    fun `the fee is computed on the accepted total and not on what was captured`() {
        val hold = captured(money(9_000), captured = money(20_000))
        val settled = ok(hold.apply(HoldCommand.RefundLessCallOutFee(declinedAfterArrival)))
        assertIs<PartRefundedHold>(settled)
        assertEquals(money(1_800), settled.feePaid)
        assertEquals(money(18_200), settled.refunded)
    }

    @Test
    fun `the id and the accepted total survive every transition`() {
        everyState().forEach { hold ->
            assertEquals(holdId(), hold.id, hold.state.name)
            assertEquals(money(9_000), hold.acceptedTotal, hold.state.name)
        }
        assertEquals(HoldState.entries.toSet(), everyState().map { it.state }.toSet())
    }

    @Test
    fun `a settled hold refuses every command by name`() {
        listOf(released(), refunded(), partRefunded()).forEach { hold ->
            assertEquals(MoneyError.NotAllowedIn(hold.state, HoldCommand.Refund), err(hold.apply(HoldCommand.Refund)))
        }
    }

    @Test
    fun `a hold still authorized cannot be released, refunded or settled`() {
        val hold = authorized()
        val release = HoldCommand.Release(ReleaseGrounds(bookingCompleted = true, nesterConfirmed = true))
        assertEquals(MoneyError.NotAllowedIn(HoldState.AUTHORIZED, release), err(hold.apply(release)))
        val refund = HoldCommand.Refund
        assertEquals(MoneyError.NotAllowedIn(HoldState.AUTHORIZED, refund), err(hold.apply(refund)))
    }

    @Test
    fun `a captured hold cannot be captured twice`() {
        val capture = HoldCommand.Capture(money(9_000))
        assertEquals(MoneyError.NotAllowedIn(HoldState.CAPTURED, capture), err(captured().apply(capture)))
    }

    @Test
    fun `the standing invariant holds after every transition the fixtures reach`() {
        everyState().forEach { hold -> assertTrue(standingInvariantHolds(hold.ledger), hold.state.name) }
    }
}
