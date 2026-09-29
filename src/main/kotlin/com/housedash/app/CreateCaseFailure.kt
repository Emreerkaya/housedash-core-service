package com.housedash.app

import com.housedash.domain.case.CaseError

sealed interface CreateCaseFailure {
    data class Rejected(
        val error: CaseError,
    ) : CreateCaseFailure

    data class CaseIdRefused(
        val error: CaseError,
    ) : CreateCaseFailure

    data object OwnerAbsent : CreateCaseFailure

    data object DescriptionAbsent : CreateCaseFailure

    data object IntakeAbsent : CreateCaseFailure

    data object IntakeKeyAbsent : CreateCaseFailure

    data object MalformedIntakeKey : CreateCaseFailure
}
