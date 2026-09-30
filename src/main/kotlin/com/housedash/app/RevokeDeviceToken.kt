package com.housedash.app

import com.housedash.domain.notification.NotificationError
import com.housedash.domain.shared.Outcome
import java.time.Clock

class RevokeDeviceTokenRequest(
    val nesterId: SubmittedNesterId?,
    val deviceId: SubmittedDeviceId?,
)

sealed interface RevokeDeviceTokenFailure {
    data class Rejected(
        val error: NotificationError,
    ) : RevokeDeviceTokenFailure

    data object RequestAbsent : RevokeDeviceTokenFailure

    data object NesterIdAbsent : RevokeDeviceTokenFailure

    data object DeviceIdAbsent : RevokeDeviceTokenFailure

    data object NotFound : RevokeDeviceTokenFailure
}

private typealias RevokeOutcome<T> = Outcome<T, RevokeDeviceTokenFailure>

private class PresentRevocationFields(
    val owner: SubmittedNesterId,
    val device: SubmittedDeviceId,
)

class RevokeDeviceToken(
    private val registrations: DeviceRegistrations,
    private val clock: Clock,
) {
    fun handle(request: RevokeDeviceTokenRequest?): RevokeOutcome<Unit> {
        val present = presentFieldsOf(request).valueOr { return it }
        val owner =
            present.owner
                .asOwner()
                .asFailure()
                .valueOr { return it }
        val device =
            present.device
                .asDeviceId()
                .asFailure()
                .valueOr { return it }
        val revoked = registrations.revoke(owner, device, clock.instant())
        return if (revoked) Outcome.Ok(Unit) else Outcome.Err(RevokeDeviceTokenFailure.NotFound)
    }
}

private fun presentFieldsOf(request: RevokeDeviceTokenRequest?): RevokeOutcome<PresentRevocationFields> {
    if (request == null) return Outcome.Err(RevokeDeviceTokenFailure.RequestAbsent)
    val owner = request.nesterId ?: return Outcome.Err(RevokeDeviceTokenFailure.NesterIdAbsent)
    val device = request.deviceId ?: return Outcome.Err(RevokeDeviceTokenFailure.DeviceIdAbsent)
    return Outcome.Ok(PresentRevocationFields(owner, device))
}

private fun <T> Outcome<T, NotificationError>.asFailure(): RevokeOutcome<T> {
    val mapped = mapError(RevokeDeviceTokenFailure::Rejected)
    return mapped
}

private inline fun <T> RevokeOutcome<T>.valueOr(bail: (Outcome.Err<RevokeDeviceTokenFailure>) -> Nothing): T =
    when (this) {
        is Outcome.Ok -> value
        is Outcome.Err -> bail(this)
    }
