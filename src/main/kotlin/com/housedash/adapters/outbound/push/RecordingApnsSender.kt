package com.housedash.adapters.outbound.push

import com.housedash.app.ApnsSender
import com.housedash.app.PushDelivery
import com.housedash.app.PushSendResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

class RecordedPushDelivery(
    val tokens: List<String>,
    val idempotencyKey: String,
)

@Component
class RecordingApnsSender : ApnsSender {
    private val logger = LoggerFactory.getLogger(RecordingApnsSender::class.java)

    private val recorded = CopyOnWriteArrayList<RecordedPushDelivery>()

    private val staleTokens = CopyOnWriteArrayList<String>()

    override fun send(delivery: PushDelivery): PushSendResult {
        recorded.add(RecordedPushDelivery(delivery.tokens, delivery.idempotencyKey))
        val stale = delivery.tokens.filter { it in staleTokens }
        val delivered = delivery.tokens.filterNot { it in staleTokens }
        logger.info(
            "recorded a push send with idempotency key {} for {} token(s), {} reported stale",
            delivery.idempotencyKey,
            delivery.tokens.size,
            stale.size,
        )
        return PushSendResult(delivered = delivered, stale = stale, transientlyFailed = emptyList())
    }

    fun markStale(token: String) {
        staleTokens.add(token)
    }

    fun deliveries(): List<RecordedPushDelivery> = recorded.toList()
}
