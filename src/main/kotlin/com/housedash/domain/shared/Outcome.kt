package com.housedash.domain.shared

sealed interface Outcome<out T, out E> {
    data class Ok<out T>(
        val value: T,
    ) : Outcome<T, Nothing>

    data class Err<out E>(
        val error: E,
    ) : Outcome<Nothing, E>
}

fun <T, E, R> Outcome<T, E>.map(f: (T) -> R): Outcome<R, E> =
    when (this) {
        is Outcome.Ok -> Outcome.Ok(f(value))
        is Outcome.Err -> this
    }

fun <T, E, R> Outcome<T, E>.flatMap(f: (T) -> Outcome<R, E>): Outcome<R, E> =
    when (this) {
        is Outcome.Ok -> f(value)
        is Outcome.Err -> this
    }
