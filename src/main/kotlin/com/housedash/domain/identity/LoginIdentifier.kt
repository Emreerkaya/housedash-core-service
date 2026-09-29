package com.housedash.domain.identity

import com.housedash.domain.shared.Outcome
import com.housedash.domain.shared.characterCount
import com.housedash.domain.shared.isPlainText

enum class IdentifierKind { EMAIL, PHONE }

sealed interface LoginIdentifierFlaw {
    data object Empty : LoginIdentifierFlaw

    data object NotPlainText : LoginIdentifierFlaw

    data class TooLong(
        val length: Int,
        val maximum: Int,
    ) : LoginIdentifierFlaw

    data object UnrecognizedShape : LoginIdentifierFlaw
}

private const val MOST_CHARACTERS_AN_IDENTIFIER_HOLDS = 254

private const val EMAIL_LOCAL_PART = """[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*"""

private const val EMAIL_DOMAIN_LABEL = """[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"""

private val EMAIL_SHAPE = Regex("^$EMAIL_LOCAL_PART@(?:$EMAIL_DOMAIN_LABEL\\.)+[A-Za-z]{2,24}$")

private val PHONE_PUNCTUATION_STRIPPED = Regex("""[\s().-]""")

private val PHONE_SHAPE = Regex("^\\+[1-9][0-9]{7,14}$")

class LoginIdentifier private constructor(
    val value: String,
    val kind: IdentifierKind,
) {
    override fun equals(other: Any?): Boolean = other is LoginIdentifier && other.value == value && other.kind == kind

    override fun hashCode(): Int = value.hashCode() * 31 + kind.hashCode()

    override fun toString(): String = value

    companion object {
        fun of(raw: String): Outcome<LoginIdentifier, LoginIdentifierFlaw> {
            val trimmed = raw.trim()
            basicFlawIn(trimmed)?.let { return Outcome.Err(it) }
            IdentifierKind.entries.forEach { kind ->
                canonicalFormOf(trimmed, kind)?.let { return Outcome.Ok(LoginIdentifier(it, kind)) }
            }
            return Outcome.Err(LoginIdentifierFlaw.UnrecognizedShape)
        }

        fun rehydrated(
            value: String,
            kind: IdentifierKind,
        ): LoginIdentifier? = canonicalFormOf(value, kind)?.let { LoginIdentifier(it, kind) }
    }
}

private fun basicFlawIn(trimmed: String): LoginIdentifierFlaw? {
    if (trimmed.isEmpty()) return LoginIdentifierFlaw.Empty
    if (!isPlainText(trimmed)) return LoginIdentifierFlaw.NotPlainText
    val characters = characterCount(trimmed)
    if (characters > MOST_CHARACTERS_AN_IDENTIFIER_HOLDS) {
        return LoginIdentifierFlaw.TooLong(characters, MOST_CHARACTERS_AN_IDENTIFIER_HOLDS)
    }
    return null
}

private fun canonicalFormOf(
    raw: String,
    kind: IdentifierKind,
): String? =
    when (kind) {
        IdentifierKind.EMAIL -> raw.lowercase().takeIf { EMAIL_SHAPE.matches(it) }
        IdentifierKind.PHONE -> raw.replace(PHONE_PUNCTUATION_STRIPPED, "").takeIf { PHONE_SHAPE.matches(it) }
    }
