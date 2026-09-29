package com.housedash.domain.shared

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

private val MARKS_A_BANK_DETAIL_MAY_BE_GROUPED_WITH =
    listOf('-', ' ', '.', '/', '*', ',', ':', '|', '_', '•', '。')

private val PADDINGS_A_BANK_DETAIL_SEPARATOR_MAY_CARRY =
    listOf<(Char) -> String>({ "$it" }, { "$it " }, { " $it" }, { " $it " }, { "  $it  " })

private fun separatorsTheMarksSpell(): List<List<String>> {
    val uniform =
        MARKS_A_BANK_DETAIL_MAY_BE_GROUPED_WITH.flatMap { mark ->
            PADDINGS_A_BANK_DETAIL_SEPARATOR_MAY_CARRY.map { padding -> listOf(padding(mark)) }
        }
    val mixed =
        MARKS_A_BANK_DETAIL_MAY_BE_GROUPED_WITH.flatMap { first ->
            MARKS_A_BANK_DETAIL_MAY_BE_GROUPED_WITH
                .filterNot { it == first }
                .map { second -> listOf(first.toString(), "$second ") }
        }
    return uniform + mixed
}

private fun spelling(separators: List<String>): String =
    separators.joinToString("/") { separator -> separator.map { "U+%04X".format(it.code) }.joinToString("") }

private fun joinedBy(
    groups: List<String>,
    separators: List<String>,
): String =
    groups
        .mapIndexed { index, group ->
            if (index == 0) group else separators[(index - 1) % separators.size] + group
        }.joinToString("")

class BankDetailSeparatorTest {
    @Test
    fun `a sort code and an iban read the same separators as every other arm that reads one`() {
        val unread =
            separatorsTheMarksSpell().flatMap { separators ->
                val sortCode = joinedBy(listOf("12", "34", "56"), separators)
                val iban = joinedBy(listOf("GB82", "WEST", "1234", "5698", "7654", "32"), separators)
                listOfNotNull(
                    "sort code".takeIf {
                        ContactDetail.PaymentLink !in contactDetailsIn("sort code $sortCode for the transfer")
                    },
                    "iban".takeIf {
                        ContactDetail.PaymentLink !in contactDetailsIn("pay it to $iban when the work is done")
                    },
                ).map { arm -> "$arm grouped with ${spelling(separators)}" }
            }
        assertEquals(
            emptyList(),
            unread,
            "the shared rule decided which marks stand between two digit groups and left how many to each arm, " +
                "so a sort code took one mark, an iban took exactly one, and a phone number took up to eight. " +
                "This fixture is a list of separator strings rather than of characters on purpose: a Char " +
                "cannot express a mark followed by a space, and a list of one Char cannot express a run spelled " +
                "two ways, so the old fixture could not build either counterexample. How many is now decided " +
                "once for every arm. These spellings are not read: " + unread.joinToString(", "),
        )
        assertNothingFound("sort code 12x34x56 for the transfer")
        assertNothingFound("pay it to GB82xWESTx1234x5698x7654x32 when the work is done")
        assertNothingFound("pay it to GB83 WEST 1234 5698 7654 32 when the work is done")
    }

    @Test
    fun `the word code is what makes sort a bank detail cue, because sort on its own is an ordinary verb`() {
        listOf(
            "can you sort 20, 30, 40 amp fuses for the consumer unit",
            "sort 12, 15, 18 mm washers into bags before you come",
            "please sort 10 - 20 - 30 of the tiles by size before you come",
            "could you sort out the 1/2 and 3/4 fittings when you arrive",
            "sort 04-00-04 of the tiles by size",
        ).forEach(::assertNothingFound)
        assertPaymentLink("sort code 04-00-04 is on the statement")
        assertPaymentLink("sortcode 04-00-04 is on the statement")
        assertPaymentLink("sort-code 04 00 04 is on the statement")
        assertEquals(
            emptyList(),
            separatorsTheMarksSpell().filterNot { separators ->
                ContactDetail.PaymentLink in
                    contactDetailsIn("sort code ${joinedBy(listOf("12", "34", "56"), separators)} for the transfer")
            },
            "the widening that took this arm from eleven of fifty-five spellings to all of them also made the " +
                "bare verb a cue, and zero corpus flips said nothing about that because the corpus held no row " +
                "with sort as a verb. Every spelling the widening bought is still bought — this leg is the " +
                "same sweep as the one above, stated again against the narrowed cue so the narrowing is priced " +
                "rather than assumed",
        )
    }
}
