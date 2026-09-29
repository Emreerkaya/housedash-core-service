package com.housedash.adapters.outbound.persistence

import com.housedash.app.CaseRepository
import com.housedash.app.IntakeAttempt
import com.housedash.app.StoredCase
import com.housedash.domain.case.Case
import com.housedash.domain.case.CaseId
import com.housedash.domain.case.CaseRow
import com.housedash.domain.case.CaseState
import com.housedash.domain.case.DescribedCase
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class InMemoryCaseRepository : CaseRepository {
    private val rowsById = ConcurrentHashMap<String, CaseRow>()

    private val photoIdsById = ConcurrentHashMap<String, List<String>>()

    private val idsByAttempt = ConcurrentHashMap<IntakeAttempt, CaseId>()

    override fun storeUnlessAlreadyStored(
        attempt: IntakeAttempt,
        case: DescribedCase,
    ): StoredCase {
        val alreadyStored = AtomicBoolean(true)
        val id =
            idsByAttempt.computeIfAbsent(attempt) {
                alreadyStored.set(false)
                rowsById[case.id.value] = rowOf(case)
                photoIdsById[case.id.value] = case.photos.map { it.value }
                case.id
            }
        return StoredCase(id, alreadyStored.get())
    }

    fun caseAt(id: CaseId): Case? {
        val row = rowsById[id.value] ?: return null
        return Case.rehydrate(row, photoIdsById[id.value].orEmpty())
    }

    fun storedCases(): Int = rowsById.size
}

private fun rowOf(case: DescribedCase): CaseRow =
    CaseRow(
        id = case.id.value,
        owner = case.owner.value,
        state = CaseState.DESCRIBED.name,
        description = case.description.text,
        createdAt = case.createdAt,
        describedAt = case.describedAt,
    )
