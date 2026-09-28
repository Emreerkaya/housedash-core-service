package com.housedash.domain.shared

import com.housedash.domain.case.CaseError
import com.housedash.domain.case.Description
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StoredTextIsComposedNotCompatibilityTest {
    private fun textOf(raw: String): String = assertIs<Outcome.Ok<Description>>(Description.of(raw), raw).value.text

    private fun errorOf(raw: String): CaseError = assertIs<Outcome.Err<CaseError>>(Description.of(raw), raw).error

    @Test
    fun `a stored description keeps the characters a compatibility fold would rewrite`() {
        val stored = textOf("the pipe under the sink is 3\u00BD inch and the Vaillant\u2122 casing is cracked")
        assertTrue(stored.contains('\u00BD'), stored)
        assertTrue(stored.contains('\u2122'), stored)
        assertEquals(false, stored.contains("1\u20442"))
        assertEquals(false, stored.contains("TM"))
    }

    @Test
    fun `a fullwidth digit phone number is still rejected, so the fold ran on a copy`() {
        assertEquals(
            CaseError.ContactDetailsInDescription(setOf(ContactDetail.PhoneNumber)),
            errorOf("reach me on \uFF19\uFF11\uFF17-\uFF15\uFF15\uFF15-\uFF10\uFF11\uFF19\uFF19 any time"),
        )
    }

    @Test
    fun `a stored description keeps a fullwidth digit that is not part of a phone number`() {
        val stored = textOf("the dial reads \uFF19\uFF11\uFF17 and the gauge needle is bent right over")
        assertTrue(stored.contains('\uFF19'), stored)
        assertEquals(false, stored.contains('9'))
    }

    @Test
    fun `a current emoji survives into the stored description`() {
        val stored = textOf("the drain needs a \uD83E\uDE8F and the \uD83E\uDDD1\u200D\uD83D\uDD27 never came")
        assertTrue(stored.contains("\uD83E\uDE8F"), stored)
        assertTrue(stored.contains("\uD83E\uDDD1\u200D\uD83D\uDD27"), stored)
    }
}
