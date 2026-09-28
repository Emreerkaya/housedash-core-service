package com.housedash.domain.case

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
        ): DraftCase = DraftCase(id, owner, at)

        fun rehydrate(
            row: CaseRow,
            photos: List<String>,
        ): Case {
            val id = caseIdOrThrow(row.id)
            val owner = nesterIdOrThrow(row.owner)
            return when (row.state) {
                CaseState.DRAFT.name -> draftFrom(row, photos, id, owner)
                CaseState.DESCRIBED.name -> describedFrom(row, photos, id, owner)
                else -> throw CorruptCase(CaseFault.UNKNOWN_STATE)
            }
        }
    }
}

class DraftCase internal constructor(
    override val id: CaseId,
    override val owner: NesterId,
    override val createdAt: Instant,
) : Case {
    override val state: CaseState get() = CaseState.DRAFT

    fun describe(
        actor: NesterId,
        description: Description,
        photos: List<PhotoId>,
        at: Instant,
    ): Outcome<DescribedCase, CaseError> {
        if (actor != owner) return Outcome.Err(CaseError.NotOwner)
        if (photos.size > Case.MAX_PHOTOS) {
            return Outcome.Err(CaseError.TooManyPhotos(photos.size, Case.MAX_PHOTOS))
        }
        val seen = mutableSetOf<PhotoId>()
        photos.forEachIndexed { position, photo ->
            if (!seen.add(photo)) return Outcome.Err(CaseError.DuplicatePhoto(position))
        }
        return Outcome.Ok(DescribedCase(id, owner, createdAt, description, photos, at))
    }
}

class DescribedCase internal constructor(
    override val id: CaseId,
    override val owner: NesterId,
    override val createdAt: Instant,
    val description: Description,
    photos: List<PhotoId>,
    val describedAt: Instant,
) : Case {
    val photos: List<PhotoId> = photos.toList()

    init {
        describedFaultOf(this.photos, createdAt, describedAt)?.let { throw CorruptCase(it) }
    }

    override val state: CaseState get() = CaseState.DESCRIBED
}

private fun describedFaultOf(
    photos: List<PhotoId>,
    createdAt: Instant,
    describedAt: Instant,
): CaseFault? =
    when {
        photos.size > Case.MAX_PHOTOS -> CaseFault.TOO_MANY_PHOTOS
        photos.distinct().size != photos.size -> CaseFault.DUPLICATE_PHOTO
        describedAt.isBefore(createdAt) -> CaseFault.DESCRIBED_BEFORE_CREATED
        else -> null
    }

private fun draftFrom(
    row: CaseRow,
    photos: List<String>,
    id: CaseId,
    owner: NesterId,
): DraftCase {
    draftFaultOf(row, photos)?.let { throw CorruptCase(it) }
    return DraftCase(id, owner, row.createdAt)
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

private fun describedFrom(
    row: CaseRow,
    photos: List<String>,
    id: CaseId,
    owner: NesterId,
): DescribedCase {
    val text = row.description ?: throw CorruptCase(CaseFault.DESCRIPTION_ABSENT)
    val describedAt = row.describedAt ?: throw CorruptCase(CaseFault.DESCRIBED_AT_ABSENT)
    return DescribedCase(
        id,
        owner,
        row.createdAt,
        descriptionOrThrow(text),
        photos.map { photoIdOrThrow(it) },
        describedAt,
    )
}

private fun caseIdOrThrow(raw: String): CaseId =
    when (val parsed = CaseId.of(raw)) {
        is Outcome.Ok -> parsed.value
        is Outcome.Err -> throw CorruptCase(CaseFault.MALFORMED_ID)
    }

private fun nesterIdOrThrow(raw: String): NesterId =
    when (val parsed = NesterId.of(raw)) {
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
