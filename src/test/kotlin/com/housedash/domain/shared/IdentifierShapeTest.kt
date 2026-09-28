package com.housedash.domain.shared

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IdentifierShapeTest {
    private val shape = IdentifierShape("cs_", 8)

    private fun flawOf(raw: String): IdentifierFlaw = assertIs<Outcome.Err<IdentifierFlaw>>(shape.check(raw)).error

    @Test
    fun `accepts the prefix with a body of permitted characters`() {
        listOf("cs_1", "cs_abcDEF12", "cs_a-b_c", "cs_--------").forEach { raw ->
            assertTrue(shape.accepts(raw), raw)
        }
    }

    @Test
    fun `rejects a missing or wrong prefix`() {
        listOf("1", "ph_1", "CS_1", "cs1", " cs_1", "xcs_1").forEach { raw ->
            assertFalse(shape.accepts(raw), raw)
        }
    }

    @Test
    fun `rejects an empty body and a body past the bound`() {
        assertFalse(shape.accepts("cs_"))
        assertTrue(shape.accepts("cs_" + "a".repeat(8)))
        assertFalse(shape.accepts("cs_" + "a".repeat(9)))
    }

    @Test
    fun `rejects a body holding anything outside the permitted characters`() {
        listOf("cs_a b", "cs_a.b", "cs_a/b", "cs_a'b", "cs_a;b", "cs_a\nb", "cs_..").forEach { raw ->
            assertFalse(shape.accepts(raw), raw)
        }
    }

    @Test
    fun `the prefix is matched literally and not as a pattern`() {
        val dotted = IdentifierShape("c.s_", 8)
        assertTrue(dotted.accepts("c.s_1"))
        assertFalse(dotted.accepts("cxs_1"))
    }

    @Test
    fun `check names the wrong prefix as its own flaw`() {
        listOf("1", "ph_1", "CS_1", "cs1", " cs_1", "").forEach { raw ->
            assertEquals(IdentifierFlaw.WrongPrefix, flawOf(raw), raw)
        }
    }

    @Test
    fun `check names a body outside the length bounds with the bounds it broke`() {
        assertEquals(IdentifierFlaw.BodyOutsideLength(0, 1, 8), flawOf("cs_"))
        assertEquals(IdentifierFlaw.BodyOutsideLength(9, 1, 8), flawOf("cs_" + "a".repeat(9)))
    }

    @Test
    fun `check names an illegal character in the body as its own flaw`() {
        listOf("cs_a b", "cs_a.b", "cs_a/b", "cs_a;b", "cs_a\nb").forEach { raw ->
            assertEquals(IdentifierFlaw.IllegalCharacterInBody, flawOf(raw), raw)
        }
    }

    @Test
    fun `check returns the accepted value unchanged`() {
        assertEquals("cs_a-b_c", assertIs<Outcome.Ok<String>>(shape.check("cs_a-b_c")).value)
    }

    @Test
    fun `no flaw carries the value that was rejected`() {
        val flaw = flawOf("cs_917-555-0199-x")
        assertEquals(IdentifierFlaw.BodyOutsideLength(14, 1, 8), flaw)
        assertFalse(flaw.toString().contains("917"))
    }
}
