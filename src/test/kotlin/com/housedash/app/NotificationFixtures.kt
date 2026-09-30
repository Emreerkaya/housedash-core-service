package com.housedash.app

import com.housedash.domain.notification.DeliveryStatus
import com.housedash.domain.notification.DeviceId
import com.housedash.domain.notification.DeviceRegistration
import com.housedash.domain.notification.DeviceTokenId
import com.housedash.domain.notification.NotificationError
import com.housedash.domain.notification.NotificationId
import com.housedash.domain.notification.QueuedNotification
import com.housedash.domain.shared.NesterId
import com.housedash.domain.shared.Outcome
import java.time.Instant

internal val NOW: Instant get() = CREATED_AT

internal fun <T, E> unwrap(outcome: Outcome<T, E>): T =
    when (outcome) {
        is Outcome.Ok -> outcome.value
        is Outcome.Err -> error("expected a value and got a refusal: ${outcome.error}")
    }

internal class InMemoryDeviceRegistrations : DeviceRegistrations {
    val registrationsByKey = mutableMapOf<Pair<String, String>, DeviceRegistration>()

    override fun upsert(registration: DeviceRegistration): DeviceTokenId {
        registrationsByKey[registration.owner.value to registration.device.value] = registration
        return registration.id
    }

    override fun find(
        owner: NesterId,
        device: DeviceId,
    ): DeviceRegistration? = registrationsByKey[owner.value to device.value]

    override fun revoke(
        owner: NesterId,
        device: DeviceId,
        at: Instant,
    ): Boolean {
        val key = owner.value to device.value
        val existing = registrationsByKey[key]
        if (existing == null) return false
        registrationsByKey[key] = existing.revoke(at)
        return true
    }

    override fun activeTokensFor(owner: NesterId): List<ActiveDeviceToken> =
        registrationsByKey.values
            .filter { it.owner == owner && !it.revoked }
            .map { ActiveDeviceToken(it.device, it.token) }
}

internal class CountingDeviceTokenIdentifiers : DeviceTokenIdentifiers {
    private var minted = 0

    override fun next(): Outcome<DeviceTokenId, NotificationError> {
        minted += 1
        return DeviceTokenId.of("dvt_$minted")
    }
}

internal class RefusingDeviceTokenIdentifiers : DeviceTokenIdentifiers {
    override fun next(): Outcome<DeviceTokenId, NotificationError> = DeviceTokenId.of("not-a-device-token-id")
}

internal class InMemoryOutbox : NotificationOutbox {
    val entriesById = mutableMapOf<String, QueuedNotification>()

    override fun enqueue(notification: QueuedNotification) {
        entriesById[notification.id.toString()] = notification
    }

    override fun drainDue(
        now: Instant,
        limit: Int,
        attempt: (QueuedNotification) -> QueuedNotification,
    ): Int {
        val due =
            entriesById.values
                .filter { it.status == DeliveryStatus.PENDING && !it.nextAttemptAt.isAfter(now) }
                .sortedBy { it.nextAttemptAt }
                .take(limit)
        due.forEach { entriesById[it.id.toString()] = attempt(it) }
        return due.size
    }
}

internal class RecordingSender(
    private val delivered: List<String> = emptyList(),
    private val stale: List<String> = emptyList(),
    private val transientlyFailed: List<String> = emptyList(),
) : ApnsSender {
    val sent = mutableListOf<PushDelivery>()

    override fun send(delivery: PushDelivery): PushSendResult {
        sent.add(delivery)
        return PushSendResult(delivered, stale, transientlyFailed)
    }
}

internal fun anId(): NotificationId = unwrap(NotificationId.of("ntf_1"))
