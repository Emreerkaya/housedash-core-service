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
            draft: DraftCase,
            description: Description,
            photos: List<PhotoId>,
            describedAt: Instant,
        ): DescribedCase =
            DescribedCase(
                draft.id,
                draft.owner,
                draft.createdAt,
                description,
                CasePhotos.rehydrated(photos),
                describedAt,
            )
    }
}
