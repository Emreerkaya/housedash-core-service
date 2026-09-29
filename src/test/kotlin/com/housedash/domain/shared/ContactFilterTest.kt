package com.housedash.domain.shared

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ContactFilterTest {
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
    fun `finds an email whose domain separator is a dot only in shape`() {
        listOf(
            "bob@gmail dot com",
            "bob@gmail\u00B7com",
            "bob@gmail\u2022com",
            "bob@gmail\u2027com",
            "bob@gmail\u30FBcom",
            "bob@gmail\uFF61com",
            "bob@gmail,com",
            "bob<at>gmail.com",
            "bob[at]gmail\u00B7com",
            "bob<dot>smith@gmail\u2022com",
        ).forEach { text ->
            assertEquals(setOf(ContactDetail.EmailAddress), contactDetailsIn(text), text)
        }
    }

    @Test
    fun `an at sign used to mean at does not turn the prose after it into a domain`() {
        listOf(
            "3 @ 5.00 each for the washers",
            "mail the receipt to 10001-1234 instead",
            "quotes @ 250, co-op board wants two more before it signs",
            "the leak started @ 8, however it had stopped by the morning",
            "washers @ 12, me and the super both looked at the joint",
            "arrived @ 9, dev work on the riser starts after that",
        ).forEach(::assertNothingFound)
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
    fun `the payment brand arm is a list, and these are brands it does not hold`() {
        listOf(
            "Payconiq",
            "Bizum",
            "Swish",
            "Vipps",
            "Blik",
            "MobilePay",
            "Pix",
            "Paytm",
            "GrabPay",
            "Twint",
        ).forEach(::assertNothingFound)
    }

    @Test
    fun `a brand whose name is an ordinary word is held only with its own host`() {
        listOf(
            "the door chime does not work when the button is pressed",
            "it would be wise to replace the whole valve while you are here",
            "the strike plate on the front door is bent out of shape",
            "we need to transfer the meter reading to the new tenant",
        ).forEach(::assertNothingFound)
        listOf(
            "chime.com/bob",
            "strike.me/bob",
            "wise.com/pay",
        ).forEach { text ->
            assertTrue(ContactDetail.PaymentLink in contactDetailsIn(text), text)
        }
        assertNothingFound("Wise")
        assertNothingFound("Chime")
    }

    @Test
    fun `bank coordinates written without a word naming them are not found`() {
        assertNothingFound("my bank is 31-27-00 12345678")
        assertNothingFound("wire it to 021000021 and 1234567890")
    }

    @Test
    fun `the confusable table covers three alphabets, and these fold under none of them`() {
        assertNothingFound("venm\u2C9F me instead")
        assertNothingFound("v\u1D07nmo me instead")
    }

    @Test
    fun `finds a payment service spelled with a cyrillic, greek or armenian lookalike`() {
        listOf(
            "c\u0430sh.app",
            "cash\u2044app",
            "v\u0435nmo me instead",
            "\u03C1aypal.me/bob",
            "venm\u0585 me instead",
            "ve\u0578mo me instead",
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
    fun `the guard refuses text longer than it reads rather than running the filter over it`() {
        val overLong = "kitchen tap drips from the base ".repeat(100)
        assertTrue(characterCount(overLong) > MOST_CHARACTERS_THE_GUARD_READS)
        val flaw = assertIs<Outcome.Err<TextFlaw>>(withoutContactDetails(overLong)).error
        assertEquals(TextFlaw.TooLong(characterCount(overLong), MOST_CHARACTERS_THE_GUARD_READS), flaw)
        val atTheCap = "a".repeat(MOST_CHARACTERS_THE_GUARD_READS)
        assertEquals(Outcome.Ok(atTheCap), withoutContactDetails(atTheCap))
    }

    @Test
    fun `contactDetailsIn is the unbounded primitive and the cap belongs to the guard above it`() {
        val overLong = "kitchen tap drips from the base. ".repeat(100) + "reach me on 917-555-0199"
        assertTrue(characterCount(overLong) > MOST_CHARACTERS_THE_GUARD_READS)
        assertEquals(setOf(ContactDetail.PhoneNumber), contactDetailsIn(overLong))
        assertIs<Outcome.Err<TextFlaw>>(withoutContactDetails(overLong))
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
            "flat 4B at 350 Example Street, bedroom 2 radiator is cold",
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
    fun `a phone shaped grouping is a phone number however the digits are named`() {
        listOf(
            "part 917-555-0199 is the one that failed",
            "serial 917 555 0199 on the burner plate",
            "ref 917-555-0199",
            "licence 917.555.0199",
        ).forEach(::assertPhoneNumber)
    }

    @Test
    fun `a word naming the digits as something else no longer vetoes phone detection`() {
        listOf(
            "ref 917-555-0199",
            "door code 917-555-0199 call me",
            "policy 917 555 0199 phone me",
            "part 9175550199 call",
            "licence 917.555.0199",
            "batch 917 555 0199 text me",
            "my order ref is 917-555-0199 call anytime",
        ).forEach(::assertPhoneNumber)
    }

    @Test
    fun `an explicit contact verb carries an ungrouped run that no other cue would carry`() {
        assertPhoneNumber("part 9175550199 call")
        assertPhoneNumber("my number 9175550199")
        assertNothingFound("part 9175550199 on the plate")
    }

    @Test
    fun `a number qualified as something else is not a phone cue but a bare one is`() {
        assertNothingFound("serial number 1234567890 is on the plate behind the panel")
        assertNothingFound("invoice number 1234567890 is still unpaid")
        assertPhoneNumber("the number is 1234567890 if you want it")
    }

    @Test
    fun `a default ignorable character between two letters never hides a brand`() {
        listOf(
            "ca\u200Dsh.app/bobby",
            "ven\u200Dmo me instead",
            "pay\u200Dpal.me/bobby",
            "insta\u200Dgram.com/bobplumber",
            "whats\u200Dapp me",
            "te\u200Dlegram.me/bobplumber",
        ).forEach { text ->
            assertTrue(contactDetailsIn(text).isNotEmpty(), text)
        }
        assertTrue(contactDetailsIn("t.me/bobplumber").isNotEmpty())
        assertTrue(contactDetailsIn("t.me\u2044bobplumber").isNotEmpty())
    }

    @Test
    fun `a comma, a semicolon or a colon groups a phone number and no cue is required`() {
        assertPhoneNumber("call me on 917,555,0199")
        assertPhoneNumber("text me on 917;555;0199")
        assertPhoneNumber("my number is 917:555:0199")
        assertPhoneNumber("917,555,0199 is best")
        assertPhoneNumber("pay me at 917,555,0199 instead of using the app")
        assertPhoneNumber("email me at 917,555,0199 rather than messaging in here")
        assertPhoneNumber("reach me 917;555;0199 anytime")
        assertPhoneNumber("917:555:0199 is best")
        assertPhoneNumber("+44,7700,900123")
    }

    @Test
    fun `a line break separates the parts of a signal exactly as a space does`() {
        assertPhoneNumber("917\n555\n0199 is best")
        assertPhoneNumber("+44\n7700\n900123")
        listOf("bob\n@\nexample.com", "bob at\nexample dot\ncom").forEach { text ->
            assertEquals(setOf(ContactDetail.EmailAddress), contactDetailsIn(text), text)
        }
        assertEquals(setOf(ContactDetail.MessagingHandle), contactDetailsIn("t.me\n/bobplumber"))
    }

    @Test
    fun `padding a phone number with extra single digit groups does not hide it`() {
        assertPhoneNumber("917-555-0199-0-0-0-0-0-0")
        assertPhoneNumber("917-555-0199 (1) (2) (3)")
        assertPhoneNumber("(1) (2) (3) 917-555-0199")
        assertNothingFound("the part number is 0141-445-2266-01 on the label")
        assertNothingFound("replace washers 1 2 3 4 5 6 7 8 9 10 in that order")
    }

    @Test
    fun `a candidate the padding rule trims is still measured against every phone rule`() {
        assertPhoneNumber("+917-555-0199-0-0-0-0-0-0")
        assertPhoneNumber("+1-2-3-917-555-0199-0-0-0")
        assertNothingFound("the plate reads 1 1234567 1234567 1234567 1 on the side")
        assertNothingFound("the rads are 1 22 33 44 55 66 77 8 across the run")
    }

    @Test
    fun `apple pay and google pay are found however their two words are joined`() {
        listOf(
            "apple-pay me",
            "google-pay me",
            "apple pay me",
            "applepay me",
            "apple  pay me",
            "apple.pay me",
            "western-union me",
            "pay-pal me",
        ).forEach { text ->
            assertEquals(setOf(ContactDetail.PaymentLink), contactDetailsIn(text), text)
        }
    }

    @Test
    fun `whats app is found however the brand is spelled`() {
        listOf(
            "what's app me instead",
            "what\u2019s app me instead",
            "whats app me instead",
            "whatsapp me instead",
            "what-s-app me instead",
        ).forEach { text ->
            assertTrue(contactDetailsIn(text).isNotEmpty(), text)
        }
        assertNothingFound("what's appropriate for this boiler is not clear")
        assertNothingFound("what is apparent here is nothing at all")
    }

    @Test
    fun `a middle dot or a bullet joins a brand exactly as a full stop does`() {
        listOf(
            "pay me at cash\u00B7app/bob",
            "t\u00B7me/bobplumber",
            "x\u2022com/bobplumber",
            "wise\u2027com is where to send it",
            "instagram\u30FBcom/bobplumber",
        ).forEach { text ->
            assertTrue(contactDetailsIn(text).isNotEmpty(), text)
        }
    }

    @Test
    fun `a combining mark between two digits does not split the run`() {
        assertPhoneNumber("917\u0300-555-0199 is best")
        assertPhoneNumber("call me on 917\u03005550199")
    }

    @Test
    fun `an email address with brackets or spaces around its at sign and dots is found`() {
        listOf(
            "bob(at)example(dot)com",
            "bob[at]example[dot]com",
            "bob{at}example{dot}com",
            "bob @ example.com",
            "bob @ example . com",
            "reach me at bob at gmail.com",
            "bob@e.x.a.m.p.l.e.c.o.m",
            "b o b @ e x a m p l e . c o m",
        ).forEach { text ->
            assertEquals(setOf(ContactDetail.EmailAddress), contactDetailsIn(text), text)
        }
    }

    @Test
    fun `a signal spaced one character to a separator is still found`() {
        listOf(
            "c a s h . a p p / b o b b y",
            "t . m e / b o b p l u m b e r",
            "v e n m o me instead",
            "w h a t s a p p me",
            "9 1 7 . 5 5 5 . 0 1 9 9",
        ).forEach { text ->
            assertTrue(contactDetailsIn(text).isNotEmpty(), text)
        }
    }

    @Test
    fun `thousands separators are money and not a phone number`() {
        assertNothingFound("the run cost 123,456,789 lira all in")
        assertNothingFound("the invoice total was 1,250.00 and 1,234 parts")
        assertPhoneNumber("call 1234,567,890 now")
    }

    @Test
    fun `a spread out number is only matched when one separator stands between each digit`() {
        assertPhoneNumber("9 1 7 5 5 5 0 1 9 9")
        assertNothingFound("steps \u2474\u2475\u2476\u2477\u2478\u2479\u247A\u247B\u247C")
    }

    @Test
    fun `a two group candidate needs a cue and ten digits, and a trailing short group is never a number`() {
        assertNothingFound("mail the receipt to 10001-1234 instead")
        assertNothingFound("call 10001-1234 now")
        assertPhoneNumber("call 10001-12345 now")
        assertNothingFound("the gasket stamped 12345-678-90 is split")
    }

    @Test
    fun `the numbers a plumbing description actually carries are all accepted`() {
        listOf(
            "fittings \u00BD \u00BE \u215C \u215D \u215E needed here",
            "call me when the \u00BD \u00BE \u215C \u215D \u215E fittings arrive",
            "fittings 1/2 3/4 3/8 5/8 7/8 needed here",
            "call me when the 1/2 3/4 3/8 5/8 7/8 fittings arrive",
            "elbows in 15 mm 22 mm 28 mm and 35 mm please",
            "the tank is 210 litres and the cylinder is 1,250 mm tall",
            "the radiator is 1400 x 600 mm and weighs 32 kg",
            "the pressure gauge reads 1.5 bar and the flow is 12.5 l/min",
            "mail the receipt to 10001-1234 instead",
            "the gasket stamped 12345-678-90 is split",
            "the invoice total was 1,250.00 and 1,234 parts",
            "the run cost 123,456,789 lira all in",
            "logged at 12:30:45 2026-09-24 by the engineer",
            "the fuse blew on 2026-09-24, 350 Example Street",
            "job 4455 on 2026-09-24 needs 2 washers 12 mm",
            "steps \u2474\u2475\u2476\u2477\u2478\u2479\u247A\u247B\u247C",
            "serial number 1234567890 is on the plate behind the panel",
            "the part number is 0141-445-2266-01 on the label",
            "appliance model ecoTEC plus 832, serial 21123400123456789",
            "the manual is at vaillant.co.uk if you want to read it",
            "3 @ 5.00 each for the washers",
            "bob at bobsplumbing.co.uk is where the invoices go",
        ).forEach(::assertNothingFound)
    }

    private fun insertionsInto(
        signal: String,
        inserted: Char,
        between: (Char) -> Boolean,
    ): List<String> =
        signal.indices
            .drop(1)
            .filter { between(signal[it - 1]) && between(signal[it]) }
            .map { signal.take(it) + inserted + signal.substring(it) }

    @Test
    fun `an invisible character between two letters never defeats a signal`() {
        val obfuscated =
            SIGNALS_SPELLED_WITH_LETTERS.flatMap { signal ->
                INVISIBLE_CHARACTERS.flatMap { insertionsInto(signal, it, Char::isLetter) }
            }
        assertTrue(obfuscated.size > SIGNALS_SPELLED_WITH_LETTERS.size)
        obfuscated.forEach { text -> assertTrue(contactDetailsIn(text).isNotEmpty(), text) }
    }

    @Test
    fun `an invisible character between two digits never splits a grouped phone number`() {
        val obfuscated = INVISIBLE_CHARACTERS.flatMap { insertionsInto("917-555-0199", it, Char::isDigit) }
        assertTrue(obfuscated.size > INVISIBLE_CHARACTERS.size)
        obfuscated.forEach(::assertPhoneNumber)
    }

    @Test
    fun `spacing a signal one character to a space never defeats it`() {
        val spread =
            SIGNALS_SPELLED_AS_ONE_TOKEN.flatMap { signal ->
                SPACES_A_SPREAD_OUT_SIGNAL_USES.map { signal.toCharArray().joinToString(it) }
            }
        spread.forEach { text -> assertTrue(contactDetailsIn(text).isNotEmpty(), text) }
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
    fun `a host with no dotted top level label is not found, and calling it not an email was wrong`() {
        assertNothingFound("bob@localhost is where the logs go")
        assertNothingFound("bob@example..com is a typo")
        assertNothingFound("bob@example.c0m is a typo")
        assertNothingFound("Kitchen tap drips from the base, email me at bob@gmail and I will send photos")
        assertNothingFound("Kitchen tap drips, email me at bob at gmail com and I will send photos")
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
    fun `the forms this filter does not match are the gaps someone chose, not the gaps that exist`() {
        listOf(
            "nine one seven five five five zero one nine nine",
            "ring 917 five five five 0199",
            "bobsplumbing.co.uk",
            "look me up, the business name is bobs plumbing",
            "bob at bobsplumbing.co.uk",
            "v.e.n.m.o me instead",
            "c-a-s-h-a-p-p me instead",
            "the plate reads 9175550199 and nothing else",
            "pay me at cash=app instead of the platform",
            "pay me at cash|app instead of the platform",
            "cash . app slash bob is where to send it",
            "wh4ts4pp me instead of using this",
            "telegram me when you are free",
            "signal me when you are free",
            "find me on nextdoor, the name is bob",
            "my insta is bobplumber if you want it",
            "reach me at bit.ly/bobplumber for the quote",
            "917 then 555 then 0199 is the number",
            "7700 9001 2345 is best",
            "7700,9001,2345 is best",
            "bob at example dot see oh em",
            "scan the qr code on my van",
        ).forEach(::assertNothingFound)
    }

    @Test
    fun `a spaced run of digits is only read as a phone number when every group is a single digit`() {
        assertPhoneNumber("9 1 7 5 5 5 0 1 9 9")
        assertNothingFound("the rads are 9 1 7 5 5 5 0 1 99 across the run")
    }

    @Test
    fun `an iban shaped code with a failing checksum is not an iban`() {
        assertNothingFound("the part code is GB33BUKB20201555555556 on the plate")
    }

    @Test
    fun `a grouped code too short to be an iban is left alone`() {
        assertNothingFound("the boiler plate reads GB33 BUKB 2020 is stamped by the door")
    }

    private companion object {
        private val SPACES_A_SPREAD_OUT_SIGNAL_USES = listOf(" ", "  ", "\u00A0", "\u2009")

        private val INVISIBLE_CHARACTERS =
            listOf('\u200D', '\u200B', '\u00AD', '\u2060', '\uFEFF', '\uFE0F', '\u0300', '\u034F')

        private val SIGNALS_SPELLED_WITH_LETTERS =
            listOf(
                "cash.app/bobby",
                "cashapp me",
                "venmo me instead",
                "paypal.me/bobby",
                "zelle please",
                "gofundme.com/bobby",
                "instagram.com/bobplumber",
                "t.me/bobplumber",
                "telegram.me/bobplumber",
                "wa.me/bobby",
                "whatsapp me",
                "bob@example.com",
            )

        private val SIGNALS_SPELLED_AS_ONE_TOKEN =
            listOf(
                "cash.app/bobby",
                "venmo",
                "paypal.me/bobby",
                "zelle",
                "gofundme.com/bobby",
                "instagram.com/bobplumber",
                "t.me/bobplumber",
                "telegram.me/bobplumber",
                "wa.me/bobby",
                "whatsapp",
                "bob@example.com",
                "917.555.0199",
            )
    }
}
