package com.housedash.domain.shared

import org.junit.jupiter.api.Test
import java.text.Normalizer
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val A_CHARACTER_NO_TABLE_HOLDS = '☃'

private const val SEPARATORS_A_NUMBER_IS_WRITTEN_WITH =
    " \t._/\\()[]-\u2212\u00B7\u2022\u2027\u30FB\u3002\uFF61\u00A0\u2013*|~#'=>!?&+@"

private const val LETTERS_A_NUMBER_IS_NOT_GROUPED_BY = "xXoO"

private const val LAST_CHARACTER_IN_THE_BASIC_PLANE = 0xFFFF

private const val DIGIT_SHAPES_OUTSIDE_THE_DECIMAL_CATEGORY = 85

private val DIGIT_VALUES = 0..9

private const val ONE_CHARACTER = 1

private val REPRESENTATIVE_OF_A_STRIPPED_CATEGORY =
    mapOf(
        CharCategory.FORMAT to '‍',
        CharCategory.NON_SPACING_MARK to '́',
        CharCategory.COMBINING_SPACING_MARK to 'ः',
        CharCategory.ENCLOSING_MARK to '⃝',
    )

private val A_SENTENCE_FOR_EVERY_PAYMENT_SERVICE =
    mapOf(
        "cash" to "settle it on cash app once the work is done here",
        "venmo" to "settle it on venmo once the work is done here",
        "revolut" to "settle it on revolut once the work is done here",
        "wero" to "settle it on wero once the work is done here",
        "payid" to "settle it on payid once the work is done here",
        "strike" to "settle it on strike.me once the work is done here",
        "chime" to "settle it on chime.com once the work is done here",
        "interac" to "settle it by interac e-transfer once the work is done",
        "pay" to "settle it on pay pal once the work is done here",
        "zelle" to "settle it on zelle once the work is done here",
        "wise" to "settle it on wise.com once the work is done here",
        "square" to "settle it on square.link once the work is done here",
        "monzo" to "settle it on monzo.me once the work is done here",
        "apple" to "settle it with apple pay once the work is done here",
        "google" to "settle it with google pay once the work is done here",
        "stripe" to "settle it on buy.stripe.com once the work is done here",
        "ko" to "settle it on ko-fi.com once the work is done here",
        "gofundme" to "settle it on gofundme.com once the work is done here",
        "patreon" to "settle it on patreon.com once the work is done here",
        "western" to "settle it by western union once the work is done here",
        "moneygram" to "settle it by moneygram once the work is done here",
        "payoneer" to "settle it on payoneer once the work is done here",
        "skrill" to "settle it on skrill once the work is done here",
        "bitcoin" to "settle it in bitcoin once the work is done here",
        "ethereum" to "settle it in ethereum once the work is done here",
        "monero" to "settle it in monero once the work is done here",
    )

private val WORD_A_BRAND_FRAGMENT_OPENS_WITH = Regex("""^[A-Za-z]+""")

private val PAYMENT_SENTENCES = A_SENTENCE_FOR_EVERY_PAYMENT_SERVICE.values.toList()

private fun paymentServicesWithout(entry: String) =
    Regex(
        "(?i)\\b(?:" + TablesTheGuardReads.paymentServices.filterNot { it == entry }.joinToString("|") + ")\\b",
    )

private const val CONFUSABLES_THE_FOLD_TABLE_HOLDS =
    "\u0430\u0432\u0435\u043A\u043C\u043D\u043E\u0440\u0441\u0442\u0443\u0445\u0455\u0456\u0458" +
        "\u04BB\u04CF\u0501\u051B\u051D\u0410\u0412\u0415\u041A\u041C\u041D\u041E\u0420\u0421" +
        "\u0422\u0425\u0405\u0406\u0408\u03B1\u03B3\u03B5\u03B9\u03BA\u03BC\u03BD\u03BF\u03C1" +
        "\u03C4\u03C5\u03C7\u0391\u0392\u0395\u0397\u0399\u039A\u039C\u039D\u039F\u03A1\u03A4" +
        "\u03A5\u03A7\u0131\u0251\u0585\u0578\u057D\u0570\u056C\u0566"

private const val DOTS_A_BRAND_MAY_BE_WRITTEN_WITH = ".\u00B7\u2022\u2027\u30FB"

private const val SLASHES_A_HANDLE_MAY_BE_WRITTEN_WITH = "/\u2044\u2215\u29F8"

private const val APOSTROPHES_A_BRAND_MAY_CARRY = "'\u2019\u02BC\u055A"

private const val LINE_BREAKS_THE_TABLE_LISTS = "\n\r\t\u000B\u000C\u0085\u2028\u2029"

