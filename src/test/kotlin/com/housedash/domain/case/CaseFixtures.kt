package com.housedash.domain.case

import com.housedash.domain.shared.Outcome
import java.time.Instant
import kotlin.test.assertIs

internal val createdAt: Instant = Instant.parse("2026-09-24T10:00:00Z")
internal val describedAt: Instant = Instant.parse("2026-09-24T10:05:00Z")

internal const val TAP_DESCRIPTION = "kitchen tap drips from the base"

internal fun caseId(raw: String = "cs_1"): CaseId = ok(CaseId.of(raw))

internal fun nesterId(raw: String = "ns_1"): NesterId = ok(NesterId.of(raw))

internal fun photoId(raw: String = "ph_1"): PhotoId = ok(PhotoId.of(raw))

internal fun description(raw: String = TAP_DESCRIPTION): Description = ok(Description.of(raw))

private fun <T> ok(outcome: Outcome<T, CaseError>): T = assertIs<Outcome.Ok<T>>(outcome).value

internal fun draft(
    owner: NesterId = nesterId(),
    at: Instant = createdAt,
): DraftCase = Case.draft(caseId(), owner, at)

internal fun described(
    photos: List<PhotoId> = listOf(photoId()),
    at: Instant = describedAt,
): DescribedCase = assertIs<Outcome.Ok<DescribedCase>>(draft().describe(nesterId(), description(), photos, at)).value
