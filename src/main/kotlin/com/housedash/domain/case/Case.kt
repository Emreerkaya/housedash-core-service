package com.housedash.domain.case

import com.housedash.domain.shared.NesterId
import com.housedash.domain.shared.Outcome
import java.time.Instant

enum class CaseState { DRAFT, DESCRIBED }

sealed interface Case {
    val id: CaseId
    val owner: NesterId
    val createdAt: Instant
    val state: CaseState

    companion object {
        const val MAX_PHOTOS = 4

        fun draft(
            id: CaseId,
            owner: NesterId,
            at: Instant,
        ): Case = DraftCase.of(id, owner, at)

        fun rehydrate(
            row: CaseRow,
            photos: List<String>,
        ): Case {
            val id = caseIdOrThrow(row.id)
            val owner = nesterIdOrThrow(row.owner)
            return when (stateOrThrow(row.state)) {
                CaseState.DRAFT -> draftFrom(row, photos, id, owner)
                CaseState.DESCRIBED -> describedFrom(row, photos, id, owner)
            }
        }
    }
}

private fun draftFrom(
    row: CaseRow,
    photos: List<String>,
    id: CaseId,
    owner: NesterId,
): DraftCase {
    draftFaultOf(row, photos)?.let { throw CorruptCase(it) }
    return DraftCase.of(id, owner, row.createdAt)
}

private fun describedFrom(
    row: CaseRow,
    photos: List<String>,
    id: CaseId,
    owner: NesterId,
): DescribedCase {
    val text = row.description ?: throw CorruptCase(CaseFault.DESCRIPTION_ABSENT)
    val describedAt = row.describedAt ?: throw CorruptCase(CaseFault.DESCRIBED_AT_ABSENT)
    return DescribedCase.rehydrated(
        DraftCase.of(id, owner, row.createdAt),
        descriptionOrThrow(text),
        photos.map { photoIdOrThrow(it) },
        describedAt,
    )
}

private fun draftFaultOf(
    row: CaseRow,
    photos: List<String>,
): CaseFault? =
    when {
        row.description != null -> CaseFault.DESCRIPTION_PRESENT_ON_DRAFT
        row.describedAt != null -> CaseFault.DESCRIBED_AT_PRESENT_ON_DRAFT
        photos.isNotEmpty() -> CaseFault.PHOTOS_PRESENT_ON_DRAFT
        else -> null
    }

private fun stateOrThrow(raw: String): CaseState =
    CaseState.entries.firstOrNull { it.name == raw } ?: throw CorruptCase(CaseFault.UNKNOWN_STATE)

private fun caseIdOrThrow(raw: String): CaseId =
    when (val parsed = CaseId.of(raw)) {
        is Outcome.Ok -> parsed.value
        is Outcome.Err -> throw CorruptCase(CaseFault.MALFORMED_ID)
    }

private fun nesterIdOrThrow(raw: String): NesterId =
    when (val parsed = ownerOf(raw)) {
        is Outcome.Ok -> parsed.value
        is Outcome.Err -> throw CorruptCase(CaseFault.MALFORMED_OWNER)
    }

private fun photoIdOrThrow(raw: String): PhotoId =
    when (val parsed = PhotoId.of(raw)) {
        is Outcome.Ok -> parsed.value
        is Outcome.Err -> throw CorruptCase(CaseFault.MALFORMED_PHOTO_ID)
    }

private fun descriptionOrThrow(raw: String): Description =
    when (val parsed = Description.of(raw)) {
        is Outcome.Ok -> parsed.value
        is Outcome.Err -> throw CorruptCase(CaseFault.DESCRIPTION_REJECTED)
    }
