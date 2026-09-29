package com.housedash.domain.money

import com.housedash.domain.shared.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReleaseGuardTest {
    @Test
    fun `of the four combinations of grounds, exactly one releases`() {
        assertEquals(4, everyGrounds.size)
        val releasing = everyGrounds.filter { captured().apply(HoldCommand.Release(it)) is Outcome.Ok }
        assertEquals(listOf(ReleaseGrounds(bookingCompleted = true, nesterConfirmed = true)), releasing)
        everyGrounds.filterNot { it.sufficient }.forEach { grounds ->
            assertEquals(MoneyError.ReleaseRefused(grounds), refusal(grounds), "$grounds")
        }
    }

    @Test
    fun `a tasker marking the visit done does not release money the nester never confirmed`() {
        val doneButUnconfirmed = ReleaseGrounds(bookingCompleted = true, nesterConfirmed = false)
        assertEquals(MoneyError.ReleaseRefused(doneButUnconfirmed), refusal(doneButUnconfirmed))
    }

    @Test
    fun `a nester confirming a visit that is not complete does not release either`() {
        val confirmedButNotDone = ReleaseGrounds(bookingCompleted = false, nesterConfirmed = true)
        assertEquals(MoneyError.ReleaseRefused(confirmedButNotDone), refusal(confirmedButNotDone))
    }

    @Test
    fun `neither ground alone is sufficient and both together are`() {
        assertFalse(ReleaseGrounds(bookingCompleted = false, nesterConfirmed = false).sufficient)
        assertFalse(ReleaseGrounds(bookingCompleted = true, nesterConfirmed = false).sufficient)
        assertFalse(ReleaseGrounds(bookingCompleted = false, nesterConfirmed = true).sufficient)
        assertTrue(ReleaseGrounds(bookingCompleted = true, nesterConfirmed = true).sufficient)
    }

    @Test
    fun `a refused release leaves the hold captured and untouched`() {
        val hold = captured()
        err(hold.apply(HoldCommand.Release(ReleaseGrounds(bookingCompleted = true, nesterConfirmed = false))))
        assertEquals(HoldState.CAPTURED, hold.state)
        assertTrue(hold.ledger.nothingHasLeft)
    }

    private fun refusal(grounds: ReleaseGrounds): MoneyError = err(captured().apply(HoldCommand.Release(grounds)))
}
