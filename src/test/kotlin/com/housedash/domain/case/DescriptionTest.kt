package com.housedash.domain.case

import com.housedash.domain.shared.Outcome
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DescriptionTest {
    @Test
    fun `rejects a description shorter than the minimum after trimming`() {
        val result = Description.of("   tap drips   ")
        val err = assertIs<Outcome.Err<CaseError>>(result)
        assertEquals(CaseError.DescriptionTooShort(9, 20), err.error)
    }

    @Test
    fun `accepts a description at exactly the minimum`() {
        val raw = "a".repeat(20)
        val ok = assertIs<Outcome.Ok<Description>>(Description.of(raw))
        assertEquals(raw, ok.value.text)
    }

    @Test
    fun `rejects a description one character past the maximum`() {
        val err = assertIs<Outcome.Err<CaseError>>(Description.of("a".repeat(2001)))
        assertEquals(CaseError.DescriptionTooLong(2001, 2000), err.error)
    }

    @Test
    fun `stores the trimmed text, not the raw text`() {
        val ok = assertIs<Outcome.Ok<Description>>(Description.of("  " + "a".repeat(25) + "  "))
        assertEquals("a".repeat(25), ok.value.text)
    }
}
