package com.housedash.domain.notification

import com.housedash.domain.shared.NesterId
import com.housedash.domain.shared.Outcome

internal fun <T, E> valueOf(outcome: Outcome<T, E>): T =
    when (outcome) {
        is Outcome.Ok -> outcome.value
        is Outcome.Err -> error("expected a value and got a refusal: ${outcome.error}")
    }

internal fun deviceId(raw: String = "dv_1"): DeviceId = valueOf(DeviceId.of(raw))

internal fun deviceTokenId(raw: String = "dvt_1"): DeviceTokenId = valueOf(DeviceTokenId.of(raw))

internal fun notificationId(raw: String = "ntf_1"): NotificationId = valueOf(NotificationId.of(raw))

internal fun owner(raw: String = "ns_1"): NesterId = valueOf(ownerOf(raw))
