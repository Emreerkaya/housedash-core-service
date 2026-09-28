package com.housedash.domain.case

import com.housedash.domain.shared.Identifier
import com.housedash.domain.shared.IdentifierShape
import com.housedash.domain.shared.Outcome

class CaseId private constructor(
    value: String,
) : Identifier(value, SHAPE) {
    companion object {
        const val MAX_BODY_LENGTH = 64

        private val SHAPE = IdentifierShape("cs_", MAX_BODY_LENGTH)

        fun of(raw: String): Outcome<CaseId, CaseError> =
            if (SHAPE.accepts(raw)) Outcome.Ok(CaseId(raw)) else Outcome.Err(CaseError.MalformedCaseId)
    }
}

class PhotoId private constructor(
    value: String,
) : Identifier(value, SHAPE) {
    companion object {
        const val MAX_BODY_LENGTH = 64

        private val SHAPE = IdentifierShape("ph_", MAX_BODY_LENGTH)

        fun of(raw: String): Outcome<PhotoId, CaseError> =
            if (SHAPE.accepts(raw)) Outcome.Ok(PhotoId(raw)) else Outcome.Err(CaseError.MalformedPhotoId)
    }
}

class NesterId private constructor(
    value: String,
) : Identifier(value, SHAPE) {
    companion object {
        const val MAX_BODY_LENGTH = 64

        private val SHAPE = IdentifierShape("ns_", MAX_BODY_LENGTH)

        fun of(raw: String): Outcome<NesterId, CaseError> =
            if (SHAPE.accepts(raw)) Outcome.Ok(NesterId(raw)) else Outcome.Err(CaseError.MalformedNesterId)
    }
}
