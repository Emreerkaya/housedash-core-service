package com.housedash.domain.notification

import com.housedash.domain.shared.IdentifierFlaw
import com.housedash.domain.shared.NesterId
import com.housedash.domain.shared.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class IdsTest {
    @Test
    fun `a device id accepts its own prefix and rejects another type's`() {
        assertEquals("dv_1", assertIs<Outcome.Ok<DeviceId>>(DeviceId.of("dv_1")).value.value)
        assertEquals(
            NotificationError.MalformedDeviceId(IdentifierFlaw.WrongPrefix),
            assertIs<Outcome.Err<NotificationError>>(DeviceId.of("dvt_1")).error,
        )
    }

    @Test
    fun `a device token id accepts its own prefix and rejects another type's`() {
        assertEquals("dvt_1", assertIs<Outcome.Ok<DeviceTokenId>>(DeviceTokenId.of("dvt_1")).value.value)
        assertEquals(
            NotificationError.MalformedDeviceTokenId(IdentifierFlaw.WrongPrefix),
            assertIs<Outcome.Err<NotificationError>>(DeviceTokenId.of("dv_1")).error,
        )
    }

    @Test
    fun `a notification id accepts its own prefix and rejects another type's`() {
        assertEquals("ntf_1", assertIs<Outcome.Ok<NotificationId>>(NotificationId.of("ntf_1")).value.value)
        assertEquals(
            NotificationError.MalformedNotificationId(IdentifierFlaw.WrongPrefix),
            assertIs<Outcome.Err<NotificationError>>(NotificationId.of("dv_1")).error,
        )
    }

    @Test
    fun `ownerOf reads the shared nester shape and names it as a notification error`() {
        assertEquals(
            NotificationError.MalformedOwner(IdentifierFlaw.WrongPrefix),
            assertIs<Outcome.Err<NotificationError>>(ownerOf("dv_1")).error,
        )
        assertEquals("ns_1", assertIs<Outcome.Ok<NesterId>>(ownerOf("ns_1")).value.value)
    }

    @Test
    fun `identifiers render as the value they hold`() {
        assertEquals("dv_1", assertIs<Outcome.Ok<DeviceId>>(DeviceId.of("dv_1")).value.toString())
        assertEquals("dvt_1", assertIs<Outcome.Ok<DeviceTokenId>>(DeviceTokenId.of("dvt_1")).value.toString())
        assertEquals("ntf_1", assertIs<Outcome.Ok<NotificationId>>(NotificationId.of("ntf_1")).value.toString())
    }

    @Test
    fun `the published body bound is the one enforced`() {
        assertEquals(64, DeviceId.MAX_BODY_LENGTH)
        assertEquals(64, DeviceTokenId.MAX_BODY_LENGTH)
        assertEquals(64, NotificationId.MAX_BODY_LENGTH)
    }

    @Test
    fun `a body one character past the bound is rejected and the bound itself is accepted`() {
        assertIs<Outcome.Ok<DeviceId>>(DeviceId.of("dv_" + "a".repeat(DeviceId.MAX_BODY_LENGTH)))
        assertIs<Outcome.Err<NotificationError>>(DeviceId.of("dv_" + "a".repeat(DeviceId.MAX_BODY_LENGTH + 1)))
    }
}
