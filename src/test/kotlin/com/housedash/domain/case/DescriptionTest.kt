package com.housedash.domain.case

import com.housedash.domain.shared.ContactDetail
import com.housedash.domain.shared.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class DescriptionTest {
    private fun errorOf(raw: String): CaseError = assertIs<Outcome.Err<CaseError>>(Description.of(raw)).error

    private fun textOf(raw: String): String = assertIs<Outcome.Ok<Description>>(Description.of(raw)).value.text

    @Test
    fun `accepts a description at exactly the minimum`() {
        assertEquals("a".repeat(20), textOf("a".repeat(20)))
    }

    @Test
    fun `accepts a description at exactly the maximum`() {
        assertEquals("a".repeat(2000), textOf("a".repeat(2000)))
    }

    @Test
    fun `stores the trimmed text and not the raw text`() {
        assertEquals("a".repeat(25), textOf("  " + "a".repeat(25) + "  "))
    }

    @Test
    fun `rejects an empty description`() {
        assertEquals(CaseError.DescriptionTooShort(0, 20), errorOf(""))
    }

    @Test
    fun `rejects a blank description`() {
        assertEquals(CaseError.DescriptionTooShort(0, 20), errorOf("        "))
    }

    @Test
    fun `rejects a description shorter than the minimum after trimming`() {
        assertEquals(CaseError.DescriptionTooShort(9, 20), errorOf("   tap drips   "))
    }

    @Test
    fun `rejects a description one character past the maximum`() {
        assertEquals(CaseError.DescriptionTooLong(2001, 2000), errorOf("a".repeat(2001)))
    }

    @Test
    fun `rejects a description padded to the minimum with zero width spaces`() {
        assertEquals(CaseError.DescriptionNotPlainText, errorOf("tap" + "\u200B".repeat(30)))
    }

    @Test
    fun `rejects a description padded to the minimum with non breaking spaces`() {
        assertEquals(CaseError.DescriptionTooShort(3, 20), errorOf("tap" + "\u00A0".repeat(30)))
    }

    @Test
    fun `rejects a description holding a nul character`() {
        assertEquals(CaseError.DescriptionNotPlainText, errorOf("the kitchen tap\u0000 drips a lot"))
    }

    @Test
    fun `rejects a description holding control characters`() {
        assertEquals(CaseError.DescriptionNotPlainText, errorOf("the kitchen tap \u0007drips a lot"))
        assertEquals(CaseError.DescriptionNotPlainText, errorOf("the kitchen \u001B[31mtap\u001B[0m drips"))
    }

    @Test
    fun `rejects a description holding a lone surrogate`() {
        assertEquals(CaseError.DescriptionNotPlainText, errorOf("the kitchen tap \uD83D drips a lot"))
    }

    @Test
    fun `counts emoji as one character each at the minimum`() {
        assertEquals(CaseError.DescriptionTooShort(10, 20), errorOf("\uD83D\uDE00".repeat(10)))
    }

    @Test
    fun `counts emoji as one character each at the maximum`() {
        assertEquals("\uD83D\uDE00".repeat(1000), textOf("\uD83D\uDE00".repeat(1000)))
        assertEquals(CaseError.DescriptionTooLong(2001, 2000), errorOf("\uD83D\uDE00".repeat(2001)))
    }

    @Test
    fun `normalises to composed form so the stored text has one spelling`() {
        val decomposed = "cafe\u0301 radiator is cold all day"
        val composed = "caf\u00E9 radiator is cold all day"
        assertEquals(composed, textOf(decomposed))
        assertEquals(Description.of(composed), Description.of(decomposed))
    }

    @Test
    fun `rejects a phone number`() {
        listOf(
            "call me on 917-555-0199 about the tap",
            "reach me at (917) 555-0199 any time",
            "my number is 9175550199 please call",
            "ring me on +1 917 555 0199 tonight",
            "my number is 917.555.0199 please call",
        ).forEach { raw ->
            assertEquals(
                CaseError.ContactDetailsInDescription(setOf(ContactDetail.PhoneNumber)),
                errorOf(raw),
                raw,
            )
        }
    }

    @Test
    fun `rejects an email address`() {
        assertEquals(
            CaseError.ContactDetailsInDescription(setOf(ContactDetail.EmailAddress)),
            errorOf("email me at bob@example.com about the tap"),
        )
    }

    @Test
    fun `rejects a payment link`() {
        listOf(
            "pay me at cash.app/\$bob for the parts",
            "just venmo me for the parts instead",
            "use paypal for the parts instead of this",
            "send it over zelle for the parts",
        ).forEach { raw ->
            assertEquals(
                CaseError.ContactDetailsInDescription(setOf(ContactDetail.PaymentLink)),
                errorOf(raw),
                raw,
            )
        }
    }

    @Test
    fun `reports every kind of contact detail it found and never the text`() {
        val error =
            errorOf("call 917-555-0199 or bob@example.com and pay at cash.app/\$bob")
        assertEquals(
            CaseError.ContactDetailsInDescription(
                setOf(ContactDetail.PhoneNumber, ContactDetail.EmailAddress, ContactDetail.PaymentLink),
            ),
            error,
        )
    }

    @Test
    fun `accepts ordinary repair prose that merely contains numbers`() {
        listOf(
            "the boiler pressure drops overnight, it is a 2015 model",
            "radiator in bedroom 2 is cold, flat 4B at 350 East 62nd Street",
            "the fuse blew on 2026-09-24 and blew again the next day",
            "the last plumber charged \$250 and it still leaks badly",
            "the pipe under the sink is 3/4 inch and weeping at the joint",
            "the tap has dripped for 14 days and the cabinet is swollen",
        ).forEach { raw ->
            assertIs<Outcome.Ok<Description>>(Description.of(raw), raw)
        }
    }

    @Test
    fun `two descriptions with the same text are equal and hash alike`() {
        val first = description()
        val second = description()
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals<Any>(first, TAP_DESCRIPTION)
        assertNotEquals(first, description("the radiator in the bedroom is cold"))
    }

    @Test
    fun `the text is not exposed through the default rendering`() {
        assertEquals(false, description().toString().contains("tap"))
    }

    @Test
    fun `the published bounds are the ones enforced`() {
        assertEquals(20, Description.MIN_LENGTH)
        assertEquals(2000, Description.MAX_LENGTH)
    }

    @Test
    fun `a description that is both too short and carries a phone number is reported as too short`() {
        assertEquals(CaseError.DescriptionTooShort(18, 20), errorOf("call me 9175550199"))
    }

    @Test
    fun `a description that is both too long and carries an email address is reported as too long`() {
        val raw = "bob@example.com " + "a".repeat(2000)
        assertIs<CaseError.DescriptionTooLong>(errorOf(raw))
    }

    @Test
    fun `a description that is not plain text and carries a phone number is reported as not plain text`() {
        assertEquals(
            CaseError.DescriptionNotPlainText,
            errorOf("call me on 917-555-0199 about the tap\u0000"),
        )
    }

    @Test
    fun `a description that is both too short and not plain text is reported as not plain text`() {
        assertEquals(CaseError.DescriptionNotPlainText, errorOf("tap\u0000"))
    }
}
