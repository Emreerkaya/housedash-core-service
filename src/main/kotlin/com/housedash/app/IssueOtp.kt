package com.housedash.app

import com.housedash.domain.identity.IdentityError
import com.housedash.domain.identity.IssuedOtp
import com.housedash.domain.identity.LoginIdentifier
import com.housedash.domain.identity.LoginIdentifierFlaw
import com.housedash.domain.shared.Outcome
import java.time.Clock
import java.time.Duration

class IssueOtpRequest(
    val identifier: SubmittedIdentifier?,
    val requesterIp: String,
)

sealed interface IssueOtpFailure {
    data object IdentifierAbsent : IssueOtpFailure

    data class MalformedIdentifier(
        val flaw: LoginIdentifierFlaw,
    ) : IssueOtpFailure

    data object RateLimited : IssueOtpFailure

    data class OtpIdRefused(
        val error: IdentityError,
    ) : IssueOtpFailure
}

class OtpDelivery(
    val codes: OtpCodeGenerator,
    val hasher: OtpHasher,
    val sender: OtpSender,
)

class IssueOtp(
    private val otps: OtpRepository,
    private val ids: OtpIdentifiers,
    private val delivery: OtpDelivery,
    private val clock: Clock,
) {
    fun handle(request: IssueOtpRequest): Outcome<Unit, IssueOtpFailure> =
        identifierOf(request)
            .flatMap { rateLimitChecked(it, request.requesterIp) }
            .flatMap { issued(it, request.requesterIp) }

    private fun identifierOf(request: IssueOtpRequest): Outcome<LoginIdentifier, IssueOtpFailure> {
        val submitted = request.identifier ?: return Outcome.Err(IssueOtpFailure.IdentifierAbsent)
        return submitted.asLoginIdentifier().mapError(IssueOtpFailure::MalformedIdentifier)
    }

    private fun rateLimitChecked(
        identifier: LoginIdentifier,
        ip: String,
    ): Outcome<LoginIdentifier, IssueOtpFailure> {
        val since = clock.instant().minus(RATE_LIMIT_WINDOW)
        if (otps.issuedCountSince(identifier, since) >= MAX_ISSUANCES_PER_IDENTIFIER) {
            return Outcome.Err(IssueOtpFailure.RateLimited)
        }
        if (otps.issuedCountFromIpSince(ip, since) >= MAX_ISSUANCES_PER_IP) {
            return Outcome.Err(IssueOtpFailure.RateLimited)
        }
        return Outcome.Ok(identifier)
    }

    private fun issued(
        identifier: LoginIdentifier,
        ip: String,
    ): Outcome<Unit, IssueOtpFailure> {
        val now = clock.instant()
        val id = ids.next().mapError(IssueOtpFailure::OtpIdRefused).valueOr { return it }
        val code = delivery.codes.next()
        val otp = IssuedOtp.issue(id, identifier, delivery.hasher.hash(code), now)
        otps.insert(otp, ip)
        delivery.sender.send(identifier, code)
        return Outcome.Ok(Unit)
    }

    private companion object {
        val RATE_LIMIT_WINDOW: Duration = Duration.ofMinutes(15)

        const val MAX_ISSUANCES_PER_IDENTIFIER = 3

        const val MAX_ISSUANCES_PER_IP = 10
    }
}

private inline fun <T> Outcome<T, IssueOtpFailure>.valueOr(bail: (Outcome.Err<IssueOtpFailure>) -> Nothing): T =
    when (this) {
        is Outcome.Ok -> value
        is Outcome.Err -> bail(this)
    }
