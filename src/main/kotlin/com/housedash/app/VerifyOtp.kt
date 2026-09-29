package com.housedash.app

import com.housedash.domain.identity.OtpVerification
import com.housedash.domain.shared.Outcome
import java.time.Clock

class VerifyOtpRequest(
    val identifier: SubmittedIdentifier?,
    val code: SubmittedCode?,
)

sealed interface VerifyOtpFailure {
    data object Invalid : VerifyOtpFailure
}

class VerifyOtp(
    private val otps: OtpRepository,
    private val hasher: OtpHasher,
    private val clock: Clock,
) {
    fun handle(request: VerifyOtpRequest): Outcome<Unit, VerifyOtpFailure> {
        val identifierField = request.identifier ?: return Outcome.Err(VerifyOtpFailure.Invalid)
        val codeField = request.code ?: return Outcome.Err(VerifyOtpFailure.Invalid)
        val identifier =
            when (val parsed = identifierField.asLoginIdentifier()) {
                is Outcome.Ok -> parsed.value
                is Outcome.Err -> return Outcome.Err(VerifyOtpFailure.Invalid)
            }
        val existing = otps.latestFor(identifier) ?: return Outcome.Err(VerifyOtpFailure.Invalid)
        val now = clock.instant()
        val codeMatches = codeField.matches(existing.codeHash, hasher)
        return when (val result = existing.verify(codeMatches, now)) {
            is OtpVerification.Accepted -> {
                otps.update(result.consumed)
                Outcome.Ok(Unit)
            }
            is OtpVerification.Rejected -> {
                otps.update(result.updated)
                Outcome.Err(VerifyOtpFailure.Invalid)
            }
        }
    }
}
