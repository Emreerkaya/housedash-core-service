package com.housedash.domain.identity

import com.housedash.domain.shared.Outcome
import java.time.Instant
import kotlin.test.assertIs

internal val createdAt: Instant = Instant.parse("2026-09-24T10:00:00Z")

internal val issuedAt: Instant = Instant.parse("2026-09-24T10:00:00Z")

internal const val AN_EMAIL = "nester@example.com"

internal const val A_PHONE = "+15555550100"

internal const val A_HASH = "9f2b3c4d5e"

internal fun accountId(raw: String = "ac_1"): AccountId = ok(AccountId.of(raw))

internal fun otpId(raw: String = "otp_1"): OtpId = ok(OtpId.of(raw))

internal fun emailIdentifier(raw: String = AN_EMAIL): LoginIdentifier = ok(LoginIdentifier.of(raw))

internal fun phoneIdentifier(raw: String = A_PHONE): LoginIdentifier = ok(LoginIdentifier.of(raw))

private fun <T, E> ok(outcome: Outcome<T, E>): T = assertIs<Outcome.Ok<T>>(outcome).value

internal fun anAccount(
    id: AccountId = accountId(),
    identifier: LoginIdentifier = emailIdentifier(),
    role: ProfileKind = ProfileKind.NESTER,
    at: Instant = createdAt,
): Account = Account.opened(id, identifier, role, at)

internal fun anIssuedOtp(
    id: OtpId = otpId(),
    identifier: LoginIdentifier = emailIdentifier(),
    codeHash: String = A_HASH,
    at: Instant = issuedAt,
): IssuedOtp = IssuedOtp.issue(id, identifier, codeHash, at)
