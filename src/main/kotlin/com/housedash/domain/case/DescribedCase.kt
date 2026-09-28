package com.housedash.domain.case

import com.housedash.domain.shared.NesterId
import com.housedash.domain.shared.Outcome
import java.time.Instant

class DescribedCase private constructor(
    override val id: CaseId,
    override val owner: NesterId,
    override val createdAt: Instant,
    val description: Description,
    private val photographs: CasePhotos,
    val describedAt: Instant,
) : Case {
    init {
        absentFieldFaultOf(id, owner, createdAt, description, photographs, describedAt)
            ?.let { throw CorruptCase(it) }
        if (describedAt.isBefore(createdAt)) throw CorruptCase(CaseFault.DESCRIBED_BEFORE_CREATED)
    }

    override val state: CaseState get() = CaseState.DESCRIBED

    val photos: List<PhotoId> get() = photographs.ids

    internal companion object {
        fun describing(
            draft: DraftCase,
            actor: NesterId,
            description: Description,
            photos: List<PhotoId>,
            at: Instant,
        ): Outcome<DescribedCase, CaseError> {
            if (actor != draft.owner) return Outcome.Err(CaseError.NotOwner)
            return CasePhotos
                .of(photos)
                .map { DescribedCase(draft.id, draft.owner, draft.createdAt, description, it, at) }
        }

        fun rehydrated(
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
                CasePhotos.rehydrated(photos.map { photoIdOrThrow(it) }),
                describedAt,
            )
        }
    }
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
