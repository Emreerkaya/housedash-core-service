package com.housedash.domain.shared

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class IdentifierTest {
    @Test
    fun `an identifier rejects a value outside its shape at construction`() {
        assertFailsWith<IllegalArgumentException> { LeftId("nope") }
    }

    @Test
    fun `two identifiers of the same type and value are equal and hash alike`() {
        assertEquals(LeftId("id_1"), LeftId("id_1"))
        assertEquals(LeftId("id_1").hashCode(), LeftId("id_1").hashCode())
    }

    @Test
    fun `two identifiers of the same type and different values are not equal`() {
        assertNotEquals(LeftId("id_1"), LeftId("id_2"))
    }

    @Test
    fun `two identifiers of different types holding the same value are never equal`() {
        assertNotEquals<Any>(LeftId("id_1"), RightId("id_1"))
        assertNotEquals<Any>(RightId("id_1"), LeftId("id_1"))
    }

    @Test
    fun `an identifier is never equal to the raw string it holds`() {
        assertNotEquals<Any>(LeftId("id_1"), "id_1")
    }

    private class LeftId(
        value: String,
    ) : Identifier(value, SHAPE)

    private class RightId(
        value: String,
    ) : Identifier(value, SHAPE)

    private companion object {
        private val SHAPE = IdentifierShape("id_", 8)
    }
}
