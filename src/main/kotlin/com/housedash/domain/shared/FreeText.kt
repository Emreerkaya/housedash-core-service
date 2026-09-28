package com.housedash.domain.shared

import java.text.Normalizer

sealed interface TextFlaw {
    data object NotPlainText : TextFlaw

    data class TooShort(
        val length: Int,
        val minimum: Int,
    ) : TextFlaw

    data class TooLong(
        val length: Int,
        val maximum: Int,
    ) : TextFlaw

    data class ContactDetails(
        val kinds: Set<ContactDetail>,
    ) : TextFlaw
}

class FreeTextRule(
    private val minimumCharacters: Int,
    private val maximumCharacters: Int,
) {
    fun check(raw: String): Outcome<String, TextFlaw> {
        if (!isPlainText(raw)) return Outcome.Err(TextFlaw.NotPlainText)
        val normalised = Normalizer.normalize(raw, Normalizer.Form.NFC).trim()
        val characters = characterCount(normalised)
        return when {
            characters < minimumCharacters -> Outcome.Err(TextFlaw.TooShort(characters, minimumCharacters))
            characters > maximumCharacters -> Outcome.Err(TextFlaw.TooLong(characters, maximumCharacters))
            else -> Outcome.Ok(normalised)
        }
    }
}
