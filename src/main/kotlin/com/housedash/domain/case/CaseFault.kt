package com.housedash.domain.case

enum class CaseFault {
    FIELD_ABSENT,
    MALFORMED_ID,
    MALFORMED_OWNER,
    MALFORMED_PHOTO_ID,
    UNKNOWN_STATE,
    DESCRIPTION_ABSENT,
    DESCRIBED_AT_ABSENT,
    DESCRIPTION_PRESENT_ON_DRAFT,
    DESCRIBED_AT_PRESENT_ON_DRAFT,
    PHOTOS_PRESENT_ON_DRAFT,
    DESCRIPTION_REJECTED,
    TOO_MANY_PHOTOS,
    DUPLICATE_PHOTO,
    DESCRIBED_BEFORE_CREATED,
}

class CorruptCase internal constructor(
    val fault: CaseFault,
) : IllegalStateException(fault.name)

internal fun absentFieldFaultOf(vararg fields: Any?): CaseFault? {
    val anyAbsent = fields.any { it == null }
    return if (anyAbsent) CaseFault.FIELD_ABSENT else null
}
