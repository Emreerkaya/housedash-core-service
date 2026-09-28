package com.housedash.domain.case

import com.housedash.domain.shared.Outcome
import java.time.Instant

enum class CaseState { DRAFT, DESCRIBED }

class Case private constructor(
    val id: CaseId,
    val state: CaseState,
    val description: Description?,
    val photos: List<PhotoId>,
    val createdAt: Instant,
    val describedAt: Instant?,
) {
    fun describe(
        description: Description,
        photos: List<PhotoId>,
        at: Instant,
    ): Outcome<Case, CaseError> {
        if (state != CaseState.DRAFT) return Outcome.Err(CaseError.AlreadyDescribed)
        if (photos.size > MAX_PHOTOS) {
            return Outcome.Err(CaseError.TooManyPhotos(photos.size, MAX_PHOTOS))
        }
        val duplicate =
            photos
                .groupingBy { it }
                .eachCount()
                .entries
                .firstOrNull { it.value > 1 }
        if (duplicate != null) return Outcome.Err(CaseError.DuplicatePhoto(duplicate.key))
        return Outcome.Ok(
            Case(id, CaseState.DESCRIBED, description, photos.toList(), createdAt, at),
        )
    }

    companion object {
        const val MAX_PHOTOS = 4

        fun draft(
            id: CaseId,
            at: Instant,
        ): Case = Case(id, CaseState.DRAFT, null, emptyList(), at, null)
    }
}
