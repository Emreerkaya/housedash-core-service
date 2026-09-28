package com.housedash.domain.case

import com.housedash.domain.shared.Identifier
import com.housedash.domain.shared.IdentifierShape
import com.housedash.domain.shared.NesterId
import com.housedash.domain.shared.Outcome

fun ownerOf(raw: String): Outcome<NesterId, CaseError> = NesterId.of(raw).mapError(CaseError::MalformedNesterId)

class CaseId private constructor(
    value: String,
) : Identifier(value, SHAPE) {
    override fun toString(): String = value

    companion object {
        const val MAX_BODY_LENGTH = 64

        private val SHAPE = IdentifierShape("cs_", MAX_BODY_LENGTH)

        fun of(raw: String): Outcome<CaseId, CaseError> =
            SHAPE
                .check(raw)
                .mapError(CaseError::MalformedCaseId)
                .map { CaseId(it) }
    }
}

class PhotoId private constructor(
    value: String,
) : Identifier(value, SHAPE) {
    override fun toString(): String = value

    companion object {
        const val MAX_BODY_LENGTH = 64

        private val SHAPE = IdentifierShape("ph_", MAX_BODY_LENGTH)

        fun of(raw: String): Outcome<PhotoId, CaseError> =
            SHAPE
                .check(raw)
                .mapError(CaseError::MalformedPhotoId)
                .map { PhotoId(it) }
    }
}