private const val PUNCTUATION_THE_THOUSANDS_ARM_OWNS = ",;:"

private const val WAYS_OF_ASKING_TO_BE_RUNG =
    "call calls called calling text texts texted ring rings dial dials phone phones telephone tel mobile " +
        "cell cellphone what~'s~app sms"

private const val NAMES_A_NUMBER_MAY_CARRY_INSTEAD =
    "serial serials model imei part parts sku meter reading invoice order ref reference barcode licence " +
        "license policy warranty asset batch code account acct lot unit catalogue catalog job door flat " +
        "buzzer apartment room version build"

private const val TOP_LEVEL_LABELS_THE_LIST_HOLDS =
    "com net org edu gov io co me uk us ca de fr nl es it ie au info mail email app dev"

private const val CONSUMER_MAIL_HOSTS_THE_LIST_HOLDS =
    "gmail googlemail hotmail outlook live msn yahoo ymail aol icloud proton protonmail gmx zoho yandex " +
        "fastmail tutanota qq"

private const val PAYMENT_SERVICES_THE_LIST_HOLDS =
    "cash~(?:app|me) venmo revolut wero payid strike.me chime.com interac~e~transfer pay~pal zelle wise.com " +
        "square.link monzo.me apple~pay google~pay (?:buy.|checkout.)?+stripe.com ko-?+fi.com gofundme.com " +
        "patreon.com western~union moneygram payoneer skrill bitcoin ethereum monero"

private const val MESSAGING_APPS_THE_LIST_HOLDS = "what~'s~app viber wechat kakaotalk"

private const val MESSAGING_HOSTS_THE_LIST_HOLD =
    "t.me telegram.me wa.me m.me api.whatsapp.com instagram.com ig.me facebook.com fb.me snapchat.com " +
        "tiktok.com x.com twitter.com nextdoor.com signal.me discord.gg linkedin.com/in"

private fun spelled(entry: String): String =
    entry
        .replace(TablesTheGuardReads.betweenTheWordsOfABrand + "{0,2}+", "~")
        .replace(TablesTheGuardReads.apostropheOrNone, "'")
        .replace(TablesTheGuardReads.dotInABrand, ".")

private fun assertTableHolds(
    pinned: String,
    table: List<String>,
    name: String,
) = assertEquals(
    pinned.split(" "),
    table.map(::spelled),
    "$name is not the list this test names. An entry that leaves a table is invisible to a test that " +
        "iterates the table, because the loop simply stops visiting it, and coverage cannot see a table at " +
        "all, so the contents are pinned here and moving them is a deliberate edit with a case beside it",
)

private fun assertTableHolds(
    pinned: String,
    table: Collection<Char>,
    name: String,
) = assertEquals(
    pinned.toList().sorted(),
    table.sorted(),
    "$name is not the set of characters this test names. An entry that leaves a table is invisible to a test " +
        "that iterates the table, because the loop simply stops visiting it",
)

private fun assertFinds(
    kind: ContactDetail,
    text: String,
    entry: String,
    table: String,
) {
    val found = contactDetailsIn(text)
    assertTrue(
        kind in found,
        "$table holds the entry ${'"'}$entry${'"'} and nothing in this suite exercised it. Coverage cannot see " +
            "a table: a map literal is one line to JaCoCo, so a table with six entries and a table with two " +
            "both report a hundred per cent. On ${'"'}$text${'"'} the guard reported $found and not $kind, so " +
            "either the entry is dead or the case for it is wrong",
    )
}

private fun assertFindsNothing(
    text: String,
    entry: String,
    table: String,
) {
    val found = contactDetailsIn(text)
    assertTrue(
        found.isEmpty(),
        "$table holds the entry ${'"'}$entry${'"'}, whose whole job is to stop an ordinary number reading as a " +
            "dialable one. On ${'"'}$text${'"'} the guard reported $found",
    )
}

class TableRowCoverageTest {
    @Test
    fun `every table this guard reads holds exactly the entries this test names`() {
        assertTableHolds(
            CONFUSABLES_THE_FOLD_TABLE_HOLDS,
            TablesTheGuardReads.confusablesFoldedToLatin.keys,
            "the confusable fold table",
        )
        assertTableHolds(
            DOTS_A_BRAND_MAY_BE_WRITTEN_WITH,
            TablesTheGuardReads.dotsABrandMayBeWrittenWith,
            "the brand dot table",
        )
        assertTableHolds(
            SLASHES_A_HANDLE_MAY_BE_WRITTEN_WITH,
            TablesTheGuardReads.slashesAHandleMayBeWrittenWith,
            "the handle slash table",
        )
    }

