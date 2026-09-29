package com.housedash.app

import com.housedash.domain.identity.IdentityError
import com.housedash.domain.identity.IssuedOtp
import com.housedash.domain.identity.LoginIdentifier
import com.housedash.domain.identity.OtpId
import com.housedash.domain.shared.Outcome
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

internal const val AN_IDENTIFIER = "otp-nester@example.com"

internal const val A_CODE = "482913"

internal const val AN_IP = "203.0.113.9"

internal val OTP_ISSUED_AT: Instant = Instant.parse("2026-09-29T09:00:00Z")

internal val OTP_CLOCK: Clock = Clock.fixed(OTP_ISSUED_AT, ZoneOffset.UTC)

internal val TEST_HASHER: OtpHasher = Sha256OtpHasher("test-pepper")

internal fun issueRequest(
    identifier: String? = AN_IDENTIFIER,
    ip: String = AN_IP,
): IssueOtpRequest = IssueOtpRequest(identifier?.let { SubmittedIdentifier(it) }, ip)

internal fun verifyRequest(
    identifier: String? = AN_IDENTIFIER,
    code: String? = A_CODE,
): VerifyOtpRequest = VerifyOtpRequest(identifier?.let { SubmittedIdentifier(it) }, code?.let { SubmittedCode(it) })

internal class InMemoryOtpRepository : OtpRepository {
    val stored = mutableListOf<Pair<IssuedOtp, String>>()

    override fun latestFor(identifier: LoginIdentifier): IssuedOtp? =
        stored.filter { it.first.identifier == identifier }.maxByOrNull { it.first.issuedAt }?.first

    override fun issuedCountSince(
        identifier: LoginIdentifier,
        since: Instant,
    ): Int = stored.count { it.first.identifier == identifier && !it.first.issuedAt.isBefore(since) }

    override fun issuedCountFromIpSince(
        ip: String,
        since: Instant,
    ): Int = stored.count { it.second == ip && !it.first.issuedAt.isBefore(since) }

    override fun insert(
        otp: IssuedOtp,
        requesterIp: String,
    ) {
        stored.add(otp to requesterIp)
    }

    override fun update(otp: IssuedOtp) {
        val index = stored.indexOfFirst { it.first.id == otp.id }
        check(index >= 0) { "updated an otp that was never inserted: ${otp.id}" }
        stored[index] = otp to stored[index].second
    }
}

internal class CountingOtpIdentifiers : OtpIdentifiers {
    private var minted = 0

    override fun next(): Outcome<OtpId, IdentityError> {
        minted += 1
        return OtpId.of("otp_$minted")
    }
}

internal class RefusingOtpIdentifiers : OtpIdentifiers {
    override fun next(): Outcome<OtpId, IdentityError> = OtpId.of("not-an-otp-id")
}

internal class FixedOtpCodes(
    private val code: String = A_CODE,
) : OtpCodeGenerator {
    override fun next(): String = code
}

internal class RecordingOtpSender : OtpSender {
    val sent = mutableListOf<Pair<LoginIdentifier, String>>()

    override fun send(
        identifier: LoginIdentifier,
        code: String,
    ) {
        sent.add(identifier to code)
    }
}
