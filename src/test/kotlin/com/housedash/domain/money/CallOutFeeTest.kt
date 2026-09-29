package com.housedash.domain.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val THE_WORKED_TABLE =
    listOf(
        6_000L to 1_200L,
        9_000L to 1_800L,
        12_000L to 2_400L,
        12_500L to 2_500L,
        20_000L to 2_500L,
        9_001L to 1_800L,
    )

private val EDGES_A_GENERATOR_MAY_MISS =
    listOf(0L, 1L, 2L, 3L, 12_497L, 12_498L, 12_499L, 12_500L, (Long.MAX_VALUE - 50) / 20, Long.MAX_VALUE)

class CallOutFeeTest {
    @Test
    fun `the worked table in the domain model holds row for row`() {
        THE_WORKED_TABLE.forEach { (accepted, fee) ->
            assertEquals(money(fee), CallOutFee.of(money(accepted)), "accepted $accepted")
        }
        assertEquals(6, THE_WORKED_TABLE.size)
    }

    @Test
    fun `ninety dollars and one cent is not a half, and the totals that do round up are three and four cents over`() {
        assertEquals(money(1_800), CallOutFee.of(money(9_001)))
        assertEquals(money(1_800), CallOutFee.of(money(9_002)))
        assertEquals(money(1_801), CallOutFee.of(money(9_003)))
        assertEquals(money(1_801), CallOutFee.of(money(9_004)))
        assertEquals(money(1_801), CallOutFee.of(money(9_005)))
        assertEquals(money(0), CallOutFee.of(money(2)))
        assertEquals(money(1), CallOutFee.of(money(3)))
    }

    @Test
    fun `the cap binds from one hundred and twenty four dollars ninety eight`() {
        assertEquals(money(2_499), CallOutFee.of(money(12_497)))
        assertEquals(money(2_500), CallOutFee.of(money(12_498)))
        assertEquals(money(2_500), CallOutFee.of(money(12_499)))
        assertEquals(money(2_500), CallOutFee.of(money(12_500)))
        assertEquals(CallOutFee.CAP, CallOutFee.of(money(Long.MAX_VALUE)))
    }

    @Test
    fun `the fee agrees with the record's formula over the whole range of a long`() {
        val totals = Samples().repeat { it.anyCents() } + EDGES_A_GENERATOR_MAY_MISS
        val disagreements =
            totals.filter { accepted -> CallOutFee.of(money(accepted)).cents != feeTheRecordSpecifies(accepted) }
        assertEquals(emptyList(), disagreements)
        assertTrue(totals.any { it > (Long.MAX_VALUE - 50) / 20 })
    }

    @Test
    fun `the fee agrees with the record's formula where the arithmetic is not trivially capped`() {
        val totals = Samples().repeat { it.cents(13_000) } + EDGES_A_GENERATOR_MAY_MISS
        val disagreements =
            totals.filter { accepted -> CallOutFee.of(money(accepted)).cents != feeTheRecordSpecifies(accepted) }
        assertEquals(emptyList(), disagreements)
        assertTrue(totals.count { it < 12_498 } > SAMPLES_PER_PROPERTY / 2)
    }

    @Test
    fun `the fee never exceeds the cap, never exceeds the accepted total, and never falls as the total rises`() {
        val totals = (Samples().repeat { it.cents(30_000) } + EDGES_A_GENERATOR_MAY_MISS).sorted()
        val fees = totals.map { CallOutFee.of(money(it)) }
        fees.forEach { assertTrue(it <= CallOutFee.CAP) }
        totals.zip(fees).forEach { (accepted, fee) -> assertTrue(fee <= money(accepted), "accepted $accepted") }
        fees.zipWithNext().forEach { (lower, higher) -> assertTrue(lower <= higher) }
    }

    @Test
    fun `of the eight ways a visit can end, exactly one owes the fee`() {
        val owing = everyEnding.filter { CallOutFee.payableOn(it) }
        assertEquals(8, everyEnding.size)
        assertEquals(listOf(declinedAfterArrival), owing)
    }

    @Test
    fun `not payable when the tasker no-shows`() {
        assertFalse(CallOutFee.payableOn(VisitEnding(taskerArrived = false, endedBy = VisitEnd.TASKER_NO_SHOW)))
        assertFalse(CallOutFee.payableOn(VisitEnding(taskerArrived = true, endedBy = VisitEnd.TASKER_NO_SHOW)))
    }

    @Test
    fun `not payable when the tasker cancels, before or after arriving`() {
        assertFalse(CallOutFee.payableOn(VisitEnding(taskerArrived = false, endedBy = VisitEnd.TASKER_CANCELLED)))
        assertFalse(CallOutFee.payableOn(VisitEnding(taskerArrived = true, endedBy = VisitEnd.TASKER_CANCELLED)))
    }

    @Test
    fun `not payable when the tasker never reached arrival, even on a declined revision`() {
        val declinedFromTheVan = VisitEnding(taskerArrived = false, endedBy = VisitEnd.NESTER_DECLINED_REVISION)
        assertFalse(CallOutFee.payableOn(declinedFromTheVan))
    }

    @Test
    fun `not payable when the nester cancels before arrival`() {
        assertFalse(CallOutFee.payableOn(VisitEnding(taskerArrived = false, endedBy = VisitEnd.NESTER_CANCELLED)))
    }

    @Test
    fun `a nester cancelling after arrival is not a declined revision, so no fee is owed there either`() {
        assertFalse(CallOutFee.payableOn(VisitEnding(taskerArrived = true, endedBy = VisitEnd.NESTER_CANCELLED)))
    }

    @Test
    fun `the constants the record binds are the ones the fee is computed from`() {
        assertEquals(20, CallOutFee.PERCENT_OF_THE_ACCEPTED_TOTAL)
        assertEquals(2_500, CallOutFee.CAP_CENTS)
        assertEquals(money(CallOutFee.CAP_CENTS), CallOutFee.CAP)
    }
}
