package com.housedash.domain.shared

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class OutcomeTest {
    private val ok: Outcome<Int, String> = Outcome.Ok(2)
    private val err: Outcome<Int, String> = Outcome.Err("boom")

    @Test
    fun `map transforms an ok value`() {
        assertEquals(Outcome.Ok(4), ok.map { it * 2 })
    }

    @Test
    fun `map passes an error through untouched`() {
        assertEquals(Outcome.Err("boom"), err.map { it * 2 })
    }

    @Test
    fun `flatMap chains an ok into the next outcome`() {
        assertEquals(Outcome.Ok("2"), ok.flatMap { Outcome.Ok(it.toString()) })
    }

    @Test
    fun `flatMap can turn an ok into an error of the same hierarchy`() {
        assertEquals(Outcome.Err("rejected"), ok.flatMap<String> { Outcome.Err("rejected") })
    }

    @Test
    fun `flatMap passes an error through without calling the transform`() {
        assertEquals(Outcome.Err("boom"), err.flatMap { Outcome.Ok(it.toString()) })
    }

    @Test
    fun `mapError rewrites an error into another hierarchy`() {
        assertEquals(Outcome.Err(4), err.mapError { it.length })
    }

    @Test
    fun `mapError leaves an ok untouched`() {
        assertEquals(Outcome.Ok(2), ok.mapError { it.length })
    }

    @Test
    fun `mapError is the only bridge between two error hierarchies`() {
        val second: Outcome<Int, SecondError> = Outcome.Err(SecondError.Late)
        assertEquals(Outcome.Err(Bridged.FromSecond(SecondError.Late)), bridge(Outcome.Ok(1), second))
        val first: Outcome<Int, FirstError> = Outcome.Err(FirstError.Missing)
        assertEquals(Outcome.Err(Bridged.FromFirst(FirstError.Missing)), bridge(first, second))
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
