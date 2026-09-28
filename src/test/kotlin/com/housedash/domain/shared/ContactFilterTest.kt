package com.housedash.domain.shared

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContactFilterTest {
    @Test
    fun `finds a phone number in every separator style`() {
        listOf(
            "917-555-0199",
            "917.555.0199",
            "917 555 0199",
            "9175550199",
            "(917) 555-0199",
            "(917)555-0199",
            "+1 917 555 0199",
            "+19175550199",
            "1-917-555-0199",
            "call 917-555-0199 now",
        ).forEach { text ->
            assertEquals(setOf(ContactDetail.PhoneNumber), contactDetailsIn(text), text)
        }
    }

    @Test
    fun `finds an email address`() {
        listOf(
            "bob@example.com",
            "bob.smith+jobs@example.co.uk",
            "write to BOB@EXAMPLE.COM today",
        ).forEach { text ->
            assertEquals(setOf(ContactDetail.EmailAddress), contactDetailsIn(text), text)
        }
    }

    @Test
    fun `finds a payment link or handle`() {
        listOf(
            "cash.app/\$bob",
            "cashapp me",
            "venmo",
            "Venmo me instead",
            "paypal.me/bob",
            "zelle please",
            "wise.com/pay",
            "revolut.me/bob",
            "square.link/x",
            "monzo.me/bob",
            "apple pay",
            "googlepay",
            "\$bobsmith",
        ).forEach { text ->
            assertTrue(ContactDetail.PaymentLink in contactDetailsIn(text), text)
        }
    }

    @Test
    fun `reports every kind it finds`() {
        assertEquals(
            setOf(ContactDetail.PhoneNumber, ContactDetail.EmailAddress, ContactDetail.PaymentLink),
            contactDetailsIn("917-555-0199 bob@example.com cash.app/\$bob"),
        )
    }

    @Test
    fun `finds nothing in ordinary repair prose`() {
        listOf(
            "kitchen tap drips from the base",
            "boiler pressure drops to half a bar overnight, a 2015 model",
            "flat 4B at 350 East 62nd Street, bedroom 2 radiator is cold",
            "the fuse blew on 2026-09-24 and blew again the next day",
            "the last quote was \$250 and the one before was \$180",
            "the pipe is 3/4 inch and weeping at the joint",
            "it has dripped for 14 days now",
            "model number BX-2200 on the sticker",
            "the meter reads 001234 and the dial is stuck",
            "my gazelle bike is chained to the wisecrack pipe",
            "the paypalace hotel radiator is not the issue",
            "cabinet is 600 mm wide and 870 mm tall",
        ).forEach { text ->
            assertEquals(emptySet(), contactDetailsIn(text), text)
        }
    }

    @Test
    fun `a nine digit run is not read as a phone number`() {
        assertEquals(emptySet(), contactDetailsIn("serial 917555019"))
    }

    @Test
    fun `an eleven digit run adjacent to more digits is not read as a phone number`() {
        assertEquals(emptySet(), contactDetailsIn("serial 191755501990001"))
    }

    @Test
    fun `text with no contact details passes through unchanged`() {
        assertEquals(Outcome.Ok("kitchen tap drips"), withoutContactDetails("kitchen tap drips"))
    }

    @Test
    fun `text with contact details is rejected carrying the kinds and never the text`() {
        val outcome = withoutContactDetails("call 917-555-0199")
        assertEquals(Outcome.Err(TextFlaw.ContactDetails(setOf(ContactDetail.PhoneNumber))), outcome)
    }
}
