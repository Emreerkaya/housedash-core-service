package com.housedash.domain.shared

class NesterId private constructor(
    value: String,
) : Identifier(value, SHAPE) {
    override fun toString(): String = value

    companion object {
        const val MAX_BODY_LENGTH = 64

        private val SHAPE = IdentifierShape("ns_", MAX_BODY_LENGTH)

        fun of(raw: String): Outcome<NesterId, IdentifierFlaw> = SHAPE.check(raw).map { NesterId(it) }
    }
}
