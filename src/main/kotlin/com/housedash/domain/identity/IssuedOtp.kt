package com.housedash.domain.identity

import com.housedash.domain.shared.Outcome
import java.time.Duration
import java.time.Instant

enum class OtpRejection { ALREADY_CONSUMED, ATTEMPTS_EXHAUSTED, EXPIRED, WRONG_CODE }

sealed interface OtpVerification {
    data class Accepted(
        val consumed: IssuedOtp,
    ) : OtpVerification

    data class Rejected(
        val updated: IssuedOtp,
        val reason: OtpRejection,
    ) : OtpVerification
}

class IssuedOtp private constructor(
    val id: OtpId,
    val identifier: LoginIdentifier,
    val codeHash: String,
    val issuedAt: Instant,
    val attempts: Int,
    val consumed: Boolean,
) {
    init {
        if (codeHash.isBlank()) throw CorruptOtp(OtpFault.BLANK_CODE_HASH)
        if (attempts < 0 || attempts > ATTEMPT_CAP) throw CorruptOtp(OtpFault.ATTEMPTS_OUT_OF_RANGE)
    }

    val expiresAt: Instant get() = issuedAt.plus(TTL)

    fun verify(
        codeMatches: Boolean,
        now: Instant,
    ): OtpVerification =
        when {
            consumed -> OtpVerification.Rejected(this, OtpRejection.ALREADY_CONSUMED)
            attempts >= ATTEMPT_CAP -> OtpVerification.Rejected(this, OtpRejection.ATTEMPTS_EXHAUSTED)
            !now.isBefore(expiresAt) -> OtpVerification.Rejected(this, OtpRejection.EXPIRED)
            codeMatches -> OtpVerification.Accepted(withState(attempts, consumed = true))
            else -> OtpVerification.Rejected(withState(attempts + 1, consumed = false), OtpRejection.WRONG_CODE)
        }

    private fun withState(
        attempts: Int,
        consumed: Boolean,
    ): IssuedOtp = IssuedOtp(id, identifier, codeHash, issuedAt, attempts, consumed)

    companion object {
        const val ATTEMPT_CAP = 5

        val TTL: Duration = Duration.ofMinutes(10)

        fun issue(
            id: OtpId,
            identifier: LoginIdentifier,
            codeHash: String,
            issuedAt: Instant,
        ): IssuedOtp = IssuedOtp(id, identifier, codeHash, issuedAt, attempts = 0, consumed = false)

        fun rehydrate(row: OtpRow): IssuedOtp {
            val id = otpIdOrThrow(row.id)
            val identifier = otpIdentifierOrThrow(row)
            return IssuedOtp(id, identifier, row.codeHash, row.issuedAt, row.attempts, row.consumed)
        }
    }
}

private fun otpIdOrThrow(raw: String): OtpId =
    when (val parsed = OtpId.of(raw)) {
        is Outcome.Ok -> parsed.value
        is Outcome.Err -> throw CorruptOtp(OtpFault.MALFORMED_ID)
    }

private fun otpIdentifierOrThrow(row: OtpRow): LoginIdentifier {
    val kind =
        IdentifierKind.entries.firstOrNull { it.name == row.identifier.kind }
            ?: throw CorruptOtp(OtpFault.UNKNOWN_IDENTIFIER_KIND)
    return LoginIdentifier.rehydrated(row.identifier.value, kind) ?: throw CorruptOtp(OtpFault.MALFORMED_IDENTIFIER)
}
