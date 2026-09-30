package com.housedash.app

import com.housedash.domain.notification.DeviceId
import com.housedash.domain.notification.DeviceRegistration
import com.housedash.domain.notification.DeviceTokenId
import com.housedash.domain.notification.NotificationError
import com.housedash.domain.notification.ownerOf
import com.housedash.domain.shared.NesterId
import com.housedash.domain.shared.Outcome
import java.time.Clock
import java.time.Instant

class DeviceRegistrationRequest(
    val nesterId: SubmittedNesterId?,
    val deviceId: SubmittedDeviceId?,
    val token: SubmittedToken?,
)

class SubmittedNesterId(
    private val submitted: String,
) {
    fun asOwner(): Outcome<NesterId, NotificationError> = ownerOf(submitted)
}

class SubmittedDeviceId(
    private val submitted: String,
) {
    fun asDeviceId(): Outcome<DeviceId, NotificationError> = DeviceId.of(submitted)
}

class SubmittedToken(
    private val submitted: String,
) {
    fun registeredAs(
        id: DeviceTokenId,
        owner: NesterId,
        device: DeviceId,
        at: Instant,
    ): Outcome<DeviceRegistration, NotificationError> = DeviceRegistration.register(id, owner, device, submitted, at)
}

sealed interface RegisterDeviceTokenFailure {
    data class Rejected(
        val error: NotificationError,
    ) : RegisterDeviceTokenFailure

    data class DeviceTokenIdRefused(
        val error: NotificationError,
    ) : RegisterDeviceTokenFailure

    data object RequestAbsent : RegisterDeviceTokenFailure

    data object NesterIdAbsent : RegisterDeviceTokenFailure

    data object DeviceIdAbsent : RegisterDeviceTokenFailure

    data object TokenAbsent : RegisterDeviceTokenFailure
}

class RegisteredDeviceToken(
    val id: DeviceTokenId,
    val replaced: Boolean,
)

private typealias RegisterOutcome<T> = Outcome<T, RegisterDeviceTokenFailure>

private class PresentFields(
    val owner: SubmittedNesterId,
    val device: SubmittedDeviceId,
    val token: SubmittedToken,
)

class RegisterDeviceToken(
    private val registrations: DeviceRegistrations,
    private val identifiers: DeviceTokenIdentifiers,
    private val clock: Clock,
) {
    fun handle(request: DeviceRegistrationRequest?): RegisterOutcome<RegisteredDeviceToken> {
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
        return registerWith(present, owner, device)
    }

    private fun registerWith(
        present: PresentFields,
        owner: NesterId,
        device: DeviceId,
    ): RegisterOutcome<RegisteredDeviceToken> {
        val existing = registrations.find(owner, device)
        val at = clock.instant()
        val id = existing?.id ?: mintedId().valueOr { return it }
        val registration =
            present.token
                .registeredAs(id, owner, device, at)
                .asFailure()
                .valueOr { return it }
        registrations.upsert(registration)
        return Outcome.Ok(RegisteredDeviceToken(id, replaced = existing != null))
    }

    private fun mintedId(): RegisterOutcome<DeviceTokenId> {
        val minted = identifiers.next()
        return minted.mapError(RegisterDeviceTokenFailure::DeviceTokenIdRefused)
    }
}

private fun presentFieldsOf(request: DeviceRegistrationRequest?): RegisterOutcome<PresentFields> {
    if (request == null) return Outcome.Err(RegisterDeviceTokenFailure.RequestAbsent)
    val owner = request.nesterId ?: return Outcome.Err(RegisterDeviceTokenFailure.NesterIdAbsent)
    val device = request.deviceId ?: return Outcome.Err(RegisterDeviceTokenFailure.DeviceIdAbsent)
    val token = request.token ?: return Outcome.Err(RegisterDeviceTokenFailure.TokenAbsent)
    return Outcome.Ok(PresentFields(owner, device, token))
}

private fun <T> Outcome<T, NotificationError>.asFailure(): RegisterOutcome<T> {
    val mapped = mapError(RegisterDeviceTokenFailure::Rejected)
    return mapped
}

private inline fun <T> RegisterOutcome<T>.valueOr(bail: (Outcome.Err<RegisterDeviceTokenFailure>) -> Nothing): T =
    when (this) {
        is Outcome.Ok -> value
        is Outcome.Err -> bail(this)
    }
