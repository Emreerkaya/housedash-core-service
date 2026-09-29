package com.housedash.domain.shared

import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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

    @Test
    fun `the base identifier is abstract and its only constructor is protected`() {
        assertTrue(Modifier.isAbstract(Identifier::class.java.modifiers))
        assertTrue(Identifier::class.java.constructors.isEmpty())
        val constructor = Identifier::class.java.declaredConstructors.single()
        assertTrue(Modifier.isProtected(constructor.modifiers))
    }

    @Test
    fun `the base identifier is abstract, so reflection has nothing to instantiate before access matters`() {
        val constructor = Identifier::class.java.declaredConstructors.single()
        assertFailsWith<InstantiationException> { constructor.newInstance("id_1", IdentifierShape("", 64)) }
    }

    @Test
    fun `a bare identifier cannot be built even once the constructor is forced open`() {
        val constructor = Identifier::class.java.declaredConstructors.single()
        constructor.isAccessible = true
        assertFailsWith<InstantiationException> { constructor.newInstance("id_1", IdentifierShape("", 64)) }
    }

    @Test
    fun `forcing a subclass constructor open still cannot produce a value outside its shape`() {
        val constructor = LeftId::class.java.declaredConstructors.single()
        constructor.isAccessible = true
        val thrown =
            assertFailsWith<InvocationTargetException> { constructor.newInstance("not a shape at all") }
        assertIs<IllegalArgumentException>(thrown.targetException)
    }

    @Test
    fun `an identifier renders as the opaque value it holds and not as an identity hash`() {
        assertEquals("id_1", LeftId("id_1").toString())
    }

    private class LeftId(
        value: String,
    ) : Identifier(value, SHAPE) {
        override fun toString(): String = value
    }

    private class RightId(
        value: String,
    ) : Identifier(value, SHAPE) {
        override fun toString(): String = value
    }

    private companion object {
        private val SHAPE = IdentifierShape("id_", 8)
    }
}