    @Test
    fun `every punctuation table this guard reads holds exactly the entries this test names`() {
        assertTableHolds(
            PUNCTUATION_THE_THOUSANDS_ARM_OWNS,
            TablesTheGuardReads.PUNCTUATION_THE_THOUSANDS_ARM_OWNS.toList(),
            "the table of marks the thousands arm reads",
        )
        assertTableHolds(
            APOSTROPHES_A_BRAND_MAY_CARRY,
            TablesTheGuardReads.apostrophesABrandMayCarry,
            "the apostrophe table",
        )
        assertTableHolds(
            LINE_BREAKS_THE_TABLE_LISTS,
            TablesTheGuardReads.lineBreaksADescriptionBoxCreates,
            "the line break table",
        )
    }

    @Test
    fun `every word list this guard reads holds exactly the entries this test names`() {
        assertTableHolds(WAYS_OF_ASKING_TO_BE_RUNG, TablesTheGuardReads.waysOfAskingToBeRung, "the phone cue list")
        assertTableHolds(
            NAMES_A_NUMBER_MAY_CARRY_INSTEAD,
            TablesTheGuardReads.namesANumberAsSomethingElse,
            "the list of names a number may carry instead",
        )
        assertTableHolds(
            TOP_LEVEL_LABELS_THE_LIST_HOLDS,
            TablesTheGuardReads.topLevelLabels,
            "the top level label list",
        )
        assertTableHolds(
            CONSUMER_MAIL_HOSTS_THE_LIST_HOLDS,
            TablesTheGuardReads.consumerMailHosts,
            "the mail host list",
        )
        assertTableHolds(PAYMENT_SERVICES_THE_LIST_HOLDS, TablesTheGuardReads.paymentServices, "the payment brand list")
        assertTableHolds(MESSAGING_APPS_THE_LIST_HOLDS, TablesTheGuardReads.messagingApps, "the messaging app list")
        assertTableHolds(MESSAGING_HOSTS_THE_LIST_HOLD, TablesTheGuardReads.messagingHosts, "the messaging host list")
    }

    @Test
    fun `every confusable in the fold table carries an email shape past the guard, and none is redundant`() {
        TablesTheGuardReads.confusablesFoldedToLatin.forEach { (confusable, latin) ->
            val carrier = "bob$latin@gmail.com"
            assertFinds(ContactDetail.EmailAddress, carrier, "$confusable to $latin", "the confusable fold table")
            assertFinds(
                ContactDetail.EmailAddress,
                "bob$confusable@gmail.com",
                "$confusable to $latin",
                "the confusable fold table",
            )
            assertEquals(
                confusable.toString(),
                Normalizer.normalize(confusable.toString(), Normalizer.Form.NFKD),
                "the fold table maps $confusable to $latin, and compatibility normalisation already does it, so " +
                    "this row buys nothing and the table is longer than the problem",
            )
        }
        assertTrue(
            contactDetailsIn("bob$A_CHARACTER_NO_TABLE_HOLDS@gmail.com").isEmpty(),
            "a character no fold table holds now carries an email address past the guard, so the two " +
                "assertions above no longer show that the table is what closes these",
        )
    }

    @Test
    fun `a separator between digit groups is anything but a letter, a digit and the thousands marks`() {
        SEPARATORS_A_NUMBER_IS_WRITTEN_WITH.forEach { separator ->
            assertFinds(
                ContactDetail.PhoneNumber,
                "call me on 917${separator}555${separator}0199",
                separator.code.toString(),
                "the rule that a separator is anything but a letter, a digit and the three thousands marks",
            )
        }
    }

    @Test
    fun `the three marks the thousands arm owns are not separators, and a letter is not one either`() {
        TablesTheGuardReads.PUNCTUATION_THE_THOUSANDS_ARM_OWNS.forEach { owned ->
            assertFindsNothing(
                "rated 10${owned}000$owned 12${owned}000 or 14${owned}000 BTU",
                owned.toString(),
                "the three marks the thousands arm owns, which the separator rule excludes on purpose so that a " +
                    "thousands-grouped quantity list is read by the arm that knows that shape",
            )
        }
        LETTERS_A_NUMBER_IS_NOT_GROUPED_BY.forEach { letter ->
            assertFindsNothing(
                "reach me on 917${letter}555${letter}0199 this week",
                letter.toString(),
                "the separator rule excludes letters, so a letter between digit groups stays an open gap and is " +
                    "pinned here as one",
            )
        }
    }

