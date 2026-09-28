package com.housedash.domain.shared

sealed interface IdentifierFlaw {
    data object WrongPrefix : IdentifierFlaw

    data class BodyOutsideLength(
        val length: Int,
        val minimum: Int,
        val maximum: Int,
    ) : IdentifierFlaw

    data object IllegalCharacterInBody : IdentifierFlaw
}

private const val MINIMUM_BODY_LENGTH = 1

private val PERMITTED_BODY = Regex("^[0-9A-Za-z_-]+${'$'}")

class IdentifierShape(
    private val prefix: String,
    private val maximumBodyLength: Int,
) {
    fun accepts(raw: String): Boolean = check(raw) is Outcome.Ok

    fun check(raw: String): Outcome<String, IdentifierFlaw> {
        if (!raw.startsWith(prefix)) return Outcome.Err(IdentifierFlaw.WrongPrefix)
        val body = raw.substring(prefix.length)
        if (body.length < MINIMUM_BODY_LENGTH || body.length > maximumBodyLength) {
            return Outcome.Err(IdentifierFlaw.BodyOutsideLength(body.length, MINIMUM_BODY_LENGTH, maximumBodyLength))
        }
        if (!PERMITTED_BODY.matches(body)) return Outcome.Err(IdentifierFlaw.IllegalCharacterInBody)
        return Outcome.Ok(raw)
    }
}

abstract class Identifier protected constructor(
    val value: String,
    shape: IdentifierShape,
) {
    init {
        require(shape.accepts(value)) { "identifier does not match the shape of its type" }
    }

    abstract override fun toString(): String

    final override fun equals(other: Any?): Boolean = other is Identifier && sameAs(other)

    private fun sameAs(other: Identifier): Boolean = other.javaClass == javaClass && other.value == value

    final override fun hashCode(): Int = value.hashCode()
}
