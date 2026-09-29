package com.housedash.app

import com.housedash.domain.case.CaseError
import com.housedash.domain.case.Description
import com.housedash.domain.case.PhotoId
import com.housedash.domain.case.ownerOf
import com.housedash.domain.shared.IdentifierShape
import com.housedash.domain.shared.NesterId
import com.housedash.domain.shared.Outcome

class SubmittedText(
    private val submitted: String,
) {
    fun asDescription(): Outcome<Description, CaseError> = Description.of(submitted)
}

class SubmittedId(
    private val submitted: String,
) {
    fun asPhotoId(): Outcome<PhotoId, CaseError> = PhotoId.of(submitted)

    fun asOwner(): Outcome<NesterId, CaseError> = ownerOf(submitted)
}

class SubmittedKey(
    private val submitted: String,
) {
    fun asIntakeKey(): Outcome<IntakeKey, CreateCaseFailure> = IntakeKey.of(submitted)
}

class IntakeKey private constructor(
    val value: String,
) {
    override fun equals(other: Any?): Boolean = other is IntakeKey && other.value == value

    override fun hashCode(): Int = value.hashCode()

    companion object {
        const val MOST_CHARACTERS = 128

        private val TOKEN_SHAPE = IdentifierShape("", MOST_CHARACTERS)

        fun of(raw: String): Outcome<IntakeKey, CreateCaseFailure> =
            TOKEN_SHAPE
                .check(raw)
                .mapError { CreateCaseFailure.MalformedIntakeKey }
                .map { IntakeKey(it) }
    }
}
