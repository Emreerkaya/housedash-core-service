package com.housedash.domain.case

import com.housedash.domain.shared.FreeTextRule
import com.housedash.domain.shared.Outcome
import com.housedash.domain.shared.TextFlaw
import com.housedash.domain.shared.withoutContactDetails

class Description private constructor(
    val text: String,
) {
    init {
        require(shaped(text) is Outcome.Ok<*>) { "description text does not satisfy the description shape rule" }
    }

    override fun equals(other: Any?): Boolean = other is Description && other.text == text

    override fun hashCode(): Int = text.hashCode()

    companion object {
        const val MIN_LENGTH = 20
        const val MAX_LENGTH = 2000

        private val RULE = FreeTextRule(MIN_LENGTH, MAX_LENGTH)

        fun of(raw: String): Outcome<Description, CaseError> =
            shaped(raw)
                .flatMap(::withoutContactDetails)
                .mapError(::asCaseError)
                .map { Description(it) }

        fun rehydrated(stored: String): Outcome<Description, CaseError> =
            shaped(stored)
                .mapError(::asCaseError)
                .map { Description(it) }

        private fun shaped(raw: String): Outcome<String, TextFlaw> = RULE.check(raw)

        private fun asCaseError(flaw: TextFlaw): CaseError =
            when (flaw) {
                TextFlaw.NotPlainText -> CaseError.DescriptionNotPlainText
                is TextFlaw.TooShort -> CaseError.DescriptionTooShort(flaw.length, flaw.minimum)
                is TextFlaw.TooLong -> CaseError.DescriptionTooLong(flaw.length, flaw.maximum)
                is TextFlaw.ContactDetails -> CaseError.ContactDetailsInDescription(flaw.kinds)
            }
    }
}
