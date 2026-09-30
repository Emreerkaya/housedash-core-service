package com.housedash.domain.notification

import com.housedash.domain.shared.FreeTextRule
import com.housedash.domain.shared.NesterId
import com.housedash.domain.shared.Outcome
import com.housedash.domain.shared.TextFlaw
import java.time.Instant

enum class DeviceRegistrationFault {
    MALFORMED_ID,
    MALFORMED_OWNER,
    MALFORMED_DEVICE_ID,
    MALFORMED_TOKEN,
    REVOKED_BEFORE_REGISTERED,
}

class CorruptDeviceRegistration internal constructor(
    val fault: DeviceRegistrationFault,
) : IllegalStateException(fault.name)

class DeviceRegistrationRow(
    val id: String,
    val owner: String,
    val device: String,
    val token: String,
    val registeredAt: Instant,
    val revokedAt: Instant?,
)

class DeviceRegistration private constructor(
    val id: DeviceTokenId,
    val owner: NesterId,
    val device: DeviceId,
    val token: String,
    val registeredAt: Instant,
    val revokedAt: Instant?,
) {
    init {
        if (revokedAt != null && revokedAt.isBefore(registeredAt)) {
            throw CorruptDeviceRegistration(DeviceRegistrationFault.REVOKED_BEFORE_REGISTERED)
        }
    }

    val revoked: Boolean get() = revokedAt != null

    override fun toString(): String = "DeviceRegistration(id=$id, owner=$owner, device=$device, revoked=$revoked)"

    fun revoke(at: Instant): DeviceRegistration = if (revoked) this else withRevocation(at)

    private fun withRevocation(at: Instant): DeviceRegistration {
        check(!revoked) { "withRevocation is reached only through revoke, which already guards against a repeat" }
        return DeviceRegistration(id, owner, device, token, registeredAt, at)
    }

    companion object {
        const val MIN_TOKEN_LENGTH = 1

        const val MAX_TOKEN_LENGTH = 4096

        private val TOKEN_RULE = FreeTextRule(MIN_TOKEN_LENGTH, MAX_TOKEN_LENGTH)

        fun register(
            id: DeviceTokenId,
            owner: NesterId,
            device: DeviceId,
            rawToken: String,
            at: Instant,
        ): Outcome<DeviceRegistration, NotificationError> =
            TOKEN_RULE
                .check(rawToken)
                .mapError { asTokenError(it) }
                .map { DeviceRegistration(id, owner, device, it, at, null) }

        fun rehydrate(row: DeviceRegistrationRow): DeviceRegistration {
            val id = idOrThrow(row.id)
            val owner = ownerOrThrow(row.owner)
            val device = deviceOrThrow(row.device)
            if (row.token.isBlank()) throw CorruptDeviceRegistration(DeviceRegistrationFault.MALFORMED_TOKEN)
            return DeviceRegistration(id, owner, device, row.token, row.registeredAt, row.revokedAt)
        }

        private fun idOrThrow(raw: String): DeviceTokenId =
            when (val parsed = DeviceTokenId.of(raw)) {
                is Outcome.Ok -> parsed.value
                is Outcome.Err -> throw CorruptDeviceRegistration(DeviceRegistrationFault.MALFORMED_ID)
            }

        private fun ownerOrThrow(raw: String): NesterId =
            when (val parsed = ownerOf(raw)) {
                is Outcome.Ok -> parsed.value
                is Outcome.Err -> throw CorruptDeviceRegistration(DeviceRegistrationFault.MALFORMED_OWNER)
            }

        private fun deviceOrThrow(raw: String): DeviceId =
            when (val parsed = DeviceId.of(raw)) {
                is Outcome.Ok -> parsed.value
                is Outcome.Err -> throw CorruptDeviceRegistration(DeviceRegistrationFault.MALFORMED_DEVICE_ID)
            }

        private fun asTokenError(flaw: TextFlaw): NotificationError =
            when (flaw) {
                TextFlaw.NotPlainText -> NotificationError.NotPlainText(NotificationTextField.TOKEN)
                is TextFlaw.TooShort ->
                    NotificationError.TooShort(NotificationTextField.TOKEN, flaw.length, flaw.minimum)
                is TextFlaw.TooLong ->
                    NotificationError.TooLong(NotificationTextField.TOKEN, flaw.length, flaw.maximum)
                is TextFlaw.ContactDetails -> NotificationError.NotPlainText(NotificationTextField.TOKEN)
            }
    }
}
