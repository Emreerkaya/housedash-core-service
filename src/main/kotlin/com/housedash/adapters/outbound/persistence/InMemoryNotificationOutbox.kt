package com.housedash.adapters.outbound.persistence

import com.housedash.app.NotificationOutbox
import com.housedash.domain.notification.DeliveryStatus
import com.housedash.domain.notification.QueuedNotification
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Repository
class InMemoryNotificationOutbox : NotificationOutbox {
    private val entriesById = ConcurrentHashMap<String, QueuedNotification>()

    private val lock = Any()

    override fun enqueue(notification: QueuedNotification) {
        entriesById[notification.id.toString()] = notification
    }

    override fun drainDue(
        now: Instant,
        limit: Int,
        attempt: (QueuedNotification) -> QueuedNotification,
    ): Int =
        synchronized(lock) {
            val due =
                entriesById.values
                    .filter { it.status == DeliveryStatus.PENDING && !it.nextAttemptAt.isAfter(now) }
                    .sortedBy { it.nextAttemptAt }
                    .take(limit)
            due.forEach { entriesById[it.id.toString()] = attempt(it) }
            due.size
        }

    fun outboxSize(): Int = entriesById.size
}
