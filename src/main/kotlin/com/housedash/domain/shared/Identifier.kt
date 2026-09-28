package com.housedash.domain.shared

class IdentifierShape(
    prefix: String,
    maximumBodyLength: Int,
) {
    private val pattern = Regex("^${Regex.escape(prefix)}[0-9A-Za-z_-]{1,$maximumBodyLength}${'$'}")

    fun accepts(raw: String): Boolean = pattern.matches(raw)
}

abstract class Identifier internal constructor(
    val value: String,
    shape: IdentifierShape,
) {
    init {
        require(shape.accepts(value)) { "identifier does not match the shape of its type" }
    }

    final override fun equals(other: Any?): Boolean = other is Identifier && sameAs(other)

    private fun sameAs(other: Identifier): Boolean = other.javaClass == javaClass && other.value == value

    final override fun hashCode(): Int = value.hashCode()
}
