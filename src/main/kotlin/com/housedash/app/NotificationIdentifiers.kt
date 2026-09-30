package com.housedash.app

import com.housedash.domain.notification.DeviceTokenId
import com.housedash.domain.notification.NotificationError
import com.housedash.domain.notification.NotificationId
import com.housedash.domain.shared.Outcome
import java.util.UUID

private const val UUID_GROUP_SEPARATOR = "-"

fun interface DeviceTokenIdentifiers {
    fun next(): Outcome<DeviceTokenId, NotificationError>
}

fun interface NotificationIdentifiers {
    fun next(): Outcome<NotificationId, NotificationError>
}

private fun freshId(prefix: String): String = prefix + UUID.randomUUID().toString().replace(UUID_GROUP_SEPARATOR, "")

class RandomDeviceTokenIdentifiers : DeviceTokenIdentifiers {
    override fun next(): Outcome<DeviceTokenId, NotificationError> = DeviceTokenId.of(freshId(PREFIX))

    private companion object {
        const val PREFIX = "dvt_"
    }
}

class RandomNotificationIdentifiers : NotificationIdentifiers {
    override fun next(): Outcome<NotificationId, NotificationError> = NotificationId.of(freshId(PREFIX))

    private companion object {
        const val PREFIX = "ntf_"
    }
}
