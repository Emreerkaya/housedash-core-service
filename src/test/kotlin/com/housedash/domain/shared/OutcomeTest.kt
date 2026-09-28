package com.housedash.domain.shared

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class OutcomeTest {
    private val ok: Outcome<Int, String> = Outcome.Ok(2)
    private val err: Outcome<Int, String> = Outcome.Err("boom")

    private fun <T> valueOf(outcome: Outcome<T, Any>): T = assertIs<Outcome.Ok<T>>(outcome).value

    private fun <E> errorOf(outcome: Outcome<Any, E>): E = assertIs<Outcome.Err<E>>(outcome).error

    @Test
    fun `map transforms an ok value`() {
        assertEquals(4, valueOf(ok.map { it * 2 }))
    }

    @Test
    fun `map passes an error through untouched`() {
        assertEquals("boom", errorOf(err.map { it * 2 }))
    }

    @Test
    fun `flatMap chains an ok into the next outcome`() {
        assertEquals("2", valueOf(ok.flatMap { Outcome.Ok(it.toString()) }))
    }

    @Test
    fun `flatMap can turn an ok into an error of the same hierarchy`() {
        assertEquals("rejected", errorOf(ok.flatMap<String> { Outcome.Err("rejected") }))
    }

    @Test
    fun `flatMap passes an error through without calling the transform`() {
        assertEquals("boom", errorOf(err.flatMap { Outcome.Ok(it.toString()) }))
    }

    @Test
    fun `mapError rewrites an error into another hierarchy`() {
        assertEquals(4, errorOf(err.mapError { it.length }))
    }

    @Test
    fun `mapError leaves an ok untouched`() {
        assertEquals(2, valueOf(ok.mapError { it.length }))
    }

    @Test
    fun `two ok results holding the same value are equal and hash alike`() {
        assertEquals(Outcome.Ok(2), Outcome.Ok(2))
        assertEquals(Outcome.Ok(2).hashCode(), Outcome.Ok(2).hashCode())
        assertEquals(Outcome.Err("boom"), Outcome.Err("boom"))
    }

    @Test
    fun `an ok never renders the payload it carries`() {
        val described = "call bob on 917 555 0199 about the boiler"
        assertEquals("Ok(String)", Outcome.Ok(described).toString())
        assertFalse(Outcome.Ok(described).toString().contains("917"))
        assertFalse(Outcome.Ok(described).toString().contains("bob"))
    }

    @Test
    fun `an ok names the type it carries, so a log line still says what succeeded`() {
        assertEquals("Ok(Int)", Outcome.Ok(2).toString())
        assertEquals("Ok(IdentifierShape)", Outcome.Ok(IdentifierShape("cs_", 8)).toString())
        assertEquals("Ok(null)", Outcome.Ok(null).toString())
    }

    @Test
    fun `an ok carrying a type with no simple name falls back to its full name`() {
        val anonymous = object {}
        assertEquals("Ok(${anonymous.javaClass.name})", Outcome.Ok(anonymous).toString())
    }

    @Test
    fun `an error still renders its facts, because no error variant carries caller text`() {
        assertEquals("Err(error=boom)", Outcome.Err("boom").toString())
        assertEquals(
            "Err(error=BodyOutsideLength(length=0, minimum=1, maximum=64))",
            Outcome.Err(IdentifierFlaw.BodyOutsideLength(0, 1, 64)).toString(),
        )
    }

    @Test
    fun `the free text rule no longer leaks the text it accepted through an ok`() {
        val accepted = FreeTextRule(20, 2000).check("call bob on 917 555 0199 about the boiler")
        assertFalse(accepted.toString().contains("917"))
        assertEquals("call bob on 917 555 0199 about the boiler", valueOf(accepted))
    }

    @Test
    fun `mapError is the only bridge between two error hierarchies`() {
        val second: Outcome<Int, SecondError> = Outcome.Err(SecondError.Late)
        assertEquals(Bridged.FromSecond(SecondError.Late), errorOf(bridge(Outcome.Ok(1), second)))
        val first: Outcome<Int, FirstError> = Outcome.Err(FirstError.Missing)
        assertEquals(Bridged.FromFirst(FirstError.Missing), errorOf(bridge(first, second)))
    }

    private fun bridge(
        first: Outcome<Int, FirstError>,
        second: Outcome<Int, SecondError>,
    ): Outcome<Int, Bridged> =
        first
            .mapError<Bridged> { Bridged.FromFirst(it) }
            .flatMap { second.mapError { error -> Bridged.FromSecond(error) } }

    private sealed interface FirstError {
        data object Missing : FirstError
    }

    private sealed interface SecondError {
        data object Late : SecondError
    }

    private sealed interface Bridged {
        data class FromFirst(
            val error: FirstError,
        ) : Bridged

        data class FromSecond(
            val error: SecondError,
        ) : Bridged
    }
}
