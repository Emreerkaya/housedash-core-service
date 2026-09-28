package com.housedash.domain.case

import com.housedash.domain.shared.NesterId
import com.housedash.domain.shared.Outcome
import java.time.Instant

class DraftCase private constructor(
    override val id: CaseId,
    override val owner: NesterId,
    override val createdAt: Instant,
) : Case {
    init {
        absentFieldFaultOf(id, owner, createdAt)?.let { throw CorruptCase(it) }
    }

    override val state: CaseState get() = CaseState.DRAFT

    fun describe(
        actor: NesterId,
        description: Description,
        photos: List<PhotoId>,
        at: Instant,
    ): Outcome<DescribedCase, CaseError> = DescribedCase.describing(this, actor, description, photos, at)

    internal companion object {
        fun of(
            id: CaseId,
            owner: NesterId,
            createdAt: Instant,
        ): DraftCase = DraftCase(id, owner, createdAt)

        fun rehydrated(
            row: CaseRow,
            photos: List<String>,
            id: CaseId,
            owner: NesterId,
        ): DraftCase {
            draftFaultOf(row, photos)?.let { throw CorruptCase(it) }
            return DraftCase(id, owner, row.createdAt)
        }
    }
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
