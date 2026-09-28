package com.housedash.domain.shared

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdentifierShapeTest {
    private val shape = IdentifierShape("cs_", 8)

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
}
