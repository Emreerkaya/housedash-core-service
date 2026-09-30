package com.housedash.app

import com.housedash.domain.notification.QueuedNotification
import com.housedash.domain.shared.NesterId
import java.time.Clock

class NotificationDrainer(
    private val outbox: NotificationOutbox,
    private val tokens: DeviceRegistrations,
    private val sender: ApnsSender,
    private val clock: Clock,
) {
    fun drain(batchSize: Int): Int = outbox.drainDue(clock.instant(), batchSize, ::attempt)

    private fun attempt(notification: QueuedNotification): QueuedNotification {
        val targets = tokens.activeTokensFor(notification.owner)
        if (targets.isEmpty()) return notification.failed(clock.instant())
        val result =
            sender.send(
                PushDelivery(
                    tokens = targets.map { it.token },
                    idempotencyKey = notification.idempotencyKey,
                    title = notification.title,
                    body = notification.body,
                ),
            )
        pruneStaleTokens(notification.owner, targets, result.stale)
        val at = clock.instant()
        return if (result.anyDelivered) notification.sent(at) else notification.failed(at)
    }

    private fun pruneStaleTokens(
        owner: NesterId,
        targets: List<ActiveDeviceToken>,
        staleTokens: List<String>,
    ) {
        val staleSet = staleTokens.toSet()
        targets.filter { it.token in staleSet }.forEach { tokens.revoke(owner, it.device, clock.instant()) }
    }
}
