package com.housedash.adapters.outbound.persistence

import com.housedash.domain.notification.DeviceId
import com.housedash.domain.notification.DeviceRegistration
import com.housedash.domain.notification.DeviceTokenId
import com.housedash.domain.notification.ownerOf
import com.housedash.domain.shared.NesterId
import com.housedash.domain.shared.Outcome
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val AT = Instant.parse("2026-09-29T09:00:00Z")

private fun <T, E> unwrap(outcome: Outcome<T, E>): T =
    when (outcome) {
        is Outcome.Ok -> outcome.value
        is Outcome.Err -> error("expected a value and got ${outcome.error}")
    }

private fun owner(raw: String = "ns_1"): NesterId = unwrap(ownerOf(raw))

private fun device(raw: String = "dv_1"): DeviceId = unwrap(DeviceId.of(raw))

private fun registration(
    id: String = "dvt_1",
    ownerRaw: String = "ns_1",
    deviceRaw: String = "dv_1",
    token: String = "tok",
): DeviceRegistration {
    val registered = DeviceRegistration.register(unwrap(DeviceTokenId.of(id)), owner(ownerRaw), device(deviceRaw), token, AT)
    return unwrap(registered)
}

class InMemoryDeviceRegistrationRepositoryTest {
    private val repository = InMemoryDeviceRegistrationRepository()

    @Test
    fun `upsert stores a registration and find reads it back`() {
        repository.upsert(registration())
        val found = assertIs<DeviceRegistration>(repository.find(owner(), device()))
        assertEquals("tok", found.token)
    }

    @Test
    fun `upserting the same owner and device again replaces rather than duplicates`() {
        repository.upsert(registration(token = "old"))
        repository.upsert(registration(id = "dvt_2", token = "new"))
        assertEquals(1, repository.registeredCount())
        assertEquals("new", assertIs<DeviceRegistration>(repository.find(owner(), device())).token)
    }

    @Test
    fun `revoke returns false for a device that was never registered`() {
        assertFalse(repository.revoke(owner(), device(), AT))
    }

    @Test
    fun `revoke returns true and the token no longer counts as active`() {
        repository.upsert(registration())
        assertTrue(repository.revoke(owner(), device(), AT))
        assertTrue(repository.activeTokensFor(owner()).isEmpty())
    }

    @Test
    fun `activeTokensFor only returns unrevoked tokens for that owner`() {
        repository.upsert(registration(deviceRaw = "dv_1", token = "t1"))
        repository.upsert(registration(id = "dvt_2", deviceRaw = "dv_2", token = "t2"))
        repository.revoke(owner(), device("dv_1"), AT)
        val active = repository.activeTokensFor(owner())
        assertEquals(1, active.size)
        assertEquals("t2", active.single().token)
    }

    @Test
    fun `find answers null for a device nothing was stored for`() {
        assertNull(repository.find(owner(), device()))
    }
}
