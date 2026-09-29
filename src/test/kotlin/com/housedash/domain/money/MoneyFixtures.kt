package com.housedash.domain.money

import com.housedash.domain.shared.Outcome
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.random.Random
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

const val SEED_EVERY_PROPERTY_RUNS_WITH = 20260929L

const val SAMPLES_PER_PROPERTY = 20_000

fun money(cents: Long): Money = Money(cents)

fun holdId(raw: String = "eh_1"): HoldId = assertIs<Outcome.Ok<HoldId>>(HoldId.of(raw)).value

fun subscriptionId(raw: String = "sb_1"): SubscriptionId {
    val parsed = SubscriptionId.of(raw)
    return assertIs<Outcome.Ok<SubscriptionId>>(parsed).value
}

val declinedAfterArrival = VisitEnding(taskerArrived = true, endedBy = VisitEnd.NESTER_DECLINED_REVISION)

val everyGrounds: List<ReleaseGrounds> =
    listOf(true, false).flatMap { completed ->
        listOf(true, false).map { confirmed -> ReleaseGrounds(completed, confirmed) }
    }

val everyEnding: List<VisitEnding> =
    listOf(true, false).flatMap { arrived -> VisitEnd.entries.map { VisitEnding(arrived, it) } }

fun <T> ok(outcome: Outcome<T, MoneyError>): T = assertIs<Outcome.Ok<T>>(outcome).value

fun <T> err(outcome: Outcome<T, MoneyError>): MoneyError = assertIs<Outcome.Err<MoneyError>>(outcome).error

fun authorized(
    acceptedTotal: Money = money(9_000),
    id: HoldId = holdId(),
): AuthorizedHold = EscrowHold.authorize(id, acceptedTotal)

fun captured(
    acceptedTotal: Money = money(9_000),
    captured: Money = acceptedTotal,
): CapturedHold = ok(authorized(acceptedTotal).apply(HoldCommand.Capture(captured))) as CapturedHold

fun released(acceptedTotal: Money = money(9_000)): ReleasedHold {
    val grounds = ReleaseGrounds(bookingCompleted = true, nesterConfirmed = true)
    return ok(captured(acceptedTotal).apply(HoldCommand.Release(grounds))) as ReleasedHold
}

fun refunded(acceptedTotal: Money = money(9_000)): RefundedHold {
    val settled = ok(captured(acceptedTotal).apply(HoldCommand.Refund))
    return settled as RefundedHold
}

fun partRefunded(acceptedTotal: Money = money(9_000)): PartRefundedHold {
    val settled = ok(captured(acceptedTotal).apply(HoldCommand.RefundLessCallOutFee(declinedAfterArrival)))
    return settled as PartRefundedHold
}

fun everyState(): List<EscrowHold> = listOf(authorized(), captured(), released(), refunded(), partRefunded())

fun ledgerRow(
    captured: Long = 9_000,
    released: Long = 0,
    refunded: Long = 0,
    feePaid: Long = 0,
): LedgerRow = LedgerRow(captured, released, refunded, feePaid)

fun holdRow(
    state: String = "CAPTURED",
    acceptedTotal: Long = 9_000,
    ledger: LedgerRow = ledgerRow(),
    bookingCompleted: Boolean? = null,
    nesterConfirmed: Boolean? = null,
): HoldRow = HoldRow("eh_1", state, acceptedTotal, ledger, bookingCompleted, nesterConfirmed)

fun holdRowWithId(id: String): HoldRow = HoldRow(id, "CAPTURED", 9_000, ledgerRow(), null, null)

fun rowOf(hold: EscrowHold): HoldRow {
    val grounds = (hold as? ReleasedHold)?.grounds
    val ledger = hold.ledger
    return HoldRow(
        hold.id.value,
        hold.state.name,
        hold.acceptedTotal.cents,
        LedgerRow(ledger.captured.cents, ledger.released.cents, ledger.refunded.cents, ledger.feePaid.cents),
        grounds?.bookingCompleted,
        grounds?.nesterConfirmed,
    )
}

fun feeTheRecordSpecifies(acceptedTotalCents: Long): Long {
    val fifth =
        BigDecimal(acceptedTotalCents)
            .multiply(BigDecimal(CallOutFee.PERCENT_OF_THE_ACCEPTED_TOTAL))
            .divide(BigDecimal(100), 0, RoundingMode.HALF_UP)
    return fifth.min(BigDecimal(CallOutFee.CAP_CENTS)).longValueExact()
}

fun whatLeft(ledger: Ledger): BigInteger =
    BigInteger.valueOf(ledger.released.cents) +
        BigInteger.valueOf(ledger.refunded.cents) +
        BigInteger.valueOf(ledger.feePaid.cents)

fun standingInvariantHolds(ledger: Ledger): Boolean = whatLeft(ledger) <= BigInteger.valueOf(ledger.captured.cents)

class Samples(
    seed: Long = SEED_EVERY_PROPERTY_RUNS_WITH,
) {
    private val random = Random(seed)

    fun anyCents(): Long = random.nextLong(0, Long.MAX_VALUE)

    fun smallCents(): Long = random.nextLong(0, 1_000_000)

    fun cents(bound: Long): Long = if (bound == 0L) 0L else random.nextLong(0, bound)

    fun acceptedTotal(): Long =
        when (random.nextInt(3)) {
            0 -> cents(30_000)
            1 -> smallCents()
            else -> anyCents()
        }

    fun percent(): Long = random.nextLong(0, 101)

    fun coin(): Boolean = random.nextBoolean()

    fun <T> pick(from: List<T>): T = from[random.nextInt(from.size)]

    fun <T> repeat(block: (Samples) -> T): List<T> = List(SAMPLES_PER_PROPERTY) { block(this) }
}

fun releasedRow(
    ledger: LedgerRow = ledgerRow(released = 9_000),
    completed: Boolean? = true,
    confirmed: Boolean? = true,
): HoldRow = holdRow(state = "RELEASED", ledger = ledger, bookingCompleted = completed, nesterConfirmed = confirmed)

fun corruptOnLoad(row: HoldRow): HoldFault = assertFailsWith<CorruptHold> { EscrowHold.rehydrate(row) }.fault

fun HoldRow.with(
    state: String = this.state,
    acceptedTotal: Long = acceptedTotalCents,
    ledger: LedgerRow = this.ledger,
    completed: Boolean? = bookingCompleted,
    confirmed: Boolean? = nesterConfirmed,
): HoldRow = HoldRow(id, state, acceptedTotal, ledger, completed, confirmed)

fun LedgerRow.with(
    captured: Long = capturedCents,
    released: Long = releasedCents,
    refunded: Long = refundedCents,
    feePaid: Long = feePaidCents,
): LedgerRow = LedgerRow(captured, released, refunded, feePaid)
