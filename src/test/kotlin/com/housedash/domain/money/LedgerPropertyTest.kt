package com.housedash.domain.money

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val FEWEST_OF_EACH_OUTCOME_A_PROPERTY_NEEDS = 100

private val MAYBE = listOf(null, true, false)

class LedgerPropertyTest {
    @Test
    fun `under random totals, what leaves a hold on every settlement path never exceeds what was captured`() {
        val samples = Samples()
        samples.repeat { it.acceptedTotal() to it.smallCents() }.forEach { (accepted, extra) ->
            val room = extra < Long.MAX_VALUE - accepted
            val captured = if (room) money(accepted + extra) else money(accepted)
            val held = captured(money(accepted), captured)
            val paths =
                listOf(
                    HoldCommand.Release(ReleaseGrounds(bookingCompleted = true, nesterConfirmed = true)),
                    HoldCommand.Refund,
                    HoldCommand.RefundLessCallOutFee(declinedAfterArrival),
                )
            paths.forEach { path ->
                val settled = ok(held.apply(path))
                assertTrue(standingInvariantHolds(settled.ledger), "$accepted $captured $path")
                assertEquals(BigInteger.valueOf(captured.cents), whatLeft(settled.ledger), "$accepted $captured $path")
            }
        }
    }

    @Test
    fun `under random totals the part refund pays the fee on the accepted total and refunds the rest`() {
        var belowTheCap = 0
        Samples().repeat { it.acceptedTotal() to it.smallCents() }.forEach { (accepted, extra) ->
            val captured = if (extra < Long.MAX_VALUE - accepted) money(accepted + extra) else money(accepted)
            val held = captured(money(accepted), captured)
            val settled = ok(held.apply(HoldCommand.RefundLessCallOutFee(declinedAfterArrival)))
            val fee = feeTheRecordSpecifies(accepted)
            if (fee < CallOutFee.CAP_CENTS) belowTheCap += 1
            assertEquals(fee, settled.ledger.feePaid.cents, "$accepted $captured")
            assertEquals(captured.cents - fee, settled.ledger.refunded.cents, "$accepted $captured")
        }
        assertTrue(belowTheCap >= FEWEST_OF_EACH_OUTCOME_A_PROPERTY_NEEDS, "below the cap $belowTheCap")
    }

    @Test
    fun `a random row either fails to load or loads into a hold whose ledger satisfies the invariant`() {
        var refused = 0
        var loaded = 0
        Samples().repeat { randomRow(it) }.forEach { row ->
            val hold = runCatching { EscrowHold.rehydrate(row) }
            hold.exceptionOrNull()?.let { assertTrue(it is CorruptHold, it.toString()) }
            hold.getOrNull()?.let {
                loaded += 1
                assertTrue(standingInvariantHolds(it.ledger), it.ledger.toString())
            } ?: run { refused += 1 }
        }
        assertTrue(refused >= FEWEST_OF_EACH_OUTCOME_A_PROPERTY_NEEDS, "refused $refused")
        assertTrue(loaded >= FEWEST_OF_EACH_OUTCOME_A_PROPERTY_NEEDS, "loaded $loaded")
    }

    @Test
    fun `a ledger accepts four random figures exactly when the invariant holds of them`() {
        var accepted = 0
        var refused = 0
        Samples().repeat { listOf(it.cents(20), it.cents(20), it.cents(20), it.cents(20)) }.forEach { figures ->
            val (captured, released, refunded) = figures
            val feePaid = figures.last()
            val holds = BigInteger.valueOf(released + refunded + feePaid) <= BigInteger.valueOf(captured)
            val built = runCatching { Ledger(money(captured), money(released), money(refunded), money(feePaid)) }
            assertEquals(holds, built.isSuccess, figures.toString())
            if (holds) accepted += 1 else refused += 1
        }
        assertTrue(accepted >= FEWEST_OF_EACH_OUTCOME_A_PROPERTY_NEEDS, "accepted $accepted")
        assertTrue(refused >= FEWEST_OF_EACH_OUTCOME_A_PROPERTY_NEEDS, "refused $refused")
    }

    private fun randomRow(samples: Samples): HoldRow =
        when (samples.pick(RowShape.entries)) {
            RowShape.AS_A_COMMAND_LEFT_IT -> rowOf(randomHold(samples))
            RowShape.ONE_FIELD_OFF -> oneFieldOff(rowOf(randomHold(samples)), samples)
            RowShape.ANY_FIGURES_AT_ALL -> anyFiguresAtAll(samples)
        }

    private fun randomHold(samples: Samples): EscrowHold {
        val accepted = money(samples.smallCents())
        val held = captured(accepted, captured = money(accepted.cents + samples.cents(500)))
        return when (samples.pick(HoldState.entries)) {
            HoldState.AUTHORIZED -> authorized(accepted)
            HoldState.CAPTURED -> held
            HoldState.RELEASED -> ok(held.apply(HoldCommand.Release(ReleaseGrounds(true, true))))
            HoldState.REFUNDED -> ok(held.apply(HoldCommand.Refund))
            HoldState.PART_REFUND -> ok(held.apply(HoldCommand.RefundLessCallOutFee(declinedAfterArrival)))
        }
    }

    private fun oneFieldOff(
        row: HoldRow,
        samples: Samples,
    ): HoldRow {
        val nudge = samples.cents(3) - 1
        val ledger = row.ledger
        return when (samples.pick(Field.entries)) {
            Field.STATE -> row.with(state = samples.pick(HoldState.entries).name)
            Field.ACCEPTED -> row.with(acceptedTotal = row.acceptedTotalCents + nudge)
            Field.CAPTURED -> row.with(ledger = ledger.with(captured = ledger.capturedCents + nudge))
            Field.RELEASED -> row.with(ledger = ledger.with(released = ledger.releasedCents + nudge))
            Field.REFUNDED -> row.with(ledger = ledger.with(refunded = ledger.refundedCents + nudge))
            Field.FEE -> row.with(ledger = ledger.with(feePaid = ledger.feePaidCents + nudge))
            Field.GROUNDS -> row.with(completed = samples.pick(MAYBE), confirmed = samples.pick(MAYBE))
        }
    }

    private fun anyFiguresAtAll(samples: Samples): HoldRow {
        val figure = { if (samples.coin()) samples.cents(12) else samples.anyCents() }
        return holdRow(
            state = samples.pick(HoldState.entries).name,
            acceptedTotal = figure(),
            ledger = LedgerRow(figure(), figure(), figure(), figure()),
            bookingCompleted = samples.pick(MAYBE),
            nesterConfirmed = samples.pick(MAYBE),
        )
    }
}

private enum class RowShape { AS_A_COMMAND_LEFT_IT, ONE_FIELD_OFF, ANY_FIGURES_AT_ALL }

private enum class Field { STATE, ACCEPTED, CAPTURED, RELEASED, REFUNDED, FEE, GROUNDS }
