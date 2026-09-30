package com.housedash.domain.notification

import com.housedash.domain.shared.IdentifierFlaw

enum class NotificationTextField { TOKEN, TITLE, BODY }

sealed interface NotificationError {
    data class MalformedDeviceId(
        val flaw: IdentifierFlaw,
    ) : NotificationError

    data class MalformedDeviceTokenId(
        val flaw: IdentifierFlaw,
    ) : NotificationError

    data class MalformedNotificationId(
        val flaw: IdentifierFlaw,
    ) : NotificationError

    data class MalformedOwner(
        val flaw: IdentifierFlaw,
    ) : NotificationError

    data class NotPlainText(
        val field: NotificationTextField,
    ) : NotificationError

    data class TooShort(
        val field: NotificationTextField,
        val length: Int,
        val minimum: Int,
    ) : NotificationError

    data class TooLong(
        val field: NotificationTextField,
        val length: Int,
        val maximum: Int,
    ) : NotificationError
}
