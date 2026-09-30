package com.housedash.app

import com.housedash.domain.notification.QueuedNotification
import java.time.Instant

interface NotificationOutbox {
    fun enqueue(notification: QueuedNotification)

    fun drainDue(
        now: Instant,
        limit: Int,
        attempt: (QueuedNotification) -> QueuedNotification,
    ): Int
}
