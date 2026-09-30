package com.housedash.adapters.outbound.push

import com.housedash.app.PushDelivery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordingApnsSenderTest {
    private val sender = RecordingApnsSender()

    @Test
    fun `sending records the delivery and reports every token delivered by default`() {
        val delivery = PushDelivery(listOf("tok-1", "tok-2"), "ntf_1", "title", "body")
        val result = sender.send(delivery)
        assertEquals(listOf("tok-1", "tok-2"), result.delivered)
        assertTrue(result.stale.isEmpty())
        assertEquals(1, sender.deliveries().size)
        assertEquals("ntf_1", sender.deliveries().single().idempotencyKey)
    }

    @Test
    fun `a token marked stale is reported stale rather than delivered`() {
        sender.markStale("tok-1")
        val result = sender.send(PushDelivery(listOf("tok-1", "tok-2"), "ntf_1", "title", "body"))
        assertEquals(listOf("tok-1"), result.stale)
        assertEquals(listOf("tok-2"), result.delivered)
    }

    @Test
    fun `a stale token is reported and not treated as an error, it is simply not delivered to`() {
        sender.markStale("tok-1")
        val result = sender.send(PushDelivery(listOf("tok-1"), "ntf_1", "title", "body"))
        assertTrue(result.delivered.isEmpty())
        assertEquals(listOf("tok-1"), result.stale)
        assertTrue(result.transientlyFailed.isEmpty())
    }
}