    @Test
    fun `every character unicode gives a digit value is folded to that digit, and there is no table of them`() {
        val shapes =
            (0..LAST_CHARACTER_IN_THE_BASIC_PLANE)
                .map { it.toChar() }
                .map { Normalizer.normalize(it.toString(), Normalizer.Form.NFKD) }
                .filter { it.length == ONE_CHARACTER }
                .map { it.first() }
                .filter { !it.isDigit() && Character.getNumericValue(it) in DIGIT_VALUES }
                .distinct()
        assertEquals(
            DIGIT_SHAPES_OUTSIDE_THE_DECIMAL_CATEGORY,
            shapes.size,
            "the number of characters this rule reaches changed. The rule is asked rather than listed on " +
                "purpose, because the corpus claimed circled digits were caught when that was true of one " +
                "member of the class and nothing else, so the count is pinned instead of the members",
        )
        shapes.forEach { shape ->
            assertFinds(
                ContactDetail.PhoneNumber,
                "917 555 019$shape",
                "U+%04X".format(shape.code),
                "the rule that a character unicode gives a digit value stands for that digit. The case carries " +
                    "no cue on purpose: uncued, the run is read only when it reaches ten digits, so the shape " +
                    "supplying the tenth is what the assertion turns on. With a cue, nine digits are enough and " +
                    "the case cannot tell a folded shape from a separator",
            )
        }
    }

    @Test
    fun `every dot a brand may be written with carries a brand host past the guard`() {
        TablesTheGuardReads.dotsABrandMayBeWrittenWith.forEach { dot ->
            assertFinds(
                ContactDetail.PaymentLink,
                "settle it on wise${dot}com once the work is done",
                dot.toString(),
                "the table of dots a brand may be written with",
            )
        }
    }

    @Test
    fun `every slash a handle may be written with carries a messaging handle past the guard`() {
        TablesTheGuardReads.slashesAHandleMayBeWrittenWith.forEach { slash ->
            assertFinds(
                ContactDetail.MessagingHandle,
                "message me on t.me${slash}bobplumber for photos",
                slash.toString(),
                "the table of slashes a handle may be written with",
            )
        }
    }

    @Test
    fun `every apostrophe a brand may carry is read inside the one brand that has one`() {
        TablesTheGuardReads.apostrophesABrandMayCarry.forEach { apostrophe ->
            assertFinds(
                ContactDetail.MessagingHandle,
                "message me on what${apostrophe}sapp about the leak",
                apostrophe.toString(),
                "the table of apostrophes a brand may carry",
            )
        }
    }

    @Test
    fun `every line break a description box creates joins the digit groups it split`() {
        TablesTheGuardReads.lineBreaksADescriptionBoxCreates.forEach { lineBreak ->
            assertFinds(
                ContactDetail.PhoneNumber,
                "call me on 917${lineBreak}555${lineBreak}0199",
                lineBreak.code.toString(),
                "the table of line breaks a description box creates",
            )
        }
    }

    @Test
    fun `every category stripped before matching is stripped from between two digit groups`() {
        assertEquals(
            TablesTheGuardReads.categoriesStrippedBeforeMatching,
            REPRESENTATIVE_OF_A_STRIPPED_CATEGORY.keys,
            "a category was added to or removed from the strip table and this test names no character in it, " +
                "so the new row has no case and the table grew without anything measuring it",
        )
        REPRESENTATIVE_OF_A_STRIPPED_CATEGORY.forEach { (category, member) ->
            assertFinds(
                ContactDetail.PhoneNumber,
                "call me on 917${member}5550199",
                category.name,
                "the table of categories stripped before matching",
            )
        }
    }

    @Test
    fun `every way of asking to be rung carries a bare keypad run past the guard`() {
        TablesTheGuardReads.waysOfAskingToBeRung.forEach { cue ->
            val spelling = if (cue == TablesTheGuardReads.whatsapp) "whatsapp" else cue
            assertTrue(
                WORD_A_BRAND_FRAGMENT_OPENS_WITH.matches(spelling),
                "the cue list gained the entry ${'"'}$cue${'"'}, which is not a plain word and is not the one " +
                    "entry this test knows how to spell, so it has no case",
            )
            assertFinds(ContactDetail.PhoneNumber, "$spelling me on 9175550199", cue, "the phone cue list")
        }
        assertTrue(
            contactDetailsIn("reach me on 9175550199").isEmpty(),
            "the cue list is an enabling list and its entire cost is false positives, so the uncued arm must " +
                "stay exactly as permissive as it was; reach is pinned as not found on purpose",
        )
    }

