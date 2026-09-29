package com.housedash.domain.money

import com.housedash.domain.shared.IdentifierFlaw
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MoneyIdsTest {
    @Test
    fun `a hold id carries the eh prefix and reports its flaw as the money context's error`() {
        assertEquals("eh_7", holdId("eh_7").value)
        assertEquals("eh_7", holdId("eh_7").toString())
        assertEquals(MoneyError.MalformedHoldId(IdentifierFlaw.WrongPrefix), err(HoldId.of("sb_7")))
        assertEquals(MoneyError.MalformedHoldId(IdentifierFlaw.IllegalCharacterInBody), err(HoldId.of("eh_7 ")))
    }

    @Test
    fun `a subscription id carries the sb prefix and reports its flaw as the money context's error`() {
        assertEquals("sb_7", subscriptionId("sb_7").value)
        assertEquals("sb_7", subscriptionId("sb_7").toString())
        assertEquals(MoneyError.MalformedSubscriptionId(IdentifierFlaw.WrongPrefix), err(SubscriptionId.of("eh_7")))
    }

    @Test
    fun `identifiers are equal by value within a type and never across types`() {
        assertEquals(holdId("eh_7"), holdId("eh_7"))
        assertNotEquals(holdId("eh_7"), holdId("eh_8"))
        assertNotEquals<Any>(holdId("eh_7"), subscriptionId("sb_7"))
    }
}
