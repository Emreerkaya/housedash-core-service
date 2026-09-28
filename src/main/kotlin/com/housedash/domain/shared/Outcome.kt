package com.housedash.domain.shared

sealed interface Outcome<out T, out E> {
    fun <R> map(transform: (T) -> R): Outcome<R, E>

    fun <R> flatMap(transform: (T) -> Outcome<R, @UnsafeVariance E>): Outcome<R, E>

    fun <F> mapError(transform: (E) -> F): Outcome<T, F>

    data class Ok<out T>(
        val value: T,
    ) : Outcome<T, Nothing> {
        override fun <R> map(transform: (T) -> R): Outcome<R, Nothing> = Ok(transform(value))

        override fun <R> flatMap(transform: (T) -> Outcome<R, Nothing>): Outcome<R, Nothing> = transform(value)

        override fun <F> mapError(transform: (Nothing) -> F): Outcome<T, F> = this
    }

    data class Err<out E>(
        val error: E,
    ) : Outcome<Nothing, E> {
        override fun <R> map(transform: (Nothing) -> R): Outcome<R, E> = this

        override fun <R> flatMap(transform: (Nothing) -> Outcome<R, @UnsafeVariance E>): Outcome<R, E> = this

        override fun <F> mapError(transform: (E) -> F): Outcome<Nothing, F> = Err(transform(error))
    }
}
