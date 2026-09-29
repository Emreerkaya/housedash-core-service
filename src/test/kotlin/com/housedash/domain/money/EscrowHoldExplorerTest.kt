package com.housedash.domain.money

import com.housedash.domain.shared.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val EVERY_COMMAND: List<HoldCommand> =
    listOf(money(8_999), money(9_000), money(9_001)).map { HoldCommand.Capture(it) } +
        everyGrounds.map { HoldCommand.Release(it) } +
        HoldCommand.Refund +
        everyEnding.map { HoldCommand.RefundLessCallOutFee(it) }

private const val STATES_TIMES_COMMANDS = 5 * 16

private const val LEGAL_APPLICATIONS = 5

private fun expectedRefusal(
    state: HoldState,
    command: HoldCommand,
): MoneyError? =
    when (state) {
        HoldState.AUTHORIZED -> authorizedRefusal(command)
        HoldState.CAPTURED -> capturedRefusal(command)
        else -> MoneyError.NotAllowedIn(state, command)
    }

private fun authorizedRefusal(command: HoldCommand): MoneyError? =
    when (command) {
        is HoldCommand.Capture -> captureRefusal(command)
        else -> MoneyError.NotAllowedIn(HoldState.AUTHORIZED, command)
    }

private fun capturedRefusal(command: HoldCommand): MoneyError? =
    when (command) {
        is HoldCommand.Capture -> MoneyError.NotAllowedIn(HoldState.CAPTURED, command)
        is HoldCommand.Release -> releaseRefusal(command)
        is HoldCommand.Refund -> null
        is HoldCommand.RefundLessCallOutFee -> settlementRefusal(command)
    }

private fun captureRefusal(command: HoldCommand.Capture): MoneyError? =
    if (command.captured < money(9_000)) MoneyError.CapturedBelowAcceptedTotal(command.captured, money(9_000)) else null

private fun releaseRefusal(command: HoldCommand.Release): MoneyError? =
    if (command.grounds.sufficient) null else MoneyError.ReleaseRefused(command.grounds)

private fun settlementRefusal(command: HoldCommand.RefundLessCallOutFee): MoneyError? =
    if (CallOutFee.payableOn(command.ending)) null else MoneyError.CallOutFeeNotOwed(command.ending)

private fun stateAfter(command: HoldCommand): HoldState =
    when (command) {
        is HoldCommand.Capture -> HoldState.CAPTURED
        is HoldCommand.Release -> HoldState.RELEASED
        is HoldCommand.Refund -> HoldState.REFUNDED
        is HoldCommand.RefundLessCallOutFee -> HoldState.PART_REFUND
    }

class EscrowHoldExplorerTest {
    @Test
    fun `every command in every state lands where the domain model draws it and nowhere else`() {
        var applications = 0
        var legal = 0
        everyState().forEach { hold ->
            EVERY_COMMAND.forEach { command ->
                applications += 1
                val outcome = hold.apply(command)
                val refusal = expectedRefusal(hold.state, command)
                if (refusal == null) {
                    legal += 1
                    val next = ok(outcome)
                    assertEquals(stateAfter(command), next.state, "${hold.state} $command")
                    assertTrue(standingInvariantHolds(next.ledger), "${hold.state} $command")
                } else {
                    assertEquals(refusal, err(outcome), "${hold.state} $command")
                }
            }
        }
        assertEquals(STATES_TIMES_COMMANDS, applications)
        assertEquals(LEGAL_APPLICATIONS, legal)
    }

    @Test
    fun `the command alphabet the explorer drives is the one this test names`() {
        assertEquals(16, EVERY_COMMAND.size)
        assertEquals(3, EVERY_COMMAND.count { it is HoldCommand.Capture })
        assertEquals(4, EVERY_COMMAND.count { it is HoldCommand.Release })
        assertEquals(1, EVERY_COMMAND.count { it is HoldCommand.Refund })
        assertEquals(8, EVERY_COMMAND.count { it is HoldCommand.RefundLessCallOutFee })
        assertEquals(5, everyState().size)
    }

    @Test
    fun `a refused command leaves the hold it was applied to unchanged`() {
        everyState().forEach { hold ->
            val before = hold.ledger
            EVERY_COMMAND.filter { expectedRefusal(hold.state, it) != null }.forEach { command ->
                assertTrue(hold.apply(command) is Outcome.Err, "${hold.state} $command")
                assertEquals(before, hold.ledger)
            }
        }
    }

    @Test
    fun `every ending that owes no fee is refused on a captured hold, and the full refund is the path`() {
        everyEnding.filterNot { CallOutFee.payableOn(it) }.forEach { ending ->
            val settle = HoldCommand.RefundLessCallOutFee(ending)
            assertEquals(MoneyError.CallOutFeeNotOwed(ending), err(captured().apply(settle)), "$ending")
            val refund = ok(captured().apply(HoldCommand.Refund))
            assertEquals(HoldState.REFUNDED, refund.state)
            assertEquals(money(9_000), refund.ledger.refunded)
        }
    }
}
