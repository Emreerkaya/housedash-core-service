package com.housedash.adapters.outbound.persistence

import com.housedash.app.ActiveDeviceToken
import com.housedash.app.DeviceRegistrations
import com.housedash.domain.notification.DeviceId
import com.housedash.domain.notification.DeviceRegistration
import com.housedash.domain.notification.DeviceTokenId
import com.housedash.domain.shared.NesterId
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

private data class RegistrationKey(
    val owner: String,
    val device: String,
)

@Repository
class InMemoryDeviceRegistrationRepository : DeviceRegistrations {
    private val registrationsByKey = ConcurrentHashMap<RegistrationKey, DeviceRegistration>()

    override fun upsert(registration: DeviceRegistration): DeviceTokenId {
        registrationsByKey[keyOf(registration.owner, registration.device)] = registration
        return registration.id
    }

    override fun find(
        owner: NesterId,
        device: DeviceId,
    ): DeviceRegistration? = registrationsByKey[keyOf(owner, device)]

    override fun revoke(
        owner: NesterId,
        device: DeviceId,
        at: Instant,
    ): Boolean {
        val key = keyOf(owner, device)
        val existing = registrationsByKey[key] ?: return false
        registrationsByKey[key] = existing.revoke(at)
        return true
    }

    override fun activeTokensFor(owner: NesterId): List<ActiveDeviceToken> =
        registrationsByKey.values
            .filter { it.owner == owner && !it.revoked }
            .map { ActiveDeviceToken(it.device, it.token) }

    fun registeredCount(): Int = registrationsByKey.size
}

private fun keyOf(
    owner: NesterId,
    device: DeviceId,
): RegistrationKey = RegistrationKey(owner.value, device.value)
