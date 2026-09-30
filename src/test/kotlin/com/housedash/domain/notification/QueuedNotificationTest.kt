package com.housedash.domain.notification

import com.housedash.domain.shared.Outcome
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val CREATED_AT = Instant.parse("2026-09-29T09:00:00Z")

private const val A_TITLE = "A visitor is at the door"

private const val A_BODY = "A tasker has arrived at 12 Ash Grove for the booked call-out"

private fun queued(
    title: String = A_TITLE,
    body: String = A_BODY,
    at: Instant = CREATED_AT,
): Outcome<QueuedNotification, NotificationError> = QueuedNotification.queue(notificationId(), owner(), title, body, at)

class QueuedNotificationTest {
    @Test
    fun `queuing starts pending with zero attempts and the created time as the next attempt`() {
        val notification = valueOf(queued())
        assertEquals(DeliveryStatus.PENDING, notification.status)
        assertEquals(0, notification.attemptCount)
        assertEquals(CREATED_AT, notification.nextAttemptAt)
        assertEquals(notificationId().toString(), notification.idempotencyKey)
    }

    @Test
    fun `a blank title is refused`() {
        val failure = assertIs<Outcome.Err<NotificationError>>(queued(title = ""))
        assertEquals(
            NotificationError.TooShort(NotificationTextField.TITLE, 0, QueuedNotification.TITLE_MIN),
            failure.error,
        )
    }

    @Test
    fun `a title past the maximum is refused before the body is checked`() {
        val tooLong = "a".repeat(QueuedNotification.TITLE_MAX + 1)
        val failure = assertIs<Outcome.Err<NotificationError>>(queued(title = tooLong, body = ""))
        val error = assertIs<NotificationError.TooLong>(failure.error)
        assertEquals(NotificationTextField.TITLE, error.field)
    }

    @Test
    fun `a body past the maximum is refused`() {
        val tooLong = "a".repeat(QueuedNotification.BODY_MAX + 1)
        val failure = assertIs<Outcome.Err<NotificationError>>(queued(body = tooLong))
        val error = assertIs<NotificationError.TooLong>(failure.error)
        assertEquals(NotificationTextField.BODY, error.field)
    }

    @Test
    fun `sent is a terminal state that keeps the attempt count`() {
        val notification = valueOf(queued())
        val sentAt = CREATED_AT.plusSeconds(5)
        val sent = notification.sent(sentAt)
        assertEquals(DeliveryStatus.SENT, sent.status)
        assertEquals(0, sent.attemptCount)
        assertEquals(sentAt, sent.nextAttemptAt)
    }

    @Test
    fun `a failed attempt stays pending with a later next-attempt time and a higher count`() {
        val notification = valueOf(queued())
        val failedAt = CREATED_AT.plusSeconds(1)
        val failed = notification.failed(failedAt)
        assertEquals(DeliveryStatus.PENDING, failed.status)
        assertEquals(1, failed.attemptCount)
        assertTrue(failed.nextAttemptAt.isAfter(failedAt), "a failed attempt backs off rather than retrying now")
    }

    @Test
    fun `after the maximum attempts a failure moves the notification to dead`() {
        var notification = valueOf(queued())
        var at = CREATED_AT
        repeat(QueuedNotification.MAX_ATTEMPTS - 1) {
            notification = notification.failed(at)
            assertEquals(DeliveryStatus.PENDING, notification.status)
            at = notification.nextAttemptAt
        }
        val dead = notification.failed(at)
        assertEquals(DeliveryStatus.DEAD, dead.status)
        assertEquals(QueuedNotification.MAX_ATTEMPTS, dead.attemptCount)
    }

    @Test
    fun `each successive failure backs off further than the last`() {
        var notification = valueOf(queued())
        notification = notification.failed(CREATED_AT)
        val firstDelay = notification.nextAttemptAt.epochSecond - CREATED_AT.epochSecond
        val secondAttemptAt = notification.nextAttemptAt
        notification = notification.failed(secondAttemptAt)
        val secondDelay = notification.nextAttemptAt.epochSecond - secondAttemptAt.epochSecond
        assertTrue(secondDelay > firstDelay, "backoff $secondDelay after two must exceed $firstDelay after one")
    }

    @Test
    fun `rehydrating round trips every field`() {
        val row =
            NotificationRow(
                id = "ntf_1",
                owner = "ns_1",
                content = NotificationContent(A_TITLE, A_BODY),
                createdAt = CREATED_AT,
                progress = RawDeliveryProgress("PENDING", 2, CREATED_AT.plusSeconds(120)),
            )
        val notification = QueuedNotification.rehydrate(row)
        assertEquals(notificationId(), notification.id)
        assertEquals(owner(), notification.owner)
        assertEquals(A_TITLE, notification.title)
        assertEquals(A_BODY, notification.body)
        assertEquals(2, notification.attemptCount)
        assertEquals(DeliveryStatus.PENDING, notification.status)
    }

    @Test
    fun `a row with a malformed id is refused on load`() {
        val thrown = assertFailsWith<CorruptNotification> { QueuedNotification.rehydrate(rowWith(id = "nope")) }
        assertEquals(NotificationFault.MALFORMED_ID, thrown.fault)
    }

    @Test
    fun `a row with a malformed owner is refused on load`() {
        val thrown = assertFailsWith<CorruptNotification> { QueuedNotification.rehydrate(rowWith(owner = "nope")) }
        assertEquals(NotificationFault.MALFORMED_OWNER, thrown.fault)
    }

    @Test
    fun `a row with an unknown status is refused on load`() {
        val thrown = assertFailsWith<CorruptNotification> { QueuedNotification.rehydrate(rowWith(status = "LOST")) }
        assertEquals(NotificationFault.UNKNOWN_STATUS, thrown.fault)
    }

    @Test
    fun `a row with a negative attempt count is refused on load`() {
        val thrown = assertFailsWith<CorruptNotification> { QueuedNotification.rehydrate(rowWith(attemptCount = -1)) }
        assertEquals(NotificationFault.NEGATIVE_ATTEMPT_COUNT, thrown.fault)
    }

    @Test
    fun `a row whose next attempt precedes its creation is refused on load`() {
        val thrown =
            assertFailsWith<CorruptNotification> {
                QueuedNotification.rehydrate(rowWith(nextAttemptAt = CREATED_AT.minusSeconds(1)))
            }
        assertEquals(NotificationFault.NEXT_ATTEMPT_BEFORE_CREATED, thrown.fault)
    }

    @Test
    fun `toString never carries the title or body`() {
        val notification = valueOf(queued())
        val rendered = notification.toString()
        assertTrue(!rendered.contains(A_TITLE) && !rendered.contains(A_BODY))
    }

    private fun rowWith(
        id: String = "ntf_1",
        owner: String = "ns_1",
        status: String = "PENDING",
        attemptCount: Int = 0,
        nextAttemptAt: Instant = CREATED_AT,
    ): NotificationRow {
        val content = NotificationContent(A_TITLE, A_BODY)
        val progress = RawDeliveryProgress(status, attemptCount, nextAttemptAt)
        return NotificationRow(id, owner, content, CREATED_AT, progress)
    }
}
