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
        ): DraftCase = DraftCase.of(id, owner, at)

        fun rehydrate(
            row: CaseRow,
            photos: List<String>,
        ): Case {
            val id = caseIdOrThrow(row.id)
            val owner = nesterIdOrThrow(row.owner)
            return when (stateOrThrow(row.state)) {
                CaseState.DRAFT -> DraftCase.rehydrated(row, photos, id, owner)
                CaseState.DESCRIBED -> DescribedCase.rehydrated(row, photos, id, owner)
            }
        }
    }
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
