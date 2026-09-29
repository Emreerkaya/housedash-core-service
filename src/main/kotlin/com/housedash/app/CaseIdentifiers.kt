package com.housedash.app

import com.housedash.domain.case.CaseError
import com.housedash.domain.case.CaseId
import com.housedash.domain.shared.Outcome
import java.util.UUID

fun interface CaseIdentifiers {
    fun next(): Outcome<CaseId, CaseError>
}

private const val CASE_ID_PREFIX = "cs_"

private const val MARK_UUID_GROUPS_ARE_SPLIT_BY = "-"

class RandomCaseIdentifiers : CaseIdentifiers {
    override fun next(): Outcome<CaseId, CaseError> =
        CaseId.of(CASE_ID_PREFIX + UUID.randomUUID().toString().replace(MARK_UUID_GROUPS_ARE_SPLIT_BY, ""))
}
