package com.housedash.app

class PushDelivery(
    val tokens: List<String>,
    val idempotencyKey: String,
    val title: String,
    val body: String,
)

class PushSendResult(
    val delivered: List<String>,
    val stale: List<String>,
    val transientlyFailed: List<String>,
) {
    val anyDelivered: Boolean get() = delivered.isNotEmpty()

    val anyRetryable: Boolean get() = transientlyFailed.isNotEmpty()
}

fun interface ApnsSender {
    fun send(delivery: PushDelivery): PushSendResult
}
