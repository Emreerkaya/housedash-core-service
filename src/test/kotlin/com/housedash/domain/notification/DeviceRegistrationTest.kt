package com.housedash.domain.notification

import com.housedash.domain.shared.NesterId
import com.housedash.domain.shared.Outcome
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val REGISTERED_AT = Instant.parse("2026-09-29T09:00:00Z")

private const val A_TOKEN = "a1b2c3d4e5f6"

private fun registered(
    id: DeviceTokenId = deviceTokenId(),
    owner: NesterId = owner(),
    device: DeviceId = deviceId(),
    token: String = A_TOKEN,
    at: Instant = REGISTERED_AT,
): Outcome<DeviceRegistration, NotificationError> = DeviceRegistration.register(id, owner, device, token, at)

class DeviceRegistrationTest {
    @Test
    fun `registering carries the id, owner, device and token and starts unrevoked`() {
        val registration = valueOf(registered())
        assertEquals(deviceTokenId(), registration.id)
        assertEquals(owner(), registration.owner)
        assertEquals(deviceId(), registration.device)
        assertEquals(A_TOKEN, registration.token)
        assertEquals(REGISTERED_AT, registration.registeredAt)
        assertFalse(registration.revoked)
        assertNull(registration.revokedAt)
    }

    @Test
    fun `a blank token is refused`() {
        val failure = assertIs<Outcome.Err<NotificationError>>(registered(token = ""))
        assertEquals(
            NotificationError.TooShort(NotificationTextField.TOKEN, 0, DeviceRegistration.MIN_TOKEN_LENGTH),
            failure.error,
        )
    }

    @Test
    fun `a token past the maximum length is refused`() {
        val tooLong = "a".repeat(DeviceRegistration.MAX_TOKEN_LENGTH + 1)
        val failure = assertIs<Outcome.Err<NotificationError>>(registered(token = tooLong))
        assertIs<NotificationError.TooLong>(failure.error)
    }

    @Test
    fun `revoking stamps the revocation time and is idempotent`() {
        val registration = valueOf(registered())
        val revokedAt = REGISTERED_AT.plusSeconds(60)
        val revoked = registration.revoke(revokedAt)
        assertTrue(revoked.revoked)
        assertEquals(revokedAt, revoked.revokedAt)
        val revokedAgain = revoked.revoke(revokedAt.plusSeconds(60))
        assertEquals(revokedAt, revokedAgain.revokedAt, "revoking twice keeps the first revocation time")
    }

    @Test
    fun `rehydrating round trips every field`() {
        val row =
            DeviceRegistrationRow(
                id = "dvt_1",
                owner = "ns_1",
                device = "dv_1",
                token = A_TOKEN,
                registeredAt = REGISTERED_AT,
                revokedAt = null,
            )
        val registration = DeviceRegistration.rehydrate(row)
        assertEquals(deviceTokenId(), registration.id)
        assertEquals(owner(), registration.owner)
        assertEquals(deviceId(), registration.device)
        assertEquals(A_TOKEN, registration.token)
    }

    @Test
    fun `a row with a malformed id is refused on load, not silently accepted`() {
        val row = rowWith(id = "nope")
        val thrown = assertFailsWith<CorruptDeviceRegistration> { DeviceRegistration.rehydrate(row) }
        assertEquals(DeviceRegistrationFault.MALFORMED_ID, thrown.fault)
    }

    @Test
    fun `a row with a malformed owner is refused on load`() {
        val row = rowWith(owner = "nope")
        val thrown = assertFailsWith<CorruptDeviceRegistration> { DeviceRegistration.rehydrate(row) }
        assertEquals(DeviceRegistrationFault.MALFORMED_OWNER, thrown.fault)
    }

    @Test
    fun `a row with a malformed device id is refused on load`() {
        val row = rowWith(device = "nope")
        val thrown = assertFailsWith<CorruptDeviceRegistration> { DeviceRegistration.rehydrate(row) }
        assertEquals(DeviceRegistrationFault.MALFORMED_DEVICE_ID, thrown.fault)
    }

    @Test
    fun `a row with a blank token is refused on load`() {
        val row = rowWith(token = "")
        val thrown = assertFailsWith<CorruptDeviceRegistration> { DeviceRegistration.rehydrate(row) }
        assertEquals(DeviceRegistrationFault.MALFORMED_TOKEN, thrown.fault)
    }

    @Test
    fun `a row revoked before it was registered is refused on load`() {
        val row = rowWith(revokedAt = REGISTERED_AT.minusSeconds(60))
        val thrown = assertFailsWith<CorruptDeviceRegistration> { DeviceRegistration.rehydrate(row) }
        assertEquals(DeviceRegistrationFault.REVOKED_BEFORE_REGISTERED, thrown.fault)
    }

    @Test
    fun `toString never carries the token`() {
        val registration = valueOf(registered())
        assertFalse(registration.toString().contains(A_TOKEN))
    }

    private fun rowWith(
        id: String = "dvt_1",
        owner: String = "ns_1",
        device: String = "dv_1",
        token: String = A_TOKEN,
        revokedAt: Instant? = null,
    ): DeviceRegistrationRow = DeviceRegistrationRow(id, owner, device, token, REGISTERED_AT, revokedAt)
}
