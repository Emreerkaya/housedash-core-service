package com.housedash.domain.case

import com.housedash.domain.shared.Outcome

class Description private constructor(
    val text: String,
) {
    override fun equals(other: Any?): Boolean = other is Description && other.text == text

    override fun hashCode(): Int = text.hashCode()

    companion object {
        const val MIN_LENGTH = 20
        const val MAX_LENGTH = 2000

        fun of(raw: String): Outcome<Description, CaseError> {
            val trimmed = raw.trim()
            return when {
                trimmed.length < MIN_LENGTH ->
                    Outcome.Err(CaseError.DescriptionTooShort(trimmed.length, MIN_LENGTH))
                trimmed.length > MAX_LENGTH ->
                    Outcome.Err(CaseError.DescriptionTooLong(trimmed.length, MAX_LENGTH))
                else -> Outcome.Ok(Description(trimmed))
            }
        }
    }
}
