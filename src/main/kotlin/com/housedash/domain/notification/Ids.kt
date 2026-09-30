package com.housedash.domain.notification

import com.housedash.domain.shared.Identifier
import com.housedash.domain.shared.IdentifierFlaw
import com.housedash.domain.shared.IdentifierShape
import com.housedash.domain.shared.NesterId
import com.housedash.domain.shared.Outcome

private fun asMalformedOwner(flaw: IdentifierFlaw) = NotificationError.MalformedOwner(flaw)

fun ownerOf(raw: String): Outcome<NesterId, NotificationError> = NesterId.of(raw).mapError(::asMalformedOwner)

class DeviceId private constructor(
    value: String,
) : Identifier(value, SHAPE) {
    override fun toString(): String = value

    companion object {
        const val MAX_BODY_LENGTH = 64

        private val SHAPE = IdentifierShape("dv_", MAX_BODY_LENGTH)

        fun of(raw: String): Outcome<DeviceId, NotificationError> =
            SHAPE.check(raw).mapError(NotificationError::MalformedDeviceId).map { DeviceId(it) }
    }
}

class DeviceTokenId private constructor(
    value: String,
) : Identifier(value, SHAPE) {
    override fun toString(): String = value

    companion object {
        const val MAX_BODY_LENGTH = 64

        private val SHAPE = IdentifierShape("dvt_", MAX_BODY_LENGTH)

        fun of(raw: String): Outcome<DeviceTokenId, NotificationError> =
            SHAPE.check(raw).mapError(NotificationError::MalformedDeviceTokenId).map { DeviceTokenId(it) }
    }
}

class NotificationId private constructor(
    value: String,
) : Identifier(value, SHAPE) {
    override fun toString(): String = value

    companion object {
        const val MAX_BODY_LENGTH = 64

        private val SHAPE = IdentifierShape("ntf_", MAX_BODY_LENGTH)

        fun of(raw: String): Outcome<NotificationId, NotificationError> =
            SHAPE.check(raw).mapError(NotificationError::MalformedNotificationId).map { NotificationId(it) }
    }
}