    @Test
    fun `every name a number may carry instead keeps an ordinary number out of the cue arm`() {
        TablesTheGuardReads.namesANumberAsSomethingElse.forEach { name ->
            assertFindsNothing(
                "the $name number is 9175550199",
                name,
                "the table of names a number may carry instead",
            )
        }
        assertFinds(
            ContactDetail.PhoneNumber,
            "the number is 9175550199",
            "no qualifier at all",
            "the table of names a number may carry instead",
        )
    }

    @Test
    fun `every top level label closes a worded email address`() {
        TablesTheGuardReads.topLevelLabels.forEach { label ->
            assertFinds(
                ContactDetail.EmailAddress,
                "write to bob at example dot $label about the leak",
                label,
                "the top level label list",
            )
        }
        assertTrue(
            contactDetailsIn("write to bob at example dot zzz about the leak").isEmpty(),
            "a label the list does not hold now closes a worded email address, so the cases above no longer " +
                "show that the list is what closes them",
        )
    }

    @Test
    fun `every consumer mail host closes a worded at before a real host`() {
        TablesTheGuardReads.consumerMailHosts.forEach { host ->
            assertFinds(
                ContactDetail.EmailAddress,
                "write to bob at $host.com about the leak under the sink",
                host,
                "the consumer mail host list",
            )
        }
        assertTrue(
            contactDetailsIn("write to bob at bobsplumbing.com about the leak under the sink").isEmpty(),
            "a host the list does not hold now closes a worded at, so the cases above no longer show that the " +
                "list is what closes them",
        )
    }

    @Test
    fun `every payment service in the brand list has a sentence that reaches that entry`() {
        val uncovered =
            TablesTheGuardReads.paymentServices.filterNot { entry ->
                val alternative = Regex("(?i)$entry")
                PAYMENT_SENTENCES.any { alternative.containsMatchIn(it) }
            }
        assertTrue(
            uncovered.isEmpty(),
            "the payment brand list holds entries no sentence in this test matches, so they were added without " +
                "anything measuring them and coverage reports a hundred per cent over the list either way: " +
                "$uncovered",
        )
    }

    @Test
    fun `no sentence written for a payment service stands for an entry that has gone`() {
        val dead =
            PAYMENT_SENTENCES.filterNot { sentence ->
                TablesTheGuardReads.paymentServices.any { Regex("(?i)$it").containsMatchIn(sentence) }
            }
        assertTrue(
            dead.isEmpty(),
            "these sentences match no entry in the payment brand list, so whatever they were written to cover " +
                "has gone and they now stand for nothing: $dead",
        )
    }

    @Test
    fun `no payment service entry is matched by another entry on every sentence that reaches it`() {
        val redundant =
            TablesTheGuardReads.paymentServices.filter { entry ->
                val withoutIt = paymentServicesWithout(entry)
                PAYMENT_SENTENCES
                    .filter { Regex("(?i)$entry").containsMatchIn(it) }
                    .all { withoutIt.containsMatchIn(it) }
            }
        assertTrue(
            redundant.isEmpty(),
            "these payment brand entries are matched by another entry in the same list on every sentence that " +
                "reaches them, so deleting them changes nothing and the list is longer than the problem it " +
                "solves: $redundant",
        )
    }

    @Test
    fun `every sentence written for a payment service is found as a payment link`() {
        A_SENTENCE_FOR_EVERY_PAYMENT_SERVICE.forEach { (brand, sentence) ->
            assertFinds(ContactDetail.PaymentLink, sentence, brand, "the payment brand list")
        }
    }

    @Test
    fun `every messaging app in the list has a case`() {
        TablesTheGuardReads.messagingApps.forEach { app ->
            val spelling = if (app == TablesTheGuardReads.whatsapp) "whatsapp" else app
            assertTrue(
                WORD_A_BRAND_FRAGMENT_OPENS_WITH.matches(spelling),
                "the messaging app list gained ${'"'}$app${'"'}, which is not a plain word and is not the one " +
                    "entry this test knows how to spell, so it has no case",
            )
            assertFinds(
                ContactDetail.MessagingHandle,
                "message me on $spelling about the leak under the sink",
                app,
                "the messaging app list",
            )
        }
    }

    @Test
    fun `every messaging host in the list has a case, built from the entry itself`() {
        TablesTheGuardReads.messagingHosts.forEach { host ->
            val written = host.replace(TablesTheGuardReads.dotInABrand, ".")
            assertTrue(
                !written.contains('['),
                "the messaging host list gained ${'"'}$host${'"'}, which this test cannot write out as a host " +
                    "by replacing the brand-dot class with a dot, so the entry has no case",
            )
            assertFinds(
                ContactDetail.MessagingHandle,
                "message me on $written/bobplumber for the photos",
                host,
                "the messaging host list",
            )
        }
    }
}
