package com.housedash.app

import com.housedash.domain.notification.DeviceId
import com.housedash.domain.notification.DeviceRegistration
import com.housedash.domain.notification.DeviceTokenId
import com.housedash.domain.shared.NesterId
import java.time.Instant

class ActiveDeviceToken(
    val device: DeviceId,
    val token: String,
)

interface DeviceRegistrations {
    fun upsert(registration: DeviceRegistration): DeviceTokenId

    fun find(
        owner: NesterId,
        device: DeviceId,
    ): DeviceRegistration?

    fun revoke(
        owner: NesterId,
        device: DeviceId,
        at: Instant,
    ): Boolean

    fun activeTokensFor(owner: NesterId): List<ActiveDeviceToken>
}
