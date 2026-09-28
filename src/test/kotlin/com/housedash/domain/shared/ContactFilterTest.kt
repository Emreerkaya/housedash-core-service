package com.housedash.domain.shared

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ContactFilterTest {
    private fun assertPhoneNumber(text: String) =
        assertEquals(
            setOf(ContactDetail.PhoneNumber),
            contactDetailsIn(text),
            text,
        )

    private fun assertNothingFound(text: String) = assertEquals(emptySet(), contactDetailsIn(text), text)

    @Test
    fun `finds a phone number in every separator style`() {
        listOf(
            "917-555-0199",
            "917.555.0199",
            "917 555 0199",
            "(917) 555-0199",
            "(917)555-0199",
            "+1 917 555 0199",
            "+19175550199",
            "1-917-555-0199",
            "call 917-555-0199 now",
            "my number is 9175550199 please call",
        ).forEach(::assertPhoneNumber)
    }

    @Test
    fun `finds a phone number whose separator is repeated or unusual`() {
        listOf(
            "917  555  0199",
            "917 - 555 - 0199",
            "917/555/0199",
            "917_555_0199",
            "( 917 ) 555-0199",
            "917\u00A0555\u00A00199",
            "917\u2009555\u20090199",
            "917\u3000555\u30000199",
            "917\u2013555\u20130199",
            "917\uFF0D555\uFF0D0199",
            "917\u00B7555\u00B70199",
        ).forEach(::assertPhoneNumber)
    }

    @Test
    fun `finds a phone number written in a non ascii digit family`() {
        listOf(
            "\uFF19\uFF11\uFF17-\uFF15\uFF15\uFF15-\uFF10\uFF11\uFF19\uFF19",
            "\u0669\u0661\u0667-\u0665\u0665\u0665-\u0660\u0661\u0669\u0669",
            "\uD835\uDFF5\uD835\uDFEC\uD835\uDFF3-\uD835\uDFF3\uD835\uDFF3\uD835\uDFF3" +
                "-\uD835\uDFEC\uD835\uDFED\uD835\uDFF5\uD835\uDFF5",
            "\u0967\u0968\u0969-\u096A\u096B\u096C-\u096D\u096E\u096F\u0966",
        ).forEach(::assertPhoneNumber)
    }

    @Test
    fun `finds a phone number spread one digit to a separator`() {
        assertPhoneNumber("9 1 7 5 5 5 0 1 9 9")
        assertPhoneNumber("9-1-7-5-5-5-0-1-9-9")
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
    fun `finds an email address whose at sign is spelled out or widened`() {
        listOf(
            "bob at example dot com",
            "bob(at)example.com",
            "bob[at]example.com",
            "bob at example (dot) com",
            "bob\uFF20example.com",
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
            "buy.stripe.com/aEU5kQ2",
            "checkout.stripe.com/x",
            "ko-fi.com/bob",
            "pay me in bitcoin",
        ).forEach { text ->
            assertTrue(ContactDetail.PaymentLink in contactDetailsIn(text), text)
        }
    }

    @Test
    fun `finds a payment service spelled with a cyrillic or greek lookalike`() {
        listOf(
            "c\u0430sh.app",
            "cash\u2044app",
            "v\u0435nmo me instead",
            "\u03C1aypal.me/bob",
        ).forEach { text ->
            assertTrue(ContactDetail.PaymentLink in contactDetailsIn(text), text)
        }
    }

    @Test
    fun `finds a bank account written as an iban, a sort code or an account number`() {
        listOf(
            "GB33BUKB20201555555555",
            "GB33 BUKB 2020 1555 5555 55",
            "DE89370400440532013000",
            "sort 04-00-04 acct 12345678",
            "sort code 040004",
            "account number 12345678",
        ).forEach { text ->
            assertTrue(ContactDetail.PaymentLink in contactDetailsIn(text), text)
        }
    }

    @Test
    fun `finds a crypto wallet address`() {
        listOf(
            "send it to 1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa please",
            "send it to bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4 please",
            "send it to 0x52908400098527886E0F7030069857D2E4169EE7 please",
        ).forEach { text ->
            assertTrue(ContactDetail.PaymentLink in contactDetailsIn(text), text)
        }
    }

    @Test
    fun `finds a messaging handle`() {
        listOf(
            "t.me/bobplumber",
            "instagram.com/bobplumber",
            "wa.me/19175550199",
            "facebook.com/bobplumber",
            "linkedin.com/in/bobplumber",
            "message me on whatsapp",
        ).forEach { text ->
            assertTrue(ContactDetail.MessagingHandle in contactDetailsIn(text), text)
        }
    }

    @Test
    fun `reports every kind it finds`() {
        assertEquals(
            setOf(
                ContactDetail.PhoneNumber,
                ContactDetail.EmailAddress,
                ContactDetail.PaymentLink,
                ContactDetail.MessagingHandle,
            ),
            contactDetailsIn("917-555-0199 bob@example.com cash.app/\$bob t.me/bob"),
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
        ).forEach(::assertNothingFound)
    }

    @Test
    fun `finds nothing in the numbers a real description carries`() {
        listOf(
            "boiler serial 1234567890",
            "serial number 1234567890 is on the plate behind the panel",
            "the part number is 0141-445-2266-01 on the label",
            "appliance model ecoTEC plus 832, serial 21123400123456789",
            "apartment 4B, buzzer 12 is on the left of the door",
            "the meter reading was 98765 on 2026-09-24",
            "invoice 4455 dated 2026-09-24 covers 3 visits",
            "the parts cost 12.50 13.75 14.00 all in",
            "sizes 10 12 14 16 18 20 22 are all wrong",
            "replace washers 1 2 3 4 5 6 7 8 9 10 in that order",
            "the pipe is 3\u00BD inch across the joint",
            "the flue is 600 mm long and the gap is 870 mm",
            "a 2015 model that has leaked for 14 days",
            "350 East 62nd Street, apartment 4B, third floor walk up",
            "the manual is at vaillant.co.uk if you want to read it",
        ).forEach(::assertNothingFound)
    }

    @Test
    fun `a ten digit run with no grouping and no phone cue is not a phone number`() {
        assertNothingFound("boiler serial 1234567890")
        assertNothingFound("the plate reads 9175550199 and nothing else")
    }

    @Test
    fun `a group longer than any phone group is not part of a phone number`() {
        assertNothingFound("the coil is stamped 1234567 890 on the side")
    }

    @Test
    fun `a grouped run with no group long enough for an exchange is not a phone number`() {
        assertNothingFound("the gauge showed 12 34 56 78 90 across the week")
    }

    @Test
    fun `nine digits one to a separator is the shortest spread out number matched`() {
        assertPhoneNumber("9 1 7 5 5 5 0 1 9")
    }

    @Test
    fun `an iban is found even when an earlier candidate fails the checksum`() {
        assertTrue(
            ContactDetail.PaymentLink in
                contactDetailsIn("AB12 3456 7890 1234 no, GB33BUKB20201555555555 yes"),
        )
    }

    @Test
    fun `a nine digit run is not read as a phone number`() {
        assertNothingFound("serial 917555019")
    }

    @Test
    fun `an eleven digit run adjacent to more digits is not read as a phone number`() {
        assertNothingFound("serial 191755501990001")
    }

    @Test
    fun `a grouped number named as something other than a phone is not a phone number`() {
        assertNothingFound("part 917-555-0199 is the one that failed")
        assertNothingFound("serial 917 555 0199 on the burner plate")
    }

    @Test
    fun `an international prefix outranks a name that would otherwise clear it`() {
        assertPhoneNumber("serial +1 917 555 0199 is how to reach me")
    }

    @Test
    fun `a two letter two digit token that fails the iban checksum is not a payment detail`() {
        assertNothingFound("BX22001234567890123 is stamped on the casing")
        assertNothingFound("GB34BUKB20201555555555 is stamped on the casing")
    }

    @Test
    fun `a token outside every iban length is not a payment detail`() {
        assertNothingFound("AB12 3456 7890 1234 5678 9012 3456 7890 123 stamped on the casing")
        assertNothingFound("AB12" + "3".repeat(31) + " stamped on the casing")
    }

    @Test
    fun `a host with no dotted top level label is not an email address`() {
        assertNothingFound("bob@localhost is where the logs go")
        assertNothingFound("bob@example..com is a typo")
        assertNothingFound("bob@example.c0m is a typo")
    }

    @Test
    fun `an email address at the very start and very end of the text is still found`() {
        assertEquals(setOf(ContactDetail.EmailAddress), contactDetailsIn("bob@example.com"))
        assertPhoneNumber("917-555-0199")
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

    @Test
    fun `the compatibility fold used for matching never reaches the value that is returned`() {
        val raw = "the pipe is 3\u00BD inch and the Vaillant\u2122 casing is cracked"
        val accepted = assertIs<Outcome.Ok<String>>(withoutContactDetails(raw))
        assertSame(raw, accepted.value)
        assertTrue(accepted.value.contains('\u00BD'))
        assertTrue(accepted.value.contains('\u2122'))
    }

    @Test
    fun `a fullwidth phone number is still rejected while the fold stays out of the value`() {
        assertPhoneNumber("\uFF19\uFF11\uFF17-\uFF15\uFF15\uFF15-\uFF10\uFF11\uFF19\uFF19")
        val raw = "\u00BD a bar of pressure, the \u2122 sticker is peeling off the casing"
        assertSame(raw, assertIs<Outcome.Ok<String>>(withoutContactDetails(raw)).value)
    }

    @Test
    fun `the forms this filter deliberately does not match`() {
        listOf(
            "nine one seven five five five zero one nine nine",
            "ring 917 five five five 0199",
            "bobsplumbing.co.uk",
            "look me up, the business name is bobs plumbing",
        ).forEach(::assertNothingFound)
    }
}
