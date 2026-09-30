package com.housedash.adapters.outbound.persistence

import com.housedash.domain.notification.NotificationId
import com.housedash.domain.notification.QueuedNotification
import com.housedash.domain.notification.ownerOf
import com.housedash.domain.shared.Outcome
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

private val CREATED_AT = Instant.parse("2026-09-29T09:00:00Z")

private fun <T, E> unwrap(outcome: Outcome<T, E>): T =
    when (outcome) {
        is Outcome.Ok -> outcome.value
        is Outcome.Err -> error("expected a value and got ${outcome.error}")
    }

private fun notification(
    id: String,
    nextAttemptAt: Instant = CREATED_AT,
): QueuedNotification =
    unwrap(
        QueuedNotification.queue(
            unwrap(NotificationId.of(id)),
            unwrap(ownerOf("ns_1")),
            "title",
            "body",
            CREATED_AT,
        ),
    ).let { if (nextAttemptAt == CREATED_AT) it else it.failed(nextAttemptAt) }

class InMemoryNotificationOutboxTest {
    private val outbox = InMemoryNotificationOutbox()

    @Test
    fun `enqueue makes a notification visible in the outbox size`() {
        outbox.enqueue(notification("ntf_1"))
        assertEquals(1, outbox.outboxSize())
    }

    @Test
    fun `drainDue claims only entries whose next attempt time has arrived`() {
        outbox.enqueue(notification("ntf_1"))
        val notYetDue = notification("ntf_2", nextAttemptAt = CREATED_AT.plusSeconds(600))
        outbox.enqueue(notYetDue)
        val drained = outbox.drainDue(CREATED_AT, 10) { it.sent(CREATED_AT) }
        assertEquals(1, drained)
    }

    @Test
    fun `drainDue respects the batch limit`() {
        outbox.enqueue(notification("ntf_1"))
        outbox.enqueue(notification("ntf_2"))
        val drained = outbox.drainDue(CREATED_AT, 1) { it.sent(CREATED_AT) }
        assertEquals(1, drained)
    }

    @Test
    fun `drainDue applies the given transition and persists the result, so a sent entry is not claimed again`() {
        outbox.enqueue(notification("ntf_1"))
        outbox.drainDue(CREATED_AT, 10) { it.sent(CREATED_AT) }
        val drainedAgain = outbox.drainDue(CREATED_AT.plusSeconds(3600), 10) { it.sent(CREATED_AT) }
        assertEquals(0, drainedAgain, "a sent entry must not be claimed again by a later drain")
    }
}
