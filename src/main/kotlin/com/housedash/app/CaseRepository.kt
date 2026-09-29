package com.housedash.app

import com.housedash.domain.case.CaseId
import com.housedash.domain.case.DescribedCase
import com.housedash.domain.shared.NesterId

data class IntakeAttempt(
    val owner: NesterId,
    val key: IntakeKey,
)

class StoredCase(
    val id: CaseId,
    val alreadyStored: Boolean,
)

interface CaseRepository {
    fun storeUnlessAlreadyStored(
        attempt: IntakeAttempt,
        case: DescribedCase,
    ): StoredCase
}
