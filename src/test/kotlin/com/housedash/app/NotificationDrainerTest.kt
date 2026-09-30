package com.housedash.app

import com.housedash.domain.notification.DeliveryStatus
import com.housedash.domain.notification.DeviceId
import com.housedash.domain.notification.DeviceRegistration
import com.housedash.domain.notification.DeviceTokenId
import com.housedash.domain.notification.NotificationId
import com.housedash.domain.notification.QueuedNotification
import com.housedash.domain.notification.ownerOf
import com.housedash.domain.shared.NesterId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun owner(): NesterId = unwrap(ownerOf("ns_1"))

private fun aRegistration(): DeviceRegistration {
    val id = unwrap(DeviceTokenId.of("dvt_1"))
    val device = unwrap(DeviceId.of("dv_1"))
    return unwrap(DeviceRegistration.register(id, owner(), device, "a-token", NOW))
}

private const val A_TITLE = "A visitor is at the door"

private const val A_BODY = "Your tasker has arrived"

private fun queuedNotification(id: String = "ntf_1"): QueuedNotification {
    val notificationId = unwrap(NotificationId.of(id))
    val queued = QueuedNotification.queue(notificationId, owner(), A_TITLE, A_BODY, NOW)
    return unwrap(queued)
}

class NotificationDrainerTest {
    @Test
    fun `a notification with an active token is sent, carrying the idempotency key`() {
        val registrations = InMemoryDeviceRegistrations()
        registrations.upsert(aRegistration())
        val outbox = InMemoryOutbox()
        outbox.enqueue(queuedNotification())
        val sender = RecordingSender(delivered = listOf("a-token"))
        val drainer = NotificationDrainer(outbox, registrations, sender, FIXED_CLOCK)

        val drained = drainer.drain(10)

        assertEquals(1, drained)
        assertEquals(DeliveryStatus.SENT, outbox.entriesById.getValue("ntf_1").status)
        assertEquals(1, sender.sent.size)
        assertEquals("ntf_1", sender.sent.single().idempotencyKey)
        assertEquals(listOf("a-token"), sender.sent.single().tokens)
    }

    @Test
    fun `a notification with no active token is not sent and counts as a failed attempt`() {
        val registrations = InMemoryDeviceRegistrations()
        val outbox = InMemoryOutbox()
        outbox.enqueue(queuedNotification())
        val sender = RecordingSender()
        val drainer = NotificationDrainer(outbox, registrations, sender, FIXED_CLOCK)

        drainer.drain(10)

        val after = outbox.entriesById.getValue("ntf_1")
        assertEquals(DeliveryStatus.PENDING, after.status)
        assertEquals(1, after.attemptCount)
        assertTrue(sender.sent.isEmpty(), "the sender must not be called when there is no route to deliver to")
    }

    @Test
    fun `a stale token is pruned from the registration and is not itself a failure`() {
        val registrations = InMemoryDeviceRegistrations()
        registrations.upsert(aRegistration())
        val outbox = InMemoryOutbox()
        outbox.enqueue(queuedNotification())
        val sender = RecordingSender(delivered = emptyList(), stale = listOf("a-token"))
        val drainer = NotificationDrainer(outbox, registrations, sender, FIXED_CLOCK)

        drainer.drain(10)

        assertTrue(registrations.activeTokensFor(owner()).isEmpty(), "a token APNs reports stale must be revoked")
        val after = outbox.entriesById.getValue("ntf_1")
        assertEquals(1, after.attemptCount, "no delivery means a retry is still scheduled")
    }

    @Test
    fun `a batch limit is respected across multiple due notifications`() {
        val registrations = InMemoryDeviceRegistrations()
        registrations.upsert(aRegistration())
        val outbox = InMemoryOutbox()
        outbox.enqueue(queuedNotification("ntf_1"))
        outbox.enqueue(queuedNotification("ntf_2"))
        val sender = RecordingSender(delivered = listOf("a-token"))
        val drainer = NotificationDrainer(outbox, registrations, sender, FIXED_CLOCK)

        val drained = drainer.drain(1)

        assertEquals(1, drained)
    }
}
