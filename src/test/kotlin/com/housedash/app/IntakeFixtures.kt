package com.housedash.app

import com.housedash.domain.case.CaseError
import com.housedash.domain.case.CaseId
import com.housedash.domain.case.DescribedCase
import com.housedash.domain.shared.Outcome
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

internal const val TAP_DESCRIPTION = "kitchen tap drips from the base when the water runs"

internal const val PHONE_IN_A_DESCRIPTION = "kitchen tap drips, reach me on 917,555,0199 today please"

internal val CREATED_AT: Instant = Instant.parse("2026-09-29T09:00:00Z")

internal val FIXED_CLOCK: Clock = Clock.fixed(CREATED_AT, ZoneOffset.UTC)

internal fun intake(
    owner: String? = "ns_1",
    description: String? = TAP_DESCRIPTION,
    photos: List<String>? = listOf("ph_1", "ph_2"),
    key: String? = "intake-1",
): CaseIntake =
    CaseIntake(
        owner?.let { SubmittedId(it) },
        description?.let { SubmittedText(it) },
        photos?.map { SubmittedId(it) },
        key?.let { SubmittedKey(it) },
    )

internal class OneCaseRepository : CaseRepository {
    val storedById = mutableMapOf<String, DescribedCase>()

    private val idsByAttempt = mutableMapOf<IntakeAttempt, CaseId>()

    override fun storeUnlessAlreadyStored(
        attempt: IntakeAttempt,
        case: DescribedCase,
    ): StoredCase {
        val known = idsByAttempt[attempt]
        if (known != null) return StoredCase(known, alreadyStored = true)
        idsByAttempt[attempt] = case.id
        storedById[case.id.value] = case
        return StoredCase(case.id, alreadyStored = false)
    }
}

internal class CountingCaseIdentifiers : CaseIdentifiers {
    private var minted = 0

    override fun next(): Outcome<CaseId, CaseError> {
        minted += 1
        return CaseId.of("cs_$minted")
    }
}

internal class RefusingCaseIdentifiers : CaseIdentifiers {
    override fun next(): Outcome<CaseId, CaseError> = CaseId.of("not-a-case-id")
}

internal fun <T> valueOf(outcome: Outcome<T, CreateCaseFailure>): T =
    when (outcome) {
        is Outcome.Ok -> outcome.value
        is Outcome.Err -> error("expected a stored case and the use case refused with ${outcome.error}")
    }

internal fun failureOf(outcome: Outcome<StoredCase, CreateCaseFailure>): CreateCaseFailure =
    when (outcome) {
        is Outcome.Ok -> error("expected a refusal and the use case stored ${outcome.value.id.value}")
        is Outcome.Err -> outcome.error
    }
