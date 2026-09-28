package com.housedash.domain.case

import java.time.Instant

class CaseRow(
    val id: String,
    val owner: String,
    val state: String,
    val description: String?,
    val createdAt: Instant,
    val describedAt: Instant?,
)

enum class CaseFault {
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
